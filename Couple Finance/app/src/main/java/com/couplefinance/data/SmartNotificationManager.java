package com.couplefinance.data;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.couplefinance.ui.budget.BudgetModels;
import com.couplefinance.ui.budget.BudgetRepository;
import com.couplefinance.utils.ParsedTransaction;

import java.text.NumberFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * SmartNotificationManager — Alertes push intelligentes CoupleFinance.
 *
 * Déclencheurs :
 *   • Grosse dépense détectée (> seuil, défaut 150 €)
 *   • Budget de catégorie dépassé
 *   • Budget de catégorie à 80 %+
 *   • Solde bas (< seuil, défaut 200 €)
 *   • Rythme de dépense mensuel dépassant le budget total
 *
 * Rate-limiting : au plus 1 alerte par type par jour (clé YYYY-DDD stockée en prefs).
 */
public final class SmartNotificationManager {

    private static final String TAG        = "SmartNotif";
    private static final String CHANNEL_ID = "cf_smart_alerts";
    private static final int    ACCENT     = 0xFFC0614A;

    private static final String PREFS                   = "smart_notif_prefs";
    private static final String K_THRESHOLD_EXPENSE     = "threshold_expense";
    private static final String K_THRESHOLD_BALANCE     = "threshold_balance";
    private static final String K_LAST_BUDGET_ALERT_DAY = "last_budget_alert_day_";
    private static final String K_LAST_EXPENSE_ALERT_TS = "last_expense_alert_ts";
    private static final String K_LAST_BALANCE_ALERT_DAY= "last_balance_alert_day";
    private static final String K_LAST_PACE_ALERT_DAY   = "last_pace_alert_day";

    private static final double DEFAULT_EXPENSE_THRESHOLD = 150.0;
    private static final double DEFAULT_BALANCE_THRESHOLD = 200.0;

    private static final int NOTIF_BASE_EXPENSE  = 3000;
    private static final int NOTIF_BUDGET        = 3100;
    private static final int NOTIF_BALANCE       = 3200;
    private static final int NOTIF_PACE          = 3300;

    private SmartNotificationManager() {}

    // ─────────────────────────────────────────────────────────────
    // Seuils configurables
    // ─────────────────────────────────────────────────────────────

    public static double getExpenseThreshold(Context ctx) {
        return prefs(ctx).getFloat(K_THRESHOLD_EXPENSE, (float) DEFAULT_EXPENSE_THRESHOLD);
    }

    public static void setExpenseThreshold(Context ctx, double v) {
        prefs(ctx).edit().putFloat(K_THRESHOLD_EXPENSE, (float) v).apply();
    }

    public static double getBalanceThreshold(Context ctx) {
        return prefs(ctx).getFloat(K_THRESHOLD_BALANCE, (float) DEFAULT_BALANCE_THRESHOLD);
    }

    public static void setBalanceThreshold(Context ctx, double v) {
        prefs(ctx).edit().putFloat(K_THRESHOLD_BALANCE, (float) v).apply();
    }

    // ─────────────────────────────────────────────────────────────
    // Point d'entrée principal — appelé après chaque synchro bancaire
    // ─────────────────────────────────────────────────────────────

    /**
     * Analyse les transactions importées et le solde pour déclencher
     * les alertes pertinentes.  Appel non-bloquant (charge BudgetRepository en async).
     */
    public static void checkAfterSync(Context ctx,
                                       List<ParsedTransaction> fresh,
                                       double liveBalance) {
        if (ctx == null) return;
        final Context app = ctx.getApplicationContext();

        // 1. Grosse dépense individuelle
        if (fresh != null) {
            double threshold = getExpenseThreshold(app);
            for (ParsedTransaction pt : fresh) {
                if (!"income".equals(pt.type) && pt.amount >= threshold) {
                    checkAndFireExpenseAlert(app, pt.amount, pt.label, pt.category, threshold);
                }
            }
        }

        // 2. Solde bas
        if (!Double.isNaN(liveBalance)) {
            double balThreshold = getBalanceThreshold(app);
            if (liveBalance < balThreshold) {
                checkAndFireBalanceAlert(app, liveBalance, balThreshold);
            }
        }

        // 3. Budget dépassé (async)
        BudgetRepository.loadBudgets(new BudgetRepository.Callback() {
            @Override public void onResult(List<BudgetModels.CategoryBudget> list) {
                checkBudgetAlerts(app, list);
                checkSpendingPaceAlert(app, list);
            }
            @Override public void onError(String error) {
                Log.d(TAG, "Budget non disponible pour alertes : " + error);
            }
        });
    }

