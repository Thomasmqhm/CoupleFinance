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
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.UserSession;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.data.BalanceManager;
import com.couplefinance.data.FinancialInsightManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HomeRenderer — Rendu premium des widgets dynamiques du Dashboard
 *
 * Responsabilités :
 * - Greeting personnalisé avec heure
 * - Progression du mois animée
 * - Animation du solde (count-up)
 * - Top catégories avec barres animées
 * - Widget Insights financiers
 * - Comparaison mois précédent
 *
 * Design : iOS 18 / Revolut / Apple Wallet
 */
public final class HomeRenderer {

    private final Activity activity;

    private final TextView tvGreeting;
    private final TextView tvGreetingEmoji;
    private final TextView tvMonthProgressLabel;
    private final TextView tvMonthProgressPct;
    private final TextView tvMonthProgressDetail;
    private final View viewMonthProgressFill;
    private final LinearLayout topCategoriesContainer;
    private final TextView tvTopCategoriesEmpty;
    private final TextView tvTopCategoriesTotal;
    private final LinearLayout financialInsightsWidget;

    private final Map<String, String> categoryEmojis;

    private double lastAnimatedBalance = 0;
    private boolean hasAnimatedOnce    = false;

    public HomeRenderer(Activity activity,
                        TextView tvGreeting,
                        TextView tvGreetingEmoji,
                        TextView tvMonthProgressLabel,
                        TextView tvMonthProgressPct,
                        TextView tvMonthProgressDetail,
                        View viewMonthProgressFill,
                        LinearLayout topCategoriesContainer,
                        TextView tvTopCategoriesEmpty,
                        TextView tvTopCategoriesTotal,
                        LinearLayout financialInsightsWidget,
                        Map<String, String> categoryEmojis) {
        this.activity               = activity;
        this.tvGreeting             = tvGreeting;
        this.tvGreetingEmoji        = tvGreetingEmoji;
        this.tvMonthProgressLabel   = tvMonthProgressLabel;
        this.tvMonthProgressPct     = tvMonthProgressPct;
        this.tvMonthProgressDetail  = tvMonthProgressDetail;
        this.viewMonthProgressFill  = viewMonthProgressFill;
        this.topCategoriesContainer = topCategoriesContainer;
        this.tvTopCategoriesEmpty   = tvTopCategoriesEmpty;
        this.tvTopCategoriesTotal   = tvTopCategoriesTotal;
        this.financialInsightsWidget = financialInsightsWidget;
        this.categoryEmojis         = categoryEmojis;
    }

    // ─────────────────────────────────────────────────────────────
    // GREETING
    // ─────────────────────────────────────────────────────────────

