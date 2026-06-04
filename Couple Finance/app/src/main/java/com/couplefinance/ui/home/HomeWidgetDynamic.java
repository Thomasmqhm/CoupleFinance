package com.couplefinance.ui.home;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HomeWidgetDynamic — Grille de widgets intelligents premium
 *
 * D1 : chaque widget est actionnable — il signale une tendance, une anomalie
 * ou une projection, pas juste un chiffre brut.
 *
 * Layout 2×N — hauteur 96dp — spacing 14dp
 */
public final class HomeWidgetDynamic {

    private final Activity          activity;
    private final SharedPreferences prefs;

    public HomeWidgetDynamic(Activity activity, SharedPreferences prefs) {
        this.activity = activity;
        this.prefs    = prefs;
    }

    // ─────────────────────────────────────────────────────────────
    // RENDER — compat overloads
    // ─────────────────────────────────────────────────────────────

    public void render(LinearLayout container,
                       double income, double expenses, double realBalance,
                       double projectedEndBalance, double monthMovement,
                       int txCount, int incomeCount, int expenseCount,
                       double todayExpenses, int todayExpenseCount,
                       int activeCategoryCount,
                       double biggestExpenseAmount, String biggestExpenseLabel,
                       String biggestExpenseCategory,
                       Map<String, Double> incomeSources,
                       double commonStartBalance, String orderPrefKey) {
        render(container, income, expenses, realBalance, projectedEndBalance, monthMovement,
                txCount, incomeCount, expenseCount, todayExpenses, todayExpenseCount,
                activeCategoryCount, biggestExpenseAmount, biggestExpenseLabel,
                biggestExpenseCategory, incomeSources, commonStartBalance, orderPrefKey, 0);
    }

    public void render(LinearLayout container,
                       double income, double expenses, double realBalance,
                       double projectedEndBalance, double monthMovement,
                       int txCount, int incomeCount, int expenseCount,
                       double todayExpenses, int todayExpenseCount,
                       int activeCategoryCount,
                       double biggestExpenseAmount, String biggestExpenseLabel,
                       String biggestExpenseCategory,
                       Map<String, Double> incomeSources,
                       double commonStartBalance, String orderPrefKey,
                       int membersInOverdraft) {
        render(container, income, expenses, realBalance, projectedEndBalance, monthMovement,
                txCount, incomeCount, expenseCount, todayExpenses, todayExpenseCount,
                activeCategoryCount, biggestExpenseAmount, biggestExpenseLabel,
                biggestExpenseCategory, incomeSources, commonStartBalance, orderPrefKey,
                membersInOverdraft, 0, 0, null, null);
    }

    // ─────────────────────────────────────────────────────────────
    // RENDER — version complète (D1)
    // ─────────────────────────────────────────────────────────────

