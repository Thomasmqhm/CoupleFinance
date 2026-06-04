package com.couplefinance.ui.home;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.Fmt;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public final class HomeWidgetCalendar {

    private final Activity activity;
    private final SharedPreferences prefs;

    private View currentCalendarPopup;

    public HomeWidgetCalendar(Activity activity, SharedPreferences prefs) {
        this.activity = activity;
        this.prefs = prefs;
    }

    public void render(GridLayout calendarGrid,
                       TextView tvCalMonth,
                       Calendar calendarMonth,
                       List<String[]> cachedTransactions) {

        if (calendarGrid == null || tvCalMonth == null || calendarMonth == null) return;

        calendarGrid.removeAllViews();

        HomeFixedChargeProvider.loadPlannedChargesForCurrentMonth(
                new HomeFixedChargeProvider.Callback() {
                    public void onLoaded(ArrayList<String[]> plannedCharges) {
                        activity.runOnUiThread(() -> {
                            ArrayList<String[]> merged = new ArrayList<>();
                            if (cachedTransactions != null) merged.addAll(cachedTransactions);
                            if (plannedCharges != null) merged.addAll(plannedCharges);
                            renderInternal(calendarGrid, tvCalMonth, calendarMonth, merged);
                        });
                    }

                    public void onError(String error) {
                        activity.runOnUiThread(() ->
                                renderInternal(calendarGrid, tvCalMonth, calendarMonth, cachedTransactions));
                    }
                });
    }

    private void renderInternal(GridLayout calendarGrid,
                                TextView tvCalMonth,
                                Calendar calendarMonth,
                                List<String[]> transactions) {

        calendarGrid.removeAllViews();
        calendarGrid.setColumnCount(7);

        SimpleDateFormat mFmt = new SimpleDateFormat("MMMM yyyy", Locale.FRENCH);
        String mn = mFmt.format(calendarMonth.getTime());
        tvCalMonth.setText(mn.substring(0, 1).toUpperCase(Locale.FRANCE) + mn.substring(1));
        tvCalMonth.setTextColor(ThemeColors.textPrimary());
        tvCalMonth.setTextSize(DS.TEXT_BODY);
        tvCalMonth.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tvCalMonth.setLetterSpacing(-0.01f);
        tvCalMonth.setIncludeFontPadding(false);

        Calendar now = Calendar.getInstance();
        Calendar week = (Calendar) now.clone();

        int dow = week.get(Calendar.DAY_OF_WEEK);
        int offset = dow == Calendar.SUNDAY ? 6 : dow - Calendar.MONDAY;
        week.add(Calendar.DAY_OF_MONTH, -offset);

        String[] dayLabels = {"L", "M", "M", "J", "V", "S", "D"};

        for (int d = 0; d < 7; d++) {
            Calendar dCal = (Calendar) week.clone();
            dCal.add(Calendar.DAY_OF_MONTH, d);

            boolean isWeekend = dCal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
                    || dCal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY;

            TextView tv = new TextView(activity);
            tv.setText(dayLabels[d]);
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(DS.TEXT_MICRO);
            tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            tv.setTextColor(isWeekend ? ThemeColors.withAlpha(ThemeColors.textMuted(), 140) : ThemeColors.textMuted());
            tv.setIncludeFontPadding(false);
            tv.setLetterSpacing(0.08f);

            GridLayout.LayoutParams g = new GridLayout.LayoutParams();
            g.width = 0;
            g.height = DS.dp(activity, 26);
            g.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            tv.setLayoutParams(g);

            calendarGrid.addView(tv);
        }

        for (int i = 0; i < 7; i++) {
            Calendar dCal = (Calendar) week.clone();
            dCal.add(Calendar.DAY_OF_MONTH, i);

            final int day = dCal.get(Calendar.DAY_OF_MONTH);
            final int month = dCal.get(Calendar.MONTH);
            final int year = dCal.get(Calendar.YEAR);

            boolean isToday = dCal.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                    && dCal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR);

            boolean isWeekend = dCal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
                    || dCal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY;

            boolean hasIncome = false;
            boolean hasExpense = false;
            boolean hasFixed = false;

            double dayTotal = 0;
            final ArrayList<String[]> dayTx = new ArrayList<>();

            if (transactions != null) {
                for (String[] tx : transactions) {
                    if (tx == null || tx.length <= 4) continue;

                    try {
                        long ms = Long.parseLong(tx[4]);
                        Calendar tc = Calendar.getInstance();
                        tc.setTimeInMillis(ms);

                        if (tc.get(Calendar.DAY_OF_MONTH) != day
                                || tc.get(Calendar.MONTH) != month
                                || tc.get(Calendar.YEAR) != year) continue;

                        dayTx.add(tx);

                        String type = tx.length > 2 && tx[2] != null ? tx[2] : "";

                        double amt = 0;
                        try {
                            amt = Double.parseDouble(tx[1]);
                        } catch (Exception ignored) {}

                        if ("income".equals(type)) {
                            hasIncome = true;
                        } else if ("fixed".equals(type) || "fixed_planned".equals(type) || "fixed_done".equals(type)) {
                            hasFixed = true;
                            hasExpense = true;
                            dayTotal += amt;
                        } else {
                            hasExpense = true;
                            dayTotal += amt;
                        }

                    } catch (Exception ignored) {}
                }
            }

            LinearLayout cell = new LinearLayout(activity);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(
                    DS.dp(activity, 2),
                    DS.dp(activity, 6),
                    DS.dp(activity, 2),
                    DS.dp(activity, 4)
            );

            GridLayout.LayoutParams g = new GridLayout.LayoutParams();
            g.width = 0;
            g.height = DS.dp(activity, 80);
            g.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            cell.setLayoutParams(g);

            TextView tvDay = new TextView(activity);
            tvDay.setText(String.valueOf(day));
            tvDay.setGravity(Gravity.CENTER);
            tvDay.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            tvDay.setIncludeFontPadding(false);

            int dayBgColor = Color.TRANSPARENT;
            int dayStroke = Color.TRANSPARENT;
            int dayTextColor;

            if (isToday) {
                dayBgColor = ThemeColors.primary();
                dayStroke = Color.TRANSPARENT;
                dayTextColor = Color.WHITE;
                tvDay.setTextSize(DS.TEXT_BODY_SMALL + 0.5f);
            } else if (hasExpense && hasIncome) {
                dayBgColor = ThemeColors.surfaceSoft();
                dayStroke = ThemeColors.borderSoft();
                dayTextColor = ThemeColors.textPrimary();
                tvDay.setTextSize(DS.TEXT_BODY_SMALL);
            } else if (hasIncome) {
                dayBgColor = ThemeColors.successBackground();
                dayStroke = ThemeColors.withAlpha(ThemeColors.success(), 50);
                dayTextColor = ThemeColors.success();
                tvDay.setTextSize(DS.TEXT_BODY_SMALL);
            } else if (hasExpense) {
                dayBgColor = ThemeColors.primarySoft();
                dayStroke = ThemeColors.withAlpha(ThemeColors.primary(), 48);
                dayTextColor = ThemeColors.primary();
                tvDay.setTextSize(DS.TEXT_BODY_SMALL);
            } else {
                dayTextColor = isWeekend
                        ? ThemeColors.withAlpha(ThemeColors.textMuted(), 160)
                        : ThemeColors.textPrimary();
                tvDay.setTextSize(DS.TEXT_BODY_SMALL);
            }

            tvDay.setTextColor(dayTextColor);
            tvDay.setBackground(buildDayBackground(dayBgColor, dayStroke, isToday));

            LinearLayout.LayoutParams dayLp = new LinearLayout.LayoutParams(
                    DS.dp(activity, 36),
                    DS.dp(activity, 36)
            );
            cell.addView(tvDay, dayLp);

            if ((hasExpense || hasIncome) && dayTotal > 0.01) {
                TextView tvAmt = new TextView(activity);
                tvAmt.setText("-" + fmtShort(dayTotal));
                tvAmt.setTextSize(9f);
                tvAmt.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                tvAmt.setTextColor(isToday
                        ? ThemeColors.withAlpha(Color.WHITE, 200)
                        : ThemeColors.primary());
                tvAmt.setGravity(Gravity.CENTER);
                tvAmt.setIncludeFontPadding(false);
                tvAmt.setSingleLine(true);

                LinearLayout.LayoutParams amtLp = new LinearLayout.LayoutParams(-2, -2);
                amtLp.topMargin = DS.dp(activity, 5);
                cell.addView(tvAmt, amtLp);
            } else {
                if (hasExpense || hasIncome || hasFixed) {
                    LinearLayout dots = new LinearLayout(activity);
                    dots.setOrientation(LinearLayout.HORIZONTAL);
                    dots.setGravity(Gravity.CENTER);

                    LinearLayout.LayoutParams dotsLp = new LinearLayout.LayoutParams(-2, DS.dp(activity, 10));
                    dotsLp.topMargin = DS.dp(activity, 5);
                    dots.setLayoutParams(dotsLp);

                    if (hasExpense) dots.addView(buildDot(isToday ? ThemeColors.withAlpha(Color.WHITE, 200) : ThemeColors.primary()));
                    if (hasIncome) dots.addView(buildDot(isToday ? ThemeColors.withAlpha(Color.WHITE, 200) : ThemeColors.success()));
                    if (hasFixed) dots.addView(buildDot(isToday ? ThemeColors.withAlpha(Color.WHITE, 200) : ThemeColors.info()));

                    cell.addView(dots);
                }
            }

            cell.setOnClickListener(v -> showDayDetails(day, dayTx));

            cell.setAlpha(0f);
            cell.animate()
                    .alpha(1f)
                    .setStartDelay(i * 32L + 60L)
                    .setDuration(DS.ANIM_FAST)
                    .start();

            calendarGrid.addView(cell);
        }
    }

    private void showDayDetails(int day, ArrayList<String[]> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            showCalendarToast("Jour " + day, "Aucune opération ce jour-là.");
            return;
        }

        ArrayList<String> lines = new ArrayList<>();

        for (int i = 0; i < transactions.size(); i++) {
            String[] tx = transactions.get(i);
            if (tx == null || tx.length < 4) continue;

            String label = cleanLabel(tx[0]);
            String type = tx.length > 2 ? tx[2] : "";
            String category = tx.length > 3 ? tx[3] : "";

            double amount = 0;
            try {
                amount = Double.parseDouble(tx[1]);
            } catch (Exception ignored) {}

            String prefix;

            if ("fixed_planned".equals(type)) {
                prefix = "Prévu · -";
            } else if ("fixed_done".equals(type)) {
                prefix = "Passé · -";
            } else {
                prefix = "income".equals(type) ? "+" : "-";
            }

            String line = prefix + Fmt.money(amount) + " · " + label;

            if (category != null && !category.isEmpty()) {
                line += " · " + category;
            }

            lines.add(line);
        }

        showCalendarToast("Jour " + day, lines);
    }

    private void showCalendarToast(String titleText, String bodyText) {
        ArrayList<String> lines = new ArrayList<>();
        lines.add(bodyText);
        showCalendarToast(titleText, lines);
    }

    private void showCalendarToast(String titleText, ArrayList<String> lines) {
        activity.runOnUiThread(() -> {
            try {
                ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();

                if (currentCalendarPopup != null) {
                    try {
                        decor.removeView(currentCalendarPopup);
                    } catch (Exception ignored) {}
                    currentCalendarPopup = null;
                }

                FrameLayout overlay = new FrameLayout(activity);
                overlay.setClickable(false);
                overlay.setClipChildren(false);
                overlay.setClipToPadding(false);

                FrameLayout.LayoutParams overlayLp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );

                LinearLayout card = new LinearLayout(activity);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(
                        DS.dp(activity, 14),
                        DS.dp(activity, 12),
                        DS.dp(activity, 14),
                        DS.dp(activity, 12)
                );
                card.setMinimumHeight(DS.dp(activity, 62));
                card.setElevation(DS.dp(activity, 18));

                GradientDrawable bg = new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{
                                ThemeColors.withAlpha(Color.WHITE, 248),
                                ThemeColors.withAlpha(Color.WHITE, 234)
                        }
                );
                bg.setCornerRadius(DS.dp(activity, 26));
                bg.setStroke(DS.dp(activity, 1), ThemeColors.withAlpha(ThemeColors.primary(), 34));
                card.setBackground(bg);

                TextView icon = new TextView(activity);
                icon.setText("•");
                icon.setTextColor(Color.WHITE);
                icon.setTextSize(22f);
                icon.setGravity(Gravity.CENTER);
                icon.setTypeface(Typeface.DEFAULT_BOLD);
                icon.setIncludeFontPadding(false);
                icon.setBackground(circle(ThemeColors.primary()));

                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                        DS.dp(activity, 38),
                        DS.dp(activity, 38)
                );
                iconLp.rightMargin = DS.dp(activity, 12);
                card.addView(icon, iconLp);

                LinearLayout texts = new LinearLayout(activity);
                texts.setOrientation(LinearLayout.VERTICAL);

                TextView title = new TextView(activity);
                title.setText(titleText);
                title.setTextColor(ThemeColors.text());
                title.setTextSize(13.5f);
                title.setTypeface(Typeface.DEFAULT_BOLD);
                title.setIncludeFontPadding(false);
                texts.addView(title);

                int maxLines = Math.min(lines == null ? 0 : lines.size(), 4);
                StringBuilder body = new StringBuilder();

                if (lines != null && !lines.isEmpty()) {
                    for (int i = 0; i < maxLines; i++) {
                        if (i > 0) body.append("\n");
                        body.append("• ").append(lines.get(i));
                    }

                    if (lines.size() > maxLines) {
                        body.append("\n+ ").append(lines.size() - maxLines).append(" autre");
                        if (lines.size() - maxLines > 1) body.append("s");
                    }
                }

                TextView bodyView = new TextView(activity);
                bodyView.setText(body.toString());
                bodyView.setTextColor(ThemeColors.subtext());
                bodyView.setTextSize(12.2f);
                bodyView.setLineSpacing(DS.dp(activity, 2), 1f);
                bodyView.setIncludeFontPadding(false);
                bodyView.setMaxLines(5);
                bodyView.setEllipsize(android.text.TextUtils.TruncateAt.END);

                LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2);
                bodyLp.topMargin = DS.dp(activity, 4);
                texts.addView(bodyView, bodyLp);

                card.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

                FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                        DS.dp(activity, 330),
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                cardLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                cardLp.bottomMargin = DS.dp(activity, 34);

                overlay.addView(card, cardLp);
                decor.addView(overlay, overlayLp);

                currentCalendarPopup = overlay;

                card.setAlpha(0f);
                card.setTranslationY(DS.dp(activity, 28));
                card.setScaleX(0.96f);
                card.setScaleY(0.96f);

                card.animate()
                        .alpha(1f)
                        .translationY(0)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(220)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (currentCalendarPopup == overlay) {
                        card.animate()
                                .alpha(0f)
                                .translationY(DS.dp(activity, 20))
                                .scaleX(0.97f)
                                .scaleY(0.97f)
                                .setDuration(180)
                                .withEndAction(() -> {
                                    try {
                                        decor.removeView(overlay);
                                    } catch (Exception ignored) {}
                                    if (currentCalendarPopup == overlay) {
                                        currentCalendarPopup = null;
                                    }
                                })
                                .start();
                    }
                }, 3600);

            } catch (Exception ignored) {}
        });
    }

    private GradientDrawable buildDayBackground(int bgColor, int strokeColor, boolean isToday) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);

        if (isToday) {
            d = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{
                            ThemeColors.blend(ThemeColors.primary(), Color.WHITE, 0.14f),
                            ThemeColors.primary(),
                            ThemeColors.blend(ThemeColors.primaryDark(), Color.BLACK, 0.08f)
                    }
            );
            d.setShape(GradientDrawable.OVAL);
        } else if (bgColor != Color.TRANSPARENT) {
            d.setColor(bgColor);
            if (strokeColor != Color.TRANSPARENT) {
                d.setStroke(DS.dp(activity, 1), strokeColor);
            }
        }

        return d;
    }

    private View buildDot(int color) {
        View v = new View(activity);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                DS.dp(activity, 5),
                DS.dp(activity, 5)
        );
        lp.setMargins(DS.dp(activity, 2), 0, DS.dp(activity, 2), 0);
        v.setLayoutParams(lp);

        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        v.setBackground(d);

        return v;
    }

    private GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    private String fmtShort(double value) {
        if (value >= 1000) {
            return String.format(Locale.FRANCE, "%.1fk€", value / 1000.0);
        }

        return String.format(Locale.FRANCE, "%,.0f€", value);
    }

    private String cleanLabel(String label) {
        if (label == null) return "Dépense";

        if (label.contains(" · ")) {
            String[] parts = label.split(" · ");
            if (parts.length > 1) return parts[1].trim();
        }

        return label.trim().isEmpty() ? "Dépense" : label.trim();
    }
}