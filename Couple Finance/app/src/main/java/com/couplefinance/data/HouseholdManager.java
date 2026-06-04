package com.couplefinance.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.couplefinance.AuthManager;
import com.couplefinance.utils.FirebaseConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class HouseholdManager {

	private static final String PREFS = "household_prefs";
	private static final String KEY_HOUSEHOLD_ID = "householdId";

	private static volatile HouseholdManager instance;

	private final Executor executor = Executors.newSingleThreadExecutor();
	private final Handler handler = new Handler(Looper.getMainLooper());

	private Context context;
	private String householdId;

	public interface Callback {
		void onSuccess(String householdId);
		void onError(String error);
	}

	public static HouseholdManager getInstance() {
		if (instance == null) {
			synchronized (HouseholdManager.class) {
				if (instance == null) {
					instance = new HouseholdManager();
				}
			}
		}
		return instance;
	}

	public void init(Context ctx) {
		if (ctx == null) return;

		context = ctx.getApplicationContext();

		SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		householdId = prefs.getString(KEY_HOUSEHOLD_ID, null);
	}

	public boolean hasHousehold() {
		return householdId != null && !householdId.trim().isEmpty();
	}

	public String getHouseholdId() {
		return householdId;
	}

	public String getHouseholdPath() {
		return "households/" + householdId;
	}

	public void createHousehold(Callback cb) {
		String token = AuthManager.getInstance().getToken();
		String userId = AuthManager.getInstance().getUserId();

		createHouseholdWithToken(token, userId, cb);
	}

	public void createHouseholdWithToken(String token, String userId, Callback cb) {
		if (token == null || token.isEmpty()) {
			error(cb, "Token invalide");
			return;
		}

		if (userId == null || userId.isEmpty()) {
			error(cb, "Utilisateur invalide");
			return;
		}

		String code = generateCode();

		executor.execute(() -> {
			try {
				String urlStr = FirebaseConfig.documentUrl("households/" + code);
				HttpURLConnection conn = open(urlStr, "PATCH", token, true);

				String body = "{\"fields\":{"
						+ "\"owner\":{\"stringValue\":\"" + escape(userId) + "\"},"
						+ "\"code\":{\"stringValue\":\"" + escape(code) + "\"},"
						+ "\"mainAccount\":{\"stringValue\":\"\"},"
						+ "\"createdAt\":{\"integerValue\":\"" + System.currentTimeMillis() + "\"}"
						+ "}}";

				writeBody(conn, body);

				int codeHttp = conn.getResponseCode();

				if (codeHttp == 200) {
					saveHousehold(code);
					saveUserHousehold(token, userId, code);   // lien serveur user -> foyer
					success(cb, code);
				} else {
					error(cb, safeRead(conn.getErrorStream()));
				}

			} catch (Exception e) {
				error(cb, e.getMessage());
			}
		});
	}

	public void joinHousehold(String code, Callback cb) {
		String token = AuthManager.getInstance().getToken();
		joinHouseholdWithToken(token, code, cb);
	}

	public void joinHouseholdWithToken(String token, String code, Callback cb) {
		if (token == null || token.isEmpty()) {
			error(cb, "Token invalide");
			return;
		}

		if (code == null || code.trim().isEmpty()) {
			error(cb, "Code invalide");
			return;
		}

		String cleanCode = code.trim().toUpperCase(Locale.FRANCE);

		executor.execute(() -> {
			try {
				String urlStr = FirebaseConfig.documentUrl("households/" + cleanCode);
				HttpURLConnection conn = open(urlStr, "GET", token, false);

				int codeHttp = conn.getResponseCode();

				if (codeHttp == 200) {
					saveHousehold(cleanCode);
					saveUserHousehold(token, AuthManager.getInstance().getUserId(), cleanCode);
					success(cb, cleanCode);
				} else {
					error(cb, "Code invalide ou foyer introuvable");
				}

			} catch (Exception e) {
				error(cb, e.getMessage());
			}
		});
	}

	public void clearHousehold() {
		householdId = null;

		if (context != null) {
			context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
					.edit()
					.clear()
					.apply();
		}
	}

	/**
	 * Enregistre le lien utilisateur -> foyer côté serveur (users/{uid}.householdId).
	 * Permet de RETROUVER le foyer après une reconnexion / réinstallation.
	 * Best-effort : n'échoue jamais la création/jointure.
	 */
	private void saveUserHousehold(String token, String uid, String code) {
		if (token == null || token.isEmpty() || uid == null || uid.isEmpty() || code == null) return;
		executor.execute(() -> {
			try {
				String url = FirebaseConfig.documentUrl("users/" + uid)
						+ "?updateMask.fieldPaths=householdId";
				HttpURLConnection conn = open(url, "PATCH", token, true);
				String body = "{\"fields\":{\"householdId\":{\"stringValue\":\""
						+ escape(code) + "\"}}}";
				writeBody(conn, body);
				conn.getResponseCode();
			} catch (Exception ignored) {}
		});
	}

	/**
	 * Restaure le foyer de l'utilisateur courant depuis le serveur
	 * (users/{uid}.householdId). À appeler à la connexion AVANT de proposer
	 * de créer/rejoindre un foyer.
	 * onSuccess(code) si un foyer est retrouvé, onError sinon.
	 */
	public void restoreHousehold(Callback cb) {
		String token = AuthManager.getInstance().getToken();
		String uid   = AuthManager.getInstance().getUserId();
		if (token == null || token.isEmpty() || uid == null || uid.isEmpty()) {
			error(cb, "Non connecté");
			return;
		}
		executor.execute(() -> {
			try {
				String url = FirebaseConfig.documentUrl("users/" + uid);
				HttpURLConnection conn = open(url, "GET", token, false);
				int http = conn.getResponseCode();
				if (http == 200) {
					String resp = safeRead(conn.getInputStream());
					int idx = resp.indexOf("\"householdId\"");
					String hid = idx >= 0 ? extractClean(resp.substring(idx), "stringValue") : "";
					if (hid != null && !hid.trim().isEmpty()) {
						saveHousehold(hid.trim());
						success(cb, hid.trim());
					} else {
						error(cb, "no-household");
					}
				} else {
					error(cb, "no-household");
				}
			} catch (Exception e) {
				error(cb, e.getMessage());
			}
		});
	}

	private void saveHousehold(String code) {
		householdId = code;

		if (context != null) {
			context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
					.edit()
					.putString(KEY_HOUSEHOLD_ID, code)
					.apply();
		}
	}

	public void getMembers(FirestoreManager.Callback cb) {
		if (!hasHousehold()) {
			success(cb, "{\"documents\":[]}");
			return;
		}

		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getToken();

				if (token == null || token.isEmpty()) {
					success(cb, "{\"documents\":[]}");
					return;
				}

				String urlStr = FirebaseConfig.collectionUrl(getHouseholdPath() + "/persons");
				HttpURLConnection conn = open(urlStr, "GET", token, false);

				int codeHttp = conn.getResponseCode();

				if (codeHttp == 200) {
					success(cb, safeRead(conn.getInputStream()));
				} else {
					success(cb, "{\"documents\":[]}");
				}

			} catch (Exception e) {
				success(cb, "{\"documents\":[]}");
			}
		});
	}

	public void getHousehold(FirestoreManager.Callback cb) {
		if (!hasHousehold()) {
			error(cb, "Aucun foyer");
			return;
		}

		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getToken();

				if (token == null || token.isEmpty()) {
					error(cb, "Token invalide");
					return;
				}

				String urlStr = FirebaseConfig.documentUrl(getHouseholdPath());
				HttpURLConnection conn = open(urlStr, "GET", token, false);

				int codeHttp = conn.getResponseCode();

				if (codeHttp == 200) {
					success(cb, safeRead(conn.getInputStream()));
				} else {
					error(cb, safeRead(conn.getErrorStream()));
				}

			} catch (Exception e) {
				error(cb, e.getMessage());
			}
		});
	}

	public void getHouseholdOwner(FirestoreManager.Callback cb) {
		getHousehold(new FirestoreManager.Callback() {
			public void onSuccess(String response) {
				String owner = "";

				if (response != null && response.contains("\"owner\"")) {
					int idx = response.indexOf("\"owner\"");
					owner = extractClean(response.substring(idx), "stringValue");
				}

				success(cb, owner);
			}

			public void onError(String error) {
				error(cb, error);
			}
		});
	}

	public void updateMainAccount(String personName, FirestoreManager.Callback cb) {
		if (!hasHousehold()) {
			error(cb, "Aucun foyer");
			return;
		}

		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getToken();

				if (token == null || token.isEmpty()) {
					error(cb, "Token invalide");
					return;
				}

				String urlStr = FirebaseConfig.documentUrl(getHouseholdPath())
						+ "&updateMask.fieldPaths=mainAccount";

				HttpURLConnection conn = open(urlStr, "PATCH", token, true);

				String body = "{\"fields\":{"
						+ "\"mainAccount\":{\"stringValue\":\"" + escape(personName) + "\"}"
						+ "}}";

				writeBody(conn, body);

				int codeHttp = conn.getResponseCode();

				if (codeHttp == 200) {
					success(cb, "ok");
				} else {
					error(cb, safeRead(conn.getErrorStream()));
				}

			} catch (Exception e) {
				error(cb, e.getMessage());
			}
		});
	}

	public void getMainAccount(FirestoreManager.Callback cb) {
		getHousehold(new FirestoreManager.Callback() {
			public void onSuccess(String response) {
				String main = "";

				if (response != null && response.contains("\"mainAccount\"")) {
					int idx = response.indexOf("\"mainAccount\"");
					main = extractClean(response.substring(idx), "stringValue");
				}

				success(cb, main);
			}

			public void onError(String error) {
				success(cb, "");
			}
		});
	}

	public void leaveHousehold(FirestoreManager.Callback cb) {
		if (!hasHousehold()) {
			clearHousehold();
			success(cb, "left");
			return;
		}

		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getToken();

				if (token == null || token.isEmpty()) {
					error(cb, "Token invalide");
					return;
				}

				String currentUid = safe(AuthManager.getInstance().getUserId());
				String currentName = safe(AuthManager.getInstance().getDisplayName());

				String membersUrl = FirebaseConfig.collectionUrl(getHouseholdPath() + "/persons");
				HttpURLConnection getConn = open(membersUrl, "GET", token, false);

				String personPathToDelete = "";

				if (getConn.getResponseCode() == 200) {
					String response = safeRead(getConn.getInputStream());
					personPathToDelete = findCurrentMemberPath(response, currentUid, currentName);
				}

				if (!personPathToDelete.isEmpty()) {
					String cleanPath = cleanFirestoreDocumentPath(personPathToDelete);
					String deleteUrl = FirebaseConfig.documentUrl(cleanPath);
					HttpURLConnection deleteConn = open(deleteUrl, "DELETE", token, false);

					int deleteCode = deleteConn.getResponseCode();

					if (deleteCode != 200 && deleteCode != 204) {
						error(cb, safeRead(deleteConn.getErrorStream()));
						return;
					}
				}

				clearHousehold();
				success(cb, "left");

			} catch (Exception e) {
				error(cb, e.getMessage());
			}
		});
	}

	public void deleteHousehold(FirestoreManager.Callback cb) {
		if (!hasHousehold()) {
			error(cb, "Aucun foyer");
			return;
		}

		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getToken();

				if (token == null || token.isEmpty()) {
					error(cb, "Token invalide");
					return;
				}

				String urlStr = FirebaseConfig.documentUrl(getHouseholdPath());
				HttpURLConnection conn = open(urlStr, "DELETE", token, false);

				int codeHttp = conn.getResponseCode();

				if (codeHttp == 200 || codeHttp == 204) {
					clearHousehold();
					success(cb, "deleted");
				} else {
					error(cb, safeRead(conn.getErrorStream()));
				}

			} catch (Exception e) {
				error(cb, e.getMessage());
			}
		});
	}

	private String findCurrentMemberPath(String json, String uid, String displayName) {
		try {
			JSONObject root = new JSONObject(json);
			JSONArray docs = root.optJSONArray("documents");

			if (docs == null) return "";

			for (int i = 0; i < docs.length(); i++) {
				JSONObject doc = docs.optJSONObject(i);
				if (doc == null) continue;

				JSONObject fields = doc.optJSONObject("fields");
				if (fields == null) continue;

				String docName = doc.optString("name", "");

				String userId = firstNonEmpty(
						readString(fields, "userId"),
						readString(fields, "uid"),
						readString(fields, "owner"),
						readString(fields, "id")
				);

				String name = firstNonEmpty(
						readString(fields, "name"),
						readString(fields, "displayName"),
						readString(fields, "firstName"),
						readString(fields, "prenom")
				);

				if (!uid.isEmpty() && uid.equals(userId)) {
					return docName;
				}

				if (!displayName.isEmpty() && normalize(displayName).equals(normalize(name))) {
					return docName;
				}
			}

		} catch (Exception ignored) {
		}

		return "";
	}

	private String cleanFirestoreDocumentPath(String fullPath) {
		if (fullPath == null) return "";

		String p = fullPath.trim();
		String marker = "/documents/";

		int idx = p.indexOf(marker);
		if (idx >= 0) {
			p = p.substring(idx + marker.length());
		}

		while (p.startsWith("/")) {
			p = p.substring(1);
		}

		return p;
	}

	private HttpURLConnection open(String urlStr, String method, String token, boolean output) throws Exception {
		URL url = new URL(urlStr);

		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod(method);
		conn.setRequestProperty("Content-Type", "application/json");

		if (token != null && !token.isEmpty()) {
			conn.setRequestProperty("Authorization", "Bearer " + token);
		}

		conn.setConnectTimeout(15000);
		conn.setReadTimeout(15000);

		if (output) {
			conn.setDoOutput(true);
		}

		return conn;
	}

	private void writeBody(HttpURLConnection conn, String body) throws Exception {
		DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
		dos.write(body.getBytes("UTF-8"));
		dos.flush();
		dos.close();
	}

	private String safeRead(InputStream is) {
		if (is == null) return "";

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

	private String generateCode() {
		String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < 6; i++) {
			sb.append(chars.charAt((int) (Math.random() * chars.length())));
		}

		return sb.toString();
	}

	private String escape(String value) {
		if (value == null) return "";
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private String extractClean(String json, String key) {
		if (json == null) return "";

		for (String search : new String[]{"\"" + key + "\": \"", "\"" + key + "\":\""}) {
			int start = json.indexOf(search);

			if (start >= 0) {
				start += search.length();
				int end = json.indexOf("\"", start);

				if (end > start) {
					return json.substring(start, end).trim();
				}
			}
		}

		return "";
	}

	private String readString(JSONObject fields, String key) {
		try {
			JSONObject obj = fields.optJSONObject(key);
			if (obj == null) return "";
			return obj.optString("stringValue", "").trim();
		} catch (Exception e) {
			return "";
		}
	}

	private String firstNonEmpty(String... values) {
		if (values == null) return "";

		for (String v : values) {
			if (v != null && !v.trim().isEmpty()) {
				return v.trim();
			}
		}

		return "";
	}

	private String normalize(String value) {
		if (value == null) return "";

		return value.trim()
				.toLowerCase(Locale.FRANCE)
				.replace("é", "e")
				.replace("è", "e")
				.replace("ê", "e")
				.replace("ë", "e")
				.replace("à", "a")
				.replace("â", "a")
				.replace("ù", "u")
				.replace("û", "u")
				.replace("î", "i")
				.replace("ï", "i")
				.replace("ô", "o")
				.replace("ç", "c")
				.replaceAll("\\s+", " ");
	}

	private String safe(String value) {
		return value == null ? "" : value.trim();
	}

	private void success(Callback cb, String value) {
		if (cb != null) {
			handler.post(() -> cb.onSuccess(value));
		}
	}

	private void error(Callback cb, String error) {
		if (cb != null) {
			handler.post(() -> cb.onError(error == null ? "Erreur inconnue" : error));
		}
	}

	private void success(FirestoreManager.Callback cb, String value) {
		if (cb != null) {
			handler.post(() -> cb.onSuccess(value));
		}
	}

	private void error(FirestoreManager.Callback cb, String error) {
		if (cb != null) {
			handler.post(() -> cb.onError(error == null ? "Erreur inconnue" : error));
		}
	}
}