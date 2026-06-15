package com.couplefinance.ui.home;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.Fmt;

import java.util.Locale;

public final class HomeMemberCard {

    private HomeMemberCard() {}

    public static class Data {
        public final String name;
        public final String colorHex;
        public final boolean isJoint;
        public final boolean isCurrentUser;

        public double startBalance;
        public double income;
        public double expenses;
        public double upcomingExpenses = 0;
        public int upcomingCount = 0;
        public double currentBalance;
        public double forecastBalance;
        public boolean lockCurrentBalanceToStart = false;
        /** Solde réel synchronisé (banque). Si non-null, prioritaire pour "Solde actuel". */
        public Double liveBalance = null;
        public String avatar = null;

        public Data(String name, String colorHex, boolean isJoint, boolean isCurrentUser) {
            this.name = name;
            this.colorHex = colorHex;
            this.isJoint = isJoint;
            this.isCurrentUser = isCurrentUser;
        }

        public void compute() {
            if (liveBalance != null) {
                currentBalance = liveBalance;
            } else {
                currentBalance = lockCurrentBalanceToStart
                        ? startBalance
                        : startBalance + income - expenses;
            }
            forecastBalance = currentBalance - Math.max(0, upcomingExpenses);
        }
    }

    public static View build(Activity activity, Data data) {
        if (data == null) {
            data = new Data("Membre", "#C86B4A", false, false);
            data.compute();
        }

        int accentColor = ThemeColors.primary();

        if (!data.isJoint && data.colorHex != null) {
            try {
                accentColor = Color.parseColor(data.colorHex);
            } catch (Exception ignored) {}
        }

        if (data.isJoint) {
            accentColor = ThemeColors.blend(ThemeColors.primary(), Color.parseColor("#4A6B9A"), 0.50f);
        }

        final int accent = accentColor;

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
card.setPadding(
        DS.dp(activity, 13),
        DS.dp(activity, 13),
        DS.dp(activity, 13),
        DS.dp(activity, 12)
);

        GradientDrawable cardBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        ThemeColors.blend(ThemeColors.surfaceFloating(), Color.WHITE, 0.18f),
                        ThemeColors.surfaceFloating()
                }
        );
        cardBg.setCornerRadius(DS.dp(activity, 24));
        cardBg.setStroke(DS.dp(activity, 1), ThemeColors.withAlpha(accent, 28));
        card.setBackground(cardBg);
        card.setElevation(DS.dp(activity, 4));
        HomeDashboardStyle.applyPressEffect(card);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        View avatar = avatar(activity, data, accent);
LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(
        DS.dp(activity, 38),
        DS.dp(activity, 38)
);
avLp.rightMargin = DS.dp(activity, 10);
        header.addView(avatar, avLp);

        LinearLayout infoCol = new LinearLayout(activity);
        infoCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvName = new TextView(activity);
        tvName.setText(safeName(data));
        tvName.setTextColor(ThemeColors.textPrimary());
        tvName.setTextSize(15.5f);
        tvName.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tvName.setLetterSpacing(-0.01f);
        tvName.setIncludeFontPadding(false);
        tvName.setSingleLine(false);
        tvName.setMaxLines(2);
        tvName.setEllipsize(null);
        infoCol.addView(tvName, new LinearLayout.LayoutParams(-1, -2));

        TextView tvRole = new TextView(activity);
        tvRole.setText(data.isJoint ? "Compte commun" : (data.isCurrentUser ? "Vous" : "Membre du foyer"));
        tvRole.setTextColor(ThemeColors.textMuted());
        tvRole.setTextSize(11.5f);
        tvRole.setIncludeFontPadding(false);

        LinearLayout.LayoutParams roleLp = new LinearLayout.LayoutParams(-1, -2);
        roleLp.topMargin = DS.dp(activity, 4);
        infoCol.addView(tvRole, roleLp);

        header.addView(infoCol, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(header);

        LinearLayout balanceBlock = new LinearLayout(activity);
        balanceBlock.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams bbLp = new LinearLayout.LayoutParams(-1, -2);
        bbLp.topMargin = DS.dp(activity, 16);
        card.addView(balanceBlock, bbLp);

        TextView currentLabel = new TextView(activity);
        currentLabel.setText("Solde actuel");
        currentLabel.setTextColor(ThemeColors.textMuted());
        currentLabel.setTextSize(10.5f);
        currentLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        currentLabel.setLetterSpacing(0.07f);
        currentLabel.setIncludeFontPadding(false);
        balanceBlock.addView(currentLabel);

        double displayBalance = (data.liveBalance != null) ? data.liveBalance : data.currentBalance;
        TextView current = new TextView(activity);
        current.setText(fmt(displayBalance));
        current.setTextColor(displayBalance >= 0 ? accent : ThemeColors.danger());
        current.setTextSize(22f);
        current.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        current.setLetterSpacing(-0.035f);
        current.setIncludeFontPadding(false);

        LinearLayout.LayoutParams curLp = new LinearLayout.LayoutParams(-1, -2);
        curLp.topMargin = DS.dp(activity, 4);
        balanceBlock.addView(current, curLp);

        FrameLayout track = new FrameLayout(activity);
        track.setBackground(round(ThemeColors.withAlpha(ThemeColors.primary(), 16), DS.RADIUS_PILL, activity));

        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(-1, DS.dp(activity, 7));
        trLp.topMargin = DS.dp(activity, 14);
        card.addView(track, trLp);

        View fill = new View(activity);
        GradientDrawable fillBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        ThemeColors.blend(ThemeColors.primary(), Color.WHITE, 0.22f),
                        ThemeColors.primary()
                }
        );
        fillBg.setCornerRadius(DS.dp(activity, DS.RADIUS_PILL));
        fill.setBackground(fillBg);
        track.addView(fill, new FrameLayout.LayoutParams(0, -1));

        double consumed = Math.abs(data.expenses) + Math.abs(data.upcomingExpenses);
        double reference = Math.max(1, Math.abs(data.startBalance) + Math.abs(data.income));
        final float progress = (float) Math.max(0.08f, Math.min(1f, consumed / reference));

        track.post(() -> {
            int w = track.getWidth();
            if (w <= 0) return;

            ValueAnimator anim = ValueAnimator.ofInt(0, Math.round(w * progress));
            anim.setDuration(DS.ANIM_HERO);
            anim.setInterpolator(new DecelerateInterpolator(2f));
            anim.addUpdateListener(a -> {
                ViewGroup.LayoutParams lp = fill.getLayoutParams();
                lp.width = (int) a.getAnimatedValue();
                fill.setLayoutParams(lp);
            });
            anim.start();
        });

        LinearLayout grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(-1, -2);
        gridLp.topMargin = DS.dp(activity, 14);
        card.addView(grid, gridLp);

        LinearLayout row1 = new LinearLayout(activity);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        grid.addView(row1);

        row1.addView(
                metric(activity, "Début du mois", fmt(data.startBalance), ThemeColors.textSecondary()),
                new LinearLayout.LayoutParams(0, -2, 1f)
        );

        LinearLayout.LayoutParams r1b = new LinearLayout.LayoutParams(0, -2, 1f);
        r1b.leftMargin = DS.dp(activity, 10);
        row1.addView(
                metric(activity, "Projection", fmt(data.forecastBalance),
                        data.forecastBalance >= 0 ? ThemeColors.success() : ThemeColors.danger()),
                r1b
        );

        LinearLayout row2 = new LinearLayout(activity);
        row2.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(-1, -2);
        row2Lp.topMargin = DS.dp(activity, 10);
        grid.addView(row2, row2Lp);

        row2.addView(
                metric(activity, "Revenus", "+" + fmt(data.income), ThemeColors.success()),
                new LinearLayout.LayoutParams(0, -2, 1f)
        );

        LinearLayout.LayoutParams r2b = new LinearLayout.LayoutParams(0, -2, 1f);
        r2b.leftMargin = DS.dp(activity, 10);
        row2.addView(metric(activity, "Dépenses", "-" + fmt(data.expenses), ThemeColors.danger()), r2b);

        if (data.upcomingCount > 0 || data.upcomingExpenses > 0.01) {
            TextView upcoming = new TextView(activity);
            upcoming.setText(data.upcomingCount + " charge" + (data.upcomingCount > 1 ? "s" : "")
                    + " à venir · -" + fmt(data.upcomingExpenses));
            upcoming.setTextColor(ThemeColors.warning());
            upcoming.setTextSize(11.5f);
            upcoming.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            upcoming.setIncludeFontPadding(false);
            upcoming.setPadding(
                    DS.dp(activity, 10),
                    DS.dp(activity, 7),
                    DS.dp(activity, 10),
                    DS.dp(activity, 7)
            );
            upcoming.setBackground(round(ThemeColors.withAlpha(ThemeColors.warning(), 18), 999, activity));

            LinearLayout.LayoutParams upLp = new LinearLayout.LayoutParams(-1, -2);
            upLp.topMargin = DS.dp(activity, 12);
            card.addView(upcoming, upLp);
        }

        card.setAlpha(0f);
        card.setTranslationY(DS.dp(activity, 10));
        card.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(DS.ANIM_NORMAL)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();

        return card;
    }

    private static View avatar(Activity activity, Data data, int accent) {
        // Avatar animal (membre ayant choisi un avatar)
        if (!data.isJoint && data.avatar != null && !data.avatar.isEmpty()) {
            int res = activity.getResources().getIdentifier(
                    "avatar_" + data.avatar, "drawable", activity.getPackageName());
            if (res != 0) {
                ImageView iv = new ImageView(activity);
                iv.setImageResource(res);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int pad = DS.dp(activity, 2);
                iv.setPadding(pad, pad, pad, pad);
                GradientDrawable ring = new GradientDrawable();
                ring.setShape(GradientDrawable.OVAL);
                ring.setColor(ThemeColors.surface());
                ring.setStroke(DS.dp(activity, 2), ThemeColors.primary());
                iv.setBackground(ring);
                return iv;
            }
        }

        TextView avatar = new TextView(activity);

        String initial = data.isJoint
                ? "🏦"
                : safeName(data).substring(0, 1).toUpperCase(Locale.FRANCE);

        avatar.setText(initial);
        avatar.setGravity(Gravity.CENTER);
        avatar.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        avatar.setTextSize(data.isJoint ? 19f : 16f);
        avatar.setIncludeFontPadding(false);

        if (data.isJoint) {
            avatar.setTextColor(ThemeColors.textSecondary());

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(ThemeColors.surfaceSoft());
            bg.setStroke(DS.dp(activity, 1), ThemeColors.borderSoft());
            avatar.setBackground(bg);
        } else {
            avatar.setTextColor(Color.WHITE);

            GradientDrawable bg = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{
                            ThemeColors.blend(accent, Color.WHITE, 0.18f),
                            accent,
                            ThemeColors.blend(accent, Color.BLACK, 0.12f)
                    }
            );
            bg.setShape(GradientDrawable.OVAL);
            avatar.setBackground(bg);
        }

        return avatar;
    }

    private static LinearLayout metric(Activity activity, String label, String value, int valueColor) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(
                DS.dp(activity, 10),
                DS.dp(activity, 9),
                DS.dp(activity, 10),
                DS.dp(activity, 9)
        );
        box.setBackground(round(ThemeColors.withAlpha(valueColor, 12), 16, activity));

        TextView tvLabel = new TextView(activity);
        tvLabel.setText(label);
        tvLabel.setTextColor(ThemeColors.textMuted());
        tvLabel.setTextSize(10.5f);
        tvLabel.setIncludeFontPadding(false);
        box.addView(tvLabel);

        TextView tvValue = new TextView(activity);
        tvValue.setText(value);
        tvValue.setTextColor(valueColor);
        tvValue.setTextSize(13.5f);
        tvValue.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tvValue.setIncludeFontPadding(false);
        tvValue.setSingleLine(true);
        tvValue.setEllipsize(android.text.TextUtils.TruncateAt.END);

        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-1, -2);
        vp.topMargin = DS.dp(activity, 4);
        box.addView(tvValue, vp);

        return box;
    }

    private static GradientDrawable round(int color, int radiusDp, Activity activity) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(DS.dp(activity, radiusDp));
        return d;
    }

    private static String safeName(Data data) {
        return data == null || data.name == null || data.name.trim().isEmpty()
                ? "Membre"
                : data.name.trim();
    }

    private static String fmt(double amount) {
        return Fmt.money(amount);
    }
}