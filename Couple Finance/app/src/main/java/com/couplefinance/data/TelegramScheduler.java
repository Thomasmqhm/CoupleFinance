package com.couplefinance.data;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Envoi Telegram automatique, déclenché à l'ouverture de l'app (pas de serveur).
 *
 *  - Digest périodique : "off" / "weekly" / "monthly".
 *    Envoyé la première fois que l'app est ouverte dans une nouvelle semaine/mois
 *    (anti-doublon via une clé de période mémorisée).
 *
 *  - Alerte "compte joint bas" : si le solde du joint passe sous un seuil,
 *    un message part (une fois par jour maximum).
 *
 * Tout passe par le chat configuré (chat commun) : aucun solde personnel n'est exposé.
 */
public final class TelegramScheduler {

    private static final String PREFS = "telegram_prefs";
    private static final String K_FREQ = "digest_freq";          // off | weekly | monthly
    private static final String K_DIGEST_LAST = "digest_last";    // clé de période déjà envoyée
    private static final String K_LOWJOINT = "alert_lowjoint";    // seuil (vide = désactivé)
    private static final String K_LOWJOINT_LAST = "alert_lowjoint_last"; // jour du dernier envoi
    private static final String K_COVERAGE = "alert_coverage";           // bool : joint < prélèvements
    private static final String K_COVERAGE_LAST = "alert_coverage_last"; // jour du dernier envoi

    public static final String OFF = "off";
    public static final String DAILY = "daily";
    public static final String WEEKLY = "weekly";
    public static final String MONTHLY = "monthly";

