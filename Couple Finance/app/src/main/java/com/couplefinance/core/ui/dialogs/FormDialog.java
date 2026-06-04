package com.couplefinance.core.ui.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.LinearLayout;

import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.components.PremiumInput;
import com.couplefinance.core.ui.components.PremiumSelector;

public final class FormDialog {

    private FormDialog() {
    }

    public static LinearLayout form(Activity activity) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static void addField(LinearLayout form, Activity activity, String label, View input) {
        if (form == null || activity == null || input == null) {
            return;
        }

        LinearLayout block = new LinearLayout(activity);
        block.setOrientation(LinearLayout.VERTICAL);

        block.addView(PremiumInput.label(activity, label));
        block.addView(input);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = DS.dp(activity, DS.GAP_SM);

        form.addView(block, lp);
    }

    public static void addText(LinearLayout form, Activity activity, String label, String hint) {
        addField(form, activity, label, PremiumInput.normal(activity, hint));
    }

    public static void addNumeric(LinearLayout form, Activity activity, String label, String hint) {
        addField(form, activity, label, PremiumInput.numeric(activity, hint));
    }

    public static void addSelector(LinearLayout form,
                                   Activity activity,
                                   String label,
                                   String[] items,
                                   int[] selectedIndex) {
        addField(form, activity, label, PremiumSelector.selector(activity, items, selectedIndex));
    }

    public static AlertDialog show(Activity activity,
                                   String icon,
                                   String title,
                                   String subtitle,
                                   View content,
                                   String primaryText,
                                   Runnable onPrimary) {

        return PremiumDialog.builder(activity)
                .icon(icon)
                .title(title)
                .subtitle(subtitle)
                .content(content)
                .primary(primaryText, onPrimary)
                .show();
    }

    public static AlertDialog showNoCancel(Activity activity,
                                           String icon,
                                           String title,
                                           String subtitle,
                                           View content,
                                           String primaryText,
                                           Runnable onPrimary) {

        return PremiumDialog.builder(activity)
                .icon(icon)
                .title(title)
                .subtitle(subtitle)
                .content(content)
                .primary(primaryText, onPrimary)
                .noSecondary()
                .show();
    }
}