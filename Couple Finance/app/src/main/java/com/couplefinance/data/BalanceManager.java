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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * BalanceManager
 *
 * Gère les soldes de début de cycle et les découverts dans Firestore.
 *
 * Synchronisation bidirectionnelle :
 *   - saveMonthlyStartBalance() écrit dans /balances/{userId}_{cycleKey}  (lu par HomeView)
 *     ET dans /persons/{docId}.monthlyStartBalance                         (lu par SettingsRepository)
 *
 * Cycle financier :
 *   La clé de cycle (ex : "2026-06") est désormais fournie par CycleManager.
 *   Si cycleStartDay=1 → comportement identique à avant (premier du mois).
 *   Si cycleStartDay=5 → cycle du 5 juin : clé "2026-06".
 */
public class BalanceManager {

	private static final String PROJECT_ID = FirebaseConfig.PROJECT_ID;
	private static final String API_KEY    = FirebaseConfig.API_KEY;

	private static final String BASE_URL = "https://firestore.googleapis.com/v1/projects/"
			+ PROJECT_ID + "/databases/(default)/documents/";

	private static final String PREFS_NAME = "home_cache";

	private static BalanceManager instance;

	private final Executor executor = Executors.newFixedThreadPool(2);
	private final Handler  handler  = new Handler(Looper.getMainLooper());

	private Context context;

	public static BalanceManager getInstance() {
		if (instance == null) instance = new BalanceManager();
		return instance;
	}

