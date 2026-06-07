package com.couplefinance.ui.credits;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.couplefinance.AppToast;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.AppDialog;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.Fmt;
import com.couplefinance.core.ui.UiFactory;
import com.couplefinance.core.ui.animations.PressAnimations;

import java.util.Calendar;
import java.util.Locale;

public final class CreditsDialogs {

	private CreditsDialogs() {
	}

	public interface OnActionDone {
		void reload();
	}

	public static void showAddDialog(Activity activity, OnActionDone callback) {
		showAddDialog(activity, callback, null, 0);
	}

	public static void showAddDialog(Activity activity, OnActionDone callback,
			String prefillName, double prefillMonthly) {
		ScrollView scroll = new ScrollView(activity);
		scroll.setFillViewport(false);
		scroll.setVerticalScrollBarEnabled(true);

		LinearLayout content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.VERTICAL);
		content.setPadding(0, 0, 0, DS.dp(activity, 10));
		scroll.addView(content);

		LinearLayout colNom = AppDialog.fieldColumn(activity, "NOM DU CRÉDIT");
		EditText etName = UiFactory.input(activity, "Ex : Crédit immobilier");
		if (prefillName != null && !prefillName.isEmpty()) etName.setText(prefillName);
		colNom.addView(etName);
		content.addView(colNom);

		LinearLayout row2 = AppDialog.fieldRow(activity);

		LinearLayout colBank = AppDialog.fieldColumn(activity, "BANQUE");
		EditText etBank = UiFactory.input(activity, "Ex : BNP");
		colBank.addView(etBank);
		row2.addView(colBank, new LinearLayout.LayoutParams(0, -2, 1f));

		LinearLayout.LayoutParams r2p2 = new LinearLayout.LayoutParams(0, -2, 1f);
		r2p2.leftMargin = DS.dp(activity, 8);

		LinearLayout colType = AppDialog.fieldColumn(activity, "TYPE");
		String[] typeLabels = CreditsModels.CreditType.allLabels();

