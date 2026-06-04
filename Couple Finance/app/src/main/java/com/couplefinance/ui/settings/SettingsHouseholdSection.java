package com.couplefinance.ui.settings;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.couplefinance.AppToast;
import com.couplefinance.AuthManager;
import com.couplefinance.HouseholdActivity;
import com.couplefinance.LoginActivity;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.theme.ThemeManager;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.components.PremiumInput;
import com.couplefinance.core.ui.dialogs.PremiumDialog;
import com.couplefinance.data.HouseholdManager;
import com.couplefinance.data.JointAccountManager;

public class SettingsHouseholdSection {

    private static final String THEME_PREFS = "couplefinance_theme";
    private static final String KEY_DARK    = "dark_mode";
    private static final String KEY_COMPACT = "compact_mode";

    private final Activity activity;
    private LinearLayout root;

    // ── Couleurs de thème ────────────────────────────────────────────────────
    private final String[] accentHex = {
            "#D88F7A", "#86B89B", "#D8B26A", "#7EA8D6",
            "#B39DDB", "#D9B8A7", "#E29AB0", "#7FC8BE"
    };

    private final String[] accentNames = {
            "Terracotta", "Sauge", "Ocre", "Azur",
            "Lavande", "Beige", "Rose", "Menthe"
    };

    public SettingsHouseholdSection(Activity activity) {
        this.activity = activity;
    }

    public View build() {
        SettingsStyles.syncWithGlobalTheme();

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        root.addView(sectionTitle("Foyer"));
        root.addView(householdCard());

        root.addView(sectionTitle("Invitation"));
        root.addView(inviteCard());

        // ── NOUVEAU : Compte joint ────────────────────────────────────────────
        root.addView(sectionTitle("Compte joint"));
        root.addView(jointAccountCard());

        root.addView(sectionTitle("Apparence"));
        root.addView(appearanceCard());

        root.addView(sectionTitle("Compte"));
        root.addView(accountCard());

        return root;
    }