	public void init(Context ctx) {
		if (ctx != null) context = ctx.getApplicationContext();
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Solde de début de cycle — écriture (double sync /balances/ + /persons/)
	// ─────────────────────────────────────────────────────────────────────────

	public void saveMonthlyStartBalance(double amount, FirestoreManager.Callback cb) {
		long anchorDate = System.currentTimeMillis();

		// Cache local immédiat (affichage instantané)
		saveMonthlyStartBalanceLocal(amount, anchorDate);

		executor.execute(() -> {
			try {
				String token       = AuthManager.getInstance().getFreshTokenSync();
				String userId      = AuthManager.getInstance().getUserId();
				String householdId = HouseholdManager.getInstance().getHouseholdId();

				if (!isValid(token) || !isValid(userId) || !isValid(householdId)) {
					handler.post(() -> cb.onError("Session invalide"));
					return;
				}

				// ── 1. Écriture dans /balances/ ──────────────────────────────────────
				// La clé utilise désormais CycleManager (ex : "2026-06" ou "2026-05"
				// selon le cycleStartDay configuré).
				String cycleKey = getCurrentCycleKey();
				String docId    = userId + "_" + cycleKey;

				String urlStr = BASE_URL + "households/" + householdId
						+ "/balances/" + docId
						+ "?updateMask.fieldPaths=balance"
						+ "&updateMask.fieldPaths=month"
						+ "&updateMask.fieldPaths=userId"
						+ "&updateMask.fieldPaths=anchorDate"
						+ "&updateMask.fieldPaths=updatedAt"
						+ "&key=" + API_KEY;

				HttpURLConnection conn = open(urlStr, "PATCH", token, true);

				String body = "{\"fields\":{"
						+ "\"balance\":{\"doubleValue\":"   + amount      + "},"
						+ "\"month\":{\"stringValue\":\""   + cycleKey    + "\"},"
						+ "\"userId\":{\"stringValue\":\""  + userId      + "\"},"
						+ "\"anchorDate\":{\"integerValue\":\"" + anchorDate + "\"},"
						+ "\"updatedAt\":{\"integerValue\":\"" + System.currentTimeMillis() + "\"}"
						+ "}}";

				conn.getOutputStream().write(body.getBytes("UTF-8"));

				int code     = conn.getResponseCode();
				String response = safeRead(code == 200 ? conn.getInputStream() : conn.getErrorStream());

				if (code != 200) {
					handler.post(() -> cb.onError("Code: " + code + " - " + response));
					return;
				}

				// ── 2. Sync vers /persons/ (fire-and-forget) ─────────────────────────
				syncMonthlyBalanceToPersons(token, userId, householdId, amount);

				handler.post(() -> cb.onSuccess(String.valueOf(amount)));

			} catch (Exception e) {
				handler.post(() -> cb.onError(e.getMessage()));
			}
		});
	}

	/**
	 * Cherche le document /persons/ appartenant à userId et y patche
	 * monthlyStartBalance. Fire-and-forget.
	 */
	private void syncMonthlyBalanceToPersons(
			String token, String userId, String householdId, double amount) {
		try {
			String listUrl = BASE_URL + "households/" + householdId + "/persons?key=" + API_KEY;
			HttpURLConnection getConn = open(listUrl, "GET", token, false);
			if (getConn.getResponseCode() != 200) return;

			String personsJson = safeRead(getConn.getInputStream());
			String personDocPath = findPersonDocPath(personsJson, userId);
			if (personDocPath == null || personDocPath.trim().isEmpty()) return;

			String patchUrl = BASE_URL + personDocPath
					+ "?updateMask.fieldPaths=monthlyStartBalance"
					+ "&key=" + API_KEY;

			HttpURLConnection patchConn = open(patchUrl, "PATCH", token, true);

			String patchBody = "{\"fields\":{"
					+ "\"monthlyStartBalance\":{\"doubleValue\":" + amount + "}"
					+ "}}";

			patchConn.getOutputStream().write(patchBody.getBytes("UTF-8"));
			patchConn.getResponseCode(); // fire-and-forget

		} catch (Exception ignored) {}
	}

	private String findPersonDocPath(String personsJson, String userId) {
		if (personsJson == null || personsJson.isEmpty()) return null;
		try {
			JSONObject root = new JSONObject(personsJson);
			JSONArray  docs = root.optJSONArray("documents");
			if (docs == null) return null;

			String displayName = AuthManager.getInstance().getDisplayName();

			for (int i = 0; i < docs.length(); i++) {
				JSONObject doc    = docs.optJSONObject(i);
				if (doc == null) continue;
				String fullName   = doc.optString("name", "");
				JSONObject fields = doc.optJSONObject("fields");
				if (fields == null) continue;

				String docUserId = readString(fields, "userId");
				if (isValid(docUserId) && docUserId.equals(userId)) {
					return cleanDocumentPath(fullName);
				}

				if (isValid(displayName)) {
					String docName = firstNonEmpty(
							readString(fields, "name"),
							readString(fields, "displayName"),
							readString(fields, "prenom"),
							readString(fields, "firstName")
					);
					if (isValid(docName) && docName.equalsIgnoreCase(displayName)) {
						return cleanDocumentPath(fullName);
					}
				}
			}
		} catch (Exception ignored) {}
		return null;
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Solde de début de cycle — lecture
	// ─────────────────────────────────────────────────────────────────────────

	public void getMonthlyStartBalance(FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token       = AuthManager.getInstance().getFreshTokenSync();
				String userId      = AuthManager.getInstance().getUserId();
				String householdId = HouseholdManager.getInstance().getHouseholdId();

				if (!isValid(token) || !isValid(userId) || !isValid(householdId)) {
					Double local = getMonthlyStartBalanceLocal();
					handler.post(() -> cb.onSuccess(local == null ? "-1" : String.valueOf(local)));
					return;
				}

				String cycleKey = getCurrentCycleKey();
				String docId    = userId + "_" + cycleKey;
				String urlStr   = BASE_URL + "households/" + householdId
						+ "/balances/" + docId + "?key=" + API_KEY;

				HttpURLConnection conn = open(urlStr, "GET", token, false);
				int code = conn.getResponseCode();

				if (code == 200) {
					String response = safeRead(conn.getInputStream());
					String val      = extractFirestoreNumber(response, "balance");
					String anchor   = extractFirestoreNumber(response, "anchorDate");

					if (!val.isEmpty()) {
						try {
							double amount = Double.parseDouble(val);
							long anchorDate = !anchor.isEmpty()
									? Long.parseLong(anchor)
									: getMonthStartMillis();
							saveMonthlyStartBalanceLocal(amount, anchorDate);
						} catch (Exception ignored) {}
						handler.post(() -> cb.onSuccess(val));
						return;
					}
				}

				Double local = getMonthlyStartBalanceLocal();
				handler.post(() -> cb.onSuccess(local == null ? "-1" : String.valueOf(local)));

			} catch (Exception e) {
				Double local = getMonthlyStartBalanceLocal();
				handler.post(() -> cb.onSuccess(local == null ? "-1" : String.valueOf(local)));
			}
		});
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Découvert
	// ─────────────────────────────────────────────────────────────────────────

