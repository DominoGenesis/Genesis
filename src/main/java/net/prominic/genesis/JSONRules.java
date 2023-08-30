package net.prominic.genesis;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import lotus.domino.Session;
import lotus.domino.View;
import lotus.domino.ACL;
import lotus.domino.ACLEntry;
import lotus.domino.Database;
import lotus.domino.DocumentCollection;
import lotus.domino.Document;
import lotus.domino.NotesException;
import net.prominic.gja_v084.GConfig;
import net.prominic.gja_v084.GLogger;
import net.prominic.util.FileUtils;
import net.prominic.utils.DominoUtils;
import net.prominic.utils.HTTP;

public class JSONRules {
	private Session m_session;
	private String m_catalog;
	private String m_configPath;
	private String m_commandPath;
	private GLogger m_logger;
	private StringBuilder m_logBuilder;

	private final String JSON_VERSION = "1.0.0";

	public JSONRules(Session session, String catalog, String configPath, String commandPath, GLogger logger) {
		m_session = session;
		m_catalog = catalog;
		m_configPath = configPath;
		m_commandPath = commandPath;
		m_logger = logger;
	}

	public boolean execute(String json) {
		JSONParser parser = new JSONParser();
		try {
			JSONObject jsonObject = (JSONObject) parser.parse(json);
			return execute(jsonObject);
		} catch (ParseException e) {
			log(e);
		}
		return false;
	}

	public boolean execute(Reader reader) {
		JSONParser parser = new JSONParser();
		try {
			JSONObject jsonObject = (JSONObject) parser.parse(reader);
			return execute(jsonObject);
		} catch (IOException e) {
			log(e);
		} catch (ParseException e) {
			log(e);
		}
		return false;
	}

	/*
	 * Exectute JSON
	 */
	private boolean execute(JSONObject obj) {
		// check json version
		if (obj.containsKey("versionjson")) {
			String versionjson = (String) obj.get("versionjson");	
			boolean valid = isValidVersionJSON(versionjson);
			if (!valid) {
				log("Genesis in outdated (can't process such JSON)");
				log("JSON version: " + versionjson);
				log("Genesis supports: " + this.JSON_VERSION);
				return false;
			};
		}

		// check require
		if (obj.containsKey("require")) {
			JSONObject requireObj = (JSONObject)obj.get("require");
			boolean valid = isRequire(requireObj);
			if (!valid) {
				log("This app requires");
				log(requireObj);
				return false;
			};
		}

		// if error
		if (obj.containsKey("error")) {
			String error = (String) obj.get("error");	
			log(error);
			return false;
		}

		JSONArray steps = (JSONArray) obj.get("steps");
		if (steps.size() == 0) {
			log("Invalid JSON structure (no steps defined)");
			return false;
		}

		if (obj.containsKey("config")) {
			JSONObject config = (JSONObject) obj.get("config");
			updateConfig(config);
		}

		if (obj.containsKey("title")) {
			log(obj.get("title"));
		}

		for(int i=0; i<steps.size(); i++) {
			JSONObject step = (JSONObject) steps.get(i);
			parseStep(step);
		}

		return true;
	}

	private boolean isRequire(JSONObject obj) {
		boolean res = true;
		try {
			if (obj.containsKey("notesversion")) {
				String[] requireVersion = ((String) obj.get("notesversion")).split("\\.");
				String[] sessionVersion = m_session.getNotesVersion().split("\\.");

				for(int i=0; i<=2; i++) {
					int requireSubVersion = Integer.parseInt(requireVersion[i]);
					int sessionSubVersion = Integer.parseInt(sessionVersion[i]);

					if (sessionSubVersion > requireSubVersion) {
						i = 2;
					}
					else if(sessionSubVersion < requireSubVersion) {
						res = false;
						i = 2;
					}
				}
			}
		} catch (NotesException e) {
			e.printStackTrace();
			res = false;
		}

		return res;
	}

