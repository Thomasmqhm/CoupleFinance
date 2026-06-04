package com.couplefinance.ui.credits;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.Fmt;
import com.couplefinance.core.ui.UiFactory;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  CreditsInsights — Rendu des insights et chart projection   ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Génère les cards d'insights financiers et le graphique     ║
 * ║  de projection des mensualités par année (Canvas custom).   ║
 * ║                                                             ║
 * ║  Appelé par : CreditsView.renderInsights()                  ║
 * ║  Appelle    : CreditsCalculator pour les calculs            ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
public final class CreditsInsights {

    private CreditsInsights() {}

    // ─────────────────────────────────────────────────────────────
    // Point d'entrée : remplit le container d'insights
    // ─────────────────────────────────────────────────────────────

    /**
     * Vide et remplit insightsContainer avec tous les insights
     * calculés depuis les données chargées.
     *
     * @param insightsContainer LinearLayout cible (colonne droite de la page)
     * @param data              données Firestore chargées
     */
    public static void render(Activity activity, LinearLayout insightsContainer,
                               CreditsModels.CreditsData data) {
        insightsContainer.removeAllViews();

        List<CreditsModels.Credit> credits = data.credits;

        // Calculs agrégés via CreditsCalculator
        double totalMonthly   = CreditsCalculator.totalMonthly(credits);
        double totalRemaining = CreditsCalculator.totalRemaining(credits);
        double debtRatio      = CreditsCalculator.debtRatio(
            totalMonthly, data.totalFixedCharges, data.totalRevenue);

        if (credits.isEmpty()) {
            addInsightCard(activity, insightsContainer, "🏦",
                "Aucun crédit",
                "Ajoutez un crédit pour suivre les mensualités et le capital restant du foyer.",
                "#4A6B9A");
            if (data.totalRevenue > 0) {
                addInsightCard(activity, insightsContainer, "📊",
                    "Revenus détectés",
                    "Vos revenus (" + Fmt.money(data.totalRevenue) + "/mois) sont enregistrés. "
                        + "Ajoutez un crédit pour calculer votre taux d'endettement.",
                    "#10B981");
            }
            return;
        }

        // Insight 1 : Taux d'endettement réel
        if (data.totalRevenue > 0) {
            String debtColor = CreditsCalculator.isOverLegalLimit(debtRatio) ? "#DC2626"
                : CreditsCalculator.isInWatchZone(debtRatio) ? "#D97706" : "#059669";
            String debtIcon  = CreditsCalculator.isOverLegalLimit(debtRatio) ? "🔴"
                : CreditsCalculator.isInWatchZone(debtRatio) ? "🟡" : "🟢";
            addInsightCard(activity, insightsContainer, debtIcon,
                "Taux d'endettement réel",
                String.format(Locale.FRANCE,
                    "%.1f%% de vos revenus (mensualités %s + charges fixes %s sur %s/mois).",
                    debtRatio,
                    Fmt.money(totalMonthly),
                    Fmt.money(data.totalFixedCharges),
                    Fmt.money(data.totalRevenue)),
                debtColor);
        }

        // Insight 2 : Prochain à solder
        CreditsModels.Credit soonest = CreditsCalculator.soonestToFinish(credits);
        if (soonest != null) {
            int ml = CreditsCalculator.monthsLeft(soonest);
            addInsightCard(activity, insightsContainer, "📉",
                soonest.name + " bientôt soldé",
                "Dans " + ml + " mois, " + Fmt.money(soonest.monthlyPayment)
                    + "/mois libérés. Orientez-les vers l'épargne !",
                "#10B981");
        }

        // Insight 3 : Intérêts restants estimés
        double totalInterests = CreditsCalculator.totalEstimatedInterests(credits);
        if (totalInterests > 0) {
            addInsightCard(activity, insightsContainer, "📅",
                "Intérêts restants",
                "~" + Fmt.money(totalInterests) + " d'intérêts estimés. "
                    + "Renégocier le taux pourrait vous économiser des milliers €.",
                "#F59E0B");
        }

        // Insight 4 : Remboursement anticipé
        CreditsModels.Credit longest = CreditsCalculator.longestRemaining(credits);
        if (longest != null && CreditsCalculator.monthsLeft(longest) > 12) {
            double interestSaved = longest.monthlyPayment * 3; // estimation simple
            addInsightCard(activity, insightsContainer, "⏱️",
                "Remboursement anticipé",
                "5 000 € sur " + longest.name + " = ~" + Fmt.money(interestSaved)
                    + " d'intérêts économisés. Intéressant si vous avez des liquidités.",
                "#8B5CF6");
        }

        // Insight 5 : Libération en vue
        if (soonest != null) {
            int mlSoonest = CreditsCalculator.monthsLeft(soonest);
            long futureMs = System.currentTimeMillis()
                + (long) mlSoonest * 30L * 24L * 3600L * 1000L;
            double newMonthly = totalMonthly - soonest.monthlyPayment;
            addInsightCard(activity, insightsContainer, "🎉",
                "Libération en vue",
                "En " + Fmt.monthLabel(futureMs) + ", votre charge passe de "
                    + Fmt.money(totalMonthly) + " à " + Fmt.money(newMonthly)
                    + " (-" + Fmt.money(soonest.monthlyPayment) + ").",
                "#C0614A");
        }

        // Graphique de projection
        renderProjectionChart(activity, insightsContainer, credits);
    }

