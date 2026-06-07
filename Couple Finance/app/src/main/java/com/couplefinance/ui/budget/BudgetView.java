package com.couplefinance.ui.budget;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import com.couplefinance.AppToast;
import com.couplefinance.R;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.theme.ThemeDrawable;
import com.couplefinance.core.ui.AppDialog;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.Fmt;
import com.couplefinance.core.ui.UiFactory;
import com.couplefinance.core.ui.animations.PressAnimations;
import com.couplefinance.core.ui.components.PremiumInput;
import com.couplefinance.core.ui.components.PremiumSelector;
import com.couplefinance.core.ui.components.PremiumButton;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class BudgetView {

	private final Activity activity;

	private LinearLayout root;
	private LinearLayout categoriesList;
	private LinearLayout insightsList;

	private TextView tvHeroTop;
	private TextView tvHeroTitle;
	private TextView tvHeroSpent;
	private TextView tvHeroRemaining;
	private TextView tvHeroProgression;

	private TextView tvExceeded;
	private TextView tvWarning;
	private TextView tvSafe;

	public BudgetView(Activity activity) {
		this.activity = activity;
	}

	public View getView() {
		ScrollView scroll = new ScrollView(activity);
		scroll.setFillViewport(true);
		scroll.setClipToPadding(false);
		scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
		scroll.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
		scroll.setBackgroundColor(Color.parseColor("#F5F7FB"));
		scroll.setPadding(0, 0, 0, 0);

		root = new LinearLayout(activity);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(dp(20), dp(16), dp(20), dp(DS.NAV_CLEARANCE));

		scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

		buildHeader();
		buildHero();
		buildStatusTiles();
		buildCategoriesSection();
		buildInsightsSection();

		load();

		return scroll;
	}

	private void buildHeader() {

	TextView title = UiFactory.pageTitle(activity, "Budget");
	title.setTextColor(ThemeColors.text());
	title.setTextSize(32f);
	title.setTypeface(null, Typeface.BOLD);

	LinearLayout.LayoutParams titleLp =
			new LinearLayout.LayoutParams(-1, -2);

	titleLp.bottomMargin = dp(18);

	root.addView(title, titleLp);

	LinearLayout btnRow = UiFactory.horizontal(activity);

	LinearLayout.LayoutParams brp =
			new LinearLayout.LayoutParams(-1, -2);

	brp.bottomMargin = dp(6);

	btnRow.setLayoutParams(brp);

	Button btnEdit = PremiumButton.secondary(activity, "Modifier");

	LinearLayout.LayoutParams editLp =
			new LinearLayout.LayoutParams(0, dp(52), 1f);

	btnRow.addView(btnEdit, editLp);

	Button btnAdd = PremiumButton.primary(activity, "+ Catégorie");

	LinearLayout.LayoutParams addP =
			new LinearLayout.LayoutParams(0, dp(52), 1f);

	addP.leftMargin = dp(12);

	btnRow.addView(btnAdd, addP);

	btnEdit.setTextSize(15);
	btnAdd.setTextSize(15);

	btnEdit.setOnClickListener(v ->
			AppToast.info(activity,
					"Touchez une catégorie pour modifier son budget"));

	btnAdd.setOnClickListener(v -> showCreateCategoryDialog());

	root.addView(btnRow);
}

	private TextView headerPill(String text, boolean primary) {
		TextView v = new TextView(activity);
		v.setText(text);
		v.setGravity(Gravity.CENTER);
		v.setSingleLine(true);
		v.setTextSize(14);
		v.setTypeface(null, Typeface.BOLD);
		v.setPadding(dp(16), 0, dp(16), 0);
		v.setTextColor(primary ? ThemeColors.buttonTextOnPrimary() : ThemeColors.text());
		v.setBackground(primary
				? rounded(ThemeColors.primary(), dp(20), ThemeColors.primary(), 0)
				: rounded(ThemeColors.card(), dp(20), ThemeColors.withAlpha(ThemeColors.border(), 90), 1));
		v.setElevation(primary ? dp(2) : dp(0));
		PressAnimations.applySoft(v);
		return v;
	}

	private void buildHero() {
		LinearLayout hero = new LinearLayout(activity);
		hero.setOrientation(LinearLayout.VERTICAL);
		hero.setGravity(Gravity.CENTER);
		hero.setPadding(dp(24), dp(18), dp(24), dp(18));
		hero.setBackground(heroGradient());
		hero.setElevation(dp(2));

		LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, dp(192));
		hp.topMargin = dp(26);
		root.addView(hero, hp);

		tvHeroTop = new TextView(activity);
		tvHeroTop.setText("Budgété 0€");
		tvHeroTop.setTextColor(Color.argb(225, 255, 255, 255));
		tvHeroTop.setTextSize(15);
		tvHeroTop.setTypeface(null, Typeface.BOLD);
		tvHeroTop.setGravity(Gravity.CENTER);

		hero.addView(tvHeroTop, new LinearLayout.LayoutParams(-1, -2));

		tvHeroTitle = new TextView(activity);
		tvHeroTitle.setText("Aucun budget défini");
		tvHeroTitle.setTextColor(Color.WHITE);
		tvHeroTitle.setTextSize(24);
		tvHeroTitle.setTypeface(null, Typeface.BOLD);
		tvHeroTitle.setGravity(Gravity.CENTER);
		tvHeroTitle.setSingleLine(false);

		LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
		titleLp.topMargin = dp(12);
		hero.addView(tvHeroTitle, titleLp);

		LinearLayout panel = new LinearLayout(activity);
		panel.setOrientation(LinearLayout.HORIZONTAL);
		panel.setGravity(Gravity.CENTER);
		panel.setPadding(dp(10), dp(12), dp(10), dp(12));
		panel.setBackground(
	rounded(
			Color.argb(42, 255, 255, 255),
			dp(24),
			Color.argb(25, 255, 255, 255),
			1
	)
);

		LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(-1, dp(78));
		panelLp.topMargin = dp(18);
		hero.addView(panel, panelLp);

		tvHeroSpent = addHeroMetric(panel, "DÉPENSÉ", "0€");
		addHeroDivider(panel);
		tvHeroRemaining = addHeroMetric(panel, "RESTANT", "0€");
		addHeroDivider(panel);
		tvHeroProgression = addHeroMetric(panel, "PROGRESSION", "0%");
	}

	private TextView addHeroMetric(LinearLayout parent, String label, String value) {
		LinearLayout box = new LinearLayout(activity);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setGravity(Gravity.CENTER);

		TextView l = new TextView(activity);
		l.setText(label);
		l.setTextColor(Color.argb(200, 255, 255, 255));
		l.setTextSize(12);
		l.setTypeface(null, Typeface.NORMAL);
		l.setGravity(Gravity.CENTER);

		TextView v = new TextView(activity);
		v.setText(value);
		v.setTextColor(Color.WHITE);
		v.setTextSize(17);
		v.setTypeface(null, Typeface.BOLD);
		v.setGravity(Gravity.CENTER);

		LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(-1, -2);
		vl.topMargin = dp(6);

		box.addView(l, new LinearLayout.LayoutParams(-1, -2));
		box.addView(v, vl);

		parent.addView(box, new LinearLayout.LayoutParams(0, -1, 1));
		return v;
	}

	private void addHeroDivider(LinearLayout parent) {
		View divider = new View(activity);
		divider.setBackgroundColor(Color.argb(70, 255, 255, 255));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(1), -1);
		lp.topMargin = dp(8);
		lp.bottomMargin = dp(8);
		parent.addView(divider, lp);
	}

	private void buildStatusTiles() {
		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);

		LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(78));
		rp.topMargin = dp(18);
		root.addView(row, rp);

		tvExceeded = statusTile(row, "0", "DÉPASSÉS", ThemeColors.dangerSoft(), ThemeColors.danger(), true);
		tvWarning = statusTile(row, "0", "ATTENTION", ThemeColors.warningSoft(), ThemeColors.warning(), true);
		tvSafe = statusTile(row, "0", "EN ORDRE", ThemeColors.successSoft(), ThemeColors.success(), false);
	}

	private TextView statusTile(LinearLayout parent, String number, String label, int bg, int fg, boolean margin) {
		TextView v = new TextView(activity);
		v.setText(number + "\n" + label);
		v.setGravity(Gravity.CENTER);
		v.setTextColor(fg);
		v.setTextSize(13);
		v.setTypeface(null, Typeface.BOLD);
		v.setBackground(rounded(bg, dp(20), Color.TRANSPARENT, 0));
		v.setAlpha(0.95f);

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
		if (margin) lp.rightMargin = dp(14);

		parent.addView(v, lp);
		return v;
	}

	private void buildCategoriesSection() {
		TextView title = sectionTitle("Catégories");

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.topMargin = dp(22);
		lp.bottomMargin = dp(14);
		root.addView(title, lp);

		categoriesList = new LinearLayout(activity);
		categoriesList.setOrientation(LinearLayout.VERTICAL);
		root.addView(categoriesList, new LinearLayout.LayoutParams(-1, -2));
	}

	private void buildInsightsSection() {
		TextView title = sectionTitle("Insights");

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.topMargin = dp(22);
		lp.bottomMargin = dp(14);
		root.addView(title, lp);

		insightsList = new LinearLayout(activity);
		insightsList.setOrientation(LinearLayout.VERTICAL);
		root.addView(insightsList, new LinearLayout.LayoutParams(-1, -2));
	}

	private TextView sectionTitle(String text) {
		TextView v = new TextView(activity);
		v.setText(text);
		v.setTextColor(ThemeColors.text());
		v.setTextSize(24);
		v.setTypeface(null, Typeface.BOLD);
		return v;
	}

	private void load() {
		BudgetRepository.loadBudgets(new BudgetRepository.Callback() {
			@Override
			public void onResult(List<BudgetModels.CategoryBudget> list) {
				activity.runOnUiThread(() -> render(list));
				// Alertes push budget (best-effort, hors UI thread)
				try {
					com.couplefinance.utils.NotificationHelper.getInstance(activity)
							.checkAndNotifyBudgets(list);
				} catch (Exception ignored) {}
			}

			@Override
			public void onError(String error) {
				activity.runOnUiThread(() -> AppToast.error(activity, "Erreur Budget : " + error));
			}
		});
	}

	private void render(List<BudgetModels.CategoryBudget> list) {
		double totalBudget = BudgetCalculator.totalBudget(list);
		double totalSpent = BudgetCalculator.totalSpent(list);
		double remaining = totalBudget - totalSpent;
		int budgetPercent = totalBudget > 0 ? (int) Math.round((totalSpent / totalBudget) * 100) : 0;

		tvHeroTop.setText("Budgété " + formatMoney(totalBudget));
		tvHeroTitle.setText(totalBudget > 0 ? "Budget mensuel défini" : "Aucun budget défini");
		tvHeroSpent.setText(formatMoney(totalSpent));
		tvHeroRemaining.setText(formatMoney(remaining));
		tvHeroProgression.setText(budgetPercent + "%");

		int exceeded = BudgetCalculator.countExceeded(list);
		int warning = BudgetCalculator.countWarning(list);
		int safe = BudgetCalculator.countSafe(list);

		tvExceeded.setText(exceeded + "\nDÉPASSÉS");
		tvWarning.setText(warning + "\nATTENTION");
		tvSafe.setText(safe + "\nEN ORDRE");
		tvSafe.setAlpha(safe > 0 ? 1f : 0.45f);

		categoriesList.removeAllViews();

		if (list == null || list.isEmpty()) {
			categoriesList.addView(emptyCard("Aucune catégorie", "Crée un budget pour commencer."));
		} else {
			for (BudgetModels.CategoryBudget item : list) {
				categoriesList.addView(categoryCard(item));
			}
		}

		renderInsights(list, totalBudget, totalSpent);
	}

	private View emptyCard(String title, String subtitle) {
		LinearLayout card = budgetCardBase();
		card.setGravity(Gravity.CENTER_VERTICAL);

		TextView t = new TextView(activity);
		t.setText(title);
		t.setTextColor(ThemeColors.text());
		t.setTextSize(18);
		t.setTypeface(null, Typeface.BOLD);

		TextView s = new TextView(activity);
		s.setText(subtitle);
		s.setTextColor(ThemeColors.subtext());
		s.setTextSize(13);

		card.addView(t);
		card.addView(s);

		return card;
	}

	private View categoryCard(BudgetModels.CategoryBudget item) {
		LinearLayout card = budgetCardBase();
		card.setOnClickListener(v -> showBudgetDialog(item));
		card.setOnLongClickListener(v -> {
			showDeleteBudgetDialog(item);
			return true;
		});
		PressAnimations.applySoft(card);

		LinearLayout top = new LinearLayout(activity);
		top.setOrientation(LinearLayout.HORIZONTAL);
		top.setGravity(Gravity.CENTER_VERTICAL);

		TextView icon = new TextView(activity);
		icon.setText(getIcon(item.name));
		icon.setTextSize(22);
		icon.setGravity(Gravity.CENTER);
		icon.setBackground(rounded(ThemeColors.backgroundSecondary(), dp(25), Color.TRANSPARENT, 0));

		top.addView(icon, new LinearLayout.LayoutParams(dp(50), dp(50)));

		TextView name = new TextView(activity);
		name.setText(item.name);
		name.setTextColor(ThemeColors.text());
		name.setTextSize(17);
		name.setTypeface(null, Typeface.BOLD);
		name.setSingleLine(true);

		LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1);
		np.leftMargin = dp(18);
		top.addView(name, np);

		LinearLayout amountGroup = new LinearLayout(activity);
		amountGroup.setOrientation(LinearLayout.VERTICAL);
		amountGroup.setGravity(Gravity.END);

		TextView amount = new TextView(activity);
		amount.setText(formatMoney(item.spent) + " / " + formatMoney(item.budget));
		amount.setTextColor(ThemeColors.text());
		amount.setTextSize(15);
		amount.setTypeface(null, Typeface.BOLD);
		amount.setSingleLine(true);
		amountGroup.addView(amount, new LinearLayout.LayoutParams(-2, -2));

		int trend = item.getTrend();
		if (trend != 0) {
			TextView tvTrend = new TextView(activity);
			tvTrend.setText(trend > 0 ? "↑ hausse" : "↓ baisse");
			tvTrend.setTextSize(10.5f);
			tvTrend.setTextColor(trend > 0 ? ThemeColors.danger() : ThemeColors.success());
			tvTrend.setTypeface(null, Typeface.BOLD);
			LinearLayout.LayoutParams trendLp = new LinearLayout.LayoutParams(-2, -2);
			trendLp.topMargin = dp(2);
			amountGroup.addView(tvTrend, trendLp);
		}

		top.addView(amountGroup, new LinearLayout.LayoutParams(-2, -2));

		card.addView(top, new LinearLayout.LayoutParams(-1, -2));

		ProgressBar progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
		progress.setMax(100);
		progress.setProgress(Math.min(100, item.getPercent()));
		progress.setProgressTintList(android.content.res.ColorStateList.valueOf(getStateColor(item)));
		progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(ThemeColors.withAlpha(ThemeColors.border(), 60)));

		LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(6));
		pp.topMargin = dp(18);
		card.addView(progress, pp);

		return card;
	}

	private LinearLayout budgetCardBase() {
		LinearLayout card = new LinearLayout(activity);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setPadding(dp(18), dp(14), dp(18), dp(14));
		card.setBackground(
	rounded(
			Color.WHITE,
			dp(26),
			Color.argb(18, 0, 0, 0),
			1
	)
);
		card.setElevation(dp(0));

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(92));
		lp.bottomMargin = dp(14);
		card.setLayoutParams(lp);

		return card;
	}

	private void renderInsights(List<BudgetModels.CategoryBudget> list, double totalBudget, double totalSpent) {
		insightsList.removeAllViews();

		BudgetModels.CategoryBudget worst = null;

		if (list != null) {
			for (BudgetModels.CategoryBudget c : list) {
				if (c.isExceeded() && (worst == null || c.getPercent() > worst.getPercent())) {
					worst = c;
				}
			}
		}

		if (worst != null) {
			insightsList.addView(insightCard(
					"⚠",
					worst.name + " dépassé",
					formatMoney(worst.spent) + " dépensés pour " + formatMoney(worst.budget) + " budgétés.",
					ThemeColors.danger()
			));
		} else {
			insightsList.addView(insightCard(
					"✓",
					"Budget maîtrisé",
					totalBudget > 0
							? "Aucun budget dépassé pour le moment."
							: "Même s’il n’y a pas encore de budget défini, vous n’avez fait aucun dépassement.",
					ThemeColors.success()
			));
		}

		if (totalBudget > 0) {
			double remaining = totalBudget - totalSpent;

			insightsList.addView(insightCard(
					remaining >= 0 ? "💰" : "↘",
					remaining >= 0 ? "Reste disponible" : "Budget dépassé",
					remaining >= 0
							? "Il reste " + formatMoney(remaining) + " sur le budget mensuel."
							: "Vous avez dépassé le budget total de " + formatMoney(Math.abs(remaining)) + ".",
					remaining >= 0 ? ThemeColors.success() : ThemeColors.danger()
			));
		}

		// ── Projection fin de mois ──────────────────────────────────────────
		Calendar now    = Calendar.getInstance();
		int day    = now.get(Calendar.DAY_OF_MONTH);
		int maxDay = now.getActualMaximum(Calendar.DAY_OF_MONTH);
		if (day > 0 && totalSpent > 0) {
			double projected   = totalSpent * ((double) maxDay / day);
			int    daysLeft    = maxDay - day;
			String daysStr     = daysLeft + " jour" + (daysLeft > 1 ? "s" : "") + " restant" + (daysLeft > 1 ? "s" : "");

			String projTitle, projBody;
			int    projColor;

			if (totalBudget > 0) {
				double overrun = projected - totalBudget;
				if (overrun > 0) {
					int pct = (int) Math.round((overrun / totalBudget) * 100.0);
					projTitle = "Dépassement probable";
					projBody  = "À ce rythme : " + formatMoney(projected)
							+ " (+"+pct+"%). " + daysStr + ".";
					projColor = ThemeColors.danger();
				} else {
					projTitle = "Projection rassurante";
					projBody  = "Fin de mois estimée : " + formatMoney(projected)
							+ " / " + formatMoney(totalBudget) + ". " + daysStr + ".";
					projColor = ThemeColors.success();
				}
			} else {
				projTitle = "Projection fin de mois";
				projBody  = "À ce rythme : " + formatMoney(projected)
						+ " de dépenses d’ici fin de mois. " + daysStr + ".";
				projColor = ThemeColors.primary();
			}

			insightsList.addView(insightCard("↗", projTitle, projBody, projColor));
		}
	}

	private View insightCard(String iconText, String title, String body, int color) {
		LinearLayout card = new LinearLayout(activity);
		card.setOrientation(LinearLayout.HORIZONTAL);
		card.setPadding(dp(16), dp(16), dp(16), dp(16));
		card.setBackground(rounded(ThemeColors.card(), dp(24), ThemeColors.withAlpha(ThemeColors.border(), 70), 1));
		card.setElevation(dp(1));

		LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
		cp.bottomMargin = dp(14);
		card.setLayoutParams(cp);

		TextView icon = new TextView(activity);
		icon.setText(iconText);
		icon.setTextSize(22);
		icon.setGravity(Gravity.CENTER);
		icon.setTextColor(color);
		icon.setBackground(rounded(ThemeColors.withAlpha(color, 28), dp(22), ThemeColors.withAlpha(color, 55), 1));

		card.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

		LinearLayout texts = new LinearLayout(activity);
		texts.setOrientation(LinearLayout.VERTICAL);

		TextView t = new TextView(activity);
		t.setText(title);
		t.setTextColor(ThemeColors.text());
		t.setTextSize(18);
		t.setTypeface(null, Typeface.BOLD);

		TextView b = new TextView(activity);
		b.setText(body);
		b.setTextColor(ThemeColors.subtext());
		b.setTextSize(14);
		b.setLineSpacing(dp(2), 1f);

		texts.addView(t);
		texts.addView(b);

		LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1);
		tp.leftMargin = dp(14);
		card.addView(texts, tp);

		return card;
	}

	private void showDeleteBudgetDialog(BudgetModels.CategoryBudget item) {
		LinearLayout info = AppDialog.infoCard(activity);
		info.setBackground(ThemeDrawable.tintDanger(activity, DS.R_MD));

		TextView tvInfo = UiFactory.body(activity, item.name + "\n" + formatMoney(item.budget)
				+ " budgétés · " + formatMoney(item.spent) + " dépensés ce mois");
		tvInfo.setTypeface(null, Typeface.BOLD);
		tvInfo.setTextColor(ThemeColors.text());
		info.addView(tvInfo);

		new AppDialog.Builder(activity)
				.icon("🗑️")
				.title("Supprimer ce budget")
				.subtitle("Le budget mensuel sera supprimé, mais la catégorie et les transactions resteront conservées.")
				.content(info)
				.primaryBtn("SUPPRIMER", () -> {
					BudgetRepository.deleteBudget(item.name, new BudgetRepository.SaveCallback() {
						@Override
						public void onSuccess() {
							AppToast.success(activity, "Budget supprimé");
							load();
						}

						@Override
						public void onError(String error) {
							AppToast.error(activity, "Erreur : " + error);
						}
					});
				})
				.show();
	}

	private void showBudgetDialog(BudgetModels.CategoryBudget item) {
		LinearLayout content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.VERTICAL);

		LinearLayout row = UiFactory.horizontal(activity);

		LinearLayout col1 = AppDialog.fieldColumn(activity, "CATÉGORIE");
		TextView category = AppDialog.readonlyField(activity, item.name);
		col1.addView(category);

		LinearLayout col2 = AppDialog.fieldColumn(activity, "BUDGET MENSUEL €");
		EditText amount = PremiumInput.numeric(activity, "Ex : 400");
		amount.setText(item.budget > 0 ? cleanAmount(item.budget) : "");
		amount.setSelection(amount.getText().length());
		col2.addView(amount);

		row.addView(col1, new LinearLayout.LayoutParams(0, -2, 1));

		LinearLayout.LayoutParams c2p = new LinearLayout.LayoutParams(0, -2, 1);
		c2p.leftMargin = dp(16);
		row.addView(col2, c2p);

		content.addView(row);

		TextView help = UiFactory.bodyMuted(activity, "Dépensé ce mois : " + formatMoney(item.spent) + " · "
				+ (item.budget > 0 ? item.getPercent() + "% consommé" : "aucun budget défini"));

		LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
		hp.topMargin = dp(18);
		content.addView(help, hp);

		new AppDialog.Builder(activity)
				.icon("✎")
				.title("Modifier le budget")
				.subtitle("Ajuste le plafond mensuel de cette catégorie.")
				.content(content)
				.primaryBtn("ENREGISTRER", () -> saveBudget(item.name, amount.getText().toString()))
				.show();
	}

	private void showCreateCategoryDialog() {
		BudgetRepository.loadExpenseCategories(new BudgetRepository.CategoryNamesCallback() {
			@Override
			public void onResult(List<String> categories) {
				activity.runOnUiThread(() -> showCreateCategoryDialogWithCategories(categories));
			}

			@Override
			public void onError(String error) {
				activity.runOnUiThread(() -> showCreateCategoryDialogWithCategories(new java.util.ArrayList<>()));
			}
		});
	}

	private void showCreateCategoryDialogWithCategories(List<String> categories) {
		LinearLayout content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.VERTICAL);

		LinearLayout row = UiFactory.horizontal(activity);

		LinearLayout col1 = AppDialog.fieldColumn(activity, "CATÉGORIE EXISTANTE");

		java.util.ArrayList<String> spinnerItems = new java.util.ArrayList<>();
		spinnerItems.add("Créer une nouvelle catégorie");
		spinnerItems.addAll(categories);

		final int[] selectedIdx = {0};
		AutoCompleteTextView spinner = PremiumSelector.selector(
				activity,
				spinnerItems.toArray(new String[0]),
				selectedIdx
		);
		col1.addView(spinner);

		LinearLayout col2 = AppDialog.fieldColumn(activity, "NOUVELLE CATÉGORIE");
		EditText name = PremiumInput.normal(activity, "Ex : Restaurants");
		name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
		col2.addView(name);

		row.addView(col1, new LinearLayout.LayoutParams(0, -2, 1));

		LinearLayout.LayoutParams c2p = new LinearLayout.LayoutParams(0, -2, 1);
		c2p.leftMargin = dp(16);
		row.addView(col2, c2p);

		content.addView(row);

		LinearLayout amountCol = AppDialog.fieldColumn(activity, "BUDGET MENSUEL €");
		EditText amount = PremiumInput.numeric(activity, "Ex : 80");
		amountCol.addView(amount);

		LinearLayout.LayoutParams arP = new LinearLayout.LayoutParams(-1, -2);
		arP.topMargin = dp(20);
		content.addView(amountCol, arP);

		new AppDialog.Builder(activity)
				.icon("+")
				.title("Budget de catégorie")
				.subtitle("Choisis une catégorie existante ou crée-en une nouvelle.")
				.content(content)
				.primaryBtn("ENREGISTRER", () -> {
					String typedName = name.getText().toString().trim();
					String selected = selectedIdx[0] >= 0 && selectedIdx[0] < spinnerItems.size()
							? spinnerItems.get(selectedIdx[0])
							: "";

					String finalName;

					if (!typedName.isEmpty()) {
						finalName = typedName;
					} else if (!selected.isEmpty() && !selected.equals("Créer une nouvelle catégorie")) {
						finalName = selected;
					} else {
						AppToast.error(activity, "Choisis ou crée une catégorie");
						return;
					}

					saveBudget(finalName, amount.getText().toString());
				})
				.show();
	}

	private void saveBudget(String name, String rawAmount) {
		try {
			if (name == null || name.trim().isEmpty()) {
				AppToast.error(activity, "Nom invalide");
				return;
			}

			if (rawAmount == null || rawAmount.trim().isEmpty()) {
				AppToast.error(activity, "Montant manquant");
				return;
			}

			double value = Double.parseDouble(rawAmount.trim().replace(",", "."));

			BudgetRepository.saveBudget(name.trim(), value, new BudgetRepository.SaveCallback() {
				@Override
				public void onSuccess() {
					AppToast.success(activity, "Budget enregistré");
					load();
				}

				@Override
				public void onError(String error) {
					AppToast.error(activity, "Erreur : " + error);
				}
			});
		} catch (Exception e) {
			AppToast.error(activity, "Montant invalide");
		}
	}

	private int getStateColor(BudgetModels.CategoryBudget c) {
		if (c.isExceeded()) return ThemeColors.danger();
		if (c.isWarning()) return ThemeColors.warning();
		return ThemeColors.primary();
	}

	private GradientDrawable heroGradient() {
		int primary = ThemeColors.primary();
		int dark = ThemeColors.primaryDark();

		GradientDrawable g = new GradientDrawable(
				GradientDrawable.Orientation.TL_BR,
				new int[]{
						ThemeColors.withAlpha(primary, 235),
						ThemeColors.withAlpha(dark, 230)
				}
		);
		g.setCornerRadius(dp(30));
		return g;
	}

	private GradientDrawable rounded(int color, int radiusPx, int strokeColor, int strokeWidthDp) {
		GradientDrawable d = new GradientDrawable();
		d.setShape(GradientDrawable.RECTANGLE);
		d.setColor(color);
		d.setCornerRadius(radiusPx);

		if (strokeWidthDp > 0) {
			d.setStroke(dp(strokeWidthDp), strokeColor);
		}

		return d;
	}

	private String cleanAmount(double value) {
		if (Math.abs(value - Math.round(value)) < 0.001) {
			return String.valueOf((int) Math.round(value));
		}
		return String.valueOf(value).replace(".", ",");
	}

	private String formatMoney(double value) {
		return Fmt.money(value);
	}

	private String getIcon(String name) {
		String n = name == null ? "" : name.toLowerCase(Locale.FRANCE);

		if (n.contains("logement") || n.contains("loyer")) return "⌂";
		if (n.contains("course") || n.contains("aliment")) return "🛒";
		if (n.contains("transport") || n.contains("essence")) return "▣";
		if (n.contains("restaurant")) return "🍽";
		if (n.contains("loisir")) return "★";
		if (n.contains("abonnement")) return "▣";
		if (n.contains("santé") || n.contains("sante")) return "+";
		if (n.contains("assurance")) return "◉";
		return "•";
	}

	private int dp(int value) {
		return DS.dp(activity, value);
	}
}