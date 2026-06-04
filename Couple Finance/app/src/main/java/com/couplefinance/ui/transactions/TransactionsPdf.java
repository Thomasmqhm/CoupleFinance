package com.couplefinance.ui.transactions;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.couplefinance.AppToast;
import com.couplefinance.data.CategoryManager;
import com.couplefinance.data.FirestoreManager;
import com.couplefinance.data.JointAccountManager;
import com.couplefinance.data.RecurringChargeManager;
import com.couplefinance.data.UserManager;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.AppDialog;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.Fmt;
import com.couplefinance.core.ui.UiFactory;
import com.couplefinance.utils.ParsedTransaction;
import com.couplefinance.utils.PdfTransactionParser;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;

/**

TransactionsPdf — Import PDF + catégories mémorisées + charges fixes suggérées.
*/
public final class TransactionsPdf {

	public static final int REQUEST_PDF_IMPORT = 4242;
	public static final long MAX_PDF_SIZE_BYTES = 10L * 1024 * 1024;

	private static final String CREATE_CAT_ACTION = "+ Créer une nouvelle catégorie";
	private static final String JOINT_ACCOUNT_LABEL = "Compte joint";
	private static final String PREF_IMPORT_CATEGORY_RULES = "pdf_import_category_rules";

	private TransactionsPdf() {
	}

	public interface OnImportComplete {
		void onSuccess(int count);

		void onError(String message);
	}

	private interface CategoryChosenCallback {
		void onChosen(String category);
	}

	private static final class ImportTarget {
		final String label;
		final String person;
		final String compte;
		final String emoji;
		final String subtitle;

		ImportTarget(String label, String person, String compte, String emoji, String subtitle) {
			this.label = label;
			this.person = person == null ? "" : person.trim();
			this.compte = compte == null ? "" : compte.trim();
			this.emoji = emoji == null ? "👤" : emoji;
			this.subtitle = subtitle == null ? "" : subtitle;
		}

	}

	public static void openPdfPicker(Activity activity) {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("application/pdf");
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		activity.startActivityForResult(Intent.createChooser(intent, "Sélectionner un relevé PDF"), REQUEST_PDF_IMPORT);
	}

	public static void handleActivityResult(Activity activity, int requestCode, int resultCode, Intent data,
			List<TransactionsModels.Transaction> existingTx, List<String[]> categories, OnImportComplete callback) {
		if (requestCode != REQUEST_PDF_IMPORT || resultCode != Activity.RESULT_OK || data == null
				|| data.getData() == null) {
			return;
		}

		Uri uri = data.getData();

		try {
			InputStream is = activity.getContentResolver().openInputStream(uri);
			if (is != null) {
				long size = is.available();
				is.close();
				if (size > MAX_PDF_SIZE_BYTES) {
					AppToast.error(activity, "Fichier trop volumineux (max 10 Mo)");
					return;
				}
			}
		} catch (Exception ignored) {
		}

		extractPdfAndParse(activity, uri, existingTx, categories, callback);

	}

	private static void extractPdfAndParse(Activity activity, Uri uri, List<TransactionsModels.Transaction> existingTx,
			List<String[]> categories, OnImportComplete callback) {
		AlertDialog progressDialog = new AlertDialog.Builder(activity).setTitle("Analyse en cours…")
				.setMessage("Lecture du relevé PDF, veuillez patienter.").setCancelable(false).create();

		progressDialog.show();
		styleAlertDialog(progressDialog);
		PDFBoxResourceLoader.init(activity);

		new AsyncTask<Void, Void, String>() {
			@Override
			protected String doInBackground(Void... v) {
				try {
					InputStream is = activity.getContentResolver().openInputStream(uri);
					if (is == null)
						return null;

					PDDocument doc = PDDocument.load(is);
					PDFTextStripper stripper = new PDFTextStripper();
					String text = stripper.getText(doc);

					doc.close();
					is.close();
					return text;
				} catch (Exception e) {
					return null;
				}
			}

			@Override
			protected void onPostExecute(String rawText) {
				progressDialog.dismiss();

				if (rawText == null || rawText.trim().isEmpty()) {
					AppToast.error(activity, "Impossible de lire ce fichier PDF.");
					return;
				}

				onTextExtracted(activity, rawText, existingTx, categories, callback);
			}
		}.execute();

	}

	private static void onTextExtracted(Activity activity, String rawText,
			List<TransactionsModels.Transaction> existingTx, List<String[]> categories, OnImportComplete callback) {
		List<ParsedTransaction> parsed = new PdfTransactionParser().parse(rawText);

		if (parsed == null || parsed.isEmpty()) {
			showNoTransactionDialog(activity);
			return;
		}

		applyDuplicateDetection(parsed, existingTx);
		ArrayList<String> catNames = buildCategoryChoices(categories, parsed);
		showImportTargetDialog(activity, parsed, catNames, existingTx, callback);

	}

