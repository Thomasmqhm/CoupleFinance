package com.couplefinance.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.RemoteViews;

import com.couplefinance.R;
import com.couplefinance.data.BankAutoSyncManager;
import com.couplefinance.ui.DashboardActivity;

/**
 * SoldeWidget — Widget écran d'accueil Android.
 *
 * Affiche sur l'écran d'accueil :
 *  ┌─────────────────────────────┐
 *  │  CoupleFinance              │
 *  │  Solde du foyer             │
 *  │  3 880,00 €                 │
 *  │  Dépenses : 1 220 €   ↻    │
 *  └─────────────────────────────┘
 *
 * Les données sont lues depuis SharedPreferences (cache local)
 * mis à jour par BalanceManager — pas d'appel réseau dans le widget.
 *
 * Installation dans AndroidManifest.xml :
 *   <receiver android:name=".widget.SoldeWidget"
 *       android:exported="true">
 *       <intent-filter>
 *           <action android:name="android.appwidget.action.APPWIDGET_UPDATE"/>
 *       </intent-filter>
 *       <meta-data
 *           android:name="android.appwidget.provider"
 *           android:resource="@xml/widget_solde_info"/>
 *   </receiver>
 */
public class SoldeWidget extends AppWidgetProvider {

    // ── Clés SharedPreferences (doivent correspondre à BalanceManager) ──
    private static final String PREFS_BALANCE  = "balance_prefs";
    private static final String KEY_BALANCE    = "monthly_start_balance";
    private static final String KEY_EXPENSES   = "widget_expenses_cache";
    private static final String KEY_INCOME     = "widget_income_cache";
    private static final String KEY_REAL_BAL   = "widget_real_balance";

    // ─────────────────────────────────────────────────────────────
    // Update
    // ─────────────────────────────────────────────────────────────

