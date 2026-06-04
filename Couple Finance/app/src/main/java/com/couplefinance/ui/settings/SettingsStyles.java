package com.couplefinance.ui.settings;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.theme.ThemeManager;

/**
 * SettingsStyles
 *
 * Passerelle de style pour l'écran Paramètres.
 * Objectif visuel : page premium type iOS / Replit screenshot.
 *
 * Les anciens champs et helpers sont conservés pour compatibilité avec les
 * autres fichiers Settings déjà présents dans le projet.
 */
public class SettingsStyles {

    public static final int BG = Color.parseColor("#F7EFE8");
    public static final int CARD = Color.WHITE;
    public static final int TEXT = Color.parseColor("#241B17");
    public static final int SUBTEXT = Color.parseColor("#9B8A80");
    public static final int BORDER = Color.parseColor("#E8DDD5");
    public static final int SUCCESS = Color.parseColor("#3D9B6D");

    public static int PRIMARY = Color.parseColor("#C7654D");
    public static int PRIMARY_LIGHT = Color.parseColor("#F3E2DC");

    public static void syncWithGlobalTheme() {
        try {
            PRIMARY = ThemeColors.primary();
            PRIMARY_LIGHT = ThemeColors.primarySoft();
        } catch (Exception e) {
            PRIMARY = Color.parseColor("#C7654D");
            PRIMARY_LIGHT = Color.parseColor("#F3E2DC");
        }
    }

    public static void applyTheme(Context context, String hex) {
        try {
            PRIMARY = Color.parseColor(hex);
            PRIMARY_LIGHT = soften(PRIMARY);
            ThemeManager.getInstance().applyThemeByColor(context, PRIMARY);
        } catch (Exception ignored) {
            syncWithGlobalTheme();
        }
    }

    public static int bg() {
        try { return ThemeColors.background(); } catch (Exception e) { return BG; }
    }

    public static int cardColor() {
        try { return ThemeColors.card(); } catch (Exception e) { return CARD; }
    }

    public static int text() {
        try { return ThemeColors.text(); } catch (Exception e) { return TEXT; }
    }

    public static int subtext() {
        try { return ThemeColors.subtext(); } catch (Exception e) { return SUBTEXT; }
    }

    public static int border() {
        try { return ThemeColors.border(); } catch (Exception e) { return BORDER; }
    }

    public static int success() {
        try { return ThemeColors.success(); } catch (Exception e) { return SUCCESS; }
    }

    public static int primary() {
        try { return ThemeColors.primary(); } catch (Exception e) { return PRIMARY; }
    }

    public static int primaryLight() {
        try { return ThemeColors.primarySoft(); } catch (Exception e) { return PRIMARY_LIGHT; }
    }

    public static int danger() {
        try { return ThemeColors.danger(); } catch (Exception e) { return Color.parseColor("#C85D67"); }
    }

    public static int warning() {
        try { return ThemeColors.warning(); } catch (Exception e) { return Color.parseColor("#B97725"); }
    }

    public static int divider() {
        try { return ThemeColors.divider(); } catch (Exception e) { return Color.parseColor("#EEE4DD"); }
    }

    public static int muted() {
        try { return ThemeColors.muted(); } catch (Exception e) { return Color.parseColor("#B0A29A"); }
    }

    private static int soften(int color) {
        int r = (int) (Color.red(color) * 0.20f + 255 * 0.80f);
        int g = (int) (Color.green(color) * 0.20f + 255 * 0.80f);
        int b = (int) (Color.blue(color) * 0.20f + 255 * 0.80f);
        return Color.rgb(r, g, b);
    }

    public static int dp(Context c, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                c.getResources().getDisplayMetrics()
        );
    }

    public static int sp(Context c, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                value,
                c.getResources().getDisplayMetrics()
        );
    }

    public static GradientDrawable card() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(cardColor());
        g.setCornerRadius(34);
        g.setStroke(2, border());
        return g;
    }

    public static GradientDrawable glassCard(Context context) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(context, 22));
        g.setStroke(dp(context, 1), Color.parseColor("#EADFD7"));
        return g;
    }

    public static GradientDrawable sectionCard(Context context) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(context, 24));
        g.setStroke(dp(context, 1), Color.parseColor("#EADFD7"));
        return g;
    }

    public static GradientDrawable hero(Context context) {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {
                        primary(),
                        Color.parseColor("#D07159"),
                        Color.parseColor("#C96750")
                }
        );
        g.setCornerRadius(dp(context, 28));
        return g;
    }

    public static GradientDrawable hero() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(primary());
        g.setCornerRadius(40);
        return g;
    }

    public static GradientDrawable statCard() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(primaryLight());
        g.setCornerRadius(26);
        return g;
    }

    public static GradientDrawable iconBubble(Context context) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor("#F5E5E1"));
        g.setCornerRadius(dp(context, 14));
        return g;
    }

    public static GradientDrawable menuBubble(Context context) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor("#F4E8E2"));
        g.setCornerRadius(dp(context, 16));
        return g;
    }

    public static GradientDrawable secondaryButton() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor("#F5E5E1"));
        g.setCornerRadius(20);
        g.setStroke(1, Color.parseColor("#F0D9D2"));
        return g;
    }

    public static GradientDrawable primaryButton() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(primary());
        g.setCornerRadius(20);
        return g;
    }

    public static GradientDrawable dangerButton() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(danger());
        g.setCornerRadius(20);
        return g;
    }

    public static void applyCardElevation(View v) {
        if (v != null) v.setElevation(3f);
    }

    public static void title(TextView tv) {
        if (tv == null) return;
        tv.setTextColor(text());
        tv.setTextSize(30);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
    }

    public static void subtitle(TextView tv) {
        if (tv == null) return;
        tv.setTextColor(subtext());
        tv.setTextSize(16);
    }

    public static void section(TextView tv) {
        if (tv == null) return;
        tv.setTextColor(text());
        tv.setTextSize(18);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
    }

    public static void cardTitle(TextView tv) {
        if (tv == null) return;
        tv.setTextColor(text());
        tv.setTextSize(17);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
    }

    public static void cardSubtitle(TextView tv) {
        if (tv == null) return;
        tv.setTextColor(subtext());
        tv.setTextSize(14);
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    public static LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(-2, -2);
    }

    public static void statValue(TextView tv) {
        if (tv == null) return;
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(18);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
    }

    public static void statLabel(TextView tv) {
        if (tv == null) return;
        tv.setTextColor(Color.parseColor("#F2D5CC"));
        tv.setTextSize(13);
    }

    public static GradientDrawable colorCircle(String hex, boolean selected) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);

        int color;
        try { color = Color.parseColor(hex); }
        catch (Exception e) { color = primary(); }

        g.setColor(color);
        if (selected) g.setStroke(4, text());
        return g;
    }
}
