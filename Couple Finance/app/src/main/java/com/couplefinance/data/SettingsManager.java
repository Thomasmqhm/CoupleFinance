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

public class SettingsManager {

	private static final String PROJECT_ID = FirebaseConfig.PROJECT_ID;
	private static final String API_KEY = FirebaseConfig.API_KEY;

	private static final String BASE_URL = "https://firestore.googleapis.com/v1/projects/" + PROJECT_ID
			+ "/databases/(default)/documents/";

	private static SettingsManager instance;

	private final Executor executor = Executors.newFixedThreadPool(3);
	private final Handler handler = new Handler(Looper.getMainLooper());

	public static SettingsManager getInstance() {
		if (instance == null)
			instance = new SettingsManager();
		return instance;
	}

	private String getHouseholdPath() {
		return "households/" + HouseholdManager.getInstance().getHouseholdId();
	}

	// ─── CATEGORIES ─────────────────────────────────────────

	public void addCategory(String name, String emoji, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getFreshTokenSync();
				HttpURLConnection conn = open(getHouseholdPath() + "/categories", "POST", token, true);

				String body = "{\"fields\":{" + "\"name\":{\"stringValue\":\"" + escape(name) + "\"},"
						+ "\"emoji\":{\"stringValue\":\"" + escape(emoji) + "\"}" + "}}";

				send(conn, body, cb);

			} catch (Exception e) {
				handler.post(() -> cb.onError(e.getMessage()));
			}
		});
	}

	public void getCategories(FirestoreManager.Callback cb) {
		getCollection(getHouseholdPath() + "/categories", cb);
	}

	public void deleteCategory(String docId, FirestoreManager.Callback cb) {
		deleteDocument(getHouseholdPath() + "/categories/" + docId, cb);
	}

	public void setCategoryBudget(String docId, double budget, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getFreshTokenSync();

				String urlStr = BASE_URL + getHouseholdPath() + "/categories/" + docId + "?updateMask.fieldPaths=budget"
						+ "&key=" + API_KEY;

				HttpURLConnection conn = openRaw(urlStr, "PATCH", token, true);

				String body = "{\"fields\":{\"budget\":{\"doubleValue\":" + budget + "}}}";

				send(conn, body, cb);

			} catch (Exception e) {
				handler.post(() -> cb.onError(e.getMessage()));
			}
		});
	}

	// ─── FIXED CHARGES ──────────────────────────────────────

	public void addFixedCharge(String name, double amount, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getFreshTokenSync();
				HttpURLConnection conn = open(getHouseholdPath() + "/fixedcharges", "POST", token, true);

				String body = "{\"fields\":{" + "\"name\":{\"stringValue\":\"" + escape(name) + "\"},"
						+ "\"amount\":{\"doubleValue\":" + amount + "}" + "}}";

				send(conn, body, cb);

			} catch (Exception e) {
				handler.post(() -> cb.onError(e.getMessage()));
			}
		});
	}

	public void getFixedCharges(FirestoreManager.Callback cb) {
		getCollection(getHouseholdPath() + "/fixedcharges", cb);
	}

	public void deleteFixedCharge(String docId, FirestoreManager.Callback cb) {
		deleteDocument(getHouseholdPath() + "/fixedcharges/" + docId, cb);
	}

	// ─── PEOPLE / PERSONS ───────────────────────────────────

	public void addPerson(String name, FirestoreManager.Callback cb) {
		if (name == null || name.trim().isEmpty() || name.equals("Moi")) {
			handler.post(() -> cb.onError("Nom invalide"));
			return;
		}

		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getFreshTokenSync();
				HttpURLConnection conn = open(getHouseholdPath() + "/persons", "POST", token, true);

				String userId = AuthManager.getInstance().getUserId();
				String body = "{\"fields\":{" + "\"name\":{\"stringValue\":\"" + escape(name) + "\"},"
						+ "\"userId\":{\"stringValue\":\"" + escape(userId != null ? userId : "") + "\"}" + "}}";

				send(conn, body, cb);

			} catch (Exception e) {
				handler.post(() -> cb.onError(e.getMessage()));
			}
		});
	}

	public void deletePerson(String docPathOrId, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getFreshTokenSync();
				String urlStr;

				if (docPathOrId.startsWith("projects/")) {
					urlStr = "https://firestore.googleapis.com/v1/" + docPathOrId + "?key=" + API_KEY;
				} else {
					urlStr = BASE_URL + getHouseholdPath() + "/persons/" + docPathOrId + "?key=" + API_KEY;
				}

				HttpURLConnection conn = openRaw(urlStr, "DELETE", token, false);

				int code = conn.getResponseCode();

				if (code == 200 || code == 204) {
					handler.post(() -> cb.onSuccess("deleted"));
				} else {
					String error = safeRead(conn.getErrorStream());
					handler.post(() -> cb.onError("Code: " + code + " - " + error));
				}

			} catch (Exception e) {
				handler.post(() -> cb.onError(e.getMessage()));
			}
		});
	}

	public void deletePersonFromHousehold(String name, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getFreshTokenSync();

				String urlStr = BASE_URL + getHouseholdPath() + "/persons?key=" + API_KEY;
				HttpURLConnection conn = openRaw(urlStr, "GET", token, false);

				if (conn.getResponseCode() != 200) {
					handler.post(() -> cb.onError("Impossible de récupérer les membres"));
					return;
				}

				String response = safeRead(conn.getInputStream());
				String foundUrl = findPersonDocumentUrl(response, name);

				if (foundUrl == null) {
					handler.post(() -> cb.onSuccess("not_found"));
					return;
				}

				HttpURLConnection deleteConn = openRaw(foundUrl + "?key=" + API_KEY, "DELETE", token, false);
				int code = deleteConn.getResponseCode();

				if (code == 200 || code == 204) {
					handler.post(() -> cb.onSuccess("deleted"));
				} else {
					String error = safeRead(deleteConn.getErrorStream());
					handler.post(() -> cb.onError("Code: " + code + " - " + error));
				}

			} catch (Exception e) {
				handler.post(() -> cb.onError(e.getMessage()));
			}
		});
	}

	// ─── PERSON REVENUE ─────────────────────────────────────

	public void updatePersonRevenue(String docFullPath, double revenue, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getFreshTokenSync();

				String urlStr = "https://firestore.googleapis.com/v1/" + docFullPath + "?updateMask.fieldPaths=revenue"
						+ "&key=" + API_KEY;

				HttpURLConnection conn = openRaw(urlStr, "PATCH", token, true);

				String body = "{\"fields\":{\"revenue\":{\"doubleValue\":" + revenue + "}}}";

				send(conn, body, cb);

			} catch (Exception e) {
				handler.post(() -> cb.onError(e.getMessage()));
			}
		});
	}

	public void getPersonRevenue(String docFullPath, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getFreshTokenSync();

				String urlStr = "https://firestore.googleapis.com/v1/" + docFullPath + "?key=" + API_KEY;
				HttpURLConnection conn = openRaw(urlStr, "GET", token, false);

				if (conn.getResponseCode() == 200) {
					String response = safeRead(conn.getInputStream());
					String value = extractFirestoreNumber(response, "revenue");
					handler.post(() -> cb.onSuccess(value.isEmpty() ? "0" : value));
				} else {
					handler.post(() -> cb.onSuccess("0"));
				}

			} catch (Exception e) {
				handler.post(() -> cb.onSuccess("0"));
			}
		});
	}

	// ─── MAIN ACCOUNT / OWNER ───────────────────────────────

	public void updateMainAccount(String personName, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getFreshTokenSync();

				String urlStr = BASE_URL + getHouseholdPath() + "?updateMask.fieldPaths=mainAccount" + "&key="
						+ API_KEY;

				HttpURLConnection conn = openRaw(urlStr, "PATCH", token, true);

				String body = "{\"fields\":{" + "\"mainAccount\":{\"stringValue\":\"" + escape(personName) + "\"}"
						+ "}}";

				send(conn, body, cb);

			} catch (Exception e) {
				handler.post(() -> cb.onError(e.getMessage()));
			}
		});
	}

	public void getMainAccount(FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getFreshTokenSync();

				HttpURLConnection conn = open(getHouseholdPath(), "GET", token, false);

				if (conn.getResponseCode() == 200) {
					String response = safeRead(conn.getInputStream());
					String main = extractFirestoreString(response, "mainAccount");
					handler.post(() -> cb.onSuccess(main));
				} else {
					handler.post(() -> cb.onSuccess(""));
				}

			} catch (Exception e) {
				handler.post(() -> cb.onSuccess(""));
			}
		});
	}

	public void getHouseholdOwner(FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getFreshTokenSync();

				HttpURLConnection conn = open(getHouseholdPath(), "GET", token, false);

				if (conn.getResponseCode() == 200) {
					String response = safeRead(conn.getInputStream());
					String owner = extractFirestoreString(response, "owner");
					handler.post(() -> cb.onSuccess(owner));
				} else {
					handler.post(() -> cb.onSuccess(""));
				}

			} catch (Exception e) {
				handler.post(() -> cb.onSuccess(""));
			}
		});
	}

	public void deleteHousehold(FirestoreManager.Callback cb) {
		deleteDocument(getHouseholdPath(), cb);
	}

	// ─── INTERNAL HELPERS ───────────────────────────────────

	private void getCollection(String path, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getFreshTokenSync();
				HttpURLConnection conn = open(path, "GET", token, false);

				if (conn.getResponseCode() == 200) {
					String response = safeRead(conn.getInputStream());
					handler.post(() -> cb.onSuccess(response));
				} else {
					handler.post(() -> cb.onSuccess("{\"documents\":[]}"));
				}

			} catch (Exception e) {
				handler.post(() -> cb.onSuccess("{\"documents\":[]}"));
			}
		});
	}

	private void deleteDocument(String path, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getFreshTokenSync();
				HttpURLConnection conn = open(path, "DELETE", token, false);

				int code = conn.getResponseCode();

				if (code == 200 || code == 204) {
					handler.post(() -> cb.onSuccess("deleted"));
				} else {
					String error = safeRead(conn.getErrorStream());
					handler.post(() -> cb.onError("Code: " + code + " - " + error));
				}

			} catch (Exception e) {
				handler.post(() -> cb.onError(e.getMessage()));
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
			DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
			dos.write(body.getBytes("UTF-8"));
			dos.flush();
			dos.close();

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

	private String findPersonDocumentUrl(String json, String personName) {
		if (json == null || personName == null)
			return null;

		int cursor = 0;

		while (true) {
			String marker1 = "\"name\": \"projects/";
			String marker2 = "\"name\":\"projects/";

			int i1 = json.indexOf(marker1, cursor);
			int i2 = json.indexOf(marker2, cursor);

			if (i1 < 0 && i2 < 0)
				break;

			int idx;
			String marker;

			if (i1 < 0) {
				idx = i2;
				marker = marker2;
			} else if (i2 < 0) {
				idx = i1;
				marker = marker1;
			} else {
				idx = Math.min(i1, i2);
				marker = i1 < i2 ? marker1 : marker2;
			}

			int pathStart = idx + marker.length();
			int pathEnd = json.indexOf("\"", pathStart);

			if (pathEnd < 0)
				break;

			String docFullPath = "projects/" + json.substring(pathStart, pathEnd);
			cursor = pathEnd;

			if (!docFullPath.contains("/persons/"))
				continue;

			int fieldsIdx = json.indexOf("\"fields\"", cursor);
			if (fieldsIdx < 0)
				break;

			String block = json.substring(fieldsIdx, Math.min(json.length(), fieldsIdx + 600));

			if (block.contains("\"stringValue\":\"" + personName + "\"")
					|| block.contains("\"stringValue\": \"" + personName + "\"")) {
				return "https://firestore.googleapis.com/v1/" + docFullPath;
			}
		}

		return null;
	}

	private String extractFirestoreString(String json, String fieldName) {
		if (json == null || json.isEmpty())
			return "";

		String marker = "\"" + fieldName + "\"";
		int fieldIndex = json.indexOf(marker);
		if (fieldIndex < 0)
			return "";

		String sub = json.substring(fieldIndex, Math.min(json.length(), fieldIndex + 300));

		String key1 = "\"stringValue\": \"";
		String key2 = "\"stringValue\":\"";

		int i = sub.indexOf(key1);
		int len = key1.length();

		if (i < 0) {
			i = sub.indexOf(key2);
			len = key2.length();
		}

		if (i < 0)
			return "";

		i += len;
		int end = sub.indexOf("\"", i);

		return end > i ? sub.substring(i, end).trim() : "";
	}

	private String extractFirestoreNumber(String json, String fieldName) {
		if (json == null || json.isEmpty())
			return "";

		String marker = "\"" + fieldName + "\"";
		int fieldIndex = json.indexOf(marker);
		if (fieldIndex < 0)
			return "";

		String sub = json.substring(fieldIndex, Math.min(json.length(), fieldIndex + 300));

		String[] keys = { "\"doubleValue\":", "\"integerValue\":" };

		for (String key : keys) {
			int i = sub.indexOf(key);

			if (i >= 0) {
				i += key.length();

				while (i < sub.length() && (sub.charAt(i) == ' ' || sub.charAt(i) == '\"')) {
					i++;
				}

				int e = i;

				while (e < sub.length()
						&& (Character.isDigit(sub.charAt(e)) || sub.charAt(e) == '.' || sub.charAt(e) == '-')) {
					e++;
				}

				if (e > i)
					return sub.substring(i, e);
			}
		}

		return "";
	}

	private String safeRead(InputStream is) {
		if (is == null)
			return "";

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

	private String escape(String value) {
		if (value == null)
			return "";
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}