	private boolean isValidVersionJSON(String versionjson) {
		String[] jsonArr = versionjson.split("\\.");
		String[] genesisArr = this.JSON_VERSION.split("\\.");

		for (int i=0; i<=2; i++) {
			int part1 = Integer.parseInt(jsonArr[i]);
			int part2 = Integer.parseInt(genesisArr[i]);

			if (part1 < part2) {
				log("JSON package is outdated: " + versionjson + " < " + this.JSON_VERSION);
				i = 3;
			}
			else if(part1 > part2) {
				log(jsonArr[i] + " > " + genesisArr[i]);
				return false;
			}
		}

		return true;
	}

	private void updateConfig(JSONObject config) {
		File f = new File(this.m_configPath);
		File dir = new File(f.getParent());
		if (!dir.exists()) {
			dir.mkdirs();
		}

		for(Object key : config.keySet()) {
			String name = (String) key;
			String value = (String) config.get(key);
			GConfig.set(this.m_configPath, name, value);
		}
	}

	/*
	 * Parse a step
	 */
	private void parseStep(JSONObject step) {
		if (step.containsKey("runif")) {
			boolean access = doRunIf((JSONObject)step.get("runif"));
			if (!access) return;
		}

		if (step.containsKey("title")) {
			log(step.get("title"));
		}

		if (step.containsKey("dependencies")) {
			doDependencies((JSONArray) step.get("dependencies"));
		}
		else if(step.containsKey("files")) {
			doFiles((JSONArray) step.get("files"));
		}
		else if(step.containsKey("notesINI")) {
			doNotesINI((JSONArray) step.get("notesINI"));
		}
		else if(step.containsKey("updatedesign")) {
			doUpdateDesign((JSONArray) step.get("updatedesign"));
		}
		else if(step.containsKey("databases")) {
			doDatabases((JSONArray) step.get("databases"));
		}
		else if(step.containsKey("commands")) {
			doCommands((JSONArray) step.get("commands"));
		}
		else if(step.containsKey("messages")) {
			doMessages((JSONArray) step.get("messages"));
		}
	}

	private void doCommands(JSONArray list) {
		if (list == null || list.size() == 0) return;

		String res = "";
		for(int i=0; i<list.size(); i++) {
			String v = (String) list.get(i);
			if (res.length()>0) res += "\n";
			res += v;
		}
		FileUtils.writeFile(new File(this.m_commandPath), res.toString());
	}

	private void doUpdateDesign(JSONArray list) {
		if (list == null || list.size() == 0) return;

		try {
			for(int i=0; i<list.size(); i++) {
				JSONObject obj = (JSONObject) list.get(i);
				String convert = isWindows() ? "nconvert" : "convert";
				String target = (String) obj.get("target");
				String template = (String) obj.get("template");
				String cmd = String.format("%s -d %s * %s", convert, target, template);
				@SuppressWarnings("unused")
				Process proc = Runtime.getRuntime().exec(cmd);
			}
		} catch (IOException e) {
			log(e);
		}
	}

	private boolean isWindows() {
		String osName = System.getProperty("os.name").toLowerCase();
		return osName.contains("win");
	}

	private boolean doRunIf(JSONObject jsonObject) {
		boolean access = true;

		if (jsonObject.containsKey("os")) {
			String osName = System.getProperty("os.name").toLowerCase();
			if (osName.contains("win")) {
				osName = "win";
			}
			else {
				osName = "unix";
			}
			JSONArray osList = (JSONArray) jsonObject.get("os");
			access = osList.contains(osName);
		}

		return access;
	}

	private void doDependencies(JSONArray list) {
		if (list == null || list.size() == 0) return;

		try {
			for(int i=0; i<list.size(); i++) {
				String v = (String) list.get(i);

				log("Dependency detected: " + v);

				StringBuilder appJSON = HTTP.get(m_catalog + "/package?openagent&id=" + v);
				JSONRules dependency = new JSONRules(this.m_session, this.m_catalog, this.m_configPath, this.m_commandPath, this.m_logger);
				dependency.execute(appJSON.toString());
			}
		} catch (IOException e) {
			log(e);
		}
	}

	/*
	 * Display messages to Domino console
	 */
	private void doMessages(JSONArray list) {
		if (list == null || list.size() == 0) return;

		for(int i=0; i<list.size(); i++) {
			String v = (String) list.get(i);
			log(v);
		}
	}

