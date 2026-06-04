package com.couplefinance.core.ui.animations;

import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.couplefinance.core.ui.Fmt;

import java.util.Locale;

public final class CountAnimations {

    private static final DecelerateInterpolator DECELERATE =
            new DecelerateInterpolator();

    private CountAnimations() {
    }

    // ─────────────────────────────
    // INTEGER
    // ─────────────────────────────

    public static void animateInt(TextView tv, int from, int to) {
        animateInt(tv, from, to, 900);
    }

    public static void animateInt(TextView tv,
                                  int from,
                                  int to,
                                  long duration) {
        if (tv == null) {
            return;
        }

        ValueAnimator animator = ValueAnimator.ofInt(from, to);

        animator.setDuration(duration);
        animator.setInterpolator(DECELERATE);

        animator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            tv.setText(String.valueOf(value));
        });

        animator.start();
    }

    // ─────────────────────────────
    // DECIMAL
    // ─────────────────────────────

    public static void animateDecimal(TextView tv,
                                      double from,
                                      double to) {
        animateDecimal(tv, from, to, 1000, 2);
    }

    public static void animateDecimal(TextView tv,
                                      double from,
                                      double to,
                                      long duration,
                                      int decimals) {
        if (tv == null) {
            return;
        }

        ValueAnimator animator = ValueAnimator.ofFloat(
                (float) from,
                (float) to
        );

        animator.setDuration(duration);
        animator.setInterpolator(DECELERATE);

        animator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();

            String format = "%." + decimals + "f";
            tv.setText(String.format(
                    Locale.FRANCE,
                    format,
                    value
            ));
        });

        animator.start();
    }

    // ─────────────────────────────
    // MONEY
    // ─────────────────────────────

    public static void animateMoney(TextView tv,
                                    double from,
                                    double to) {
        animateMoney(tv, from, to, 1100);
    }

    public static void animateMoney(TextView tv,
                                    double from,
                                    double to,
                                    long duration) {
        if (tv == null) {
            return;
        }

        ValueAnimator animator = ValueAnimator.ofFloat(
                (float) from,
                (float) to
        );

        animator.setDuration(duration);
        animator.setInterpolator(DECELERATE);

        animator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            tv.setText(Fmt.money(value));
        });

        animator.start();
    }

    public static void animateSignedMoney(TextView tv,
                                          double from,
                                          double to) {
        animateSignedMoney(tv, from, to, 1100);
    }

    public static void animateSignedMoney(TextView tv,
                                          double from,
                                          double to,
                                          long duration) {
        if (tv == null) {
            return;
        }

        ValueAnimator animator = ValueAnimator.ofFloat(
                (float) from,
                (float) to
        );

        animator.setDuration(duration);
        animator.setInterpolator(DECELERATE);

        animator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            tv.setText(Fmt.moneySigned(value));
        });

        animator.start();
    }

    // ─────────────────────────────
    // PERCENT
    // ─────────────────────────────

    public static void animatePercent(TextView tv,
                                      int from,
                                      int to) {
        animatePercent(tv, from, to, 800);
    }

    public static void animatePercent(TextView tv,
                                      int from,
                                      int to,
                                      long duration) {
        if (tv == null) {
            return;
        }

        ValueAnimator animator = ValueAnimator.ofInt(from, to);

        animator.setDuration(duration);
        animator.setInterpolator(DECELERATE);

        animator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            tv.setText(value + "%");
        });

        animator.start();
    }

    // ─────────────────────────────
    // PROGRESS BAR
    // ─────────────────────────────

    public static void animateProgress(ProgressBar pb,
                                       int from,
                                       int to) {
        animateProgress(pb, from, to, 850);
    }

    public static void animateProgress(ProgressBar pb,
                                       int from,
                                       int to,
                                       long duration) {
        if (pb == null) {
            return;
        }

        ValueAnimator animator = ValueAnimator.ofInt(from, to);

        animator.setDuration(duration);
        animator.setInterpolator(DECELERATE);

        animator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            pb.setProgress(value);
        });

        animator.start();
    }

    // ─────────────────────────────
    // SCALE POP
    // ─────────────────────────────

    public static void pulse(TextView tv) {
        if (tv == null) {
            return;
        }

        tv.animate()
                .scaleX(1.08f)
                .scaleY(1.08f)
                .setDuration(120)
                .withEndAction(() ->
                        tv.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(160)
                                .start()
                )
                .start();
    }
}