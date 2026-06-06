package com.couplefinance.ui.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.couplefinance.AppToast;
import com.couplefinance.core.ui.AppDialog;
import com.couplefinance.data.TelegramManager;
import com.couplefinance.data.TelegramSummary;
import com.couplefinance.data.TelegramScheduler;
import com.couplefinance.data.BankAutoSyncManager;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.data.HouseholdManager;
import com.couplefinance.data.CycleManager;
import com.couplefinance.data.JointAccountManager;
import com.couplefinance.ui.bank.BankConnectionView;
import com.couplefinance.ui.home.HomeWidgetRegistry;
import com.couplefinance.ui.transactions.TransactionsRepository;
import com.couplefinance.ui.transactions.TransactionsModels;

import java.util.ArrayList;  // ← NOUVEAU
import java.util.List;       // ← NOUVEAU

public class SettingsView {

	private final Activity activity;
	private ScrollView scroll;
	private LinearLayout root;
	private LinearLayout content;
	private LinearLayout body;   // zone échangeable : menu ↔ sous-page
	private View headerView;     // titre « Paramètres » + sous-titre
	private View heroView;       // bandeau « Foyer »

	private static final String PREF_THEME = "couplefinance_theme";
	private static final String KEY_DARK = "dark_mode";
	private static final String PREF_DASHBOARD = "dashboard_widgets";
	private static final String PREF_SYNC = "couplefinance_settings";
	private static final String KEY_LAST_SYNC = "last_sync_ts";
	private static final String KEY_LAST_SYNC_LABEL = "last_sync_label";

	public SettingsView(Activity activity) {
		this.activity = activity;
	}

	public View getView() {
		build();
		return scroll;
	}

	private void build() {
		scroll = new ScrollView(activity);
		scroll.setFillViewport(false);
		scroll.setVerticalScrollBarEnabled(false);
		scroll.setBackgroundColor(ThemeColors.background());

		root = new LinearLayout(activity);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setGravity(Gravity.CENTER_HORIZONTAL);
		root.setPadding(DS.dp(activity, 18), DS.dp(activity, 26), DS.dp(activity, 18), DS.dp(activity, 110));

		scroll.addView(root, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT,
				ScrollView.LayoutParams.WRAP_CONTENT));

