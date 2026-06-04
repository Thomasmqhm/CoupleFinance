package com.couplefinance.ui.repartition;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import com.couplefinance.core.ui.animations.PressAnimations;

import java.util.ArrayList;
import java.util.List;

public class RepartitionView extends BaseView {

	private int[] ratio;

	private TextView btnRatio;
	private TextView tvTotal;
	private TextView tvStatus;
	private TextView tvRatioValue;

	private LinearLayout heroMembersRow;
	private LinearLayout ratioBarContainer;
	private LinearLayout insightsList;

	private final ArrayList<String> currentMembers = new ArrayList<>();

	public RepartitionView(Activity activity) {
		super(activity);
		ratio = RepartitionRepository.loadRatio(activity);
	}

	@Override
	public View getView() {
		ScrollView scroll = new ScrollView(activity);
		scroll.setFillViewport(true);
		scroll.setClipToPadding(false);
		scroll.setBackgroundColor(ThemeColors.background());

		LinearLayout root = new LinearLayout(activity);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(dp(18), dp(18), dp(18), dp(125));
		root.setBackgroundColor(ThemeColors.background());

		scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

		buildHeader(root);
		buildHero(root);
		buildRatioSection(root);
		buildInsights(root);

		load();

		return scroll;
	}

	private void buildHeader(LinearLayout root) {
		LinearLayout header = new LinearLayout(activity);
		header.setOrientation(LinearLayout.HORIZONTAL);
		header.setGravity(Gravity.CENTER_VERTICAL);

		TextView title = new TextView(activity);
		title.setText("Qui doit combien ?");
		title.setTextColor(ThemeColors.text());
		title.setTextSize(26);
		title.setTypeface(Typeface.DEFAULT_BOLD);
		title.setIncludeFontPadding(false);

		header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

		btnRatio = new TextView(activity);
		btnRatio.setText("Ratio " + ratio[0] + "/" + ratio[1] + "  ›");
		btnRatio.setTextColor(ThemeColors.primary());
		btnRatio.setTextSize(14);
		btnRatio.setTypeface(Typeface.DEFAULT_BOLD);
		btnRatio.setGravity(Gravity.CENTER);
		btnRatio.setPadding(dp(16), dp(10), dp(16), dp(10));
		btnRatio.setBackground(bg(alpha(ThemeColors.primary(), 24), 100));

		PressAnimations.apply(btnRatio);
		btnRatio.setOnClickListener(v -> showRatioDialog());

		header.addView(btnRatio);
		root.addView(header, new LinearLayout.LayoutParams(-1, -2));
	}

	private void buildHero(LinearLayout root) {
		FrameLayout hero = new FrameLayout(activity);

		GradientDrawable heroBg = new GradientDrawable(
				GradientDrawable.Orientation.TL_BR,
				new int[]{
						alpha(ThemeColors.primary(), 18),
						alpha(Color.WHITE, 170),
						alpha(ThemeColors.primary(), 14)
				}
		);
		heroBg.setCornerRadius(dp(30));
		heroBg.setStroke(dp(1), alpha(Color.WHITE, 190));

		hero.setBackground(heroBg);
		hero.setPadding(dp(20), dp(22), dp(20), dp(20));
		hero.setElevation(dp(2));

		LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, -2);
		heroLp.topMargin = dp(28);
		root.addView(hero, heroLp);

		View bubble = new View(activity);
		bubble.setBackground(circle(alpha(ThemeColors.primary(), 16)));

		FrameLayout.LayoutParams bubbleLp = new FrameLayout.LayoutParams(dp(160), dp(160));
		bubbleLp.gravity = Gravity.RIGHT | Gravity.TOP;
		bubbleLp.topMargin = -dp(60);
		bubbleLp.rightMargin = -dp(30);
		hero.addView(bubble, bubbleLp);

