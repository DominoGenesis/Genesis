package net.prominic.utils;

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
	
	/*
	 * Refresh design of database
	 */
	public static void refreshDesign(Database targetDb, Database templateDb) {
		try {
			log("[Refresh Design] " + targetDb.getTitle() + " - started");
			
			Document targetDoc = targetDb.getDocumentByID("FFFF0010");
			Document templateDoc = templateDb.getDocumentByID("FFFF0010");
			
			// set inherit design name
			String targetTitle = targetDb.getTitle() + "\n" + "#2" + templateDb.getFileName();
			targetDoc.replaceItemValue("$Title", targetTitle);
			targetDoc.save();
			targetDb.setTitle(targetTitle);
			
			// set master template name
			String templateTitle = templateDb.getTitle() + "\n" + "#1" + templateDb.getFileName();
			templateDoc.replaceItemValue("$Title", templateTitle);
			templateDoc.save();
			templateDb.setTitle(templateTitle);
			
			targetDb.getParent().sendConsoleCommand(null, "load design -f " + targetDb.getFilePath());
			
			log("[Refresh Design] " + targetDb.getTitle() + " - completed");
		} catch (NotesException e) {
			log("[Refresh Design] failed: " + e.getMessage());
			e.printStackTrace();
		}
	}
	

	private static void log(String s) {
		System.out.println(s);
	}

}
