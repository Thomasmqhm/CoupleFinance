package com.couplefinance.ui.home;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.Fmt;
import com.couplefinance.ui.epargne.EpargneCalculator;
import com.couplefinance.ui.epargne.EpargneModels;
import com.couplefinance.ui.epargne.EpargneRepository;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeWidgets {

    public static final String W_QUICK_SUMMARY = "widget_quick_summary";
    public static final String W_BUDGET_HEALTH = "widget_budget_health";
    public static final String W_DAILY_BURN = "widget_daily_burn";
    public static final String W_MONTH_FORECAST = "widget_month_forecast";
    public static final String W_BIGGEST_EXPENSE = "widget_biggest_expense";
    public static final String W_SAVINGS_RATE = "widget_savings_rate";
    public static final String W_CATEGORY_COUNT = "widget_category_count";
    public static final String W_INCOME_SOURCES = "widget_income_sources";
    public static final String W_ACTIVITY = "widget_activity";

    private final Activity activity;
    private final SharedPreferences prefs;

    public HomeWidgets(Activity activity, SharedPreferences prefs) {
        this.activity = activity;
        this.prefs = prefs;
    }

    public static String[] getDynamicKeys() {
        return new String[]{
                W_QUICK_SUMMARY,
                W_BUDGET_HEALTH,
                W_DAILY_BURN,
                W_MONTH_FORECAST,
                W_BIGGEST_EXPENSE,
                W_SAVINGS_RATE,
                W_CATEGORY_COUNT,
                W_INCOME_SOURCES,
                W_ACTIVITY
        };
    }

    public static String[] getDynamicTitles() {
        return new String[]{
                "Résumé express",
                "Santé du budget",
                "Rythme journalier",
                "Projection fin de mois",
                "Plus grosse dépense",
                "Taux d'épargne",
                "Nombre de catégories",
                "Sources de revenus",
                "Activité du mois"
        };
    }

    public void renderDynamicWidgets(LinearLayout container,
                                     double income,
                                     double expenses,
                                     double realBalance,
                                     double projectedEndBalance,
                                     double monthMovement,
                                     int txCount,
                                     int incomeCount,
                                     int expenseCount,
                                     double todayExpenses,
                                     int todayExpenseCount,
                                     int activeCategoryCount,
                                     double biggestExpenseAmount,
                                     String biggestExpenseLabel,
                                     String biggestExpenseCategory,
                                     Map<String, Double> incomeSources,
                                     double commonStartBalance,
                                     String orderPrefKey) {
        renderDynamicWidgets(container, income, expenses, realBalance, projectedEndBalance,
                monthMovement, txCount, incomeCount, expenseCount, todayExpenses, todayExpenseCount,
                activeCategoryCount, biggestExpenseAmount, biggestExpenseLabel, biggestExpenseCategory,
                incomeSources, commonStartBalance, orderPrefKey, 0);
    }

    public void renderDynamicWidgets(LinearLayout container,
                                     double income,
                                     double expenses,
                                     double realBalance,
                                     double projectedEndBalance,
                                     double monthMovement,
                                     int txCount,
                                     int incomeCount,
                                     int expenseCount,
                                     double todayExpenses,
                                     int todayExpenseCount,
                                     int activeCategoryCount,
                                     double biggestExpenseAmount,
                                     String biggestExpenseLabel,
                                     String biggestExpenseCategory,
                                     Map<String, Double> incomeSources,
                                     double commonStartBalance,
                                     String orderPrefKey,
                                     int membersInOverdraft) {
        new HomeWidgetDynamic(activity, prefs).render(
                container,
                income,
                expenses,
                realBalance,
                projectedEndBalance,
                monthMovement,
                txCount,
                incomeCount,
                expenseCount,
                todayExpenses,
                todayExpenseCount,
                activeCategoryCount,
                biggestExpenseAmount,
                biggestExpenseLabel,
                biggestExpenseCategory,
                incomeSources,
                commonStartBalance,
                orderPrefKey,
                membersInOverdraft
        );
    }

    public void renderSevenDayCalendar(GridLayout calendarGrid,
                                       TextView tvCalMonth,
                                       Calendar calendarMonth,
                                       List<String[]> transactions) {
        new HomeWidgetCalendar(activity, prefs).render(
                calendarGrid,
                tvCalMonth,
                calendarMonth,
                transactions
        );
    }

    public void renderPersonCards(LinearLayout personCards,
                                  LinearLayout personBars,
                                  LinearLayout topCategoriesContainer,
                                  TextView tvTopCategoriesEmpty,
                                  TextView tvTopCategoriesTotal,
                                  Map<String, double[]> personBalances,
                                  double startBalance,
                                  String myName,
                                  View.OnClickListener regularizeClick) {
        new HomeWidgetCards(activity, prefs).renderPersonCards(
                personCards,
                personBars,
                topCategoriesContainer,
                tvTopCategoriesEmpty,
                tvTopCategoriesTotal,
                personBalances,
                startBalance,
                myName,
                regularizeClick
        );
    }

    public void renderMemberExpenseSplit(LinearLayout personBars,
                                         Map<String, Double> expensesByPerson,
                                         double totalExpenses) {
        new HomeWidgetCards(activity, prefs).renderMemberExpenseSplit(
                personBars,
                expensesByPerson,
                totalExpenses
        );
    }

    public void renderRecentTransactions(LinearLayout container,
                                         List<String[]> transactions,
                                         View.OnClickListener rowClick) {
        new HomeWidgetCards(activity, prefs).renderRecentTransactions(
                container,
                transactions,
                rowClick
        );
    }

    // ─────────────────────────────────────────────────────────────
    // Widget objectifs d'épargne
    // ─────────────────────────────────────────────────────────────

    /**
     * Ajoute une carte pleine largeur "Objectifs d'épargne" au bas du container.
     * Chargement asynchrone — la carte apparaît une fois les données disponibles.
     */
    public void renderSavingsGoalsCard(LinearLayout container) {
        if (container == null) return;

        // Placeholder animé pendant le chargement
        final LinearLayout card = buildSavingsCard(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = DS.dp(activity, DS.CARD_GAP);
        container.addView(card, lp);

        EpargneRepository.loadAll(activity, new EpargneRepository.OnDataLoaded() {
            @Override public void onLoaded(EpargneModels.EpargneData data) {
                activity.runOnUiThread(() -> {
                    int idx = container.indexOfChild(card);
                    if (idx < 0) return;
                    LinearLayout updated = buildSavingsCard(data);
                    container.removeViewAt(idx);
                    container.addView(updated, idx, lp);
                });
            }
            @Override public void onError(String message) {
                // Garde le placeholder vide — pas d'erreur visible
            }
        });
    }

    private LinearLayout buildSavingsCard(EpargneModels.EpargneData data) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(DS.dp(activity, DS.CARD_PADDING), DS.dp(activity, DS.CARD_PADDING),
                DS.dp(activity, DS.CARD_PADDING), DS.dp(activity, DS.CARD_PADDING));

        int accent = 0xFF2D7D55; // vert épargne
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{ ThemeColors.blend(ThemeColors.surfaceFloating(), Color.parseColor("#2D7D55"), 0.05f),
                           ThemeColors.surfaceFloating() }
        );
        bg.setCornerRadius(DS.dp(activity, DS.RADIUS_2XL));
        bg.setStroke(DS.dp(activity, 1), ThemeColors.withAlpha(accent, 30));
        card.setBackground(bg);
        HomeDashboardStyle.applyNativeElevation(card, 4f);

        // En-tête
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(activity);
        title.setText("🌱  Objectifs d'épargne");
        title.setTextSize(DS.TEXT_SUBTITLE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(ThemeColors.textPrimary());
        title.setLetterSpacing(-0.012f);
        title.setIncludeFontPadding(false);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        card.addView(header);

        if (data == null || data.goals == null || data.goals.isEmpty()) {
            // Placeholder / état vide
            TextView empty = new TextView(activity);
            empty.setText(data == null ? "Chargement…" : "Aucun objectif défini. Ajoutez-en dans Épargne.");
            empty.setTextColor(ThemeColors.textMuted());
            empty.setTextSize(DS.TEXT_BODY_SMALL);
            empty.setIncludeFontPadding(false);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, -2);
            ep.topMargin = DS.dp(activity, DS.SPACE_8);
            card.addView(empty, ep);
            return card;
        }

        // Résumé global
        double totalSaved  = EpargneCalculator.totalSaved(data.goals);
        double totalTarget = EpargneCalculator.totalTarget(data.goals);
        int    globalPct   = EpargneCalculator.globalPercent(data.goals);

        LinearLayout summaryRow = new LinearLayout(activity);
        summaryRow.setOrientation(LinearLayout.HORIZONTAL);
        summaryRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams srp = new LinearLayout.LayoutParams(-1, -2);
        srp.topMargin = DS.dp(activity, DS.SPACE_12);
        summaryRow.setLayoutParams(srp);

        TextView tvSaved = new TextView(activity);
        tvSaved.setText(Fmt.money(totalSaved) + " / " + Fmt.money(totalTarget));
        tvSaved.setTextSize(DS.TEXT_BODY);
        tvSaved.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tvSaved.setTextColor(accent);
        tvSaved.setIncludeFontPadding(false);
        summaryRow.addView(tvSaved, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView tvPct = new TextView(activity);
        tvPct.setText(globalPct + "%");
        tvPct.setTextSize(DS.TEXT_BODY_SMALL);
        tvPct.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tvPct.setTextColor(ThemeColors.textMuted());
        tvPct.setIncludeFontPadding(false);
        summaryRow.addView(tvPct);

        card.addView(summaryRow);

        // Barre de progression globale
        ProgressBar globalBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        globalBar.setMax(100);
        globalBar.setProgress(Math.min(100, globalPct));
        globalBar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        globalBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(
                ThemeColors.withAlpha(ThemeColors.border(), 60)));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, DS.dp(activity, 6));
        gp.topMargin = DS.dp(activity, DS.SPACE_8);
        card.addView(globalBar, gp);

        // Liste des objectifs (max 3)
        int max = Math.min(3, data.goals.size());
        for (int i = 0; i < max; i++) {
            EpargneModels.SavingsGoal g = data.goals.get(i);
            card.addView(buildGoalRow(g, accent), buildGoalRowLp());
        }

        // Lien si plus
        if (data.goals.size() > 3) {
            TextView more = new TextView(activity);
            more.setText("+ " + (data.goals.size() - 3) + " objectif(s) de plus");
            more.setTextColor(ThemeColors.primary());
            more.setTextSize(DS.TEXT_BODY_SMALL);
            more.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            more.setIncludeFontPadding(false);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
            mp.topMargin = DS.dp(activity, DS.SPACE_8);
            card.addView(more, mp);
        }

        return card;
    }

    private View buildGoalRow(EpargneModels.SavingsGoal g, int accent) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView emojiView = new TextView(activity);
        emojiView.setText(g.emoji != null && !g.emoji.isEmpty() ? g.emoji : "🎯");
        emojiView.setTextSize(18f);
        emojiView.setGravity(Gravity.CENTER);
        emojiView.setIncludeFontPadding(false);
        row.addView(emojiView, new LinearLayout.LayoutParams(DS.dp(activity, 28), DS.dp(activity, 28)));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView tvName = new TextView(activity);
        tvName.setText(g.name);
        tvName.setTextSize(DS.TEXT_BODY_SMALL);
        tvName.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tvName.setTextColor(ThemeColors.textPrimary());
        tvName.setSingleLine(true);
        tvName.setIncludeFontPadding(false);
        texts.addView(tvName);

        int pct = EpargneCalculator.progressPercent(g);
        TextView tvPct = new TextView(activity);
        tvPct.setText(pct + "% · " + Fmt.money(g.current) + " / " + Fmt.money(g.target));
        tvPct.setTextSize(DS.TEXT_MICRO);
        tvPct.setTextColor(ThemeColors.textMuted());
        tvPct.setIncludeFontPadding(false);
        texts.addView(tvPct);

        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
        tp.leftMargin = DS.dp(activity, DS.SPACE_10);
        row.addView(texts, tp);

        String badge = EpargneCalculator.badgeLabel(g);
        if (badge != null && !badge.isEmpty()) {
            TextView tvBadge = new TextView(activity);
            tvBadge.setText(badge);
            tvBadge.setTextSize(10f);
            tvBadge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            int badgeColor = g.isLate() ? ThemeColors.danger() : ThemeColors.primary();
            tvBadge.setTextColor(badgeColor);
            tvBadge.setPadding(DS.dp(activity, 8), DS.dp(activity, 3), DS.dp(activity, 8), DS.dp(activity, 3));
            GradientDrawable bb = new GradientDrawable();
            bb.setColor(ThemeColors.withAlpha(badgeColor, 18));
            bb.setCornerRadius(DS.dp(activity, DS.RADIUS_PILL));
            tvBadge.setBackground(bb);
            row.addView(tvBadge);
        }

        return row;
    }

    private LinearLayout.LayoutParams buildGoalRowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = DS.dp(activity, DS.SPACE_12);
        return lp;
    }

    // ─────────────────────────────────────────────────────────────
    // Gauge santé financière
    // ─────────────────────────────────────────────────────────────

    public static class GaugeRefs {
        public final GaugeView gauge;
        public final TextView label;

        GaugeRefs(GaugeView gauge, TextView label) {
            this.gauge = gauge;
            this.label = label;
        }
    }

    public GaugeRefs installFinancialGauge(FrameLayout container) {
    if (container == null) return null;

    container.removeAllViews();
    container.setVisibility(View.GONE);

    TextView label = new TextView(activity);
    label.setText("—/100");

    return new GaugeRefs(null, label);
}
public void updateFinancialGauge(GaugeRefs refs,
                                 TextView statusBadge,
                                 double income,
                                 double expenses,
                                 double balance,
                                 boolean overdraftDefined,
                                 double overdraftLimit) {
    int score = HomeCalculator.financialScoreDetailed(
            income,
            expenses,
            balance,
            overdraftDefined,
            overdraftLimit
    );

    String status;
    int statusColor;
    int statusBg;

    if (score >= 85) {
        status = score + "/100";
        statusColor = ThemeColors.success();
        statusBg = ThemeColors.successSoft();
    } else if (score >= 65) {
        status = score + "/100";
        statusColor = ThemeColors.warning();
        statusBg = ThemeColors.warningSoft();
    } else if (score >= 45) {
        status = score + "/100";
        statusColor = ThemeColors.primary();
        statusBg = ThemeColors.primarySoft();
    } else {
        status = score + "/100";
        statusColor = ThemeColors.danger();
        statusBg = ThemeColors.dangerSoft();
    }

    if (refs != null && refs.label != null) {
        refs.label.setText(status);
        refs.label.setTextColor(statusColor);
    }

    if (statusBadge != null) {
        statusBadge.setVisibility(View.VISIBLE);
        statusBadge.setText(status);
        statusBadge.setTextColor(statusColor);
        statusBadge.setTextSize(24f);
        statusBadge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusBadge.setGravity(Gravity.CENTER);
        statusBadge.setIncludeFontPadding(false);
        statusBadge.setPadding(0, 0, 0, 0);
        statusBadge.setBackgroundColor(Color.TRANSPARENT);
    }
}

    private GradientDrawable pill(int color, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(DS.dp(activity, DS.RADIUS_PILL));
        if (stroke != Color.TRANSPARENT) {
            d.setStroke(DS.dp(activity, 1), stroke);
        }
        return d;
    }

    // ─────────────────────────────────────────────────────────────
    // GaugeView premium
    // ─────────────────────────────────────────────────────────────

    public static class GaugeView extends View {

        private final Paint trackPaint;
        private final Paint arcPaint;
        private final Paint glowPaint;
        private final RectF rect = new RectF();

        private int score = 0;
        private int animatedScore = 0;
        private int accentColor = ThemeColors.primary();

        public GaugeView(android.content.Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);

            trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            trackPaint.setStyle(Paint.Style.STROKE);
            trackPaint.setStrokeCap(Paint.Cap.ROUND);

            arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            arcPaint.setStyle(Paint.Style.STROKE);
            arcPaint.setStrokeCap(Paint.Cap.ROUND);

            glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            glowPaint.setStyle(Paint.Style.STROKE);
            glowPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        public void setAccentColor(int color) {
            this.accentColor = color;
            invalidate();
        }

        public void setScore(int value) {
            score = Math.max(0, Math.min(100, value));
            animatedScore = score;
            invalidate();
        }

        public void setScoreAnimated(int value) {
            score = Math.max(0, Math.min(100, value));

            ValueAnimator animator = ValueAnimator.ofInt(animatedScore, score);
            animator.setDuration(DS.ANIM_SLOW);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    animatedScore = (int) animation.getAnimatedValue();
                    invalidate();
                }
            });
            animator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float stroke = Math.max(11f, getWidth() * 0.078f);
            float glowStroke = stroke + 6f;

            float pad = glowStroke / 2f + 8f;
            rect.set(pad, pad, getWidth() - pad, getHeight() - pad);

            trackPaint.setStrokeWidth(stroke);
            trackPaint.setColor(ThemeColors.borderSoft());

            glowPaint.setStrokeWidth(glowStroke);
            glowPaint.setColor(ThemeColors.withAlpha(accentColor, 34));

            arcPaint.setStrokeWidth(stroke);
            arcPaint.setColor(accentColor);

            canvas.drawArc(rect, 135, 270, false, trackPaint);

            if (animatedScore > 0) {
                float sweep = 270f * (animatedScore / 100f);
                canvas.drawArc(rect, 135, sweep, false, glowPaint);
                canvas.drawArc(rect, 135, sweep, false, arcPaint);
            }
        }
    }
}