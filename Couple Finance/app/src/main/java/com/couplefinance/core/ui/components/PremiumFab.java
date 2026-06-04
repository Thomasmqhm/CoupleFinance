package com.couplefinance.core.ui.components;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.animations.PressAnimations;
import com.couplefinance.core.ui.effects.GradientFactory;
import com.couplefinance.core.ui.effects.ShadowFactory;

public final class PremiumFab {

    private PremiumFab() {
    }

    public static TextView primary(Context ctx, String icon) {
        TextView fab = base(ctx, icon);

        fab.setTextColor(ThemeColors.onPrimary());

        fab.setBackground(
                GradientFactory.gradientDiagonal(
                        ctx,
                        ThemeColors.primary(),
                        ThemeColors.primaryDark(),
                        999
                )
        );

        ShadowFactory.fab(fab, ctx);

        return fab;
    }

    public static TextView secondary(Context ctx, String icon) {
        TextView fab = base(ctx, icon);

        fab.setTextColor(ThemeColors.text());

        fab.setBackground(
                GradientFactory.bordered(
                        ctx,
                        ThemeColors.card(),
                        ThemeColors.border(),
                        999
                )
        );

        ShadowFactory.soft(fab, ctx);

        return fab;
    }

    public static TextView success(Context ctx, String icon) {
        TextView fab = base(ctx, icon);

        fab.setTextColor(ThemeColors.white());

        fab.setBackground(
                GradientFactory.solid(
                        ctx,
                        ThemeColors.success(),
                        999
                )
        );

        ShadowFactory.fab(fab, ctx);

        return fab;
    }

    public static TextView danger(Context ctx, String icon) {
        TextView fab = base(ctx, icon);

        fab.setTextColor(ThemeColors.white());

        fab.setBackground(
                GradientFactory.solid(
                        ctx,
                        ThemeColors.danger(),
                        999
                )
        );

        ShadowFactory.fab(fab, ctx);

        return fab;
    }

    public static TextView extended(Context ctx,
                                    String icon,
                                    String text) {

        TextView fab = new TextView(ctx);

        fab.setText(icon + "  " + text);

        fab.setTextColor(ThemeColors.white());

        fab.setTextSize(DS.TEXT_SM);

        fab.setTypeface(null, Typeface.BOLD);

        fab.setGravity(Gravity.CENTER);

        fab.setPadding(
                DS.dp(ctx, 22),
                DS.dp(ctx, 14),
                DS.dp(ctx, 22),
                DS.dp(ctx, 14)
        );

        fab.setBackground(
                GradientFactory.gradientDiagonal(
                        ctx,
                        ThemeColors.primary(),
                        ThemeColors.primaryDark(),
                        999
                )
        );

        ShadowFactory.fab(fab, ctx);

        PressAnimations.applySoft(fab);

        return fab;
    }

    public static FrameLayout.LayoutParams params(Context ctx) {
        FrameLayout.LayoutParams lp =
                new FrameLayout.LayoutParams(
                        DS.dp(ctx, 58),
                        DS.dp(ctx, 58)
                );

        lp.gravity = Gravity.BOTTOM | Gravity.END;

        lp.rightMargin = DS.dp(ctx, 22);
        lp.bottomMargin = DS.dp(ctx, 22);

        return lp;
    }

    public static FrameLayout.LayoutParams extendedParams(Context ctx) {
        FrameLayout.LayoutParams lp =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        lp.gravity = Gravity.BOTTOM | Gravity.END;

        lp.rightMargin = DS.dp(ctx, 22);
        lp.bottomMargin = DS.dp(ctx, 22);

        return lp;
    }

    private static TextView base(Context ctx, String icon) {
        TextView fab = new TextView(ctx);

        fab.setText(icon == null ? "+" : icon);

        fab.setTextSize(24f);

        fab.setTypeface(null, Typeface.BOLD);

        fab.setGravity(Gravity.CENTER);

        int size = DS.dp(ctx, 58);

        fab.setLayoutParams(
                new FrameLayout.LayoutParams(size, size)
        );

        PressAnimations.applySoft(fab);

        return fab;
    }
}