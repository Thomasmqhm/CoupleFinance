package com.couplefinance.ui.virements;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.couplefinance.AppToast;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.AppDialog;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.UiFactory;
import com.couplefinance.data.JointAccountManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class VirementDialogs {

	public interface BeneficiaryCallback {
		void onSave(String name, String iban);
	}

	public interface TransferCallback {
		void onSave(String from, String to, double amount, String motif, long dateMs, boolean fromIsHousehold,
				boolean toIsHousehold);
	}

	private VirementDialogs() {
	}

	public static void showAddBeneficiaryDialog(Activity activity, BeneficiaryCallback callback) {
		LinearLayout form = formRoot(activity);

		EditText etName = UiFactory.input(activity, "Nom du bénéficiaire");
		EditText etIban = UiFactory.input(activity, "IBAN");

		addWithGap(form, etName, activity);
		addWithGap(form, etIban, activity);

		new AppDialog.Builder(activity)
				.icon("+")
				.title("Nouveau bénéficiaire")
				.subtitle("Ajoute un compte externe pour le retrouver rapidement lors d'un virement.")
				.content(form)
				.primaryBtn("AJOUTER", () -> callback.onSave(
						etName.getText().toString().trim(),
						etIban.getText().toString().trim()
				))
				.show();
	}

	public static void showAddTransferDialog(Activity activity, List<String> members,
			List<VirementModels.Beneficiary> beneficiaries, TransferCallback callback) {

		try {
			JointAccountManager.getInstance().init(activity);
		} catch (Exception ignored) {
		}

		LinearLayout form = formRoot(activity);

		ArrayList<String> values = new ArrayList<>();
		ArrayList<Boolean> internals = new ArrayList<>();

		for (String member : members) {
			if (member == null || member.trim().isEmpty())
				continue;

			addUnique(values, internals, member.trim(), true);
		}

		try {
			JointAccountManager jm = JointAccountManager.getInstance();

			if (jm.isEnabledLocal()) {
				addUnique(values, internals, jm.getNameLocal(), true);
			}
		} catch (Exception ignored) {
		}

		if (beneficiaries != null) {
			for (VirementModels.Beneficiary beneficiary : beneficiaries) {
				if (beneficiary != null && beneficiary.name != null && !beneficiary.name.trim().isEmpty()) {
					addUnique(values, internals, beneficiary.name.trim(), false);
				}
			}
		}

		if (values.isEmpty()) {
			String me = "";
			try { me = com.couplefinance.UserSession.getInstance().getNameOrFallback(); } catch (Exception ignored) {}
			if (me == null || me.trim().isEmpty() || me.contains("@")) {
				try { me = com.couplefinance.AuthManager.getInstance().getDisplayName(); } catch (Exception ignored) {}
			}
			if (me == null || me.trim().isEmpty() || me.contains("@")) me = "Moi";
			values.add(me.trim());
			internals.add(true);
		}

		TextView tvFrom = UiFactory.bodyMuted(activity, "Depuis");
		Spinner spFrom = spinner(activity, values);

		TextView tvTo = UiFactory.bodyMuted(activity, "Vers");
		Spinner spTo = spinner(activity, values);
		if (values.size() > 1) {
			spTo.setSelection(1);
		}

		EditText etAmount = UiFactory.inputNumeric(activity, "Montant");
		etAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

		EditText etMotif = UiFactory.input(activity, "Motif");

		// Sélecteur de date du virement : indispensable pour que les comptes
		// rattachent le mouvement au bon jour (sinon date du jour imposée,
		// ce qui fausse les soldes de début de mois).
		TextView tvDate = UiFactory.bodyMuted(activity, "Date du virement");

		final long[] selectedDate = { System.currentTimeMillis() };
		final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE);

		final TextView dateButton = new TextView(activity);
		dateButton.setText("📅  " + dateFormat.format(new Date(selectedDate[0])));
		dateButton.setTextColor(ThemeColors.text());
		dateButton.setGravity(Gravity.CENTER_VERTICAL);
		dateButton.setSingleLine(true);
		dateButton.setPadding(DS.dp(activity, 10), 0, DS.dp(activity, 10), 0);
		dateButton.setBackground(UiFactory.bgBordered(DS.CARD, DS.BORDER, DS.R_SM, activity));
		dateButton.setLayoutParams(new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, DS.dp(activity, DS.INPUT_HEIGHT)));
		dateButton.setOnClickListener(v -> {
			Calendar c = Calendar.getInstance();
			c.setTimeInMillis(selectedDate[0]);
			new DatePickerDialog(
					activity,
					(picker, year, month, day) -> {
						Calendar picked = Calendar.getInstance();
						picked.clear();
						picked.set(year, month, day, 12, 0, 0);
						selectedDate[0] = picked.getTimeInMillis();
						dateButton.setText("📅  " + dateFormat.format(new Date(selectedDate[0])));
					},
					c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
			).show();
		});

		addWithSmallGap(form, tvFrom, activity);
		addWithGap(form, spFrom, activity);
		addWithSmallGap(form, tvTo, activity);
		addWithGap(form, spTo, activity);
		addWithGap(form, etAmount, activity);
		addWithGap(form, etMotif, activity);
		addWithSmallGap(form, tvDate, activity);
		addWithGap(form, dateButton, activity);

		new AppDialog.Builder(activity)
				.icon("↗")
				.title("Nouveau virement")
				.subtitle("Le virement créera automatiquement les transactions liées si un membre du foyer est concerné.")
				.content(form)
				.primaryBtn("VALIDER", () -> {
					int fromIndex = Math.max(0, spFrom.getSelectedItemPosition());
					int toIndex = Math.max(0, spTo.getSelectedItemPosition());

					if (fromIndex >= values.size())
						fromIndex = 0;
					if (toIndex >= values.size())
						toIndex = 0;

					String from = values.get(fromIndex);
					String to = values.get(toIndex);
					boolean fromInternal = internals.get(fromIndex);
					boolean toInternal = internals.get(toIndex);

					if (from.equalsIgnoreCase(to)) {
						AppToast.error(activity, "Choisissez deux comptes différents");
						return;
					}

					double amount;
					try {
						amount = Double.parseDouble(etAmount.getText().toString().trim().replace(',', '.'));
					} catch (Exception e) {
						amount = 0;
					}

					if (amount <= 0) {
						AppToast.error(activity, "Montant invalide");
						return;
					}

					callback.onSave(
							from,
							to,
							amount,
							etMotif.getText().toString().trim(),
							selectedDate[0],
							fromInternal,
							toInternal
					);
				})
				.show();
	}

	public static void showDeleteBeneDialog(Activity activity, VirementModels.Beneficiary bene, Runnable callback) {
		AppDialog.confirm(
				activity,
				"Supprimer le bénéficiaire ?",
				"Le bénéficiaire " + safe(bene.name) + " sera retiré de la liste.",
				"SUPPRIMER",
				callback
		);
	}

	public static void showDeleteTransferDialog(Activity activity, VirementModels.Transfer transfer, Runnable callback) {
		AppDialog.confirm(
				activity,
				"Supprimer le virement ?",
				safe(transfer.from) + " → " + safe(transfer.to),
				"SUPPRIMER",
				callback
		);
	}

	private static LinearLayout formRoot(Activity activity) {
		LinearLayout form = new LinearLayout(activity);
		form.setOrientation(LinearLayout.VERTICAL);
		form.setPadding(0, DS.dp(activity, DS.GAP_SM), 0, 0);
		return form;
	}

	private static Spinner spinner(Activity activity, ArrayList<String> values) {
		Spinner spinner = new Spinner(activity);
		ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, values);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
		spinner.setBackground(UiFactory.bgBordered(DS.CARD, DS.BORDER, DS.R_SM, activity));
		spinner.setPadding(DS.dp(activity, 10), 0, DS.dp(activity, 10), 0);
		spinner.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				DS.dp(activity, DS.INPUT_HEIGHT)));
		return spinner;
	}

	private static void addUnique(ArrayList<String> values, ArrayList<Boolean> internals, String value,
			boolean internal) {
		if (value == null || value.trim().isEmpty())
			return;

		String clean = value.trim();

		for (String existing : values) {
			if (existing != null && existing.equalsIgnoreCase(clean)) {
				return;
			}
		}

		values.add(clean);
		internals.add(internal);
	}

	private static void addWithGap(LinearLayout root, android.view.View child, Activity activity) {
		LinearLayout.LayoutParams current = child.getLayoutParams() instanceof LinearLayout.LayoutParams
				? (LinearLayout.LayoutParams) child.getLayoutParams()
				: null;

		int height = current != null ? current.height : ViewGroup.LayoutParams.WRAP_CONTENT;

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				height
		);
		lp.bottomMargin = DS.dp(activity, DS.GAP_SM);
		root.addView(child, lp);
	}

	private static void addWithSmallGap(LinearLayout root, android.view.View child, Activity activity) {
		LinearLayout.LayoutParams current = child.getLayoutParams() instanceof LinearLayout.LayoutParams
				? (LinearLayout.LayoutParams) child.getLayoutParams()
				: null;

		int height = current != null ? current.height : ViewGroup.LayoutParams.WRAP_CONTENT;

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				height
		);
		lp.bottomMargin = DS.dp(activity, 4);
		root.addView(child, lp);
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}
}