		Spinner spinner = new Spinner(activity);
		spinner.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, typeLabels));
		spinner.setBackground(UiFactory.bgBordered(ThemeColors.card(), ThemeColors.border(), DS.R_SM, activity));
		spinner.setLayoutParams(new LinearLayout.LayoutParams(-1, DS.dp(activity, 46)));

		colType.addView(spinner);
		row2.addView(colType, r2p2);
		content.addView(row2);

		LinearLayout row3 = AppDialog.fieldRow(activity);

		LinearLayout colTotal = AppDialog.fieldColumn(activity, "MONTANT TOTAL €");
		EditText etTotal = UiFactory.inputNumeric(activity, "Ex : 200000");
		colTotal.addView(etTotal);
		row3.addView(colTotal, new LinearLayout.LayoutParams(0, -2, 1f));

		LinearLayout.LayoutParams r3p2 = new LinearLayout.LayoutParams(0, -2, 1f);
		r3p2.leftMargin = DS.dp(activity, 8);

		LinearLayout colMonthly = AppDialog.fieldColumn(activity, "MENSUALITÉ €");
		EditText etMonthly = UiFactory.inputNumeric(activity, "Auto");
		if (prefillMonthly > 0) etMonthly.setText(String.format(java.util.Locale.FRANCE, "%.2f", prefillMonthly));
		colMonthly.addView(etMonthly);
		row3.addView(colMonthly, r3p2);

		content.addView(row3);

		LinearLayout row4 = AppDialog.fieldRow(activity);

		LinearLayout colDuration = AppDialog.fieldColumn(activity, "DURÉE (MOIS)");
		EditText etDuration = UiFactory.inputNumeric(activity, "Ex : 240");
		colDuration.addView(etDuration);
		row4.addView(colDuration, new LinearLayout.LayoutParams(0, -2, 1f));

		LinearLayout.LayoutParams r4p2 = new LinearLayout.LayoutParams(0, -2, 1f);
		r4p2.leftMargin = DS.dp(activity, 8);

		LinearLayout colRate = AppDialog.fieldColumn(activity, "TAUX ANNUEL %");
		EditText etRate = UiFactory.inputNumeric(activity, "Ex : 3.5");
		colRate.addView(etRate);
		row4.addView(colRate, r4p2);

		content.addView(row4);

		LinearLayout row5 = AppDialog.fieldRow(activity);

		LinearLayout payerCol = AppDialog.fieldColumn(activity, "PAYÉ PAR");

		java.util.List<String> payersList = new java.util.ArrayList<>();
		payersList.add("Compte joint");

		try {
			com.couplefinance.ui.settings.SettingsModels.State state =
					com.couplefinance.ui.settings.SettingsCache.get();

			if (state != null && state.members != null) {
				for (com.couplefinance.ui.settings.SettingsModels.Member m : state.members) {
					if (m == null || m.name == null || m.name.trim().isEmpty())
						continue;

					addUniqueName(payersList, m.name.trim());
				}
			}
		} catch (Exception ignored) {
		}

		Spinner payerSpinner = new Spinner(activity);
		ArrayAdapter<String> payerAdapter = new ArrayAdapter<>(
				activity,
				android.R.layout.simple_spinner_dropdown_item,
				payersList
		);
		payerSpinner.setAdapter(payerAdapter);
		payerSpinner.setBackground(UiFactory.bgBordered(ThemeColors.card(), ThemeColors.border(), DS.R_SM, activity));
		payerSpinner.setLayoutParams(new LinearLayout.LayoutParams(-1, DS.dp(activity, 46)));

		payerCol.addView(payerSpinner);
		row5.addView(payerCol, new LinearLayout.LayoutParams(0, -2, 1f));

		loadMembersFromFirestore(activity, payersList, payerSpinner);

		LinearLayout.LayoutParams r5p2 = new LinearLayout.LayoutParams(0, -2, 1f);
		r5p2.leftMargin = DS.dp(activity, 8);

		LinearLayout paymentDayCol = AppDialog.fieldColumn(activity, "JOUR PRÉLÈVEMENT");
		EditText etPaymentDay = UiFactory.inputNumeric(activity, "Ex : 5");
		etPaymentDay.setText("5");
		paymentDayCol.addView(etPaymentDay);
		row5.addView(paymentDayCol, r5p2);

		content.addView(row5);

		final long[] startDateMs = { System.currentTimeMillis() };
		final Calendar startCal = Calendar.getInstance();

		LinearLayout colDate = AppDialog.fieldColumn(activity, "DATE DE DÉBUT");
		Button btnDate = UiFactory.btnSecondary(activity, Fmt.dateShort(startDateMs[0]));
		btnDate.setLayoutParams(new LinearLayout.LayoutParams(-1, DS.dp(activity, 44)));
		btnDate.setTextColor(ThemeColors.primary());
		btnDate.setAllCaps(false);
		PressAnimations.apply(btnDate);

		colDate.addView(btnDate);
		content.addView(colDate);

		TextView tvAuto = new TextView(activity);
		tvAuto.setText("Mensualité calculée automatiquement avec le montant, la durée et le taux.");
		tvAuto.setTextColor(ThemeColors.subtext());
		tvAuto.setTextSize(11f);
		tvAuto.setTypeface(null, Typeface.BOLD);
		tvAuto.setPadding(DS.dp(activity, 2), DS.dp(activity, 4), DS.dp(activity, 2), 0);
		content.addView(tvAuto);

		final boolean[] updatingMonthly = { false };

		Runnable recomputeMonthly = () -> {
			if (updatingMonthly[0])
				return;

			double total = parseInput(etTotal.getText().toString(), 0);
			int duration = (int) parseInput(etDuration.getText().toString(), 0);
			double annualRate = parseInput(etRate.getText().toString(), 0);

			if (total <= 0 || duration <= 0)
				return;

			double monthly = computeMonthlyPayment(total, annualRate, duration);

			if (monthly > 0) {
				String value = String.format(Locale.FRANCE, "%.2f", monthly);

				if (!value.equals(etMonthly.getText().toString())) {
					updatingMonthly[0] = true;
					etMonthly.setText(value);
					etMonthly.setSelection(etMonthly.getText().length());
					updatingMonthly[0] = false;
				}
			}
		};

		TextWatcher autoWatcher = new TextWatcher() {
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			public void afterTextChanged(Editable s) {
				recomputeMonthly.run();
			}
		};

		etTotal.addTextChangedListener(autoWatcher);
		etDuration.addTextChangedListener(autoWatcher);
		etRate.addTextChangedListener(autoWatcher);

		btnDate.setOnClickListener(v -> {
			DatePickerDialog dpd = new DatePickerDialog(activity, (view, year, month, day) -> {
				startCal.set(year, month, day, 1, 0, 0);
				startCal.set(Calendar.MILLISECOND, 0);

				startDateMs[0] = startCal.getTimeInMillis();

				btnDate.setText(Fmt.dateShort(startDateMs[0]));
				btnDate.setBackground(UiFactory.bg(ThemeColors.infoBackground(), DS.R_SM, activity));
				btnDate.setTextColor(ThemeColors.info());

				recomputeMonthly.run();
			}, startCal.get(Calendar.YEAR), startCal.get(Calendar.MONTH), startCal.get(Calendar.DAY_OF_MONTH));

			dpd.show();
		});

		final AlertDialog[] dialogRef = new AlertDialog[1];

		dialogRef[0] = new AppDialog.Builder(activity)
				.icon("🏦")
				.title("Nouveau crédit")
				.subtitle("Ajoutez un crédit au foyer.")
				.content(scroll)
				.primaryBtn("CRÉER", () -> {
					String name = etName.getText().toString().trim();
					String bank = etBank.getText().toString().trim();

					String type = spinner.getSelectedItem() != null
							? spinner.getSelectedItem().toString()
							: "Autre";

					double total = parseInput(etTotal.getText().toString(), 0);
					double monthly = parseInput(etMonthly.getText().toString(), 0);
					int duration = (int) parseInput(etDuration.getText().toString(), 0);
					double rate = parseInput(etRate.getText().toString(), 0);

					String paidBy = payerSpinner.getSelectedItem() != null
							? payerSpinner.getSelectedItem().toString()
							: "Compte joint";

					int paymentDay = (int) parseInput(etPaymentDay.getText().toString(), 5);

					if (paymentDay < 1)
						paymentDay = 1;

					if (paymentDay > 28)
						paymentDay = 28;

					if (name.isEmpty()) {
						AppToast.error(activity, "Nom obligatoire");
						return;
					}

					if (total <= 0) {
						AppToast.error(activity, "Montant invalide");
						return;
					}

					if (monthly <= 0) {
						AppToast.error(activity, "Mensualité invalide");
						return;
					}

					if (duration <= 0) {
						AppToast.error(activity, "Durée invalide");
						return;
					}

					String emoji = CreditsModels.emojiForName(name);
					String compte = paidBy.equalsIgnoreCase("Compte joint") ? "joint" : "perso";

					CreditsRepository.addCredit(
							name,
							total,
							monthly,
							startDateMs[0],
							duration,
							emoji,
							bank,
							type,
							rate,
							paidBy,
							compte,
							paymentDay,
							activity,
							new CreditsRepository.OnWriteComplete() {
								@Override
								public void onSuccess() {
									AppToast.success(activity, "Crédit ajouté");

									if (dialogRef[0] != null) {
										dialogRef[0].dismiss();
									}

									if (callback != null) {
										callback.reload();
									}
								}

								@Override
								public void onError(String e) {
									AppToast.error(activity, e == null ? "Erreur" : e);
								}
							}
					);
				})
				.build();

		dialogRef[0].show();
	}

	/**
	 * Ouvre le dialog d'ajout de crédit pré-rempli à partir d'une transaction importée.
	 * Pré-remplit le nom (label de la transaction) et la mensualité (montant).
	 */
	public static void showAddDialogFromTransaction(Activity activity,
			com.couplefinance.ui.transactions.TransactionsModels.Transaction tx,
			Runnable onDone) {
		showAddDialog(activity, new OnActionDone() {
			@Override public void reload() { if (onDone != null) onDone.run(); }
		}, tx.label, Math.abs(tx.amount));
	}

	public static void showDeleteDialog(Activity activity, CreditsModels.Credit credit, OnActionDone callback) {
		LinearLayout info = AppDialog.infoCard(activity);

		TextView tvInfo = new TextView(activity);
		tvInfo.setText(
				credit.emoji + "  " + credit.name
						+ "\nCapital restant estimé : "
						+ Fmt.money(CreditsCalculator.computeRemaining(credit))
		);
		tvInfo.setTextColor(ThemeColors.text());
		tvInfo.setTextSize(DS.TEXT_BODY);
		tvInfo.setTypeface(null, Typeface.BOLD);

		info.addView(tvInfo);

		final AlertDialog[] dialogRef = new AlertDialog[1];

		dialogRef[0] = new AppDialog.Builder(activity)
				.icon("🗑️")
				.title("Supprimer le crédit")
				.subtitle("Cette action supprime uniquement ce crédit du suivi.")
				.content(info)
				.primaryBtn("SUPPRIMER", () -> CreditsRepository.deleteCredit(
						credit.docId,
						activity,
						new CreditsRepository.OnWriteComplete() {
							@Override
							public void onSuccess() {
								AppToast.success(activity, "Crédit supprimé");

								if (dialogRef[0] != null) {
									dialogRef[0].dismiss();
								}

								if (callback != null) {
									callback.reload();
								}
							}

							@Override
							public void onError(String e) {
								AppToast.error(activity, "Erreur : " + e);
							}
						}
				))
				.build();

		dialogRef[0].show();
	}

	private static void loadMembersFromFirestore(
			Activity activity,
			java.util.List<String> payersList,
			Spinner payerSpinner
	) {
		try {
			com.couplefinance.data.HouseholdManager.getInstance().getMembers(
					new com.couplefinance.data.FirestoreManager.Callback() {
						public void onSuccess(String response) {
							activity.runOnUiThread(() -> {
								try {
									String[] parts = response.split("\"fields\":");

									for (int i = 1; i < parts.length; i++) {
										if (!parts[i].contains("\"name\""))
											continue;

										String memberName = extractFirestoreString(
												parts[i].substring(parts[i].indexOf("\"name\"")),
												"stringValue"
										);

										if (memberName == null || memberName.trim().isEmpty())
											continue;

										memberName = memberName.trim();

										if (memberName.equalsIgnoreCase("Moi")
												|| memberName.equalsIgnoreCase("null"))
											continue;

										addUniqueName(payersList, memberName);
									}

									payerSpinner.setAdapter(new ArrayAdapter<>(
											activity,
											android.R.layout.simple_spinner_dropdown_item,
											payersList
									));
								} catch (Exception ignored) {
								}
							});
						}

						public void onError(String error) {
						}
					}
			);
		} catch (Exception ignored) {
		}
	}

	private static void addUniqueName(java.util.List<String> list, String name) {
		if (list == null || name == null || name.trim().isEmpty())
			return;

		String clean = name.trim();

		for (String existing : list) {
			if (existing != null && existing.equalsIgnoreCase(clean))
				return;
		}

		list.add(clean);
	}

	private static double parseInput(String value, double def) {
		try {
			return Double.parseDouble(
					value
							.replace(",", ".")
							.replace("€", "")
							.replace(" ", "")
							.trim()
			);
		} catch (Exception e) {
			return def;
		}
	}

	private static String extractFirestoreString(String json, String key) {
		if (json == null || key == null)
			return "";

		for (String marker : new String[]{
				"\"" + key + "\": \"",
				"\"" + key + "\":\""
		}) {
			int i = json.indexOf(marker);

			if (i >= 0) {
				int start = i + marker.length();
				int end = json.indexOf("\"", start);

				if (end > start)
					return json.substring(start, end).trim();
			}
		}

		return "";
	}

	private static double computeMonthlyPayment(double capital, double annualRate, int months) {
		if (capital <= 0 || months <= 0)
			return 0;

		if (annualRate <= 0)
			return capital / months;

		double monthlyRate = annualRate / 100d / 12d;

		double monthly =
				(capital * monthlyRate)
						/ (1d - Math.pow(1d + monthlyRate, -months));

		return Math.max(0, monthly);
	}
}