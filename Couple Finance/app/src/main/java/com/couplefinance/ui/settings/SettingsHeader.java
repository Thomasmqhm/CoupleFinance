package com.couplefinance.ui.settings;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.effects.GradientFactory;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * SettingsHeader — Hero card premium pour l'écran Paramètres.
 *
 * Design :
 *   ┌──────────────────────────────────────────────────┐
 *   │  FOYER                                           │
 *   │  Prénom & Prénom                         [stats] │
 *   │  Description · depuis date                       │
 *   └──────────────────────────────────────────────────┘
 *
 * Entièrement connecté au thème dynamique via ThemeColors.
 */
public class SettingsHeader {

    private final Activity activity;

    public SettingsHeader(Activity activity) {
        this.activity = activity;
    }

    public View build() {
        SettingsModels.State state = SettingsCache.get();

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = DS.dp(activity, 8);
        container.setLayoutParams(lp);

        // ── Hero card ────────────────────────────────────────────────
        LinearLayout hero = new LinearLayout(activity);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(
                DS.dp(activity, 22),
                DS.dp(activity, 22),
                DS.dp(activity, 22),
                DS.dp(activity, 22)
        );
        hero.setBackground(buildHeroBackground());
        hero.setElevation(DS.dp(activity, 3));

        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, -2);
        heroLp.bottomMargin = DS.dp(activity, 6);
        hero.setLayoutParams(heroLp);

        // ── Ligne du haut : badge FOYER + stats (tablettes) ──────────
        LinearLayout topRow = new LinearLayout(activity);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        // Badge "FOYER"
        TextView badgeLabel = new TextView(activity);
        badgeLabel.setText("FOYER");
        badgeLabel.setTextColor(ThemeColors.withAlpha(Color.WHITE, 180));
        badgeLabel.setTextSize(10f);
        badgeLabel.setTypeface(null, Typeface.BOLD);
        badgeLabel.setLetterSpacing(0.14f);

        topRow.addView(badgeLabel, new LinearLayout.LayoutParams(0, -2, 1f));

        // Badge membres (ex: "2 membres")
        TextView membersBadge = new TextView(activity);
        membersBadge.setText(state.memberCount() + " membre" + (state.memberCount() > 1 ? "s" : ""));
        membersBadge.setTextColor(ThemeColors.withAlpha(Color.WHITE, 200));
        membersBadge.setTextSize(11f);
        membersBadge.setTypeface(null, Typeface.BOLD);
        membersBadge.setPadding(
                DS.dp(activity, 10),
                DS.dp(activity, 5),
                DS.dp(activity, 10),
                DS.dp(activity, 5)
        );

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(ThemeColors.withAlpha(Color.WHITE, 28));
        badgeBg.setCornerRadius(DS.dp(activity, 20));
        membersBadge.setBackground(badgeBg);

        topRow.addView(membersBadge);
        hero.addView(topRow);

        // ── Nom du foyer ─────────────────────────────────────────────
        TextView nameView = new TextView(activity);
        nameView.setText(buildHouseholdTitle(state));
        nameView.setTextColor(Color.WHITE);
        nameView.setTextSize(SettingsResponsive.useTwoColumns(activity) ? 28f : 22f);
        nameView.setTypeface(null, Typeface.BOLD);
        nameView.setIncludeFontPadding(false);

        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(-1, -2);
        nameLp.topMargin = DS.dp(activity, 10);
        hero.addView(nameView, nameLp);

        // ── Description ──────────────────────────────────────────────
        String descText = (state.description != null && !state.description.isEmpty())
                ? state.description : "Foyer commun";
        String sinceText = (state.createdAtLabel != null && !state.createdAtLabel.isEmpty())
                ? " · depuis " + state.createdAtLabel.toLowerCase(Locale.FRANCE) : "";

        TextView desc = new TextView(activity);
        desc.setText(descText + sinceText);
        desc.setTextColor(ThemeColors.withAlpha(Color.WHITE, 170));
        desc.setTextSize(13f);
        desc.setLineSpacing(2f, 1f);

        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(-1, -2);
        descLp.topMargin = DS.dp(activity, 8);
        hero.addView(desc, descLp);

        // ── Ligne de stats (charges fixes + ratio) ───────────────────
        if (state.totalCharges() > 0 || state.memberCount() > 0) {
            LinearLayout statsRow = new LinearLayout(activity);
            statsRow.setOrientation(LinearLayout.HORIZONTAL);

            LinearLayout.LayoutParams statsLp = new LinearLayout.LayoutParams(-1, -2);
            statsLp.topMargin = DS.dp(activity, 18);
            statsRow.setLayoutParams(statsLp);

            statsRow.addView(statPill("🏠", formatMoney(state.totalCharges()), "Charges fixes"));

            if (state.memberCount() > 1) {
                LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(-2, -2);
                pillLp.leftMargin = DS.dp(activity, 10);
                View pill2 = statPill("⚖", "50 / 50", "Répartition");
                statsRow.addView(pill2, pillLp);
            }

            hero.addView(statsRow);
        }

