package com.couplefinance.core.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.theme.ThemeDrawable;
import com.couplefinance.core.ui.dialogs.PremiumDialog;

/**
 * AppDialog — Façade haut niveau pour les dialogs de l'application.
 *
 * Délègue entièrement à PremiumDialog.Builder.
 * La configuration de la Window (gravity, width, background) est gérée
 * dans PremiumDialog.build() — ne pas la dupliquer ici.
 *
 * widthRatio = 0.88f par défaut (88% de l'écran), transmis via .width().
 */
public final class AppDialog {

    private AppDialog() {
    }

    public static void confirm(Activity activity,
                               String title,
                               String message,
                               String confirmLabel,
                               Runnable onConfirm) {
        new Builder(activity)
                .icon("⚠")
                .title(title)
                .subtitle(message)
                .primaryBtn(confirmLabel, onConfirm)
                .show();
    }

    public static void alert(Activity activity, String title, String message) {
        new Builder(activity)
                .icon("ℹ")
                .title(title)
                .subtitle(message)
                .primaryBtn("OK", null)
                .cancelable(false)
                .noCancelBtn()
                .show();
    }

    public static void error(Activity activity, String message) {
        new Builder(activity)
                .icon("✕")
                .title("Erreur")
                .subtitle(message)
                .primaryBtn("OK", null)
                .noCancelBtn()
                .show();
    }

    public static class Builder {

        private final Activity activity;

        private String icon = null;
        private String title = null;
        private String subtitle = null;
        private View content = null;

        private String primaryLabel = "OK";
        private Runnable primaryAction = null;

        private String cancelLabel = "ANNULER";
        private boolean showCancel = true;
        private boolean cancelable = true;

        private float widthRatio = 0.88f;

        public Builder(Activity activity) {
            this.activity = activity;
        }

        public Builder icon(String icon) {
            this.icon = icon;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder subtitle(String subtitle) {
            this.subtitle = subtitle;
            return this;
        }

        public Builder content(View content) {
            this.content = content;
            return this;
        }

        public Builder primaryBtn(String label, Runnable action) {
            this.primaryLabel = label;
            this.primaryAction = action;
            return this;
        }

        public Builder noCancelBtn() {
            this.showCancel = false;
            return this;
        }

        public Builder cancelBtn(String label) {
            this.cancelLabel = label;
            this.showCancel = true;
            return this;
        }

        public Builder cancelable(boolean cancelable) {
            this.cancelable = cancelable;
            return this;
        }

        public Builder width(float ratio) {
            this.widthRatio = ratio;
            return this;
        }

        public AlertDialog show() {
            AlertDialog dialog = build();
            dialog.show();
            return dialog;
        }

        public AlertDialog build() {
            PremiumDialog.Builder builder = PremiumDialog.builder(activity)
                    .icon(icon)
                    .title(title)
                    .subtitle(subtitle)
                    .content(content)
                    .primary(primaryLabel, primaryAction)
                    .cancelable(cancelable)
                    .width(widthRatio);

            if (showCancel) {
                builder.secondary(cancelLabel, null);
            } else {
                builder.noSecondary();
            }

            return builder.build();
        }
    }

    // ── Helpers UI pour les formulaires dans les dialogs ──────────────────────

    public static LinearLayout fieldColumn(Activity ctx, String label) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView tvLabel = new TextView(ctx);
        tvLabel.setText(label);
        tvLabel.setTextColor(ThemeColors.primary());
        tvLabel.setTextSize(DS.TEXT_XS);
        tvLabel.setTypeface(null, Typeface.BOLD);
        tvLabel.setLetterSpacing(0.08f);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = DS.dp(ctx, 8);
        col.addView(tvLabel, lp);

        return col;
    }

    public static LinearLayout fieldRow(Activity ctx) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = DS.dp(ctx, DS.GAP_LG);
        row.setLayoutParams(rp);

        return row;
    }

    public static TextView readonlyField(Activity ctx, String value) {
        TextView tv = new TextView(ctx);
        tv.setText(value);
        tv.setTextColor(ThemeColors.text());
        tv.setTextSize(DS.TEXT_BODY);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setBackground(ThemeDrawable.input(ctx));

        int pH = DS.dp(ctx, DS.PAD_INPUT);
        tv.setPadding(pH, 0, pH, 0);
        tv.setLayoutParams(new LinearLayout.LayoutParams(-1, DS.dp(ctx, DS.INPUT_HEIGHT)));

        return tv;
    }

    public static LinearLayout infoCard(Activity ctx) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);

        int p = DS.dp(ctx, DS.PAD_INPUT);
        card.setPadding(p, p, p, p);
        card.setBackground(ThemeDrawable.card(ctx));

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = DS.dp(ctx, DS.GAP_LG);
        card.setLayoutParams(cp);

        return card;
    }
}
