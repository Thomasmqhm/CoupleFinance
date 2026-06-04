package com.couplefinance.ui.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.AppToast;
import com.couplefinance.core.theme.ThemeColors;

public class SettingsDialog {

	public interface AmountCallback {
		void onValue(double value);
	}

	public interface TextCallback {
		void onText(String value);
	}

	public interface IntCallback {
		void onValue(int value);
	}

	public interface ConfirmCallback {
		void onConfirm();
	}

	public static void showAmountDialog(Activity activity, String icon, String title, String subtitle,
			double currentValue, AmountCallback callback) {
		showInputDialog(activity, icon, title, subtitle,
				currentValue > 0 ? String.valueOf((int) currentValue) : "",
				"Ex : 2850", true, value -> {
					try {
						double amount = Double.parseDouble(value.replace(",", "."));

						if (amount < 0) {
							AppToast.error(activity, "Montant invalide");
							return;
						}

						if (callback != null)
							callback.onValue(amount);

					} catch (Exception e) {
						AppToast.error(activity, "Montant invalide");
					}
				});
	}

	public static void showDayDialog(Activity activity, int currentDay, IntCallback callback) {
		showInputDialog(activity, "📅", "Jour de prélèvement",
				"Choisissez un jour entre 1 et 28 pour garantir un passage chaque mois.",
				currentDay > 0 ? String.valueOf(currentDay) : "1",
				"Ex : 15", true, value -> {
					try {
						int day = Integer.parseInt(value.trim());

						if (day < 1 || day > 28) {
							AppToast.error(activity, "Jour entre 1 et 28");
							return;
						}

						if (callback != null)
							callback.onValue(day);

					} catch (Exception e) {
						AppToast.error(activity, "Jour invalide");
					}
				});
	}

	public static void showTextDialog(Activity activity, String icon, String title, String subtitle,
			String currentValue, String hint, TextCallback callback) {
		showInputDialog(activity, icon, title, subtitle, currentValue, hint, false, value -> {
			if (value == null || value.trim().isEmpty()) {
				AppToast.error(activity, "Champ manquant");
				return;
			}

			if (callback != null)
				callback.onText(value.trim());
		});
	}

	private static void showInputDialog(Activity activity, String icon, String title, String subtitle,
			String currentValue, String hint, boolean number, TextCallback callback) {
		SettingsStyles.syncWithGlobalTheme();

		LinearLayout layout = baseDialogLayout(activity);

		layout.addView(dialogIcon(activity, icon));
		layout.addView(dialogTitle(activity, title), withTopMargin(activity, 18));
		layout.addView(dialogSubtitle(activity, subtitle), withTopMargin(activity, 8));

		EditText input = new EditText(activity);
		input.setText(currentValue == null ? "" : currentValue);
		input.setHint(hint);
		input.setTextColor(SettingsStyles.text());
		input.setHintTextColor(SettingsStyles.subtext());
		input.setTextSize(18);
		input.setSingleLine(true);

		if (number) {
			input.setInputType(InputType.TYPE_CLASS_NUMBER
					| InputType.TYPE_NUMBER_FLAG_DECIMAL
					| InputType.TYPE_NUMBER_FLAG_SIGNED);
		} else {
			input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
		}

		input.setPadding(SettingsStyles.dp(activity, 16), 0, SettingsStyles.dp(activity, 16), 0);
		input.setBackground(SettingsStyles.secondaryButton());

		LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				SettingsStyles.dp(activity, 58)
		);
		inputParams.topMargin = SettingsStyles.dp(activity, 20);
		layout.addView(input, inputParams);

		LinearLayout buttons = buttonsRow(activity);
		TextView cancel = button(activity, "Annuler", false);
		TextView save = button(activity, "Enregistrer", true);

		addButtons(activity, buttons, cancel, save);
		layout.addView(buttons, withTopMargin(activity, 24));

		AlertDialog dialog = new AlertDialog.Builder(activity).setView(layout).create();

		cancel.setOnClickListener(v -> dialog.dismiss());

		save.setOnClickListener(v -> {
			String value = input.getText().toString().trim();

			if (value.isEmpty()) {
				AppToast.error(activity, "Champ manquant");
				return;
			}

			dialog.dismiss();

			if (callback != null)
				callback.onText(value);
		});

