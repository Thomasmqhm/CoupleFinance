package com.couplefinance.core.theme;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/**
 * Gestion du mode sombre 3 états : auto / light / dark.
 * "auto" suit l'UI_MODE_NIGHT du système.
 */
public final class DarkModeManager {

    public static final String OPTION_AUTO  = "auto";
    public static final String OPTION_LIGHT = "light";
    public static final String OPTION_DARK  = "dark";

    private static final String PREFS = "couplefinance_theme";
    private static final String KEY   = "dark_mode_option";

    private DarkModeManager() {}

    /** Enregistre l'option et applique le thème correspondant via ThemeManager. */
    public static void setOption(Activity activity, String option) {
        if (activity == null) return;
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, option).apply();
        applyToThemeManager(activity);
        activity.recreate();
    }

    /** Retourne l'option courante (auto / light / dark). */
    public static String getOption(Context context) {
        if (context == null) return OPTION_AUTO;
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // Migration de l'ancien booléen
        if (p.contains("dark_mode") && !p.contains(KEY)) {
            boolean old = p.getBoolean("dark_mode", false);
            String migrated = old ? OPTION_DARK : OPTION_LIGHT;
            p.edit().putString(KEY, migrated).remove("dark_mode").apply();
            return migrated;
        }
        return p.getString(KEY, OPTION_AUTO);
    }

    /** Résout si le mode sombre est actif (true = dark). */
    public static boolean isDark(Context context) {
        String option = getOption(context);
        if (OPTION_DARK.equals(option)) return true;
        if (OPTION_LIGHT.equals(option)) return false;
        // Auto : suit le système
        int nightBits = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightBits == Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * À appeler dans onCreate de chaque Activity avant setContentView.
     * ThemeManager.initialize() intègre déjà isDark() — cette méthode
     * est conservée pour les Activities qui n'appellent pas initialize().
     */
    public static void applyToThemeManager(Context context) {
        if (context == null) return;
        ThemeManager.getInstance().initialize(context);
    }

    /** Label affiché dans les paramètres. */
    public static String getOptionLabel(Context context) {
        switch (getOption(context)) {
            case OPTION_DARK:  return "Sombre";
            case OPTION_LIGHT: return "Clair";
            default:           return "Automatique";
        }
    }
}
