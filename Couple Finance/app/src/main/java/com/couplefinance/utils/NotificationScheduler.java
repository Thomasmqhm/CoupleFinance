package com.couplefinance.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.couplefinance.data.CycleManager;
import com.couplefinance.data.HouseholdManager;
import com.couplefinance.data.RecurringChargeManager;
import com.couplefinance.ui.settings.SettingsCache;
import com.couplefinance.ui.settings.SettingsModels;

import java.util.Calendar;
import java.util.List;

/**
 * NotificationScheduler — Planification de deux types d'alarmes.
 *
 * ── Type 1 : Rappels prélèvements ──────────────────────────────────────
 *   3 jours avant chaque charge fixe du mois.
 *   Codes AlarmManager : 5000–5049 (BASE_REQUEST_CODE).
 *   Action : CHARGE_REMINDER
 *
 * ── Type 2 : Saisie de solde (nouveau cycle) ───────────────────────────
 *   Le jour de démarrage du cycle (configurable dans CycleManager),
 *   à 9h00. Rappelle à l'utilisateur de saisir son solde bancaire.
 *   Code AlarmManager : REQUEST_CODE_BALANCE = 4999.
 *   Action : BALANCE_REMINDER
 *
 * Les deux types sont reprogrammés ensemble via scheduleAll().
 * En cas de reboot, BOOT_COMPLETED les replanifie automatiquement.
 *
 * AndroidManifest.xml — ajouter BALANCE_REMINDER au ChargeAlarmReceiver :
 *   <action android:name="com.couplefinance.BALANCE_REMINDER"/>
 */
public final class NotificationScheduler {

    private static final int    DAYS_BEFORE          = 3;
    private static final int    ALARM_HOUR           = 9;
    private static final int    BASE_REQUEST_CODE    = 5000;
    private static final int    REQUEST_CODE_BALANCE = 4999;
    private static final int    REQUEST_CODE_TG      = 4998;

    private static final String ACTION_CHARGE_REMINDER  = "com.couplefinance.CHARGE_REMINDER";
    private static final String ACTION_BALANCE_REMINDER = "com.couplefinance.BALANCE_REMINDER";
    private static final String ACTION_TG_ALERTS         = "com.couplefinance.TG_ALERTS";

    private NotificationScheduler() {}

    // ─────────────────────────────────────────────────────────────
    // API publique
    // ─────────────────────────────────────────────────────────────

    /**
     * Planifie TOUTES les alarmes :
     *   • Rappels prélèvements (charges fixes)
     *   • Saisie de solde (début de cycle)
     *
     * Annule les alarmes existantes avant de reprogrammer.
     * À appeler depuis :
     *   - DashboardActivity.onCreate()
     *   - AbonnementsView après ajout/modification/suppression d'une charge
     *   - SettingsView après modification du jour de début de cycle
     */
    public static void scheduleAll(Context ctx) {
        if (ctx == null) return;

        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        // Annuler toutes les alarmes existantes
        for (int i = 0; i < 50; i++) cancelChargeAlarm(ctx, am, BASE_REQUEST_CODE + i);
        cancelBalanceAlarm(ctx, am);

        // Reprogrammer les rappels de prélèvements
        scheduleChargeReminders(ctx, am);

        // Reprogrammer l'alarme de saisie de solde
        scheduleBalanceReminder(ctx, am);

        // Vérification quotidienne des alertes Telegram (arrière-plan)
        scheduleTelegramAlerts(ctx, am);
    }

    /**
     * Annule toutes les alarmes (ex : lors de la déconnexion).
     */
    public static void cancelAll(Context ctx) {
        if (ctx == null) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        for (int i = 0; i < 50; i++) cancelChargeAlarm(ctx, am, BASE_REQUEST_CODE + i);
        cancelBalanceAlarm(ctx, am);
        cancelTelegramAlerts(ctx, am);
    }

    // ─────────────────────────────────────────────────────────────
    // Alarmes — Rappels prélèvements
    // ─────────────────────────────────────────────────────────────

