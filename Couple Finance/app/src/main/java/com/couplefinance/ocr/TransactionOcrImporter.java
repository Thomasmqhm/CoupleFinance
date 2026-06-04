package com.couplefinance.ocr;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.AppToast;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.AppDialog;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.UiFactory;
import com.couplefinance.data.CategoryManager;
import com.couplefinance.data.FirestoreManager;
import com.couplefinance.data.JointAccountManager;
import com.couplefinance.data.MerchantRuleManager;
import com.couplefinance.data.UserManager;
import com.couplefinance.ui.transactions.TransactionsModels;
import com.couplefinance.ui.transactions.TransactionsRepository;
import com.couplefinance.utils.ParsedTransaction;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;

public final class TransactionOcrImporter {

	public static final int REQUEST_OCR_IMAGE = 4343;
	public static final long MAX_IMAGE_SIZE_BYTES = 8L * 1024 * 1024;

	private static final int MODE_RECEIPT = 0;
	private static final int MODE_BANK = 1;

	private TransactionOcrImporter() {
	}

	public interface OnImportComplete {
		void onSuccess(int count);
		void onError(String message);
	}

	public static void openImagePicker(Activity activity) {
		if (activity == null) return;

		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("image/*");
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.putExtra(Intent.EXTRA_MIME_TYPES,
				new String[]{"image/jpeg", "image/png", "image/webp"});

		activity.startActivityForResult(
				Intent.createChooser(intent, "Sélectionner une image"),
				REQUEST_OCR_IMAGE);
	}

	public static void handleActivityResult(Activity activity,
			int requestCode,
			int resultCode,
			Intent data,
			List<TransactionsModels.Transaction> existingTx,
			List<String[]> categories,
			OnImportComplete callback) {

		if (requestCode != REQUEST_OCR_IMAGE
				|| resultCode != Activity.RESULT_OK
				|| data == null
				|| data.getData() == null) {
			return;
		}

		MerchantRuleManager.getInstance().init(activity);

		Uri uri = data.getData();

		try {
			InputStream is = activity.getContentResolver().openInputStream(uri);
			if (is != null) {
				long size = is.available();
				is.close();

				if (size > MAX_IMAGE_SIZE_BYTES) {
					AppToast.error(activity, "Image trop volumineuse (max 8 Mo)");
					return;
				}
			}
		} catch (Exception ignored) {
		}

		askMode(activity, uri, existingTx, categories, callback);
	}

	private static void askMode(Activity activity,
			Uri uri,
			List<TransactionsModels.Transaction> existingTx,
			List<String[]> categories,
			OnImportComplete callback) {

		LinearLayout content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.VERTICAL);

		TextView helper = UiFactory.bodyMuted(activity,
				"Choisis le type d'image pour adapter l'analyse OCR.");
		helper.setTextSize(DS.TEXT_XS);

		LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
		hp.bottomMargin = DS.dp(activity, DS.GAP);
		content.addView(helper, hp);

		final AlertDialog[] holder = new AlertDialog[1];

		View receipt = premiumChoiceRow(activity,
				"🧾  Ticket de caisse",
				"Idéal pour les tickets papier : restaurant, magasin, caisse automatique.");
		receipt.setOnClickListener(v -> {
			dismiss(holder);
			runOcr(activity, uri, MODE_RECEIPT, existingTx, categories, callback);
		});
		content.addView(receipt);

		View bank = premiumChoiceRow(activity,
				"🏦  Screenshot bancaire",
				"Idéal pour une liste d'opérations depuis l'application de ta banque.");
		bank.setOnClickListener(v -> {
			dismiss(holder);
			runOcr(activity, uri, MODE_BANK, existingTx, categories, callback);
		});
		content.addView(bank);

