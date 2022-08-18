package net.prominic.genesis;

import java.io.File;
import java.io.FileReader;
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
				FileReader fr = new FileReader(files[i]);
				this.getLogger().info(String.format("> %s is going to be processed", files[i].getName()));
				
				JSONRules rules = new JSONRules(session, Catalog, JavaAddinConfig, JavaAddinCommand, getLogger());
				boolean res = rules.execute(fr);
				if (!res) {
					this.getLogger().info("(!) The json file can't be executed");
				}
				
				fr.close();

				String timestamp = String.valueOf(new java.util.Date().getTime());
				String newPath = JavaAddinJSONResponse + File.separator + timestamp + "-" + files[i].getName();
				this.getLogger().info(String.format("> file has been moved to a new location: %s", newPath));
				files[i].renameTo(new File(newPath));
			}
		} catch (Exception e) {
			this.getLogger().severe(e);
			e.printStackTrace();
		}					
	}
}