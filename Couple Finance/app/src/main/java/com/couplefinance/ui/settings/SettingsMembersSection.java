package com.couplefinance.ui.settings;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.AppToast;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.data.HouseholdManager;

/**
 * SettingsMembersSection — Foyer / Membres.
 *
 * Important : on ne crée plus de membre manuellement ici.
 * Chaque personne doit créer son propre compte et rejoindre le foyer via le code.
 * Cette section sert uniquement à afficher les membres existants, copier le code
 * d'invitation et gérer les informations locales non destructives.
 */
public class SettingsMembersSection {

    private final Activity activity;
    private LinearLayout root;

    public SettingsMembersSection(Activity activity) {
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

        buildContent(SettingsCache.get());
        return root;
    }

    public void setVisible(boolean visible) {
        if (root != null) root.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void buildContent(SettingsModels.State state) {
        if (root == null) return;

        root.removeAllViews();

        LinearLayout left = new LinearLayout(activity);
        left.setOrientation(LinearLayout.VERTICAL);
        root.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams infoParams = SettingsStyles.matchWrap();
        infoParams.bottomMargin = SettingsStyles.dp(activity, 18);
        left.addView(invitationCard(), infoParams);

        if (state == null || state.members == null || state.members.isEmpty()) {
            left.addView(emptyMemberCard());
        } else {
            for (int i = 0; i < state.members.size(); i++) {
                LinearLayout.LayoutParams memberParams = SettingsStyles.matchWrap();
                memberParams.bottomMargin = SettingsStyles.dp(activity, 18);
                left.addView(memberCard(state.members.get(i), i), memberParams);
            }
        }

        LinearLayout right = new LinearLayout(activity);
        right.setOrientation(LinearLayout.VERTICAL);

        right.addView(buildHouseholdInfoCard(state));

        LinearLayout.LayoutParams guideParams = SettingsStyles.matchWrap();
        guideParams.topMargin = SettingsStyles.dp(activity, 16);
        right.addView(buildGuideCard(), guideParams);

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

    private View invitationCard() {
        LinearLayout card = SettingsCards.sectionCard(activity);

        TextView title = new TextView(activity);
        title.setText("Invitation au foyer");
        SettingsStyles.cardTitle(title);
        card.addView(title);

        TextView sub = new TextView(activity);
        sub.setText("Pour ajouter quelqu'un, il doit créer son propre compte avec son adresse mail, puis rejoindre ce foyer avec le code d'invitation.");
        SettingsStyles.cardSubtitle(sub);
        LinearLayout.LayoutParams subParams = SettingsStyles.matchWrap();
        subParams.topMargin = SettingsStyles.dp(activity, 8);
        card.addView(sub, subParams);

        String code = HouseholdManager.getInstance().getHouseholdId();

        TextView codeView = new TextView(activity);
        codeView.setText(code == null || code.trim().isEmpty() ? "Code indisponible" : code);
        codeView.setTextColor(ThemeColors.text());
        codeView.setTextSize(16f);
        codeView.setTypeface(Typeface.DEFAULT_BOLD);
        codeView.setGravity(Gravity.CENTER);
        codeView.setPadding(
                SettingsStyles.dp(activity, 16),
                SettingsStyles.dp(activity, 13),
                SettingsStyles.dp(activity, 16),
                SettingsStyles.dp(activity, 13)
        );
        codeView.setBackground(SettingsStyles.secondaryButton());

        LinearLayout.LayoutParams codeParams = SettingsStyles.matchWrap();
        codeParams.topMargin = SettingsStyles.dp(activity, 16);
        card.addView(codeView, codeParams);

        TextView copy = new TextView(activity);
        copy.setText("Copier le code");
        copy.setTextColor(Color.WHITE);
        copy.setTypeface(Typeface.DEFAULT_BOLD);
        copy.setTextSize(14f);
        copy.setGravity(Gravity.CENTER);
        copy.setPadding(
                SettingsStyles.dp(activity, 18),
                SettingsStyles.dp(activity, 13),
                SettingsStyles.dp(activity, 18),
                SettingsStyles.dp(activity, 13)
        );
        copy.setBackground(SettingsStyles.primaryButton());
        copy.setOnClickListener(v -> copyHouseholdCode(code));

        LinearLayout.LayoutParams copyParams = SettingsStyles.matchWrap();
        copyParams.topMargin = SettingsStyles.dp(activity, 12);
        card.addView(copy, copyParams);

        return card;
    }

    private View emptyMemberCard() {
        LinearLayout card = SettingsCards.sectionCard(activity);

        TextView title = new TextView(activity);
        title.setText("Aucun autre membre");
        SettingsStyles.cardTitle(title);
        card.addView(title);

        TextView sub = new TextView(activity);
        sub.setText("Le foyer affichera automatiquement les personnes qui rejoignent avec leur propre compte.");
        SettingsStyles.cardSubtitle(sub);

        LinearLayout.LayoutParams sp = SettingsStyles.matchWrap();
        sp.topMargin = SettingsStyles.dp(activity, 8);
        card.addView(sub, sp);

        return card;
    }

    private View memberCard(SettingsModels.Member member, int index) {
        LinearLayout card = SettingsCards.sectionCard(activity);

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView avatarView = new TextView(activity);
        avatarView.setText(member != null ? member.initial() : "?");
        avatarView.setTextColor(Color.WHITE);
        avatarView.setTextSize(23f);
        avatarView.setTypeface(Typeface.DEFAULT_BOLD);
        avatarView.setGravity(Gravity.CENTER);
        avatarView.setIncludeFontPadding(false);

        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setColor(safeColor(member != null ? member.color : null, pastelColor(index)));
        avatarBg.setShape(GradientDrawable.OVAL);
        avatarView.setBackground(avatarBg);

        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(
                SettingsStyles.dp(activity, 58),
                SettingsStyles.dp(activity, 58)
        );
        top.addView(avatarView, avatarParams);

        LinearLayout info = new LinearLayout(activity);
        info.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        infoParams.leftMargin = SettingsStyles.dp(activity, 16);
        top.addView(info, infoParams);

        TextView tvName = new TextView(activity);
        tvName.setText(safeText(member != null ? member.name : null, "Membre"));
        SettingsStyles.cardTitle(tvName);
        tvName.setTextSize(21f);
        info.addView(tvName);

        TextView tvRole = new TextView(activity);
        tvRole.setText(safeText(member != null ? member.role : null, member != null && member.admin ? "ADMINISTRATEUR" : "MEMBRE"));
        tvRole.setTextColor(SettingsStyles.primary());
        tvRole.setTypeface(Typeface.DEFAULT_BOLD);
        tvRole.setTextSize(12f);

        LinearLayout.LayoutParams roleParams = SettingsStyles.wrapWrap();
        roleParams.topMargin = SettingsStyles.dp(activity, 4);
        info.addView(tvRole, roleParams);

        if (member != null && member.admin) {
            TextView badge = new TextView(activity);
            badge.setText("ADMIN");
            badge.setTextColor(SettingsStyles.primary());
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setTextSize(12f);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(
                    SettingsStyles.dp(activity, 14),
                    SettingsStyles.dp(activity, 8),
                    SettingsStyles.dp(activity, 14),
                    SettingsStyles.dp(activity, 8)
            );
            badge.setBackground(SettingsStyles.secondaryButton());
            top.addView(badge);
        }

        card.addView(top);
        return card;
    }

    private View buildHouseholdInfoCard(SettingsModels.State state) {
        LinearLayout card = SettingsCards.sectionCard(activity);

        TextView title = new TextView(activity);
        title.setText("Résumé du foyer");
        SettingsStyles.section(title);
        card.addView(title);

        int count = state == null || state.members == null ? 0 : state.members.size();

        TextView members = new TextView(activity);
        members.setText(count + " membre" + (count > 1 ? "s" : "") + " actif" + (count > 1 ? "s" : ""));
        SettingsStyles.cardSubtitle(members);

        LinearLayout.LayoutParams mp = SettingsStyles.matchWrap();
        mp.topMargin = SettingsStyles.dp(activity, 10);
        card.addView(members, mp);

        TextView hint = new TextView(activity);
        hint.setText("Les accès sont liés aux comptes utilisateurs, pas à des fiches créées manuellement.");
        SettingsStyles.cardSubtitle(hint);

        LinearLayout.LayoutParams hp = SettingsStyles.matchWrap();
        hp.topMargin = SettingsStyles.dp(activity, 8);
        card.addView(hint, hp);

        return card;
    }

    private View buildGuideCard() {
        LinearLayout card = SettingsCards.sectionCard(activity);

        TextView title = new TextView(activity);
        title.setText("Comment ajouter quelqu'un ?");
        SettingsStyles.section(title);
        card.addView(title);

        TextView body = new TextView(activity);
        body.setText("1. La personne crée son compte\n2. Elle rejoint votre foyer avec le code\n3. Elle apparaît automatiquement ici");
        SettingsStyles.cardSubtitle(body);
        body.setLineSpacing(SettingsStyles.dp(activity, 2), 1f);

        LinearLayout.LayoutParams bp = SettingsStyles.matchWrap();
        bp.topMargin = SettingsStyles.dp(activity, 10);
        card.addView(body, bp);

        return card;
    }

    private void copyHouseholdCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            AppToast.error(activity, "Code indisponible");
            return;
        }

        android.content.ClipboardManager cm =
                (android.content.ClipboardManager) activity.getSystemService(Activity.CLIPBOARD_SERVICE);

        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Code foyer", code));
            AppToast.success(activity, "Code copié");
        }
    }

    private int pastelColor(int index) {
        int[] colors = {
                Color.parseColor("#D8A48F"),
                Color.parseColor("#A8C8B8"),
                Color.parseColor("#DCCB8F"),
                Color.parseColor("#9FB7D9"),
                Color.parseColor("#B8A5D6"),
                Color.parseColor("#E2B8C6")
        };
        return colors[Math.abs(index) % colors.length];
    }

    private int safeColor(String value, int fallback) {
        try {
            if (value == null || value.trim().isEmpty()) return fallback;
            return Color.parseColor(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private String safeText(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
