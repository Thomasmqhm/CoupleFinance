package com.couplefinance.ui.home;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import android.text.TextUtils;

import com.couplefinance.core.ui.DS;
import com.couplefinance.data.BankAutoSyncManager;
import com.couplefinance.core.ui.Fmt;
import com.couplefinance.AuthManager;
import com.couplefinance.data.FirestoreManager;
import com.couplefinance.data.FinancialInsightManager;
import com.couplefinance.data.RecurringChargeManager;
import com.couplefinance.R;
import com.couplefinance.UserSession;
import com.couplefinance.data.BalanceManager;
import com.couplefinance.data.HouseholdManager;
import com.couplefinance.data.TransactionManager;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.ui.home.HomeCalculator;
import com.couplefinance.ui.home.HomeMemberCard;
import com.couplefinance.ui.home.HomeMemberSection;
import com.couplefinance.ui.DashboardActivity;
import com.couplefinance.widget.SoldeWidget;
import com.couplefinance.data.CreditManager;
import com.couplefinance.ui.credits.CreditsModels;
import com.couplefinance.ui.credits.CreditsParser;
import com.couplefinance.utils.ActivityLogger;

import java.text.SimpleDateFormat;
import java.util.*;

public class HomeView {

	private final Activity activity;
	private final Handler refreshHandler = new Handler(Looper.getMainLooper());
	private boolean isActive = true;
	private int loadRetryCount = 0;

	private TextView tvBalance, tvExpenses, tvSavings, tvIncomeDetail;
	private TextView tvIncomeHealth, tvExpensesHealth, tvProjectionHealth;
	private TextView tvTransactionCount, tvFixedDetail;
	private TextView tvEndBalance, tvEndBalanceDetail, tvEndBalanceDetail2;
	private TextView tvNotification, tvSyncStatus, tvSyncDot, tvCalMonth;
	private TextView tvGreeting, tvGreetingEmoji;
	private TextView tvMonthProgressLabel, tvMonthProgressPct, tvMonthProgressDetail;
	private View viewMonthProgressFill;
	private View viewHealthProgress;

	// ── ✨ Étape 2 : comparaisons mois précédent + top catégories ──
	private TextView tvIncomeCompare, tvExpensesCompare, tvSavingsCompare;
	private LinearLayout topCategoriesContainer;
	private TextView tvTopCategoriesEmpty, tvTopCategoriesTotal;

	// ── Dashboard dynamique : widgets activables/désactivables ──
	private View heroCard;
	private View widgetMonthProgress, widgetStatsCards, widgetPersons, widgetTopCategories, widgetBottomLine;
	private LinearLayout dashboardContent, dynamicWidgetsContainer;
	private TextView btnDashboardWidgets;
	private HomeNotifications homeNotifications;
	private LinearLayout memberSectionContainer;
	private HomeMemberSection memberSection;
	private HomeOrganizer homeOrganizer;
	private FrameLayout scoreGaugeContainer;
	private HomeWidgets.GaugeRefs gaugeRefs;
	private SharedPreferences dashboardPrefs;

	private static final String PREF_DASHBOARD = "dashboard_widgets";
	private static final String W_MONTH_PROGRESS = "widget_month_progress";
	private static final String W_STATS = "widget_stats";
	private static final String W_PERSONS = "widget_persons";
	private static final String W_TOP_CATEGORIES = "widget_top_categories";
	private static final String W_BOTTOM_LINE = "widget_bottom_line";
	private static final String W_QUICK_SUMMARY = "widget_quick_summary";
	private static final String W_BUDGET_HEALTH = "widget_budget_health";
	private static final String W_DAILY_BURN = "widget_daily_burn";
	private static final String W_MONTH_FORECAST = "widget_month_forecast";
	private static final String W_BIGGEST_EXPENSE = "widget_biggest_expense";
	private static final String W_SAVINGS_RATE = "widget_savings_rate";
	private static final String W_CATEGORY_COUNT = "widget_category_count";
	private static final String W_INCOME_SOURCES = "widget_income_sources";
	private static final String W_ACTIVITY = "widget_activity";
	private static final String W_DYNAMIC_LIBRARY = "widget_dynamic_library";
	private static final String PREF_ORDER_SECTIONS = "dashboard_order_sections";
	private static final String PREF_ORDER_DYNAMIC = "dashboard_order_dynamic";
	private static final String PREF_REFERENCE_STYLE_VERSION = "reference_style_version_v2";

	// Cache des emojis par catégorie (chargé depuis CategoryManager)
	private Map<String, String> categoryEmojis = new HashMap<>();
	private Map<String, Double> categoryBudgets = new HashMap<>();
	private GridLayout calendarGrid;
	private LinearLayout personCards, categoryBars, personBars;

	private Calendar calendarMonth = Calendar.getInstance();
	private List<String[]> cachedTransactions = new ArrayList<>();

	// Tous les membres du foyer (pour afficher les cartes même sans transaction)
	private List<String> allHouseholdMembers = new ArrayList<>();

	// userId → nom affiché (pour lier les soldes aux cartes)
	private Map<String, String> userIdToName = new HashMap<>();

	// nom (lowercase) → solde début de mois (pour afficher le solde du partenaire)
	private Map<String, Double> memberBalances = new HashMap<>();

	// Solde personnel de l'utilisateur connecté (pour sa carte)
	private double monthlyStartBalance = 0;
	private boolean monthlyStartBalanceDefined = false;
	private long monthlyStartBalanceDate = 0;
	private long commonBalanceAnchorDate = 0;

	// Somme des soldes de tous les membres (pour le Solde commun)
	private double commonStartBalance = 0;

	private double lastAnimatedBalance = 0;
	private boolean hasAnimatedOnce = false;

	private double overdraftLimit = 0;
	private boolean overdraftDefined = false;

	// Snapshot des métriques du cycle — utilisé par shareMonthSummary()
	private double snapshotIncome   = 0;
	private double snapshotExpenses = 0;
	private double snapshotBalance  = 0;

	public HomeView(Activity activity) {
		this.activity = activity;
		BalanceManager.getInstance().init(activity);
	}

	private String getMyName() {
		// 1) Prénom sauvegardé manuellement dans le profil
		try {
			String saved = activity.getSharedPreferences("couplefinance_profile", Activity.MODE_PRIVATE)
					.getString("display_name", "");
			if (saved != null && !saved.trim().isEmpty()) return saved.trim();
		} catch (Exception ignored) {}

		// 2) Map userId→nom chargée depuis les membres du foyer
		try {
			String myUid = AuthManager.getInstance().getUserId();
			if (myUid != null && !myUid.isEmpty() && userIdToName != null) {
				String n = userIdToName.get(myUid);
				if (n != null && !n.trim().isEmpty()) {
					activity.getSharedPreferences("couplefinance_profile", Activity.MODE_PRIVATE)
							.edit().putString("display_name", n.trim()).apply();
					return n.trim();
				}
			}
		} catch (Exception ignored) {}

		// 3) UserSession — seulement si pas un email/identifiant/fallback
		try {
			String name = UserSession.getInstance().getName();
			if (name != null && !name.trim().isEmpty()
					&& !name.equalsIgnoreCase("Moi")
					&& !name.contains("@") && !name.contains(".")) return name.trim();
		} catch (Exception ignored) {}

		// 4) AuthManager — seulement si pas un email/identifiant/fallback
		try {
			String dn = AuthManager.getInstance().getDisplayName();
			if (dn != null && !dn.trim().isEmpty()
					&& !dn.equalsIgnoreCase("Moi")
					&& !dn.contains("@") && !dn.contains(".")) return dn.trim();
		} catch (Exception ignored) {}

		return "";
	}

