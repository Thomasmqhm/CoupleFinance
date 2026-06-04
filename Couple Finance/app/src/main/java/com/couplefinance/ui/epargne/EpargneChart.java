package com.couplefinance.ui.epargne;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;

import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.Fmt;
import com.couplefinance.core.ui.UiFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  EpargneChart — Graphique historique + insights épargne     ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Génère :                                                   ║
 * ║    • Les cards d'insights financiers                        ║
 * ║    • Le graphique Canvas "Historique mensuel"               ║
 * ║                                                             ║
 * ║  Séparé de EpargneView pour alléger la vue principale.      ║
 * ║  Appelé par : EpargneView.renderInsights()                  ║
 * ║  Appelle    : EpargneCalculator pour les chiffres           ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
public final class EpargneChart {

	private EpargneChart() {
	}

	// ─────────────────────────────────────────────────────────────
	// Point d'entrée : remplit insightsContainer
	// ─────────────────────────────────────────────────────────────

	/**
	 * Vide et remplit le container d'insights avec les cards calculées
	 * depuis data, puis ajoute le graphique historique en bas.
	 */
	public static void render(Activity activity, LinearLayout insightsContainer, EpargneModels.EpargneData data) {
		insightsContainer.removeAllViews();

		List<EpargneModels.SavingsGoal> goals = data.goals;

		if (goals.isEmpty()) {
			addInsightCard(activity, insightsContainer, "⭐", "Meilleur mois",
					"Créez des objectifs pour suivre votre épargne.", "#F59E0B");
			addInsightCard(activity, insightsContainer, "📈", "Taux d'épargne",
					"Ajoutez vos objectifs pour voir votre taux.", "#10B981");
			addInsightCard(activity, insightsContainer, "⚡", "Priorité suggérée",
					"Commencez par un fond d'urgence (3 mois de charges).", "#EF4444");
			addInsightCard(activity, insightsContainer, "🎯", "Projection annuelle",
					"Définissez vos objectifs pour activer les projections.", "#8B5CF6");
			renderHistoryChart(activity, insightsContainer, data.monthHistory, data.monthLabels);
			return;
		}

		// 1. Meilleur mois
		int bestIdx = EpargneCalculator.bestMonthIndex(data.monthHistory);
		double bestVal = bestIdx >= 0 ? data.monthHistory[bestIdx] : 0;
		addInsightCard(activity, insightsContainer, "⭐", "Meilleur mois",
				bestVal > 0 ? data.monthLabels[bestIdx] + " : " + Fmt.money(bestVal) + " épargnés — votre record !"
						: "Continuez à épargner pour établir votre record.",
				"#F59E0B");

		// 2. Taux d'épargne
		double rate = EpargneCalculator.savingsRate(goals);
		addInsightCard(activity, insightsContainer, "📈", "Taux d'épargne", rate >= 26
				? String.format(Locale.FRANCE, "Vous épargnez %.1f%% — bien au-dessus de la moyenne (15%%).", rate)
				: rate >= 15 ? String.format(Locale.FRANCE, "Vous épargnez %.1f%% — dans la moyenne.", rate)
						: String.format(Locale.FRANCE,
								"Vous épargnez %.1f%% — en dessous de 15%%. Augmentez vos versements !", rate),
				"#10B981");

		// 3. Priorité : objectif le plus urgent ou le plus avancé
		EpargneModels.SavingsGoal urgent = EpargneCalculator.mostUrgent(goals);
		if (urgent != null) {
			int months = EpargneCalculator.monthsLeft(urgent);
			double monthly = months > 0 ? Math.ceil(urgent.remaining() / months) : urgent.remaining();
			addInsightCard(activity, insightsContainer, "⚡", "Priorité suggérée",
					urgent.name + " dans " + months + " mois — versez " + Fmt.money(monthly) + "/mois pour y arriver !",
					"#EF4444");
		} else {
			EpargneModels.SavingsGoal best = EpargneCalculator.mostAdvanced(goals);
			if (best != null) {
				addInsightCard(activity, insightsContainer, "⚡", "Priorité suggérée",
						best.name + " à " + EpargneCalculator.progressPercent(best) + "% — encore "
								+ Fmt.money(best.remaining()) + " pour l'atteindre !",
						"#EF4444");
			} else {
				addInsightCard(activity, insightsContainer, "⚡", "Priorité suggérée",
						"Tous vos objectifs sont atteints ! 🎉", "#EF4444");
			}
		}

		// 4. Projection annuelle
		double totalMonthly = EpargneCalculator.totalMonthlyNeeded(goals);
		addInsightCard(activity, insightsContainer, "🎯", "Projection annuelle",
				totalMonthly > 0
						? "À " + Fmt.money(totalMonthly) + "/mois, vous épargnerez " + Fmt.money(totalMonthly * 12)
								+ " cette année."
						: "Tous vos objectifs sont en bonne voie.",
				"#8B5CF6");

		// 5. Objectifs en retard
		int lateCount = EpargneCalculator.countLate(goals);
		if (lateCount > 0) {
			addInsightCard(
					activity, insightsContainer, "🚨", "Objectifs en retard", lateCount + " objectif"
							+ (lateCount > 1 ? "s ont" : " a") + " dépassé la date cible. Révisez vos mensualités.",
					"#DC2626");
		}

		// Graphique historique en bas
		renderHistoryChart(activity, insightsContainer, data.monthHistory, data.monthLabels);
	}

