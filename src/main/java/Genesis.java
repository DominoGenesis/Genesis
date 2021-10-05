package net.prominic.Genesis;

import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;

import lotus.domino.NotesException;
import lotus.domino.NotesFactory;
import lotus.domino.Session;
import lotus.notes.addins.JavaServerAddin;
import lotus.notes.internal.MessageQueue;

import net.prominic.utils.RESTClient;

public class App extends JavaServerAddin {
	// Constants
	private final String		JADDIN_NAME				= "Genesis";
	private final String		JADDIN_VERSION			= "0.1.0";
	private final String		JADDIN_DATE				= "2021-09-17 17:30 (dac)";
	
	// MessageQueue Constants
	public static final int MQ_MAX_MSGSIZE = 1024;
	// this is already defined (should be = 1):
	public static final int	MQ_WAIT_FOR_MSG = MessageQueue.MQ_WAIT_FOR_MSG;

	// Message Queue name for this Addin (normally uppercase);
	// MSG_Q_PREFIX is defined in JavaServerAddin.class
	final String 			qName 					= MSG_Q_PREFIX + JADDIN_NAME.toUpperCase();
	MessageQueue 			mq						= null;
	Session 				m_session				= null;

	private String[] 		args 					= null;
	private int 			dominoTaskID			= 0;
	private String			dac						= "";

	// constructor if parameters are provided
	public App(String[] args) {
		this.args = args;
	}

	public App() {}

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
				dac = args[0];
				if ("dev".equals(dac)) {
					dac = "https://domino-1.dmytro.cloud/dac.nsf";
				}
			}
			else {
				dac = "https://domino-1.dmytro.cloud/dac.nsf";
			}
			
			// check if connection could be established
			if (!check()) {
				logMessage("connection (*FAILED*) with: " + dac);
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

			while (this.addInRunning() && (messageQueueState != MessageQueue.ERR_MQ_QUITTING)) {
				/* gives control to other task in non preemptive os*/
				OSPreemptOccasionally();

				// check for command from console
				messageQueueState = mq.get(qBuffer, MQ_MAX_MSGSIZE, MessageQueue.MQ_WAIT_FOR_MSG, 1000);
				if (messageQueueState == MessageQueue.ERR_MQ_QUITTING) {
					setAddinState("Quit");
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
			String url = dac.concat("/check?openagent");
			buf = RESTClient.sendGET(url);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return buf.toString().equals("OK");
	}

	private void resolveMessageQueueState(String cmd) {
		if ("-h".equals(cmd) || "help".equals(cmd)) {
			showHelp();
		}
		else if ("-i".equals(cmd) || "info".equals(cmd)) {
			showInfo();
		}
		else if ("-c".equals(cmd) || "check".equals(cmd)) {
			if (check()) {
				logMessage("OK to connect with with: " + dac);
			}
			else {
				logMessage("*FAILED* to connect with: " + dac);
			}
		}
		else {
			logMessage("invalid command (use -h or help to get details)");
		}
	}

	private void showHelp() {
		int year = Calendar.getInstance().get(Calendar.YEAR);
		logMessage("*** Usage ***");
		AddInLogMessageText("load runjava Genesis");
		AddInLogMessageText("tell Genesis <command>");
		AddInLogMessageText("   quit       Unload Genesis");
		AddInLogMessageText("   help       Show help information (or -h)");
		AddInLogMessageText("   info       Show version and more of Genesis (or -i)");
		AddInLogMessageText("   check      Check connection with Domino App Catalog (or -c)");
		AddInLogMessageText("Copyright (C) Prominic.NET, Inc. 2021" + (year > 2021 ? " - " + Integer.toString(year) : ""));
		AddInLogMessageText("See https://prominic.net for more details.");
	}

	private void showInfo() {
		logMessage("version      " + this.JADDIN_VERSION);
		logMessage("date         " + this.JADDIN_DATE);
		logMessage("app catalog  " + this.dac);
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

		AddInSetStatusLine(this.dominoTaskID, text);
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
