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

public class EventManager {

	private static volatile EventManager instance;

	private final Executor executor = Executors.newSingleThreadExecutor();
	private final Handler handler = new Handler(Looper.getMainLooper());

	public static EventManager getInstance() {
		if (instance == null) {
			synchronized (EventManager.class) {
				if (instance == null) instance = new EventManager();
			}
		}
		return instance;
	}

	private String getHouseholdPath() {
		return "households/" + HouseholdManager.getInstance().getHouseholdId();
	}

	private String getCollectionPath() {
		return getHouseholdPath() + "/events";
	}

	public void addEvent(String title, String type, double amount, long date, String person, String note,
			FirestoreManager.Callback cb) {

		if (title == null || title.trim().isEmpty()) {
			postError(cb, "Titre invalide");
			return;
		}

		executor.execute(() -> {
			HttpURLConnection conn = null;
			try {
				String token = AuthManager.getInstance().getToken();
				conn = open(FirebaseConfig.documentUrl(getCollectionPath()), "POST", token, true);
				String body = "{\"fields\":{"
						+ "\"title\":{\"stringValue\":\"" + escapeJson(title) + "\"},"
						+ "\"type\":{\"stringValue\":\"" + escapeJson(type) + "\"},"
						+ "\"amount\":{\"doubleValue\":" + amount + "},"
						+ "\"date\":{\"integerValue\":\"" + date + "\"},"
						+ "\"person\":{\"stringValue\":\"" + escapeJson(person) + "\"},"
						+ "\"note\":{\"stringValue\":\"" + escapeJson(note) + "\"},"
						+ "\"createdAt\":{\"integerValue\":\"" + System.currentTimeMillis() + "\"}"
						+ "}}";
				send(conn, body, cb);
			} catch (Exception e) {
				postError(cb, e.getMessage());
			} finally {
				if (conn != null) conn.disconnect();
			}
		});
	}

	public void getEvents(FirestoreManager.Callback cb) {
		executor.execute(() -> {
			HttpURLConnection conn = null;
			try {
				String token = AuthManager.getInstance().getToken();
				conn = open(FirebaseConfig.documentUrl(getCollectionPath()), "GET", token, false);
				int code = conn.getResponseCode();
				if (code == 200) {
					postSuccess(cb, safeRead(conn.getInputStream()));
				} else {
					postSuccess(cb, "{\"documents\":[]}");
				}
			} catch (Exception e) {
				postSuccess(cb, "{\"documents\":[]}");
			} finally {
				if (conn != null) conn.disconnect();
			}
		});
	}

	public void deleteEvent(String docPath, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			HttpURLConnection conn = null;
			try {
				String token = AuthManager.getInstance().getToken();
				conn = open(cleanDocUrl(docPath), "DELETE", token, false);
				int code = conn.getResponseCode();
				if (code == 200 || code == 204) {
					postSuccess(cb, "deleted");
				} else {
					postError(cb, "Code: " + code + " - " + safeRead(conn.getErrorStream()));
				}
			} catch (Exception e) {
				postError(cb, e.getMessage());
			} finally {
				if (conn != null) conn.disconnect();
			}
		});
	}

	private HttpURLConnection open(String urlStr, String method, String token, boolean output) throws Exception {
		HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
		conn.setRequestMethod(method);
		conn.setRequestProperty("Content-Type", "application/json");
		if (token != null && !token.isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + token);
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
				postSuccess(cb, response);
			} else {
				postError(cb, "Code: " + code + " - " + response);
			}
		} catch (Exception e) {
			postError(cb, e.getMessage());
		}
	}

	private String cleanDocUrl(String docPath) {
		if (docPath == null) {
			docPath = "";
		}

		String path = docPath.trim();

		if (path.startsWith("projects/")) {
			return FirebaseConfig.apiRootUrl(path);
		}

		if (path.contains("/events/")) {
			return FirebaseConfig.documentUrl(path);
		}

		return FirebaseConfig.documentUrl(getCollectionPath() + "/" + path);
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

	private String escapeJson(String value) {
		if (value == null) {
			return "";
		}

		return value
				.replace("\\", "\\\\")
				.replace("\"", "\\\"");
	}

	private void postSuccess(FirestoreManager.Callback cb, String response) {
		handler.post(() -> cb.onSuccess(response));
	}

	private void postError(FirestoreManager.Callback cb, String error) {
		handler.post(() -> cb.onError(error));
	}
}