	// ─────────────────────────────────────────────────────────────
	// Graphique historique mensuel (Canvas)
	// ─────────────────────────────────────────────────────────────

	/**
	 * Ajoute le graphique "Historique mensuel" à insightsContainer.
	 * Barres verticales colorées avec labels et valeurs.
	 */
	public static void renderHistoryChart(Activity activity, LinearLayout insightsContainer, double[] history,
			String[] labels) {
		TextView header = UiFactory.sectionTitle(activity, "Historique mensuel");

		LinearLayout.LayoutParams hLP = new LinearLayout.LayoutParams(-1, -2);
		hLP.topMargin = DS.dp(activity, DS.GAP_LG);
		hLP.bottomMargin = DS.dp(activity, DS.GAP_SM);
		header.setLayoutParams(hLP);

		insightsContainer.addView(header);

		final double[] h = history;
		final String[] lb = labels;

		View chartView = new View(activity) {
			@Override
			protected void onDraw(Canvas canvas) {
				drawBarChart(canvas, getWidth(), getHeight(), h, lb, activity);
			}
		};

		chartView.setBackground(UiFactory.bgBordered(ThemeColors.card(), ThemeColors.border(), DS.R_MD, activity));

		LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, DS.dp(activity, 140));
		clp.bottomMargin = DS.dp(activity, DS.GAP_SM);
		chartView.setLayoutParams(clp);

