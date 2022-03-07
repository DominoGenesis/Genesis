import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import lotus.domino.Database;
import lotus.domino.NotesException;
import lotus.domino.NotesFactory;
import lotus.domino.Session;
import lotus.notes.addins.JavaServerAddin;
import lotus.notes.internal.MessageQueue;

abstract class JavaServerAddinGenesis extends JavaServerAddin {

	// MessageQueue Constants
	// Message Queue name for this Addin (normally uppercase);
	// MSG_Q_PREFIX is defined in JavaServerAddin.class
	protected static final int 	MQ_MAX_MSGSIZE 			= 1024;
	protected MessageQueue 		mq						= null;
	protected Session 			m_session				= null;
	protected Database			m_ab					= null;
	protected String			m_javaAddinRootFolder	= null;
	protected String			m_javaAddinCommand		= null;
	protected String[] 			args 					= null;
	private int 				dominoTaskID			= 0;
	
	private static final String COMMAND_FILE_NAME		= "command.txt";

	// constructor if parameters are provided
	public JavaServerAddinGenesis(String[] args) {
		this.args = args;
	}

	public JavaServerAddinGenesis() {}
	
	protected abstract String getJavaAddinName();
	protected abstract String getJavaAddinVersion();
	protected abstract String getJavaAddinDate();
	protected abstract void showHelp();

	protected String getQName() {
		return MSG_Q_PREFIX + getJavaAddinName().toUpperCase();
	}
	
	/* the runNotes method, which is the main loop of the Addin */
	@Override
	public void runNotes () {
		// Set the Java thread name to the class name (default would be "Thread-n")
		this.setName(this.getJavaAddinName());

		// Create the status line showed in 'Show Task' console command
		this.dominoTaskID = createAddinStatusLine(this.getJavaAddinName());

		try {
			runNotesBeforeInitialize();
			
			m_session = NotesFactory.createSession();
			m_ab = m_session.getDatabase(m_session.getServerName(), "names.nsf");

			String javaAddinFolder = new File(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
			m_javaAddinRootFolder = javaAddinFolder.substring(0, javaAddinFolder.indexOf("javaaddin") + "javaaddin".length());
			m_javaAddinCommand = javaAddinFolder + File.separator + COMMAND_FILE_NAME;
			logMessage(m_javaAddinCommand);
			
			ProgramConfig pc = new ProgramConfig(this.getJavaAddinName(), this.args);
			pc.setState(m_ab, ProgramConfig.LOAD);		// set program documents in LOAD state
			
			showInfo();
			
			listen();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	protected void runNotesBeforeInitialize() {}

	// scan JavaAddin folder for sub-folders and adjust command.txt file with reload command
	public void restart() {
		File file = new File(m_javaAddinRootFolder);
		String[] directories = file.list(new FilenameFilter() {
		  @Override
		  public boolean accept(File current, String name) {
		    return new File(current, name).isDirectory();
		  }
		});
		
		for(int i=0; i<directories.length; i++) {
			File f = new File(m_javaAddinRootFolder + File.separator + directories[i] + File.separator + COMMAND_FILE_NAME);
			sendCommand(f, "reload");
		}
	}
	
	public void reload() {
		ProgramConfig pc = new ProgramConfig(this.getJavaAddinName(), this.args);
		pc.setState(m_ab, ProgramConfig.UNLOAD);		// set program documents in UNLOAD state
		this.stopAddin();
	}
	
	private void sendCommand(File file, String cmd) {
		try {
			PrintWriter writer = new PrintWriter(file, "UTF-8");
			writer.println(cmd);
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}
	
	protected void listen() {
		StringBuffer qBuffer = new StringBuffer(MQ_MAX_MSGSIZE);

		try {
			mq = new MessageQueue();
			int messageQueueState = mq.create(this.getQName(), 0, 0);	// use like MQCreate in API
			if (messageQueueState == MessageQueue.ERR_DUPLICATE_MQ) {
				logMessage(this.getJavaAddinName() + " task is already running");
				return;
			}

			if (messageQueueState != MessageQueue.NOERROR) {
				logMessage("Unable to create the Domino message queue");
				return;
			}

			if (mq.open(this.getQName(), 0) != MessageQueue.NOERROR) {
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

				// execute commands from file
				File file = new File(m_javaAddinCommand);
				logMessage(String.valueOf(file.exists()));
				if (file.exists()) {
					BufferedReader br = new BufferedReader(new FileReader(file));
					String line;
					while((line = br.readLine()) != null) {
						System.out.println(line);
						
						if (!line.isEmpty()) {
							resolveMessageQueueState(line);
						}
					}
					
					br.close();
					file.delete();
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	protected boolean resolveMessageQueueState(String cmd) {
		boolean flag = false;
		
		if ("-h".equals(cmd) || "help".equals(cmd)) {
			showHelp();
			flag = true;
		}
		else if ("info".equals(cmd)) {
			showInfo();
			flag = true;
		}
		else if ("quit".equals(cmd)) {
			quit();
			flag = true;
		}
		else if ("reload".equals(cmd)) {
			reload();
			flag = true;
		}
		else if ("restart".equals(cmd)) {
			restart();
			flag = true;
		}
		
		return flag;
	}

	protected void showInfo() {
		logMessage("version      " + this.getJavaAddinName());
		logMessage("date         " + this.getJavaAddinDate());
		logMessage("parameters   " + Arrays.toString(this.args));
	}
	
	protected void quit() {
		this.stopAddin();
	}
	
	/**
	 * Write a log message to the Domino console. The message string will be prefixed with the add-in name
	 * followed by a column, e.g. <code>"AddinName: xxxxxxxx"</code>
	 *
	 * @param	message		Message to be displayed
	 */
	protected final void logMessage(String message) {
		AddInLogMessageText(this.getJavaAddinName() + ": " + message, 0);
	}

	/**
	 * Set the text of the add-in which is shown in command <code>"show tasks"</code>.
	 *
	 * @param	text	Text to be set
	 */
	protected final void setAddinState(String text) {

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
	protected final int createAddinStatusLine(String name) {
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
	protected void terminate() {
		try {
			AddInDeleteStatusLine(dominoTaskID);

			if (this.m_session != null) {
				this.m_session.recycle();
				this.m_session = null;
			}
			if (this.mq != null) {
				this.mq.close(0);
				this.mq = null;
			}

			logMessage("UNLOADED (OK) " + this.getJavaAddinVersion());
		} catch (NotesException e) {
			logMessage("UNLOADED (**FAILED**) " + this.getJavaAddinVersion());
		}
	}
}
