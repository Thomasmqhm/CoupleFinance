package com.couplefinance.ui.credits;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.couplefinance.core.base.BaseView;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.Fmt;
import com.couplefinance.core.ui.PageHeader;
import com.couplefinance.core.ui.UiFactory;
import com.couplefinance.core.ui.animations.PressAnimations;
import com.couplefinance.ui.utils.PremiumHeroBanner;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CreditsView extends BaseView {

	private PremiumHeroBanner heroBanner;

	private static final String TAG_CAPITAL = "stat_capital";
	private static final String TAG_PROCHAIN = "stat_prochain";
	private static final String TAG_ENDETTEMENT = "stat_endettement";

	private LinearLayout alertBar;
	private LinearLayout creditList;
	private LinearLayout insightsList;
	private LinearLayout txCreditList;

	public CreditsView(Activity activity) {
		super(activity);
	}

	@Override
	public View getView() {
		ScrollView scroll = makeScrollRoot();
		LinearLayout root = makePageRoot(scroll);

		buildHeader(root);
		buildHero(root);
		buildAlertBar(root);
		buildContentLayout(root);

		load();

		return scroll;
	}

	private void buildHeader(LinearLayout root) {
		LinearLayout.LayoutParams lp = lpMarginTop(0);
		lp.bottomMargin = dp(DS.GAP_LG);

		root.addView(PageHeader.forCredits(ctx(), this::showAddDialog), lp);
	}

	private void buildHero(LinearLayout root) {
		heroBanner = new PremiumHeroBanner(activity, ThemeColors.primary());
		heroBanner.setMainMetric("MENSUALITÉS TOTALES", "—", "sur 0 crédit actif");
		heroBanner.addStatCard("CAPITAL RESTANT", "—", TAG_CAPITAL);
		heroBanner.addStatCard("PROCHAIN À SOLDER", "—", TAG_PROCHAIN);
		heroBanner.addStatCard("TAUX ENDETTEMENT", "—", TAG_ENDETTEMENT);

		root.addView(heroBanner.getView());
	}

	private void buildAlertBar(LinearLayout root) {
		alertBar = UiFactory.horizontal(activity);
		alertBar.setPadding(dp(DS.PAD_INPUT), dp(12), dp(DS.PAD_INPUT), dp(12));
		alertBar.setVisibility(View.GONE);

		LinearLayout.LayoutParams ap = lpFull();
		ap.bottomMargin = dp(DS.GAP);

		root.addView(alertBar, ap);
	}

	private void buildContentLayout(LinearLayout root) {
		LinearLayout main = UiFactory.horizontal(activity);
		root.addView(main, lpFull());

		LinearLayout left = UiFactory.vertical(activity);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1.35f);
		lp.rightMargin = dp(DS.GAP);

		main.addView(left, lp);

		TextView secTitle = UiFactory.sectionTitle(ctx(), "Crédits en cours");
		secTitle.setTextColor(ThemeColors.text());

		LinearLayout.LayoutParams tp = lpFull();
		tp.bottomMargin = dp(DS.GAP_SM);

		left.addView(secTitle, tp);

		creditList = UiFactory.vertical(activity);
		left.addView(creditList, lpFull());

		// Section transactions importées catégorisées "Crédits"
		TextView txSecTitle = UiFactory.sectionTitle(ctx(), "Opérations crédit importées");
		txSecTitle.setTextColor(ThemeColors.text());
		LinearLayout.LayoutParams txtp = lpFull();
		txtp.topMargin = dp(DS.GAP_LG);
		txtp.bottomMargin = dp(DS.GAP_SM);
		left.addView(txSecTitle, txtp);

		txCreditList = UiFactory.vertical(activity);
		left.addView(txCreditList, lpFull());

		LinearLayout right = UiFactory.vertical(activity);
		main.addView(right, new LinearLayout.LayoutParams(0, -2, 0.95f));

		TextView insightsTitle = UiFactory.sectionTitle(ctx(), "Insights");
		insightsTitle.setTextColor(ThemeColors.text());
		right.addView(insightsTitle);

		insightsList = UiFactory.vertical(activity);

		LinearLayout.LayoutParams ilp = lpFull();
		ilp.topMargin = dp(DS.GAP_SM);

		right.addView(insightsList, ilp);
	}

	private void load() {
		CreditsRepository.loadAll(activity, new CreditsRepository.OnDataLoaded() {
			@Override
			public void onLoaded(CreditsModels.CreditsData data) {
				render(data);
			}

			@Override
			public void onError(String msg) {
				render(new CreditsModels.CreditsData(new java.util.ArrayList<>(), 0, 0));
			}
		});
		loadCreditTransactions();
	}

	private void loadCreditTransactions() {
		com.couplefinance.ui.transactions.TransactionsRepository.loadAll(
				activity,
				new com.couplefinance.ui.transactions.TransactionsRepository.OnDataLoaded() {
					@Override
					public void onLoaded(java.util.List<com.couplefinance.ui.transactions.TransactionsModels.Transaction> txs,
							java.util.List<String> members,
							java.util.List<String[]> cats) {
						java.util.List<com.couplefinance.ui.transactions.TransactionsModels.Transaction> credits = new java.util.ArrayList<>();
						if (txs != null) {
							for (com.couplefinance.ui.transactions.TransactionsModels.Transaction tx : txs) {
								if (tx != null && "Crédits".equalsIgnoreCase(
										tx.category == null ? "" : tx.category.trim())) {
									credits.add(tx);
								}
							}
						}
						activity.runOnUiThread(() -> renderCreditTransactions(credits));
					}

					@Override
					public void onError(String message) {}
				});
	}

	private void renderCreditTransactions(java.util.List<com.couplefinance.ui.transactions.TransactionsModels.Transaction> txs) {
		if (txCreditList == null) return;
		txCreditList.removeAllViews();

		if (txs == null || txs.isEmpty()) {
			TextView empty = UiFactory.bodyMuted(activity, "Aucune transaction de crédit importée.");
			empty.setTextSize(DS.TEXT_SM);
			txCreditList.addView(empty);
			return;
		}

		java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.FRANCE);
		for (com.couplefinance.ui.transactions.TransactionsModels.Transaction tx : txs) {
			LinearLayout row = UiFactory.horizontal(activity);
			row.setPadding(dp(DS.PAD_INPUT), dp(DS.GAP_SM), dp(DS.PAD_INPUT), dp(DS.GAP_SM));
			row.setBackground(UiFactory.card(activity).getBackground());
			LinearLayout.LayoutParams rp = lpFull();
			rp.bottomMargin = dp(DS.GAP_SM);
			row.setLayoutParams(rp);

			LinearLayout textCol = UiFactory.vertical(activity);
			TextView tvLabel = UiFactory.body(activity, tx.label == null ? "–" : tx.label);
			tvLabel.setTextSize(DS.TEXT_SM);
			tvLabel.setTypeface(null, Typeface.BOLD);
			tvLabel.setTextColor(ThemeColors.text());

			String dateTxt = tx.dateMs > 0 ? sdf.format(new java.util.Date(tx.dateMs)) : "–";
			String personTxt = tx.person != null && !tx.person.isEmpty() ? " · " + tx.person : "";
			TextView tvSub = UiFactory.bodyMuted(activity, dateTxt + personTxt);
			tvSub.setTextSize(DS.TEXT_XS);

			textCol.addView(tvLabel);
			textCol.addView(tvSub);
			row.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1f));

			TextView tvAmount = UiFactory.body(activity,
					com.couplefinance.core.ui.Fmt.money(Math.abs(tx.amount)));
			tvAmount.setTextSize(DS.TEXT_SM);
			tvAmount.setTypeface(null, Typeface.BOLD);
			tvAmount.setTextColor(ThemeColors.expense());

			row.addView(tvAmount, new LinearLayout.LayoutParams(-2, -2));
			txCreditList.addView(row);
		}
	}

	private void render(CreditsModels.CreditsData data) {
		List<CreditsModels.Credit> credits = data.credits;

		double totalMonthly = CreditsCalculator.totalMonthly(credits);
		double totalRemaining = CreditsCalculator.totalRemaining(credits);
		double debtRatio = CreditsCalculator.debtRatio(totalMonthly, data.totalFixedCharges, data.totalRevenue);
		CreditsModels.Credit soonest = CreditsCalculator.soonestToFinish(credits);

		heroBanner.updateMainValue(Fmt.money(totalMonthly) + "/mois");

		heroBanner.updateSubtitle(
				"sur " + credits.size()
						+ " crédit"
						+ (credits.size() > 1 ? "s" : "")
						+ " actif"
						+ (credits.size() > 1 ? "s" : "")
		);

		heroBanner.updateStatCard(TAG_CAPITAL, Fmt.money(totalRemaining));

		heroBanner.updateStatCard(
				TAG_PROCHAIN,
				soonest != null
						? soonest.name + "\n" + CreditsCalculator.monthsLeft(soonest) + " mois"
						: "–"
		);

		if (data.totalRevenue > 0) {
			heroBanner.updateStatCard(TAG_ENDETTEMENT, String.format(Locale.FRANCE, "%.1f%%", debtRatio));
		} else {
			heroBanner.updateStatCard(
					TAG_ENDETTEMENT,
					credits.size() + " actif" + (credits.size() > 1 ? "s" : "")
			);
		}

		renderAlertBar(data, totalMonthly, debtRatio);
		renderCredits(credits);
		CreditsInsights.render(activity, insightsList, data);
	}

	private void renderAlertBar(CreditsModels.CreditsData data, double totalMonthly, double debtRatio) {
		if (data.credits.isEmpty()) {
			alertBar.setVisibility(View.GONE);
			return;
		}

		alertBar.removeAllViews();
		alertBar.setVisibility(View.VISIBLE);

		String msg;
		boolean isWarning;

		if (data.totalRevenue <= 0) {
			msg = "Ajoutez vos revenus dans Paramètres pour calculer votre taux d'endettement.";
			isWarning = true;
		} else if (CreditsCalculator.isOverLegalLimit(debtRatio)) {
			msg = String.format(
					Locale.FRANCE,
					"Taux d'endettement de %.1f%% — au-dessus de la limite légale de 35%%. Revenus : %s, Charges totales : %s.",
					debtRatio,
					Fmt.money(data.totalRevenue),
					Fmt.money(totalMonthly + data.totalFixedCharges)
			);
			isWarning = true;
		} else if (CreditsCalculator.isInWatchZone(debtRatio)) {
			msg = String.format(
					Locale.FRANCE,
					"Taux d'endettement de %.1f%% — dans les limites mais surveillez vos charges. Revenus : %s.",
					debtRatio,
					Fmt.money(data.totalRevenue)
			);
			isWarning = true;
		} else {
			msg = String.format(
					Locale.FRANCE,
					"Taux d'endettement de %.1f%% — bien en dessous du seuil légal de 35%%.",
					debtRatio
			);
			isWarning = false;
		}

		int bgColor = isWarning ? ThemeColors.warningBackground() : ThemeColors.successBackground();
		int fgColor = isWarning ? ThemeColors.warning() : ThemeColors.success();
		int strokeColor = isWarning ? ThemeColors.warning() : ThemeColors.success();

		alertBar.setBackground(UiFactory.bgBordered(bgColor, strokeColor, DS.R_SM, activity));

		TextView tvIcon = UiFactory.body(activity, "•");
		tvIcon.setTextSize(18);
		tvIcon.setTextColor(fgColor);

		LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-2, -2);
		ip.rightMargin = dp(DS.GAP_SM);

		alertBar.addView(tvIcon, ip);

		TextView tvMsg = UiFactory.body(activity, msg);
		tvMsg.setTextColor(fgColor);
		tvMsg.setTextSize(DS.TEXT_SM);

		alertBar.addView(tvMsg, new LinearLayout.LayoutParams(0, -2, 1f));
	}

	private void renderCredits(List<CreditsModels.Credit> credits) {
		creditList.removeAllViews();

		if (credits.isEmpty()) {
			LinearLayout empty = UiFactory.emptyState(
					activity,
					"Aucun crédit",
					"Aucun crédit enregistré pour le foyer."
			);

			creditList.addView(empty, lpFull());
			return;
		}

		for (CreditsModels.Credit credit : credits) {
			LinearLayout.LayoutParams cp = lpFull();
			cp.bottomMargin = dp(DS.GAP);

			creditList.addView(makeCreditCard(credit), cp);
		}
	}

	private View makeCreditCard(CreditsModels.Credit credit) {
		double remaining = CreditsCalculator.computeRemaining(credit);
		double paid = CreditsCalculator.amountPaid(credit);
		int pct = CreditsCalculator.percentPaid(credit);
		int ml = CreditsCalculator.monthsLeft(credit);
		long endMs = CreditsCalculator.endDateMs(credit);

		String startFmt = credit.startDateMs > 0 ? Fmt.dateShort(credit.startDateMs) : "–";
		String endFmt = endMs > 0 ? Fmt.dateShort(endMs) : "–";

		CreditsModels.CreditType typeEnum = CreditsModels.CreditType.fromLabel(credit.type);

		LinearLayout card = UiFactory.card(ctx());
		PressAnimations.apply(card);

		card.setOnLongClickListener(v -> {
			showDeleteDialog(credit);
			return true;
		});

		LinearLayout row1 = UiFactory.horizontal(activity);
		LinearLayout.LayoutParams r1p = lpFull();
		r1p.bottomMargin = dp(DS.GAP_SM);
		row1.setLayoutParams(r1p);

		TextView tvEmoji = UiFactory.circleIcon(
				activity,
				credit.emoji,
				ThemeColors.primarySoft(),
				ThemeColors.text(),
				52
		);

		LinearLayout.LayoutParams eLP = new LinearLayout.LayoutParams(dp(52), dp(52));
		eLP.rightMargin = dp(DS.GAP_SM);
		tvEmoji.setLayoutParams(eLP);

		LinearLayout nameCol = UiFactory.vertical(activity);

		TextView tvName = UiFactory.body(activity, credit.name);
		tvName.setTextSize(DS.TEXT_BODY);
		tvName.setTypeface(null, Typeface.BOLD);
		tvName.setTextColor(ThemeColors.text());

		LinearLayout badgeRow = UiFactory.horizontal(activity);
		LinearLayout.LayoutParams brp = lpFull();
		brp.topMargin = dp(3);
		badgeRow.setLayoutParams(brp);

		if (credit.bank != null && !credit.bank.isEmpty()) {
			TextView tvBank = UiFactory.bodyMuted(ctx(), credit.bank);
			tvBank.setTextColor(ThemeColors.subtext());

			LinearLayout.LayoutParams bkp = new LinearLayout.LayoutParams(-2, -2);
			bkp.rightMargin = dp(DS.GAP_SM);

			badgeRow.addView(tvBank, bkp);
		}

		badgeRow.addView(UiFactory.badge(
				ctx(),
				credit.type,
				Color.parseColor(typeEnum.bgColor),
				Color.parseColor(typeEnum.textColor)
		));

		nameCol.addView(tvName);
		nameCol.addView(badgeRow);

		LinearLayout monthlyCol = UiFactory.vertical(activity);
		monthlyCol.setGravity(Gravity.END);

		TextView tvMonthly = UiFactory.amountNeutral(activity, Fmt.money(credit.monthlyPayment));
		tvMonthly.setTextColor(ThemeColors.primary());
		tvMonthly.setTextSize(20);
		tvMonthly.setGravity(Gravity.END);

		TextView tvMonthlyLbl = UiFactory.bodyMuted(ctx(), "/mois");
		tvMonthlyLbl.setTextColor(ThemeColors.subtext());
		tvMonthlyLbl.setGravity(Gravity.END);

		monthlyCol.addView(tvMonthly);
		monthlyCol.addView(tvMonthlyLbl);

		row1.addView(tvEmoji);
		row1.addView(nameCol, lpWeight(1f));
		row1.addView(monthlyCol);

		card.addView(row1);

		LinearLayout row2 = UiFactory.horizontal(activity);
		LinearLayout.LayoutParams r2p = lpFull();
		r2p.bottomMargin = dp(6);
		row2.setLayoutParams(r2p);

		TextView tvPaid = UiFactory.bodyMuted(ctx(), "Remboursé : " + Fmt.money(paid));
		tvPaid.setTextColor(ThemeColors.subtext());

		row2.addView(tvPaid, lpWeight(1f));

		TextView tvPct = UiFactory.amountNeutral(activity, Fmt.percent(pct));
		tvPct.setTextColor(ThemeColors.primary());
		tvPct.setTextSize(DS.TEXT_SM);

		row2.addView(tvPct);

		card.addView(row2);

		FrameLayout progressWrapper = buildProgressBar(pct);
		card.addView(progressWrapper);

		LinearLayout row3 = UiFactory.horizontal(activity);
		LinearLayout.LayoutParams r3p = lpFull();
		r3p.bottomMargin = dp(DS.GAP_SM);
		row3.setLayoutParams(r3p);

		TextView labelRemaining = UiFactory.bodyMuted(ctx(), "Capital restant dû : ");
		labelRemaining.setTextColor(ThemeColors.subtext());
		row3.addView(labelRemaining);

		TextView tvRemaining = UiFactory.amountNeutral(activity, Fmt.money(remaining));
		tvRemaining.setTextSize(DS.TEXT_SM);
		tvRemaining.setTextColor(ThemeColors.text());
		row3.addView(tvRemaining, lpWeight(1f));

		TextView tvTotal = UiFactory.bodyMuted(ctx(), "Total : " + Fmt.money(credit.totalAmount));
		tvTotal.setTextColor(ThemeColors.subtext());
		row3.addView(tvTotal);

		card.addView(row3);

		LinearLayout row4 = UiFactory.horizontal(activity);
		row4.setLayoutParams(lpFull());

		row4.addView(
				detailChip("TAUX", credit.rate > 0 ? String.format(Locale.FRANCE, "%.2f%%", credit.rate) : "–"),
				chipLP(true)
		);

		row4.addView(detailChip("DÉBUT", startFmt), chipLP(true));
		row4.addView(detailChip("FIN", endFmt), chipLP(true));
		row4.addView(
		detailChip(
				"PAYÉ PAR",
				credit.paidBy == null || credit.paidBy.isEmpty()
						? (credit.isJoint() ? "Compte joint" : "—")
						: credit.paidBy
		),
		chipLP(true)
);

row4.addView(
		detailChip(
				"PRÉLÈVEMENT",
				"Le " + credit.paymentDay
		),
		chipLP(false)
);
		row4.addView(detailChip("RESTANT", ml > 0 ? ml + " mois" : "Terminé"), chipLP(false));
		

		card.addView(row4);
		card.addView(buildPaymentHistory(credit));

		return card;
	}

	private FrameLayout buildProgressBar(int pct) {
		FrameLayout progressWrapper = new FrameLayout(activity);

		LinearLayout.LayoutParams pwp = lpFullH(8);
		pwp.bottomMargin = dp(DS.GAP_SM);
		progressWrapper.setLayoutParams(pwp);

		View track = new View(activity);
		track.setBackground(UiFactory.bg(ThemeColors.primarySoft(), DS.R_XS, activity));
		track.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));

		View fill = new View(activity);
		fill.setBackground(UiFactory.bg(ThemeColors.primary(), DS.R_XS, activity));
		fill.setLayoutParams(new FrameLayout.LayoutParams(0, -1));

		progressWrapper.addView(track);
		progressWrapper.addView(fill);

		final int pctFinal = Math.max(0, Math.min(100, pct));

		progressWrapper.post(() -> {
			int w = progressWrapper.getWidth();

			if (w > 0) {
				fill.setLayoutParams(new FrameLayout.LayoutParams((int) (w * pctFinal / 100f), -1));
			}
		});

		return progressWrapper;
	}

	private View buildPaymentHistory(CreditsModels.Credit credit) {
		LinearLayout section = UiFactory.vertical(activity);

		LinearLayout.LayoutParams sp = lpFull();
		sp.topMargin = dp(DS.GAP_SM);
		section.setLayoutParams(sp);

		section.addView(UiFactory.divider(activity));

		TextView histTitle = UiFactory.smallLabel(ctx(), "PAIEMENTS — 12 DERNIERS MOIS");
		histTitle.setTextColor(ThemeColors.subtext());

		LinearLayout.LayoutParams htp = lpFull();
		htp.topMargin = dp(DS.GAP_SM);
		htp.bottomMargin = dp(DS.GAP_SM);

		section.addView(histTitle, htp);

		LinearLayout dotsRow = UiFactory.horizontal(activity);
		section.addView(dotsRow, lpFull());

		Calendar dotCal = Calendar.getInstance();
		long endMs = CreditsCalculator.endDateMs(credit);

		for (int m = 11; m >= 0; m--) {
			Calendar mc = (Calendar) dotCal.clone();
			mc.add(Calendar.MONTH, -m);

			long monthMs = mc.getTimeInMillis();
			boolean active = credit.startDateMs > 0 && monthMs >= credit.startDateMs && monthMs <= endMs;

			String monthLabel = new SimpleDateFormat("MMM", Locale.FRANCE)
					.format(mc.getTime())
					.substring(0, 1)
					.toUpperCase(Locale.FRANCE);

			LinearLayout dotCol = UiFactory.vertical(activity);
			dotCol.setGravity(Gravity.CENTER);
			dotCol.setLayoutParams(lpWeight(1f));

			View dot = new View(activity);

			int dotColor = !active
					? ThemeColors.border()
					: (m == 0 ? ThemeColors.primary() : ThemeColors.success());

			dot.setBackground(UiFactory.bg(dotColor, 99, activity));

			LinearLayout.LayoutParams dotLP = new LinearLayout.LayoutParams(dp(10), dp(10));
			dotLP.bottomMargin = dp(3);
			dotLP.gravity = Gravity.CENTER_HORIZONTAL;

			dot.setLayoutParams(dotLP);

			TextView dotLabel = UiFactory.bodyMuted(ctx(), monthLabel);
			dotLabel.setTextSize(8);
			dotLabel.setGravity(Gravity.CENTER);
			dotLabel.setTextColor(active ? ThemeColors.subtext() : ThemeColors.border());

			dotCol.addView(dot);
			dotCol.addView(dotLabel);

			dotsRow.addView(dotCol);
		}

		LinearLayout legend = UiFactory.horizontal(activity);

		LinearLayout.LayoutParams legP = lpFull();
		legP.topMargin = dp(DS.GAP_SM);
		legend.setLayoutParams(legP);

		legend.addView(legendDot(ThemeColors.success(), "Payé"));
		legend.addView(legendSpacer());
		legend.addView(legendDot(ThemeColors.primary(), "Mois en cours"));

		section.addView(legend);

		return section;
	}

	private LinearLayout legendDot(int color, String label) {
		LinearLayout row = UiFactory.horizontal(activity);

		View dot = new View(activity);
		dot.setBackground(UiFactory.bg(color, 99, activity));

		LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(dp(8), dp(8));
		dp2.rightMargin = dp(5);
		dot.setLayoutParams(dp2);

		TextView tvLabel = UiFactory.bodyMuted(ctx(), label);
		tvLabel.setTextColor(ThemeColors.subtext());

		row.addView(dot);
		row.addView(tvLabel);

		return row;
	}

	private View legendSpacer() {
		View v = new View(activity);
		v.setLayoutParams(new LinearLayout.LayoutParams(dp(DS.GAP_SM), 0));
		return v;
	}

	private LinearLayout detailChip(String label, String value) {
		LinearLayout chip = UiFactory.vertical(activity);
		chip.setPadding(dp(12), dp(DS.GAP_SM), dp(12), dp(DS.GAP_SM));
		chip.setBackground(UiFactory.bg(ThemeColors.card(), DS.R_XS, activity));

		TextView tvL = UiFactory.smallLabel(activity, label);
		tvL.setTextColor(ThemeColors.subtext());

		TextView tvV = UiFactory.amountNeutral(activity, value);
		tvV.setTextSize(DS.TEXT_SM);
		tvV.setTextColor(ThemeColors.text());

		chip.addView(tvL);
		chip.addView(tvV);

		return chip;
	}

	private LinearLayout.LayoutParams chipLP(boolean marginRight) {
		LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);

		if (marginRight) {
			p.rightMargin = dp(DS.GAP_SM);
		}

		return p;
	}

	private void showAddDialog() {
		CreditsDialogs.showAddDialog(activity, this::load);
	}

	private void showDeleteDialog(CreditsModels.Credit credit) {
		CreditsDialogs.showDeleteDialog(activity, credit, this::load);
	}
}