package com.couplefinance.ui.epargne;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.couplefinance.core.base.BaseView;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.Fmt;
import com.couplefinance.core.ui.animations.PressAnimations;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EpargneView extends BaseView {

	private LinearLayout goalsContainer;
	private LinearLayout insightsContainer;

	private TextView tvHeroTotal;
	private TextView tvHeroSubtitle;
	private TextView tvHeroGoals;
	private TextView tvHeroMonth;
	private TextView tvHeroPercent;

	public EpargneView(Activity activity) {
		super(activity);
	}

	@Override
	public View getView() {

		ScrollView scroll = new ScrollView(activity);
		scroll.setFillViewport(true);
		scroll.setClipToPadding(false);
		scroll.setBackgroundColor(ThemeColors.background());

		LinearLayout root = new LinearLayout(activity);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(dp(18), dp(18), dp(18), dp(com.couplefinance.core.ui.DS.NAV_CLEARANCE));
		root.setBackgroundColor(ThemeColors.background());

		scroll.addView(root, new ScrollView.LayoutParams(
				ScrollView.LayoutParams.MATCH_PARENT,
				ScrollView.LayoutParams.WRAP_CONTENT
		));

		buildHeader(root);
		buildHero(root);
		buildGoals(root);
		buildInsights(root);

		load();

		return scroll;
	}

	private void buildHeader(LinearLayout root) {

		LinearLayout header = new LinearLayout(activity);
		header.setOrientation(LinearLayout.HORIZONTAL);
		header.setGravity(Gravity.CENTER_VERTICAL);

		LinearLayout.LayoutParams headerLp =
				new LinearLayout.LayoutParams(-1, -2);

		root.addView(header, headerLp);

		LinearLayout titles = new LinearLayout(activity);
		titles.setOrientation(LinearLayout.VERTICAL);

		LinearLayout.LayoutParams titlesLp =
				new LinearLayout.LayoutParams(0, -2, 1f);

		header.addView(titles, titlesLp);

		TextView top = new TextView(activity);
		top.setText("ÉPARGNE · " + currentMonth());
		top.setTextColor(ThemeColors.subtext());
		top.setTextSize(11);
		top.setTypeface(Typeface.DEFAULT_BOLD);
		top.setLetterSpacing(0.06f);

		titles.addView(top);

		TextView title = new TextView(activity);
		title.setText("Mes objectifs");
		title.setTextColor(ThemeColors.text());
		title.setTextSize(26);
		title.setTypeface(Typeface.DEFAULT_BOLD);
		title.setIncludeFontPadding(false);

		LinearLayout.LayoutParams titleLp =
				new LinearLayout.LayoutParams(-1, -2);

		titleLp.topMargin = dp(4);

		titles.addView(title, titleLp);

		TextView add = new TextView(activity);
		add.setText("+ Objectif");
		add.setTextColor(Color.WHITE);
		add.setTextSize(13);
		add.setTypeface(Typeface.DEFAULT_BOLD);
		add.setGravity(Gravity.CENTER);
		add.setPadding(dp(16), dp(10), dp(16), dp(10));
		add.setBackground(bg(ThemeColors.primary(), 100));

		PressAnimations.apply(add);

		add.setOnClickListener(v -> showAddDialog());

		header.addView(add);
	}

	private void buildHero(LinearLayout root) {

		FrameLayout hero = new FrameLayout(activity);

		GradientDrawable heroBg = new GradientDrawable(
				GradientDrawable.Orientation.TL_BR,
				new int[]{
						ThemeColors.primary(),
						ThemeColors.blend(
								ThemeColors.primary(),
								Color.WHITE,
								0.22f
						)
				}
		);

		heroBg.setCornerRadius(dp(30));

		hero.setBackground(heroBg);
		hero.setPadding(dp(22), dp(20), dp(22), dp(18));
		hero.setElevation(dp(2));

		LinearLayout.LayoutParams heroLp =
				new LinearLayout.LayoutParams(-1, -2);

		heroLp.topMargin = dp(18);

		root.addView(hero, heroLp);

		View bubble = new View(activity);
		bubble.setBackground(circle(
				ThemeColors.withAlpha(Color.WHITE, 30)
		));

		FrameLayout.LayoutParams bubbleLp =
				new FrameLayout.LayoutParams(dp(160), dp(160));

		bubbleLp.gravity = Gravity.END | Gravity.TOP;
		bubbleLp.topMargin = -dp(60);
		bubbleLp.rightMargin = -dp(30);

		hero.addView(bubble, bubbleLp);

		LinearLayout content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.VERTICAL);

		hero.addView(content);

		TextView label = new TextView(activity);
		label.setText("Épargne totale");
		label.setTextColor(
				ThemeColors.withAlpha(Color.WHITE, 210)
		);
		label.setTextSize(14);

		content.addView(label);

		tvHeroTotal = new TextView(activity);
		tvHeroTotal.setText("0 €");
		tvHeroTotal.setTextColor(Color.WHITE);
		tvHeroTotal.setTextSize(34);
		tvHeroTotal.setTypeface(Typeface.DEFAULT_BOLD);

		LinearLayout.LayoutParams totalLp =
				new LinearLayout.LayoutParams(-1, -2);

		totalLp.topMargin = dp(10);

		content.addView(tvHeroTotal, totalLp);

		tvHeroSubtitle = new TextView(activity);
		tvHeroSubtitle.setText("Chargement...");
		tvHeroSubtitle.setTextColor(
				ThemeColors.withAlpha(Color.WHITE, 190)
		);
		tvHeroSubtitle.setTextSize(13);

		LinearLayout.LayoutParams subLp =
				new LinearLayout.LayoutParams(-1, -2);

		subLp.topMargin = dp(4);

		content.addView(tvHeroSubtitle, subLp);

		View divider = new View(activity);
		divider.setBackgroundColor(
				ThemeColors.withAlpha(Color.WHITE, 35)
		);

		LinearLayout.LayoutParams dividerLp =
				new LinearLayout.LayoutParams(-1, dp(1));

		dividerLp.topMargin = dp(18);
		dividerLp.bottomMargin = dp(16);

		content.addView(divider, dividerLp);

		LinearLayout stats = new LinearLayout(activity);
		stats.setOrientation(LinearLayout.HORIZONTAL);

		content.addView(stats);

		tvHeroGoals = heroStat(stats, "OBJECTIFS");
		tvHeroMonth = heroStat(stats, "CE MOIS");
		tvHeroPercent = heroStat(stats, "CIBLE");
	}

	private TextView heroStat(LinearLayout parent, String label) {

		LinearLayout block = new LinearLayout(activity);
		block.setOrientation(LinearLayout.VERTICAL);

		LinearLayout.LayoutParams blockLp =
				new LinearLayout.LayoutParams(0, -2, 1f);

		parent.addView(block, blockLp);

		TextView tvLabel = new TextView(activity);
		tvLabel.setText(label);
		tvLabel.setTextColor(
				ThemeColors.withAlpha(Color.WHITE, 170)
		);
		tvLabel.setTextSize(10);
		tvLabel.setTypeface(Typeface.DEFAULT_BOLD);

		block.addView(tvLabel);

		TextView value = new TextView(activity);
		value.setText("—");
		value.setTextColor(Color.WHITE);
		value.setTextSize(20);
		value.setTypeface(Typeface.DEFAULT_BOLD);

		LinearLayout.LayoutParams valueLp =
				new LinearLayout.LayoutParams(-1, -2);

		valueLp.topMargin = dp(6);

		block.addView(value, valueLp);

		return value;
	}

	private void buildGoals(LinearLayout root) {

		TextView title = sectionTitle("Objectifs");

		LinearLayout.LayoutParams titleLp =
				new LinearLayout.LayoutParams(-1, -2);

		titleLp.topMargin = dp(24);

		root.addView(title, titleLp);

		goalsContainer = new LinearLayout(activity);
		goalsContainer.setOrientation(LinearLayout.VERTICAL);

		LinearLayout.LayoutParams goalsLp =
				new LinearLayout.LayoutParams(-1, -2);

		goalsLp.topMargin = dp(14);

		root.addView(goalsContainer, goalsLp);
	}

	private void buildInsights(LinearLayout root) {

		TextView title = sectionTitle("Résumé");

		LinearLayout.LayoutParams titleLp =
				new LinearLayout.LayoutParams(-1, -2);

		titleLp.topMargin = dp(24);

		root.addView(title, titleLp);

		insightsContainer = new LinearLayout(activity);
		insightsContainer.setOrientation(LinearLayout.VERTICAL);

		LinearLayout.LayoutParams insightsLp =
				new LinearLayout.LayoutParams(-1, -2);

		insightsLp.topMargin = dp(14);

		root.addView(insightsContainer, insightsLp);
	}

	private TextView sectionTitle(String text) {

		TextView tv = new TextView(activity);
		tv.setText(text);
		tv.setTextColor(ThemeColors.text());
		tv.setTextSize(19);
		tv.setTypeface(Typeface.DEFAULT_BOLD);

		return tv;
	}

	private void load() {

		EpargneRepository.loadAll(activity,
				new EpargneRepository.OnDataLoaded() {

					@Override
					public void onLoaded(EpargneModels.EpargneData data) {
						render(data);
					}

					@Override
					public void onError(String msg) {

						render(new EpargneModels.EpargneData(
								new ArrayList<>(),
								new double[]{0,0,0,0},
								EpargneParser.buildMonthLabels()
						));
					}
				});
	}

	private void render(EpargneModels.EpargneData data) {

		List<EpargneModels.SavingsGoal> goals = data.goals;

		double total =
				EpargneCalculator.totalSaved(goals);

		int completed =
				EpargneCalculator.countCompleted(goals);

		int pct =
				EpargneCalculator.globalPercent(goals);

		tvHeroTotal.setText(Fmt.money(total));

		tvHeroSubtitle.setText(
				goals.size() +
						" objectif" +
						(goals.size() > 1 ? "s" : "") +
						" en cours"
		);

		tvHeroGoals.setText(
				completed + "/" + goals.size()
		);

		tvHeroMonth.setText(
				"+" + Fmt.money(total)
		);

		tvHeroPercent.setText(pct + "%");

		renderGoals(goals);
		renderInsights(data);
	}

	private void renderGoals(List<EpargneModels.SavingsGoal> goals) {

		goalsContainer.removeAllViews();

		if (goals == null || goals.isEmpty()) {

			goalsContainer.addView(emptyCard());
			goalsContainer.addView(addGoalCard());

			return;
		}

		for (EpargneModels.SavingsGoal goal : goals) {
			goalsContainer.addView(goalCard(goal));
		}

		goalsContainer.addView(addGoalCard());
	}

	private View goalCard(EpargneModels.SavingsGoal goal) {

		int accent = parseGoalColor(goal.colorHex);

		int pct =
				EpargneCalculator.progressPercent(goal);

		LinearLayout card = new LinearLayout(activity);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setPadding(dp(16), dp(16), dp(16), dp(14));
		card.setBackground(bg(ThemeColors.card(), 28));
		card.setElevation(dp(2));

		PressAnimations.apply(card);

		card.setOnClickListener(v ->
				showDepositDialog(goal));

		card.setOnLongClickListener(v -> {
			showOptionsMenu(goal);
			return true;
		});

		LinearLayout.LayoutParams cardLp =
				new LinearLayout.LayoutParams(-1, -2);

		cardLp.bottomMargin = dp(12);

		card.setLayoutParams(cardLp);

		LinearLayout top = new LinearLayout(activity);
		top.setOrientation(LinearLayout.HORIZONTAL);
		top.setGravity(Gravity.CENTER_VERTICAL);

		card.addView(top);

		TextView icon = new TextView(activity);
		icon.setText(
				goal.emoji == null ||
						goal.emoji.trim().isEmpty()
						? "💰"
						: goal.emoji
		);

		icon.setTextSize(20);
		icon.setGravity(Gravity.CENTER);
		icon.setBackground(
				circle(
						ThemeColors.blend(
								accent,
								Color.WHITE,
								0.82f
						)
				)
		);

		LinearLayout.LayoutParams iconLp =
				new LinearLayout.LayoutParams(dp(50), dp(50));

		top.addView(icon, iconLp);

		LinearLayout texts = new LinearLayout(activity);
		texts.setOrientation(LinearLayout.VERTICAL);

		LinearLayout.LayoutParams textsLp =
				new LinearLayout.LayoutParams(0, -2, 1f);

		textsLp.leftMargin = dp(12);

		top.addView(texts, textsLp);

		TextView name = new TextView(activity);
		name.setText(goal.name);
		name.setTextColor(ThemeColors.text());
		name.setTextSize(17);
		name.setTypeface(Typeface.DEFAULT_BOLD);

		texts.addView(name);

		TextView target = new TextView(activity);
		target.setText(
				"Objectif : " +
						Fmt.money(goal.target)
		);

		target.setTextColor(ThemeColors.subtext());
		target.setTextSize(13);

		LinearLayout.LayoutParams targetLp =
				new LinearLayout.LayoutParams(-1, -2);

		targetLp.topMargin = dp(4);

		texts.addView(target, targetLp);

		TextView badge = new TextView(activity);
		badge.setText(EpargneCalculator.badgeLabel(goal));
		badge.setTextColor(accent);
		badge.setTextSize(12);
		badge.setTypeface(Typeface.DEFAULT_BOLD);
		badge.setPadding(dp(12), dp(7), dp(12), dp(7));
		badge.setBackground(
				bg(
						ThemeColors.blend(
								accent,
								Color.WHITE,
								0.88f
						),
						100
				)
		);

		top.addView(badge);

		LinearLayout values = new LinearLayout(activity);
		values.setOrientation(LinearLayout.HORIZONTAL);
		values.setGravity(Gravity.CENTER_VERTICAL);

		LinearLayout.LayoutParams valuesLp =
				new LinearLayout.LayoutParams(-1, -2);

		valuesLp.topMargin = dp(18);

		card.addView(values, valuesLp);

		TextView current = new TextView(activity);
		current.setText(Fmt.money(goal.current));
		current.setTextColor(accent);
		current.setTextSize(20);
		current.setTypeface(Typeface.DEFAULT_BOLD);

		LinearLayout.LayoutParams currentLp =
				new LinearLayout.LayoutParams(0, -2, 1f);

		values.addView(current, currentLp);

		TextView right = new TextView(activity);

		right.setText(
				dateLabel(goal)
		);

		right.setTextColor(ThemeColors.subtext());
		right.setTextSize(12);

		values.addView(right);

		card.addView(progressBar(pct, accent));

		// Message motivationnel selon la progression
		if (!goal.isCompleted() && pct >= 75) {
			String motivMsg;
			if (pct >= 95)      motivMsg = "🎯 Presque là ! Plus que quelques euros !";
			else if (pct >= 90) motivMsg = "🔥 Incroyable ! Presque terminé !";
			else if (pct >= 80) motivMsg = "💪 Super avance ! La ligne d'arrivée approche !";
			else                motivMsg = "✨ Plus que 25% à aller, continuez !";
			TextView tvMotiv = new TextView(activity);
			tvMotiv.setText(motivMsg);
			tvMotiv.setTextSize(11.5f);
			tvMotiv.setTextColor(accent);
			tvMotiv.setTypeface(null, Typeface.BOLD);
			LinearLayout.LayoutParams motivLp = new LinearLayout.LayoutParams(-1, -2);
			motivLp.topMargin = dp(6);
			card.addView(tvMotiv, motivLp);
		}

		double monthly = EpargneCalculator.smartMonthly(goal);
		if (monthly > 0 && !goal.isCompleted()) {
			TextView tvMonthly = new TextView(activity);
			tvMonthly.setText("→ " + Fmt.money(monthly) + "/mois recommandé");
			tvMonthly.setTextSize(11f);
			tvMonthly.setTextColor(ThemeColors.withAlpha(accent, 200));
			LinearLayout.LayoutParams monthlyLp = new LinearLayout.LayoutParams(-1, -2);
			monthlyLp.topMargin = dp(6);
			monthlyLp.bottomMargin = dp(4);
			card.addView(tvMonthly, monthlyLp);
		}

		LinearLayout footer = new LinearLayout(activity);
		footer.setOrientation(LinearLayout.HORIZONTAL);
		footer.setGravity(Gravity.CENTER_VERTICAL);

		LinearLayout.LayoutParams footerLp =
				new LinearLayout.LayoutParams(-1, -2);

		footerLp.topMargin = dp(12);

		card.addView(footer, footerLp);

		TextView remain = new TextView(activity);

		remain.setText(
				goal.remaining() <= 0
						? "Objectif atteint"
						: "Reste " +
						Fmt.money(goal.remaining())
		);

		remain.setTextColor(
				goal.remaining() <= 0
						? ThemeColors.success()
						: ThemeColors.subtext()
		);

		remain.setTextSize(12);
		remain.setTypeface(Typeface.DEFAULT_BOLD);

		LinearLayout.LayoutParams remainLp =
				new LinearLayout.LayoutParams(0, -2, 1f);

		footer.addView(remain, remainLp);

		TextView deposit = new TextView(activity);
		deposit.setText("+ Verser");
		deposit.setTextColor(Color.WHITE);
		deposit.setTextSize(12);
		deposit.setTypeface(Typeface.DEFAULT_BOLD);
		deposit.setGravity(Gravity.CENTER);
		deposit.setPadding(dp(14), dp(8), dp(14), dp(8));
		deposit.setBackground(bg(accent, 100));

		PressAnimations.apply(deposit);

		deposit.setOnClickListener(v ->
				showDepositDialog(goal));

		footer.addView(deposit);

		return card;
	}

	private View progressBar(int pct, int accent) {

		FrameLayout wrapper = new FrameLayout(activity);

		LinearLayout.LayoutParams lp =
				new LinearLayout.LayoutParams(-1, dp(7));

		lp.topMargin = dp(12);

		wrapper.setLayoutParams(lp);

		View track = new View(activity);
		track.setBackground(bg(
				ThemeColors.divider(),
				100
		));

		wrapper.addView(track,
				new FrameLayout.LayoutParams(-1, -1));

		View fill = new View(activity);
		fill.setBackground(bg(accent, 100));

		wrapper.addView(fill);

		wrapper.post(() -> {

			int w = wrapper.getWidth();

			FrameLayout.LayoutParams flp =
					new FrameLayout.LayoutParams(
							(int)(w * (pct / 100f)),
							-1
					);

			fill.setLayoutParams(flp);

			fill.setScaleX(0f);

			fill.animate()
					.scaleX(1f)
					.setDuration(700)
					.start();
		});

		return wrapper;
	}

	private View addGoalCard() {

		LinearLayout card = new LinearLayout(activity);
		card.setOrientation(LinearLayout.HORIZONTAL);
		card.setGravity(Gravity.CENTER);
		card.setPadding(dp(20), dp(18), dp(20), dp(18));

		GradientDrawable gd = new GradientDrawable();
		gd.setColor(Color.TRANSPARENT);
		gd.setCornerRadius(dp(26));
		gd.setStroke(
				dp(1),
				ThemeColors.withAlpha(
						ThemeColors.primary(),
						90
				)
		);

		card.setBackground(gd);

		TextView tv = new TextView(activity);
		tv.setText("+ Nouvel objectif");
		tv.setTextColor(ThemeColors.primary());
		tv.setTextSize(15);
		tv.setTypeface(Typeface.DEFAULT_BOLD);

		card.addView(tv);

		PressAnimations.apply(card);

		card.setOnClickListener(v ->
				showAddDialog());

		return card;
	}

	private View emptyCard() {

		LinearLayout card = new LinearLayout(activity);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setPadding(dp(20), dp(20), dp(20), dp(20));
		card.setBackground(bg(ThemeColors.card(), 28));

		LinearLayout.LayoutParams cardLp =
				new LinearLayout.LayoutParams(-1, -2);

		cardLp.bottomMargin = dp(12);

		card.setLayoutParams(cardLp);

		TextView emoji = new TextView(activity);
		emoji.setText("🎯");
		emoji.setTextSize(30);

		card.addView(emoji);

		TextView title = new TextView(activity);
		title.setText("Aucun objectif");
		title.setTextColor(ThemeColors.text());
		title.setTextSize(17);
		title.setTypeface(Typeface.DEFAULT_BOLD);

		LinearLayout.LayoutParams titleLp =
				new LinearLayout.LayoutParams(-1, -2);

		titleLp.topMargin = dp(14);

		card.addView(title, titleLp);

		TextView sub = new TextView(activity);
		sub.setText("Commencez votre épargne.");
		sub.setTextColor(ThemeColors.subtext());
		sub.setTextSize(13);

		LinearLayout.LayoutParams subLp =
				new LinearLayout.LayoutParams(-1, -2);

		subLp.topMargin = dp(6);

		card.addView(sub, subLp);

		return card;
	}

	private void renderInsights(EpargneModels.EpargneData data) {

		insightsContainer.removeAllViews();

		LinearLayout card = new LinearLayout(activity);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setPadding(dp(18), dp(18), dp(18), dp(18));
		card.setBackground(bg(ThemeColors.card(), 28));

		insightsContainer.addView(card);

		TextView title = new TextView(activity);
		title.setText("Historique mensuel");
		title.setTextColor(ThemeColors.text());
		title.setTextSize(17);
		title.setTypeface(Typeface.DEFAULT_BOLD);

		card.addView(title);

		HorizontalScrollView hsv =
				new HorizontalScrollView(activity);

		hsv.setHorizontalScrollBarEnabled(false);

		LinearLayout.LayoutParams hsvLp =
				new LinearLayout.LayoutParams(-1, dp(150));

		hsvLp.topMargin = dp(16);

		card.addView(hsv, hsvLp);

		LinearLayout bars = new LinearLayout(activity);
		bars.setOrientation(LinearLayout.HORIZONTAL);
		bars.setGravity(Gravity.BOTTOM);

		hsv.addView(bars);

		double[] values = data.monthHistory;
		String[] labels = data.monthLabels;

		double max = 1;

		for (double v : values) {
			if (v > max) max = v;
		}

		for (int i = 0; i < values.length; i++) {

			bars.addView(
					monthBar(
							i < labels.length
									? labels[i]
									: "",
							values[i],
							max,
							i == values.length - 1
					)
			);
		}
	}

	private View monthBar(
			String label,
			double value,
			double max,
			boolean active
	) {

		LinearLayout item = new LinearLayout(activity);
		item.setOrientation(LinearLayout.VERTICAL);
		item.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);

		LinearLayout.LayoutParams itemLp =
				new LinearLayout.LayoutParams(dp(62), -1);

		itemLp.rightMargin = dp(8);

		item.setLayoutParams(itemLp);

		FrameLayout graph = new FrameLayout(activity);

		LinearLayout.LayoutParams graphLp =
				new LinearLayout.LayoutParams(
						dp(40),
						dp(100)
				);

		graph.setLayoutParams(graphLp);

		item.addView(graph);

		int h = value <= 0
				? dp(12)
				: (int)Math.max(
				dp(16),
				dp(100) * (value / max)
		);

		View bar = new View(activity);

		bar.setBackground(bg(
				active
						? ThemeColors.primary()
						: ThemeColors.blend(
						ThemeColors.primary(),
						Color.WHITE,
						0.58f
				),
				12
		));

		FrameLayout.LayoutParams barLp =
				new FrameLayout.LayoutParams(
						dp(40),
						h
				);

		barLp.gravity = Gravity.BOTTOM;

		graph.addView(bar, barLp);

		TextView tv = new TextView(activity);
		tv.setText(label);
		tv.setTextColor(ThemeColors.subtext());
		tv.setTextSize(11);
		tv.setGravity(Gravity.CENTER);

		LinearLayout.LayoutParams tvLp =
				new LinearLayout.LayoutParams(-1, -2);

		tvLp.topMargin = dp(10);

		item.addView(tv, tvLp);

		return item;
	}

	private String dateLabel(EpargneModels.SavingsGoal goal) {

		if (goal.hasDate()) {

			return capitalize(
					new SimpleDateFormat(
							"MMM yyyy",
							Locale.FRANCE
					).format(
							new Date(goal.targetDateMs)
					)
			);
		}

		return "Sans date";
	}

	private int parseGoalColor(String hex) {

		try {

			if (hex != null &&
					hex.trim().startsWith("#")) {

				return Color.parseColor(hex.trim());
			}

		} catch (Exception ignored) {}

		return ThemeColors.primary();
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

	private String capitalize(String s) {

		if (s == null || s.length() == 0) {
			return s;
		}

		return s.substring(0, 1)
				.toUpperCase(Locale.FRANCE)
				+ s.substring(1);
	}

	private String currentMonth() {

		String[] months = {
				"JANVIER",
				"FÉVRIER",
				"MARS",
				"AVRIL",
				"MAI",
				"JUIN",
				"JUILLET",
				"AOÛT",
				"SEPTEMBRE",
				"OCTOBRE",
				"NOVEMBRE",
				"DÉCEMBRE"
		};

		Calendar c = Calendar.getInstance();

		return months[c.get(Calendar.MONTH)]
				+ " "
				+ c.get(Calendar.YEAR);
	}

	private void showAddDialog() {
		EpargneDialogs.showAddDialog(activity, this::load);
	}

	private void showDepositDialog(EpargneModels.SavingsGoal goal) {
		EpargneDialogs.showDepositDialog(activity, goal, this::load);
	}

	private void showDeleteDialog(EpargneModels.SavingsGoal goal) {
		EpargneDialogs.showDeleteDialog(activity, goal, this::load);
	}

	private void showOptionsMenu(EpargneModels.SavingsGoal goal) {
		EpargneDialogs.showOptionsMenu(activity, goal, this::load);
	}
}