package net.prominic.utils;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/*
 * Just an example of HTTP client with post/get methods
 */
public class HTTP {
	public static StringBuffer post(String endpoint, String data) throws IOException {
		HttpURLConnection con = getConnection(endpoint);

		con.setDoOutput(true);
		con.setRequestMethod("POST");
		con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		con.getOutputStream().write(data.getBytes(), 0, data.length());

		return response(con);
	}

	public static StringBuffer get(String endpoint) throws IOException {
		HttpURLConnection con = getConnection(endpoint);

		con.setRequestMethod("GET");

		return response(con);
	}

	private static HttpURLConnection getConnection(String endpoint) throws IOException {
		URL url = new URL(endpoint);

		HttpURLConnection con = null;
		String protocol = url.getProtocol();
		if ("https".equals(protocol)) {
			con = (HttpsURLConnection) url.openConnection();
		}
		else if("http".equals(protocol)) {
			con = (HttpURLConnection) url.openConnection();
		}
		else {
			throw new IllegalArgumentException("Unexpected protocol: " + protocol);			
		}

		con.setConnectTimeout(5000); //set timeout to 5 seconds

		return con;
	}

	private static StringBuffer response(HttpURLConnection con) throws IOException {
		// handle error response code it occurs
		int responseCode = con.getResponseCode();
		InputStream inputStream;
		if (200 <= responseCode && responseCode <= 299) {
			inputStream = con.getInputStream();
		} else {
			inputStream = con.getErrorStream();
		}

		BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));

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
