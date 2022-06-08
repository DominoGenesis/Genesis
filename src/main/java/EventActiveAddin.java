import java.io.File;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

import net.prominic.gja_v081.Event;
import net.prominic.gja_v081.GConfig;
import net.prominic.gja_v081.GLogger;
import net.prominic.util.FileUtils;

public class EventActiveAddin extends Event {
	public String JavaAddinRoot = null;
	public String JavaAddinConfig = null;
	public String JavaAddinLive = null;

	public EventActiveAddin(String name, long seconds, boolean fireOnStart, GLogger logger) {
		super(name, seconds, fireOnStart, logger);
	}

	@Override
	public void run() {
		try {
			File file = new File(JavaAddinRoot);
			if (!file.exists()) return;
			String osName = System.getProperty("os.name").toLowerCase();
			boolean inWindows = osName.contains("win");

			File[] directories = file.listFiles();
			for(int i=0; i<directories.length; i++) {
				if (directories[i].isDirectory()) {
					String addinName = directories[i].getName();
					String addinConfigPath = JavaAddinRoot + File.separator + addinName + File.separator + JavaAddinConfig;
					String addinLivePath = JavaAddinRoot + File.separator + addinName + File.separator + JavaAddinLive;
					String addinActive = GConfig.get(addinConfigPath, "active");
					String runjava = GConfig.get(addinConfigPath, "runjava");
					if ("1".equals(addinActive) && !isLive(addinLivePath)) {
						String runjavaTask = inWindows ? "nrunjava" : "runjava";
						String cmd = String.format("%s %s", runjavaTask, runjava);
						@SuppressWarnings("unused")
						Process proc = Runtime.getRuntime().exec(cmd);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}					

	}

	private boolean isLive(String javaAddinPath) {
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
	}
}