	/*
	 * Download files
	 */
	private void doFiles(JSONArray list) {
		if (list == null || list.size() == 0) return;

		String directory;
		try {
			directory = this.m_session.getEnvironmentString("Directory", true);

			for(int i=0; i<list.size(); i++) {
				JSONObject obj = (JSONObject) list.get(i);

				String from = (String) obj.get("from");
				String to = (String) obj.get("to");
				boolean replace = obj.containsKey("replace") && (Boolean)obj.get("replace");

				if (to.indexOf("${directory}")>=0) {
					to = to.replace("${directory}", directory);
				};

				saveFile(from, to, replace);
				log("> " + to);
			}
		} catch (NotesException e) {
			log(e);
		} catch (IOException e) {
			log(e);
		}
	}

	private void saveFile(String from, String to, boolean replace) throws IOException {
		// check if file already exists (by default skip)
		File file = new File(to);
		if (file.exists() && !replace) {
			return;
		}

		// create sub folders if needed
		String toPath = to.substring(0, to.lastIndexOf("/"));
		File dir = new File(toPath);
		if (!dir.exists()) {
			dir.mkdirs();
		}

		boolean res = HTTP.saveFile(new URL(from), to);
		if (!res) {
			log("> failed to download");
			return;
		}
	}

	/*
	 * notes.INI handling
	 */
	private void doNotesINI(JSONArray list) {
		if (list == null || list.size() == 0) return;

		for(int i=0; i<list.size(); i++) {
			JSONObject obj = (JSONObject) list.get(i);

			String name = (String) obj.get("name");
			String value = String.valueOf(obj.get("value"));

			boolean multivalue = obj.containsKey("multivalue") && (Boolean)obj.get("multivalue");
			String sep = multivalue ? (String) obj.get("sep") : "";

			try {
				setNotesINI(name, value, multivalue, sep);
			} catch (NotesException e) {
				log(e);
			}
		}
	}

	/*
	 * notes.INI variables
	 */
	private void setNotesINI(String name, String value, boolean multivalue, String sep) throws NotesException {
		if (multivalue) {
			String currentValue = m_session.getEnvironmentString(name, true);
			if (!currentValue.contains(value)) {
				if (!currentValue.isEmpty()) {
					currentValue += sep;
				}
				currentValue += value;
			}
			value = currentValue;
		}

		m_session.setEnvironmentVar(name, value, true);	
	}

	private void doDatabases(JSONArray list) {
		if (list == null || list.size() == 0) return;

		for(int i=0; i<list.size(); i++) {
			JSONObject database = (JSONObject) list.get(i);
			parseDatabase(database);
		}
	}

	private void parseDatabase(JSONObject json) {
		try {
			Database database = null;
			String action = (String) json.get("action");
			String filePath = (String) json.get("filePath");
			boolean sign = json.containsKey("sign") && (Boolean) json.get("sign");
			boolean replace = json.containsKey("replace") && (Boolean)json.get("replace");

			if (replace) {
				log("> replace: true");
				database = m_session.getDatabase(null, filePath);
				log("> database will be deleted: " + database.getFilePath());
				if (database != null && database.isOpen()) {
					database.remove();
				}
			}

			if ("create".equalsIgnoreCase(action)) {
				if (json.containsKey("templatePath")) {
					String title = (String) json.get("title");
					String templatePath = (String) json.get("templatePath");
					database = createDatabaseFromTemplate(filePath, title, templatePath);	
				}
				else if(json.containsKey("replicaPath")) {
					String replicaServer = (String) json.get("replicaServer");
					String replicaPath = (String) json.get("replicaPath");
					database = createDatabaseReplica(filePath, replicaServer, replicaPath);	
				}
			}
			else {
				database = m_session.getDatabase(null, filePath);
			}

			if (database == null) {
				log("> Database not found: " + filePath);
				return;
			};

			if (sign) {
				DominoUtils.sign(database);
			}

			if (json.containsKey("ACL")) {
				parseACL(database, (JSONObject) json.get("ACL"));
			}

			JSONArray documents = (JSONArray) json.get("documents");
			parseDocuments(database, documents);

			JSONArray views = (JSONArray) json.get("views");
			parseViews(database, views);
		} catch (NotesException e) {
			log(e);
		}
	}

