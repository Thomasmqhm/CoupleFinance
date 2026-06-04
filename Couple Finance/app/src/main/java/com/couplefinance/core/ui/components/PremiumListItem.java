package com.couplefinance.core.ui.components;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.animations.PressAnimations;
import com.couplefinance.core.ui.effects.GradientFactory;

public final class PremiumListItem {

    private PremiumListItem() {
    }

    public static LinearLayout simple(Context ctx,
                                      String title,
                                      String subtitle) {

        return create(
                ctx,
                null,
                title,
                subtitle,
                null,
                ThemeColors.text(),
                false
        );
    }

    public static LinearLayout amount(Context ctx,
                                      String title,
                                      String subtitle,
                                      String amount,
                                      int amountColor) {

        return create(
                ctx,
                null,
                title,
                subtitle,
                amount,
                amountColor,
                false
        );
    }

    public static LinearLayout clickable(Context ctx,
                                         String title,
                                         String subtitle,
                                         String value,
                                         int valueColor) {

        LinearLayout row = create(
                ctx,
                null,
                title,
                subtitle,
                value,
                valueColor,
                true
        );

        row.setClickable(true);
        row.setFocusable(true);
        PressAnimations.applySoft(row);

        return row;
    }

    public static LinearLayout withIcon(Context ctx,
                                        String icon,
                                        String title,
                                        String subtitle,
                                        String value,
                                        int valueColor) {

        return create(
                ctx,
                icon,
                title,
                subtitle,
                value,
                valueColor,
                false
        );
    }

    public static LinearLayout clickableWithIcon(Context ctx,
                                                 String icon,
                                                 String title,
                                                 String subtitle,
                                                 String value,
                                                 int valueColor) {

        LinearLayout row = create(
                ctx,
                icon,
                title,
                subtitle,
                value,
                valueColor,
                true
        );

        row.setClickable(true);
        row.setFocusable(true);
        PressAnimations.applySoft(row);

        return row;
    }

    public static LinearLayout transaction(Context ctx,
                                           String title,
                                           String subtitle,
                                           String amount,
                                           boolean income) {

        return withIcon(
                ctx,
                income ? "↑" : "↓",
                title,
                subtitle,
                amount,
                income ? ThemeColors.success() : ThemeColors.danger()
        );
    }

    public static LinearLayout info(Context ctx,
                                    String title,
                                    String subtitle) {

        return withIcon(
                ctx,
                "i",
                title,
                subtitle,
                null,
                ThemeColors.info()
        );
    }

    public static LinearLayout warning(Context ctx,
                                       String title,
                                       String subtitle) {

        return withIcon(
                ctx,
                "!",
                title,
                subtitle,
                null,
                ThemeColors.warning()
        );
    }

    public static LinearLayout danger(Context ctx,
                                      String title,
                                      String subtitle) {

        return withIcon(
                ctx,
                "!",
                title,
                subtitle,
                null,
                ThemeColors.danger()
        );
    }

    private static LinearLayout create(Context ctx,
                                       String icon,
                                       String title,
                                       String subtitle,
                                       String value,
                                       int valueColor,
                                       boolean showChevron) {

        LinearLayout row = PremiumCard.row(ctx);
        row.setPadding(
                DS.dp(ctx, 16),
                DS.dp(ctx, 13),
                DS.dp(ctx, 16),
                DS.dp(ctx, 13)
        );

        if (icon != null && !icon.trim().isEmpty()) {
            TextView tvIcon = new TextView(ctx);
            tvIcon.setText(icon);
            tvIcon.setTextColor(valueColor);
            tvIcon.setTextSize(16f);
            tvIcon.setTypeface(null, Typeface.BOLD);
            tvIcon.setGravity(Gravity.CENTER);
            tvIcon.setBackground(
                    GradientFactory.circle(
                            ThemeColors.withAlpha(valueColor, 32)
                    )
            );

            int size = DS.dp(ctx, 38);
            LinearLayout.LayoutParams iconLp =
                    new LinearLayout.LayoutParams(size, size);
            iconLp.rightMargin = DS.dp(ctx, 12);

            row.addView(tvIcon, iconLp);
        }

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(title == null ? "" : title);
        tvTitle.setTextColor(ThemeColors.text());
        tvTitle.setTextSize(DS.TEXT_BODY);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setSingleLine(true);

        texts.addView(tvTitle);

        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView tvSub = new TextView(ctx);
            tvSub.setText(subtitle);
            tvSub.setTextColor(ThemeColors.subtext());
            tvSub.setTextSize(DS.TEXT_SM);
            tvSub.setSingleLine(true);

            LinearLayout.LayoutParams subLp =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
            subLp.topMargin = DS.dp(ctx, 2);

            texts.addView(tvSub, subLp);
        }

        row.addView(texts, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        if (value != null && !value.trim().isEmpty()) {
            TextView tvValue = new TextView(ctx);
            tvValue.setText(value);
            tvValue.setTextColor(valueColor);
            tvValue.setTextSize(DS.TEXT_BODY);
            tvValue.setTypeface(null, Typeface.BOLD);
            tvValue.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            tvValue.setSingleLine(true);

            LinearLayout.LayoutParams valueLp =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
            valueLp.leftMargin = DS.dp(ctx, 10);

            row.addView(tvValue, valueLp);
        }

        if (showChevron) {
            TextView chevron = new TextView(ctx);
            chevron.setText("›");
            chevron.setTextColor(ThemeColors.subtext());
            chevron.setTextSize(24f);
            chevron.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams chLp =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
            chLp.leftMargin = DS.dp(ctx, 8);

            row.addView(chevron, chLp);
        }

        return row;
    }
}