package com.couplefinance.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.couplefinance.AuthManager;
import com.couplefinance.UserSession;
import com.couplefinance.utils.FirebaseConfig;
import com.couplefinance.utils.NotificationHelper;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * RecurringChargeManager — Application automatique des charges récurrentes.
 *
 * Cycle financier configurable :
 *   getCurrentMonth() délègue désormais à CycleManager.getCurrentCycleKey().
 *   Cela garantit que lastAppliedMonth respecte le cycle configuré par l'utilisateur.
 *
 *   Rétrocompatibilité :
 *     Si cycleStartDay=1, getCurrentCycleKey() retourne "YYYY-MM" calendaire identique
 *     à l'ancienne implémentation. Aucune donnée Firestore n'est impactée.
 *
 *   Note V1 :
 *     Si dueDay < cycleStartDay (ex : charge le 3 avec cycle démarrant le 5),
 *     la charge sera appliquée dès le calendaire dayOfMonth 3, même si ce jour
 *     appartient encore au cycle précédent. Correction prévue en V2.
 */
public class RecurringChargeManager {

    private static final String PREFS_NAME        = "recurring_prefs";
    private static final String KEY_LAST_CHECK_DAY = "last_check_day";

    private static volatile RecurringChargeManager instance;

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler  handler  = new Handler(Looper.getMainLooper());

    private Context context;

    private RecurringChargeManager() {}

    public static RecurringChargeManager getInstance() {
        if (instance == null) {
            synchronized (RecurringChargeManager.class) {
                if (instance == null) instance = new RecurringChargeManager();
            }
        }
        return instance;
    }

    public void init(Context ctx) {
        if (ctx != null) context = ctx.getApplicationContext();
    }

    // ─────────────────────────────────────────────────────────────
    // Création d'une charge fixe depuis une transaction
    // ─────────────────────────────────────────────────────────────

    public void createFixedChargeFromTransaction(
            String label, double amount, String category,
            long dateMs, FirestoreManager.Callback cb) {
        createFixedChargeFromTransaction(label, amount, category, dateMs, null, cb);
    }

    public void createFixedChargeFromTransaction(
            String label, double amount, String category,
            long dateMs, String person, FirestoreManager.Callback cb) {

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String householdId = HouseholdManager.getInstance().getHouseholdId();
                String token       = AuthManager.getInstance().getToken();

                if (householdId == null || householdId.trim().isEmpty()) {
                    postError(cb, "Foyer introuvable"); return;
                }
                if (token == null || token.trim().isEmpty()) {
                    postError(cb, "Utilisateur non connecté"); return;
                }

                String cleanLabel    = label    == null || label.trim().isEmpty()    ? "Charge fixe"    : label.trim();
                String cleanCategory = category == null || category.trim().isEmpty() ? "Charges fixes"  : category.trim();
                double cleanAmount   = Math.abs(amount);
                int    day           = normalizeDueDay(extractDayOfMonth(dateMs));
                String paidBy        = (person != null && !person.trim().isEmpty())
                        ? person.trim() : getCurrentPersonName();

                if (cleanAmount <= 0) { postError(cb, "Montant invalide"); return; }

                FixedCharge existing = findSimilarFixedChargeSync(cleanLabel, cleanAmount, cleanCategory);
                if (existing != null) { postSuccess(cb, "EXISTS"); return; }

                String path = "households/" + householdId + "/fixedcharges";
                String body = "{\"fields\":{"
                        + "\"name\":{\"stringValue\":\""           + escapeJson(cleanLabel)    + "\"},"
                        + "\"amount\":{\"doubleValue\":"            + cleanAmount               + "},"
                        + "\"category\":{\"stringValue\":\""       + escapeJson(cleanCategory) + "\"},"
                        + "\"dayOfMonth\":{\"integerValue\":\""    + day                       + "\"},"
                        + "\"paidBy\":{\"stringValue\":\""         + escapeJson(paidBy)        + "\"},"
                        + "\"lastAppliedMonth\":{\"stringValue\":\"\"                            },"
                        + "\"createdAt\":{\"integerValue\":\""     + System.currentTimeMillis()+ "\"},"
                        + "\"source\":{\"stringValue\":\"pdf_import\"}"
                        + "}}";

                conn = (HttpURLConnection) new URL(FirebaseConfig.collectionUrl(path)).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);