    public void updateGreeting() {
        if (tvGreeting == null) return;

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

        String greeting;
        String emoji;

        if (hour >= 5 && hour < 12) {
            greeting = "Bonjour";
            emoji    = "☀️";
        } else if (hour >= 12 && hour < 18) {
            greeting = "Bon après-midi";
            emoji    = "☀️";
        } else if (hour >= 18 && hour < 22) {
            greeting = "Bonsoir";
            emoji    = "🌙";
        } else {
            greeting = "Bonne nuit";
            emoji    = "🌙";
        }

        String name = UserSession.getInstance().getNameOrFallback();

        if (name != null && !name.trim().isEmpty() && !name.equalsIgnoreCase("Moi")) {
            tvGreeting.setText(greeting + " " + name);
        } else {
            tvGreeting.setText(greeting);
        }

        // Style premium — iOS 18 greeting
        tvGreeting.setTextColor(ThemeColors.textPrimary());
        tvGreeting.setTextSize(DS.TEXT_TITLE);
        tvGreeting.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tvGreeting.setLetterSpacing(-0.018f);
        tvGreeting.setIncludeFontPadding(false);

        if (tvGreetingEmoji != null) {
            tvGreetingEmoji.setText(emoji);
            tvGreetingEmoji.setTextSize(22f);
            tvGreetingEmoji.setIncludeFontPadding(false);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // PROGRESSION DU MOIS — barre animée premium
    // ─────────────────────────────────────────────────────────────

    public void updateMonthProgress() {
        if (viewMonthProgressFill == null) return;

        Calendar cal        = Calendar.getInstance();
        int currentDay      = cal.get(Calendar.DAY_OF_MONTH);
        int totalDays       = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int remaining       = totalDays - currentDay;

        final float progress = Math.min(1f, (float) currentDay / totalDays);
        final int pct        = Math.round(progress * 100);

        // Label
        if (tvMonthProgressLabel != null) {
            tvMonthProgressLabel.setText("Progression du mois");
            tvMonthProgressLabel.setTextColor(ThemeColors.textSecondary());
            tvMonthProgressLabel.setTextSize(DS.TEXT_CAPTION);
            tvMonthProgressLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            tvMonthProgressLabel.setLetterSpacing(0.04f);
            tvMonthProgressLabel.setIncludeFontPadding(false);
        }

        // Couleur selon avancement — vert → amber → rouge
        final int fillColor;
        if (pct >= 90) {
            fillColor = ThemeColors.danger();
        } else if (pct >= 75) {
            fillColor = ThemeColors.warning();
        } else {
            fillColor = ThemeColors.primary();
        }

        // Pourcentage
        if (tvMonthProgressPct != null) {
            tvMonthProgressPct.setTextColor(fillColor);
            tvMonthProgressPct.setTextSize(DS.TEXT_SUBTITLE);
            tvMonthProgressPct.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            tvMonthProgressPct.setLetterSpacing(-0.015f);
            tvMonthProgressPct.setIncludeFontPadding(false);

            // Animation count-up du pourcentage
            ValueAnimator pctAnim = ValueAnimator.ofInt(0, pct);
            pctAnim.setDuration(DS.ANIM_HERO);
            pctAnim.setInterpolator(new DecelerateInterpolator(1.8f));
            pctAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    tvMonthProgressPct.setText(a.getAnimatedValue() + "%");
                }
            });
            pctAnim.start();
        }

        // Détail jours restants
        if (tvMonthProgressDetail != null) {
            String detail;
            if (remaining == 0) {
                detail = "Dernier jour du mois";
            } else if (remaining == 1) {
                detail = "Jour " + currentDay + " sur " + totalDays + " · 1 jour restant";
            } else {
                detail = "Jour " + currentDay + " sur " + totalDays + " · " + remaining + " jours restants";
            }
            tvMonthProgressDetail.setText(detail);
            tvMonthProgressDetail.setTextColor(ThemeColors.textMuted());
            tvMonthProgressDetail.setTextSize(DS.TEXT_CAPTION);
            tvMonthProgressDetail.setIncludeFontPadding(false);
        }

        // Fond + remplissage gradient
        try {
            viewMonthProgressFill.setBackground(HomeDashboardStyle.progressFill(activity, fillColor));
        } catch (Exception ignored) {}

        // Animation de largeur fluide
        viewMonthProgressFill.post(new Runnable() {
            @Override
            public void run() {
                View parent = (View) viewMonthProgressFill.getParent();
                if (parent == null) return;

                int parentWidth = parent.getWidth();
                if (parentWidth <= 0) return;

                final int finalWidth = Math.round(parentWidth * progress);

                ValueAnimator animator = ValueAnimator.ofInt(0, finalWidth);
                animator.setDuration(DS.ANIM_HERO);
                animator.setInterpolator(new DecelerateInterpolator(2f));
                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator anim) {
                        ViewGroup.LayoutParams lp = viewMonthProgressFill.getLayoutParams();
                        lp.width = (int) anim.getAnimatedValue();
                        viewMonthProgressFill.setLayoutParams(lp);
                    }
                });
                animator.start();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────
    // ANIMATION SOLDE — count-up premium
    // ─────────────────────────────────────────────────────────────

