package com.couplefinance.ui.settings;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;

/**
 * SettingsAppearanceSection — thème, langue et devise.
 */
public class SettingsAppearanceSection {

    private static final String PREF_SETTINGS = "couplefinance_settings";

    private final Activity activity;
    private LinearLayout root;

    public SettingsAppearanceSection(Activity activity) {
        this.activity = activity;
    }

    public View build() {
        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams rootLp = SettingsStyles.matchWrap();
        rootLp.bottomMargin = SettingsStyles.dp(activity, 22);
        root.setLayoutParams(rootLp);

        root.addView(sectionTitle("Apparence"));

        LinearLayout card = card();
        card.addView(darkModeRow());
        card.addView(divider());
        card.addView(row("🌍", "Langue", languageLabel(), () -> SettingsDialogs.showLanguage(activity, this::refresh)));
        card.addView(divider());
        card.addView(row("€", "Devise", currencyLabel(), () -> SettingsDialogs.showCurrency(activity, this::refresh)));
        root.addView(card);

        return root;
    }

    public void setVisible(boolean visible) {
        if (root != null) root.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private View darkModeRow() {
        String currentLabel = com.couplefinance.core.theme.DarkModeManager.getOptionLabel(activity);
        return row("🌙", "Apparence", currentLabel,
                () -> SettingsDialogs.showDarkModeOptions(activity, this::refresh));
    }

    private View row(String iconText, String titleText, String subtitle, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(
                SettingsStyles.dp(activity, 18),
                SettingsStyles.dp(activity, 15),
                SettingsStyles.dp(activity, 18),
                SettingsStyles.dp(activity, 15)
        );
        row.setClickable(true);
        row.setFocusable(true);

        row.addView(icon(iconText));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextColor(ThemeColors.text());
        title.setTextSize(DS.TEXT_BODY);
        title.setTypeface(null, Typeface.BOLD);
        texts.addView(title);

        TextView sub = new TextView(activity);
        sub.setText(subtitle);
        sub.setTextColor(ThemeColors.subtext());
        sub.setTextSize(DS.TEXT_SM);
        LinearLayout.LayoutParams subLp = SettingsStyles.matchWrap();
        subLp.topMargin = SettingsStyles.dp(activity, 3);
        texts.addView(sub, subLp);

        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView chevron = new TextView(activity);
        chevron.setText("›");
        chevron.setTextColor(ThemeColors.muted());
        chevron.setTextSize(24f);
        row.addView(chevron);

        row.setOnClickListener(v -> {
            if (action != null) action.run();
        });

        return row;
    }

    private TextView icon(String text) {
        TextView icon = new TextView(activity);
        icon.setText(text);
        icon.setGravity(Gravity.CENTER);
        icon.setTextSize(17f);
        icon.setBackground(SettingsStyles.secondaryButton());

        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                SettingsStyles.dp(activity, 42),
                SettingsStyles.dp(activity, 42)
        );
        iconLp.rightMargin = SettingsStyles.dp(activity, 14);
        icon.setLayoutParams(iconLp);
        return icon;
    }

    private String languageLabel() {
        SharedPreferences prefs = activity.getSharedPreferences(PREF_SETTINGS, Activity.MODE_PRIVATE);
        String value = prefs.getString("language", "fr");
        return "en".equals(value) ? "English" : "Français";
    }

    private String currencyLabel() {
        SharedPreferences prefs = activity.getSharedPreferences(PREF_SETTINGS, Activity.MODE_PRIVATE);
        String value = prefs.getString("currency", "EUR");
        if ("USD".equals(value)) return "USD ($)";
        if ("GBP".equals(value)) return "GBP (£)";
        return "EUR (€)";
    }

    private TextView sectionTitle(String text) {
        TextView tv = new TextView(activity);
        tv.setText(text.toUpperCase(java.util.Locale.FRANCE));
        tv.setTextColor(ThemeColors.subtext());
        tv.setTextSize(DS.TEXT_XS);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setLetterSpacing(0.08f);

        LinearLayout.LayoutParams lp = SettingsStyles.matchWrap();
        lp.leftMargin = SettingsStyles.dp(activity, 4);
        lp.bottomMargin = SettingsStyles.dp(activity, 10);
        tv.setLayoutParams(lp);
        return tv;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(SettingsStyles.glassCard(activity));
        card.setPadding(0, SettingsStyles.dp(activity, 4), 0, SettingsStyles.dp(activity, 4));
        SettingsStyles.applyCardElevation(card);
        card.setLayoutParams(SettingsStyles.matchWrap());
        return card;
    }

    private View divider() {
        View v = new View(activity);
        v.setBackgroundColor(ThemeColors.divider());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, SettingsStyles.dp(activity, 1)));
        lp.leftMargin = SettingsStyles.dp(activity, 74);
        v.setLayoutParams(lp);
        return v;
    }
    private void refresh() {
        if (root == null) return;
        root.removeAllViews();
        root.addView(sectionTitle("Apparence"));
        LinearLayout card = card();
        card.addView(darkModeRow());
        card.addView(divider());
        card.addView(row("\uD83C\uDF0D", "Langue", languageLabel(), () -> SettingsDialogs.showLanguage(activity, this::refresh)));
        card.addView(divider());
        card.addView(row("\u20AC", "Devise", currencyLabel(), () -> SettingsDialogs.showCurrency(activity, this::refresh)));
        root.addView(card);
    }

}
