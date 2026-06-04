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

public class TransactionManager {

	private static final String PROJECT_ID = FirebaseConfig.PROJECT_ID;
	private static final String API_KEY = FirebaseConfig.API_KEY;

	private static final String BASE_URL = "https://firestore.googleapis.com/v1/projects/" + PROJECT_ID
			+ "/databases/(default)/documents/";

	private static volatile TransactionManager instance;

	private final Executor executor = Executors.newFixedThreadPool(3);
	private final Handler handler = new Handler(Looper.getMainLooper());

	public static TransactionManager getInstance() {
		if (instance == null) {
			synchronized (TransactionManager.class) {
				if (instance == null)
					instance = new TransactionManager();
			}
		}
		return instance;
	}

	private String getHouseholdPath() {
		return "households/" + HouseholdManager.getInstance().getHouseholdId();
	}

	public void addTransactionWithDate(String label, double amount, String type, String category, long date,
			FirestoreManager.Callback cb) {
		addTransactionWithDateAndShared(label, amount, type, category, date, "", false, false, null, cb);
	}

	public void addTransactionWithDateAndShared(String label, double amount, String type, String category, long date,
			String person, boolean shared, boolean isShareSplit, FirestoreManager.Callback cb) {
		addTransactionWithDateAndShared(label, amount, type, category, date, person, shared, isShareSplit, null, cb);
	}

	public void addTransactionWithDateAndShared(String label, double amount, String type, String category, long date,
			boolean shared, FirestoreManager.Callback cb) {
		addTransactionWithDateAndShared(label, amount, type, category, date, "", shared, false, null, cb);
	}

	public void addTransactionWithDateAndShared(String label, double amount, String type, String category, long date,
			String person, boolean shared, boolean isShareSplit, String compte, FirestoreManager.Callback cb) {
		addTransactionFull(label, amount, type, category, date, person, shared, isShareSplit, compte, "", cb);
	}

	/**
	 * Crée une transaction en y attachant un {@code transferId} : l'identifiant
	 * du virement qui l'a générée.
	 *
	 * <p>Cela établit un lien direct et fiable virement → transaction, utilisé
	 * pour supprimer proprement les 2 transactions d'un virement sans avoir à
	 * les deviner par correspondance.</p>
	 */
	public void addTransactionWithTransferId(String label, double amount, String type, String category, long date,
			String person, boolean shared, boolean isShareSplit, String compte, String transferId,
			FirestoreManager.Callback cb) {
		addTransactionFull(label, amount, type, category, date, person, shared, isShareSplit, compte,
				transferId == null ? "" : transferId, cb);
	}

