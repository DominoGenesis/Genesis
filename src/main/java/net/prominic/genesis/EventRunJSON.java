package net.prominic.genesis;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import lotus.domino.Session;
import net.prominic.gja_v082.Event;
import net.prominic.gja_v082.GLogger;

public class EventRunJSON extends Event {
	public Session session = null;
	public String JavaAddinConfig = null;
	public String JavaAddinJSON = null;
	public String JavaAddinJSONResponse = null;
	public String JavaAddinCommand = null;
	public String Catalog = null;

	public EventRunJSON(String name, long seconds, boolean fireOnStart, GLogger logger) {
		super(name, seconds, fireOnStart, logger);
	}

	@Override
	public void run() {
		try {
			File folder = new File(JavaAddinJSON);
			if (!folder.exists()) return;

			File[] files = folder.listFiles();
			if (files.length == 0) return;

			File folder2 = new File(JavaAddinJSONResponse);
			if (!folder2.exists()) {
				folder2.mkdir();
			}

			for(int i=0; i<files.length; i++) {
				File file = files[i];
				processFile(file);
			}
		} catch (Exception e) {
			this.getLogger().severe(e);
			e.printStackTrace();
		}
	}

	private void processFile(File file) throws FileNotFoundException, UnsupportedEncodingException {
		String message = "OK";
		try {
			FileReader fr = new FileReader(file);
			this.getLogger().info(String.format("> %s is going to be processed", file.getName()));

			JSONRules rules = new JSONRules(session, Catalog, JavaAddinConfig, JavaAddinCommand, getLogger());
			boolean res = rules.execute(fr);
			if (!res) {
				message = "The json file can't be processed";
			}

			fr.close();
			file.delete();
			this.getLogger().info(String.format("> file has been processed and deleted: %s", file.getName()));
		} catch (Exception e) {
			this.getLogger().severe(e);
			e.printStackTrace();
			message = e.getMessage();
			if (message == null || message.isEmpty()) {
				message = "an undefined exception was thrown";
			}
		} finally {
			PrintWriter writer = new PrintWriter(JavaAddinJSONResponse + File.separator + file.getName(), "UTF-8");
			writer.println(message);
			writer.close();
		}
	}
}