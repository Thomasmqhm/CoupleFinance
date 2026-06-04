package com.couplefinance.ui.home;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.Fmt;
import com.couplefinance.ui.repartition.RepartitionCalculator;
import com.couplefinance.ui.repartition.RepartitionModels;
import com.couplefinance.ui.repartition.RepartitionRepository;
import com.couplefinance.ui.utils.MerchantLogoManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HomeWidgetCards {

	private final Activity activity;
	private final SharedPreferences prefs;

	public HomeWidgetCards(Activity activity, SharedPreferences prefs) {
		this.activity = activity;
		this.prefs = prefs;
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
		if (personCards == null) return;

		personCards.removeAllViews();
		personCards.setOrientation(LinearLayout.VERTICAL);

		renderPersonCardsShell(personCards, "Chargement...", null);

		RepartitionRepository.loadAll(activity, new RepartitionRepository.OnDataLoaded() {
			@Override
			public void onLoaded(RepartitionModels.RepartitionData data) {
				List<String> members = data.members == null ? new ArrayList<>() : data.members;

				if (members.size() < 2) {
					personCards.removeAllViews();
					renderPersonCardsShell(personCards, "Répartition équilibrée ✓", null);
					return;
				}

				RepartitionModels.RepartitionResult result = RepartitionCalculator.calculate(
						data.allTransactions,
						members,
						data.ratio
				);

				personCards.removeAllViews();
				renderPersonCardsShell(personCards, null, new Object[]{result, members, regularizeClick});
			}

			@Override
			public void onError(String message) {
				personCards.removeAllViews();
				renderPersonCardsShell(personCards, "Répartition indisponible", null);
			}
		});
	}

	private void renderPersonCardsShell(LinearLayout personCards, String placeholder, Object[] data) {
		LinearLayout header = new LinearLayout(activity);
		header.setOrientation(LinearLayout.HORIZONTAL);
		header.setGravity(Gravity.CENTER_VERTICAL);

		TextView title = new TextView(activity);
		title.setText("Qui doit quoi");
		title.setTextSize(DS.TEXT_SUBTITLE);
		title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
		title.setTextColor(ThemeColors.textPrimary());
		title.setLetterSpacing(-0.012f);
		title.setIncludeFontPadding(false);
		header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

		TextView badge = new TextView(activity);
		badge.setText("Auto");
		badge.setTextSize(10f);
		badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
		badge.setTextColor(ThemeColors.primary());
		badge.setGravity(Gravity.CENTER);
		badge.setLetterSpacing(0.06f);
		badge.setIncludeFontPadding(false);
		badge.setPadding(
				DS.dp(activity, 10),
				DS.dp(activity, 5),
				DS.dp(activity, 10),
				DS.dp(activity, 5)
		);

		GradientDrawable badgeBg = new GradientDrawable();
		badgeBg.setColor(ThemeColors.primaryMuted());
		badgeBg.setCornerRadius(DS.dp(activity, DS.RADIUS_PILL));
		badgeBg.setStroke(DS.dp(activity, 1), ThemeColors.withAlpha(ThemeColors.primary(), 42));
		badge.setBackground(badgeBg);

		header.addView(badge);
		personCards.addView(header);

		LinearLayout card = new LinearLayout(activity);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setPadding(
				DS.dp(activity, DS.CARD_PADDING),
				DS.dp(activity, DS.CARD_PADDING),
				DS.dp(activity, DS.CARD_PADDING),
				DS.dp(activity, DS.CARD_PADDING)
		);
		card.setBackground(HomeDashboardStyle.card(activity));
		HomeDashboardStyle.applyNativeElevation(card, 5f);

		LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
		cardLp.topMargin = DS.dp(activity, DS.SPACE_14);
		personCards.addView(card, cardLp);

		if (data == null) {
			renderPlaceholderDebtCard(card, placeholder);
			return;
		}

		RepartitionModels.RepartitionResult result = (RepartitionModels.RepartitionResult) data[0];
		List<String> members = (List<String>) data[1];
		View.OnClickListener regularizeClick = (View.OnClickListener) data[2];

		if (regularizeClick != null) {
			card.setOnClickListener(regularizeClick);
			HomeDashboardStyle.applyPressEffect(card);
		}

		renderDebtCard(card, result, members);
	}

	private void renderPlaceholderDebtCard(LinearLayout card, String text) {
		LinearLayout visual = new LinearLayout(activity);
		visual.setOrientation(LinearLayout.HORIZONTAL);
		visual.setGravity(Gravity.CENTER_VERTICAL);

		visual.addView(
				buildAvatar("T", avatarColor(0), DS.AVATAR_MD),
				new LinearLayout.LayoutParams(
						DS.dp(activity, DS.AVATAR_MD),
						DS.dp(activity, DS.AVATAR_MD)
				)
		);

		FrameLayout barWrap = new FrameLayout(activity);
		LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(0, DS.dp(activity, 12), 1f);
		barLp.setMargins(DS.dp(activity, DS.SPACE_14), 0, DS.dp(activity, DS.SPACE_14), 0);

		View track = new View(activity);
		GradientDrawable trackBg = new GradientDrawable();
		trackBg.setColor(ThemeColors.withAlpha(ThemeColors.border(), 55));
		trackBg.setCornerRadius(DS.dp(activity, DS.RADIUS_PILL));
		track.setBackground(trackBg);
		barWrap.addView(track, new FrameLayout.LayoutParams(-1, DS.dp(activity, 10), Gravity.CENTER));

		visual.addView(barWrap, barLp);

		visual.addView(
				buildAvatar("P", avatarColor(1), DS.AVATAR_MD),
				new LinearLayout.LayoutParams(
						DS.dp(activity, DS.AVATAR_MD),
						DS.dp(activity, DS.AVATAR_MD)
				)
		);

		card.addView(visual);

		TextView result = new TextView(activity);
		result.setGravity(Gravity.CENTER);
		result.setTextSize(DS.TEXT_BODY_SMALL);
		result.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
		result.setIncludeFontPadding(false);
		result.setText(text == null ? "Chargement..." : text);
		result.setTextColor(ThemeColors.textMuted());

		LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(-1, -2);
		rLp.topMargin = DS.dp(activity, DS.SPACE_18);
		card.addView(result, rLp);
	}

	private void renderDebtCard(LinearLayout card,
								RepartitionModels.RepartitionResult result,
								List<String> members) {
		String leftName = members.size() > 0 ? members.get(0) : "Moi";
		String rightName = members.size() > 1 ? members.get(1) : "Partenaire";

		double debt = result.reimbursement;
		boolean balanced = result.isBalanced();

		boolean leftDebtor = result.debtor != null && result.debtor.equalsIgnoreCase(leftName);
		boolean rightDebtor = result.debtor != null && result.debtor.equalsIgnoreCase(rightName);

		LinearLayout visual = new LinearLayout(activity);
		visual.setOrientation(LinearLayout.HORIZONTAL);
		visual.setGravity(Gravity.CENTER_VERTICAL);

		visual.addView(
				buildAvatar(leftName, avatarColor(0), DS.AVATAR_MD),
				new LinearLayout.LayoutParams(
						DS.dp(activity, DS.AVATAR_MD),
						DS.dp(activity, DS.AVATAR_MD)
				)
		);

		FrameLayout barWrap = new FrameLayout(activity);
		LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(0, DS.dp(activity, 12), 1f);
		barLp.setMargins(DS.dp(activity, DS.SPACE_14), 0, DS.dp(activity, DS.SPACE_14), 0);

		View track = new View(activity);
		GradientDrawable trackBg = new GradientDrawable();
		trackBg.setColor(ThemeColors.withAlpha(ThemeColors.border(), 55));
		trackBg.setCornerRadius(DS.dp(activity, DS.RADIUS_PILL));
		track.setBackground(trackBg);
		barWrap.addView(track, new FrameLayout.LayoutParams(-1, DS.dp(activity, 10), Gravity.CENTER));

		final View fill = new View(activity);
		GradientDrawable fillBg = new GradientDrawable(
				GradientDrawable.Orientation.LEFT_RIGHT,
				new int[]{
						ThemeColors.blend(ThemeColors.primary(), Color.WHITE, 0.24f),
						ThemeColors.primary(),
						ThemeColors.blend(ThemeColors.primaryDark(), Color.BLACK, 0.08f)
				}
		);
		fillBg.setCornerRadius(DS.dp(activity, DS.RADIUS_PILL));
		fill.setBackground(fillBg);

		FrameLayout.LayoutParams fillLp = new FrameLayout.LayoutParams(0, DS.dp(activity, 10));
		fillLp.gravity = leftDebtor ? Gravity.RIGHT | Gravity.CENTER_VERTICAL : Gravity.LEFT | Gravity.CENTER_VERTICAL;
		barWrap.addView(fill, fillLp);

		visual.addView(barWrap, barLp);

		visual.addView(
				buildAvatar(rightName, avatarColor(1), DS.AVATAR_MD),
				new LinearLayout.LayoutParams(
						DS.dp(activity, DS.AVATAR_MD),
						DS.dp(activity, DS.AVATAR_MD)
				)
		);

		card.addView(visual);

		TextView resultText = new TextView(activity);
		resultText.setGravity(Gravity.CENTER);
		resultText.setTextSize(DS.TEXT_BODY_SMALL);
		resultText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
		resultText.setIncludeFontPadding(false);

		if (balanced || members.size() < 2) {
			resultText.setText("Répartition équilibrée ✓");
			resultText.setTextColor(ThemeColors.success());
		} else {
			SpannableStringBuilder ssb = new SpannableStringBuilder();
			ssb.append(result.debtor).append(" doit ");

			int s = ssb.length();
			ssb.append(Fmt.money(debt));
			ssb.setSpan(
					new ForegroundColorSpan(ThemeColors.primary()),
					s,
					ssb.length(),
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
			);

			ssb.append(" à ").append(result.creditor);

			resultText.setText(ssb);
			resultText.setTextColor(ThemeColors.textPrimary());
		}

		LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(-1, -2);
		rLp.topMargin = DS.dp(activity, DS.SPACE_18);
		card.addView(resultText, rLp);

		double maxDebt = Math.max(1.0, result.totalShared);
		final float ratio = balanced ? 0.0f : (float) Math.max(0.18f, Math.min(0.75f, debt / maxDebt));

		barWrap.post(() -> {
			int w = barWrap.getWidth();
			if (w <= 0) return;

			ValueAnimator a = ValueAnimator.ofInt(0, Math.max(DS.dp(activity, 20), (int) (w * ratio)));
			a.setDuration(DS.ANIM_HERO);
			a.setInterpolator(new DecelerateInterpolator(2f));
			a.addUpdateListener(an -> {
				ViewGroup.LayoutParams lp = fill.getLayoutParams();
				lp.width = (int) an.getAnimatedValue();
				fill.setLayoutParams(lp);
			});
			a.start();
		});
	}

	public void renderMemberExpenseSplit(LinearLayout personBars, Map<String, Double> expensesByPerson, double totalExpenses) {
		if (personBars == null) return;
		personBars.removeAllViews();
		personBars.setOrientation(LinearLayout.VERTICAL);
		if (expensesByPerson == null || expensesByPerson.isEmpty()) return;

		int index = 0;

		for (Map.Entry<String, Double> e : expensesByPerson.entrySet()) {
			String name = e.getKey();
			double value = e.getValue();
			double pct = totalExpenses > 0 ? (value / totalExpenses) * 100.0 : 0;
			int accent = avatarColor(index);

			LinearLayout row = new LinearLayout(activity);
			row.setOrientation(LinearLayout.VERTICAL);

			LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
			rowLp.bottomMargin = DS.dp(activity, DS.SPACE_16);
			row.setLayoutParams(rowLp);

			HomeDashboardStyle.fadeIn(row, index * 60L);

			LinearLayout top = new LinearLayout(activity);
			top.setOrientation(LinearLayout.HORIZONTAL);
			top.setGravity(Gravity.CENTER_VERTICAL);

			TextView av = buildAvatar(name, accent, DS.AVATAR_SM);

			LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(
					DS.dp(activity, DS.AVATAR_SM),
					DS.dp(activity, DS.AVATAR_SM)
			);
			avLp.rightMargin = DS.dp(activity, DS.SPACE_12);
			top.addView(av, avLp);

			TextView tvName = new TextView(activity);
			tvName.setText(name);
			tvName.setTextSize(DS.TEXT_BODY_SMALL);
			tvName.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
			tvName.setTextColor(ThemeColors.textPrimary());
			tvName.setSingleLine(true);
			tvName.setLetterSpacing(-0.01f);
			tvName.setIncludeFontPadding(false);

			top.addView(tvName, new LinearLayout.LayoutParams(0, -2, 1f));

			LinearLayout amtCol = new LinearLayout(activity);
			amtCol.setOrientation(LinearLayout.VERTICAL);
			amtCol.setGravity(Gravity.END);

			TextView tvAmt = new TextView(activity);
			tvAmt.setText(fmt(value));
			tvAmt.setTextSize(DS.TEXT_BODY_SMALL);
			tvAmt.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
			tvAmt.setTextColor(accent);
			tvAmt.setLetterSpacing(-0.01f);
			tvAmt.setIncludeFontPadding(false);
			amtCol.addView(tvAmt);

			TextView tvPct = new TextView(activity);
			tvPct.setText(String.format(Locale.FRANCE, "%.0f%%", pct));
			tvPct.setTextSize(DS.TEXT_MICRO);
			tvPct.setTextColor(ThemeColors.textMuted());
			tvPct.setIncludeFontPadding(false);
			tvPct.setPadding(0, DS.dp(activity, 2), 0, 0);
			amtCol.addView(tvPct);

			top.addView(amtCol);
			row.addView(top);

			LinearLayout trackRow = new LinearLayout(activity);
			trackRow.setOrientation(LinearLayout.HORIZONTAL);

			GradientDrawable trackBg = new GradientDrawable();
			trackBg.setColor(ThemeColors.withAlpha(ThemeColors.border(), 50));
			trackBg.setCornerRadius(DS.dp(activity, DS.RADIUS_PILL));
			trackRow.setBackground(trackBg);

			LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(-1, DS.dp(activity, 8));
			trackLp.topMargin = DS.dp(activity, DS.SPACE_8);
			row.addView(trackRow, trackLp);

			View fillBar = new View(activity);
			GradientDrawable fBg = new GradientDrawable(
					GradientDrawable.Orientation.LEFT_RIGHT,
					new int[]{
							ThemeColors.blend(accent, Color.WHITE, 0.28f),
							accent
					}
			);
			fBg.setCornerRadius(DS.dp(activity, DS.RADIUS_PILL));
			fillBar.setBackground(fBg);

			trackRow.addView(fillBar, new LinearLayout.LayoutParams(0, -1, (float) Math.max(1, pct)));
			trackRow.addView(new View(activity), new LinearLayout.LayoutParams(0, -1, (float) Math.max(0, 100 - pct)));

			personBars.addView(row);
			index++;
		}
	}

	public void renderRecentTransactions(LinearLayout container, List<String[]> transactions, View.OnClickListener rowClick) {
	if (container == null) return;

	container.removeAllViews();
	container.setOrientation(LinearLayout.VERTICAL);

	if (transactions == null || transactions.isEmpty()) {
		renderEmptyTransactions(container);
		return;
	}

	int max = Math.min(6, transactions.size());

	for (int i = 0; i < max; i++) {
		String[] tx = transactions.get(i);
		if (tx == null || tx.length < 4) continue;

		String label = tx[0] != null ? tx[0] : "Opération";
		String amount = tx[1] != null ? tx[1] : "0";
		String type = tx.length > 2 ? tx[2] : "expense";
		String category = tx.length > 3 ? tx[3] : "";
		boolean isIncome = "income".equals(type);

		int mainColor = isIncome ? ThemeColors.success() : ThemeColors.primary();
		int softBg = isIncome ? ThemeColors.successSoft() : ThemeColors.primarySoft();

		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(
				DS.dp(activity, 10),
				DS.dp(activity, 9),
				DS.dp(activity, 10),
				DS.dp(activity, 9)
		);

		GradientDrawable rowBg = new GradientDrawable();
		rowBg.setColor(ThemeColors.surfaceFloating());
		rowBg.setCornerRadius(DS.dp(activity, 18));
		rowBg.setStroke(DS.dp(activity, 1), ThemeColors.withAlpha(mainColor, 16));
		row.setBackground(rowBg);

		HomeDashboardStyle.applyNativeElevation(row, 2f);
		HomeDashboardStyle.applyPressEffect(row);
		HomeDashboardStyle.fadeIn(row, i * 35L);

		if (rowClick != null) row.setOnClickListener(rowClick);

		View icon = MerchantLogoManager.createMerchantBubble(
				activity,
				label,
				category,
				isIncome,
				mainColor,
				softBg,
				ThemeColors.withAlpha(mainColor, 40)
		);

		LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
				DS.dp(activity, 34),
				DS.dp(activity, 34)
		);
		iconLp.rightMargin = DS.dp(activity, 9);
		row.addView(icon, iconLp);

		LinearLayout texts = new LinearLayout(activity);
		texts.setOrientation(LinearLayout.VERTICAL);

		TextView tvTitle = new TextView(activity);
		tvTitle.setText(cleanLabel(label));
		tvTitle.setTextSize(12.5f);
		tvTitle.setTextColor(ThemeColors.textPrimary());
		tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
		tvTitle.setSingleLine(true);
		tvTitle.setIncludeFontPadding(false);
		tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
		texts.addView(tvTitle);

		TextView tvSub = new TextView(activity);
		tvSub.setText((category == null || category.isEmpty() ? "Opération" : category) + (isIncome ? " · Revenu" : " · Dépense"));
		tvSub.setTextSize(10.5f);
		tvSub.setTextColor(ThemeColors.textMuted());
		tvSub.setSingleLine(true);
		tvSub.setIncludeFontPadding(false);
		tvSub.setEllipsize(android.text.TextUtils.TruncateAt.END);

		LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
		subLp.topMargin = DS.dp(activity, 3);
		texts.addView(tvSub, subLp);

		row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

		double amt = 0;
		try {
			amt = Double.parseDouble(amount);
		} catch (Exception ignored) {}

		TextView tvAmt = new TextView(activity);
		tvAmt.setText(String.format(Locale.FRANCE, "%s%,.2f €", isIncome ? "+" : "-", Math.abs(amt)));
		tvAmt.setTextSize(12.5f);
		tvAmt.setTypeface(Typeface.DEFAULT_BOLD);
		tvAmt.setTextColor(mainColor);
		tvAmt.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
		tvAmt.setIncludeFontPadding(false);
		tvAmt.setSingleLine(true);
		tvAmt.setEllipsize(android.text.TextUtils.TruncateAt.END);

		LinearLayout.LayoutParams amtLp = new LinearLayout.LayoutParams(
				DS.dp(activity, 92),
				-2
		);
		amtLp.leftMargin = DS.dp(activity, 8);
		row.addView(tvAmt, amtLp);

		LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
		rowLp.bottomMargin = DS.dp(activity, 7);
		container.addView(row, rowLp);
	}
}

	private void renderEmptyTransactions(LinearLayout container) {
		LinearLayout card = new LinearLayout(activity);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setGravity(Gravity.CENTER);
		card.setPadding(
				DS.dp(activity, DS.CARD_PADDING_LARGE),
				DS.dp(activity, DS.SPACE_40),
				DS.dp(activity, DS.CARD_PADDING_LARGE),
				DS.dp(activity, DS.SPACE_40)
		);

		GradientDrawable bg = new GradientDrawable();
		bg.setColor(ThemeColors.blend(ThemeColors.surfaceFloating(), ThemeColors.background(), 0.40f));
		bg.setCornerRadius(DS.dp(activity, DS.RADIUS_2XL));
		bg.setStroke(DS.dp(activity, 1), ThemeColors.withAlpha(ThemeColors.border(), 50));
		card.setBackground(bg);

		TextView icon = new TextView(activity);
		icon.setText("↻");
		icon.setTextSize(22f);
		icon.setTextColor(ThemeColors.textMuted());
		icon.setGravity(Gravity.CENTER);
		icon.setIncludeFontPadding(false);

		GradientDrawable iBg = new GradientDrawable();
		iBg.setShape(GradientDrawable.OVAL);
		iBg.setColor(ThemeColors.surfaceSoft());
		iBg.setStroke(DS.dp(activity, 1), ThemeColors.borderSoft());
		icon.setBackground(iBg);

		LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(
				DS.dp(activity, DS.AVATAR_XL),
				DS.dp(activity, DS.AVATAR_XL)
		);
		iLp.gravity = Gravity.CENTER_HORIZONTAL;
		card.addView(icon, iLp);

		TextView title = new TextView(activity);
		title.setText("Aucune opération récente");
		title.setTextSize(DS.TEXT_BODY);
		title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
		title.setTextColor(ThemeColors.textPrimary());
		title.setGravity(Gravity.CENTER);
		title.setLetterSpacing(-0.01f);
		title.setIncludeFontPadding(false);

		LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(-2, -2);
		tLp.topMargin = DS.dp(activity, DS.SPACE_18);
		tLp.gravity = Gravity.CENTER_HORIZONTAL;
		card.addView(title, tLp);

		TextView sub = new TextView(activity);
		sub.setText("Vos transactions s'afficheront ici\nautomatiquement.");
		sub.setTextSize(DS.TEXT_BODY_SMALL);
		sub.setTextColor(ThemeColors.textMuted());
		sub.setGravity(Gravity.CENTER);
		sub.setLineSpacing(DS.dp(activity, 3), 1f);
		sub.setIncludeFontPadding(false);

		LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(-2, -2);
		sLp.topMargin = DS.dp(activity, DS.SPACE_8);
		sLp.gravity = Gravity.CENTER_HORIZONTAL;
		card.addView(sub, sLp);

		container.addView(card);
		HomeDashboardStyle.fadeIn(card, 80L);
	}

	private TextView buildAvatar(String name, int color, int sizeDp) {
		TextView av = new TextView(activity);
		av.setText(initial(name));
		av.setTextColor(Color.WHITE);
		av.setTextSize(sizeDp >= DS.AVATAR_MD ? 16f : 13f);
		av.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
		av.setGravity(Gravity.CENTER);
		av.setIncludeFontPadding(false);
		av.setBackground(HomeDashboardStyle.avatarGradient(color));
		return av;
	}

	private String cleanLabel(String label) {
		if (label == null) return "Dépense";

		if (label.contains(" · ")) {
			String[] p = label.split(" · ");
			if (p.length > 1) return p[1].trim();
		}

		return label.trim().isEmpty() ? "Dépense" : label.trim();
	}

	private String fmt(double v) {
		return Fmt.money(v);
	}

	private String initial(String n) {
		if (n == null || n.trim().isEmpty()) return "?";
		return n.trim().substring(0, 1).toUpperCase(Locale.FRANCE);
	}

	private int avatarColor(int i) {
		int[] c = {
				0xFFC0614A,
				0xFF2D7D55,
				0xFFB97725,
				0xFF4A6B9A,
				0xFF7C5FB0,
				0xFFC76F8A,
				0xFF2D7D6F,
				0xFF8C7D76
		};

		return c[Math.abs(i) % c.length];
	}
}