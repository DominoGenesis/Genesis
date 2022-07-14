package net.prominic.utils;

import lotus.domino.Session;
import lotus.domino.Database;
import lotus.domino.DateTime;
import lotus.domino.Document;
import lotus.domino.NoteCollection;
import lotus.domino.NotesException;
import lotus.domino.NotesThread;
import lotus.domino.Registration;

public class DominoUtils {
	public static void sign(Database database) {
		try {
			NotesThread.sinitThread();

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
			log(String.format("[Sign] failed: %d %s", e.id, e.text));
			e.printStackTrace();
		}
		finally {
			NotesThread.stermThread();
		}
	}

	public static void crossCertify(Session session, String regServer, String certId, String userId) {
		try {
			NotesThread.sinitThread();
			
			log("[CrossCertify] - started");
			
			Registration reg = session.createRegistration();
			reg.setRegistrationServer(regServer);
			reg.setCertifierIDFile(certId);

			if (reg.crossCertify(userId)) {
				log("[CrossCertify] - succeeded");
			}
			else {
				log("[CrossCertify] - failed");
			}
			
			reg.recycle();
		} catch(NotesException e) {
			log(String.format("[CrossCertify] failed: %d %s", e.id, e.text));
			e.printStackTrace();
		}
		finally {
			NotesThread.stermThread();
		}
	}
	
	public static void crossCertify(Session session, String regServer, String certId, String certPassword, DateTime expirationDate, String userId) {
		try {
//			NotesThread.sinitThread();
			
			log("[CrossCertify] - started (1)");
			
			Registration reg = session.createRegistration();
			reg.setRegistrationServer(regServer);
			reg.setCertifierIDFile(certId);
			reg.setExpiration(expirationDate);

			if (reg.crossCertify(userId, certPassword, "Programmatically cross certified (using Genesis)")) {
				log("[CrossCertify] - succeeded");
			}
			else {
				log("[CrossCertify] - failed");
			}
			
			reg.recycle();
		} catch(NotesException e) {
			log(String.format("[CrossCertify] failed: %d %s", e.id, e.text));
			e.printStackTrace();
		}
		finally {
//			NotesThread.stermThread();
		}
	}

	private static void log(String s) {
		System.out.println(s);
	}

}
