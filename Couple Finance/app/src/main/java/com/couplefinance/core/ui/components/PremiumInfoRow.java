package com.couplefinance.core.ui.components;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.effects.GradientFactory;

public final class PremiumInfoRow {

    private PremiumInfoRow() {
    }

    public static LinearLayout create(Context ctx, String icon, String title, String subtitle) {
        return create(ctx, icon, title, subtitle, ThemeColors.primary());
    }

    public static LinearLayout create(Context ctx,
                                      String icon,
                                      String title,
                                      String subtitle,
                                      int color) {

        LinearLayout row = PremiumCard.row(ctx);
        row.setPadding(
                DS.dp(ctx, 16),
                DS.dp(ctx, 14),
                DS.dp(ctx, 16),
                DS.dp(ctx, 14)
        );

        TextView tvIcon = new TextView(ctx);
        tvIcon.setText(icon == null ? "" : icon);
        tvIcon.setTextColor(color);
        tvIcon.setTextSize(16f);
        tvIcon.setTypeface(null, Typeface.BOLD);
        tvIcon.setGravity(Gravity.CENTER);
        tvIcon.setBackground(GradientFactory.circle(
                ThemeColors.withAlpha(color, 32)
        ));

        int size = DS.dp(ctx, 38);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(size, size);
        iconLp.rightMargin = DS.dp(ctx, 12);
        row.addView(tvIcon, iconLp);

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(title == null ? "" : title);
        tvTitle.setTextColor(ThemeColors.text());
        tvTitle.setTextSize(DS.TEXT_BODY);
        tvTitle.setTypeface(null, Typeface.BOLD);

        texts.addView(tvTitle);

        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView tvSub = new TextView(ctx);
            tvSub.setText(subtitle);
            tvSub.setTextColor(ThemeColors.subtext());
            tvSub.setTextSize(DS.TEXT_SM);
            tvSub.setLineSpacing(2f, 1.05f);

            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            subLp.topMargin = DS.dp(ctx, 3);

            texts.addView(tvSub, subLp);
        }

        row.addView(texts, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        return row;
    }

    public static LinearLayout success(Context ctx, String title, String subtitle) {
        return create(ctx, "✓", title, subtitle, ThemeColors.success());
    }

    public static LinearLayout warning(Context ctx, String title, String subtitle) {
        return create(ctx, "!", title, subtitle, ThemeColors.warning());
    }

    public static LinearLayout danger(Context ctx, String title, String subtitle) {
        return create(ctx, "!", title, subtitle, ThemeColors.danger());
    }

    public static LinearLayout info(Context ctx, String title, String subtitle) {
        return create(ctx, "i", title, subtitle, ThemeColors.info());
    }
}