package com.couplefinance.core.ui.components;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.Fmt;
import com.couplefinance.core.ui.animations.CountAnimations;

public final class PremiumStatCard {

    private PremiumStatCard() {
    }

    // ─────────────────────────────
    // BASIC
    // ─────────────────────────────

    public static LinearLayout create(Context ctx,
                                      String label,
                                      String value,
                                      int valueColor) {

        LinearLayout card = PremiumCard.compact(ctx);

        card.setGravity(Gravity.CENTER);

        TextView tvValue = new TextView(ctx);
        tvValue.setText(value);
        tvValue.setTextColor(valueColor);
        tvValue.setTextSize(DS.TEXT_STAT);
        tvValue.setTypeface(null, Typeface.BOLD);
        tvValue.setGravity(Gravity.CENTER);

        TextView tvLabel = new TextView(ctx);
        tvLabel.setText(label);
        tvLabel.setTextColor(ThemeColors.subtext());
        tvLabel.setTextSize(DS.TEXT_XS);
        tvLabel.setTypeface(null, Typeface.BOLD);
        tvLabel.setLetterSpacing(0.08f);
        tvLabel.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams lpLabel =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        lpLabel.topMargin = DS.dp(ctx, 6);

        card.addView(tvValue);
        card.addView(tvLabel, lpLabel);

        return card;
    }

    // ─────────────────────────────
    // SEMANTIC
    // ─────────────────────────────

    public static LinearLayout income(Context ctx,
                                      String label,
                                      double amount) {

        return create(
                ctx,
                label,
                Fmt.money(amount),
                ThemeColors.success()
        );
    }

    public static LinearLayout expense(Context ctx,
                                       String label,
                                       double amount) {

        return create(
                ctx,
                label,
                "-" + Fmt.money(amount),
                ThemeColors.danger()
        );
    }

    public static LinearLayout neutral(Context ctx,
                                       String label,
                                       String value) {

        return create(
                ctx,
                label,
                value,
                ThemeColors.text()
        );
    }

    public static LinearLayout warning(Context ctx,
                                       String label,
                                       String value) {

        return create(
                ctx,
                label,
                value,
                ThemeColors.warning()
        );
    }

    public static LinearLayout info(Context ctx,
                                    String label,
                                    String value) {

        return create(
                ctx,
                label,
                value,
                ThemeColors.info()
        );
    }

    // ─────────────────────────────
    // HERO
    // ─────────────────────────────

    public static LinearLayout hero(Context ctx,
                                    String label,
                                    String value,
                                    int valueColor) {

        LinearLayout card = PremiumCard.hero(ctx);

        card.setGravity(Gravity.CENTER);

        TextView tvLabel = new TextView(ctx);
        tvLabel.setText(label);
        tvLabel.setTextColor(ThemeColors.subtext());
        tvLabel.setTextSize(DS.TEXT_XS);
        tvLabel.setTypeface(null, Typeface.BOLD);
        tvLabel.setLetterSpacing(0.08f);
        tvLabel.setGravity(Gravity.CENTER);

        TextView tvValue = new TextView(ctx);
        tvValue.setText(value);
        tvValue.setTextColor(valueColor);
        tvValue.setTextSize(DS.TEXT_HERO);
        tvValue.setTypeface(null, Typeface.BOLD);
        tvValue.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams lpValue =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        lpValue.topMargin = DS.dp(ctx, 8);

        card.addView(tvLabel);
        card.addView(tvValue, lpValue);

        return card;
    }

    // ─────────────────────────────
    // ANIMATED
    // ─────────────────────────────

    public static LinearLayout animatedMoney(Context ctx,
                                             String label,
                                             double from,
                                             double to,
                                             boolean positive) {

        LinearLayout card = PremiumCard.compact(ctx);

        card.setGravity(Gravity.CENTER);

        TextView tvValue = new TextView(ctx);
        tvValue.setText(Fmt.money(from));
        tvValue.setTextColor(
                positive
                        ? ThemeColors.success()
                        : ThemeColors.danger()
        );
        tvValue.setTextSize(DS.TEXT_STAT);
        tvValue.setTypeface(null, Typeface.BOLD);
        tvValue.setGravity(Gravity.CENTER);

        TextView tvLabel = new TextView(ctx);
        tvLabel.setText(label);
        tvLabel.setTextColor(ThemeColors.subtext());
        tvLabel.setTextSize(DS.TEXT_XS);
        tvLabel.setTypeface(null, Typeface.BOLD);
        tvLabel.setLetterSpacing(0.08f);
        tvLabel.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams lpLabel =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        lpLabel.topMargin = DS.dp(ctx, 6);

        card.addView(tvValue);
        card.addView(tvLabel, lpLabel);

        CountAnimations.animateMoney(
                tvValue,
                from,
                to
        );

        return card;
    }

    // ─────────────────────────────
    // HELPERS
    // ─────────────────────────────

    public static void setWeight(LinearLayout card) {
        if (card == null) {
            return;
        }

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                );

        card.setLayoutParams(lp);
    }

    public static LinearLayout.LayoutParams weightedParams(Context ctx) {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                );

        lp.rightMargin = DS.dp(ctx, DS.GAP_SM);

        return lp;
    }
}