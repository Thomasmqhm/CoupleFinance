package com.couplefinance.core.ui.components;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;

public final class PremiumSection {

    private PremiumSection() {
    }

    public static LinearLayout container(Context ctx) {
        LinearLayout section = new LinearLayout(ctx);
        section.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = DS.dp(ctx, DS.GAP_LG);
        section.setLayoutParams(lp);

        return section;
    }

    public static TextView title(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(ThemeColors.text());
        tv.setTextSize(DS.TEXT_SECTION);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        return tv;
    }

    public static TextView subtitle(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(ThemeColors.subtext());
        tv.setTextSize(DS.TEXT_SM);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        return tv;
    }

    public static TextView label(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(ThemeColors.primary());
        tv.setTextSize(DS.TEXT_LABEL);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setLetterSpacing(0.08f);
        return tv;
    }

    public static LinearLayout header(Context ctx, String title) {
        return header(ctx, title, null, null);
    }

    public static LinearLayout header(Context ctx, String title, String subtitle) {
        return header(ctx, title, subtitle, null);
    }

    public static LinearLayout header(Context ctx, String title, String subtitle, View action) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView tvTitle = title(ctx, title);
        texts.addView(tvTitle);

        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView tvSub = subtitle(ctx, subtitle);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
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

        if (action != null) {
            LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            actionLp.leftMargin = DS.dp(ctx, DS.GAP_SM);
            row.addView(action, actionLp);
        }

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowLp.bottomMargin = DS.dp(ctx, DS.GAP_SM);
        row.setLayoutParams(rowLp);

        return row;
    }

    public static LinearLayout pageHeader(Context ctx, String label, String title, String subtitle) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);

        if (label != null && !label.trim().isEmpty()) {
            TextView tvLabel = label(ctx, label);
            box.addView(tvLabel);
        }

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(title);
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
            TextView tvSub = subtitle(ctx, subtitle);
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

    public static void addGap(LinearLayout parent, Context ctx) {
        addGap(parent, ctx, DS.GAP);
    }

    public static void addGap(LinearLayout parent, Context ctx, int heightDp) {
        if (parent == null || ctx == null) {
            return;
        }

        View spacer = new View(ctx);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                DS.dp(ctx, heightDp)
        ));
        parent.addView(spacer);
    }
}