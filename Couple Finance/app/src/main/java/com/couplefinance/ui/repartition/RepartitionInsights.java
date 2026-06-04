package com.couplefinance.ui.repartition;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.widget.*;

import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.Fmt;
import com.couplefinance.core.ui.UiFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  RepartitionInsights — Graphique historique + insights      ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Génère :                                                   ║
 * ║    • Les barres catégories (renderCategories)               ║
 * ║    • Le graphique Canvas "Historique des écarts"            ║
 * ║    • Les cards d'insights                                   ║
 * ║                                                             ║
 * ║  Appelé par : RepartitionView                               ║
 * ║  Appelle    : RepartitionCalculator pour les ratios         ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
public final class RepartitionInsights {

    private RepartitionInsights() {}

    // ─────────────────────────────────────────────────────────────
    // Barres de catégories
    // ─────────────────────────────────────────────────────────────

    /**
     * Remplit le container "Par catégorie" avec les barres de progression.
     */
    public static void renderCategories(Activity activity, LinearLayout container,
                                         Map<String, Double> categoryTotals, double total) {
        container.removeAllViews();

        if (categoryTotals.isEmpty()) {
            TextView tv = UiFactory.bodyMuted(activity, "Aucune dépense ce mois");
            container.addView(tv);
            return;
        }

        LinearLayout card = UiFactory.card(activity);
        container.addView(card, new LinearLayout.LayoutParams(-1, -2));

        for (Map.Entry<String, Double> e : categoryTotals.entrySet()) {
            String cat = e.getKey();
            double amt = e.getValue();
            int    pct = total > 0 ? (int) Math.round(amt / total * 100) : 0;

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.bottomMargin = DS.dp(activity, DS.GAP_SM);
            row.setLayoutParams(rp);

            // Label + montant + %
            LinearLayout labelRow = new LinearLayout(activity);
            labelRow.setOrientation(LinearLayout.HORIZONTAL);
            labelRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lrp = new LinearLayout.LayoutParams(-1, -2);
            lrp.bottomMargin = DS.dp(activity, 4);
            labelRow.setLayoutParams(lrp);

            // Icône catégorie
            TextView tvIcon = new TextView(activity);
            tvIcon.setText(RepartitionModels.iconForCategory(cat));
            tvIcon.setTextSize(14);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-2, -2);
            ip.rightMargin = DS.dp(activity, DS.GAP_SM);
            tvIcon.setLayoutParams(ip);
            labelRow.addView(tvIcon);

            TextView tvCat = UiFactory.body(activity, cat);
            tvCat.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            labelRow.addView(tvCat);

            TextView tvAmt = new TextView(activity);
            tvAmt.setText(Fmt.money(amt) + " (" + pct + "%)");
            tvAmt.setTextColor(DS.DARK);
            tvAmt.setTextSize(DS.TEXT_SM);
            tvAmt.setTypeface(null, Typeface.BOLD);
            labelRow.addView(tvAmt);
            row.addView(labelRow);

            // Barre de progression
            FrameLayout barWrap = new FrameLayout(activity);
            barWrap.setLayoutParams(new LinearLayout.LayoutParams(-1, DS.dp(activity, 6)));

            View track = new View(activity);
            track.setBackground(UiFactory.bg(DS.BORDER_LIGHT, DS.R_XS, activity));
            track.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));

            View fill = new View(activity);
            fill.setBackground(UiFactory.bg(DS.TERRA, DS.R_XS, activity));
            fill.setLayoutParams(new FrameLayout.LayoutParams(0, -1));

            barWrap.addView(track);
            barWrap.addView(fill);

            final int pctFinal = pct;
            barWrap.post(() -> {
                int w = barWrap.getWidth();
                if (w > 0)
                    fill.setLayoutParams(new FrameLayout.LayoutParams((int)(w * pctFinal / 100f), -1));
            });
            row.addView(barWrap);
            card.addView(row);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Graphique historique des écarts (Canvas)
    // ─────────────────────────────────────────────────────────────

    /**
     * Crée et retourne la View du graphique "Historique des écarts".
     * Barres côte-à-côte : membre0 (terracotta) et membre1 (bleu).
     */
    public static View buildHistoryChart(Activity activity,
                                          RepartitionModels.MonthHistory history,
                                          List<String> members) {
        final RepartitionModels.MonthHistory h = history;
        final List<String> m = members;

        View chartView = new View(activity) {
            @Override protected void onDraw(Canvas canvas) {
                drawHistoryChart(canvas, getWidth(), getHeight(), h, m, activity);
            }
        };

        chartView.setBackground(UiFactory.bgBordered(DS.CARD, DS.BORDER, DS.R_MD, activity));
        return chartView;
    }

    /**
     * Dessine les barres de l'historique sur le Canvas.
     */
    private static void drawHistoryChart(Canvas canvas, int w, int h,
                                          RepartitionModels.MonthHistory history,
                                          List<String> members,
                                          Activity activity) {
        if (w <= 0 || h <= 0) return;

        int pH = DS.dp(activity, 48), pV = DS.dp(activity, 24), n = 4;
        int totalW = w - pH * 2;
        int barW   = (int)(totalW / n * 0.45f);
        int gap    = totalW / n;

        double maxVal = 1;
        for (int i = 0; i < n; i++)
            maxVal = Math.max(maxVal, Math.max(history.ecarts[i][0], history.ecarts[i][1]));
        int cH = h - pV * 2 - DS.dp(activity, 18);

        Paint p0  = buildPaint(DS.TERRA);
        Paint p1  = buildPaint(DS.BLUE);
        Paint lbl = buildTextPaint(DS.dp(activity, 9), DS.DARK, true,  Paint.Align.CENTER);
        Paint mon = buildTextPaint(DS.dp(activity, 9), DS.MUTED, false, Paint.Align.CENTER);

        for (int i = 0; i < n; i++) {
            int cx = pH + gap * i + gap / 2;

            // Barre membre 0 (gauche)
            double v0 = history.ecarts[i][0];
            if (v0 > 0) {
                int bH = (int)(cH * (v0 / maxVal));
                RectF r = new RectF(cx - barW, pV + cH - bH, cx, pV + cH);
                canvas.drawRoundRect(r, DS.dp(activity, 4), DS.dp(activity, 4), p0);
                canvas.drawText(fmtShort(v0), cx - barW / 2, r.top - DS.dp(activity, 3), lbl);
            }

            // Barre membre 1 (droite)
            double v1 = history.ecarts[i][1];
            if (v1 > 0) {
                int bH = (int)(cH * (v1 / maxVal));
                int off = DS.dp(activity, 2);
                RectF r = new RectF(cx + off, pV + cH - bH, cx + barW + off, pV + cH);
                canvas.drawRoundRect(r, DS.dp(activity, 4), DS.dp(activity, 4), p1);
                canvas.drawText(fmtShort(v1), cx + barW / 2 + off, r.top - DS.dp(activity, 3), lbl);
            }

            // Mois
            if (history.labels != null && i < history.labels.length)
                canvas.drawText(history.labels[i], cx, h - DS.dp(activity, 4), mon);
        }

        // Légende
        if (members.size() >= 2) {
            Paint legP = buildTextPaint(DS.dp(activity, 9), DS.MUTED, false, Paint.Align.LEFT);
            float ly = h - DS.dp(activity, 4);
            p0.setAlpha(200);
            canvas.drawCircle(DS.dp(activity, 6), ly - DS.dp(activity, 3), DS.dp(activity, 4), p0);
            canvas.drawText(members.get(0) + " a trop payé",
                DS.dp(activity, 14), ly, legP);
            p1.setAlpha(200);
            canvas.drawCircle(w / 2f + DS.dp(activity, 6), ly - DS.dp(activity, 3),
                DS.dp(activity, 4), p1);
            canvas.drawText(members.get(1) + " a trop payé",
                w / 2f + DS.dp(activity, 14), ly, legP);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Insights
    // ─────────────────────────────────────────────────────────────

    /**
     * Remplit le container d'insights selon le résultat de répartition.
     */
    public static void renderInsights(Activity activity, LinearLayout insightsList,
                                       RepartitionModels.RepartitionResult result,
                                       List<String> members) {
        insightsList.removeAllViews();

        if (result.totalShared <= 0) {
            addInsightCard(activity, insightsList, "💡",
                "Aucune dépense commune",
                "Ajoutez des dépenses partagées pour voir la répartition.",
                "#6B7280");
            return;
        }

        // 1. Tendance
        if (members.size() >= 2) {
            double pct0 = RepartitionCalculator.spentPercent0(result);
            double pct1 = RepartitionCalculator.spentPercent1(result);
            String dominant = pct0 > 55 ? members.get(0) : (pct1 > 55 ? members.get(1) : null);

            if (dominant != null) {
                double dominantPct = pct0 > 55 ? pct0 : pct1;
                addInsightCard(activity, insightsList, "📈", "Tendance",
                    "Sur ce mois, " + dominant + " paie en moyenne "
                        + String.format(Locale.FRANCE, "%.0f%%", dominantPct)
                        + " des dépenses communes.",
                    "#10B981");
            } else {
                addInsightCard(activity, insightsList, "📈", "Tendance",
                    String.format(Locale.FRANCE,
                        "Répartition équilibrée ce mois : %.0f%% / %.0f%%.", pct0, pct1),
                    "#10B981");
            }
        }

        // 2. Remboursement
        if (!result.isBalanced()) {
            addInsightCard(activity, insightsList, "💸", "Remboursement suggéré",
                "Un virement de " + Fmt.money(result.reimbursement)
                    + " de " + result.debtor + " vers " + result.creditor
                    + " équilibrerait les comptes.",
                "#F59E0B");
        } else {
            addInsightCard(activity, insightsList, "✅", "Comptes équilibrés",
                "Aucun remboursement nécessaire ce mois. Bravo !",
                "#059669");
        }

        // 3. Volume
        int txCount = result.thisMonthTx.size();
        addInsightCard(activity, insightsList, "📊", "Volume du mois",
            txCount + " dépense" + (txCount > 1 ? "s" : "") + " partagée"
                + (txCount > 1 ? "s" : "") + " ce mois pour "
                + Fmt.money(result.totalShared) + " au total.",
            "#8B5CF6");
    }

    // ─────────────────────────────────────────────────────────────
    // Composant insight card
    // ─────────────────────────────────────────────────────────────

    private static void addInsightCard(Activity activity, LinearLayout container,
                                        String icon, String title, String body,
                                        String accentHex) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);
        card.setPadding(DS.dp(activity, DS.PAD_INPUT), DS.dp(activity, 12),
                        DS.dp(activity, DS.PAD_INPUT), DS.dp(activity, 12));
        card.setBackground(UiFactory.bgBordered(DS.CARD, DS.BORDER, DS.R_MD, activity));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = DS.dp(activity, DS.GAP_SM);
        card.setLayoutParams(lp);

        TextView tvIcon = new TextView(activity);
        tvIcon.setText(icon);
        tvIcon.setTextSize(15);
        tvIcon.setGravity(android.view.Gravity.CENTER);
        try {
            int base = Color.parseColor(accentHex);
            int bgC  = Color.argb(30, Color.red(base), Color.green(base), Color.blue(base));
            tvIcon.setBackground(UiFactory.bg(bgC, DS.R_SM, activity));
        } catch (Exception ignored) {}
        LinearLayout.LayoutParams iLP = new LinearLayout.LayoutParams(
            DS.dp(activity, 38), DS.dp(activity, 38));
        iLP.rightMargin = DS.dp(activity, DS.GAP_SM);
        tvIcon.setLayoutParams(iLP);

        LinearLayout col = new LinearLayout(activity);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView tvT = new TextView(activity);
        tvT.setText(title);
        tvT.setTextColor(DS.DARK);
        tvT.setTextSize(DS.TEXT_SM);
        tvT.setTypeface(null, Typeface.BOLD);

        TextView tvB = UiFactory.bodyMuted(activity, body);
        tvB.setTextSize(11);

        col.addView(tvT);
        col.addView(tvB);
        card.addView(tvIcon);
        card.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        container.addView(card);
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private static Paint buildPaint(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        return p;
    }

    private static Paint buildTextPaint(int sizePx, int color, boolean bold, Paint.Align align) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(sizePx);
        p.setColor(color);
        p.setTextAlign(align);
        if (bold) p.setTypeface(Typeface.DEFAULT_BOLD);
        return p;
    }

    private static String fmtShort(double v) {
        return v >= 1000
            ? String.format(Locale.FRANCE, "%.0fk€", v / 1000.0)
            : String.format(Locale.FRANCE, "%.0f€", v);
    }
}
