package com.couplefinance.core.ui.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.animations.FadeAnimations;
import com.couplefinance.core.ui.components.PremiumButton;
import com.couplefinance.core.ui.effects.GradientFactory;
import com.couplefinance.core.ui.effects.ShadowFactory;

public final class PremiumBottomSheet {

    private PremiumBottomSheet() {
    }

    public static Builder builder(Activity activity) {
        return new Builder(activity);
    }

    public static class Builder {

        private final Activity activity;

        private String title;
        private String subtitle;
        private String primaryText;
        private String secondaryText = "Annuler";

        private View content;

        private Runnable primaryAction;
        private Runnable secondaryAction;

        private boolean showPrimary = false;
        private boolean showSecondary = true;
        private boolean dangerPrimary = false;
        private boolean cancelable = true;

        public Builder(Activity activity) {
            this.activity = activity;
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

        public Builder primary(String text, Runnable action) {
            this.primaryText = text;
            this.primaryAction = action;
            this.showPrimary = true;
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

        public Dialog show() {
            Dialog dialog = new Dialog(activity);
            dialog.setCancelable(cancelable);

            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(
                    DS.dp(activity, DS.PAD_DIALOG),
                    DS.dp(activity, 14),
                    DS.dp(activity, DS.PAD_DIALOG),
                    DS.dp(activity, 24)
            );

            root.setBackground(GradientFactory.bordered(
                    activity,
                    ThemeColors.modal(),
                    ThemeColors.border(),
                    DS.R_XL
            ));

            View handle = new View(activity);
            handle.setBackground(GradientFactory.solid(
                    activity,
                    ThemeColors.withAlpha(ThemeColors.subtext(), 80),
                    99
            ));

            LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(
                    DS.dp(activity, 48),
                    DS.dp(activity, 5)
            );
            handleLp.gravity = Gravity.CENTER_HORIZONTAL;
            handleLp.bottomMargin = DS.dp(activity, 18);
            root.addView(handle, handleLp);

            if (title != null && !title.trim().isEmpty()) {
                TextView tvTitle = new TextView(activity);
                tvTitle.setText(title);
                tvTitle.setTextColor(ThemeColors.text());
                tvTitle.setTextSize(22f);
                tvTitle.setTypeface(null, Typeface.BOLD);
                tvTitle.setGravity(Gravity.CENTER);
                tvTitle.setIncludeFontPadding(false);

                root.addView(tvTitle);
            }

            if (subtitle != null && !subtitle.trim().isEmpty()) {
                TextView tvSub = new TextView(activity);
                tvSub.setText(subtitle);
                tvSub.setTextColor(ThemeColors.subtext());
                tvSub.setTextSize(DS.TEXT_SM);
                tvSub.setGravity(Gravity.CENTER);
                tvSub.setLineSpacing(2f, 1.08f);

                LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                subLp.topMargin = DS.dp(activity, 8);
                root.addView(tvSub, subLp);
            }

            if (content != null) {
                ScrollView scroll = new ScrollView(activity);
                scroll.setFillViewport(false);
                scroll.addView(content);

                LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                contentLp.topMargin = DS.dp(activity, 20);
                root.addView(scroll, contentLp);
            }

            if (showPrimary || showSecondary) {
                LinearLayout actions = new LinearLayout(activity);
                actions.setOrientation(LinearLayout.HORIZONTAL);
                actions.setGravity(Gravity.CENTER_VERTICAL);

                LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                actionsLp.topMargin = DS.dp(activity, 22);

                if (showSecondary) {
                    TextView secondary = PremiumButton.secondary(activity, secondaryText);
                    secondary.setOnClickListener(v -> {
                        if (secondaryAction != null) {
                            secondaryAction.run();
                        }
                        dialog.dismiss();
                    });

                    LinearLayout.LayoutParams secLp = new LinearLayout.LayoutParams(
                            0,
                            DS.dp(activity, DS.BTN_HEIGHT),
                            1f
                    );
                    secLp.rightMargin = DS.dp(activity, DS.GAP_SM);
                    actions.addView(secondary, secLp);
                }

                if (showPrimary) {
                    TextView primary = dangerPrimary
                            ? PremiumButton.danger(activity, primaryText)
                            : PremiumButton.primary(activity, primaryText);

                    primary.setOnClickListener(v -> {
                        if (primaryAction != null) {
                            primaryAction.run();
                        }
                    });

                    actions.addView(primary, new LinearLayout.LayoutParams(
                            0,
                            DS.dp(activity, DS.BTN_HEIGHT),
                            1f
                    ));
                }

                root.addView(actions, actionsLp);
            }

            dialog.setContentView(root);

            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }

            dialog.setOnShowListener(d -> {
                Window w = dialog.getWindow();
                if (w != null) {
                    w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

                    WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                    lp.copyFrom(w.getAttributes());
                    lp.width = WindowManager.LayoutParams.MATCH_PARENT;
                    lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
                    lp.gravity = Gravity.BOTTOM;
                    lp.dimAmount = 0.45f;

                    w.setAttributes(lp);
                    w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                }

                ShadowFactory.bottomSheet(root, activity);
                FadeAnimations.slideUp(root);
            });

            dialog.show();

            Window shownWindow = dialog.getWindow();
            if (shownWindow != null) {
                shownWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

                WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                lp.copyFrom(shownWindow.getAttributes());
                lp.width = WindowManager.LayoutParams.MATCH_PARENT;
                lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
                lp.gravity = Gravity.BOTTOM;
                lp.dimAmount = 0.45f;

                shownWindow.setAttributes(lp);
                shownWindow.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            }

            return dialog;
        }
    }
}