    // ─────────────────────────────────────────────────────────────
    // Graphique de projection (Canvas custom)
    // ─────────────────────────────────────────────────────────────

    /**
     * Ajoute le graphique de projection des mensualités par année
     * au bas de insightsContainer.
     */
    private static void renderProjectionChart(Activity activity, LinearLayout insightsContainer,
                                               List<CreditsModels.Credit> credits) {
        TextView header = UiFactory.sectionTitle(activity, "Projection des mensualités");
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.topMargin    = DS.dp(activity, DS.GAP_SM);
        hlp.bottomMargin = DS.dp(activity, DS.GAP_SM);
        header.setLayoutParams(hlp);
        insightsContainer.addView(header);

        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        int maxYears    = CreditsCalculator.maxYearsNeeded(credits, 8);

        final double[] yearlyMonthly = CreditsCalculator.projectionByYear(credits, maxYears);
        final String[] yearLabels    = new String[maxYears];
        double maxVal = 1;
        for (int y = 0; y < maxYears; y++) {
            yearLabels[y] = String.valueOf(currentYear + y);
            if (yearlyMonthly[y] > maxVal) maxVal = yearlyMonthly[y];
        }

        final double maxValFinal = maxVal;
        final int    years       = maxYears;

        View chartView = new View(activity) {
            @Override protected void onDraw(Canvas canvas) {
                drawChart(canvas, getWidth(), getHeight(),
                    yearlyMonthly, yearLabels, years, maxValFinal, activity);
            }
        };

        chartView.setBackground(UiFactory.bgBordered(DS.CARD, DS.BORDER, DS.R_MD, activity));

        int chartH = Math.max(DS.dp(activity, 30) * maxYears + DS.dp(activity, 40), DS.dp(activity, 200));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, chartH);
        clp.bottomMargin = DS.dp(activity, DS.GAP_SM);
        chartView.setLayoutParams(clp);
        insightsContainer.addView(chartView);
    }

    /**
     * Dessine les barres horizontales de projection sur le Canvas.
     * Appelé par onDraw() de la View anonyme dans renderProjectionChart().
     */
    private static void drawChart(Canvas canvas, int w, int h,
                                   double[] vals, String[] labels, int n, double maxVal,
                                   Activity activity) {
        if (w <= 0 || h <= 0 || n == 0) return;

        int padL   = DS.dp(activity, 50);
        int padR   = DS.dp(activity, 110);
        int padV   = DS.dp(activity, 16);
        int barH   = DS.dp(activity, 18);
        int slotH  = (h - padV * 2) / n;
        int barMaxW = w - padL - padR;

        Paint barFg   = paint(DS.TERRA,    true);
        Paint barBg2  = paint(DS.BLUE,     true);
        Paint trackP  = paint(0xFFEEF4FF, true);
        Paint lblP    = textPaint(DS.dp(activity, 10), DS.MUTED, false, Paint.Align.RIGHT);
        Paint valP    = textPaint(DS.dp(activity, 10), DS.DARK,  true,  Paint.Align.LEFT);

        for (int i = 0; i < n; i++) {
            float cy  = padV + i * slotH + slotH / 2f;
            float top = cy - barH / 2f;
            float bot = cy + barH / 2f;

            canvas.drawText(labels[i], padL - DS.dp(activity, 6), cy + DS.dp(activity, 4), lblP);
            canvas.drawRoundRect(new RectF(padL, top, padL + barMaxW, bot),
                DS.dp(activity, 6), DS.dp(activity, 6), trackP);

            if (vals[i] > 0) {
                float fillW  = (float) (barMaxW * (vals[i] / maxVal));
                float splitW = n > 1 ? fillW * 0.6f : fillW;
                canvas.drawRoundRect(new RectF(padL, top, padL + fillW, bot),
                    DS.dp(activity, 6), DS.dp(activity, 6), barBg2);
                canvas.drawRoundRect(new RectF(padL, top, padL + splitW, bot),
                    DS.dp(activity, 6), DS.dp(activity, 6), barFg);
                canvas.drawText(Fmt.money(vals[i]) + "/mois",
                    padL + fillW + DS.dp(activity, 8), cy + DS.dp(activity, 4), valP);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Composant insight card
    // ─────────────────────────────────────────────────────────────

    private static void addInsightCard(Activity activity, LinearLayout container,
                                        String icon, String title, String body, String accentHex) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(DS.dp(activity, 16), DS.dp(activity, 14),
                        DS.dp(activity, 16), DS.dp(activity, 14));
        card.setBackground(UiFactory.bgBordered(DS.CARD, DS.BORDER, DS.R_MD, activity));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = DS.dp(activity, DS.GAP_SM);
        card.setLayoutParams(lp);

        // Icône
        TextView tvIcon = new TextView(activity);
        tvIcon.setText(icon);
        tvIcon.setTextSize(16);
        try {
            int base = Color.parseColor(accentHex);
            int r = (int)(Color.red(base) * 0.12f + 255 * 0.88f);
            int g = (int)(Color.green(base) * 0.12f + 255 * 0.88f);
            int b = (int)(Color.blue(base) * 0.12f + 255 * 0.88f);
            tvIcon.setBackground(UiFactory.bg(Color.rgb(r, g, b),
                DS.dp(activity, DS.R_SM), activity));
        } catch (Exception ignored) {}
        LinearLayout.LayoutParams iLP = new LinearLayout.LayoutParams(
            DS.dp(activity, 40), DS.dp(activity, 40));
        iLP.rightMargin = DS.dp(activity, DS.GAP_SM);
        tvIcon.setLayoutParams(iLP);
        tvIcon.setGravity(android.view.Gravity.CENTER);

        // Textes
        LinearLayout col = new LinearLayout(activity);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(title);
        tvTitle.setTextColor(DS.DARK);
        tvTitle.setTextSize(DS.TEXT_SM);
        tvTitle.setTypeface(null, Typeface.BOLD);

        TextView tvBody = new TextView(activity);
        tvBody.setText(body);
        tvBody.setTextColor(DS.MUTED);
        tvBody.setTextSize(12);

        col.addView(tvTitle);
        col.addView(tvBody);
        card.addView(tvIcon);
        card.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        container.addView(card);
    }

    // ─────────────────────────────────────────────────────────────
    // Paint helpers
    // ─────────────────────────────────────────────────────────────

    private static Paint paint(int color, boolean antiAlias) {
        Paint p = new Paint(antiAlias ? Paint.ANTI_ALIAS_FLAG : 0);
        p.setColor(color);
        return p;
    }

    private static Paint textPaint(int textSizePx, int color, boolean bold, Paint.Align align) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(textSizePx);
        p.setColor(color);
        p.setTextAlign(align);
        if (bold) p.setTypeface(Typeface.DEFAULT_BOLD);
        return p;
    }
}
