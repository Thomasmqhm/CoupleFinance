package com.couplefinance.ocr;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.AppDialog;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.Fmt;
import com.couplefinance.core.ui.UiFactory;
import com.couplefinance.data.MerchantRuleManager;
import com.couplefinance.utils.ParsedTransaction;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public final class OcrTransactionPreviewDialog {

	private static final String CREATE_CAT_ACTION = "+ Créer une nouvelle catégorie";

	public interface OnConfirm {
		void onConfirm(List<ParsedTransaction> confirmed);
	}

	public interface CategoryCreator {
		interface Result {
			void onCreated(String categoryName);
		}

		void createCategory(Activity activity, Result result);
	}

	private interface CategoryChosen {
		void onChosen(String category);
	}

	private interface DatePicked {
		void onPicked(long dateMs);
	}

	private OcrTransactionPreviewDialog() {
	}

	public static void show(Activity activity,
			String modeLabel,
			String targetLabel,
			List<ParsedTransaction> transactions,
			List<String> categoryNames,
			CategoryCreator categoryCreator,
			OnConfirm onConfirm) {

		if (activity == null || transactions == null || transactions.isEmpty()) {
			return;
		}

		MerchantRuleManager.getInstance().init(activity);

		final ArrayList<String> catNames = sanitizeCategories(categoryNames, transactions);
		applyKnownRulesAndAdaptCategories(transactions, catNames);

		int total = transactions.size();
		int dupCnt = countDuplicates(transactions);
		int newCnt = total - dupCnt;

		LinearLayout content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.VERTICAL);

		TextView targetInfo = UiFactory.bodyMuted(activity,
				"Mode : " + safe(modeLabel) + "  ·  Attribué à : " + safe(targetLabel));
		targetInfo.setTextSize(DS.TEXT_XS);
		targetInfo.setTypeface(null, Typeface.BOLD);
		targetInfo.setTextColor(ThemeColors.primary());

		LinearLayout.LayoutParams tip = new LinearLayout.LayoutParams(-1, -2);
		tip.bottomMargin = DS.dp(activity, DS.GAP_SM);
		content.addView(targetInfo, tip);

		TextView helper = UiFactory.bodyMuted(activity,
				"Vérifie chaque ligne. Les corrections de libellé et catégorie seront mémorisées.");
		helper.setTextSize(DS.TEXT_XS);

		LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
		hp.bottomMargin = DS.dp(activity, DS.GAP);
		content.addView(helper, hp);

		LinearLayout statsRow = new LinearLayout(activity);
		statsRow.setOrientation(LinearLayout.HORIZONTAL);
		LinearLayout.LayoutParams srp = new LinearLayout.LayoutParams(-1, -2);
		srp.bottomMargin = DS.dp(activity, DS.GAP);
		content.addView(statsRow, srp);

		statsRow.addView(statBox(activity, String.valueOf(total), "Détectées", ThemeColors.text()));
		statsRow.addView(statBox(activity, String.valueOf(newCnt), "Nouvelles", ThemeColors.success()));
		statsRow.addView(statBox(activity, String.valueOf(dupCnt), "Doublons", ThemeColors.subtext()));

		ScrollView sv = new ScrollView(activity);
		sv.setFillViewport(false);
		sv.setLayoutParams(new LinearLayout.LayoutParams(-1, DS.dp(activity, 440)));

		LinearLayout list = new LinearLayout(activity);
		list.setOrientation(LinearLayout.VERTICAL);
		sv.addView(list);

		final int count = transactions.size();
		final EditText[] labelInputs = new EditText[count];
		final EditText[] amountInputs = new EditText[count];
		final TextView[] dateButtons = new TextView[count];
		final TextView[] categoryButtons = new TextView[count];
		final String[] selectedCategories = new String[count];
		final long[] selectedDates = new long[count];
		final CheckBox[] checkBoxes = new CheckBox[count];

		for (int i = 0; i < count; i++) {
			final int index = i;
			final ParsedTransaction tx = transactions.get(i);
			final boolean isDup = tx.duplicate;
			final boolean isIncome = "income".equals(tx.type);

			selectedDates[i] = tx.dateMs > 0 ? tx.dateMs : System.currentTimeMillis();
			selectedCategories[i] = adaptCategoryToExisting(
					safeCategory(tx.category, isIncome), catNames, isIncome);
			tx.category = selectedCategories[i];

			LinearLayout card = new LinearLayout(activity);
			card.setOrientation(LinearLayout.VERTICAL);
			card.setPadding(
					DS.dp(activity, DS.PAD),
					DS.dp(activity, DS.PAD_SM),
					DS.dp(activity, DS.PAD),
					DS.dp(activity, DS.PAD_SM));
			card.setBackground(UiFactory.bgBordered(
					isDup ? ThemeColors.backgroundSecondary() : ThemeColors.card(),
					isDup ? ThemeColors.divider() : ThemeColors.border(),
					DS.R_MD,
					activity));

			LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
			cp.bottomMargin = DS.dp(activity, DS.GAP_SM);
			list.addView(card, cp);

			LinearLayout top = new LinearLayout(activity);
			top.setOrientation(LinearLayout.HORIZONTAL);
			top.setGravity(Gravity.CENTER_VERTICAL);

			CheckBox cb = new CheckBox(activity);
			cb.setChecked(!isDup);
			tintCheckbox(cb);
			checkBoxes[i] = cb;
			top.addView(cb, new LinearLayout.LayoutParams(-2, -2));

			LinearLayout titleBox = new LinearLayout(activity);
			titleBox.setOrientation(LinearLayout.VERTICAL);
			titleBox.setPadding(DS.dp(activity, 4), 0, DS.dp(activity, 8), 0);
			top.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1f));

			TextView labelTitle = UiFactory.bodyMuted(activity, "Libellé");
			labelTitle.setTextSize(10f);
			labelTitle.setTypeface(null, Typeface.BOLD);
			titleBox.addView(labelTitle);

			EditText etLabel = new EditText(activity);
			etLabel.setText(tx.label == null ? "" : tx.label);
			etLabel.setTextSize(DS.TEXT_SM);
			etLabel.setTextColor(isDup ? ThemeColors.subtext() : ThemeColors.text());
			etLabel.setHint("Nom visible dans Transactions");
			etLabel.setHintTextColor(ThemeColors.subtext());
			etLabel.setSingleLine(true);
			etLabel.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
			etLabel.setBackgroundColor(Color.TRANSPARENT);
			etLabel.setPadding(0, 0, 0, DS.dp(activity, 2));
			labelInputs[i] = etLabel;
			titleBox.addView(etLabel, new LinearLayout.LayoutParams(-1, -2));

			etLabel.setOnFocusChangeListener((v, hasFocus) -> {
				if (!hasFocus) {
					String newLabel = etLabel.getText().toString().trim();
					if (!newLabel.isEmpty()) {
						rememberLabelAndApplyToSameMerchant(transactions, labelInputs, index, newLabel);
					}
				}
			});

			etLabel.addTextChangedListener(new TextWatcher() {
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
				public void onTextChanged(CharSequence s, int start, int before, int count) {}

				public void afterTextChanged(Editable s) {
					if (s != null && s.toString().trim().length() > 0) {
						tx.label = s.toString().trim();
					}
				}
			});

			if (isDup) {
				TextView dupBadge = new TextView(activity);
				dupBadge.setText("Doublon probable");
				dupBadge.setTextSize(9.5f);
				dupBadge.setTypeface(null, Typeface.BOLD);
				dupBadge.setTextColor(ThemeColors.subtext());
				dupBadge.setGravity(Gravity.CENTER);
				dupBadge.setPadding(
						DS.dp(activity, 8),
						DS.dp(activity, 4),
						DS.dp(activity, 8),
						DS.dp(activity, 4));
				dupBadge.setBackground(UiFactory.bgBordered(
						ThemeColors.backgroundSecondary(),
						ThemeColors.divider(),
						DS.R_PILL,
						activity));
				top.addView(dupBadge);
			}

			card.addView(top);

			LinearLayout amountRow = new LinearLayout(activity);
			amountRow.setOrientation(LinearLayout.HORIZONTAL);
			amountRow.setGravity(Gravity.CENTER_VERTICAL);

			LinearLayout.LayoutParams arp = new LinearLayout.LayoutParams(-1, -2);
			arp.topMargin = DS.dp(activity, DS.GAP_SM);
			card.addView(amountRow, arp);

			EditText etAmount = new EditText(activity);
			etAmount.setText(Fmt.moneyInput(tx.amount));
			etAmount.setTextSize(DS.TEXT_SM);
			etAmount.setTextColor(ThemeColors.text());
			etAmount.setSingleLine(true);
			etAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
			etAmount.setPadding(
					DS.dp(activity, DS.PAD_INPUT),
					DS.dp(activity, 9),
					DS.dp(activity, DS.PAD_INPUT),
					DS.dp(activity, 9));
			etAmount.setBackground(UiFactory.bgBordered(
					ThemeColors.backgroundSecondary(),
					ThemeColors.border(),
					DS.R_MD,
					activity));
			amountInputs[i] = etAmount;

			LinearLayout.LayoutParams amLp = new LinearLayout.LayoutParams(0, -2, 1f);
			amLp.rightMargin = DS.dp(activity, DS.GAP_SM);
			amountRow.addView(etAmount, amLp);

			TextView typeBadge = new TextView(activity);
			typeBadge.setText(isIncome ? "Revenu" : "Dépense");
			typeBadge.setTextSize(11f);
			typeBadge.setTypeface(null, Typeface.BOLD);
			typeBadge.setTextColor(isIncome ? ThemeColors.success() : ThemeColors.muted());
			typeBadge.setGravity(Gravity.CENTER);
			typeBadge.setPadding(
					DS.dp(activity, 12),
					DS.dp(activity, 8),
					DS.dp(activity, 12),
					DS.dp(activity, 8));
			amountRow.addView(typeBadge);

			LinearLayout metaRow = new LinearLayout(activity);
			metaRow.setOrientation(LinearLayout.HORIZONTAL);
			metaRow.setGravity(Gravity.CENTER_VERTICAL);

			LinearLayout.LayoutParams mrp = new LinearLayout.LayoutParams(-1, -2);
			mrp.topMargin = DS.dp(activity, DS.GAP_SM);
			card.addView(metaRow, mrp);

			TextView dateButton = new TextView(activity);
			dateButton.setText("📅  " + formatDate(selectedDates[index]));
			dateButton.setTextSize(DS.TEXT_SM);
			dateButton.setTextColor(ThemeColors.text());
			dateButton.setGravity(Gravity.CENTER_VERTICAL);
			dateButton.setSingleLine(true);
			dateButton.setPadding(
					DS.dp(activity, DS.PAD_INPUT),
					DS.dp(activity, 9),
					DS.dp(activity, DS.PAD_INPUT),
					DS.dp(activity, 9));
			dateButton.setBackground(UiFactory.bgBordered(
					ThemeColors.backgroundSecondary(),
					ThemeColors.border(),
					DS.R_PILL,
					activity));
			dateButtons[i] = dateButton;

			dateButton.setOnClickListener(v -> showDatePicker(activity, selectedDates[index], picked -> {
				selectedDates[index] = picked;
				dateButtons[index].setText("📅  " + formatDate(picked));
			}));

			LinearLayout.LayoutParams dbLp = new LinearLayout.LayoutParams(0, -2, 1f);
			dbLp.rightMargin = DS.dp(activity, DS.GAP_SM);
			metaRow.addView(dateButton, dbLp);

			TextView catButton = new TextView(activity);
			catButton.setText(selectedCategories[index] + "  ▾");
			catButton.setTextSize(DS.TEXT_SM);
			catButton.setTextColor(ThemeColors.text());
			catButton.setGravity(Gravity.CENTER_VERTICAL);
			catButton.setSingleLine(true);
			catButton.setPadding(
					DS.dp(activity, DS.PAD_INPUT),
					DS.dp(activity, 9),
					DS.dp(activity, DS.PAD_INPUT),
					DS.dp(activity, 9));
			catButton.setBackground(UiFactory.bgBordered(
					ThemeColors.backgroundSecondary(),
					ThemeColors.border(),
					DS.R_PILL,
					activity));
			categoryButtons[i] = catButton;

			catButton.setOnClickListener(v -> showCategoryPicker(activity, catNames,
					selectedCategories[index], categoryCreator, chosen -> {
						selectedCategories[index] = chosen;
						categoryButtons[index].setText(chosen + "  ▾");

						rememberCategoryAndApplyToSameMerchant(
								transactions,
								selectedCategories,
								categoryButtons,
								index,
								chosen);
					}));

			metaRow.addView(catButton, new LinearLayout.LayoutParams(0, -2, 1f));
		}

		content.addView(sv);

		new AppDialog.Builder(activity)
				.icon("📷")
				.title("Importer " + newCnt + " transaction" + (newCnt > 1 ? "s" : ""))
				.subtitle(dupCnt > 0
						? dupCnt + " doublon(s) probable(s) décoché(s). Tout reste modifiable."
						: "Vérifie les libellés, montants, dates et catégories avant import.")
				.content(content)
				.primaryBtn("IMPORTER", () -> {
					List<ParsedTransaction> confirmed = collect(
							activity,
							transactions,
							checkBoxes,
							labelInputs,
							amountInputs,
							selectedDates,
							selectedCategories);
					if (onConfirm != null) {
						onConfirm.onConfirm(confirmed);
					}
				})
				.show();
	}

	private static void applyKnownRulesAndAdaptCategories(List<ParsedTransaction> transactions,
			ArrayList<String> allowedCategories) {
		if (transactions == null) return;

		MerchantRuleManager rules = MerchantRuleManager.getInstance();

		for (ParsedTransaction tx : transactions) {
			if (tx == null) continue;

			if (tx.merchantKey == null || tx.merchantKey.trim().isEmpty()) {
				tx.merchantKey = rules.resolveMerchantKey(tx.label);
			}

			rules.applyKnownRule(tx, allowedCategories);

			boolean income = "income".equals(tx.type);
			tx.category = adaptCategoryToExisting(
					safeCategory(tx.category, income),
					allowedCategories,
					income);
		}
	}

	private static void rememberCategoryAndApplyToSameMerchant(List<ParsedTransaction> transactions,
			String[] selectedCategories,
			TextView[] categoryButtons,
			int sourceIndex,
			String chosenCategory) {
		if (transactions == null || sourceIndex < 0 || sourceIndex >= transactions.size()) return;

		ParsedTransaction source = transactions.get(sourceIndex);
		String sourceKey = MerchantRuleManager.getInstance().resolveMerchantKey(source);

		if (sourceKey.isEmpty()) return;

		MerchantRuleManager.getInstance().saveCategoryRule(sourceKey, chosenCategory);

		for (int i = 0; i < transactions.size(); i++) {
			ParsedTransaction tx = transactions.get(i);
			String key = MerchantRuleManager.getInstance().resolveMerchantKey(tx);

			if (sourceKey.equals(key)) {
				tx.category = chosenCategory;
				selectedCategories[i] = chosenCategory;
				if (categoryButtons[i] != null) {
					categoryButtons[i].setText(chosenCategory + "  ▾");
				}
			}
		}
	}

	private static void rememberLabelAndApplyToSameMerchant(List<ParsedTransaction> transactions,
			EditText[] labelInputs,
			int sourceIndex,
			String newLabel) {
		if (transactions == null || sourceIndex < 0 || sourceIndex >= transactions.size()) return;

		ParsedTransaction source = transactions.get(sourceIndex);
		String sourceKey = MerchantRuleManager.getInstance().resolveMerchantKey(source);

		if (sourceKey.isEmpty()) return;

		MerchantRuleManager.getInstance().saveLabelRule(sourceKey, newLabel);

		for (int i = 0; i < transactions.size(); i++) {
			ParsedTransaction tx = transactions.get(i);
			String key = MerchantRuleManager.getInstance().resolveMerchantKey(tx);

			if (sourceKey.equals(key)) {
				tx.label = newLabel;
				if (labelInputs[i] != null && i != sourceIndex) {
					labelInputs[i].setText(newLabel);
				}
			}
		}
	}

	private static List<ParsedTransaction> collect(Activity activity,
			List<ParsedTransaction> source,
			CheckBox[] checkBoxes,
			EditText[] labelInputs,
			EditText[] amountInputs,
			long[] dates,
			String[] categories) {

		List<ParsedTransaction> out = new ArrayList<>();

		for (int i = 0; i < source.size(); i++) {
			if (checkBoxes[i] == null || !checkBoxes[i].isChecked()) {
				continue;
			}

			ParsedTransaction original = source.get(i);

			String label = labelInputs[i].getText().toString().trim();
			if (label.isEmpty()) {
				label = original.label == null ? "Transaction" : original.label;
			}

			double amount = parseAmount(amountInputs[i].getText().toString(), original.amount);
			if (amount <= 0) {
				continue;
			}

			ParsedTransaction edited = new ParsedTransaction(
					label,
					amount,
					original.type,
					categories[i],
					dates[i]);

			edited.merchantKey = original.merchantKey;
			edited.duplicate = original.duplicate;
			edited.selected = true;

			MerchantRuleManager.getInstance().init(activity);
			MerchantRuleManager.getInstance().saveRuleFromTransaction(edited);

			out.add(edited);
		}

		return out;
	}

	private static void showCategoryPicker(Activity activity, ArrayList<String> catNames,
			String selected, CategoryCreator creator, CategoryChosen callback) {

		LinearLayout content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.VERTICAL);

		TextView helper = UiFactory.bodyMuted(activity,
				"Choisis une catégorie existante. Cette correction sera mémorisée.");
		helper.setTextSize(DS.TEXT_XS);

		LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
		hp.bottomMargin = DS.dp(activity, DS.GAP);
		content.addView(helper, hp);

		ScrollView scroll = new ScrollView(activity);
		scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, DS.dp(activity, 360)));

		LinearLayout list = new LinearLayout(activity);
		list.setOrientation(LinearLayout.VERTICAL);
		scroll.addView(list);

		final AlertDialog[] holder = new AlertDialog[1];

		if (catNames != null) {
			for (String cat : catNames) {
				if (cat == null || cat.trim().isEmpty()) continue;

				String clean = cat.trim();

				TextView row = premiumChoiceRow(activity,
						clean.equalsIgnoreCase(selected) ? "✓  " + clean : clean,
						clean.equalsIgnoreCase(selected));

				row.setOnClickListener(v -> {
					if (callback != null) callback.onChosen(clean);
					dismiss(holder);
				});

				list.addView(row);
			}
		}

		TextView create = premiumChoiceRow(activity, "＋ Créer une nouvelle catégorie", false);
		create.setTextColor(ThemeColors.primary());
		create.setTypeface(null, Typeface.BOLD);
		create.setOnClickListener(v -> {
			dismiss(holder);

			if (creator != null) {
				creator.createCategory(activity, name -> {
					if (name != null && !name.trim().isEmpty()) {
						String clean = name.trim();
						if (!containsIgnoreCase(catNames, clean)) {
							catNames.add(clean);
						}
						if (callback != null) callback.onChosen(clean);
					}
				});
			}
		});
		list.addView(create);

		content.addView(scroll);

		holder[0] = new AppDialog.Builder(activity)
				.icon("🏷️")
				.title("Choisir une catégorie")
				.subtitle("Catégories enregistrées")
				.content(content)
				.primaryBtn("FERMER", () -> dismiss(holder))
				.show();
	}

	private static TextView premiumChoiceRow(Activity activity, String text, boolean selected) {
		TextView row = new TextView(activity);
		row.setText(text);
		row.setTextSize(DS.TEXT_SM);
		row.setTextColor(selected ? ThemeColors.primary() : ThemeColors.text());
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(
				DS.dp(activity, DS.PAD),
				DS.dp(activity, 13),
				DS.dp(activity, DS.PAD),
				DS.dp(activity, 13));
		row.setBackground(UiFactory.bgBordered(
				selected ? ThemeColors.backgroundSecondary() : ThemeColors.card(),
				selected ? ThemeColors.primary() : ThemeColors.border(),
				DS.R_MD,
				activity));

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.bottomMargin = DS.dp(activity, DS.GAP_SM);
		row.setLayoutParams(lp);

		return row;
	}

	private static void showDatePicker(Activity activity, long currentMs, DatePicked callback) {
		Calendar c = Calendar.getInstance();
		if (currentMs > 0) {
			c.setTimeInMillis(currentMs);
		}

		android.app.DatePickerDialog dialog = new android.app.DatePickerDialog(
				activity,
				(view, year, month, day) -> {
					Calendar picked = Calendar.getInstance();
					picked.clear();
					picked.set(year, month, day, 12, 0, 0);
					callback.onPicked(picked.getTimeInMillis());
				},
				c.get(Calendar.YEAR),
				c.get(Calendar.MONTH),
				c.get(Calendar.DAY_OF_MONTH));

		dialog.show();
	}

	private static LinearLayout statBox(Activity activity, String value, String label, int color) {
		LinearLayout box = new LinearLayout(activity);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setGravity(Gravity.CENTER);
		box.setPadding(
				DS.dp(activity, DS.PAD_INPUT),
				DS.dp(activity, DS.GAP_SM),
				DS.dp(activity, DS.PAD_INPUT),
				DS.dp(activity, DS.GAP_SM));
		box.setBackground(UiFactory.bgBordered(
				ThemeColors.card(),
				ThemeColors.border(),
				DS.R_SM,
				activity));

		LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(0, -2, 1f);
		boxLp.leftMargin = DS.dp(activity, 3);
		boxLp.rightMargin = DS.dp(activity, 3);
		box.setLayoutParams(boxLp);

		TextView tvV = new TextView(activity);
		tvV.setText(value);
		tvV.setTextSize(20f);
		tvV.setTypeface(null, Typeface.BOLD);
		tvV.setTextColor(color);
		tvV.setGravity(Gravity.CENTER);

		TextView tvL = UiFactory.bodyMuted(activity, label);
		tvL.setGravity(Gravity.CENTER);
		tvL.setTextSize(DS.TEXT_XS);
		tvL.setTextColor(ThemeColors.subtext());

		box.addView(tvV);
		box.addView(tvL);
		return box;
	}

	private static void tintCheckbox(CheckBox cb) {
		try {
			cb.setButtonTintList(
					android.content.res.ColorStateList.valueOf(ThemeColors.primary()));
		} catch (Exception ignored) {
		}
	}

	private static void dismiss(AlertDialog[] holder) {
		try {
			if (holder != null && holder.length > 0 && holder[0] != null && holder[0].isShowing()) {
				holder[0].dismiss();
			}
		} catch (Exception ignored) {
		}
	}

	private static ArrayList<String> sanitizeCategories(List<String> categoryNames,
			List<ParsedTransaction> transactions) {
		ArrayList<String> names = new ArrayList<>();

		// Les catégories système doivent toujours être présentes
		names.add("Virements");
		names.add("Crédits");

		if (categoryNames != null) {
			for (String cat : categoryNames) {
				if (cat != null && !cat.trim().isEmpty()) {
					String clean = cat.replace("|expense", "")
							.replace("|income", "")
							.trim();

					if (!clean.isEmpty()
							&& !clean.equals(CREATE_CAT_ACTION)
							&& !containsIgnoreCase(names, clean)) {
						names.add(clean);
					}
				}
			}
		}

		if (transactions != null) {
			for (ParsedTransaction tx : transactions) {
				if (tx != null && tx.category != null && !tx.category.trim().isEmpty()) {
					String clean = tx.category.replace("|expense", "")
							.replace("|income", "")
							.trim();

					if (!containsIgnoreCase(names, clean)) {
						names.add(clean);
					}
				}
			}
		}

		if (names.isEmpty()) {
			names.add("Autre");
		}

		return names;
	}

	private static String adaptCategoryToExisting(String category,
			List<String> allowed,
			boolean income) {

		String raw = safe(category);
		if (raw.isEmpty()) raw = income ? "Revenus" : "Autre";

		if (containsIgnoreCase(allowed, raw)) {
			return findExactValue(allowed, raw);
		}

		String n = normalize(raw);

		String[][] aliases = {
				{"courses", "Alimentation"},
				{"restaurants", "Restauration"},
				{"restaurant", "Restauration"},
				{"sante", "Santé"},
				{"transport", "Transport"},
				{"energie", "Logement"},
				{"abonnement", "Abonnements"},
				{"autres prets", "Crédit"},
				{"credit", "Crédit"},
				{"assurance", "Frais bancaires"},
				{"mouvements internes", "Virements"},
				{"virements", "Virements"},
				{"revenus", "Salaire"},
				{"salaire", "Salaire"},
				{"allocations", "CAF"},
				{"caf", "CAF"}
		};

		for (String[] alias : aliases) {
			if (n.contains(normalize(alias[0])) && containsIgnoreCase(allowed, alias[1])) {
				return findExactValue(allowed, alias[1]);
			}
		}

		if (income) {
			if (containsIgnoreCase(allowed, "Revenus")) return findExactValue(allowed, "Revenus");
			if (containsIgnoreCase(allowed, "Salaire")) return findExactValue(allowed, "Salaire");
			if (containsIgnoreCase(allowed, "CAF")) return findExactValue(allowed, "CAF");
		}

		if (containsIgnoreCase(allowed, "Autre")) return findExactValue(allowed, "Autre");
		if (containsIgnoreCase(allowed, "Général")) return findExactValue(allowed, "Général");

		return raw;
	}

	private static String findExactValue(List<String> list, String value) {
		if (list == null || value == null) return value;

		for (String s : list) {
			if (s != null && s.equalsIgnoreCase(value)) {
				return s;
			}
		}
		return value;
	}

	private static int countDuplicates(List<ParsedTransaction> list) {
		int count = 0;
		if (list != null) {
			for (ParsedTransaction pt : list) {
				if (pt != null && pt.duplicate) count++;
			}
		}
		return count;
	}

	private static boolean containsIgnoreCase(List<String> list, String value) {
		if (list == null || value == null) return false;

		for (String s : list) {
			if (s != null && s.equalsIgnoreCase(value)) return true;
		}

		return false;
	}

	private static double parseAmount(String raw, double fallback) {
		if (raw == null) return fallback;

		try {
			String clean = raw.trim()
					.replace("€", "")
					.replace(" ", "")
					.replace("\u00A0", "")
					.replace(",", ".");

			if (clean.isEmpty()) return fallback;

			double value = Double.parseDouble(clean);
			return value > 0 ? Math.round(value * 100.0) / 100.0 : fallback;

		} catch (Exception e) {
			return fallback;
		}
	}

	private static String safeCategory(String raw, boolean income) {
		if (raw == null || raw.trim().isEmpty()) {
			return income ? "Revenus" : "Autre";
		}

		return raw.replace("|expense", "")
				.replace("|income", "")
				.trim();
	}

	private static String formatDate(long ms) {
		try {
			return new java.text.SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
					.format(new java.util.Date(ms));
		} catch (Exception e) {
			return "";
		}
	}

	private static String safe(String value) {
		return value == null ? "" : value.trim();
	}

	private static String normalize(String value) {
		if (value == null) return "";

		String lower = value.toLowerCase(Locale.FRANCE).trim();

		String noAccent = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD)
				.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

		return noAccent.replaceAll("\\s+", " ").trim();
	}
}