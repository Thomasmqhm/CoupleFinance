package com.couplefinance.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.couplefinance.AuthManager;
import com.couplefinance.UserSession;
import com.couplefinance.ui.credits.CreditsModels;
import com.couplefinance.ui.credits.CreditsParser;
import com.couplefinance.utils.FirebaseConfig;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CreditManager {

	private static final String PROJECT_ID = FirebaseConfig.PROJECT_ID;
	private static final String API_KEY    = FirebaseConfig.API_KEY;

	private static final String BASE_URL =
			"https://firestore.googleapis.com/v1/projects/"
					+ PROJECT_ID
					+ "/databases/(default)/documents/";

	private static CreditManager instance;

	private final Executor executor = Executors.newFixedThreadPool(2);
	private final Handler  handler  = new Handler(Looper.getMainLooper());

	private Context context;

	public static CreditManager getInstance() {
		if (instance == null) {
			instance = new CreditManager();
		}
		return instance;
	}

	public void init(Context ctx) {
		if (ctx != null) context = ctx.getApplicationContext();
	}

	private String getHouseholdPath() {
		return "households/" + HouseholdManager.getInstance().getHouseholdId();
	}

	// ─────────────────────────────────────────────
	// CREATE
	// ─────────────────────────────────────────────

	public void addCredit(
			String name,
			double totalAmount,
			double monthlyPayment,
			long startDate,
			int durationMonths,
			String emoji,
			String bank,
			String type,
			double rate,
			String paidBy,
			String compte,
			int paymentDay,
			FirestoreManager.Callback cb
	) {

		executor.execute(() -> {
			try {

				String token = AuthManager.getInstance().getFreshTokenSync();

				HttpURLConnection conn = open(
						getHouseholdPath() + "/credits",
						"POST",
						token,
						true
				);

				String body =
						"{\"fields\":{"

								+ "\"name\":{\"stringValue\":\""
								+ escape(name)
								+ "\"},"

								+ "\"totalAmount\":{\"doubleValue\":"
								+ totalAmount
								+ "},"

								+ "\"monthlyPayment\":{\"doubleValue\":"
								+ monthlyPayment
								+ "},"

								+ "\"startDate\":{\"integerValue\":\""
								+ startDate
								+ "\"},"

								+ "\"durationMonths\":{\"integerValue\":\""
								+ durationMonths
								+ "\"},"

								+ "\"emoji\":{\"stringValue\":\""
								+ escape(emoji)
								+ "\"},"

								+ "\"bank\":{\"stringValue\":\""
								+ escape(bank)
								+ "\"},"

								+ "\"type\":{\"stringValue\":\""
								+ escape(type)
								+ "\"},"

								+ "\"rate\":{\"doubleValue\":"
								+ rate
								+ "},"

								+ "\"paidBy\":{\"stringValue\":\""
								+ escape(paidBy)
								+ "\"},"

								+ "\"compte\":{\"stringValue\":\""
								+ escape(compte)
								+ "\"},"

								+ "\"paymentDay\":{\"integerValue\":\""
								+ paymentDay
								+ "\"},"

								+ "\"lastAppliedMonth\":{\"stringValue\":\"\"},"

								+ "\"createdAt\":{\"integerValue\":\""
								+ System.currentTimeMillis()
								+ "\"}"

								+ "}}";

				send(conn, body, cb);

			} catch (Exception e) {
				handler.post(() -> cb.onError(e.getMessage()));
			}
		});
	}

	// Compatibilité ancienne signature
	public void addCredit(
			String name,
			double totalAmount,
			double monthlyPayment,
			long startDate,
			int durationMonths,
			String emoji,
			String bank,
			String type,
			double rate,
			FirestoreManager.Callback cb
	) {
		addCredit(name, totalAmount, monthlyPayment, startDate, durationMonths,
				emoji, bank, type, rate, "", "", 1, cb);
	}

	// ─────────────────────────────────────────────
	// READ
	// ─────────────────────────────────────────────

	public void getCredits(FirestoreManager.Callback cb) {

		executor.execute(() -> {

			try {

				String token = AuthManager.getInstance().getFreshTokenSync();

				HttpURLConnection conn = open(
						getHouseholdPath() + "/credits",
						"GET",
						token,
						false
				);

				int code = conn.getResponseCode();

				if (code == 200) {
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

	// ─────────────────────────────────────────────
	// DELETE
	// ─────────────────────────────────────────────

	public void deleteCredit(String docPath, FirestoreManager.Callback cb) {

		executor.execute(() -> {

			try {

				String token = AuthManager.getInstance().getFreshTokenSync();

				String urlStr;

				if (docPath.startsWith("projects/")) {
					urlStr = "https://firestore.googleapis.com/v1/"
							+ docPath + "?key=" + API_KEY;
				} else {
					urlStr = BASE_URL + getHouseholdPath()
							+ "/credits/" + docPath + "?key=" + API_KEY;
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

	// ─────────────────────────────────────────────
	// CHECK AND APPLY CREDITS — génération mensuelle des transactions
	//
	// Pour chaque crédit actif ce mois-ci dont le jour de prélèvement
	// est déjà passé (ou aujourd'hui), et dont la transaction du mois
	// n'existe pas encore, on crée la transaction dans /transactions/.
	//
	// Déduplication via le champ recurringKey = docId + "_" + YYYY-MM
	// (même logique que RecurringChargeManager pour les abonnements).
	// ─────────────────────────────────────────────

	public void checkAndApplyCredits(Runnable onDone) {
		executor.execute(() -> {
			try {
				String householdId = HouseholdManager.getInstance().getHouseholdId();
				String token = AuthManager.getInstance().getToken();

				if (householdId == null || householdId.trim().isEmpty()
						|| token == null || token.trim().isEmpty()) {
					postDone(onDone);
					return;
				}

				// Fetch credits
				HttpURLConnection conn = open(getHouseholdPath() + "/credits", "GET", token, false);
				if (conn.getResponseCode() != 200) {
					postDone(onDone);
					return;
				}
				String json = safeRead(conn.getInputStream());
				List<CreditsModels.Credit> credits = CreditsParser.parseCredits(json);

				String currentMonth = getCurrentMonth();
				int today = getDayOfMonth();

				for (CreditsModels.Credit credit : credits) {
					if (credit == null || credit.docId == null || credit.docId.isEmpty()) continue;

					// Vérifier que le crédit est actif ce mois-ci
					if (!isCreditActiveThisMonth(credit)) continue;

					// Vérifier que le jour de prélèvement est passé ou aujourd'hui
					int dueDay = Math.max(1, Math.min(28, credit.paymentDay));
					if (today < dueDay) continue;

					// Vérifier que la transaction du mois n'a pas déjà été créée
					String creditKey = "credit_" + credit.docId + "_" + currentMonth;
					if (transactionExistsForKey(creditKey, householdId, token)) {
						// Mettre à jour lastAppliedMonth si besoin (silencieux)
						patchLastAppliedMonthSync(credit.docId, currentMonth, householdId, token);
						continue;
					}

					// Résoudre la personne : paidBy du crédit, sinon utilisateur connecté
					String person = (credit.paidBy != null && !credit.paidBy.trim().isEmpty())
							? credit.paidBy.trim()
							: getCurrentPersonName();

					// Construire la date de la transaction (jour de prélèvement du mois courant)
					long txDate = buildDateForCurrentMonth(dueDay);

					// Label : Personne · Nom du crédit
					String label = person + " · " + credit.name;

					// Compte joint ?
					String compte = (credit.compte != null
							&& !credit.compte.trim().isEmpty()) ? credit.compte.trim() : "";

					boolean created = addCreditTransactionSync(
							label, credit.monthlyPayment, person, credit.name,
							credit.docId, txDate, creditKey, compte,
							householdId, token);

					if (created) {
						patchLastAppliedMonthSync(credit.docId, currentMonth, householdId, token);
					}
				}

			} catch (Exception ignored) {
			}

			postDone(onDone);
		});
	}

	// ─────────────────────────────────────────────
	// INTERNAL — credit transaction write
	// ─────────────────────────────────────────────

	private boolean addCreditTransactionSync(
			String label,
			double monthlyPayment,
			String person,
			String creditName,
			String creditDocId,
			long txDate,
			String recurringKey,
			String compte,
			String householdId,
			String token) {
		try {
			String path = "households/" + householdId + "/transactions";
			String body = "{\"fields\":{"
					+ "\"label\":{\"stringValue\":\"" + escapeJson(label) + "\"},"
					+ "\"amount\":{\"doubleValue\":" + Math.abs(monthlyPayment) + "},"
					+ "\"type\":{\"stringValue\":\"fixed\"},"
					+ "\"category\":{\"stringValue\":\"Crédits\"},"
					+ "\"userId\":{\"stringValue\":\"" + escapeJson(AuthManager.getInstance().getUserId()) + "\"},"
					+ "\"person\":{\"stringValue\":\"" + escapeJson(person) + "\"},"
					+ "\"date\":{\"integerValue\":\"" + txDate + "\"},"
					+ "\"shared\":{\"booleanValue\":false},"
					+ "\"auto\":{\"booleanValue\":true},"
					+ "\"recurring\":{\"booleanValue\":true},"
					+ "\"isFixedCharge\":{\"booleanValue\":true},"
					+ "\"isShareSplit\":{\"booleanValue\":false},"
					+ "\"isReimbursement\":{\"booleanValue\":false},"
					+ "\"compte\":{\"stringValue\":\"" + escapeJson(compte) + "\"},"
					+ "\"creditId\":{\"stringValue\":\"" + escapeJson(creditDocId) + "\"},"
					+ "\"recurringKey\":{\"stringValue\":\"" + escapeJson(recurringKey) + "\"}"
					+ "}}";

			URL url = new URL(FirebaseConfig.collectionUrl(path));
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("Authorization", "Bearer " + token);
			conn.setConnectTimeout(10000);
			conn.setReadTimeout(10000);
			conn.setDoOutput(true);

			DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
			dos.write(body.getBytes("UTF-8"));
			dos.flush();
			dos.close();

			int code = conn.getResponseCode();
			return code == 200 || code == 201;

		} catch (Exception e) {
			return false;
		}
	}

	private boolean transactionExistsForKey(String recurringKey, String householdId, String token) {
		try {
			String structuredQuery =
					"{\"structuredQuery\":{"
							+ "\"from\":[{\"collectionId\":\"transactions\"}],"
							+ "\"where\":{"
							+ "\"fieldFilter\":{"
							+ "\"field\":{\"fieldPath\":\"recurringKey\"},"
							+ "\"op\":\"EQUAL\","
							+ "\"value\":{\"stringValue\":\"" + escapeJson(recurringKey) + "\"}"
							+ "}"
							+ "},"
							+ "\"limit\":1"
							+ "}}";

			String urlString =
					"https://firestore.googleapis.com/v1/projects/"
							+ PROJECT_ID
							+ "/databases/(default)/documents/households/"
							+ URLEncoder.encode(householdId, "UTF-8")
							+ ":runQuery?key=" + API_KEY;

			URL url = new URL(urlString);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("Authorization", "Bearer " + token);
			conn.setConnectTimeout(10000);
			conn.setReadTimeout(10000);
			conn.setDoOutput(true);

			DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
			dos.write(structuredQuery.getBytes("UTF-8"));
			dos.flush();
			dos.close();

			if (conn.getResponseCode() != 200) return false;
			return safeRead(conn.getInputStream()).contains("\"document\"");

		} catch (Exception e) {
			return false;
		}
	}

	private void patchLastAppliedMonthSync(String docId, String month,
			String householdId, String token) {
		try {
			String urlStr = BASE_URL
					+ "households/" + householdId + "/credits/" + docId
					+ "?updateMask.fieldPaths=lastAppliedMonth&key=" + API_KEY;

			String body = "{\"fields\":{"
					+ "\"lastAppliedMonth\":{\"stringValue\":\"" + escapeJson(month) + "\"}"
					+ "}}";

			HttpURLConnection conn = openRaw(urlStr, "PATCH", token, true);
			DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
			dos.write(body.getBytes("UTF-8"));
			dos.flush();
			dos.close();
			conn.getResponseCode(); // fire and forget
		} catch (Exception ignored) {}
	}

	private boolean isCreditActiveThisMonth(CreditsModels.Credit credit) {
		if (credit == null || credit.startDateMs <= 0 || credit.durationMonths <= 0)
			return false;

		Calendar start = Calendar.getInstance();
		start.setTimeInMillis(credit.startDateMs);

		Calendar end = Calendar.getInstance();
		end.setTimeInMillis(credit.startDateMs);
		end.add(Calendar.MONTH, credit.durationMonths);

		Calendar now = Calendar.getInstance();
		return !now.before(start) && !now.after(end);
	}

	private String getCurrentPersonName() {
		String name = "";
		try { name = UserSession.getInstance().getNameOrFallback(); } catch (Exception ignored) {}
		if (name == null || name.trim().isEmpty() || name.contains("@")) {
			try { name = AuthManager.getInstance().getDisplayName(); } catch (Exception ignored) {}
		}
		return (name == null || name.trim().isEmpty() || name.contains("@")) ? "Moi" : name.trim();
	}

	private String getCurrentMonth() {
		Calendar c = Calendar.getInstance();
		return c.get(Calendar.YEAR) + "-"
				+ String.format(java.util.Locale.US, "%02d", c.get(Calendar.MONTH) + 1);
	}

	private int getDayOfMonth() {
		return Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
	}

	private long buildDateForCurrentMonth(int dayOfMonth) {
		int safe = Math.max(1, Math.min(28, dayOfMonth));
		Calendar c = Calendar.getInstance();
		c.set(Calendar.DAY_OF_MONTH, safe);
		c.set(Calendar.HOUR_OF_DAY, 9);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		return c.getTimeInMillis();
	}

	private void postDone(Runnable onDone) {
		if (onDone != null) handler.post(onDone);
	}

	// ─────────────────────────────────────────────
	// INTERNAL HTTP helpers
	// ─────────────────────────────────────────────

	private HttpURLConnection open(String path, String method, String token, boolean output)
			throws Exception {
		return openRaw(BASE_URL + path + "?key=" + API_KEY, method, token, output);
	}

	private HttpURLConnection openRaw(String urlStr, String method, String token, boolean output)
			throws Exception {
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
			String response = safeRead(code == 200 || code == 201
					? conn.getInputStream() : conn.getErrorStream());

			if (code == 200 || code == 201) {
				handler.post(() -> cb.onSuccess(response));
			} else {
				handler.post(() -> cb.onError("Code: " + code + " - " + response));
			}
		} catch (Exception e) {
			handler.post(() -> cb.onError(e.getMessage()));
		}
	}

	private String safeRead(InputStream is) {
		if (is == null) return "";
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(is));
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = br.readLine()) != null) sb.append(line);
			return sb.toString();
		} catch (Exception e) {
			return "";
		}
	}

	private String escape(String value) {
		if (value == null) return "";
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private String escapeJson(String value) {
		return escape(value);
	}
}
