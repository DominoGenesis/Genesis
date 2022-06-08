import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import lotus.domino.NotesException;
import net.prominic.genesis.JSONRules;
import net.prominic.gja_v080.GConfig;
import net.prominic.gja_v080.JavaServerAddinGenesis;
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
		return "0.6.9";
	}

	@Override
	protected String getJavaAddinDate() {
		return "2022-06-08 12:45";
	}

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
			EventCatalogReport eventCatalogSend = new EventCatalogReport("CatalogSend", 3600, true, m_logger);
			eventCatalogSend.Catalog = m_catalog;
			eventCatalogSend.Server = URLEncoder.encode(server, "UTF-8");
			eventCatalogSend.JavaAddinRoot = JAVA_ADDIN_ROOT;
			eventCatalogSend.JavaAddinConfig = CONFIG_FILE_NAME; 
			this.eventsAdd(eventCatalogSend);
			
			EventActiveAddin eventActiveAddin = new EventActiveAddin("Active Addin", 7200, true, m_logger);
			eventActiveAddin.JavaAddinRoot = JAVA_ADDIN_ROOT;
			eventActiveAddin.JavaAddinConfig = CONFIG_FILE_NAME;
			eventActiveAddin.JavaAddinLive = LIVE_FILE_NAME;
			this.eventsAdd(eventActiveAddin);
			
			EventUpdate eventUpdate = new EventUpdate("Update", 7200, true, m_logger);
			eventUpdate.Catalog = m_catalog;
			eventUpdate.ConfigFilePath = this.m_javaAddinConfig; 
			eventUpdate.CommandFilePath = this.m_javaAddinCommand;
			this.eventsAdd(eventUpdate);			
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

	private void showState() {
		String[] addinName = this.getAllAddin();

		for (int i = 0; i < addinName.length; i++) {
			String version = GConfig.get(addinName[i], "version");
			logMessage(String.format("%s (%s)", addinName[i], version));
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
