package com.couplefinance.core.ui.components;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.animations.PressAnimations;
import com.couplefinance.core.ui.effects.GlassEffect;
import com.couplefinance.core.ui.effects.GradientFactory;
import com.couplefinance.core.ui.effects.ShadowFactory;

public final class PremiumCard {

    private PremiumCard() {
    }

    public static LinearLayout standard(Context ctx) {
        LinearLayout card = base(ctx);
        card.setBackground(GradientFactory.card(ctx));
        ShadowFactory.card(card, ctx);
        return card;
    }

    public static LinearLayout elevated(Context ctx) {
        LinearLayout card = base(ctx);
        card.setBackground(GradientFactory.cardSoft(ctx));
        ShadowFactory.soft(card, ctx);
        return card;
    }

    public static LinearLayout glass(Context ctx) {
        LinearLayout card = base(ctx);
        GlassEffect.apply(card, ctx, DS.R_MD);
        return card;
    }

    public static LinearLayout hero(Context ctx) {
        LinearLayout card = base(ctx);
        card.setBackground(GradientFactory.primarySoftGradient(ctx, DS.R_LG));
        ShadowFactory.hero(card, ctx);
        return card;
    }

    public static LinearLayout primary(Context ctx) {
        LinearLayout card = base(ctx);
        card.setBackground(GradientFactory.primaryGradient(ctx, DS.R_LG));
        ShadowFactory.hero(card, ctx);
        return card;
    }

    public static LinearLayout success(Context ctx) {
        LinearLayout card = base(ctx);
        card.setBackground(GradientFactory.bordered(
                ctx,
                ThemeColors.successSoft(),
                ThemeColors.withAlpha(ThemeColors.success(), 45),
                DS.R_MD
        ));
        ShadowFactory.card(card, ctx);
        return card;
    }

    public static LinearLayout warning(Context ctx) {
        LinearLayout card = base(ctx);
        card.setBackground(GradientFactory.bordered(
                ctx,
                ThemeColors.warningSoft(),
                ThemeColors.withAlpha(ThemeColors.warning(), 45),
                DS.R_MD
        ));
        ShadowFactory.card(card, ctx);
        return card;
    }

    public static LinearLayout danger(Context ctx) {
        LinearLayout card = base(ctx);
        card.setBackground(GradientFactory.bordered(
                ctx,
                ThemeColors.dangerSoft(),
                ThemeColors.withAlpha(ThemeColors.danger(), 45),
                DS.R_MD
        ));
        ShadowFactory.card(card, ctx);
        return card;
    }

    public static LinearLayout info(Context ctx) {
        LinearLayout card = base(ctx);
        card.setBackground(GradientFactory.bordered(
                ctx,
                ThemeColors.infoSoft(),
                ThemeColors.withAlpha(ThemeColors.info(), 45),
                DS.R_MD
        ));
        ShadowFactory.card(card, ctx);
        return card;
    }

    public static LinearLayout row(Context ctx) {
        LinearLayout card = standard(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        return card;
    }

    public static LinearLayout rowGlass(Context ctx) {
        LinearLayout card = glass(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        return card;
    }

    public static LinearLayout clickable(Context ctx) {
        LinearLayout card = standard(ctx);
        card.setClickable(true);
        card.setFocusable(true);
        PressAnimations.applySoft(card);
        return card;
    }

    public static LinearLayout clickableGlass(Context ctx) {
        LinearLayout card = glass(ctx);
        card.setClickable(true);
        card.setFocusable(true);
        PressAnimations.applySoft(card);
        return card;
    }

    public static LinearLayout compact(Context ctx) {
        LinearLayout card = standard(ctx);
        int p = DS.dp(ctx, 14);
        card.setPadding(p, p, p, p);
        return card;
    }

    public static LinearLayout section(Context ctx) {
        LinearLayout card = standard(ctx);
        int h = DS.dp(ctx, DS.PAD_CARD);
        int v = DS.dp(ctx, 18);
        card.setPadding(h, v, h, v);
        return card;
    }

    public static LinearLayout noPadding(Context ctx) {
        LinearLayout card = standard(ctx);
        card.setPadding(0, 0, 0, 0);
        return card;
    }

    public static LinearLayout base(Context ctx) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        int p = DS.dp(ctx, DS.PAD_CARD);
        card.setPadding(p, p, p, p);

        card.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        return card;
    }

    public static void applyDefaultMargin(LinearLayout card, Context ctx) {
        if (card == null || ctx == null) {
            return;
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = DS.dp(ctx, DS.GAP);
        card.setLayoutParams(lp);
    }

    public static void applySmallMargin(LinearLayout card, Context ctx) {
        if (card == null || ctx == null) {
            return;
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = DS.dp(ctx, DS.GAP_SM);
        card.setLayoutParams(lp);
    }
}