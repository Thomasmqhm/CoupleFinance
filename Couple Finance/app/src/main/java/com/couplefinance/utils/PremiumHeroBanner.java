package com.couplefinance.ui.utils;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.theme.ThemeDrawable;
import com.couplefinance.core.ui.DS;

import java.util.ArrayList;
import java.util.List;

public class PremiumHeroBanner {

    private static final int BANNER_HEIGHT_DP = -2; // wrap_content
    private static final int BANNER_PADDING_H_DP = 24;
    private static final int BANNER_PADDING_V_DP = 20;
    private static final int BANNER_MARGIN_BOTTOM_DP = 20;
    private static final int BANNER_CORNER_DP = 24;

    private static final int BOX_CORNER_DP = 16;
    private static final int BOX_PADDING_H_DP = 14;
    private static final int BOX_PADDING_V_DP = 12;
    private static final int BOX_GAP_DP = 10;

    private static final int BOX_GLASS_INT = Color.argb(44, 255, 255, 255);

    private final Activity activity;
    private final LinearLayout root;
    private final LinearLayout leftContainer;
    private final LinearLayout rightContainer;

    private int backgroundColor;

    private TextView tvMainLabel;
    private TextView tvMainValue;
    private TextView tvSubtitle;

    private final List<LinearLayout> statBoxes = new ArrayList<>();

    public PremiumHeroBanner(Activity activity, int backgroundColor) {
        this.activity = activity;
        this.backgroundColor = backgroundColor != 0 ? backgroundColor : ThemeColors.primary();

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.START);
        root.setClickable(false);
        root.setFocusable(false);
        root.setPadding(
                dp(BANNER_PADDING_H_DP),
                dp(BANNER_PADDING_V_DP),
                dp(BANNER_PADDING_H_DP),
                dp(BANNER_PADDING_V_DP)
        );
        root.setBackground(ThemeDrawable.rounded(this.backgroundColor, dp(BANNER_CORNER_DP)));

        LinearLayout.LayoutParams rootParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rootParams.bottomMargin = dp(BANNER_MARGIN_BOTTOM_DP);
        root.setLayoutParams(rootParams);

        leftContainer = new LinearLayout(activity);
        leftContainer.setOrientation(LinearLayout.VERTICAL);
        leftContainer.setGravity(Gravity.CENTER_VERTICAL);
        leftContainer.setClickable(false);
        leftContainer.setFocusable(false);

        LinearLayout.LayoutParams lpLeft = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lpLeft.bottomMargin = dp(14);

        rightContainer = new LinearLayout(activity);
        rightContainer.setOrientation(LinearLayout.HORIZONTAL);
        rightContainer.setGravity(Gravity.CENTER_VERTICAL);
        rightContainer.setClickable(false);
        rightContainer.setFocusable(false);

