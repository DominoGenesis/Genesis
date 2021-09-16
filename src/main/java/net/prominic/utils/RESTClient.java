package net.prominic.utils;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class RESTClient {
	private static final String USER_AGENT = "AddInDirector";

	public static StringBuffer sendPOST(String endpoint, String data) throws IOException {
		HttpURLConnection con = open(endpoint);
		con.setDoOutput(true);
		con.setRequestMethod("POST");
		con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		con.getOutputStream().write(data.getBytes(), 0, data.length());

		return response(con);
	}

	public static StringBuffer sendGET(String endpoint) throws IOException {
		HttpURLConnection con = open(endpoint);
		con.setRequestMethod("GET");
		return response(con);
	}

	private static HttpURLConnection open(String endpoint) throws IOException {
		URL url = new URL(endpoint);

		HttpURLConnection con = null;
		String protocol = url.getProtocol();
		if (protocol.equals("https")) {
			con = (HttpsURLConnection) url.openConnection();
		}
		else if(protocol.equals("http")) {
			con = (HttpURLConnection) url.openConnection();
		}

		if (con == null) {
			throw new IllegalArgumentException("Unexpected protocol: " + protocol);
		}

		con.setConnectTimeout(5000); //set timeout to 5 seconds
		con.setRequestProperty("User-Agent", USER_AGENT);

		return con;
	}

	private static StringBuffer response(HttpURLConnection con) throws IOException {
		int responseCode = con.getResponseCode();
		if (responseCode != HttpURLConnection.HTTP_OK) {
			con.disconnect();
			throw new IOException("Unexpected response code: " + Integer.toString(responseCode));
		}

		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		StringBuffer response = new StringBuffer();
		String inputLine;

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}

		in.close();
		con.disconnect();

		return response;
	}

	public static boolean saveURLTo(String fileURL, String filePath) {
		boolean res = false;
		try {
			URL url = new URL(fileURL);
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			int responseCode = con.getResponseCode();

			if (responseCode == HttpURLConnection.HTTP_OK) {
				InputStream is = con.getInputStream();
				FileOutputStream os = new FileOutputStream(filePath);

				int bytesRead = -1;
				byte[] buffer = new byte[4096];
				while ((bytesRead = is.read(buffer)) != -1) {
					os.write(buffer, 0, bytesRead);
				}

				os.close();
				is.close();

				res = true;
			}
			con.disconnect();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return res;
	}
}
