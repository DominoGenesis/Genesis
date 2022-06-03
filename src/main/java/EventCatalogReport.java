import java.io.File;
import java.io.IOException;
import net.prominic.gja_v20220602.Event;
import net.prominic.gja_v20220602.GConfig;
import net.prominic.gja_v20220602.GLogger;
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
			e.printStackTrace();
		}
	}

	private StringBuilder getAllAddinData() {
		File file = new File(JavaAddinRoot);
		if (!file.exists()) return null;
		
		StringBuilder res = new StringBuilder();
		File[] directories = file.listFiles();
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