    public void render(LinearLayout container,
                       double income, double expenses, double realBalance,
                       double projectedEndBalance, double monthMovement,
                       int txCount, int incomeCount, int expenseCount,
                       double todayExpenses, int todayExpenseCount,
                       int activeCategoryCount,
                       double biggestExpenseAmount, String biggestExpenseLabel,
                       String biggestExpenseCategory,
                       Map<String, Double> incomeSources,
                       double commonStartBalance, String orderPrefKey,
                       int membersInOverdraft,
                       double prevIncome, double prevExpenses,
                       Map<String, Double> catTotals,
                       Map<String, Double> catBudgets) {

        if (container == null) return;
        container.removeAllViews();

        Calendar now    = Calendar.getInstance();
        int day         = now.get(Calendar.DAY_OF_MONTH);
        int maxDay      = now.getActualMaximum(Calendar.DAY_OF_MONTH);
        int daysLeft    = maxDay - day;

        double avgDaily          = day > 0 ? expenses / day : 0;
        double forecastExpenses  = avgDaily * maxDay;
        double trendForecast     = commonStartBalance + income - forecastExpenses;
        double forecastBalance   = Math.abs(projectedEndBalance) > 0.01 ? projectedEndBalance : trendForecast;
        double savingsRate       = income > 0 ? ((income - expenses) / income) * 100.0 : 0;
        double expenseVsIncome   = income > 0 ? (expenses / income) * 100.0 : 0;
        double netSavings        = income - expenses;

        ArrayList<WidgetData> widgets = new ArrayList<>();

        // ── W1 : Épargne nette du mois (remplace "Résumé Express") ───────────
        if (isEnabled(HomeWidgets.W_QUICK_SUMMARY)) {
            String sign = netSavings >= 0 ? "+" : "";
            int col = netSavings >= 0 ? ThemeColors.success() : ThemeColors.danger();
            String sub = "Revenu − dépenses";
            widgets.add(new WidgetData("◎", "ÉPARGNE", sub,
                    sign + fmtMoney(netSavings), col)
                    .sub(fmtMoney(income) + " revenus · " + fmtMoney(expenses) + " dép."));
        }

        // ── W2 : Santé du budget (B1 enrichi) ────────────────────────────────
        if (isEnabled(HomeWidgets.W_BUDGET_HEALTH)) {
            String health; int hColor;
            if (membersInOverdraft > 0) {
                health = "Découvert (" + membersInOverdraft + ")";
                hColor = ThemeColors.danger();
            } else if (expenseVsIncome >= 100) { health = "Critique";     hColor = ThemeColors.danger(); }
            else if (expenseVsIncome >=  85)   { health = "Tendu";        hColor = ThemeColors.warning(); }
            else if (expenseVsIncome >=  65)   { health = "Stable";       hColor = ThemeColors.primary(); }
            else                               { health = "Excellent";    hColor = ThemeColors.success(); }
            String pctStr = income > 0.01
                    ? String.format(Locale.FRANCE, "%.0f%%", expenseVsIncome) + " · " + health
                    : health;
            widgets.add(new WidgetData("⌁", "SANTÉ", "Du budget", pctStr, hColor)
                    .sub(membersInOverdraft > 0 ? membersInOverdraft + " membre(s) à découvert" : "Basé sur revenus/dépenses"));
        }

        // ── W3 : Rythme journalier vs mois précédent ─────────────────────────
        if (isEnabled(HomeWidgets.W_DAILY_BURN)) {
            double prevAvgDaily = prevExpenses > 0.01 ? prevExpenses / maxDay : 0;
            String trend = "";
            int burnColor = ThemeColors.primary();
            if (prevAvgDaily > 0.01) {
                double delta = ((avgDaily - prevAvgDaily) / prevAvgDaily) * 100.0;
                if (delta > 10) {
                    trend = " ↑" + String.format(Locale.FRANCE, "%.0f%%", Math.abs(delta));
                    burnColor = ThemeColors.danger();
                } else if (delta < -10) {
                    trend = " ↓" + String.format(Locale.FRANCE, "%.0f%%", Math.abs(delta));
                    burnColor = ThemeColors.success();
                } else {
                    trend = " ≈";
                }
            }
            widgets.add(new WidgetData("◷", "RYTHME", "Journalier",
                    fmtMoney(avgDaily) + trend, burnColor)
                    .sub("Prévision : " + fmtMoney(forecastExpenses) + " fin de mois"));
        }

        // ── W4 : Projection fin de mois — actionnable ────────────────────────
        if (isEnabled(HomeWidgets.W_MONTH_FORECAST)) {
            String forecastLabel;
            int fColor;
            if (forecastBalance >= 0) {
                if (forecastBalance > commonStartBalance * 0.1) {
                    forecastLabel = "OK · " + fmtMoney(forecastBalance);
                    fColor = ThemeColors.success();
                } else {
                    forecastLabel = "Juste · " + fmtMoney(forecastBalance);
                    fColor = ThemeColors.warning();
                }
            } else {
                forecastLabel = "Risque · " + fmtMoney(forecastBalance);
                fColor = ThemeColors.danger();
            }
            widgets.add(new WidgetData("↗", "PROJECTION", daysLeft + "j restants",
                    forecastLabel, fColor)
                    .sub("Départ : " + fmtMoney(commonStartBalance)));
        }

        // ── W5 : Dépassement budget — catégorie la plus en risque ─────────────
        // Remplace "Catégories utilisées : N" (inutile)
        if (isEnabled(HomeWidgets.W_CATEGORY_COUNT)) {
            String budgetAlert = null;
            double worstRatio  = 0;
            int badColor = ThemeColors.warning();
            if (catTotals != null && catBudgets != null) {
                for (Map.Entry<String, Double> e : catTotals.entrySet()) {
                    String cat = e.getKey();
                    double spent = e.getValue() == null ? 0 : e.getValue();
                    Double bObj = catBudgets.get(cat);
                    if (bObj == null || bObj <= 0) continue;
                    double ratio = spent / bObj;
                    if (ratio > worstRatio) {
                        worstRatio = ratio;
                        int pct = (int) Math.round(ratio * 100.0);
                        String name = cat.length() > 10 ? cat.substring(0, 9) + "…" : cat;
                        budgetAlert = name + " " + pct + "%";
                        badColor = ratio >= 1.0 ? ThemeColors.danger() : ThemeColors.warning();
                    }
                }
            }
            if (budgetAlert != null) {
                widgets.add(new WidgetData("!", "BUDGET", "Catégorie",
                        budgetAlert, badColor)
                        .sub("Attention : budget dépassé ou tendu"));
            } else {
                widgets.add(new WidgetData("✓", "BUDGET", "Catégories",
                        activeCategoryCount + " · OK", ThemeColors.success())
                        .sub("Tous les budgets respectés"));
            }
        }

        // ── W6 : Comparaison mois précédent ──────────────────────────────────
        // Remplace "Sources de revenus : N" (peu utile)
        if (isEnabled(HomeWidgets.W_INCOME_SOURCES)) {
            double expDelta = prevExpenses > 0.01 ? expenses - prevExpenses : 0;
            String cmpLabel;
            int cmpColor;
            if (prevExpenses < 0.01) {
                cmpLabel = "Pas de données";
                cmpColor = ThemeColors.primary();
            } else {
                String sign = expDelta >= 0 ? "+" : "";
                double pct = Math.abs(expDelta / prevExpenses) * 100.0;
                cmpLabel = sign + fmtMoney(expDelta)
                        + String.format(Locale.FRANCE, " (%.0f%%)", pct);
                cmpColor = expDelta <= 0 ? ThemeColors.success() : ThemeColors.danger();
            }
            String prevSub = prevExpenses > 0.01 ? "Mois dernier : " + fmtMoney(prevExpenses) : "Premier mois enregistré";
            widgets.add(new WidgetData("↺", "VS MOIS", "Précédent",
                    cmpLabel, cmpColor)
                    .sub(prevSub));
        }

        // ── W7 : Plus grosse dépense (améliorée) ─────────────────────────────
        if (isEnabled(HomeWidgets.W_BIGGEST_EXPENSE)) {
            if (biggestExpenseAmount > 0) {
                String shortLabel = biggestExpenseLabel != null && biggestExpenseLabel.length() > 12
                        ? biggestExpenseLabel.substring(0, 11) + "…"
                        : (biggestExpenseLabel != null ? biggestExpenseLabel : "—");
                String catSub = biggestExpenseCategory != null && !biggestExpenseCategory.isEmpty()
                        ? "Catégorie : " + biggestExpenseCategory : "Ce mois-ci";
                widgets.add(new WidgetData("▣", "MAX DÉPENSE",
                        shortLabel,
                        fmtMoney(biggestExpenseAmount),
                        ThemeColors.danger())
                        .sub(catSub));
            } else {
                widgets.add(new WidgetData("▣", "MAX DÉPENSE", "Ce mois-ci",
                        "Aucune", ThemeColors.textMuted())
                        .sub("Aucune dépense enregistrée"));
            }
        }

        // ── W8 : Taux d'épargne avec objectif ────────────────────────────────
        if (isEnabled(HomeWidgets.W_SAVINGS_RATE)) {
            int savColor;
            String savLabel;
            if (savingsRate >= 20)     { savColor = ThemeColors.success(); savLabel = "Objectif ✓"; }
            else if (savingsRate >= 10){ savColor = ThemeColors.warning(); savLabel = "Objectif 20%"; }
            else if (savingsRate >= 0) { savColor = ThemeColors.primary(); savLabel = "À améliorer"; }
            else                       { savColor = ThemeColors.danger();  savLabel = "Déficit"; }
            widgets.add(new WidgetData("◈", "ÉPARGNE",
                    savLabel,
                    String.format(Locale.FRANCE, "%.1f%%", savingsRate),
                    savColor)
                    .sub("Soit " + fmtMoney(Math.max(0, netSavings)) + " mis de côté"));
        }

        // ── W9 : Activité — anomalie de dépense ──────────────────────────────
        if (isEnabled(HomeWidgets.W_ACTIVITY)) {
            // Détecte si une catégorie a un volume atypique (>30% des dépenses totales)
            String anomaly = null;
            if (catTotals != null && expenses > 0.01) {
                double worstShare = 0;
                for (Map.Entry<String, Double> e : catTotals.entrySet()) {
                    double share = (e.getValue() == null ? 0 : e.getValue()) / expenses;
                    if (share > 0.30 && share > worstShare) {
                        worstShare = share;
                        String n = e.getKey();
                        n = n.length() > 10 ? n.substring(0, 9) + "…" : n;
                        anomaly = n + " " + (int)(share * 100) + "%";
                    }
                }
            }
            if (anomaly != null) {
                widgets.add(new WidgetData("⚡", "ANOMALIE",
                        "Catégorie dominante",
                        anomaly, ThemeColors.warning())
                        .sub("Représente +30% des dépenses"));
            } else {
                widgets.add(new WidgetData("↺", "ACTIVITÉ", "Du mois",
                        txCount + " opérations", ThemeColors.primary())
                        .sub(incomeCount + " revenus · " + expenseCount + " dépenses"));
            }
        }

        sortWidgets(widgets, orderPrefKey);
        renderGrid(container, widgets);
    }

