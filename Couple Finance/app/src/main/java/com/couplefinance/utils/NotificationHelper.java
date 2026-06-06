package com.couplefinance.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.couplefinance.R;
import com.couplefinance.ui.DashboardActivity;

/**
 * NotificationHelper — Notifications locales 100% Android natif.
 *
 * Canaux :
 *  1. channel_transactions  → Nouvelle transaction du partenaire
 *  2. channel_charges       → Rappel charges fixes non appliquées
 *  3. channel_reminders     → Rappels 3 jours avant prélèvement
 *  4. channel_cycle         → Début de cycle (saisie de solde)
 */
public class NotificationHelper {

    private static final String CHANNEL_TRANSACTIONS = "channel_transactions";
    private static final String CHANNEL_CHARGES      = "channel_charges";
    private static final String CHANNEL_REMINDERS    = "channel_reminders";
    private static final String CHANNEL_CYCLE        = "channel_cycle";

    private static final String PREFS_NAME       = "notif_prefs";
    private static final String KEY_LAST_SEEN_TX = "last_seen_tx_timestamp";

    /** Extra posé sur l'intent DashboardActivity pour ouvrir le dialog de saisie de solde. */
    public static final String EXTRA_OPEN_BALANCE_DIALOG = "open_balance_dialog";

    private static NotificationHelper instance;
    private final Context context;

    private NotificationHelper(Context ctx) {
        this.context = ctx.getApplicationContext();
        createChannels();
    }

    public static NotificationHelper getInstance(Context ctx) {
        if (instance == null) {
            instance = new NotificationHelper(ctx);
        }
        return instance;
    }

    // ─────────────────────────────────────────────────────────────
    // Canaux (Android 8+)
    // ─────────────────────────────────────────────────────────────

    private static final String CHANNEL_BUDGET  = "channel_budget";
    private static final String CHANNEL_SAVINGS = "channel_savings";

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        // Budget dépassé / en alerte
        NotificationChannel budgetChannel = new NotificationChannel(
                CHANNEL_BUDGET, "Alertes budget",
                NotificationManager.IMPORTANCE_HIGH);
        budgetChannel.setDescription("Alerte quand une catégorie dépasse son budget");
        nm.createNotificationChannel(budgetChannel);

        // Objectif épargne atteint
        NotificationChannel savingsChannel = new NotificationChannel(
                CHANNEL_SAVINGS, "Objectifs épargne",
                NotificationManager.IMPORTANCE_DEFAULT);
        savingsChannel.setDescription("Félicitations quand un objectif d'épargne est atteint");
        nm.createNotificationChannel(savingsChannel);

        // Transactions partenaire
        NotificationChannel txChannel = new NotificationChannel(
                CHANNEL_TRANSACTIONS, "Nouvelles transactions",
                NotificationManager.IMPORTANCE_DEFAULT);
        txChannel.setDescription("Notifie quand votre partenaire ajoute une dépense");
        nm.createNotificationChannel(txChannel);

        // Charges fixes à appliquer
        NotificationChannel chargeChannel = new NotificationChannel(
                CHANNEL_CHARGES, "Charges fixes",
                NotificationManager.IMPORTANCE_HIGH);
        chargeChannel.setDescription("Rappels pour les charges fixes du mois");
        nm.createNotificationChannel(chargeChannel);

        // Rappels avant prélèvement
        NotificationChannel reminderChannel = new NotificationChannel(
                CHANNEL_REMINDERS, "Rappels prélèvements",
                NotificationManager.IMPORTANCE_DEFAULT);
        reminderChannel.setDescription("Rappels 3 jours avant chaque prélèvement");
        nm.createNotificationChannel(reminderChannel);