	private void addTransactionFull(String label, double amount, String type, String category, long date,
			String person, boolean shared, boolean isShareSplit, String compte, String transferId,
			FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getToken();
				String userId = AuthManager.getInstance().getUserId();

				HttpURLConnection conn = open(getHouseholdPath() + "/transactions", "POST", token, true);

				long now = System.currentTimeMillis();

				String compteField = "";
				if (compte != null && !compte.trim().isEmpty()) {
					compteField = ",\"compte\":{\"stringValue\":\"" + escape(compte.trim()) + "\"}";
				}

				// transferId : présent uniquement pour les transactions de virement.
				String transferField = "";
				if (transferId != null && !transferId.trim().isEmpty()) {
					transferField = ",\"transferId\":{\"stringValue\":\"" + escape(transferId.trim()) + "\"}";
				}

				String body = "{\"fields\":{" + "\"label\":{\"stringValue\":\"" + escape(label) + "\"},"
						+ "\"amount\":{\"doubleValue\":" + amount + "}," + "\"type\":{\"stringValue\":\"" + escape(type)
						+ "\"}," + "\"category\":{\"stringValue\":\"" + escape(category) + "\"},"
						+ "\"userId\":{\"stringValue\":\"" + escape(userId) + "\"}," + "\"person\":{\"stringValue\":\""
						+ escape(person != null ? person : "") + "\"}," + "\"shared\":{\"booleanValue\":" + shared
						+ "}," + "\"isShareSplit\":{\"booleanValue\":" + isShareSplit + "},"
						+ "\"isReimbursement\":{\"booleanValue\":false}," + "\"date\":{\"integerValue\":\"" + date
						+ "\"}," + "\"addedMs\":{\"integerValue\":\"" + now + "\"}" + compteField + transferField
						+ "}}";

				send(conn, body, cb);

			} catch (Exception e) {
				postError(cb, e.getMessage());
			}
		});
	}

	public void addShareSplitTransaction(String label, double amount, String type, String category, long date,
			FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getToken();
				String userId = AuthManager.getInstance().getUserId();

				HttpURLConnection conn = open(getHouseholdPath() + "/transactions", "POST", token, true);

				long now = System.currentTimeMillis();

				String body = "{\"fields\":{" + "\"label\":{\"stringValue\":\"" + escape(label) + "\"},"
						+ "\"amount\":{\"doubleValue\":" + amount + "}," + "\"type\":{\"stringValue\":\"" + escape(type)
						+ "\"}," + "\"category\":{\"stringValue\":\"" + escape(category) + "\"},"
						+ "\"userId\":{\"stringValue\":\"" + escape(userId) + "\"},"
						+ "\"person\":{\"stringValue\":\"\"}," + "\"shared\":{\"booleanValue\":true},"
						+ "\"isShareSplit\":{\"booleanValue\":true}," + "\"isReimbursement\":{\"booleanValue\":false},"
						+ "\"date\":{\"integerValue\":\"" + date + "\"}," + "\"addedMs\":{\"integerValue\":\"" + now
						+ "\"}" + "}}";

				send(conn, body, cb);

			} catch (Exception e) {
				postError(cb, e.getMessage());
			}
		});
	}

	public void updateTransactionWithShared(String docId, String label, double amount, String type, String category,
			long date, String person, boolean shared, FirestoreManager.Callback cb) {
		updateTransactionWithShared(docId, label, amount, type, category, date, person, shared, null, cb);
	}

	public void updateTransactionWithShared(String docId, String label, double amount, String type, String category,
			long date, String person, boolean shared, String compte, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getToken();

				String compteField = "";
				String compteUpdateMask = "";

				if (compte != null) {
					compteField = ",\"compte\":{\"stringValue\":\"" + escape(compte.trim()) + "\"}";
					compteUpdateMask = "&updateMask.fieldPaths=compte";
				}

				String path = getHouseholdPath() + "/transactions/" + docId + "?updateMask.fieldPaths=label"
						+ "&updateMask.fieldPaths=amount" + "&updateMask.fieldPaths=type"
						+ "&updateMask.fieldPaths=category" + "&updateMask.fieldPaths=date"
						+ "&updateMask.fieldPaths=person" + "&updateMask.fieldPaths=shared"
						+ "&updateMask.fieldPaths=addedMs" + compteUpdateMask;

				HttpURLConnection conn = open(path, "PATCH", token, true);

				String body = "{\"fields\":{" + "\"label\":{\"stringValue\":\"" + escape(label) + "\"},"
						+ "\"amount\":{\"doubleValue\":" + amount + "}," + "\"type\":{\"stringValue\":\"" + escape(type)
						+ "\"}," + "\"category\":{\"stringValue\":\"" + escape(category) + "\"},"
						+ "\"date\":{\"integerValue\":\"" + date + "\"}," + "\"person\":{\"stringValue\":\""
						+ escape(person != null ? person : "") + "\"}," + "\"shared\":{\"booleanValue\":" + shared
						+ "}," + "\"addedMs\":{\"integerValue\":\"" + System.currentTimeMillis() + "\"}" + compteField
						+ "}}";

				send(conn, body, cb);

			} catch (Exception e) {
				postError(cb, e.getMessage());
			}
		});
	}

	public void getTransactions(FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getToken();
				// Récupérer toutes les pages (Firestore limite à 100 docs par GET)
				String allJson = fetchAllTransactionPages(token);
				postSuccess(cb, allJson);
			} catch (Exception e) {
				postError(cb, e.getMessage());
			}
		});
	}

	/**
	 * Récupère TOUTES les transactions en paginant avec nextPageToken.
	 * Firestore REST renvoie au max 300 docs par page (défaut 100).
	 * On demande 300 par page pour minimiser le nombre d'appels.
	 * Les réponses JSON sont fusionnées en un seul JSON valide.
	 */
	private String fetchAllTransactionPages(String token) {
		try {
			StringBuilder allDocuments = new StringBuilder();
			String pageToken = null;
			boolean firstPage = true;

			do {
				String url = BASE_URL + getHouseholdPath() + "/transactions?pageSize=300&key=" + API_KEY;
				if (pageToken != null) {
					url += "&pageToken=" + pageToken;
				}

				HttpURLConnection conn = open(url, "GET", token, false);
				int code = conn.getResponseCode();

				if (code < 200 || code >= 300) {
					// En cas d'erreur sur une page, on retourne ce qu'on a
					break;
				}

				String page = safeRead(conn.getInputStream());

				// Extraire les documents de cette page et les accumuler
				String docs = extractDocumentsArray(page);
				if (!docs.isEmpty()) {
					if (!firstPage)
						allDocuments.append(",");
					allDocuments.append(docs);
					firstPage = false;
				}

				// Chercher le nextPageToken pour la page suivante
				pageToken = extractNextPageToken(page);

			} while (pageToken != null && !pageToken.isEmpty());

			// Reconstruire un JSON valide avec tous les documents
			return "{\"documents\":[" + allDocuments.toString() + "]}";

		} catch (Exception e) {
			return "{\"documents\":[]}";
		}
	}

	/**
	 * Extrait le contenu du tableau "documents" d'une réponse Firestore
	 * sans les crochets [ ], pour pouvoir les concaténer entre pages.
	 */
	private String extractDocumentsArray(String json) {
		if (json == null || json.isEmpty())
			return "";
		int start = json.indexOf("[");
		int end = json.lastIndexOf("]");
		if (start < 0 || end <= start)
			return "";
		String inner = json.substring(start + 1, end).trim();
		return inner;
	}

	/**
	 * Extrait le nextPageToken d'une réponse Firestore paginée.
	 * Retourne null s'il n'y en a pas (dernière page).
	 */
	private String extractNextPageToken(String json) {
		if (json == null)
			return null;
		// Chercher "nextPageToken": "VALUE" avec ou sans espace
		String marker1 = "\"nextPageToken\": \"";
		String marker2 = "\"nextPageToken\":\"";
		for (String marker : new String[] { marker1, marker2 }) {
			int idx = json.indexOf(marker);
			if (idx >= 0) {
				int start = idx + marker.length();
				int end2 = json.indexOf("\"", start);
				if (end2 > start)
					return json.substring(start, end2);
			}
		}
		return null;
	}

	public void deleteTransaction(String docId, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getToken();
				String url = BASE_URL + getHouseholdPath() + "/transactions/" + docId + "?key=" + API_KEY;

				HttpURLConnection conn = open(url, "DELETE", token, false);
				int code = conn.getResponseCode();

				if (code >= 200 && code < 300) {
					postSuccess(cb, "");
				} else {
					String err = safeRead(conn.getErrorStream());
					postError(cb, "Code: " + code + " - " + err);
				}

			} catch (Exception e) {
				postError(cb, e.getMessage());
			}
		});
	}

	public void addReimbursementTransaction(String label, double amount, String type, String category, long date,
			FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token = AuthManager.getInstance().getToken();
				String userId = AuthManager.getInstance().getUserId();

				HttpURLConnection conn = open(getHouseholdPath() + "/transactions", "POST", token, true);

				long now = System.currentTimeMillis();

				boolean isIncome = "income".equalsIgnoreCase(type);
				boolean isReimbursement = !isIncome;

				String person = "";
				if (label != null && label.contains(" · ")) {
					person = label.split(" · ", 2)[0].trim();
				}

				String body = "{\"fields\":{" + "\"label\":{\"stringValue\":\"" + escape(label) + "\"},"
						+ "\"amount\":{\"doubleValue\":" + amount + "}," + "\"type\":{\"stringValue\":\"" + escape(type)
						+ "\"}," + "\"category\":{\"stringValue\":\"" + escape(category) + "\"},"
						+ "\"userId\":{\"stringValue\":\"" + escape(userId) + "\"}," + "\"person\":{\"stringValue\":\""
						+ escape(person) + "\"}," + "\"shared\":{\"booleanValue\":false},"
						+ "\"isShareSplit\":{\"booleanValue\":false}," + "\"isReimbursement\":{\"booleanValue\":"
						+ isReimbursement + "}," + "\"date\":{\"integerValue\":\"" + date + "\"},"
						+ "\"addedMs\":{\"integerValue\":\"" + now + "\"}" + "}}";

				send(conn, body, cb);

			} catch (Exception e) {
				postError(cb, e.getMessage());
			}
		});
	}

	private HttpURLConnection open(String path, String method, String token, boolean output) throws Exception {
		String url;

		if (path.startsWith("http")) {
			url = path;
		} else {
			if (path.contains("?")) {
				url = BASE_URL + path + "&key=" + API_KEY;
			} else {
				url = BASE_URL + path + "?key=" + API_KEY;
			}
		}

		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();

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

			String response = safeRead(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());

			if (code >= 200 && code < 300) {
				postSuccess(cb, response);
			} else {
				postError(cb, "Code: " + code + " - " + response);
			}

		} catch (Exception e) {
			postError(cb, e.getMessage());
		}
	}

	private void postSuccess(FirestoreManager.Callback cb, String response) {
		if (cb == null)
			return;
		handler.post(() -> cb.onSuccess(response == null ? "" : response));
	}

	private void postError(FirestoreManager.Callback cb, String error) {
		if (cb == null)
			return;
		handler.post(() -> cb.onError(error == null ? "Erreur inconnue" : error));
	}

	private String escape(String value) {
		if (value == null)
			return "";
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
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
}