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
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;

import java.util.Calendar;
import java.util.List;
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