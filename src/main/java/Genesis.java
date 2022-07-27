import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import lotus.domino.Database;
import lotus.domino.DateTime;
import lotus.domino.NotesException;
import net.prominic.genesis.EventActivate;
import net.prominic.genesis.EventCatalogReport;
import net.prominic.genesis.EventMyAccountDominoPerformanceLogging;
import net.prominic.genesis.EventUpdate;
import net.prominic.genesis.JSONRules;
import net.prominic.genesis.ProgramConfig;
import net.prominic.gja_v082.GConfig;
import net.prominic.gja_v082.JavaServerAddinGenesis;
import net.prominic.utils.DominoUtils;
import net.prominic.utils.HTTP;

public class Genesis extends JavaServerAddinGenesis {
	private String m_catalog = "";

	public Genesis(String[] args) {
		super(args);
	}

	public Genesis() {
		super();
	}

	@Override
	protected String getJavaAddinVersion() {
		return "0.6.14 (logging)";
	}

	@Override
	protected String getJavaAddinDate() {
		return "2022-07-27 02:30";
	}

	/*
	private void fixDominoMeter(String server) {
		try {
			StringBuilder debug = new StringBuilder();
			String GJA_DominoMeter = m_session.getEnvironmentString("GJA_DominoMeter", true);
			if (GJA_DominoMeter.isEmpty()) return;
			debug.append("\n > GJA_DominoMeter = " + GJA_DominoMeter);

			String userClasses = m_session.getEnvironmentString("JAVAUSERCLASSES", true);
			if (!userClasses.contains("DominoMeter")) return;
			debug.append("\n > JAVAUSERCLASSES = " + userClasses);

			String checkRunjava = GConfig.get("JavaAddin/DominoMeter/config.txt", "runjava");
			if (checkRunjava==null) return;
			debug.append("\n > checkRunjava = " + checkRunjava);

			logMessage("Fix DominoMeter - will be used to correct DominoMeter");
			debug.append("\n > FixDominoMeter is applied");

			// 1. program documents - cleanup
			View view = m_ab.getView("($Programs)");
			view.refresh();
			DocumentCollection col = view.getAllDocumentsByKey(server, true);
			Document doc = col.getFirstDocument();
			String runjava = null;
			while (doc != null) {
				Document nextDoc = col.getNextDocument(doc);

				String CmdLine = doc.getItemValueString("CmdLine");
				if (CmdLine.toLowerCase().startsWith("dominometer")) {
					doc.remove(true);
					if (runjava==null) {
						runjava = CmdLine;
					}
				}

				doc = nextDoc;
			}

			// 2. notes.ini cleanup
			String platform = m_session.getPlatform();
			String notesIniSep = platform.contains("Windows") ? ";" : ":";

			String[] userClassesArr = userClasses.split("\\" + notesIniSep);
			for (int i = 0; i < userClassesArr.length; i++) {
				if (userClassesArr[i].contains("DominoMeter")) {
					userClasses = userClasses.replace(userClassesArr[i] + notesIniSep, "");
					userClasses = userClasses.replace(userClassesArr[i], "");
					i = userClassesArr.length;
				}
			}

			m_session.setEnvironmentVar("JAVAUSERCLASSES", userClasses, true);
			m_session.setEnvironmentVar("GJA_DominoMeter", "JavaAddin/DominoMeter/DominoMeter-118.jar", true);

			debug.append(" > FixDominoMeter completed");
			logInstall("\nFixDominoMeter", true, debug.toString());

			restartAll(true);
		} catch (NotesException e) {
			e.printStackTrace();
		}
	}
	 */

	@Override
	protected boolean runNotesAfterInitialize() {
		if (args != null && args.length > 0) {
			m_catalog = args[0];
			if ("dev".equals(m_catalog)) {
				m_catalog = "https://domino-1.dmytro.cloud/gc.nsf";
			}
		} else {
			m_catalog = "https://domino-1.dmytro.cloud/gc.nsf";
		}

		// check if connection could be established
		if (!check()) {
			logWarning("connection (*FAILED*) with: " + m_catalog);
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
			eventActivate.JavaAddinRoot = JAVA_ADDIN_ROOT;
			eventActivate.JavaAddinConfig = CONFIG_FILE_NAME;
			eventActivate.JavaAddinLive = LIVE_FILE_NAME;
			this.eventsAdd(eventActivate);

			EventUpdate eventUpdate = new EventUpdate("Update", 7200, true, m_logger);
			eventUpdate.Catalog = m_catalog;
			eventUpdate.ConfigFilePath = this.m_javaAddinConfig; 
			eventUpdate.CommandFilePath = this.m_javaAddinCommand;
			this.eventsAdd(eventUpdate);
			
			EventMyAccountDominoPerformanceLogging eventLogging = new EventMyAccountDominoPerformanceLogging("Logging", 86400, true, m_logger);
			eventLogging.ab = this.m_ab;
			eventLogging.session = this.m_session;
			this.eventsAdd(eventLogging);
			
			//fixDominoMeter(server);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

		return true;
	}

	/*
	 * test connection with Domino App Catalog (dac.nsf)
	 */
	private boolean check() {
		StringBuilder buf = new StringBuilder();
		try {
			String url = m_catalog.concat("/check?openagent");
			buf = HTTP.get(url);
		} catch (IOException e) {
			logSevere(e);
		}
		return buf.toString().equals("OK");
	}

	protected boolean resolveMessageQueueState(String cmd) {
		boolean flag = super.resolveMessageQueueState(cmd);
		if (flag)
			return true;

		if ("check".equals(cmd)) {
			if (check()) {
				logMessage("OK to connect with with: " + m_catalog);
			} else {
				logWarning("*FAILED* to connect with: " + m_catalog);
			}
		} else if ("list".equals(cmd)) {
			showList();
		} else if ("state".equals(cmd)) {
			showState();
		} else if ("MyAccountDominoPerformanceLogging".equals(cmd)) {
			MyAccountDominoPerformanceLogging();
		} else if (cmd.startsWith("crosscertify") || cmd.startsWith("cc ")) {
			crossCertify(cmd);
		} else if (cmd.startsWith("sign")) {
			sign(cmd);
		} else if (cmd.startsWith("install")) {
			install(cmd);
		} else if (cmd.startsWith("update")) {
			update(cmd);
		} else {
			logMessage("Command is not recognized (use -h or help to get details)");
			return false;
		}

		return true;
	}

	private void MyAccountDominoPerformanceLogging() {
		EventMyAccountDominoPerformanceLogging eventLogging = new EventMyAccountDominoPerformanceLogging("Logging", 86400, true, m_logger);
		eventLogging.ab = this.m_ab;
		eventLogging.session = this.m_session;
		eventLogging.run();
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

			DominoUtils.sign(database);
		} catch (NotesException e) {
			e.printStackTrace();
		}
	}

