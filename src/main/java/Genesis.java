import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import lotus.domino.NotesException;
import net.prominic.genesis.JSONRules;
import net.prominic.gja_v20220413.JavaServerAddinGenesis;
import net.prominic.utils.HTTP;

public class Genesis extends JavaServerAddinGenesis {
	private String				m_catalog					= "";

	@Override
	protected String getJavaAddinVersion() {
		return "0.6.8";
	}

	@Override
	protected String getJavaAddinDate() {
		return "2022-04-13 15:05";
	}

	@Override
	protected void runNotesBeforeInitialize() {
		if (args != null && args.length > 0) {
			m_catalog = args[0];
			if ("dev".equals(m_catalog)) {
				m_catalog = "https://domino-1.dmytro.cloud/gc.nsf";
			}
		}
		else {
			m_catalog = "https://domino-1.dmytro.cloud/gc.nsf";
		}

		// check if connection could be established
		if (!check()) {
			logWarning("connection (*FAILED*) with: " + m_catalog);
		}
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
		if (flag) return true;

		if ("check".equals(cmd)) {
			if (check()) {
				logMessage("OK to connect with with: " + m_catalog);
			}
			else {
				logWarning("*FAILED* to connect with: " + m_catalog);
			}
		}
		else if ("list".equals(cmd)) {
			showList();
		}
		else if ("state".equals(cmd)) {
			showState();
		}
		else if(cmd.startsWith("install")) {
			install(cmd);
		}
		else {
			logMessage("Command is not recognized (use -h or help to get details)");
			return false;
		}

		return true;
	}

	private void showState() {
		String[] addinName = this.getAllAddin();

		for(int i=0; i<addinName.length; i++) {
			String javaAddin = JAVA_ADDIN_ROOT + File.separator + addinName[i];

			boolean status = isLive(javaAddin);
			logMessage(addinName[i] + " : " + String.valueOf(status));
		}
	}

	private void install(String cmd) {
		try {
			// validate command
			String[] optArr = cmd.split("\\s+");
			if (optArr.length != 2) {
				logMessage("Command is not recognized (use -h or help to get details)");
				logMessage("Install and update command should addin as a parameter");
				return;
			}

			// find addin in catalog
			String app = optArr[1];
			StringBuffer buf = HTTP.get(m_catalog + "/app?openagent&name=" + app);

			JSONRules rules = new JSONRules(m_session, this.m_ab, this.getJavaAddinName(), this.m_catalog, this.m_logger);
			boolean res = rules.execute(buf.toString());

			logInstall(app, JSONRules.VERSION, res, rules.getLogBuffer().toString());
			m_logger.info(rules.getLogBuffer().toString());
 
		} catch (IOException e) {
			logMessage("Install command failed: " + e.getMessage());
		}
	}

	public boolean logInstall(String app, String version, boolean status, String console) {
		try {
			String server = m_session.getServerName();
			server = URLEncoder.encode(server, "UTF-8");
			app = URLEncoder.encode(app, "UTF-8");
			version = URLEncoder.encode(version, "UTF-8");
			console = URLEncoder.encode(console, "UTF-8");

			String endpoint = m_catalog + "/log?openAgent";
			StringBuffer res = HTTP.post(endpoint, "&server=" + server + "&app=" + app + "&version=" + version + "&status=" + (status ? "1" : "") + "&console=" + console);

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
			for(int i = 0; i < listArr.length; i++) {
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
