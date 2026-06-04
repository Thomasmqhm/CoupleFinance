package com.couplefinance.core.ui.components;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.animations.PressAnimations;
import com.couplefinance.core.ui.effects.GradientFactory;
import com.couplefinance.core.ui.effects.ShadowFactory;

public final class PremiumButton {

    private PremiumButton() {
    }

    public static Button primary(Context ctx, String text) {
        Button button = base(ctx, text);
        button.setTextColor(ThemeColors.buttonTextOnPrimary());
        button.setBackground(GradientFactory.buttonPrimary(ctx));
        ShadowFactory.soft(button, ctx);
        PressAnimations.apply(button);
        return button;
    }

    public static Button secondary(Context ctx, String text) {
        Button button = base(ctx, text);
        button.setTextColor(ThemeColors.buttonText());
        button.setBackground(GradientFactory.buttonSecondary(ctx));
        ShadowFactory.subtle(button, ctx);
        PressAnimations.applySoft(button);
        return button;
    }

    public static Button danger(Context ctx, String text) {
        Button button = base(ctx, text);
        button.setTextColor(Color.WHITE);
        button.setBackground(GradientFactory.buttonDanger(ctx));
        ShadowFactory.soft(button, ctx);
        PressAnimations.apply(button);
        return button;
    }

    public static Button text(Context ctx, String text) {
        Button button = base(ctx, text);
        button.setTextColor(ThemeColors.primary());
        button.setBackground(null);
        PressAnimations.applySoft(button);
        return button;
    }

    public static Button glass(Context ctx, String text) {
        Button button = base(ctx, text);
        button.setTextColor(ThemeColors.text());
        button.setBackground(GradientFactory.glass(ctx, 28));
        ShadowFactory.subtle(button, ctx);
        PressAnimations.applySoft(button);
        return button;
    }

    public static Button success(Context ctx, String text) {
        Button button = base(ctx, text);
        button.setTextColor(Color.WHITE);
        button.setBackground(GradientFactory.successGradient(ctx, 28));
        ShadowFactory.soft(button, ctx);
        PressAnimations.apply(button);
        return button;
    }

    public static Button warning(Context ctx, String text) {
        Button button = base(ctx, text);
        button.setTextColor(Color.WHITE);
        button.setBackground(GradientFactory.warningGradient(ctx, 28));
        ShadowFactory.soft(button, ctx);
        PressAnimations.apply(button);
        return button;
    }

    public static Button compactPrimary(Context ctx, String text) {
        Button button = primary(ctx, text);
        button.setTextSize(DS.TEXT_XS);
        button.setPadding(DS.dp(ctx, 12), 0, DS.dp(ctx, 12), 0);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                DS.dp(ctx, 42)
        ));
        return button;
    }

    public static Button compactSecondary(Context ctx, String text) {
        Button button = secondary(ctx, text);
        button.setTextSize(DS.TEXT_XS);
        button.setPadding(DS.dp(ctx, 12), 0, DS.dp(ctx, 12), 0);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                DS.dp(ctx, 42)
        ));
        return button;
    }

    public static Button fullWidthPrimary(Context ctx, String text) {
        Button button = primary(ctx, text);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                DS.dp(ctx, DS.BTN_HEIGHT)
        ));
        return button;
    }

    public static Button fullWidthSecondary(Context ctx, String text) {
        Button button = secondary(ctx, text);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                DS.dp(ctx, DS.BTN_HEIGHT)
        ));
        return button;
    }

    public static Button fullWidthDanger(Context ctx, String text) {
        Button button = danger(ctx, text);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                DS.dp(ctx, DS.BTN_HEIGHT)
        ));
        return button;
    }

    private static Button base(Context ctx, String text) {
        Button button = new Button(ctx);

        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(DS.TEXT_SM);
        button.setTypeface(null, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);

        button.setStateListAnimator(null);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);

        button.setPadding(
                DS.dp(ctx, DS.PAD_INPUT),
                0,
                DS.dp(ctx, DS.PAD_INPUT),
                0
        );

        button.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                DS.dp(ctx, DS.BTN_HEIGHT)
        ));

        return button;
    }
}