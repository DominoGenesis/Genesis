import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;

import lotus.domino.NotesException;
import net.prominic.utils.HTTP;

public class Genesis extends JavaServerAddinGenesis {
	private final String 		JAVA_USER_CLASSES 		= "JAVAUSERCLASSES";

	private String				catalog					= "";

	@Override
	protected String getJavaAddinName() {
		return "Genesis";
	}

	@Override
	protected String getJavaAddinVersion() {
		return "0.4.0";
	}

	@Override
	protected String getJavaAddinDate() {
		return "2022-03-07 20:31";
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
		else if ("-l".equals(cmd) || "list".equals(cmd)) {
			showList();
		}
		else if(cmd.startsWith("install")) {
			install(cmd);
		}
		else if(cmd.startsWith("update")) {
			update(cmd);
		}
		else if(cmd.startsWith("delete")) {
			delete(cmd);
		}
		else {
			logMessage("Command is not recognized (use -h or help to get details)");
			return false;
		}

		return true;
	}

	private void delete(String cmd) {

	}

	private void update(String cmd) {

	}

	private void install(String cmd) {
		try {
			String[] optArr = cmd.split("\\s+");
			if (optArr.length != 2) {
				logMessage("Install command should have only 1 parameter (addin name)");
				return;
			}

			String name = optArr[1];
			StringBuffer buf = HTTP.get(catalog + "/app?openagent&name=" + name);
			String[] bufArr = buf.toString().split("\\|");

			if (bufArr[0].startsWith("error")) {
				logMessage(bufArr[0]);
				return;
			}

			String latest = bufArr[0];
			String[] latestArr = latest.split(";");
			String version = latestArr[0];
			String url = catalog + "/" + latestArr[1];
			String fileName = latestArr[1].substring(latestArr[1].lastIndexOf("/") + 1);

			// addin filename
			fileName = fileName.replaceAll("\\.", "-");
			logMessage("fileName: " + fileName);

			// addin folder
			String addinFolderPath = this.getAddinParentFolder() + File.separator + name;
			logMessage("addinFolderPath: " + addinFolderPath);
			File dir = new File(addinFolderPath);
			if (!dir.exists()) {
				dir.mkdirs();
				logMessage("addinFolderPath: " + addinFolderPath + " -- created !!");
			}

			String addinFilePath = addinFolderPath + File.separator + fileName;
			logMessage("addinFilePath: " + addinFilePath);
			File file = new File(addinFilePath);
			if (file.exists() && file.isFile()) {
				logMessage(addinFilePath + " --- file already eixsts");
				return;
			}

			logMessage("detected version: " + version);
			logMessage("url: " + url);
			logMessage("filename: " + fileName);
			logMessage("will be downloaded to: " + addinFilePath);
			boolean downloaded = HTTP.saveURLTo(url, addinFilePath);
			if (!downloaded) {
				logMessage("> filed (not downloaded)");
				return;
			}

			logMessage("> ok (downloaded)");

			registerAddin(addinFilePath, name);

		} catch (IOException e) {
			logMessage("Install command failed: " + e.getMessage());
		}
	}
	
	private String join(String[] arr, String sep) {
		String res = "";		
		if (arr != null && arr.length > 0) {
			for(int i = 0; i < arr.length; i++) {
				if (i > 0) {
					res += " ";
				}
				res += arr[i];
			}
		}
		return res;
	}

	private void registerAddin(String filePath, String addinName) {
		try {
			String userClasses = m_session.getEnvironmentString(JAVA_USER_CLASSES, true);
			logMessage(JAVA_USER_CLASSES + " (current) = " + userClasses);
			String NotesIniLine = "." + File.separator + filePath;

			String platform = m_session.getPlatform();
			String notesIniSep = platform.contains("Windows") ? ";" : ":";

			if (userClasses.isEmpty()) {
				userClasses = NotesIniLine;
			}
			else {
				if (userClasses.indexOf(addinName) > 0) {
					String[] userClassesArr = userClasses.split("\\" + notesIniSep);
					for (int i = 0; i < userClassesArr.length; i++) {
						if (userClassesArr[i].contains(addinName)) {
							userClassesArr[i] = NotesIniLine;
							userClasses = join(userClassesArr, notesIniSep);
							i = userClassesArr.length;
						}
					}
				}
				else {
					userClasses = userClasses + notesIniSep + NotesIniLine;
				}
			}

			m_session.setEnvironmentVar(JAVA_USER_CLASSES, userClasses, true);
			logMessage(JAVA_USER_CLASSES + " (new) set to " + userClasses);
		} catch (NotesException e) {
			logMessage("Install command failed: " + e.getMessage());
		}
	}

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

	@Override
	protected void showHelp() {
		int year = Calendar.getInstance().get(Calendar.YEAR);
		logMessage("*** Usage ***");
		AddInLogMessageText("load runjava Genesis");
		AddInLogMessageText("tell Genesis <command>");
		AddInLogMessageText("   quit             Unload Genesis");
		AddInLogMessageText("   help             Show help information (or -h)");
		AddInLogMessageText("   info             Show version and more of Genesis");
		AddInLogMessageText("   check            Check connection with Catalog");
		AddInLogMessageText("   list             List of available Java addin in the Catalog");
		AddInLogMessageText("   install <name>   Install Java addin from the Catalog");
		AddInLogMessageText("Copyright (C) Prominic.NET, Inc. 2021" + (year > 2021 ? " - " + Integer.toString(year) : ""));
		AddInLogMessageText("See https://prominic.net for more details.");
	}

	@Override
	protected void showInfo() {
		logMessage("version      " + this.getJavaAddinName());
		logMessage("date         " + this.getJavaAddinDate());
		logMessage("catalog      " + this.catalog);
		logMessage("parameters   " + Arrays.toString(this.args));
	}

}
