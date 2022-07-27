package net.prominic.genesis;

import java.util.HashMap;
import java.util.Map;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.Session;
import net.prominic.gja_v082.Event;
import net.prominic.gja_v082.GLogger;

public class EventMyAccountDominoPerformanceLogging extends Event {
	public Session session = null;
	public Database ab = null;

	public EventMyAccountDominoPerformanceLogging(String name, long seconds, boolean fireOnStart, GLogger logger) {
		super(name, seconds, fireOnStart, logger);
	}

	@Override
	public void run() {
		try {
			logInfo("[MyAccountDominoPerformanceLogging - started]");
			
			String server = session.getServerName();
			Document serverDoc = ab.getView("($ServersLookup)").getDocumentByKey(server, true);	

			HashMap<String, String> fieldsString = new HashMap<String, String>();
			fieldsString.put("HTTP_LogToFiles", "1");
			fieldsString.put("HTTP_LogToDomLog", "0");
			fieldsString.put("HTTP_AccessLogFormat", "1");
			fieldsString.put("HTTP_AccessLogFormat", "1");
			fieldsString.put("HTTP_LogTime", "0");
			fieldsString.put("HTTP_LogFileDuration", "0");

			HashMap<String, Integer> fieldsInteger = new HashMap<String, Integer>();
			fieldsInteger.put("HTTP_MaxLogEntrySize", 10);
			fieldsInteger.put("HTTP_MaxLogFileSize", 0);

			boolean toSave = false;
			
			// Iterating Strings through for loop
			for (Map.Entry<String, String> set : fieldsString.entrySet()) {
				String itemName = set.getKey();

				String docValue = serverDoc.getItemValueString(itemName);
				String defValue = set.getValue();
				if (!defValue.equals(docValue)) {
					serverDoc.replaceItemValue(itemName, defValue);
					logInfo(String.format("> set %s = %s", itemName, defValue));
					toSave = true;
				}
			}
			
			// Iterating Strings through for loop
			for (Map.Entry<String, Integer> set : fieldsInteger.entrySet()) {
				String itemName = set.getKey();

				int docValue;
				if (serverDoc.hasItem(itemName)) {
					docValue = serverDoc.getItemValueInteger(itemName);
				}
				else {
					docValue = -1;
				}

				int defValue = set.getValue();
				if (defValue != docValue) {
					serverDoc.replaceItemValue(itemName, defValue);
					logInfo(String.format("> set %s = %d", itemName, defValue));
					toSave = true;
				}
			}
			
			if (toSave) {
				logInfo("> logging settings have been updated");
				serverDoc.save();
			}
			else {
				logInfo("> logging settings - OK (no updates)");
			}
			
			logInfo("[MyAccountDominoPerformanceLogging - completed]");
		} catch (NotesException e) {
			e.printStackTrace();
		}
	}

	private void logInfo(String msg) {
		getLogger().info(msg);
		System.out.println(msg);
	}
}
