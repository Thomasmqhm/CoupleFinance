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

public class PersonManager {

	private static volatile PersonManager instance;

	private final Executor executor = Executors.newSingleThreadExecutor();
	private final Handler  handler  = new Handler(Looper.getMainLooper());

	public static PersonManager getInstance() {
		if (instance == null) {
			synchronized (PersonManager.class) {
				if (instance == null) instance = new PersonManager();
			}
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
			HttpURLConnection conn = null;
			try {
				String token = AuthManager.getInstance().getToken();
				conn = open(FirebaseConfig.documentUrl(getPersonsPath()), "GET", token, false);
				if (conn.getResponseCode() == 200) {
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

	public void addPerson(String name, FirestoreManager.Callback cb) {
		if (name == null || name.trim().isEmpty() || name.equals("Moi")) {
			postError(cb, "Nom invalide: " + name);
			return;
		}
		executor.execute(() -> {
			HttpURLConnection checkConn = null;
			HttpURLConnection conn = null;
			try {
				String token = AuthManager.getInstance().getToken();

				// Vérifier doublon avant d'ajouter
				checkConn = open(FirebaseConfig.documentUrl(getPersonsPath()), "GET", token, false);
				if (checkConn.getResponseCode() == 200) {
					String response = safeRead(checkConn.getInputStream());
					String nameLower = name.toLowerCase();
					if (response.toLowerCase().contains("\"stringvalue\":\"" + nameLower + "\"")
							|| response.toLowerCase().contains("\"stringvalue\": \"" + nameLower + "\"")) {
						postSuccess(cb, "already_exists");
						return;
					}
				}

				conn = open(FirebaseConfig.documentUrl(getPersonsPath()), "POST", token, true);
				String body = "{\"fields\":{"
						+ "\"name\":{\"stringValue\":\"" + escapeJson(name) + "\"},"
						+ "\"createdAt\":{\"integerValue\":\"" + System.currentTimeMillis() + "\"}"
						+ "}}";
				send(conn, body, cb);
			} catch (Exception e) {
				postError(cb, e.getMessage());
			} finally {
				if (checkConn != null) checkConn.disconnect();
				if (conn != null) conn.disconnect();
			}
		});
	}

	public void deletePerson(String docPath, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			HttpURLConnection conn = null;
			try {
				String token = AuthManager.getInstance().getToken();
				conn = open(cleanDocUrl(docPath), "DELETE", token, false);
				int code = conn.getResponseCode();
				if (code == 200 || code == 204) {
					postSuccess(cb, "deleted");
				} else {
					final String err = safeRead(conn.getErrorStream());
					postError(cb, "Code: " + code + " - " + err);
				}
			} catch (Exception e) {
				postError(cb, e.getMessage());
			} finally {
				if (conn != null) conn.disconnect();
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
			public void onError(String error) { postError(cb, error); }
		});
	}

	// ─── OWNER / FOYER ──────────────────────────────────────

	public void getHouseholdOwner(FirestoreManager.Callback cb) {
		executor.execute(() -> {
			HttpURLConnection conn = null;
			try {
				String token = AuthManager.getInstance().getToken();
				conn = open(FirebaseConfig.documentUrl(getHouseholdPath()), "GET", token, false);
				if (conn.getResponseCode() == 200) {
					String response = safeRead(conn.getInputStream());
					postSuccess(cb, extractFieldString(response, "owner"));
				} else {
					postError(cb, "Code: " + conn.getResponseCode());
				}
			} catch (Exception e) {
				postError(cb, e.getMessage());
			} finally {
				if (conn != null) conn.disconnect();
			}
		});
	}

	// ─── REVENU ─────────────────────────────────────────────

	public void updatePersonRevenue(String docPath, double revenue, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			HttpURLConnection conn = null;
			try {
				String token  = AuthManager.getInstance().getToken();
				String urlStr = FirebaseConfig.documentUpdateUrl(cleanDocumentPath(docPath), "revenue");
				conn = open(urlStr, "PATCH", token, true);
				String body = "{\"fields\":{\"revenue\":{\"doubleValue\":" + revenue + "}}}";
				send(conn, body, cb);
			} catch (Exception e) {
				postError(cb, e.getMessage());
			} finally {
				if (conn != null) conn.disconnect();
			}
		});
	}

	public void getPersonRevenue(String docPath, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			HttpURLConnection conn = null;
			try {
				String token = AuthManager.getInstance().getToken();
				conn = open(FirebaseConfig.documentUrl(cleanDocumentPath(docPath)), "GET", token, false);
				if (conn.getResponseCode() == 200) {
					String response = safeRead(conn.getInputStream());
					String revenue = extractFirestoreNumber(response, "revenue");
					postSuccess(cb, revenue.isEmpty() ? "0" : revenue);
				} else {
					postSuccess(cb, "0");
				}
			} catch (Exception e) {
				postSuccess(cb, "0");
			} finally {
				if (conn != null) conn.disconnect();
			}
		});
	}

	// ─── COMPTE PRINCIPAL ───────────────────────────────────

	public void updateMainAccount(String personName, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			HttpURLConnection conn = null;
			try {
				String token  = AuthManager.getInstance().getToken();
				String urlStr = FirebaseConfig.documentUpdateUrl(getHouseholdPath(), "mainAccount");
				conn = open(urlStr, "PATCH", token, true);
				String body = "{\"fields\":{\"mainAccount\":{\"stringValue\":\""
						+ escapeJson(personName) + "\"}}}";
				send(conn, body, cb);
			} catch (Exception e) {
				postError(cb, e.getMessage());
			} finally {
				if (conn != null) conn.disconnect();
			}
		});
	}

	public void getMainAccount(FirestoreManager.Callback cb) {
		executor.execute(() -> {
			HttpURLConnection conn = null;
			try {
				String token = AuthManager.getInstance().getToken();
				conn = open(FirebaseConfig.documentUrl(getHouseholdPath()), "GET", token, false);
				if (conn.getResponseCode() == 200) {
					String response = safeRead(conn.getInputStream());
					postSuccess(cb, extractFieldString(response, "mainAccount"));
				} else {
					postSuccess(cb, "");
				}
			} catch (Exception e) {
				postSuccess(cb, "");
			} finally {
				if (conn != null) conn.disconnect();
			}
		});
	}

	// ─── HTTP ───────────────────────────────────────────────

	private HttpURLConnection open(String urlStr, String method, String token, boolean output) throws Exception {
		HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
		conn.setRequestMethod(method);
		conn.setRequestProperty("Content-Type", "application/json");
		if (token != null && !token.isEmpty())
			conn.setRequestProperty("Authorization", "Bearer " + token);
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
			final String response = safeRead(
					code == 200 || code == 201 ? conn.getInputStream() : conn.getErrorStream());
			if (code == 200 || code == 201) {
				postSuccess(cb, response);
			} else {
				postError(cb, "Code: " + code + " - " + response);
			}
		} catch (Exception e) {
			postError(cb, e.getMessage());
		}
		// disconnect() appelé par le bloc finally de l'appelant
	}

	// ─── HELPERS ────────────────────────────────────────────

	private String cleanDocumentPath(String docPath) {
		if (docPath == null) docPath = "";
		String path = docPath.trim();
		if (path.startsWith("projects/")) {
			int marker = path.indexOf("/documents/");
			if (marker >= 0) return path.substring(marker + "/documents/".length());
		}
		if (path.contains("/persons/")) return path;
		return getPersonsPath() + "/" + path;
	}

	private String cleanDocUrl(String docPath) {
		if (docPath == null) docPath = "";
		String path = docPath.trim();
		if (path.startsWith("projects/")) return FirebaseConfig.apiRootUrl(path);
		if (path.contains("/persons/")) return FirebaseConfig.documentUrl(path);
		return FirebaseConfig.documentUrl(getPersonsPath() + "/" + path);
	}

	private String findPersonDocPathByName(String json, String targetName) {
		int cursor = 0;
		while (true) {
			String m1 = "\"name\": \"projects/";
			String m2 = "\"name\":\"projects/";
			int i1 = json.indexOf(m1, cursor);
			int i2 = json.indexOf(m2, cursor);
			if (i1 < 0 && i2 < 0) break;
			int idx;
			String marker;
			if (i1 < 0) { idx = i2; marker = m2; }
			else if (i2 < 0) { idx = i1; marker = m1; }
			else if (i1 < i2) { idx = i1; marker = m1; }
			else { idx = i2; marker = m2; }
			int pathStart = idx + marker.length();
			int pathEnd   = json.indexOf("\"", pathStart);
			if (pathEnd < 0) break;
			String docFullPath = "projects/" + json.substring(pathStart, pathEnd);
			cursor = pathEnd;
			if (!docFullPath.contains("/persons/")) continue;
			int fieldsIdx = json.indexOf("\"fields\"", cursor);
			if (fieldsIdx < 0) break;
			String block = json.substring(fieldsIdx, Math.min(json.length(), fieldsIdx + 600));
			if (extractFieldString(block, "name").equalsIgnoreCase(targetName)) return docFullPath;
		}
		return "";
	}

	private String extractFieldString(String json, String fieldName) {
		if (json == null || json.isEmpty()) return "";
		int fieldIndex = json.indexOf("\"" + fieldName + "\"");
		if (fieldIndex < 0) return "";
		String sub = json.substring(fieldIndex, Math.min(json.length(), fieldIndex + 300));
		for (String search : new String[]{"\"stringValue\": \"", "\"stringValue\":\""}) {
			int start = sub.indexOf(search);
			if (start >= 0) {
				start += search.length();
				int end = sub.indexOf("\"", start);
				if (end > start) return sub.substring(start, end).trim();
			}
		}
		return "";
	}

	private String extractFirestoreNumber(String json, String fieldName) {
		if (json == null || json.isEmpty()) return "";
		int fieldIndex = json.indexOf("\"" + fieldName + "\"");
		if (fieldIndex < 0) return "";
		String sub = json.substring(fieldIndex, Math.min(json.length(), fieldIndex + 300));
		for (String key : new String[]{"\"doubleValue\":", "\"integerValue\":"}) {
			int i = sub.indexOf(key);
			if (i >= 0) {
				i += key.length();
				while (i < sub.length() && (sub.charAt(i) == ' ' || sub.charAt(i) == '"')) i++;
				int e = i;
				while (e < sub.length()
						&& (Character.isDigit(sub.charAt(e)) || sub.charAt(e) == '.' || sub.charAt(e) == '-')) e++;
				if (e > i) return sub.substring(i, e);
			}
		}
		return "";
	}

	private String safeRead(InputStream is) {
		if (is == null) return "";
		try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = br.readLine()) != null) sb.append(line);
			return sb.toString();
		} catch (Exception e) { return ""; }
	}

	private String escapeJson(String value) {
		if (value == null) return "";
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private void postSuccess(FirestoreManager.Callback cb, String response) {
		handler.post(() -> cb.onSuccess(response));
	}

	private void postError(FirestoreManager.Callback cb, String error) {
		handler.post(() -> cb.onError(error));
	}
}
