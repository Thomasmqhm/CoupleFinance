package com.couplefinance.ui.settings;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.data.JointAccountManager;

/**
 * SettingsJointAccountSection — réglages du Compte Joint.
 *
 * Étape 4 : section dédiée au seul élément qui diffère d'une personne réelle.
 * Les préférences sont locales pour rester compatibles avec l'état actuel du projet
 * et éviter de casser Firestore. Les écrans Dashboard / Transactions pourront lire
 * ces mêmes clés ensuite.
 */
public class SettingsJointAccountSection {

    public static final String PREFS = "couplefinance_joint_account";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_NAME = "name";
    public static final String KEY_INITIAL_BALANCE = "initial_balance";
    public static final String KEY_INCLUDE_DASHBOARD = "include_dashboard";
    public static final String KEY_INCLUDE_REPARTITION = "include_repartition";

    private final Activity activity;
    private LinearLayout root;

    public SettingsJointAccountSection(Activity activity) {
        this.activity = activity;
    }

    public View build() {
        SettingsStyles.syncWithGlobalTheme();

        root = new LinearLayout(activity);
        root.setOrientation(
                SettingsResponsive.useTwoColumns(activity)
                        ? LinearLayout.HORIZONTAL
                        : LinearLayout.VERTICAL
        );

        buildContent();

        // Recharge la donnée partagée du compte joint depuis Firestore,
        // puis reconstruit la section pour afficher la valeur à jour
        // (visible par tous les membres du foyer).
        JointAccountManager.getInstance().refresh(activity, () -> {
            if (activity != null && !activity.isFinishing()) {
                buildContent();
            }
        });

        return root;
    }

