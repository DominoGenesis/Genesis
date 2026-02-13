package net.prominic.genesis;

import java.io.File;
import java.util.Calendar;
import java.util.Date;

import lotus.domino.NotesException;
import lotus.domino.Session;
import net.prominic.gja_v085.Event;
import net.prominic.gja_v085.GConfig;
import net.prominic.gja_v085.GLogger;
import net.prominic.gja_v085.utils.FileUtils;

public class EventActivate extends Event {
	public Session session = null;
	public String JavaAddinRoot = null;
	public String JavaAddinConfig = null;
	public String JavaAddinLive = null;

	public EventActivate(String name, long seconds, boolean fireOnStart, GLogger logger) {
		super(name, seconds, fireOnStart, logger);
	}

	@Override
	public void run() {
		try {
			File file = new File(JavaAddinRoot);
			if (!file.exists()) {
				logWarning("[EventActivate] JavaAddinRoot does not exist: " + JavaAddinRoot);
				return;
			}

			File[] directories = file.listFiles();
			if (directories == null) return;

			for(int i=0; i<directories.length; i++) {
				if (directories[i].isDirectory()) {
					String addinName = directories[i].getName();
					String addinConfigPath = JavaAddinRoot + File.separator + addinName + File.separator + JavaAddinConfig;
					String addinLivePath = JavaAddinRoot + File.separator + addinName + File.separator + JavaAddinLive;

					File configFile = new File(addinConfigPath);
					if (!configFile.exists()) continue;

					String addinActive = GConfig.get(addinConfigPath, "active");
					String runjava = GConfig.get(addinConfigPath, "runjava");

					if ("1".equals(addinActive) && !isLive(addinLivePath)) {
						String cmd = String.format("load runjava %s", runjava);

						try {
							if (session == null) {
								logSevere("[EventActivate] Session is null, cannot send console command");
							} else {
								String result = session.sendConsoleCommand("", cmd);
								this.getLogger().info("[EventActivate] " + addinName + ": " + cmd);
								if (result != null && !result.isEmpty()) {
									this.getLogger().info("[EventActivate] " + addinName + " response: " + result.trim());
								}
							}
						} catch (NotesException e) {
							logSevere("[EventActivate] Failed: " + cmd + " - " + e.getMessage());
						}
					}
				}
			}
		} catch (Exception e) {
			logSevere("[EventActivate] Error: " + e.getMessage());
		}

	}

	private void logWarning(String message) {
		this.getLogger().warning(message);
		System.out.println(message);
	}

	private void logSevere(String message) {
		this.getLogger().severe(message);
		System.out.println(message);
	}

	private boolean isLive(String javaAddinPath) {
		try {
			File f = new File(javaAddinPath);
			if (!f.exists()) return false;

			String sTimeStamp = FileUtils.readFile(f);
			if (sTimeStamp.length() == 0) return false;

			// last live date
			long timeStamp = Long.parseLong(sTimeStamp);
			Date date1 = new Date(timeStamp);
			Calendar c1 = Calendar.getInstance();
			c1.setTime(date1);
			c1.add(Calendar.HOUR, 1);

			// current date
			Calendar c2 = Calendar.getInstance();

			return c1.after(c2);
		} catch(Exception e){
			return false;
		}
	}
}
