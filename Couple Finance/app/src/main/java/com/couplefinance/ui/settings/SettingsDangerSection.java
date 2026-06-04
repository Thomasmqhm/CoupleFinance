package com.couplefinance.ui.settings;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;

/**
 * Section Danger des paramètres.
 *
 * Elle est volontairement séparée de SettingsView pour éviter que les actions
 * sensibles soient mélangées avec le rendu général.
 */
public final class SettingsDangerSection {

    private SettingsDangerSection() {
    }

    public static View build(Activity activity) {
        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText("DANGER");
        title.setTextColor(ThemeColors.subtext());
        title.setTextSize(DS.TEXT_XS);
        title.setTypeface(null, Typeface.BOLD);
        title.setLetterSpacing(0.08f);

        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.leftMargin = DS.dp(activity, 20);
        titleLp.topMargin = DS.dp(activity, 4);
        titleLp.bottomMargin = DS.dp(activity, 8);
        wrapper.addView(title, titleLp);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBg(activity));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.leftMargin = DS.dp(activity, 16);
        cardLp.rightMargin = DS.dp(activity, 16);
        cardLp.bottomMargin = DS.dp(activity, 28);
        wrapper.addView(card, cardLp);

        card.addView(row(
                activity,
                "Supprimer le compte",
                "Confirmation forte requise",
                true,
                () -> SettingsDialogs.confirmDeleteAccount(activity)
        ));

        View divider = new View(activity);
        divider.setBackgroundColor(ThemeColors.divider());

        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, DS.dp(activity, 1));
        divLp.leftMargin = DS.dp(activity, 56);
        card.addView(divider, divLp);

        card.addView(row(
                activity,
                "Déconnexion",
                "Quitter l'application proprement",
                false,
                () -> SettingsDialogs.confirmLogout(activity)
        ));

        return wrapper;
    }

    private static View row(Activity activity,
                            String title,
                            String subtitle,
                            boolean danger,
                            Runnable action) {

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(
                DS.dp(activity, 16),
                DS.dp(activity, 14),
                DS.dp(activity, 16),
                DS.dp(activity, 14)
        );
        row.setClickable(true);
        row.setFocusable(true);

        TextView icon = new TextView(activity);
        icon.setGravity(Gravity.CENTER);
        icon.setText(danger ? "!" : "↩");
        icon.setTextSize(16f);
        icon.setTypeface(null, Typeface.BOLD);
        icon.setTextColor(danger ? ThemeColors.danger() : ThemeColors.primary());
        icon.setBackground(iconBg(activity, danger));

        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                DS.dp(activity, 34),
                DS.dp(activity, 34)
        );
        iconLp.rightMargin = DS.dp(activity, 14);
        row.addView(icon, iconLp);

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(title);
        tvTitle.setTextColor(danger ? ThemeColors.danger() : ThemeColors.text());
        tvTitle.setTextSize(DS.TEXT_BODY);
        tvTitle.setTypeface(null, Typeface.BOLD);
        texts.addView(tvTitle);

        TextView tvSubtitle = new TextView(activity);
        tvSubtitle.setText(subtitle);
        tvSubtitle.setTextColor(ThemeColors.subtext());
        tvSubtitle.setTextSize(DS.TEXT_XS);

        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = DS.dp(activity, 2);
        texts.addView(tvSubtitle, subLp);

        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView chevron = new TextView(activity);
        chevron.setText("›");
        chevron.setTextColor(ThemeColors.muted());
        chevron.setTextSize(20f);
        row.addView(chevron);

        row.setOnClickListener(v -> {
            if (action != null) action.run();
        });

        return row;
    }

    private static GradientDrawable cardBg(Activity activity) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ThemeColors.card());
        bg.setCornerRadius(DS.dp(activity, DS.R_LG));
        bg.setStroke(DS.dp(activity, 1), ThemeColors.border());
        return bg;
    }

    private static GradientDrawable iconBg(Activity activity, boolean danger) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(danger ? ThemeColors.dangerSoft() : ThemeColors.primarySoft());
        bg.setCornerRadius(DS.dp(activity, 10));
        return bg;
    }
}
