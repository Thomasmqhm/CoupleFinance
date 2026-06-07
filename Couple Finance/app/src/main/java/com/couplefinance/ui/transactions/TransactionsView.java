package com.couplefinance.ui.transactions;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;

import com.couplefinance.R;
import com.couplefinance.core.base.BaseView;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.Fmt;
import com.couplefinance.core.ui.animations.PressAnimations;
import com.couplefinance.core.ui.components.PremiumChip;
import com.couplefinance.data.FixedChargeSuggestionManager;
import com.couplefinance.data.JointAccountManager;
import com.couplefinance.data.MerchantRuleManager;
import com.couplefinance.ui.DashboardActivity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TransactionsView extends BaseView {

	private static final String PERSON_ALL = "all";
	private static final String PERSON_JOINT = "__joint__";

	private List<TransactionsModels.Transaction> allTransactions = new ArrayList<>();
	private List<TransactionsModels.Transaction> filtered = new ArrayList<>();
	private List<String> members = new ArrayList<>();
	private List<String[]> categories = new ArrayList<>();
	private List<String> availableMonths = new ArrayList<>();

	private final TransactionsModels.FilterState filterState = new TransactionsModels.FilterState();

	private TransactionsAdapter adapter;
	private ListView listTransactions;

	private TextView tvStatRevenues;
	private TextView tvStatExpenses;
	private TextView tvStatNet;
	private TextView tvStatCount;
	private TextView tvListTitle;

	private Button btnFilterAll;
	private Button btnFilterIncome;
	private Button btnFilterExpenses;
	private Button btnFilterCurrent;
	private Button btnFilterLast;
	private Button btnFilterAllTime;

	private LinearLayout llPersonButtons;
	private LinearLayout llCategoryButtons;

	private TextView btnMonthPrev;
	private TextView btnMonthNext;
	private TextView tvMonthDisplay;

	public TransactionsView(Activity activity) {
		super(activity);
		MerchantRuleManager.getInstance().init(activity);
		FixedChargeSuggestionManager.getInstance().init(activity);
		try {
			JointAccountManager.getInstance().init(activity);
		} catch (Exception ignored) {
		}
	}

	@Override
	public View getView() {
		View view = LayoutInflater.from(activity).inflate(R.layout.activity_transactions, null);

		listTransactions = view.findViewById(R.id.listTransactions);
		adapter = new TransactionsAdapter(activity, filtered, members);
		listTransactions.setAdapter(adapter);
		listTransactions.setDivider(null);
		listTransactions.setDividerHeight(dp(3));
		listTransactions.setCacheColorHint(0x00000000);
		listTransactions.setSelector(android.R.color.transparent);
		listTransactions.setOverScrollMode(View.OVER_SCROLL_NEVER);
		// Réserve la hauteur de la barre de nav pour que le dernier élément ne soit pas masqué.
		listTransactions.setClipToPadding(false);
		listTransactions.setPadding(
				listTransactions.getPaddingLeft(),
				listTransactions.getPaddingTop(),
				listTransactions.getPaddingRight(),
				dp(DS.NAV_CLEARANCE));

		listTransactions.setOnItemClickListener((parent, v, pos, id) -> TransactionsDialogs.showEditDialog(activity,
				filtered.get(pos), allTransactions, members, categories, this::reloadAfterWrite));

		listTransactions.setOnItemLongClickListener((parent, v, pos, id) -> {
			TransactionsDialogs.showDeleteDialog(activity, filtered.get(pos), allTransactions, this::reloadAfterWrite);
			return true;
		});

		Button btnAdd = view.findViewById(R.id.btnAdd);
		styleAddButton(btnAdd);
		btnAdd.setOnClickListener(v -> TransactionsDialogs.showAddDialog(activity, members, categories,
				this::reloadAfterWrite));

		injectImportButton(view);

		btnFilterAll = view.findViewById(R.id.btnFilterAll);
		btnFilterIncome = view.findViewById(R.id.btnFilterIncome);
		btnFilterExpenses = view.findViewById(R.id.btnFilterExpenses);

		btnFilterAll.setOnClickListener(v -> setTypeFilter("all"));
		btnFilterIncome.setOnClickListener(v -> setTypeFilter("income"));
		btnFilterExpenses.setOnClickListener(v -> setTypeFilter("expenses"));

		btnFilterCurrent = view.findViewById(R.id.btnFilterCurrentMonth);
		btnFilterLast = view.findViewById(R.id.btnFilterLastMonth);
		btnFilterAllTime = view.findViewById(R.id.btnFilterAll2);

		btnFilterCurrent.setOnClickListener(v -> setPeriodFilter("current"));
		btnFilterLast.setOnClickListener(v -> setPeriodFilter("last"));
		btnFilterAllTime.setOnClickListener(v -> setPeriodFilter("all"));

		injectMonthNavigation(btnFilterCurrent);

		EditText etSearch = view.findViewById(R.id.etSearch);
		if (etSearch != null) {
			etSearch.setTextColor(ThemeColors.inputText());
			etSearch.setHintTextColor(ThemeColors.inputHint());
			etSearch.setTypeface(null, Typeface.NORMAL);
			etSearch.setSingleLine(true);
			etSearch.setBackgroundColor(0x00000000);
			etSearch.setPadding(0, 0, 0, 0);

			View searchParent = (View) etSearch.getParent();
			if (searchParent != null) {
				searchParent.setBackground(rounded(ThemeColors.card(), dp(28),
						ThemeColors.withAlpha(ThemeColors.border(), 70), 1));
				searchParent.setElevation(dp(0));
			}

			etSearch.addTextChangedListener(new TextWatcher() {
				public void beforeTextChanged(CharSequence s, int a, int b, int c) {
				}

				public void afterTextChanged(Editable s) {
				}

				public void onTextChanged(CharSequence s, int a, int b, int c) {
					filterState.search = s == null ? "" : s.toString().toLowerCase(Locale.FRANCE).trim();
					applyFilters();
				}
			});
		}

		llPersonButtons = view.findViewById(R.id.llPersonButtons);
		if (llPersonButtons != null) {
			llPersonButtons.setTag("llPersonButtons");
			setupPersonPillButtons(llPersonButtons);
		}

		llCategoryButtons = injectCategorySection(view, llPersonButtons);

		tvStatRevenues = view.findViewById(R.id.tvStatRevenues);
		tvStatExpenses = view.findViewById(R.id.tvStatExpenses);
		tvStatNet = view.findViewById(R.id.tvStatNet);
		tvStatCount = view.findViewById(R.id.tvStatCount);
		tvListTitle = view.findViewById(R.id.tvListTitle);

		styleStatCards();

		styleFilterBtn(btnFilterAll, true);
		styleFilterBtn(btnFilterIncome, false);
		styleFilterBtn(btnFilterExpenses, false);
		styleFilterBtn(btnFilterCurrent, true);
		styleFilterBtn(btnFilterLast, false);
		styleFilterBtn(btnFilterAllTime, false);

		load();
		return view;
	}

	/**
	 * Branche les boutons d'import (PDF et image OCR) et le bouton de repli
	 * des filtres avancés.
	 *
	 * <p>Depuis la refonte compacte, les imports sont de petites icônes
	 * placées dans le header (XML) à côté du "+". On ne crée donc plus de
	 * boutons pleine largeur par code ; on se contente de câbler les vues
	 * définies dans le layout.</p>
	 */
	private void injectImportButton(View root) {
		Button btnImportPdf = root.findViewById(R.id.btnImportPdf);
		if (btnImportPdf != null) {
			PressAnimations.applySoft(btnImportPdf);
			btnImportPdf.setOnClickListener(v -> TransactionsPdf.openPdfPicker(activity));
		}

		Button btnImportImage = root.findViewById(R.id.btnImportImage);
		if (btnImportImage != null) {
			PressAnimations.applySoft(btnImportImage);
			btnImportImage.setOnClickListener(v ->
					com.couplefinance.ocr.TransactionOcrImporter.openImagePicker(activity));
		}

		Button btnExportCsv = root.findViewById(R.id.btnExportCsv);
		if (btnExportCsv != null) {
			PressAnimations.applySoft(btnExportCsv);
			btnExportCsv.setOnClickListener(v -> exportCsv());
		}

		// Bouton "Filtres" : replie / déplie la zone des filtres avancés
		// (Personnes + Catégories). Repliée par défaut via android:visibility.
		final Button btnToggleFilters = root.findViewById(R.id.btnToggleFilters);
		final View filtersExtra = root.findViewById(R.id.filtersExtra);
		if (btnToggleFilters != null && filtersExtra != null) {
			PressAnimations.applySoft(btnToggleFilters);
			btnToggleFilters.setOnClickListener(v -> {
				boolean willShow = filtersExtra.getVisibility() != View.VISIBLE;
				filtersExtra.setVisibility(willShow ? View.VISIBLE : View.GONE);
				btnToggleFilters.setText(willShow ? "⚙ Filtres ▴" : "⚙ Filtres");
			});
		}
	}

	private void exportCsv() {
		List<TransactionsModels.Transaction> source = filtered.isEmpty() ? allTransactions : filtered;
		if (source.isEmpty()) {
			com.couplefinance.AppToast.info(activity, "Aucune transaction à exporter");
			return;
		}

		try {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE);
			File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
			if (dir == null) dir = activity.getFilesDir();
			String stamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.FRANCE).format(new java.util.Date());
			File file = new File(dir, "transactions_" + stamp + ".csv");

			FileWriter fw = new FileWriter(file);
			fw.write("Date,Libellé,Montant,Type,Catégorie,Personne,Compte\n");
			for (TransactionsModels.Transaction t : source) {
				String date = sdf.format(new java.util.Date(t.dateMs));
				String label = csvEscape(t.label);
				String amount = String.format(Locale.FRANCE, "%.2f", t.amount);
				String type = "income".equals(t.type) ? "Revenu" : "Dépense";
				String cat = csvEscape(t.category != null ? t.category : "");
				String person = csvEscape(t.person != null ? t.person : "");
				String compte = csvEscape(t.compte != null ? t.compte : "");
				fw.write(date + "," + label + "," + amount + "," + type + "," + cat + "," + person + "," + compte + "\n");
			}
			fw.close();

			Uri uri = FileProvider.getUriForFile(activity,
					activity.getPackageName() + ".provider", file);

			Intent share = new Intent(Intent.ACTION_SEND);
			share.setType("text/csv");
			share.putExtra(Intent.EXTRA_STREAM, uri);
			share.putExtra(Intent.EXTRA_SUBJECT, "Transactions CoupleFinance");
			share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			activity.startActivity(Intent.createChooser(share, "Partager le CSV"));

		} catch (Exception e) {
			com.couplefinance.AppToast.error(activity, "Erreur export : " + e.getMessage());
		}
	}

	private static String csvEscape(String s) {
		if (s == null) return "";
		if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
			return "\"" + s.replace("\"", "\"\"") + "\"";
		}
		return s;
	}

	private void styleAddButton(Button btn) {
		if (btn == null)
			return;

		btn.setText("+");
		btn.setAllCaps(false);
		btn.setTextSize(23);
		btn.setTypeface(null, Typeface.BOLD);
		btn.setTextColor(ThemeColors.buttonTextOnPrimary());
		btn.setGravity(Gravity.CENTER);
		btn.setPadding(0, 0, 0, dp(2));
		btn.setMinWidth(0);
		btn.setMinHeight(0);
		btn.setStateListAnimator(null);
		btn.setBackground(circle(ThemeColors.primary()));
		btn.setElevation(dp(2));

		ViewGroup.LayoutParams lp = btn.getLayoutParams();
		if (lp != null) {
			lp.width = dp(48);
			lp.height = dp(48);
			btn.setLayoutParams(lp);
		}

		PressAnimations.applySoft(btn);
	}

	private void styleStatCards() {
		styleStatParent(tvStatRevenues);
		styleStatParent(tvStatExpenses);
		styleStatParent(tvStatNet);
		styleStatParent(tvStatCount);
	}

	private void styleStatParent(TextView tv) {
		if (tv == null)
			return;

		View parent = (View) tv.getParent();
		if (parent != null) {
			parent.setBackground(rounded(ThemeColors.card(), dp(23),
					ThemeColors.withAlpha(ThemeColors.border(), 65), 1));
			parent.setElevation(dp(0));
		}
	}

	private void injectMonthNavigation(View anchorView) {
		if (anchorView == null)
			return;

		ViewGroup parent = (ViewGroup) anchorView.getParent();
		if (parent == null)
			return;

		LinearLayout navBar = new LinearLayout(activity);
		navBar.setOrientation(LinearLayout.HORIZONTAL);
		navBar.setGravity(Gravity.CENTER_VERTICAL);
		navBar.setPadding(0, dp(5), 0, 0);
		navBar.setVisibility(View.GONE);

		btnMonthPrev = new TextView(activity);
		btnMonthPrev.setText("‹");
		btnMonthPrev.setTextSize(18f);
		btnMonthPrev.setTypeface(null, Typeface.BOLD);
		btnMonthPrev.setGravity(Gravity.CENTER);
		btnMonthPrev.setTextColor(ThemeColors.primary());
		btnMonthPrev.setBackground(buildNavBtnBg());
		btnMonthPrev.setOnClickListener(v -> navigateToPreviousMonth());

		tvMonthDisplay = new TextView(activity);
		tvMonthDisplay.setText(TransactionsFilter.monthLabel(filterState.period));
		tvMonthDisplay.setTextColor(ThemeColors.text());
		tvMonthDisplay.setTextSize(11);
		tvMonthDisplay.setTypeface(null, Typeface.BOLD);
		tvMonthDisplay.setGravity(Gravity.CENTER);
		tvMonthDisplay.setSingleLine(true);

		btnMonthNext = new TextView(activity);
		btnMonthNext.setText("›");
		btnMonthNext.setTextSize(18f);
		btnMonthNext.setTypeface(null, Typeface.BOLD);
		btnMonthNext.setGravity(Gravity.CENTER);
		btnMonthNext.setTextColor(ThemeColors.primary());
		btnMonthNext.setBackground(buildNavBtnBg());
		btnMonthNext.setOnClickListener(v -> navigateToNextMonth());

		navBar.addView(btnMonthPrev, new LinearLayout.LayoutParams(dp(34), dp(34)));
		navBar.addView(tvMonthDisplay, new LinearLayout.LayoutParams(0, -2, 1f));
		navBar.addView(btnMonthNext, new LinearLayout.LayoutParams(dp(34), dp(34)));

		int insertIdx = parent.getChildCount();
		if (btnFilterAllTime != null) {
			insertIdx = parent.indexOfChild(btnFilterAllTime) + 1;
		}

		LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(-1, -2);
		navLp.topMargin = dp(5);
		parent.addView(navBar, insertIdx, navLp);

		updateMonthNavDisplay();
	}

	private GradientDrawable buildNavBtnBg() {
		return rounded(ThemeColors.primarySoft(), dp(17), ThemeColors.withAlpha(ThemeColors.primary(), 15), 1);
	}

	private void navigateToPreviousMonth() {
		if ("current".equals(filterState.period)) {
			setPeriodFilter("last");
			return;
		}

		if ("last".equals(filterState.period)) {
			String prev = TransactionsFilter.previousPeriod(availableMonths, "last");
			if (prev != null)
				setArbitraryMonthFilter(prev);
			return;
		}

		String prev = TransactionsFilter.previousPeriod(availableMonths, filterState.period);
		if (prev != null)
			setArbitraryMonthFilter(prev);
	}

	private void navigateToNextMonth() {
		if ("all".equals(filterState.period))
			return;

		if (filterState.period != null && filterState.period.startsWith("month:")) {
			String next = TransactionsFilter.nextPeriod(availableMonths, filterState.period);

			if (next != null) {
				java.util.Calendar now = java.util.Calendar.getInstance();
				int[] parsed = TransactionsFilter.parseMonthPeriod(next);

				if (parsed[0] == now.get(java.util.Calendar.MONTH)
						&& parsed[1] == now.get(java.util.Calendar.YEAR)) {
					setPeriodFilter("current");
				} else {
					java.util.Calendar prev = (java.util.Calendar) now.clone();
					prev.add(java.util.Calendar.MONTH, -1);

					if (parsed[0] == prev.get(java.util.Calendar.MONTH)
							&& parsed[1] == prev.get(java.util.Calendar.YEAR)) {
						setPeriodFilter("last");
					} else {
						setArbitraryMonthFilter(next);
					}
				}
			} else {
				setPeriodFilter("last");
			}
			return;
		}

		if ("last".equals(filterState.period)) {
			setPeriodFilter("current");
		}
	}

	private void setArbitraryMonthFilter(String monthPeriod) {
		filterState.period = monthPeriod;

		styleFilterBtn(btnFilterCurrent, false);
		styleFilterBtn(btnFilterLast, false);
		styleFilterBtn(btnFilterAllTime, false);

		updateMonthNavDisplay();
		applyFilters();
	}

	private void updateMonthNavDisplay() {
		if (tvMonthDisplay == null)
			return;

		tvMonthDisplay.setText(TransactionsFilter.monthLabel(filterState.period));

		if (btnMonthPrev != null) {
			boolean canGoPrev = canNavigatePrev();
			btnMonthPrev.setAlpha(canGoPrev ? 1f : 0.3f);
			btnMonthPrev.setEnabled(canGoPrev);
		}

		if (btnMonthNext != null) {
			boolean canGoNext = !"current".equals(filterState.period) && !"all".equals(filterState.period);
			btnMonthNext.setAlpha(canGoNext ? 1f : 0.3f);
			btnMonthNext.setEnabled(canGoNext);
		}
	}

	private boolean canNavigatePrev() {
		if ("all".equals(filterState.period))
			return false;
		if ("current".equals(filterState.period))
			return true;

		if ("last".equals(filterState.period)) {
			return TransactionsFilter.previousPeriod(availableMonths, "last") != null;
		}

		return TransactionsFilter.previousPeriod(availableMonths, filterState.period) != null;
	}

	private void load() {
		TransactionsRepository.loadAll(activity, new TransactionsRepository.OnDataLoaded() {
			public void onLoaded(List<TransactionsModels.Transaction> transactions, List<String> loadedMembers,
					List<String[]> loadedCategories) {

				allTransactions = transactions == null ? new ArrayList<>() : transactions;
				categories = loadedCategories == null ? new ArrayList<>() : loadedCategories;

				members = buildMembersForFilters(loadedMembers, allTransactions);

				availableMonths = TransactionsFilter.availableMonths(allTransactions);

				applyFilters();
				setupPersonPillButtons(llPersonButtons);
				rebuildCategoryPills();
				updateMonthNavDisplay();
			}

			public void onError(String msg) {
				allTransactions = new ArrayList<>();
				members = new ArrayList<>();
				applyFilters();
				setupPersonPillButtons(llPersonButtons);
			}
		});
	}

	private List<String> buildMembersForFilters(List<String> loadedMembers,
			List<TransactionsModels.Transaction> transactions) {

		List<String> out = new ArrayList<>();

		if (loadedMembers != null) {
			for (String m : loadedMembers) {
				addMemberFilter(out, m);
			}
		}

		if (transactions != null) {
			for (TransactionsModels.Transaction tx : transactions) {
				if (tx == null)
					continue;

				addMemberFilter(out, extractPersonFromLabel(tx.label));

				if (!isJointTransaction(tx)) {
					addMemberFilter(out, tx.person);
				}
			}
		}

		return out;
	}

	private void addMemberFilter(List<String> out, String value) {
		if (out == null || value == null)
			return;

		String clean = value.trim();

		if (clean.isEmpty())
			return;

		if ("Moi".equalsIgnoreCase(clean) || "null".equalsIgnoreCase(clean))
			return;

		if (isJointName(clean))
			return;

		for (String existing : out) {
			if (existing != null && existing.equalsIgnoreCase(clean))
				return;
		}

		out.add(clean);
	}

	private String extractPersonFromLabel(String label) {
		if (label == null)
			return "";

		if (label.contains(" · ")) {
			String first = label.split(" · ")[0].trim();
			if (!first.isEmpty() && !isJointName(first))
				return first;
		}

		return "";
	}

	private void reloadAfterWrite() {
		load();
	}

	private void applyFilters() {
		filtered = computeFilteredSnapshot(filterState.period);

		if (adapter != null) {
			adapter.update(filtered, members);
		}

		refreshListHeight();
		updateStats();
		updatePeriodBadges();

		if (tvListTitle != null) {
			tvListTitle.setText(buildListTitle());
			tvListTitle.setTextColor(ThemeColors.text());
			tvListTitle.setTypeface(null, Typeface.BOLD);
			tvListTitle.setTextSize(20);
		}

		updateMonthNavDisplay();
	}

	private List<TransactionsModels.Transaction> computeFilteredSnapshot(String periodOverride) {
		String originalPerson = filterState.person;
		String originalPeriod = filterState.period;

		if (periodOverride != null) {
			filterState.period = periodOverride;
		}

		filterState.person = PERSON_ALL;

		List<TransactionsModels.Transaction> base = TransactionsFilter.apply(allTransactions, filterState);

		filterState.person = originalPerson;
		filterState.period = originalPeriod;

		return applyPersonFilterLocally(base, originalPerson);
	}

	private List<TransactionsModels.Transaction> applyPersonFilterLocally(List<TransactionsModels.Transaction> base,
			String selectedPerson) {

		if (base == null)
			return new ArrayList<>();

		if (selectedPerson == null || selectedPerson.trim().isEmpty() || PERSON_ALL.equals(selectedPerson)) {
			return new ArrayList<>(base);
		}

		List<TransactionsModels.Transaction> out = new ArrayList<>();

		for (TransactionsModels.Transaction tx : base) {
			if (tx == null)
				continue;

			if (PERSON_JOINT.equals(selectedPerson)) {
				if (isJointTransaction(tx))
					out.add(tx);
			} else {
				if (!isJointTransaction(tx) && txBelongsToPerson(tx, selectedPerson))
					out.add(tx);
			}
		}

		return out;
	}

	private String buildListTitle() {
		String title = TransactionsFilter.listTitle(filterState.period);

		String person = filterState.person;

		if (person == null || person.trim().isEmpty() || PERSON_ALL.equals(person))
			return title;

		if (PERSON_JOINT.equals(person))
			return title + " · Compte joint";

		return title + " · " + person;
	}

	private void refreshListHeight() {
		if (listTransactions == null || adapter == null)
			return;

		listTransactions.post(() -> {
			int totalHeight = 0;
			int count = adapter.getCount();

			if (count <= 0) {
				ViewGroup.LayoutParams emptyParams = listTransactions.getLayoutParams();
				emptyParams.height = dp(80);
				listTransactions.setLayoutParams(emptyParams);
				return;
			}

			int width = listTransactions.getWidth();
			if (width <= 0) {
				width = activity.getResources().getDisplayMetrics().widthPixels - dp(40);
			}

			int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);

			for (int i = 0; i < count; i++) {
				View item = adapter.getView(i, null, listTransactions);

				if (item == null)
					continue;

				item.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
				totalHeight += item.getMeasuredHeight();
			}

			totalHeight += listTransactions.getDividerHeight() * Math.max(0, count - 1);
			totalHeight += listTransactions.getPaddingTop() + listTransactions.getPaddingBottom();
			totalHeight += dp(120);

			ViewGroup.LayoutParams params = listTransactions.getLayoutParams();
			params.height = totalHeight;
			listTransactions.setLayoutParams(params);
			listTransactions.requestLayout();
		});
	}

	private void setTypeFilter(String type) {
		filterState.type = type;

		styleFilterBtn(btnFilterAll, "all".equals(type));
		styleFilterBtn(btnFilterIncome, "income".equals(type));
		styleFilterBtn(btnFilterExpenses, "expenses".equals(type));

		applyFilters();
	}

	private void setPeriodFilter(String period) {
		filterState.period = period;

		styleFilterBtn(btnFilterCurrent, "current".equals(period));
		styleFilterBtn(btnFilterLast, "last".equals(period));
		styleFilterBtn(btnFilterAllTime, "all".equals(period));

		updateMonthNavDisplay();
		applyFilters();
	}

	private void updateStats() {
		TransactionsModels.Stats stats = TransactionsFilter.computeStats(filtered);

		if (tvStatRevenues != null) {
			tvStatRevenues.setText(Fmt.money(stats.revenues));
			tvStatRevenues.setTextColor(ThemeColors.success());
			tvStatRevenues.setTypeface(null, Typeface.BOLD);
		}

		if (tvStatExpenses != null) {
			tvStatExpenses.setText("-" + Fmt.money(stats.expenses));
			tvStatExpenses.setTextColor(ThemeColors.danger());
			tvStatExpenses.setTypeface(null, Typeface.BOLD);
		}

		if (tvStatNet != null) {
			tvStatNet.setText(Fmt.moneySigned(stats.net));
			tvStatNet.setTextColor(stats.net >= 0 ? ThemeColors.success() : ThemeColors.danger());
			tvStatNet.setTypeface(null, Typeface.BOLD);
		}

		if (tvStatCount != null) {
			tvStatCount.setText(String.valueOf(stats.count));
			tvStatCount.setTextColor(ThemeColors.text());
			tvStatCount.setTypeface(null, Typeface.BOLD);
		}
	}

	private void setupPersonPillButtons(LinearLayout container) {
		LinearLayout pills = container != null ? container : llPersonButtons;

		if (pills == null) {
			View v = activity.getWindow().getDecorView();
			pills = v.findViewWithTag("llPersonButtons");
		}

		if (pills == null)
			return;

		final LinearLayout finalPills = pills;
		finalPills.removeAllViews();
		finalPills.setOrientation(LinearLayout.HORIZONTAL);

		List<PersonOption> options = new ArrayList<>();
		options.add(new PersonOption(PERSON_ALL, "Tous"));

		for (String member : members) {
			if (member == null || member.trim().isEmpty())
				continue;

			if (isJointName(member))
				continue;

			options.add(new PersonOption(member.trim(), member.trim()));
		}

		if (shouldShowJointChip()) {
			options.add(new PersonOption(PERSON_JOINT, getJointChipLabel()));
		}

		if (filterState.person == null || filterState.person.trim().isEmpty()) {
			filterState.person = PERSON_ALL;
		}

		for (PersonOption opt : options) {
			final String personKey = opt.key;
			boolean active = personKey.equals(filterState.person);

			TextView chip = PremiumChip.selectable(activity, opt.label, active);
			chip.setTag(personKey);
			stylePillText(chip, active);

			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(32));
			lp.rightMargin = dp(7);
			chip.setLayoutParams(lp);

			chip.setOnClickListener(v -> {
				filterState.person = personKey;

				for (int i = 0; i < finalPills.getChildCount(); i++) {
					View c = finalPills.getChildAt(i);

					if (c instanceof TextView) {
						String btnPerson = c.getTag() != null ? c.getTag().toString() : PERSON_ALL;
						stylePillText((TextView) c, btnPerson.equals(personKey));
					}
				}

				applyFilters();
			});

			finalPills.addView(chip);
		}
	}

	private boolean shouldShowJointChip() {
		try {
			if (JointAccountManager.getInstance().isEnabledLocal())
				return true;
		} catch (Exception ignored) {
		}

		if (allTransactions != null) {
			for (TransactionsModels.Transaction tx : allTransactions) {
				if (isJointTransaction(tx))
					return true;
			}
		}

		return false;
	}

	private String getJointChipLabel() {
		try {
			String n = JointAccountManager.getInstance().getNameLocal();
			if (n != null && !n.trim().isEmpty())
				return n.trim();
		} catch (Exception ignored) {
		}

		return "Compte joint";
	}

	public void onPdfActivityResult(int requestCode, int resultCode, Intent data) {
		TransactionsPdf.handleActivityResult(activity, requestCode, resultCode, data, allTransactions, categories,
				new TransactionsPdf.OnImportComplete() {
					public void onSuccess(int count) {
						reloadAfterWrite();
					}

					public void onError(String e) {
					}
				});

		// Import OCR d'image (screenshot bancaire / ticket de caisse).
		com.couplefinance.ocr.TransactionOcrImporter.handleActivityResult(
				activity, requestCode, resultCode, data, allTransactions, categories,
				new com.couplefinance.ocr.TransactionOcrImporter.OnImportComplete() {
					public void onSuccess(int count) {
						reloadAfterWrite();
					}

					public void onError(String e) {
					}
				});
	}

	private void styleFilterBtn(Button btn, boolean active) {
		if (btn == null)
			return;

		btn.setAllCaps(false);
		btn.setTextSize(11);
		btn.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
		btn.setStateListAnimator(null);
		btn.setPadding(dp(11), 0, dp(11), 0);
		btn.setMinHeight(0);
		btn.setMinWidth(0);
		btn.setTextColor(active ? ThemeColors.buttonTextOnPrimary() : ThemeColors.subtext());

		btn.setBackground(active ? rounded(ThemeColors.primary(), dp(17), ThemeColors.primary(), 0)
				: rounded(ThemeColors.card(), dp(17), ThemeColors.withAlpha(ThemeColors.border(), 70), 1));

		btn.setElevation(0);
		PressAnimations.applySoft(btn);
	}

	private void stylePillText(TextView tv, boolean active) {
		if (tv == null)
			return;

		tv.setTextSize(10);
		tv.setGravity(Gravity.CENTER);
		tv.setSingleLine(true);
		tv.setPadding(dp(11), 0, dp(11), 0);
		tv.setTextColor(active ? ThemeColors.buttonTextOnPrimary() : ThemeColors.subtext());
		tv.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);

		tv.setBackground(active ? rounded(ThemeColors.primary(), dp(16), ThemeColors.primary(), 0)
				: rounded(ThemeColors.card(), dp(16), ThemeColors.withAlpha(ThemeColors.border(), 70), 1));

		tv.setElevation(0);
	}

	private void updatePeriodBadges() {
		updatePeriodBtn(btnFilterCurrent, "current", "Ce mois");
		updatePeriodBtn(btnFilterLast, "last", "Mois préc.");
		updatePeriodBtn(btnFilterAllTime, "all", "Historique");
	}

	private void updatePeriodBtn(Button btn, String period, String label) {
		if (btn == null)
			return;

		int count = computeCountForPeriod(period);
		btn.setText(label + "  " + count);
	}

	private int computeCountForPeriod(String period) {
		List<TransactionsModels.Transaction> snapshot = computeFilteredSnapshot(period);
		return snapshot == null ? 0 : snapshot.size();
	}

	private LinearLayout injectCategorySection(View root, View anchorPersonSection) {
		if (anchorPersonSection == null)
			return null;

		View personScroll = root.findViewById(R.id.personScrollTransactions);
		if (personScroll == null)
			return null;

		ViewGroup parent = (ViewGroup) personScroll.getParent();
		if (parent == null)
			return null;

		TextView tvLabel = new TextView(activity);
		tvLabel.setText("Catégories");
		tvLabel.setTextColor(ThemeColors.text());
		tvLabel.setTextSize(14);
		tvLabel.setTypeface(null, Typeface.BOLD);

		LinearLayout.LayoutParams lpLabel = new LinearLayout.LayoutParams(-1, -2);
		lpLabel.topMargin = dp(10);
		lpLabel.bottomMargin = dp(6);

		HorizontalScrollView scroll = new HorizontalScrollView(activity);
		scroll.setHorizontalScrollBarEnabled(false);
		scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

		LinearLayout llCat = new LinearLayout(activity);
		llCat.setOrientation(LinearLayout.HORIZONTAL);
		llCat.setTag("llCategoryButtons");

		scroll.addView(llCat, new HorizontalScrollView.LayoutParams(-2, -2));

		LinearLayout.LayoutParams lpScroll = new LinearLayout.LayoutParams(-1, -2);

		int index = parent.indexOfChild(personScroll);
		parent.addView(tvLabel, index + 1, lpLabel);
		parent.addView(scroll, index + 2, lpScroll);

		return llCat;
	}

	private void rebuildCategoryPills() {
		if (llCategoryButtons == null) {
			View v = activity.getWindow().getDecorView();
			llCategoryButtons = v.findViewWithTag("llCategoryButtons");
		}

		if (llCategoryButtons == null)
			return;

		setupCategoryPillButtons(llCategoryButtons);
	}

	private void setupCategoryPillButtons(LinearLayout container) {
		container.removeAllViews();
		container.setOrientation(LinearLayout.HORIZONTAL);

		List<String> cats = TransactionsFilter.availableCategories(allTransactions);

		List<String> options = new ArrayList<>();
		options.add("Toutes");
		options.addAll(cats);

		for (String opt : options) {
			final String catKey = "Toutes".equals(opt) ? "all" : opt;
			boolean active = filterState.category.equals(catKey);

			TextView chip = PremiumChip.selectable(activity, opt, active);
			chip.setTag(catKey);
			stylePillText(chip, active);

			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(32));
			lp.rightMargin = dp(7);
			chip.setLayoutParams(lp);

			chip.setOnClickListener(v -> {
				filterState.category = catKey;

				for (int i = 0; i < container.getChildCount(); i++) {
					View c = container.getChildAt(i);

					if (c instanceof TextView) {
						String k = c.getTag() != null ? c.getTag().toString() : "all";
						stylePillText((TextView) c, k.equals(catKey));
					}
				}

				applyFilters();
			});

			container.addView(chip);
		}
	}

	private boolean isJointTransaction(TransactionsModels.Transaction tx) {
		if (tx == null)
			return false;

		String compte = getStringField(tx, "compte");

		if (compte.isEmpty())
			compte = getStringField(tx, "account");

		if (compte.isEmpty())
			compte = getStringField(tx, "sourceAccount");

		if ("joint".equalsIgnoreCase(compte))
			return true;

		String person = getStringField(tx, "person");
		if (isJointName(person))
			return true;

		String label = getStringField(tx, "label");
		return normalize(label).contains("compte joint");
	}

	private boolean txBelongsToPerson(TransactionsModels.Transaction tx, String selectedPerson) {
		if (tx == null || selectedPerson == null)
			return false;

		String target = normalize(selectedPerson);

		String person = getStringField(tx, "person");
		if (!person.isEmpty() && normalize(person).equals(target))
			return true;

		String member = getStringField(tx, "member");
		if (!member.isEmpty() && normalize(member).equals(target))
			return true;

		String owner = getStringField(tx, "owner");
		if (!owner.isEmpty() && normalize(owner).equals(target))
			return true;

		String userName = getStringField(tx, "userName");
		if (!userName.isEmpty() && normalize(userName).equals(target))
			return true;

		String label = getStringField(tx, "label");
		if (label.contains(" · ")) {
			String first = label.split(" · ")[0].trim();
			return normalize(first).equals(target);
		}

		return false;
	}

	private String getStringField(Object object, String fieldName) {
		if (object == null || fieldName == null)
			return "";

		try {
			Field f = object.getClass().getDeclaredField(fieldName);
			f.setAccessible(true);

			Object value = f.get(object);
			return value == null ? "" : String.valueOf(value).trim();

		} catch (Exception ignored) {
			return "";
		}
	}

	private boolean isJointName(String name) {
		if (name == null)
			return false;

		String n = normalize(name);

		return n.equals("compte joint") || n.equals("joint") || n.equals("compte commun");
	}

	private String normalize(String value) {
		if (value == null)
			return "";

		return value.trim().toLowerCase(Locale.FRANCE).replace("é", "e").replace("è", "e").replace("ê", "e")
				.replace("ë", "e").replace("à", "a").replace("â", "a").replace("ä", "a").replace("î", "i")
				.replace("ï", "i").replace("ô", "o").replace("ö", "o").replace("ù", "u").replace("û", "u")
				.replace("ü", "u").replace("ç", "c");
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

	private GradientDrawable circle(int color) {
		GradientDrawable d = new GradientDrawable();
		d.setShape(GradientDrawable.OVAL);
		d.setColor(color);
		return d;
	}

	private void refreshDashboard() {
		if (activity instanceof DashboardActivity) {
			((DashboardActivity) activity).refreshCurrentView();
		}
	}

	private static final class PersonOption {
		final String key;
		final String label;

		PersonOption(String key, String label) {
			this.key = key;
			this.label = label;
		}
	}
}