import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import net.prominic.genesis.EventActivate;
import net.prominic.genesis.EventCatalogReport;
import net.prominic.genesis.EventRunJSON;
import net.prominic.genesis.EventUpdate;
import net.prominic.genesis.JSONRules;
import net.prominic.genesis.ProgramConfig;
import net.prominic.gja_v085.GConfig;
import net.prominic.gja_v085.JavaServerAddinGenesis;
import net.prominic.utils.GenesisUtils;
import net.prominic.utils.HTTP;

public class Genesis extends JavaServerAddinGenesis {
	private String m_catalog = "";
	private Database m_ab = null;

	public Genesis(String[] args) {
		super(args);
	}

	public Genesis() {
		super();
	}

	@Override
	protected String getJavaAddinVersion() {
		return "1.0.0";
	}

	@Override
	protected String getJavaAddinDate() {
		return "2026-02-13";
	}

	@Override
	protected boolean runNotesAfterInitialize() {
		if (args != null && args.length > 0) {
			m_catalog = args[0];
			if ("dev".equals(m_catalog)) {
				m_catalog = "https://domino-1.dmytro.cloud/gc.nsf";
			}
			if ("appstore".equals(m_catalog)) {
				m_catalog = "https://appstore.dominogenesis.com/gc.nsf";
			}
		} else {
			m_catalog = "https://appstore.dominogenesis.com/gc.nsf";
		}

		// check if connection could be established
		if (!check(m_catalog, "")) {
			logWarning("connection (*FAILED*) with: " + m_catalog);
		}

		// Open Address Book (names.nsf) for program documents
		try {
			m_ab = m_session.getDatabase(null, "names.nsf");
			if (m_ab == null || !m_ab.isOpen()) {
				logSevere("Failed to open names.nsf - unloading Genesis");
				return false;
			}
		} catch (NotesException e) {
			logSevere("Failed to open names.nsf: " + e.getMessage() + " - unloading Genesis");
			return false;
		}

		// Update program documents
		ProgramConfig pc = new ProgramConfig(this.getJavaAddinName(), this.args, m_logger);
		pc.setState(m_ab, ProgramConfig.LOAD);		// set program documents in LOAD state

		// event to read instruction from catalog
		String server;
		try {
			server = m_session.getServerName();
		} catch (NotesException e) {
			server = "n/a";
		}

		try {
			EventCatalogReport eventCatalogReport = new EventCatalogReport("CatalogReport", 86400, true, m_logger);
			eventCatalogReport.Catalog = m_catalog;
			eventCatalogReport.Server = URLEncoder.encode(server, "UTF-8");
			eventCatalogReport.JavaAddinRoot = JAVA_ADDIN_ROOT;
			eventCatalogReport.JavaAddinConfig = CONFIG_FILE_NAME; 
			this.eventsAdd(eventCatalogReport);

			EventActivate eventActivate = new EventActivate("Activate", 14400, true, m_logger);
			eventActivate.session = m_session;
			eventActivate.JavaAddinRoot = JAVA_ADDIN_ROOT;
			eventActivate.JavaAddinConfig = CONFIG_FILE_NAME;
			eventActivate.JavaAddinLive = LIVE_FILE_NAME;
			this.eventsAdd(eventActivate);

			EventUpdate eventUpdate = new EventUpdate("Update", 7200, true, m_logger);
			eventUpdate.Catalog = m_catalog;
			eventUpdate.ConfigFilePath = this.m_javaAddinConfig; 
			eventUpdate.CommandFilePath = this.m_javaAddinCommand;
			this.eventsAdd(eventUpdate);

			EventRunJSON eventRunJSON = new EventRunJSON("RunJSON", 1, true, m_logger);
			eventRunJSON.session = m_session;
			eventRunJSON.Catalog = m_catalog;
			eventRunJSON.JavaAddinConfig = this.m_javaAddinConfig;
			eventRunJSON.JavaAddinCommand = this.m_javaAddinCommand;
			eventRunJSON.JavaAddinJSON = this.m_javaAddinFolder + File.separator + "json";
			eventRunJSON.JavaAddinJSONResponse = this.m_javaAddinFolder + File.separator + "jsonresponse";
			this.eventsAdd(eventRunJSON);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

		return true;
	}

	/*
	 * test connection with Domino App Catalog (dac.nsf)
	 */
	private boolean check(String host, String secret) {
		StringBuilder buf = new StringBuilder();
		try {
			String url = host.concat("/check?openagent");
			if (!secret.isEmpty()) {
				url += "&secret="+secret;
			}
			buf = HTTP.get(url);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return buf.toString().equals("OK");
	}

	protected boolean resolveMessageQueueState(String cmd) {
		boolean flag = super.resolveMessageQueueState(cmd);
		if (flag)
			return true;

		boolean res = executeCmd(m_catalog, "", cmd);
		return res;
	}
	
	private boolean executeCmd(String host, String secret, String cmd) {
		if (secret.equals("-")) {
			secret = "";
		}

		if ("check".equals(cmd)) {
			if (check(host, secret)) {
				logMessage("OK to connect with: " + host);
			} else {
				logWarning("*FAILED* to connect with: " + host);
			}
		} else if ("list".equals(cmd)) {
			showList(host, secret);
		} else if ("state".equals(cmd)) {
			showState();
		} else if ("MyAccountDominoPerformanceLogging".equals(cmd)) {
			MyAccountDominoPerformanceLogging();
		} else if (cmd.startsWith("sign")) {
			sign(cmd);
		} else if (cmd.startsWith("origin")) {
			origin(cmd);
		} else if (cmd.startsWith("install")) {
			install(host, secret, cmd);
		} else if (cmd.startsWith("update")) {
			update(host, secret, cmd);
		} else if (cmd.startsWith("runjson")) {
			runjson(cmd);
		} else {
			logMessage("Command is not recognized (use -h or help to get details)");
			return false;
		}

		return true;
	}

	private static final String[] ALLOWED_HOSTS = {
		"appstore.dominogenesis.com",
		"domino-1.dmytro.cloud"
	};

	private boolean isHostAllowed(String host) {
		try {
			java.net.URL url = new java.net.URL(host);
			String hostname = url.getHost().toLowerCase();
			for (String allowed : ALLOWED_HOSTS) {
				if (hostname.equals(allowed) || hostname.endsWith("." + allowed)) {
					return true;
				}
			}
		} catch (java.net.MalformedURLException e) {
			return false;
		}
		return false;
	}

	private void origin(String cmd) {
		// validate command
		String[] parts = cmd.split("\\s+");
		if (parts.length < 4) {
			logMessage("Command is not recognized (use -h or help to get details)");
			return;
		}

		// get addin name and it's JSON
		String host = parts[1];
		String secret = parts[2];

		// validate host against whitelist to prevent SSRF
		if (!isHostAllowed(host)) {
			logMessage("Host not allowed: " + host);
			logMessage("Allowed hosts: " + Arrays.toString(ALLOWED_HOSTS));
			return;
		}

		// install app by id
		String buf = " " + secret + " ";
		String newCmd = cmd.substring(cmd.indexOf(buf) + buf.length());

		executeCmd(host, secret, newCmd);
	}

	private void runjson(String cmd) {
		// validate command
		String[] parts = cmd.split("\\s+");
		if (parts.length < 2) {
			logMessage("Command is not recognized (use -h or help to get details)");
			logMessage("runjson command should have <filepath> as a parameter");
			return;
		}

		// get filepath to json
		String filepath = parts[1];
		FileReader fr = null;
		try {
			fr = new FileReader(filepath);

			String configPath = JAVA_ADDIN_ROOT + File.separator + "Genesis" + File.separator + CONFIG_FILE_NAME;
			JSONRules rules = new JSONRules(m_session, this.m_catalog, configPath, this.m_javaAddinCommand, m_logger);
			boolean res = rules.execute(fr);
			if (!res) {
				logMessage("The json file can't be executed");
			}
		} catch (IOException e) {
			logMessage("JSON failed: " + e.getMessage());
		} finally {
			if (fr != null) {
				try {
					fr.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}
	}

	private void MyAccountDominoPerformanceLogging() {
		logMessage("[MyAccountDominoPerformanceLogging - started]");

		if (m_ab == null) {
			logMessage("[MyAccountDominoPerformanceLogging - skipped: names.nsf not available]");
			return;
		}

		lotus.domino.View view = null;
		Document serverDoc = null;
		try {
			String server = m_session.getServerName();
			view = m_ab.getView("($ServersLookup)");
			if (view == null) {
				logMessage("[MyAccountDominoPerformanceLogging - skipped: ($ServersLookup) view not found]");
				return;
			}
			serverDoc = view.getDocumentByKey(server, true);
			if (serverDoc == null) {
				logMessage("[MyAccountDominoPerformanceLogging - skipped: server document not found]");
				return;
			}

			HashMap<String, String> fieldsString = new HashMap<String, String>();
			fieldsString.put("HTTP_LogToFiles", "1");
			fieldsString.put("HTTP_LogToDomLog", "0");
			fieldsString.put("HTTP_AccessLogFormat", "1");
			fieldsString.put("HTTP_LogTime", "0");
			fieldsString.put("HTTP_LogFileDuration", "0");

			HashMap<String, Integer> fieldsInteger = new HashMap<String, Integer>();
			fieldsInteger.put("HTTP_MaxLogEntrySize", 10);
			fieldsInteger.put("HTTP_MaxLogFileSize", 0);

			boolean toSave = false;

			// Iterating Strings through for loop
			for (Map.Entry<String, String> set : fieldsString.entrySet()) {
				String itemName = set.getKey();

				String docValue = serverDoc.getItemValueString(itemName);
				String defValue = set.getValue();
				if (!defValue.equals(docValue)) {
					serverDoc.replaceItemValue(itemName, defValue);
					logMessage(String.format("> set %s = %s", itemName, defValue));
					toSave = true;
				}
			}

			// Iterating Integers through for loop
			for (Map.Entry<String, Integer> set : fieldsInteger.entrySet()) {
				String itemName = set.getKey();

				int docValue;
				if (serverDoc.hasItem(itemName)) {
					docValue = serverDoc.getItemValueInteger(itemName);
				}
				else {
					docValue = -1;
				}

				int defValue = set.getValue();
				if (defValue != docValue) {
					serverDoc.replaceItemValue(itemName, defValue);
					logMessage(String.format("> set %s = %d", itemName, defValue));
					toSave = true;
				}
			}

			if (toSave) {
				logMessage("> logging settings have been updated");
				serverDoc.save();
			}
			else {
				logMessage("> logging settings - OK (no updates)");
			}

		} catch (NotesException e) {
			e.printStackTrace();
		} finally {
			net.prominic.gja_v085.utils.DominoUtils.recycle(serverDoc, view);
		}

		logMessage("[MyAccountDominoPerformanceLogging - completed]");
	}

	private void sign(String cmd) {
		try {
			// validate command
			String[] parts = cmd.split("\\s+");
			if (parts.length < 2) {
				logMessage("Command is not recognized (use -h or help to get details)");
				logMessage("Sign command should have a parameter (path to Database)");
				return;
			}

			String dbFilePath = parts[1];
			Database database = m_session.getDatabase(null, dbFilePath);
			if (database == null || !database.isOpen()) {
				logMessage("database not found: " + dbFilePath);
				return;
			}

			GenesisUtils.sign(database);
		} catch (NotesException e) {
			e.printStackTrace();
		}
	}

	private void showState() {
		String[] directories = getAllAddin();
		if (directories == null) {
			logMessage("No addins found");
			return;
		}
		for(int i=0; i<directories.length; i++) {
			String configPath = JAVA_ADDIN_ROOT + File.separator + directories[i] + File.separator + CONFIG_FILE_NAME;
			File f = new File(configPath);
			String version = f.exists() ? GConfig.get(configPath, "version") : "?";
			logMessage(String.format("%s (%s)", directories[i], version));
		}		
	}
	
	private static final int MAX_UPDATE_DEPTH = 10;

	private void update(String host, String secret, String cmd) {
		update(host, secret, cmd, 0);
	}

	private void update(String host, String secret, String cmd, int depth) {
		if (depth >= MAX_UPDATE_DEPTH) {
			logMessage("Maximum update depth reached (" + MAX_UPDATE_DEPTH + "). Stopping update chain.");
			return;
		}

		try {
			// validate command
			String[] parts = cmd.split("\\s+");
			if (parts.length < 2) {
				logMessage("Command is not recognized (use -h or help to get details)");
				logMessage("Install and update command should addin as a parameter");
				return;
			}

			// get addin name and it's JSON
			String id = parts[1];
			String configPath = JAVA_ADDIN_ROOT + File.separator + id + File.separator + CONFIG_FILE_NAME;
			String version = GConfig.get(configPath, "version");

			String url = host + "/package.update?openagent&id=" + id + "&v=" + version;
			if (!secret.isEmpty()) {
				url += "&secret="+secret;
			}
			String buf = HTTP.get(url).toString();
			if (buf.length() < 50) {
				logMessage("You run latest version");
				return;
			}

			// check number of parameters
			int counter = 0;
			for (int i = 0; i < 20; i++) {
				if (buf.contains(String.format("{%d}", i))) {
					counter++;
				} else {
					i = 20;
				}
			}
			if (counter + 2 > parts.length) {
				logMessage(String.format("This installation requires %d parameters", counter));
				return;
			}

			// inject optional parameters
			for (int i = 0; i < counter; i++) {
				logMessage(String.format("Param ${%d} => %s", i, parts[i + 2]));
				buf = buf.replace(String.format("{%d}", i), parts[i + 2]);
			}

			JSONRules rules = new JSONRules(m_session, this.m_catalog, configPath, this.m_javaAddinCommand, m_logger);
			boolean res = rules.execute(buf);
			if (!res) {
				logMessage("The package could not be executed");
				return;
			}

			logInstall(m_catalog, id, res, rules.getLogData().toString());
			m_logger.info(rules.getLogData().toString());

			update(host, secret, cmd, depth + 1);
		} catch (IOException e) {
			logMessage("Install command failed: " + e.getMessage());
		}
	}

	private void install(String host, String secret, String cmd) {
		try {
			// validate command
			String[] parts = cmd.split("\\s+");
			if (parts.length < 2) {
				logMessage("Command is not recognized (use -h or help to get details)");
				return;
			}

			// id
			String id = parts[1];
			// params
			String[] params = Arrays.copyOfRange(parts, 2, parts.length);
			
			String url = host + "/package?openagent&id=" + id;
			if (!secret.isEmpty()) {
				url += "&secret="+secret;
			}
			String buf = HTTP.get(url).toString();

			// check number of parameters
			int counter = 0;
			for (int i = 0; i < 20; i++) {
				if (buf.contains(String.format("{%d}", i))) {
					counter++;
				} else {
					i = 20;
				}
			}
			if (counter > params.length) {
				logMessage(String.format("This installation requires %d parameters", counter));
				return;
			}

			// inject optional parameters
			for (int i = 0; i < counter; i++) {
				logMessage(String.format("Param ${%d} => %s", i, params[i]));
				buf = buf.replace(String.format("{%d}", i), params[i]);
			}

			String configPath = JAVA_ADDIN_ROOT + File.separator + id + File.separator + CONFIG_FILE_NAME;
			JSONRules rules = new JSONRules(m_session, host, configPath, this.m_javaAddinCommand, m_logger);
			boolean res = rules.execute(buf);

			logInstall(host, id, res, rules.getLogData().toString());
			m_logger.info(rules.getLogData().toString());

		} catch (IOException e) {
			logMessage("install command failed: " + e.getMessage());
		}
	}

	public boolean logInstall(String host, String app, boolean status, String console) {
		try {
			String server = m_session.getServerName();
			server = URLEncoder.encode(server, "UTF-8");
			app = URLEncoder.encode(app, "UTF-8");
			console = URLEncoder.encode(console, "UTF-8");

			String endpoint = host + "/log?openAgent";
			String data = String.format("&server=%s&app=%s&status=%s&console=%s", server, app, (status ? "1" : ""), console);
			StringBuilder res = HTTP.post(endpoint, data);

			return res.toString().equalsIgnoreCase("OK");
		} catch (IOException e) {
			logWarning(e);
		} catch (NotesException e) {
			logWarning(e);
		}

		return false;
	}

	/*
	 * Show all available addins in Genesis Catalog
	 */
	private void showList(String host, String secret) {
		try {
			String url = host.concat("/list?openagent");
			if (!secret.isEmpty()) {
				url += "&secret=" + secret;
			}
			StringBuilder list = HTTP.get(url);
			String[] listArr = list.toString().split("\\|");
			logMessage("*** List of App registered in Genesis Catalog ***");
			for (int i = 0; i < listArr.length; i++) {
				logMessage("   ".concat(listArr[i]));
			}
		} catch (IOException e) {
			logWarning(e.getMessage());
		}
	}

	/*
	 * Extend default Help
	 */
	protected void showHelpExt() {
		logMessage("   help             Show help information (or -h)");

		logMessage("   check            Check connection with Catalog");
		logMessage("   list             List of available Java addin in the Catalog");
		logMessage("   state            Show all installed addin (active and non active)");
		logMessage("   install <id>     Install Java addin from the Catalog");
		logMessage("   update <name>    Update Java addin from the Catalog");
		logMessage("   sign <dbpath>    Sign a database");
		logMessage("   runjson <path>   Process json config file");
		logMessage("   MyAccountDominoPerformanceLogging          Set performance logging");
		logMessage("   origin <host> <secret> <command>           Allow to use another server" );
	}

	/*
	 * Extend default Info
	 */
	protected void showInfoExt() {
		logMessage("catalog      " + this.m_catalog);
	}

	protected void termBeforeCleanup() {
		if (m_ab != null) {
			ProgramConfig pc = new ProgramConfig(this.getJavaAddinName(), this.args, m_logger);
			pc.setState(m_ab, ProgramConfig.UNLOAD);		// set program documents in UNLOAD state
			net.prominic.gja_v085.utils.DominoUtils.recycle(m_ab);
			m_ab = null;
		}
	}

}
