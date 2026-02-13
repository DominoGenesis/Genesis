package net.prominic.genesis;

import java.io.File;
import java.io.IOException;

import net.prominic.gja_v085.Event;
import net.prominic.gja_v085.GConfig;
import net.prominic.gja_v085.GLogger;
import net.prominic.utils.HTTP;

public class EventCatalogReport extends Event {
	public String JavaAddinRoot = null;
	public String JavaAddinConfig = null;
	public String Catalog = null;
	public String Server = null;

	public EventCatalogReport(String name, long seconds, boolean fireOnStart, GLogger logger) {
		super(name, seconds, fireOnStart, logger);
	}

	@Override
	public void run() {
		try {
			StringBuilder addins = getAllAddinData();
			if (addins == null) return;

			String endpoint = Catalog + "/report?openAgent";
			String data = String.format("server=%s&data=%s", Server, addins.toString());

			HTTP.post(endpoint, data);
		} catch (IOException e) {
			this.getLogger().severe(e);
		}
	}

	private StringBuilder getAllAddinData() {
		File file = new File(JavaAddinRoot);
		if (!file.exists()) return null;

		StringBuilder res = new StringBuilder();
		File[] directories = file.listFiles();
		if (directories == null) return null;
		for(int i=0; i<directories.length; i++) {
			if (directories[i].isDirectory()) {
				if (res.length() > 0) {
					res.append("|");
				}
				String addinName = directories[i].getName();
				String addinConfigPath = JavaAddinRoot + File.separator + addinName + File.separator + JavaAddinConfig;
				String addinVersion = GConfig.get(addinConfigPath, "version");
				String item = String.format("%s;%s", addinName, addinVersion);
				res.append(item);
			}
		}

		return res;
	}
}
