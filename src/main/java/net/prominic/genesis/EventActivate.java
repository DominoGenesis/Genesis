package net.prominic.genesis;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

import net.prominic.gja_v085.Event;
import net.prominic.gja_v085.GConfig;
import net.prominic.gja_v085.GLogger;
import net.prominic.gja_v085.utils.FileUtils;

public class EventActivate extends Event {
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
			log("[EventActivate] Checking JavaAddinRoot: " + JavaAddinRoot);

			if (!file.exists()) {
				logWarning("[EventActivate] JavaAddinRoot does not exist: " + JavaAddinRoot);
				return;
			}

			String osName = System.getProperty("os.name").toLowerCase();
			boolean inWindows = osName.contains("win");
			log("[EventActivate] OS detected: " + osName + ", isWindows: " + inWindows);

			File[] directories = file.listFiles();
			if (directories == null) {
				logWarning("[EventActivate] Unable to list directories in: " + JavaAddinRoot);
				return;
			}

			log("[EventActivate] Found " + directories.length + " entries in JavaAddinRoot");

			for(int i=0; i<directories.length; i++) {
				if (directories[i].isDirectory()) {
					String addinName = directories[i].getName();
					String addinConfigPath = JavaAddinRoot + File.separator + addinName + File.separator + JavaAddinConfig;
					String addinLivePath = JavaAddinRoot + File.separator + addinName + File.separator + JavaAddinLive;

					log("[EventActivate] Checking addin: " + addinName);
					log("[EventActivate]   Config path: " + addinConfigPath);
					log("[EventActivate]   Live path: " + addinLivePath);

					File configFile = new File(addinConfigPath);
					if (!configFile.exists()) {
						log("[EventActivate]   Config file does not exist, skipping");
						continue;
					}

					String addinActive = GConfig.get(addinConfigPath, "active");
					String runjava = GConfig.get(addinConfigPath, "runjava");

					log("[EventActivate]   active=" + addinActive + ", runjava=" + runjava);

					boolean isCurrentlyLive = isLive(addinLivePath);
					log("[EventActivate]   isLive=" + isCurrentlyLive);

					if ("1".equals(addinActive) && !isCurrentlyLive) {
						String runjavaTask = inWindows ? "nrunjava" : "runjava";
						String cmd = String.format("%s %s", runjavaTask, runjava);

						log("[EventActivate]   Attempting to start addin: " + addinName);
						log("[EventActivate]   Command: " + cmd);

						try {
							Process proc = Runtime.getRuntime().exec(cmd);
							log("[EventActivate]   Process started successfully for: " + addinName);

							// Give the process a moment and check if it's still alive
							Thread.sleep(1000);
							boolean isAlive = proc.isAlive();
							log("[EventActivate]   Process isAlive after 1s: " + isAlive);

							if (!isAlive) {
								int exitCode = proc.exitValue();
								logWarning("[EventActivate]   Process exited immediately with code: " + exitCode);
							}
						} catch (IOException e) {
							logSevere("[EventActivate]   Failed to execute command: " + cmd);
							logSevere("[EventActivate]   Error: " + e.getMessage());
							this.getLogger().severe(e);
						} catch (InterruptedException e) {
							logWarning("[EventActivate]   Thread interrupted while checking process");
						}
					} else {
						if (!"1".equals(addinActive)) {
							log("[EventActivate]   Addin is not active, skipping");
						} else {
							log("[EventActivate]   Addin is already live, skipping");
						}
					}
				}
			}
			log("[EventActivate] Completed checking all addins");
		} catch (Exception e) {
			logSevere("[EventActivate] Unexpected error: " + e.getMessage());
			this.getLogger().severe(e);
			e.printStackTrace();
		}

	}

	private void log(String message) {
		this.getLogger().info(message);
		System.out.println(message);
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