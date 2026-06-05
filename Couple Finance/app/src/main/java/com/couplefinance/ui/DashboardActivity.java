package com.couplefinance.ui;

import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.content.Intent;

import com.couplefinance.AppToast;
import com.couplefinance.UserSession;
import com.couplefinance.UserRepository;
import com.couplefinance.AuthManager;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.animations.TabTransition;
import com.couplefinance.data.FirestoreManager;
import com.couplefinance.LoginActivity;
import com.couplefinance.R;
import com.couplefinance.data.BalanceManager;
import com.couplefinance.data.HouseholdManager;
import com.couplefinance.data.CycleManager;
import com.couplefinance.data.JointAccountManager;
import com.couplefinance.data.TransactionManager;
import com.couplefinance.data.UserManager;
import com.couplefinance.ui.agenda.AgendaView;
import com.couplefinance.ui.budget.BudgetView;
import com.couplefinance.ui.credits.CreditsView;
import com.couplefinance.ui.epargne.EpargneView;
import com.couplefinance.ui.home.HomeView;
import com.couplefinance.ui.repartition.RepartitionView;
import com.couplefinance.ui.settings.SettingsView;
import com.couplefinance.ui.transactions.TransactionsView;
import com.couplefinance.ui.virements.VirementView;
import com.couplefinance.data.CreditManager;
import com.couplefinance.data.RecurringChargeManager;
import com.couplefinance.data.BankAutoSyncManager;
import com.couplefinance.data.TelegramScheduler;
import com.couplefinance.utils.NotificationHelper;
import com.couplefinance.utils.NotificationScheduler;
import com.couplefinance.ui.analyse.AnalyseView;

public class DashboardActivity extends Activity {

	private FrameLayout container;
	private LinearLayout sidebar;
	private TextView tvFirebaseStatus;

	private Button btnHome, btnAgenda, btnTransactions, btnVirements;
	private Button btnBudget, btnEpargne, btnCredits, btnVuePerso, btnSettings;
	private Button btnAbonnements;
	private Button btnAnalyse;

	private LinearLayout bottomNav;
	private LinearLayout tabHome, tabTransactions, tabBudget, tabEpargne, tabMore;
	private TextView tabLabelHome, tabLabelTransactions, tabLabelBudget, tabLabelEpargne, tabLabelMore;
	private TextView tabIconHome, tabIconTransactions, tabIconBudget, tabIconEpargne, tabIconMore;

	private View sidebarScrim;
	private boolean isPhoneMode = false;
	private int currentPhoneTab = 0;

	private TransactionsView currentTransactionsView;
	private boolean isFirstResume = true;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		BalanceManager.getInstance().init(this);

		// ── Cycle financier configurable ─────────────────────────────────
		// Initialiser CycleManager après BalanceManager.
		// loadFromFirestore synchronise le cycleStartDay entre les deux membres
		// puis reprogramme l'alarme de saisie de solde au bon jour.
		CycleManager.getInstance().init(this);
		CycleManager.getInstance().loadFromFirestore(new FirestoreManager.Callback() {
			@Override
			public void onSuccess(String result) {
				NotificationScheduler.scheduleAll(DashboardActivity.this);
			}

			@Override
			public void onError(String error) {
				// Erreur réseau : on planifie quand même avec le jour local
				NotificationScheduler.scheduleAll(DashboardActivity.this);
			}
		});

		AuthManager.getInstance().init(this);
		HouseholdManager.getInstance().init(this);
		JointAccountManager.getInstance().init(this);
		setContentView(R.layout.activity_dashboard);
		initViews();
		detectMode();
		if (isPhoneMode)
			setupBottomNavigation();
		else {
			setupSidebarNavigation();
			applyThemeToSidebar();
		}
		// Modal "solde début du mois" retiré du lancement : la saisie se fait
		// désormais via l'étape de connexion bancaire (onboarding) ou les Paramètres.
		// (La méthode reste utilisée par la notification de début de cycle.)
		// checkMonthlyBalance();
		navigateToHome();
		requestNotificationPermission();
		UserManager.getInstance().registerCurrentUserAsMember();
		try {
			RecurringChargeManager.getInstance().init(this);
			RecurringChargeManager.getInstance().checkAndApplyRecurringCharges(null);
			CreditManager.getInstance().init(this);
			CreditManager.getInstance().checkAndApplyCredits(null);
			NotificationHelper.getInstance(this).notifyPendingFixedCharges(3);
			TelegramScheduler.checkAndSend(this);
			if (BankAutoSyncManager.isEnabled(this)) BankAutoSyncManager.scheduleDaily(this);
		} catch (Exception ignored) {
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (isPhoneMode)
			applyThemeToBottomNav();
		else
			applyThemeToSidebar();
		updateSidebarUser();
		checkFirebaseStatus();
		if (!isFirstResume) {
			try {
				UserManager.getInstance().checkIfStillMember(this::showExpelledDialog);
			} catch (Exception ignored) {
			}
			try {
				RecurringChargeManager.getInstance().checkReminderIfNeeded();
			} catch (Exception ignored) {
			}
		}
		isFirstResume = false;
	}