		holder[0] = new AppDialog.Builder(activity)
				.icon("📷")
				.title("Type d'image à importer")
				.subtitle("Import OCR intelligent")
				.content(content)
				.primaryBtn("ANNULER", () -> dismiss(holder))
				.show();
	}

	private static void runOcr(Activity activity,
			Uri uri,
			int mode,
			List<TransactionsModels.Transaction> existingTx,
			List<String[]> categories,
			OnImportComplete callback) {

		Bitmap bitmap = decodeBitmap(activity, uri);

		if (bitmap == null) {
			AppToast.error(activity, "Impossible de lire cette image");
			notifyError(callback, "Image illisible");
			return;
		}

		final AlertDialog[] progressHolder = new AlertDialog[1];

		LinearLayout content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.VERTICAL);

		TextView msg = UiFactory.bodyMuted(activity,
				"Lecture du texte et reconstruction des opérations. Patiente quelques secondes.");
		msg.setTextSize(DS.TEXT_SM);
		content.addView(msg);

		progressHolder[0] = new AppDialog.Builder(activity)
				.icon("🔎")
				.title("Analyse OCR en cours…")
				.subtitle(mode == MODE_BANK ? "Screenshot bancaire" : "Ticket de caisse")
				.content(content)
				.show();

		OcrEngine engine = new TesseractOcrEngine();

		engine.recognize(activity, bitmap, new OcrEngine.Callback() {
			@Override
			public void onSuccess(String rawText) {
				dismiss(progressHolder);
				engine.release();
				onTextRecognized(activity, rawText, mode, existingTx, categories, callback);
			}

			@Override
			public void onError(String message) {
				dismiss(progressHolder);
				engine.release();
				AppToast.error(activity, message);
				notifyError(callback, message);
			}
		});
	}

	private static void onTextRecognized(Activity activity,
			String rawText,
			int mode,
			List<TransactionsModels.Transaction> existingTx,
			List<String[]> categories,
			OnImportComplete callback) {

		List<ParsedTransaction> parsed;
		String modeLabel;

		if (mode == MODE_BANK) {
			parsed = new BankScreenshotOcrParser().parse(rawText);
			modeLabel = "Screenshot bancaire";
		} else {
			parsed = new ReceiptOcrParser().parse(rawText);
			modeLabel = "Ticket de caisse";
		}

		if (parsed == null || parsed.isEmpty()) {
			showNoResultDialog(activity, mode);
			notifyError(callback, "Aucune transaction détectée");
			return;
		}

		List<String> categoryNames = buildCategoryNames(categories, parsed);

		MerchantRuleManager.getInstance().init(activity);
		for (ParsedTransaction pt : parsed) {
			MerchantRuleManager.getInstance().applyKnownRule(pt, categoryNames);
		}

		applyDuplicateDetection(parsed, existingTx);
		askTargetThenPreview(activity, modeLabel, parsed, categoryNames, existingTx, callback);
	}

	private static void askTargetThenPreview(Activity activity,
			String modeLabel,
			List<ParsedTransaction> parsed,
			List<String> categoryNames,
			List<TransactionsModels.Transaction> existingTx,
			OnImportComplete callback) {

		ArrayList<Target> targets = buildTargets(existingTx);

		LinearLayout content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.VERTICAL);

		TextView helper = UiFactory.bodyMuted(activity,
				"Choisis à qui attribuer les transactions importées.");
		helper.setTextSize(DS.TEXT_XS);

		LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
		hp.bottomMargin = DS.dp(activity, DS.GAP);
		content.addView(helper, hp);

		final AlertDialog[] holder = new AlertDialog[1];

		for (Target target : targets) {
			View row = premiumChoiceRow(activity,
					target.emoji + "  " + target.label,
					"joint".equals(target.compte)
							? "Importé sur le compte joint"
							: "Importé au nom de " + target.person);

			row.setOnClickListener(v -> {
				dismiss(holder);
				showPreview(activity, modeLabel, target, parsed, categoryNames, callback);
			});

			content.addView(row);
		}

		holder[0] = new AppDialog.Builder(activity)
				.icon("👥")
				.title("Attribuer les transactions")
				.subtitle("Choix de la personne ou du compte")
				.content(content)
				.primaryBtn("ANNULER", () -> dismiss(holder))
				.show();
	}

	private static final class Target {
		final String label;
		final String person;
		final String compte;
		final String emoji;

		Target(String label, String person, String compte, String emoji) {
			this.label = label;
			this.person = person == null ? "" : person.trim();
			this.compte = compte == null ? "" : compte.trim();
			this.emoji = emoji;
		}
	}

	private static ArrayList<Target> buildTargets(List<TransactionsModels.Transaction> existingTx) {
		ArrayList<Target> targets = new ArrayList<>();
		ArrayList<String> persons = new ArrayList<>();

		if (existingTx != null) {
			for (TransactionsModels.Transaction tx : existingTx) {
				if (tx != null
						&& tx.person != null
						&& !tx.person.trim().isEmpty()
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

			if (fallback == null || fallback.trim().isEmpty()) {
				fallback = "Utilisateur";
			}

			persons.add(fallback.trim());
		}

		for (String person : persons) {
			targets.add(new Target(person, person, "", "👤"));
		}

		boolean jointEnabled = false;

		try {
			jointEnabled = JointAccountManager.getInstance().isEnabledLocal();
		} catch (Exception ignored) {
		}

		if (jointEnabled) {
			String jointName = "Compte joint";

			try {
				String n = JointAccountManager.getInstance().getNameLocal();
				if (n != null && !n.trim().isEmpty()) {
					jointName = n.trim();
				}
			} catch (Exception ignored) {
			}

			targets.add(new Target(jointName, "", "joint", "🏦"));
		}

		return targets;
	}

	private static void showPreview(Activity activity,
			String modeLabel,
			Target target,
			List<ParsedTransaction> parsed,
			List<String> categoryNames,
			OnImportComplete callback) {

		OcrTransactionPreviewDialog.CategoryCreator creator =
				(act, result) -> showCreateCategoryDialog(act, result);

		OcrTransactionPreviewDialog.show(
				activity,
				modeLabel,
				"joint".equals(target.compte) ? target.label : target.person,
				parsed,
				categoryNames,
				creator,
				confirmed -> executeImport(activity, confirmed, target, callback));
	}

	private static void executeImport(Activity activity,
			List<ParsedTransaction> confirmed,
			Target target,
			OnImportComplete callback) {

		if (confirmed == null || confirmed.isEmpty()) {
			AppToast.info(activity, "Aucune transaction sélectionnée");
			notifyError(callback, "Sélection vide");
			return;
		}

		String person = target == null ? "" : target.person;
		String compte = target == null ? "" : target.compte;

		List<TransactionsModels.Transaction> toImport = new ArrayList<>();

		for (ParsedTransaction pt : confirmed) {
			if (pt == null) continue;

			MerchantRuleManager.getInstance().init(activity);
			MerchantRuleManager.getInstance().saveRuleFromTransaction(pt);

			String type = "income".equals(pt.type) ? "income" : "variable";

			String category = pt.category == null || pt.category.trim().isEmpty()
					? ("income".equals(pt.type) ? "Revenus" : "Autre")
					: pt.category.trim();

			TransactionsModels.Transaction tx = new TransactionsModels.Transaction(
					pt.label,
					pt.amount,
					type,
					category,
					pt.dateMs,
					System.currentTimeMillis(),
					person,
					false,
					false,
					false,
					"",
					compte);

			toImport.add(tx);
		}

		if (toImport.isEmpty()) {
			AppToast.info(activity, "Aucune transaction valide");
			notifyError(callback, "Rien à importer");
			return;
		}

		ensureCategoriesExist(toImport);

		final int total = toImport.size();
		final AlertDialog[] progressHolder = new AlertDialog[1];

		LinearLayout content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.VERTICAL);

		TextView progressText = UiFactory.bodyMuted(activity, "0 / " + total);
		progressText.setTextSize(DS.TEXT_SM);
		progressText.setTypeface(null, Typeface.BOLD);
		progressText.setTextColor(ThemeColors.primary());
		content.addView(progressText);

		progressHolder[0] = new AppDialog.Builder(activity)
				.icon("☁️")
				.title("Import en cours…")
				.subtitle("Enregistrement dans Couple Finance")
				.content(content)
				.show();

		TransactionsRepository.importBatch(toImport, activity,
				(done, t) -> progressText.setText(done + " / " + t),
				new TransactionsRepository.OnWriteComplete() {
					@Override
					public void onSuccess() {
						dismiss(progressHolder);
						AppToast.success(activity, total + " transaction(s) importée(s) !");
						if (callback != null) {
							callback.onSuccess(total);
						}
					}

					@Override
					public void onError(String e) {
						dismiss(progressHolder);
						AppToast.error(activity, "Erreur d'import : " + e);
						notifyError(callback, e);
					}
				});
	}

	private static void applyDuplicateDetection(List<ParsedTransaction> parsed,
			List<TransactionsModels.Transaction> existing) {

		HashSet<String> existingKeys = new HashSet<>();
		HashSet<String> importKeys = new HashSet<>();

		if (existing != null) {
			for (TransactionsModels.Transaction tx : existing) {
				if (tx != null) {
					existingKeys.add(duplicateKey(
							tx.dateMs,
							tx.isIncome() ? "income" : "variable",
							tx.amount,
							tx.label));
				}
			}
		}

		for (ParsedTransaction pt : parsed) {
			if (pt == null) continue;

			String key = duplicateKey(
					pt.dateMs,
					"income".equals(pt.type) ? "income" : "variable",
					pt.amount,
					pt.label);

			pt.duplicate = existingKeys.contains(key) || importKeys.contains(key);

			if (pt.duplicate) {
				pt.selected = false;
				pt.duplicateReason = "Transaction identique déjà présente";
				pt.duplicateWarning = "Doublon probable";
			}

			importKeys.add(key);
		}
	}

	private static String duplicateKey(long dateMs, String type, double amount, String label) {
		String day = "";

		try {
			Calendar c = Calendar.getInstance();
			c.setTimeInMillis(dateMs);
			day = c.get(Calendar.YEAR) + "-"
					+ (c.get(Calendar.MONTH) + 1) + "-"
					+ c.get(Calendar.DAY_OF_MONTH);
		} catch (Exception ignored) {
		}

		String labelKey = label == null ? "" :
				label.toLowerCase(java.util.Locale.FRANCE)
						.replaceAll("[^a-z0-9]", " ")
						.replaceAll("\\s+", " ")
						.trim();

		return day + "|" + type + "|" + Math.round(amount * 100) + "|" + labelKey;
	}

	private static List<String> buildCategoryNames(List<String[]> categories,
			List<ParsedTransaction> parsed) {

		ArrayList<String> names = new ArrayList<>();

		if (categories != null) {
			for (String[] cat : categories) {
				if (cat != null
						&& cat.length > 0
						&& cat[0] != null
						&& !cat[0].trim().isEmpty()) {

					String clean = cat[0]
							.replace("|expense", "")
							.replace("|income", "")
							.trim();

					if (!clean.isEmpty() && !containsIgnoreCase(names, clean)) {
						names.add(clean);
					}
				}
			}
		}

		if (parsed != null) {
			for (ParsedTransaction pt : parsed) {
				if (pt != null
						&& pt.category != null
						&& !pt.category.trim().isEmpty()
						&& !containsIgnoreCase(names, pt.category.trim())) {
					names.add(pt.category.trim());
				}
			}
		}

		if (names.isEmpty()) {
			names.add("Autre");
		}

		return names;
	}

	private static void ensureCategoriesExist(List<TransactionsModels.Transaction> transactions) {
		ArrayList<String> seen = new ArrayList<>();

		for (TransactionsModels.Transaction tx : transactions) {
			if (tx == null || tx.category == null || tx.category.trim().isEmpty()) {
				continue;
			}

			String cat = tx.category.trim();

			if (cat.equalsIgnoreCase("Autre") || cat.equalsIgnoreCase("Autres")) {
				continue;
			}

			if (containsIgnoreCase(seen, cat)) {
				continue;
			}

			seen.add(cat);

			String nameWithType = tx.isIncome() ? cat + "|income" : cat + "|expense";

			CategoryManager.getInstance().addCategory(
					nameWithType,
					guessEmoji(cat),
					new FirestoreManager.Callback() {
						public void onSuccess(String r) {
						}

						public void onError(String e) {
						}
					});
		}
	}

	private static void showCreateCategoryDialog(Activity activity,
			OcrTransactionPreviewDialog.CategoryCreator.Result result) {

		LinearLayout content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.VERTICAL);

		TextView helper = UiFactory.bodyMuted(activity,
				"La catégorie sera ajoutée à tes catégories enregistrées et réutilisable dans les prochains imports.");
		helper.setTextSize(DS.TEXT_XS);

		LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
		hp.bottomMargin = DS.dp(activity, DS.GAP);
		content.addView(helper, hp);

		EditText input = new EditText(activity);
		input.setHint("Exemple : Courses, Tabac, EDF…");
		input.setSingleLine(true);
		input.setTextSize(DS.TEXT_SM);
		input.setTextColor(ThemeColors.text());
		input.setHintTextColor(ThemeColors.subtext());
		input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
		input.setPadding(
				DS.dp(activity, DS.PAD_INPUT),
				DS.dp(activity, 12),
				DS.dp(activity, DS.PAD_INPUT),
				DS.dp(activity, 12));
		input.setBackground(UiFactory.bgBordered(
				ThemeColors.backgroundSecondary(),
				ThemeColors.border(),
				DS.R_MD,
				activity));
		content.addView(input, new LinearLayout.LayoutParams(-1, -2));

		final AlertDialog[] holder = new AlertDialog[1];

		holder[0] = new AppDialog.Builder(activity)
				.icon("🏷️")
				.title("Nouvelle catégorie")
				.subtitle("Ajout aux catégories")
				.content(content)
				.primaryBtn("CRÉER", () -> {
					String name = input.getText().toString().trim();

					if (name.isEmpty()) {
						AppToast.error(activity, "Nom de catégorie obligatoire");
						return;
					}

					CategoryManager.getInstance().addCategory(
							name,
							guessEmoji(name),
							new FirestoreManager.Callback() {
								public void onSuccess(String r) {
									AppToast.success(activity, "Catégorie créée");
									dismiss(holder);

									if (result != null) {
										result.onCreated(name);
									}
								}

								public void onError(String e) {
									AppToast.error(activity, "Création impossible : " + e);
								}
							});
				})
				.show();
	}

	private static Bitmap decodeBitmap(Activity activity, Uri uri) {
		try {
			Bitmap bmp = MediaStore.Images.Media.getBitmap(
					activity.getContentResolver(),
					uri);

			if (bmp != null) {
				return downscaleIfNeeded(bmp);
			}
		} catch (Throwable ignored) {
		}

		try {
			BitmapFactory.Options bounds = new BitmapFactory.Options();
			bounds.inJustDecodeBounds = true;

			InputStream is1 = activity.getContentResolver().openInputStream(uri);
			BitmapFactory.decodeStream(is1, null, bounds);
			if (is1 != null) is1.close();

			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, 2200);
			opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

			InputStream is2 = activity.getContentResolver().openInputStream(uri);
			Bitmap bmp = BitmapFactory.decodeStream(is2, null, opts);
			if (is2 != null) is2.close();

			return bmp;

		} catch (Throwable t) {
			return null;
		}
	}

	private static Bitmap downscaleIfNeeded(Bitmap src) {
		if (src == null) return null;

		int max = Math.max(src.getWidth(), src.getHeight());
		int limit = 2400;

		if (max <= limit) return src;

		float ratio = (float) limit / (float) max;
		int w = Math.round(src.getWidth() * ratio);
		int h = Math.round(src.getHeight() * ratio);

		try {
			return Bitmap.createScaledBitmap(src, Math.max(1, w), Math.max(1, h), true);
		} catch (Throwable t) {
			return src;
		}
	}

	private static int computeSampleSize(int width, int height, int targetMax) {
		int sample = 1;
		int max = Math.max(width, height);

		while (max / sample > targetMax) {
			sample *= 2;
		}

		return Math.max(1, sample);
	}

	private static void showNoResultDialog(Activity activity, int mode) {
		String message = mode == MODE_BANK
				? "Aucune opération n'a pu être lue sur ce screenshot. Assure-toi que l'image est nette et bien cadrée."
				: "Aucun montant n'a pu être détecté sur ce ticket. Réessaie avec une photo plus nette et bien éclairée.";

		LinearLayout content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.VERTICAL);

		TextView tv = UiFactory.bodyMuted(activity, message);
		tv.setTextSize(DS.TEXT_SM);
		content.addView(tv);

		new AppDialog.Builder(activity)
				.icon("⚠️")
				.title("Aucune transaction détectée")
				.subtitle(mode == MODE_BANK ? "Screenshot bancaire" : "Ticket de caisse")
				.content(content)
				.primaryBtn("OK", () -> {
				})
				.show();
	}

	private static View premiumChoiceRow(Activity activity, String title, String subtitle) {
		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.VERTICAL);
		row.setPadding(
				DS.dp(activity, DS.PAD),
				DS.dp(activity, 13),
				DS.dp(activity, DS.PAD),
				DS.dp(activity, 13));
		row.setBackground(UiFactory.bgBordered(
				ThemeColors.card(),
				ThemeColors.border(),
				DS.R_MD,
				activity));
		row.setClickable(true);
		row.setFocusable(true);

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.bottomMargin = DS.dp(activity, DS.GAP_SM);
		row.setLayoutParams(lp);

		TextView titleView = new TextView(activity);
		titleView.setText(title);
		titleView.setTextSize(16f);
		titleView.setTypeface(null, Typeface.BOLD);
		titleView.setTextColor(ThemeColors.text());
		titleView.setGravity(Gravity.CENTER_VERTICAL);
		row.addView(titleView);

		TextView subView = UiFactory.bodyMuted(activity, subtitle);
		subView.setTextSize(DS.TEXT_XS);

		LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
		sp.topMargin = DS.dp(activity, 3);
		row.addView(subView, sp);

		return row;
	}

	private static void dismiss(AlertDialog[] holder) {
		try {
			if (holder != null && holder.length > 0 && holder[0] != null && holder[0].isShowing()) {
				holder[0].dismiss();
			}
		} catch (Exception ignored) {
		}
	}

	private static void notifyError(OnImportComplete callback, String message) {
		if (callback != null) {
			callback.onError(message == null ? "Erreur inconnue" : message);
		}
	}

	private static boolean containsIgnoreCase(List<String> list, String value) {
		if (list == null || value == null) return false;

		for (String s : list) {
			if (s != null && s.equalsIgnoreCase(value)) {
				return true;
			}
		}

		return false;
	}

	private static String guessEmoji(String name) {
		String n = name == null ? "" : name.toLowerCase(java.util.Locale.FRANCE);

		if (n.contains("revenu") || n.contains("salaire") || n.contains("caf")) return "↗️";
		if (n.contains("course") || n.contains("aliment")) return "🛒";
		if (n.contains("loyer") || n.contains("logement")) return "🏠";
		if (n.contains("transport") || n.contains("essence")) return "🚗";
		if (n.contains("restaurant") || n.contains("resto")) return "🍽️";
		if (n.contains("abonnement")) return "📱";
		if (n.contains("sante") || n.contains("santé")) return "💊";
		if (n.contains("banque") || n.contains("frais")) return "🏦";
		if (n.contains("crédit") || n.contains("credit") || n.contains("prêt") || n.contains("pret")) return "🏦";

		return "🏷️";
	}
}