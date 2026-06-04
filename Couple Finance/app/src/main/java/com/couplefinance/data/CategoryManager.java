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

	private static CategoryManager instance;

	private final Executor executor = Executors.newFixedThreadPool(2);
	private final Handler handler = new Handler(Looper.getMainLooper());

	public static CategoryManager getInstance() {
		if (instance == null)
			instance = new CategoryManager();
		return instance;
	}

	private String getHouseholdPath() {
		return "households/" + HouseholdManager.getInstance().getHouseholdId();
	}

	private String getCategoriesPath() {
		return getHouseholdPath() + "/categories";
	}

	public void addCategory(String name, String emoji, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getToken();

				HttpURLConnection conn = openPost(getCategoriesPath(), token);
				if (conn == null) {
					postError(cb, "Connection failed");
					return;
				}

				String rawName = name == null ? "" : name.trim();
				String type = "expense";

				if (rawName.endsWith("|income")) {
					type = "income";
					rawName = rawName.replace("|income", "").trim();
				} else if (rawName.endsWith("|expense")) {
					type = "expense";
					rawName = rawName.replace("|expense", "").trim();
				}

				String safeName = safeJson(rawName);
				String safeEmoji = safeJson(emoji);
				String safeType = safeJson(type);

				String body = "{\"fields\":{"
						+ "\"name\":{\"stringValue\":\"" + safeName + "\"},"
						+ "\"type\":{\"stringValue\":\"" + safeType + "\"},"
						+ "\"emoji\":{\"stringValue\":\"" + safeEmoji + "\"}"
						+ "}}";

				send(conn, body, cb);

			} catch (Exception e) {
				postError(cb, e.getMessage());
			}
		});
	}

	public void getCategories(FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getToken();

				HttpURLConnection conn = openGet(getCategoriesPath(), token);
				if (conn == null) {
					postSuccess(cb, "{\"documents\":[]}");
					return;
				}

				int code = conn.getResponseCode();

				if (code == 200) {
					postSuccess(cb, safeRead(conn.getInputStream()));
				} else {
					postSuccess(cb, "{\"documents\":[]}");
				}

			} catch (Exception e) {
				postSuccess(cb, "{\"documents\":[]}");
			}
		});
	}

	public void deleteCategory(String docPath, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getToken();

				String url = buildDocumentUrl(docPath, "categories");
				HttpURLConnection conn = openDelete(url, token);

				if (conn == null) {
					postError(cb, "Connection failed");
					return;
				}

				int code = conn.getResponseCode();

				if (code == 200 || code == 204) {
					postSuccess(cb, "deleted");
				} else {
					postError(cb, "Code: " + code + " - " + safeRead(conn.getErrorStream()));
				}

			} catch (Exception e) {
				postError(cb, e.getMessage());
			}
		});
	}

	public void setCategoryBudget(String docId, double budget, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getToken();

				String urlStr = FirebaseConfig.BASE_URL
						+ getCategoriesPath()
						+ "/"
						+ docId
						+ "?updateMask.fieldPaths=budget&key="
						+ FirebaseConfig.API_KEY;

				HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
				conn.setRequestMethod("PATCH");
				conn.setRequestProperty("Content-Type", "application/json");

				if (token != null && !token.isEmpty()) {
					conn.setRequestProperty("Authorization", "Bearer " + token);
				}

				conn.setConnectTimeout(10000);
				conn.setReadTimeout(10000);
				conn.setDoOutput(true);

				String body = "{\"fields\":{\"budget\":{\"doubleValue\":" + budget + "}}}";

				DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
				dos.write(body.getBytes("UTF-8"));
				dos.flush();
				dos.close();

				int code = conn.getResponseCode();

				if (code == 200) {
					postSuccess(cb, "ok");
				} else {
					postError(cb, "Code: " + code + " - " + safeRead(conn.getErrorStream()));
				}

			} catch (Exception e) {
				postError(cb, e.getMessage());
			}
		});
	}

	private HttpURLConnection openPost(String path, String token) {
		return open(path, "POST", token);
	}

	private HttpURLConnection openGet(String path, String token) {
		return open(path, "GET", token);
	}

	private HttpURLConnection open(String path, String method, String token) {
		try {
			URL url = new URL(FirebaseConfig.BASE_URL + path + "?key=" + FirebaseConfig.API_KEY);

			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod(method);
			conn.setRequestProperty("Content-Type", "application/json");

			if (token != null && !token.isEmpty()) {
				conn.setRequestProperty("Authorization", "Bearer " + token);
			}

			conn.setConnectTimeout(10000);
			conn.setReadTimeout(10000);

			if (!method.equals("GET") && !method.equals("DELETE")) {
				conn.setDoOutput(true);
			}

			return conn;

		} catch (Exception e) {
			return null;
		}
	}

	private HttpURLConnection openDelete(String fullUrl, String token) {
		try {
			String urlStr = fullUrl.contains("key=")
					? fullUrl
					: fullUrl + (fullUrl.contains("?") ? "&key=" : "?key=") + FirebaseConfig.API_KEY;

			HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
			conn.setRequestMethod("DELETE");

			if (token != null && !token.isEmpty()) {
				conn.setRequestProperty("Authorization", "Bearer " + token);
			}

			conn.setConnectTimeout(10000);
			conn.setReadTimeout(10000);

			return conn;

		} catch (Exception e) {
			return null;
		}
	}

	private void send(HttpURLConnection conn, String body, FirestoreManager.Callback cb) {
		try {
			if (body != null) {
				DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
				dos.write(body.getBytes("UTF-8"));
				dos.flush();
				dos.close();
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
		if (is == null)
			return "";

		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(is));
			StringBuilder sb = new StringBuilder();
			String line;

			while ((line = br.readLine()) != null)
				sb.append(line);

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