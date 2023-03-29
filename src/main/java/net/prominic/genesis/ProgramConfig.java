package net.prominic.genesis;

import lotus.domino.Database;

import lotus.domino.DateTime;
import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.NotesException;
import lotus.domino.View;
import net.prominic.gja_v084.GLogger;

public class ProgramConfig {
	private final static String COMMENT_PROMINIC = "[PROMINIC.NET] (created automatically). Please do not delete it.\nPlease contact Support@Prominic.NET with any questions about this program document.";
	public final static int LOAD = 1;
	public final static int UNLOAD = 2;

	private final static int PROGRAM_MINUTES_DEFAULT = 20;
	private final static String PROGRAM_DISABLE = "0";
	private final static String PROGRAM_ENABLE = "1";
	private final static String PROGRAM_SERVERSTART = "2";

	private String m_addinName;
	private String m_addinParams;
	private GLogger m_logger;

	public ProgramConfig(String addinName, String[] addinParamsArr, GLogger logger) {
		m_addinName = addinName;
		m_addinParams = "";		
		if (addinParamsArr != null && addinParamsArr.length > 0) {
			for(int i = 0; i < addinParamsArr.length; i++) {
				m_addinParams += " ";
				m_addinParams += addinParamsArr[i];
			}
		}
		m_logger = logger;
	}

	/*
	 * Set state for program documents
	 */
	public boolean setState(Database database, int state) {
		try {
			String server = database.getServer();
			View view = database.getView("($Programs)");
			DocumentCollection col = view.getAllDocumentsByKey(server, true);
			boolean programStartupOnly = false;
			boolean programScheduled = false;
			String newEnabled = (state == LOAD) ? PROGRAM_DISABLE : PROGRAM_ENABLE;

			Document doc = col.getFirstDocument();
			while (doc != null) {
				Document nextDoc = col.getNextDocument(doc);

				if (isAddinDoc(doc)) {
					if (isProgramAtStartupOnly(doc)) {
						if (!programStartupOnly) {
							programStartupOnly = true;
							updateProgram(database, doc, PROGRAM_SERVERSTART);
						}
						else {
							deleteDuplicate(doc);
							doc = null;
						}
					}
					else {
						if (!programScheduled) {
							programScheduled = true;
							updateProgram(database, doc, newEnabled);
						}
						else {
							deleteDuplicate(doc);
							doc = null;
						}
					}
				}

				if (doc != null) {
					doc.recycle();
				}

				doc = nextDoc;
			}

			if (!programStartupOnly) {
				doc = createProgram(database, PROGRAM_SERVERSTART);
				doc.recycle();
			}

			if (!programScheduled) {
				doc = createProgram(database, newEnabled);
				doc.recycle();
			}

			col.recycle();
			view.recycle();

			return true;
		} catch(Exception e) {
			e.printStackTrace();
		}

		return false;
	}

	/*
	 * Disable related program documents
	 */
	public boolean disable(Database database) {
		try {
			String server = database.getServer();
			View view = database.getView("($Programs)");
			DocumentCollection col = view.getAllDocumentsByKey(server, true);

			Document doc = col.getFirstDocument();
			while (doc != null) {
				Document nextDoc = col.getNextDocument(doc);

				if (isAddinDoc(doc) && !"0".equals(doc.getItemValueString("Enabled"))) {
					doc.replaceItemValue("Enabled", "0");
					doc.save();
					log("program document disabled");	
				}

				doc.recycle();
				doc = nextDoc;
			}

			col.recycle();
			view.recycle();

			return true;
		} catch(Exception e) {
			e.printStackTrace();
		}

		return false;
	}
	
	private void deleteDuplicate(Document doc) throws NotesException {
		doc.remove(true);
		log("program document deleted (duplicate)");
	}

	private Document updateProgram(Database database, Document doc, String newEnabled) throws NotesException {
		String cmdLine = m_addinName + m_addinParams;

		boolean toSave = false;
		String enabled = doc.getItemValueString("Enabled");

		if (!cmdLine.equalsIgnoreCase(doc.getItemValueString("CmdLine"))) {
			doc.replaceItemValue("CmdLine", cmdLine);
			toSave = true;
			log("program document updated. CmdLine: " + cmdLine);
		}

		if (!newEnabled.equals(doc.getItemValueString("Enabled"))) {
			doc.replaceItemValue("Enabled", newEnabled);
			toSave = true;
			log("program document updated. Schedule change: " + getEnabledLabel(enabled) + " -> " + getEnabledLabel(newEnabled));
		}

		if (newEnabled.equals(PROGRAM_ENABLE)) {
			setSchedule(database, doc, newEnabled);
			log("program document updated. Schedule to run at: " + doc.getFirstItem("Schedule").getDateTimeValue().getLocalTime());
			toSave = true;
		}

		if (toSave) {
			doc.save();
		}

		return doc;
	}

	/*
	 * set Schedule to now+20 minutes
	 */
	private void setSchedule(Database database, Document doc, String enabled) throws NotesException {
		DateTime dt = database.getParent().createDateTime("Today");
		dt.setNow();
		dt.adjustMinute(PROGRAM_MINUTES_DEFAULT);
		doc.replaceItemValue("Schedule", dt);
		dt.recycle();
	}

	/*
	 * Create program document
	 */
	private Document createProgram(Database database, String enabled) throws NotesException {
		Document doc = database.createDocument();

		doc.replaceItemValue("Form", "Program");
		doc.replaceItemValue("Type", "Program");
		doc.replaceItemValue("Source", database.getServer());
		doc.replaceItemValue("Program", "runjava");
		doc.replaceItemValue("Enabled", enabled);
		doc.replaceItemValue("Comments", COMMENT_PROMINIC);
		doc.replaceItemValue("CmdLine", m_addinName + m_addinParams);

		if (enabled.equalsIgnoreCase(PROGRAM_ENABLE)) {
			setSchedule(database, doc, enabled);
		}

		doc.computeWithForm(true, false);
		doc.save();

		log("program document created. Schedule: " + getEnabledLabel(enabled));

		return doc;
	}

	private String getEnabledLabel(String v) {
		if (PROGRAM_ENABLE.equals(v)) {
			return "Enabled";
		}
		else if(PROGRAM_SERVERSTART.equals(v)) {
			return "At server startup only";
		}
		else {
			return "Disabled";
		}
	}

	/*
	 * Check if program document is created for current Addin
	 */
	private boolean isAddinDoc(Document doc) throws NotesException {
		String cmdLine = doc.getItemValueString("CmdLine");
		return cmdLine.toLowerCase().contains(m_addinName.toLowerCase());
	}

	/*
	 * Check if Program document is set to be scheduled
	 */
	private boolean isProgramAtStartupOnly(Document doc) throws NotesException {
		return "2".equals(doc.getItemValueString("Enabled"));
	}

	private void log(String message) {
		m_logger.info(m_addinName + " " + message);
		System.out.println(m_addinName + " " + message);
	}

}
