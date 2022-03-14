import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import lotus.domino.NotesException;
import net.prominic.utils.HTTP;

public class Genesis extends JavaServerAddinGenesis {
	private String				catalog					= "";

	@Override
	protected String getJavaAddinVersion() {
		return "0.5.3";
	}

	@Override
	protected String getJavaAddinDate() {
		return "2022-03-14 18:45";
	}

	@Override
	protected void runNotesBeforeInitialize() {
		if (args != null && args.length > 0) {
			catalog = args[0];
			if ("dev".equals(catalog)) {
				catalog = "https://domino-1.dmytro.cloud/gc.nsf";
			}
		}
		else {
			catalog = "https://domino-1.dmytro.cloud/gc.nsf";
		}

		// check if connection could be established
		if (!check()) {
			logMessage("connection (*FAILED*) with: " + catalog);
		}
	}

	/*
	 * test connection with Domino App Catalog (dac.nsf)
	 */
	private boolean check() {
		StringBuffer buf = new StringBuffer();
		try {
			String url = catalog.concat("/check?openagent");
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
				logMessage("OK to connect with with: " + catalog);
			}
			else {
				logMessage("*FAILED* to connect with: " + catalog);
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
			String name = optArr[1];
			StringBuffer buf = HTTP.get(catalog + "/app?openagent&name=" + name);
			String[] bufArr = buf.toString().split("\\|");
			if (bufArr[0].startsWith("error")) {
				logMessage("Addin is not found, operation failed");
				return;
			}

			// TODO: should be updated once we define JSON format for catalog
			String latest = bufArr[0];
			String[] latestArr = latest.split(";");
			String version = latestArr[0];
			String url = catalog + "/" + latestArr[1];
			String fileName = latestArr[1].substring(latestArr[1].lastIndexOf("/") + 1);
			String fileNameVersion = version.replace(".", "-");
			fileName = fileName.replace(".", "-" + fileNameVersion + ".");

			// addin filename
			logMessage(fileName + " - latest version");

			// addin folder
			String addinFolderPath = JAVA_ADDIN_ROOT + File.separator + name;
			File dir = new File(addinFolderPath);

			if (!dir.exists()) {
				dir.mkdirs();
				logMessage(addinFolderPath + " --- created");
			}
			else {
				logMessage(addinFolderPath + " --- already exists. skip.");
			}

			String addinFilePath = addinFolderPath + File.separator + fileName;
			File file = new File(addinFilePath);
			if (file.exists() && file.isFile()) {
				logMessage(addinFilePath + " --- already downloaded.");
			}
			else {
				logMessage("latest version: " + url);
				logMessage(addinFilePath + " --- downloading...");
				boolean downloaded = HTTP.saveURLTo(url, addinFilePath);
				if (downloaded) {
					logMessage("> OK");
					logMessage("> " + addinFilePath);
				}
				else {
					logMessage("> FAILED (installation failed)");
					return;
				}
			}

			registerAddin(name, addinFilePath);

			restartAll(true);
		} catch (IOException e) {
			logMessage("Install command failed: " + e.getMessage());
		}
	}

	/*
	 * Register addin in notes.ini (updates notes.ini)
	 */
	private void registerAddin(String addinName, String filePath) {
		try {
			String variableName = "GJA_" + addinName;
			String userClasses = m_session.getEnvironmentString(JAVA_USER_CLASSES_EXT, true);
			if (!userClasses.contains(variableName)) {
				if (!userClasses.isEmpty()) {
					userClasses += ",";
				}
				userClasses += variableName;
				m_session.setEnvironmentVar(JAVA_USER_CLASSES_EXT, userClasses, true);
			}

			m_session.setEnvironmentVar(variableName, filePath, true);
			logMessage(addinName + " - registered successfully");
		} catch (NotesException e) {
			logMessage("Registration of the addin failed: " + e.getMessage());
		}
	}

	/*
	 * Show all available addins in Genesis Catalog
	 */
	private void showList() {
		try {
			StringBuffer list = HTTP.get(catalog.concat("/list?openagent"));
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
		AddInLogMessageText("   update <name>    Update Java addin from the Catalog");
	}

	/*
	 * Extend default Info
	 */
	protected void showInfoExt() {
		logMessage("catalog      " + this.catalog);
	}

}