        // Début de cycle — saisie de solde
        NotificationChannel cycleChannel = new NotificationChannel(
                CHANNEL_CYCLE, "Début de cycle",
                NotificationManager.IMPORTANCE_HIGH);
        cycleChannel.setDescription("Rappel de saisir le solde au début de chaque cycle");
        nm.createNotificationChannel(cycleChannel);
    }

    // ─────────────────────────────────────────────────────────────
    // Nouvelle transaction partenaire
    // ─────────────────────────────────────────────────────────────

    public void notifyNewPartnerTransaction(String partnerName, String label,
                                             double amount, long txTimestamp) {
        long lastSeen = getLastSeenTimestamp();
        if (txTimestamp <= lastSeen) return;

        String name  = (partnerName != null && !partnerName.isEmpty()) ? partnerName : "Votre partenaire";
        String title = name + " a ajouté une dépense";
        String body  = label + " · " + String.format(java.util.Locale.FRANCE, "%.2f €", amount);

        sendNotification(CHANNEL_TRANSACTIONS, 1001, title, body, false);
    }

    // ─────────────────────────────────────────────────────────────
    // Charges fixes à appliquer
    // ─────────────────────────────────────────────────────────────

    public void notifyPendingFixedCharges(int count) {
        if (count <= 0) return;

        String title = count == 1
                ? "1 charge fixe en attente"
                : count + " charges fixes en attente";
        String body = "Ouvrez l'app pour appliquer vos charges du mois.";

        sendNotification(CHANNEL_CHARGES, 1002, title, body, false);
    }

    // ─────────────────────────────────────────────────────────────
    // Rappel avant prélèvement
    // ─────────────────────────────────────────────────────────────

    /**
     * Affiche une notification de rappel avant prélèvement.
     * Appelé par ChargeAlarmReceiver.
     *
     * @param title  Ex : "Prélèvement dans 3 jours"
     * @param body   Ex : "Loyer · 850 € sera prélevé le 5"
     */
    public void notifyChargeReminder(String title, String body) {
        sendNotification(CHANNEL_REMINDERS,
                (int) (System.currentTimeMillis() % 10000),
                title, body, false);
    }

    // ─────────────────────────────────────────────────────────────
    // Début de cycle — saisie de solde
    // ─────────────────────────────────────────────────────────────

    /**
     * Notification "Nouveau cycle financier — saisissez votre solde".
     * Appuyer sur la notification ouvre DashboardActivity avec le flag
     * EXTRA_OPEN_BALANCE_DIALOG=true.
     *
     * @param cycleLabel Ex : "5 juin → 4 juil. 2026"
     */
    public void notifyNewCycleStarted(String cycleLabel) {
        String title = "Nouveau cycle démarré 📅";
        String body  = cycleLabel != null && !cycleLabel.isEmpty()
                ? "Cycle " + cycleLabel + " — Saisissez votre solde actuel."
                : "Saisissez votre solde pour démarrer votre nouveau cycle.";

        sendNotification(CHANNEL_CYCLE, 2001, title, body, true);
    }

    // ─────────────────────────────────────────────────────────────
    // Core
    // ─────────────────────────────────────────────────────────────

    /**
     * @param openBalanceDialog si vrai, l'intent posera EXTRA_OPEN_BALANCE_DIALOG=true
     *                          pour que DashboardActivity ouvre le dialog de saisie.
     */
    private void sendNotification(String channelId, int notifId,
                                   String title, String body,
                                   boolean openBalanceDialog) {
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Intent intent = new Intent(context, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (openBalanceDialog) {
            intent.putExtra(EXTRA_OPEN_BALANCE_DIALOG, true);
        }

        int piFlags = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;

        PendingIntent pi = PendingIntent.getActivity(context, notifId, intent, piFlags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, channelId)
                : new Notification.Builder(context);

        Notification notif = builder
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();

        try {
            nm.notify(notifId, notif);
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS non accordée sur API 33+
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Gestion du "dernier vu"
    // ─────────────────────────────────────────────────────────────

    public long getLastSeenTimestamp() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_SEEN_TX, 0);
    }

    public void markTransactionsAsSeen(long timestamp) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_SEEN_TX, timestamp)
                .apply();
    }

    // ─────────────────────────────────────────────────────────────
    // Alertes budget
    // ─────────────────────────────────────────────────────────────

    public void notifyBudgetExceeded(String categoryName, double spent, double budget) {
        String title = "🔴 Budget dépassé : " + categoryName;
        double over = spent - budget;
        String body = String.format(java.util.Locale.FRANCE,
                "Dépassement de %.2f € (%.0f%% utilisé)", over, budget > 0 ? spent / budget * 100 : 100);
        int id = 3000 + (categoryName != null ? categoryName.hashCode() & 0xFFF : 0);
        sendNotification(CHANNEL_BUDGET, id, title, body, false);
    }

    public void notifyBudgetWarning(String categoryName, int percent) {
        String title = "⚠️ Budget : " + categoryName;
        String body = percent + "% du budget utilisé ce mois-ci";
        int id = 3500 + (categoryName != null ? categoryName.hashCode() & 0xFFF : 0);
        sendNotification(CHANNEL_BUDGET, id, title, body, false);
    }

    public void notifySavingsGoalCompleted(String goalName) {
        String title = "🎉 Objectif atteint !";
        String body = (goalName != null ? goalName : "Votre objectif") + " est entièrement financé.";
        int id = 4000 + (goalName != null ? goalName.hashCode() & 0xFFF : 0);
        sendNotification(CHANNEL_SAVINGS, id, title, body, false);
    }

    // Vérifie tous les budgets et envoie les alertes nécessaires (appel best-effort)
    public void checkAndNotifyBudgets(java.util.List<com.couplefinance.ui.budget.BudgetModels.CategoryBudget> budgets) {
        if (budgets == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        for (com.couplefinance.ui.budget.BudgetModels.CategoryBudget b : budgets) {
            if (b == null || b.budget <= 0) continue;
            String key = "budget_notif_" + b.name + "_" + java.util.Calendar.getInstance().get(java.util.Calendar.MONTH);
            int lastPct = prefs.getInt(key, 0);
            int pct = b.getPercent();
            if (b.isExceeded() && lastPct < 100) {
                notifyBudgetExceeded(b.name, b.spent, b.budget);
                prefs.edit().putInt(key, 100).apply();
            } else if (pct >= 80 && lastPct < 80) {
                notifyBudgetWarning(b.name, pct);
                prefs.edit().putInt(key, 80).apply();
            }
        }
    }
}
