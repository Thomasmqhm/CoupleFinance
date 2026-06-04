package com.couplefinance.core.ui.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.components.PremiumCard;
import com.couplefinance.core.ui.effects.GradientFactory;

public final class ConfirmDialog {

    private ConfirmDialog() {
    }

    public static AlertDialog delete(Activity activity,
                                     String title,
                                     String message,
                                     Runnable onConfirm) {

        return danger(
                activity,
                "🗑️",
                title,
                message,
                "SUPPRIMER",
                onConfirm
        );
    }

    public static AlertDialog danger(Activity activity,
                                     String icon,
                                     String title,
                                     String message,
                                     String confirmText,
                                     Runnable onConfirm) {

        LinearLayout content = infoBox(
                activity,
                message,
                ThemeColors.dangerSoft(),
                ThemeColors.withAlpha(ThemeColors.danger(), 45),
                ThemeColors.text()
        );

        return PremiumDialog.builder(activity)
                .icon(icon)
                .title(title)
                .subtitle("Cette action est irréversible.")
                .content(content)
                .primary(confirmText, onConfirm)
                .dangerPrimary()
                .show();
    }

    public static AlertDialog warning(Activity activity,
                                      String icon,
                                      String title,
                                      String subtitle,
                                      String message,
                                      String confirmText,
                                      Runnable onConfirm) {

        LinearLayout content = infoBox(
                activity,
                message,
                ThemeColors.warningSoft(),
                ThemeColors.withAlpha(ThemeColors.warning(), 45),
                ThemeColors.text()
        );

        return PremiumDialog.builder(activity)
                .icon(icon)
                .title(title)
                .subtitle(subtitle)
                .content(content)
                .primary(confirmText, onConfirm)
                .show();
    }

    public static AlertDialog info(Activity activity,
                                   String icon,
                                   String title,
                                   String subtitle,
                                   String message,
                                   String confirmText,
                                   Runnable onConfirm) {

        LinearLayout content = infoBox(
                activity,
                message,
                ThemeColors.infoSoft(),
                ThemeColors.withAlpha(ThemeColors.info(), 45),
                ThemeColors.text()
        );

        return PremiumDialog.builder(activity)
                .icon(icon)
                .title(title)
                .subtitle(subtitle)
                .content(content)
                .primary(confirmText, onConfirm)
                .show();
    }

    public static AlertDialog simple(Activity activity,
                                     String icon,
                                     String title,
                                     String subtitle,
                                     Runnable onConfirm) {

        return PremiumDialog.builder(activity)
                .icon(icon)
                .title(title)
                .subtitle(subtitle)
                .primary("OK", onConfirm)
                .noSecondary()
                .show();
    }

    public static AlertDialog success(Activity activity,
                                      String title,
                                      String subtitle) {

        return PremiumDialog.builder(activity)
                .icon("✓")
                .title(title)
                .subtitle(subtitle)
                .primary("OK", null)
                .noSecondary()
                .show();
    }

    private static LinearLayout infoBox(Activity activity,
                                        String message,
                                        int bgColor,
                                        int borderColor,
                                        int textColor) {

        LinearLayout box = PremiumCard.base(activity);
        box.setBackground(GradientFactory.bordered(
                activity,
                bgColor,
                borderColor,
                DS.R_MD
        ));

        TextView tv = new TextView(activity);
        tv.setText(message);
        tv.setTextColor(textColor);
        tv.setTextSize(DS.TEXT_SM);
        tv.setLineSpacing(2f, 1.08f);

        box.addView(tv);

        return box;
    }
}