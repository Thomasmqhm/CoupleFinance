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

public class TransferManager {

	private static volatile TransferManager instance;

	private final Executor executor = Executors.newFixedThreadPool(3);
	private final Handler handler = new Handler(Looper.getMainLooper());

	public static TransferManager getInstance() {
		if (instance == null) {
			synchronized (TransferManager.class) {
				if (instance == null) instance = new TransferManager();
			}
		}
		return instance;
	}

	private String householdPath() {
		return "households/" + HouseholdManager.getInstance().getHouseholdId();
	}

	// ─────────────────────────────────────────────
	// BENEFICIARIES
	// ─────────────────────────────────────────────

	public void addBeneficiary(String name, String iban, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			String body = "{\"fields\":{"
					+ "\"name\":{\"stringValue\":\"" + escape(name) + "\"},"
					+ "\"iban\":{\"stringValue\":\"" + escape(iban) + "\"}"
					+ "}}";

			send("POST", householdPath() + "/beneficiaries", body, cb);
		});
	}

	public void getBeneficiaries(FirestoreManager.Callback cb) {
		executor.execute(() ->
				send("GET", householdPath() + "/beneficiaries", null, cb));
	}

	public void deleteBeneficiary(String docId, FirestoreManager.Callback cb) {
		executor.execute(() ->
				send("DELETE", householdPath() + "/beneficiaries/" + docId, null, cb));
	}

	// ─────────────────────────────────────────────
	// TRANSFERS
	// ─────────────────────────────────────────────

	public void addTransfer(String from, String to, double amount, String motif, long date,
							FirestoreManager.Callback cb) {
		executor.execute(() -> {
			String body = "{\"fields\":{"
					+ "\"from\":{\"stringValue\":\"" + escape(from) + "\"},"
					+ "\"to\":{\"stringValue\":\"" + escape(to) + "\"},"
					+ "\"amount\":{\"doubleValue\":" + amount + "},"
					+ "\"motif\":{\"stringValue\":\"" + escape(motif) + "\"},"
					+ "\"date\":{\"integerValue\":" + date + "},"
					+ "\"transactionId\":{\"stringValue\":\"\"}"
					+ "}}";

			send("POST", householdPath() + "/transfers", body, cb);
		});
	}

	public void getTransfers(FirestoreManager.Callback cb) {
		executor.execute(() ->
				send("GET", householdPath() + "/transfers", null, cb));
	}

	public void deleteTransfer(String docId, FirestoreManager.Callback cb) {
		executor.execute(() ->
				send("DELETE", householdPath() + "/transfers/" + docId, null, cb));
	}

	public void linkTransferToTransaction(String transferId, String txId, FirestoreManager.Callback cb) {
		executor.execute(() -> {
			String path = householdPath() + "/transfers/" + transferId
					+ "?updateMask.fieldPaths=transactionId";

			String body = "{\"fields\":{"
					+ "\"transactionId\":{\"stringValue\":\"" + escape(txId) + "\"}"
					+ "}}";

			send("PATCH", path, body, cb);
		});
	}

	// ─────────────────────────────────────────────
	// BUSINESS LOGIC
	// ─────────────────────────────────────────────

	public void createTransferWithTransactions(
			String from,
			String to,
			double amount,
			String motif,
			long date,
			boolean fromIsHousehold,
			boolean toIsHousehold,
			FirestoreManager.Callback cb
	) {
		addTransfer(from, to, amount, motif, date, new FirestoreManager.Callback() {
			public void onSuccess(String transferResponse) {
				String transferDocId = extractDocId(transferResponse);

				if (fromIsHousehold && toIsHousehold) {
					createInternalTransferTransactions(from, to, amount, motif, date, transferDocId, cb);
				} else if (fromIsHousehold) {
					createOutgoingTransferTransaction(from, to, amount, motif, date, cb);
				} else if (toIsHousehold) {
					createIncomingTransferTransaction(from, to, amount, motif, date, cb);
				} else {
					success(cb, transferResponse);
				}
			}

			public void onError(String e) {
				error(cb, e);
			}
		});
	}

	private void createInternalTransferTransactions(
			String from,
			String to,
			double amount,
			String motif,
			long date,
			String transferDocId,
			FirestoreManager.Callback cb
	) {
		String labelExpense = from + " · Virement → " + to + optionalMotif(motif);
		String labelIncome = to + " · Virement reçu de " + from + optionalMotif(motif);

		TransactionManager.getInstance().addTransactionWithDate(
				labelExpense,
				amount,
				"variable",
				"Virement",
				date,
				new FirestoreManager.Callback() {
					public void onSuccess(String expenseResponse) {
						String txDocId = extractDocId(expenseResponse);

						if (!transferDocId.isEmpty() && !txDocId.isEmpty()) {
							linkTransferToTransaction(transferDocId, txDocId, new FirestoreManager.Callback() {
								public void onSuccess(String r) {}
								public void onError(String e) {}
							});
						}

						TransactionManager.getInstance().addTransactionWithDate(
								labelIncome,
								amount,
								"income",
								"Virement",
								date,
								new FirestoreManager.Callback() {
									public void onSuccess(String incomeResponse) {
										success(cb, incomeResponse);
									}

									public void onError(String e) {
										error(cb, e);
									}
								});
					}

					public void onError(String e) {
						error(cb, e);
					}
				});
	}

	private void createOutgoingTransferTransaction(
			String from,
			String to,
			double amount,
			String motif,
			long date,
			FirestoreManager.Callback cb
	) {
		String label = from + " · Virement → " + to + optionalMotif(motif);

		TransactionManager.getInstance().addTransactionWithDate(
				label,
				amount,
				"variable",
				"Virement",
				date,
				cb);
	}

	private void createIncomingTransferTransaction(
			String from,
			String to,
			double amount,
			String motif,
			long date,
			FirestoreManager.Callback cb
	) {
		String label = to + " · Virement reçu de " + from + optionalMotif(motif);

		TransactionManager.getInstance().addTransactionWithDate(
				label,
				amount,
				"income",
				"Virement",
				date,
				cb);
	}

	// ─────────────────────────────────────────────
	// NETWORK
	// ─────────────────────────────────────────────

	private void send(String method, String path, String body, FirestoreManager.Callback cb) {
		HttpURLConnection conn = null;
		try {
			String token = AuthManager.getInstance().getToken();

			String urlStr;
			if (path.startsWith("http")) {
				urlStr = path;
			} else {
				urlStr = FirebaseConfig.BASE_URL + path;
			}

			if (!urlStr.contains("?")) {
				urlStr += "?key=" + FirebaseConfig.API_KEY;
			} else if (!urlStr.contains("key=")) {
				urlStr += "&key=" + FirebaseConfig.API_KEY;
			}

			conn = (HttpURLConnection) new URL(urlStr).openConnection();
			conn.setRequestMethod(method);
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setConnectTimeout(10000);
			conn.setReadTimeout(10000);

			if (token != null && !token.isEmpty()) {
				conn.setRequestProperty("Authorization", "Bearer " + token);
			}

			if (body != null && !method.equals("GET") && !method.equals("DELETE")) {
				conn.setDoOutput(true);
				try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
					dos.write(body.getBytes("UTF-8"));
				}
			}

			int code = conn.getResponseCode();
			String response = safeRead(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());

			if (code >= 200 && code < 300) {
				success(cb, response.isEmpty() ? "ok" : response);
			} else if (method.equals("GET")) {
				success(cb, "{\"documents\":[]}");
			} else {
				error(cb, "Code: " + code + " - " + response);
			}

		} catch (Exception e) {
			error(cb, e.getMessage());
		} finally {
			if (conn != null) conn.disconnect();
		}
	}

	// ─────────────────────────────────────────────
	// UTILS
	// ─────────────────────────────────────────────

	private String optionalMotif(String motif) {
		if (motif == null || motif.trim().isEmpty())
			return "";
		return " (" + motif.trim() + ")";
	}

	private String extractDocId(String response) {
		if (response == null)
			return "";

		int ni = response.indexOf("\"name\": \"");
		if (ni < 0)
			ni = response.indexOf("\"name\":\"");

		if (ni >= 0) {
			int s = response.indexOf("\"", ni + 8) + 1;
			int e = response.indexOf("\"", s);

			if (e > s) {
				String fullPath = response.substring(s, e);
				if (fullPath.contains("/"))
					return fullPath.substring(fullPath.lastIndexOf("/") + 1);
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
		} catch (Exception e) {
			return "";
		}
	}

	private String escape(String value) {
		if (value == null)
			return "";

		return value
				.replace("\\", "\\\\")
				.replace("\"", "\\\"");
	}

	private void success(FirestoreManager.Callback cb, String value) {
		handler.post(() -> cb.onSuccess(value));
	}

	private void error(FirestoreManager.Callback cb, String value) {
		handler.post(() -> cb.onError(value));
	}
}