	public void saveOverdraftLimit(double amount, FirestoreManager.Callback cb) {
		double cleanAmount = amount == 0 ? 0 : -Math.abs(amount);
		saveOverdraftLimitLocal(cleanAmount);

		executor.execute(() -> {
			try {
				String token       = AuthManager.getInstance().getFreshTokenSync();
				String userId      = AuthManager.getInstance().getUserId();
				String householdId = HouseholdManager.getInstance().getHouseholdId();

				if (!isValid(token) || !isValid(userId) || !isValid(householdId)) {
					handler.post(() -> cb.onError("Session invalide"));
					return;
				}

				String docId  = userId + "_overdraft";
				String urlStr = BASE_URL + "households/" + householdId
						+ "/balances/" + docId
						+ "?updateMask.fieldPaths=overdraftLimit"
						+ "&updateMask.fieldPaths=userId"
						+ "&updateMask.fieldPaths=updatedAt"
						+ "&key=" + API_KEY;

				HttpURLConnection conn = open(urlStr, "PATCH", token, true);

				String body = "{\"fields\":{"
						+ "\"overdraftLimit\":{\"doubleValue\":" + cleanAmount + "},"
						+ "\"userId\":{\"stringValue\":\""       + userId      + "\"},"
						+ "\"updatedAt\":{\"integerValue\":\""   + System.currentTimeMillis() + "\"}"
						+ "}}";

				conn.getOutputStream().write(body.getBytes("UTF-8"));
				int code     = conn.getResponseCode();
				String response = safeRead(code == 200 ? conn.getInputStream() : conn.getErrorStream());

				if (code == 200) {
					handler.post(() -> cb.onSuccess(String.valueOf(cleanAmount)));
				} else {
					handler.post(() -> cb.onError("Code: " + code + " - " + response));
				}

			} catch (Exception e) {
				handler.post(() -> cb.onError(e.getMessage()));
			}
		});
	}