    public void setVisible(boolean visible) {
        if (root != null) root.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    // ── Household card ────────────────────────────────────────────────────────

    private View householdCard() {
        LinearLayout card = premiumCard();
        card.addView(infoRow("Nom du foyer", "CoupleFinance"));
        card.addView(infoRow("Description", "Gestion financière premium"));
        card.addView(infoRow("Code foyer", getInviteCode()));
        return card;
    }

    // ── Invite card ───────────────────────────────────────────────────────────

    private View inviteCard() {
        LinearLayout card = premiumCard();
        card.addView(title("Inviter un membre"));
        card.addView(subtitle("Partagez ce code avec votre partenaire."));

        String code = getInviteCode();

        TextView codeView = new TextView(activity);
        codeView.setText(code);
        codeView.setTextColor(ThemeColors.text());
        codeView.setTextSize(30f);
        codeView.setTypeface(Typeface.DEFAULT_BOLD);
        codeView.setGravity(Gravity.CENTER);
        codeView.setLetterSpacing(0.12f);
        codeView.setPadding(0, SettingsStyles.dp(activity, 22), 0, SettingsStyles.dp(activity, 22));

        GradientDrawable codeBg = new GradientDrawable();
        codeBg.setColor(ThemeColors.backgroundSecondary());
        codeBg.setCornerRadius(SettingsStyles.dp(activity, 24));
        codeBg.setStroke(SettingsStyles.dp(activity, 1), ThemeColors.border());
        codeView.setBackground(codeBg);

        LinearLayout.LayoutParams cp = SettingsStyles.matchWrap();
        cp.topMargin = SettingsStyles.dp(activity, 20);
        card.addView(codeView, cp);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams ap = SettingsStyles.matchWrap();
        ap.topMargin = SettingsStyles.dp(activity, 16);
        card.addView(actions, ap);

        TextView copy  = primaryButton("Copier");
        TextView share = secondaryButton("Partager");

        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, SettingsStyles.dp(activity, 54), 1f);
        lp1.rightMargin = SettingsStyles.dp(activity, 10);
        actions.addView(copy, lp1);
        actions.addView(share, new LinearLayout.LayoutParams(0, SettingsStyles.dp(activity, 54), 1f));

        copy.setOnClickListener(v -> copyInviteCode(code));
        share.setOnClickListener(v -> shareInviteCode(code));

        return card;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COMPTE JOINT — Card principale
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Card de gestion du compte joint optionnel.
     *
     * Structure :
     *   🏦 Compte joint   [Activé / Désactivé]
     *   ─────────────────────────────────────────
     *   Nom du compte :  [Compte joint           ]
     *   Solde de départ : 2 000,00 €  [Modifier]
     *   ─────────────────────────────────────────
     *   ℹ Le compte joint apparaît comme un troisième membre dans le dashboard.
     */
    private View jointAccountCard() {
        LinearLayout card = premiumCard();

        JointAccountManager jm = JointAccountManager.getInstance();
        boolean enabled = jm.isEnabledLocal();
        String  name    = jm.getNameLocal();
        double  balance = jm.getBalanceLocal();

        // ── En-tête avec toggle ────────────────────────────────────────────
        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        // Icône + titre
        LinearLayout titleCol = new LinearLayout(activity);
        titleCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvTitle = new TextView(activity);
        tvTitle.setText("🏦  Compte joint");
        tvTitle.setTextColor(ThemeColors.text());
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        titleCol.addView(tvTitle);

        TextView tvSub = new TextView(activity);
        tvSub.setText("Troisième membre optionnel partagé par le couple");
        tvSub.setTextColor(ThemeColors.subtext());
        tvSub.setTextSize(12f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = DS.dp(activity, 3);
        titleCol.addView(tvSub, subLp);

        headerRow.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1f));

        // Badge statut
        TextView statusBadge = new TextView(activity);
        refreshStatusBadge(statusBadge, enabled);
        headerRow.addView(statusBadge);

        card.addView(headerRow);

        // ── Zone de configuration (visible seulement si activé) ────────────
        LinearLayout configZone = new LinearLayout(activity);
        configZone.setOrientation(LinearLayout.VERTICAL);
        configZone.setVisibility(enabled ? View.VISIBLE : View.GONE);

        // Divider
        View div1 = divider();
        configZone.addView(div1);

        // Champ nom
        configZone.addView(settingLabel("Nom du compte"));

        EditText etName = PremiumInput.normal(activity, "Ex : Compte BNPP commun");
        etName.setText(name);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(-1, DS.dp(activity, DS.INPUT_HEIGHT));
        nameLp.topMargin = DS.dp(activity, 6);
        configZone.addView(etName, nameLp);

        // Solde de début de mois
        configZone.addView(settingLabel("Solde de début de mois"));

        LinearLayout balRow = new LinearLayout(activity);
        balRow.setOrientation(LinearLayout.HORIZONTAL);
        balRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams balRowLp = new LinearLayout.LayoutParams(-1, -2);
        balRowLp.topMargin = DS.dp(activity, 6);
        balRow.setLayoutParams(balRowLp);

        TextView tvBalance = new TextView(activity);
        tvBalance.setText(String.format(java.util.Locale.FRANCE, "%.2f €", balance));
        tvBalance.setTextColor(ThemeColors.text());
        tvBalance.setTextSize(16f);
        tvBalance.setTypeface(null, Typeface.BOLD);
        balRow.addView(tvBalance, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView btnEditBalance = secondaryButton("Modifier");
        btnEditBalance.setOnClickListener(v -> showJointBalanceDialog(tvBalance));
        balRow.addView(btnEditBalance, new LinearLayout.LayoutParams(-2, DS.dp(activity, 44)));

        configZone.addView(balRow);

        // Bouton Enregistrer le nom
        TextView btnSaveName = primaryButton("Enregistrer");
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-1, DS.dp(activity, 50));
        saveLp.topMargin = DS.dp(activity, 16);
        btnSaveName.setOnClickListener(v -> {
            String newName = etName.getText().toString().trim();
            if (newName.isEmpty()) newName = JointAccountManager.DEFAULT_NAME;
            final String finalName = newName;
            btnSaveName.setEnabled(false);
            btnSaveName.setAlpha(0.6f);
            jm.setEnabled(true, finalName, new JointAccountManager.Callback() {
                public void onSuccess() {
                    activity.runOnUiThread(() -> {
                        btnSaveName.setEnabled(true);
                        btnSaveName.setAlpha(1f);
                        AppToast.success(activity, "Compte joint mis à jour");
                    });
                }
                public void onError(String e) {
                    activity.runOnUiThread(() -> {
                        btnSaveName.setEnabled(true);
                        btnSaveName.setAlpha(1f);
                        AppToast.info(activity, "Enregistré localement");
                    });
                }
            });
        });
        configZone.addView(btnSaveName, saveLp);

        card.addView(configZone);

        // ── Info contextuelle ──────────────────────────────────────────────
        View div2 = divider();
        card.addView(div2);

        TextView info = new TextView(activity);
        info.setText(enabled
                ? "✓ Le compte joint apparaît dans le dashboard sous les cards individuelles."
                : "ℹ Activez le compte joint pour suivre un budget commun en parallèle des comptes individuels.");
        info.setTextColor(enabled ? ThemeColors.success() : ThemeColors.subtext());
        info.setTextSize(12f);
        info.setLineSpacing(3f, 1f);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-1, -2);
        infoLp.topMargin = DS.dp(activity, 10);
        card.addView(info, infoLp);