	private static void showNoTransactionDialog(Activity activity) {
		new AppDialog.Builder(activity).icon("📄").title("Aucune transaction détectée").subtitle(
				"Le format du relevé n'est pas reconnu ou il ne contient aucune transaction.\n\nBanques supportées : Crédit Mutuel, CMB.")
				.primaryBtn("OK", null).noCancelBtn().show();
	}

	private static void showImportTargetDialog(Activity activity, List<ParsedTransaction> transactions,
			ArrayList<String> catNames, List<TransactionsModels.Transaction> existingTx, OnImportComplete callback) {
		ArrayList<ImportTarget> targets = buildImportTargets(existingTx);
		final ImportTarget[] selected = new ImportTarget[] { targets.get(0) };

		LinearLayout content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.VERTICAL);

		TextView helper = UiFactory.bodyMuted(activity,
				"Choisis à qui rattacher ce relevé. Pour un relevé du compte joint, sélectionne “Compte joint”.");
		helper.setTextSize(DS.TEXT_XS);
		LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
		hp.bottomMargin = DS.dp(activity, DS.GAP);
		content.addView(helper, hp);

		LinearLayout list = new LinearLayout(activity);
		list.setOrientation(LinearLayout.VERTICAL);
		content.addView(list);

		ArrayList<TextView> rows = new ArrayList<>();

		for (int i = 0; i < targets.size(); i++) {
			ImportTarget target = targets.get(i);

			TextView row = new TextView(activity);
			row.setText(target.emoji + "  " + target.label + "\n" + target.subtitle);
			row.setTextSize(DS.TEXT_SM);
			row.setTextColor(ThemeColors.text());
			row.setGravity(Gravity.CENTER_VERTICAL);
			row.setPadding(DS.dp(activity, DS.PAD), DS.dp(activity, 12), DS.dp(activity, DS.PAD), DS.dp(activity, 12));
			row.setBackground(UiFactory.bgBordered(i == 0 ? ThemeColors.backgroundSecondary() : ThemeColors.card(),
					i == 0 ? ThemeColors.primary() : ThemeColors.border(), DS.R_MD, activity));

			LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
			rp.bottomMargin = DS.dp(activity, DS.GAP_SM);
			list.addView(row, rp);
			rows.add(row);

			row.setOnClickListener(v -> {
				selected[0] = target;
				for (int j = 0; j < rows.size(); j++) {
					ImportTarget t = targets.get(j);
					rows.get(j).setBackground(UiFactory.bgBordered(
							t == selected[0] ? ThemeColors.backgroundSecondary() : ThemeColors.card(),
							t == selected[0] ? ThemeColors.primary() : ThemeColors.border(), DS.R_MD, activity));
				}
			});
		}

