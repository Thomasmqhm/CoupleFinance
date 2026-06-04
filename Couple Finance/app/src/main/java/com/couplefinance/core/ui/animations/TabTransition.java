package com.couplefinance.core.ui.animations;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * TabTransition — Transition fluide entre onglets du dashboard.
 *
 * Remplace le cut brutal (removeAllViews + addView instantané)
 * par un enchaînement fade out → swap → fade in en 200ms total.
 *
 * Usage dans DashboardActivity.switchTo() :
 *
 *   TabTransition.swap(container, newView);
 *
 * Compatible Java 8, aucune dépendance externe.
 */
public final class TabTransition {

    /** Durée du fade out en ms. */
    private static final int FADE_OUT_MS = 100;

    /** Durée du fade in en ms. */
    private static final int FADE_IN_MS  = 160;

    private TabTransition() {}

    /**
     * Effectue la transition entre la view actuelle et la nouvelle.
     *
     * 1. Fade out de la view actuelle (100ms)
     * 2. Swap (removeAllViews + addView)
     * 3. Fade in de la nouvelle view (160ms)
     *
     * @param container  FrameLayout qui contient les vues
     * @param newView    Nouvelle vue à afficher
     */
    public static void swap(FrameLayout container, View newView) {
        if (container == null || newView == null) {
            // Fallback sans animation
            if (container != null) {
                container.removeAllViews();
                container.addView(newView);
            }
            return;
        }

        // Si le container est vide, pas besoin de fade out
        if (container.getChildCount() == 0) {
            newView.setAlpha(0f);
            container.addView(newView);
            fadeIn(newView);
            return;
        }

        View currentView = container.getChildAt(0);

        // ── Fade OUT de la vue actuelle ───────────────────────────
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(currentView, "alpha", 1f, 0f);
        fadeOut.setDuration(FADE_OUT_MS);
        fadeOut.setInterpolator(new DecelerateInterpolator());

        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // ── Swap ──────────────────────────────────────────
                container.removeAllViews();
                newView.setAlpha(0f);
                container.addView(newView);

                // ── Fade IN de la nouvelle vue ────────────────────
                fadeIn(newView);
            }
        });

        fadeOut.start();
    }

    private static void fadeIn(View view) {
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f);
        fadeIn.setDuration(FADE_IN_MS);
        fadeIn.setInterpolator(new DecelerateInterpolator(1.5f));
        fadeIn.start();
    }
}