    /**
     * Alerte grosse dépense déclenchée directement depuis l'UI (ajout manuel).
     */
    public static void checkSingleExpense(Context ctx, double amount, String label, String category) {
        if (ctx == null) return;
        Context app = ctx.getApplicationContext();
        double threshold = getExpenseThreshold(app);
        if (amount >= threshold) {
            checkAndFireExpenseAlert(app, amount, label, category, threshold);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Vérifications individuelles
    // ─────────────────────────────────────────────────────────────

    private static void checkAndFireExpenseAlert(Context app, double amount, String label,
                                                   String category, double threshold) {
        SharedPreferences p = prefs(app);
        long last = p.getLong(K_LAST_EXPENSE_ALERT_TS, 0);
        long now  = System.currentTimeMillis();
        // Une seule alerte grosse dépense max toutes les 30 min (évite le spam)
        if (now - last < 30 * 60 * 1000L) return;
        p.edit().putLong(K_LAST_EXPENSE_ALERT_TS, now).apply();

        String title = "Grosse dépense détectée";
        String name  = (label != null && !label.isEmpty()) ? label : "Dépense";
        String cat   = (category != null && !category.isEmpty()) ? " · " + category : "";
        String body  = fmt(amount) + " — " + name + cat;

        sendAlert(app, NOTIF_BASE_EXPENSE, title, body);
    }

    private static void checkAndFireBalanceAlert(Context app, double balance, double threshold) {
        String today = todayKey();
        if (today.equals(prefs(app).getString(K_LAST_BALANCE_ALERT_DAY, ""))) return;
        prefs(app).edit().putString(K_LAST_BALANCE_ALERT_DAY, today).apply();

        String title = "⚠ Solde faible";
        String body  = "Solde disponible : " + fmt(balance)
                + " (seuil d'alerte : " + fmt(threshold) + ")";
        sendAlert(app, NOTIF_BALANCE, title, body);
    }

    private static void checkBudgetAlerts(Context app, List<BudgetModels.CategoryBudget> list) {
        if (list == null || list.isEmpty()) return;
        String today = todayKey();

        for (BudgetModels.CategoryBudget c : list) {
            if (!c.isExceeded() && !c.isWarning()) continue;

            String key = K_LAST_BUDGET_ALERT_DAY + c.name.hashCode();
            if (today.equals(prefs(app).getString(key, ""))) continue;
            prefs(app).edit().putString(key, today).apply();

            String title;
            String body;

            if (c.isExceeded()) {
                title = "Budget dépassé — " + c.name;
                body  = fmt(c.spent) + " dépensés sur " + fmt(c.budget) + " budgétés"
                        + " (" + c.getPercent() + "%).";
            } else {
                title = "Budget à " + c.getPercent() + "% — " + c.name;
                body  = fmt(c.spent) + " / " + fmt(c.budget) + " — encore " + fmt(c.getRemaining()) + " disponibles.";
            }

            sendAlert(app, NOTIF_BUDGET + Math.abs(c.name.hashCode() % 50), title, body);
        }
    }

    private static void checkSpendingPaceAlert(Context app, List<BudgetModels.CategoryBudget> list) {
        if (list == null || list.isEmpty()) return;

        Calendar now = Calendar.getInstance();
        int day    = now.get(Calendar.DAY_OF_MONTH);
        int maxDay = now.getActualMaximum(Calendar.DAY_OF_MONTH);
        if (day <= 0) return;

        double totalBudget = 0, totalSpent = 0;
        for (BudgetModels.CategoryBudget c : list) {
            totalBudget += c.budget;
            totalSpent  += c.spent;
        }
        if (totalBudget < 0.01) return;

        double projected = totalSpent * ((double) maxDay / day);
        if (projected < totalBudget * 1.15) return; // moins de 15% de dépassement prévu → pas d'alerte

        String today = todayKey();
        if (today.equals(prefs(app).getString(K_LAST_PACE_ALERT_DAY, ""))) return;
        prefs(app).edit().putString(K_LAST_PACE_ALERT_DAY, today).apply();

        String title = "Rythme de dépenses élevé";
        int pct = (int) Math.round((projected / totalBudget - 1) * 100);
        String body = "À ce rythme, vous dépasserez votre budget de +" + pct + "%."
                + " Projection : " + fmt(projected) + " pour " + fmt(totalBudget) + " budgétés.";
        sendAlert(app, NOTIF_PACE, title, body);
    }

    // ─────────────────────────────────────────────────────────────
    // Notification helper
    // ─────────────────────────────────────────────────────────────

    private static void sendAlert(Context app, int id, String title, String body) {
        ensureChannel(app);
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Intent intent = new Intent(app, com.couplefinance.ui.DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(app, id, intent, piFlags);

        int smallIcon = app.getResources().getIdentifier("ic_stat_sync", "drawable", app.getPackageName());
        if (smallIcon == 0) smallIcon = android.R.drawable.ic_dialog_alert;

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(app, CHANNEL_ID)
                : new Notification.Builder(app);

        b.setContentTitle(title)
         .setContentText(body)
         .setStyle(new Notification.BigTextStyle().bigText(body).setSummaryText("CoupleFinance"))
         .setSmallIcon(smallIcon)
         .setColor(ACCENT)
         .setSubText("CoupleFinance")
         .setCategory(Notification.CATEGORY_STATUS)
         .setVisibility(Notification.VISIBILITY_PUBLIC)
         .setShowWhen(true)
         .setContentIntent(pi)
         .setAutoCancel(true);

        try { nm.notify(id, b.build()); }
        catch (Exception e) { Log.w(TAG, "notify échoué : " + e.getMessage()); }
    }

    private static void ensureChannel(Context app) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Alertes financières", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Alertes budget, grosse dépense, solde faible");
        ch.enableLights(true);
        ch.setLightColor(ACCENT);
        ch.setShowBadge(true);
        nm.createNotificationChannel(ch);
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String todayKey() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) + "-" + c.get(Calendar.DAY_OF_YEAR);
    }

    private static String fmt(double v) {
        try {
            NumberFormat nf = NumberFormat.getInstance(Locale.FRANCE);
            nf.setMaximumFractionDigits(2);
            nf.setMinimumFractionDigits(0);
            return nf.format(v) + " €";
        } catch (Exception e) {
            return String.format(Locale.FRANCE, "%.2f €", v);
        }
    }
}
