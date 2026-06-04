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

public class FirestoreManager {

	private static final String PROJECT_ID = FirebaseConfig.PROJECT_ID;
	private static final String API_KEY = FirebaseConfig.API_KEY;

	private static final String BASE_URL =
			"https://firestore.googleapis.com/v1/projects/" + PROJECT_ID
					+ "/databases/(default)/documents/";

	private static volatile FirestoreManager instance;

	private final Executor executor = Executors.newFixedThreadPool(4);
	private final Handler handler = new Handler(Looper.getMainLooper());

	// ✅ CALLBACK FIX
	public interface Callback {
		void onSuccess(String response);
		void onError(String error);
	}

	public static FirestoreManager getInstance() {
		if (instance == null) {
			synchronized (FirestoreManager.class) {
				if (instance == null) {
					instance = new FirestoreManager();
				}
			}
		}
		return instance;
	}

	private String getToken() {
		return AuthManager.getInstance().getFreshTokenSync();
	}

	private String getHouseholdId() {
		return HouseholdManager.getInstance().getHouseholdId();
	}

	public String getHouseholdPath() {
		return "households/" + getHouseholdId();
	}

	public void getDocument(String path, Callback cb) {
		request("GET", path, null, cb);
	}

	public void getCollection(String path, Callback cb) {
		request("GET", path, null, cb);
	}

	public void postDocument(String path, String body, Callback cb) {
		request("POST", path, body, cb);
	}

	public void patchDocument(String path, String body, String updateMask, Callback cb) {
		String finalPath = (updateMask == null || updateMask.isEmpty())
				? path
				: path + "?" + updateMask;

		request("PATCH", finalPath, body, cb);
	}

	public void deleteDocument(String path, Callback cb) {
		request("DELETE", path, null, cb);
	}

	private void request(String method, String path, String body, Callback cb) {

		executor.execute(() -> {
			try {
				String token = getToken();

				if (token == null || token.isEmpty()) {
					handler.post(() -> cb.onError("Session invalide"));
					return;
				}

				String separator = path.contains("?") ? "&" : "?";
				URL url = new URL(BASE_URL + path + separator + "key=" + API_KEY);

				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod(method);
				conn.setRequestProperty("Content-Type", "application/json");
				conn.setRequestProperty("Authorization", "Bearer " + token);
				conn.setConnectTimeout(10000);
				conn.setReadTimeout(10000);

				if (!method.equals("GET") && !method.equals("DELETE")) {
					conn.setDoOutput(true);

					if (body != null) {
						DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
						dos.write(body.getBytes("UTF-8"));
						dos.flush();
						dos.close();
					}
				}

				int code = conn.getResponseCode();
				String response = safeRead(
						code >= 200 && code < 300
								? conn.getInputStream()
								: conn.getErrorStream()
				);

				if (code >= 200 && code < 300) {
					handler.post(() -> cb.onSuccess(response));
				} else {
					handler.post(() -> cb.onError("Code: " + code + " - " + response));
				}

			} catch (Exception e) {
				handler.post(() -> cb.onError(e.getMessage()));
			}
		});
	}

	public String cleanDocumentId(String docPath) {
		if (docPath == null)
			return "";

		String p = docPath.trim();

		if (p.contains("/")) {
			return p.substring(p.lastIndexOf("/") + 1);
		}

		return p;
	}

	public String escape(String value) {
		if (value == null)
			return "";

		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	public String readString(String json, String fieldName) {
		if (json == null || json.isEmpty())
			return "";

		String marker = "\"" + fieldName + "\"";
		int fieldIndex = json.indexOf(marker);
		if (fieldIndex < 0)
			return "";

		String sub = json.substring(fieldIndex, Math.min(json.length(), fieldIndex + 300));

		for (String key : new String[] { "\"stringValue\": \"", "\"stringValue\":\"" }) {
			int i = sub.indexOf(key);
			if (i >= 0) {
				i += key.length();
				int e = sub.indexOf("\"", i);
				if (e > i)
					return sub.substring(i, e).trim();
			}
		}

		return "";
	}

	public String readNumber(String json, String fieldName) {
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

				if (e > i)
					return sub.substring(i, e);
			}
		}

		return "";
	}

	public String readBoolean(String json, String fieldName) {
		if (json == null || json.isEmpty())
			return "false";

		String marker = "\"" + fieldName + "\"";
		int fieldIndex = json.indexOf(marker);
		if (fieldIndex < 0)
			return "false";

		String sub = json.substring(fieldIndex, Math.min(json.length(), fieldIndex + 120));

		return sub.contains("\"booleanValue\":true") || sub.contains("\"booleanValue\": true")
				? "true"
				: "false";
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
}