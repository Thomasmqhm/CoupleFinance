package com.couplefinance.ui.agenda;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;

import java.util.Calendar;
import java.util.Locale;

public final class AgendaCalendar {

    private AgendaCalendar() {
    }

    static final int COLOR_INCOME = Color.parseColor("#16A34A");
    static final int COLOR_FIXED = Color.parseColor("#8B5CF6");
    static final int COLOR_RDV = Color.parseColor("#4A6B9A");

    static int COLOR_EXPENSE() {
        return ThemeColors.primary();
    }

    private static final String[] FR_DAYS = {
            "Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim"
    };

    public static LinearLayout buildDaysHeader(Activity activity) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = DS.dp(activity, 2);
        header.setLayoutParams(lp);

        for (String day : FR_DAYS) {
            TextView tv = new TextView(activity);
            tv.setText(day);
            tv.setTextSize(10f);
            tv.setTextColor(ThemeColors.subtext());
            tv.setTypeface(null, Typeface.BOLD);
            tv.setGravity(Gravity.CENTER);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            header.addView(tv);
        }

        return header;
    }

    public static void buildGrid(Activity activity,
                                 LinearLayout calendarGrid,
                                 Calendar displayedMonth,
                                 int selectedDay,
                                 AgendaModels.AgendaData data,
                                 OnDayClick onDayClick) {
        calendarGrid.removeAllViews();

        Calendar today = Calendar.getInstance();

        int month = displayedMonth.get(Calendar.MONTH);
        int year = displayedMonth.get(Calendar.YEAR);

        Calendar cal = (Calendar) displayedMonth.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);

        int firstDow = cal.get(Calendar.DAY_OF_WEEK);
        int offset = (firstDow == 1) ? 6 : firstDow - 2;
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int weeks = (int) Math.ceil((offset + daysInMonth) / 7.0);

        for (int w = 0; w < weeks; w++) {
            LinearLayout weekRow = new LinearLayout(activity);
            weekRow.setOrientation(LinearLayout.HORIZONTAL);
            weekRow.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));

            for (int d = 0; d < 7; d++) {
                int dayNum = w * 7 + d - offset + 1;

                weekRow.addView(makeCell(
                        activity,
                        dayNum,
                        daysInMonth,
                        month,
                        year,
                        today,
                        selectedDay,
                        data,
                        onDayClick
                ));
            }

            calendarGrid.addView(weekRow);
        }
    }

    private static View makeCell(Activity activity,
                                 int dayNum,
                                 int daysInMonth,
                                 int month,
                                 int year,
                                 Calendar today,
                                 int selectedDay,
                                 AgendaModels.AgendaData data,
                                 OnDayClick onDayClick) {
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        cell.setPadding(
                DS.dp(activity, 2),
                DS.dp(activity, 5),
                DS.dp(activity, 2),
                DS.dp(activity, 3)
        );
        cell.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1f));

        boolean valid = dayNum >= 1 && dayNum <= daysInMonth;

        if (!valid) {
            cell.addView(new View(activity), new LinearLayout.LayoutParams(-1, -1));
            return cell;
        }

        boolean isToday = dayNum == today.get(Calendar.DAY_OF_MONTH)
                && month == today.get(Calendar.MONTH)
                && year == today.get(Calendar.YEAR);

        boolean isSelected = dayNum == selectedDay;

        boolean isPast = isPastDay(dayNum, month, year, today);

        Calendar dayDate = Calendar.getInstance();
        dayDate.set(year, month, dayNum, 0, 0, 0);
        dayDate.set(Calendar.MILLISECOND, 0);

        long dayStart = dayDate.getTimeInMillis();

        dayDate.set(Calendar.HOUR_OF_DAY, 23);
        dayDate.set(Calendar.MINUTE, 59);
        dayDate.set(Calendar.SECOND, 59);
        dayDate.set(Calendar.MILLISECOND, 999);

        long dayEnd = dayDate.getTimeInMillis();

        DayIndicators ind = computeIndicators(data, dayStart, dayEnd);

        if (isSelected && !isToday) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(ThemeColors.primarySoft());
            bg.setCornerRadius(DS.dp(activity, 14));
            bg.setStroke(DS.dp(activity, 1), ThemeColors.primary());
            cell.setBackground(bg);
        }

        TextView tvDay = new TextView(activity);
        tvDay.setText(String.valueOf(dayNum));
        tvDay.setGravity(Gravity.CENTER);
        tvDay.setTextSize(12f);
        tvDay.setTypeface(null, Typeface.BOLD);
        tvDay.setIncludeFontPadding(false);
        tvDay.setMinWidth(DS.dp(activity, 42));
        tvDay.setMinHeight(DS.dp(activity, 28));
        tvDay.setPadding(
                DS.dp(activity, 10),
                DS.dp(activity, 6),
                DS.dp(activity, 10),
                DS.dp(activity, 6)
        );

        if (isToday) {
            tvDay.setTextColor(Color.WHITE);
            tvDay.setBackground(makePill(ThemeColors.primary(), activity));
        } else if (ind.hasIncome && ind.hasExpense) {
            tvDay.setTextColor(ThemeColors.text());
            tvDay.setBackground(makePillBorder(ThemeColors.card(), ThemeColors.border(), activity));
        } else if (ind.hasIncome) {
            tvDay.setTextColor(Color.WHITE);
            tvDay.setBackground(makePill(COLOR_INCOME, activity));
        } else if (ind.hasExpense) {
            tvDay.setTextColor(Color.WHITE);
            tvDay.setBackground(makePill(COLOR_EXPENSE(), activity));
        } else {
            tvDay.setTextColor(isPast ? ThemeColors.subtext() : ThemeColors.text());
        }

        cell.addView(tvDay, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                DS.dp(activity, 30)
        ));

        LinearLayout dots = new LinearLayout(activity);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams dotsLP = new LinearLayout.LayoutParams(-2, DS.dp(activity, 8));
        dotsLP.topMargin = DS.dp(activity, 4);
        dots.setLayoutParams(dotsLP);

        if (ind.hasExpense) {
            dots.addView(makeDot(COLOR_EXPENSE(), activity));
        }

        if (ind.hasIncome) {
            dots.addView(makeDot(COLOR_INCOME, activity));
        }

        if (ind.hasFixed) {
            dots.addView(makeDot(COLOR_FIXED, activity));
        }

        if (ind.hasRdv) {
            dots.addView(makeDot(COLOR_RDV, activity));
        }

        cell.addView(dots);

        if (ind.dayExpense > 0 || ind.dayIncome > 0) {
            boolean onlyIncome = ind.dayIncome > 0 && ind.dayExpense <= 0;

            TextView tvAmt = new TextView(activity);
            tvAmt.setText(onlyIncome
                    ? "+" + shortMoney(ind.dayIncome)
                    : "-" + shortMoney(ind.dayExpense)
            );
            tvAmt.setTextSize(9f);
            tvAmt.setTypeface(null, Typeface.BOLD);
            tvAmt.setTextColor(onlyIncome ? COLOR_INCOME : COLOR_EXPENSE());
            tvAmt.setSingleLine(true);
            tvAmt.setAlpha(0.9f);

            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-2, -2);
            ap.topMargin = DS.dp(activity, 1);

            cell.addView(tvAmt, ap);
        }

        final int finalDay = dayNum;

        cell.setClickable(true);
        cell.setOnClickListener(v -> onDayClick.onDaySelected(
                finalDay,
                month,
                year,
                dayStart,
                dayEnd
        ));

        return cell;
    }

    private static DayIndicators computeIndicators(AgendaModels.AgendaData data,
                                                   long dayStart,
                                                   long dayEnd) {
        DayIndicators ind = new DayIndicators();

        if (data == null) {
            return ind;
        }

        for (AgendaModels.AgendaTransaction tx : data.transactions) {
            if (tx.dateMs < dayStart || tx.dateMs > dayEnd) {
                continue;
            }

            if (tx.isIncome()) {
                ind.hasIncome = true;
                ind.dayIncome += tx.amount;
            } else if (tx.isFixed()) {
                ind.hasFixed = true;
                ind.hasExpense = true;
                ind.dayExpense += tx.amount;
            } else {
                ind.hasExpense = true;
                ind.dayExpense += tx.amount;
            }
        }

        for (AgendaModels.AgendaEvent ev : data.events) {
            if (ev.dateMs >= dayStart && ev.dateMs <= dayEnd && ev.isRdv()) {
                ind.hasRdv = true;
                break;
            }
        }

        return ind;
    }

    private static GradientDrawable makePill(int color, Activity activity) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(DS.dp(activity, 999));
        return d;
    }

    private static GradientDrawable makePillBorder(int fill, int stroke, Activity activity) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(DS.dp(activity, 999));
        d.setStroke(DS.dp(activity, 1), stroke);
        return d;
    }

    private static View makeDot(int color, Activity activity) {
        View dot = new View(activity);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                DS.dp(activity, 5),
                DS.dp(activity, 5)
        );
        lp.setMargins(DS.dp(activity, 2), 0, DS.dp(activity, 2), 0);
        dot.setLayoutParams(lp);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color);
        dot.setBackground(bg);

        return dot;
    }

    private static boolean isPastDay(int dayNum, int month, int year, Calendar today) {
        if (year < today.get(Calendar.YEAR)) {
            return true;
        }

        if (year > today.get(Calendar.YEAR)) {
            return false;
        }

        if (month < today.get(Calendar.MONTH)) {
            return true;
        }

        if (month > today.get(Calendar.MONTH)) {
            return false;
        }

        return dayNum < today.get(Calendar.DAY_OF_MONTH);
    }

    private static String shortMoney(double value) {
        return value >= 1000
                ? String.format(Locale.getDefault(), "%.1fk€", value / 1000.0)
                : String.format(Locale.getDefault(), "%.0f€", value);
    }

    public interface OnDayClick {
        void onDaySelected(int day, int month, int year, long dayStart, long dayEnd);
    }

    private static class DayIndicators {
        boolean hasIncome = false;
        boolean hasExpense = false;
        boolean hasFixed = false;
        boolean hasRdv = false;
        double dayIncome = 0;
        double dayExpense = 0;
    }
}