                try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
                    dos.write(body.getBytes("UTF-8"));
                }

                int code     = conn.getResponseCode();
                String response = safeRead(code >= 200 && code < 300
                        ? conn.getInputStream() : conn.getErrorStream());

                if (code >= 200 && code < 300) postSuccess(cb, response);
                else                           postError(cb, "Code: " + code + " - " + response);

            } catch (Exception e) {
                postError(cb, e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private FixedCharge findSimilarFixedChargeSync(String label, double amount, String category) {
        List<FixedCharge> charges = fetchFixedChargesSync();
        String key   = normalizeMerchant(label);
        long   cents = Math.round(Math.abs(amount) * 100);

        for (FixedCharge charge : charges) {
            if (charge == null) continue;
            String chargeKey   = normalizeMerchant(charge.name);
            long   chargeCents = Math.round(Math.abs(charge.amount) * 100);
            boolean sameMerchant = !key.isEmpty() && key.equals(chargeKey);
            boolean sameAmount   = cents == chargeCents;
            boolean sameCategory = category == null || category.trim().isEmpty()
                    || charge.category == null
                    || charge.category.equalsIgnoreCase(category);
            if (sameMerchant && sameAmount && sameCategory) return charge;
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────
    // Application des charges récurrentes
    // ─────────────────────────────────────────────────────────────

    public void checkAndApplyRecurringCharges(Runnable onDone) {
        if (context == null) {
            if (onDone != null) handler.post(onDone);
            return;
        }

        String todayKey = getTodayKey();

        executor.execute(() -> {
            int pendingCount = 0;
            try {
                List<FixedCharge> charges = fetchFixedChargesSync();

                // ── Clé du cycle courant (ex : "2026-06") ───────────────────
                String currentCycleKey = getCurrentMonth();
                int today = getDayOfMonth();

                for (FixedCharge charge : charges) {
                    if (charge == null) continue;

                    int dueDay = normalizeDueDay(charge.dayOfMonth);
                    if (today < dueDay) continue;

                    // lastAppliedMonth est maintenant comparé à la clé de cycle
                    if (currentCycleKey.equals(charge.lastAppliedMonth)) continue;

                    String recurringKey = buildRecurringKey(charge.docId, currentCycleKey);
                    if (recurringTransactionExistsSync(recurringKey)) {
                        updateLastAppliedMonthSync(charge.docId, currentCycleKey);
                        continue;
                    }

                    long transactionDate = buildDateForCurrentMonth(dueDay);
                    boolean created = addTransactionSync(charge, transactionDate, recurringKey);

                    if (created) updateLastAppliedMonthSync(charge.docId, currentCycleKey);
                    else         pendingCount++;
                }

                saveLastCheckDay(todayKey);

            } catch (Exception ignored) {}

            int finalPendingCount = pendingCount;
            handler.post(() -> {
                if (finalPendingCount > 0 && context != null) {
                    NotificationHelper.getInstance(context)
                            .notifyPendingFixedCharges(finalPendingCount);
                }
                if (onDone != null) onDone.run();
            });
        });
    }

    public void checkReminderIfNeeded() {
        if (context == null) return;

        executor.execute(() -> {
            int pending = 0;
            try {
                List<FixedCharge> charges      = fetchFixedChargesSync();
                String            currentMonth  = getCurrentMonth();
                int               today         = getDayOfMonth();

                for (FixedCharge charge : charges) {
                    if (charge == null) continue;
                    int    dueDay      = normalizeDueDay(charge.dayOfMonth);
                    String recurringKey = buildRecurringKey(charge.docId, currentMonth);

                    if (today >= dueDay
                            && !currentMonth.equals(charge.lastAppliedMonth)
                            && !recurringTransactionExistsSync(recurringKey)) {
                        pending++;
                    }
                }
            } catch (Exception ignored) {}

            int count = pending;
            handler.post(() -> {
                if (count > 0 && context != null) {
                    NotificationHelper.getInstance(context)
                            .notifyPendingFixedCharges(count);
                }
            });
        });
    }

    // ─────────────────────────────────────────────────────────────
    // Charges à venir ce cycle
    // ─────────────────────────────────────────────────────────────

    public interface UpcomingChargesCallback {
        void onResult(double totalUpcoming, int count);
        void onError(String error);
    }

    public void getUpcomingChargesForCurrentMonth(UpcomingChargesCallback cb) {
        executor.execute(() -> {
            try {
                List<FixedCharge> charges      = fetchFixedChargesSync();
                String            currentMonth  = getCurrentMonth();
                int               today         = getDayOfMonth();

                double total = 0;
                int    count = 0;

                for (FixedCharge charge : charges) {
                    if (charge == null) continue;
                    int    dueDay      = normalizeDueDay(charge.dayOfMonth);
                    String recurringKey = buildRecurringKey(charge.docId, currentMonth);

                    if (dueDay > today
                            && !currentMonth.equals(charge.lastAppliedMonth)
                            && !recurringTransactionExistsSync(recurringKey)) {
                        total += Math.abs(charge.amount);
                        count++;
                    }
                }

                double finalTotal = total;
                int    finalCount = count;
                handler.post(() -> { if (cb != null) cb.onResult(finalTotal, finalCount); });

            } catch (Exception e) {
                handler.post(() -> { if (cb != null) cb.onError(e.getMessage()); });
            }
        });
    }

    public interface UpcomingChargesByMemberCallback {
        void onResult(java.util.Map<String, Double>  amountByMember,
                      java.util.Map<String, Integer> countByMember,
                      double totalUpcoming, int totalCount);
        void onError(String error);
    }

    public void getUpcomingChargesForCurrentMonthByMember(UpcomingChargesByMemberCallback cb) {
        executor.execute(() -> {
            try {
                List<FixedCharge> charges      = fetchFixedChargesSync();
                String            currentMonth  = getCurrentMonth();
                int               today         = getDayOfMonth();

                java.util.Map<String, Double>  amountByMember = new java.util.LinkedHashMap<>();
                java.util.Map<String, Integer> countByMember  = new java.util.LinkedHashMap<>();
                double total = 0;
                int    count = 0;

                for (FixedCharge charge : charges) {
                    if (charge == null) continue;
                    int    dueDay      = normalizeDueDay(charge.dayOfMonth);
                    String recurringKey = buildRecurringKey(charge.docId, currentMonth);

                    if (dueDay > today
                            && !currentMonth.equals(charge.lastAppliedMonth)
                            && !recurringTransactionExistsSync(recurringKey)) {

                        String payer = charge.payer == null || charge.payer.trim().isEmpty()
                                ? getCurrentPersonName()
                                : charge.payer.trim();

                        double amount = Math.abs(charge.amount);
                        amountByMember.put(payer, amountByMember.containsKey(payer)
                                ? amountByMember.get(payer) + amount : amount);
                        countByMember.put(payer, countByMember.containsKey(payer)
                                ? countByMember.get(payer) + 1 : 1);
                        total += amount;
                        count++;
                    }
                }

                double finalTotal = total;
                int    finalCount = count;
                handler.post(() -> {
                    if (cb != null) cb.onResult(amountByMember, countByMember, finalTotal, finalCount);
                });

            } catch (Exception e) {
                handler.post(() -> { if (cb != null) cb.onError(e.getMessage()); });
            }
        });
    }

    // ─────────────────────────────────────────────────────────────
    // Fetch Firestore
    // ─────────────────────────────────────────────────────────────

    private List<FixedCharge> fetchFixedChargesSync() {
        List<FixedCharge> result = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            String householdId = HouseholdManager.getInstance().getHouseholdId();
            String token       = AuthManager.getInstance().getToken();
            if (householdId == null || householdId.trim().isEmpty()) return result;
            if (token       == null || token.trim().isEmpty())       return result;

            String path = "households/" + householdId + "/fixedcharges";
            conn = (HttpURLConnection) new URL(FirebaseConfig.collectionUrl(path)).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            if (conn.getResponseCode() != 200) return result;
            return parseCharges(safeRead(conn.getInputStream()));

        } catch (Exception ignored) {
            return result;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private List<FixedCharge> parseCharges(String json) {
        List<FixedCharge> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;

        String[] docs = json.split("\"name\":\\s*\"projects/");
        for (int i = 1; i < docs.length; i++) {
            String doc  = docs[i];
            String docId         = extractDocId(doc);
            String name          = extractString(doc, "name");
            double amount        = extractDouble(doc, "amount");
            String category      = extractString(doc, "category");
            String paidBy        = extractString(doc, "paidBy");
            String lastApplied   = extractString(doc, "lastAppliedMonth");
            int    dayOfMonth    = extractInt(doc, "dayOfMonth", 1);
            String payer         = extractString(doc, "payer");
            String resolvedPayer = !payer.trim().isEmpty()  ? payer.trim()
                                 : !paidBy.trim().isEmpty() ? paidBy.trim()
                                 : "";
            if (!name.isEmpty()) {
                list.add(new FixedCharge(docId, name,
                        category.isEmpty() ? "Charges fixes" : category,
                        amount, lastApplied, dayOfMonth, resolvedPayer));
            }
        }
        return list;
    }

    private String extractDocId(String doc) {
        try {
            int quote = doc.indexOf('"');
            if (quote <= 0) return "";
            String path = doc.substring(0, quote);
            int slash = path.lastIndexOf('/');
            return (slash >= 0 && slash + 1 < path.length())
                    ? path.substring(slash + 1) : "";
        } catch (Exception e) { return ""; }
    }

    private boolean recurringTransactionExistsSync(String recurringKey) {
        HttpURLConnection conn = null;
        try {
            String householdId = HouseholdManager.getInstance().getHouseholdId();
            String token       = AuthManager.getInstance().getToken();
            if (householdId == null || householdId.trim().isEmpty()) return false;
            if (token       == null || token.trim().isEmpty())       return false;
            if (recurringKey == null || recurringKey.trim().isEmpty()) return false;

            String query = "{\"structuredQuery\":{"
                    + "\"from\":[{\"collectionId\":\"transactions\"}],"
                    + "\"where\":{\"fieldFilter\":{"
                    + "\"field\":{\"fieldPath\":\"recurringKey\"},"
                    + "\"op\":\"EQUAL\","
                    + "\"value\":{\"stringValue\":\"" + escapeJson(recurringKey) + "\"}"
                    + "}},"
                    + "\"limit\":1"
                    + "}}";

            String urlString = "https://firestore.googleapis.com/v1/projects/"
                    + FirebaseConfig.PROJECT_ID
                    + "/databases/(default)/documents/households/"
                    + URLEncoder.encode(householdId, "UTF-8")
                    + ":runQuery?key=" + FirebaseConfig.API_KEY;

            conn = (HttpURLConnection) new URL(urlString).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);

            try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
                dos.write(query.getBytes("UTF-8"));
            }

            if (conn.getResponseCode() != 200) return false;
            String response = safeRead(conn.getInputStream());
            return response != null && response.contains("\"document\"");

        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private boolean addTransactionSync(FixedCharge charge, long date, String recurringKey) {
        HttpURLConnection conn = null;
        try {
            String householdId = HouseholdManager.getInstance().getHouseholdId();
            String token       = AuthManager.getInstance().getToken();
            if (householdId == null || householdId.trim().isEmpty()) return false;
            if (token       == null || token.trim().isEmpty())       return false;

            String path   = "households/" + householdId + "/transactions";
            String person = resolvePaidBy(charge);
            String label  = person + " · " + charge.name;

            String body = "{\"fields\":{"
                    + "\"label\":{\"stringValue\":\""          + escapeJson(label)          + "\"},"
                    + "\"amount\":{\"doubleValue\":"             + Math.abs(charge.amount)    + "},"
                    + "\"type\":{\"stringValue\":\"fixed\"},"
                    + "\"category\":{\"stringValue\":\""        + escapeJson(charge.category) + "\"},"
                    + "\"userId\":{\"stringValue\":\""          + escapeJson(AuthManager.getInstance().getUserId()) + "\"},"
                    + "\"person\":{\"stringValue\":\""          + escapeJson(person)          + "\"},"
                    + "\"date\":{\"integerValue\":\""           + date                        + "\"},"
                    + "\"shared\":{\"booleanValue\":false},"
                    + "\"auto\":{\"booleanValue\":true},"
                    + "\"recurring\":{\"booleanValue\":true},"
                    + "\"isFixedCharge\":{\"booleanValue\":true},"
                    + "\"isShareSplit\":{\"booleanValue\":false},"
                    + "\"isReimbursement\":{\"booleanValue\":false},"
                    + "\"recurringChargeId\":{\"stringValue\":\"" + escapeJson(charge.docId) + "\"},"
                    + "\"recurringKey\":{\"stringValue\":\""      + escapeJson(recurringKey)  + "\"}"
                    + "}}";

            conn = (HttpURLConnection) new URL(FirebaseConfig.collectionUrl(path)).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);

            try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
                dos.write(body.getBytes("UTF-8"));
            }

            int code = conn.getResponseCode();
            return code == 200 || code == 201;

        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String resolvePaidBy(FixedCharge charge) {
        if (charge != null && charge.payer != null && !charge.payer.trim().isEmpty()) {
            return charge.payer.trim();
        }
        return getCurrentPersonName();
    }

    private void updateLastAppliedMonthSync(String docId, String month) {
        HttpURLConnection conn = null;
        try {
            String householdId = HouseholdManager.getInstance().getHouseholdId();
            String token       = AuthManager.getInstance().getToken();
            if (householdId == null || householdId.trim().isEmpty()) return;
            if (token       == null || token.trim().isEmpty())       return;
            if (docId       == null || docId.trim().isEmpty())       return;

            String path = "households/" + householdId + "/fixedcharges/" + docId;
            String body = "{\"fields\":{\"lastAppliedMonth\":{\"stringValue\":\""
                    + escapeJson(month) + "\"}}}";

            conn = (HttpURLConnection) new URL(FirebaseConfig.documentUrl(path)
                    .replace("?key=", "?updateMask.fieldPaths=lastAppliedMonth&key=")).openConnection();
            conn.setRequestMethod("PATCH");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);

            try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
                dos.write(body.getBytes("UTF-8"));
            }
            conn.getResponseCode();

        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Clé de cycle — délègue à CycleManager
    // ─────────────────────────────────────────────────────────────

    /**
     * Retourne la clé du cycle financier courant.
     *
     * Ancienne implémentation : Calendar-based "YYYY-MM" calendaire.
     * Nouvelle implémentation : délègue à CycleManager.getCurrentCycleKey().
     *
     * Le format reste "YYYY-MM" — rétrocompatible avec lastAppliedMonth Firestore.
     */
    public static String getCurrentMonth() {
        return CycleManager.getInstance().getCurrentCycleKey();
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private String getCurrentPersonName() {
        String name = "";
        try { name = UserSession.getInstance().getNameOrFallback(); } catch (Exception ignored) {}
        if (name == null || name.trim().isEmpty() || name.contains("@")) {
            try { name = AuthManager.getInstance().getDisplayName(); } catch (Exception ignored) {}
        }
        return (name == null || name.trim().isEmpty() || name.contains("@")) ? "Moi" : name.trim();
    }

    private String buildRecurringKey(String docId, String cycleKey) {
        String safeId = docId == null || docId.trim().isEmpty() ? "unknown" : docId.trim();
        return safeId + "_" + cycleKey;
    }

    private int normalizeDueDay(int day) {
        if (day < 1)  return 1;
        if (day > 28) return 28;
        return day;
    }

    private int extractDayOfMonth(long dateMs) {
        try {
            Calendar c = Calendar.getInstance();
            if (dateMs > 0) c.setTimeInMillis(dateMs);
            return c.get(Calendar.DAY_OF_MONTH);
        } catch (Exception e) { return 1; }
    }

    private long buildDateForCurrentMonth(int dayOfMonth) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_MONTH, normalizeDueDay(dayOfMonth));
        c.set(Calendar.HOUR_OF_DAY, 9);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private String extractString(String json, String field) {
        try {
            String marker = "\"" + field + "\"";
            int idx = json.indexOf(marker);
            if (idx < 0) return "";
            String sub = json.substring(idx, Math.min(json.length(), idx + 260));
            String[] keys = {"\"stringValue\": \"", "\"stringValue\":\""};
            for (String key : keys) {
                int i = sub.indexOf(key);
                if (i >= 0) {
                    i += key.length();
                    int e = sub.indexOf("\"", i);
                    if (e > i) return sub.substring(i, e);
                }
            }
            return "";
        } catch (Exception e) { return ""; }
    }

    private double extractDouble(String json, String field) {
        try {
            String marker = "\"" + field + "\"";
            int idx = json.indexOf(marker);
            if (idx < 0) return 0;
            String sub = json.substring(idx, Math.min(json.length(), idx + 180));
            String[] keys = {"\"doubleValue\": ", "\"doubleValue\":",
                             "\"integerValue\": \"", "\"integerValue\":\""};
            for (String key : keys) {
                int i = sub.indexOf(key);
                if (i >= 0) {
                    i += key.length();
                    int e = i;
                    while (e < sub.length() && (Character.isDigit(sub.charAt(e))
                            || sub.charAt(e) == '.' || sub.charAt(e) == '-')) e++;
                    return Double.parseDouble(sub.substring(i, e));
                }
            }
            return 0;
        } catch (Exception e) { return 0; }
    }

    private int extractInt(String json, String field, int fallback) {
        try {
            String marker = "\"" + field + "\"";
            int idx = json.indexOf(marker);
            if (idx < 0) return fallback;
            String sub = json.substring(idx, Math.min(json.length(), idx + 180));
            String[] keys = {"\"integerValue\": \"", "\"integerValue\":\"",
                             "\"doubleValue\": ",    "\"doubleValue\":"};
            for (String key : keys) {
                int i = sub.indexOf(key);
                if (i >= 0) {
                    i += key.length();
                    int e = i;
                    while (e < sub.length() && (Character.isDigit(sub.charAt(e))
                            || sub.charAt(e) == '.')) e++;
                    return (int) Double.parseDouble(sub.substring(i, e));
                }
            }
            return fallback;
        } catch (Exception e) { return fallback; }
    }

    private String normalizeMerchant(String value) {
        if (value == null) return "";
        String clean = value.toLowerCase(java.util.Locale.FRANCE)
                .replace("prélèvement", "").replace("prelevement", "")
                .replace("carte", "").replace("cb", "")
                .replace("virement", "").replace("reçu", "").replace("recu", "")
                .replaceAll("\\d+[,.]?\\d*\\s*eur", "")
                .replaceAll("\\d+[,.]?\\d*\\s*€", "")
                .replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        return clean.length() > 28 ? clean.substring(0, 28).trim() : clean;
    }

    private void postSuccess(FirestoreManager.Callback cb, String response) {
        if (cb == null) return;
        handler.post(() -> cb.onSuccess(response == null ? "" : response));
    }

    private void postError(FirestoreManager.Callback cb, String error) {
        if (cb == null) return;
        handler.post(() -> cb.onError(error == null ? "Erreur inconnue" : error));
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
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

    private SharedPreferences getPrefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private void saveLastCheckDay(String day) {
        getPrefs().edit().putString(KEY_LAST_CHECK_DAY, day).apply();
    }

    private String getTodayKey() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR)
                + "-" + String.format("%02d", c.get(Calendar.MONTH) + 1)
                + "-" + String.format("%02d", c.get(Calendar.DAY_OF_MONTH));
    }

    private int getDayOfMonth() {
        return Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
    }

    // ─────────────────────────────────────────────────────────────
    // Modèle interne
    // ─────────────────────────────────────────────────────────────

    private static class FixedCharge {
        String docId;
        String name;
        String category;
        double amount;
        String lastAppliedMonth;
        int    dayOfMonth;
        String payer;

        FixedCharge(String docId, String name, String category, double amount,
                    String lastAppliedMonth, int dayOfMonth, String payer) {
            this.docId            = docId;
            this.name             = name;
            this.category         = category;
            this.amount           = amount;
            this.lastAppliedMonth = lastAppliedMonth;
            this.dayOfMonth       = dayOfMonth;
            this.payer            = payer;
        }
    }
}
