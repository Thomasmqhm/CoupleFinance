package com.couplefinance.core.ui.effects;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;

public final class ShadowFactory {

    private ShadowFactory() {
    }

    // ─────────────────────────────
    // BASIC SHADOWS
    // ─────────────────────────────

    public static void none(View view) {
        if (view == null) {
            return;
        }

        view.setElevation(0f);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setTranslationZ(0f);
        }
    }

    public static void subtle(View view, Context ctx) {
        apply(view, ctx, 1.2f);
    }

    public static void soft(View view, Context ctx) {
        apply(view, ctx, 2f);
    }

    public static void medium(View view, Context ctx) {
        apply(view, ctx, 4f);
    }

    public static void strong(View view, Context ctx) {
        apply(view, ctx, 7f);
    }

    public static void floating(View view, Context ctx) {
        apply(view, ctx, 10f);
    }

    private static void apply(View view, Context ctx, float dp) {
        if (view == null || ctx == null) {
            return;
        }

        float px = DS.dp(ctx, (int) dp);

        view.setElevation(px);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setTranslationZ(px / 2f);
        }
    }

    // ─────────────────────────────
    // CARD SHADOWS
    // ─────────────────────────────

    public static void card(View view, Context ctx) {
        if (view == null || ctx == null) {
            return;
        }

        subtle(view, ctx);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            view.setClipToOutline(false);
        }
    }

    public static void hero(View view, Context ctx) {
        if (view == null || ctx == null) {
            return;
        }

        medium(view, ctx);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            view.setClipToOutline(false);
        }
    }

    public static void modal(View view, Context ctx) {
        if (view == null || ctx == null) {
            return;
        }

        strong(view, ctx);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            view.setClipToOutline(false);
        }
    }

    public static void bottomSheet(View view, Context ctx) {
        if (view == null || ctx == null) {
            return;
        }

        floating(view, ctx);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            view.setClipToOutline(false);
        }
    }

    // ─────────────────────────────
    // INTERACTIVE
    // ─────────────────────────────

    public static void pressed(View view, Context ctx) {
        if (view == null || ctx == null) {
            return;
        }

        soft(view, ctx);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.animate()
                    .translationZ(DS.dp(ctx, 1))
                    .setDuration(80)
                    .start();
        }
    }

    public static void released(View view, Context ctx) {
        if (view == null || ctx == null) {
            return;
        }

        subtle(view, ctx);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.animate()
                    .translationZ(0f)
                    .setDuration(120)
                    .start();
        }
    }

    // ─────────────────────────────
    // GLOW EFFECTS
    // ─────────────────────────────

    public static void glowPrimary(View view, Context ctx) {
        glow(view, ctx, ThemeColors.primary());
    }

    public static void glowSuccess(View view, Context ctx) {
        glow(view, ctx, ThemeColors.success());
    }

    public static void glowDanger(View view, Context ctx) {
        glow(view, ctx, ThemeColors.danger());
    }

    public static void glow(View view, Context ctx, int color) {
        if (view == null || ctx == null) {
            return;
        }

        medium(view, ctx);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setOutlineSpotShadowColor(color);
            view.setOutlineAmbientShadowColor(
                    ThemeColors.withAlpha(color, 120)
            );
        }
    }
    
    public static void fab(View view, Context ctx) {
        if (view == null || ctx == null) {
            return;
        }

        floating(view, ctx);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            view.setClipToOutline(false);
        }
    }


    // ─────────────────────────────
    // HELPERS
    // ─────────────────────────────

    public static void applyRoundedOutline(View view) {
        if (view == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            view.setClipToOutline(false);
        }
    }
}