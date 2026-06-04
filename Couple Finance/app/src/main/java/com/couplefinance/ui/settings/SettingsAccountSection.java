package com.couplefinance.ui.settings;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.AppToast;
import com.couplefinance.AuthManager;
import com.couplefinance.UserSession;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;

/**
 * SettingsAccountSection — section Compte.
 *
 * Garde les actions sensibles verrouillées pour l'instant afin d'éviter de
 * casser Firebase Auth, mais rend les préférences locales et les dialogs premium.
 */
public class SettingsAccountSection {

    private final Activity activity;
    private LinearLayout root;

    public SettingsAccountSection(Activity activity) {
        this.activity = activity;
    }

    public View build() {
        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams rootLp = SettingsStyles.matchWrap();
        rootLp.bottomMargin = SettingsStyles.dp(activity, 22);
        root.setLayoutParams(rootLp);

        root.addView(sectionTitle("Compte personnel"));

        LinearLayout card = card();
        card.addView(row("👤", "Profil personnel", profileSubtitle(), () -> SettingsDialogs.showProfile(activity, this::refresh)));
        card.addView(divider());
        card.addView(row("🔐", "Sécurité", securitySubtitle(), () -> SettingsDialogs.showSecurity(activity, this::refresh)));
        card.addView(divider());
        card.addView(row("🔔", "Notifications", notificationsSubtitle(), () -> SettingsDialogs.showNotifications(activity, this::refresh)));
        root.addView(card);

        return root;
    }

    public void setVisible(boolean visible) {
        if (root != null) root.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private String profileSubtitle() {
        try {
            String name = UserSession.getInstance().getNameOrFallback();
            if (name != null && !name.trim().isEmpty()) return name;
        } catch (Exception ignored) {
        }

        try {
            String name = AuthManager.getInstance().getDisplayName();
            if (name != null && !name.trim().isEmpty()) return name;
        } catch (Exception ignored) {
        }

        try {
            SharedPreferences prefs = activity.getSharedPreferences("couplefinance_profile", Activity.MODE_PRIVATE);
            String name = prefs.getString("display_name", "");
            if (name != null && !name.trim().isEmpty()) return name;
        } catch (Exception ignored) {
        }

        return "Modifier vos informations";
    }

    private String securitySubtitle() {
        try {
            SharedPreferences prefs = activity.getSharedPreferences("couplefinance_security", Activity.MODE_PRIVATE);
            boolean biometric = prefs.getBoolean("biometric_enabled", false);
            boolean privacy = prefs.getBoolean("privacy_on_start", false);

            if (biometric && privacy) return "Biométrie active · confidentialité au lancement";
            if (biometric) return "Biométrie active";
            if (privacy) return "Confidentialité au lancement";
        } catch (Exception ignored) {
        }

        return "Biométrie et confidentialité";
    }

    private String notificationsSubtitle() {
        try {
            SharedPreferences prefs = activity.getSharedPreferences("couplefinance_notifications", Activity.MODE_PRIVATE);

            int active = 0;
            if (prefs.getBoolean("budget_alerts", true)) active++;
            if (prefs.getBoolean("large_expense_alerts", true)) active++;
            if (prefs.getBoolean("fixed_charge_alerts", true)) active++;
            if (prefs.getBoolean("savings_alerts", true)) active++;
            if (prefs.getBoolean("monthly_summary", true)) active++;
            if (prefs.getBoolean("sync_alerts", true)) active++;

            return active + "/6 alertes actives";
        } catch (Exception ignored) {
        }

        return "Alertes budget, charges et épargne";
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

    private View row(String icon, String title, String subtitle, Runnable action) {
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

        TextView tvIcon = new TextView(activity);
        tvIcon.setText(icon);
        tvIcon.setGravity(Gravity.CENTER);
        tvIcon.setTextSize(17f);
        tvIcon.setBackground(SettingsStyles.secondaryButton());
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                SettingsStyles.dp(activity, 42),
                SettingsStyles.dp(activity, 42)
        );
        iconLp.rightMargin = SettingsStyles.dp(activity, 14);
        row.addView(tvIcon, iconLp);

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(title);
        tvTitle.setTextColor(ThemeColors.text());
        tvTitle.setTextSize(DS.TEXT_BODY);
        tvTitle.setTypeface(null, Typeface.BOLD);
        texts.addView(tvTitle);

        TextView tvSubtitle = new TextView(activity);
        tvSubtitle.setText(subtitle);
        tvSubtitle.setTextColor(ThemeColors.subtext());
        tvSubtitle.setTextSize(DS.TEXT_SM);
        LinearLayout.LayoutParams subLp = SettingsStyles.matchWrap();
        subLp.topMargin = SettingsStyles.dp(activity, 3);
        texts.addView(tvSubtitle, subLp);

        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView chevron = new TextView(activity);
        chevron.setText("›");
        chevron.setTextColor(ThemeColors.muted());
        chevron.setTextSize(24f);
        row.addView(chevron);

        row.setOnClickListener(v -> {
            if (action != null) action.run();
            else AppToast.info(activity, "Action indisponible");
        });

        return row;
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
        root.addView(sectionTitle("Compte personnel"));
        LinearLayout card = card();
        card.addView(row("\uD83D\uDC64", "Profil personnel", profileSubtitle(), () -> SettingsDialogs.showProfile(activity, this::refresh)));
        card.addView(divider());
        card.addView(row("\uD83D\uDD10", "Sécurité", securitySubtitle(), () -> SettingsDialogs.showSecurity(activity, this::refresh)));
        card.addView(divider());
        card.addView(row("\uD83D\uDD14", "Notifications", notificationsSubtitle(), () -> SettingsDialogs.showNotifications(activity, this::refresh)));
        root.addView(card);
    }

}