		content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.VERTICAL);

		int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
		int maxWidth = DS.dp(activity, 860);
		int finalWidth = Math.min(screenWidth - DS.dp(activity, 36), maxWidth);

		LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(finalWidth,
				LinearLayout.LayoutParams.WRAP_CONTENT);

		content.setLayoutParams(contentLp);
		root.addView(content);

		buildHeader();
		buildHero();

		body = new LinearLayout(activity);
		body.setOrientation(LinearLayout.VERTICAL);
		content.addView(body, new LinearLayout.LayoutParams(-1, -2));

		showMenu();
	}

	// ────────────────────────────────────────────────────────────────────────
	// Navigation : menu principal ↔ sous-pages
	// ────────────────────────────────────────────────────────────────────────

	/** Affiche le menu des catégories, groupées par sections. */
	private void showMenu() {
		body.removeAllViews();
		if (headerView != null) headerView.setVisibility(View.VISIBLE);
		if (heroView != null) heroView.setVisibility(View.VISIBLE);

		menuGroup("👤  PROFIL & FOYER",
				menuRow("Profil personnel", "Nom, photo, sécurité", false,
						v -> openCategory("Profil", this::buildAccountSection)),
				menuRow("Foyer & cycle", "Membres, répartition, catégories, cycle", false,
						v -> openCategory("Foyer & cycle", this::buildHouseholdSection)));

		menuGroup("🏦  BANQUE & SYNCHRONISATION",
				menuRow("Connexion bancaire", "Comptes liés, import automatique", false,
						v -> openCategory("Connexion bancaire", this::buildBankingSection)),
				menuRow("Notifications & Telegram", "Alertes Telegram, digest, seuils", false,
						v -> openCategory("Notifications & Telegram", this::buildNotificationsSection)));

		menuGroup("🎨  APPARENCE & DASHBOARD",
				menuRow("Apparence", "Thème sombre, couleur principale", false,
						v -> openCategory("Apparence", this::buildAppearanceSection)),
				menuRow("Widgets du dashboard", "Personnaliser le tableau de bord", false,
						v -> openCategory("Widgets du dashboard", this::buildWidgetsSection)));

		menuGroup("📊  DONNÉES",
				menuRow("Export & Import", "CSV, PDF, historique", false,
						v -> openCategory("Export & Import", this::buildDataSection)));

		menuGroup("⚠️  ZONE DE DANGER",
				menuRow("Actions sensibles", "Déconnexion, quitter le foyer, supprimer le compte", true,
						v -> openCategory("Actions sensibles", this::buildDangerSection)));
	}

	/** Un groupe = un titre de section + une carte contenant les lignes (avec séparateurs). */
	private void menuGroup(String title, View... rows) {
		TextView t = new TextView(activity);
		t.setText(title);
		t.setTextColor(ThemeColors.subtext());
		t.setTextSize(12f);
		t.setTypeface(null, Typeface.BOLD);
		t.setLetterSpacing(0.18f);
		LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(-1, -2);
		tLp.leftMargin = DS.dp(activity, 6);
		tLp.bottomMargin = DS.dp(activity, 10);
		body.addView(t, tLp);

		LinearLayout card = new LinearLayout(activity);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setBackground(cardBg());
		card.setElevation(DS.dp(activity, 2));
		card.setClipToOutline(false);
		LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
		cp.bottomMargin = DS.dp(activity, 24);
		body.addView(card, cp);

		for (int i = 0; i < rows.length; i++) {
			if (i > 0) card.addView(divider());
			card.addView(rows[i]);
		}
	}

	/** Ligne de menu sans icône : titre + sous-titre + chevron. */
	private View menuRow(String title, String subtitle, boolean danger, View.OnClickListener l) {
		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(DS.dp(activity, 18), DS.dp(activity, 18), DS.dp(activity, 18), DS.dp(activity, 18));
		row.setClickable(true);
		row.setFocusable(true);
		row.setOnClickListener(l);

		LinearLayout texts = new LinearLayout(activity);
		texts.setOrientation(LinearLayout.VERTICAL);

		TextView t = new TextView(activity);
		t.setText(title);
		t.setTextColor(danger ? Color.parseColor("#C0392B") : ThemeColors.text());
		t.setTextSize(16.5f);
		t.setTypeface(null, Typeface.BOLD);
		texts.addView(t);

		if (subtitle != null && !subtitle.isEmpty()) {
			TextView s = new TextView(activity);
			s.setText(subtitle);
			s.setTextColor(danger ? Color.parseColor("#D98880") : ThemeColors.subtext());
			s.setTextSize(13.5f);
			LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(-1, -2);
			sLp.topMargin = DS.dp(activity, 3);
			texts.addView(s, sLp);
		}

		row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

		TextView chev = new TextView(activity);
		chev.setText("\u203A");
		chev.setTextColor(danger ? Color.parseColor("#D98880") : ThemeColors.subtext());
		chev.setTextSize(20f);
		row.addView(chev);

		return row;
	}

	/** Ouvre une sous-page : « ‹ Paramètres » + grand titre + contenu de la section. */
	private void openCategory(String title, Runnable sectionBuilder) {
		body.removeAllViews();
		if (headerView != null) headerView.setVisibility(View.GONE);
		if (heroView != null) heroView.setVisibility(View.GONE);

		TextView back = new TextView(activity);
		back.setText("\u2039 Paramètres");
		back.setTextColor(ThemeColors.primary());
		back.setTextSize(16f);
		back.setTypeface(null, Typeface.BOLD);
		back.setClickable(true);
		back.setFocusable(true);
		back.setOnClickListener(v -> showMenu());
		LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(-1, -2);
		bLp.leftMargin = DS.dp(activity, 4);
		bLp.bottomMargin = DS.dp(activity, 12);
		body.addView(back, bLp);

		TextView h = new TextView(activity);
		h.setText(title);
		h.setTextColor(ThemeColors.text());
		h.setTextSize(30f);
		h.setTypeface(null, Typeface.BOLD);
		LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(-1, -2);
		hLp.leftMargin = DS.dp(activity, 4);
		hLp.bottomMargin = DS.dp(activity, 20);
		body.addView(h, hLp);

		sectionBuilder.run();
	}

	// ────────────────────────────────────────────────────────────────────────
	// Sections existantes (inchangées)
	// ────────────────────────────────────────────────────────────────────────

	private void buildHeader() {
		LinearLayout header = new LinearLayout(activity);
		header.setOrientation(LinearLayout.VERTICAL);

		LinearLayout top = new LinearLayout(activity);
		top.setOrientation(LinearLayout.HORIZONTAL);
		top.setGravity(Gravity.CENTER_VERTICAL);

		TextView menu = new TextView(activity);
		menu.setText("☰");
		menu.setTextSize(22f);
		menu.setGravity(Gravity.CENTER);
		menu.setTextColor(ThemeColors.text());
		menu.setBackground(roundBg(ThemeColors.primarySoft(), 18));

		LinearLayout.LayoutParams menuLp = new LinearLayout.LayoutParams(DS.dp(activity, 54), DS.dp(activity, 54));
		menuLp.rightMargin = DS.dp(activity, 18);
		top.addView(menu, menuLp);

		TextView badge = new TextView(activity);
		badge.setText("CONFIGURATION");
		badge.setTextColor(ThemeColors.primary());
		badge.setTextSize(12f);
		badge.setTypeface(null, Typeface.BOLD);
		badge.setLetterSpacing(0.22f);
		top.addView(badge);

		header.addView(top);

		TextView title = new TextView(activity);
		title.setText("Paramètres");
		title.setTextColor(ThemeColors.text());
		title.setTextSize(34f);
		title.setTypeface(null, Typeface.BOLD);

		LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
		titleLp.topMargin = DS.dp(activity, 28);
		header.addView(title, titleLp);

		TextView subtitle = new TextView(activity);
		subtitle.setText("Gérez votre foyer, vos préférences et votre dashboard.");
		subtitle.setTextColor(ThemeColors.subtext());
		subtitle.setTextSize(15f);

		LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
		subLp.topMargin = DS.dp(activity, 6);
		subLp.bottomMargin = DS.dp(activity, 28);
		header.addView(subtitle, subLp);

		content.addView(header);
		headerView = header;
	}

	private void buildHero() {
		LinearLayout hero = new LinearLayout(activity);
		hero.setOrientation(LinearLayout.HORIZONTAL);
		hero.setGravity(Gravity.CENTER_VERTICAL);
		hero.setPadding(DS.dp(activity, 20), DS.dp(activity, 22), DS.dp(activity, 20), DS.dp(activity, 22));
		hero.setBackground(heroBg());

		FrameLayout avatarFrame = new FrameLayout(activity);
		TextView avatar = new TextView(activity);
		String householdName = "Foyer";
		try {
			SettingsModels.State state = SettingsCache.get();
			if (state != null && state.householdName != null && !state.householdName.trim().isEmpty()) {
				householdName = state.householdName.trim();
			}
		} catch (Exception ignored) {
		}

		avatar.setText(firstLetter(householdName));
		avatar.setGravity(Gravity.CENTER);
		avatar.setTextSize(26f);
		avatar.setTypeface(null, Typeface.BOLD);
		avatar.setTextColor(ThemeColors.primary());
		avatar.setBackground(roundBg(Color.argb(35, 255, 255, 255), 999));

		LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(DS.dp(activity, 62), DS.dp(activity, 62));
		avLp.rightMargin = DS.dp(activity, 16);
		avatarFrame.addView(avatar);
		hero.addView(avatarFrame, avLp);

		LinearLayout texts = new LinearLayout(activity);
		texts.setOrientation(LinearLayout.VERTICAL);

		TextView name = new TextView(activity);
		name.setText(householdName);
		name.setTextColor(Color.WHITE);
		name.setTextSize(22f);
		name.setTypeface(null, Typeface.BOLD);
		texts.addView(name);

		String hid = "";
		try {
			hid = HouseholdManager.getInstance().getHouseholdId();
		} catch (Exception ignored) {
		}

		TextView code = new TextView(activity);
		code.setText(hid != null && !hid.isEmpty() ? "Code : " + hid : "Aucun foyer");
		code.setTextColor(Color.argb(190, 255, 255, 255));
		code.setTextSize(14f);
		LinearLayout.LayoutParams codeLp = new LinearLayout.LayoutParams(-1, -2);
		codeLp.topMargin = DS.dp(activity, 3);
		texts.addView(code, codeLp);

		hero.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

		TextView btnMembers = new TextView(activity);
		btnMembers.setText("Membres");
		btnMembers.setTextColor(Color.WHITE);
		btnMembers.setTextSize(15f);
		btnMembers.setTypeface(null, Typeface.BOLD);
		btnMembers.setGravity(Gravity.CENTER);
		btnMembers.setPadding(DS.dp(activity, 16), DS.dp(activity, 10), DS.dp(activity, 16), DS.dp(activity, 10));
		btnMembers.setBackground(roundBg(Color.argb(42, 255, 255, 255), 999));
		btnMembers.setOnClickListener(v -> SettingsDialogs.showMembers(activity));

		LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-2, -2);
		btnLp.leftMargin = DS.dp(activity, 12);
		hero.addView(btnMembers, btnLp);

		LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, -2);
		heroLp.bottomMargin = DS.dp(activity, 24);
		content.addView(hero, heroLp);
		heroView = hero;
	}

	private void buildAccountSection() {
		LinearLayout card = sectionCard("MON PROFIL");

		card.addView(row("👤", "Profil personnel", "Nom affiché, avatar",
				v -> SettingsDialogs.showProfile(activity, this::refresh)));

		card.addView(divider());

		card.addView(row("🔒", "Sécurité", "Changer le mot de passe",
				v -> SettingsDialogs.showSecurity(activity, this::refresh)));

		card.addView(divider());

		card.addView(row("🔔", "Notifications locales", "Alertes, rappels de charges",
				v -> SettingsDialogs.showNotifications(activity, this::refresh)));
	}

	private void buildHouseholdSection() {
		LinearLayout card = sectionCard("FOYER");

		card.addView(row("👥", "Membres du foyer", "Gérer les accès et invitations",
				v -> SettingsDialogs.showMembers(activity)));

		card.addView(divider());

		card.addView(rowWithValue("⚖️", "Ratio de répartition", getRatioValue(),
				v -> SettingsDialogs.showRatio(activity, this::refresh)));

		card.addView(divider());

		card.addView(row("🏷️", "Catégories", "Ajouter ou renommer les catégories",
				v -> SettingsDialogs.showCategories(activity)));

		card.addView(divider());

		card.addView(rowWithValue("🏦", "Compte joint", jointAccountValue(),
				v -> SettingsDialogs.showJointAccount(activity, this::refresh)));

		card.addView(divider());

		int cycleDay = CycleManager.getInstance().getCycleStartDay();
		String cycleValue = cycleDay == 1 ? "1er du mois" : "Le " + cycleDay + " du mois";
		card.addView(rowWithValue("📅", "Début de cycle", cycleValue, v -> showCycleStartDayDialog()));
	}

	private void showCycleStartDayDialog() {
		LinearLayout content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.VERTICAL);

		TextView info = new TextView(activity);
		info.setText("Choisissez le jour du mois où démarre votre cycle financier.\n\n"
				+ "Exemple : si vous êtes payé le 5, choisissez « 5 ».\n" + "Cycle résultant : "
				+ CycleManager.getInstance().getCurrentCycleLabel());
		info.setTextSize(DS.TEXT_SM);
		info.setTextColor(ThemeColors.subtext());
		LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-1, -2);
		infoLp.bottomMargin = DS.dp(activity, DS.GAP);
		content.addView(info, infoLp);

		android.widget.NumberPicker picker = new android.widget.NumberPicker(activity);
		picker.setMinValue(1);
		picker.setMaxValue(28);
		picker.setValue(CycleManager.getInstance().getCycleStartDay());
		picker.setWrapSelectorWheel(false);

		LinearLayout.LayoutParams pickerLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		pickerLp.gravity = Gravity.CENTER_HORIZONTAL;
		content.addView(picker, pickerLp);

		final android.app.AlertDialog[] holder = new android.app.AlertDialog[1];

		holder[0] = new com.couplefinance.core.ui.AppDialog.Builder(activity).icon("📅").title("Début de cycle")
				.subtitle("Jour de démarrage du cycle financier").content(content).primaryBtn("ENREGISTRER", () -> {
					int selectedDay = picker.getValue();
					CycleManager.getInstance().saveCycleStartDay(selectedDay,
							new com.couplefinance.data.FirestoreManager.Callback() {
								@Override
								public void onSuccess(String result) {
									com.couplefinance.utils.NotificationScheduler.scheduleAll(activity);
									AppToast.success(activity,
											"Cycle : " + CycleManager.getInstance().getCurrentCycleLabel());
									refresh();
								}

								@Override
								public void onError(String error) {
									com.couplefinance.utils.NotificationScheduler.scheduleAll(activity);
									AppToast.info(activity, "Cycle enregistré localement");
									refresh();
								}
							});
					try {
						if (holder[0] != null && holder[0].isShowing())
							holder[0].dismiss();
					} catch (Exception ignored) {
					}
				}).show();
	}

	// ════════════════════════════════════════════════════════════════════════
	// NOUVELLE SECTION: CONNEXION BANCAIRE 🏦
	// ════════════════════════════════════════════════════════════════════════

	/**
	 * Section complète pour gérer les connexions bancaires via Enable Banking.
	 * Intègre BankConnectionView avec la détection des doublons.
	 */
	private void buildBankingSection() {
		LinearLayout card = sectionCard("COMPTES LIÉS");

		card.addView(row("🔗", "Gérer les comptes bancaires",
				"Connecter ou reconnecter votre banque",
				v -> onBankConnectionClicked()));

		card.addView(divider());

		card.addView(bankAutoSyncRow());

		card.addView(divider());

		card.addView(rowWithValue("🕐", "Heure de synchro automatique",
				BankAutoSyncManager.getTimeLabel(activity),
				v -> showSyncTimeDialog()));

		card.addView(divider());

		card.addView(bankNotifyEachRow());
	}

	private void buildNotificationsSection() {
		LinearLayout card = sectionCard("TELEGRAM");

		card.addView(row("✈️", "Configurer & personnaliser",
				"Bot, comptes, sections, fréquence, alertes",
				v -> showTelegramDialog()));

		card.addView(divider());

		card.addView(telegramDigestRow());

		card.addView(divider());

		card.addView(row("💡", "Alertes intelligentes",
				"Grosse dépense, solde bas — seuils personnalisables",
				v -> showSmartAlertsDialog()));

		card.addView(divider());

		card.addView(row("💡", "Alertes intelligentes",
				"Grosse dépense, solde bas — seuils personnalisables",
				v -> showSmartAlertsDialog()));

		LinearLayout card2 = sectionCard("NOTIFICATIONS LOCALES");

		card2.addView(row("🔔", "Rappels et alertes",
				"Charges à venir, début de cycle",
				v -> SettingsDialogs.showNotifications(activity, this::refresh)));
	}

	private View telegramDigestRow() {
		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(DS.dp(activity, 18), DS.dp(activity, 16), DS.dp(activity, 18), DS.dp(activity, 16));

		LinearLayout texts = new LinearLayout(activity);
		texts.setOrientation(LinearLayout.VERTICAL);

		TextView tvTitle = new TextView(activity);
		tvTitle.setText("Résumé automatique");
		tvTitle.setTextColor(ThemeColors.text());
		tvTitle.setTextSize(17f);
		tvTitle.setTypeface(null, Typeface.BOLD);
		texts.addView(tvTitle);

		String freq = TelegramScheduler.getDigestFrequency(activity);
		String freqLabel = TelegramScheduler.OFF.equals(freq)    ? "Désactivé"
				: TelegramScheduler.DAILY.equals(freq)   ? "Quotidien"
				: TelegramScheduler.WEEKLY.equals(freq)  ? "Hebdomadaire" : "Mensuel";
		TextView tvSub = new TextView(activity);
		tvSub.setText("Envoi : " + freqLabel);
		tvSub.setTextColor(ThemeColors.subtext());
		tvSub.setTextSize(13f);
		LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
		subLp.topMargin = DS.dp(activity, 2);
		texts.addView(tvSub, subLp);

		row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

		TextView chev = new TextView(activity);
		chev.setText("›");
		chev.setTextColor(ThemeColors.subtext());
		chev.setTextSize(28f);
		row.addView(chev);

		row.setClickable(true);
		row.setFocusable(true);
		row.setOnClickListener(v -> showTelegramFrequencyDialog());

		return row;
	}

	private void showTelegramFrequencyDialog() {
		final String[] selFreq = { TelegramScheduler.getDigestFrequency(activity) };
		final String[] codes = { TelegramScheduler.OFF, TelegramScheduler.DAILY, TelegramScheduler.WEEKLY, TelegramScheduler.MONTHLY };
		final String[] labels = { "Désactivé", "Quotidien", "Hebdo", "Mensuel" };

		LinearLayout box = new LinearLayout(activity);
		box.setOrientation(LinearLayout.VERTICAL);

		final TextView[] chips = new TextView[codes.length];
		final Runnable restyle = () -> {
			for (int i = 0; i < codes.length; i++) {
				boolean on = codes[i].equals(selFreq[0]);
				GradientDrawable bg = new GradientDrawable();
				bg.setColor(on ? ThemeColors.primary() : ThemeColors.surfaceSoft());
				bg.setCornerRadius(DS.dp(activity, 10));
				chips[i].setBackground(bg);
				chips[i].setTextColor(on ? ThemeColors.white() : ThemeColors.subtext());
			}
		};

		LinearLayout freqRow = new LinearLayout(activity);
		freqRow.setOrientation(LinearLayout.HORIZONTAL);
		for (int i = 0; i < codes.length; i++) {
			final int idx = i;
			TextView chip = new TextView(activity);
			chip.setText(labels[i]);
			chip.setGravity(Gravity.CENTER);
			chip.setTextSize(DS.TEXT_SM);
			chip.setTypeface(null, Typeface.BOLD);
			int pv = DS.dp(activity, 12);
			int ph = DS.dp(activity, 6);
			chip.setPadding(ph, pv, ph, pv);
			chip.setOnClickListener(v -> {
				selFreq[0] = codes[idx];
				TelegramScheduler.setDigestFrequency(activity, selFreq[0]);
				restyle.run();
			});
			chips[i] = chip;
			LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1f);
			if (i > 0) clp.leftMargin = DS.dp(activity, 8);
			freqRow.addView(chip, clp);
		}
		restyle.run();
		box.addView(freqRow);

		TextView desc = new TextView(activity);
		desc.setText("Le résumé est envoyé la première fois que l'app est ouverte dans la nouvelle période.");
		desc.setTextColor(ThemeColors.subtext());
		desc.setTextSize(13f);
		LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(-1, -2);
		descLp.topMargin = DS.dp(activity, 14);
		box.addView(desc, descLp);

		final AlertDialog[] h = {null};
		h[0] = new AppDialog.Builder(activity)
				.icon("📅").title("Fréquence du digest").subtitle("Résumé Telegram automatique")
				.content(box)
				.primaryBtn("FERMER", () -> {
					refresh();
					try { if (h[0] != null) h[0].dismiss(); } catch (Exception ignored) {}
				}).show();
	}

	private void showTelegramAlertsDialog() {
		LinearLayout box = new LinearLayout(activity);
		box.setOrientation(LinearLayout.VERTICAL);

		TextView tvThresholdLabel = new TextView(activity);
		tvThresholdLabel.setText("Alerte si le compte joint passe sous (€) :");
		tvThresholdLabel.setTextColor(ThemeColors.text());
		tvThresholdLabel.setTextSize(14f);
		tvThresholdLabel.setTypeface(null, Typeface.BOLD);
		box.addView(tvThresholdLabel);

		final EditText etThreshold = new EditText(activity);
		etThreshold.setHint("Ex: 200");
		double curTh = TelegramScheduler.getLowJointThreshold(activity);
		if (!Double.isNaN(curTh)) {
			etThreshold.setText(curTh == Math.floor(curTh)
					? String.valueOf((long) curTh) : String.valueOf(curTh));
		}
		etThreshold.setTextColor(ThemeColors.text());
		etThreshold.setHintTextColor(ThemeColors.muted());
		etThreshold.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
				| android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
		LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(-1, -2);
		etLp.topMargin = DS.dp(activity, 8);
		box.addView(etThreshold, etLp);

		final android.widget.CheckBox cbCoverage = new android.widget.CheckBox(activity);
		cbCoverage.setText("Alerter si le joint ne couvre pas les prélèvements à venir");
		cbCoverage.setTextColor(ThemeColors.subtext());
		cbCoverage.setTextSize(14f);
		cbCoverage.setChecked(TelegramScheduler.isCoverageAlert(activity));
		LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(-1, -2);
		cbLp.topMargin = DS.dp(activity, 16);
		box.addView(cbCoverage, cbLp);

		final AlertDialog[] h = {null};
		h[0] = new AppDialog.Builder(activity)
				.icon("⚠️").title("Alertes Telegram").subtitle("Seuils de déclenchement")
				.content(box)
				.primaryBtn("ENREGISTRER", () -> {
					String th = etThreshold.getText().toString().trim().replace(',', '.');
					if (th.isEmpty()) TelegramScheduler.setLowJointThreshold(activity, Double.NaN);
					else {
						try { TelegramScheduler.setLowJointThreshold(activity, Double.parseDouble(th)); }
						catch (Exception ignored) {}
					}
					TelegramScheduler.setCoverageAlert(activity, cbCoverage.isChecked());
					AppToast.success(activity, "Alertes enregistrées");
					try { if (h[0] != null) h[0].dismiss(); } catch (Exception ignored) {}
				}).show();
	}

	private void showSmartAlertsDialog() {
		LinearLayout box = new LinearLayout(activity);
		box.setOrientation(LinearLayout.VERTICAL);

		TextView label1 = new TextView(activity);
		label1.setText("Alerte grosse dépense (seuil en €) :");
		label1.setTextColor(ThemeColors.text());
		label1.setTextSize(14f);
		label1.setTypeface(null, Typeface.BOLD);
		box.addView(label1);

		final android.widget.EditText etExpense = new android.widget.EditText(activity);
		etExpense.setHint("Ex : 150");
		double curExp = com.couplefinance.data.SmartNotificationManager.getExpenseThreshold(activity);
		etExpense.setText(curExp == Math.floor(curExp) ? String.valueOf((long) curExp) : String.valueOf(curExp));
		etExpense.setTextColor(ThemeColors.text());
		etExpense.setHintTextColor(ThemeColors.muted());
		etExpense.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
		LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, -2);
		ep.topMargin = DS.dp(activity, 6);
		ep.bottomMargin = DS.dp(activity, 18);
		box.addView(etExpense, ep);

		TextView label2 = new TextView(activity);
		label2.setText("Alerte solde bas (seuil en €) :");
		label2.setTextColor(ThemeColors.text());
		label2.setTextSize(14f);
		label2.setTypeface(null, Typeface.BOLD);
		box.addView(label2);

		final android.widget.EditText etBalance = new android.widget.EditText(activity);
		etBalance.setHint("Ex : 200");
		double curBal = com.couplefinance.data.SmartNotificationManager.getBalanceThreshold(activity);
		etBalance.setText(curBal == Math.floor(curBal) ? String.valueOf((long) curBal) : String.valueOf(curBal));
		etBalance.setTextColor(ThemeColors.text());
		etBalance.setHintTextColor(ThemeColors.muted());
		etBalance.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
		LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
		bp.topMargin = DS.dp(activity, 6);
		box.addView(etBalance, bp);

		final AlertDialog[] h = {null};
		h[0] = new AppDialog.Builder(activity)
				.icon("💡").title("Alertes intelligentes").subtitle("Seuils de déclenchement des notifications push")
				.content(box)
				.primaryBtn("ENREGISTRER", () -> {
					String expStr = etExpense.getText().toString().trim().replace(',', '.');
					String balStr = etBalance.getText().toString().trim().replace(',', '.');
					try {
						if (!expStr.isEmpty()) com.couplefinance.data.SmartNotificationManager.setExpenseThreshold(activity, Double.parseDouble(expStr));
					} catch (Exception ignored) {}
					try {
						if (!balStr.isEmpty()) com.couplefinance.data.SmartNotificationManager.setBalanceThreshold(activity, Double.parseDouble(balStr));
					} catch (Exception ignored) {}
					AppToast.success(activity, "Alertes enregistrées");
					try { if (h[0] != null) h[0].dismiss(); } catch (Exception ignored) {}
				}).show();
	}

	/** Toggle : active/désactive la synchro bancaire automatique quotidienne. */
	private View bankAutoSyncRow() {
		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(android.view.Gravity.CENTER_VERTICAL);
		row.setPadding(DS.dp(activity, 18), DS.dp(activity, 16),
				DS.dp(activity, 18), DS.dp(activity, 16));

		LinearLayout texts = new LinearLayout(activity);
		texts.setOrientation(LinearLayout.VERTICAL);

		TextView tvTitle = new TextView(activity);
		tvTitle.setText("Synchro automatique");
		tvTitle.setTextColor(ThemeColors.text());
		tvTitle.setTextSize(17f);
		tvTitle.setTypeface(null, Typeface.BOLD);
		texts.addView(tvTitle);

		TextView tvSub = new TextView(activity);
		tvSub.setText("Vérifier les comptes chaque jour à "
				+ BankAutoSyncManager.getTimeLabel(activity));
		tvSub.setTextColor(ThemeColors.subtext());
		tvSub.setTextSize(13f);
		LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
		subLp.topMargin = DS.dp(activity, 2);
		texts.addView(tvSub, subLp);

		row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

		Switch sw = new Switch(activity);
		sw.setChecked(BankAutoSyncManager.isEnabled(activity));
		sw.setOnCheckedChangeListener((b, isChecked) -> {
			BankAutoSyncManager.setEnabled(activity, isChecked);
			AppToast.success(activity, isChecked
					? "Synchro quotidienne activée"
					: "Synchro quotidienne désactivée");
		});
		try {
			sw.setThumbTintList(ColorStateList.valueOf(ThemeColors.primary()));
		} catch (Exception ignored) {}
		row.addView(sw);

		return row;
	}

	/** Toggle : notification par transaction (sinon résumé seul). */
	private View bankNotifyEachRow() {
		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(android.view.Gravity.CENTER_VERTICAL);
		row.setPadding(DS.dp(activity, 18), DS.dp(activity, 16),
				DS.dp(activity, 18), DS.dp(activity, 16));

		LinearLayout texts = new LinearLayout(activity);
		texts.setOrientation(LinearLayout.VERTICAL);

		TextView tvTitle = new TextView(activity);
		tvTitle.setText("Notifier chaque transaction");
		tvTitle.setTextColor(ThemeColors.text());
		tvTitle.setTextSize(17f);
		tvTitle.setTypeface(null, Typeface.BOLD);
		texts.addView(tvTitle);

		TextView tvSub = new TextView(activity);
		tvSub.setText("Sinon, résumé quotidien uniquement");
		tvSub.setTextColor(ThemeColors.subtext());
		tvSub.setTextSize(13f);
		LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
		subLp.topMargin = DS.dp(activity, 2);
		texts.addView(tvSub, subLp);

		row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

		Switch sw = new Switch(activity);
		sw.setChecked(BankAutoSyncManager.isNotifyEach(activity));
		sw.setOnCheckedChangeListener((b, isChecked) ->
				BankAutoSyncManager.setNotifyEach(activity, isChecked));
		try {
			sw.setThumbTintList(ColorStateList.valueOf(ThemeColors.primary()));
		} catch (Exception ignored) {}
		row.addView(sw);

		return row;
	}

	/** Dialog heure + minutes de la synchro quotidienne. */
	private LinearLayout.LayoutParams tgTop(int dp) {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.topMargin = DS.dp(activity, dp);
		return lp;
	}

	private TextView tgButton(String label) {
		TextView b = new TextView(activity);
		b.setText(label);
		b.setTextColor(ThemeColors.white());
		b.setTypeface(null, Typeface.BOLD);
		b.setTextSize(DS.TEXT_SM);
		b.setGravity(android.view.Gravity.CENTER);
		int ph = DS.dp(activity, 14), pv = DS.dp(activity, 11);
		b.setPadding(ph, pv, ph, pv);
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(ThemeColors.primary());
		bg.setCornerRadius(DS.dp(activity, 12));
		b.setBackground(bg);
		return b;
	}

	private void showTelegramDialog() {
		TelegramManager.getInstance().init(activity);

		LinearLayout box = new LinearLayout(activity);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setPadding(0, DS.dp(activity, 4), 0, DS.dp(activity, 8));

		// ── Section 1 : Configuration du bot ──
		box.addView(tgSectionLabel("⚙️ Configuration du bot"));

		TextView info = new TextView(activity);
		info.setText("1. Crée un bot via @BotFather et colle son token.\n"
				+ "2. Écris /start au bot depuis le compte destinataire.\n"
				+ "3. Touche « Récupérer le chat_id ».");
		info.setTextColor(ThemeColors.subtext());
		info.setTextSize(DS.TEXT_SM);
		box.addView(info, tgTop(4));

		final EditText etToken = new EditText(activity);
		etToken.setHint("Token du bot (ex: 123456:ABC…)");
		etToken.setText(TelegramManager.getInstance().getBotToken());
		etToken.setTextColor(ThemeColors.text());
		etToken.setHintTextColor(ThemeColors.muted());
		box.addView(etToken, tgTop(10));

		final EditText etChat = new EditText(activity);
		etChat.setHint("chat_id du destinataire");
		etChat.setText(TelegramManager.getInstance().getChatId());
		etChat.setTextColor(ThemeColors.text());
		etChat.setHintTextColor(ThemeColors.muted());
		etChat.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
				| android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
		box.addView(etChat, tgTop(8));

		LinearLayout botBtns = new LinearLayout(activity);
		botBtns.setOrientation(LinearLayout.HORIZONTAL);
		TextView btnFetch = tgButton("Récupérer le chat_id");
		btnFetch.setOnClickListener(v -> {
			TelegramManager.getInstance().setBotToken(etToken.getText().toString().trim());
			AppToast.info(activity, "Recherche du chat…");
			TelegramManager.getInstance().fetchLatestChatId(new TelegramManager.Callback() {
				@Override public void onSuccess(String r) {
					etChat.setText(TelegramManager.getInstance().getChatId());
					AppToast.success(activity, "Trouvé : " + r);
				}
				@Override public void onError(String e) { AppToast.error(activity, e); }
			});
		});
		TextView btnTest = tgButton("Test");
		btnTest.setOnClickListener(v -> {
			TelegramManager.getInstance().setBotToken(etToken.getText().toString().trim());
			TelegramManager.getInstance().setChatId(etChat.getText().toString().trim());
			TelegramManager.getInstance().sendTest(new TelegramManager.Callback() {
				@Override public void onSuccess(String r) { AppToast.success(activity, "Message envoyé ✓"); }
				@Override public void onError(String e) { AppToast.error(activity, e); }
			});
		});
		LinearLayout.LayoutParams fetchLp = new LinearLayout.LayoutParams(0, -2, 2f);
		LinearLayout.LayoutParams testLp  = new LinearLayout.LayoutParams(0, -2, 1f);
		testLp.leftMargin = DS.dp(activity, 8);
		botBtns.addView(btnFetch, fetchLp);
		botBtns.addView(btnTest,  testLp);
		box.addView(botBtns, tgTop(10));

		// ── Section 2 : Comptes affichés ──
		box.addView(tgSectionLabel("👥 Comptes à afficher"), tgTop(20));

		LinearLayout accountsBox = new LinearLayout(activity);
		accountsBox.setOrientation(LinearLayout.VERTICAL);
		List<String> selectedAccounts = TelegramScheduler.getSelectedAccounts(activity);

		android.widget.CheckBox cbJoint = tgCheckBox("Compte joint",
				selectedAccounts.isEmpty() || containsIgnoreCase(selectedAccounts, "Compte joint"));
		accountsBox.addView(cbJoint, tgTop(6));

		List<android.widget.CheckBox> memberCbs = new ArrayList<>();
		List<String> memberNames = new ArrayList<>();
		try {
			SettingsModels.State state = SettingsCache.get();
			if (state != null && state.members != null) {
				for (SettingsModels.Member m : state.members) {
					if (m == null || m.name == null || m.name.trim().isEmpty()) continue;
					String name = m.name.trim();
					android.widget.CheckBox cb = tgCheckBox(name,
							selectedAccounts.isEmpty() || containsIgnoreCase(selectedAccounts, name));
					memberNames.add(name);
					memberCbs.add(cb);
					accountsBox.addView(cb, tgTop(4));
				}
			}
		} catch (Exception ignored) {}
		box.addView(accountsBox, tgTop(4));

		// ── Section 3 : Sections du résumé ──
		box.addView(tgSectionLabel("📋 Sections du résumé"), tgTop(20));

		TextView sectInfo = new TextView(activity);
		sectInfo.setText("Choisissez ce que vous voulez voir dans le digest Telegram.");
		sectInfo.setTextColor(ThemeColors.subtext());
		sectInfo.setTextSize(DS.TEXT_SM - 1);
		box.addView(sectInfo, tgTop(4));

		String[][] sectDefs = {
			{"💰 Soldes",          "balances"},
			{"📊 Bilan mensuel",    "month"},
			{"📋 Prélèvements", "charges"},
			{"🏦 Crédits",    "credits"},
			{"🌱 Épargne",    "savings"},
			{"📉 Budget",           "budget"},
			{"📆 Agenda",           "agenda"},
			{"🔮 Projection",       "projection"},
			{"🧾 Opérations",  "recent"},
			{"🏷️ Catégories", "categories"}
		};
		boolean[] sectDefaults = {
			TelegramScheduler.isShowBalances(activity),
			TelegramScheduler.isShowMonth(activity),
			TelegramScheduler.isShowCharges(activity),
			TelegramScheduler.isShowCredits(activity),
			TelegramScheduler.isShowSavings(activity),
			TelegramScheduler.isShowBudget(activity),
			TelegramScheduler.isShowAgenda(activity),
			TelegramScheduler.isShowProjection(activity),
			TelegramScheduler.isShowRecent(activity),
			TelegramScheduler.isShowCategories(activity)
		};
		android.widget.CheckBox[] sectCbs = new android.widget.CheckBox[sectDefs.length];
		LinearLayout sectGrid = new LinearLayout(activity);
		sectGrid.setOrientation(LinearLayout.VERTICAL);
		for (int i = 0; i < sectDefs.length; i += 2) {
			LinearLayout rowG = new LinearLayout(activity);
			rowG.setOrientation(LinearLayout.HORIZONTAL);
			android.widget.CheckBox cb1 = tgCheckBox(sectDefs[i][0], sectDefaults[i]);
			sectCbs[i] = cb1;
			rowG.addView(cb1, new LinearLayout.LayoutParams(0, -2, 1f));
			if (i + 1 < sectDefs.length) {
				android.widget.CheckBox cb2 = tgCheckBox(sectDefs[i + 1][0], sectDefaults[i + 1]);
				sectCbs[i + 1] = cb2;
				LinearLayout.LayoutParams c2lp = new LinearLayout.LayoutParams(0, -2, 1f);
				c2lp.leftMargin = DS.dp(activity, 8);
				rowG.addView(cb2, c2lp);
			}
			LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
			rowLp.topMargin = DS.dp(activity, 4);
			sectGrid.addView(rowG, rowLp);
		}
		box.addView(sectGrid, tgTop(8));

		// ── Section 4 : Fréquence d'envoi ──
		box.addView(tgSectionLabel("⏰ Fréquence d'envoi automatique"), tgTop(20));

		final String[] selFreq = { TelegramScheduler.getDigestFrequency(activity) };
		final String[] freqCodes   = { TelegramScheduler.OFF, TelegramScheduler.DAILY,
				TelegramScheduler.WEEKLY, TelegramScheduler.MONTHLY };
		final String[] freqLabels  = { "Désactivé", "Quotidien", "Hebdo", "Mensuel" };
		final TextView[] freqChips = new TextView[freqCodes.length];
		final Runnable restyleFreq = () -> {
			for (int i = 0; i < freqCodes.length; i++) {
				boolean on = freqCodes[i].equals(selFreq[0]);
				GradientDrawable bg = new GradientDrawable();
				bg.setColor(on ? ThemeColors.primary() : ThemeColors.surfaceSoft());
				bg.setCornerRadius(DS.dp(activity, 10));
				freqChips[i].setBackground(bg);
				freqChips[i].setTextColor(on ? ThemeColors.white() : ThemeColors.subtext());
			}
		};
		LinearLayout freqRow = new LinearLayout(activity);
		freqRow.setOrientation(LinearLayout.HORIZONTAL);
		for (int i = 0; i < freqCodes.length; i++) {
			final int idx = i;
			TextView chip = new TextView(activity);
			chip.setText(freqLabels[i]);
			chip.setGravity(Gravity.CENTER);
			chip.setTextSize(DS.TEXT_SM);
			chip.setTypeface(null, Typeface.BOLD);
			int pv = DS.dp(activity, 10);
			chip.setPadding(DS.dp(activity, 4), pv, DS.dp(activity, 4), pv);
			chip.setOnClickListener(v -> { selFreq[0] = freqCodes[idx]; restyleFreq.run(); });
			freqChips[i] = chip;
			LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1f);
			if (i > 0) clp.leftMargin = DS.dp(activity, 6);
			freqRow.addView(chip, clp);
		}
		restyleFreq.run();
		box.addView(freqRow, tgTop(8));

		// ── Section 5 : Alertes ──
		box.addView(tgSectionLabel("🔔 Alertes automatiques"), tgTop(20));

		final EditText etThreshold = new EditText(activity);
		etThreshold.setHint("Alerte si le joint passe sous (€)");
		double curTh = TelegramScheduler.getLowJointThreshold(activity);
		if (!Double.isNaN(curTh)) {
			etThreshold.setText(curTh == Math.floor(curTh)
					? String.valueOf((long) curTh) : String.valueOf(curTh));
		}
		etThreshold.setTextColor(ThemeColors.text());
		etThreshold.setHintTextColor(ThemeColors.muted());
		etThreshold.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
				| android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
		box.addView(etThreshold, tgTop(8));

		final android.widget.CheckBox cbCoverage = new android.widget.CheckBox(activity);
		cbCoverage.setText("Alerter si le joint ne couvre pas les prélèvements");
		cbCoverage.setTextColor(ThemeColors.subtext());
		cbCoverage.setTextSize(DS.TEXT_SM);
		cbCoverage.setChecked(TelegramScheduler.isCoverageAlert(activity));
		box.addView(cbCoverage, tgTop(8));

		// ── Bouton envoi immédiat ──
		TextView btnSummary = tgButton("📤  Envoyer le résumé maintenant");
		btnSummary.setOnClickListener(v -> {
			AppToast.info(activity, "Préparation du résumé…");
			TelegramSummary.buildAndSend(activity, new TelegramSummary.Callback() {
				@Override public void onSuccess() { AppToast.success(activity, "Résumé envoyé ✓"); }
				@Override public void onError(String e) { AppToast.error(activity, e); }
			});
		});
		box.addView(btnSummary, tgTop(20));

		ScrollView sc = new ScrollView(activity);
		sc.addView(box);

		final AlertDialog[] h = {null};
		h[0] = new AppDialog.Builder(activity)
				.icon("✈️").title("Notifications Telegram").subtitle("100% personnalisable")
				.content(sc)
				.primaryBtn("ENREGISTRER", () -> {
					TelegramManager.getInstance().setBotToken(etToken.getText().toString().trim());
					TelegramManager.getInstance().setChatId(etChat.getText().toString().trim());
					List<String> chosen = new ArrayList<>();
					if (cbJoint.isChecked()) chosen.add("Compte joint");
					for (int i = 0; i < memberCbs.size(); i++) {
						if (memberCbs.get(i).isChecked()) chosen.add(memberNames.get(i));
					}
					int totalAccounts = 1 + memberNames.size();
					TelegramScheduler.setSelectedAccounts(activity,
							chosen.size() == totalAccounts ? new ArrayList<>() : chosen);
					TelegramScheduler.setShowBalances(activity,   sectCbs[0] != null && sectCbs[0].isChecked());
					TelegramScheduler.setShowMonth(activity,      sectCbs[1] != null && sectCbs[1].isChecked());
					TelegramScheduler.setShowCharges(activity,    sectCbs[2] != null && sectCbs[2].isChecked());
					TelegramScheduler.setShowCredits(activity,    sectCbs[3] != null && sectCbs[3].isChecked());
					TelegramScheduler.setShowSavings(activity,    sectCbs[4] != null && sectCbs[4].isChecked());
					TelegramScheduler.setShowBudget(activity,     sectCbs[5] != null && sectCbs[5].isChecked());
					TelegramScheduler.setShowAgenda(activity,     sectCbs[6] != null && sectCbs[6].isChecked());
					TelegramScheduler.setShowProjection(activity, sectCbs[7] != null && sectCbs[7].isChecked());
					TelegramScheduler.setShowRecent(activity,     sectCbs[8] != null && sectCbs[8].isChecked());
					TelegramScheduler.setShowCategories(activity, sectCbs[9] != null && sectCbs[9].isChecked());
					TelegramScheduler.setDigestFrequency(activity, selFreq[0]);
					String th = etThreshold.getText().toString().trim().replace(',', '.');
					if (th.isEmpty()) TelegramScheduler.setLowJointThreshold(activity, Double.NaN);
					else { try { TelegramScheduler.setLowJointThreshold(activity, Double.parseDouble(th)); } catch (Exception ignored) {} }
					TelegramScheduler.setCoverageAlert(activity, cbCoverage.isChecked());
					AppToast.success(activity, "Telegram configuré ✓");
					refresh();
					try { if (h[0] != null) h[0].dismiss(); } catch (Exception ignored) {}
				}).show();
	}

	private TextView tgSectionLabel(String text) {
		TextView tv = new TextView(activity);
		tv.setText(text);
		tv.setTextColor(ThemeColors.text());
		tv.setTypeface(null, Typeface.BOLD);
		tv.setTextSize(14f);
		return tv;
	}

	private android.widget.CheckBox tgCheckBox(String label, boolean checked) {
		android.widget.CheckBox cb = new android.widget.CheckBox(activity);
		cb.setText(label);
		cb.setChecked(checked);
		cb.setTextColor(ThemeColors.subtext());
		cb.setTextSize(DS.TEXT_SM);
		return cb;
	}

	private boolean containsIgnoreCase(List<String> list, String value) {
		if (list == null || value == null) return false;
		for (String s : list) {
			if (value.equalsIgnoreCase(s)) return true;
		}
		return false;
	}


	private void showSyncTimeDialog() {
		final NumberPicker hourPicker = new NumberPicker(activity);
		hourPicker.setMinValue(0);
		hourPicker.setMaxValue(23);
		hourPicker.setValue(BankAutoSyncManager.getHour(activity));

		final NumberPicker minutePicker = new NumberPicker(activity);
		minutePicker.setMinValue(0);
		minutePicker.setMaxValue(59);
		minutePicker.setValue(BankAutoSyncManager.getMinute(activity));
		// Afficher les minutes sur 2 chiffres (00, 01, …)
		String[] mins = new String[60];
		for (int i = 0; i < 60; i++) mins[i] = String.format(java.util.Locale.FRANCE, "%02d", i);
		minutePicker.setDisplayedValues(mins);

		LinearLayout wrap = new LinearLayout(activity);
		wrap.setOrientation(LinearLayout.HORIZONTAL);
		wrap.setGravity(android.view.Gravity.CENTER);

		LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(-2, -2);
		wrap.addView(hourPicker, pLp);

		TextView sep = new TextView(activity);
		sep.setText("  :  ");
		sep.setTextSize(24f);
		sep.setTypeface(null, Typeface.BOLD);
		sep.setTextColor(ThemeColors.text());
		wrap.addView(sep);

		wrap.addView(minutePicker, pLp);

		final AlertDialog[] h = {null};
		h[0] = new AppDialog.Builder(activity)
				.icon("🕐").title("Heure de synchro").subtitle("Heure et minutes")
				.content(wrap)
				.primaryBtn("ENREGISTRER", () -> {
					BankAutoSyncManager.setHour(activity, hourPicker.getValue());
					BankAutoSyncManager.setMinute(activity, minutePicker.getValue());
					AppToast.success(activity,
							"Synchro à " + BankAutoSyncManager.getTimeLabel(activity));
					try { if (h[0] != null) h[0].dismiss(); } catch (Exception ignored) {}
					refresh();
				}).show();
	}

	/**
	* Callback du bouton de connexion bancaire.
	* Charge les transactions existantes pour la détection des doublons,
	* puis affiche BankConnectionView.
	*/
	private void onBankConnectionClicked() {
		AppToast.info(activity, "Chargement en cours…");

		// Utiliser loadAll() pour récupérer les transactions existantes
		TransactionsRepository.loadAll(activity, new TransactionsRepository.OnDataLoaded() {
			@Override
			public void onLoaded(List<TransactionsModels.Transaction> transactions, List<String> members,
					List<String[]> categories) {
				// transactions = liste des transactions existantes
				BankConnectionView.show(activity, transactions != null ? transactions : new java.util.ArrayList<>());
			}

			@Override
			public void onError(String message) {
				// En cas d'erreur, afficher quand même sans doublons
				AppToast.error(activity, "Erreur chargement : " + message);
				BankConnectionView.show(activity, new java.util.ArrayList<>());
			}
		});
	}

	// ════════════════════════════════════════════════════════════════════════

	private void buildAppearanceSection() {
		LinearLayout card = sectionCard("APPARENCE");

		card.addView(darkModeRow());

		card.addView(divider());

		card.addView(rowWithValue("◉", "Couleur du thème", themeColorLabel(), v -> showThemeColorDialog()));
	}

	private void buildWidgetsSection() {
		LinearLayout card = sectionCard("WIDGETS DU DASHBOARD");

		SharedPreferences prefs = activity.getSharedPreferences(PREF_DASHBOARD, Activity.MODE_PRIVATE);

		String[] sectionKeys = HomeWidgetRegistry.getSectionKeys();
		String[] sectionTitles = HomeWidgetRegistry.getSectionTitles();

		for (int i = 0; i < sectionKeys.length; i++) {
			final String key = sectionKeys[i];
			final String title = sectionTitles[i];

			if (i > 0)
				card.addView(divider());
			card.addView(widgetSwitchRow(prefs, key, title, "Section principale"));
		}

		card.addView(widgetGroupLabel("WIDGETS INTELLIGENTS"));

		String[] dynKeys = HomeWidgetRegistry.getDynamicKeys();
		String[] dynTitles = HomeWidgetRegistry.getDynamicTitles();

		for (int i = 0; i < dynKeys.length; i++) {
			final String key = dynKeys[i];
			final String title = dynTitles[i];

			card.addView(divider());
			card.addView(widgetSwitchRow(prefs, key, title, "Widget intelligent"));
		}
	}

	private View widgetSwitchRow(SharedPreferences prefs, String key, String title, String subtitle) {
		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(DS.dp(activity, 18), DS.dp(activity, 14), DS.dp(activity, 18), DS.dp(activity, 14));

		LinearLayout texts = new LinearLayout(activity);
		texts.setOrientation(LinearLayout.VERTICAL);

		TextView tvTitle = new TextView(activity);
		tvTitle.setText(title);
		tvTitle.setTextColor(ThemeColors.text());
		tvTitle.setTextSize(17f);
		tvTitle.setTypeface(null, Typeface.BOLD);
		texts.addView(tvTitle);

		TextView tvSub = new TextView(activity);
		tvSub.setText(subtitle);
		tvSub.setTextColor(ThemeColors.subtext());
		tvSub.setTextSize(13f);
		LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
		subLp.topMargin = DS.dp(activity, 2);
		texts.addView(tvSub, subLp);

		row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

		Switch sw = new Switch(activity);
		sw.setChecked(prefs.getBoolean(key, true));
		sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
			prefs.edit().putBoolean(key, isChecked).apply();
		});

		try {
			sw.setThumbTintList(android.content.res.ColorStateList.valueOf(ThemeColors.primary()));
		} catch (Exception ignored) {
		}

		row.addView(sw);

		return row;
	}

	private View widgetGroupLabel(String label) {
		TextView tv = new TextView(activity);
		tv.setText(label);
		tv.setTextColor(ThemeColors.subtext());
		tv.setTextSize(11f);
		tv.setTypeface(null, Typeface.BOLD);
		tv.setLetterSpacing(0.14f);

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.topMargin = DS.dp(activity, 6);
		lp.bottomMargin = DS.dp(activity, 2);
		lp.leftMargin = DS.dp(activity, 92);
		tv.setLayoutParams(lp);

		return tv;
	}

	private void buildDataSection() {
		LinearLayout card = sectionCard("DONNÉES");

		card.addView(row("📤", "Exporter les données", "CSV ou PDF", v -> SettingsDialogs.showExport(activity)));

		card.addView(divider());

		card.addView(row("📥", "Importer", "Depuis un fichier CSV ou PDF", v -> SettingsDialogs.showImport(activity)));

		card.addView(divider());

		card.addView(row("🔄", "Synchronisation", lastSyncLabel(),
				v -> SettingsDialogs.showSync(activity, this::refresh)));
	}

	private void buildDangerSection() {
		LinearLayout card = sectionCard("ACTIONS SENSIBLES");

		card.addView(dangerRow("\u21AA", "Quitter le foyer", "Vous perdrez l'accès aux données partagées",
				v -> confirmLeaveHousehold()));

		card.addView(divider());

		card.addView(dangerRow("\u232B", "Supprimer le compte", "Suppression définitive de votre compte",
				v -> SettingsDialogs.confirmDeleteAccount(activity)));

		card.addView(divider());

		card.addView(dangerRow("\u00D7", "Déconnexion", "Se déconnecter de l'application",
				v -> SettingsDialogs.confirmLogout(activity)));

		TextView note = new TextView(activity);
		note.setText("Ces actions sont permanentes et ne peuvent pas être annulées.");
		note.setTextColor(ThemeColors.subtext());
		note.setTextSize(13f);
		note.setGravity(Gravity.CENTER);
		LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(-1, -2);
		nLp.topMargin = DS.dp(activity, 18);
		body.addView(note, nLp);
	}

	/** Confirmation puis sortie du foyer (API HouseholdManager.leaveHousehold). */
	private void confirmLeaveHousehold() {
		TextView msg = new TextView(activity);
		msg.setText("Vous quitterez ce foyer et perdrez l'accès aux données partagées "
				+ "(transactions, budgets, comptes communs). Cette action est irréversible.");
		msg.setTextColor(ThemeColors.text());
		msg.setTextSize(14.5f);
		int p = DS.dp(activity, 4);
		msg.setPadding(p, p, p, p);

		final AlertDialog[] h = {null};
		h[0] = new AppDialog.Builder(activity)
				.icon("\u21AA").title("Quitter le foyer").subtitle("Action irréversible")
				.content(msg)
				.primaryBtn("QUITTER", () -> {
					AppToast.info(activity, "Sortie du foyer…");
					HouseholdManager.getInstance().leaveHousehold(new com.couplefinance.data.FirestoreManager.Callback() {
						@Override public void onSuccess(String response) {
							try { HouseholdManager.getInstance().clearHousehold(); } catch (Exception ignored) {}
							AppToast.success(activity, "Vous avez quitté le foyer");
							try { if (h[0] != null) h[0].dismiss(); } catch (Exception ignored) {}
							try { activity.recreate(); } catch (Exception ignored) {}
						}
						@Override public void onError(String message) {
							AppToast.error(activity, "Erreur : " + message);
						}
					});
				}).show();
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Helpers UI
	// ─────────────────────────────────────────────────────────────────────────

	private LinearLayout sectionCard(String title) {
		return collapsibleSectionCard(title, true, false);
	}

	/**
	 * Carte de section repliable (accordéon).
	 * @param expanded    contenu visible par défaut
	 * @param collapsible si false → pas de chevron, toujours ouvert (comportement classique)
	 */
	private LinearLayout collapsibleSectionCard(String title, boolean expanded, boolean collapsible) {
		LinearLayout header = new LinearLayout(activity);
		header.setOrientation(LinearLayout.HORIZONTAL);
		header.setGravity(Gravity.CENTER_VERTICAL);

		TextView sectionTitle = new TextView(activity);
		sectionTitle.setText(title);
		sectionTitle.setTextColor(ThemeColors.subtext());
		sectionTitle.setTextSize(12f);
		sectionTitle.setTypeface(null, Typeface.BOLD);
		sectionTitle.setLetterSpacing(0.18f);

		LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(0, -2, 1f);
		stLp.leftMargin = DS.dp(activity, 6);
		header.addView(sectionTitle, stLp);

		final TextView chevron = new TextView(activity);
		chevron.setTextColor(ThemeColors.subtext());
		chevron.setTextSize(12f);
		chevron.setTypeface(null, Typeface.BOLD);
		LinearLayout.LayoutParams chLp = new LinearLayout.LayoutParams(-2, -2);
		chLp.rightMargin = DS.dp(activity, 8);
		if (collapsible) header.addView(chevron, chLp);

		LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(-1, -2);
		hLp.bottomMargin = DS.dp(activity, 10);
		body.addView(header, hLp);

		final LinearLayout card = new LinearLayout(activity);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setBackground(cardBg());
		card.setElevation(DS.dp(activity, 2));
		card.setClipToOutline(false);

		LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
		cp.bottomMargin = DS.dp(activity, 28);
		body.addView(card, cp);

		if (collapsible) {
			final boolean[] open = { expanded };
			card.setVisibility(expanded ? View.VISIBLE : View.GONE);
			chevron.setText(expanded ? "\u25BE" : "\u25B8"); // ▾ / ▸
			header.setClickable(true);
			header.setFocusable(true);
			header.setOnClickListener(v -> {
				open[0] = !open[0];
				card.setVisibility(open[0] ? View.VISIBLE : View.GONE);
				chevron.setText(open[0] ? "\u25BE" : "\u25B8");
			});
		}

		return card;
	}

	private View row(String icon, String title, String subtitle, View.OnClickListener listener) {
		return baseRow(icon, title, subtitle, null, false, listener);
	}

	private View rowWithValue(String icon, String title, String value, View.OnClickListener listener) {
		return baseRow(icon, title, null, value, false, listener);
	}

	private View dangerRow(String icon, String title, String subtitle, View.OnClickListener listener) {
		return baseRow(icon, title, subtitle, null, true, listener);
	}

	private View baseRow(String icon, String title, String subtitle, String value, boolean danger,
			View.OnClickListener listener) {

		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(DS.dp(activity, 18), DS.dp(activity, 18), DS.dp(activity, 18), DS.dp(activity, 18));
		row.setClickable(true);
		row.setFocusable(true);
		row.setOnClickListener(listener);

		// (Pas d'icône : lignes texte seul, comme la maquette.)

		LinearLayout texts = new LinearLayout(activity);
		texts.setOrientation(LinearLayout.VERTICAL);

		TextView tvTitle = new TextView(activity);
		tvTitle.setText(title);
		tvTitle.setTextColor(danger ? ThemeColors.danger() : ThemeColors.text());
		tvTitle.setTextSize(20f);
		tvTitle.setTypeface(null, Typeface.BOLD);
		texts.addView(tvTitle);

		if (subtitle != null && !subtitle.isEmpty()) {
			TextView tvSub = new TextView(activity);
			tvSub.setText(subtitle);
			tvSub.setTextColor(ThemeColors.subtext());
			tvSub.setTextSize(15f);

			LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
			sp.topMargin = DS.dp(activity, 3);
			texts.addView(tvSub, sp);
		}

		row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

		if (value != null && !value.isEmpty()) {
			TextView tvValue = new TextView(activity);
			tvValue.setText(value);
			tvValue.setTextColor(ThemeColors.subtext());
			tvValue.setTextSize(17f);

			LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-2, -2);
			vp.rightMargin = DS.dp(activity, 8);
			row.addView(tvValue, vp);
		}

		TextView chevron = new TextView(activity);
		chevron.setText("›");
		chevron.setTextColor(ThemeColors.subtext());
		chevron.setTextSize(30f);
		row.addView(chevron);

		return row;
	}

	private View darkModeRow() {
		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(DS.dp(activity, 18), DS.dp(activity, 18), DS.dp(activity, 18), DS.dp(activity, 18));

		TextView title = new TextView(activity);
		title.setText("Thème sombre");
		title.setTextColor(ThemeColors.text());
		title.setTextSize(20f);
		title.setTypeface(null, Typeface.BOLD);
		row.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

		SharedPreferences prefs = activity.getSharedPreferences(PREF_THEME, Activity.MODE_PRIVATE);

		Switch sw = new Switch(activity);
		sw.setChecked(prefs.getBoolean(KEY_DARK, false));
		sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
			SettingsDialogs.toggleDarkMode(activity, isChecked);
		});

		row.addView(sw);

		return row;
	}

	private void showThemeColorDialog() {
		final String[] labels = { "Terracotta", "Sauge", "Bleu", "Lavande", "Rose", "Menthe", "Sable" };

		final int[] colors = { Color.parseColor("#C0614A"), Color.parseColor("#86B89B"), Color.parseColor("#6F9FB5"),
				Color.parseColor("#9A8BC2"), Color.parseColor("#C98CA5"), Color.parseColor("#7FB8AA"),
				Color.parseColor("#B8946B") };

		LinearLayout panel = new LinearLayout(activity);
		panel.setOrientation(LinearLayout.HORIZONTAL);
		panel.setGravity(Gravity.CENTER);
		panel.setPadding(0, DS.dp(activity, 8), 0, DS.dp(activity, 8));

		for (int i = 0; i < labels.length; i++) {
			final String label = labels[i];
			final int color = colors[i];

			TextView dot = new TextView(activity);
			dot.setText("✓");
			dot.setGravity(Gravity.CENTER);
			dot.setTextColor(Color.WHITE);
			dot.setTextSize(13f);
			dot.setTypeface(null, Typeface.BOLD);
			dot.setBackground(circleBg(color));

			LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(DS.dp(activity, 42), DS.dp(activity, 42));
			dp.leftMargin = DS.dp(activity, 5);
			dp.rightMargin = DS.dp(activity, 5);

			dot.setOnClickListener(v -> {
				try {
					com.couplefinance.core.theme.ThemeManager.getInstance().applyThemeByColor(activity, color);
				} catch (Exception ignored) {
				}

				SharedPreferences prefs = activity.getSharedPreferences(PREF_THEME, Activity.MODE_PRIVATE);
				prefs.edit().putString("theme_color_label", label).putInt("theme_color_value", color).apply();

				AppToast.success(activity, "Thème : " + label);
				refresh();
			});

			panel.addView(dot, dp);
		}

		com.couplefinance.core.ui.dialogs.PremiumDialog.builder(activity).icon("◉").title("Couleur du thème")
				.subtitle("Choisissez une couleur pastel.").view(panel).primary("Fermer", () -> {
				}).noSecondary().show();
	}

	private View divider() {
		View div = new View(activity);
		div.setBackgroundColor(ThemeColors.divider());

		LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, DS.dp(activity, 1));
		dp.leftMargin = DS.dp(activity, 92);
		div.setLayoutParams(dp);

		return div;
	}

	private GradientDrawable cardBg() {
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(ThemeColors.card());
		bg.setCornerRadius(DS.dp(activity, 24));
		bg.setStroke(DS.dp(activity, 1), ThemeColors.border());
		return bg;
	}

	private GradientDrawable heroBg() {
		int c = getThemeColor();
		return new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
				new int[] { adjustAlpha(c, 235), ThemeColors.primary() }) {
			{
				setCornerRadius(DS.dp(activity, 28));
			}
		};
	}

	private GradientDrawable roundBg(int color, int radius) {
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(color);
		bg.setCornerRadius(DS.dp(activity, radius));
		return bg;
	}

	private GradientDrawable circleBg(int color) {
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(color);
		bg.setShape(GradientDrawable.OVAL);
		return bg;
	}

	private int getThemeColor() {
		return activity.getSharedPreferences(PREF_THEME, Activity.MODE_PRIVATE).getInt("theme_color_value",
				ThemeColors.primary());
	}

	private String themeColorLabel() {
		return activity.getSharedPreferences(PREF_THEME, Activity.MODE_PRIVATE).getString("theme_color_label",
				"Terracotta");
	}

	private int adjustAlpha(int color, int alpha) {
		return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
	}

	private String getRatioValue() {
		try {
			int[] r = com.couplefinance.ui.repartition.RepartitionRepository.loadRatio(activity);
			if (r != null && r.length > 0) {
				return r[0] + "/" + (100 - r[0]);
			}
		} catch (Exception ignored) {
		}
		return "50/50";
	}

	private String jointAccountValue() {
		try {
			return JointAccountManager.getInstance().isEnabledLocal() ? "Activé" : "Désactivé";
		} catch (Exception ignored) {
			return "Désactivé";
		}
	}

	private String lastSyncLabel() {
		SharedPreferences prefs = activity.getSharedPreferences(PREF_SYNC, Activity.MODE_PRIVATE);

		String label = prefs.getString(KEY_LAST_SYNC_LABEL, null);
		if (label != null && !label.trim().isEmpty()) {
			return "Dernière synchro : " + label;
		}

		long ts = prefs.getLong(KEY_LAST_SYNC, 0);
		if (ts > 0) {
			java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",
					java.util.Locale.FRANCE);
			return "Dernière synchro : " + sdf.format(new java.util.Date(ts));
		}

		return "Dernière synchro : jamais";
	}

	private String firstLetter(String value) {
		if (value == null || value.trim().isEmpty()) {
			return "F";
		}
		return value.trim().substring(0, 1).toUpperCase(java.util.Locale.FRANCE);
	}

	private void refresh() {
		if (content == null)
			return;

		content.removeAllViews();

		buildHeader();
		buildHero();

		body = new LinearLayout(activity);
		body.setOrientation(LinearLayout.VERTICAL);
		content.addView(body, new LinearLayout.LayoutParams(-1, -2));

		showMenu();
	}
}