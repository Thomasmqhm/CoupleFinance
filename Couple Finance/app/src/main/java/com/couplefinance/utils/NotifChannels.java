package com.couplefinance.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

/**
 * Centralized notification channel registry.
 * All channel IDs live here; call ensureAll() once at app startup.
 */
public final class NotifChannels {

    public static final String TRANSACTIONS  = "channel_transactions";
    public static final String CHARGES       = "channel_charges";
    public static final String REMINDERS     = "channel_reminders";
    public static final String CYCLE         = "channel_cycle";
    public static final String BUDGET        = "channel_budget";
    public static final String SAVINGS       = "channel_savings";
    public static final String SMART_ALERTS  = "cf_smart_alerts";
    public static final String BANK_SYNC     = "channel_bank_autosync";

    private static final int ACCENT = 0xFFC0614A;

    private NotifChannels() {}

    public static void ensureAll(Context ctx) {
        if (ctx == null) return;
        NotificationManager nm = (NotificationManager)
                ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        nm.createNotificationChannel(channel(TRANSACTIONS,
                "Nouvelles transactions",
                "Notifie quand votre partenaire ajoute une dépense",
                NotificationManager.IMPORTANCE_DEFAULT, false));

        nm.createNotificationChannel(channel(CHARGES,
                "Charges fixes",
                "Rappels pour les charges fixes du mois",
                NotificationManager.IMPORTANCE_HIGH, false));

        nm.createNotificationChannel(channel(REMINDERS,
                "Rappels prélèvements",
                "Rappels 3 jours avant chaque prélèvement",
                NotificationManager.IMPORTANCE_DEFAULT, false));

        nm.createNotificationChannel(channel(CYCLE,
                "Début de cycle",
                "Rappel de saisir le solde au début de chaque cycle",
                NotificationManager.IMPORTANCE_HIGH, false));

        NotificationChannel budgetCh = channel(BUDGET,
                "Alertes budget",
                "Alerte quand une catégorie dépasse son budget",
                NotificationManager.IMPORTANCE_HIGH, true);
        nm.createNotificationChannel(budgetCh);

        nm.createNotificationChannel(channel(SAVINGS,
                "Objectifs épargne",
                "Félicitations quand un objectif d'épargne est atteint",
                NotificationManager.IMPORTANCE_DEFAULT, false));

        NotificationChannel smartCh = channel(SMART_ALERTS,
                "Alertes financières",
                "Alertes budget, grosse dépense, solde faible",
                NotificationManager.IMPORTANCE_HIGH, true);
        nm.createNotificationChannel(smartCh);

        nm.createNotificationChannel(channel(BANK_SYNC,
                "Synchro bancaire",
                "Résumé quotidien des opérations bancaires",
                NotificationManager.IMPORTANCE_DEFAULT, true));
    }

    private static NotificationChannel channel(String id, String name, String desc,
                                                int importance, boolean lights) {
        NotificationChannel ch = new NotificationChannel(id, name, importance);
        ch.setDescription(desc);
        if (lights) {
            ch.enableLights(true);
            ch.setLightColor(ACCENT);
        }
        ch.setShowBadge(true);
        return ch;
    }
}
