package com.couplefinance.core.ui.components;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;

public final class PremiumDivider {

    private PremiumDivider() {
    }

    public static View line(Context ctx) {
        View view = new View(ctx);
        view.setBackgroundColor(ThemeColors.divider());
        view.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                DS.dp(ctx, 1)
        ));
        return view;
    }

    public static View lineWithMargins(Context ctx) {
        View view = line(ctx);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                DS.dp(ctx, 1)
        );
        lp.topMargin = DS.dp(ctx, DS.GAP_SM);
        lp.bottomMargin = DS.dp(ctx, DS.GAP_SM);

        view.setLayoutParams(lp);
        return view;
    }

    public static View spacer(Context ctx, int heightDp) {
        View view = new View(ctx);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                DS.dp(ctx, heightDp)
        ));
        return view;
    }

    public static View gap(Context ctx) {
        return spacer(ctx, DS.GAP);
    }

    public static View smallGap(Context ctx) {
        return spacer(ctx, DS.GAP_SM);
    }

    public static View largeGap(Context ctx) {
        return spacer(ctx, DS.GAP_LG);
    }
}