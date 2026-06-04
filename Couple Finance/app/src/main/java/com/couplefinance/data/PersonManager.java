package com.couplefinance.data;

import android.os.Handler;
import android.os.Looper;

import com.couplefinance.AuthManager;
import com.couplefinance.data.FirestoreManager;
import com.couplefinance.utils.FirebaseConfig;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class PersonManager {

	private static PersonManager instance;

	private final Executor executor = Executors.newSingleThreadExecutor();
	private final Handler handler = new Handler(Looper.getMainLooper());

	public static PersonManager getInstance() {
		if (instance == null) {
			instance = new PersonManager();
		}
		return instance;
	}

	private String getHouseholdPath() {
		return "households/" + HouseholdManager.getInstance().getHouseholdId();
	}

	private String getPersonsPath() {
		return getHouseholdPath() + "/persons";
	}

	// ─── PERSONNES ──────────────────────────────────────────

	public void getPersons(FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getToken();

				HttpURLConnection conn = openGet(getPersonsPath(), token);
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

	public void addPerson(String name, FirestoreManager.Callback cb) {
		if (name == null || name.trim().isEmpty() || name.equals("Moi")) {
			postError(cb, "Nom invalide: " + name);
			return;
		}

		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getToken();

				HttpURLConnection checkConn = openGet(getPersonsPath(), token);
				if (checkConn != null && checkConn.getResponseCode() == 200) {
					String response = safeRead(checkConn.getInputStream());

					if (response.toLowerCase().contains("\"stringvalue\":\"" + name.toLowerCase() + "\"")
							|| response.toLowerCase().contains("\"stringvalue\": \"" + name.toLowerCase() + "\"")) {
						postSuccess(cb, "already_exists");
						return;
					}
				}

				HttpURLConnection conn = openPost(getPersonsPath(), token);
				if (conn == null) {
					postError(cb, "Connection failed");
					return;
				}

				String body = "{\"fields\":{"
						+ "\"name\":{\"stringValue\":\"" + escapeJson(name) + "\"},"
						+ "\"createdAt\":{\"integerValue\":\"" + System.currentTimeMillis() + "\"}"
						+ "}}";

				send(conn, body, cb);

			} catch (Exception e) {
				postError(cb, e.getMessage());
			}
		});
	}

	public void deletePerson(String docPath, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getToken();

				HttpURLConnection conn = openDelete(cleanDocUrl(docPath), token);
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

	public void deletePersonFromHousehold(String name, FirestoreManager.Callback cb) {
		if (name == null || name.trim().isEmpty()) {
			postSuccess(cb, "not_found");
			return;
		}

		getPersons(new FirestoreManager.Callback() {
			public void onSuccess(String response) {
				String foundPath = findPersonDocPathByName(response, name);

				if (foundPath == null || foundPath.isEmpty()) {
					postSuccess(cb, "not_found");
					return;
				}

				deletePerson(foundPath, cb);
			}

			public void onError(String error) {
				postError(cb, error);
			}
		});
	}

	// ─── OWNER / FOYER ──────────────────────────────────────

	public void getHouseholdOwner(FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getToken();

				HttpURLConnection conn = openGet(getHouseholdPath(), token);
				if (conn == null) {
					postError(cb, "Connection failed");
					return;
				}

				int code = conn.getResponseCode();

				if (code == 200) {
					String response = safeRead(conn.getInputStream());
					String owner = extractFieldString(response, "owner");
					postSuccess(cb, owner);
				} else {
					postError(cb, "Code: " + code);
				}

			} catch (Exception e) {
				postError(cb, e.getMessage());
			}
		});
	}

	// ─── REVENU ─────────────────────────────────────────────

	public void updatePersonRevenue(String docPath, double revenue, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getToken();

				String cleanPath = cleanDocumentPath(docPath);
				String urlStr = FirebaseConfig.documentUpdateUrl(cleanPath, "revenue");

				HttpURLConnection conn = openPatch(urlStr, token);
				if (conn == null) {
					postError(cb, "Connection failed");
					return;
				}

				String body = "{\"fields\":{"
						+ "\"revenue\":{\"doubleValue\":" + revenue + "}"
						+ "}}";

				send(conn, body, cb);

			} catch (Exception e) {
				postError(cb, e.getMessage());
			}
		});
	}

	public void getPersonRevenue(String docPath, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getToken();

				HttpURLConnection conn = openGet(cleanDocumentPath(docPath), token);
				if (conn == null) {
					postSuccess(cb, "0");
					return;
				}

				int code = conn.getResponseCode();

				if (code == 200) {
					String response = safeRead(conn.getInputStream());
					String revenue = extractFirestoreNumber(response, "revenue");
					postSuccess(cb, revenue.isEmpty() ? "0" : revenue);
				} else {
					postSuccess(cb, "0");
				}

			} catch (Exception e) {
				postSuccess(cb, "0");
			}
		});
	}

	// ─── COMPTE PRINCIPAL ───────────────────────────────────

	public void updateMainAccount(String personName, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getToken();

				String urlStr = FirebaseConfig.documentUpdateUrl(getHouseholdPath(), "mainAccount");

				HttpURLConnection conn = openPatch(urlStr, token);
				if (conn == null) {
					postError(cb, "Connection failed");
					return;
				}

				String body = "{\"fields\":{"
						+ "\"mainAccount\":{\"stringValue\":\"" + escapeJson(personName) + "\"}"
						+ "}}";

				send(conn, body, cb);

			} catch (Exception e) {
				postError(cb, e.getMessage());
			}
		});
	}

	public void getMainAccount(FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getToken();

				HttpURLConnection conn = openGet(getHouseholdPath(), token);
				if (conn == null) {
					postSuccess(cb, "");
					return;
				}

				int code = conn.getResponseCode();

				if (code == 200) {
					String response = safeRead(conn.getInputStream());
					postSuccess(cb, extractFieldString(response, "mainAccount"));
				} else {
					postSuccess(cb, "");
				}

			} catch (Exception e) {
				postSuccess(cb, "");
			}
		});
	}

	// ─── HTTP ───────────────────────────────────────────────

	private HttpURLConnection openGet(String path, String token) {
		try {
			URL url = new URL(FirebaseConfig.documentUrl(path));
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			conn.setRequestMethod("GET");
			conn.setRequestProperty("Content-Type", "application/json");

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

	private HttpURLConnection openPost(String path, String token) {
		try {
			URL url = new URL(FirebaseConfig.documentUrl(path));
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");

			if (token != null && !token.isEmpty()) {
				conn.setRequestProperty("Authorization", "Bearer " + token);
			}

			conn.setConnectTimeout(10000);
			conn.setReadTimeout(10000);
			conn.setDoOutput(true);

			return conn;

		} catch (Exception e) {
			return null;
		}
	}

	private HttpURLConnection openPatch(String urlStr, String token) {
		try {
			URL url = new URL(urlStr);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			conn.setRequestMethod("PATCH");
			conn.setRequestProperty("Content-Type", "application/json");

			if (token != null && !token.isEmpty()) {
				conn.setRequestProperty("Authorization", "Bearer " + token);
			}

			conn.setConnectTimeout(10000);
			conn.setReadTimeout(10000);
			conn.setDoOutput(true);

			return conn;

		} catch (Exception e) {
			return null;
		}
	}

	private HttpURLConnection openDelete(String fullUrl, String token) {
		try {
			URL url = new URL(fullUrl);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

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
			DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
			dos.write(body.getBytes("UTF-8"));
			dos.flush();
			dos.close();

			int code = conn.getResponseCode();

			String response = safeRead(code == 200 || code == 201
					? conn.getInputStream()
					: conn.getErrorStream());

			if (code == 200 || code == 201) {
				postSuccess(cb, response);
			} else {
				postError(cb, "Code: " + code + " - " + response);
			}

		} catch (Exception e) {
			postError(cb, e.getMessage());
		}
	}

	// ─── HELPERS ────────────────────────────────────────────

	private String cleanDocumentPath(String docPath) {
		if (docPath == null) {
			docPath = "";
		}

		String path = docPath.trim();

		if (path.startsWith("projects/")) {
			int marker = path.indexOf("/documents/");
			if (marker >= 0) {
				return path.substring(marker + "/documents/".length());
			}
		}

		if (path.contains("/persons/")) {
			return path;
		}

		return getPersonsPath() + "/" + path;
	}

	private String cleanDocUrl(String docPath) {
		if (docPath == null) {
			docPath = "";
		}

		String path = docPath.trim();

		if (path.startsWith("projects/")) {
			return FirebaseConfig.apiRootUrl(path);
		}

		if (path.contains("/persons/")) {
			return FirebaseConfig.documentUrl(path);
		}

		return FirebaseConfig.documentUrl(getPersonsPath() + "/" + path);
	}

	private String findPersonDocPathByName(String json, String targetName) {
		int cursor = 0;

		while (true) {
			String m1 = "\"name\": \"projects/";
			String m2 = "\"name\":\"projects/";

			int i1 = json.indexOf(m1, cursor);
			int i2 = json.indexOf(m2, cursor);

			if (i1 < 0 && i2 < 0) {
				break;
			}

			int idx;
			String marker;

			if (i1 < 0) {
				idx = i2;
				marker = m2;
			} else if (i2 < 0) {
				idx = i1;
				marker = m1;
			} else if (i1 < i2) {
				idx = i1;
				marker = m1;
			} else {
				idx = i2;
				marker = m2;
			}

			int pathStart = idx + marker.length();
			int pathEnd = json.indexOf("\"", pathStart);

			if (pathEnd < 0) {
				break;
			}

			String docFullPath = "projects/" + json.substring(pathStart, pathEnd);
			cursor = pathEnd;

			if (!docFullPath.contains("/persons/")) {
				continue;
			}

			int fieldsIdx = json.indexOf("\"fields\"", cursor);
			if (fieldsIdx < 0) {
				break;
			}

			String block = json.substring(fieldsIdx, Math.min(json.length(), fieldsIdx + 600));
			String name = extractFieldString(block, "name");

			if (name.equalsIgnoreCase(targetName)) {
				return docFullPath;
			}
		}

		return "";
	}

	private String extractFieldString(String json, String fieldName) {
		if (json == null || json.isEmpty()) {
			return "";
		}

		String marker = "\"" + fieldName + "\"";
		int fieldIndex = json.indexOf(marker);

		if (fieldIndex < 0) {
			return "";
		}

		String sub = json.substring(fieldIndex, Math.min(json.length(), fieldIndex + 300));

		String[] searches = {
				"\"stringValue\": \"",
				"\"stringValue\":\""
		};

		for (String search : searches) {
			int start = sub.indexOf(search);

			if (start >= 0) {
				start += search.length();
				int end = sub.indexOf("\"", start);

				if (end > start) {
					return sub.substring(start, end).trim();
				}
			}
		}

		return "";
	}

	private String extractFirestoreNumber(String json, String fieldName) {
		if (json == null || json.isEmpty()) {
			return "";
		}

		String marker = "\"" + fieldName + "\"";
		int fieldIndex = json.indexOf(marker);

		if (fieldIndex < 0) {
			return "";
		}

		String sub = json.substring(fieldIndex, Math.min(json.length(), fieldIndex + 300));

		String[] keys = {
				"\"doubleValue\":",
				"\"integerValue\":"
		};

		for (String key : keys) {
			int i = sub.indexOf(key);

			if (i >= 0) {
				i += key.length();

				while (i < sub.length() && (sub.charAt(i) == ' ' || sub.charAt(i) == '"')) {
					i++;
				}

				int e = i;

				while (e < sub.length()
						&& (Character.isDigit(sub.charAt(e))
						|| sub.charAt(e) == '.'
						|| sub.charAt(e) == '-')) {
					e++;
				}

				if (e > i) {
					return sub.substring(i, e);
				}
			}
		}

		return "";
	}

	private String safeRead(InputStream is) {
		if (is == null) {
			return "";
		}

		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(is));
			StringBuilder sb = new StringBuilder();
			String line;

			while ((line = br.readLine()) != null) {
				sb.append(line);
			}

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