	private void parseViews(Database database, JSONArray array) {
		try {
			if (array == null) return;

			for(int i=0; i<array.size(); i++) {
				JSONObject obj = (JSONObject) array.get(i);

				String name = (String) obj.get("name");
				View view = database.getView(name);
				if (view != null) {
					boolean refresh = obj.containsKey("refresh") && (Boolean) obj.get("refresh");
					if (refresh) {
						view.refresh();
					}
				}
			}
		} catch (NotesException e) {
			log(e);
		}

	}

	private void parseACL(Database database, JSONObject json) {
		try {

			ACL acl = database.getACL();
			boolean toSave = false;

			if (json.containsKey("roles")) {
				toSave = parseRoles(acl, (JSONArray) json.get("roles")) || toSave;
			}

			if (json.containsKey("ACLEntries")) {
				toSave = parseACLEntries(acl, (JSONArray) json.get("ACLEntries")) || toSave;
			}

			if (toSave) {
				log("> ACL: updated & saved");
				acl.save();
			}
			else {
				log("> ACL: no updates");
			}

		} catch (NotesException e) {
			log(e);
		}

	}

	private boolean parseRoles(ACL acl, JSONArray array) {
		boolean toSave = false;
		if (array == null || array.size() == 0) return toSave;

		try {
			Vector<?> roles = acl.getRoles();
			for(int i=0; i<array.size(); i++) {
				String val = (String) array.get(i);

				String valAsRole = "["+val+"]";
				if (roles.contains(valAsRole)) {
					log(String.format("> ACL: role (%s) already exists", val));
				}
				else {
					acl.addRole(val);
					log(String.format("> ACL: added role (%s)", val));
					toSave = true;
				}
			}
		} catch (NotesException e) {
			log(e);
		}

		return toSave;
	}

	private boolean parseACLEntries(ACL acl, JSONArray array) {
		boolean toSave = false;
		if (array == null || array.size() == 0) return toSave;

		for(int i=0; i<array.size(); i++) {
			JSONObject obj = (JSONObject) array.get(i);

			toSave = parseACLEntry(acl, obj) || toSave;
		}

		return toSave;
	}

