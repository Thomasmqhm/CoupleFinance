package com.couplefinance.core.ui.effects;

import android.content.Context;
import android.view.View;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;

public final class GlassEffect {

    private GlassEffect() {
    }

    public static void apply(View view, Context ctx) {
        apply(view, ctx, DS.R_MD);
    }

    public static void apply(View view, Context ctx, int radiusDp) {
        if (view == null || ctx == null) {
            return;
        }

        view.setBackground(GradientFactory.glass(ctx, radiusDp));
        view.setElevation(DS.dp(ctx, 1));
    }

    public static void applyStrong(View view, Context ctx) {
        applyStrong(view, ctx, DS.R_LG);
    }

    public static void applyStrong(View view, Context ctx, int radiusDp) {
        if (view == null || ctx == null) {
            return;
        }

        view.setBackground(GradientFactory.bordered(
                ctx,
                ThemeColors.cardElevated(),
                ThemeColors.glassBorder(),
                radiusDp
        ));
        view.setElevation(DS.dp(ctx, 2));
    }

    public static void applySoft(View view, Context ctx) {
        applySoft(view, ctx, DS.R_MD);
    }

    public static void applySoft(View view, Context ctx, int radiusDp) {
        if (view == null || ctx == null) {
            return;
        }

        view.setBackground(GradientFactory.bordered(
                ctx,
                ThemeColors.withAlpha(ThemeColors.card(), 235),
                ThemeColors.divider(),
                radiusDp
        ));
        view.setElevation(DS.dp(ctx, 1));
    }

    public static void applyPrimaryTint(View view, Context ctx) {
        applyPrimaryTint(view, ctx, DS.R_MD);
    }

    public static void applyPrimaryTint(View view, Context ctx, int radiusDp) {
        if (view == null || ctx == null) {
            return;
        }

        view.setBackground(GradientFactory.bordered(
                ctx,
                ThemeColors.primarySoft(),
                ThemeColors.withAlpha(ThemeColors.primary(), 45),
                radiusDp
        ));
        view.setElevation(DS.dp(ctx, 1));
    }

    public static void applySuccessTint(View view, Context ctx) {
        applySuccessTint(view, ctx, DS.R_MD);
    }

    public static void applySuccessTint(View view, Context ctx, int radiusDp) {
        if (view == null || ctx == null) {
            return;
        }

        view.setBackground(GradientFactory.bordered(
                ctx,
                ThemeColors.successSoft(),
                ThemeColors.withAlpha(ThemeColors.success(), 45),
                radiusDp
        ));
        view.setElevation(DS.dp(ctx, 1));
    }

    public static void applyWarningTint(View view, Context ctx) {
        applyWarningTint(view, ctx, DS.R_MD);
    }

    public static void applyWarningTint(View view, Context ctx, int radiusDp) {
        if (view == null || ctx == null) {
            return;
        }

        view.setBackground(GradientFactory.bordered(
                ctx,
                ThemeColors.warningSoft(),
                ThemeColors.withAlpha(ThemeColors.warning(), 45),
                radiusDp
        ));
        view.setElevation(DS.dp(ctx, 1));
    }

    public static void applyDangerTint(View view, Context ctx) {
        applyDangerTint(view, ctx, DS.R_MD);
    }

    public static void applyDangerTint(View view, Context ctx, int radiusDp) {
        if (view == null || ctx == null) {
            return;
        }

        view.setBackground(GradientFactory.bordered(
                ctx,
                ThemeColors.dangerSoft(),
                ThemeColors.withAlpha(ThemeColors.danger(), 45),
                radiusDp
        ));
        view.setElevation(DS.dp(ctx, 1));
    }

    public static void remove(View view) {
        if (view == null) {
            return;
        }

        view.setBackground(null);
        view.setElevation(0);
    }
}