    public void animateBalance(final TextView target, double newValue) {
        if (target == null) return;

        target.setTextColor(Color.WHITE);
        target.setAlpha(1f);
        target.setVisibility(View.VISIBLE);
        target.setTextSize(DS.TEXT_DISPLAY);
        target.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        target.setLetterSpacing(-0.035f);
        target.setIncludeFontPadding(false);

        double start = hasAnimatedOnce ? lastAnimatedBalance : newValue;

        ValueAnimator animator = ValueAnimator.ofFloat((float) start, (float) newValue);
        animator.setDuration(hasAnimatedOnce ? DS.ANIM_HERO : DS.ANIM_NORMAL);
        animator.setInterpolator(new DecelerateInterpolator(2f));
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float v = (float) a.getAnimatedValue();
                target.setText(String.format(Locale.FRANCE, "%,.2f €", v));
            }
        });
        animator.start();

        lastAnimatedBalance = newValue;
        hasAnimatedOnce = true;
    }

    public void resetAnimationState() {
        hasAnimatedOnce = false;
        lastAnimatedBalance = 0;
    }

    // ─────────────────────────────────────────────────────────────
    // COMPARAISON MOIS PRÉCÉDENT
    // ─────────────────────────────────────────────────────────────

    public void updateComparison(TextView tv, double current, double previous, boolean invertColors) {
        if (tv == null) return;

        tv.setTextSize(DS.TEXT_CAPTION);
        tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tv.setLetterSpacing(0.02f);
        tv.setIncludeFontPadding(false);

        if (previous < 0.01) {
            if (current < 0.01) {
                tv.setText(" ");
            } else {
                tv.setText("Pas encore de comparaison");
                tv.setTextColor(ThemeColors.textMuted());
            }
            return;
        }

        double diff = current - previous;
        double pct  = (diff / previous) * 100.0;

        String arrow;
        int    color;

        if (Math.abs(pct) < 0.5) {
            arrow = "→";
            color = ThemeColors.textMuted();
        } else if (pct > 0) {
            arrow = "↗";
            color = invertColors ? ThemeColors.danger() : ThemeColors.success();
        } else {
            arrow = "↘";
            color = invertColors ? ThemeColors.success() : ThemeColors.danger();
        }

        String pctText = String.format(Locale.FRANCE, "%+.0f%%", pct);
        tv.setText(arrow + " " + pctText + " vs " + prevMonthShort());
        tv.setTextColor(color);
    }

    // ─────────────────────────────────────────────────────────────
    // TOP CATÉGORIES — barres premium animées
    // ─────────────────────────────────────────────────────────────

    public void renderTopCategories(Map<String, Double> categoryTotals, double totalExpenses) {
        if (topCategoriesContainer == null) return;
        topCategoriesContainer.removeAllViews();

        if (categoryTotals == null || categoryTotals.isEmpty() || totalExpenses < 0.01) {
            if (tvTopCategoriesEmpty != null) {
                tvTopCategoriesEmpty.setVisibility(View.VISIBLE);
                tvTopCategoriesEmpty.setText("Aucune dépense catégorisée pour le moment.");
                tvTopCategoriesEmpty.setTextColor(ThemeColors.textMuted());
                tvTopCategoriesEmpty.setTextSize(DS.TEXT_BODY_SMALL);
                tvTopCategoriesEmpty.setIncludeFontPadding(true);
            }
            if (tvTopCategoriesTotal != null) {
                tvTopCategoriesTotal.setText("");
            }
            return;
        }

        if (tvTopCategoriesEmpty != null) {
            tvTopCategoriesEmpty.setVisibility(View.GONE);
        }

        List<Map.Entry<String, Double>> sorted = new ArrayList<>(categoryTotals.entrySet());
        Collections.sort(sorted, (a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<Map.Entry<String, Double>> top3 = sorted.size() > 3 ? sorted.subList(0, 3) : sorted;

        double top3Sum = 0;
        for (Map.Entry<String, Double> e : top3) {
            top3Sum += e.getValue();
        }

        if (tvTopCategoriesTotal != null) {
            int pctOfTotal = (int) Math.round((top3Sum / totalExpenses) * 100);
            tvTopCategoriesTotal.setText(pctOfTotal + "% du total");
            tvTopCategoriesTotal.setTextColor(ThemeColors.textMuted());
            tvTopCategoriesTotal.setTextSize(DS.TEXT_CAPTION);
            tvTopCategoriesTotal.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            tvTopCategoriesTotal.setLetterSpacing(0.03f);
        }

        double max = top3.get(0).getValue();

        // Palette de couleurs premium pour les barres
        int[] barColors = {
                ThemeColors.primary(),
                ThemeColors.success(),
                ThemeColors.warning()
        };

        int idx = 0;
        for (Map.Entry<String, Double> entry : top3) {
            String catName   = entry.getKey();
            double catAmount = entry.getValue();

            String emoji = (categoryEmojis != null && categoryEmojis.containsKey(catName))
                    ? categoryEmojis.get(catName)
                    : "📊";

            int barColor = barColors[idx % barColors.length];

            View row = buildTopCategoryRow(
                    emoji, catName, catAmount, max, barColor,
                    idx == top3.size() - 1, idx
            );
            topCategoriesContainer.addView(row);
            idx++;
        }
    }

    private View buildTopCategoryRow(String emoji,
                                      String name,
                                      double amount,
                                      double max,
                                      int barColor,
                                      boolean isLast,
                                      int index) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        if (!isLast) {
            rowLp.setMargins(0, 0, 0, DS.dp(activity, DS.SPACE_18));
        }
        row.setLayoutParams(rowLp);

        // Animation d'apparition en cascade
        HomeDashboardStyle.fadeIn(row, index * 55L);

        // Ligne info: emoji + nom + montant
        LinearLayout topLine = new LinearLayout(activity);
        topLine.setOrientation(LinearLayout.HORIZONTAL);
        topLine.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(-1, -2);
        topLp.setMargins(0, 0, 0, DS.dp(activity, DS.SPACE_10));
        topLine.setLayoutParams(topLp);

        // Emoji icon — cercle premium
        TextView tvEmoji = new TextView(activity);
        tvEmoji.setText(emoji);
        tvEmoji.setTextSize(15f);
        tvEmoji.setGravity(Gravity.CENTER);
        tvEmoji.setIncludeFontPadding(false);

        GradientDrawable emojiBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        ThemeColors.blend(barColor, Color.WHITE, 0.82f),
                        ThemeColors.blend(barColor, Color.WHITE, 0.68f)
                }
        );
        emojiBg.setShape(GradientDrawable.OVAL);
        emojiBg.setStroke(DS.dp(activity, 1), ThemeColors.withAlpha(barColor, 50));
        tvEmoji.setBackground(emojiBg);

        LinearLayout.LayoutParams eLp = new LinearLayout.LayoutParams(
                DS.dp(activity, DS.AVATAR_SM),
                DS.dp(activity, DS.AVATAR_SM)
        );
        eLp.setMargins(0, 0, DS.dp(activity, DS.SPACE_12), 0);
        topLine.addView(tvEmoji, eLp);

        // Nom de catégorie
        TextView tvName = new TextView(activity);
        tvName.setText(name);
        tvName.setTextSize(DS.TEXT_BODY_SMALL);
        tvName.setTextColor(ThemeColors.textPrimary());
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setSingleLine(true);
        tvName.setIncludeFontPadding(false);
        tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        topLine.addView(tvName, new LinearLayout.LayoutParams(0, -2, 1f));

        // Montant
        TextView tvAmount = new TextView(activity);
        tvAmount.setText(String.format(Locale.FRANCE, "%,.0f €", amount));
        tvAmount.setTextSize(DS.TEXT_BODY_SMALL);
        tvAmount.setTextColor(barColor);
        tvAmount.setTypeface(null, Typeface.BOLD);
        tvAmount.setLetterSpacing(-0.01f);
        tvAmount.setIncludeFontPadding(false);
        topLine.addView(tvAmount);

        row.addView(topLine);

        // Barre de progression premium avec track + fill animé
        FrameLayout track = new FrameLayout(activity);
        track.setBackground(HomeDashboardStyle.progressTrack(activity));
        track.setLayoutParams(new LinearLayout.LayoutParams(-1, DS.dp(activity, 8)));

        final View fill = new View(activity);
        fill.setLayoutParams(new FrameLayout.LayoutParams(0, -1));
        fill.setBackground(HomeDashboardStyle.progressFill(activity, barColor));
        track.addView(fill);
        row.addView(track);

        // Pourcentage sous la barre
        final double pctVal = max > 0 ? (amount / max) * 100.0 : 0;
        TextView tvPct = new TextView(activity);
        tvPct.setText(String.format(Locale.FRANCE, "%.0f%%", pctVal));
        tvPct.setTextSize(DS.TEXT_MICRO);
        tvPct.setTextColor(ThemeColors.textMuted());
        tvPct.setLetterSpacing(0.03f);
        tvPct.setIncludeFontPadding(false);

        LinearLayout.LayoutParams pctLp = new LinearLayout.LayoutParams(-2, -2);
        pctLp.setMargins(0, DS.dp(activity, DS.SPACE_4), 0, 0);
        row.addView(tvPct, pctLp);

        final float ratio = max > 0 ? (float) (amount / max) : 0f;

        track.post(new Runnable() {
            @Override
            public void run() {
                int w = track.getWidth();
                if (w <= 0) return;

                ValueAnimator anim = ValueAnimator.ofInt(0, Math.round(w * ratio));
                anim.setDuration(DS.ANIM_SLOW);
                anim.setStartDelay(index * 55L + 100L);
                anim.setInterpolator(new DecelerateInterpolator(1.8f));
                anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator a) {
                        ViewGroup.LayoutParams lp = fill.getLayoutParams();
                        lp.width = (int) a.getAnimatedValue();
                        fill.setLayoutParams(lp);
                    }
                });
                anim.start();
            }
        });

        return row;
    }

    // ─────────────────────────────────────────────────────────────
    // FINANCIAL INSIGHTS WIDGET — refonte premium
    // ─────────────────────────────────────────────────────────────

    public void renderInsightsWidget(LinearLayout widget,
                                      List<FinancialInsightManager.Insight> insights) {
        if (widget == null) return;

        widget.removeAllViews();
        widget.setOrientation(LinearLayout.VERTICAL);
        widget.setPadding(
                DS.dp(activity, DS.CARD_PADDING),
                DS.dp(activity, DS.CARD_PADDING),
                DS.dp(activity, DS.CARD_PADDING),
                DS.dp(activity, DS.CARD_PADDING)
        );
        widget.setBackground(HomeDashboardStyle.card(activity));
        HomeDashboardStyle.applyNativeElevation(widget, 6f);
        HomeDashboardStyle.applyPressEffect(widget);

        final List<FinancialInsightManager.Insight> safeInsights =
                insights == null
                        ? new ArrayList<FinancialInsightManager.Insight>()
                        : new ArrayList<>(insights);

        // — En-tête avec icône gradient
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        // Icône premium avec gradient
        TextView icon = new TextView(activity);
        icon.setText("✦");
        icon.setTextSize(14f);
        icon.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        icon.setTextColor(Color.WHITE);
        icon.setGravity(Gravity.CENTER);
        icon.setIncludeFontPadding(false);

        GradientDrawable iconBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        ThemeColors.blend(ThemeColors.primary(), Color.WHITE, 0.14f),
                        ThemeColors.primary(),
                        ThemeColors.blend(ThemeColors.primaryDark(), Color.BLACK, 0.08f)
                }
        );
        iconBg.setShape(GradientDrawable.OVAL);
        icon.setBackground(iconBg);

        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                DS.dp(activity, DS.AVATAR_SM),
                DS.dp(activity, DS.AVATAR_SM)
        );
        header.addView(icon, iconLp);

        // Titre + sous-titre
        LinearLayout titleBox = new LinearLayout(activity);
        titleBox.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1f);
        titleLp.setMargins(DS.dp(activity, DS.SPACE_12), 0, DS.dp(activity, DS.SPACE_8), 0);

        TextView title = new TextView(activity);
        title.setText("Insights financiers");
        title.setTextSize(DS.TEXT_BODY);
        title.setTextColor(ThemeColors.textPrimary());
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setLetterSpacing(-0.01f);
        title.setIncludeFontPadding(false);
        titleBox.addView(title);

        TextView subtitle = new TextView(activity);
        subtitle.setText(safeInsights.isEmpty()
                ? "Aucune anomalie détectée"
                : safeInsights.size() + " alerte" + (safeInsights.size() > 1 ? "s" : "") + " à vérifier");
        subtitle.setTextSize(DS.TEXT_CAPTION);
        subtitle.setTextColor(ThemeColors.textMuted());
        subtitle.setPadding(0, DS.dp(activity, DS.SPACE_4), 0, 0);
        subtitle.setIncludeFontPadding(false);
        titleBox.addView(subtitle);

        header.addView(titleBox, titleLp);

        // Lien "Voir ›"
        TextView chevron = new TextView(activity);
        chevron.setText("Voir ›");
        chevron.setTextSize(DS.TEXT_CAPTION);
        chevron.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        chevron.setTextColor(ThemeColors.primary());
        chevron.setIncludeFontPadding(false);
        chevron.setPadding(DS.dp(activity, DS.SPACE_8), DS.dp(activity, DS.SPACE_6),
                DS.dp(activity, DS.SPACE_8), DS.dp(activity, DS.SPACE_6));

        // Badge actif si des insights
        if (!safeInsights.isEmpty()) {
            GradientDrawable chevBg = new GradientDrawable();
            chevBg.setColor(ThemeColors.primaryMuted());
            chevBg.setCornerRadius(DS.dp(activity, DS.RADIUS_PILL));
            chevron.setBackground(chevBg);
        }
        header.addView(chevron);

        widget.addView(header);

        // Séparateur premium
        View divider = new View(activity);
        divider.setBackgroundColor(ThemeColors.divider());
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, DS.dp(activity, 1));
        divLp.setMargins(0, DS.dp(activity, DS.SPACE_16), 0, DS.dp(activity, DS.SPACE_14));
        widget.addView(divider, divLp);

        // État vide
        if (safeInsights.isEmpty()) {
            LinearLayout emptyRow = new LinearLayout(activity);
            emptyRow.setOrientation(LinearLayout.HORIZONTAL);
            emptyRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView checkMark = new TextView(activity);
            checkMark.setText("✓");
            checkMark.setTextSize(14f);
            checkMark.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            checkMark.setTextColor(ThemeColors.success());
            checkMark.setGravity(Gravity.CENTER);
            checkMark.setIncludeFontPadding(false);

            GradientDrawable checkBg = new GradientDrawable();
            checkBg.setShape(GradientDrawable.OVAL);
            checkBg.setColor(ThemeColors.successBackground());
            checkBg.setStroke(DS.dp(activity, 1), ThemeColors.withAlpha(ThemeColors.success(), 40));
            checkMark.setBackground(checkBg);

            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                    DS.dp(activity, 30), DS.dp(activity, 30)
            );
            cLp.setMargins(0, 0, DS.dp(activity, DS.SPACE_12), 0);
            emptyRow.addView(checkMark, cLp);

            TextView empty = new TextView(activity);
            empty.setText("Ton budget ne montre pas de variation inhabituelle.");
            empty.setTextSize(DS.TEXT_BODY_SMALL);
            empty.setTextColor(ThemeColors.textSecondary());
            empty.setIncludeFontPadding(false);
            empty.setLineSpacing(DS.dp(activity, 2), 1f);
            emptyRow.addView(empty, new LinearLayout.LayoutParams(0, -2, 1f));

            widget.addView(emptyRow);
            return;
        }

        int max = Math.min(3, safeInsights.size());
        for (int i = 0; i < max; i++) {
            View row = buildInsightRow(safeInsights.get(i), i == max - 1);
            widget.addView(row);
        }
    }

    private View buildInsightRow(FinancialInsightManager.Insight insight, boolean last) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(
                0,
                DS.dp(activity, DS.SPACE_8),
                0,
                last ? 0 : DS.dp(activity, DS.SPACE_8)
        );

        boolean isRisk = insight.severity == FinancialInsightManager.SEVERITY_RISK;

        int accent = isRisk ? ThemeColors.danger()  : ThemeColors.warning();
        int bg     = isRisk ? ThemeColors.dangerSoft() : ThemeColors.warningSoft();

        // Dot indicateur
        TextView dot = new TextView(activity);
        dot.setText(isRisk ? "!" : "·");
        dot.setTextSize(13f);
        dot.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        dot.setGravity(Gravity.CENTER);
        dot.setTextColor(accent);
        dot.setIncludeFontPadding(false);

        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(bg);
        dotBg.setStroke(DS.dp(activity, 1), ThemeColors.withAlpha(accent, 55));
        dot.setBackground(dotBg);

        row.addView(dot, new LinearLayout.LayoutParams(
                DS.dp(activity, 28), DS.dp(activity, 28)
        ));

        // Textes
        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, -2, 1f);
        tLp.setMargins(DS.dp(activity, DS.SPACE_12), 0, 0, 0);

        TextView t = new TextView(activity);
        t.setText(insight.title);
        t.setTextSize(DS.TEXT_BODY_SMALL);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setTextColor(ThemeColors.textPrimary());
        t.setIncludeFontPadding(false);
        texts.addView(t);

        TextView s = new TextView(activity);
        s.setText(insight.subtitle);
        s.setTextSize(DS.TEXT_CAPTION);
        s.setTextColor(ThemeColors.textMuted());
        s.setPadding(0, DS.dp(activity, DS.SPACE_2), 0, 0);
        s.setIncludeFontPadding(false);
        texts.addView(s);

        row.addView(texts, tLp);

        // Séparateur si pas dernier
        if (!last) {
            View sep = new View(activity);
            sep.setBackgroundColor(ThemeColors.divider());
            LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(-1, DS.dp(activity, 1));
            sepLp.setMargins(0, DS.dp(activity, DS.SPACE_8), 0, 0);

            LinearLayout wrapper = new LinearLayout(activity);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.addView(row);
            wrapper.addView(sep, sepLp);
            return wrapper;
        }

        return row;
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────

    public String prevMonthShort() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MONTH, -1);
        SimpleDateFormat fmt = new SimpleDateFormat("MMM", Locale.FRENCH);
        String v = fmt.format(c.getTime()).toLowerCase(Locale.FRENCH);
        return v.endsWith(".") ? v : v + ".";
    }

    public String getBalanceAnchorLabel(long commonBalanceAnchorDate) {
        long anchor = commonBalanceAnchorDate > 0
                ? commonBalanceAnchorDate
                : BalanceManager.getInstance().getMonthStartMillis();

        Calendar a = Calendar.getInstance();
        a.setTimeInMillis(anchor);

        Calendar m = Calendar.getInstance();
        m.set(Calendar.DAY_OF_MONTH, 1);
        m.set(Calendar.HOUR_OF_DAY, 0);
        m.set(Calendar.MINUTE, 0);
        m.set(Calendar.SECOND, 0);
        m.set(Calendar.MILLISECOND, 0);

        if (Math.abs(anchor - m.getTimeInMillis()) < 60_000) {
            return "Début du mois";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM", Locale.FRANCE);
        return "Solde saisi le " + sdf.format(new Date(anchor));
    }

    public int getBalanceColor(double balance, boolean overdraftDefined, double overdraftLimit) {
        return HomeDashboardStyle.balanceColor(balance, overdraftDefined, overdraftLimit);
    }
}
