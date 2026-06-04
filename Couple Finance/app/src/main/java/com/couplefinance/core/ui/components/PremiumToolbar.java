package com.couplefinance.core.ui.components;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;

public final class PremiumToolbar {

    private PremiumToolbar() {
    }

    public static LinearLayout create(Context ctx, String title) {
        return create(ctx, title, null, null);
    }

    public static LinearLayout create(Context ctx, String title, String subtitle) {
        return create(ctx, title, subtitle, null);
    }

    public static LinearLayout create(Context ctx, String title, String subtitle, View action) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, DS.dp(ctx, DS.GAP));

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(title == null ? "" : title);
        tvTitle.setTextColor(ThemeColors.text());
        tvTitle.setTextSize(22f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setIncludeFontPadding(false);

        texts.addView(tvTitle);

        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView tvSub = new TextView(ctx);
            tvSub.setText(subtitle);
            tvSub.setTextColor(ThemeColors.subtext());
            tvSub.setTextSize(DS.TEXT_SM);

            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            subLp.topMargin = DS.dp(ctx, 4);
            texts.addView(tvSub, subLp);
        }

        row.addView(texts, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        if (action != null) {
            LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    DS.dp(ctx, 44)
            );
            actionLp.leftMargin = DS.dp(ctx, DS.GAP_SM);
            row.addView(action, actionLp);
        }

        return row;
    }

    public static LinearLayout page(Context ctx, String label, String title, String subtitle) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);

        if (label != null && !label.trim().isEmpty()) {
            TextView tvLabel = new TextView(ctx);
            tvLabel.setText(label);
            tvLabel.setTextColor(ThemeColors.primary());
            tvLabel.setTextSize(DS.TEXT_LABEL);
            tvLabel.setTypeface(null, Typeface.BOLD);
            tvLabel.setLetterSpacing(0.08f);
            box.addView(tvLabel);
        }

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(title == null ? "" : title);
        tvTitle.setTextColor(ThemeColors.text());
        tvTitle.setTextSize(DS.TEXT_TITLE);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setIncludeFontPadding(false);

        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleLp.topMargin = DS.dp(ctx, 6);
        box.addView(tvTitle, titleLp);

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
            subLp.topMargin = DS.dp(ctx, 6);
            box.addView(tvSub, subLp);
        }

        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        boxLp.bottomMargin = DS.dp(ctx, DS.GAP_LG);
        box.setLayoutParams(boxLp);

        return box;
    }

    public static TextView actionText(Context ctx, String text) {
        TextView tv = PremiumChip.glass(ctx, text);
        tv.setTextColor(ThemeColors.primary());
        return tv;
    }
}