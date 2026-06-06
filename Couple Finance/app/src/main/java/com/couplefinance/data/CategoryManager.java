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

public class CategoryManager {

	private static volatile CategoryManager instance;

	private final Executor executor = Executors.newFixedThreadPool(2);
	private final Handler handler = new Handler(Looper.getMainLooper());

	public static CategoryManager getInstance() {
		if (instance == null) {
			synchronized (CategoryManager.class) {
				if (instance == null) instance = new CategoryManager();
			}
		}
		return instance;
	}

	private String getHouseholdPath() {
		return "households/" + HouseholdManager.getInstance().getHouseholdId();
	}

	private String getCategoriesPath() {
		return getHouseholdPath() + "/categories";
	}

	private static final java.util.Set<String> PROTECTED = new java.util.HashSet<>(java.util.Arrays.asList(
			"virements", "virement", "crédits", "crédit", "credits", "credit"));

	public void addCategory(String name, String emoji, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			HttpURLConnection conn = null;
			try {
				String token = AuthManager.getInstance().getToken();
				conn = open(FirebaseConfig.BASE_URL + getCategoriesPath() + "?key=" + FirebaseConfig.API_KEY, "POST", token, true);

				String rawName = name == null ? "" : name.trim();
				String type = "expense";
				if (rawName.endsWith("|income")) {
					type = "income";
					rawName = rawName.replace("|income", "").trim();
				} else if (rawName.endsWith("|expense")) {
					rawName = rawName.replace("|expense", "").trim();
				}

				if (PROTECTED.contains(rawName.toLowerCase(java.util.Locale.FRENCH))) {
					postError(cb, "Catégorie système réservée.");
					return;
				}

				String body = "{\"fields\":{"
						+ "\"name\":{\"stringValue\":\"" + safeJson(rawName) + "\"},"
						+ "\"type\":{\"stringValue\":\"" + safeJson(type) + "\"},"
						+ "\"emoji\":{\"stringValue\":\"" + safeJson(emoji) + "\"}"
						+ "}}";
				send(conn, body, cb);
			} catch (Exception e) {
				postError(cb, e.getMessage());
			} finally {
				if (conn != null) conn.disconnect();
			}
		});
	}

	public void getCategories(FirestoreManager.Callback cb) {
		executor.execute(() -> {
			HttpURLConnection conn = null;
			try {
				String token = AuthManager.getInstance().getToken();
				conn = open(FirebaseConfig.BASE_URL + getCategoriesPath() + "?key=" + FirebaseConfig.API_KEY, "GET", token, false);
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

	public void deleteCategory(String docPath, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			HttpURLConnection conn = null;
			try {
				String token = AuthManager.getInstance().getToken();
				String urlStr = buildDocumentUrl(docPath, "categories");
				if (!urlStr.contains("key=")) {
					urlStr += (urlStr.contains("?") ? "&key=" : "?key=") + FirebaseConfig.API_KEY;
				}
				conn = open(urlStr, "DELETE", token, false);
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

	public void setCategoryBudget(String docId, double budget, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			HttpURLConnection conn = null;
			try {
				String token = AuthManager.getInstance().getToken();
				String urlStr = FirebaseConfig.BASE_URL + getCategoriesPath() + "/" + docId
						+ "?updateMask.fieldPaths=budget&key=" + FirebaseConfig.API_KEY;
				conn = open(urlStr, "PATCH", token, true);
				String body = "{\"fields\":{\"budget\":{\"doubleValue\":" + budget + "}}}";
				try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
					dos.write(body.getBytes("UTF-8"));
				}
				int code = conn.getResponseCode();
				if (code == 200) {
					postSuccess(cb, "ok");
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
			if (body != null) {
				try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
					dos.write(body.getBytes("UTF-8"));
				}
			}
			int code = conn.getResponseCode();
			String response = safeRead(code == 200 || code == 201 ? conn.getInputStream() : conn.getErrorStream());
			if (code == 200 || code == 201) {
				postSuccess(cb, response);
			} else {
				postError(cb, response);
			}
		} catch (Exception e) {
			postError(cb, e.getMessage());
		}
	}

	private String buildDocumentUrl(String docPath, String collection) {
		String path = docPath == null ? "" : docPath.trim();

		if (path.startsWith("projects/")) {
			return "https://firestore.googleapis.com/v1/" + path;
		}

		return FirebaseConfig.BASE_URL + getHouseholdPath() + "/" + collection + "/" + path;
	}

	private String safeJson(String value) {
		if (value == null)
			return "";

		return value
				.replace("\\", "\\\\")
				.replace("\"", "\\\"");
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

	private void postSuccess(FirestoreManager.Callback cb, String response) {
		if (cb != null)
			handler.post(() -> cb.onSuccess(response));
	}

	private void postError(FirestoreManager.Callback cb, String error) {
		if (cb != null)
			handler.post(() -> cb.onError(error));
	}
}