    private static void scheduleChargeReminders(Context ctx, AlarmManager am) {
        SettingsModels.State state   = SettingsCache.get();
        List<SettingsModels.FixedCharge> charges = state.charges;
        if (charges == null || charges.isEmpty()) return;

        Calendar today       = Calendar.getInstance();
        int currentMonth     = today.get(Calendar.MONTH);
        int currentYear      = today.get(Calendar.YEAR);
        int requestCode      = BASE_REQUEST_CODE;

        for (SettingsModels.FixedCharge charge : charges) {
            if (charge == null || charge.name == null) continue;

            int dueDay = normalizeDueDay(charge.dayOfMonth);

            Calendar reminderDate = Calendar.getInstance();
            reminderDate.set(currentYear, currentMonth,
                    Math.max(1, dueDay - DAYS_BEFORE),
                    ALARM_HOUR, 0, 0);
            reminderDate.set(Calendar.MILLISECOND, 0);

            if (reminderDate.before(today)) {
                reminderDate.add(Calendar.MONTH, 1);
            }

            Calendar dueDate = Calendar.getInstance();
            dueDate.set(currentYear, currentMonth, dueDay, 12, 0, 0);
            if (dueDate.before(today)
                    && reminderDate.get(Calendar.MONTH) == currentMonth) {
                continue;
            }

            scheduleAlarm(ctx, am, requestCode,
                    ACTION_CHARGE_REMINDER,
                    buildChargeIntent(ctx, charge.name, charge.amount),
                    reminderDate.getTimeInMillis());

            requestCode++;
        }
    }

    private static Intent buildChargeIntent(Context ctx, String name, double amount) {
        Intent intent = new Intent(ctx, ChargeAlarmReceiver.class);
        intent.setAction(ACTION_CHARGE_REMINDER);
        intent.putExtra("charge_name",   name);
        intent.putExtra("charge_amount", amount);
        intent.putExtra("days_before",   DAYS_BEFORE);
        return intent;
    }

    // ─────────────────────────────────────────────────────────────
    // Alarme — Saisie de solde (début de cycle)
    // ─────────────────────────────────────────────────────────────

    /**
     * Programme l'alarme de saisie de solde pour le prochain jour de début
     * de cycle (ex : le 5 du mois prochain si on est déjà passé le 5).
     *
     * Si aujourd'hui EST le jour de début de cycle et qu'il est avant 9h00,
     * l'alarme est programmée pour aujourd'hui à 9h00.
     */
    private static void scheduleBalanceReminder(Context ctx, AlarmManager am) {
        int cycleStartDay = CycleManager.getInstance().getCycleStartDay();
        int alarmDay      = normalizeDueDay(cycleStartDay);

        Calendar today   = Calendar.getInstance();
        Calendar trigger = Calendar.getInstance();
        trigger.set(Calendar.DAY_OF_MONTH, alarmDay);
        trigger.set(Calendar.HOUR_OF_DAY,  ALARM_HOUR);
        trigger.set(Calendar.MINUTE,       0);
        trigger.set(Calendar.SECOND,       0);
        trigger.set(Calendar.MILLISECOND,  0);

        // Si la date trigger est déjà passée ce mois-ci → mois prochain
        if (!trigger.after(today)) {
            trigger.add(Calendar.MONTH, 1);
        }

        Intent intent = new Intent(ctx, ChargeAlarmReceiver.class);
        intent.setAction(ACTION_BALANCE_REMINDER);

        scheduleAlarm(ctx, am, REQUEST_CODE_BALANCE, ACTION_BALANCE_REMINDER,
                intent, trigger.getTimeInMillis());
    }

    private static void cancelBalanceAlarm(Context ctx, AlarmManager am) {
        Intent intent = new Intent(ctx, ChargeAlarmReceiver.class);
        intent.setAction(ACTION_BALANCE_REMINDER);

        int flags = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_NO_CREATE;

        PendingIntent pi = PendingIntent.getBroadcast(
                ctx, REQUEST_CODE_BALANCE, intent, flags);
        if (pi != null) { am.cancel(pi); pi.cancel(); }
    }

    private static void scheduleTelegramAlerts(Context ctx, AlarmManager am) {
        Calendar trigger = Calendar.getInstance();
        trigger.set(Calendar.HOUR_OF_DAY, ALARM_HOUR);
        trigger.set(Calendar.MINUTE,      0);
        trigger.set(Calendar.SECOND,      0);
        trigger.set(Calendar.MILLISECOND, 0);
        if (!trigger.after(Calendar.getInstance())) trigger.add(Calendar.DAY_OF_MONTH, 1);

        Intent intent = new Intent(ctx, ChargeAlarmReceiver.class);
        intent.setAction(ACTION_TG_ALERTS);

        int flags = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, REQUEST_CODE_TG, intent, flags);