        container.addView(hero);

        // ── Rangée de 3 stat cards sous le hero ──────────────────────
        if (SettingsResponsive.useTwoColumns(activity)) {
            LinearLayout cardsRow = new LinearLayout(activity);
            cardsRow.setOrientation(LinearLayout.HORIZONTAL);

            LinearLayout.LayoutParams cardsLp = new LinearLayout.LayoutParams(-1, -2);
            cardsLp.topMargin = DS.dp(activity, 12);
            cardsRow.setLayoutParams(cardsLp);

            cardsRow.addView(miniStatCard("👥", String.valueOf(state.memberCount()), "Membres"), statCardParams(0));
            cardsRow.addView(miniStatCard("📂", String.valueOf(
                    state.categories != null ? state.categories.size() : 0), "Catégories"), statCardParams(1));
            cardsRow.addView(miniStatCard("🔁", String.valueOf(
                    state.charges != null ? state.charges.size() : 0), "Charges"), statCardParams(1));

            container.addView(cardsRow);
        }

        return container;
    }

    // ── Helpers UI ───────────────────────────────────────────────────────────

    /**
     * Pill flottante dans le hero (ex: "🏠 450 € · Charges fixes")
     */
    private View statPill(String emoji, String value, String label) {
        LinearLayout pill = new LinearLayout(activity);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(
                DS.dp(activity, 12),
                DS.dp(activity, 8),
                DS.dp(activity, 14),
                DS.dp(activity, 8)
        );

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ThemeColors.withAlpha(Color.WHITE, 24));
        bg.setCornerRadius(DS.dp(activity, 20));
        pill.setBackground(bg);

        TextView emojiView = new TextView(activity);
        emojiView.setText(emoji);
        emojiView.setTextSize(14f);
        pill.addView(emojiView);

        TextView valueView = new TextView(activity);
        valueView.setText("  " + value);
        valueView.setTextColor(Color.WHITE);
        valueView.setTextSize(13f);
        valueView.setTypeface(null, Typeface.BOLD);
        pill.addView(valueView);

        TextView labelView = new TextView(activity);
        labelView.setText("  · " + label);
        labelView.setTextColor(ThemeColors.withAlpha(Color.WHITE, 160));
        labelView.setTextSize(12f);
        pill.addView(labelView);

        return pill;
    }

    /**
     * Mini card de stat (sous le hero, mode tablette)
     */
    private View miniStatCard(String emoji, String value, String label) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                DS.dp(activity, 16),
                DS.dp(activity, 14),
                DS.dp(activity, 16),
                DS.dp(activity, 14)
        );
        card.setBackground(GradientFactory.bordered(
                activity,
                ThemeColors.card(),
                ThemeColors.border(),
                DS.R_MD
        ));
        card.setElevation(DS.dp(activity, 2));

        TextView emojiView = new TextView(activity);
        emojiView.setText(emoji);
        emojiView.setTextSize(20f);
        card.addView(emojiView);

        TextView valueView = new TextView(activity);
        valueView.setText(value);
        valueView.setTextColor(ThemeColors.text());
        valueView.setTextSize(22f);
        valueView.setTypeface(null, Typeface.BOLD);

        LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(-2, -2);
        vLp.topMargin = DS.dp(activity, 6);
        card.addView(valueView, vLp);

        TextView labelView = new TextView(activity);
        labelView.setText(label);
        labelView.setTextColor(ThemeColors.subtext());
        labelView.setTextSize(12f);

        LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(-2, -2);
        lLp.topMargin = DS.dp(activity, 2);
        card.addView(labelView, lLp);

        return card;
    }

    private LinearLayout.LayoutParams statCardParams(int leftMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.leftMargin = DS.dp(activity, leftMargin == 0 ? 0 : 10);
        return lp;
    }

    /**
     * Background du hero : dégradé du primary vers primaryDark, coins arrondis.
     */
    private GradientDrawable buildHeroBackground() {
        int[] colors = {
                ThemeColors.primary(),
                ThemeColors.primaryDark()
        };

        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                colors
        );
        gd.setCornerRadius(DS.dp(activity, DS.R_XL));
        return gd;
    }

    private String buildHouseholdTitle(SettingsModels.State state) {
        if (state.members == null || state.members.isEmpty()) {
            return state.householdName != null ? state.householdName : "Mon foyer";
        }

        if (state.members.size() == 1) {
            return state.members.get(0).name;
        }

        String first  = state.members.get(0).name;
        String second = state.members.get(1).name;

        if (first  == null || first.trim().isEmpty())  first  = "Moi";
        if (second == null || second.trim().isEmpty()) second = "Membre";

        return first + " & " + second;
    }

    private String formatMoney(double amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');

        DecimalFormat df = new DecimalFormat("#,##0", symbols);
        return df.format(amount) + " €";
    }
}
