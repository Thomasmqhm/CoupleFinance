package com.couplefinance.ui.settings;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.AppToast;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * SettingsDataSection — export, import, synchronisation et zone danger.
 *
 * Étape Données :
 * - export CSV transactions
 * - export résumé paramètres
 * - import redirigé vers Transactions pour garder le pipeline PDF existant
 * - synchronisation réelle des Settings + cache
 * - dernière synchro persistée
 */
public class SettingsDataSection {

    private static final String PREFS = "couplefinance_settings";
    private static final String KEY_LAST_SYNC = "last_sync_label";
    private static final String KEY_LAST_SYNC_TS = "last_sync_ts";

    private final Activity activity;
    private LinearLayout root;
    private TextView syncSubtitle;

    public SettingsDataSection(Activity activity) {
        this.activity = activity;
    }

    public View build() {
        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams rootLp = SettingsStyles.matchWrap();
        rootLp.bottomMargin = SettingsStyles.dp(activity, 22);
        root.setLayoutParams(rootLp);

        root.addView(sectionTitle("Données"));

        LinearLayout dataCard = card();
        dataCard.addView(row("↓", "Exporter les données", "Transactions CSV ou résumé Paramètres", () -> SettingsDialogs.showExport(activity)));
        dataCard.addView(divider());
        dataCard.addView(row("↑", "Importer", "Ouvrir l’import depuis Transactions", () -> SettingsDialogs.showImport(activity)));
        dataCard.addView(divider());
        dataCard.addView(syncRow());
        root.addView(dataCard);

        root.addView(space(18));
        root.addView(sectionTitle("Danger"));

        LinearLayout dangerCard = card();
        dangerCard.addView(dangerRow("⚠", "Supprimer le compte", "Action irréversible", () -> SettingsDialogs.confirmDeleteAccount(activity)));
        dangerCard.addView(divider());
        dangerCard.addView(dangerRow("🚪", "Déconnexion", "Quitter cette session", () -> SettingsDialogs.confirmLogout(activity)));
        root.addView(dangerCard);

        return root;
    }

    public void setVisible(boolean visible) {
        if (root != null) root.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private View syncRow() {
        View row = baseRow("↻", "Synchronisation", getSyncSubtitle(), false, () ->
                SettingsDialogs.showSync(activity, this::syncNow)
        );

        try {
            LinearLayout line = (LinearLayout) row;
            LinearLayout texts = (LinearLayout) line.getChildAt(1);
            syncSubtitle = (TextView) texts.getChildAt(1);
        } catch (Exception ignored) {
        }

        return row;
    }

    private void syncNow() {
        new SettingsRepository(activity).load(new SettingsRepository.LoadCallback() {
            public void onLoaded(SettingsModels.State state) {
                activity.runOnUiThread(() -> {
                    saveLastSync();
                    if (syncSubtitle != null) syncSubtitle.setText(getSyncSubtitle());
                    AppToast.success(activity, "Synchronisé");
                });
            }

            public void onError(String error) {
                activity.runOnUiThread(() -> AppToast.error(activity, "Erreur de synchronisation"));
            }
        });
    }

    private void saveLastSync() {
        long now = System.currentTimeMillis();
        String label = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
                .format(new Date(now));

        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_SYNC_TS, now)
                .putString(KEY_LAST_SYNC, label)
                .apply();
    }

    private String getSyncSubtitle() {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        String last = prefs.getString(KEY_LAST_SYNC, "Jamais");
        return "Dernière synchro : " + last;
    }

    private View row(String iconText, String titleText, String subtitle, Runnable action) {
        return baseRow(iconText, titleText, subtitle, false, action);
    }

    private View dangerRow(String iconText, String titleText, String subtitle, Runnable action) {
        return baseRow(iconText, titleText, subtitle, true, action);
    }

    private View baseRow(String iconText, String titleText, String subtitle, boolean danger, Runnable action) {
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

        TextView icon = new TextView(activity);
        icon.setText(iconText);
        icon.setGravity(Gravity.CENTER);
        icon.setTextSize(17f);
        icon.setTextColor(danger ? ThemeColors.danger() : ThemeColors.text());
        icon.setBackground(SettingsStyles.secondaryButton());
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                SettingsStyles.dp(activity, 42),
                SettingsStyles.dp(activity, 42)
        );
        iconLp.rightMargin = SettingsStyles.dp(activity, 14);
        row.addView(icon, iconLp);

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextColor(danger ? ThemeColors.danger() : ThemeColors.text());
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

    private TextView sectionTitle(String text) {
        TextView tv = new TextView(activity);
        tv.setText(text.toUpperCase(Locale.FRANCE));
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

    private View space(int heightDp) {
        View v = new View(activity);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, SettingsStyles.dp(activity, heightDp)));
        return v;
    }
}