        // Inexact : pas besoin de la permission d'alarme exacte (Android 12+)
        try {
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, trigger.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY, pi);
        } catch (Exception ignored) {
        }
    }

    private static void cancelTelegramAlerts(Context ctx, AlarmManager am) {
        Intent intent = new Intent(ctx, ChargeAlarmReceiver.class);
        intent.setAction(ACTION_TG_ALERTS);
        int flags = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_NO_CREATE;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, REQUEST_CODE_TG, intent, flags);
        if (pi != null) { am.cancel(pi); pi.cancel(); }
    }

    // ─────────────────────────────────────────────────────────────
    // Planification générique
    // ─────────────────────────────────────────────────────────────

    private static void scheduleAlarm(Context ctx, AlarmManager am,
                                       int requestCode, String action,
                                       Intent intent, long triggerAtMillis) {
        int flags = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;

        PendingIntent pi = PendingIntent.getBroadcast(ctx, requestCode, intent, flags);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            }
        } catch (SecurityException e) {
            try { am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi); }
            catch (Exception ignored) {}
        }
    }

    private static void cancelChargeAlarm(Context ctx, AlarmManager am, int requestCode) {
        Intent intent = new Intent(ctx, ChargeAlarmReceiver.class);
        intent.setAction(ACTION_CHARGE_REMINDER);

        int flags = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_NO_CREATE;

        PendingIntent pi = PendingIntent.getBroadcast(ctx, requestCode, intent, flags);
        if (pi != null) { am.cancel(pi); pi.cancel(); }
    }

    private static int normalizeDueDay(int day) {
        if (day < 1)  return 1;
        if (day > 28) return 28;
        return day;
    }

    // ─────────────────────────────────────────────────────────────
    // BroadcastReceiver
    // ─────────────────────────────────────────────────────────────

    /**
     * ChargeAlarmReceiver — Reçoit les alarmes et affiche les notifications.
     *
     * AndroidManifest.xml (ajouter BALANCE_REMINDER) :
     *   <receiver android:name=".utils.NotificationScheduler$ChargeAlarmReceiver"
     *       android:exported="false">
     *       <intent-filter>
     *           <action android:name="com.couplefinance.CHARGE_REMINDER"/>
     *           <action android:name="com.couplefinance.BALANCE_REMINDER"/>
     *           <action android:name="android.intent.action.BOOT_COMPLETED"/>
     *       </intent-filter>
     *   </receiver>
     */
    public static class ChargeAlarmReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (ctx == null || intent == null) return;

            // Évalue les alertes Telegram en arrière-plan (no-op si non configuré)
            try { com.couplefinance.data.TelegramScheduler.checkAlertsBackground(ctx); } catch (Exception ignored) {}

            String action = intent.getAction();

            // ── Replanifier après reboot ────────────────────────────────
            if ("android.intent.action.BOOT_COMPLETED".equals(action)) {
                HouseholdManager.getInstance().init(ctx);
                CycleManager.getInstance().init(ctx);
                scheduleAll(ctx);
                return;
            }

            // ── Saisie de solde — début de cycle ────────────────────────
            if (ACTION_BALANCE_REMINDER.equals(action)) {
                CycleManager.getInstance().init(ctx);
                String label = CycleManager.getInstance().getCurrentCycleLabel();
                NotificationHelper.getInstance(ctx).notifyNewCycleStarted(label);

                // Reprogrammer pour le cycle suivant
                AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
                if (am != null) scheduleBalanceReminder(ctx, am);
                return;
            }

            // ── Rappel prélèvement ──────────────────────────────────────
            if (ACTION_CHARGE_REMINDER.equals(action)) {
                String chargeName = intent.getStringExtra("charge_name");
                double amount     = intent.getDoubleExtra("charge_amount", 0);
                int    daysBefore = intent.getIntExtra("days_before", DAYS_BEFORE);

                if (chargeName == null || chargeName.isEmpty()) return;

                String title = "Prélèvement dans " + daysBefore + " jour"
                        + (daysBefore > 1 ? "s" : "");
                String body  = chargeName + " · "
                        + String.format(java.util.Locale.FRANCE, "%.2f €", amount)
                        + " sera prélevé le "
                        + (Calendar.getInstance().get(Calendar.DAY_OF_MONTH) + daysBefore);

                NotificationHelper.getInstance(ctx).notifyChargeReminder(title, body);

                RecurringChargeManager.getInstance().init(ctx);
                RecurringChargeManager.getInstance().checkAndApplyRecurringCharges(null);
            }
        }
    }
}