	private void crossCertify(String cmd) {
		try {
			// validate command
			String[] parts = cmd.split("\\s+");
			if (parts.length < 6) {
				logMessage("crosscertify (or cc) command should have at least 5 parameters");
				logMessage("tell genesis cc <1.RegistrationServer> <2.cert.id> <3.password> <4.Expiration:dd-mm-yyyy> <5.user.id>");
				return;
			}

			String registrationServer = parts[1];
			String certID = parts[2];
			String certPassword = parts[3];
			String expiration = parts[4];	// dd-mm-yyyy
			String userID = parts[5];

			DateFormat formatter = new SimpleDateFormat("dd-mm-yyyy");
			Date date = formatter.parse(expiration);
			DateTime expirationDate = m_session.createDateTime(date);

			DominoUtils.crossCertify(m_session, registrationServer, certID, certPassword, expirationDate, userID);
		} catch (NotesException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	private void showState() {
		String[] directories = getAllAddin();
		for(int i=0; i<directories.length; i++) {
			String configPath = JAVA_ADDIN_ROOT + File.separator + directories[i] + File.separator + CONFIG_FILE_NAME;
			File f = new File(configPath);
			String version = f.exists() ? GConfig.get(configPath, "version") : "?";
			logMessage(String.format("%s (%s)", directories[i], version));
		}		
	}

	private void update(String cmd) {
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

			String buf = HTTP.get(m_catalog + "/package.update?openagent&id=" + id + "&v=" + version).toString();
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

			logInstall(id, res, rules.getLogData().toString());
			m_logger.info(rules.getLogData().toString());

			update(cmd);
		} catch (IOException e) {
			logMessage("Install command failed: " + e.getMessage());
		}
	}

	private void install(String cmd) {
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

			String buf = HTTP.get(m_catalog + "/package?openagent&id=" + id).toString();

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

			String configPath = JAVA_ADDIN_ROOT + File.separator + id + File.separator + CONFIG_FILE_NAME;
			JSONRules rules = new JSONRules(m_session, this.m_catalog, configPath, this.m_javaAddinCommand, m_logger);
			boolean res = rules.execute(buf);

			logInstall(id, res, rules.getLogData().toString());
			m_logger.info(rules.getLogData().toString());

		} catch (IOException e) {
			logMessage("Install command failed: " + e.getMessage());
		}
	}

	public boolean logInstall(String app, boolean status, String console) {
		try {
			String server = m_session.getServerName();
			server = URLEncoder.encode(server, "UTF-8");
			app = URLEncoder.encode(app, "UTF-8");
			console = URLEncoder.encode(console, "UTF-8");

			String endpoint = m_catalog + "/log?openAgent";
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
	private void showList() {
		try {
			StringBuilder list = HTTP.get(m_catalog.concat("/list?openagent"));
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
		logMessage("   check            Check connection with Catalog");
		logMessage("   list             List of available Java addin in the Catalog");
		logMessage("   state            Show all installed addin (active and non active)");
		logMessage("   install <name>   Install Java addin from the Catalog");
		logMessage("   update <name>    Update Java addin from the Catalog");
		logMessage("   sign <dbpath>    Sign a database");
		logMessage("   MyAccountDominoPerformanceLogging    set performance logging");
		//logMessage("   cc   help   	    Cross certify an ID");
		
	}

	/*
	 * Extend default Info
	 */
	protected void showInfoExt() {
		logMessage("catalog      " + this.m_catalog);
	}

	protected void termBeforeAB() {
		ProgramConfig pc = new ProgramConfig(this.getJavaAddinName(), this.args, m_logger);
		pc.setState(m_ab, ProgramConfig.UNLOAD);		// set program documents in UNLOAD state
	}

}
