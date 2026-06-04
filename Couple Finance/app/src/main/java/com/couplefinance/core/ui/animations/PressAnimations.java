package com.couplefinance.core.ui.animations;

import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

public final class PressAnimations {

    private static final DecelerateInterpolator DECELERATE = new DecelerateInterpolator();
    private static final OvershootInterpolator OVERSHOOT = new OvershootInterpolator(1.6f);

    private PressAnimations() {
    }

    public static void apply(View view) {
        apply(view, 0.96f, 90, 140);
    }

    public static void applySoft(View view) {
        apply(view, 0.985f, 80, 120);
    }

    public static void applyStrong(View view) {
        apply(view, 0.93f, 80, 150);
    }

    public static void apply(View view, float pressedScale, long downDuration, long upDuration) {
        if (view == null) {
            return;
        }

        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate()
                            .scaleX(pressedScale)
                            .scaleY(pressedScale)
                            .alpha(0.94f)
                            .setDuration(downDuration)
                            .setInterpolator(DECELERATE)
                            .start();
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(upDuration)
                            .setInterpolator(OVERSHOOT)
                            .start();
                    break;
            }

            return false;
        });
    }

    public static void clickPulse(View view) {
        if (view == null) {
            return;
        }

        view.animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(70)
                .setInterpolator(DECELERATE)
                .withEndAction(() ->
                        view.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(140)
                                .setInterpolator(OVERSHOOT)
                                .start()
                )
                .start();
    }

    public static void pop(View view) {
        if (view == null) {
            return;
        }

        view.setScaleX(0.92f);
        view.setScaleY(0.92f);
        view.setAlpha(0f);

        view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(220)
                .setInterpolator(OVERSHOOT)
                .start();
    }
}