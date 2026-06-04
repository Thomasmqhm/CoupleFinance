package com.couplefinance.core.ui.animations;

import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

public final class FadeAnimations {

    private static final DecelerateInterpolator DECELERATE = new DecelerateInterpolator();

    private FadeAnimations() {
    }

    public static void fadeIn(View view) {
        fadeIn(view, 180);
    }

    public static void fadeIn(View view, long duration) {
        if (view == null) {
            return;
        }

        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);

        view.animate()
                .alpha(1f)
                .setDuration(duration)
                .setInterpolator(DECELERATE)
                .start();
    }

    public static void fadeOut(View view) {
        fadeOut(view, 160);
    }

    public static void fadeOut(View view, long duration) {
        if (view == null) {
            return;
        }

        view.animate()
                .alpha(0f)
                .setDuration(duration)
                .setInterpolator(DECELERATE)
                .withEndAction(() -> view.setVisibility(View.GONE))
                .start();
    }

    public static void slideUp(View view) {
        slideUp(view, 220, 18);
    }

    public static void slideUp(View view, long duration, int offsetPx) {
        if (view == null) {
            return;
        }

        view.setAlpha(0f);
        view.setTranslationY(offsetPx);
        view.setVisibility(View.VISIBLE);

        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(duration)
                .setInterpolator(DECELERATE)
                .start();
    }

    public static void slideDown(View view) {
        slideDown(view, 200, 18);
    }

    public static void slideDown(View view, long duration, int offsetPx) {
        if (view == null) {
            return;
        }

        view.animate()
                .alpha(0f)
                .translationY(offsetPx)
                .setDuration(duration)
                .setInterpolator(DECELERATE)
                .withEndAction(() -> {
                    view.setVisibility(View.GONE);
                    view.setTranslationY(0f);
                })
                .start();
    }

    public static void staggerChildren(ViewGroup parent) {
        staggerChildren(parent, 35, 220);
    }

    public static void staggerChildren(ViewGroup parent, long delayStep, long duration) {
        if (parent == null) {
            return;
        }

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(18f);

            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i * delayStep)
                    .setDuration(duration)
                    .setInterpolator(DECELERATE)
                    .start();
        }
    }
}