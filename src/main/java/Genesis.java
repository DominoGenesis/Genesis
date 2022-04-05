import java.io.File;

import java.io.IOException;
import java.nio.file.Files;

import net.prominic.gja_v20220405.JavaServerAddinGenesis;
import net.prominic.install.JSONRules;
import net.prominic.log.Logger;
import net.prominic.utils.HTTP;

public class Genesis extends JavaServerAddinGenesis {
	private String				m_catalog					= "";
	private Logger 				m_logger					= null;
	
	@Override
	protected String getJavaAddinVersion() {
		return "0.6.5";
	}

	@Override
	protected String getJavaAddinDate() {
		return "2022-04-05 18:45";
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
			logMessage("connection (*FAILED*) with: " + m_catalog);
		}
	}
	
	@Override
	protected void runNotesBeforeListen() {
		m_logger = new Logger(m_session, m_catalog);
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
			e.printStackTrace();
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
				logMessage("*FAILED* to connect with: " + m_catalog);
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
		else if(cmd.startsWith("cleanup")) {
			cleanup();
		}
		else {
			logMessage("Command is not recognized (use -h or help to get details)");
			return false;
		}

		return true;
	}

	/*
	 * Review all installed addins and delete folders if addin is uninstalled
	 */
	private void cleanup() {
		String[] addinName = this.getAllAddin();

		for(int i=0; i<addinName.length; i++) {
			String javaAddin = JAVA_ADDIN_ROOT + File.separator + addinName[i];
			boolean status = isLive(javaAddin);
			if (!status) {
				File f = new File(javaAddin + File.separator + COMMAND_FILE_NAME);
				String cmd = this.readFile(f);
				if (cmd.equalsIgnoreCase("uninstall")) {
					File dir = new File(javaAddin);
					deleteDir(dir);
					logMessage("deleted: " + javaAddin);
				}
			}
		}
	}
	
	/*
	 * Delete directory with files
	 */
	void deleteDir(File file) {
	    File[] contents = file.listFiles();
	    if (contents != null) {
	        for (File f : contents) {
	            if (! Files.isSymbolicLink(f.toPath())) {
	                deleteDir(f);
	            }
	        }
	    }
	    file.delete();
	}

	private void showState() {
		String[] addinName = this.getAllAddin();

		for(int i=0; i<addinName.length; i++) {
			String javaAddin = JAVA_ADDIN_ROOT + File.separator + addinName[i];

			boolean status = isLive(javaAddin);
			this.logMessage(addinName[i] + " : " + String.valueOf(status));
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

			JSONRules rules = new JSONRules(m_session, m_catalog, m_logger);
			boolean res = rules.execute(buf.toString());
			m_logger.logInstall(app, JSONRules.VERSION, res, rules.getLogBuffer().toString());
		} catch (IOException e) {
			logMessage("Install command failed: " + e.getMessage());
		}
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
			logMessage(e.getMessage());
		}
	}

	/*
	 * Extend default Help
	 */
	protected void showHelpExt() {
		AddInLogMessageText("   check            Check connection with Catalog");
		AddInLogMessageText("   list             List of available Java addin in the Catalog");
		AddInLogMessageText("   state            Show all installed addin (active and non active)");
		AddInLogMessageText("   install <name>   Install Java addin from the Catalog");
	}

	/*
	 * Extend default Info
	 */
	protected void showInfoExt() {
		logMessage("catalog      " + this.m_catalog);
	}

}
