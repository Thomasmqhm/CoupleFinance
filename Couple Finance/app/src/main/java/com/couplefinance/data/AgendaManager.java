package com.couplefinance.data;

import android.os.Handler;
import android.os.Looper;

import com.couplefinance.AuthManager;
import com.couplefinance.data.FirestoreManager;
import com.couplefinance.utils.FirebaseConfig;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AgendaManager {

	private static final String PROJECT_ID = FirebaseConfig.PROJECT_ID;
	private static final String API_KEY = FirebaseConfig.API_KEY;

	private static final String BASE_URL = "https://firestore.googleapis.com/v1/projects/"
			+ PROJECT_ID + "/databases/(default)/documents/";

	private static volatile AgendaManager instance;

	private final Executor executor = Executors.newFixedThreadPool(2);
	private final Handler handler = new Handler(Looper.getMainLooper());

	public static AgendaManager getInstance() {
		if (instance == null) {
			synchronized (AgendaManager.class) {
				if (instance == null) instance = new AgendaManager();
			}
		}
		return instance;
	}

	private String getHouseholdPath() {
		return "households/" + HouseholdManager.getInstance().getHouseholdId();
	}

	public void addEvent(String title, String type, double amount, long date,
			String person, String note, FirestoreManager.Callback cb) {

		executor.execute(() -> {
			HttpURLConnection conn = null;
			try {
				String token = AuthManager.getInstance().getFreshTokenSync();
				conn = open(getHouseholdPath() + "/events", "POST", token, true);
				String body = "{\"fields\":{"
						+ "\"title\":{\"stringValue\":\"" + escape(title) + "\"},"
						+ "\"type\":{\"stringValue\":\"" + escape(type) + "\"},"
						+ "\"amount\":{\"doubleValue\":" + amount + "},"
						+ "\"date\":{\"integerValue\":\"" + date + "\"},"
						+ "\"person\":{\"stringValue\":\"" + escape(person) + "\"},"
						+ "\"note\":{\"stringValue\":\"" + escape(note) + "\"}"
						+ "}}";
				send(conn, body, cb);
			} catch (Exception e) {
				handler.post(() -> cb.onError(e.getMessage()));
			} finally {
				if (conn != null) conn.disconnect();
			}
		});
	}

	public void getEvents(FirestoreManager.Callback cb) {
		executor.execute(() -> {
			HttpURLConnection conn = null;
			try {
				String token = AuthManager.getInstance().getFreshTokenSync();
				conn = open(getHouseholdPath() + "/events", "GET", token, false);
				int code = conn.getResponseCode();
				if (code == 200) {
					String response = safeRead(conn.getInputStream());
					handler.post(() -> cb.onSuccess(response));
				} else {
					handler.post(() -> cb.onSuccess("{\"documents\":[]}"));
				}
			} catch (Exception e) {
				handler.post(() -> cb.onSuccess("{\"documents\":[]}"));
			} finally {
				if (conn != null) conn.disconnect();
			}
		});
	}

	public void deleteEvent(String docPath, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			HttpURLConnection conn = null;
			try {
				String token = AuthManager.getInstance().getFreshTokenSync();
				String urlStr;
				if (docPath.startsWith("projects/")) {
					urlStr = "https://firestore.googleapis.com/v1/" + docPath + "?key=" + API_KEY;
				} else {
					urlStr = BASE_URL + getHouseholdPath() + "/events/" + docPath + "?key=" + API_KEY;
				}
				conn = openRaw(urlStr, "DELETE", token, false);
				int code = conn.getResponseCode();
				if (code == 200 || code == 204) {
					handler.post(() -> cb.onSuccess("deleted"));
				} else {
					String error = safeRead(conn.getErrorStream());
					handler.post(() -> cb.onError("Code: " + code + " - " + error));
				}
			} catch (Exception e) {
				handler.post(() -> cb.onError(e.getMessage()));
			} finally {
				if (conn != null) conn.disconnect();
			}
		});
	}

	private HttpURLConnection open(String path, String method, String token, boolean output) throws Exception {
		String urlStr = BASE_URL + path + "?key=" + API_KEY;
		return openRaw(urlStr, method, token, output);
	}

	private HttpURLConnection openRaw(String urlStr, String method, String token, boolean output) throws Exception {
		HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
		conn.setRequestMethod(method);
		conn.setRequestProperty("Content-Type", "application/json");

		if (token != null && !token.isEmpty()) {
			conn.setRequestProperty("Authorization", "Bearer " + token);
		}

		conn.setConnectTimeout(10000);
		conn.setReadTimeout(10000);
		conn.setDoOutput(output);

		return conn;
	}

	private void send(HttpURLConnection conn, String body, FirestoreManager.Callback cb) {
		try {
			try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
				dos.write(body.getBytes("UTF-8"));
			}
			int code = conn.getResponseCode();
			String response = safeRead(code == 200 || code == 201 ? conn.getInputStream() : conn.getErrorStream());
			if (code == 200 || code == 201) {
				handler.post(() -> cb.onSuccess(response));
			} else {
				handler.post(() -> cb.onError("Code: " + code + " - " + response));
			}
		} catch (Exception e) {
			handler.post(() -> cb.onError(e.getMessage()));
		}
	}

	private String safeRead(InputStream is) {
		if (is == null) return "";
		try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = br.readLine()) != null) sb.append(line);
			return sb.toString();
		} catch (Exception e) {
			return "";
		}
	}

	private String escape(String value) {
		if (value == null) return "";
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}