    // ─────────────────────────────────────────────────────────────
    // GRILLE 2 COLONNES
    // ─────────────────────────────────────────────────────────────

    private void renderGrid(LinearLayout container, ArrayList<WidgetData> widgets) {
        if (container == null || widgets == null) return;

        LinearLayout currentRow = null;

        for (int i = 0; i < widgets.size(); i++) {

            if (i % 2 == 0) {
                currentRow = new LinearLayout(activity);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
                rowLp.bottomMargin = DS.dp(activity, DS.CARD_GAP);
                container.addView(currentRow, rowLp);
            }

            View card = createCard(widgets.get(i), i);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.height = -2; // wrap_content — height driven by content
            lp.setMargins(
                    i % 2 == 0 ? 0                              : DS.dp(activity, DS.CARD_GAP / 2 + 2),
                    0,
                    i % 2 == 0 ? DS.dp(activity, DS.CARD_GAP / 2 + 2) : 0,
                    0
            );

            if (currentRow != null) currentRow.addView(card, lp);
        }

        if (widgets.size() % 2 != 0 && currentRow != null) {
            currentRow.addView(new View(activity), new LinearLayout.LayoutParams(0, 0, 1f));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // CARD WIDGET
    // ─────────────────────────────────────────────────────────────

    private View createCard(final WidgetData data, int index) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.TOP);
        card.setPadding(
                DS.dp(activity, DS.CARD_PADDING), DS.dp(activity, DS.CARD_PADDING),
                DS.dp(activity, DS.CARD_PADDING), DS.dp(activity, DS.CARD_PADDING)
        );
        // minHeight aligns with other dashboard cards
        card.setMinimumHeight(DS.dp(activity, 140));
        card.setBackground(makeWidgetBg(data.accent));
        HomeDashboardStyle.applyNativeElevation(card, 4f);
        HomeDashboardStyle.applyPressEffect(card);

        card.setAlpha(0f);
        card.setTranslationY(DS.dp(activity, DS.SPACE_14));
        card.setScaleX(0.96f);
        card.setScaleY(0.96f);
        card.animate()
                .alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setStartDelay(index * 28L).setDuration(DS.ANIM_NORMAL)
                .setInterpolator(new DecelerateInterpolator(1.8f)).start();

        // Icône circulaire
        TextView icon = new TextView(activity);
        icon.setText(data.icon); icon.setTextSize(16f);
        icon.setTextColor(data.accent); icon.setTypeface(Typeface.DEFAULT_BOLD);
        icon.setGravity(Gravity.CENTER); icon.setIncludeFontPadding(false);
        GradientDrawable iBg = new GradientDrawable();
        iBg.setShape(GradientDrawable.OVAL);
        iBg.setColor(ThemeColors.withAlpha(data.accent, 20));
        iBg.setStroke(DS.dp(activity, 1), ThemeColors.withAlpha(data.accent, 44));
        icon.setBackground(iBg);
        LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(DS.dp(activity, 32), DS.dp(activity, 32));
        iLp.bottomMargin = DS.dp(activity, DS.SPACE_8);
        card.addView(icon, iLp);

        // Label uppercase
        TextView label = new TextView(activity);
        label.setText(data.label); label.setTextColor(ThemeColors.textMuted());
        label.setTextSize(11f); label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setLetterSpacing(0.09f); label.setSingleLine(true); label.setIncludeFontPadding(false);
        card.addView(label);

        // Titre / sous-titre
        TextView title = new TextView(activity);
        title.setText(data.title); title.setTextColor(ThemeColors.textPrimary());
        title.setTextSize(14f); title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLetterSpacing(-0.01f); title.setSingleLine(true); title.setIncludeFontPadding(false);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(-1, -2);
        tLp.topMargin = DS.dp(activity, DS.SPACE_4);
        card.addView(title, tLp);

        card.addView(new View(activity), new LinearLayout.LayoutParams(-1, 0, 1f));

        // Valeur principale
        TextView value = new TextView(activity);
        value.setText(data.value);
        value.setTextColor(data.accent); value.setTextSize(18f);
        value.setTypeface(Typeface.DEFAULT_BOLD); value.setLetterSpacing(-0.015f);
        value.setSingleLine(true); value.setIncludeFontPadding(false);
        LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(-1, -2);
        vLp.topMargin = DS.dp(activity, DS.SPACE_6);
        card.addView(value, vLp);

        // Ligne de contexte optionnelle (sub)
        if (data.sub != null && !data.sub.isEmpty()) {
            TextView sub = new TextView(activity);
            sub.setText(data.sub);
            sub.setTextColor(ThemeColors.textMuted());
            sub.setTextSize(11f);
            sub.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
            sub.setSingleLine(true);
            sub.setIncludeFontPadding(false);
            LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(-1, -2);
            sLp.topMargin = DS.dp(activity, DS.SPACE_4);
            card.addView(sub, sLp);
        }

        return card;
    }

