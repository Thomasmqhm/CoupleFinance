package com.couplefinance.core.ui.components;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.Fmt;

public final class PremiumMetricRow {

    private PremiumMetricRow() {
    }

    public static LinearLayout text(Context ctx, String label, String value) {
        return create(ctx, label, value, ThemeColors.text());
    }

    public static LinearLayout money(Context ctx, String label, double amount) {
        return create(ctx, label, Fmt.money(amount), ThemeColors.text());
    }

    public static LinearLayout signedMoney(Context ctx, String label, double amount) {
        return create(
                ctx,
                label,
                Fmt.moneySigned(amount),
                amount >= 0 ? ThemeColors.success() : ThemeColors.danger()
        );
    }

    public static LinearLayout income(Context ctx, String label, double amount) {
        return create(ctx, label, Fmt.money(amount), ThemeColors.success());
    }

    public static LinearLayout expense(Context ctx, String label, double amount) {
        return create(ctx, label, "-" + Fmt.money(amount), ThemeColors.danger());
    }

    public static LinearLayout percent(Context ctx, String label, int percent) {
        return create(ctx, label, percent + "%", ThemeColors.primary());
    }

    public static LinearLayout warning(Context ctx, String label, String value) {
        return create(ctx, label, value, ThemeColors.warning());
    }

    public static LinearLayout info(Context ctx, String label, String value) {
        return create(ctx, label, value, ThemeColors.info());
    }

    public static LinearLayout create(Context ctx,
                                      String label,
                                      String value,
                                      int valueColor) {

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(
                0,
                DS.dp(ctx, 8),
                0,
                DS.dp(ctx, 8)
        );

        TextView tvLabel = new TextView(ctx);
        tvLabel.setText(label == null ? "" : label);
        tvLabel.setTextColor(ThemeColors.subtext());
        tvLabel.setTextSize(DS.TEXT_SM);
        tvLabel.setSingleLine(true);

        row.addView(tvLabel, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        TextView tvValue = new TextView(ctx);
        tvValue.setText(value == null ? "" : value);
        tvValue.setTextColor(valueColor);
        tvValue.setTextSize(DS.TEXT_SM);
        tvValue.setTypeface(null, Typeface.BOLD);
        tvValue.setGravity(Gravity.END);
        tvValue.setSingleLine(true);

        row.addView(tvValue, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        return row;
    }
}