package com.couplefinance.ui.home;

import android.app.Activity;
import android.content.SharedPreferences;

import com.couplefinance.AuthManager;

import java.util.Calendar;
import java.util.Locale;

/**
 * Cache local côté SharedPreferences pour le dashboard Home.
 *
 * Centralise la lecture/écriture des deux valeurs mises en cache localement
 * pour offrir un affichage instantané pendant que les vraies données
 * remontent de Firestore :
 *
 *   - la limite de découvert ("overdraft_limit_{uid}")
 *   - le solde de début de mois ("monthly_start_balance_{uid}_{YYYY-MM}")
 *
 * Toutes les méthodes sont statiques. En cas d'absence de l'identifiant
 * utilisateur, les getters renvoient simplement {@code null} pour signaler
 * qu'il n'y a pas de valeur disponible — l'appelant garde la main sur le
 * fallback à appliquer.
 */
public final class HomeData {

    /** Nom du fichier SharedPreferences utilisé par tout le dashboard Home. */
    public static final String PREFS_NAME = "home_cache";

    private HomeData() {}

    // ─────────────────────────────────────────────────────
    // Limite de découvert (constante par utilisateur)
    // ─────────────────────────────────────────────────────

    private static String overdraftKey() {
        String uid = AuthManager.getInstance().getUserId();
        return "overdraft_limit_" + uid;
    }

    public static void saveOverdraft(Activity activity, double amount) {
        if (activity == null) return;
        activity.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE)
                .edit()
                .putFloat(overdraftKey(), (float) amount)
                .apply();
    }

    /**
     * @return la limite de découvert mise en cache, ou null si aucun utilisateur
     *         n'est connecté ou qu'aucune valeur n'est encore stockée.
     */
    public static Double getOverdraft(Activity activity) {
        if (activity == null) return null;
        if (AuthManager.getInstance().getUserId() == null) return null;

        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        String key = overdraftKey();
        if (!prefs.contains(key)) return null;
        return (double) prefs.getFloat(key, 0f);
    }

    // ─────────────────────────────────────────────────────
    // Solde de début de mois (clé par utilisateur ET par mois)
    // ─────────────────────────────────────────────────────

    private static String monthlyBalanceKey() {
        Calendar cal = Calendar.getInstance();
        String month = cal.get(Calendar.YEAR) + "-"
                + String.format(Locale.ROOT, "%02d", cal.get(Calendar.MONTH) + 1);
        String uid = AuthManager.getInstance().getUserId();
        return "monthly_start_balance_" + uid + "_" + month;
    }

    public static void saveMonthlyBalance(Activity activity, double amount) {
        if (activity == null) return;
        activity.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE)
                .edit()
                .putFloat(monthlyBalanceKey(), (float) amount)
                .apply();
    }

    /**
     * @return le solde de début de mois courant mis en cache, ou null si aucun
     *         utilisateur n'est connecté ou qu'aucune valeur n'est encore
     *         stockée pour le mois en cours.
     */
    public static Double getMonthlyBalance(Activity activity) {
        if (activity == null) return null;
        if (AuthManager.getInstance().getUserId() == null) return null;

        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        String key = monthlyBalanceKey();
        if (!prefs.contains(key)) return null;
        return (double) prefs.getFloat(key, 0f);
    }
}
