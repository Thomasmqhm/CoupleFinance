package com.couplefinance.data;

import android.os.Handler;
import android.os.Looper;

import com.couplefinance.AuthManager;
import com.couplefinance.utils.FirebaseConfig;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SavingsManager {

	private static volatile SavingsManager instance;

	private final Executor executor = Executors.newFixedThreadPool(2);
	private final Handler handler = new Handler(Looper.getMainLooper());

	public static SavingsManager getInstance() {
		if (instance == null) {
			synchronized (SavingsManager.class) {
				if (instance == null) instance = new SavingsManager();
			}
		}
		return instance;
	}

	private String getHouseholdPath() {
		return "households/" + HouseholdManager.getInstance().getHouseholdId();
	}

	public void addSaving(String name, double target, double current,
						  String emoji, String color, long targetDateMs,
						  FirestoreManager.Callback cb) {
		executor.execute(() -> {
			HttpURLConnection conn = null;
			try {
				String token = AuthManager.getInstance().getFreshTokenSync();

				String body = "{\"fields\":{"
						+ "\"name\":{\"stringValue\":\"" + escape(name) + "\"},"
						+ "\"target\":{\"doubleValue\":" + target + "},"
						+ "\"current\":{\"doubleValue\":" + current + "},"
						+ "\"emoji\":{\"stringValue\":\"" + escape(emoji) + "\"},"
						+ "\"color\":{\"stringValue\":\"" + escape(color) + "\"},"
						+ "\"targetDate\":{\"integerValue\":\"" + targetDateMs + "\"},"
						+ "\"createdAt\":{\"integerValue\":\"" + System.currentTimeMillis() + "\"}"
						+ "}}";

				conn = openRaw(FirebaseConfig.collectionUrl(getHouseholdPath() + "/savings"), "POST", token, true);
				send(conn, body, cb);

			} catch (Exception e) {
				handler.post(() -> cb.onError(e.getMessage()));
			} finally {
				if (conn != null) conn.disconnect();
			}
		});
	}

	public void addSaving(String name, double target, double current,
						  String emoji, String color,
						  FirestoreManager.Callback cb) {
		addSaving(name, target, current, emoji, color, 0L, cb);
	}

	public void getSavings(FirestoreManager.Callback cb) {
		executor.execute(() -> {
			HttpURLConnection conn = null;
			try {
				String token = AuthManager.getInstance().getFreshTokenSync();
				conn = openRaw(FirebaseConfig.collectionUrl(getHouseholdPath() + "/savings"), "GET", token, false);
				int code = conn.getResponseCode();
				if (code >= 200 && code < 300) {
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

	public void updateSavingCurrent(String docPath, double current, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			HttpURLConnection conn = null;
			try {
				String token = AuthManager.getInstance().getFreshTokenSync();
				String cleanPath = normalizeSavingDocPath(docPath);
				String body = "{\"fields\":{\"current\":{\"doubleValue\":" + current + "}}}";
				conn = openRaw(FirebaseConfig.documentUpdateUrl(cleanPath, "current"), "PATCH", token, true);
				send(conn, body, cb);
			} catch (Exception e) {
				handler.post(() -> cb.onError(e.getMessage()));
			} finally {
				if (conn != null) conn.disconnect();
			}
		});
	}

	public void updateSavingTargetDate(String docPath, long targetDateMs, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			HttpURLConnection conn = null;
			try {
				String token = AuthManager.getInstance().getFreshTokenSync();
				String cleanPath = normalizeSavingDocPath(docPath);
				String body = "{\"fields\":{\"targetDate\":{\"integerValue\":\"" + targetDateMs + "\"}}}";
				conn = openRaw(FirebaseConfig.documentUpdateUrl(cleanPath, "targetDate"), "PATCH", token, true);
				send(conn, body, cb);
			} catch (Exception e) {
				handler.post(() -> cb.onError(e.getMessage()));
			} finally {
				if (conn != null) conn.disconnect();
			}
		});
	}

	public void updateSavingFull(String docPath, String name, double target,
								  String emoji, String color, long targetDateMs,
								  FirestoreManager.Callback cb) {
		executor.execute(() -> {
			HttpURLConnection conn = null;
			try {
				String token = AuthManager.getInstance().getFreshTokenSync();
				String cleanPath = normalizeSavingDocPath(docPath);
				String body = "{\"fields\":{"
						+ "\"name\":{\"stringValue\":\"" + escape(name) + "\"},"
						+ "\"target\":{\"doubleValue\":" + target + "},"
						+ "\"emoji\":{\"stringValue\":\"" + escape(emoji) + "\"},"
						+ "\"color\":{\"stringValue\":\"" + escape(color) + "\"},"
						+ "\"targetDate\":{\"integerValue\":\"" + targetDateMs + "\"}"
						+ "}}";
				conn = openRaw(
						FirebaseConfig.documentUpdateUrl(cleanPath, "name", "target", "emoji", "color", "targetDate"),
						"PATCH", token, true);
				send(conn, body, cb);
			} catch (Exception e) {
				handler.post(() -> cb.onError(e.getMessage()));
			} finally {
				if (conn != null) conn.disconnect();
			}
		});
	}

	public void deleteSaving(String docPath, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			HttpURLConnection conn = null;
			try {
				String token = AuthManager.getInstance().getFreshTokenSync();
				String cleanPath = normalizeSavingDocPath(docPath);
				conn = openRaw(FirebaseConfig.documentUrl(cleanPath), "DELETE", token, false);
				int code = conn.getResponseCode();
				if (code >= 200 && code < 300) {
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

	private String normalizeSavingDocPath(String docPath) {
		if (docPath == null) {
			return "";
		}

		String p = docPath.trim();

		if (p.startsWith("https://firestore.googleapis.com/v1/")) {
			p = p.substring("https://firestore.googleapis.com/v1/".length());
		}

		if (p.startsWith("projects/")) {
			String marker = "/documents/";
			int index = p.indexOf(marker);
			if (index >= 0) {
				p = p.substring(index + marker.length());
			}
		}

		while (p.startsWith("/")) {
			p = p.substring(1);
		}

		if (p.contains("/savings/")) {
			return p;
		}

		if (p.contains("/")) {
			return p;
		}

		return getHouseholdPath() + "/savings/" + p;
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
			String response = safeRead(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
			if (code >= 200 && code < 300) {
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
		if (value == null) {
			return "";
		}

		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}