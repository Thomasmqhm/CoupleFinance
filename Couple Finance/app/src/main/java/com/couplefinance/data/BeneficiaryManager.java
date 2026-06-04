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

public class BeneficiaryManager {

	private static BeneficiaryManager instance;

	private final Executor executor = Executors.newSingleThreadExecutor();
	private final Handler handler = new Handler(Looper.getMainLooper());

	public static BeneficiaryManager getInstance() {
		if (instance == null) {
			instance = new BeneficiaryManager();
		}
		return instance;
	}

	private String getHouseholdPath() {
		return "households/" + HouseholdManager.getInstance().getHouseholdId();
	}

	private String getCollectionPath() {
		return getHouseholdPath() + "/beneficiaries";
	}

	public void addBeneficiary(String name, String iban, FirestoreManager.Callback cb) {
		if (name == null || name.trim().isEmpty()) {
			postError(cb, "Nom du bénéficiaire invalide");
			return;
		}

		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getToken();

				HttpURLConnection conn = openPost(getCollectionPath(), token);
				if (conn == null) {
					postError(cb, "Connection failed");
					return;
				}

				String body = "{\"fields\":{"
						+ "\"name\":{\"stringValue\":\"" + escapeJson(name) + "\"},"
						+ "\"iban\":{\"stringValue\":\"" + escapeJson(iban) + "\"},"
						+ "\"createdAt\":{\"integerValue\":\"" + System.currentTimeMillis() + "\"}"
						+ "}}";

				send(conn, body, cb);

			} catch (Exception e) {
				postError(cb, e.getMessage());
			}
		});
	}

	public void getBeneficiaries(FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getToken();

				HttpURLConnection conn = openGet(getCollectionPath(), token);
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

	public void deleteBeneficiary(String docPath, FirestoreManager.Callback cb) {
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

	private String cleanDocUrl(String docPath) {
		if (docPath == null) {
			docPath = "";
		}

		String path = docPath.trim();

		if (path.startsWith("projects/")) {
			return FirebaseConfig.apiRootUrl(path);
		}

		if (path.contains("/beneficiaries/")) {
			return FirebaseConfig.documentUrl(path);
		}

		return FirebaseConfig.documentUrl(getCollectionPath() + "/" + path);
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