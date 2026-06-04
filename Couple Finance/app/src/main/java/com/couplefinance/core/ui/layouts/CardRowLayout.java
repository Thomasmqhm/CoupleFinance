package com.couplefinance.core.ui.layouts;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import com.couplefinance.core.ui.DS;

public final class CardRowLayout {

    private CardRowLayout() {
    }

    public static LinearLayout row(Context ctx) {
        LinearLayout row = new LinearLayout(ctx);

        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        lp.bottomMargin = DS.dp(ctx, DS.GAP_SM);

        row.setLayoutParams(lp);

        return row;
    }

    public static LinearLayout centered(Context ctx) {
        LinearLayout row = row(ctx);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    public static LinearLayout spaced(Context ctx) {
        LinearLayout row = row(ctx);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    public static LinearLayout weighted(Context ctx) {
        LinearLayout row = row(ctx);
        row.setWeightSum(2f);
        return row;
    }

    public static LinearLayout weighted3(Context ctx) {
        LinearLayout row = row(ctx);
        row.setWeightSum(3f);
        return row;
    }

    public static LinearLayout weighted4(Context ctx) {
        LinearLayout row = row(ctx);
        row.setWeightSum(4f);
        return row;
    }

    public static LinearLayout.LayoutParams weight(Context ctx) {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                );

        lp.rightMargin = DS.dp(ctx, DS.GAP_SM);

        return lp;
    }

    public static LinearLayout.LayoutParams weightLast() {
        return new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
    }

    public static LinearLayout.LayoutParams weight(Context ctx, float weight) {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        weight
                );

        lp.rightMargin = DS.dp(ctx, DS.GAP_SM);

        return lp;
    }

    public static LinearLayout.LayoutParams weightLast(float weight) {
        return new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                weight
        );
    }

    public static void addGap(LinearLayout row,
                              Context ctx,
                              int widthDp) {

        if (row == null || ctx == null) {
            return;
        }

        View spacer = new View(ctx);

        spacer.setLayoutParams(
                new LinearLayout.LayoutParams(
                        DS.dp(ctx, widthDp),
                        1
                )
        );

        row.addView(spacer);
    }

    public static void addSmallGap(LinearLayout row,
                                   Context ctx) {

        addGap(row, ctx, DS.GAP_SM);
    }

    public static void addGap(LinearLayout row,
                              Context ctx) {

        addGap(row, ctx, DS.GAP);
    }

    public static void addLargeGap(LinearLayout row,
                                   Context ctx) {

        addGap(row, ctx, DS.GAP_LG);
    }
}