	private boolean parseACLEntry(ACL acl, JSONObject obj) {
		boolean toSave = false;
		if (!obj.containsKey("name")) return toSave;

		try {
			String name = (String) obj.get("name");
			ACLEntry entry = acl.getEntry(name);

			// 1. get/create entry (default no access)
			if (entry == null) {
				entry = acl.createACLEntry(name, ACL.LEVEL_NOACCESS);
				log(String.format("> ACL: new entry (%s)", name));
				toSave = true;
			}

			// 2. level
			if (obj.containsKey("level")) {
				String sLevel = (String) obj.get("level");
				int level = ACL.LEVEL_NOACCESS;
				if ("noAccess".equalsIgnoreCase(sLevel)) {
					level = ACL.LEVEL_NOACCESS;
				}
				else if("depositor".equalsIgnoreCase(sLevel)) {
					level = ACL.LEVEL_DEPOSITOR;
				}
				else if("reader".equalsIgnoreCase(sLevel)) {
					level = ACL.LEVEL_READER;
				}
				else if("author".equalsIgnoreCase(sLevel)) {
					level = ACL.LEVEL_AUTHOR;
				}
				else if("editor".equalsIgnoreCase(sLevel)) {
					level = ACL.LEVEL_EDITOR;
				}
				else if("designer".equalsIgnoreCase(sLevel)) {
					level = ACL.LEVEL_DESIGNER;
				}
				else if("manager".equalsIgnoreCase(sLevel)) {
					level = ACL.LEVEL_MANAGER;
				}

				if (entry.getLevel() != level) {
					entry.setLevel(level);
					toSave = true;
					log(String.format(">> ACLEntry: level (%s)", sLevel));
				}
			}

			// 3. type
			if (obj.containsKey("type")) {
				String sType = (String) obj.get("type");
				int type = ACLEntry.TYPE_UNSPECIFIED;
				if ("unspecified".equalsIgnoreCase(sType)) {
					type = ACLEntry.TYPE_UNSPECIFIED;
				}
				else if("person".equalsIgnoreCase(sType)) {
					type = ACLEntry.TYPE_PERSON;
				}
				else if("server".equalsIgnoreCase(sType)) {
					type = ACLEntry.TYPE_SERVER;
				}
				else if("personGroup".equalsIgnoreCase(sType)) {
					type = ACLEntry.TYPE_PERSON_GROUP;
				}
				else if("serverGroup".equalsIgnoreCase(sType)) {
					type = ACLEntry.TYPE_SERVER_GROUP;
				}
				else if("mixedGroup".equalsIgnoreCase(sType)) {
					type = ACLEntry.TYPE_MIXED_GROUP;
				}

				if (entry.getUserType() != type) {
					entry.setUserType(type);
					log(String.format(">> ACLEntry: type (%s)", sType));
					toSave = true;
				}
			}

			// 4. canCreateDocuments
			boolean canCreateDocuments = obj.containsKey("canCreateDocuments") && (Boolean) obj.get("canCreateDocuments");
			if (entry.isCanCreateDocuments() != canCreateDocuments) {
				entry.setCanCreateDocuments(canCreateDocuments);
				log(String.format(">> ACLEntry: setCanCreateDocuments (%b)", canCreateDocuments));
				toSave = true;

			}

			// 5. canDeleteDocuments
			boolean canDeleteDocuments = obj.containsKey("canDeleteDocuments") && (Boolean) obj.get("canDeleteDocuments");
			if (entry.isCanDeleteDocuments() != canDeleteDocuments) {
				entry.setCanDeleteDocuments(canDeleteDocuments);
				log(String.format(">> ACLEntry: canDeleteDocuments (%b)", canDeleteDocuments));
				toSave = true;
			}

			// 6. canCreatePersonalAgent
			boolean canCreatePersonalAgent = obj.containsKey("canCreatePersonalAgent") && (Boolean) obj.get("canCreatePersonalAgent");
			if (entry.isCanCreatePersonalAgent() != canCreatePersonalAgent) {
				entry.setCanCreatePersonalAgent(canCreatePersonalAgent);
				log(String.format(">> ACLEntry: canCreatePersonalAgent (%b)", canCreatePersonalAgent));
				toSave = true;
			}

			// 7. canCreatePersonalFolder
			boolean canCreatePersonalFolder = obj.containsKey("canCreatePersonalFolder") && (Boolean) obj.get("canCreatePersonalFolder");
			if (entry.isCanCreatePersonalFolder() != canCreatePersonalFolder) {
				entry.setCanCreatePersonalFolder(canCreatePersonalFolder);
				log(String.format(">> ACLEntry: canCreatePersonalFolder (%b)", canCreatePersonalFolder));
				toSave = true;
			}

			// 8. canCreateSharedFolder
			boolean canCreateSharedFolder = obj.containsKey("canCreateSharedFolder") && (Boolean) obj.get("canCreateSharedFolder");
			if (entry.isCanCreateSharedFolder() != canCreateSharedFolder) {
				entry.setCanCreateSharedFolder(canCreateSharedFolder);
				log(String.format("> ACL: entry canCreateSharedFolder (%b)", canCreateSharedFolder));
				toSave = true;
			}

			// 9. canCreateLSOrJavaAgent
			boolean canCreateLSOrJavaAgent = obj.containsKey("canCreateLSOrJavaAgent") && (Boolean) obj.get("canCreateLSOrJavaAgent");
			if (entry.isCanCreateLSOrJavaAgent() != canCreateLSOrJavaAgent) {
				entry.setCanCreateLSOrJavaAgent(canCreateLSOrJavaAgent);
				log(String.format(">> ACLEntry: canCreateLSOrJavaAgent (%b)", canCreateLSOrJavaAgent));
				toSave = true;
			}

			// 10. isPublicReader
			boolean isPublicReader = obj.containsKey("isPublicReader") && (Boolean) obj.get("isPublicReader");
			if (entry.isPublicReader() != isPublicReader) {
				entry.setPublicReader(isPublicReader);
				log(String.format(">> ACLEntry: isPublicReader (%b)", isPublicReader));
				toSave = true;
			}

			// 11. isPublicWriter
			boolean isPublicWriter = obj.containsKey("isPublicWriter") && (Boolean) obj.get("isPublicWriter");
			if (entry.isPublicWriter() != isPublicWriter) {
				entry.setPublicWriter(isPublicWriter);
				log(String.format(">> ACLEntry: isPublicWriter (%b)", isPublicWriter));
				toSave = true;
			}

			// 12. canReplicateOrCopyDocuments
			boolean canReplicateOrCopyDocuments = obj.containsKey("canReplicateOrCopyDocuments") && (Boolean) obj.get("canReplicateOrCopyDocuments");
			if (entry.isCanReplicateOrCopyDocuments() != canReplicateOrCopyDocuments) {
				entry.setCanReplicateOrCopyDocuments(canReplicateOrCopyDocuments);
				log(String.format(">> ACLEntry: canReplicateOrCopyDocuments (%b)", canReplicateOrCopyDocuments));
				toSave = true;
			}

			// 13. roles
			if (obj.containsKey("roles")) {
				Vector<?> aclRoles = acl.getRoles();
				JSONArray roles = (JSONArray) obj.get("roles");
				for(int i=0; i<roles.size(); i++) {
					String role = String.format("[%s]", (String) roles.get(i));

					if (aclRoles.contains(role) && !entry.isRoleEnabled(role)) {
						entry.enableRole(role);
						log(String.format(">> ACLEntry: enableRole (%s)", role));
						toSave = true;
					}
				}
			}
		} catch (Exception e) {
			log(e);
		}

		return toSave;
	}

