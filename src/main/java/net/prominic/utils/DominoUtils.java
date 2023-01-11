package net.prominic.utils;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NoteCollection;
import lotus.domino.NotesException;
import lotus.domino.NotesThread;

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
	
	private static void log(String s) {
		System.out.println(s);
	}

}
