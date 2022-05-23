import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Date;

import lotus.domino.NotesException;
import net.prominic.genesis.JSONRules;
import net.prominic.gja_v20220517.JavaServerAddinGenesis;
import net.prominic.utils.HTTP;

public class Genesis extends JavaServerAddinGenesis {
	private String m_catalog = "";

	@Override
	protected String getJavaAddinVersion() {
		return "0.6.8";
	}

	@Override
	protected String getJavaAddinDate() {
		return "2022-05-23 12:45";
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

		// event to read instruction from catalog
		String server;
		try {
			server = m_session.getServerName();
		} catch (NotesException e) {
			server = "n/a";
		}

		try {
			EventCatalogReport eventCatalogSend = new EventCatalogReport("CatalogSend", 600, true, m_logger);
			eventCatalogSend.Catalog = m_catalog;
			eventCatalogSend.Server = URLEncoder.encode(server, "UTF-8");
			eventCatalogSend.JavaAddinRoot = JAVA_ADDIN_ROOT;
			this.eventsAdd(eventCatalogSend);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

		return true;
	}

	/*
	 * test connection with Domino App Catalog (dac.nsf)
	 */
	private boolean check() {
		StringBuffer buf = new StringBuffer();
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
		} else {
			logMessage("Command is not recognized (use -h or help to get details)");
			return false;
		}

		return true;
	}

	private void showState() {
		String[] addinName = this.getAllAddin();

		for (int i = 0; i < addinName.length; i++) {
			String javaAddin = JAVA_ADDIN_ROOT + File.separator + addinName[i];

			boolean status = isLive(javaAddin);
			logMessage(addinName[i] + " : " + String.valueOf(status));
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
			String app = parts[1];
			String buf = HTTP.get(m_catalog + "/app?openagent&name=" + app).toString();

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

			JSONRules rules = new JSONRules(m_session, this.m_ab, this.getJavaAddinName(), this.m_catalog,
					this.m_logger);
			boolean res = rules.execute(buf);

			logInstall(app, res, rules.getLogBuffer().toString());
			m_logger.info(rules.getLogBuffer().toString());

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
			StringBuffer res = HTTP.post(endpoint, "&server=" + server + "&app=" + app
					+ "&status=" + (status ? "1" : "") + "&console=" + console);

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
			StringBuffer list = HTTP.get(m_catalog.concat("/list?openagent"));
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

}
