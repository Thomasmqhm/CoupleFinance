package com.couplefinance.core.ui.layouts;

import android.content.Context;
import android.widget.LinearLayout;

import com.couplefinance.core.ui.DS;

public final class MetricGridLayout {

    private MetricGridLayout() {
    }

    public static LinearLayout grid2(Context ctx) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        lp.bottomMargin = DS.dp(ctx, DS.GAP);

        root.setLayoutParams(lp);

        return root;
    }

    public static LinearLayout row(Context ctx) {
        LinearLayout row = new LinearLayout(ctx);

        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(2f);

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        lp.bottomMargin = DS.dp(ctx, DS.GAP_SM);

        row.setLayoutParams(lp);

        return row;
    }

    public static LinearLayout row3(Context ctx) {
        LinearLayout row = new LinearLayout(ctx);

        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(3f);

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        lp.bottomMargin = DS.dp(ctx, DS.GAP_SM);

        row.setLayoutParams(lp);

        return row;
    }

    public static LinearLayout.LayoutParams item(Context ctx) {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                );

        lp.rightMargin = DS.dp(ctx, DS.GAP_SM);

        return lp;
    }

    public static LinearLayout.LayoutParams itemLast() {
        return new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
    }

    public static LinearLayout.LayoutParams item(Context ctx, float weight) {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        weight
                );

        lp.rightMargin = DS.dp(ctx, DS.GAP_SM);

        return lp;
    }

    public static void addMetricRow(LinearLayout grid,
                                    LinearLayout row) {

        if (grid == null || row == null) {
            return;
        }

        grid.addView(row);
    }

    public static LinearLayout autoRow(Context ctx, int count) {
        if (count <= 2) {
            return row(ctx);
        }

        return row3(ctx);
    }
}