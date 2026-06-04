package com.couplefinance.core.theme;

import android.content.Context;
import android.content.SharedPreferences;

public class ThemeStorage {

    private static final String PREFS_NAME = "couplefinance_theme_prefs";

    private static final String KEY_ACTIVE_THEME = "active_theme";
    private static final String KEY_CUSTOM_PRIMARY = "custom_primary";

    private ThemeStorage() {
    }

    public static void saveThemeId(Context context, String themeId) {
        if (context == null || themeId == null || themeId.trim().isEmpty())
            return;

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        prefs.edit()
                .putString(KEY_ACTIVE_THEME, themeId)
                .apply();
    }

    public static String loadThemeId(Context context) {
        if (context == null)
            return "terracotta";

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        return prefs.getString(KEY_ACTIVE_THEME, "terracotta");
    }

    public static void saveCustomPrimary(Context context, int color) {
        if (context == null)
            return;

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        prefs.edit()
                .putInt(KEY_CUSTOM_PRIMARY, color)
                .apply();
    }

    public static int loadCustomPrimary(Context context, int fallback) {
        if (context == null)
            return fallback;

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        return prefs.getInt(KEY_CUSTOM_PRIMARY, fallback);
    }

    public static void clear(Context context) {
        if (context == null)
            return;

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        prefs.edit().clear().apply();
    }
}