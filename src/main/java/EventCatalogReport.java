import java.io.IOException;

import net.prominic.gja_v20220517.Event;
import net.prominic.gja_v20220517.GLogger;
import net.prominic.utils.HTTP;

public class EventCatalogReport extends Event {
	public String Catalog = null;
	public String Server = null;
	public String Data = null;
	
	public EventCatalogReport(String name, long seconds, boolean fireOnStart, GLogger logger) {
		super(name, seconds, fireOnStart, logger);
	}

	@Override
	public void run() {
		System.out.print("Catalog Report");
		
		String endpoint = Catalog + "/report?openAgent";
		String data = String.format("server=%s&data=%s", Server, Data);
		
		try {
			HTTP.post(endpoint, data);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
