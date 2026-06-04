package com.couplefinance.core.theme;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * ThemeConnector — Connecte n'importe quelle vue au thème dynamique.
 *
 * Problème résolu :
 * Agenda, Répartition et Virements utilisent encore DS.BG, DS.DARK, DS.PRIMARY
 * (constantes statiques figées). Quand l'utilisateur change de thème, ces vues
 * restent à la couleur originale.
 *
 * Solution :
 * ThemeConnector.apply(rootView) parcourt récursivement toutes les vues
 * et met à jour les couleurs connues vers ThemeColors dynamiques.
 *
 * Usage :
 *   // Dans getView() de chaque View concernée, après le build :
 *   ThemeConnector.apply(scroll);
 *
 * Fonctionnement :
 *   - Fond blanc / beige fixe → ThemeColors.background() / ThemeColors.card()
 *   - Texte foncé fixe → ThemeColors.text()
 *   - Texte gris fixe → ThemeColors.subtext()
 *   - Fond primary fixe → ThemeColors.primary()
 *   - Fond card fixe → ThemeColors.card()
 */
public final class ThemeConnector {

    // Couleurs fixes connues dans le projet (DS.BG, DS.CARD, etc.)
    private static final int[] KNOWN_BACKGROUNDS = {
            0xFFF7EFE7,  // DS.BG — beige clair
            0xFFF9F5F2,  // variante fond clair
            0xFFFFFFFF,  // blanc pur (card)
            0xFFFFFDFC,  // blanc cassé (cardAlt)
            0xFFF0EAE5,  // fond secondaire
            0xFFEDE5DC,  // fond tertiaire
    };

    private static final int[] KNOWN_TEXT_DARK = {
            0xFF1A2E2B,  // DS.DARK
            0xFF2E2926,  // variante foncée
            0xFF202020,  // quasi-noir
            0xFF3A3531,  // brun foncé
    };

    private static final int[] KNOWN_TEXT_SECONDARY = {
            0xFF6D5E55,  // DS.SECONDARY
            0xFF8D8680,  // gris chaud
            0xFF94A3B8,  // gris bleu
            0xFFA68A78,  // beige moyen
            0xFF9E8A82,  // gris rosé
    };

    private static final int[] KNOWN_PRIMARY = {
            0xFFC0614A,  // terracotta principal
            0xFFB06A57,  // variante
            0xFFD88F7A,  // terracotta clair
    };

    private ThemeConnector() {}

    // ─────────────────────────────────────────────────────────────
    // API publique
    // ─────────────────────────────────────────────────────────────

    /**
     * Applique le thème dynamique à toute la hiérarchie de vues.
     * À appeler après getView() dans Agenda, Répartition, Virements.
     */
    public static void apply(View root) {
        if (root == null) return;
        applyToView(root);
        if (root instanceof ViewGroup) applyRecursive((ViewGroup) root);
    }

    /**
     * Applique uniquement le fond de la vue racine (ScrollView/LinearLayout).
     */
    public static void applyBackground(View root) {
        if (root == null) return;
        root.setBackgroundColor(ThemeColors.background());
    }

    // ─────────────────────────────────────────────────────────────
    // Récursion
    // ─────────────────────────────────────────────────────────────

    private static void applyRecursive(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            applyToView(child);
            if (child instanceof ViewGroup) applyRecursive((ViewGroup) child);
        }
    }

    private static void applyToView(View view) {
        if (view == null) return;

        // ── Fond ──────────────────────────────────────────────────
        android.graphics.drawable.Drawable bg = view.getBackground();

        if (bg instanceof ColorDrawable) {
            int color = ((ColorDrawable) bg).getColor();
            if (isKnownBackground(color)) {
                // ScrollView et LinearLayout racines → background
                if (view instanceof ScrollView || isRootContainer(view)) {
                    view.setBackgroundColor(ThemeColors.background());
                } else {
                    view.setBackgroundColor(ThemeColors.card());
                }
            } else if (isKnownPrimary(color)) {
                view.setBackgroundColor(ThemeColors.primary());
            } else if (isKnownTextDark(color)) {
                // Ne pas changer les fonds très sombres (texte blanc sur fond foncé)
            }
        } else if (bg instanceof GradientDrawable) {
            // GradientDrawable avec couleur solide connue → mettre à jour
            // On ne peut pas lire facilement la couleur d'un GradientDrawable
            // sans API interne, donc on skip (les cards créées avec GradientFactory
            // sont déjà dynamiques).
        }

        // ── Couleur texte ─────────────────────────────────────────
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            int textColor = tv.getCurrentTextColor();

            if (isKnownTextDark(textColor)) {
                tv.setTextColor(ThemeColors.text());
            } else if (isKnownTextSecondary(textColor)) {
                tv.setTextColor(ThemeColors.subtext());
            } else if (isKnownPrimary(textColor)) {
                tv.setTextColor(ThemeColors.primary());
            }
        }

        // ── EditText ──────────────────────────────────────────────
        if (view instanceof EditText) {
            EditText et = (EditText) view;
            if (isKnownTextDark(et.getCurrentTextColor()))
                et.setTextColor(ThemeColors.text());
            et.setHintTextColor(ThemeColors.muted());
        }

        // ── Button ────────────────────────────────────────────────
        if (view instanceof Button) {
            Button btn = (Button) view;
            int textColor = btn.getCurrentTextColor();
            if (isKnownPrimary(textColor)) btn.setTextColor(ThemeColors.primary());
            else if (isKnownTextDark(textColor)) btn.setTextColor(ThemeColors.text());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers de reconnaissance de couleur
    // ─────────────────────────────────────────────────────────────

    private static boolean isKnownBackground(int color) {
        for (int c : KNOWN_BACKGROUNDS) if (similar(color, c)) return true;
        return false;
    }

    private static boolean isKnownTextDark(int color) {
        for (int c : KNOWN_TEXT_DARK) if (similar(color, c)) return true;
        return false;
    }

    private static boolean isKnownTextSecondary(int color) {
        for (int c : KNOWN_TEXT_SECONDARY) if (similar(color, c)) return true;
        return false;
    }

    private static boolean isKnownPrimary(int color) {
        for (int c : KNOWN_PRIMARY) if (similar(color, c)) return true;
        return false;
    }

    /**
     * Deux couleurs sont "similaires" si leurs composantes R, G, B
     * diffèrent de moins de 15 sur 255.
     */
    private static boolean similar(int a, int b) {
        int dr = Math.abs(android.graphics.Color.red(a)   - android.graphics.Color.red(b));
        int dg = Math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b));
        int db = Math.abs(android.graphics.Color.blue(a)  - android.graphics.Color.blue(b));
        return dr < 15 && dg < 15 && db < 15;
    }

    private static boolean isRootContainer(View view) {
        return view instanceof LinearLayout
                && (view.getLayoutParams() == null
                || view.getLayoutParams().width == ViewGroup.LayoutParams.MATCH_PARENT);
    }
}
