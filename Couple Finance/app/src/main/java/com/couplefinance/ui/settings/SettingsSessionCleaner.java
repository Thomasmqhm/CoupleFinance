package com.couplefinance.ui.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.couplefinance.AuthManager;
import com.couplefinance.LoginActivity;

/**
 * SettingsSessionCleaner
 *
 * Centralise les actions sensibles de session :
 * - déconnexion
 * - nettoyage des préférences locales non critiques
 * - retour LoginActivity sans retour fantôme vers le Dashboard
 *
 * Important :
 * - ne supprime pas les préférences de thème par défaut
 * - ne touche pas aux documents Firestore
 * - ne lance aucune navigation Dashboard
 */
public final class SettingsSessionCleaner {

    private SettingsSessionCleaner() {
    }

    public static void logout(Activity activity) {
        if (activity == null) return;

        try {
            AuthManager.getInstance().logout();
        } catch (Exception ignored) {
        }

        clearRuntimePrefs(activity);

        Intent intent = new Intent(activity, LoginActivity.class);
        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        activity.startActivity(intent);

        try {
            activity.finishAffinity();
        } catch (Exception e) {
            activity.finish();
        }
    }

    public static void clearRuntimePrefs(Activity activity) {
        if (activity == null) return;

        clear(activity, "couplefinance_runtime");
        clear(activity, "couplefinance_session");
        clear(activity, "couplefinance_cache");
        clear(activity, "dashboard_runtime");
        clear(activity, "settings_runtime");

        /*
         * On garde volontairement :
         * - couplefinance_theme
         * - dashboard_widgets
         * - préférences de ratio
         *
         * L'utilisateur retrouve ainsi son thème et sa configuration locale
         * après reconnexion, sans retour fantôme sur Dashboard.
         */
    }

    public static void clearAllLocalUserData(Activity activity) {
        if (activity == null) return;

        clearRuntimePrefs(activity);

        clear(activity, "couplefinance_prefs");
        clear(activity, "couplefinance_notifications");
        clear(activity, "couplefinance_security");
        clear(activity, "couplefinance_currency");
        clear(activity, "couplefinance_language");
        clear(activity, "couplefinance_joint_account");
    }

    private static void clear(Context context, String prefsName) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
            prefs.edit().clear().apply();
        } catch (Exception ignored) {
        }
    }
}
