package net.prominic.genesis;

import java.io.File;
import java.io.IOException;

import net.prominic.gja_v085.Event;
import net.prominic.gja_v085.GConfig;
import net.prominic.gja_v085.GLogger;
import net.prominic.gja_v085.utils.FileUtils;
import net.prominic.utils.HTTP;

public class EventUpdate extends Event {
	public String ConfigFilePath = null;
	public String CommandFilePath = null;
	public String Catalog = null;

	public EventUpdate(String name, long seconds, boolean fireOnStart, GLogger logger) {
		super(name, seconds, fireOnStart, logger);
	}

	@Override
	public void run() {
		try {
			// get addin name and it's JSON
			String addinID = "Genesis";
			String version = GConfig.get(ConfigFilePath, "version");

			String endpoint = String.format("%s/package.update?openagent&id=%s&v=%s", Catalog, addinID, version);
			String buf = HTTP.get(endpoint).toString();
			if (buf.length() < 50) {
				return;
			}

			String msg = "New version of Genesis is detected. Update process has started";
			this.getLogger().info(msg);
			FileUtils.writeFile(new File(CommandFilePath), "update Genesis");
		} catch (IOException e) {
			this.getLogger().severe(e);
		}
	}

}
