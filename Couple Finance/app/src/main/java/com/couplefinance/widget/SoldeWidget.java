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

        // Clic sur le widget → ouvre le dashboard
        Intent openIntent = new Intent(ctx, DashboardActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;

        PendingIntent openPi = PendingIntent.getActivity(ctx, 0, openIntent, flags);
        views.setOnClickPendingIntent(R.id.widget_root, openPi);

        // Clic sur le bouton refresh → demande mise à jour
        Intent refreshIntent = new Intent(ctx, SoldeWidget.class);
        refreshIntent.setAction("com.couplefinance.UPDATE_WIDGET");
        PendingIntent refreshPi = PendingIntent.getBroadcast(ctx, 1, refreshIntent, flags);
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPi);

        manager.updateAppWidget(widgetId, views);
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