        // ── Bouton Activer / Désactiver ────────────────────────────────────
        TextView btnToggle = enabled ? dangerButton("Désactiver le compte joint")
                                     : primaryButton("Activer le compte joint");
        LinearLayout.LayoutParams toggleLp = new LinearLayout.LayoutParams(-1, DS.dp(activity, 52));
        toggleLp.topMargin = DS.dp(activity, 14);

        btnToggle.setOnClickListener(v -> {
            boolean newEnabled = !jm.isEnabledLocal();
            String  currentName = jm.getNameLocal();
            btnToggle.setEnabled(false);
            btnToggle.setAlpha(0.6f);

            jm.setEnabled(newEnabled, currentName, new JointAccountManager.Callback() {
                public void onSuccess() {
                    activity.runOnUiThread(() -> {
                        AppToast.success(activity,
                                newEnabled ? "Compte joint activé" : "Compte joint désactivé");
                        // Rebuild de la section pour refléter le nouvel état
                        rebuildJointCard(card, configZone, statusBadge, info, btnToggle, newEnabled);
                    });
                }
                public void onError(String e) {
                    activity.runOnUiThread(() -> {
                        btnToggle.setEnabled(true);
                        btnToggle.setAlpha(1f);
                        AppToast.info(activity, "Changement enregistré localement");
                        rebuildJointCard(card, configZone, statusBadge, info, btnToggle, newEnabled);
                    });
                }
            });
        });

        card.addView(btnToggle, toggleLp);