	private void parseDocuments(Database database, JSONArray array) {
		if (array == null) return;

		for(int i=0; i<array.size(); i++) {
			JSONObject doc = (JSONObject) array.get(i);

			String action = (String) doc.get("action");
			boolean computeWithForm = doc.containsKey("computeWithForm") && (Boolean) doc.get("computeWithForm");
			if ("create".equalsIgnoreCase(action)) {
				createDocuments(database, doc, computeWithForm);
			}
			else if("export".equalsIgnoreCase(action)) {
				exportDocuments(database, doc);
			}
			else {
				updateDocuments(database, doc, computeWithForm);
			}
		}
	}

	private void exportDocuments(Database database, JSONObject json) {
		// search formula
		if (!json.containsKey("search")) {
			log("> documents.search - not found");
			return;
		};
		JSONObject search = (JSONObject) json.get("search");

		// evaluate - content of file
		if (!json.containsKey("evaluate")) {
			log("> documents.evaluate - not found");
			return;
		};
		String evaluate = (String) json.get("evaluate");
		String filePath = (String) json.get("filePath");
		try {
			if (!search.containsKey("formula")) {
				log("> documents.search.formula - not found");
				return;
			};

			String formula = (String) search.get("formula");
			Long max = search.containsKey("max") ? (Long) search.get("max") : 0;

			DocumentCollection col = database.search(formula, null, max.intValue());
			log(String.format("> %s found: %d", formula, col.getCount()));
			if (col.getCount() == 0) return;

			StringBuilder sb = new StringBuilder();

			Document doc = col.getFirstDocument();
			while (doc != null) {
				Vector<?> content = m_session.evaluate(evaluate, doc);

				sb.append(content.get(0));
				doc = col.getNextDocument();
			}

			FileUtils.writeFile(new File(filePath), sb.toString());
		} catch (NotesException e) {
			log(e);
		}
	}

	private void createDocuments(Database database, JSONObject json, boolean computeWithForm) {
		JSONObject items = (JSONObject) json.get("items");
		String evaluate = (String) json.get("evaluate");
		Document doc = null;
		try {
			doc = database.createDocument();
			updateDocument(doc, evaluate, items, computeWithForm);
		} catch (NotesException e) {
			log(e);
		}
	}

