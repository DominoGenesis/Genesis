package net.prominic.log;

import java.io.IOException;
import java.net.URLEncoder;

import lotus.domino.NotesException;
import lotus.domino.Session;
import net.prominic.utils.HTTP;

public class Logger {
	private String m_catalog;
	private Session m_session;
	
	public Logger(Session session, String catalog) {
		m_session = session;
		m_catalog = catalog;
	}
	
	public boolean logInstall(String app, String version, boolean status, String console) {
		try {
			String server = m_session.getServerName();
			server = URLEncoder.encode(server, "UTF-8");
			app = URLEncoder.encode(app, "UTF-8");
			version = URLEncoder.encode(version, "UTF-8");
			console = URLEncoder.encode(console, "UTF-8");
			
			String endpoint = m_catalog + "/log?openAgent";
			StringBuffer res = HTTP.post(endpoint, "&server=" + server + "&app=" + app + "&version=" + version + "&status=" + (status ? "1" : "") + "&console=" + console);

			return res.toString().equalsIgnoreCase("OK");
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NotesException e) {
			e.printStackTrace();
		}	
		
		return false;
	}	
}