	public View getView() {
		View view = LayoutInflater.from(activity).inflate(R.layout.view_home, null);
		dashboardPrefs = activity.getSharedPreferences(PREF_DASHBOARD, Activity.MODE_PRIVATE);
		homeOrganizer = new HomeOrganizer(activity, dashboardPrefs, PREF_ORDER_SECTIONS, PREF_ORDER_DYNAMIC,
				new HomeOrganizer.Callbacks() {
					public void onApplyWidgetVisibilityAnimated() {
						applyWidgetVisibilityAnimated();
					}

					public void onApplyDashboardSectionOrderAnimated() {
						applyDashboardSectionOrderAnimated();
					}

					public void onApplyDashboardSectionOrder() {
						applyDashboardSectionOrder();
					}

					public void onReloadData() {
						loadData();
					}

					public boolean isWidgetEnabled(String key) {
						return HomeView.this.isWidgetEnabled(key);
					}

					public boolean isMainSectionKey(String key) {
						return HomeView.this.isMainSectionKey(key);
					}
				});
		ensureReferenceStyleDefaults();

		tvBalance = view.findViewById(R.id.tvBalance);
		tvExpenses = view.findViewById(R.id.tvExpenses);
		tvSavings = view.findViewById(R.id.tvSavings);
		tvIncomeDetail = view.findViewById(R.id.tvIncomeDetail);
		tvIncomeHealth = view.findViewById(R.id.tvIncomeDetailOld);
		tvExpensesHealth = view.findViewById(R.id.tvExpensesOld);
		tvProjectionHealth = view.findViewById(R.id.tvSavingsOld);
		tvTransactionCount = view.findViewById(R.id.tvTransactionCount);
		tvFixedDetail = view.findViewById(R.id.tvFixedDetail);
		tvEndBalance = view.findViewById(R.id.tvEndBalance);
		tvEndBalanceDetail = view.findViewById(R.id.tvEndBalanceDetail);
		tvEndBalanceDetail2 = view.findViewById(R.id.tvEndBalanceDetail2);
		tvNotification = view.findViewById(R.id.tvNotification);
		tvSyncStatus = view.findViewById(R.id.tvSyncStatus);
		tvSyncDot = view.findViewById(R.id.tvSyncDot);
		tvCalMonth = view.findViewById(R.id.tvCalMonth);
		calendarGrid = view.findViewById(R.id.calendarGrid);
		personCards = view.findViewById(R.id.personCards);
		categoryBars = view.findViewById(R.id.categoryBars);
		personBars = view.findViewById(R.id.personBars);
		scoreGaugeContainer = view.findViewById(R.id.scoreGaugeContainer);
		setupFinancialGauge();
		// ── ✨ Étape 1 : Greeting + Barre progression du mois ──
		tvGreeting = view.findViewById(R.id.tvGreeting);
		tvGreetingEmoji = view.findViewById(R.id.tvGreetingEmoji);
		tvMonthProgressLabel = view.findViewById(R.id.tvMonthProgressLabel);
		tvMonthProgressPct = view.findViewById(R.id.tvMonthProgressPct);
		tvMonthProgressDetail = view.findViewById(R.id.tvMonthProgressDetail);
		viewMonthProgressFill = view.findViewById(R.id.viewMonthProgressFill);
		viewHealthProgress = view.findViewById(R.id.viewHealthProgress);
		// ── ✨ Étape 2 : comparaisons + top catégories ──
		tvIncomeCompare = view.findViewById(R.id.tvIncomeCompare);
		tvExpensesCompare = view.findViewById(R.id.tvExpensesCompare);
		tvSavingsCompare = view.findViewById(R.id.tvSavingsCompare);
		topCategoriesContainer = view.findViewById(R.id.topCategoriesContainer);
		tvTopCategoriesEmpty = view.findViewById(R.id.tvTopCategoriesEmpty);
		tvTopCategoriesTotal = view.findViewById(R.id.tvTopCategoriesTotal);

		dashboardContent = view.findViewById(R.id.dashboardContent);
		widgetMonthProgress = view.findViewById(R.id.widgetMonthProgress);
		widgetStatsCards = view.findViewById(R.id.widgetStatsCards);
		widgetPersons = personCards;
		widgetTopCategories = view.findViewById(R.id.widgetTopCategories);
		widgetBottomLine = view.findViewById(R.id.widgetBottomLine);
		dynamicWidgetsContainer = view.findViewById(R.id.dynamicWidgetsContainer);
		heroCard = view.findViewById(R.id.heroCard);
		btnDashboardWidgets = view.findViewById(R.id.btnDashboardWidgets);
		if (btnDashboardWidgets != null) {
			btnDashboardWidgets.setOnClickListener(v -> showWidgetPicker());
			btnDashboardWidgets.setOnLongClickListener(v -> {
				showDashboardOrganizer();
				return true;
			});
			btnDashboardWidgets.setVisibility(View.GONE);
		}

		setupDashboardActions(view);
		installPremiumDashboardExperience(view);
		applyReferenceDashboardTypography();
		applyWidgetVisibility();
		applyDashboardSectionOrder();
		animateVisibleChildren(dashboardContent);

		loadCategoryEmojis();

		updateGreeting();
		updateMonthProgress();

		String myName = getMyName();
		if (!myName.isEmpty() && tvNotification != null) {
			tvNotification.setText(myName + ".");
		}

		if (tvSyncStatus != null) {
			SimpleDateFormat fmt = new SimpleDateFormat("MMMM yyyy", Locale.FRENCH);
			String mois = fmt.format(new Date());
			tvSyncStatus.setText(mois.substring(0, 1).toUpperCase() + mois.substring(1));
		}

		View btnCalPrev = view.findViewById(R.id.btnCalPrev);
		if (btnCalPrev != null) {
			btnCalPrev.setOnClickListener(v -> {
				calendarMonth.add(Calendar.MONTH, -1);
				renderCalendar();
			});
		}

		View btnCalNext = view.findViewById(R.id.btnCalNext);
		if (btnCalNext != null) {
			btnCalNext.setOnClickListener(v -> {
				calendarMonth.add(Calendar.MONTH, 1);
				renderCalendar();
			});
		}

		setSyncStatus("connecting");
		resetDashboardBeforeLoad();
		loadData();
		startAutoRefresh();
		renderCalendar();

		final BankAutoSyncManager.OnBalancesRefreshed balanceRefreshListener = () ->
				activity.runOnUiThread(() -> { if (isActive) loadData(); });
		BankAutoSyncManager.addBalanceListener(balanceRefreshListener);

		view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
			public void onViewAttachedToWindow(View v) {
				isActive = true;
				loadData();
			}

			public void onViewDetachedFromWindow(View v) {
				isActive = false;
				refreshHandler.removeCallbacksAndMessages(null);
				BankAutoSyncManager.removeBalanceListener(balanceRefreshListener);
			}
		});

		return view;
	}

	private void setupDashboardActions(View view) {
		// Rend le dashboard vraiment utilisable : chaque bloc mène vers l'onglet logique.
		View seeAll = view.findViewById(R.id.btnSeeAllTransactions);
		if (seeAll != null) {
			seeAll.setOnClickListener(v -> openDashboardTab(R.id.btnTransactions));
		}

		if (widgetBottomLine != null) {
			widgetBottomLine.setOnClickListener(v -> openDashboardTab(R.id.btnTransactions));
		}

		if (widgetMonthProgress != null) {
			widgetMonthProgress.setOnClickListener(v -> openDashboardTab(R.id.btnAgenda));
		}

		if (personCards != null) {
			personCards.setOnClickListener(v -> openDashboardTab(R.id.btnVuePerso));
		}

		if (widgetTopCategories != null) {
			widgetTopCategories.setOnClickListener(v -> openDashboardTab(R.id.btnVuePerso));
		}

		if (widgetStatsCards != null) {
			widgetStatsCards.setOnClickListener(v -> openDashboardTab(R.id.btnBudget));
		}
	}

	private void openDashboardTab(int buttonId) {
		try {
			if (activity instanceof DashboardActivity) {
				((DashboardActivity) activity).navigateTo(buttonId);
			} else {
				View nav = activity.findViewById(buttonId);
				if (nav != null)
					nav.performClick();
			}
		} catch (Exception ignored) {
		}
	}

	private void ensureReferenceStyleDefaults() {
		if (dashboardPrefs == null)
			return;
		if (dashboardPrefs.getBoolean(PREF_REFERENCE_STYLE_VERSION, false))
			return;

		SharedPreferences.Editor e = dashboardPrefs.edit();
		String[] keys = getAllWidgetKeys();
		for (String key : keys)
			e.putBoolean(key, true);
		e.remove(PREF_ORDER_SECTIONS);
		e.remove(PREF_ORDER_DYNAMIC);
		e.putBoolean(PREF_REFERENCE_STYLE_VERSION, true);
		e.apply();
	}

	private void applyReferenceDashboardTypography() {
		if (tvBalance != null) {
			HomeDashboardStyle.premiumBalance(tvBalance);
		}

		if (tvGreeting != null) {
			HomeDashboardStyle.premiumTitle(tvGreeting);
		}

		if (tvGreetingEmoji != null) {
			tvGreetingEmoji.setTextSize(22f);
			tvGreetingEmoji.setIncludeFontPadding(false);
		}

		if (tvNotification != null) {
			tvNotification.setVisibility(View.GONE);
		}

		if (tvSyncStatus != null) {
			HomeDashboardStyle.premiumSmallPrimary(tvSyncStatus);
		}

		if (tvEndBalanceDetail != null) {
			tvEndBalanceDetail.setTextColor(ThemeColors.onPrimarySecondary());
			tvEndBalanceDetail.setTextSize(DS.TEXT_CAPTION);
			tvEndBalanceDetail.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
		}

		if (tvEndBalanceDetail2 != null) {
			tvEndBalanceDetail2.setTextColor(ThemeColors.onPrimarySecondary());
			tvEndBalanceDetail2.setTextSize(DS.TEXT_CAPTION);
		}
	}

	private void styleHeaderAvatar() {
		if (tvGreetingEmoji == null)
			return;

		GradientDrawable bg = new GradientDrawable();
		bg.setShape(GradientDrawable.OVAL);
		bg.setColor(ThemeColors.surfaceFloating());
		bg.setStroke(DS.dp(activity, 1), ThemeColors.borderSoft());

		tvGreetingEmoji.setBackground(bg);
		tvGreetingEmoji.setElevation(DS.dp(activity, 5));
		tvGreetingEmoji.setGravity(Gravity.CENTER);
	}

	private void installPremiumDashboardExperience(View view) {
		if (view == null)
			return;

		view.setBackgroundColor(ThemeColors.background());
		installPremiumNotificationsButton();
		styleHeaderAvatar();

		if (heroCard != null) {

			heroCard.setBackground(HomeDashboardStyle.heroGradient(activity));
			heroCard.setElevation(DS.dp(activity, 18));

			heroCard.setPadding(DS.dp(activity, DS.CARD_PADDING_LARGE), DS.dp(activity, DS.CARD_PADDING_LARGE),
					DS.dp(activity, DS.CARD_PADDING_LARGE), DS.dp(activity, DS.CARD_PADDING_LARGE));

			ViewGroup.LayoutParams heroLp = heroCard.getLayoutParams();

			if (heroLp != null) {
				heroLp.height = DS.dp(activity, 240);
				heroCard.setLayoutParams(heroLp);
			}

			heroCard.setOnLongClickListener(v -> {
				shareMonthSummary();
				return true;
			});
		}

		if (dashboardContent != null) {

			dashboardContent.setClipToPadding(false);

			dashboardContent.setPadding(DS.dp(activity, DS.SCREEN_HORIZONTAL), DS.dp(activity, DS.SCREEN_TOP),
					DS.dp(activity, DS.SCREEN_HORIZONTAL), DS.dp(activity, DS.SCREEN_BOTTOM));

			dashboardContent.setShowDividers(LinearLayout.SHOW_DIVIDER_NONE);

			memberSectionContainer = new LinearLayout(activity);
			memberSectionContainer.setOrientation(LinearLayout.VERTICAL);

			LinearLayout.LayoutParams mslp = new LinearLayout.LayoutParams(-1, -2);

			mslp.topMargin = DS.dp(activity, DS.SPACE_16);
			mslp.bottomMargin = DS.dp(activity, DS.SPACE_8);

			memberSectionContainer.setLayoutParams(mslp);

			// Sur tablette, le layout deux colonnes fournit un point d'ancrage
			// dédié (memberSectionAnchor) dans la colonne droite : la section
			// "Comptes liés" y est injectée pour apparaître en priorité à
			// droite. Sur téléphone, cet ancrage n'existe pas et la section
			// est insérée dans dashboardContent comme avant (index 2).
			View memberAnchor = view.findViewById(R.id.memberSectionAnchor);

			if (memberAnchor instanceof LinearLayout) {
				((LinearLayout) memberAnchor).addView(memberSectionContainer);
			} else {
				int memberInsertIndex = Math.min(2, dashboardContent.getChildCount());
				dashboardContent.addView(memberSectionContainer, memberInsertIndex);
			}
		}

		if (btnDashboardWidgets != null) {
			btnDashboardWidgets.setText("Widgets");
			btnDashboardWidgets.setTextColor(Color.WHITE);
			btnDashboardWidgets.setTextSize(DS.TEXT_CAPTION);
			btnDashboardWidgets.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
			btnDashboardWidgets.setGravity(Gravity.CENTER);
			btnDashboardWidgets.setPadding(DS.dp(activity, DS.SPACE_16), DS.dp(activity, DS.SPACE_8),
					DS.dp(activity, DS.SPACE_16), DS.dp(activity, DS.SPACE_8));
			btnDashboardWidgets.setBackground(buildPill(ThemeColors.primary(), ThemeColors.withAlpha(Color.WHITE, 60)));
			btnDashboardWidgets.setElevation(DS.dp(activity, 8));
		}

		applySoftCard(widgetMonthProgress);
		applySoftCard(widgetStatsCards);
		applySoftCard(widgetTopCategories);
		applySoftCard(widgetBottomLine);
		applySoftCard(personCards);

		if (widgetBottomLine != null) {
			widgetBottomLine.setPadding(DS.dp(activity, DS.CARD_PADDING), DS.dp(activity, DS.CARD_PADDING),
					DS.dp(activity, DS.CARD_PADDING), DS.dp(activity, DS.CARD_PADDING));
		}
		installFloatingSpacing();
	}

	private void installFloatingSpacing() {

		if (dashboardContent == null)
			return;

		for (int i = 0; i < dashboardContent.getChildCount(); i++) {

			View child = dashboardContent.getChildAt(i);

			if (child == null)
				continue;

			ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) child.getLayoutParams();

			if (lp == null)
				continue;

			lp.bottomMargin += DS.dp(activity, 2);

			child.setTranslationZ(DS.dp(activity, 1));

			child.setLayoutParams(lp);
		}
	}

	private void applySoftCard(View card) {
		if (card == null)
			return;

		GradientDrawable bg = new GradientDrawable();
		bg.setColor(ThemeColors.surfaceFloating());
		bg.setCornerRadius(DS.dp(activity, 26));
		bg.setStroke(DS.dp(activity, 1), ThemeColors.borderSoft());
		card.setBackground(bg);
		card.setElevation(DS.dp(activity, 6));
	}

	private void updateRealNotifications(double income, double expenses, double balance, double monthMovement,
			Map<String, Double> categoryTotals, long latestUnexpectedExpenseDate,
			List<FinancialInsightManager.Insight> financialInsights, Map<String, Double> memberCurrentBalances,
			double projectedEndBalance, int upcomingChargesCount) {
		ArrayList<HomeNotificationItem> list = new ArrayList<>();

		// ── Synchronisation bancaire automatique ─────────────────
		if (BankAutoSyncManager.isEnabled(activity)) {
			String syncSummary = BankAutoSyncManager.getLastSummary(activity);
			if (syncSummary != null && !syncSummary.isEmpty()) {
				list.add(new HomeNotificationItem("🔄", "Synchronisation bancaire",
						syncSummary, Color.parseColor("#4076A8"),
						Color.parseColor("#EEF6FF"), Color.parseColor("#CFE3F5")));
			} else {
				list.add(new HomeNotificationItem("🔄", "Synchronisation bancaire activée",
						"Prochaine vérification à " + BankAutoSyncManager.getTimeLabel(activity),
						Color.parseColor("#4076A8"),
						Color.parseColor("#EEF6FF"), Color.parseColor("#CFE3F5")));
			}
		}

		// ── Priorité 1 : découvert individuel (A3) ──────────────────────────
		if (memberCurrentBalances != null) {
			for (Map.Entry<String, Double> entry : memberCurrentBalances.entrySet()) {
				String memberName = entry.getKey();
				double memberBal = entry.getValue() == null ? 0 : entry.getValue();
				if (memberBal < -0.01) {
					list.add(new HomeNotificationItem("⚠", memberName + " est à découvert",
							"Solde actuel : " + Fmt.money(memberBal) + " · Régularisation recommandée.",
							Color.parseColor("#B04A3A"), Color.parseColor("#FFF1EC"), Color.parseColor("#F4C7B8")));
				}
			}
		}

		// ── Priorité 2 : projection fin de mois négative (B3) ────
		if (projectedEndBalance < -0.01) {
			list.add(new HomeNotificationItem("⚠", "Projection fin de mois négative",
					"Solde prévu : " + Fmt.money(projectedEndBalance) + " après charges fixes restantes.",
					Color.parseColor("#B04A3A"), Color.parseColor("#FFF1EC"), Color.parseColor("#F4C7B8")));
		}

		// ── Priorité 3 : budgets catégorie dépassés/tendus (B3) ──
		if (categoryTotals != null && categoryBudgets != null) {
			java.util.List<double[]> budgetAlerts = new java.util.ArrayList<>();
			java.util.List<String> budgetAlertNames = new java.util.ArrayList<>();
			for (Map.Entry<String, Double> e : categoryTotals.entrySet()) {
				String cat = e.getKey();
				double spent = e.getValue() == null ? 0 : e.getValue();
				double budget = getBudgetForCategory(cat);
				if (budget <= 0)
					continue;
				double ratio = spent / budget;
				if (ratio >= 0.85) {
					budgetAlerts.add(new double[] { ratio, spent, budget });
					budgetAlertNames.add(cat);
				}
			}
			for (int i = 0; i < budgetAlerts.size() - 1; i++) {
				for (int j = i + 1; j < budgetAlerts.size(); j++) {
					if (budgetAlerts.get(j)[0] > budgetAlerts.get(i)[0]) {
						double[] tmp = budgetAlerts.get(i);
						budgetAlerts.set(i, budgetAlerts.get(j));
						budgetAlerts.set(j, tmp);
						String tmpN = budgetAlertNames.get(i);
						budgetAlertNames.set(i, budgetAlertNames.get(j));
						budgetAlertNames.set(j, tmpN);
					}
				}
			}
			for (int i = 0; i < Math.min(budgetAlerts.size(), 2); i++) {
				double ratio = budgetAlerts.get(i)[0];
				double spent = budgetAlerts.get(i)[1];
				double budget = budgetAlerts.get(i)[2];
				String cat = budgetAlertNames.get(i);
				int pct = (int) Math.round(ratio * 100.0);
				boolean over = ratio >= 1.0;
				list.add(new HomeNotificationItem(over ? "⚠" : "!",
						(over ? "Budget " + cat + " dépassé" : "Budget " + cat + " à " + pct + "%"),
						formatMoney(spent) + " / " + formatMoney(budget) + " utilisés ce mois-ci",
						over ? Color.parseColor("#B04A3A") : Color.parseColor("#A67C3A"),
						over ? Color.parseColor("#FFF1EC") : Color.parseColor("#FFF7E6"),
						over ? Color.parseColor("#F4C7B8") : Color.parseColor("#F1D7A8")));
			}
		}

		// ── Priorité 4 : aucun revenu (B3) ────────────────────────────
		boolean hasWarning = !list.isEmpty();
		if (!hasWarning && income <= 0.01 && expenses > 0.01) {
			list.add(new HomeNotificationItem("!", "Aucun revenu ce mois-ci",
					"Déjà " + formatMoney(expenses) + " de dépenses enregistrées.", Color.parseColor("#A67C3A"),
					Color.parseColor("#FFF7E6"), Color.parseColor("#F1D7A8")));
			hasWarning = true;
		}

		// ── Priorité 5 : ratio dépenses/revenus élevé ──────────────────
		if (income > 0.01 && expenses / income >= 0.85) {
			int pct = (int) Math.round(expenses / income * 100.0);
			list.add(new HomeNotificationItem("!", "Dépenses à " + pct + "% des revenus",
					"Il reste " + formatMoney(Math.max(0, income - expenses)) + " avant l'équilibre du mois.",
					Color.parseColor("#A67C3A"), Color.parseColor("#FFF7E6"), Color.parseColor("#F1D7A8")));
			hasWarning = true;
		}

		// ── B3 : charges fixes à venir ce mois-ci ─────────────────
		if (upcomingChargesCount > 0) {
			double totalUpcoming = 0;
			for (Double amt : upcomingChargesByMember.values()) {
				if (amt != null)
					totalUpcoming += amt;
			}
			if (totalUpcoming > 0.01) {
				list.add(new HomeNotificationItem("▷",
						upcomingChargesCount + " charge" + (upcomingChargesCount > 1 ? "s" : "") + " fixe"
								+ (upcomingChargesCount > 1 ? "s" : "") + " à venir",
						formatMoney(totalUpcoming) + " encore à prélever ce mois-ci.", Color.parseColor("#4076A8"),
						Color.parseColor("#EEF6FF"), Color.parseColor("#CFE3F5")));
			}
		}

		// ── B2 : mouvement mensuel — supprimé si contradictoire ───
		// "Solde en hausse" n’est pas affiché si un warning est déjà présent.
		if (Math.abs(monthMovement) > 0.01) {
			boolean positive = monthMovement >= 0;
			if (positive && hasWarning) {
				// B2 : message positif supprimé — contradictoire avec les alertes présentes
			} else {
				list.add(new HomeNotificationItem(positive ? "✓" : "i",
						positive ? "Solde en hausse ce mois-ci" : "Solde en baisse ce mois-ci",
						(positive ? "+" : "") + formatMoney(monthMovement) + " · Solde actuel " + formatMoney(balance),
						positive ? Color.parseColor("#2D7A55") : Color.parseColor("#4076A8"),
						positive ? Color.parseColor("#EFFAF3") : Color.parseColor("#EEF6FF"),
						positive ? Color.parseColor("#CFEBD8") : Color.parseColor("#CFE3F5")));
			}
		}

		// ── Jours sans dépense imprévue (uniquement si pas d’alerte) ─
		int quietDays = computeQuietDays(latestUnexpectedExpenseDate);
		if (quietDays >= 1 && !hasWarning) {
			list.add(new HomeNotificationItem("✓",
					quietDays + " jour" + (quietDays > 1 ? "s" : "") + " sans dépense imprévue", "Continue comme ça !",
					Color.parseColor("#2D7A55"), Color.parseColor("#EFFAF3"), Color.parseColor("#CFEBD8")));
		}

		// ── Insights FinancialInsightManager ─────────────────────
		if (financialInsights != null) {
			for (FinancialInsightManager.Insight insight : financialInsights) {
				if (insight == null)
					continue;
				list.add(toNotificationItem(insight));
			}
		}

		// ── Activité récente du foyer (3 derniers événements) ────
		try {
			java.util.List<ActivityLogger.Event> recent = ActivityLogger.getRecentEvents(activity);
			int shown = 0;
			for (ActivityLogger.Event ev : recent) {
				if (shown >= 3) break;
				int color, bgColor, borderColor;
				if ("budget".equals(ev.type)) {
					color = Color.parseColor("#B04A3A");
					bgColor = Color.parseColor("#FFF1EC");
					borderColor = Color.parseColor("#F4C7B8");
				} else if ("savings".equals(ev.type)) {
					color = Color.parseColor("#2D7A55");
					bgColor = Color.parseColor("#EFFAF3");
					borderColor = Color.parseColor("#CFEBD8");
				} else {
					color = Color.parseColor("#4076A8");
					bgColor = Color.parseColor("#EEF6FF");
					borderColor = Color.parseColor("#CFE3F5");
				}
				list.add(new HomeNotificationItem(ev.icon,
						ev.title, ev.subtitle + " · " + ev.relativeTime(),
						color, bgColor, borderColor));
				shown++;
			}
		} catch (Exception ignored) {}

		if (list.isEmpty()) {
			list.add(new HomeNotificationItem("✓", "Tout est à jour", "Aucune alerte détectée sur tes données du mois.",
					Color.parseColor("#2D7A55"), Color.parseColor("#EFFAF3"), Color.parseColor("#CFEBD8")));
		}

		if (homeNotifications != null) {
			homeNotifications.setNotifications(list);
		}
	}

	private HomeNotificationItem toNotificationItem(FinancialInsightManager.Insight insight) {
		if (insight == null) {
			return new HomeNotificationItem("i", "Insight financier", "Analyse indisponible.",
					Color.parseColor("#4076A8"), Color.parseColor("#EEF6FF"), Color.parseColor("#CFE3F5"));
		}

		if (insight.severity == FinancialInsightManager.SEVERITY_RISK) {
			return new HomeNotificationItem("!", insight.title, insight.subtitle, Color.parseColor("#B04A3A"),
					Color.parseColor("#FFF1EC"), Color.parseColor("#F4C7B8"));
		}

		if (insight.severity == FinancialInsightManager.SEVERITY_WARNING) {
			return new HomeNotificationItem("!", insight.title, insight.subtitle, Color.parseColor("#A67C3A"),
					Color.parseColor("#FFF7E6"), Color.parseColor("#F1D7A8"));
		}

		return new HomeNotificationItem("i", insight.title, insight.subtitle, Color.parseColor("#4076A8"),
				Color.parseColor("#EEF6FF"), Color.parseColor("#CFE3F5"));
	}

	private double getBudgetForCategory(String category) {
		if (category == null || categoryBudgets == null)
			return 0;
		Double exact = categoryBudgets.get(category);
		if (exact != null)
			return exact;
		for (Map.Entry<String, Double> e : categoryBudgets.entrySet()) {
			if (e.getKey() != null && e.getKey().equalsIgnoreCase(category))
				return e.getValue() == null ? 0 : e.getValue();
		}
		return 0;
	}

	private int computeQuietDays(long latestExpenseDateMs) {
		Calendar now = Calendar.getInstance();
		if (latestExpenseDateMs <= 0)
			return 0;
		long diffMs = now.getTimeInMillis() - latestExpenseDateMs;
		return (int) (diffMs / (1000 * 60 * 60 * 24));
	}

	private boolean isUnexpectedExpenseCategory(String category, String label) {
		return HomeCalculator.isUnexpectedExpense(category, label);
	}

	private void installPremiumNotificationsButton() {
		if (homeNotifications == null) {
			homeNotifications = new HomeNotifications(activity, this::showWidgetPicker, this::showDashboardOrganizer);
		}
		homeNotifications.install(dashboardContent);
	}

	private GradientDrawable makeGlassCardBackground(int accent) {
		return makeGlassCardBackgroundLocal(accent);
	}

	private void softenDashboardCards(ViewGroup parent) {
		if (parent == null)
			return;
		for (int i = 0; i < parent.getChildCount(); i++) {
			View child = parent.getChildAt(i);
			if (child instanceof ViewGroup)
				softenDashboardCards((ViewGroup) child);
			if (child instanceof LinearLayout && child.getBackground() != null && child.getTag() == null) {
				child.setElevation(Math.max(child.getElevation(), DS.dp(activity, 1)));
			}
		}
	}

	private boolean isWidgetEnabled(String key) {
		if (dashboardPrefs == null)
			return true;
		return dashboardPrefs.getBoolean(key, true);
	}

	private void setWidgetVisible(View widget, String key) {
		if (widget != null) {
			widget.setVisibility(isWidgetEnabled(key) ? View.VISIBLE : View.GONE);
		}
	}

	private void applyWidgetVisibility() {
		setWidgetVisible(widgetMonthProgress, W_MONTH_PROGRESS);
		setWidgetVisible(widgetStatsCards, W_STATS);
		setWidgetVisible(widgetPersons, W_PERSONS);
		setWidgetVisible(widgetTopCategories, W_TOP_CATEGORIES);
		setWidgetVisible(widgetBottomLine, W_BOTTOM_LINE);
		if (dynamicWidgetsContainer != null)
			dynamicWidgetsContainer.setVisibility(hasAtLeastOneDynamicWidgetEnabled() ? View.VISIBLE : View.GONE);
	}

	private boolean isDynamicWidgetEnabled(String key) {
		return isWidgetEnabled(key);
	}

	private boolean hasAtLeastOneDynamicWidgetEnabled() {
		String[] keys = getDynamicKeys();
		for (String key : keys) {
			if (isDynamicWidgetEnabled(key))
				return true;
		}
		return false;
	}

	private void showWidgetPicker() {
		homeOrganizer.showWidgetPicker();
	}

	private boolean isMainSectionKey(String key) {
		return W_MONTH_PROGRESS.equals(key) || W_STATS.equals(key) || W_BOTTOM_LINE.equals(key) || W_PERSONS.equals(key)
				|| W_TOP_CATEGORIES.equals(key) || W_DYNAMIC_LIBRARY.equals(key);
	}

	private void showDashboardOrganizer() {
		homeOrganizer.showDashboardOrganizer();
	}

	private void moveAgendaToTop() {
		homeOrganizer.moveAgendaToTop();
	}

	private void applyDashboardSectionOrder() {
		// Layout premium figé façon application bancaire : on conserve l'ordre XML
		// pour garder la grille 2 colonnes (santé financière à gauche, widgets à droite).
		// Les widgets restent activables/masquables via applyWidgetVisibility().
		return;
	}

	private View getSectionView(String key) {
		if (W_MONTH_PROGRESS.equals(key))
			return widgetMonthProgress;
		if (W_STATS.equals(key))
			return widgetStatsCards;
		if (W_BOTTOM_LINE.equals(key))
			return widgetBottomLine;
		if (W_PERSONS.equals(key))
			return widgetPersons;
		if (W_TOP_CATEGORIES.equals(key))
			return widgetTopCategories;
		if (W_DYNAMIC_LIBRARY.equals(key))
			return dynamicWidgetsContainer;
		return null;
	}

	private void applyWidgetVisibilityAnimated() {
		applyWidgetVisibility();
		animateVisibleChildren(dashboardContent);
	}

	private void applyDashboardSectionOrderAnimated() {
		applyDashboardSectionOrder();
		animateVisibleChildren(dashboardContent);
	}

	private void animateVisibleChildren(ViewGroup parent) {
		if (parent == null)
			return;

		for (int i = 0; i < parent.getChildCount(); i++) {
			View child = parent.getChildAt(i);
			if (child == null || child.getVisibility() != View.VISIBLE)
				continue;

			child.setAlpha(0f);
			child.setTranslationY(DS.dp(activity, 18));
			child.setScaleX(0.985f);
			child.setScaleY(0.985f);

			child.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setStartDelay(i * 38L).setDuration(360)
					.setInterpolator(new DecelerateInterpolator()).start();
		}
	}

	private void pulse(View view) {
		if (view == null)
			return;
		view.animate().scaleX(0.98f).scaleY(0.98f).setDuration(70)
				.withEndAction(() -> view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()).start();
	}

	private GradientDrawable makeWidgetRowBackground(boolean active) {
		return makeWidgetRowBackgroundLocal(active);
	}

	private GradientDrawable makeGlassCardBackgroundLocal(int accent) {
		return HomeDashboardStyle.glass(activity, accent);
	}

	private GradientDrawable makeWidgetRowBackgroundLocal(boolean active) {
		return HomeDashboardStyle.widgetRow(activity, active);
	}

	private GradientDrawable makeCardBackground() {
		return HomeDashboardStyle.card(activity);
	}

	private GradientDrawable buildPill(int backgroundColor, int borderColor) {
		return HomeDashboardStyle.pill(activity, backgroundColor, borderColor);
	}

	private int withAlpha(int color, int alpha) {
		return HomeDashboardStyle.withAlpha(color, alpha);
	}

	private GradientDrawable buildCircleLocal(int color) {
		return HomeDashboardStyle.circle(color);
	}

	private int balanceColorLocal(double balance) {
		return HomeDashboardStyle.balanceColor(balance, overdraftDefined, overdraftLimit);
	}

	private String extractStrLocal(String json, String key) {
		if (json == null || key == null) {
			return "";
		}

		String marker = "\"" + key + "\"";
		int keyIndex = json.indexOf(marker);
		if (keyIndex < 0) {
			return "";
		}

		int stringIndex = json.indexOf("\"stringValue\"", keyIndex);
		if (stringIndex >= 0) {
			int colon = json.indexOf(":", stringIndex);
			int firstQuote = json.indexOf("\"", colon + 1);
			int secondQuote = json.indexOf("\"", firstQuote + 1);
			if (firstQuote >= 0 && secondQuote > firstQuote) {
				return json.substring(firstQuote + 1, secondQuote).trim();
			}
		}

		int integerIndex = json.indexOf("\"integerValue\"", keyIndex);
		if (integerIndex >= 0) {
			return extractNumberAfterColon(json, integerIndex);
		}

		int doubleIndex = json.indexOf("\"doubleValue\"", keyIndex);
		if (doubleIndex >= 0) {
			return extractNumberAfterColon(json, doubleIndex);
		}

		return "";
	}

	private String extractNumLocal(String json, String key) {
		if (json == null || key == null) {
			return "0";
		}

		String marker = "\"" + key + "\"";
		int keyIndex = json.indexOf(marker);
		if (keyIndex < 0) {
			return "0";
		}

		int doubleIndex = json.indexOf("\"doubleValue\"", keyIndex);
		int integerIndex = json.indexOf("\"integerValue\"", keyIndex);

		if (doubleIndex >= 0 && (integerIndex < 0 || doubleIndex < integerIndex)) {
			return extractNumberAfterColon(json, doubleIndex);
		}

		if (integerIndex >= 0) {
			return extractNumberAfterColon(json, integerIndex);
		}

		return "0";
	}

	private String extractNumberAfterColon(String json, int startIndex) {
		int colon = json.indexOf(":", startIndex);
		if (colon < 0) {
			return "0";
		}

		int i = colon + 1;
		while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == '"' || json.charAt(i) == '\n'
				|| json.charAt(i) == '\r')) {
			i++;
		}

		int start = i;
		while (i < json.length()) {
			char c = json.charAt(i);
			if (!Character.isDigit(c) && c != '.' && c != '-' && c != '+') {
				break;
			}
			i++;
		}

		if (i <= start) {
			return "0";
		}

		return json.substring(start, i).replace("\"", "").trim();
	}

	private String prevMonthShortLocal() {
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.MONTH, -1);
		SimpleDateFormat format = new SimpleDateFormat("MMM", Locale.FRENCH);
		String value = format.format(calendar.getTime()).toLowerCase(Locale.FRENCH);
		return value.endsWith(".") ? value : value + ".";
	}

	private void startAutoRefresh() {
		refreshHandler.removeCallbacksAndMessages(null);

		refreshHandler.postDelayed(new Runnable() {
			public void run() {
				if (isActive) {
					loadData();
					refreshHandler.postDelayed(this, 60000);
				}
			}
		}, 60000);
	}

	private void setSyncStatus(String status) {
		// Dans le dashboard style référence, tvSyncDot sert au badge "+ ce mois".
		// On ne change donc plus son fond ici pour éviter un badge vert foncé non conforme.
	}

	private boolean isNetworkAvailable() {
		try {
			android.net.ConnectivityManager cm = (android.net.ConnectivityManager) activity
					.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
			android.net.NetworkInfo info = cm != null ? cm.getActiveNetworkInfo() : null;
			return info != null && info.isConnected();
		} catch (Exception e) {
			return false;
		}
	}

	private void resetDashboardBeforeLoad() {
		hasAnimatedOnce = false;
		lastAnimatedBalance = 0;

		monthlyStartBalance = 0;
		commonStartBalance = 0;
		monthlyStartBalanceDefined = false;
		monthlyStartBalanceDate = 0;
		commonBalanceAnchorDate = 0;

		if (tvBalance != null)
			tvBalance.setText("—");

		if (tvExpenses != null)
			tvExpenses.setText("—");

		if (tvSavings != null)
			tvSavings.setText("—");

		if (tvEndBalance != null)
			tvEndBalance.setText("Solde commun");

		if (tvTransactionCount != null)
			tvTransactionCount.setText("—");
	}

	private void loadData() {
		if (!isNetworkAvailable()) {
			setSyncStatus("error");
			return;
		}

		setSyncStatus("connecting");
		resetDashboardBeforeLoad();

		// Recharge le Compte Joint partagé depuis Firestore AVANT de calculer
		// le dashboard, afin que le solde joint saisi par un autre membre
		// soit pris en compte. En cas d'échec réseau, refresh() conserve le
		// dernier snapshot connu (fallback hors-ligne) et appelle quand même
		// le callback : le chargement n'est jamais bloqué.
		com.couplefinance.data.JointAccountManager.getInstance().refresh(activity, this::loadHouseholdMembers);
	}

	private void loadHouseholdMembers() {
		if (activity == null || activity.isFinishing()) {
			return;
		}

		HouseholdManager.getInstance().getMembers(new FirestoreManager.Callback() {
			public void onSuccess(String response) {
				List<String> firestoreMembers = new ArrayList<>();
				Map<String, String> parsedUserIdToName = new HashMap<>();
				String[] parts = response.split("\"fields\":");

				for (int i = 1; i < parts.length; i++) {
					if (parts[i].contains("\"name\"")) {
						String name = extractStr(parts[i].substring(parts[i].indexOf("\"name\"")), "stringValue");

						if (name.isEmpty() || name.equals("null") || name.equals("Moi"))
							continue;

						boolean exists = false;
						for (String existing : firestoreMembers) {
							if (existing.equalsIgnoreCase(name)) {
								exists = true;
								break;
							}
						}

						if (!exists)
							firestoreMembers.add(name);

						// Extraire le userId associé à ce membre
						if (parts[i].contains("\"userId\"")) {
							String uid = extractStr(parts[i].substring(parts[i].indexOf("\"userId\"")), "stringValue");
							if (!uid.isEmpty()) {
								parsedUserIdToName.put(uid, name);
							}
						}
					}
				}

				// Mettre à jour la map globale userId → nom
				userIdToName = parsedUserIdToName;

				// Si notre uid n'est pas dans la map, tenter de l'associer
				// au seul membre non encore lié (userId absent du doc Firestore)
				String myUid = AuthManager.getInstance().getUserId();
				if (myUid != null && !myUid.isEmpty() && !userIdToName.containsKey(myUid)) {
					List<String> unlinkedMembers = new ArrayList<>(firestoreMembers);
					for (String linkedName : parsedUserIdToName.values()) {
						unlinkedMembers.removeIf(m -> m.equalsIgnoreCase(linkedName));
					}
					if (unlinkedMembers.size() == 1) {
						String inferredName = unlinkedMembers.get(0);
						userIdToName.put(myUid, inferredName);
						activity.getSharedPreferences("couplefinance_profile", Activity.MODE_PRIVATE)
								.edit().putString("display_name", inferredName).apply();
						com.couplefinance.data.UserManager.getInstance()
								.registerCurrentUserAsMemberWithName(inferredName);
						// Mettre à jour le sidebar avec le prénom résolu
						if (activity instanceof com.couplefinance.ui.DashboardActivity) {
							((com.couplefinance.ui.DashboardActivity) activity).refreshSidebarUser();
						}
					} else {
						com.couplefinance.data.UserManager.getInstance().registerCurrentUserAsMember();
					}
				}

				// Mettre à jour le greeting avec le prénom résolu
				updateGreeting();

				String myName = getMyName();

				if (myName.isEmpty()) {
					// Pas de correspondance userId : on ne prend PAS le premier membre
					// au hasard — mieux vaut laisser le greeting sans prénom
				}

				List<String> allNames = new ArrayList<>();

				if (!myName.isEmpty() && !myName.equals("Moi"))
					allNames.add(myName);

				for (String m : firestoreMembers) {
					if (m.equalsIgnoreCase(myName))
						continue;

					boolean dup = false;
					for (String a : allNames) {
						if (a.equalsIgnoreCase(m)) {
							dup = true;
							break;
						}
					}

					if (!dup)
						allNames.add(m);
				}

				if (allNames.isEmpty() && !myName.isEmpty())
					allNames.add(myName);

				if (!allNames.isEmpty()) {
					StringBuilder sb = new StringBuilder();

					for (int i = 0; i < allNames.size(); i++) {
						if (i > 0)
							sb.append(" & ");
						sb.append(allNames.get(i));
					}

					sb.append(".");
					final String title = sb.toString();

					activity.runOnUiThread(() -> {
						if (isActive && tvNotification != null)
							tvNotification.setText(title);
					});
				}

				allHouseholdMembers = new ArrayList<>(allNames);
				loadTransactions();
			}

			public void onError(String e) {
				loadTransactions();
			}
		});
	}

	private void loadTransactions() {
		TransactionManager.getInstance().getTransactions(new FirestoreManager.Callback() {
			public void onSuccess(String response) {
				loadRetryCount = 0;
				setSyncStatus("ok");
				cachedTransactions = parseAllTransactions(response);
				checkPartnerNotifications(cachedTransactions);
				checkWeeklyRecap(cachedTransactions);
				loadFinancialSettingsThenProcess();
				renderCalendar();
			}

			public void onError(String error) {
				// Si le token vient d'être rafraîchi en arrière-plan,
				// on réessaie une fois après 2 secondes.
				if (loadRetryCount < 2 && isActive) {
					loadRetryCount++;
					refreshHandler.postDelayed(() -> {
						if (isActive)
							loadTransactions();
					}, 2000);
				} else {
					loadRetryCount = 0;
					setSyncStatus("error");
				}
			}
		});
	}

	private void styleHeroBadge(TextView tv, int textColor) {
		if (tv == null)
			return;

		tv.setVisibility(View.VISIBLE);
		tv.setSingleLine(true);
		tv.setMaxLines(1);
		tv.setTextColor(textColor);
		tv.setTextSize(12f);
		tv.setTypeface(Typeface.DEFAULT_BOLD);
		tv.setAlpha(1f);
		tv.setPadding(DS.dp(activity, 14), DS.dp(activity, 7), DS.dp(activity, 14), DS.dp(activity, 7));
		tv.setBackground(buildPill(Color.parseColor("#33FFFFFF"), Color.parseColor("#44FFFFFF")));
	}

	private void checkPartnerNotifications(List<String[]> transactions) {
		String myUserId = AuthManager.getInstance().getUserId();
		if (myUserId == null || myUserId.isEmpty())
			return;

		com.couplefinance.utils.NotificationHelper nh = com.couplefinance.utils.NotificationHelper
				.getInstance(activity);

		long lastSeen = nh.getLastSeenTimestamp();
		long maxPartnerTs = 0;
		String partnerLabel = "";
		double partnerAmount = 0;

		for (String[] tx : transactions) {
			if (tx.length < 8)
				continue;
			String txUserId = tx[7];

			if (txUserId.isEmpty() || txUserId.equals(myUserId))
				continue;

			long ts = 0;
			try {
				ts = Long.parseLong(tx[4]);
			} catch (Exception ignored) {
			}

			if (ts > lastSeen && ts > maxPartnerTs) {
				maxPartnerTs = ts;
				partnerLabel = tx[0];
				try {
					partnerAmount = Double.parseDouble(tx[1]);
				} catch (Exception ignored) {
				}
			}
		}

		if (maxPartnerTs > 0) {
			String partnerName = com.couplefinance.data.UserManager.getInstance().getCurrentDisplayNameOrFallback();
			nh.notifyNewPartnerTransaction(partnerName, partnerLabel, partnerAmount, maxPartnerTs);
			nh.markTransactionsAsSeen(maxPartnerTs);
		}
	}

	private void shareMonthSummary() {
		SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.FRENCH);
		String month = sdf.format(new Date());

		double sav = snapshotIncome - snapshotExpenses;
		int score = HomeCalculator.financialScoreDetailed(
				snapshotIncome, snapshotExpenses, snapshotBalance,
				overdraftDefined, overdraftLimit, 0);

		String myName = getMyName();
		String greeting = myName.isEmpty() ? "Foyer" : myName;

		StringBuilder sb = new StringBuilder();
		sb.append("💑 Bilan de ").append(greeting).append(" — ").append(month).append("\n\n");
		sb.append("💰 Revenus    : ").append(String.format(Locale.FRANCE, "%.2f €", snapshotIncome)).append("\n");
		sb.append("💸 Dépenses   : ").append(String.format(Locale.FRANCE, "%.2f €", snapshotExpenses)).append("\n");
		sb.append("🐷 Épargne    : ").append(String.format(Locale.FRANCE, "%.2f €", sav)).append("\n");
		sb.append("💳 Solde      : ").append(String.format(Locale.FRANCE, "%.2f €", snapshotBalance)).append("\n");
		sb.append("❤️ Santé      : ").append(score).append("/100\n");
		sb.append("\nPartagé depuis CoupleFinance 📱");

		android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
		intent.setType("text/plain");
		intent.putExtra(android.content.Intent.EXTRA_TEXT, sb.toString());
		activity.startActivity(android.content.Intent.createChooser(intent, "Partager le bilan"));
	}

	private void checkWeeklyRecap(List<String[]> transactions) {
		try {
			long weekStart = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
			double weekExp = 0, weekInc = 0;
			for (String[] tx : transactions) {
				if (tx.length < 5) continue;
				long ts = 0;
				try { ts = Long.parseLong(tx[4]); } catch (Exception ignored) {}
				if (ts < weekStart) continue;
				String type = tx.length > 2 ? tx[2] : "";
				double amount = 0;
				try { amount = Double.parseDouble(tx[1]); } catch (Exception ignored) {}
				if ("income".equalsIgnoreCase(type)) weekInc += amount;
				else weekExp += amount;
			}
			com.couplefinance.utils.NotificationHelper.getInstance(activity)
					.checkAndSendWeeklyRecap(weekExp, weekInc);
		} catch (Exception ignored) {}
	}

	private String getOverdraftCacheKey() {
		return "overdraft_limit_" + AuthManager.getInstance().getUserId();
	}

	private void saveOverdraftLocal(double amount) {
		HomeData.saveOverdraft(activity, amount);
	}

	private Double getOverdraftLocal() {
		return HomeData.getOverdraft(activity);
	}

	private void loadFinancialSettingsThenProcess() {
		BalanceManager.getInstance().getAllMembersStartBalance(new FirestoreManager.Callback() {
			public void onSuccess(String r) {
				String myUserId = AuthManager.getInstance().getUserId();
				String currentMonth = com.couplefinance.data.RecurringChargeManager.getCurrentMonth();

				double myBalance = 0;
				final double[] totalBalance = { 0 };
				boolean myDefined = false;

				long myAnchorDate = 0;
				long maxAnchorDate = 0;

				Map<String, Double> parsedMemberBalances = new HashMap<>();
				Map<String, Double> balancesByUid = new LinkedHashMap<>();

				try {
					if (r == null || r.trim().isEmpty()) {
						applyCachedBalanceFallback();
						loadOverdraftThenProcess();
						return;
					}

					String[] docs = r.split("\"fields\":");

					for (int i = 1; i < docs.length; i++) {
						String doc = docs[i];

						if (doc == null || !doc.contains("\"month\""))
							continue;

						int monthIdx = doc.indexOf("\"month\"");
						int userIdx = doc.indexOf("\"userId\"");
						int balanceIdx = doc.indexOf("\"balance\"");

						if (monthIdx < 0 || userIdx < 0 || balanceIdx < 0)
							continue;

						String month = extractStr(doc.substring(monthIdx), "stringValue");

						if (!currentMonth.equals(month))
							continue;

						String uid = extractStr(doc.substring(userIdx), "stringValue");
						String val = extractNum(doc.substring(balanceIdx), "doubleValue");

						if (val == null || val.trim().isEmpty())
							val = extractNum(doc.substring(balanceIdx), "integerValue");

						if (uid == null || uid.trim().isEmpty() || val == null || val.trim().isEmpty())
							continue;

						long anchorDate = BalanceManager.getInstance().getMonthStartMillis();

						int anchorIdx = doc.indexOf("\"anchorDate\"");
						if (anchorIdx >= 0) {
							try {
								String anchorPart = doc.substring(anchorIdx);
								String anchorVal = extractFirestoreDateValue(anchorPart);

								if (anchorVal != null && !anchorVal.trim().isEmpty())
									anchorDate = Long.parseLong(anchorVal);

							} catch (Exception ignored) {
								anchorDate = BalanceManager.getInstance().getMonthStartMillis();
							}
						}

						try {
							double amount = Double.parseDouble(val);

							totalBalance[0] += amount;
							maxAnchorDate = Math.max(maxAnchorDate, anchorDate);
							balancesByUid.put(uid, amount);

							String memberName = userIdToName.get(uid);

							if (memberName != null && !memberName.trim().isEmpty()) {
								parsedMemberBalances.put(memberName.toLowerCase(Locale.FRANCE), amount);
							}

							if (myUserId != null && myUserId.equals(uid)) {
								myBalance = amount;
								myAnchorDate = anchorDate;
								myDefined = true;
							}

						} catch (Exception ignored) {
						}
					}

					if (myUserId != null && !myUserId.trim().isEmpty()) {
						String myName = getMyName();

						Double myAmount = balancesByUid.get(myUserId);

						if (myName != null && !myName.trim().isEmpty() && myAmount != null) {
							parsedMemberBalances.put(myName.toLowerCase(Locale.FRANCE), myAmount);
						}
					}

					for (String member : allHouseholdMembers) {
						if (member == null || member.trim().isEmpty())
							continue;

						String cleanMember = member.trim();
						String key = cleanMember.toLowerCase(Locale.FRANCE);

						if (parsedMemberBalances.containsKey(key))
							continue;

						for (Map.Entry<String, Double> uidBalance : balancesByUid.entrySet()) {
							String uid = uidBalance.getKey();
							Double amount = uidBalance.getValue();

							if (uid == null || amount == null)
								continue;

							String knownName = userIdToName.get(uid);

							if (knownName != null && knownName.trim().equalsIgnoreCase(cleanMember)) {
								parsedMemberBalances.put(key, amount);
								break;
							}
						}
					}

					if (parsedMemberBalances.size() < allHouseholdMembers.size()) {
						for (Map.Entry<String, Double> uidBalance : balancesByUid.entrySet()) {
							Double amount = uidBalance.getValue();

							if (amount == null)
								continue;

							boolean amountAlreadyUsed = false;

							for (Double existing : parsedMemberBalances.values()) {
								if (existing != null && Math.abs(existing - amount) < 0.001) {
									amountAlreadyUsed = true;
									break;
								}
							}

							if (amountAlreadyUsed)
								continue;

							for (String member : allHouseholdMembers) {
								if (member == null || member.trim().isEmpty())
									continue;

								String key = member.trim().toLowerCase(Locale.FRANCE);

								if (!parsedMemberBalances.containsKey(key)) {
									parsedMemberBalances.put(key, amount);
									break;
								}
							}
						}
					}

					memberBalances = parsedMemberBalances;

					double jointStartBalance = readJointBalanceDirect();

					if (myDefined) {
						monthlyStartBalance = myBalance;
						monthlyStartBalanceDate = myAnchorDate > 0 ? myAnchorDate
								: BalanceManager.getInstance().getMonthStartMillis();
						monthlyStartBalanceDefined = true;
						BalanceManager.getInstance().saveMonthlyStartBalanceLocal(myBalance, monthlyStartBalanceDate);
					} else {
						applyCachedBalanceFallback();
					}

					commonStartBalance = totalBalance[0] + jointStartBalance;

					commonBalanceAnchorDate = maxAnchorDate > 0 ? maxAnchorDate
							: BalanceManager.getInstance().getMonthStartMillis();

					if (myDefined && myAnchorDate > 0) {
						commonBalanceAnchorDate = myAnchorDate;
					}

				} catch (Exception e) {
					applyCachedBalanceFallback();
					double jointFallback = readJointBalanceDirect();
					commonStartBalance = totalBalance[0] + jointFallback;
					commonBalanceAnchorDate = monthlyStartBalanceDate;
				}

				loadOverdraftThenProcess();
			}

			public void onError(String e) {
				applyCachedBalanceFallback();
				commonStartBalance = monthlyStartBalance + readJointBalanceDirect();
				commonBalanceAnchorDate = monthlyStartBalanceDate;
				loadOverdraftThenProcess();
			}
		});
	}

	/**
	 * Retourne le solde de début de mois du Compte Joint à inclure dans le
	 * solde de départ commun.
	 *
	 * <p>La donnée provient désormais du {@link com.couplefinance.data.JointAccountManager},
	 * dont le snapshot est rechargé depuis Firestore au début de {@code loadData()}.
	 * Le compte joint est donc une donnée partagée du foyer : le solde saisi
	 * par un membre est visible par les autres après reload.</p>
	 *
	 * <p>Retourne 0 si le compte joint est désactivé ou non configuré.</p>
	 */
	private double readJointBalanceDirect() {
		try {
			com.couplefinance.data.JointAccountManager jm = com.couplefinance.data.JointAccountManager.getInstance();
			jm.init(activity);

			if (!jm.isEnabledLocal())
				return 0;

			return jm.getBalanceLocal();
		} catch (Exception e) {
			return 0;
		}
	}

	private void applyCachedBalanceFallback() {
		Double cached = BalanceManager.getInstance().getMonthlyStartBalanceLocal();

		if (cached != null) {
			monthlyStartBalance = cached;
			monthlyStartBalanceDate = BalanceManager.getInstance().getMonthlyStartBalanceDateLocal();
			monthlyStartBalanceDefined = true;
		} else {
			monthlyStartBalanceDefined = false;
			monthlyStartBalance = 0;
			monthlyStartBalanceDate = BalanceManager.getInstance().getMonthStartMillis();
		}
	}

	private void loadOverdraftThenProcess() {
		BalanceManager.getInstance().getOverdraftLimit(new FirestoreManager.Callback() {
			public void onSuccess(String r) {
				boolean loaded = false;

				try {
					double value = Double.parseDouble(r);

					if (value != 0) {
						overdraftDefined = true;
						overdraftLimit = Math.abs(value);
						saveOverdraftLocal(value);
						loaded = true;
					}
				} catch (Exception ignored) {
				}

				if (!loaded) {
					Double cached = getOverdraftLocal();

					if (cached != null && cached != 0) {
						overdraftDefined = true;
						overdraftLimit = Math.abs(cached);
					} else {
						overdraftDefined = false;
						overdraftLimit = 0;
					}
				}

				loadUpcomingFixedChargesThenProcess();
			}

			public void onError(String e) {
				Double cached = getOverdraftLocal();

				if (cached != null && cached != 0) {
					overdraftDefined = true;
					overdraftLimit = Math.abs(cached);
				} else {
					overdraftDefined = false;
					overdraftLimit = 0;
				}

				loadUpcomingFixedChargesThenProcess();
			}
		});
	}

	private Map<String, Double> upcomingChargesByMember = new HashMap<>();
	private Map<String, Integer> upcomingChargesCountByMember = new HashMap<>();

	private void loadUpcomingFixedChargesThenProcess() {
		RecurringChargeManager.getInstance().getUpcomingChargesForCurrentMonthByMember(
				new RecurringChargeManager.UpcomingChargesByMemberCallback() {
					@Override
					public void onResult(Map<String, Double> amountByMember, Map<String, Integer> countByMember,
							double totalUpcoming, int totalCount) {

						upcomingChargesByMember = amountByMember != null ? new HashMap<>(amountByMember)
								: new HashMap<>();

						upcomingChargesCountByMember = countByMember != null ? new HashMap<>(countByMember)
								: new HashMap<>();

						loadUpcomingCreditsThenProcess(totalUpcoming, totalCount);
					}

					@Override
					public void onError(String error) {
						upcomingChargesByMember = new HashMap<>();
						upcomingChargesCountByMember = new HashMap<>();
						loadUpcomingCreditsThenProcess(0, 0);
					}
				});
	}

	private void loadUpcomingCreditsThenProcess(double fixedUpcomingTotal, int fixedUpcomingCount) {
		CreditManager.getInstance().getCredits(new FirestoreManager.Callback() {
			@Override
			public void onSuccess(String json) {
				double creditsUpcomingTotal = 0;
				int creditsUpcomingCount = 0;

				try {
					List<CreditsModels.Credit> credits = CreditsParser.parseCredits(json);

					for (CreditsModels.Credit credit : credits) {
						if (credit == null)
							continue;

						if (!isCreditActiveThisMonth(credit))
							continue;

						if (!isCreditPaymentUpcomingThisMonth(credit))
							continue;

						String key = credit.isJoint()
								? com.couplefinance.data.JointAccountManager.getInstance().getNameLocal()
								: credit.paidBy;

						if (key == null || key.trim().isEmpty())
							key = getMyName();

						key = key.trim();

						addUpcomingAmountForKey(key, credit.monthlyPayment);
						addUpcomingCountForKey(key, 1);

						creditsUpcomingTotal += credit.monthlyPayment;
						creditsUpcomingCount++;
					}
				} catch (Exception ignored) {
				}

				processData(cachedTransactions, fixedUpcomingTotal + creditsUpcomingTotal,
						fixedUpcomingCount + creditsUpcomingCount);
			}

			@Override
			public void onError(String error) {
				processData(cachedTransactions, fixedUpcomingTotal, fixedUpcomingCount);
			}
		});
	}

	private boolean isCreditActiveThisMonth(CreditsModels.Credit credit) {
		if (credit == null || credit.startDateMs <= 0 || credit.durationMonths <= 0)
			return false;

		Calendar start = Calendar.getInstance();
		start.setTimeInMillis(credit.startDateMs);

		Calendar end = Calendar.getInstance();
		end.setTimeInMillis(credit.startDateMs);
		end.add(Calendar.MONTH, credit.durationMonths);

		Calendar now = Calendar.getInstance();

		return !now.before(start) && !now.after(end);
	}

	private boolean isCreditPaymentUpcomingThisMonth(CreditsModels.Credit credit) {
		if (credit == null)
			return false;

		Calendar now = Calendar.getInstance();

		int today = now.get(Calendar.DAY_OF_MONTH);
		int maxDay = now.getActualMaximum(Calendar.DAY_OF_MONTH);

		int day = credit.paymentDay > 0 ? credit.paymentDay : 1;
		if (day > maxDay)
			day = maxDay;

		return day >= today;
	}

	private void addUpcomingAmountForKey(String key, double amount) {
		if (key == null || key.trim().isEmpty() || amount <= 0)
			return;

		String realKey = findExistingUpcomingAmountKey(key);
		Double old = upcomingChargesByMember.get(realKey);
		upcomingChargesByMember.put(realKey, (old == null ? 0 : old) + amount);
	}

	private void addUpcomingCountForKey(String key, int count) {
		if (key == null || key.trim().isEmpty() || count <= 0)
			return;

		String realKey = findExistingUpcomingCountKey(key);
		Integer old = upcomingChargesCountByMember.get(realKey);
		upcomingChargesCountByMember.put(realKey, (old == null ? 0 : old) + count);
	}

	private String findExistingUpcomingAmountKey(String key) {
		for (String existing : upcomingChargesByMember.keySet()) {
			if (existing != null && existing.equalsIgnoreCase(key))
				return existing;
		}
		return key;
	}

	private String findExistingUpcomingCountKey(String key) {
		for (String existing : upcomingChargesCountByMember.keySet()) {
			if (existing != null && existing.equalsIgnoreCase(key))
				return existing;
		}
		return key;
	}

	private String extractFirestoreDateValue(String json) {
		if (json == null)
			return "0";

		String[] keys = { "integerValue", "doubleValue", "stringValue" };

		for (String key : keys) {
			int k = json.indexOf("\"" + key + "\"");
			if (k < 0)
				continue;

			int colon = json.indexOf(":", k);
			if (colon < 0)
				continue;

			int startQuote = json.indexOf("\"", colon + 1);
			int endQuote = startQuote >= 0 ? json.indexOf("\"", startQuote + 1) : -1;

			if (startQuote >= 0 && endQuote > startQuote) {
				String value = json.substring(startQuote + 1, endQuote).trim();
				if (!value.isEmpty())
					return value;
			}

			String sub = json.substring(colon + 1).trim();
			StringBuilder sb = new StringBuilder();

			for (int i = 0; i < sub.length(); i++) {
				char c = sub.charAt(i);
				if ((c >= '0' && c <= '9') || c == '.' || c == '-') {
					sb.append(c);
				} else if (sb.length() > 0) {
					break;
				}
			}

			if (sb.length() > 0)
				return sb.toString();
		}

		return "0";
	}

	private List<String[]> parseAllTransactions(String json) {
		List<String[]> list = new ArrayList<>();
		String[] parts = json.split("\"fields\":");

		for (int i = 1; i < parts.length; i++) {
			String label = "";
			String amount = "0";
			String type = "";
			String category = "";
			String date = "0";
			String isShareSplit = "false";
			String isReimbursement = "false";
			String userId = "";
			String person = "";
			String compte = "";

			if (parts[i].contains("\"label\""))
				label = extractStr(parts[i].substring(parts[i].indexOf("\"label\"")), "stringValue");

			if (parts[i].contains("\"amount\""))
				amount = extractNum(parts[i].substring(parts[i].indexOf("\"amount\"")), "doubleValue");

			if (parts[i].contains("\"type\""))
				type = extractStr(parts[i].substring(parts[i].indexOf("\"type\"")), "stringValue");

			if (parts[i].contains("\"category\""))
				category = extractStr(parts[i].substring(parts[i].indexOf("\"category\"")), "stringValue");

			if (parts[i].contains("\"date\"")) {
				date = extractFirestoreDateValue(parts[i].substring(parts[i].indexOf("\"date\"")));
			}

			if (parts[i].contains("\"isShareSplit\"")) {
				int s = parts[i].indexOf("\"isShareSplit\"");
				String sub = parts[i].substring(s, Math.min(s + 60, parts[i].length()));

				if (sub.contains("booleanValue\":true") || sub.contains("booleanValue\": true"))
					isShareSplit = "true";
			}

			if (parts[i].contains("\"isReimbursement\"")) {
				int s = parts[i].indexOf("\"isReimbursement\"");
				String sub = parts[i].substring(s, Math.min(s + 60, parts[i].length()));

				if (sub.contains("booleanValue\":true") || sub.contains("booleanValue\": true"))
					isReimbursement = "true";
			}

			if (parts[i].contains("\"userId\""))
				userId = extractStr(parts[i].substring(parts[i].indexOf("\"userId\"")), "stringValue");

			if (parts[i].contains("\"person\""))
				person = extractStr(parts[i].substring(parts[i].indexOf("\"person\"")), "stringValue");

			if (parts[i].contains("\"compte\""))
				compte = extractStr(parts[i].substring(parts[i].indexOf("\"compte\"")), "stringValue");

			if (!label.isEmpty()) {
				list.add(new String[] { label, amount, type, category, date, isShareSplit, isReimbursement, userId,
						person, compte });
			}
		}

		return list;
	}

	private void processData(List<String[]> transactions, double upcomingFixedCharges, int upcomingFixedChargesCount) {
		if (!isActive)
			return;

		double totalIncome = 0;
		double totalExpenses = 0;
		double availableIncome = 0;
		double availableExpenses = 0;

		Map<String, double[]> personBalances = new LinkedHashMap<>();

		String myName = getMyName();
		if (!myName.isEmpty())
			personBalances.put(myName, new double[] { 0, 0, 0, 0 });

		for (String member : allHouseholdMembers) {
			if (member == null || member.trim().isEmpty())
				continue;

			if (member.equalsIgnoreCase("Compte joint") || member.equalsIgnoreCase("Joint")
					|| member.equalsIgnoreCase("Compte commun"))
				continue;

			boolean exists = false;

			for (String k : personBalances.keySet()) {
				if (k.equalsIgnoreCase(member)) {
					exists = true;
					break;
				}
			}

			if (!exists)
				personBalances.put(member, new double[] { 0, 0, 0, 0 });
		}

		List<String[]> recentTx = new ArrayList<>();

		Calendar cal = Calendar.getInstance();
		int curMonth = cal.get(Calendar.MONTH);
		int curYear = cal.get(Calendar.YEAR);

		Calendar prevCal = (Calendar) cal.clone();
		prevCal.add(Calendar.MONTH, -1);
		int prevMonth = prevCal.get(Calendar.MONTH);
		int prevYear = prevCal.get(Calendar.YEAR);

		double prevIncome = 0;
		double prevExpenses = 0;

		Map<String, Double> categoryTotals = new HashMap<>();
		Map<String, Double> incomeSources = new HashMap<>();
		Set<String> activeExpenseCategories = new HashSet<>();

		double biggestExpenseAmount = 0;
		String biggestExpenseLabel = "";
		String biggestExpenseCategory = "";

		int currentMonthTxCount = 0;
		int currentMonthIncomeCount = 0;
		int currentMonthExpenseCount = 0;
		int todayExpenseCount = 0;

		double todayExpenses = 0;
		long latestUnexpectedExpenseDate = 0;

		Calendar todayCal = Calendar.getInstance();

		for (String[] tx : transactions) {
			if (tx == null || tx.length < 5)
				continue;

			double amount = 0;

			try {
				amount = Double.parseDouble(tx[1]);
			} catch (Exception ignored) {
			}

			String label = tx[0] == null ? "" : tx[0];
			String type = tx[2] == null ? "" : tx[2];
			String category = tx[3] == null ? "" : tx[3];

			long dateMs = 0;

			try {
				dateMs = Long.parseLong(tx[4]);
			} catch (Exception ignored) {
			}

			boolean isShareSplit = tx.length > 5 && "true".equals(tx[5]);
			boolean isReimbursement = tx.length > 6 && "true".equals(tx[6]);

			String compte = tx.length > 9 ? tx[9] : "";
			boolean isJointTx = "joint".equalsIgnoreCase(compte);

			if (isShareSplit)
				continue;

			boolean isTransferCategory = category.equalsIgnoreCase("Virement") || category.equalsIgnoreCase("Virements")
					|| category.equalsIgnoreCase(com.couplefinance.data.JointAccountManager.JOINT_TRANSFER_CATEGORY);

			String person = tx.length > 8 ? tx[8] : "";

			if (person == null || person.trim().isEmpty()) {
				person = null;

				if (label.contains(" · ")) {
					person = label.split(" · ")[0].trim();

					if (!person.isEmpty()) {
						person = person.substring(0, 1).toUpperCase() + person.substring(1);
					}
				}
			} else {
				person = person.trim();
			}

			if (person != null && !person.isEmpty()) {
				if (person.equalsIgnoreCase("Compte joint") || person.equalsIgnoreCase("Joint")
						|| person.equalsIgnoreCase("Compte commun")) {
					person = null;
				}
			}

			if (person != null && !person.isEmpty()) {
				// "Moi" est un nom legacy — on le redirige vers le vrai prénom de l'utilisateur
				if (person.equalsIgnoreCase("Moi") && myName != null && !myName.isEmpty()) {
					person = myName;
				}

				String existingKey = null;

				for (String k : personBalances.keySet()) {
					if (k.equalsIgnoreCase(person)) {
						existingKey = k;
						break;
					}
				}

				if (existingKey == null) {
					personBalances.put(person, new double[] { 0, 0, 0, 0 });
				} else {
					person = existingKey;
				}
			}

			Calendar txCal = Calendar.getInstance();

			if (dateMs > 0)
				txCal.setTime(new Date(dateMs));

			boolean isCurMonth = txCal.get(Calendar.MONTH) == curMonth && txCal.get(Calendar.YEAR) == curYear;

			boolean isPrevMonth = txCal.get(Calendar.MONTH) == prevMonth && txCal.get(Calendar.YEAR) == prevYear;

			if (isReimbursement || label.contains("Rééquilibrage des dépenses")) {
				recentTx.add(tx);
				continue;
			}

			if (isCurMonth) {
				currentMonthTxCount++;

				if (type.equals("income"))
					currentMonthIncomeCount++;
				else
					currentMonthExpenseCount++;

				if (!type.equals("income")) {
					if (amount > biggestExpenseAmount) {
						biggestExpenseAmount = amount;
						biggestExpenseLabel = label;
						biggestExpenseCategory = category;
					}

					if (category != null && !category.isEmpty())
						activeExpenseCategories.add(category);

					if (txCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
							&& txCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)) {
						todayExpenses += amount;
						todayExpenseCount++;
					}

					if (dateMs > latestUnexpectedExpenseDate && HomeCalculator.isUnexpectedExpense(category, label)) {
						latestUnexpectedExpenseDate = dateMs;
					}
				}

				if (type.equals("income")) {
					String source = category == null || category.isEmpty() ? "Revenus" : category;
					Double prev = incomeSources.get(source);
					incomeSources.put(source, (prev == null ? 0.0 : prev) + amount);
				}

				if (type.equals("income")) {
					totalIncome += amount;
				} else {
					totalExpenses += amount;

					if (category != null && !category.isEmpty()) {
						Double prev = categoryTotals.get(category);
						categoryTotals.put(category, (prev == null ? 0.0 : prev) + amount);
					}
				}

				if (!isTransferCategory && shouldImpactAvailableBalance(dateMs)) {
					if (type.equals("income")) {
						availableIncome += amount;
					} else {
						availableExpenses += amount;
					}
				}

				if (person != null && !isJointTx) {
					String personKey = person;

					for (String k : personBalances.keySet()) {
						if (k.equalsIgnoreCase(person)) {
							personKey = k;
							break;
						}
					}

					double[] vals = personBalances.get(personKey);

					if (vals != null) {
						if (!isTransferCategory) {
							if (type.equals("income")) {
								vals[0] += amount;
							} else {
								vals[1] += amount;
							}
						}

						if (!isTransferCategory && shouldImpactAvailableBalance(dateMs)) {
							if (type.equals("income")) {
								vals[2] += amount;
							} else {
								vals[3] += amount;
							}
						}
					}
				}
			}

			if (isPrevMonth) {
				if (type.equals("income")) {
					prevIncome += amount;
				} else {
					prevExpenses += amount;
				}
			}

			recentTx.add(tx);
		}

		Collections.sort(recentTx, new Comparator<String[]>() {
			@Override
			public int compare(String[] a, String[] b) {
				long da = getTransactionSortTimestamp(a);
				long db = getTransactionSortTimestamp(b);
				return Long.compare(db, da);
			}
		});

		final double monthMovement = totalIncome - totalExpenses;
		final double availableMovement = availableIncome - availableExpenses;
		final double realBalance = commonStartBalance + availableMovement;
		final double projectedEndBalance = realBalance - Math.abs(upcomingFixedCharges);

		final double inc = totalIncome;
		final double exp = totalExpenses;
		final double sav = Math.max(0, realBalance);

		final double pInc = prevIncome;
		final double pExp = prevExpenses;
		final double pSav = Math.max(0, prevIncome - prevExpenses);

		final Map<String, Double> catTotals = categoryTotals;
		final Map<String, Double> incSources = incomeSources;

		final int fCurrentMonthTxCount = currentMonthTxCount;
		final int fCurrentMonthIncomeCount = currentMonthIncomeCount;
		final int fCurrentMonthExpenseCount = currentMonthExpenseCount;
		final int fTodayExpenseCount = todayExpenseCount;

		final double fTodayExpenses = todayExpenses;
		final long fLatestUnexpectedExpenseDate = latestUnexpectedExpenseDate;
		final int fActiveCategoryCount = activeExpenseCategories.size();

		final double fBiggestExpenseAmount = biggestExpenseAmount;
		final String fBiggestExpenseLabel = biggestExpenseLabel;
		final String fBiggestExpenseCategory = biggestExpenseCategory;

		final Map<String, double[]> pBalances = personBalances;
		final List<String[]> latestTx = recentTx.size() > 6 ? recentTx.subList(0, 6) : recentTx;

		final double myStartBalance = monthlyStartBalance;
		final double commonStart = commonStartBalance;
		final String myNameFinal = myName;

		// ── A3 : soldes courants par membre (solde de début + mouvements du mois) ──
		// Utilisés pour détecter les membres en découvert individuellement.
		final Map<String, Double> memberCurrentBalances = new java.util.LinkedHashMap<>();
		for (Map.Entry<String, double[]> entry : personBalances.entrySet()) {
			String memberName = entry.getKey();
			double[] vals = entry.getValue();
			// vals[2] = revenus "disponibles" (depuis date d'ancrage), vals[3] = dépenses disponibles
			double startBal = 0;
			for (Map.Entry<String, Double> mb : memberBalances.entrySet()) {
				if (mb.getKey() != null && mb.getKey().equalsIgnoreCase(memberName)) {
					startBal = mb.getValue() == null ? 0 : mb.getValue();
					break;
				}
			}
			double currentBal = startBal + (vals != null ? vals[2] - vals[3] : 0);
			memberCurrentBalances.put(memberName, currentBal);
		}

		// B1 : calculer le score avant runOnUiThread pour qu'il soit capturable par le lambda
		int membersInOverdraftPre = 0;
		for (Double bal : memberCurrentBalances.values()) {
			if (bal != null && bal < -0.01)
				membersInOverdraftPre++;
		}
		final int fHealthScore = HomeCalculator.financialScoreDetailed(inc, exp, realBalance, overdraftDefined,
				overdraftLimit, membersInOverdraftPre);

		final int fMembersInOverdraft = membersInOverdraftPre;

		activity.runOnUiThread(() -> {
			if (!isActive)
				return;

			// Snapshot pour le partage
			snapshotIncome   = inc;
			snapshotExpenses = exp;
			snapshotBalance  = realBalance;

			renderCalendar();

			// B1 : mise à jour du score de santé et de la barre de progression
			updateHealthScoreWidget(fHealthScore);

			tvExpenses.setText(String.format(Locale.getDefault(), "%,.2f €", exp));
			if (inc > 0.01) {
				int savingsPct = (int) Math.round((sav / inc) * 100.0);
				tvSavings.setText(String.format(Locale.getDefault(), "%,.2f € (%d%%)", sav, savingsPct));
			} else {
				tvSavings.setText(String.format(Locale.getDefault(), "%,.2f €", sav));
			}
			tvIncomeDetail.setText(String.format(Locale.getDefault(), "%,.2f €", inc));

			if (tvIncomeHealth != null)
				tvIncomeHealth.setText(String.format(Locale.getDefault(), "%,.2f €", inc));

			// A2 : message de santé budgétaire dynamique (pas hardcodé)
			if (tvExpensesHealth != null) {
				String budgetMsg;
				if (inc > 0.01) {
					double ratio = exp / inc;
					if (ratio > 1.0)
						budgetMsg = "⚠ Dépenses supérieures aux revenus";
					else if (ratio > 0.9)
						budgetMsg = "⚠ Budget très tendu ce mois-ci";
					else if (ratio > 0.75)
						budgetMsg = "Attention : budget chargé";
					else if (ratio > 0.5)
						budgetMsg = "Budget en cours d'utilisation";
					else
						budgetMsg = "Budget bien maîtrisé ✓";
				} else if (exp > 0.01) {
					budgetMsg = "⚠ Dépenses sans revenu enregistré";
				} else {
					budgetMsg = "Aucune donnée ce mois-ci";
				}
				tvExpensesHealth.setText(budgetMsg);
			}

			if (tvBalance != null) {
				tvBalance.setTextColor(Color.WHITE);
				tvBalance.setAlpha(1f);
				tvBalance.setVisibility(View.VISIBLE);
				animateBalance(tvBalance, realBalance);
			}

			SoldeWidget.updateCache(activity, realBalance, exp, inc);

			updateRealNotifications(inc, exp, realBalance, monthMovement, catTotals, fLatestUnexpectedExpenseDate,
					new java.util.ArrayList<>(), memberCurrentBalances, projectedEndBalance, upcomingFixedChargesCount);

			if (myNameFinal != null && !myNameFinal.trim().isEmpty()) {
				memberBalances.put(myNameFinal.toLowerCase(Locale.FRANCE), myStartBalance);
			}

			renderPersonCards(pBalances, myStartBalance, myNameFinal);
			renderMemberCards(pBalances, myNameFinal);
			renderRecentTransactions(latestTx);

			updateComparison(tvExpensesCompare, exp, pExp, true);
			updateComparison(tvSavingsCompare, sav, pSav, false);

			renderTopCategories(catTotals, exp);

			renderDynamicWidgets(inc, exp, sav, realBalance, projectedEndBalance, monthMovement, pInc, pExp,
					fCurrentMonthTxCount, fCurrentMonthIncomeCount, fCurrentMonthExpenseCount, fTodayExpenses,
					fTodayExpenseCount, fActiveCategoryCount, fBiggestExpenseAmount, fBiggestExpenseLabel,
					fBiggestExpenseCategory, incSources, fMembersInOverdraft);
		});
	}

	private boolean shouldImpactAvailableBalance(long transactionDateMs) {
		if (transactionDateMs <= 0)
			return false;

		long anchor = monthlyStartBalanceDate > 0 ? monthlyStartBalanceDate
				: (commonBalanceAnchorDate > 0 ? commonBalanceAnchorDate
						: BalanceManager.getInstance().getMonthStartMillis());

		Calendar tx = Calendar.getInstance();
		tx.setTimeInMillis(transactionDateMs);
		tx.set(Calendar.HOUR_OF_DAY, 0);
		tx.set(Calendar.MINUTE, 0);
		tx.set(Calendar.SECOND, 0);
		tx.set(Calendar.MILLISECOND, 0);

		Calendar a = Calendar.getInstance();
		a.setTimeInMillis(anchor);
		a.set(Calendar.HOUR_OF_DAY, 0);
		a.set(Calendar.MINUTE, 0);
		a.set(Calendar.SECOND, 0);
		a.set(Calendar.MILLISECOND, 0);

		// Le solde de début de mois est saisi AVANT les opérations du jour
		// d'ancrage. Les transactions datées du jour d'ancrage lui-même doivent
		// donc compter dans le solde courant : comparaison >= (et non > strict),
		// sinon les opérations du jour de saisie sont perdues.
		return tx.getTimeInMillis() >= a.getTimeInMillis();
	}

	private String getBalanceAnchorLabel() {
		long anchor = commonBalanceAnchorDate > 0 ? commonBalanceAnchorDate
				: BalanceManager.getInstance().getMonthStartMillis();

		Calendar a = Calendar.getInstance();
		a.setTimeInMillis(anchor);

		Calendar m = Calendar.getInstance();
		m.set(Calendar.DAY_OF_MONTH, 1);
		m.set(Calendar.HOUR_OF_DAY, 0);
		m.set(Calendar.MINUTE, 0);
		m.set(Calendar.SECOND, 0);
		m.set(Calendar.MILLISECOND, 0);

		if (Math.abs(anchor - m.getTimeInMillis()) < 60_000) {
			return "Début du mois";
		}

		SimpleDateFormat sdf = new SimpleDateFormat("dd/MM", Locale.FRANCE);
		return "Solde saisi le " + sdf.format(new Date(anchor));
	}

	// B1 : mise à jour du score de santé financière — label + barre de progression
	private void updateHealthScoreWidget(int score) {
		// Label score (ex. "72/100")
		if (tvIncomeCompare != null) {
			String scoreText = score + "/100";
			tvIncomeCompare.setText(scoreText);
			int scoreColor;
			if (score >= 85)
				scoreColor = ThemeColors.success();
			else if (score >= 65)
				scoreColor = ThemeColors.warning();
			else if (score >= 45)
				scoreColor = ThemeColors.primary();
			else
				scoreColor = ThemeColors.danger();
			tvIncomeCompare.setTextColor(scoreColor);
		}
		// Barre de progression (viewHealthProgress)
		if (viewHealthProgress != null) {
			viewHealthProgress.post(() -> {
				View parent = (View) viewHealthProgress.getParent();
				if (parent == null)
					return;
				int total = parent.getMeasuredWidth();
				if (total <= 0)
					total = parent.getWidth();
				if (total <= 0)
					return;
				int targetW = (int) (total * Math.max(0, Math.min(100, score)) / 100.0);
				ViewGroup.LayoutParams lp = viewHealthProgress.getLayoutParams();
				lp.width = targetW;
				viewHealthProgress.setLayoutParams(lp);
				// Couleur de la barre selon le score
				int barColor;
				if (score >= 85)
					barColor = ThemeColors.success();
				else if (score >= 65)
					barColor = ThemeColors.warning();
				else if (score >= 45)
					barColor = ThemeColors.primary();
				else
					barColor = ThemeColors.danger();
				android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
				bg.setColor(barColor);
				bg.setCornerRadius(DS.dp(activity, 4));
				viewHealthProgress.setBackground(bg);
			});
		}
	}

	private void setupFinancialGauge() {
		gaugeRefs = new HomeWidgets(activity, dashboardPrefs).installFinancialGauge(scoreGaugeContainer);
	}

	private void updateReferenceFinancialGauge(double income, double expenses, double balance) {
		double safeIncome = income;
		double safeExpenses = expenses;

		if (safeIncome <= 0.01 && safeExpenses > 0.01) {
			safeIncome = safeExpenses * 0.5;
		}

		new HomeWidgets(activity, dashboardPrefs).updateFinancialGauge(gaugeRefs, tvIncomeCompare, safeIncome,
				safeExpenses, balance, overdraftDefined, overdraftLimit);
	}

	private void renderPersonCards(Map<String, double[]> personBalances, double startBalance, String myName) {
		new HomeWidgets(activity, dashboardPrefs).renderPersonCards(personCards, personBars, topCategoriesContainer,
				tvTopCategoriesEmpty, tvTopCategoriesTotal, personBalances, startBalance, myName,
				v -> openDashboardTab(R.id.btnVuePerso));
	}

	private long getTransactionSortTimestamp(String[] tx) {
		if (tx == null || tx.length <= 4)
			return 0;

		try {
			return Long.parseLong(tx[4]);
		} catch (Exception ignored) {
			return 0;
		}
	}

	private void renderRecentTransactions(List<String[]> transactions) {
		new HomeWidgets(activity, dashboardPrefs).renderRecentTransactions(categoryBars, transactions,
				v -> openDashboardTab(R.id.btnTransactions));
	}

	private void renderCalendar() {
		new HomeWidgets(activity, dashboardPrefs).renderSevenDayCalendar(calendarGrid, tvCalMonth, calendarMonth,
				cachedTransactions);
	}

	private String getMonthlyBalanceCacheKey() {
		Calendar cal = Calendar.getInstance();
		String month = cal.get(Calendar.YEAR) + "-" + String.format("%02d", cal.get(Calendar.MONTH) + 1);
		String uid = AuthManager.getInstance().getUserId();
		return "monthly_start_balance_" + uid + "_" + month;
	}

	private void saveMonthlyBalanceLocal(double amount) {
		HomeData.saveMonthlyBalance(activity, amount);
	}

	private Double getMonthlyBalanceLocal() {
		return HomeData.getMonthlyBalance(activity);
	}

	private void animateBalance(TextView target, double newValue) {
		if (target == null)
			return;

		target.setTextColor(Color.WHITE);
		target.setAlpha(1f);
		target.setVisibility(View.VISIBLE);
		target.setTextSize(DS.TEXT_DISPLAY);
		target.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
		target.setLetterSpacing(-0.03f);
		target.setIncludeFontPadding(false);

		double start = hasAnimatedOnce ? lastAnimatedBalance : newValue;

		ValueAnimator animator = ValueAnimator.ofFloat((float) start, (float) newValue);
		animator.setDuration(hasAnimatedOnce ? DS.ANIM_HERO : DS.ANIM_NORMAL);
		animator.setInterpolator(new DecelerateInterpolator());

		animator.addUpdateListener(animation -> {
			float value = (float) animation.getAnimatedValue();
			target.setText(String.format(Locale.FRANCE, "%,.2f €", value));
		});

		animator.start();

		lastAnimatedBalance = newValue;
		hasAnimatedOnce = true;
	}

	private int getBalanceColor(double balance) {
		return balanceColorLocal(balance);
	}

	private GradientDrawable buildCircle(int color) {
		return buildCircleLocal(color);
	}

	private String extractStr(String json, String key) {
		return extractStrLocal(json, key);
	}

	private String extractNum(String json, String key) {
		return extractNumLocal(json, key);
	}
	// ═══════════════════════════════════════════════════════
	// ✨ ÉTAPE 1 : Greeting dynamique + Barre progression mois
	// ═══════════════════════════════════════════════════════

	/**
	 * Met à jour le texte de salutation et l'emoji selon l'heure de la journée.
	 * Format : "Bonjour Thomas," + ☀️/🌙
	 */
	private void updateGreeting() {
		if (tvGreeting == null)
			return;

		int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

		String greeting;
		String emoji;

		if (hour >= 5 && hour < 12) {
			greeting = "Bonjour";
			emoji = " ☀️";
		} else if (hour >= 12 && hour < 18) {
			greeting = "Bon après-midi";
			emoji = " ☀️";
		} else if (hour >= 18 && hour < 22) {
			greeting = "Bonsoir";
			emoji = " 🌙";
		} else {
			greeting = "Bonne nuit";
			emoji = " 🌙";
		}

		String name = getMyName();
		if (name != null && !name.isEmpty()) {
			tvGreeting.setText(greeting + " " + name + ",");
		} else {
			tvGreeting.setText(greeting + ",");
		}

		if (tvGreetingEmoji != null) {
			tvGreetingEmoji.setText(emoji);
		}
	}

	/**
	 * Met à jour la barre de progression du mois en cours.
	 * Affiche : "Jour X sur Y", "Z%", "N jours restants"
	 * La barre s'anime de 0% à la valeur réelle, et passe en orange au-delà de 80%.
	 */
	private void updateMonthProgress() {
		if (viewMonthProgressFill == null)
			return;

		Calendar cal = Calendar.getInstance();
		int currentDay = cal.get(Calendar.DAY_OF_MONTH);
		int totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
		int remaining = totalDays - currentDay;

		final float progress = Math.min(1f, (float) currentDay / (float) totalDays);
		final int pct = Math.round(progress * 100);

		// Texte
		if (tvMonthProgressLabel != null)
			tvMonthProgressLabel.setText("Cette semaine");

		if (tvMonthProgressPct != null)
			tvMonthProgressPct.setText(pct + "%");

		if (tvMonthProgressDetail != null) {
			if (remaining == 0)
				tvMonthProgressDetail.setText("Dernier jour du mois");
			else if (remaining == 1)
				tvMonthProgressDetail.setText("1 jour restant");
			else
				tvMonthProgressDetail.setText(remaining + " jours restants");
		}

		// Couleur de la barre : terracotta normalement, orange si > 80%
		final int fillColor = (pct >= 80) ? ThemeColors.warning() : ThemeColors.primary();
		final int pctColor = fillColor;

		if (tvMonthProgressPct != null)
			tvMonthProgressPct.setTextColor(pctColor);

		// Met à jour la couleur du drawable de remplissage
		try {
			android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
			bg.setColor(fillColor);
			bg.setCornerRadius(DS.dp(activity, 4));
			viewMonthProgressFill.setBackground(bg);
		} catch (Exception ignored) {
		}

		// Animation : on attend que la vue ait sa taille mesurée
		viewMonthProgressFill.post(() -> {
			View parent = (View) viewMonthProgressFill.getParent();
			if (parent == null)
				return;

			final int parentWidth = parent.getWidth();
			if (parentWidth <= 0)
				return;

			final int finalWidth = Math.round(parentWidth * progress);

			ValueAnimator animator = ValueAnimator.ofInt(0, finalWidth);
			animator.setDuration(900);
			animator.setInterpolator(new DecelerateInterpolator());
			animator.addUpdateListener(anim -> {
				int v = (int) anim.getAnimatedValue();
				ViewGroup.LayoutParams lp = viewMonthProgressFill.getLayoutParams();
				lp.width = v;
				viewMonthProgressFill.setLayoutParams(lp);
			});
			animator.start();
		});
	}

	// ═══════════════════════════════════════════════════════
	// ✨ ÉTAPE 2 : Comparaison mois précédent + Top catégories
	// ═══════════════════════════════════════════════════════

	/**
	 * Met à jour un TextView avec la comparaison entre la valeur actuelle et le mois précédent.
	 *
	 * @param tv             TextView à mettre à jour
	 * @param current        Valeur du mois en cours
	 * @param previous       Valeur du mois précédent
	 * @param invertColors   Si true, baisse = vert (cas des dépenses) ; sinon hausse = vert
	 */
	private void updateComparison(TextView tv, double current, double previous, boolean invertColors) {
		if (tv == null)
			return;

		// Pas de mois précédent ou trop petit pour comparaison fiable
		if (previous < 0.01) {
			if (current < 0.01) {
				tv.setText(" ");
			} else {
				tv.setText("Pas de comparaison");
				tv.setTextColor(ThemeColors.subtext());
			}
			return;
		}

		double diff = current - previous;
		double pct = (diff / previous) * 100.0;

		String arrow;
		int color;

		if (Math.abs(pct) < 0.5) {
			arrow = "→";
			color = ThemeColors.subtext();
		} else if (pct > 0) {
			arrow = "↑";
			color = invertColors ? ThemeColors.danger() : ThemeColors.success();
		} else {
			arrow = "↓";
			color = invertColors ? ThemeColors.success() : ThemeColors.danger();
		}

		String prevMonthShort = getPrevMonthShort();
		String pctText = String.format(Locale.getDefault(), "%+.0f%%", pct);

		tv.setText(arrow + " " + pctText + " vs " + prevMonthShort);
		tv.setTextColor(color);
	}

	/**
	 * Renvoie le nom court du mois précédent (ex : "oct.", "nov.").
	 */
	private String getPrevMonthShort() {
		return prevMonthShortLocal();
	}

	/**
	 * Charge les emojis associés à chaque catégorie (depuis CategoryManager).
	 * Stocké dans `categoryEmojis` (catégorie → emoji).
	 */

	private void renderDynamicWidgets(double income, double expenses, double savings, double realBalance,
			double projectedEndBalance, double monthMovement, double prevIncome, double prevExpenses, int txCount,
			int incomeCount, int expenseCount, double todayExpenses, int todayExpenseCount, int activeCategoryCount,
			double biggestExpenseAmount, String biggestExpenseLabel, String biggestExpenseCategory,
			Map<String, Double> incomeSources) {
		renderDynamicWidgets(income, expenses, savings, realBalance, projectedEndBalance, monthMovement, prevIncome,
				prevExpenses, txCount, incomeCount, expenseCount, todayExpenses, todayExpenseCount, activeCategoryCount,
				biggestExpenseAmount, biggestExpenseLabel, biggestExpenseCategory, incomeSources, 0);
	}

	private void renderDynamicWidgets(double income, double expenses, double savings, double realBalance,
			double projectedEndBalance, double monthMovement, double prevIncome, double prevExpenses, int txCount,
			int incomeCount, int expenseCount, double todayExpenses, int todayExpenseCount, int activeCategoryCount,
			double biggestExpenseAmount, String biggestExpenseLabel, String biggestExpenseCategory,
			Map<String, Double> incomeSources, int membersInOverdraft) {
		if (dynamicWidgetsContainer == null)
			return;

		new HomeWidgets(activity, dashboardPrefs).renderDynamicWidgets(dynamicWidgetsContainer, income, expenses,
				realBalance, projectedEndBalance, monthMovement, txCount, incomeCount, expenseCount, todayExpenses,
				todayExpenseCount, activeCategoryCount, biggestExpenseAmount, biggestExpenseLabel,
				biggestExpenseCategory, incomeSources, commonStartBalance, PREF_ORDER_DYNAMIC, membersInOverdraft);

		// Widget objectifs d'épargne (chargement async, s'ajoute en bas)
		new HomeWidgets(activity, dashboardPrefs).renderSavingsGoalsCard(dynamicWidgetsContainer);
	}

	private String formatMoney(double value) {
		return Fmt.money(value);
	}

	private void loadCategoryEmojis() {
		com.couplefinance.data.CategoryManager.getInstance().getCategories(new FirestoreManager.Callback() {
			public void onSuccess(String response) {
				Map<String, String> parsed = new HashMap<>();
				Map<String, Double> parsedBudgets = new HashMap<>();
				String[] parts = response.split("\"fields\":");

				for (int i = 1; i < parts.length; i++) {
					String name = "";
					String emoji = "";
					double budget = 0;

					if (parts[i].contains("\"name\""))
						name = extractStr(parts[i].substring(parts[i].indexOf("\"name\"")), "stringValue");

					if (parts[i].contains("\"emoji\""))
						emoji = extractStr(parts[i].substring(parts[i].indexOf("\"emoji\"")), "stringValue");

					if (parts[i].contains("\"budget\"")) {
						try {
							String budgetPart = parts[i].substring(parts[i].indexOf("\"budget\""));
							String val = budgetPart.contains("doubleValue")
									? extractNum(budgetPart.substring(budgetPart.indexOf("doubleValue")), "doubleValue")
									: extractNum(budgetPart.substring(budgetPart.indexOf("integerValue")),
											"integerValue");
							budget = Double.parseDouble(val);
						} catch (Exception ignored) {
						}
					}

					if (!name.isEmpty()) {
						parsed.put(name, emoji.isEmpty() ? "📊" : emoji);
						if (budget > 0)
							parsedBudgets.put(name, budget);
					}
				}

				categoryEmojis = parsed;
				categoryBudgets = parsedBudgets;
				if (cachedTransactions != null && !cachedTransactions.isEmpty()) {
					loadFinancialSettingsThenProcess();
				}
			}

			public void onError(String e) {
				// silencieux : on continue avec un emoji par défaut
			}
		});
	}

	/**
	 * Affiche le top 3 des catégories de dépenses du mois en cours sous forme de barres.
	 */
	private void renderTopCategories(Map<String, Double> categoryTotals, double totalExpenses) {
		if (topCategoriesContainer == null)
			return;

		topCategoriesContainer.removeAllViews();

		if (categoryTotals == null || categoryTotals.isEmpty() || totalExpenses < 0.01) {
			if (tvTopCategoriesEmpty != null)
				tvTopCategoriesEmpty.setVisibility(View.VISIBLE);
			if (tvTopCategoriesTotal != null)
				tvTopCategoriesTotal.setText("");
			return;
		}

		if (tvTopCategoriesEmpty != null)
			tvTopCategoriesEmpty.setVisibility(View.GONE);

		// Trier par montant décroissant et garder les 3 premières
		List<Map.Entry<String, Double>> sorted = new ArrayList<>(categoryTotals.entrySet());
		Collections.sort(sorted, (a, b) -> Double.compare(b.getValue(), a.getValue()));

		List<Map.Entry<String, Double>> top3 = sorted.size() > 3 ? sorted.subList(0, 3) : sorted;

		// Total affiché (somme du top 3)
		double top3Sum = 0;
		for (Map.Entry<String, Double> e : top3)
			top3Sum += e.getValue();

		if (tvTopCategoriesTotal != null) {
			int pctOfTotal = (int) Math.round((top3Sum / totalExpenses) * 100);
			tvTopCategoriesTotal.setText(pctOfTotal + "% du total");
		}

		// Le max pour normaliser les barres
		double max = top3.get(0).getValue();

		// Couleurs des barres (cycle terracotta → vert → or)
		int[] barColors = { ThemeColors.primary(), ThemeColors.success(), ThemeColors.warning() };

		int idx = 0;
		for (Map.Entry<String, Double> entry : top3) {
			String catName = entry.getKey();
			double catAmount = entry.getValue();
			String emoji = categoryEmojis.containsKey(catName) ? categoryEmojis.get(catName) : "📊";
			int barColor = barColors[idx % barColors.length];

			View row = buildTopCategoryRow(emoji, catName, catAmount, max, barColor, idx == top3.size() - 1);
			topCategoriesContainer.addView(row);
			idx++;
		}
	}

	/**
	 * Construit une ligne du top catégorie : [emoji] [nom] [barre] [montant].
	 */
	private View buildTopCategoryRow(String emoji, String name, double amount, double max, int barColor,
			boolean isLast) {
		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.VERTICAL);

		LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		if (!isLast)
			rowLp.setMargins(0, 0, 0, DS.dp(activity, 12));
		row.setLayoutParams(rowLp);

		// Ligne du haut : emoji + nom + montant
		LinearLayout topLine = new LinearLayout(activity);
		topLine.setOrientation(LinearLayout.HORIZONTAL);
		topLine.setGravity(android.view.Gravity.CENTER_VERTICAL);
		LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		topLp.setMargins(0, 0, 0, DS.dp(activity, 6));
		topLine.setLayoutParams(topLp);

		TextView tvEmoji = new TextView(activity);
		tvEmoji.setText(emoji);
		tvEmoji.setTextSize(15f);
		LinearLayout.LayoutParams emojiLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		emojiLp.setMargins(0, 0, DS.dp(activity, 8), 0);
		tvEmoji.setLayoutParams(emojiLp);
		topLine.addView(tvEmoji);

		TextView tvName = new TextView(activity);
		tvName.setText(name);
		tvName.setTextSize(13f);
		tvName.setTextColor(ThemeColors.text());
		tvName.setTypeface(null, Typeface.BOLD);
		tvName.setSingleLine(true);
		tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
		LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		tvName.setLayoutParams(nameLp);
		topLine.addView(tvName);

		TextView tvAmount = new TextView(activity);
		tvAmount.setText(String.format(Locale.getDefault(), "%,.0f €", amount));
		tvAmount.setTextSize(13f);
		tvAmount.setTextColor(ThemeColors.text());
		tvAmount.setTypeface(null, Typeface.BOLD);
		topLine.addView(tvAmount);

		row.addView(topLine);

		// Piste de la barre
		FrameLayout track = new FrameLayout(activity);
		LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				DS.dp(activity, 6));
		track.setLayoutParams(trackLp);

		GradientDrawable trackBg = new GradientDrawable();
		trackBg.setColor(ThemeColors.divider());
		trackBg.setCornerRadius(DS.dp(activity, 3));
		track.setBackground(trackBg);

		// Remplissage
		final View fill = new View(activity);
		FrameLayout.LayoutParams fillLp = new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
		fill.setLayoutParams(fillLp);

		GradientDrawable fillBg = new GradientDrawable();
		fillBg.setColor(barColor);
		fillBg.setCornerRadius(DS.dp(activity, 3));
		fill.setBackground(fillBg);

		track.addView(fill);
		row.addView(track);

		// Animation de la barre
		final float ratio = max > 0 ? (float) (amount / max) : 0f;
		track.post(() -> {
			int trackWidth = track.getWidth();
			if (trackWidth <= 0)
				return;

			final int finalWidth = Math.round(trackWidth * ratio);

			ValueAnimator animator = ValueAnimator.ofInt(0, finalWidth);
			animator.setDuration(700);
			animator.setInterpolator(new DecelerateInterpolator());
			animator.addUpdateListener(anim -> {
				int v = (int) anim.getAnimatedValue();
				ViewGroup.LayoutParams lp = fill.getLayoutParams();
				lp.width = v;
				fill.setLayoutParams(lp);
			});
			animator.start();
		});

		return row;
	}

	// =========================
	// 🔥 WIDGET SYSTEM CORE - STABLE
	// IMPORTANT : les clés doivent être EXACTEMENT les mêmes que les constantes W_*
	// sinon applyDashboardSectionOrder() ne peut pas retrouver les vues.
	// =========================

	private String[] getAllWidgetKeys() {
		return HomeWidgetRegistry.getAllWidgetKeys();
	}

	private String[] getAllWidgetTitles() {
		return HomeWidgetRegistry.getAllWidgetTitles();
	}

	private String[] getSectionKeys() {
		return HomeWidgetRegistry.getSectionKeys();
	}

	private String[] getSectionTitles() {
		return HomeWidgetRegistry.getSectionTitles();
	}

	private String[] getDynamicKeys() {
		return HomeWidgetRegistry.getDynamicKeys();
	}

	private String[] getDynamicTitles() {
		return HomeWidgetRegistry.getDynamicTitles();
	}

	private java.util.Map<String, String> avatarCache;

	private void applyAvatarsToCards(List<HomeMemberCard.Data> list) {
		if (list == null || avatarCache == null) return;
		for (HomeMemberCard.Data d : list) {
			if (d == null || d.name == null) continue;
			for (Map.Entry<String, String> e : avatarCache.entrySet()) {
				if (e.getKey() != null && e.getKey().equalsIgnoreCase(d.name)) {
					if (e.getValue() != null && !e.getValue().isEmpty()) d.avatar = e.getValue();
					break;
				}
			}
		}
	}

	private void renderMemberCards(Map<String, double[]> personBalances, String myName) {
		if (memberSectionContainer == null)
			return;

		Map<String, double[]> cleanBalances = new LinkedHashMap<>();

		if (personBalances != null) {
			for (Map.Entry<String, double[]> e : personBalances.entrySet()) {
				String name = e.getKey();

				if (name == null || name.trim().isEmpty())
					continue;

				if (name.equalsIgnoreCase("Compte joint") || name.equalsIgnoreCase("Joint")
						|| name.equalsIgnoreCase("Compte commun"))
					continue;

				cleanBalances.put(name, e.getValue());
			}
		}

		Map<String, String> colors = new HashMap<>();

		try {
			com.couplefinance.ui.settings.SettingsModels.State state = com.couplefinance.ui.settings.SettingsCache
					.get();

			if (state != null && state.members != null) {
				for (com.couplefinance.ui.settings.SettingsModels.Member m : state.members) {
					if (m != null && m.name != null && m.color != null)
						colors.put(m.name, m.color);
				}
			}
		} catch (Exception ignored) {
		}

		List<HomeMemberCard.Data> memberDataList = HomeMemberSection.buildMemberDataList(cleanBalances, memberBalances,
				myName, colors, upcomingChargesByMember, upcomingChargesCountByMember);

	boolean jointEnabled =
        com.couplefinance.data.JointAccountManager.getInstance().isEnabledLocal();

String jointName =
        com.couplefinance.data.JointAccountManager.getInstance().getNameLocal();

HomeMemberCard.Data jointData = jointEnabled
        ? HomeMemberSection.buildJointData(
                cachedTransactions,
                upcomingChargesByMember,
                upcomingChargesCountByMember)
        : null;

		// ── Soldes "live" de la synchro bancaire (affichage "Solde actuel") ──
		// Lecture autonome des préférences (ne dépend que de ce fichier + HomeMemberCard).
		if (memberDataList != null) {
			for (HomeMemberCard.Data d : memberDataList) {
				if (d == null || d.name == null) continue;
				double live = liveBalanceFor(d.name);
				if (!Double.isNaN(live)) {
					// Le solde live (banque) est prioritaire sur le solde calculé.
					// On l'applique directement à currentBalance/forecastBalance car
					// buildMemberDataList() écrase currentBalance APRÈS compute() — il
					// faut donc le réappliquer ici, après coup.
					d.liveBalance = live;
					d.currentBalance = live;
					d.forecastBalance = live - Math.max(0, d.upcomingExpenses);
				}
				// Avatar animal de l'utilisateur courant (depuis la session)
				if (myName != null && d.name.equalsIgnoreCase(myName)) {
					try {
						String myAvatar = com.couplefinance.AuthManager.getInstance().getLocalAvatar();
						if (myAvatar == null || myAvatar.isEmpty()) {
							com.couplefinance.models.UserProfile up = UserSession.getInstance().getUser();
							if (up != null) myAvatar = up.getAvatar();
						}
						if (myAvatar != null && !myAvatar.isEmpty()) d.avatar = myAvatar;
					} catch (Exception ignored) {
					}
				}
			}
		}
		if (jointData != null) {
			double live = liveBalanceFor(jointName);
			if (!Double.isNaN(live)) {
				jointData.liveBalance = live;
				jointData.currentBalance = live;
				jointData.forecastBalance = live - Math.max(0, jointData.upcomingExpenses);
				// Propagate live bank balance to Firestore so the other device sees the same value.
				try {
					com.couplefinance.data.JointAccountManager.getInstance()
							.saveMonthlyStartBalance(activity.getApplicationContext(), live, null);
				} catch (Exception ignored) { }
			}
		}

		if (memberSection == null)
			memberSection = new HomeMemberSection(activity);

		applyAvatarsToCards(memberDataList);
		memberSection.render(memberSectionContainer, memberDataList, jointData, jointEnabled);

		// Avatars des autres membres : requête users par householdId, puis re-render
		// avatarCache null = pas encore chargé ; empty = chargé mais vide (on ré-essaiera)
		if (avatarCache == null || avatarCache.isEmpty()) {
			final List<HomeMemberCard.Data> fdl = memberDataList;
			final HomeMemberCard.Data fjd = jointData;
			final boolean fje = jointEnabled;
			String hh = com.couplefinance.data.HouseholdManager.getInstance().getHouseholdId();
			if (hh != null && !hh.isEmpty()) {
				com.couplefinance.UserRepository.getInstance().loadHouseholdAvatars(hh, map -> {
					if (map != null && !map.isEmpty()) {
						avatarCache = map;
						applyAvatarsToCards(fdl);
						if (memberSection != null)
							memberSection.render(memberSectionContainer, fdl, fjd, fje);
					}
				});
			}
		}
	}

	/**
	 * Lit le solde "live" d'un compte directement depuis les préférences de la
	 * synchro bancaire (la même source que la notification). Autonome : ne dépend
	 * d'aucune autre classe. Renvoie NaN si aucun solde pour ce libellé.
	 */
	private double liveBalanceFor(String label) {
    if (label == null || label.trim().isEmpty()) {
        return Double.NaN;
    }

    android.content.SharedPreferences sp =
            activity.getSharedPreferences(
                    "bank_autosync_prefs",
                    android.content.Context.MODE_PRIVATE);

    String disp = sp.getString("autosync_live_balances", "");
    if (disp == null || disp.trim().isEmpty()) {
        return Double.NaN;
    }

    String want = normalizeBalanceLabel(label);
    String wantFirst = want.split("\\s+")[0];

    Double exact = null;
    Double byFirst = null;
    Double byContains = null;

    for (String part : disp.split("·")) {
        int sep = part.indexOf(" : ");
        if (sep < 0) {
            continue;
        }

        String key = normalizeBalanceLabel(part.substring(0, sep));

        String num = part.substring(sep + 3)
                .replace("€", "")
                .replace("\u2212", "-")
                .replace(" ", "")
                .replace("\u00A0", "")
                .trim()
                .replace(",", ".");

        double val;
        try {
            val = Double.parseDouble(num);
        } catch (Exception e) {
            continue;
        }

        if (key.equals(want)) {
            exact = val;
        } else {
            String keyFirst = key.split("\\s+")[0];

            if (keyFirst.equals(wantFirst)
                    || want.equals(keyFirst)) {
                byFirst = val;
            } else if (key.contains(want)
                    || want.contains(key)) {
                byContains = val;
            }
        }
    }

    if (exact != null) {
        return exact;
    }

    if (byFirst != null) {
        return byFirst;
    }

    if (byContains != null) {
        return byContains;
    }

    return Double.NaN;
}

private String normalizeBalanceLabel(String value) {
    if (value == null) {
        return "";
    }

    return java.text.Normalizer.normalize(
                    value,
                    java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(java.util.Locale.FRENCH)
            .trim();
}
}