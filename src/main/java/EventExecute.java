import java.io.IOException;

import lotus.domino.Database;
import lotus.domino.Session;
import net.prominic.genesis.JSONRules;
import net.prominic.gja_v20220517.Event;
import net.prominic.gja_v20220517.GLogger;
import net.prominic.utils.HTTP;

public class EventExecute extends Event {
	public Session Session = null;
	public Database AB = null;
	public String JavaAddinName = null;
	public String Catalog = null;
	public String Server = null;
	public String Data = null;
	public GLogger Logger = null;
	
	public EventExecute(String name, long seconds, boolean fireOnStart, GLogger logger) {
		super(name, seconds, fireOnStart, logger);
	}

	@Override
	public void run() {
		try {
			System.out.print("Execute");
			String endpoint = Catalog + "/execute?openAgent&id=dominometer";
			String buf = HTTP.get(endpoint).toString();

			JSONRules rules = new JSONRules(Session, AB, JavaAddinName, Catalog, Logger);
			rules.execute(buf);
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}

}
