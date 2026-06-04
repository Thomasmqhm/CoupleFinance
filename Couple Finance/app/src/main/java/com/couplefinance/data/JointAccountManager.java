package com.couplefinance.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.couplefinance.AuthManager;
import com.couplefinance.utils.FirebaseConfig;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * JointAccountManager — Compte Joint partagé du foyer.
 *
 * <p>OBJECTIF (refonte) : le Compte Joint n'est plus une donnée locale par
 * appareil mais une vraie donnée partagée stockée dans Firestore :</p>
 *
 * <pre>
 *   households/{householdId}/jointAccount/settings   (config : enabled, name)
 *   households/{householdId}/jointAccount/{yyyy-MM}   (solde de chaque mois)
 * </pre>
 *
 * <p>La sous-collection {@code jointAccount} contient un document "settings"
 * pour la configuration globale, et un document par mois (nommé "yyyy-MM")
 * pour le solde de début de mois. Cette structure respecte l'alternance
 * stricte collection/document imposée par Firestore.</p>
 *
 * <p>Quand X modifie le solde de début de mois, Y le voit après un reload, et
 * inversement, car la donnée transite par Firestore et non plus par les
 * SharedPreferences locales.</p>
 *
 * <h3>Compatibilité</h3>
 * Toutes les signatures synchrones historiques sont conservées
 * ({@code isEnabledLocal()}, {@code getNameLocal()}, {@code getBalanceLocal()},
 * {@code getAnchorLocal()}, {@code setEnabled(...)}, {@code saveBalance(...)},
 * {@code setNameLocal(...)}, {@code setBalanceLocal(...)}, {@code load(...)}).
 * Elles lisent désormais un <b>snapshot mémoire</b> ({@link Snapshot}) qui est
 * hydraté depuis Firestore par {@link #refresh(Context, Runnable)}.
 *
 * <p>Le cache {@link SharedPreferences} reste présent mais uniquement comme
 * <b>fallback hors-ligne</b> : il n'est jamais la source principale.</p>
 *
 * <h3>Utilisation recommandée</h3>
 * <pre>
 *   // Au démarrage d'un écran (Dashboard, Settings, Transactions...) :
 *   JointAccountManager.getInstance().refresh(activity, () -> renderUi());
 *
 *   // À l'enregistrement d'une modification (dialogue Settings) :
 *   JointAccountManager.getInstance().saveSettings(
 *           activity, enabled, name, startBalance, callback);
 * </pre>
 */
public class JointAccountManager {

	public static final String JOINT_MEMBER_ID = "__joint__";
	public static final String DEFAULT_NAME = "Compte joint";

	/** Catégorie Firestore dédiée aux mouvements internes vers/depuis le joint. */
	public static final String JOINT_TRANSFER_CATEGORY = "Virement compte joint";

	// ── Cache local (fallback hors-ligne uniquement) ──────────────
	private static final String PREFS_NAME = "joint_account_prefs";
	private static final String KEY_ENABLED = "joint_enabled";
	private static final String KEY_NAME = "joint_name";
	private static final String KEY_BALANCE_PFX = "joint_balance_";
	private static final String KEY_ANCHOR_PFX = "joint_anchor_";
	private static final String KEY_UPDATED_AT = "joint_updated_at";
	private static final String KEY_SYNCED_HOUSEHOLD = "joint_synced_household";

	/** Anciennes clés (étapes précédentes) — migration ascendante. */
	private static final String LEGACY_PREFS_NAME = "couplefinance_joint_account";
	private static final String LEGACY_KEY_ENABLED = "enabled";
	private static final String LEGACY_KEY_NAME = "name";
	private static final String LEGACY_KEY_INITIAL_BALANCE = "initial_balance";

	private static volatile JointAccountManager instance;

	private final Executor executor = Executors.newSingleThreadExecutor();
	private final Handler handler = new Handler(Looper.getMainLooper());

	private Context context;

	/** Snapshot mémoire — source de vérité pour tous les getters synchrones. */
	private volatile Snapshot snapshot;

	/** Dernière erreur HTTP rencontrée (diagnostic). */
	private volatile String lastError = "";

	private JointAccountManager() {
	}

	public static JointAccountManager getInstance() {
		if (instance == null) {
			synchronized (JointAccountManager.class) {
				if (instance == null) {
					instance = new JointAccountManager();
				}
			}
		}
		return instance;
	}

	// ─────────────────────────────────────────────────────────────
	// Modèle
	// ─────────────────────────────────────────────────────────────

	/** Photo immuable de l'état du compte joint pour le mois courant. */
	public static final class Snapshot {
		public boolean enabled;
		public String name = DEFAULT_NAME;
		public double monthlyStartBalance;
		public long anchorDate;
		public String month = "";
		public String updatedBy = "";
		public long updatedAt;

		Snapshot copy() {
			Snapshot s = new Snapshot();
			s.enabled = enabled;
			s.name = name;
			s.monthlyStartBalance = monthlyStartBalance;
			s.anchorDate = anchorDate;
			s.month = month;
			s.updatedBy = updatedBy;
			s.updatedAt = updatedAt;
			return s;
		}
	}

	public interface Callback {
		void onSuccess();

		void onError(String error);
	}

	public interface DataCallback {
		void onResult(boolean enabled, String name, double balance, long anchorDate);
	}

	public interface SnapshotCallback {
		void onResult(Snapshot snapshot);
	}

	// ─────────────────────────────────────────────────────────────
	// Initialisation
	// ─────────────────────────────────────────────────────────────

	public void init(Context ctx) {
		if (ctx != null && context == null) {
			context = ctx.getApplicationContext();
			migrateLegacyIfNeeded(context);
		}
		if (snapshot == null) {
			snapshot = readLocalSnapshot();
		}
	}

	private Snapshot current() {
		Snapshot s = snapshot;
		if (s == null) {
			s = readLocalSnapshot();
			snapshot = s;
		}
		return s;
	}

	// ─────────────────────────────────────────────────────────────
	// Lecture / écriture distante (Firestore) — API principale
	// ─────────────────────────────────────────────────────────────

	/**
	 * Recharge le compte joint depuis Firestore puis exécute {@code onDone}
	 * sur le thread principal. En cas d'échec réseau, conserve le snapshot
	 * local existant (fallback hors-ligne) et exécute quand même {@code onDone}.
	 */
	public void refresh(Context ctx, Runnable onDone) {
		init(ctx);

		final String householdId = householdIdOrEmpty();
		if (householdId.isEmpty()) {
			// Pas de foyer : on garde le snapshot local tel quel.
			runOnMain(onDone);
			return;
		}

		executor.execute(() -> {
			Snapshot loaded = fetchRemoteSnapshot(householdId);

			if (loaded != null) {
				snapshot = loaded;
				writeLocalSnapshot(loaded, householdId);
			}
			// Si loaded == null : on conserve le snapshot courant (offline).

			runOnMain(onDone);
		});
	}

	/** Variante avec accès direct au snapshot rechargé. */
	public void refresh(Context ctx, SnapshotCallback cb) {
		init(ctx);

		final String householdId = householdIdOrEmpty();
		if (householdId.isEmpty()) {
			final Snapshot s = current().copy();
			handler.post(() -> {
				if (cb != null) cb.onResult(s);
			});
			return;
		}

		executor.execute(() -> {
			Snapshot loaded = fetchRemoteSnapshot(householdId);

			if (loaded != null) {
				snapshot = loaded;
				writeLocalSnapshot(loaded, householdId);
			}

			final Snapshot result = current().copy();
			handler.post(() -> {
				if (cb != null) cb.onResult(result);
			});
		});
	}

	/**
	 * Enregistre la configuration complète du compte joint dans Firestore
	 * (settings/current + monthlyBalances/{mois courant}).
	 */
	public void saveSettings(Context ctx, boolean enabled, String name,
			double monthlyStartBalance, Callback cb) {
		init(ctx);

		final String householdId = householdIdOrEmpty();
		final String cleanName = normalizeName(name);
		final String month = monthKey();
		final long now = System.currentTimeMillis();
		// L'ancrage du Compte Joint est le JOUR de saisie du solde (et non le
		// 1er du mois). Le solde saisi reflète l'état du compte ce jour-là,
		// avant les opérations du jour : seules les transactions à partir de
		// cette date comptent. Cela aligne le Compte Joint sur le comportement
		// des comptes membres (un virement antérieur n'est pas recompté).
		final long anchor = todayStartMillis();
		final String updatedBy = currentUserId();

		// Mise à jour optimiste du snapshot mémoire + cache local immédiate.
		Snapshot optimistic = new Snapshot();
		optimistic.enabled = enabled;
		optimistic.name = cleanName;
		optimistic.monthlyStartBalance = monthlyStartBalance;
		optimistic.anchorDate = anchor;
		optimistic.month = month;
		optimistic.updatedBy = updatedBy;
		optimistic.updatedAt = now;
		snapshot = optimistic;
		writeLocalSnapshot(optimistic, householdId);

		if (householdId.isEmpty()) {
			// Aucun foyer : on reste en local uniquement.
			notifySuccess(cb);
			return;
		}

		executor.execute(() -> {
			lastError = "";

			boolean okSettings = writeSettingsDocument(
					householdId, enabled, cleanName, monthlyStartBalance,
					anchor, month, updatedBy, now);

			boolean okMonth = writeMonthlyBalanceDocument(
					householdId, month, monthlyStartBalance, anchor,
					cleanName, updatedBy, now);

			if (okSettings && okMonth) {
				notifySuccess(cb);
			} else {
				// Remonte la cause HTTP réelle pour faciliter le diagnostic.
				String detail = lastError == null || lastError.isEmpty()
						? "cause inconnue"
						: lastError;
				notifyError(cb, "Échec synchro compte joint — " + detail);
			}
		});
	}

	/**
	 * Enregistre uniquement le solde de début de mois du compte joint
	 * (conserve l'état activé et le nom courants).
	 */
	public void saveMonthlyStartBalance(Context ctx, double monthlyStartBalance, Callback cb) {
		Snapshot s = current();
		saveSettings(ctx, s.enabled, s.name, monthlyStartBalance, cb);
	}

	// ─────────────────────────────────────────────────────────────
	// API synchrone historique (lit le snapshot mémoire)
	// ─────────────────────────────────────────────────────────────

	public void setEnabled(boolean enabled) {
		Snapshot s = current();
		saveSettings(context, enabled, s.name, s.monthlyStartBalance, null);
	}

	public void setEnabled(Context ctx, boolean enabled) {
		init(ctx);
		Snapshot s = current();
		saveSettings(ctx, enabled, s.name, s.monthlyStartBalance, null);
	}

	public void setEnabled(boolean enabled, String name, Callback cb) {
		Snapshot s = current();
		saveSettings(context, enabled, name, s.monthlyStartBalance, cb);
	}

	public void setEnabled(Context ctx, boolean enabled, String name, Callback cb) {
		init(ctx);
		Snapshot s = current();
		saveSettings(ctx, enabled, name, s.monthlyStartBalance, cb);
	}

	public void saveBalance(double amount, Callback cb) {
		Snapshot s = current();
		saveSettings(context, s.enabled, s.name, amount, cb);
	}

	public void saveBalance(Context ctx, double amount, Callback cb) {
		init(ctx);
		Snapshot s = current();
		saveSettings(ctx, s.enabled, s.name, amount, cb);
	}

	public void saveBalance(double amount) {
		saveBalance(amount, null);
	}

	/** Alias compatibilité SettingsDialogs. */
	public void setNameLocal(Context ctx, String name) {
		if (ctx == null || name == null) return;
		init(ctx);
		Snapshot s = current();
		saveSettings(ctx, s.enabled, name, s.monthlyStartBalance, null);
	}

	/** Alias compatibilité SettingsDialogs. */
	public void setBalanceLocal(Context ctx, double balance) {
		if (ctx == null) return;
		init(ctx);
		Snapshot s = current();
		saveSettings(ctx, s.enabled, s.name, balance, null);
	}

	public boolean isEnabledLocal() {
		return current().enabled;
	}

	public boolean isEnabledLocal(Context ctx) {
		init(ctx);
		return current().enabled;
	}

	public String getNameLocal() {
		String n = current().name;
		return (n == null || n.trim().isEmpty()) ? DEFAULT_NAME : n;
	}

	public String getNameLocal(Context ctx) {
		init(ctx);
		return getNameLocal();
	}

	public double getBalanceLocal() {
		return current().monthlyStartBalance;
	}

	public double getBalanceLocal(Context ctx) {
		init(ctx);
		return current().monthlyStartBalance;
	}

	public long getAnchorLocal() {
		long a = current().anchorDate;
		return a > 0 ? a : monthStartMillis();
	}

	public long getAnchorLocal(Context ctx) {
		init(ctx);
		return getAnchorLocal();
	}

	public boolean hasBalanceLocal() {
		SharedPreferences p = prefsOrNull();
		return p != null && p.contains(KEY_BALANCE_PFX + monthKey());
	}

	/** Snapshot courant (copie immuable). */
	public Snapshot getSnapshot() {
		return current().copy();
	}

	public void load(DataCallback cb) {
		if (cb == null) return;
		Snapshot s = current();
		handler.post(() -> cb.onResult(s.enabled, getNameLocal(), s.monthlyStartBalance, getAnchorLocal()));
	}

	public void load(Context ctx, DataCallback cb) {
		init(ctx);
		load(cb);
	}

	/** Charge le compte joint depuis Firestore puis renvoie les données. */
	public void loadFromFirestore(Context ctx, DataCallback cb) {
		refresh(ctx, () -> {
			if (cb != null) {
				Snapshot s = current();
				cb.onResult(s.enabled, getNameLocal(), s.monthlyStartBalance, getAnchorLocal());
			}
		});
	}

	// ─────────────────────────────────────────────────────────────
	// Firestore REST — lecture
	// ─────────────────────────────────────────────────────────────

	private Snapshot fetchRemoteSnapshot(String householdId) {
		String month = monthKey();

		// 1) Tente le document mensuel (prioritaire pour le solde courant).
		Snapshot monthly = readDocument(
				"households/" + householdId + "/jointAccount/" + month);

		// 2) Tente le document de réglages global.
		Snapshot settings = readDocument(
				"households/" + householdId + "/jointAccount/settings");

		if (monthly == null && settings == null) {
			return null; // échec réseau total → fallback local
		}

		Snapshot result = new Snapshot();
		result.month = month;

		// enabled / name proviennent des réglages globaux ; fallback mensuel.
		if (settings != null) {
			result.enabled = settings.enabled;
			result.name = settings.name;
		} else {
			result.enabled = monthly.enabled;
			result.name = monthly.name;
		}

		// monthlyStartBalance : priorité au document du mois.
		if (monthly != null && monthly.month.equals(month)) {
			result.monthlyStartBalance = monthly.monthlyStartBalance;
			result.anchorDate = monthly.anchorDate > 0 ? monthly.anchorDate : monthStartMillis();
			result.updatedBy = monthly.updatedBy;
			result.updatedAt = monthly.updatedAt;
		} else if (settings != null) {
			result.monthlyStartBalance = settings.monthlyStartBalance;
			result.anchorDate = settings.anchorDate > 0 ? settings.anchorDate : monthStartMillis();
			result.updatedBy = settings.updatedBy;
			result.updatedAt = settings.updatedAt;
		} else {
			result.anchorDate = monthStartMillis();
		}

		if (result.name == null || result.name.trim().isEmpty()) {
			result.name = DEFAULT_NAME;
		}

		return result;
	}

	/** Lit un document Firestore et le mappe en Snapshot, ou null si absent/erreur. */
	private Snapshot readDocument(String path) {
		HttpURLConnection conn = null;
		try {
			String token = AuthManager.getInstance().getToken();
			if (token == null || token.isEmpty()) return null;

			conn = open(FirebaseConfig.documentUrl(path), "GET", token, false);
			int code = conn.getResponseCode();

			if (code == 404) {
				Snapshot empty = new Snapshot();
				empty.anchorDate = monthStartMillis();
				return empty;
			}

			if (code != 200) return null;

			String body = safeRead(conn.getInputStream());
			return parseSnapshot(body);

		} catch (Exception e) {
			return null;
		} finally {
			if (conn != null) conn.disconnect();
		}
	}

	private Snapshot parseSnapshot(String json) {
		Snapshot s = new Snapshot();
		s.anchorDate = monthStartMillis();

		if (json == null || json.isEmpty()) {
			return s;
		}

		try {
			JSONObject root = new JSONObject(json);
			JSONObject fields = root.optJSONObject("fields");
			if (fields == null) {
				return s;
			}

			s.enabled = readBool(fields, "enabled", false);
			String name = readStr(fields, "name");
			s.name = (name == null || name.trim().isEmpty()) ? DEFAULT_NAME : name.trim();
			s.monthlyStartBalance = readNum(fields, "monthlyStartBalance", 0d);

			long anchor = (long) readNum(fields, "anchorDate", 0d);
			s.anchorDate = anchor > 0 ? anchor : monthStartMillis();

			String month = readStr(fields, "month");
			s.month = month == null ? "" : month.trim();

			s.updatedBy = readStr(fields, "updatedBy");
			s.updatedAt = (long) readNum(fields, "updatedAt", 0d);

		} catch (Exception ignored) {
		}

		return s;
	}

	// ─────────────────────────────────────────────────────────────
	// Firestore REST — écriture
	// ─────────────────────────────────────────────────────────────

	private boolean writeSettingsDocument(String householdId, boolean enabled, String name,
			double balance, long anchor, String month, String updatedBy, long updatedAt) {
		String path = "households/" + householdId + "/jointAccount/settings";
		String body = buildFieldsBody(enabled, name, balance, anchor, month, updatedBy, updatedAt);
		return patch(path, body);
	}

	private boolean writeMonthlyBalanceDocument(String householdId, String month,
			double balance, long anchor, String name, String updatedBy, long updatedAt) {
		String path = "households/" + householdId + "/jointAccount/" + month;
		// On stocke aussi name/enabled dans le doc mensuel pour robustesse.
		String body = buildFieldsBody(true, name, balance, anchor, month, updatedBy, updatedAt);
		return patch(path, body);
	}

	private String buildFieldsBody(boolean enabled, String name, double balance,
			long anchor, String month, String updatedBy, long updatedAt) {
		return "{\"fields\":{"
				+ "\"enabled\":{\"booleanValue\":" + enabled + "},"
				+ "\"name\":{\"stringValue\":\"" + escape(name) + "\"},"
				+ "\"monthlyStartBalance\":{\"doubleValue\":" + balance + "},"
				+ "\"anchorDate\":{\"integerValue\":\"" + anchor + "\"},"
				+ "\"month\":{\"stringValue\":\"" + escape(month) + "\"},"
				+ "\"updatedBy\":{\"stringValue\":\"" + escape(updatedBy) + "\"},"
				+ "\"updatedAt\":{\"integerValue\":\"" + updatedAt + "\"}"
				+ "}}";
	}

	private boolean patch(String path, String body) {
		HttpURLConnection conn = null;
		try {
			String token = AuthManager.getInstance().getToken();
			if (token == null || token.isEmpty()) {
				lastError = "token absent (session non authentifiée)";
				return false;
			}

			conn = open(FirebaseConfig.documentUrl(path), "PATCH", token, true);
			writeBody(conn, body);

			int code = conn.getResponseCode();
			if (code >= 200 && code < 300) return true;

			String response = safeRead(conn.getErrorStream());
			if (response != null && response.length() > 180) response = response.substring(0, 180);
			lastError = "HTTP " + code + (response == null || response.isEmpty() ? "" : " · " + response);
			return false;

		} catch (Exception e) {
			lastError = "exception réseau : " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
			return false;
		} finally {
			if (conn != null) conn.disconnect();
		}
	}

	// ─────────────────────────────────────────────────────────────
	// Cache local (fallback hors-ligne uniquement)
	// ─────────────────────────────────────────────────────────────

	private Snapshot readLocalSnapshot() {
		Snapshot s = new Snapshot();
		s.month = monthKey();
		s.anchorDate = monthStartMillis();

		SharedPreferences p = prefsOrNull();
		if (p == null) {
			return s;
		}

		s.enabled = p.getBoolean(KEY_ENABLED, false);
		s.name = p.getString(KEY_NAME, DEFAULT_NAME);

		String key = KEY_BALANCE_PFX + monthKey();
		if (p.contains(key)) {
			s.monthlyStartBalance = p.getFloat(key, 0f);
			s.anchorDate = p.getLong(KEY_ANCHOR_PFX + monthKey(), monthStartMillis());
		} else {
			// Dernier solde connu (tous mois confondus) — report.
			String latest = null;
			for (String k : p.getAll().keySet()) {
				if (k.startsWith(KEY_BALANCE_PFX) && (latest == null || k.compareTo(latest) > 0)) {
					latest = k;
				}
			}
			if (latest != null) {
				s.monthlyStartBalance = p.getFloat(latest, 0f);
			}
		}

		s.updatedAt = p.getLong(KEY_UPDATED_AT, 0L);

		if (s.name == null || s.name.trim().isEmpty()) {
			s.name = DEFAULT_NAME;
		}
		return s;
	}

	private void writeLocalSnapshot(Snapshot s, String householdId) {
		SharedPreferences p = prefsOrNull();
		if (p == null || s == null) {
			return;
		}

		String month = monthKey();
		p.edit()
				.putBoolean(KEY_ENABLED, s.enabled)
				.putString(KEY_NAME, normalizeName(s.name))
				.putFloat(KEY_BALANCE_PFX + month, (float) s.monthlyStartBalance)
				.putLong(KEY_ANCHOR_PFX + month, s.anchorDate > 0 ? s.anchorDate : monthStartMillis())
				.putLong(KEY_UPDATED_AT, s.updatedAt > 0 ? s.updatedAt : System.currentTimeMillis())
				.putString(KEY_SYNCED_HOUSEHOLD, householdId == null ? "" : householdId)
				.apply();
	}

	private void migrateLegacyIfNeeded(Context ctx) {
		SharedPreferences p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		if (p.contains(KEY_ENABLED) || p.contains(KEY_NAME)) {
			return;
		}

		SharedPreferences legacy = ctx.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE);
		boolean enabled = legacy.getBoolean(LEGACY_KEY_ENABLED, false);
		String name = legacy.getString(LEGACY_KEY_NAME, DEFAULT_NAME);
		float balance = legacy.getFloat(LEGACY_KEY_INITIAL_BALANCE, 0f);
		long now = System.currentTimeMillis();

		p.edit()
				.putBoolean(KEY_ENABLED, enabled)
				.putString(KEY_NAME, normalizeName(name))
				.putFloat(KEY_BALANCE_PFX + monthKey(), balance)
				.putLong(KEY_ANCHOR_PFX + monthKey(), now)
				.putLong(KEY_UPDATED_AT, now)
				.apply();
	}

	private SharedPreferences prefsOrNull() {
		if (context == null) {
			return null;
		}
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
	}

	// ─────────────────────────────────────────────────────────────
	// HTTP helpers
	// ─────────────────────────────────────────────────────────────

	private HttpURLConnection open(String urlStr, String method, String token, boolean output)
			throws Exception {
		URL url = new URL(urlStr);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod(method);
		conn.setRequestProperty("Content-Type", "application/json");

		if (token != null && !token.isEmpty()) {
			conn.setRequestProperty("Authorization", "Bearer " + token);
		}

		conn.setConnectTimeout(12000);
		conn.setReadTimeout(12000);

		if (output) {
			conn.setDoOutput(true);
		}
		return conn;
	}

	private void writeBody(HttpURLConnection conn, String body) throws Exception {
		try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
			dos.write(body.getBytes("UTF-8"));
		}
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

	// ─────────────────────────────────────────────────────────────
	// JSON helpers
	// ─────────────────────────────────────────────────────────────

	private String readStr(JSONObject fields, String key) {
		try {
			JSONObject obj = fields.optJSONObject(key);
			if (obj == null) {
				return "";
			}
			return obj.optString("stringValue", "").trim();
		} catch (Exception e) {
			return "";
		}
	}

	private double readNum(JSONObject fields, String key, double def) {
		try {
			JSONObject obj = fields.optJSONObject(key);
			if (obj == null) {
				return def;
			}
			if (obj.has("doubleValue")) {
				return obj.optDouble("doubleValue", def);
			}
			if (obj.has("integerValue")) {
				return Double.parseDouble(obj.optString("integerValue", String.valueOf(def)));
			}
			return def;
		} catch (Exception e) {
			return def;
		}
	}

	private boolean readBool(JSONObject fields, String key, boolean def) {
		try {
			JSONObject obj = fields.optJSONObject(key);
			if (obj == null) {
				return def;
			}
			return obj.optBoolean("booleanValue", def);
		} catch (Exception e) {
			return def;
		}
	}

	// ─────────────────────────────────────────────────────────────
	// Divers
	// ─────────────────────────────────────────────────────────────

	private String householdIdOrEmpty() {
		try {
			String id = HouseholdManager.getInstance().getHouseholdId();
			return id == null ? "" : id.trim();
		} catch (Exception e) {
			return "";
		}
	}

	private String currentUserId() {
		try {
			String id = AuthManager.getInstance().getUserId();
			return id == null ? "" : id.trim();
		} catch (Exception e) {
			return "";
		}
	}

	private String normalizeName(String name) {
		if (name == null) {
			return DEFAULT_NAME;
		}
		String clean = name.trim();
		return clean.isEmpty() ? DEFAULT_NAME : clean;
	}

	private String escape(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private String monthKey() {
		Calendar c = Calendar.getInstance();
		int month = c.get(Calendar.MONTH) + 1;
		return c.get(Calendar.YEAR) + "-" + (month < 10 ? "0" + month : String.valueOf(month));
	}

	private long monthStartMillis() {
		Calendar c = Calendar.getInstance();
		c.set(Calendar.DAY_OF_MONTH, 1);
		c.set(Calendar.HOUR_OF_DAY, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		return c.getTimeInMillis();
	}

	/** Début du jour courant (minuit) — ancrage par défaut du Compte Joint. */
	private long todayStartMillis() {
		Calendar c = Calendar.getInstance();
		c.set(Calendar.HOUR_OF_DAY, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		return c.getTimeInMillis();
	}

	private void runOnMain(Runnable r) {
		if (r != null) {
			handler.post(r);
		}
	}

	private void notifySuccess(Callback cb) {
		if (cb != null) {
			handler.post(cb::onSuccess);
		}
	}

	private void notifyError(Callback cb, String error) {
		if (cb != null) {
			handler.post(() -> cb.onError(error));
		}
	}
}