		insightsContainer.addView(chartView);
	}

	// ─────────────────────────────────────────────────────────────
	// Rendu Canvas — barres verticales
	// ─────────────────────────────────────────────────────────────

	private static void drawBarChart(Canvas canvas, int w, int h, double[] history, String[] labels,
			Activity activity) {
		if (w <= 0 || h <= 0 || history == null || history.length == 0)
			return;

		int pH = DS.dp(activity, 20), pV = DS.dp(activity, 24), n = history.length;
		int totalW = w - pH * 2;
		int barW = (int) (totalW / n * 0.55f);
		int gap = totalW / n;

		double maxVal = 1;
		for (double v : history)
			if (v > maxVal)
				maxVal = v;
		int chartH = h - pV * 2;

		// Couleur du dernier mois (mois courant) = vert épargne
		int colorActive = ThemeColors.primary();
		int colorPast = ThemeColors.primaryMuted();

		Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
		Paint lbl = buildTextPaint(DS.dp(activity, 9), DS.DARK, true, Paint.Align.CENTER);
		Paint mon = buildTextPaint(DS.dp(activity, 9), DS.MUTED, false, Paint.Align.CENTER);

		for (int i = 0; i < n; i++) {
			double val = history[i];
			int bH = (int) (chartH * (val / maxVal));
			if (bH < DS.dp(activity, 4) && val > 0)
				bH = DS.dp(activity, 4);
			int cx = pH + gap * i + gap / 2;

			RectF r = new RectF(cx - barW / 2f, pV + chartH - bH, cx + barW / 2f, pV + chartH);

			fill.setColor(i == n - 1 ? colorActive : colorPast);
			fill.setAlpha(i == n - 1 ? 255 : 160 + i * 15);
			canvas.drawRoundRect(r, DS.dp(activity, 6), DS.dp(activity, 6), fill);

			// Valeur au-dessus de la barre
			if (val > 0) {
				fill.setAlpha(255);
				canvas.drawText(fmtShort(val), cx, r.top - DS.dp(activity, 4), lbl);
			}

			// Label mois en bas
			String lb = labels != null && i < labels.length ? labels[i] : "";
			if (lb.length() > 8)
				lb = lb.substring(0, 8);
			canvas.drawText(lb, cx, h - DS.dp(activity, 4), mon);
		}
	}

	// ─────────────────────────────────────────────────────────────
	// Composant insight card
	// ─────────────────────────────────────────────────────────────

	private static void addInsightCard(Activity activity, LinearLayout container, String icon, String title,
			String body, String accentHex) {
		LinearLayout card = new LinearLayout(activity);
		card.setOrientation(LinearLayout.HORIZONTAL);
		card.setGravity(android.view.Gravity.CENTER_VERTICAL);
		card.setPadding(DS.dp(activity, DS.PAD_INPUT), DS.dp(activity, 14), DS.dp(activity, DS.PAD_INPUT),
				DS.dp(activity, 14));
		card.setBackground(UiFactory.bgBordered(DS.CARD, DS.BORDER, DS.R_MD, activity));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.bottomMargin = DS.dp(activity, DS.GAP_SM);
		card.setLayoutParams(lp);

		// Icône avec fond coloré pastel
		TextView tvIcon = new TextView(activity);
		tvIcon.setText(icon);
		tvIcon.setTextSize(16);
		tvIcon.setGravity(android.view.Gravity.CENTER);
		try {
			int base = Color.parseColor(accentHex);
			int bgC = Color.argb(30, Color.red(base), Color.green(base), Color.blue(base));
			tvIcon.setBackground(UiFactory.bg(bgC, DS.R_SM, activity));
		} catch (Exception ignored) {
			tvIcon.setBackground(UiFactory.bg(DS.TERRA_LIGHT, DS.R_SM, activity));
		}
		LinearLayout.LayoutParams iLP = new LinearLayout.LayoutParams(DS.dp(activity, 40), DS.dp(activity, 40));
		iLP.rightMargin = DS.dp(activity, DS.GAP_SM);
		tvIcon.setLayoutParams(iLP);

		// Textes
		LinearLayout col = new LinearLayout(activity);
		col.setOrientation(LinearLayout.VERTICAL);

		TextView tvTitle = UiFactory.sectionTitle(activity, title);
		tvTitle.setTextSize(DS.TEXT_SM);

		TextView tvBody = UiFactory.bodyMuted(activity, body);
		tvBody.setTextSize(12);

		col.addView(tvTitle);
		col.addView(tvBody);

		card.addView(tvIcon);
		card.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
		container.addView(card);
	}

	// ─────────────────────────────────────────────────────────────
	// Helpers
	// ─────────────────────────────────────────────────────────────

	private static Paint buildTextPaint(int sizePx, int color, boolean bold, Paint.Align align) {
		Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
		p.setTextSize(sizePx);
		p.setColor(color);
		p.setTextAlign(align);
		if (bold)
			p.setTypeface(Typeface.DEFAULT_BOLD);
		return p;
	}

	private static String fmtShort(double v) {
		return v >= 1000 ? String.format(Locale.FRANCE, "%.0fk€", v / 1000.0)
				: String.format(Locale.FRANCE, "%.0f€", v);
	}
}
