package net.prominic.utils;

import lotus.domino.Database;
import lotus.domino.DateTime;
import lotus.domino.Document;
import lotus.domino.NoteCollection;
import lotus.domino.NotesException;
import lotus.domino.NotesFactory;
import lotus.domino.NotesThread;
import lotus.domino.Registration;
import lotus.domino.Session;

public class DominoUtils {
	public static void sign(Database database) {
		try {
			log(String.format("[Sign] %s - started", database.getTitle()));

			NoteCollection nc = database.createNoteCollection(false);
			nc.selectAllDesignElements(true);
			nc.buildCollection();

			log(" - design elements to sign: " + String.valueOf(nc.getCount()));

			String noteid = nc.getFirstNoteID();
			while (noteid.length() > 0) {
				Document doc = database.getDocumentByID(noteid);

				doc.sign();
				doc.save();
				doc.recycle();

				noteid = nc.getNextNoteID(noteid);
			}

			log(String.format("[Sign] %s - completed", database.getTitle()));

			nc.recycle();		
		} catch (NotesException e) {
			log(String.format("[Sign] failed: %s", e.getMessage()));
			e.printStackTrace();
		}
	}

	public static void crossCertify(String regServer, String certIdFilePath, String idFilePath, String password, int years)  throws Exception {
		try {
			log("[CrossCertify] - started");
			
			NotesThread.sinitThread();

			// build the session arguments
			String[] args = null;
			log(" - using default notesID path.");
			args = new String[0];

			Session session = NotesFactory.createSession(null, args, null, null);
			log(String.format(" - running on Notes Version: %s", session.getNotesVersion()));

			Registration reg = session.createRegistration();
			reg.setRegistrationServer(regServer);
			reg.setCertifierIDFile(certIdFilePath);

			DateTime dt = session.createDateTime("Today");
			dt.setNow();
			dt.adjustYear(years);
			reg.setExpiration(dt);

			if (reg.crossCertify(idFilePath, // ID file
					password, // certifier password
					"Programmatically cross certified (using Genesis)")) // comment field
			{
				log("[CrossCertify] - succeeded");
			}
			else {
				log("[CrossCertify] - failed");
			}
		} catch(NotesException e) {
			log(String.format("[CrossCertify] failed: %d %s", e.id, e.text));
			e.printStackTrace();
		}
		finally {
			NotesThread.stermThread();
		}
	}

	private static void log(String s) {
		System.out.println(s);
	}

}
