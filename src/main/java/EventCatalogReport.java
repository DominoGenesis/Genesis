import java.io.File;

import java.io.FilenameFilter;
import java.io.IOException;

import net.prominic.gja_v20220602.Event;
import net.prominic.gja_v20220602.GLogger;
import net.prominic.util.StringUtils;
import net.prominic.utils.HTTP;

public class EventCatalogReport extends Event {
	public String JavaAddinRoot = null;
	public String Catalog = null;
	public String Server = null;
	
	public EventCatalogReport(String name, long seconds, boolean fireOnStart, GLogger logger) {
		super(name, seconds, fireOnStart, logger);
	}

	@Override
	public void run() {
		String[] app = getAllAddin();
		String endpoint = Catalog + "/report?openAgent";
		String data = String.format("server=%s&data=%s", Server, StringUtils.join(app, ";"));
		
		try {
			HTTP.post(endpoint, data);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String[] getAllAddin() {
		File file = new File(JavaAddinRoot);
		String[] directories = file.list(new FilenameFilter() {
			@Override
			public boolean accept(File current, String name) {
				return new File(current, name).isDirectory();
			}
		});

		return directories;
	}
}
