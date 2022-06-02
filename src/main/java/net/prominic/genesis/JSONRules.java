package net.prominic.genesis;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.util.Map;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.NotesException;
import lotus.domino.Session;
import net.prominic.gja_v20220602.GConfig;
import net.prominic.gja_v20220602.GLogger;
import net.prominic.utils.DominoUtils;
import net.prominic.utils.HTTP;

public class JSONRules {
	private Session m_session;
	private Database m_ab;
	private String m_addin;
	private String m_catalog;
	private String m_config;
	private GLogger m_logger;
	private StringBuffer m_logBuffer;

	private final String JSON_VERSION = "1.0.0";

	public JSONRules(Session session, Database ab, String addin, String catalog, String config, GLogger logger) {
		m_session = session;
		m_ab = ab;
		m_addin = addin;
		m_catalog = catalog;
		m_config = config;
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
		m_logBuffer = new StringBuffer();

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

	private boolean isValidVersionJSON(String versionjson) {
		String[] jsonArr = versionjson.split("\\.");
		String[] genesisArr = this.JSON_VERSION.split("\\.");

		for (int i=0; i<=2; i++) {
			int part1 = Integer.parseInt(jsonArr[i]);
			int part2 = Integer.parseInt(genesisArr[i]);

			if (part1 < part2) {
				log(jsonArr[i] + " < " + genesisArr[i]);
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
		File f = new File(this.m_config);
		File dir = new File(f.getParent());
		if (!dir.exists()) {
			dir.mkdirs();
		}
		
		for(Object key : config.keySet()) {
			String name = (String) key;
			String value = (String) config.get(key);
			GConfig.set(this.m_config, name, value);
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
		else if(step.containsKey("messages")) {
			doMessages((JSONArray) step.get("messages"));
		}
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

				StringBuffer appJSON = HTTP.get(m_catalog + "/package?openagent&id=" + v);
				JSONRules dependency = new JSONRules(this.m_session, this.m_ab, this.m_addin, this.m_catalog, this.m_config, this.m_logger);
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

			JSONArray documents = (JSONArray) json.get("documents");
			parseDocuments(database, documents);
		} catch (NotesException e) {
			log(e);
		}
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
			else {
				updateDocuments(database, doc, computeWithForm);
			}
		}
	}

	private void createDocuments(Database database, JSONObject json, boolean computeWithForm) {
		JSONObject items = (JSONObject) json.get("items");
		Document doc = null;
		try {
			doc = database.createDocument();
			updateDocument(doc, items, computeWithForm);
		} catch (NotesException e) {
			log(e);
		}
	}

	private void updateDocuments(Database database, JSONObject json, boolean computeWithForm) {
		JSONObject items = (JSONObject) json.get("items");
		JSONObject search = (JSONObject) json.get("search");

		try {
			String formula = (String) search.get("formula");

			Long number = (Long) (search.containsKey("number") ? search.get("number") : 0);

			DocumentCollection col = database.search(formula, null, number.intValue());
			if (col.getCount() == 0) return;

			Document doc = col.getFirstDocument();
			updateDocument(doc, items, computeWithForm);
		} catch (NotesException e) {
			log(e);
		}
	}

	private void updateDocument(Document doc, JSONObject items, boolean computeWithForm) throws NotesException {
		@SuppressWarnings("unchecked")
		Set<Map.Entry<String, Object>> entries = items.entrySet();
		for (Map.Entry<String, Object> entry : entries) {
			String name = entry.getKey();
			String value = (String) entry.getValue();
			doc.replaceItemValue(name, this.m_session.evaluate(value));
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
				log(database.getFilePath() + " - already exists; skip creating;");
			}
			else {
				log(filePath + " - attempt to create based on template: " + templatePath);
				Database template = m_session.getDatabase(null, templatePath);
				if (!template.isOpen()) {
					log(templatePath + " - template not found");
					return null;
				}
				database = template.createFromTemplate(null, filePath, true);
				database.setTitle(title);
				log(database.getFilePath() + " - has been created");
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

	public StringBuffer getLogBuffer() {
		return m_logBuffer;
	}

	private void log(Exception e) {
		e.printStackTrace();
		m_logBuffer.append(e.getLocalizedMessage());
		m_logBuffer.append(System.getProperty("line.separator"));
	}

	private void log(Object o) {
		System.out.println(o.toString());
		m_logBuffer.append(o.toString());
		m_logBuffer.append(System.getProperty("line.separator"));
	}

}