		new AppDialog.Builder(activity).icon("👤").title("Attribuer le relevé")
				.subtitle("Cette sélection sera appliquée à toutes les transactions importées.").content(content)
				.primaryBtn("CONTINUER", () -> showValidationDialog(activity, transactions, catNames,
						selected[0].person, selected[0].compte, callback))
				.show();

	}

	private static ArrayList<ImportTarget> buildImportTargets(List<TransactionsModels.Transaction> existingTx) {
		ArrayList<ImportTarget> targets = new ArrayList<>();
		ArrayList<String> persons = new ArrayList<>();

		if (existingTx != null) {
			for (TransactionsModels.Transaction tx : existingTx) {
				if (tx != null && tx.person != null && !tx.person.trim().isEmpty()
						&& !containsIgnoreCase(persons, tx.person.trim())) {
					persons.add(tx.person.trim());
				}
			}
		}

		if (persons.isEmpty()) {
			String fallback = "";
			try {
				fallback = UserManager.getInstance().getCurrentDisplayNameOrFallback();
			} catch (Exception ignored) {
			}
			if (fallback == null || fallback.trim().isEmpty())
				fallback = "Utilisateur";
			persons.add(fallback.trim());
		}

		for (String person : persons) {
			targets.add(new ImportTarget(person, person, "", "👤", "Transactions rattachées à " + person));
		}

		boolean jointEnabled = false;
		try {
			jointEnabled = JointAccountManager.getInstance().isEnabledLocal();
		} catch (Exception ignored) {
		}

		if (jointEnabled) {
			String jointName = JOINT_ACCOUNT_LABEL;
			try {
				jointName = JointAccountManager.getInstance().getNameLocal();
			} catch (Exception ignored) {
			}
			if (jointName == null || jointName.trim().isEmpty())
				jointName = JOINT_ACCOUNT_LABEL;
			targets.add(
					new ImportTarget(jointName.trim(), "", "joint", "🏦", "Transactions rattachées au compte joint"));
		}

		return targets;

	}

	private static void showValidationDialog(Activity activity, List<ParsedTransaction> transactions,
			ArrayList<String> catNames, String importPerson, String importCompte, OnImportComplete callback) {
		int total = transactions.size();
		int dupCnt = countDuplicates(transactions);
		int newCnt = total - dupCnt;

		LinearLayout content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.VERTICAL);

		String targetLabel = "joint".equals(importCompte) ? "Compte joint"
				: (importPerson == null || importPerson.trim().isEmpty() ? "Moi" : importPerson.trim());

		TextView targetInfo = UiFactory.bodyMuted(activity, "Relevé attribué à : " + targetLabel);
		targetInfo.setTextSize(DS.TEXT_XS);
		targetInfo.setTypeface(null, Typeface.BOLD);
		targetInfo.setTextColor(ThemeColors.primary());
		LinearLayout.LayoutParams tip = new LinearLayout.LayoutParams(-1, -2);
		tip.bottomMargin = DS.dp(activity, DS.GAP_SM);
		content.addView(targetInfo, tip);

		TextView helper = UiFactory.bodyMuted(activity,
				"Chaque ligne est modifiable. Tu peux aussi cocher “Charge fixe” pour l’ajouter automatiquement aux abonnements.");
		helper.setTextSize(DS.TEXT_XS);
		LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
		hp.bottomMargin = DS.dp(activity, DS.GAP);
		content.addView(helper, hp);

		LinearLayout statsRow = new LinearLayout(activity);
		statsRow.setOrientation(LinearLayout.HORIZONTAL);
		LinearLayout.LayoutParams srp = new LinearLayout.LayoutParams(-1, -2);
		srp.bottomMargin = DS.dp(activity, DS.GAP);
		statsRow.setLayoutParams(srp);
		statsRow.addView(statBox(activity, String.valueOf(total), "Total", ThemeColors.text()));
		statsRow.addView(statBox(activity, String.valueOf(newCnt), "Nouvelles", ThemeColors.success()));
		statsRow.addView(statBox(activity, String.valueOf(dupCnt), "Doublons", ThemeColors.subtext()));
		content.addView(statsRow);

		ScrollView sv = new ScrollView(activity);
		sv.setFillViewport(false);
		sv.setLayoutParams(new LinearLayout.LayoutParams(-1, DS.dp(activity, 430)));

		LinearLayout list = new LinearLayout(activity);
		list.setOrientation(LinearLayout.VERTICAL);
		sv.addView(list);

		EditText[] editTexts = new EditText[transactions.size()];
		TextView[] categoryButtons = new TextView[transactions.size()];
		String[] selectedCategories = new String[transactions.size()];
		CheckBox[] checkBoxes = new CheckBox[transactions.size()];
		CheckBox[] fixedChargeBoxes = new CheckBox[transactions.size()];

		for (int i = 0; i < transactions.size(); i++) {
			final int index = i;
			ParsedTransaction tx = transactions.get(i);
			boolean isDup = tx.duplicate;
			boolean isIncome = "income".equals(tx.type);

			LinearLayout card = new LinearLayout(activity);
			card.setOrientation(LinearLayout.VERTICAL);
			card.setPadding(DS.dp(activity, DS.PAD), DS.dp(activity, DS.PAD_SM), DS.dp(activity, DS.PAD),
					DS.dp(activity, DS.PAD_SM));
			card.setBackground(UiFactory.bgBordered(isDup ? ThemeColors.backgroundSecondary() : ThemeColors.card(),
					isDup ? ThemeColors.divider() : ThemeColors.border(), DS.R_MD, activity));

			LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
			cp.bottomMargin = DS.dp(activity, DS.GAP_SM);
			card.setLayoutParams(cp);

			LinearLayout top = new LinearLayout(activity);
			top.setOrientation(LinearLayout.HORIZONTAL);
			top.setGravity(Gravity.CENTER_VERTICAL);

			CheckBox cb = new CheckBox(activity);
			cb.setChecked(!isDup);
			try {
				cb.setButtonTintList(android.content.res.ColorStateList.valueOf(ThemeColors.primary()));
			} catch (Exception ignored) {
			}
			checkBoxes[i] = cb;
			top.addView(cb, new LinearLayout.LayoutParams(-2, -2));

			LinearLayout titleBox = new LinearLayout(activity);
			titleBox.setOrientation(LinearLayout.VERTICAL);
			titleBox.setPadding(DS.dp(activity, 4), 0, DS.dp(activity, 8), 0);
			top.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1f));

			TextView labelTitle = UiFactory.bodyMuted(activity, "Libellé de la transaction");
			labelTitle.setTextSize(10f);
			labelTitle.setTypeface(null, Typeface.BOLD);
			titleBox.addView(labelTitle);

			EditText etLabel = new EditText(activity);
			etLabel.setText(cleanImportedLabel(tx.label));
			etLabel.setTextSize(DS.TEXT_SM);
			etLabel.setTextColor(isDup ? ThemeColors.subtext() : ThemeColors.text());
			etLabel.setHint("Nom visible dans Transactions");
			etLabel.setHintTextColor(ThemeColors.subtext());
			etLabel.setSingleLine(true);
			etLabel.setPadding(0, 0, 0, DS.dp(activity, 2));
			etLabel.setBackgroundColor(android.graphics.Color.TRANSPARENT);
			editTexts[i] = etLabel;
			titleBox.addView(etLabel, new LinearLayout.LayoutParams(-1, -2));

			TextView tvAmount = UiFactory.bodyMuted(activity, Fmt.moneySigned(isIncome ? tx.amount : -tx.amount) + " · "
					+ Fmt.dateRelative(tx.dateMs) + (isDup ? " · Doublon probable" : ""));
			tvAmount.setTextSize(11f);
			tvAmount.setTextColor(
					isDup ? ThemeColors.subtext() : (isIncome ? ThemeColors.success() : ThemeColors.muted()));
			titleBox.addView(tvAmount);

			TextView catButton = new TextView(activity);
			String savedCat = getSavedCategoryForLabel(activity, tx.label);
			String initialCat = !savedCat.isEmpty() ? savedCat : safeCategory(tx.category, isIncome);

			selectedCategories[i] = initialCat;
			catButton.setText(initialCat + "  ▾");
			catButton.setTextSize(DS.TEXT_SM);
			catButton.setTextColor(ThemeColors.text());
			catButton.setGravity(Gravity.CENTER_VERTICAL);
			catButton.setSingleLine(true);
			catButton.setPadding(DS.dp(activity, DS.PAD_INPUT), DS.dp(activity, 9), DS.dp(activity, DS.PAD_INPUT),
					DS.dp(activity, 9));
			catButton.setBackground(
					UiFactory.bgBordered(ThemeColors.backgroundSecondary(), ThemeColors.border(), DS.R_PILL, activity));
			categoryButtons[i] = catButton;

			catButton.setOnClickListener(
					v -> showCategoryPicker(activity, catNames, selectedCategories[index], chosen -> {
						String cleanChosen = cleanCategoryName(chosen);
						selectedCategories[index] = cleanChosen;
						categoryButtons[index].setText(cleanChosen + "  ▾");
						String currentLabel = editTexts[index].getText().toString();
						saveCategoryForLabel(activity, currentLabel, cleanChosen);
						applyCategoryToSimilarRows(transactions, editTexts, selectedCategories, categoryButtons, index,
								cleanChosen);
					}));

			top.addView(catButton, new LinearLayout.LayoutParams(DS.dp(activity, 190), -2));
			card.addView(top);

			CheckBox fixedCb = new CheckBox(activity);
			fixedCb.setText("Déclarer comme charge fixe / paiement récurrent");
			fixedCb.setTextSize(11f);
			fixedCb.setTextColor(isIncome ? ThemeColors.subtext() : ThemeColors.text());
			fixedCb.setEnabled(!isIncome && !isDup);
			fixedCb.setChecked(false);
			try {
				fixedCb.setButtonTintList(android.content.res.ColorStateList.valueOf(ThemeColors.primary()));
			} catch (Exception ignored) {
			}
			fixedChargeBoxes[i] = fixedCb;
			card.addView(fixedCb);

			list.addView(card);
		}

		content.addView(sv);

		new AppDialog.Builder(activity).icon("📄")
				.title("Importer " + newCnt + " transaction" + (newCnt > 1 ? "s" : ""))
				.subtitle(dupCnt > 0 ? dupCnt
						+ " doublon(s) désactivé(s) automatiquement. Les libellés et catégories restent modifiables."
						: "Contrôlez les libellés et les catégories avant validation.")
				.content(content).primaryBtn("IMPORTER", () -> executeImport(activity, transactions, editTexts,
						selectedCategories, checkBoxes, fixedChargeBoxes, importPerson, importCompte, callback))
				.show();

	}

	private static void executeImport(Activity activity, List<ParsedTransaction> parsedList, EditText[] editTexts,
			String[] selectedCategories, CheckBox[] checkBoxes, CheckBox[] fixedChargeBoxes, String importPerson,
			String importCompte, OnImportComplete callback) {
		List<TransactionsModels.Transaction> toImport = new ArrayList<>();
		List<TransactionsModels.Transaction> fixedToCreate = new ArrayList<>();

		String finalPerson = importPerson == null ? "" : importPerson.trim();
		String finalCompte = importCompte == null ? "" : importCompte.trim();

		for (int i = 0; i < parsedList.size(); i++) {
			if (!checkBoxes[i].isChecked())
				continue;

			ParsedTransaction pt = parsedList.get(i);
			String label = editTexts[i].getText().toString().trim();
			String category = i < selectedCategories.length ? cleanCategoryName(selectedCategories[i]) : "";

			if (label.isEmpty())
				continue;

			String type = "income".equals(pt.type) ? "income" : "variable";
			if (category.isEmpty())
				category = "income".equals(pt.type) ? "Revenus" : "Autre";

			TransactionsModels.Transaction tx = new TransactionsModels.Transaction(label, pt.amount, type, category,
					pt.dateMs, System.currentTimeMillis(), finalPerson, false, false, false, "", finalCompte);
			toImport.add(tx);

			if (!"income".equals(pt.type) && fixedChargeBoxes != null && i < fixedChargeBoxes.length
					&& fixedChargeBoxes[i] != null && fixedChargeBoxes[i].isChecked()) {
				fixedToCreate.add(tx);
			}
		}

		if (toImport.isEmpty()) {
			AppToast.info(activity, "Aucune transaction sélectionnée");
			return;
		}

		// ── Créer en Firestore les catégories qui n'existent pas encore ──────
		// Les catégories venant du PDF (Tabac, Alimentation, etc.) sont stockées
		// dans les transactions mais pas dans households/{id}/categories.
		// Sans ça, elles n'apparaissent pas dans le filtre Catégorie.
		ensureCategoriesExist(toImport);

		AlertDialog progressDialog = new AlertDialog.Builder(activity).setTitle("Import en cours…")
				.setMessage("0 / " + toImport.size()).setCancelable(false).create();

		progressDialog.show();
		styleAlertDialog(progressDialog);

		final int total = toImport.size();

		TransactionsRepository.importBatch(toImport, activity, (done, t) -> progressDialog.setMessage(done + " / " + t),
				new TransactionsRepository.OnWriteComplete() {
					public void onSuccess() {
						createFixedChargesAfterImport(activity, fixedToCreate, () -> {
							progressDialog.dismiss();
							String extra = fixedToCreate.isEmpty() ? ""
									: " · " + fixedToCreate.size() + " charge(s) fixe(s) créée(s)";
							AppToast.success(activity, total + " transaction(s) importée(s) !" + extra);
							callback.onSuccess(total);
						});
					}

					public void onError(String e) {
						progressDialog.dismiss();
						AppToast.error(activity, "Erreur d'import : " + e);
						callback.onError(e);
					}
				});

	}

	private static void createFixedChargesAfterImport(Activity activity,
			List<TransactionsModels.Transaction> fixedToCreate, Runnable onDone) {
		if (fixedToCreate == null || fixedToCreate.isEmpty()) {
			if (onDone != null)
				onDone.run();
			return;
		}

		final int[] done = { 0 };
		final int total = fixedToCreate.size();

		for (TransactionsModels.Transaction tx : fixedToCreate) {
			RecurringChargeManager.getInstance().createFixedChargeFromTransaction(tx.label, tx.amount, tx.category,
					tx.dateMs, new FirestoreManager.Callback() {
						public void onSuccess(String response) {
							done[0]++;
							if (done[0] >= total && onDone != null)
								activity.runOnUiThread(onDone);
						}

						public void onError(String error) {
							done[0]++;
							if (done[0] >= total && onDone != null)
								activity.runOnUiThread(onDone);
						}
					});
		}

	}

	private static void showCategoryPicker(Activity activity, ArrayList<String> catNames, String selected,
			CategoryChosenCallback callback) {
		final ArrayList<String> choices = new ArrayList<>();
		if (catNames != null) {
			for (String cat : catNames) {
				if (cat != null && !cat.trim().isEmpty() && !cat.equals(CREATE_CAT_ACTION)
						&& !containsIgnoreCase(choices, cat.trim())) {
					choices.add(cat.trim());
				}
			}
		}
		choices.add(CREATE_CAT_ACTION);

		int checked = findCategoryPosition(choices, selected);

		AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Choisir une catégorie")
				.setSingleChoiceItems(choices.toArray(new String[0]), checked, (d, which) -> {
					String choice = choices.get(which);
					if (CREATE_CAT_ACTION.equals(choice)) {
						d.dismiss();
						showCreateCategoryDialog(activity, catNames, callback);
					} else {
						callback.onChosen(choice);
						d.dismiss();
					}
				}).setNegativeButton("Annuler", null).create();

		dialog.show();
		styleAlertDialog(dialog);

	}

	private static void showCreateCategoryDialog(Activity activity, ArrayList<String> catNames,
			CategoryChosenCallback callback) {
		LinearLayout card = new LinearLayout(activity);
		card.setOrientation(LinearLayout.VERTICAL);
		int pad = DS.dp(activity, 22);
		card.setPadding(pad, pad, pad, DS.dp(activity, 16));
		card.setBackground(UiFactory.bgBordered(ThemeColors.card(), ThemeColors.border(), DS.R_MD, activity));

		TextView title = new TextView(activity);
		title.setText("Nouvelle catégorie");
		title.setTextSize(20f);
		title.setTextColor(ThemeColors.text());
		title.setTypeface(null, Typeface.BOLD);
		card.addView(title);

		TextView hint = UiFactory.bodyMuted(activity,
				"Ajoute une catégorie propre au foyer. Elle sera disponible pour les prochains imports PDF.");
		hint.setTextSize(DS.TEXT_XS);
		LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
		hp.topMargin = DS.dp(activity, 6);
		card.addView(hint, hp);

		EditText input = new EditText(activity);
		input.setHint("Exemple : Courses, Tabac, EDF…");
		input.setSingleLine(true);
		input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
		input.setTextColor(ThemeColors.text());
		input.setHintTextColor(ThemeColors.subtext());
		input.setTextSize(DS.TEXT_SM);
		input.setPadding(DS.dp(activity, DS.PAD_INPUT), DS.dp(activity, 10), DS.dp(activity, DS.PAD_INPUT),
				DS.dp(activity, 10));
		input.setBackground(
				UiFactory.bgBordered(ThemeColors.backgroundSecondary(), ThemeColors.border(), DS.R_MD, activity));
		LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
		ip.topMargin = DS.dp(activity, DS.GAP);
		card.addView(input, ip);

		LinearLayout actions = new LinearLayout(activity);
		actions.setOrientation(LinearLayout.HORIZONTAL);
		actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);

		TextView cancel = new TextView(activity);
		cancel.setText("ANNULER");
		cancel.setTextColor(ThemeColors.subtext());
		cancel.setTypeface(null, Typeface.BOLD);
		cancel.setGravity(Gravity.CENTER);
		cancel.setPadding(DS.dp(activity, 18), DS.dp(activity, 12), DS.dp(activity, 18), DS.dp(activity, 12));

		TextView create = new TextView(activity);
		create.setText("CRÉER");
		create.setTextColor(ThemeColors.primary());
		create.setTypeface(null, Typeface.BOLD);
		create.setGravity(Gravity.CENTER);
		create.setPadding(DS.dp(activity, 18), DS.dp(activity, 12), DS.dp(activity, 18), DS.dp(activity, 12));

		actions.addView(cancel);
		actions.addView(create);
		LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2);
		ap.topMargin = DS.dp(activity, DS.GAP);
		card.addView(actions, ap);

		AlertDialog dialog = new AlertDialog.Builder(activity).setView(card).create();

		cancel.setOnClickListener(v -> dialog.dismiss());
		create.setOnClickListener(v -> {
			String name = input.getText().toString().trim();
			if (name.isEmpty()) {
				input.setError("Nom obligatoire");
				return;
			}

			if (containsIgnoreCase(catNames, name)) {
				callback.onChosen(name);
				dialog.dismiss();
				return;
			}

			CategoryManager.getInstance().addCategory(name, guessEmoji(name), new FirestoreManager.Callback() {
				public void onSuccess(String response) {
					if (!containsIgnoreCase(catNames, name)) {
						int insertAt = Math.max(0, catNames.size() - 1);
						catNames.add(insertAt, name);
					}
					AppToast.success(activity, "Catégorie créée");
					callback.onChosen(name);
					dialog.dismiss();
				}

				public void onError(String error) {
					AppToast.error(activity, "Impossible de créer la catégorie : " + error);
				}
			});
		});

		dialog.show();

		if (dialog.getWindow() != null) {
			dialog.getWindow().setBackgroundDrawable(
					new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
			dialog.getWindow().setDimAmount(0.72f);
			dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		}

	}

	private static void applyDuplicateDetection(List<ParsedTransaction> parsed,
			List<TransactionsModels.Transaction> existing) {
		HashSet<String> existingKeys = new HashSet<>();
		HashSet<String> importKeys = new HashSet<>();

		// Les labels en base ont déjà été nettoyés par cleanImportedLabel().
		// On utilise duplicateKey() qui normalise les deux de la même façon.
		if (existing != null) {
			for (TransactionsModels.Transaction tx : existing) {
				if (tx != null) {
					existingKeys.add(
							duplicateKey(tx.dateMs, tx.isIncome() ? "income" : "variable",
									tx.amount, tx.label));
				}
			}
		}

		for (ParsedTransaction pt : parsed) {
			// Comparer avec le label nettoyé (comme il sera stocké en base)
			String cleanedLabel = cleanImportedLabel(pt.label);
			String key = duplicateKey(pt.dateMs, "income".equals(pt.type) ? "income" : "variable",
					pt.amount, cleanedLabel);
			pt.duplicate = existingKeys.contains(key) || importKeys.contains(key);
			importKeys.add(key);
		}

	}

	private static int countDuplicates(List<ParsedTransaction> list) {
		int count = 0;
		if (list == null)
			return 0;
		for (ParsedTransaction pt : list)
			if (pt != null && pt.duplicate)
				count++;
		return count;
	}

	private static String duplicateKey(long dateMs, String type, double amount, String label) {
		String day = "";
		try {
			Calendar c = Calendar.getInstance();
			c.setTimeInMillis(dateMs);
			day = c.get(Calendar.YEAR) + "-" + (c.get(Calendar.MONTH) + 1) + "-" + c.get(Calendar.DAY_OF_MONTH);
		} catch (Exception ignored) {
		}

		// Clé stricte : date précise + montant exact + label normalisé minimal.
		// On n'utilise plus merchantRuleKey() qui supprimait "virement"/"recu"
		// et rendait des transactions différentes identiques (faux doublons).
		String labelKey = label == null ? "" :
				label.toLowerCase(java.util.Locale.FRANCE)
				     .replaceAll("[^a-z0-9]", " ")
				     .replaceAll("\\s+", " ")
				     .trim();

		return day + "|" + type + "|" + Math.round(amount * 100) + "|" + labelKey;
	}

	private static ArrayList<String> buildCategoryChoices(List<String[]> categories, List<ParsedTransaction> parsed) {
		ArrayList<String> names = new ArrayList<>();

		if (categories != null) {
			for (String[] cat : categories) {
				if (cat != null && cat.length > 0 && cat[0] != null && !cat[0].trim().isEmpty()) {
					String clean = cleanCategoryName(cat[0].trim());
					if (!clean.isEmpty() && !containsIgnoreCase(names, clean))
						names.add(clean);
				}
			}
		}

		if (parsed != null) {
			for (ParsedTransaction pt : parsed) {
				String clean = cleanCategoryName(pt.category);
				if (!clean.isEmpty() && !containsIgnoreCase(names, clean))
					names.add(clean);
			}
		}

		if (!names.contains(CREATE_CAT_ACTION))
			names.add(CREATE_CAT_ACTION);
		return names;

	}

	private static boolean containsIgnoreCase(List<String> list, String value) {
		if (list == null || value == null)
			return false;
		for (String s : list)
			if (s != null && s.equalsIgnoreCase(value))
				return true;
		return false;
	}

	private static int findCategoryPosition(ArrayList<String> cats, String cat) {
		if (cats == null || cats.isEmpty() || cat == null)
			return 0;
		for (int i = 0; i < cats.size(); i++) {
			String current = cats.get(i);
			if (current != null && current.equalsIgnoreCase(cat))
				return i;
		}
		return 0;
	}

	private static String safeCategory(String raw, boolean income) {
		String value = cleanCategoryName(raw);
		if (value.isEmpty())
			return income ? "Revenus" : "Autre";
		return value;
	}

	private static String cleanCategoryName(String value) {
		if (value == null)
			return "";
		return value.replace("|expense", "").replace("|income", "").trim();
	}

	private static String merchantRuleKey(String label) {
		if (label == null)
			return "";

		String clean = label.toLowerCase(java.util.Locale.FRANCE).replace("prélèvement", "").replace("prelevement", "")
				.replace("carte", "").replace("cb", "").replace("virement", "").replace("reçu", "").replace("recu", "")
				.replaceAll("\\d+[,.]?\\d*\\s*eur", "").replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();

		if (clean.length() > 28)
			clean = clean.substring(0, 28).trim();
		return clean;
	}

	private static String cleanImportedLabel(String label) {
		if (label == null)
			return "";

		String s = label.trim();

		s = s.replaceAll("(?i)^carte\\s*-\\s*", "");
		s = s.replaceAll("(?i)^pr[ée]l[èe]vement\\s*-\\s*", "");
		s = s.replaceAll("(?i)^virement\\s*-\\s*", "");
		s = s.replaceAll("(?i)\\b\\d+[,.]\\d+\\s*EUR\\b", "");
		s = s.replaceAll("(?i)\\b\\d+[,.]\\d+\\s*€\\b", "");
		s = s.replaceAll("\\s+", " ").trim();

		String upper = s.toUpperCase(java.util.Locale.FRANCE);
		if (upper.contains("OCTOPUS"))
			return "OCTOPUS";
		if (upper.contains("NINTENDO"))
			return "NINTENDO";

		return removeRepeatedWords(s).trim();
	}

	private static String getSavedCategoryForLabel(Activity activity, String label) {
		try {
			String key = merchantRuleKey(label);
			if (key.isEmpty())
				return "";
			return activity.getSharedPreferences(PREF_IMPORT_CATEGORY_RULES, Activity.MODE_PRIVATE).getString(key, "");
		} catch (Exception e) {
			return "";
		}
	}

	private static void saveCategoryForLabel(Activity activity, String label, String category) {
		try {
			String key = merchantRuleKey(label);
			if (key.isEmpty() || category == null || category.trim().isEmpty())
				return;
			activity.getSharedPreferences(PREF_IMPORT_CATEGORY_RULES, Activity.MODE_PRIVATE).edit()
					.putString(key, cleanCategoryName(category)).apply();
		} catch (Exception ignored) {
		}
	}

	private static void applyCategoryToSimilarRows(List<ParsedTransaction> transactions, EditText[] editTexts,
			String[] selectedCategories, TextView[] categoryButtons, int sourceIndex, String category) {
		if (transactions == null || editTexts == null || selectedCategories == null || categoryButtons == null)
			return;
		if (sourceIndex < 0 || sourceIndex >= transactions.size())
			return;

		String sourceKey = merchantRuleKey(editTexts[sourceIndex].getText().toString());
		if (sourceKey.isEmpty())
			return;

		for (int i = 0; i < transactions.size(); i++) {
			if (i >= editTexts.length || i >= selectedCategories.length || i >= categoryButtons.length)
				continue;
			String rowKey = merchantRuleKey(editTexts[i].getText().toString());
			if (sourceKey.equals(rowKey)) {
				String cleanCategory = cleanCategoryName(category);
				selectedCategories[i] = cleanCategory;
				categoryButtons[i].setText(cleanCategory + "  ▾");
			}
		}

	}



	private static String removeRepeatedWords(String value) {
		if (value == null)
			return "";
		String[] words = value.trim().split("\\s+");
		StringBuilder out = new StringBuilder();
		String prev = "";

		for (String word : words) {
			if (word == null || word.trim().isEmpty())
				continue;
			String clean = word.trim();
			if (clean.equalsIgnoreCase(prev))
				continue;
			if (out.length() > 0)
				out.append(" ");
			out.append(clean);
			prev = clean;
		}
		return out.toString().trim();

	}

	/**
	 * Pour chaque catégorie des transactions importées, la crée en Firestore
	 * si elle n'existe pas encore. Fire-and-forget, silencieux.
	 */
	private static void ensureCategoriesExist(
			List<TransactionsModels.Transaction> transactions) {

		// Dédupliquer localement pour ne pas créer plusieurs fois la même
		ArrayList<String> seen = new ArrayList<String>();

		for (TransactionsModels.Transaction tx : transactions) {
			if (tx == null || tx.category == null || tx.category.trim().isEmpty()) continue;
			String cat = tx.category.trim();
			if (cat.equalsIgnoreCase("Autre") || cat.equalsIgnoreCase("Autres")) continue;

			boolean alreadySeen = false;
			for (String s : seen) {
				if (s.equalsIgnoreCase(cat)) { alreadySeen = true; break; }
			}
			if (alreadySeen) continue;
			seen.add(cat);

			String nameWithType = tx.isIncome() ? cat + "|income" : cat + "|expense";

			// Fire-and-forget : Firestore ignorera si elle existe déjà (POST = nouveau doc)
			// On accepte les éventuels doublons en base — getCategories déduplique à la lecture
			CategoryManager.getInstance().addCategory(
					nameWithType,
					guessEmoji(cat),
					new com.couplefinance.data.FirestoreManager.Callback() {
						public void onSuccess(String r) { /* silencieux */ }
						public void onError(String e)   { /* silencieux */ }
					});
		}
	}

	private static String guessEmoji(String name) {
		String n = name == null ? "" : name.toLowerCase(java.util.Locale.FRANCE);
		if (n.contains("revenu") || n.contains("salaire"))
			return "↗️";
		if (n.contains("course") || n.contains("aliment") || n.contains("super") || n.contains("lidl"))
			return "🛒";
		if (n.contains("loyer") || n.contains("logement"))
			return "🏠";
		if (n.contains("edf") || n.contains("élec") || n.contains("elec") || n.contains("energie"))
			return "⚡";
		if (n.contains("eau") || n.contains("saur"))
			return "💧";
		if (n.contains("transport") || n.contains("essence") || n.contains("carburant"))
			return "🚗";
		if (n.contains("restaurant") || n.contains("resto") || n.contains("fast"))
			return "🍽️";
		if (n.contains("abonnement") || n.contains("netflix") || n.contains("spotify"))
			return "📱";
		if (n.contains("banque") || n.contains("frais"))
			return "🏦";
		if (n.contains("santé") || n.contains("sante") || n.contains("mutuelle"))
			return "💊";
		if (n.contains("tabac"))
			return "🚬";
		return "🏷️";
	}

	private static LinearLayout statBox(Activity activity, String value, String label, int color) {
		LinearLayout box = new LinearLayout(activity);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setGravity(Gravity.CENTER);
		box.setPadding(DS.dp(activity, DS.PAD_INPUT), DS.dp(activity, DS.GAP_SM), DS.dp(activity, DS.PAD_INPUT),
				DS.dp(activity, DS.GAP_SM));
		box.setBackground(UiFactory.bgBordered(ThemeColors.card(), ThemeColors.border(), DS.R_SM, activity));

		LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(0, -2, 1f);
		boxLp.leftMargin = DS.dp(activity, 3);
		boxLp.rightMargin = DS.dp(activity, 3);
		box.setLayoutParams(boxLp);

		TextView tvV = new TextView(activity);
		tvV.setText(value);
		tvV.setTextSize(20f);
		tvV.setTypeface(null, android.graphics.Typeface.BOLD);
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

	private static void styleAlertDialog(AlertDialog dialog) {
		if (dialog == null)
			return;
		try {
			if (dialog.getWindow() != null) {
				dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
			}
			ListView lv = dialog.getListView();
			if (lv != null)
				lv.setBackgroundColor(ThemeColors.modal());
			Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
			Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
			if (positive != null)
				positive.setTextColor(ThemeColors.primary());
			if (negative != null)
				negative.setTextColor(ThemeColors.subtext());
		} catch (Exception ignored) {
		}
	}
}