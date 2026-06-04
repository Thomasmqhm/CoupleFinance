package com.couplefinance.core.ui.layouts;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;

public final class PageLayout {

    private PageLayout() {
    }

    public static ScrollView scroll(Context ctx) {
        ScrollView scroll = new ScrollView(ctx);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(ThemeColors.background());
        return scroll;
    }

    public static LinearLayout root(Context ctx) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(
                DS.dp(ctx, DS.PAD_PAGE),
                DS.dp(ctx, 24),
                DS.dp(ctx, DS.PAD_PAGE),
                DS.dp(ctx, 42)
        );
        return root;
    }

    public static ScrollView scrollWithRoot(Context ctx, LinearLayout root) {
        ScrollView scroll = scroll(ctx);
        scroll.addView(root);
        return scroll;
    }

    public static LinearLayout screen(Context ctx) {
        return root(ctx);
    }

    public static void addGap(LinearLayout parent, Context ctx) {
        addGap(parent, ctx, DS.GAP);
    }

    public static void addSmallGap(LinearLayout parent, Context ctx) {
        addGap(parent, ctx, DS.GAP_SM);
    }

    public static void addLargeGap(LinearLayout parent, Context ctx) {
        addGap(parent, ctx, DS.GAP_LG);
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