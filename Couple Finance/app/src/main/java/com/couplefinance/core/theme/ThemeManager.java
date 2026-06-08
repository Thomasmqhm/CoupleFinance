package com.couplefinance.core.theme;

import android.content.Context;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

public class ThemeManager {

    private static ThemeManager instance;

    private ThemePalette currentTheme;

    private final List<ThemeListener> listeners = new ArrayList<>();

    public interface ThemeListener {
        void onThemeChanged(ThemePalette theme);
    }

    private ThemeManager() {
    }

    public static ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }

        return instance;
    }

    public void initialize(Context context) {
        // Dark mode override: if DarkModeManager says dark, use dark theme
        if (DarkModeManager.isDark(context)) {
            currentTheme = ThemePresets.dark();
        } else {
            String themeId = ThemeStorage.loadThemeId(context);
            // Never load "dark" theme in light mode (safety: reset to terracotta)
            if ("dark".equals(themeId)) themeId = "terracotta";
            currentTheme = getThemeById(context, themeId);
        }
    }

    public void reload(Context context) {
        initialize(context);
        notifyThemeChanged();
    }

    public ThemePalette getTheme() {
        if (currentTheme == null) {
            currentTheme = ThemePresets.terracotta();
        }

        return currentTheme;
    }

    public String getCurrentThemeId() {
        return getTheme().id;
    }

    /** Applique un thème sans le persister dans ThemeStorage. */
    public void applyThemeDirect(String themeId) {
        currentTheme = getThemeById(null, themeId);
        notifyThemeChanged();
    }

    public void applyTheme(Context context, String themeId) {
        currentTheme = getThemeById(context, themeId);

        if (context != null) {
            ThemeStorage.saveThemeId(context, currentTheme.id);
        }

        notifyThemeChanged();
    }

    public void applyThemeByColor(Context context, int primaryColor) {
        ThemePalette custom = ThemePresets.terracotta();

        custom.id = "custom";
        custom.name = "Personnalisé";

        custom.primary = primaryColor;
        custom.primaryDark = darken(primaryColor);
        custom.accent = soften(primaryColor);
        custom.switchActive = primaryColor;

        currentTheme = custom;

        if (context != null) {
            ThemeStorage.saveThemeId(context, "custom");
            ThemeStorage.saveCustomPrimary(context, primaryColor);
        }

        notifyThemeChanged();
    }

    public void registerListener(ThemeListener listener) {
        if (listener == null) {
            return;
        }

        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void unregisterListener(ThemeListener listener) {
        listeners.remove(listener);
    }

    private void notifyThemeChanged() {
        List<ThemeListener> snapshot = new ArrayList<>(listeners);

        for (ThemeListener listener : snapshot) {
            if (listener != null) {
                listener.onThemeChanged(getTheme());
            }
        }
    }

    private ThemePalette getThemeById(Context context, String id) {
        if (id == null || id.trim().isEmpty()) {
            return ThemePresets.terracotta();
        }

        switch (id) {
            case "custom":
                return customFromStorage(context);

            case "dark":
                return ThemePresets.dark();

            case "ocean":
                return ThemePresets.ocean();

            case "sage":
                return ThemePresets.sage();

            case "lavender":
                return ThemePresets.lavender();

            case "rose":
                return ThemePresets.rose();

            case "mint":
                return ThemePresets.mint();

            case "sand":
                return ThemePresets.sand();

            case "terracotta":
            default:
                return ThemePresets.terracotta();
        }
    }

    private ThemePalette customFromStorage(Context context) {
        int primary = ThemeStorage.loadCustomPrimary(
                context,
                Color.parseColor("#C86B4A")
        );

        ThemePalette custom = ThemePresets.terracotta();

        custom.id = "custom";
        custom.name = "Personnalisé";

        custom.primary = primary;
        custom.primaryDark = darken(primary);
        custom.accent = soften(primary);
        custom.switchActive = primary;

        return custom;
    }

    private int soften(int color) {
        int r = (int) (Color.red(color) * 0.38f + 255 * 0.62f);
        int g = (int) (Color.green(color) * 0.38f + 255 * 0.62f);
        int b = (int) (Color.blue(color) * 0.38f + 255 * 0.62f);

        return Color.rgb(r, g, b);
    }

    private int darken(int color) {
        int r = (int) (Color.red(color) * 0.76f);
        int g = (int) (Color.green(color) * 0.76f);
        int b = (int) (Color.blue(color) * 0.76f);

        return Color.rgb(r, g, b);
    }
}