        return card;
    }

    /**
     * Met à jour dynamiquement la card compte joint après toggle sans rebuild complet.
     */
    private void rebuildJointCard(LinearLayout card, LinearLayout configZone,
                                   TextView statusBadge, TextView info,
                                   TextView btnToggle, boolean nowEnabled) {
        // Mettre à jour le badge statut
        refreshStatusBadge(statusBadge, nowEnabled);

        // Afficher/masquer la zone de config
        configZone.setVisibility(nowEnabled ? View.VISIBLE : View.GONE);

        // Mettre à jour le texte d'info
        info.setText(nowEnabled
                ? "✓ Le compte joint apparaît dans le dashboard sous les cards individuelles."
                : "ℹ Activez le compte joint pour suivre un budget commun en parallèle des comptes individuels.");
        info.setTextColor(nowEnabled ? ThemeColors.success() : ThemeColors.subtext());

        // Mettre à jour le bouton toggle
        btnToggle.setEnabled(true);
        btnToggle.setAlpha(1f);
        btnToggle.setText(nowEnabled ? "Désactiver le compte joint" : "Activer le compte joint");

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(nowEnabled ? ThemeColors.danger() : ThemeColors.primary());
        bg.setCornerRadius(SettingsStyles.dp(activity, 20));
        btnToggle.setBackground(bg);
        btnToggle.setTextColor(Color.WHITE);
    }

    private void refreshStatusBadge(TextView badge, boolean enabled) {
        badge.setText(enabled ? "Activé" : "Désactivé");
        badge.setTextColor(enabled ? ThemeColors.success() : ThemeColors.subtext());
        badge.setTextSize(12f);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setPadding(
                DS.dp(activity, 12), DS.dp(activity, 6),
                DS.dp(activity, 12), DS.dp(activity, 6)
        );

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(enabled
                ? ThemeColors.withAlpha(ThemeColors.success(), 18)
                : ThemeColors.backgroundSecondary());
        badgeBg.setCornerRadius(DS.dp(activity, 20));
        badgeBg.setStroke(DS.dp(activity, 1), enabled ? ThemeColors.withAlpha(ThemeColors.success(), 60) : ThemeColors.border());
        badge.setBackground(badgeBg);
    }

    /** Dialog de saisie du solde du compte joint. */
    private void showJointBalanceDialog(TextView tvBalance) {
        EditText input = PremiumInput.numeric(activity, "Ex : 2000");

        PremiumDialog.builder(activity)
                .icon("🏦")
                .title("Solde du compte joint")
                .subtitle("Montant disponible sur le compte joint en début de mois.")
                .content(input)
                .primary("Enregistrer", () -> {
                    String val = input.getText().toString().trim().replace(",", ".");
                    if (val.isEmpty()) return;
                    try {
                        double amount = Double.parseDouble(val);
                        JointAccountManager.getInstance().saveBalance(amount, new JointAccountManager.Callback() {
                            public void onSuccess() {
                                activity.runOnUiThread(() -> {
                                    tvBalance.setText(String.format(java.util.Locale.FRANCE, "%.2f €", amount));
                                    AppToast.success(activity, "Solde enregistré");
                                });
                            }
                            public void onError(String e) {
                                activity.runOnUiThread(() -> AppToast.info(activity, "Enregistré localement"));
                            }
                        });
                    } catch (Exception ignored) {
                        AppToast.error(activity, "Montant invalide");
                    }
                })
                .show();
    }

    // ── Appearance card ───────────────────────────────────────────────────────

    private View appearanceCard() {
        LinearLayout card = premiumCard();
        card.addView(title("Thème global"));
        card.addView(subtitle("Le thème est appliqué au Dashboard, Sidebar, modals et widgets."));

        HorizontalScrollView hsv = new HorizontalScrollView(activity);
        hsv.setHorizontalScrollBarEnabled(false);

        LinearLayout colors = new LinearLayout(activity);
        colors.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(colors);

        String selected = colorToHex(ThemeColors.primary());

        for (int i = 0; i < accentHex.length; i++) {
            String hex = accentHex[i];
            colors.addView(colorItem(hex, accentNames[i], selected.equalsIgnoreCase(hex)));
        }

        LinearLayout.LayoutParams hsvLp = SettingsStyles.matchWrap();
        hsvLp.topMargin = SettingsStyles.dp(activity, 18);
        card.addView(hsv, hsvLp);

        card.addView(themeSwitchRow("Mode sombre", KEY_DARK));
        card.addView(themeSwitchRow("Mode compact", KEY_COMPACT));

        return card;
    }

    private View colorItem(String hex, String label, boolean selected) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(
                SettingsStyles.dp(activity, 74), LinearLayout.LayoutParams.WRAP_CONTENT);
        boxLp.rightMargin = SettingsStyles.dp(activity, 12);
        box.setLayoutParams(boxLp);

        View circle = new View(activity);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor(hex));
        if (selected) bg.setStroke(SettingsStyles.dp(activity, 4), ThemeColors.text());
        circle.setBackground(bg);

        box.addView(circle, new LinearLayout.LayoutParams(
                SettingsStyles.dp(activity, 52), SettingsStyles.dp(activity, 52)));

        TextView tv = new TextView(activity);
        tv.setText(label);
        tv.setTextSize(11f);
        tv.setTextColor(ThemeColors.subtext());
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams tp = SettingsStyles.wrapWrap();
        tp.topMargin = SettingsStyles.dp(activity, 8);
        box.addView(tv, tp);

        box.setOnClickListener(v -> {
            int color = Color.parseColor(hex);
            ThemeManager.getInstance().applyThemeByColor(activity, color);
            AppToast.success(activity, "Thème appliqué");
            activity.recreate();
        });

        return box;
    }

    // ── Account card ──────────────────────────────────────────────────────────

    private View accountCard() {
        LinearLayout card = premiumCard();

        TextView leave  = secondaryButton("Quitter le foyer");
        card.addView(leave);

        TextView logout = dangerButton("Déconnexion");
        LinearLayout.LayoutParams lp = SettingsStyles.matchWrap();
        lp.topMargin = SettingsStyles.dp(activity, 12);
        card.addView(logout, lp);

        leave.setOnClickListener(v -> confirmLeaveHousehold());
        logout.setOnClickListener(v -> confirmLogout());

        return card;
    }

    // ── Helpers UI ────────────────────────────────────────────────────────────

    private View sectionTitle(String text) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextColor(ThemeColors.text());
        tv.setTextSize(22f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(SettingsStyles.dp(activity, 4), SettingsStyles.dp(activity, 4),
                0, SettingsStyles.dp(activity, 14));
        return tv;
    }

    private View infoRow(String titleText, String value) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, SettingsStyles.dp(activity, 12), 0, SettingsStyles.dp(activity, 12));

        TextView t = new TextView(activity);
        t.setText(titleText);
        t.setTextColor(ThemeColors.subtext());
        t.setTextSize(13f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(t);

        TextView v = new TextView(activity);
        v.setText(value);
        v.setTextColor(ThemeColors.text());
        v.setTextSize(17f);
        LinearLayout.LayoutParams vp = SettingsStyles.wrapWrap();
        vp.topMargin = SettingsStyles.dp(activity, 4);
        row.addView(v, vp);

        return row;
    }

    private TextView settingLabel(String text) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextColor(ThemeColors.subtext());
        tv.setTextSize(12f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setLetterSpacing(0.05f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = DS.dp(activity, 14);
        tv.setLayoutParams(lp);
        return tv;
    }

    private View divider() {
        View v = new View(activity);
        v.setBackgroundColor(ThemeColors.divider());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, DS.dp(activity, 1));
        lp.topMargin    = DS.dp(activity, 14);
        lp.bottomMargin = DS.dp(activity, 4);
        v.setLayoutParams(lp);
        return v;
    }

    private View themeSwitchRow(String titleText, String key) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, SettingsStyles.dp(activity, 16), 0, SettingsStyles.dp(activity, 6));

        TextView tv = new TextView(activity);
        tv.setText(titleText);
        tv.setTextColor(ThemeColors.text());
        tv.setTextSize(15f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));

        Switch sw = new Switch(activity);
        row.addView(sw);

        return row;
    }

    private LinearLayout premiumCard() {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                SettingsStyles.dp(activity, 22), SettingsStyles.dp(activity, 22),
                SettingsStyles.dp(activity, 22), SettingsStyles.dp(activity, 22)
        );

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ThemeColors.card());
        bg.setCornerRadius(SettingsStyles.dp(activity, 28));
        bg.setStroke(SettingsStyles.dp(activity, 1), ThemeColors.border());
        card.setBackground(bg);
        card.setElevation(SettingsStyles.dp(activity, 4));

        LinearLayout.LayoutParams lp = SettingsStyles.matchWrap();
        lp.bottomMargin = SettingsStyles.dp(activity, 22);
        card.setLayoutParams(lp);

        return card;
    }

    private TextView primaryButton(String text) {
        TextView btn = baseButton(text);
        btn.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ThemeColors.primary());
        bg.setCornerRadius(SettingsStyles.dp(activity, 20));
        btn.setBackground(bg);
        return btn;
    }

    private TextView secondaryButton(String text) {
        TextView btn = baseButton(text);
        btn.setTextColor(ThemeColors.text());
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ThemeColors.backgroundSecondary());
        bg.setCornerRadius(SettingsStyles.dp(activity, 20));
        bg.setStroke(SettingsStyles.dp(activity, 1), ThemeColors.border());
        btn.setBackground(bg);
        return btn;
    }

    private TextView dangerButton(String text) {
        TextView btn = baseButton(text);
        btn.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ThemeColors.danger());
        bg.setCornerRadius(SettingsStyles.dp(activity, 20));
        btn.setBackground(bg);
        return btn;
    }

    private TextView baseButton(String text) {
        TextView btn = new TextView(activity);
        btn.setText(text);
        btn.setGravity(Gravity.CENTER);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextSize(14f);
        btn.setPadding(
                SettingsStyles.dp(activity, 18), SettingsStyles.dp(activity, 16),
                SettingsStyles.dp(activity, 18), SettingsStyles.dp(activity, 16)
        );
        return btn;
    }

    private TextView title(String text) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextSize(20f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(ThemeColors.text());
        return tv;
    }

    private TextView subtitle(String text) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextSize(14f);
        tv.setTextColor(ThemeColors.subtext());
        LinearLayout.LayoutParams lp = SettingsStyles.matchWrap();
        lp.topMargin = SettingsStyles.dp(activity, 6);
        tv.setLayoutParams(lp);
        return tv;
    }

    // ── Dialogs / Actions ─────────────────────────────────────────────────────

    private void confirmLeaveHousehold() {
        PremiumDialog.builder(activity)
                .title("Quitter le foyer ?")
                .subtitle("Vous devrez rejoindre ou créer un nouveau foyer.")
                .primary("Quitter", this::leaveHousehold)
                .secondary("Annuler", null)
                .show();
    }

    private void confirmLogout() {
        PremiumDialog.builder(activity)
                .title("Déconnexion")
                .subtitle("Votre foyer restera enregistré.")
                .primary("Déconnexion", this::logout)
                .secondary("Annuler", null)
                .show();
    }

    private void leaveHousehold() {
        try {
            HouseholdManager.getInstance().clearHousehold();
            Intent intent = new Intent(activity, HouseholdActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(intent);
            activity.finish();
        } catch (Exception e) {
            AppToast.error(activity, "Impossible de quitter le foyer");
        }
    }

    private void logout() {
        try {
            AuthManager.getInstance().logout();
            Intent intent = new Intent(activity, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(intent);
            activity.finish();
        } catch (Exception e) {
            AppToast.error(activity, "Déconnexion impossible");
        }
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    private String getInviteCode() {
        String id = HouseholdManager.getInstance().getHouseholdId();
        return (id != null && !id.isEmpty()) ? id : "—";
    }

    private void copyInviteCode(String code) {
        ClipboardManager clipboard = (ClipboardManager)
                activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Code foyer", code));
            AppToast.success(activity, "Code copié !");
        }
    }

    private void shareInviteCode(String code) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT,
                    "Rejoins mon foyer CoupleFinance avec le code : " + code);
            activity.startActivity(Intent.createChooser(intent, "Partager le code foyer"));
        } catch (Exception e) {
            AppToast.error(activity, "Partage impossible");
        }
    }

    private String colorToHex(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }
}
