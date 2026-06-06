package com.couplefinance.data;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.couplefinance.ui.transactions.TransactionsModels;
import com.couplefinance.ui.transactions.TransactionsRepository;
import com.couplefinance.utils.ParsedTransaction;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * BankAutoSyncManager — Synchronisation bancaire automatique quotidienne.
 *
 * Fonctionnement :
 *   1. Une alarme quotidienne (par défaut 20h00) déclenche {@link BankSyncReceiver}.
 *   2. Le receiver appelle {@link #runSync(Context)} :
 *        - récupère les transactions des 3 derniers jours via Enable Banking ;
 *        - détecte les nouvelles (absentes de Firestore) ;
 *        - nettoie les libellés + catégorise + détecte les charges fixes ;
 *        - importe automatiquement ;
 *        - récupère le solde ;
 *        - envoie une notification résumé :
 *            « 3 opérations aujourd'hui : −45,30 €. Solde : 1 234 € ».
 *   3. Reprogramme l'alarme pour le lendemain.
 *
 * AndroidManifest.xml — déclarer le receiver :
 *   <receiver android:name=".data.BankAutoSyncManager$BankSyncReceiver"
 *       android:exported="false">
 *       <intent-filter>
 *           <action android:name="com.couplefinance.BANK_AUTO_SYNC"/>
 *           <action android:name="android.intent.action.BOOT_COMPLETED"/>
 *       </intent-filter>
 *   </receiver>
 *
 * Activation : BankAutoSyncManager.scheduleDaily(ctx) dans DashboardActivity.onCreate().
 */
public final class BankAutoSyncManager {

    private static final String TAG = "BankAutoSync";

    private static final String PREFS          = "bank_autosync_prefs";
    private static final String K_ENABLED      = "autosync_enabled";
    private static final String K_HOUR         = "autosync_hour";
    private static final String K_MINUTE       = "autosync_minute";
    private static final String K_LAST_SUMMARY = "autosync_last_summary";
    private static final String K_LIVE_BALANCES = "autosync_live_balances";
    private static final String K_LIVE_BALANCES_MAP = "autosync_live_balances_map";
    private static final String K_LAST_SYNC    = "autosync_last_ms";
    private static final String K_NOTIFY_EACH  = "autosync_notify_each"; // notif par tx ou résumé
    private static final String K_IMPORTED_KEYS = "autosync_imported_keys"; // cache anti-doublon

    private static final String ACTION_SYNC    = "com.couplefinance.BANK_AUTO_SYNC";
    private static final int    REQUEST_CODE   = 4998;
    private static final int    DEFAULT_HOUR   = 20;

    private static final String CHANNEL_ID     = "channel_bank_autosync";
    private static final int    ACCENT         = 0xFFC0614A; // terracotta (teinte notif)
    private static final int    NOTIF_ID       = 2050;

    private BankAutoSyncManager() {}

    // ─────────────────────────────────────────────────────────────
    // Listeners — notifiés après chaque mise à jour des soldes
    // ─────────────────────────────────────────────────────────────

    public interface OnBalancesRefreshed { void onRefreshed(); }

    private static final CopyOnWriteArrayList<OnBalancesRefreshed> balanceListeners
            = new CopyOnWriteArrayList<>();

    public static void addBalanceListener(OnBalancesRefreshed l) {
        if (l != null) balanceListeners.addIfAbsent(l);
    }

    public static void removeBalanceListener(OnBalancesRefreshed l) {
        balanceListeners.remove(l);
    }

    // ─────────────────────────────────────────────────────────────
    // Configuration
    // ─────────────────────────────────────────────────────────────

    public static boolean isEnabled(Context ctx) {
        return prefs(ctx).getBoolean(K_ENABLED, false);
    }
    public static void setEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(K_ENABLED, enabled).apply();
        if (enabled) scheduleDaily(ctx); else cancelDaily(ctx);
    }
    public static int getHour(Context ctx) {
        return prefs(ctx).getInt(K_HOUR, DEFAULT_HOUR);
    }
    public static void setHour(Context ctx, int hour) {
        prefs(ctx).edit().putInt(K_HOUR, Math.max(0, Math.min(23, hour))).apply();
        if (isEnabled(ctx)) scheduleDaily(ctx);
    }
    public static int getMinute(Context ctx) {
        return prefs(ctx).getInt(K_MINUTE, 0);
    }
    public static void setMinute(Context ctx, int minute) {
        prefs(ctx).edit().putInt(K_MINUTE, Math.max(0, Math.min(59, minute))).apply();
        if (isEnabled(ctx)) scheduleDaily(ctx);
    }
    /** Heure formatée "HH:MM" pour affichage. */
    public static String getTimeLabel(Context ctx) {
        return String.format(Locale.FRANCE, "%02d:%02d", getHour(ctx), getMinute(ctx));
    }
    /** Résumé de la dernière synchro (pour le dashboard). "" si jamais synchronisé. */
    public static String getLastSummary(Context ctx) {
        return prefs(ctx).getString(K_LAST_SUMMARY, "");
    }
    /** Soldes "live" des comptes (affichage seulement, ne touche pas le cycle). */
    public static String getLiveBalances(Context ctx) {
        return prefs(ctx).getString(K_LIVE_BALANCES, "");
    }
    /**
     * Réinitialise l'état de la synchro : vide le cache anti-doublon, les soldes
     * live et le dernier résumé. À utiliser après avoir corrigé l'attribution des
     * comptes pour permettre une re-synchronisation complète et propre.
     * NB : ne supprime PAS les transactions déjà importées dans Firestore.
     */
    public static void resetSync(Context ctx) {
        prefs(ctx).edit()
                .remove(K_IMPORTED_KEYS)
                .remove(K_LIVE_BALANCES)
                .remove(K_LIVE_BALANCES_MAP)
                .remove(K_LAST_SUMMARY)
                .remove(K_LAST_SYNC)
                .apply();
    }
    public static boolean isNotifyEach(Context ctx) {
        return prefs(ctx).getBoolean(K_NOTIFY_EACH, false);
    }
    public static void setNotifyEach(Context ctx, boolean each) {
        prefs(ctx).edit().putBoolean(K_NOTIFY_EACH, each).apply();
    }
    public static long getLastSync(Context ctx) {
        return prefs(ctx).getLong(K_LAST_SYNC, 0);
    }

    // ─────────────────────────────────────────────────────────────
    // Planification
    // ─────────────────────────────────────────────────────────────

    public static void scheduleDaily(Context ctx) {
        if (ctx == null) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        int hour = getHour(ctx);
        int minute = getMinute(ctx);
        Calendar now     = Calendar.getInstance();
        Calendar trigger = Calendar.getInstance();
        trigger.set(Calendar.HOUR_OF_DAY, hour);
        trigger.set(Calendar.MINUTE, minute);
        trigger.set(Calendar.SECOND, 0);
        trigger.set(Calendar.MILLISECOND, 0);
        if (!trigger.after(now)) trigger.add(Calendar.DAY_OF_YEAR, 1);

        Intent intent = new Intent(ctx, BankSyncReceiver.class);
        intent.setAction(ACTION_SYNC);

        int flags = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, REQUEST_CODE, intent, flags);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                // Inexacte mais se déclenche même en Doze / app fermée,
                // SANS la permission SCHEDULE_EXACT_ALARM (Android 12+).
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.getTimeInMillis(), pi);
            else
                am.set(AlarmManager.RTC_WAKEUP, trigger.getTimeInMillis(), pi);
        } catch (Exception e) {
            try { am.set(AlarmManager.RTC_WAKEUP, trigger.getTimeInMillis(), pi); }
            catch (Exception ignored) {}
        }
        Log.d(TAG, "Synchro quotidienne planifiée à " + getTimeLabel(ctx));
    }

    public static void cancelDaily(Context ctx) {
        if (ctx == null) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(ctx, BankSyncReceiver.class);
        intent.setAction(ACTION_SYNC);
        int flags = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_NO_CREATE;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, REQUEST_CODE, intent, flags);
        if (pi != null) { am.cancel(pi); pi.cancel(); }
    }

    // ─────────────────────────────────────────────────────────────
    // Synchronisation
    // ─────────────────────────────────────────────────────────────

    /**
     * Lance une synchronisation des 3 derniers jours, importe les nouvelles
     * transactions et envoie une notification résumé.
     * Peut aussi être appelée manuellement (bouton « Synchroniser maintenant »).
     */
    public static void runSync(Context ctx) {
        if (ctx == null) return;
        final Context app = ctx.getApplicationContext();

        EnableBankingManager.getInstance().init(app);
        if (!EnableBankingManager.getInstance().isConnected()) {
            Log.d(TAG, "Pas de banque connectée, synchro ignorée.");
            return;
        }

        // Période : 3 derniers jours (capture les opérations récentes)
        Calendar cal = Calendar.getInstance();
        String dateTo = EnableBankingManager.millisToDateStr(cal.getTimeInMillis());
        cal.add(Calendar.DAY_OF_YEAR, -3);
        String dateFrom = EnableBankingManager.millisToDateStr(cal.getTimeInMillis());

        EnableBankingManager.getInstance().syncAllAccounts(dateFrom, dateTo,
                new EnableBankingManager.TransactionsCallback() {
                    @Override public void onResult(List<EnableBankingManager.BankTransaction> txList) {
                        processSync(app, txList);
                    }
                    @Override public void onError(String error) {
                        Log.w(TAG, "Synchro auto échouée : " + error);
                    }
                });
    }

    private static void processSync(Context app,
                                     List<EnableBankingManager.BankTransaction> bankTx) {
        if (bankTx == null || bankTx.isEmpty()) {
            Log.d(TAG, "Aucune transaction récente.");
            recordEmptySync(app);
            rescheduleTomorrow(app);
            return;
        }

        // Enrichissement (nettoyage libellés + catégories + attribution)
        List<ParsedTransaction> parsed = BankImportPipeline.enrich(bankTx, app, null);

        // Détection de doublons via cache local de clés déjà importées
        java.util.Set<String> imported = new java.util.HashSet<>(
                prefs(app).getStringSet(K_IMPORTED_KEYS, new java.util.HashSet<>()));

        List<ParsedTransaction> fresh = new ArrayList<>();
        java.util.Set<String> newKeys = new java.util.HashSet<>(imported);
        for (ParsedTransaction pt : parsed) {
            String key = dedupKey(pt);
            if (imported.contains(key)) continue; // déjà importé un jour précédent
            fresh.add(pt);
            newKeys.add(key);
        }

        if (fresh.isEmpty()) {
            Log.d(TAG, "Aucune nouvelle transaction.");
            fetchBalancesForEmptySync(app);   // récupère quand même les soldes live
            rescheduleTomorrow(app);
            return;
        }

        // Construire les transactions à importer (avec attribution)
        List<TransactionsModels.Transaction> toImport = new ArrayList<>();
        double totalSpent = 0; double totalIncome = 0;
        MerchantRuleManager.getInstance().init(app);
        String me = currentUserName(app);   // vrai nom (jamais "Moi")
        for (ParsedTransaction pt : fresh) {
            MerchantRuleManager.getInstance().saveRuleFromTransaction(pt);
            String type = "income".equals(pt.type) ? "income" : "variable";
            String cat  = pt.category == null || pt.category.isEmpty()
                    ? ("income".equals(pt.type) ? "Revenus" : "Autre") : pt.category;

            String person = me; String compte = "";
            if ("joint".equals(pt.owner))                     { compte = "joint"; }
            else if (pt.owner != null && !pt.owner.isEmpty()) { person = pt.owner; }
            // Garde-fou : ne JAMAIS créer un membre fantôme. Si on ne sait pas
            // à qui attribuer (owner vide ET nom courant inconnu), on met en joint.
            if (compte.isEmpty() && person.isEmpty()) compte = "joint";

            toImport.add(new TransactionsModels.Transaction(
                    pt.label, pt.amount, type, cat,
                    pt.dateMs, System.currentTimeMillis(),
                    person, false, false, false, "", compte));

            if ("income".equals(pt.type)) totalIncome += pt.amount;
            else                          totalSpent  += pt.amount;
        }

        final int    count  = toImport.size();
        final double spent  = totalSpent;
        final double income = totalIncome;
        final List<ParsedTransaction> freshFinal = fresh;
        final java.util.Set<String> keysFinal = newKeys;

        // Import (Activity null → importBatch exécute le callback directement)
        TransactionsRepository.importBatch(toImport, null, null,
                new TransactionsRepository.OnWriteComplete() {
                    @Override public void onSuccess() {
                        // Mémoriser les clés importées
                        prefs(app).edit()
                                .putStringSet(K_IMPORTED_KEYS, keysFinal)
                                .putLong(K_LAST_SYNC, System.currentTimeMillis())
                                .apply();
                        BankImportPipeline.autoDetectRecurringCharges(freshFinal, app);
                        BankImportPipeline.autoCreateTransfers(freshFinal, app);
                        fetchBalanceAndNotify(app, count, spent, income, freshFinal);
                        rescheduleTomorrow(app);
                    }
                    @Override public void onError(String e) {
                        Log.w(TAG, "Import auto échoué : " + e);
                        rescheduleTomorrow(app);
                    }
                });
    }

    /** Clé de doublon stable : date|type|centimes|merchantKey. */
    private static String dedupKey(ParsedTransaction pt) {
        long cents = Math.round(Math.abs(pt.amount) * 100);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(pt.dateMs);
        String day = String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH)+1, c.get(Calendar.DAY_OF_MONTH));
        String mk = pt.merchantKey != null && !pt.merchantKey.isEmpty()
                ? pt.merchantKey : (pt.label != null ? pt.label : "");
        return day + "|" + pt.type + "|" + cents + "|" + mk;
    }

    private static void fetchBalanceAndNotify(Context app, int count, double spent,
                                               double income, List<ParsedTransaction> fresh) {
        EnableBankingManager.getInstance().getAllBalances(
                new EnableBankingManager.BalancesCallback() {
                    @Override public void onResult(List<EnableBankingManager.AccountBalance> balances, double main) {
                        double bal = 0;
                        for (EnableBankingManager.AccountBalance b : balances) {
                            if ("ITBD".equals(b.type) || "CLBD".equals(b.type)) { bal = b.signedAmount(); break; }
                        }
                        if (bal == 0 && !balances.isEmpty()) bal = balances.get(0).signedAmount();
                        // Stocker les soldes "live" pour affichage (sans toucher le cycle)
                        storeLiveBalances(app, balances);
                        sendSummaryNotification(app, count, spent, income, bal, fresh);
                    }
                    @Override public void onError(String error) {
                        sendSummaryNotification(app, count, spent, income, Double.NaN, fresh);
                    }
                });
    }

    /**
     * Stocke le solde « live » de chaque compte pour AFFICHAGE uniquement.
     * Ne touche PAS le solde de début de cycle (évite le double comptage).
     * Construit un résumé du type « Joint : 1 234,00 € · Thomas : 567,00 € ».
     */
    private static void storeLiveBalances(Context app,
            List<EnableBankingManager.AccountBalance> balances) {
        if (balances == null || balances.isEmpty()) return;

        // Meilleur solde par index de compte (ITBD > CLBD > autre)
        java.util.Map<Integer, Double> bestByAccount = new java.util.LinkedHashMap<>();
        java.util.Map<Integer, Integer> rankByAccount = new java.util.HashMap<>();
        for (EnableBankingManager.AccountBalance b : balances) {
            int rank = "ITBD".equals(b.type) ? 3 : "CLBD".equals(b.type) ? 2 : 1;
            Integer cur = rankByAccount.get(b.accountIndex);
            if (cur == null || rank > cur) {
                rankByAccount.put(b.accountIndex, rank);
                bestByAccount.put(b.accountIndex, b.signedAmount());
            }
        }

        // Construire le résumé lisible (libellé propriétaire + montant)
        StringBuilder sb = new StringBuilder();   // affichage
        StringBuilder map = new StringBuilder();  // parseable: label=montant|||…
        for (java.util.Map.Entry<Integer, Double> e : bestByAccount.entrySet()) {
            String owner = EnableBankingManager.getInstance().getOwnerForIndex(e.getKey());
            // Si le propriétaire n'est pas configuré, on attribue au compte de l'utilisateur courant
            if (owner == null || owner.isEmpty()) {
                String fallback = currentUserName(app);
                if (!fallback.isEmpty()) owner = fallback;
            }
            String label = ownerLabel(owner);
            if (label.isEmpty()) label = "Compte " + (e.getKey() + 1);
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(label).append(" : ").append(fmt(e.getValue()));
            if (map.length() > 0) map.append("|||");
            map.append(label).append("=").append(e.getValue());
        }

        prefs(app).edit()
                .putString(K_LIVE_BALANCES, sb.toString())
                .putString(K_LIVE_BALANCES_MAP, map.toString())
                .apply();

        // ── Écrit les soldes dans les managers, EXACTEMENT comme le modal
        // « Solde début du mois ». Les cards liront ces valeurs nativement.
        // → la synchro bancaire devient la source unique du solde des cards.
        persistBalancesToManagers(app, bestByAccount);

        // Notifier les écrans actifs (ex : HomeView) pour raffraîchir l'affichage
        for (OnBalancesRefreshed l : balanceListeners) {
            try { l.onRefreshed(); } catch (Exception ignored) {}
        }

        // Rafraîchir le widget écran d'accueil (même app fermée)
        try { com.couplefinance.widget.SoldeWidget.requestRefresh(app); } catch (Exception ignored) {}
    }

    /**
     * Pour chaque compte synchronisé, enregistre son solde via le même chemin que
     * le modal "Solde début du mois" :
     *   - propriétaire = utilisateur courant → BalanceManager.saveMonthlyStartBalance
     *   - propriétaire = "joint"             → JointAccountManager.saveMonthlyStartBalance
     *   - autre membre                       → ignoré (son propre appareil le gère)
     */
    private static void persistBalancesToManagers(Context app,
            java.util.Map<Integer, Double> bestByAccount) {
        if (bestByAccount == null || bestByAccount.isEmpty()) return;

        String me = currentUserName(app).toLowerCase(Locale.FRENCH);
        String meFirst = me.isEmpty() ? "" : me.split("\\s+")[0];

        for (java.util.Map.Entry<Integer, Double> e : bestByAccount.entrySet()) {
            String owner = EnableBankingManager.getInstance().getOwnerForIndex(e.getKey());
            final double value = e.getValue();

            if ("joint".equals(owner)) {
                try {
                    JointAccountManager.getInstance().saveMonthlyStartBalance(app, value,
                            new JointAccountManager.Callback() {
                                @Override public void onSuccess() { Log.d(TAG, "Solde joint synchronise : " + value); }
                                @Override public void onError(String error) { Log.w(TAG, "Solde joint : " + error); }
                            });
                } catch (Exception ex) { Log.w(TAG, "saveMonthlyStartBalance joint", ex); }
                continue;
            }

            // Compte sans propriétaire configuré → attribuer à l'utilisateur courant
            if ((owner == null || owner.isEmpty()) && !me.isEmpty()) owner = me;

            if (owner != null && !owner.isEmpty() && !me.isEmpty()) {
                final String ownerFinal = owner;
                String oLc = owner.trim().toLowerCase(Locale.FRENCH);
                String oFirst = oLc.split("\\s+")[0];
                boolean isCurrentUser = oLc.equals(me) || oFirst.equals(meFirst);
                if (isCurrentUser) {
                    try {
                        BalanceManager.getInstance().saveMonthlyStartBalance(value,
                                new FirestoreManager.Callback() {
                                    @Override public void onSuccess(String response) { Log.d(TAG, "Solde " + ownerFinal + " synchronise : " + value); }
                                    @Override public void onError(String message) { Log.w(TAG, "Solde " + ownerFinal + " : " + message); }
                                });
                    } catch (Exception ex) { Log.w(TAG, "saveMonthlyStartBalance user", ex); }
                }
                // autre membre : pas d'API pour ecrire son solde depuis cet appareil -> ignore
            }
        }
    }

    /**
     * Solde "live" d'un compte pour un libellé donné (nom de membre ou "Compte joint").
     * Renvoie Double.NaN si aucun solde synchronisé pour ce libellé.
     */
    public static double getLiveBalanceFor(Context ctx, String label) {
        if (label == null) return Double.NaN;
        String want = label.trim().toLowerCase(Locale.FRENCH);
        String wantFirst = want.split("\\s+")[0];

        // 1) Source parseable (synchro récente)
        java.util.LinkedHashMap<String, Double> entries = new java.util.LinkedHashMap<>();
        String raw = prefs(ctx).getString(K_LIVE_BALANCES_MAP, "");
        if (!raw.isEmpty()) {
            for (String entry : raw.split("\\|\\|\\|")) {
                int eq = entry.lastIndexOf('=');
                if (eq <= 0) continue;
                try { entries.put(entry.substring(0, eq).trim(),
                        Double.parseDouble(entry.substring(eq + 1))); } catch (Exception ignored) {}
            }
        }
        // 2) Fallback : parser la chaîne d'affichage "Label : -214,00 € · Autre : 8,94 €"
        if (entries.isEmpty()) {
            String disp = prefs(ctx).getString(K_LIVE_BALANCES, "");
            if (!disp.isEmpty()) {
                for (String part : disp.split("·")) {
                    int sep = part.indexOf(" : ");
                    if (sep < 0) continue;
                    String key = part.substring(0, sep).trim();
                    String num = part.substring(sep + 3)
                            .replace("€", "").replace("\u2212", "-")
                            .replace(" ", "").replace("\u00A0", "").trim()
                            .replace(",", ".");
                    try { entries.put(key, Double.parseDouble(num)); } catch (Exception ignored) {}
                }
            }
        }
        if (entries.isEmpty()) return Double.NaN;

        // Matching : exact → prénom → contient
        Double exact = null, byFirst = null, byContains = null;
        for (java.util.Map.Entry<String, Double> e : entries.entrySet()) {
            String key = e.getKey().toLowerCase(Locale.FRENCH);
            double val = e.getValue();
            if (key.equals(want)) exact = val;
            else if (key.split("\\s+")[0].equals(wantFirst) || want.equals(key.split("\\s+")[0])) byFirst = val;
            else if (key.contains(want) || want.contains(key)) byContains = val;
        }
        if (exact != null)     return exact;
        if (byFirst != null)   return byFirst;
        if (byContains != null) return byContains;
        return Double.NaN;
    }

    // ─────────────────────────────────────────────────────────────
    // Notification
    // ─────────────────────────────────────────────────────────────

    private static void sendSummaryNotification(Context app, int count, double spent,
                                                  double income, double balance,
                                                  List<ParsedTransaction> fresh) {
        ensureChannel(app);

        String title = count + " opération" + (count > 1 ? "s" : "") + " aujourd'hui";

        StringBuilder body = new StringBuilder();
        if (spent  > 0) body.append("Dépensé : ").append(fmt(spent)).append("  ");
        if (income > 0) body.append("Reçu : ").append(fmt(income)).append("  ");

        // Soldes live (multi-comptes) si disponibles, sinon le solde principal
        String liveBalances = getLiveBalances(app);
        if (liveBalances != null && !liveBalances.isEmpty()) {
            body.append("\n").append(liveBalances);
        } else if (!Double.isNaN(balance)) {
            body.append("\nSolde disponible : ").append(fmt(balance));
        }

        // Notification par transaction si activé
        if (isNotifyEach(app) && fresh != null) {
            int id = NOTIF_ID + 1;
            for (ParsedTransaction pt : fresh) {
                String sign = "income".equals(pt.type) ? "+" : "−";
                sendNotif(app, id++, pt.label,
                        sign + fmt(pt.amount) + "  ·  " + ownerLabel(pt.owner));
            }
        }

        // Notification résumé (toujours)
        sendNotif(app, NOTIF_ID, title, body.toString().trim());

        // Mémoriser pour affichage dans le dashboard
        String summary = title + " · " + body.toString().replace("\n", " · ").trim();
        prefs(app).edit().putString(K_LAST_SUMMARY, summary).apply();
    }

    private static void sendNotif(Context app, int id, String title, String body) {
        ensureChannel(app);
        NotificationManager nm = (NotificationManager)
                app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Intent intent = new Intent(app, com.couplefinance.ui.DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(app, id, intent, piFlags);

        // Petite icône : silhouette monochrome "ic_stat_sync" si fournie, sinon système.
        int smallIcon = app.getResources().getIdentifier(
                "ic_stat_sync", "drawable", app.getPackageName());
        if (smallIcon == 0) smallIcon = android.R.drawable.ic_popup_sync;

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(app, CHANNEL_ID)
                : new Notification.Builder(app);

        b.setContentTitle(title)
         .setContentText(body)
         .setStyle(new Notification.BigTextStyle().bigText(body).setSummaryText("CoupleFinance"))
         .setSmallIcon(smallIcon)
         .setColor(ACCENT)                 // teinte terracotta (icône + en-tête)
         .setSubText("CoupleFinance")
         .setCategory(Notification.CATEGORY_STATUS)
         .setVisibility(Notification.VISIBILITY_PUBLIC)
         .setShowWhen(true)
         .setContentIntent(pi)
         .setAutoCancel(true);

        // Grande icône = logo de l'app (ic_launcher) si disponible.
        try {
            int logo = app.getResources().getIdentifier("ic_launcher", "mipmap", app.getPackageName());
            if (logo == 0) logo = app.getApplicationInfo().icon;
            android.graphics.Bitmap bmp = drawableToBitmap(app, logo);
            if (bmp != null) b.setLargeIcon(bmp);
        } catch (Exception ignored) {}

        try {
            nm.notify(id, b.build());
        } catch (Exception e) {
            Log.w(TAG, "notify échoué : " + e.getMessage());
        }
    }

    /** Rend un drawable (y compris vectoriel / adaptatif) en Bitmap pour setLargeIcon. */
    private static android.graphics.Bitmap drawableToBitmap(Context ctx, int resId) {
        if (resId == 0) return null;
        try {
            android.graphics.drawable.Drawable d =
                    ctx.getResources().getDrawable(resId, ctx.getTheme());
            if (d == null) return null;
            if (d instanceof android.graphics.drawable.BitmapDrawable) {
                return ((android.graphics.drawable.BitmapDrawable) d).getBitmap();
            }
            int w = d.getIntrinsicWidth()  > 0 ? d.getIntrinsicWidth()  : 108;
            int h = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : 108;
            android.graphics.Bitmap bmp =
                    android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(bmp);
            d.setBounds(0, 0, c.getWidth(), c.getHeight());
            d.draw(c);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    private static void ensureChannel(Context app) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager)
                app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Synchro bancaire", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Résumé quotidien des opérations bancaires");
        ch.enableLights(true);
        ch.setLightColor(ACCENT);
        ch.setShowBadge(true);
        nm.createNotificationChannel(ch);
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private static void rescheduleTomorrow(Context app) {
        if (isEnabled(app)) scheduleDaily(app);
    }
    /** Vrai nom de l'utilisateur courant. Renvoie "" si inconnu (jamais "Moi"). */
    private static String currentUserName(Context ctx) {
        try {
            String n = com.couplefinance.UserSession.getInstance().getNameOrFallback();
            if (n != null && !n.trim().isEmpty() && !n.contains("@")) return n.trim();
        } catch (Exception ignored) {}
        try {
            String n = com.couplefinance.AuthManager.getInstance().getDisplayName();
            if (n != null && !n.trim().isEmpty() && !n.contains("@")) return n.trim();
        } catch (Exception ignored) {}
        return "";
    }
    /** Enregistre une synchro sans nouvelle opération (visible dans le dashboard). */
    private static void recordEmptySync(Context app) {
        long now = System.currentTimeMillis();
        String when = new java.text.SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)
                .format(new java.util.Date(now));
        String live = getLiveBalances(app);
        String summary = "Aucune nouvelle opération · synchro " + when;
        if (live != null && !live.isEmpty()) summary += " · " + live;
        prefs(app).edit()
                .putLong(K_LAST_SYNC, now)
                .putString(K_LAST_SUMMARY, summary)
                .apply();

        // Vraie notification Android — émise à CHAQUE synchro, même sans
        // nouvelle opération (le résumé inclut les soldes à jour si dispo).
        String body = "Comptes vérifiés à " + when.substring(when.length() - 5);
        if (live != null && !live.isEmpty()) body += "\n" + live;
        sendNotif(app, NOTIF_ID, "Synchronisation bancaire", body);
    }

    /** Récupère et stocke les soldes live même quand aucune transaction n'est nouvelle. */
    private static void fetchBalancesForEmptySync(Context app) {
        EnableBankingManager.getInstance().getAllBalances(
                new EnableBankingManager.BalancesCallback() {
                    @Override public void onResult(List<EnableBankingManager.AccountBalance> balances, double main) {
                        storeLiveBalances(app, balances);
                        recordEmptySync(app);   // résumé incluant les soldes live
                    }
                    @Override public void onError(String error) {
                        recordEmptySync(app);
                    }
                });
    }
    private static String fmt(double v) {
        return String.format(Locale.FRANCE, "%.2f €", v);
    }
    private static String ownerLabel(String owner) {
        if ("joint".equals(owner)) return "Compte joint";
        if (owner != null && !owner.isEmpty()) return owner;
        return "";
    }
    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ─────────────────────────────────────────────────────────────
    // BroadcastReceiver
    // ─────────────────────────────────────────────────────────────

    public static class BankSyncReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context ctx, Intent intent) {
            if (ctx == null || intent == null) return;
            final Context app = ctx.getApplicationContext();
            String action = intent.getAction();

            if ("android.intent.action.BOOT_COMPLETED".equals(action)
                    || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
                // Au démarrage de l'appareil / mise à jour de l'app : replanifier l'alarme.
                if (isEnabled(app)) scheduleDaily(app);
                return;
            }

            if (ACTION_SYNC.equals(action)) {
                Log.d(TAG, "Déclenchement synchro auto quotidienne (app fermée OK)");

                // 1) Replanifie TOUT DE SUITE la prochaine occurrence : même si le
                //    travail ci-dessous échoue ou est tué, la chaîne quotidienne tient.
                if (isEnabled(app)) scheduleDaily(app);

                // 2) Garde le process vivant pendant le travail réseau asynchrone.
                //    Sans ça, onReceive retourne et Android peut tuer le process
                //    avant la fin de la synchro → aucune notification.
                final PendingResult pr = goAsync();

                try {
                    CycleManager.getInstance().init(app);
                    RecurringChargeManager.getInstance().init(app);
                    MerchantRuleManager.getInstance().init(app);
                    runSync(app);
                } catch (Exception e) {
                    Log.w(TAG, "runSync (receiver) : " + e.getMessage());
                }

                // 3) Laisse ~9 s au réseau pour aboutir (limite ANR du broadcast ~10 s),
                //    puis libère le receiver. La notif est émise pendant ce laps.
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                        () -> { try { pr.finish(); } catch (Exception ignored) {} }, 9000L);
            }
        }
    }
}
