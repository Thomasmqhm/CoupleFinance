package com.couplefinance.ui.analyse;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.couplefinance.AuthManager;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.Fmt;
import com.couplefinance.data.CycleManager;
import com.couplefinance.data.FinancialInsightManager;
import com.couplefinance.data.HouseholdManager;
import com.couplefinance.utils.FirebaseConfig;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Écran Analyse & Prévisions.
 *
 * Contenu :
 *  - Score de santé financière (0-100) avec jauge animée
 *  - Résumé du cycle en cours (revenus / dépenses / solde net)
 *  - Graphique d'évolution mensuelle sur 6 mois (Canvas)
 *  - Répartition par catégorie avec barres animées
 *  - Prévision fin de cycle (extrapolation linéaire)
 *  - Top 5 commerçants du mois
 *  - Insights automatiques de FinancialInsightManager
 */
public class AnalyseView {

    private final Activity activity;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Executor executor = Executors.newSingleThreadExecutor();

    private LinearLayout rootLayout;
    private LinearLayout contentContainer;
    private TextView tvLoading;
    private TextView btnShare;

    private AnalyseCalculator lastCalc;
    private boolean isActive = true;

    public AnalyseView(Activity activity) {
        this.activity = activity;
    }

    public View getView() {
        ScrollView scroll = new ScrollView(activity);
        scroll.setBackgroundColor(ThemeColors.background());
        scroll.setFillViewport(true);

        rootLayout = new LinearLayout(activity);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(0, 0, 0, DS.dp(activity, 100));
        scroll.addView(rootLayout, new FrameLayout.LayoutParams(-1, -2));

        buildHeader();
        buildLoadingState();
        loadData();

        return scroll;
    }

