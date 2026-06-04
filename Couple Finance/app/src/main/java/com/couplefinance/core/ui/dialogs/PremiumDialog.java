package com.couplefinance.core.ui.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.components.PremiumButton;
import com.couplefinance.core.ui.effects.GradientFactory;
import com.couplefinance.core.ui.effects.ShadowFactory;

public final class PremiumDialog {

    private PremiumDialog() {
    }

    public static Builder builder(Activity activity) {
        return new Builder(activity);
    }

    public static class Builder {

        private final Activity activity;

        private String icon;
        private String title;
        private String subtitle;
        private View content;

        private String primaryText = "OK";
        private String secondaryText = "Annuler";

        private Runnable primaryAction;
        private Runnable secondaryAction;

        private boolean showSecondary = true;
        private boolean cancelable = true;
        private boolean dangerPrimary = false;
        private float widthRatio = 0.62f;

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

        /** Alias pour content() — compatibilité avec SettingsDialogs. */
        public Builder view(View content) {
            this.content = content;
            return this;
        }

        public Builder primary(String text, Runnable action) {
            this.primaryText = text;
            this.primaryAction = action;
            return this;
        }

        public Builder secondary(String text, Runnable action) {
            this.secondaryText = text;
            this.secondaryAction = action;
            this.showSecondary = true;
            return this;
        }

        public Builder noSecondary() {
            this.showSecondary = false;
            return this;
        }

        public Builder dangerPrimary() {
            this.dangerPrimary = true;
            return this;
        }

        public Builder cancelable(boolean cancelable) {
            this.cancelable = cancelable;
            return this;
        }

        public Builder width(float widthRatio) {
            if (widthRatio > 0.30f && widthRatio <= 0.98f) {
                this.widthRatio = widthRatio;
            }
            return this;
        }

        public AlertDialog build() {
            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(
                    DS.dp(activity, 22),
                    DS.dp(activity, 22),
                    DS.dp(activity, 22),
                    DS.dp(activity, 20)
            );

            root.setBackground(GradientFactory.bordered(
                    activity,
                    ThemeColors.modal(),
                    ThemeColors.border(),
                    DS.R_XL
            ));

            LinearLayout header = new LinearLayout(activity);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            if (icon != null && !icon.trim().isEmpty()) {
                TextView tvIcon = new TextView(activity);
                tvIcon.setText(icon);
                tvIcon.setTextSize(22f);
                tvIcon.setGravity(Gravity.CENTER);
                tvIcon.setBackground(GradientFactory.circle(ThemeColors.primarySoft()));

                int size = DS.dp(activity, 48);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(size, size);
                iconLp.rightMargin = DS.dp(activity, 12);
                header.addView(tvIcon, iconLp);
            }

            LinearLayout titleBox = new LinearLayout(activity);
            titleBox.setOrientation(LinearLayout.VERTICAL);

            if (title != null && !title.trim().isEmpty()) {
                TextView tvTitle = new TextView(activity);
                tvTitle.setText(title);
                tvTitle.setTextColor(ThemeColors.text());
                tvTitle.setTextSize(20f);
                tvTitle.setTypeface(null, Typeface.BOLD);
                tvTitle.setIncludeFontPadding(false);
                titleBox.addView(tvTitle);
            }

            if (subtitle != null && !subtitle.trim().isEmpty()) {
                TextView tvSub = new TextView(activity);
                tvSub.setText(subtitle);
                tvSub.setTextColor(ThemeColors.subtext());
                tvSub.setTextSize(DS.TEXT_SM);
                tvSub.setLineSpacing(2f, 1.05f);

                LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                subLp.topMargin = DS.dp(activity, 5);
                titleBox.addView(tvSub, subLp);
            }

            header.addView(titleBox, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            ));

            if ((title != null && !title.trim().isEmpty())
                    || (subtitle != null && !subtitle.trim().isEmpty())
                    || (icon != null && !icon.trim().isEmpty())) {
                root.addView(header);
            }

            if (content != null) {
                ScrollView scroll = new ScrollView(activity);
                scroll.setFillViewport(false);
                scroll.setVerticalScrollBarEnabled(false);
                scroll.addView(content);

                LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                contentLp.topMargin = DS.dp(activity, 18);
                root.addView(scroll, contentLp);
            }

            LinearLayout actions = new LinearLayout(activity);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            actionsLp.topMargin = DS.dp(activity, 18);

            // dialogRef est assigné avant que les listeners soient déclenchés,
            // ce qui garantit que dismiss() fonctionne toujours.
            final AlertDialog[] dialogRef = new AlertDialog[1];

            if (showSecondary) {
                TextView secondary = PremiumButton.secondary(activity, secondaryText);

                secondary.setOnClickListener(v -> {
                    if (secondaryAction != null) {
                        secondaryAction.run();
                    }
                    if (dialogRef[0] != null && dialogRef[0].isShowing()) {
                        dialogRef[0].dismiss();
                    }
                });

                LinearLayout.LayoutParams secLp = new LinearLayout.LayoutParams(
                        0,
                        DS.dp(activity, DS.BTN_HEIGHT),
                        1f
                );
                secLp.rightMargin = DS.dp(activity, DS.GAP_SM);
                actions.addView(secondary, secLp);
            }

            TextView primary = dangerPrimary
                    ? PremiumButton.danger(activity, primaryText)
                    : PremiumButton.primary(activity, primaryText);

            primary.setOnClickListener(v -> {
                // Fermer le dialog d'abord, puis lancer l'action.
                // L'action Firestore est async : elle se poursuit après
                // la fermeture du dialog et rappelle onSuccess/onError
                // qui affichent le toast et rechargent la liste.
                if (dialogRef[0] != null && dialogRef[0].isShowing()) {
                    dialogRef[0].dismiss();
                }
                if (primaryAction != null) {
                    primaryAction.run();
                }
            });

            actions.addView(primary, new LinearLayout.LayoutParams(
                    0,
                    DS.dp(activity, DS.BTN_HEIGHT),
                    1f
            ));

            root.addView(actions, actionsLp);

            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setView(root)
                    .setCancelable(cancelable)
                    .create();

            dialogRef[0] = dialog;
            root.setTag(dialog);

            // Configuration de la fenêtre AVANT l'affichage : on dimensionne
            // et on rend le fond transparent dès maintenant, pendant que la
            // fenêtre existe mais n'est pas encore visible. Cela évite que la
            // modale apparaisse en taille par défaut puis « saute » à sa
            // largeur finale (le décalage visuel bref à l'ouverture).
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

                int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
                int width = (int) (screenWidth * widthRatio);
                window.setLayout(width, LinearLayout.LayoutParams.WRAP_CONTENT);
            }

            // L'ombre dépend du rendu de la vue : on l'applique une fois la
            // modale réellement affichée (setOnShowListener), mais sans plus
            // toucher à la taille de la fenêtre.
            dialog.setOnShowListener(d -> ShadowFactory.modal(root, activity));

            return dialog;
        }

        public AlertDialog show() {
            AlertDialog dialog = build();

            // La taille et le fond sont déjà configurés dans build() :
            // on se contente d'afficher, sans redimensionner après coup.
            dialog.show();

            return dialog;
        }
    }
}