    public void setVisible(boolean visible) {
        if (root != null) root.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void buildContent() {
        if (root == null) return;

        root.removeAllViews();

        LinearLayout left = new LinearLayout(activity);
        left.setOrientation(LinearLayout.VERTICAL);
        root.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams mainParams = SettingsStyles.matchWrap();
        mainParams.bottomMargin = SettingsStyles.dp(activity, 18);
        left.addView(mainCard(), mainParams);

        left.addView(optionsCard());

        LinearLayout right = new LinearLayout(activity);
        right.setOrientation(LinearLayout.VERTICAL);
        right.addView(infoCard());

        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                SettingsResponsive.sideWidth(activity),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        if (SettingsResponsive.useTwoColumns(activity)) {
            rightParams.leftMargin = SettingsStyles.dp(activity, 24);
        } else {
            rightParams.topMargin = SettingsStyles.dp(activity, 24);
        }

        root.addView(right, rightParams);
    }

    private View mainCard() {
        JointAccountManager manager = JointAccountManager.getInstance();
        manager.init(activity);

        boolean enabled = manager.isEnabledLocal();
        String name = manager.getNameLocal();
        float balance = (float) manager.getBalanceLocal();

        LinearLayout card = SettingsCards.sectionCard(activity);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon = new TextView(activity);
        icon.setText("CJ");
        icon.setTextColor(Color.WHITE);
        icon.setTextSize(17f);
        icon.setTypeface(Typeface.DEFAULT_BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setIncludeFontPadding(false);
        icon.setBackground(SettingsStyles.primaryButton());

        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                SettingsStyles.dp(activity, 54),
                SettingsStyles.dp(activity, 54)
        );
        iconParams.rightMargin = SettingsStyles.dp(activity, 16);
        row.addView(icon, iconParams);

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText(name == null || name.trim().isEmpty() ? "Compte Joint" : name);
        SettingsStyles.cardTitle(title);
        title.setTextSize(22f);
        texts.addView(title);

        TextView sub = new TextView(activity);
        sub.setText(enabled ? "Activé · solde début du mois " + money(balance) : "Désactivé");
        sub.setTextColor(enabled ? ThemeColors.success() : ThemeColors.subtext());
        sub.setTextSize(14f);
        sub.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams subParams = SettingsStyles.matchWrap();
        subParams.topMargin = SettingsStyles.dp(activity, 4);
        texts.addView(sub, subParams);

        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView status = new TextView(activity);
        status.setText(enabled ? "ON" : "OFF");
        status.setTextColor(enabled ? Color.WHITE : ThemeColors.text());
        status.setTextSize(12f);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        status.setGravity(Gravity.CENTER);
        status.setPadding(
                SettingsStyles.dp(activity, 13),
                SettingsStyles.dp(activity, 8),
                SettingsStyles.dp(activity, 13),
                SettingsStyles.dp(activity, 8)
        );
        status.setBackground(enabled ? SettingsStyles.primaryButton() : SettingsStyles.secondaryButton());
        row.addView(status);

        card.addView(row);

        TextView edit = new TextView(activity);
        edit.setText("Configurer le Compte Joint");
        edit.setTextColor(Color.WHITE);
        edit.setTypeface(Typeface.DEFAULT_BOLD);
        edit.setTextSize(14f);
        edit.setGravity(Gravity.CENTER);
        edit.setPadding(
                SettingsStyles.dp(activity, 18),
                SettingsStyles.dp(activity, 13),
                SettingsStyles.dp(activity, 18),
                SettingsStyles.dp(activity, 13)
        );
        edit.setBackground(SettingsStyles.primaryButton());
        edit.setOnClickListener(v -> SettingsDialogs.showJointAccount(activity, this::buildContent));

        LinearLayout.LayoutParams editParams = SettingsStyles.matchWrap();
        editParams.topMargin = SettingsStyles.dp(activity, 18);
        card.addView(edit, editParams);

        return card;
    }

    private View optionsCard() {
        SharedPreferences prefs = prefs();

        LinearLayout card = SettingsCards.sectionCard(activity);

        TextView title = new TextView(activity);
        title.setText("Intégration dans l'application");
        SettingsStyles.section(title);
        card.addView(title);

        card.addView(optionRow(
                "Dashboard",
                prefs.getBoolean(KEY_INCLUDE_DASHBOARD, true)
                        ? "Inclus dans le résumé" : "Masqué du résumé"
        ));

        card.addView(optionRow(
                "Répartition",
                prefs.getBoolean(KEY_INCLUDE_REPARTITION, true)
                        ? "Pris en compte" : "Ignoré"
        ));

        return card;
    }

    private View optionRow(String label, String value) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, SettingsStyles.dp(activity, 13), 0, SettingsStyles.dp(activity, 8));

        TextView left = new TextView(activity);
        left.setText(label);
        left.setTextColor(ThemeColors.text());
        left.setTextSize(15f);
        row.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView right = new TextView(activity);
        right.setText(value);
        right.setTextColor(ThemeColors.subtext());
        right.setTextSize(13f);
        right.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(right);

        return row;
    }

    private View infoCard() {
        LinearLayout card = SettingsCards.sectionCard(activity);

        TextView title = new TextView(activity);
        title.setText("À quoi sert le Compte Joint ?");
        SettingsStyles.section(title);
        card.addView(title);

        TextView body = new TextView(activity);
        body.setText("Le Compte Joint n'est pas une personne. Il suit l'argent commun du foyer : "
                + "solde de début de mois, virements reçus, dépenses communes et affichage dans le dashboard. "
                + "Il est partagé entre tous les membres : chacun voit et peut modifier la même donnée.");
        SettingsStyles.cardSubtitle(body);
        body.setLineSpacing(SettingsStyles.dp(activity, 2), 1f);

        LinearLayout.LayoutParams bodyParams = SettingsStyles.matchWrap();
        bodyParams.topMargin = SettingsStyles.dp(activity, 10);
        card.addView(body, bodyParams);

        return card;
    }

    private SharedPreferences prefs() {
        return activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
    }

    private String money(float value) {
        return String.format(java.util.Locale.FRANCE, "%,.2f €", value);
    }
}