    private void buildHeader() {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(DS.dp(activity, 20), DS.dp(activity, 20), DS.dp(activity, 20), DS.dp(activity, 4));

        TextView title = new TextView(activity);
        title.setText("Analyse & Prévisions");
        title.setTextSize(26f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(ThemeColors.text());
        header.addView(title);

        LinearLayout subRow = new LinearLayout(activity);
        subRow.setOrientation(LinearLayout.HORIZONTAL);
        subRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams subRowLp = new LinearLayout.LayoutParams(-1, -2);
        subRowLp.topMargin = DS.dp(activity, 4);
        header.addView(subRow, subRowLp);

        TextView sub = new TextView(activity);
        sub.setText(CycleManager.getInstance().getCurrentCycleLabel());
        sub.setTextSize(13f);
        sub.setTextColor(ThemeColors.subtext());
        subRow.addView(sub, new LinearLayout.LayoutParams(0, -2, 1f));

        btnShare = new TextView(activity);
        btnShare.setText("↗ Partager");
        btnShare.setTextSize(12f);
        btnShare.setTypeface(null, Typeface.BOLD);
        btnShare.setTextColor(ThemeColors.primary());
        btnShare.setPadding(DS.dp(activity, 10), DS.dp(activity, 6), DS.dp(activity, 10), DS.dp(activity, 6));
        btnShare.setVisibility(View.GONE);
        btnShare.setOnClickListener(v -> shareAnalysis());
        subRow.addView(btnShare);

        rootLayout.addView(header);
    }

    private void buildLoadingState() {
        contentContainer = new LinearLayout(activity);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setPadding(DS.dp(activity, 16), 0, DS.dp(activity, 16), 0);

        tvLoading = new TextView(activity);
        tvLoading.setText("Chargement des données...");
        tvLoading.setTextSize(14f);
        tvLoading.setTextColor(ThemeColors.subtext());
        tvLoading.setGravity(Gravity.CENTER);
        tvLoading.setPadding(0, DS.dp(activity, 40), 0, DS.dp(activity, 40));
        contentContainer.addView(tvLoading);

        rootLayout.addView(contentContainer);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Chargement des données
    // ─────────────────────────────────────────────────────────────────────────

    private void shareAnalysis() {
        if (lastCalc == null) return;

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMMM yyyy", Locale.FRENCH);
        String month = sdf.format(new java.util.Date());

        double income   = lastCalc.getCycleIncome();
        double expenses = lastCalc.getCycleExpenses();
        double savings  = income - expenses;
        int    score    = lastCalc.getHealthScore();

        StringBuilder sb = new StringBuilder();
        sb.append("📊 Analyse CoupleFinance — ").append(month).append("\n\n");
        sb.append("💰 Revenus : ").append(Fmt.money(income)).append("\n");
        sb.append("💸 Dépenses : ").append(Fmt.money(expenses)).append("\n");
        sb.append("🐷 Épargne nette : ").append(Fmt.money(savings)).append("\n");
        sb.append("❤️ Score santé : ").append(score).append("/100\n\n");

        List<AnalyseCalculator.MonthData> months = lastCalc.getLast6Months();
        if (!months.isEmpty()) {
            sb.append("📅 Évolution sur 6 mois :\n");
            for (AnalyseCalculator.MonthData m : months) {
                sb.append("  ").append(m.label).append(" → ").append(Fmt.money(m.expenses)).append("\n");
            }
        }

        AnalyseCalculator.Forecast forecast = lastCalc.getForecast();
        if (forecast != null && forecast.message != null && !forecast.message.isEmpty()) {
            sb.append("\n🔮 Prévision : ").append(forecast.message);
        }

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        activity.startActivity(Intent.createChooser(intent, "Partager l'analyse"));
    }

    private void loadData() {
        executor.execute(() -> {
            try {
                String token = AuthManager.getInstance().getFreshTokenSync();
                String householdId = HouseholdManager.getInstance().getHouseholdId();
                if (token == null || token.isEmpty() || householdId == null || householdId.isEmpty()) {
                    showError("Connexion requise.");
                    return;
                }

                String url = "https://firestore.googleapis.com/v1/projects/" + FirebaseConfig.PROJECT_ID
                        + "/databases/(default)/documents/households/" + householdId
                        + "/transactions?pageSize=500&key=" + FirebaseConfig.API_KEY;

                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(12000);

                try {
                    int code = conn.getResponseCode();
                    if (code != 200) { showError("Erreur réseau (" + code + ")"); return; }
                    String json = readStream(conn.getInputStream());
                    List<String[]> txs = parseTransactions(json);
                    uiHandler.post(() -> { if (isActive) renderAll(txs); });
                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                showError("Impossible de charger les données.");
            }
        });
    }

    private void showError(String msg) {
        uiHandler.post(() -> {
            if (!isActive) return;
            tvLoading.setText(msg);
            tvLoading.setTextColor(Color.parseColor("#EF4444"));
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rendu principal
    // ─────────────────────────────────────────────────────────────────────────

    private void renderAll(List<String[]> txs) {
        contentContainer.removeAllViews();

        AnalyseCalculator calc = new AnalyseCalculator(txs);
        lastCalc = calc;

        buildScoreCard(calc);
        buildMonthComparison(calc);
        buildCycleSummary(calc);
        buildEvolutionChart(calc);
        buildCategoryBreakdown(calc);
        buildForecast(calc);
        buildTopMerchants(calc);
        buildInsights(txs);

        if (btnShare != null) btnShare.setVisibility(View.VISIBLE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Comparaison mois précédent
    // ─────────────────────────────────────────────────────────────────────────

    private void buildMonthComparison(AnalyseCalculator calc) {
        List<AnalyseCalculator.MonthData> months = calc.getLast6Months();
        if (months == null || months.size() < 2) return;

        AnalyseCalculator.MonthData current  = months.get(months.size() - 1);
        AnalyseCalculator.MonthData previous = months.get(months.size() - 2);
        if (current.expenses == 0 && current.income == 0) return;

        LinearLayout card = makeCard();
        card.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText("📊 vs mois précédent");
        title.setTextSize(DS.TEXT_MD);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(ThemeColors.text());
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.bottomMargin = DS.dp(activity, 12);
        card.addView(title, tlp);

        card.addView(compRow("Dépenses",   previous.expenses, current.expenses, true));
        card.addView(compRow("Revenus",    previous.income,   current.income,   false));
        double prevNet = previous.income - previous.expenses;
        double currNet = current.income  - current.expenses;
        card.addView(compRow("Solde net",  prevNet,           currNet,          false));

        contentContainer.addView(card);
    }

    private LinearLayout compRow(String label, double prev, double curr, boolean lowerIsBetter) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.bottomMargin = DS.dp(activity, 8);
        row.setLayoutParams(rlp);

        TextView tvLabel = new TextView(activity);
        tvLabel.setText(label);
        tvLabel.setTextSize(DS.TEXT_SM);
        tvLabel.setTextColor(ThemeColors.subtext());
        row.addView(tvLabel, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView tvPrev = new TextView(activity);
        tvPrev.setText(Fmt.money(Math.abs(prev)));
        tvPrev.setTextSize(DS.TEXT_SM);
        tvPrev.setTextColor(ThemeColors.subtext());
        row.addView(tvPrev);

        TextView tvArrow = new TextView(activity);
        tvArrow.setText("  →  ");
        tvArrow.setTextSize(DS.TEXT_SM);
        tvArrow.setTextColor(ThemeColors.subtext());
        row.addView(tvArrow);

        TextView tvCurr = new TextView(activity);
        tvCurr.setText(Fmt.money(Math.abs(curr)));
        tvCurr.setTextSize(DS.TEXT_SM);
        tvCurr.setTypeface(Typeface.DEFAULT_BOLD);

        double diff = curr - prev;
        boolean improved = lowerIsBetter ? diff < -0.5 : diff > 0.5;
        boolean worsened = lowerIsBetter ? diff > 0.5  : diff < -0.5;
        String arrow = improved ? " ↘" : (worsened ? " ↗" : "");
        String diffStr = diff == 0 ? "" : (diff > 0 ? " (+" : " (") + Fmt.money(Math.abs(diff)) + ")";
        tvCurr.setText(Fmt.money(Math.abs(curr)) + arrow + diffStr);
        tvCurr.setTextColor(improved ? ThemeColors.success() : (worsened ? ThemeColors.danger() : ThemeColors.text()));
        row.addView(tvCurr);

        return row;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Score de santé financière
    // ─────────────────────────────────────────────────────────────────────────

    private void buildScoreCard(AnalyseCalculator calc) {
        int score = calc.getHealthScore();

        LinearLayout card = makeCard();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        // Gauge canvas
        GaugeView gauge = new GaugeView(activity, score);
        int gaugeSize = DS.dp(activity, 90);
        LinearLayout.LayoutParams gaugeLp = new LinearLayout.LayoutParams(gaugeSize, gaugeSize);
        gaugeLp.rightMargin = DS.dp(activity, 16);
        card.addView(gauge, gaugeLp);

        // Textes
        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, -2, 1f);
        card.addView(texts, textLp);

        TextView label = new TextView(activity);
        label.setText("Score de santé");
        label.setTextSize(12f);
        label.setTextColor(ThemeColors.subtext());
        texts.addView(label);

        TextView scoreVal = new TextView(activity);
        scoreVal.setText(score + " / 100");
        scoreVal.setTextSize(28f);
        scoreVal.setTypeface(null, Typeface.BOLD);
        scoreVal.setTextColor(scoreColor(score));
        texts.addView(scoreVal);

        TextView scoreLabel = new TextView(activity);
        scoreLabel.setText(scoreLabel(score));
        scoreLabel.setTextSize(13f);
        scoreLabel.setTextColor(ThemeColors.subtext());
        texts.addView(scoreLabel);

        addCard(card);
        gauge.animateIn();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Résumé du cycle
    // ─────────────────────────────────────────────────────────────────────────

    private void buildCycleSummary(AnalyseCalculator calc) {
        LinearLayout card = makeCard();
        card.setOrientation(LinearLayout.VERTICAL);

        TextView title = sectionTitle("Cycle en cours");
        card.addView(title);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = DS.dp(activity, 12);
        card.addView(row, rowLp);

        addSummaryColumn(row, "Revenus", calc.getCycleIncome(), Color.parseColor("#22C55E"), true);
        addSummaryColumn(row, "Dépenses", calc.getCycleExpenses(), Color.parseColor("#EF4444"), true);
        double net = calc.getCycleIncome() - calc.getCycleExpenses();
        addSummaryColumn(row, "Net", net, net >= 0 ? Color.parseColor("#22C55E") : Color.parseColor("#EF4444"), false);

        addCard(card);
    }

    private void addSummaryColumn(LinearLayout parent, String label, double value, int color, boolean addDivider) {
        LinearLayout col = new LinearLayout(activity);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        parent.addView(col, lp);

        TextView tvLabel = new TextView(activity);
        tvLabel.setText(label);
        tvLabel.setTextSize(11f);
        tvLabel.setTextColor(ThemeColors.subtext());
        tvLabel.setGravity(Gravity.CENTER);
        col.addView(tvLabel);

        TextView tvValue = new TextView(activity);
        tvValue.setText(Fmt.money(value));
        tvValue.setTextSize(17f);
        tvValue.setTypeface(null, Typeface.BOLD);
        tvValue.setTextColor(color);
        tvValue.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(-1, -2);
        valueLp.topMargin = DS.dp(activity, 4);
        col.addView(tvValue, valueLp);

        if (addDivider) {
            View div = new View(activity);
            div.setBackgroundColor(ThemeColors.border());
            LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(1, DS.dp(activity, 36));
            divLp.gravity = Gravity.CENTER_VERTICAL;
            parent.addView(div, divLp);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Graphique évolution 6 mois
    // ─────────────────────────────────────────────────────────────────────────

    private void buildEvolutionChart(AnalyseCalculator calc) {
        LinearLayout card = makeCard();
        card.setOrientation(LinearLayout.VERTICAL);

        card.addView(sectionTitle("Évolution sur 6 mois"));

        List<AnalyseCalculator.MonthData> months = calc.getLast6Months();
        if (months.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("Pas encore assez de données.");
            empty.setTextSize(13f);
            empty.setTextColor(ThemeColors.subtext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = DS.dp(activity, 12);
            card.addView(empty, lp);
        } else {
            LineChartView chart = new LineChartView(activity, months);
            LinearLayout.LayoutParams chartLp = new LinearLayout.LayoutParams(-1, DS.dp(activity, 160));
            chartLp.topMargin = DS.dp(activity, 16);
            card.addView(chart, chartLp);
        }

        addCard(card);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Répartition par catégorie
    // ─────────────────────────────────────────────────────────────────────────

    private void buildCategoryBreakdown(AnalyseCalculator calc) {
        Map<String, Double> cats = calc.getCycleExpensesByCategory();
        if (cats.isEmpty()) return;

        LinearLayout card = makeCard();
        card.setOrientation(LinearLayout.VERTICAL);
        card.addView(sectionTitle("Dépenses par catégorie"));

        double total = 0;
        for (double v : cats.values()) total += v;
        final double finalTotal = total;

        List<Map.Entry<String, Double>> sorted = new ArrayList<>(cats.entrySet());
        Collections.sort(sorted, (a, b) -> Double.compare(b.getValue(), a.getValue()));
        if (sorted.size() > 7) sorted = sorted.subList(0, 7);

        int[] colors = {
            Color.parseColor("#C0614A"), Color.parseColor("#E8956D"),
            Color.parseColor("#F59E0B"), Color.parseColor("#22C55E"),
            Color.parseColor("#3B82F6"), Color.parseColor("#8B5CF6"),
            Color.parseColor("#EC4899")
        };

        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Double> entry = sorted.get(i);
            double pct = finalTotal > 0 ? entry.getValue() / finalTotal : 0;
            int color = colors[i % colors.length];
            addCategoryBar(card, entry.getKey(), entry.getValue(), pct, color);
        }

        addCard(card);
    }

    private void addCategoryBar(LinearLayout parent, String name, double amount, double pct, int color) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = DS.dp(activity, 10);
        parent.addView(row, lp);

        LinearLayout labelRow = new LinearLayout(activity);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(labelRow);

        TextView tvName = new TextView(activity);
        tvName.setText(name);
        tvName.setTextSize(13f);
        tvName.setTextColor(ThemeColors.text());
        tvName.setTypeface(null, Typeface.BOLD);
        labelRow.addView(tvName, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView tvPct = new TextView(activity);
        tvPct.setText(Math.round(pct * 100) + "% · " + Fmt.money(amount));
        tvPct.setTextSize(12f);
        tvPct.setTextColor(ThemeColors.subtext());
        labelRow.addView(tvPct);

        // Background bar
        FrameLayout barBg = new FrameLayout(activity);
        GradientDrawable barBgBg = new GradientDrawable();
        barBgBg.setColor(ThemeColors.backgroundSecondary());
        barBgBg.setCornerRadius(DS.dp(activity, 4));
        barBg.setBackground(barBgBg);
        LinearLayout.LayoutParams barBgLp = new LinearLayout.LayoutParams(-1, DS.dp(activity, 8));
        barBgLp.topMargin = DS.dp(activity, 6);
        row.addView(barBg, barBgLp);

        // Fill bar (will animate)
        View fill = new View(activity);
        GradientDrawable fillBg = new GradientDrawable();
        fillBg.setColor(color);
        fillBg.setCornerRadius(DS.dp(activity, 4));
        fill.setBackground(fillBg);
        FrameLayout.LayoutParams fillLp = new FrameLayout.LayoutParams(0, -1);
        barBg.addView(fill, fillLp);

        // Animate bar
        barBg.post(() -> {
            int maxWidth = barBg.getWidth();
            if (maxWidth <= 0) return;
            int targetWidth = (int) (maxWidth * pct);
            ValueAnimator anim = ValueAnimator.ofInt(0, targetWidth);
            anim.setDuration(700);
            anim.setInterpolator(new DecelerateInterpolator());
            anim.addUpdateListener(va -> {
                ViewGroup.LayoutParams p = fill.getLayoutParams();
                p.width = (int) va.getAnimatedValue();
                fill.setLayoutParams(p);
            });
            anim.start();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Prévision fin de cycle
    // ─────────────────────────────────────────────────────────────────────────

    private void buildForecast(AnalyseCalculator calc) {
        AnalyseCalculator.Forecast forecast = calc.getForecast();

        LinearLayout card = makeCard();
        card.setOrientation(LinearLayout.VERTICAL);
        card.addView(sectionTitle("Prévision fin de cycle"));

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = DS.dp(activity, 12);
        card.addView(row, rowLp);

        // Icône
        TextView icon = new TextView(activity);
        icon.setText(forecast.isOverspend ? "⚠" : "✓");
        icon.setTextSize(28f);
        icon.setTextColor(forecast.isOverspend ? Color.parseColor("#F59E0B") : Color.parseColor("#22C55E"));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(-2, -2);
        iconLp.rightMargin = DS.dp(activity, 12);
        row.addView(icon, iconLp);

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView tvMain = new TextView(activity);
        tvMain.setText(Fmt.money(forecast.projectedExpenses) + " projetées");
        tvMain.setTextSize(20f);
        tvMain.setTypeface(null, Typeface.BOLD);
        tvMain.setTextColor(forecast.isOverspend ? Color.parseColor("#EF4444") : ThemeColors.text());
        texts.addView(tvMain);

        TextView tvSub = new TextView(activity);
        tvSub.setText(forecast.message);
        tvSub.setTextSize(13f);
        tvSub.setTextColor(ThemeColors.subtext());
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = DS.dp(activity, 3);
        texts.addView(tvSub, subLp);

        // Progress bar
        if (forecast.progressPct > 0) {
            FrameLayout barBg = new FrameLayout(activity);
            GradientDrawable bgD = new GradientDrawable();
            bgD.setColor(ThemeColors.backgroundSecondary());
            bgD.setCornerRadius(DS.dp(activity, 6));
            barBg.setBackground(bgD);
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, DS.dp(activity, 10));
            barLp.topMargin = DS.dp(activity, 14);
            card.addView(barBg, barLp);

            View fill = new View(activity);
            int fillColor = forecast.progressPct >= 90
                    ? Color.parseColor("#EF4444")
                    : forecast.progressPct >= 70
                    ? Color.parseColor("#F59E0B")
                    : Color.parseColor("#22C55E");
            GradientDrawable fd = new GradientDrawable();
            fd.setColor(fillColor);
            fd.setCornerRadius(DS.dp(activity, 6));
            fill.setBackground(fd);
            barBg.addView(fill, new FrameLayout.LayoutParams(0, -1));

            barBg.post(() -> {
                int w = barBg.getWidth();
                if (w <= 0) return;
                int target = (int) (w * Math.min(1.0, forecast.progressPct / 100.0));
                ValueAnimator a = ValueAnimator.ofInt(0, target);
                a.setDuration(900);
                a.setInterpolator(new DecelerateInterpolator());
                a.addUpdateListener(va -> {
                    ViewGroup.LayoutParams lp2 = fill.getLayoutParams();
                    lp2.width = (int) va.getAnimatedValue();
                    fill.setLayoutParams(lp2);
                });
                a.start();
            });

            TextView tvPct = new TextView(activity);
            tvPct.setText((int) Math.min(100, forecast.progressPct) + "% du budget cycle consommé");
            tvPct.setTextSize(11f);
            tvPct.setTextColor(ThemeColors.subtext());
            LinearLayout.LayoutParams pctLp = new LinearLayout.LayoutParams(-1, -2);
            pctLp.topMargin = DS.dp(activity, 4);
            card.addView(tvPct, pctLp);
        }

        addCard(card);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Top 5 commerçants
    // ─────────────────────────────────────────────────────────────────────────

    private void buildTopMerchants(AnalyseCalculator calc) {
        List<Map.Entry<String, Double>> top = calc.getTopMerchants(5);
        if (top.isEmpty()) return;

        LinearLayout card = makeCard();
        card.setOrientation(LinearLayout.VERTICAL);
        card.addView(sectionTitle("Top commerçants du cycle"));

        for (int i = 0; i < top.size(); i++) {
            Map.Entry<String, Double> entry = top.get(i);
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, DS.dp(activity, 44));
            if (i > 0) {
                rowLp.topMargin = 0;
                // Add divider
                View div = new View(activity);
                div.setBackgroundColor(ThemeColors.border());
                card.addView(div, new LinearLayout.LayoutParams(-1, 1));
            }
            card.addView(row, rowLp);

            TextView rank = new TextView(activity);
            rank.setText(String.valueOf(i + 1));
            rank.setTextSize(12f);
            rank.setTextColor(ThemeColors.subtext());
            rank.setTypeface(null, Typeface.BOLD);
            rank.setMinWidth(DS.dp(activity, 28));
            rank.setGravity(Gravity.CENTER);
            row.addView(rank);

            // Colored dot
            View dot = new View(activity);
            int[] palette = {
                Color.parseColor("#C0614A"), Color.parseColor("#E8956D"),
                Color.parseColor("#F59E0B"), Color.parseColor("#22C55E"),
                Color.parseColor("#3B82F6")
            };
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(palette[i % palette.length]);
            dot.setBackground(dotBg);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(DS.dp(activity, 8), DS.dp(activity, 8));
            dotLp.gravity = Gravity.CENTER_VERTICAL;
            dotLp.rightMargin = DS.dp(activity, 10);
            row.addView(dot, dotLp);

            TextView name = new TextView(activity);
            name.setText(prettyName(entry.getKey()));
            name.setTextSize(14f);
            name.setTextColor(ThemeColors.text());
            name.setSingleLine(true);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView amount = new TextView(activity);
            amount.setText(Fmt.money(entry.getValue()));
            amount.setTextSize(14f);
            amount.setTypeface(null, Typeface.BOLD);
            amount.setTextColor(Color.parseColor("#EF4444"));
            row.addView(amount);
        }

        addCard(card);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Insights automatiques
    // ─────────────────────────────────────────────────────────────────────────

    private void buildInsights(List<String[]> txs) {
        List<FinancialInsightManager.Insight> insights = FinancialInsightManager.analyze(txs);
        if (insights.isEmpty()) return;

        LinearLayout card = makeCard();
        card.setOrientation(LinearLayout.VERTICAL);
        card.addView(sectionTitle("Alertes intelligentes"));

        for (FinancialInsightManager.Insight insight : insights) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.topMargin = DS.dp(activity, 12);
            card.addView(row, rowLp);

            // Severity indicator
            int severityColor = insight.severity == FinancialInsightManager.SEVERITY_RISK
                    ? Color.parseColor("#EF4444")
                    : insight.severity == FinancialInsightManager.SEVERITY_WARNING
                    ? Color.parseColor("#F59E0B")
                    : Color.parseColor("#3B82F6");

            View bar = new View(activity);
            GradientDrawable barBg = new GradientDrawable();
            barBg.setColor(severityColor);
            barBg.setCornerRadius(DS.dp(activity, 3));
            bar.setBackground(barBg);
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(DS.dp(activity, 4), -1);
            barLp.rightMargin = DS.dp(activity, 12);
            barLp.bottomMargin = DS.dp(activity, 2);
            row.addView(bar, barLp);

            LinearLayout texts = new LinearLayout(activity);
            texts.setOrientation(LinearLayout.VERTICAL);
            row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView tvTitle = new TextView(activity);
            tvTitle.setText(insight.title);
            tvTitle.setTextSize(14f);
            tvTitle.setTypeface(null, Typeface.BOLD);
            tvTitle.setTextColor(ThemeColors.text());
            texts.addView(tvTitle);

            if (insight.subtitle != null && !insight.subtitle.isEmpty()) {
                TextView tvSub = new TextView(activity);
                tvSub.setText(insight.subtitle);
                tvSub.setTextSize(12f);
                tvSub.setTextColor(ThemeColors.subtext());
                LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
                subLp.topMargin = DS.dp(activity, 2);
                texts.addView(tvSub, subLp);
            }
        }

        addCard(card);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers UI
    // ─────────────────────────────────────────────────────────────────────────

    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = DS.dp(activity, 18);
        card.setPadding(pad, pad, pad, pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ThemeColors.card());
        bg.setCornerRadius(DS.dp(activity, 18));
        card.setBackground(bg);
        return card;
    }

    private void addCard(LinearLayout card) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = DS.dp(activity, 12);
        contentContainer.addView(card, lp);
    }

    private TextView sectionTitle(String text) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextSize(15f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(ThemeColors.text());
        return tv;
    }

    private int scoreColor(int score) {
        if (score >= 75) return Color.parseColor("#22C55E");
        if (score >= 50) return Color.parseColor("#F59E0B");
        return Color.parseColor("#EF4444");
    }

    private String scoreLabel(int score) {
        if (score >= 80) return "Excellente santé financière";
        if (score >= 65) return "Bonne gestion";
        if (score >= 45) return "Attention aux dépenses";
        return "Situation tendue";
    }

    private String prettyName(String key) {
        if (key == null || key.isEmpty()) return "Autre";
        String[] words = key.split(" ");
        StringBuilder b = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            if (w.length() <= 3) b.append(w.toUpperCase(Locale.ROOT));
            else b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
        }
        return b.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parse transactions (même format que HomeView)
    // ─────────────────────────────────────────────────────────────────────────

    private List<String[]> parseTransactions(String json) {
        List<String[]> list = new ArrayList<>();
        if (json == null || json.isEmpty()) return list;
        String[] parts = json.split("\"fields\":");
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];
            String label = extract(p, "label", "stringValue");
            String amount = extractNum(p, "amount");
            String type = extract(p, "type", "stringValue");
            String category = extract(p, "category", "stringValue");
            String date = extractDate(p);
            String shareSplit = extractBool(p, "isShareSplit");
            String reimbursement = extractBool(p, "isReimbursement");
            String userId = extract(p, "userId", "stringValue");
            if (!label.isEmpty()) {
                list.add(new String[]{ label, amount, type, category, date,
                        shareSplit, reimbursement, userId });
            }
        }
        return list;
    }

    private String extract(String json, String field, String valueKey) {
        int idx = json.indexOf("\"" + field + "\"");
        if (idx < 0) return "";
        String sub = json.substring(idx, Math.min(json.length(), idx + 300));
        int vi = sub.indexOf("\"" + valueKey + "\"");
        if (vi < 0) return "";
        int colon = sub.indexOf(":", vi);
        if (colon < 0) return "";
        int q1 = sub.indexOf("\"", colon + 1);
        if (q1 < 0) return "";
        int q2 = sub.indexOf("\"", q1 + 1);
        if (q2 < 0) return "";
        return sub.substring(q1 + 1, q2);
    }

    private String extractNum(String json, String field) {
        int idx = json.indexOf("\"" + field + "\"");
        if (idx < 0) return "0";
        String sub = json.substring(idx, Math.min(json.length(), idx + 200));
        for (String key : new String[]{"\"doubleValue\":", "\"integerValue\":"}) {
            int ki = sub.indexOf(key);
            if (ki >= 0) {
                int start = ki + key.length();
                while (start < sub.length() && (sub.charAt(start) == ' ' || sub.charAt(start) == '"')) start++;
                int end = start;
                while (end < sub.length() && (Character.isDigit(sub.charAt(end)) || sub.charAt(end) == '.' || sub.charAt(end) == '-')) end++;
                if (end > start) return sub.substring(start, end);
            }
        }
        return "0";
    }

    private String extractDate(String json) {
        int idx = json.indexOf("\"date\"");
        if (idx < 0) return "0";
        String sub = json.substring(idx, Math.min(json.length(), idx + 300));
        // timestampValue
        int tsi = sub.indexOf("\"timestampValue\"");
        if (tsi >= 0) {
            int q1 = sub.indexOf("\"", tsi + 16);
            int q2 = q1 >= 0 ? sub.indexOf("\"", q1 + 1) : -1;
            if (q1 >= 0 && q2 > q1) {
                String ts = sub.substring(q1 + 1, q2);
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                    sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                    return String.valueOf(sdf.parse(ts).getTime());
                } catch (Exception ignored) {}
                try {
                    java.text.SimpleDateFormat sdf2 = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                    sdf2.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                    return String.valueOf(sdf2.parse(ts).getTime());
                } catch (Exception ignored) {}
            }
        }
        // integerValue fallback
        return extractNum(json.substring(idx, Math.min(json.length(), idx + 300)), "date");
    }

    private String extractBool(String json, String field) {
        int idx = json.indexOf("\"" + field + "\"");
        if (idx < 0) return "false";
        String sub = json.substring(idx, Math.min(json.length(), idx + 80));
        return (sub.contains("booleanValue\":true") || sub.contains("booleanValue\": true")) ? "true" : "false";
    }

    private String readStream(InputStream is) {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    public void onDestroy() {
        isActive = false;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GaugeView — jauge semi-circulaire animée
    // ═════════════════════════════════════════════════════════════════════════

    private static class GaugeView extends View {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int targetScore;
        private float currentAngle = 0f;
        private final RectF oval = new RectF();

        GaugeView(Context ctx, int score) {
            super(ctx);
            this.targetScore = score;

            bgPaint.setStyle(Paint.Style.STROKE);
            bgPaint.setStrokeWidth(DS.dp(ctx, 9));
            bgPaint.setColor(Color.parseColor("#E8DDD6"));
            bgPaint.setStrokeCap(Paint.Cap.ROUND);

            fillPaint.setStyle(Paint.Style.STROKE);
            fillPaint.setStrokeWidth(DS.dp(ctx, 9));
            fillPaint.setStrokeCap(Paint.Cap.ROUND);
            if (score >= 75) fillPaint.setColor(Color.parseColor("#22C55E"));
            else if (score >= 50) fillPaint.setColor(Color.parseColor("#F59E0B"));
            else fillPaint.setColor(Color.parseColor("#EF4444"));

            textPaint.setTextSize(DS.dp(ctx, 16));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(score >= 75
                    ? Color.parseColor("#22C55E")
                    : score >= 50 ? Color.parseColor("#F59E0B") : Color.parseColor("#EF4444"));
        }

        void animateIn() {
            float targetAngle = (targetScore / 100f) * 180f;
            ValueAnimator anim = ValueAnimator.ofFloat(0f, targetAngle);
            anim.setDuration(1000);
            anim.setInterpolator(new DecelerateInterpolator());
            anim.addUpdateListener(va -> {
                currentAngle = (float) va.getAnimatedValue();
                invalidate();
            });
            anim.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            int pad = (int) bgPaint.getStrokeWidth() / 2 + 4;
            oval.set(pad, pad, w - pad, h - pad);

            // Background arc (180° semi-circle, bottom half hidden)
            canvas.drawArc(oval, 180, 180, false, bgPaint);
            // Fill arc
            if (currentAngle > 0) canvas.drawArc(oval, 180, currentAngle, false, fillPaint);
            // Score text
            canvas.drawText(String.valueOf(targetScore), w / 2f, h - pad * 2, textPaint);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // LineChartView — courbe d'évolution mensuelle
    // ═════════════════════════════════════════════════════════════════════════

    private static class LineChartView extends View {
        private final List<AnalyseCalculator.MonthData> months;
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint expensePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress = 0f;

        LineChartView(Context ctx, List<AnalyseCalculator.MonthData> months) {
            super(ctx);
            this.months = months;

            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(DS.dp(ctx, 2.5f));
            linePaint.setColor(Color.parseColor("#22C55E"));
            linePaint.setStrokeJoin(Paint.Join.ROUND);

            expensePaint.setStyle(Paint.Style.STROKE);
            expensePaint.setStrokeWidth(DS.dp(ctx, 2f));
            expensePaint.setColor(Color.parseColor("#EF4444"));
            expensePaint.setStrokeJoin(Paint.Join.ROUND);
            expensePaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{DS.dp(ctx, 6), DS.dp(ctx, 3)}, 0));

            dotPaint.setStyle(Paint.Style.FILL);
            dotPaint.setColor(Color.parseColor("#22C55E"));

            labelPaint.setTextSize(DS.dp(ctx, 9));
            labelPaint.setTextAlign(Paint.Align.CENTER);
            labelPaint.setColor(Color.parseColor("#8A7A70"));

            fillPaint.setStyle(Paint.Style.FILL);

            post(() -> {
                ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
                anim.setDuration(900);
                anim.setInterpolator(new DecelerateInterpolator());
                anim.addUpdateListener(va -> { progress = (float) va.getAnimatedValue(); invalidate(); });
                anim.start();
            });
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (months.isEmpty()) return;
            int w = getWidth();
            int h = getHeight();
            int padLeft = DS.dp(getContext(), 10);
            int padRight = DS.dp(getContext(), 10);
            int padTop = DS.dp(getContext(), 12);
            int padBottom = DS.dp(getContext(), 20);

            int chartW = w - padLeft - padRight;
            int chartH = h - padTop - padBottom;

            double maxVal = 1;
            for (AnalyseCalculator.MonthData m : months) {
                maxVal = Math.max(maxVal, Math.max(m.income, m.expenses));
            }

            int n = months.size();
            float step = n > 1 ? (float) chartW / (n - 1) : 0;

            // Income line
            Path incomePath = new Path();
            Path fillPath = new Path();
            boolean first = true;
            for (int i = 0; i < n; i++) {
                float x = padLeft + i * step;
                float y = padTop + chartH - (float) (months.get(i).income / maxVal) * chartH;
                if (first) { incomePath.moveTo(x, y); fillPath.moveTo(x, padTop + chartH); fillPath.lineTo(x, y); first = false; }
                else { incomePath.lineTo(x, y); fillPath.lineTo(x, y); }
            }
            // Fill under income
            fillPath.lineTo(padLeft + (n - 1) * step, padTop + chartH);
            fillPath.close();
            int[] gradColors = { Color.parseColor("#3022C55E"), Color.parseColor("#0022C55E") };
            LinearGradient grad = new LinearGradient(0, padTop, 0, padTop + chartH, gradColors, null, Shader.TileMode.CLAMP);
            fillPaint.setShader(grad);
            canvas.drawPath(fillPath, fillPaint);

            // Clip income line to progress
            canvas.save();
            canvas.clipRect(0, 0, padLeft + progress * ((n - 1) * step), h);
            canvas.drawPath(incomePath, linePaint);
            canvas.restore();

            // Expense line
            Path expPath = new Path();
            first = true;
            for (int i = 0; i < n; i++) {
                float x = padLeft + i * step;
                float y = padTop + chartH - (float) (months.get(i).expenses / maxVal) * chartH;
                if (first) { expPath.moveTo(x, y); first = false; }
                else expPath.lineTo(x, y);
            }
            canvas.save();
            canvas.clipRect(0, 0, padLeft + progress * ((n - 1) * step), h);
            canvas.drawPath(expPath, expensePaint);
            canvas.restore();

            // Dots + labels
            for (int i = 0; i < n; i++) {
                float x = padLeft + i * step;
                float yIn = padTop + chartH - (float) (months.get(i).income / maxVal) * chartH;
                if (i / (float)(n - 1) <= progress) {
                    dotPaint.setColor(Color.parseColor("#22C55E"));
                    canvas.drawCircle(x, yIn, DS.dp(getContext(), 3.5f), dotPaint);
                }
                canvas.drawText(months.get(i).label, x, h - DS.dp(getContext(), 2), labelPaint);
            }
        }
    }
}
