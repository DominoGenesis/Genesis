package net.prominic.utils;

import java.io.IOException;
import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NoteCollection;
import lotus.domino.NotesException;

public class DominoUtils {
	public static void sign(Database database) {
		try {
			log("[Sign] " + database.getTitle() + " - started");

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

			log("[Sign] " + database.getTitle() + " - completed");

			nc.recycle();		
		} catch (NotesException e) {
			log("sign command failed: " + e.getMessage());
		}
	}

	private static void log(String s) {
		System.out.println(s);
	}

}
