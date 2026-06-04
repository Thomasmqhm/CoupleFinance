package com.couplefinance.core.ui.layouts;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.components.PremiumSection;

public final class SectionLayout {

    private SectionLayout() {
    }

    public static LinearLayout vertical(Context ctx) {
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        lp.bottomMargin = DS.dp(ctx, DS.GAP_LG);

        layout.setLayoutParams(lp);

        return layout;
    }

    public static LinearLayout centered(Context ctx) {
        LinearLayout layout = vertical(ctx);
        layout.setGravity(Gravity.CENTER);
        return layout;
    }

    public static LinearLayout horizontal(Context ctx) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        row.setLayoutParams(lp);

        return row;
    }

    public static LinearLayout header(Context ctx,
                                      String title) {

        return PremiumSection.header(
                ctx,
                title
        );
    }

    public static LinearLayout header(Context ctx,
                                      String title,
                                      String subtitle) {

        return PremiumSection.header(
                ctx,
                title,
                subtitle
        );
    }

    public static LinearLayout header(Context ctx,
                                      String title,
                                      String subtitle,
                                      View action) {

        return PremiumSection.header(
                ctx,
                title,
                subtitle,
                action
        );
    }

    public static void addSpacing(LinearLayout parent,
                                  Context ctx) {

        addSpacing(parent, ctx, DS.GAP);
    }

    public static void addSmallSpacing(LinearLayout parent,
                                       Context ctx) {

        addSpacing(parent, ctx, DS.GAP_SM);
    }

    public static void addLargeSpacing(LinearLayout parent,
                                       Context ctx) {

        addSpacing(parent, ctx, DS.GAP_LG);
    }

    public static void addSpacing(LinearLayout parent,
                                  Context ctx,
                                  int dp) {

        if (parent == null || ctx == null) {
            return;
        }

        View spacer = new View(ctx);

        spacer.setLayoutParams(
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        DS.dp(ctx, dp)
                )
        );

        parent.addView(spacer);
    }

    public static LinearLayout weightedRow(Context ctx) {
        LinearLayout row = horizontal(ctx);

        row.setWeightSum(2f);

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
}