    private TelegramScheduler() { }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ─── Réglages (utilisés par l'écran Paramètres) ───

    public static void setDigestFrequency(Context ctx, String freq) {
        if (ctx == null || freq == null) return;
        prefs(ctx).edit().putString(K_FREQ, freq).apply();
    }

    public static String getDigestFrequency(Context ctx) {
        return ctx == null ? OFF : prefs(ctx).getString(K_FREQ, OFF);
    }

    /** Seuil d'alerte joint bas. NaN = désactivé. */
    public static void setLowJointThreshold(Context ctx, double value) {
        if (ctx == null) return;
        SharedPreferences.Editor ed = prefs(ctx).edit();
        if (Double.isNaN(value)) ed.remove(K_LOWJOINT);
        else ed.putString(K_LOWJOINT, String.valueOf(value));
        ed.apply();
    }

    public static double getLowJointThreshold(Context ctx) {
        if (ctx == null) return Double.NaN;
        String s = prefs(ctx).getString(K_LOWJOINT, "");
        if (s == null || s.isEmpty()) return Double.NaN;
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    public static void setCoverageAlert(Context ctx, boolean on) {
        if (ctx != null) prefs(ctx).edit().putBoolean(K_COVERAGE, on).apply();
    }

    public static boolean isCoverageAlert(Context ctx) {
        return ctx != null && prefs(ctx).getBoolean(K_COVERAGE, false);
    }

    // ─── Point d'entrée : à appeler à l'ouverture (DashboardActivity.onCreate) ───

    public static void checkAndSend(final Activity activity) {
        if (activity == null) return;
        TelegramManager.getInstance().init(activity);
        if (!TelegramManager.getInstance().isConfigured()) return;

        final Context ctx = activity.getApplicationContext();
        checkDigest(activity, ctx);
        checkAlerts(ctx);
    }

    /** Alertes seules, sans digest : utilisable depuis un BroadcastReceiver (app fermée). */
    public static void checkAlertsBackground(Context ctx) {
        if (ctx == null) return;
        TelegramManager.getInstance().init(ctx);
        if (!TelegramManager.getInstance().isConfigured()) return;
        Context app = ctx.getApplicationContext();
        checkAlerts(app);
        checkDigestBackground(app);
    }

    private static void checkAlerts(Context ctx) {
        checkLowJointAlert(ctx);
        checkCoverageAlert(ctx);
    }

    private static void checkDigest(final Activity activity, final Context ctx) {
        String freq = getDigestFrequency(ctx);
        if (OFF.equals(freq)) return;

        final String periodKey = periodKey(freq);
        if (periodKey.equals(prefs(ctx).getString(K_DIGEST_LAST, ""))) return; // déjà envoyé

        // On marque AVANT l'envoi pour éviter les doubles déclenchements rapprochés ;
        // si l'envoi échoue, on remet la clé précédente.
        final String previous = prefs(ctx).getString(K_DIGEST_LAST, "");
        prefs(ctx).edit().putString(K_DIGEST_LAST, periodKey).apply();

        TelegramSummary.buildAndSend(activity, new TelegramSummary.Callback() {
            @Override public void onSuccess() { /* clé déjà posée */ }
            @Override public void onError(String error) {
                prefs(ctx).edit().putString(K_DIGEST_LAST, previous).apply(); // on réessaiera
            }
        });
    }

    /**
     * Digest "léger" envoyable en arrière-plan (sans Activity) : compte joint +
     * prélèvements à venir. Partage la clé de période avec le digest complet :
     * si l'app est ouverte d'abord, c'est le digest complet qui part ; sinon
     * l'alarme quotidienne envoie cette version allégée. Un seul des deux par période.
     */
    private static void checkDigestBackground(final Context ctx) {
        String freq = getDigestFrequency(ctx);
        if (OFF.equals(freq)) return;

        final String periodKey = periodKey(freq);
        if (periodKey.equals(prefs(ctx).getString(K_DIGEST_LAST, ""))) return;

        final String previous = prefs(ctx).getString(K_DIGEST_LAST, "");
        prefs(ctx).edit().putString(K_DIGEST_LAST, periodKey).apply(); // on réserve la période

        final StringBuilder sb = new StringBuilder();
        sb.append("<b>CoupleFinance \u2014 r\u00e9sum\u00e9</b>\n");
        sb.append("<i>").append(headerDate()).append("</i>\n");

        double joint = getJointBalance(ctx);
        if (!Double.isNaN(joint)) {
            sb.append("\n<b>Compte joint</b>\n\u2022 Solde : ").append(money(joint)).append("\n");
        }

        try {
            RecurringChargeManager.getInstance().init(ctx);
            RecurringChargeManager.getInstance().getUpcomingChargesForCurrentMonth(
                    new RecurringChargeManager.UpcomingChargesCallback() {
                        @Override public void onResult(double total, int count) {
                            if (count > 0) {
                                sb.append("\n<b>Prochains pr\u00e9l\u00e8vements</b>\n\u2022 ")
                                        .append(count)
                                        .append(count > 1 ? " pr\u00e9l\u00e8vements \u00e0 venir : "
                                                          : " pr\u00e9l\u00e8vement \u00e0 venir : ")
                                        .append(money(total)).append("\n");
                            }
                            sendLite(ctx, sb.toString(), previous);
                        }

                        @Override public void onError(String error) {
                            sendLite(ctx, sb.toString(), previous);
                        }
                    });
        } catch (Exception e) {
            sendLite(ctx, sb.toString(), previous);
        }
    }

    private static void sendLite(final Context ctx, String message, final String previous) {
        TelegramManager.getInstance().sendMessage(message, new TelegramManager.Callback() {
            @Override public void onSuccess(String response) { }
            @Override public void onError(String error) {
                prefs(ctx).edit().putString(K_DIGEST_LAST, previous).apply(); // on réessaiera
            }
        });
    }
    
    private static double getJointBalance(Context ctx) {

    double live = BankAutoSyncManager.getLiveBalanceFor(ctx, "Compte joint");

    if (!Double.isNaN(live)) {
        return live;
    }

    try {
        JointAccountManager.getInstance().init(ctx);
        return JointAccountManager.getInstance().getBalanceLocal(ctx);
    } catch (Exception e) {
        return Double.NaN;
    }
}

    private static String headerDate() {
        return new SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(new Date());
    }

    private static void checkLowJointAlert(final Context ctx) {
        double threshold = getLowJointThreshold(ctx);
        if (Double.isNaN(threshold)) return;

        double joint = getJointBalance(ctx);
        if (Double.isNaN(joint) || joint >= threshold) return;

        String today = dayKey();
        if (today.equals(prefs(ctx).getString(K_LOWJOINT_LAST, ""))) return; // déjà alerté aujourd'hui

        String msg = "<b>\u26a0\ufe0f Compte joint bas</b>\n"
                + "Solde : " + money(joint) + "\n"
                + "Seuil d'alerte : " + money(threshold);

        prefs(ctx).edit().putString(K_LOWJOINT_LAST, today).apply();
        TelegramManager.getInstance().sendMessage(msg, new TelegramManager.Callback() {
            @Override public void onSuccess(String response) { }
            @Override public void onError(String error) {
                prefs(ctx).edit().remove(K_LOWJOINT_LAST).apply(); // on pourra réessayer
            }
        });
    }

    private static void checkCoverageAlert(final Context ctx) {
        if (!isCoverageAlert(ctx)) return;
        final double joint = getJointBalance(ctx);
        if (Double.isNaN(joint)) return;
        try {
            RecurringChargeManager.getInstance().init(ctx);
            RecurringChargeManager.getInstance().getUpcomingChargesForCurrentMonth(
                    new RecurringChargeManager.UpcomingChargesCallback() {
                        @Override
                        public void onResult(double total, int count) {
                            if (count <= 0 || total <= 0 || joint >= total) return;
                            String today = dayKey();
                            if (today.equals(prefs(ctx).getString(K_COVERAGE_LAST, ""))) return;
                            String msg = "<b>\u26a0\ufe0f Pr\u00e9l\u00e8vements non couverts</b>\n"
                                    + "Compte joint : " + money(joint) + "\n"
                                    + "\u00c0 pr\u00e9lever ce mois : " + money(total);
                            prefs(ctx).edit().putString(K_COVERAGE_LAST, today).apply();
                            TelegramManager.getInstance().sendMessage(msg, new TelegramManager.Callback() {
                                @Override public void onSuccess(String response) { }
                                @Override public void onError(String error) {
                                    prefs(ctx).edit().remove(K_COVERAGE_LAST).apply();
                                }
                            });
                        }

                        @Override
                        public void onError(String error) { }
                    });
        } catch (Exception ignored) {
        }
    }

    // ─── Helpers ───

    private static String periodKey(String freq) {
        Calendar c = Calendar.getInstance(Locale.FRANCE);
        int year = c.get(Calendar.YEAR);
        if (DAILY.equals(freq)) {
            return year + "-D" + c.get(Calendar.DAY_OF_YEAR);
        }
        if (WEEKLY.equals(freq)) {
            return year + "-W" + c.get(Calendar.WEEK_OF_YEAR);
        }
        return year + "-M" + (c.get(Calendar.MONTH) + 1);
    }

    private static String dayKey() {
        Calendar c = Calendar.getInstance(Locale.FRANCE);
        return c.get(Calendar.YEAR) + "-" + c.get(Calendar.DAY_OF_YEAR);
    }

    private static String money(double v) {
        return String.format(Locale.FRANCE, "%,.2f \u20ac", v).replace('\u00a0', ' ');
    }
}