    // ─────────────────────────────────────────────────────────────
    // DRAWABLES
    // ─────────────────────────────────────────────────────────────

    private GradientDrawable makeWidgetBg(int accent) {
        int base   = ThemeColors.surfaceFloating();
        int tinted = ThemeColors.blend(base, accent, 0.04f);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{ThemeColors.blend(tinted, Color.WHITE, 0.18f), tinted}
        );
        bg.setCornerRadius(DS.dp(activity, DS.RADIUS_2XL));
        bg.setStroke(DS.dp(activity, 1), ThemeColors.withAlpha(accent, 30));
        return bg;
    }

    // ─────────────────────────────────────────────────────────────
    // TRI
    // ─────────────────────────────────────────────────────────────

    private void sortWidgets(ArrayList<WidgetData> widgets, String orderPrefKey) {
        if (widgets == null || widgets.size() < 2 || prefs == null || orderPrefKey == null) return;
        String stored = prefs.getString(orderPrefKey, null);
        if (stored == null || stored.trim().isEmpty()) return;

        ArrayList<String> order = new ArrayList<>();
        for (String p : stored.split("\\|")) {
            if (p != null && !p.trim().isEmpty()) order.add(p.trim());
        }

        String[] keys   = HomeWidgets.getDynamicKeys();
        String[] titles = HomeWidgets.getDynamicTitles();

        Collections.sort(widgets, (a, b) -> Integer.compare(
                orderIndex(resolveTitle(a), order, keys, titles),
                orderIndex(resolveTitle(b), order, keys, titles)
        ));
    }

    private String resolveTitle(WidgetData d) {
        if (d == null) return "";
        String c = (d.label + " " + d.title).toLowerCase(Locale.FRANCE);
        if (c.contains("épargne") && c.contains("nette"))  return "Résumé express";
        if (c.contains("épargne") && c.contains("objectif")) return "Taux d'épargne";
        if (c.contains("épargne"))                         return "Résumé express";
        if (c.contains("santé"))                           return "Santé du budget";
        if (c.contains("rythme"))                          return "Rythme journalier";
        if (c.contains("projection"))                      return "Projection fin de mois";
        if (c.contains("max dépense") || c.contains("plus grosse")) return "Plus grosse dépense";
        if (c.contains("budget") && c.contains("catégor")) return "Nombre de catégories";
        if (c.contains("vs mois") || c.contains("précédent")) return "Sources de revenus";
        if (c.contains("activité") || c.contains("anomalie")) return "Activité du mois";
        return d.label + " " + d.title;
    }

    private int orderIndex(String title, List<String> order, String[] keys, String[] titles) {
        String key = null;
        for (int i = 0; i < titles.length && i < keys.length; i++) {
            if (titles[i].equals(title)) { key = keys[i]; break; }
        }
        int idx = key == null ? -1 : order.indexOf(key);
        return idx < 0 ? 999 : idx;
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────

    private boolean isEnabled(String key) { return prefs == null || prefs.getBoolean(key, true); }
    private String fmtMoney(double v)     { return String.format(Locale.FRANCE, "%,.0f €", v); }

    // ─────────────────────────────────────────────────────────────
    // DATA
    // ─────────────────────────────────────────────────────────────

    private static class WidgetData {
        String icon, label, title, value, sub;
        int accent;
        WidgetData(String i, String l, String t, String v, int a) {
            icon = i; label = l; title = t; value = v; accent = a; sub = null;
        }
        WidgetData sub(String s) { this.sub = s; return this; }
    }
}
