package com.couplefinance.core.ui.effects;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.view.View;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;

/**
 * ElevationSystem — Système d'élévation différenciée pour CoupleFinance.
 *
 * Principe : chaque niveau de l'interface a une élévation distincte
 * qui crée une hiérarchie visuelle perceptible — exactement comme
 * Revolut, N26 ou Lydia.
 *
 * Niveaux définis :
 *
 *   L0 — fond de page          (0dp)
 *   L1 — cards normales        (2dp)   — transactions, widgets secondaires
 *   L2 — cards importantes     (4dp)   — stats cards, section cards
 *   L3 — hero card             (8dp)   — solde principal, hero banner
 *   L4 — modals/dialogs        (12dp)
 *   L5 — FAB / actions flottantes (18dp)
 *
 * Ombres colorées (API 28+) :
 *   Les ombres suivent la couleur de la card pour renforcer
 *   la palette terracotta/thème dynamique.
 */
public final class ElevationSystem {

    private ElevationSystem() {}

    // ── Niveaux d'élévation en dp ─────────────────────────────────
    public static final float L0 = 0f;
    public static final float L1 = 2f;
    public static final float L2 = 4f;
    public static final float L3 = 8f;
    public static final float L4 = 12f;
    public static final float L5 = 18f;

    // ─────────────────────────────────────────────────────────────
    // API publique
    // ─────────────────────────────────────────────────────────────

    /** Card de transaction, widget secondaire. */
    public static void applyL1(View view, Context ctx) {
        apply(view, ctx, L1, ThemeColors.shadow(), false);
    }

    /** Stats card, section card, budget card. */
    public static void applyL2(View view, Context ctx) {
        apply(view, ctx, L2, ThemeColors.shadow(), false);
    }

    /** Hero card (solde principal), card mise en avant. */
    public static void applyL3(View view, Context ctx) {
        apply(view, ctx, L3, ThemeColors.withAlpha(ThemeColors.primary(), 60), true);
    }

    /** Hero card avec couleur d'ombre personnalisée. */
    public static void applyL3Colored(View view, Context ctx, int color) {
        apply(view, ctx, L3, ThemeColors.withAlpha(color, 70), true);
    }

    /** Modal, dialog, bottom sheet. */
    public static void applyL4(View view, Context ctx) {
        apply(view, ctx, L4, ThemeColors.shadow(), true);
    }

    /** FAB, action flottante. */
    public static void applyL5(View view, Context ctx) {
        apply(view, ctx, L5, ThemeColors.withAlpha(ThemeColors.primary(), 80), true);
    }

    // ── Applique une élévation au widget de budget ────────────────
    /** Card Budget (légèrement plus saillante que les autres). */
    public static void applyBudgetCard(View view, Context ctx) {
        apply(view, ctx, L2, ThemeColors.withAlpha(ThemeColors.primary(), 30), false);
    }

    // ── Applique une élévation à une card success ─────────────────
    public static void applySuccessCard(View view, Context ctx) {
        apply(view, ctx, L2, ThemeColors.withAlpha(ThemeColors.success(), 40), false);
    }

    // ── Applique une élévation à une card danger ──────────────────
    public static void applyDangerCard(View view, Context ctx) {
        apply(view, ctx, L2, ThemeColors.withAlpha(ThemeColors.danger(), 40), false);
    }

    // ─────────────────────────────────────────────────────────────
    // Core
    // ─────────────────────────────────────────────────────────────

    private static void apply(View view, Context ctx, float elevDp,
                               int shadowColor, boolean useColoredShadow) {
        if (view == null || ctx == null) return;

        float px = DS.dp(ctx, (int) elevDp);
        view.setElevation(px);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && useColoredShadow) {
            // API 28+ : ombres colorées (spot = lumière directe, ambient = lumière ambiante)
            view.setOutlineSpotShadowColor(shadowColor);
            view.setOutlineAmbientShadowColor(ThemeColors.withAlpha(shadowColor, 80));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setTranslationZ(px * 0.4f);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers de migration depuis ShadowFactory (rétrocompat)
    // ─────────────────────────────────────────────────────────────

    /** Remplace ShadowFactory.card() avec hiérarchie correcte. */
    public static void card(View view, Context ctx) {
        applyL1(view, ctx);
    }

    /** Remplace ShadowFactory.hero() avec ombre colorée. */
    public static void hero(View view, Context ctx) {
        applyL3(view, ctx);
    }

    /** Remplace ShadowFactory.modal() */
    public static void modal(View view, Context ctx) {
        applyL4(view, ctx);
    }

    // ─────────────────────────────────────────────────────────────
    // Application batch — pour softenDashboardCards
    // ─────────────────────────────────────────────────────────────

    /**
     * Applique l'élévation correcte à toutes les cards d'un ViewGroup.
     * Plus haut dans la page → élévation légèrement plus forte.
     */
    public static void applyHierarchical(android.view.ViewGroup parent, Context ctx) {
        if (parent == null || ctx == null) return;

        int total = parent.getChildCount();
        for (int i = 0; i < total; i++) {
            View child = parent.getChildAt(i);
            if (child == null) continue;

            // Les 2 premiers enfants (hero + stats) → L2/L3
            // Les suivants → L1
            if (i == 0) {
                applyL3(child, ctx);
            } else if (i == 1) {
                applyL2(child, ctx);
            } else {
                applyL1(child, ctx);
            }
        }
    }
}