	public void getOverdraftLimit(FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token       = AuthManager.getInstance().getFreshTokenSync();
				String userId      = AuthManager.getInstance().getUserId();
				String householdId = HouseholdManager.getInstance().getHouseholdId();

				if (!isValid(token) || !isValid(userId) || !isValid(householdId)) {
					Double local = getOverdraftLimitLocal();
					handler.post(() -> cb.onSuccess(local == null ? "0" : String.valueOf(local)));
					return;
				}

				String docId  = userId + "_overdraft";
				String urlStr = BASE_URL + "households/" + householdId
						+ "/balances/" + docId + "?key=" + API_KEY;

				HttpURLConnection conn = open(urlStr, "GET", token, false);
				int code = conn.getResponseCode();

				if (code == 200) {
					String response = safeRead(conn.getInputStream());
					String val      = extractFirestoreNumber(response, "overdraftLimit");

					if (!val.isEmpty()) {
						try { saveOverdraftLimitLocal(Double.parseDouble(val)); }
						catch (Exception ignored) {}
						handler.post(() -> cb.onSuccess(val));
						return;
					}
				}

				Double local = getOverdraftLimitLocal();
				handler.post(() -> cb.onSuccess(local == null ? "0" : String.valueOf(local)));

			} catch (Exception e) {
				Double local = getOverdraftLimitLocal();
				handler.post(() -> cb.onSuccess(local == null ? "0" : String.valueOf(local)));
			}
		});
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Cache local
	// ─────────────────────────────────────────────────────────────────────────

	public boolean hasMonthlyStartBalanceLocal() {
		return getMonthlyStartBalanceLocal() != null;
	}

	public void saveMonthlyStartBalanceLocal(double amount) {
		saveMonthlyStartBalanceLocal(amount, getMonthlyStartBalanceDateLocal());
	}

	public void saveMonthlyStartBalanceLocal(double amount, long anchorDate) {
		if (context == null) return;
		if (anchorDate <= 0) anchorDate = getMonthStartMillis();
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
				.putFloat(getMonthlyBalanceCacheKey(), (float) amount)
				.putLong(getMonthlyBalanceDateCacheKey(), anchorDate)
				.apply();
	}

	public Double getMonthlyStartBalanceLocal() {
		if (context == null) return null;
		if (!isValid(AuthManager.getInstance().getUserId())) return null;
		SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		String key = getMonthlyBalanceCacheKey();
		if (!prefs.contains(key)) return null;
		return (double) prefs.getFloat(key, 0f);
	}

	public long getMonthlyStartBalanceDateLocal() {
		if (context == null) return getMonthStartMillis();
		if (!isValid(AuthManager.getInstance().getUserId())) return getMonthStartMillis();
		SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		String key = getMonthlyBalanceDateCacheKey();
		if (!prefs.contains(key)) return getMonthStartMillis();
		return prefs.getLong(key, getMonthStartMillis());
	}

	public void saveOverdraftLimitLocal(double amount) {
		if (context == null) return;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
				.putFloat(getOverdraftCacheKey(), (float) amount)
				.apply();
	}

	public Double getOverdraftLimitLocal() {
		if (context == null) return null;
		if (!isValid(AuthManager.getInstance().getUserId())) return null;
		SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		String key = getOverdraftCacheKey();
		if (!prefs.contains(key)) return null;
		return (double) prefs.getFloat(key, 0f);
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Soldes de tous les membres (HomeView)
	// ─────────────────────────────────────────────────────────────────────────

	public void getAllMembersStartBalance(FirestoreManager.Callback cb) {
		executor.execute(() -> {
			try {
				String token       = AuthManager.getInstance().getFreshTokenSync();
				String householdId = HouseholdManager.getInstance().getHouseholdId();

				if (!isValid(token) || !isValid(householdId)) {
					handler.post(() -> cb.onError("Session invalide"));
					return;
				}

				String urlStr = BASE_URL + "households/" + householdId
						+ "/balances" + "?key=" + API_KEY;

				HttpURLConnection conn = open(urlStr, "GET", token, false);
				int code = conn.getResponseCode();

				if (code == 200) {
					String result = safeRead(conn.getInputStream());
					handler.post(() -> cb.onSuccess(result));
				} else {
					String err = safeRead(conn.getErrorStream());
					handler.post(() -> cb.onError("Code: " + code + " - " + err));
				}

			} catch (Exception e) {
				handler.post(() -> cb.onError(e.getMessage()));
			}
		});
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Clés cache — utilisent désormais CycleManager
	// ─────────────────────────────────────────────────────────────────────────

	private String getMonthlyBalanceCacheKey() {
		String householdId = HouseholdManager.getInstance().getHouseholdId();
		if (householdId == null || householdId.trim().isEmpty()) householdId = "no_household";
		return "monthly_start_balance_"
				+ AuthManager.getInstance().getUserId()
				+ "_" + householdId
				+ "_" + getCurrentCycleKey();
	}

	private String getMonthlyBalanceDateCacheKey() {
		String householdId = HouseholdManager.getInstance().getHouseholdId();
		if (householdId == null || householdId.trim().isEmpty()) householdId = "no_household";
		return "monthly_start_balance_date_"
				+ AuthManager.getInstance().getUserId()
				+ "_" + householdId
				+ "_" + getCurrentCycleKey();
	}

	private String getOverdraftCacheKey() {
		String householdId = HouseholdManager.getInstance().getHouseholdId();
		if (householdId == null || householdId.trim().isEmpty()) householdId = "no_household";
		return "overdraft_limit_"
				+ AuthManager.getInstance().getUserId()
				+ "_" + householdId;
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Utilitaires — délèguent à CycleManager
	// ─────────────────────────────────────────────────────────────────────────

	/**
	 * Clé du cycle courant.
	 * Était "YYYY-MM" calendaire — délègue maintenant à CycleManager.
	 * Format inchangé : "YYYY-MM" (basé sur le mois de début du cycle).
	 */
	private String getCurrentCycleKey() {
		return CycleManager.getInstance().getCurrentCycleKey();
	}

	/**
	 * Timestamp de début du cycle courant.
	 * Était getMonthStartMillis() = 1er du mois — délègue maintenant à CycleManager.
	 */
	public long getMonthStartMillis() {
		return CycleManager.getInstance().getCurrentCycleStartMillis();
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Helpers HTTP / JSON
	// ─────────────────────────────────────────────────────────────────────────

	private HttpURLConnection open(String urlStr, String method,
	                               String token, boolean output) throws Exception {
		HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
		conn.setRequestMethod(method);
		conn.setRequestProperty("Content-Type", "application/json");
		if (isValid(token)) conn.setRequestProperty("Authorization", "Bearer " + token);
		conn.setConnectTimeout(10000);
		conn.setReadTimeout(10000);
		conn.setDoOutput(output);
		return conn;
	}

	private String cleanDocumentPath(String fullPath) {
		if (fullPath == null) return "";
		String p      = fullPath.trim();
		String marker = "/documents/";
		int idx = p.indexOf(marker);
		if (idx >= 0) p = p.substring(idx + marker.length());
		while (p.startsWith("/")) p = p.substring(1);
		return p;
	}

	private String readString(JSONObject fields, String key) {
		try {
			JSONObject obj = fields.optJSONObject(key);
			if (obj == null) return "";
			return obj.optString("stringValue", "").trim();
		} catch (Exception e) { return ""; }
	}

	private String firstNonEmpty(String... values) {
		if (values == null) return "";
		for (String v : values) if (v != null && !v.trim().isEmpty()) return v.trim();
		return "";
	}

	private boolean isValid(String value) {
		return value != null && !value.trim().isEmpty();
	}

	private String extractFirestoreNumber(String json, String fieldName) {
		if (json == null || json.isEmpty()) return "";
		int fieldIndex = json.indexOf("\"" + fieldName + "\"");
		if (fieldIndex < 0) return "";
		String sub = json.substring(fieldIndex, Math.min(json.length(), fieldIndex + 300));
		String[] keys = {"\"doubleValue\":", "\"integerValue\":"};
		for (String key : keys) {
			int i = sub.indexOf(key);
			if (i >= 0) {
				i += key.length();
				while (i < sub.length()
						&& (sub.charAt(i) == ' ' || sub.charAt(i) == '"')) i++;
				int e = i;
				while (e < sub.length()
						&& (Character.isDigit(sub.charAt(e))
						    || sub.charAt(e) == '.'
						    || sub.charAt(e) == '-')) e++;
				if (e > i) return sub.substring(i, e);
			}
		}
		return "";
	}

	private String safeRead(InputStream is) {
		if (is == null) return "";
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(is));
			StringBuilder  sb = new StringBuilder();
			String line;
			while ((line = br.readLine()) != null) sb.append(line);
			return sb.toString();
		} catch (Exception e) { return ""; }
	}
}
