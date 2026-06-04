package com.couplefinance.data;

import android.content.Context;
import android.content.SharedPreferences;
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
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * CycleManager — Gestion du cycle financier configurable.
 *
 * Le cycle peut démarrer n'importe quel jour du mois (1 à 28).
 * Défaut : 1er du mois (comportement calendaire identique à l'ancienne logique).
 *
 * Exemples (cycleStartDay = 5) :
 *   Appel le 10 juin → cycle actuel  : 5 juin → 4 juillet
 *   Appel le 3  juin → cycle actuel  : 5 mai  → 4 juin
 *
 * Rétrocompatibilité totale :
 *   getCycleKey() retourne TOUJOURS "YYYY-MM" basé sur le mois du DÉBUT du cycle.
 *   → Les clés Firestore (lastAppliedMonth, balance/{docId}) restent au même format.
 *   → Si cycleStartDay=1, le comportement est identique à avant.
 *
 * Utilisation :
 *   CycleManager.getInstance().init(ctx);          // DashboardActivity.onCreate()
 *   CycleManager.getInstance().loadFromFirestore(); // synchro entre membres
 *   CycleManager.getInstance().isInCurrentCycle(dateMs);
 *   CycleManager.getInstance().getCurrentCycleKey();
 */
public class CycleManager {

    private static final String PREFS_NAME        = "cycle_prefs";
    private static final String KEY_START_DAY     = "cycle_start_day";
    public  static final int    DEFAULT_START_DAY = 1;

    private static final String BASE_URL =
            "https://firestore.googleapis.com/v1/projects/"
            + FirebaseConfig.PROJECT_ID
            + "/databases/(default)/documents/";

    private static volatile CycleManager instance;

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler  handler  = new Handler(Looper.getMainLooper());

    private Context context;
    private int     cachedStartDay = DEFAULT_START_DAY;

    private CycleManager() {}

    public static CycleManager getInstance() {
        if (instance == null) {
            synchronized (CycleManager.class) {
                if (instance == null) instance = new CycleManager();
            }
        }
        return instance;
    }

    // ─────────────────────────────────────────────────────────────
    // Initialisation
    // ─────────────────────────────────────────────────────────────

    /**
     * À appeler dans DashboardActivity.onCreate() juste après BalanceManager.init().
     */
    public void init(Context ctx) {
        if (ctx == null) return;
        context        = ctx.getApplicationContext();
        cachedStartDay = readLocal();
    }

    // ─────────────────────────────────────────────────────────────
    // Persistance locale
    // ─────────────────────────────────────────────────────────────

    private int readLocal() {
        if (context == null) return DEFAULT_START_DAY;
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                      .getInt(KEY_START_DAY, DEFAULT_START_DAY);
    }

    private void writeLocal(int day) {
        cachedStartDay = day;
        if (context == null) return;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
               .edit().putInt(KEY_START_DAY, day).apply();
    }

    // ─────────────────────────────────────────────────────────────
    // API publique — configuration
    // ─────────────────────────────────────────────────────────────

    /** Jour configuré de démarrage du cycle (1-28). */
    public int getCycleStartDay() {
        return cachedStartDay;
    }

    /**
     * Sauvegarde le jour de démarrage localement ET dans Firestore
     * (champ cycleStartDay du document household — partagé entre les deux membres).
     *
     * Appeler depuis SettingsView, après que l'utilisateur a choisi un jour.
     * Appeler ensuite NotificationScheduler.scheduleAll(ctx) pour reprogrammer
     * l'alarme de saisie de solde au nouveau jour.
     */
    public void saveCycleStartDay(int day, FirestoreManager.Callback cb) {
        int cleanDay = Math.max(1, Math.min(28, day));
        writeLocal(cleanDay);

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String token       = AuthManager.getInstance().getFreshTokenSync();
                String householdId = HouseholdManager.getInstance().getHouseholdId();

                if (!valid(token) || !valid(householdId)) {
                    handler.post(() -> cb.onSuccess("local_only"));
                    return;
                }

                String urlStr = BASE_URL + "households/" + householdId
                        + "?updateMask.fieldPaths=cycleStartDay"
                        + "&key=" + FirebaseConfig.API_KEY;

                conn = open(urlStr, "PATCH", token, true);
                String body = "{\"fields\":{\"cycleStartDay\":{\"integerValue\":\""
                        + cleanDay + "\"}}}";

                try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
                    dos.write(body.getBytes("UTF-8"));
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    handler.post(() -> cb.onSuccess(String.valueOf(cleanDay)));
                } else {
                    String err = safeRead(conn.getErrorStream());
                    handler.post(() -> cb.onError("Code: " + code + " – " + err));
                }

            } catch (Exception e) {
                handler.post(() -> cb.onError(e.getMessage()));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    /**
     * Charge cycleStartDay depuis Firestore et met à jour le cache local.
     * À appeler au démarrage (fire-and-forget, n'affecte pas le flux principal).
     */
    public void loadFromFirestore(FirestoreManager.Callback cb) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String token       = AuthManager.getInstance().getFreshTokenSync();
                String householdId = HouseholdManager.getInstance().getHouseholdId();

                if (!valid(token) || !valid(householdId)) {
                    handler.post(() -> cb.onSuccess(String.valueOf(cachedStartDay)));
                    return;
                }

                String urlStr = BASE_URL + "households/" + householdId
                        + "?key=" + FirebaseConfig.API_KEY;

                conn = open(urlStr, "GET", token, false);

                if (conn.getResponseCode() == 200) {
                    String json = safeRead(conn.getInputStream());
                    int day = parseIntField(json, "cycleStartDay");
                    if (day >= 1 && day <= 28) writeLocal(day);
                }

                handler.post(() -> cb.onSuccess(String.valueOf(cachedStartDay)));

            } catch (Exception e) {
                handler.post(() -> cb.onSuccess(String.valueOf(cachedStartDay)));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────
    // API publique — bornes du cycle
    // ─────────────────────────────────────────────────────────────

    /**
     * Timestamp de DÉBUT du cycle actuel (00:00:00.000).
     *
     * Exemples (cycleStartDay=5) :
     *   Appel le 10 juin → 5 juin  00:00:00
     *   Appel le 3  juin → 5 mai   00:00:00
     */
    public long getCurrentCycleStartMillis() {
        return cycleStart(cachedStartDay, Calendar.getInstance());
    }

    /**
     * Timestamp de FIN du cycle actuel (23:59:59.999).
     * = début du cycle suivant – 1 ms.
     */
    public long getCurrentCycleEndMillis() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(getCurrentCycleStartMillis());
        c.add(Calendar.MONTH, 1);
        return c.getTimeInMillis() - 1L;
    }

    /** Timestamp de début du cycle PRÉCÉDENT. */
    public long getPreviousCycleStartMillis() {
        Calendar ref = Calendar.getInstance();
        ref.add(Calendar.MONTH, -1);
        return cycleStart(cachedStartDay, ref);
    }

    /** Timestamp de fin du cycle PRÉCÉDENT (= début cycle actuel – 1 ms). */
    public long getPreviousCycleEndMillis() {
        return getCurrentCycleStartMillis() - 1L;
    }

    // ─────────────────────────────────────────────────────────────
    // API publique — appartenance
    // ─────────────────────────────────────────────────────────────

    /** Vrai si dateMs appartient au cycle financier COURANT. */
    public boolean isInCurrentCycle(long dateMs) {
        if (dateMs <= 0) return false;
        return dateMs >= getCurrentCycleStartMillis()
            && dateMs <= getCurrentCycleEndMillis();
    }

    /** Vrai si dateMs appartient au cycle financier PRÉCÉDENT. */
    public boolean isInPreviousCycle(long dateMs) {
        if (dateMs <= 0) return false;
        return dateMs >= getPreviousCycleStartMillis()
            && dateMs <= getPreviousCycleEndMillis();
    }

    // ─────────────────────────────────────────────────────────────
    // API publique — clés et labels
    // ─────────────────────────────────────────────────────────────

    /**
     * Clé unique du cycle courant — format "YYYY-MM".
     *
     * Basée sur le mois du DÉBUT du cycle (pas du jour courant).
     * Compatible avec les données existantes (lastAppliedMonth, clés balance).
     *
     * Exemples (cycleStartDay=5) :
     *   Le 10 juin → cycle commence le 5 juin → "2026-06"
     *   Le 3  juin → cycle commence le 5 mai  → "2026-05"
     *   Le 3  juin + cycleStartDay=1 → "2026-06" (calendaire, inchangé)
     */
    public String getCurrentCycleKey() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(getCurrentCycleStartMillis());
        return String.format(Locale.US, "%04d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
    }

    /**
     * Label lisible du cycle courant.
     * Exemple : "5 juin → 4 juil. 2026"
     */
    public String getCurrentCycleLabel() {
        final String[] months = {
            "janv.", "févr.", "mars", "avr.", "mai", "juin",
            "juil.", "août", "sept.", "oct.", "nov.", "déc."
        };
        Calendar s = Calendar.getInstance();
        s.setTimeInMillis(getCurrentCycleStartMillis());
        Calendar e = Calendar.getInstance();
        e.setTimeInMillis(getCurrentCycleEndMillis());

        return s.get(Calendar.DAY_OF_MONTH) + " " + months[s.get(Calendar.MONTH)]
             + " → "
             + e.get(Calendar.DAY_OF_MONTH) + " " + months[e.get(Calendar.MONTH)]
             + " " + e.get(Calendar.YEAR);
    }

    // ─────────────────────────────────────────────────────────────
    // Calcul interne
    // ─────────────────────────────────────────────────────────────

    /**
     * Calcule le timestamp de début de cycle pour un startDay
     * et une date de référence quelconque.
     *
     * Règle :
     *   Si DOM(ref) >= startDay → cycle a commencé CE mois-ci
     *   Sinon                   → cycle a commencé LE MOIS DERNIER
     */
    private long cycleStart(int startDay, Calendar ref) {
        int day   = Math.max(1, Math.min(28, startDay));
        int year  = ref.get(Calendar.YEAR);
        int month = ref.get(Calendar.MONTH);
        int dom   = ref.get(Calendar.DAY_OF_MONTH);

        if (dom < day) {
            // On est avant le startDay → le cycle a commencé le mois précédent
            month--;
            if (month < 0) { month = 11; year--; }
        }

        Calendar c = Calendar.getInstance();
        c.set(year, month, day, 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers HTTP / parsing
    // ─────────────────────────────────────────────────────────────

    private HttpURLConnection open(String urlStr, String method,
                                   String token, boolean output) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json");
        if (valid(token)) conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setDoOutput(output);
        return conn;
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

    private int parseIntField(String json, String field) {
        if (json == null || json.isEmpty()) return 0;
        int idx = json.indexOf("\"" + field + "\"");
        if (idx < 0) return 0;
        String sub = json.substring(idx, Math.min(json.length(), idx + 200));
        String[] keys = {
            "\"integerValue\": \"", "\"integerValue\":\"",
            "\"doubleValue\": ",    "\"doubleValue\":"
        };
        for (String key : keys) {
            int i = sub.indexOf(key);
            if (i >= 0) {
                i += key.length();
                while (i < sub.length()
                        && (sub.charAt(i) == '"' || sub.charAt(i) == ' ')) i++;
                int e = i;
                while (e < sub.length()
                        && (Character.isDigit(sub.charAt(e))
                            || sub.charAt(e) == '.')) e++;
                try { return (int) Double.parseDouble(sub.substring(i, e)); }
                catch (Exception ignored) {}
            }
        }
        return 0;
    }

    private boolean valid(String v) {
        return v != null && !v.trim().isEmpty();
    }
}