        LinearLayout.LayoutParams lpRight = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        root.addView(leftContainer, lpLeft);
        root.addView(rightContainer, lpRight);
    }

    public void setMainMetric(String label, String value, String subtitle) {
        leftContainer.removeAllViews();

        tvMainLabel = new TextView(activity);
        tvMainLabel.setText(label != null ? label.toUpperCase() : "");
        tvMainLabel.setTextSize(9.5f);
        tvMainLabel.setLetterSpacing(0.11f);
        tvMainLabel.setTextColor(labelColor());
        tvMainLabel.setTypeface(null, Typeface.BOLD);
        tvMainLabel.setIncludeFontPadding(false);
        tvMainLabel.setGravity(Gravity.START);
        leftContainer.addView(tvMainLabel);

        tvMainValue = new TextView(activity);
        tvMainValue.setText(value != null ? value : "—");
        tvMainValue.setTextSize(26f);
        tvMainValue.setTextColor(Color.WHITE);
        tvMainValue.setTypeface(null, Typeface.BOLD);
        tvMainValue.setIncludeFontPadding(false);
        tvMainValue.setSingleLine(false);
        tvMainValue.setMaxLines(2);
        tvMainValue.setGravity(Gravity.START);
        tvMainValue.setTag("banner_main_value");

        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        vp.topMargin = dp(9);
        leftContainer.addView(tvMainValue, vp);

        tvSubtitle = new TextView(activity);
        tvSubtitle.setText(subtitle != null ? subtitle : "");
        tvSubtitle.setTextSize(12.5f);
        tvSubtitle.setTextColor(labelColor());
        tvSubtitle.setIncludeFontPadding(false);
        tvSubtitle.setGravity(Gravity.START);
        tvSubtitle.setTag("banner_subtitle");
        tvSubtitle.setVisibility(subtitle != null && !subtitle.isEmpty() ? View.VISIBLE : View.GONE);

        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        sp.topMargin = dp(8);
        leftContainer.addView(tvSubtitle, sp);
    }

    public void addStatCard(String label, String value, String tag) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(
                dp(BOX_PADDING_H_DP),
                dp(BOX_PADDING_V_DP),
                dp(BOX_PADDING_H_DP),
                dp(BOX_PADDING_V_DP)
        );
        box.setClickable(false);
        box.setFocusable(false);
        box.setTag(tag != null ? tag : ("box_" + statBoxes.size()));
        box.setBackground(ThemeDrawable.rounded(BOX_GLASS_INT, dp(BOX_CORNER_DP)));

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );

        if (!statBoxes.isEmpty()) {
            bp.leftMargin = dp(BOX_GAP_DP);
        }

        rightContainer.addView(box, bp);
        statBoxes.add(box);

        TextView tvBoxLabel = new TextView(activity);
        tvBoxLabel.setText(label != null ? label.toUpperCase() : "");
        tvBoxLabel.setTextSize(9.5f);
        tvBoxLabel.setLetterSpacing(0.10f);
        tvBoxLabel.setTextColor(labelColor());
        tvBoxLabel.setTypeface(null, Typeface.BOLD);
        tvBoxLabel.setIncludeFontPadding(false);
        tvBoxLabel.setGravity(Gravity.START);
        tvBoxLabel.setSingleLine(true);
        tvBoxLabel.setTag((tag != null ? tag : "box") + "_label");
        box.addView(tvBoxLabel);

        TextView tvBoxValue = new TextView(activity);
        tvBoxValue.setText(value != null ? value : "—");
        tvBoxValue.setTextSize(16f);
        tvBoxValue.setTypeface(null, Typeface.BOLD);
        tvBoxValue.setTextColor(Color.WHITE);
        tvBoxValue.setIncludeFontPadding(false);
        tvBoxValue.setSingleLine(false);
        tvBoxValue.setMaxLines(2);
        tvBoxValue.setGravity(Gravity.START);
        tvBoxValue.setTag((tag != null ? tag : "box") + "_value");

        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        vp.topMargin = dp(9);
        box.addView(tvBoxValue, vp);
    }

    public void addStatCard(String label, String value) {
        addStatCard(label, value, null);
    }

    public void updateMainValue(final String newValue) {
        if (tvMainValue == null) return;

        activity.runOnUiThread(() ->
                tvMainValue.setText(newValue != null ? newValue : "—")
        );
    }

    public void updateSubtitle(final String newSubtitle) {
        if (tvSubtitle == null) return;

        activity.runOnUiThread(() -> {
            tvSubtitle.setText(newSubtitle != null ? newSubtitle : "");
            tvSubtitle.setVisibility(
                    newSubtitle != null && !newSubtitle.isEmpty()
                            ? View.VISIBLE
                            : View.GONE
            );
        });
    }

    public void updateStatCard(final String tag, final String newValue) {
        if (tag == null) return;

        activity.runOnUiThread(() -> {
            View v = rightContainer.findViewWithTag(tag + "_value");

            if (v instanceof TextView) {
                ((TextView) v).setText(newValue != null ? newValue : "—");
            }
        });
    }

    public void updateStatCardLabel(final String tag, final String newLabel) {
        if (tag == null) return;

        activity.runOnUiThread(() -> {
            View v = rightContainer.findViewWithTag(tag + "_label");

            if (v instanceof TextView) {
                ((TextView) v).setText(newLabel != null ? newLabel.toUpperCase() : "");
            }
        });
    }

    public void clearStatCards() {
        rightContainer.removeAllViews();
        statBoxes.clear();
    }

    public void setBannerColor(int color) {
        backgroundColor = color != 0 ? color : ThemeColors.primary();
        root.setBackground(ThemeDrawable.rounded(backgroundColor, dp(BANNER_CORNER_DP)));
    }

    public void setThemeColor() {
        setBannerColor(ThemeColors.primary());
    }

    public void setSubtitleColor(String colorHex) {
        if (tvSubtitle == null) return;

        try {
            tvSubtitle.setTextColor(Color.parseColor(colorHex));
        } catch (Exception ignored) {
        }
    }

    public void setMainValueColor(String colorHex) {
        if (tvMainValue == null) return;

        try {
            tvMainValue.setTextColor(Color.parseColor(colorHex));
        } catch (Exception ignored) {
        }
    }

    public void setMainValueTextSize(float sp) {
        if (tvMainValue == null) return;
        tvMainValue.setTextSize(sp);
    }

    public void addCustomRightView(View view) {
        if (view != null) {
            rightContainer.addView(view);
        }
    }

    public void addCustomLeftView(View view) {
        if (view != null) {
            leftContainer.addView(view);
        }
    }

    public LinearLayout getRightContainer() {
        return rightContainer;
    }

    public LinearLayout getLeftContainer() {
        return leftContainer;
    }

    public View getView() {
        return root;
    }

    public TextView getMainValueTextView() {
        return tvMainValue;
    }

    private int labelColor() {
        return Color.argb(225, 255, 255, 255);
    }

    private int dp(int v) {
        return DS.dp(activity, v);
    }
}