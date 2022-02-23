import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Calendar;

import lotus.domino.NotesException;
import lotus.domino.NotesFactory;
import lotus.domino.Session;
import lotus.notes.addins.JavaServerAddin;
import lotus.notes.internal.MessageQueue;
import net.prominic.utils.HTTP;

public class Genesis extends JavaServerAddin {
	// Constants
	private final String		JADDIN_NAME				= "Genesis";
	private final String		JADDIN_VERSION			= "0.3.0";
	private final String		JADDIN_DATE				= "2022-02-23 15:30 (file)";

	private final String 		JAVA_USER_CLASSES 		= "JAVAUSERCLASSES";

	// MessageQueue Constants
	// Message Queue name for this Addin (normally uppercase);
	// MSG_Q_PREFIX is defined in JavaServerAddin.class
	private static final int 	MQ_MAX_MSGSIZE 			= 1024;
	private final String 		qName 					= MSG_Q_PREFIX + JADDIN_NAME.toUpperCase();


	MessageQueue 				mq						= null;
	Session 					m_session				= null;

	private String[] 			args 					= null;
	private int 				dominoTaskID			= 0;
	private String				catalog					= "";
	private String				bufState				= "";

	// constructor if parameters are provided
	public Genesis(String[] args) {
		this.args = args;
	}

	public Genesis() {}

	@Override
	public void runNotes () {
		// Set the Java thread name to the class name (default would be "Thread-n")
		this.setName(JADDIN_NAME);

		// Create the status line showed in 'Show Task' console command
		this.dominoTaskID = createAddinStatusLine(this.JADDIN_NAME);

		setAddinState("Initializing");

		try {
			m_session = NotesFactory.createSession();

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

			showInfo();

			listen();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	private void listen() {
		StringBuffer qBuffer = new StringBuffer(1024);

		try {
			mq = new MessageQueue();
			int messageQueueState = mq.create(qName, 0, 0);	// use like MQCreate in API
			if (messageQueueState == MessageQueue.ERR_DUPLICATE_MQ) {
				logMessage(this.JADDIN_NAME + " task is already running");
				return;
			}

			if (messageQueueState != MessageQueue.NOERROR) {
				logMessage("Unable to create the Domino message queue");
				return;
			}

			if (mq.open(qName, 0) != MessageQueue.NOERROR) {
				logMessage("Unable to open Domino message queue");
				return;
			}

			setAddinState("Idle");
			while (this.addInRunning() && (messageQueueState != MessageQueue.ERR_MQ_QUITTING)) {
				/* gives control to other task in non preemptive os*/
				OSPreemptOccasionally();

				// check for command from console
				messageQueueState = mq.get(qBuffer, MQ_MAX_MSGSIZE, MessageQueue.MQ_WAIT_FOR_MSG, 1000);
				if (messageQueueState == MessageQueue.ERR_MQ_QUITTING) {
					return;
				}

				// check messages for Genesis
				String cmd = qBuffer.toString().trim();
				if (!cmd.isEmpty()) {
					resolveMessageQueueState(cmd);
				};
			}
		} catch(Exception e) {
			e.printStackTrace();
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

	private void resolveMessageQueueState(String cmd) {
		if ("-h".equals(cmd) || "help".equals(cmd)) {
			showHelp();
		}
		else if ("info".equals(cmd)) {
			showInfo();
		}
		else if ("check".equals(cmd)) {
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
		else if(cmd.startsWith("-i") || cmd.startsWith("install")) {
			install(cmd);
		}
		else if(cmd.startsWith("dbsigner")) {
			dbsigner(cmd);
		}
		else {
			logMessage("Command is not recognized (use -h or help to get details)");
		}
	}

	private void dbsigner(String cmd) {
		String[] optArr = cmd.split("\\s+");
		if (optArr.length != 2) {
			logMessage("there must be 2 parameters");
			return;
		}

		PrintWriter writer;
		try {
			writer = new PrintWriter("dbsigner.txt", "UTF-8");
			logMessage("wrote a command: " + optArr[1]);
			writer.println(optArr[1]);
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	// TODO: work in progress
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
			String filePath = "javaaddin" + File.separator + fileName;
			logMessage("detected version: " + version);
			logMessage("url: " + url);
			logMessage("filename: " + fileName);
			logMessage("will be downloaded to: " + filePath);
			boolean downloaded = HTTP.saveURLTo(url, filePath);
			if (!downloaded) {
				logMessage("> filed (not downloaded)");
				return;
			}

			logMessage("> ok (downloaded)");

			registerAddin(filePath);

		} catch (IOException e) {
			logMessage("Install command failed: " + e.getMessage());
		}
	}

	private void registerAddin(String filePath) {
		try {
			// 4. register new JAR in notes.ini
			// Example: JAVAUSERCLASSESEXT=.\DominoMeterAddin\DominoMeter-5.jar
			String userClasses = m_session.getEnvironmentString(JAVA_USER_CLASSES, true);
			logMessage(JAVA_USER_CLASSES + " (current) = " + userClasses);
			String NotesIniLine = "." + File.separator + filePath;

			String platform = m_session.getPlatform();
			String notesIniSep = platform.contains("Windows") ? ";" : ":";

			if (userClasses.isEmpty()) {
				userClasses = NotesIniLine;
				logMessage(filePath + " - registered as the only one addin");
			}
			else if (userClasses.indexOf(filePath) == -1) {
				userClasses = userClasses + notesIniSep + NotesIniLine;
				logMessage(filePath + " - registered!");
			}
			else {
				logMessage(filePath + " - already registered");
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

	private void showHelp() {
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

	private void showInfo() {
		logMessage("version      " + this.JADDIN_VERSION);
		logMessage("date         " + this.JADDIN_DATE);
		logMessage("catalog      " + this.catalog);
		logMessage("parameters   " + Arrays.toString(this.args));
	}

	/**
	 * Write a log message to the Domino console. The message string will be prefixed with the add-in name
	 * followed by a column, e.g. <code>"AddinName: xxxxxxxx"</code>
	 *
	 * @param	message		Message to be displayed
	 */
	private final void logMessage(String message) {
		AddInLogMessageText(this.JADDIN_NAME + ": " + message, 0);
	}

	/**
	 * Set the text of the add-in which is shown in command <code>"show tasks"</code>.
	 *
	 * @param	text	Text to be set
	 */
	private final void setAddinState(String text) {

		if (this.dominoTaskID == 0)
			return;

		if (!text.equals(this.bufState)) {
			AddInSetStatusLine(this.dominoTaskID, text);
			this.bufState = text;
		}
	}

	/**
	 * Create the Domino task status line which is shown in <code>"show tasks"</code> command.
	 *
	 * Note: This method is also called by the JAddinThread and the user add-in
	 *
	 * @param	name	Name of task
	 * @return	Domino task ID
	 */
	public final int createAddinStatusLine(String name) {
		return (AddInCreateStatusLine(name));
	}

	@Override
	public void termThread() {
		terminate();

		super.termThread();
	}

	/**
	 * Terminate all variables
	 */
	private void terminate() {
		try {
			AddInDeleteStatusLine(dominoTaskID);

			if (this.mq != null) {
				this.mq.close(0);
				this.mq = null;
			}

			if (this.m_session != null) {
				this.m_session.recycle();
				this.m_session = null;
			}

			logMessage("UNLOADED (OK) " + JADDIN_VERSION);
		} catch (NotesException e) {
			logMessage("UNLOADED (**FAILED**) " + JADDIN_VERSION);
		}
	}
}