		showDialog(activity, dialog);
	}

	public static void showConfirmDialog(Activity activity, String icon, String title, String subtitle,
			String confirmText, ConfirmCallback callback) {
		SettingsStyles.syncWithGlobalTheme();

		LinearLayout layout = baseDialogLayout(activity);

		layout.addView(dialogIcon(activity, icon));
		layout.addView(dialogTitle(activity, title), withTopMargin(activity, 18));
		layout.addView(dialogSubtitle(activity, subtitle), withTopMargin(activity, 8));

		LinearLayout buttons = buttonsRow(activity);
		TextView cancel = button(activity, "Annuler", false);
		TextView confirm = button(activity, confirmText, true);

		addButtons(activity, buttons, cancel, confirm);
		layout.addView(buttons, withTopMargin(activity, 24));

		AlertDialog dialog = new AlertDialog.Builder(activity).setView(layout).create();

		cancel.setOnClickListener(v -> dialog.dismiss());

		confirm.setOnClickListener(v -> {
			dialog.dismiss();

			if (callback != null)
				callback.onConfirm();
		});

		showDialog(activity, dialog);
	}

	private static LinearLayout baseDialogLayout(Activity activity) {
		LinearLayout layout = new LinearLayout(activity);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setPadding(
				SettingsStyles.dp(activity, 26),
				SettingsStyles.dp(activity, 24),
				SettingsStyles.dp(activity, 26),
				SettingsStyles.dp(activity, 22)
		);

		GradientDrawable bg = new GradientDrawable();
		bg.setColor(ThemeColors.modal());
		bg.setCornerRadius(SettingsStyles.dp(activity, 26));
		bg.setStroke(SettingsStyles.dp(activity, 1), ThemeColors.border());

		layout.setBackground(bg);

		return layout;
	}

	private static TextView dialogIcon(Activity activity, String icon) {
		TextView iconView = new TextView(activity);
		iconView.setText(icon);
		iconView.setTextSize(24);
		iconView.setGravity(Gravity.CENTER);
		iconView.setBackground(SettingsStyles.secondaryButton());

		iconView.setLayoutParams(new LinearLayout.LayoutParams(
				SettingsStyles.dp(activity, 54),
				SettingsStyles.dp(activity, 54)
		));

		return iconView;
	}

	private static TextView dialogTitle(Activity activity, String title) {
		TextView tvTitle = new TextView(activity);
		tvTitle.setText(title);
		tvTitle.setTextColor(SettingsStyles.text());
		tvTitle.setTextSize(24);
		tvTitle.setTypeface(Typeface.DEFAULT_BOLD);

		return tvTitle;
	}

	private static TextView dialogSubtitle(Activity activity, String subtitle) {
		TextView tvSub = new TextView(activity);
		tvSub.setText(subtitle);
		tvSub.setTextColor(SettingsStyles.subtext());
		tvSub.setTextSize(14);

		return tvSub;
	}

	private static LinearLayout buttonsRow(Activity activity) {
		LinearLayout buttons = new LinearLayout(activity);
		buttons.setOrientation(LinearLayout.HORIZONTAL);
		return buttons;
	}

	private static void addButtons(Activity activity, LinearLayout buttons, TextView left, TextView right) {
		LinearLayout.LayoutParams leftParams =
				new LinearLayout.LayoutParams(0, SettingsStyles.dp(activity, 52), 1f);
		leftParams.rightMargin = SettingsStyles.dp(activity, 10);

		buttons.addView(left, leftParams);
		buttons.addView(right, new LinearLayout.LayoutParams(0, SettingsStyles.dp(activity, 52), 1f));
	}

	private static LinearLayout.LayoutParams withTopMargin(Activity activity, int dp) {
		LinearLayout.LayoutParams params = SettingsStyles.matchWrap();
		params.topMargin = SettingsStyles.dp(activity, dp);
		return params;
	}

	private static void showDialog(Activity activity, AlertDialog dialog) {
		dialog.show();

		if (dialog.getWindow() != null) {
			dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
			dialog.getWindow().setLayout(
					(int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f),
					ViewGroup.LayoutParams.WRAP_CONTENT
			);
		}
	}

	private static TextView button(Activity activity, String text, boolean primary) {
		TextView btn = new TextView(activity);
		btn.setText(text);
		btn.setGravity(Gravity.CENTER);
		btn.setTypeface(Typeface.DEFAULT_BOLD);
		btn.setTextSize(15);
		btn.setTextColor(primary ? Color.WHITE : SettingsStyles.text());
		btn.setBackground(primary ? SettingsStyles.primaryButton() : SettingsStyles.secondaryButton());
		return btn;
	}
}