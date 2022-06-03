import java.io.File;
import java.io.IOException;

import net.prominic.genesis.JSONRules;
import net.prominic.gja_v20220602.Event;
import net.prominic.gja_v20220602.GConfig;
import net.prominic.gja_v20220602.GLogger;
import net.prominic.util.FileUtils;
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
			System.out.println(version);

			String endpoint = String.format("%s/package.update?openagent&id=%s&v=%s", Catalog, addinID, version);
			System.out.println(endpoint);
			String buf = HTTP.get(endpoint).toString();
			System.out.println(buf.length());
			if (buf.length() < 50) {
				return;
			}
			
			FileUtils.writeFile(new File(CommandFilePath), "update Genesis");
			System.out.println("set command to update Genesis");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