    @Override
    public void onUpdate(Context ctx, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            updateWidget(ctx, manager, id);
        }
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);

        // Mise à jour manuelle depuis l'app
        if ("com.couplefinance.UPDATE_WIDGET".equals(intent.getAction())) {
            AppWidgetManager manager = AppWidgetManager.getInstance(ctx);
            ComponentName component = new ComponentName(ctx, SoldeWidget.class);
            int[] ids = manager.getAppWidgetIds(component);
            for (int id : ids) updateWidget(ctx, manager, id);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Rendu du widget
    // ─────────────────────────────────────────────────────────────

    private static void updateWidget(Context ctx, AppWidgetManager manager, int widgetId) {
        RemoteViews views = new RemoteViews(ctx.getPackageName(), R.layout.widget_solde);

        // Lire les données depuis le cache local
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_BALANCE, Context.MODE_PRIVATE);

        double realBalance = Double.longBitsToDouble(
                prefs.getLong(KEY_REAL_BAL, Double.doubleToLongBits(0.0)));
        double expenses = Double.longBitsToDouble(
                prefs.getLong(KEY_EXPENSES, Double.doubleToLongBits(0.0)));
        double income = Double.longBitsToDouble(
                prefs.getLong(KEY_INCOME, Double.doubleToLongBits(0.0)));

        // Formater les montants
        String balanceStr  = formatMoney(realBalance);
        String expensesStr = formatMoney(expenses);
        String incomeStr   = formatMoney(income);

        // Couleur du solde (positif = vert, négatif = rouge)
        // RemoteViews ne supporte pas setTextColor dynamiquement sur API < 31,
        // on utilise un texte avec indicateur
        String balanceDisplay = (realBalance >= 0 ? "" : "−") + balanceStr;

        // Remplir les vues
        views.setTextViewText(R.id.widget_balance, balanceDisplay);
        views.setTextViewText(R.id.widget_expenses, "Dépenses : " + expensesStr);
        views.setTextViewText(R.id.widget_income,   "Revenus : " + incomeStr);
        views.setTextViewText(R.id.widget_title, "CoupleFinance");
        views.setTextViewText(R.id.widget_subtitle, "Solde du foyer");

        // ── Solde live du compte joint (source : synchro bancaire, autonome) ──
        // Lu directement depuis les préférences de BankAutoSyncManager, mises à
        // jour même quand l'app est fermée. Le widget reste donc « connecté ».
        double joint = Double.NaN;
        try {
            joint = BankAutoSyncManager.getLiveBalanceFor(ctx, "Compte joint");
        } catch (Exception ignored) {
        }
        if (!Double.isNaN(joint)) {
            String jointDisplay = (joint >= 0 ? "" : "−") + formatMoney(joint);
            views.setTextViewText(R.id.widget_joint, "Compte joint : " + jointDisplay);
            views.setViewVisibility(R.id.widget_joint, android.view.View.VISIBLE);
        } else {
            views.setViewVisibility(R.id.widget_joint, android.view.View.GONE);
        }

        // ── Horodatage de la dernière synchro bancaire ──
        try {
            long lastSync = BankAutoSyncManager.getLastSync(ctx);
            if (lastSync > 0) {
                String when = new java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.FRANCE)
                        .format(new java.util.Date(lastSync));
                views.setTextViewText(R.id.widget_updated, "Sync " + when);
            } else {
                views.setTextViewText(R.id.widget_updated, "");
            }
        } catch (Exception ignored) {
            views.setTextViewText(R.id.widget_updated, "");
        }

        int flags = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;

        // Clic sur le widget → ouvre le dashboard
        Intent openIntent = new Intent(ctx, DashboardActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(ctx, 0, openIntent, flags);
        views.setOnClickPendingIntent(R.id.widget_root, openPi);

        // Clic sur le bouton refresh → demande mise à jour
        Intent refreshIntent = new Intent(ctx, SoldeWidget.class);
        refreshIntent.setAction("com.couplefinance.UPDATE_WIDGET");
        PendingIntent refreshPi = PendingIntent.getBroadcast(ctx, 1, refreshIntent, flags);
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPi);

        // Clic sur "＋ Ajouter" → ouvre directement l'ajout rapide de transaction
        Intent addIntent = new Intent(ctx, DashboardActivity.class);
        addIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        addIntent.putExtra(DashboardActivity.EXTRA_QUICK_ADD, true);
        PendingIntent addPi = PendingIntent.getActivity(ctx, 2, addIntent, flags);
        views.setOnClickPendingIntent(R.id.widget_add, addPi);

        manager.updateAppWidget(widgetId, views);
    }

    /**
     * Demande une mise à jour de tous les widgets installés. À appeler depuis la
     * synchro bancaire (BankAutoSyncManager) pour que le widget reflète les
     * nouveaux soldes même app fermée.
     */
    public static void requestRefresh(Context ctx) {
        if (ctx == null) return;
        Intent intent = new Intent(ctx, SoldeWidget.class);
        intent.setAction("com.couplefinance.UPDATE_WIDGET");
        ctx.sendBroadcast(intent);
    }

    // ─────────────────────────────────────────────────────────────
    // API statique — appelée par HomeView/BalanceManager pour
    // mettre à jour le cache widget
    // ─────────────────────────────────────────────────────────────

    /**
     * Met à jour le cache SharedPreferences du widget avec les dernières données.
     * Appeler depuis HomeView.processData() après le calcul du solde.
     *
     * @param ctx        Context application
     * @param realBalance  Solde actuel calculé
     * @param expenses     Total dépenses du mois
     * @param income       Total revenus du mois
     */
    public static void updateCache(Context ctx, double realBalance,
                                    double expenses, double income) {
        if (ctx == null) return;

        ctx.getSharedPreferences(PREFS_BALANCE, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_REAL_BAL,  Double.doubleToLongBits(realBalance))
                .putLong(KEY_EXPENSES,  Double.doubleToLongBits(expenses))
                .putLong(KEY_INCOME,    Double.doubleToLongBits(income))
                .apply();

        // Déclencher une mise à jour du widget
        Intent intent = new Intent(ctx, SoldeWidget.class);
        intent.setAction("com.couplefinance.UPDATE_WIDGET");
        ctx.sendBroadcast(intent);
    }

    // ─────────────────────────────────────────────────────────────

    private static String formatMoney(double amount) {
        return String.format(java.util.Locale.FRANCE, "%,.0f €", Math.abs(amount));
    }
}