	private void updateDocuments(Database database, JSONObject json, boolean computeWithForm) {
		if (!json.containsKey("search")) {
			log("> documents.search - not found");
			return;
		};

		JSONObject items = (JSONObject) json.get("items");
		String evaluate = (String) json.get("evaluate");
		JSONObject search = (JSONObject) json.get("search");
		try {
			if (!search.containsKey("formula")) {
				log("> documents.search.formula - not found");
				return;
			};

			String formula = (String) search.get("formula");
			Long max = search.containsKey("max") ? (Long) search.get("max") : 0;

			DocumentCollection col = database.search(formula, null, max.intValue());
			log(String.format("> %s found: %d", formula, col.getCount()));
			if (col.getCount() == 0) return;

			Document doc = col.getFirstDocument();
			while (doc != null) {
				updateDocument(doc, evaluate, items, computeWithForm);
				doc = col.getNextDocument();
			}
		} catch (NotesException e) {
			log(e);
		}
	}

	private void updateDocument(Document doc, String evaluate, JSONObject items, boolean computeWithForm) throws NotesException {
		if (evaluate != null) {
			m_session.evaluate(evaluate, doc);
		}

		if (items != null) {
			@SuppressWarnings("unchecked")
			Set<Map.Entry<String, Object>> entries = items.entrySet();
			for (Map.Entry<String, Object> entry : entries) {
				String name = entry.getKey();
				Object value = entry.getValue();

				if (value instanceof JSONArray) {
					JSONArray ja = (JSONArray) value;
					Vector<Object> v = new Vector<Object>();
					Collections.addAll(v, ja.toArray());
					doc.replaceItemValue(name, v);
				} else {
					doc.replaceItemValue(name, value);
				}
			}	
		}

		if (computeWithForm) {
			doc.computeWithForm(true, false);
		}
		doc.save();
	}

	private Database createDatabaseFromTemplate(String filePath, String title, String templatePath) {
		Database database = null;
		try {
			database = m_session.getDatabase(null, filePath, false);
			if (database != null && database.isOpen()) {
				log(String.format("> %s - already exists; skip creating;", database.getFilePath()));
			}
			else {
				log(filePath + " - attempt to create based on template: " + templatePath);
				Database template = m_session.getDatabase(null, templatePath);
				if (!template.isOpen()) {
					log(String.format("> %s  - template not found", templatePath));
					return null;
				}
				database = template.createFromTemplate(null, filePath, true);
				database.setTitle(title);
				log(String.format("> %s - has been created", database.getFilePath()));
			}

			if (!database.isOpen()) {
				database.open();
			}
		} catch (NotesException e) {
			log(e);
		}

		return database;
	}

	private Database createDatabaseReplica(String filePath, String replicaServer, String replicaPath) {
		Database database = null;
		try {
			database = m_session.getDatabase(null, filePath, false);
			if (database != null && database.isOpen()) {
				log(database.getFilePath() + " - already exists; skip creating;");
			}
			else {
				Database replicaDb = m_session.getDatabase(replicaServer, replicaPath);
				if (replicaDb == null) {
					log(replicaServer + "!!" + replicaPath + " - replica not found");
					return null;
				}

				database = replicaDb.createReplica(null, filePath);
				log(database.getFilePath() + " - has been created");
			}
		} catch (NotesException e) {
			log(e);
		}

		return database;
	}

	public StringBuilder getLogData() {
		return m_logBuilder;
	}

	private void log(Exception e) {
		if (m_logBuilder == null) {
			m_logBuilder = new StringBuilder();
		}

		e.printStackTrace();
		m_logger.severe(e);

		String message = e.getLocalizedMessage();
		if (message == null || message.isEmpty()) {
			message = "an undefined exception was thrown";
		}

		m_logBuilder.append(message);
		m_logBuilder.append(System.getProperty("line.separator"));	
	}

	private void log(Object o) {
		if (m_logBuilder == null) {
			m_logBuilder = new StringBuilder();
		}

		System.out.println(o.toString());
		m_logBuilder.append(o.toString());
		m_logBuilder.append(System.getProperty("line.separator"));
	}

}