		LinearLayout content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.VERTICAL);
		content.setGravity(Gravity.CENTER_HORIZONTAL);

		hero.addView(content, new FrameLayout.LayoutParams(-1, -2));

		TextView label = new TextView(activity);
		label.setText("Total dépenses communes");
		label.setTextColor(ThemeColors.subtext());
		label.setTextSize(15);
		label.setTypeface(Typeface.DEFAULT_BOLD);
		label.setGravity(Gravity.CENTER);

		content.addView(label);

		tvTotal = new TextView(activity);
		tvTotal.setText("0 €");
		tvTotal.setTextColor(ThemeColors.text());
		tvTotal.setTextSize(38);
		tvTotal.setTypeface(Typeface.DEFAULT_BOLD);
		tvTotal.setGravity(Gravity.CENTER);
		tvTotal.setIncludeFontPadding(false);

		LinearLayout.LayoutParams totalLp = new LinearLayout.LayoutParams(-1, -2);
		totalLp.topMargin = dp(12);
		content.addView(tvTotal, totalLp);

		tvStatus = new TextView(activity);
		tvStatus.setText("✓ Les comptes sont équilibrés");
		tvStatus.setTextColor(ThemeColors.success());
		tvStatus.setTextSize(13);
		tvStatus.setTypeface(Typeface.DEFAULT_BOLD);
		tvStatus.setGravity(Gravity.CENTER);
		tvStatus.setPadding(dp(16), dp(8), dp(16), dp(8));
		tvStatus.setBackground(bg(alpha(ThemeColors.success(), 22), 100));

		LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-2, -2);
		statusLp.topMargin = dp(14);
		content.addView(tvStatus, statusLp);

		heroMembersRow = new LinearLayout(activity);
		heroMembersRow.setOrientation(LinearLayout.HORIZONTAL);
		heroMembersRow.setGravity(Gravity.CENTER);

		LinearLayout.LayoutParams membersLp = new LinearLayout.LayoutParams(-1, -2);
		membersLp.topMargin = dp(24);
		content.addView(heroMembersRow, membersLp);
	}

	private void buildRatioSection(LinearLayout root) {
		LinearLayout top = new LinearLayout(activity);
		top.setOrientation(LinearLayout.HORIZONTAL);
		top.setGravity(Gravity.CENTER_VERTICAL);

		LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(-1, -2);
		topLp.topMargin = dp(30);
		root.addView(top, topLp);

		TextView title = sectionTitle("Ratio actuel");
		top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

		tvRatioValue = new TextView(activity);
		tvRatioValue.setText(ratio[0] + "/" + ratio[1]);
		tvRatioValue.setTextColor(ThemeColors.subtext());
		tvRatioValue.setTextSize(20);
		tvRatioValue.setTypeface(Typeface.DEFAULT_BOLD);
		top.addView(tvRatioValue);

		ratioBarContainer = new LinearLayout(activity);
		ratioBarContainer.setOrientation(LinearLayout.VERTICAL);

		LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, dp(54));
		barLp.topMargin = dp(16);
		root.addView(ratioBarContainer, barLp);
	}

	private void buildInsights(LinearLayout root) {
		TextView title = sectionTitle("Insights");

		LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
		titleLp.topMargin = dp(30);
		root.addView(title, titleLp);

		insightsList = new LinearLayout(activity);
		insightsList.setOrientation(LinearLayout.VERTICAL);

		LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(-1, -2);
		listLp.topMargin = dp(16);
		root.addView(insightsList, listLp);
	}

	private void load() {
		RepartitionRepository.loadAll(activity, new RepartitionRepository.OnDataLoaded() {
			@Override
			public void onLoaded(RepartitionModels.RepartitionData data) {
				compute(data);
			}

			@Override
			public void onError(String msg) {
				compute(new RepartitionModels.RepartitionData(
						new ArrayList<>(),
						new ArrayList<>(),
						ratio
				));
			}
		});
	}

	private void compute(RepartitionModels.RepartitionData data) {
		ratio = data.ratio;

		currentMembers.clear();

		if (data.members != null) {
			currentMembers.addAll(data.members);
		}

		List<String> members = data.members == null ? new ArrayList<>() : data.members;

		if (members.size() < 2) {
			renderSingleMemberState(members);
			return;
		}

		RepartitionModels.RepartitionResult result = RepartitionCalculator.calculate(
				data.allTransactions,
				members,
				data.ratio
		);

		render(result, members);
	}

	private void render(RepartitionModels.RepartitionResult result, List<String> members) {
		tvTotal.setText(Fmt.money(result.totalShared));

		if (result.isBalanced()) {
			tvStatus.setText("✓ Les comptes sont équilibrés");
			tvStatus.setTextColor(ThemeColors.success());
			tvStatus.setBackground(bg(alpha(ThemeColors.success(), 22), 100));
			tvStatus.setOnClickListener(null);
		} else {
			tvStatus.setText(result.debtor + " doit " + Fmt.money(result.reimbursement) + " à " + result.creditor);
			tvStatus.setTextColor(ThemeColors.primary());
			tvStatus.setBackground(bg(alpha(ThemeColors.primary(), 26), 100));
			tvStatus.setOnClickListener(v -> showSettlementDialog(result, members));
			PressAnimations.apply(tvStatus);
		}

		renderHeroMembers(result, members);
		renderRatioBar(members);
		renderInsights(result, members);
	}

	private void renderHeroMembers(RepartitionModels.RepartitionResult result, List<String> members) {
		heroMembersRow.removeAllViews();

		LinearLayout card0 = memberMiniCard(members.get(0), result.spent0, result.ideal0, 0);
		LinearLayout card1 = memberMiniCard(members.get(1), result.spent1, result.ideal1, 1);

		LinearLayout.LayoutParams lp0 = new LinearLayout.LayoutParams(0, -2, 1f);
		lp0.rightMargin = dp(8);

		LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, -2, 1f);
		lp1.leftMargin = dp(8);

		heroMembersRow.addView(card0, lp0);
		heroMembersRow.addView(card1, lp1);
	}

	private LinearLayout memberMiniCard(String name, double paid, double ideal, int index) {
		LinearLayout card = new LinearLayout(activity);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setPadding(dp(14), dp(14), dp(14), dp(14));
		card.setBackground(bg(Color.WHITE, 24));
		card.setElevation(dp(1));

		LinearLayout header = new LinearLayout(activity);
		header.setOrientation(LinearLayout.HORIZONTAL);
		header.setGravity(Gravity.CENTER_VERTICAL);

		TextView avatar = avatar(name, index);

		LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dp(32), dp(32));
		avLp.rightMargin = dp(10);
		header.addView(avatar, avLp);

		TextView tvName = new TextView(activity);
		tvName.setText(name);
		tvName.setTextColor(ThemeColors.text());
		tvName.setTextSize(16);
		tvName.setTypeface(Typeface.DEFAULT_BOLD);
		tvName.setSingleLine(true);

		header.addView(tvName, new LinearLayout.LayoutParams(0, -2, 1f));
		card.addView(header);

		card.addView(detailLine("Payé", Fmt.money(paid), ThemeColors.text(), true));
		card.addView(detailLine("Idéal (" + (index == 0 ? ratio[0] : ratio[1]) + "%)", Fmt.money(ideal), ThemeColors.subtext(), false));

		return card;
	}

	private LinearLayout detailLine(String label, String value, int color, boolean first) {
		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);

		LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
		rowLp.topMargin = first ? dp(14) : dp(8);
		row.setLayoutParams(rowLp);

		TextView tvLabel = new TextView(activity);
		tvLabel.setText(label);
		tvLabel.setTextColor(ThemeColors.subtext());
		tvLabel.setTextSize(13);

		row.addView(tvLabel, new LinearLayout.LayoutParams(0, -2, 1f));

		TextView tvValue = new TextView(activity);
		tvValue.setText(value);
		tvValue.setTextColor(color);
		tvValue.setTextSize(16);
		tvValue.setTypeface(Typeface.DEFAULT_BOLD);

		row.addView(tvValue);

		return row;
	}

	private void renderRatioBar(List<String> members) {
		ratioBarContainer.removeAllViews();

		FrameLayout wrapper = new FrameLayout(activity);
		wrapper.setPadding(dp(6), dp(6), dp(6), dp(6));
		wrapper.setBackground(bg(Color.WHITE, 22));
		wrapper.setElevation(dp(2));

		PressAnimations.apply(wrapper);
		wrapper.setOnClickListener(v -> showRatioDialog());

		ratioBarContainer.addView(wrapper, new LinearLayout.LayoutParams(-1, -1));

		LinearLayout split = new LinearLayout(activity);
		split.setOrientation(LinearLayout.HORIZONTAL);

		wrapper.addView(split, new FrameLayout.LayoutParams(-1, -1));

		TextView left = ratioPart(
				initial(members.size() > 0 ? members.get(0) : "T"),
				alpha(DS.avatarColor(0), 35),
				Gravity.LEFT | Gravity.CENTER_VERTICAL
		);

		TextView right = ratioPart(
				initial(members.size() > 1 ? members.get(1) : "P"),
				alpha(DS.avatarColor(1), 35),
				Gravity.RIGHT | Gravity.CENTER_VERTICAL
		);

		left.setOnClickListener(v -> showRatioDialog());
		right.setOnClickListener(v -> showRatioDialog());

		split.addView(left, new LinearLayout.LayoutParams(0, -1, Math.max(1, ratio[0])));
		split.addView(right, new LinearLayout.LayoutParams(0, -1, Math.max(1, ratio[1])));

		TextView center = new TextView(activity);
		center.setText("+");
		center.setTextColor(ThemeColors.primary());
		center.setTextSize(21);
		center.setTypeface(Typeface.DEFAULT_BOLD);
		center.setGravity(Gravity.CENTER);
		center.setBackground(circle(Color.WHITE));
		center.setElevation(dp(4));
		center.setClickable(true);
		center.setFocusable(true);

		PressAnimations.apply(center);
		center.setOnClickListener(v -> showRatioDialog());

		FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(dp(42), dp(42));
		centerLp.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
		wrapper.addView(center, centerLp);

		wrapper.post(() -> {
			int availableWidth = wrapper.getWidth() - dp(12);
			int x = dp(6) + (int) (availableWidth * (ratio[0] / 100f)) - dp(21);

			FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) center.getLayoutParams();
			lp.leftMargin = Math.max(dp(2), Math.min(x, wrapper.getWidth() - dp(44)));
			lp.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
			center.setLayoutParams(lp);
		});
	}

	private TextView ratioPart(String initial, int bgColor, int gravity) {
		TextView tv = new TextView(activity);
		tv.setText(initial);
		tv.setTextColor(Color.WHITE);
		tv.setTextSize(12);
		tv.setTypeface(Typeface.DEFAULT_BOLD);
		tv.setGravity(gravity);
		tv.setPadding(dp(18), 0, dp(18), 0);
		tv.setBackground(bg(bgColor, 18));
		return tv;
	}

	private void renderInsights(RepartitionModels.RepartitionResult result, List<String> members) {
		insightsList.removeAllViews();

		if (result.totalShared <= 0.5) {
			insightsList.addView(insightCard(
					"$",
					"Aucune dépense commune",
					"Commencez par ajouter des dépenses partagées pour voir l’évolution de votre équilibre.",
					"",
					0,
					0,
					0,
					null
			));
			return;
		}

		double initialDebt = computeInitialDebt(result);
		double alreadyPaid = Math.max(0, initialDebt - result.reimbursement);

		if (result.isBalanced()) {
			insightsList.addView(insightCard(
					"✓",
					"Équilibre parfait",
					"Les dépenses communes sont correctement réparties selon le ratio actuel.",
					"",
					initialDebt,
					alreadyPaid,
					result.reimbursement,
					null
			));
		} else {
			insightsList.addView(insightCard(
					"↔",
					"Remboursement conseillé",
					result.debtor + " doit encore rembourser " + Fmt.money(result.reimbursement) + " à " + result.creditor + ".",
					"Créer un virement",
					initialDebt,
					alreadyPaid,
					result.reimbursement,
					v -> showSettlementDialog(result, members)
			));
		}
	}

	private double computeInitialDebt(RepartitionModels.RepartitionResult result) {
		double rawBalance0 = result.spent0 - result.ideal0;
		double rawBalance1 = result.spent1 - result.ideal1;

		if (rawBalance0 < -0.5) {
			return Math.abs(rawBalance0);
		}

		if (rawBalance1 < -0.5) {
			return Math.abs(rawBalance1);
		}

		return result.reimbursement;
	}

	private View insightCard(String icon,
							 String title,
							 String body,
							 String action,
							 double initialDebt,
							 double alreadyPaid,
							 double remaining,
							 View.OnClickListener listener) {
		LinearLayout card = new LinearLayout(activity);
		card.setOrientation(LinearLayout.HORIZONTAL);
		card.setPadding(dp(18), dp(18), dp(18), dp(18));
		card.setBackground(bg(ThemeColors.card(), 26));
		card.setElevation(dp(2));

		TextView tvIcon = new TextView(activity);
		tvIcon.setText(icon);
		tvIcon.setGravity(Gravity.CENTER);
		tvIcon.setTextSize(24);
		tvIcon.setTextColor(ThemeColors.primary());
		tvIcon.setBackground(circle(alpha(ThemeColors.primary(), 20)));

		LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(58), dp(58));
		iconLp.rightMargin = dp(16);
		card.addView(tvIcon, iconLp);

		LinearLayout texts = new LinearLayout(activity);
		texts.setOrientation(LinearLayout.VERTICAL);
		card.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

		TextView tvTitle = new TextView(activity);
		tvTitle.setText(title);
		tvTitle.setTextColor(ThemeColors.text());
		tvTitle.setTextSize(17);
		tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
		texts.addView(tvTitle);

		TextView tvBody = new TextView(activity);
		tvBody.setText(body);
		tvBody.setTextColor(ThemeColors.subtext());
		tvBody.setTextSize(14);
		tvBody.setLineSpacing(dp(3), 1f);

		LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2);
		bodyLp.topMargin = dp(8);
		texts.addView(tvBody, bodyLp);

		if (initialDebt > 0.5 || alreadyPaid > 0.5 || remaining > 0.5) {
			LinearLayout details = new LinearLayout(activity);
			details.setOrientation(LinearLayout.VERTICAL);
			details.setPadding(dp(14), dp(12), dp(14), dp(12));
			details.setBackground(bg(alpha(ThemeColors.primary(), 12), 18));

			LinearLayout.LayoutParams detailsLp = new LinearLayout.LayoutParams(-1, -2);
			detailsLp.topMargin = dp(14);
			texts.addView(details, detailsLp);

			details.addView(insightAmountRow("Dette initiale", Fmt.money(initialDebt), ThemeColors.text()));
			details.addView(insightAmountRow("Déjà remboursé", Fmt.money(alreadyPaid), ThemeColors.success()));
			details.addView(insightAmountRow("Reste à payer", Fmt.money(remaining), remaining <= 0.5 ? ThemeColors.success() : ThemeColors.primary()));
		}

		if (action != null && !action.trim().isEmpty()) {
			TextView tvAction = new TextView(activity);
			tvAction.setText(action);
			tvAction.setTextColor(ThemeColors.primary());
			tvAction.setTextSize(14);
			tvAction.setTypeface(Typeface.DEFAULT_BOLD);

			LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(-1, -2);
			actionLp.topMargin = dp(14);
			texts.addView(tvAction, actionLp);

			if (listener != null) {
				tvAction.setOnClickListener(listener);
				PressAnimations.apply(tvAction);
			}
		}

		return card;
	}

	private LinearLayout insightAmountRow(String label, String value, int valueColor) {
		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);

		TextView tvLabel = new TextView(activity);
		tvLabel.setText(label);
		tvLabel.setTextColor(ThemeColors.subtext());
		tvLabel.setTextSize(12);

		row.addView(tvLabel, new LinearLayout.LayoutParams(0, -2, 1f));

		TextView tvValue = new TextView(activity);
		tvValue.setText(value);
		tvValue.setTextColor(valueColor);
		tvValue.setTextSize(13);
		tvValue.setTypeface(Typeface.DEFAULT_BOLD);

		row.addView(tvValue);

		return row;
	}

	private void renderSingleMemberState(List<String> members) {
		tvTotal.setText("0 €");
		tvStatus.setText("Ajoutez un second membre au foyer");
		tvStatus.setTextColor(ThemeColors.primary());
		tvStatus.setBackground(bg(alpha(ThemeColors.primary(), 24), 100));

		heroMembersRow.removeAllViews();

		LinearLayout card0 = memberMiniCard(members.size() > 0 ? members.get(0) : "Moi", 0, 0, 0);
		LinearLayout card1 = memberMiniCard("Partenaire", 0, 0, 1);

		LinearLayout.LayoutParams lp0 = new LinearLayout.LayoutParams(0, -2, 1f);
		lp0.rightMargin = dp(8);

		LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, -2, 1f);
		lp1.leftMargin = dp(8);

		heroMembersRow.addView(card0, lp0);
		heroMembersRow.addView(card1, lp1);

		renderRatioBar(members);

		insightsList.removeAllViews();
		insightsList.addView(insightCard(
				"$",
				"Aucune dépense commune",
				"Commencez par ajouter des dépenses partagées pour voir l’évolution de votre équilibre.",
				"",
				0,
				0,
				0,
				null
		));
	}

	private TextView sectionTitle(String text) {
		TextView tv = new TextView(activity);
		tv.setText(text);
		tv.setTextColor(ThemeColors.text());
		tv.setTextSize(22);
		tv.setTypeface(Typeface.DEFAULT_BOLD);
		tv.setIncludeFontPadding(false);
		return tv;
	}

	private TextView avatar(String name, int index) {
		TextView tv = new TextView(activity);
		tv.setText(initial(name));
		tv.setTextColor(Color.WHITE);
		tv.setTextSize(14);
		tv.setTypeface(Typeface.DEFAULT_BOLD);
		tv.setGravity(Gravity.CENTER);
		tv.setIncludeFontPadding(false);
		tv.setBackground(circle(DS.avatarColor(index)));
		return tv;
	}

	private String initial(String name) {
		if (name == null || name.trim().isEmpty()) {
			return "?";
		}
		return name.trim().substring(0, 1).toUpperCase(java.util.Locale.FRANCE);
	}

	private GradientDrawable bg(int color, int radiusDp) {
		GradientDrawable gd = new GradientDrawable();
		gd.setColor(color);
		gd.setCornerRadius(dp(radiusDp));
		return gd;
	}

	private GradientDrawable circle(int color) {
		GradientDrawable gd = new GradientDrawable();
		gd.setShape(GradientDrawable.OVAL);
		gd.setColor(color);
		return gd;
	}

	private int alpha(int color, int alpha) {
		return Color.argb(
				alpha,
				Color.red(color),
				Color.green(color),
				Color.blue(color)
		);
	}

	private void showRatioDialog() {
		RepartitionDialogs.showRatioDialog(activity, ratio, currentMembers, newRatio0 -> {
			ratio[0] = newRatio0;
			ratio[1] = 100 - newRatio0;

			RepartitionRepository.saveRatio(activity, newRatio0);

			btnRatio.setText("Ratio " + ratio[0] + "/" + ratio[1] + "  ›");
			tvRatioValue.setText(ratio[0] + "/" + ratio[1]);

			load();
		});
	}

	private void showSettlementDialog(RepartitionModels.RepartitionResult result, List<String> members) {
		RepartitionDialogs.showSettlementDialog(
				activity,
				result.debtor,
				result.creditor,
				result.reimbursement,
				members,
				this::load
		);
	}
}