	/**
	 * Intercepte les intents entrants (ex : tap sur notification de début de cycle).
	 * Si l'extra EXTRA_OPEN_BALANCE_DIALOG est présent, ouvre le dialog de saisie de solde.
	 */
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		if (intent != null
				&& intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_BALANCE_DIALOG, false)) {
			showMonthlyBalanceDialog();
		}
	}

	private void initViews() {
		container = findViewById(R.id.container);
		sidebar = findViewById(R.id.sidebar);
		tvFirebaseStatus = findViewById(R.id.tvFirebaseStatus);
		sidebarScrim = findViewById(R.id.sidebarScrim);
		btnHome = findViewById(R.id.btnHome);
		btnAgenda = findViewById(R.id.btnAgenda);
		btnTransactions = findViewById(R.id.btnTransactions);
		btnVirements = findViewById(R.id.btnVirements);
		btnBudget = findViewById(R.id.btnBudget);
		btnEpargne = findViewById(R.id.btnEpargne);
		btnCredits = findViewById(R.id.btnCredits);
		btnVuePerso = findViewById(R.id.btnVuePerso);
		btnSettings = findViewById(R.id.btnSettings);
		btnAbonnements = findViewById(R.id.btnAbonnements);
		btnAnalyse = findViewById(R.id.btnAnalyse);
		bottomNav = findViewById(R.id.bottomNav);
		tabHome = findViewById(R.id.tabHome);
		tabTransactions = findViewById(R.id.tabTransactions);
		tabBudget = findViewById(R.id.tabBudget);
		tabEpargne = findViewById(R.id.tabEpargne);
		tabMore = findViewById(R.id.tabMore);
		tabLabelHome = findViewById(R.id.tabLabelHome);
		tabLabelTransactions = findViewById(R.id.tabLabelTransactions);
		tabLabelBudget = findViewById(R.id.tabLabelBudget);
		tabLabelEpargne = findViewById(R.id.tabLabelEpargne);
		tabLabelMore = findViewById(R.id.tabLabelMore);
		tabIconHome = findViewById(R.id.tabIconHome);
		tabIconTransactions = findViewById(R.id.tabIconTransactions);
		tabIconBudget = findViewById(R.id.tabIconBudget);
		tabIconEpargne = findViewById(R.id.tabIconEpargne);
		tabIconMore = findViewById(R.id.tabIconMore);
	}

	private void detectMode() {
		isPhoneMode = (bottomNav != null);
	}

	private void setupBottomNavigation() {
		if (tabHome != null)
			tabHome.setOnClickListener(v -> switchPhoneTab(0));
		if (tabTransactions != null)
			tabTransactions.setOnClickListener(v -> switchPhoneTab(1));
		if (tabBudget != null)
			tabBudget.setOnClickListener(v -> switchPhoneTab(2));
		if (tabEpargne != null)
			tabEpargne.setOnClickListener(v -> switchPhoneTab(3));
		if (tabMore != null)
			tabMore.setOnClickListener(v -> openSidebar());
		TextView hamburger = findViewById(R.id.btnHamburger);
		if (hamburger != null)
			hamburger.setOnClickListener(v -> openSidebar());
		View btnClose = findViewById(R.id.btnCloseSidebar);
		if (btnClose != null)
			btnClose.setOnClickListener(v -> closeSidebar());
		if (sidebarScrim != null)
			sidebarScrim.setOnClickListener(v -> closeSidebar());
		setupSidebarNavigation();
		applyThemeToBottomNav();
		selectPhoneTab(0);
	}

	private void switchPhoneTab(int index) {
		currentPhoneTab = index;
		selectPhoneTab(index);
		switch (index) {
		case 0:
			showHome();
			break;
		case 1:
			showTransactions();
			break;
		case 2:
			showBudget();
			break;
		case 3:
			showEpargne();
			break;
		}
	}

	private void selectPhoneTab(int index) {
		LinearLayout[] tabs = { tabHome, tabTransactions, tabBudget, tabEpargne, tabMore };
		TextView[] labels = { tabLabelHome, tabLabelTransactions, tabLabelBudget, tabLabelEpargne, tabLabelMore };
		TextView[] icons = { tabIconHome, tabIconTransactions, tabIconBudget, tabIconEpargne, tabIconMore };

		int active = Color.WHITE;
		int inactive = Color.parseColor("#8A7A70");
		int activeBg = ThemeColors.primary();

		for (int i = 0; i < tabs.length; i++) {
			if (tabs[i] == null)
				continue;
			boolean sel = (i == index);

			if (sel) {
				GradientDrawable bg = new GradientDrawable();
				bg.setColor(activeBg);
				bg.setCornerRadius(DS.dp(this, 22));
				tabs[i].setBackground(bg);
				if (labels[i] != null) {
					labels[i].setVisibility(View.VISIBLE);
					labels[i].setTextColor(active);
					labels[i].setTypeface(null, Typeface.BOLD);
				}
				if (icons[i] != null)
					icons[i].setTextColor(active);
			} else {
				tabs[i].setBackground(null);
				if (labels[i] != null) {
					labels[i].setVisibility(View.GONE);
				}
				if (icons[i] != null)
					icons[i].setTextColor(inactive);
			}
		}
	}

	private void setupSidebarNavigation() {
		if (btnHome != null)
			btnHome.setOnClickListener(v -> sidebarNav(btnHome, this::showHome));
		if (btnAgenda != null)
			btnAgenda.setOnClickListener(v -> sidebarNav(btnAgenda, this::showAgenda));
		if (btnTransactions != null)
			btnTransactions.setOnClickListener(v -> sidebarNav(btnTransactions, this::showTransactions));
		if (btnVirements != null)
			btnVirements.setOnClickListener(v -> sidebarNav(btnVirements, this::showVirements));
		if (btnBudget != null)
			btnBudget.setOnClickListener(v -> sidebarNav(btnBudget, this::showBudget));
		if (btnEpargne != null)
			btnEpargne.setOnClickListener(v -> sidebarNav(btnEpargne, this::showEpargne));
		if (btnCredits != null)
			btnCredits.setOnClickListener(v -> sidebarNav(btnCredits, this::showCredits));
		if (btnVuePerso != null)
			btnVuePerso.setOnClickListener(v -> sidebarNav(btnVuePerso, this::showVuePerso));
		if (btnSettings != null)
			btnSettings.setOnClickListener(v -> sidebarNav(btnSettings, this::showSettings));
		if (btnAbonnements != null)
			btnAbonnements.setOnClickListener(v -> sidebarNav(btnAbonnements, this::showAbonnements));
		if (btnAnalyse != null)
			btnAnalyse.setOnClickListener(v -> sidebarNav(btnAnalyse, this::showAnalyse));
	}

	private void sidebarNav(Button btn, Runnable action) {
		if (!isPhoneMode)
			setActiveButton(btn);
		action.run();
		if (isPhoneMode)
			closeSidebar();
	}

	private void openSidebar() {
		if (sidebar == null)
			return;
		sidebar.setVisibility(View.VISIBLE);
		float startX = sidebar.getWidth() > 0 ? sidebar.getWidth() : 700f;
		sidebar.setTranslationX(startX);
		ObjectAnimator anim = ObjectAnimator.ofFloat(sidebar, "translationX", 0f);
		anim.setDuration(260);
		anim.setInterpolator(new DecelerateInterpolator());
		anim.start();
		if (sidebarScrim != null) {
			sidebarScrim.setVisibility(View.VISIBLE);
			sidebarScrim.setAlpha(0f);
			sidebarScrim.animate().alpha(1f).setDuration(260).start();
		}
	}

	private void closeSidebar() {
		if (sidebar == null || !isPhoneMode)
			return;
		float endX = sidebar.getWidth() > 0 ? sidebar.getWidth() : 700f;
		ObjectAnimator anim = ObjectAnimator.ofFloat(sidebar, "translationX", endX);
		anim.setDuration(200);
		anim.setInterpolator(new DecelerateInterpolator());
		anim.addListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(android.animation.Animator a) {
				sidebar.setVisibility(View.GONE);
			}
		});
		anim.start();
		if (sidebarScrim != null)
			sidebarScrim.animate().alpha(0f).setDuration(200).withEndAction(() -> sidebarScrim.setVisibility(View.GONE))
					.start();
	}

	private void applyThemeToBottomNav() {
		if (bottomNav == null)
			return;
		View topbar = findViewById(R.id.topbar);
		if (topbar != null)
			topbar.setBackgroundColor(ThemeColors.background());
		View div = findViewById(R.id.topbarDivider);
		if (div != null)
			div.setBackgroundColor(ThemeColors.border());
		if (container != null)
			container.setBackgroundColor(ThemeColors.background());
		selectPhoneTab(currentPhoneTab);
	}

	private void applyThemeToSidebar() {
		if (sidebar == null || isPhoneMode)
			return;
		sidebar.setBackgroundColor(ThemeColors.sidebar());
		View topbar = findViewById(R.id.topbar);
		if (topbar != null)
			topbar.setBackgroundColor(ThemeColors.background());
		if (container != null)
			container.setBackgroundColor(ThemeColors.background());
	}

	private void setActiveButton(Button btn) {
		Button[] all = { btnHome, btnAgenda, btnTransactions, btnVirements, btnBudget, btnEpargne, btnCredits,
				btnVuePerso, btnSettings, btnAbonnements, btnAnalyse };
		for (Button b : all) {
			if (b == null)
				continue;
			b.setBackgroundColor(Color.TRANSPARENT);
			b.setTextColor(ThemeColors.subtext());
			b.setAlpha(0.92f);
		}
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(ThemeColors.withAlpha(ThemeColors.primary(), 18));
		bg.setCornerRadius(DS.dp(this, 10));
		btn.setAlpha(1f);
		btn.setBackground(bg);
		btn.setTextColor(ThemeColors.primary());
	}

	public void navigateTo(int buttonId) {
		if (isPhoneMode) {
			if (buttonId == R.id.btnHome)
				switchPhoneTab(0);
			else if (buttonId == R.id.btnTransactions)
				switchPhoneTab(1);
			else if (buttonId == R.id.btnBudget)
				switchPhoneTab(2);
			else if (buttonId == R.id.btnEpargne)
				switchPhoneTab(3);
			else {
				openSidebar();
				Button b = findViewById(buttonId);
				if (b != null)
					b.performClick();
			}
		} else {
			Button b = findViewById(buttonId);
			if (b != null)
				b.performClick();
		}
	}

	private void navigateToHome() {
		if (isPhoneMode)
			switchPhoneTab(0);
		else
			sidebarNav(btnHome, this::showHome);
	}

	private void switchTo(View view) {
		TabTransition.swap(container, view);
	}

	private void showHome() {
		switchTo(new HomeView(this).getView());
	}

	private void showAgenda() {
		switchTo(new AgendaView(this).getView());
	}

	private void showTransactions() {
		currentTransactionsView = new TransactionsView(this);
		switchTo(currentTransactionsView.getView());
	}

	private void showVirements() {
		switchTo(new VirementView(this).getView());
	}

	private void showBudget() {
		switchTo(new BudgetView(this).getView());
	}

	private void showEpargne() {
		switchTo(new EpargneView(this).getView());
	}

	private void showCredits() {
		switchTo(new CreditsView(this).getView());
	}

	private void showVuePerso() {
		switchTo(new RepartitionView(this).getView());
	}

	private void showSettings() {
		switchTo(new SettingsView(this).getView());
	}

	private void showAbonnements() {
		switchTo(new com.couplefinance.ui.abonnements.AbonnementsView(this).getView());
	}

	private void showAnalyse() {
		switchTo(new AnalyseView(this).getView());
	}

	private void checkFirebaseStatus() {
		if (tvFirebaseStatus == null)
			return;
		tvFirebaseStatus.setText("Connexion...");
		tvFirebaseStatus.setTextColor(Color.parseColor("#F59E0B"));
		TransactionManager.getInstance().getTransactions(new FirestoreManager.Callback() {
			public void onSuccess(String r) {
				runOnUiThread(() -> {
					tvFirebaseStatus.setText("Connecte");
					tvFirebaseStatus.setTextColor(Color.parseColor("#22C55E"));
				});
			}

			public void onError(String e) {
				runOnUiThread(() -> {
					tvFirebaseStatus.setText("Erreur");
					tvFirebaseStatus.setTextColor(Color.parseColor("#EF4444"));
				});
			}
		});
	}

	public void refreshSidebarUser() {
		updateSidebarUser();
	}

	private void updateSidebarUser() {
		try {
			final String name = UserManager.getInstance().getCurrentDisplayName(this);
			final TextView tv = findViewById(R.id.tvSidebarUser);
			if (tv == null) return;
			tv.setText(name);
			tv.setTextColor(ThemeColors.text());

			if (!(tv.getParent() instanceof LinearLayout)) return;
			final LinearLayout footer = (LinearLayout) tv.getParent();

			// Affichage immédiat : avatar de l'utilisateur courant
			java.util.LinkedHashMap<String, String> mine = new java.util.LinkedHashMap<>();
			try {
				String myAvatar = AuthManager.getInstance().getLocalAvatar();
				if (myAvatar == null || myAvatar.isEmpty()) {
					com.couplefinance.models.UserProfile up = UserSession.getInstance().getUser();
					if (up != null) myAvatar = up.getAvatar();
				}
				if (myAvatar != null && !myAvatar.isEmpty()) mine.put(name, myAvatar);
			} catch (Exception ignored) {
			}
			renderSidebarProfile(footer, tv, mine, name);

			// Puis tous les membres du foyer via la requête users
			String hh = HouseholdManager.getInstance().getHouseholdId();
			if (hh != null && !hh.isEmpty()) {
				UserRepository.getInstance().loadHouseholdAvatars(hh, map -> {
					if (map == null || map.isEmpty()) return;
					java.util.LinkedHashMap<String, String> ordered = new java.util.LinkedHashMap<>();
					for (java.util.Map.Entry<String, String> e : map.entrySet())
						if (e.getKey() != null && e.getKey().equalsIgnoreCase(name))
							ordered.put(e.getKey(), e.getValue());
					for (java.util.Map.Entry<String, String> e : map.entrySet())
						if (!(e.getKey() != null && e.getKey().equalsIgnoreCase(name)))
							ordered.put(e.getKey(), e.getValue());
					renderSidebarProfile(footer, tv, ordered, name);
				});
			}
		} catch (Exception ignored) {
		}
	}

	private void renderSidebarProfile(LinearLayout footer, TextView tv,
			java.util.Map<String, String> avatars, String name) {
		footer.removeAllViews();
		footer.setOrientation(LinearLayout.HORIZONTAL);
		footer.setGravity(Gravity.CENTER_VERTICAL);

		int sz = DS.dp(this, 30);
		int i = 0;
		for (java.util.Map.Entry<String, String> e : avatars.entrySet()) {
			if (e.getValue() == null || e.getValue().isEmpty()) continue;
			ImageView iv = new ImageView(this);
			int res = getResources().getIdentifier("avatar_" + e.getValue(), "drawable", getPackageName());
			if (res != 0) iv.setImageResource(res);
			iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
			int pad = DS.dp(this, 1);
			iv.setPadding(pad, pad, pad, pad);
			GradientDrawable ring = new GradientDrawable();
			ring.setShape(GradientDrawable.OVAL);
			ring.setColor(ThemeColors.surface());
			ring.setStroke(DS.dp(this, 2), ThemeColors.primary());
			iv.setBackground(ring);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
			if (i > 0) lp.leftMargin = -DS.dp(this, 9);
			footer.addView(iv, lp);
			i++;
		}

		tv.setText(name);
		LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0, -2, 1f);
		tvLp.leftMargin = DS.dp(this, 10);
		footer.addView(tv, tvLp);
	}

	private void showExpelledDialog() {
		new AlertDialog.Builder(this).setTitle("Foyer quitte").setMessage("Vous avez ete retire du foyer.")
				.setCancelable(false).setPositiveButton("OK", (d, w) -> {
					HouseholdManager.getInstance().clearHousehold();
					UserManager.getInstance().resetRegistrationState();
					Intent i = new Intent(this, LoginActivity.class);
					i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
					i.putExtra("show_household_screen", true);
					startActivity(i);
					finish();
				}).show();
	}

	private void checkMonthlyBalance() {
		if (BalanceManager.getInstance().hasMonthlyStartBalanceLocal())
			return;
		BalanceManager.getInstance().getMonthlyStartBalance(new FirestoreManager.Callback() {
			public void onSuccess(String r) {
				try {
					if (Double.parseDouble(r) == -1 && !BalanceManager.getInstance().hasMonthlyStartBalanceLocal())
						runOnUiThread(() -> showMonthlyBalanceDialog());
				} catch (Exception ignored) {
					if (!BalanceManager.getInstance().hasMonthlyStartBalanceLocal())
						runOnUiThread(() -> showMonthlyBalanceDialog());
				}
			}

			public void onError(String e) {
				if (!BalanceManager.getInstance().hasMonthlyStartBalanceLocal())
					runOnUiThread(() -> showMonthlyBalanceDialog());
			}
		});
	}

	private void showMonthlyBalanceDialog() {
		LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setPadding(DS.dp(this, 24), DS.dp(this, 24), DS.dp(this, 24), DS.dp(this, 20));
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(ThemeColors.card());
		bg.setCornerRadius(DS.dp(this, 22));
		layout.setBackground(bg);
		TextView title = new TextView(this);
		title.setText("Solde reel du mois");
		title.setTextSize(22f);
		title.setTextColor(ThemeColors.text());
		title.setTypeface(null, Typeface.BOLD);
		layout.addView(title);
		TextView sub = new TextView(this);
		sub.setText("Montant disponible en debut de mois.");
		sub.setTextSize(13f);
		sub.setTextColor(ThemeColors.subtext());
		LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
		sp.setMargins(0, DS.dp(this, 6), 0, DS.dp(this, 20));
		layout.addView(sub, sp);
		android.widget.EditText input = new android.widget.EditText(this);
		input.setHint("Ex : 1250");
		input.setTextColor(ThemeColors.text());
		input.setHintTextColor(ThemeColors.subtext());
		input.setTextSize(18f);
		input.setSingleLine(true);
		input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
		input.setPadding(DS.dp(this, 16), 0, DS.dp(this, 16), 0);
		GradientDrawable ib = new GradientDrawable();
		ib.setColor(ThemeColors.backgroundSecondary());
		ib.setCornerRadius(DS.dp(this, 14));
		ib.setStroke(1, ThemeColors.border());
		input.setBackground(ib);
		LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, DS.dp(this, 58));
		ip.setMargins(0, 0, 0, DS.dp(this, 24));
		layout.addView(input, ip);
		LinearLayout btns = new LinearLayout(this);
		btns.setOrientation(LinearLayout.HORIZONTAL);
		Button btnL = new Button(this);
		btnL.setText("Plus tard");
		btnL.setBackgroundResource(R.drawable.btn_dialog_cancel);
		btnL.setStateListAnimator(null);
		Button btnS = new Button(this);
		btnS.setText("Enregistrer");
		btnS.setTextColor(Color.WHITE);
		btnS.setTypeface(null, Typeface.BOLD);
		btnS.setBackgroundResource(R.drawable.btn_dialog_save);
		btnS.setStateListAnimator(null);
		LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, DS.dp(this, 52), 1f);
		b1.rightMargin = DS.dp(this, 10);
		btns.addView(btnL, b1);
		btns.addView(btnS, new LinearLayout.LayoutParams(0, DS.dp(this, 52), 1f));
		layout.addView(btns);
		android.widget.ScrollView sv = new android.widget.ScrollView(this);
		sv.addView(layout);
		AlertDialog dialog = new AlertDialog.Builder(this).setView(sv).create();
		btnL.setOnClickListener(v -> dialog.dismiss());
		btnS.setOnClickListener(v -> {
			String val = input.getText().toString().trim().replace(",", ".");
			if (val.isEmpty()) {
				AppToast.error(this, "Montant manquant");
				return;
			}
			try {
				double amount = Double.parseDouble(val);
				BalanceManager.getInstance().saveMonthlyStartBalance(amount, new FirestoreManager.Callback() {
					public void onSuccess(String r) {
						dialog.dismiss();
						AppToast.success(DashboardActivity.this, "Solde enregistre");
					}

					public void onError(String e) {
						runOnUiThread(() -> {
							dialog.dismiss();
							AppToast.info(DashboardActivity.this, "Enregistre localement");
						});
					}
				});
			} catch (Exception e) {
				AppToast.error(this, "Montant invalide");
			}
		});
		dialog.show();
		if (dialog.getWindow() != null) {
			dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
			dialog.getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.92f),
					ViewGroup.LayoutParams.WRAP_CONTENT);
		}
	}

	private void requestNotificationPermission() {
		if (Build.VERSION.SDK_INT >= 33
				&& checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
			requestPermissions(new String[] { "android.permission.POST_NOTIFICATIONS" }, 101);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (currentTransactionsView != null)
			currentTransactionsView.onPdfActivityResult(requestCode, resultCode, data);
	}

	public void refreshCurrentView() {
	}
}
