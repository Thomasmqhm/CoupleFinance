package com.couplefinance.ui.settings;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * SettingsTabs — navigation horizontale premium des paramètres.
 *
 * Étape 4 : séparation claire entre les membres du foyer et le Compte Joint.
 * Les membres ne sont plus créés manuellement : chaque personne rejoint avec son
 * propre compte. Le Compte Joint devient une section dédiée.
 */
public class SettingsTabs {

    public interface TabListener {
        void onTabSelected(int index);
    }

    private final Activity activity;
    private final TabListener listener;

    private final String[] tabs = {
            "Compte",
            "Foyer",
            "Compte joint",
            "Catégories",
            "Charges fixes",
            "Apparence",
            "Données"
    };

    private TextView[] tabViews;

    public SettingsTabs(Activity activity, TabListener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    public View build() {
        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(0, 0, 0, 0);

        LinearLayout.LayoutParams params = SettingsStyles.matchWrap();
        params.bottomMargin = SettingsStyles.dp(activity, 24);
        scroll.setLayoutParams(params);

        tabViews = new TextView[tabs.length];

        for (int i = 0; i < tabs.length; i++) {
            final int index = i;

            TextView tv = new TextView(activity);
            tv.setText(tabs[i]);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setSingleLine(true);
            tv.setTextSize(14f);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setPadding(
                    SettingsStyles.dp(activity, 18),
                    SettingsStyles.dp(activity, 13),
                    SettingsStyles.dp(activity, 18),
                    SettingsStyles.dp(activity, 13)
            );

            LinearLayout.LayoutParams tvParams = SettingsStyles.wrapWrap();
            tvParams.rightMargin = SettingsStyles.dp(activity, 10);
            tv.setLayoutParams(tvParams);

            tv.setOnClickListener(v -> {
                select(index);
                if (listener != null) listener.onTabSelected(index);
            });

            tabViews[i] = tv;
            root.addView(tv);
        }

        scroll.addView(root);
        select(0);
        return scroll;
    }

    public void select(int index) {
        if (tabViews == null) return;

        for (int i = 0; i < tabViews.length; i++) {
            TextView tv = tabViews[i];
            if (tv == null) continue;

            if (i == index) {
                tv.setTextColor(android.graphics.Color.WHITE);
                tv.setBackground(SettingsStyles.primaryButton());
            } else {
                tv.setTextColor(SettingsStyles.text());
                tv.setBackground(SettingsStyles.secondaryButton());
            }
        }
    }

    public int count() {
        return tabs.length;
    }
}
