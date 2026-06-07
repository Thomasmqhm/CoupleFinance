package com.couplefinance.ui.virements;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.couplefinance.AppToast;
import com.couplefinance.core.base.BaseView;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.Fmt;
import com.couplefinance.core.ui.animations.PressAnimations;
import com.couplefinance.data.JointAccountManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class VirementView extends BaseView {

	private static final String FILTER_CURRENT = "current";
	private static final String FILTER_LAST = "last";
	private static final String FILTER_ALL = "all";

	private final Activity activity;
	private final VirementRepository repository;
	private final VirementLogic logic;

	private final ArrayList<VirementModels.Beneficiary> allBeneficiaries = new ArrayList<>();
	private final ArrayList<VirementModels.Beneficiary> filteredBeneficiaries = new ArrayList<>();
	private final ArrayList<VirementModels.Transfer> allTransfers = new ArrayList<>();
	private final ArrayList<VirementModels.Transfer> filteredTransfers = new ArrayList<>();
	private final ArrayList<String> members = new ArrayList<>();

	private LinearLayout root;
	private LinearLayout beneficiariesCard;
	private LinearLayout beneficiariesList;
	private LinearLayout historyCard;
	private LinearLayout historyList;

	private TextView tvHeroAmount;
	private TextView tvHeroSubtitle;
	private TextView tvHeroCount;
	private TextView tvHeroBenes;

	private Button btnCurrent;
	private Button btnLast;
	private Button btnAll;

	private String filterPeriod = FILTER_CURRENT;
	private String searchBene = "";
	private String searchTransfer = "";

	public VirementView(Activity activity) {
		super(activity);
		this.activity = activity;
		this.repository = new VirementRepository();
		this.logic = new VirementLogic(repository);

		try {
			JointAccountManager.getInstance().init(activity);
		} catch (Exception ignored) {
		}
	}

	public View getView() {
		ScrollView scroll = new ScrollView(activity);
		scroll.setFillViewport(true);
		scroll.setClipToPadding(false);
		scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
		scroll.setBackgroundColor(Color.parseColor("#F8F3EF"));
		scroll.setPadding(0, 0, 0, 0);

		root = new LinearLayout(activity);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(dp(20), dp(14), dp(20), dp(DS.NAV_CLEARANCE));

		scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

		buildHeader();
		buildHero();
		buildBeneficiaries();
		buildHistory();

		load();

		return scroll;
	}

	private void buildHeader() {
		LinearLayout header = new LinearLayout(activity);
		header.setOrientation(LinearLayout.HORIZONTAL);
		header.setGravity(Gravity.CENTER_VERTICAL);

		root.addView(header, new LinearLayout.LayoutParams(-1, -2));

		TextView title = new TextView(activity);
		title.setText("Virements");
		title.setTextColor(ThemeColors.text());
		title.setTextSize(31);
		title.setTypeface(null, Typeface.BOLD);
		title.setSingleLine(true);
		title.setIncludeFontPadding(false);

		header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

		Button btnBene = pillButton("☻  Bénéficiaire");
		btnBene.setOnClickListener(v -> openAddBeneficiary());

		LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(dp(158), dp(42));
		b1.leftMargin = dp(10);
		header.addView(btnBene, b1);
	}

	private void buildHero() {
		LinearLayout hero = new LinearLayout(activity);
		hero.setOrientation(LinearLayout.VERTICAL);
		hero.setPadding(dp(22), dp(18), dp(22), dp(16));
		hero.setBackground(rounded(Color.WHITE, dp(24), Color.parseColor("#EEE5DC"), 1));
		hero.setElevation(dp(1));

		LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, dp(164));
		hp.topMargin = dp(22);
		root.addView(hero, hp);

		TextView arrow = new TextView(activity);
		arrow.setText("↔");
		arrow.setTextSize(62);
		arrow.setTypeface(null, Typeface.BOLD);
		arrow.setTextColor(Color.argb(17, 0, 0, 0));
		arrow.setIncludeFontPadding(false);

		LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(-2, -2);
		arrowLp.gravity = Gravity.RIGHT;
		arrowLp.bottomMargin = -dp(12);
		hero.addView(arrow, arrowLp);

		TextView label = new TextView(activity);
		label.setText("Total viré ce mois");
		label.setTextSize(14);
		label.setTextColor(ThemeColors.subtext());
		label.setIncludeFontPadding(false);
		hero.addView(label);

		tvHeroAmount = new TextView(activity);
		tvHeroAmount.setText("0,00 €");
		tvHeroAmount.setTextSize(47);
		tvHeroAmount.setTypeface(null, Typeface.BOLD);
		tvHeroAmount.setTextColor(ThemeColors.text());
		tvHeroAmount.setIncludeFontPadding(false);

		LinearLayout.LayoutParams amountLp = new LinearLayout.LayoutParams(-1, -2);
		amountLp.topMargin = dp(8);
		hero.addView(tvHeroAmount, amountLp);

		tvHeroSubtitle = new TextView(activity);
		tvHeroSubtitle.setText("Aucun virement ce mois-ci");
		tvHeroSubtitle.setTextColor(ThemeColors.subtext());
		tvHeroSubtitle.setTextSize(12);
		tvHeroSubtitle.setIncludeFontPadding(false);

		LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
		subLp.topMargin = dp(3);
		hero.addView(tvHeroSubtitle, subLp);

		View divider = new View(activity);
		divider.setBackgroundColor(Color.parseColor("#EEE7E0"));

		LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(-1, dp(1));
		dividerLp.topMargin = dp(12);
		dividerLp.bottomMargin = dp(9);
		hero.addView(divider, dividerLp);

		LinearLayout stats = new LinearLayout(activity);
		stats.setOrientation(LinearLayout.HORIZONTAL);
		stats.setGravity(Gravity.CENTER_VERTICAL);
		hero.addView(stats, new LinearLayout.LayoutParams(-1, -2));

		tvHeroCount = heroStat(stats, "Virements", "0");

		View sep = new View(activity);
		sep.setBackgroundColor(Color.parseColor("#EEE7E0"));

		LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(dp(1), dp(34));
		sepLp.leftMargin = dp(20);
		sepLp.rightMargin = dp(20);
		stats.addView(sep, sepLp);

		tvHeroBenes = heroStat(stats, "Bénéficiaires", "0");
	}

	private TextView heroStat(LinearLayout parent, String label, String value) {
		LinearLayout box = new LinearLayout(activity);
		box.setOrientation(LinearLayout.VERTICAL);

		TextView l = new TextView(activity);
		l.setText(label);
		l.setTextSize(12);
		l.setTextColor(ThemeColors.subtext());
		l.setIncludeFontPadding(false);

		TextView v = new TextView(activity);
		v.setText(value);
		v.setTextSize(18);
		v.setTypeface(null, Typeface.BOLD);
		v.setTextColor(ThemeColors.text());
		v.setIncludeFontPadding(false);

		LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-1, -2);
		vlp.topMargin = dp(4);

		box.addView(l);
		box.addView(v, vlp);

		parent.addView(box);

		return v;
	}

	private void buildBeneficiaries() {
		TextView title = sectionTitle("Bénéficiaires enregistrés");

		LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
		tlp.topMargin = dp(22);
		tlp.bottomMargin = dp(10);
		root.addView(title, tlp);

		beneficiariesCard = new LinearLayout(activity);
		beneficiariesCard.setOrientation(LinearLayout.VERTICAL);
		beneficiariesCard.setPadding(dp(14), dp(12), dp(14), dp(12));
		beneficiariesCard.setBackground(rounded(Color.WHITE, dp(24), Color.parseColor("#EEE5DC"), 1));
		beneficiariesCard.setElevation(dp(1));

		root.addView(beneficiariesCard, new LinearLayout.LayoutParams(-1, -2));

		EditText search = searchField("Rechercher...");
		beneficiariesCard.addView(search, new LinearLayout.LayoutParams(-1, dp(42)));

		search.addTextChangedListener(new SimpleWatcher() {
			@Override
			public void afterTextChanged(Editable s) {
				searchBene = s == null ? "" : s.toString();
				applyFilters();
			}
		});

		beneficiariesList = new LinearLayout(activity);
		beneficiariesList.setOrientation(LinearLayout.VERTICAL);

		LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
		blp.topMargin = dp(12);
		beneficiariesCard.addView(beneficiariesList, blp);
	}

	private void buildHistory() {
		TextView title = sectionTitle("Historique");

		LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
		tlp.topMargin = dp(24);
		tlp.bottomMargin = dp(10);
		root.addView(title, tlp);

		historyCard = new LinearLayout(activity);
		historyCard.setOrientation(LinearLayout.VERTICAL);
		historyCard.setPadding(dp(8), dp(8), dp(8), dp(12));
		historyCard.setBackground(rounded(Color.WHITE, dp(24), Color.parseColor("#EEE5DC"), 1));
		historyCard.setElevation(dp(1));

		root.addView(historyCard, new LinearLayout.LayoutParams(-1, -2));

		LinearLayout filters = new LinearLayout(activity);
		filters.setOrientation(LinearLayout.HORIZONTAL);
		filters.setGravity(Gravity.CENTER_VERTICAL);
		filters.setBackground(rounded(Color.parseColor("#FBF6F1"), dp(20), Color.TRANSPARENT, 0));
		filters.setPadding(dp(4), dp(4), dp(4), dp(4));

		historyCard.addView(filters, new LinearLayout.LayoutParams(-1, dp(46)));

		btnCurrent = filterButton("Ce mois");
		btnLast = filterButton("Mois préc.");
		btnAll = filterButton("Tout");

		filters.addView(btnCurrent, new LinearLayout.LayoutParams(0, -1, 1));

		LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, -1, 1);
		lp2.leftMargin = dp(4);
		filters.addView(btnLast, lp2);

		LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(0, -1, 1);
		lp3.leftMargin = dp(4);
		filters.addView(btnAll, lp3);

		btnCurrent.setOnClickListener(v -> {
			filterPeriod = FILTER_CURRENT;
			updateFilters();
			applyFilters();
		});

		btnLast.setOnClickListener(v -> {
			filterPeriod = FILTER_LAST;
			updateFilters();
			applyFilters();
		});

		btnAll.setOnClickListener(v -> {
			filterPeriod = FILTER_ALL;
			updateFilters();
			applyFilters();
		});

		updateFilters();

		Button btnNewTransfer = bigTransferButton();
		btnNewTransfer.setOnClickListener(v -> openAddTransfer());

		LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, dp(54));
		nlp.topMargin = dp(10);
		historyCard.addView(btnNewTransfer, nlp);

		historyList = new LinearLayout(activity);
		historyList.setOrientation(LinearLayout.VERTICAL);

		LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
		hlp.topMargin = dp(10);
		historyCard.addView(historyList, hlp);
	}

	private static List<VirementModels.Transfer> dedupTransfers(List<VirementModels.Transfer> raw) {
		if (raw == null) return new java.util.ArrayList<>();
		java.util.LinkedHashMap<String, VirementModels.Transfer> seen = new java.util.LinkedHashMap<>();
		for (VirementModels.Transfer t : raw) {
			// Clé : from|to|montant arrondi à 2 décimales|jour (ms tronqué au jour)
			long dayMs = (t.dateMs / 86400000L) * 86400000L;
			String key = t.from + "|" + t.to + "|"
					+ String.format(java.util.Locale.US, "%.2f", t.amount) + "|" + dayMs;
			// On garde le premier (docPath non vide en priorité)
			if (!seen.containsKey(key) || seen.get(key).docPath.isEmpty()) {
				seen.put(key, t);
			}
		}
		return new java.util.ArrayList<>(seen.values());
	}

	private void load() {
		repository.loadAll(activity, data -> {
			allBeneficiaries.clear();
			allBeneficiaries.addAll(data.beneficiaries);

			allTransfers.clear();
			allTransfers.addAll(dedupTransfers(data.transfers));

			Collections.sort(allTransfers, (a, b) -> Long.compare(b.dateMs, a.dateMs));

			members.clear();
			for (String m : data.members) {
				if (m == null || m.trim().isEmpty())
					continue;

				if (isJointName(m))
					continue;

				boolean exists = false;
				for (String existing : members) {
					if (existing.equalsIgnoreCase(m.trim())) {
						exists = true;
						break;
					}
				}

				if (!exists)
					members.add(m.trim());
			}

			applyFilters();
		});
	}

	private void applyFilters() {
		filteredBeneficiaries.clear();

		String qBene = norm(searchBene);

		for (VirementModels.Beneficiary bene : allBeneficiaries) {
			if (qBene.isEmpty() || norm(bene.name).contains(qBene) || norm(bene.iban).contains(qBene)) {
				filteredBeneficiaries.add(bene);
			}
		}

		filteredTransfers.clear();

		String qTransfer = norm(searchTransfer);

		for (VirementModels.Transfer t : allTransfers) {
			if (!matchesPeriod(t.dateMs)) {
				continue;
			}

			String hay = norm(t.from + " " + t.to + " " + t.motif + " " + Fmt.money(t.amount));

			if (qTransfer.isEmpty() || hay.contains(qTransfer)) {
				filteredTransfers.add(t);
			}
		}

		renderBeneficiaries();
		renderTransfers();
		updateHero();
	}

	private void renderBeneficiaries() {
		beneficiariesList.removeAllViews();

		if (filteredBeneficiaries.isEmpty()) {
			beneficiariesList.addView(emptyCard("Aucun bénéficiaire"));
			return;
		}

		for (VirementModels.Beneficiary bene : filteredBeneficiaries) {
			LinearLayout card = new LinearLayout(activity);
			card.setOrientation(LinearLayout.HORIZONTAL);
			card.setGravity(Gravity.CENTER_VERTICAL);
			card.setPadding(dp(8), dp(7), dp(8), dp(7));

			TextView avatar = new TextView(activity);
			avatar.setText(Fmt.initial(bene.name));
			avatar.setTextSize(16);
			avatar.setTypeface(null, Typeface.BOLD);
			avatar.setGravity(Gravity.CENTER);
			avatar.setTextColor(ThemeColors.primary());
			avatar.setIncludeFontPadding(false);
			avatar.setBackground(
					rounded(ThemeColors.withAlpha(ThemeColors.primary(), 22), dp(22), Color.TRANSPARENT, 0));

			card.addView(avatar, new LinearLayout.LayoutParams(dp(44), dp(44)));

			LinearLayout texts = new LinearLayout(activity);
			texts.setOrientation(LinearLayout.VERTICAL);
			texts.setGravity(Gravity.CENTER_VERTICAL);

			LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1);
			tp.leftMargin = dp(16);
			card.addView(texts, tp);

			TextView name = new TextView(activity);
			name.setText(bene.name == null || bene.name.trim().isEmpty() ? "Bénéficiaire" : bene.name);
			name.setTextSize(17);
			name.setTypeface(null, Typeface.BOLD);
			name.setTextColor(ThemeColors.text());
			name.setSingleLine(true);
			name.setIncludeFontPadding(false);
			texts.addView(name);

			TextView iban = new TextView(activity);
			iban.setText(maskIban(bene.iban));
			iban.setTextSize(14);
			iban.setTextColor(ThemeColors.subtext());
			iban.setSingleLine(true);
			iban.setIncludeFontPadding(false);

			LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, -2);
			ilp.topMargin = dp(3);
			texts.addView(iban, ilp);

			beneficiariesList.addView(card);
		}
	}

	private void renderTransfers() {
		historyList.removeAllViews();

		if (filteredTransfers.isEmpty()) {
			historyList.addView(emptyCard("Aucun virement"));
			return;
		}

		for (VirementModels.Transfer t : filteredTransfers) {
			LinearLayout card = new LinearLayout(activity);
			card.setOrientation(LinearLayout.HORIZONTAL);
			card.setGravity(Gravity.CENTER_VERTICAL);
			card.setPadding(dp(8), dp(8), dp(8), dp(8));

			TextView avatar = new TextView(activity);
			avatar.setText("↗");
			avatar.setTextSize(18);
			avatar.setGravity(Gravity.CENTER);
			avatar.setTextColor(ThemeColors.primary());
			avatar.setIncludeFontPadding(false);
			avatar.setBackground(
					rounded(ThemeColors.withAlpha(ThemeColors.primary(), 24), dp(22), Color.TRANSPARENT, 0));

			card.addView(avatar, new LinearLayout.LayoutParams(dp(44), dp(44)));

			LinearLayout texts = new LinearLayout(activity);
			texts.setOrientation(LinearLayout.VERTICAL);
			texts.setGravity(Gravity.CENTER_VERTICAL);

			LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1);
			tp.leftMargin = dp(16);
			card.addView(texts, tp);

			TextView label = new TextView(activity);
			label.setText(buildTransferTitle(t));
			label.setTextSize(17);
			label.setTypeface(null, Typeface.BOLD);
			label.setTextColor(ThemeColors.text());
			label.setSingleLine(true);
			label.setIncludeFontPadding(false);
			texts.addView(label);

			TextView motif = new TextView(activity);
			motif.setText(
					(t.motif == null || t.motif.isEmpty() ? "Virement" : t.motif) + " · " + Fmt.dateShort(t.dateMs));
			motif.setTextColor(ThemeColors.subtext());
			motif.setTextSize(13);
			motif.setSingleLine(true);
			motif.setIncludeFontPadding(false);

			LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
			mlp.topMargin = dp(3);
			texts.addView(motif, mlp);

			TextView amount = new TextView(activity);
			amount.setText(Fmt.money(t.amount));
			amount.setTextSize(18);
			amount.setTypeface(null, Typeface.BOLD);
			amount.setTextColor(ThemeColors.text());
			amount.setSingleLine(true);
			amount.setIncludeFontPadding(false);

			card.addView(amount);

			final VirementModels.Transfer transferRef = t;
			card.setOnLongClickListener(v -> {
				VirementDialogs.showDeleteTransferDialog(activity, transferRef, () ->
						repository.deleteTransfer(transferRef, activity, (success, message) -> {
							if (success) {
								AppToast.success(activity, "Virement supprimé");
								load();
							} else {
								AppToast.error(activity, "Suppression impossible : " + message);
							}
						}));
				return true;
			});

			historyList.addView(card);
		}
	}

	private String buildTransferTitle(VirementModels.Transfer t) {
		if (t == null)
			return "Virement";

		String from = t.from == null ? "" : t.from.trim();
		String to = t.to == null ? "" : t.to.trim();

		if (from.isEmpty() && to.isEmpty())
			return "Virement";

		if (from.isEmpty())
			return to;

		if (to.isEmpty())
			return from;

		return from + " → " + to;
	}

	private void updateHero() {
		double total = 0;
		int currentCount = 0;

		for (VirementModels.Transfer t : allTransfers) {
			if (isCurrentMonth(t.dateMs)) {
				total += t.amount;
				currentCount++;
			}
		}

		tvHeroAmount.setText(Fmt.money(total));
		tvHeroSubtitle.setText(currentCount == 0 ? "Aucun virement ce mois-ci" : currentCount + " virement(s)");
		tvHeroCount.setText(String.valueOf(filteredTransfers.size()));
		tvHeroBenes.setText(String.valueOf(allBeneficiaries.size()));
	}

	private void updateFilters() {
		styleFilter(btnCurrent, FILTER_CURRENT.equals(filterPeriod));
		styleFilter(btnLast, FILTER_LAST.equals(filterPeriod));
		styleFilter(btnAll, FILTER_ALL.equals(filterPeriod));
	}

	private void styleFilter(Button b, boolean active) {
		if (b == null)
			return;

		b.setTextColor(active ? ThemeColors.text() : ThemeColors.subtext());
		b.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
		b.setTextSize(14);
		b.setAllCaps(false);
		b.setStateListAnimator(null);
		b.setGravity(Gravity.CENTER);
		b.setPadding(0, 0, 0, 0);
		b.setBackground(active ? rounded(Color.WHITE, dp(16), Color.parseColor("#E9DED2"), 1)
				: rounded(Color.TRANSPARENT, dp(16), Color.TRANSPARENT, 0));
		b.setElevation(active ? dp(1) : 0);
		PressAnimations.applySoft(b);
	}

	private Button filterButton(String text) {
		Button b = new Button(activity);
		b.setText(text);
		b.setAllCaps(false);
		b.setTextSize(14);
		b.setTypeface(null, Typeface.NORMAL);
		b.setTextColor(ThemeColors.subtext());
		b.setGravity(Gravity.CENTER);
		b.setPadding(0, 0, 0, 0);
		b.setStateListAnimator(null);
		b.setBackground(rounded(Color.TRANSPARENT, dp(16), Color.TRANSPARENT, 0));
		b.setElevation(0f);
		PressAnimations.applySoft(b);
		return b;
	}

	private Button pillButton(String text) {
		Button b = new Button(activity);
		b.setText(text);
		b.setAllCaps(false);
		b.setTextSize(14);
		b.setTypeface(null, Typeface.BOLD);
		b.setGravity(Gravity.CENTER);
		b.setPadding(0, 0, 0, 0);
		b.setTextColor(ThemeColors.text());
		b.setBackground(rounded(Color.parseColor("#EFE7DE"), dp(21), Color.TRANSPARENT, 0));
		b.setStateListAnimator(null);
		b.setElevation(0f);
		PressAnimations.applySoft(b);
		return b;
	}

	private Button bigTransferButton() {
		Button b = new Button(activity);
		b.setText("↔  Nouveau virement");
		b.setAllCaps(false);
		b.setTextSize(18);
		b.setTypeface(null, Typeface.BOLD);
		b.setTextColor(Color.WHITE);
		b.setGravity(Gravity.CENTER);
		b.setPadding(0, 0, 0, 0);
		b.setStateListAnimator(null);
		b.setBackground(rounded(ThemeColors.primary(), dp(26), Color.TRANSPARENT, 0));
		b.setElevation(dp(2));
		PressAnimations.applySoft(b);
		return b;
	}

	private EditText searchField(String hint) {
		EditText e = new EditText(activity);
		e.setHint(hint);
		e.setSingleLine(true);
		e.setTextSize(15);
		e.setTextColor(ThemeColors.text());
		e.setHintTextColor(ThemeColors.subtext());
		e.setPadding(dp(18), 0, dp(18), 0);
		e.setMinHeight(dp(42));
		e.setBackground(rounded(Color.parseColor("#FBF7F3"), dp(18), Color.TRANSPARENT, 0));
		return e;
	}

	private View emptyCard(String text) {
		LinearLayout box = new LinearLayout(activity);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setGravity(Gravity.CENTER);
		box.setPadding(dp(18), dp(18), dp(18), dp(18));

		TextView tv = new TextView(activity);
		tv.setText(text);
		tv.setTextSize(15);
		tv.setTextColor(ThemeColors.subtext());
		tv.setGravity(Gravity.CENTER);
		tv.setIncludeFontPadding(false);

		box.addView(tv);

		return box;
	}

	private TextView sectionTitle(String text) {
		TextView tv = new TextView(activity);
		tv.setText(text);
		tv.setTextSize(24);
		tv.setTypeface(null, Typeface.BOLD);
		tv.setTextColor(ThemeColors.text());
		tv.setIncludeFontPadding(false);
		return tv;
	}

	private GradientDrawable rounded(int color, int radius, int stroke, int strokeDp) {
		GradientDrawable d = new GradientDrawable();
		d.setColor(color);
		d.setCornerRadius(radius);

		if (strokeDp > 0) {
			d.setStroke(dp(strokeDp), stroke);
		}

		return d;
	}

	private boolean matchesPeriod(long dateMs) {
		if (FILTER_ALL.equals(filterPeriod))
			return true;
		if (FILTER_LAST.equals(filterPeriod))
			return isLastMonth(dateMs);
		return isCurrentMonth(dateMs);
	}

	private boolean isCurrentMonth(long dateMs) {
		Calendar now = Calendar.getInstance(Locale.FRANCE);
		Calendar c = Calendar.getInstance(Locale.FRANCE);
		c.setTimeInMillis(dateMs);

		return now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.MONTH) == c.get(Calendar.MONTH);
	}

	private boolean isLastMonth(long dateMs) {
		Calendar last = Calendar.getInstance(Locale.FRANCE);
		last.add(Calendar.MONTH, -1);

		Calendar c = Calendar.getInstance(Locale.FRANCE);
		c.setTimeInMillis(dateMs);

		return last.get(Calendar.YEAR) == c.get(Calendar.YEAR) && last.get(Calendar.MONTH) == c.get(Calendar.MONTH);
	}

	private String maskIban(String iban) {
		if (iban == null || iban.isEmpty())
			return "IBAN";

		String clean = iban.replace(" ", "");

		if (clean.length() < 8)
			return clean;

		return clean.substring(0, 4) + " •••• " + clean.substring(clean.length() - 4);
	}

	private void openAddBeneficiary() {
		VirementDialogs.showAddBeneficiaryDialog(activity, (name, iban) -> {
			repository.addBeneficiary(name, iban, activity, (success, msg) -> {
				if (success) {
					AppToast.success(activity, "Bénéficiaire ajouté");
					load();
				} else {
					AppToast.error(activity, msg);
				}
			});
		});
	}

	private void openAddTransfer() {
		VirementDialogs.showAddTransferDialog(activity, buildTransferMembersWithJoint(), allBeneficiaries,
				this::saveTransfer);
	}

	private void openAddTransferTo(String bene) {
		VirementDialogs.showAddTransferDialog(activity, buildTransferMembersWithJoint(), allBeneficiaries,
				(from, to, amount, motif, dateMs, fromInternal, toInternal) ->
						saveTransfer(from, bene, amount, motif, dateMs, fromInternal, false));
	}

	private ArrayList<String> buildTransferMembersWithJoint() {
		ArrayList<String> list = new ArrayList<>();

		for (String m : members) {
			if (m == null || m.trim().isEmpty())
				continue;

			if (isJointName(m))
				continue;

			boolean exists = false;
			for (String existing : list) {
				if (existing.equalsIgnoreCase(m.trim())) {
					exists = true;
					break;
				}
			}

			if (!exists)
				list.add(m.trim());
		}

		try {
			JointAccountManager jm = JointAccountManager.getInstance();
			jm.init(activity);

			if (jm.isEnabledLocal()) {
				String jointName = jm.getNameLocal();

				boolean exists = false;
				for (String m : list) {
					if (m.equalsIgnoreCase(jointName)) {
						exists = true;
						break;
					}
				}

				if (!exists)
					list.add(jointName);
			}
		} catch (Exception ignored) {
		}

		return list;
	}

	private void saveTransfer(String from, String to, double amount, String motif, long dateMs, boolean fromInternal,
			boolean toInternal) {

		logic.doTransfer(from, to, amount, motif, dateMs, fromInternal, toInternal, activity, (success, msg) -> {
			if (success) {
				AppToast.success(activity, "Virement ajouté");
				load();
			} else {
				AppToast.error(activity, msg);
			}
		});
	}

	private boolean isJointName(String value) {
		if (value == null)
			return false;

		String clean = value.trim().toLowerCase(Locale.FRANCE);

		return clean.equals("compte joint")
				|| clean.equals("joint")
				|| clean.equals("compte commun")
				|| clean.equals(JointAccountManager.DEFAULT_NAME.toLowerCase(Locale.FRANCE));
	}

	private String norm(String s) {
		return s == null ? "" : s.toLowerCase(Locale.FRANCE);
	}

	private abstract static class SimpleWatcher implements TextWatcher {
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		}

		public void onTextChanged(CharSequence s, int start, int before, int count) {
		}
	}

	@Override
	protected int dp(int v) {
		return DS.dp(activity, v);
	}
}