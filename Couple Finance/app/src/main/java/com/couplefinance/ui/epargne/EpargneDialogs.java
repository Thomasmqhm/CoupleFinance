package com.couplefinance.ui.epargne;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.AppToast;
import com.couplefinance.data.TelegramManager;
import com.couplefinance.UserSession;
import com.couplefinance.data.FirestoreManager;
import com.couplefinance.data.TransactionManager;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.AppDialog;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.Fmt;
import com.couplefinance.core.ui.UiFactory;
import com.couplefinance.core.ui.animations.PressAnimations;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class EpargneDialogs {

	private EpargneDialogs() {
	}

	public interface OnActionDone {
		void reload();
	}

	public static void showAddDialog(Activity activity, OnActionDone callback) {
		LinearLayout content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.VERTICAL);

		LinearLayout colNom = AppDialog.fieldColumn(activity, "NOM DE L'OBJECTIF");
		EditText etName = UiFactory.input(activity, "Ex : Voyage Japon, Voiture...");
		colNom.addView(etName);
		content.addView(colNom);

		LinearLayout row2 = AppDialog.fieldRow(activity);

		LinearLayout colTarget = AppDialog.fieldColumn(activity, "MONTANT CIBLE €");
		EditText etTarget = UiFactory.inputNumeric(activity, "Ex : 3 000");
		colTarget.addView(etTarget);
		row2.addView(colTarget, new LinearLayout.LayoutParams(0, -2, 1f));

		LinearLayout.LayoutParams c2lp = new LinearLayout.LayoutParams(0, -2, 1f);
		c2lp.leftMargin = DS.dp(activity, DS.GAP);

		LinearLayout colCurrent = AppDialog.fieldColumn(activity, "DÉJÀ ÉPARGNÉ €");
		EditText etCurrent = UiFactory.inputNumeric(activity, "0");
		colCurrent.addView(etCurrent);
		row2.addView(colCurrent, c2lp);

		content.addView(row2);

		final long[] selectedDateMs = { 0L };
		final Calendar cal = Calendar.getInstance();

		Button btnDate = UiFactory.btnSecondary(activity, "Date cible facultative");
		btnDate.setTextColor(ThemeColors.primary());
		btnDate.setAllCaps(false);
		PressAnimations.apply(btnDate);

		LinearLayout.LayoutParams dateLp =
				new LinearLayout.LayoutParams(-1, DS.dp(activity, DS.BTN_HEIGHT));
		dateLp.topMargin = DS.dp(activity, DS.GAP);
		btnDate.setLayoutParams(dateLp);

		content.addView(btnDate);

		LinearLayout previewCard = buildPreviewCard(activity);
		previewCard.setVisibility(View.GONE);
		content.addView(previewCard);

		TextView tvPreviewBody = previewCard.findViewWithTag("body");

		Runnable updatePreview = () -> {
			double t = parseInput(etTarget.getText().toString(), 0);
			double c = parseInput(etCurrent.getText().toString(), 0);
			double rem = Math.max(0, t - c);

			if (rem <= 0 || t <= 0) {
				previewCard.setVisibility(View.GONE);
				return;
			}

			previewCard.setVisibility(View.VISIBLE);

			if (selectedDateMs[0] > System.currentTimeMillis()) {
				int months = EpargneCalculator.monthsBetween(System.currentTimeMillis(), selectedDateMs[0]);
				if (months <= 0) months = 1;

				double monthly = Math.ceil(rem / months);
				String dateStr = new SimpleDateFormat("dd MMM yyyy", Locale.FRANCE)
						.format(new Date(selectedDateMs[0]));

				if (tvPreviewBody != null) {
					tvPreviewBody.setText(
							"Pour avoir " + Fmt.money(t) + " le " + capitalize(dateStr)
									+ "\n→ " + Fmt.money(monthly) + "/mois pendant " + months + " mois."
					);
				}
			} else {
				double monthly12 = Math.ceil(rem / 12.0);

				if (tvPreviewBody != null) {
					tvPreviewBody.setText(
							"Sans date cible → ~" + Fmt.money(monthly12) + "/mois sur 12 mois."
									+ "\nChoisissez une date pour un plan précis."
					);
				}
			}
		};

		btnDate.setOnClickListener(v -> {
			DatePickerDialog dpd = new DatePickerDialog(
					activity,
					(view, year, month, day) -> {
						cal.set(year, month, day, 0, 0, 0);
						cal.set(Calendar.MILLISECOND, 0);

						selectedDateMs[0] = cal.getTimeInMillis();

						String label = capitalize(
								new SimpleDateFormat("dd MMM yyyy", Locale.FRANCE).format(cal.getTime())
						);

						btnDate.setText(label);
						btnDate.setBackground(UiFactory.bg(
								ThemeColors.successBackground(),
								DS.R_SM,
								activity
						));
						btnDate.setTextColor(ThemeColors.success());

						updatePreview.run();
					},
					cal.get(Calendar.YEAR),
					cal.get(Calendar.MONTH),
					cal.get(Calendar.DAY_OF_MONTH)
			);

			dpd.getDatePicker().setMinDate(System.currentTimeMillis() + 86_400_000L);
			dpd.show();
		});

		TextWatcher tw = simpleTextWatcher(updatePreview);
		etTarget.addTextChangedListener(tw);
		etCurrent.addTextChangedListener(tw);

		final AlertDialog[] dialogRef = new AlertDialog[1];

		dialogRef[0] = new AppDialog.Builder(activity)
				.icon("💰")
				.title("Nouvel objectif")
				.subtitle("Ajoutez un objectif d'épargne commun au foyer.")
				.content(content)
				.primaryBtn("CRÉER", () -> {
					String name = etName.getText().toString().trim();

					if (name.isEmpty()) {
						AppToast.error(activity, "Nom requis");
						return;
					}

					double target = parseInput(etTarget.getText().toString(), -1);
					double current = parseInput(etCurrent.getText().toString(), 0);

					if (target <= 0) {
						AppToast.error(activity, "Montant cible invalide");
						return;
					}

					EpargneRepository.addGoal(
							name,
							target,
							current,
							EpargneModels.autoEmoji(name),
							EpargneModels.autoColor(name),
							selectedDateMs[0],
							activity,
							new EpargneRepository.OnWriteComplete() {
								@Override
								public void onSuccess() {
									AppToast.success(activity, "Objectif créé");

									dismissThenReload(dialogRef[0], callback);
								}

								@Override
								public void onError(String e) {
									AppToast.error(activity, "Erreur : " + e);
								}
							}
					);
				})
				.build();

		dialogRef[0].show();
	}

	public static void showDepositDialog(Activity activity, EpargneModels.SavingsGoal goal, OnActionDone callback) {
		LinearLayout content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.VERTICAL);

		content.addView(buildGoalSummaryCard(activity, goal));
		content.addView(buildRemainingBanner(activity, goal));

		if (goal.hasDate()) {
			int months = EpargneCalculator.monthsLeft(goal);

			if (months > 0 && goal.remaining() > 0) {
				double monthly = Math.ceil(goal.remaining() / months);

				TextView reminder = buildInfoText(
						activity,
						"Pour atteindre votre date cible → " + Fmt.money(monthly) + "/mois",
						ThemeColors.successBackground(),
						ThemeColors.success()
				);

				LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
				rp.bottomMargin = DS.dp(activity, DS.GAP_SM);
				reminder.setLayoutParams(rp);

				content.addView(reminder);
			}
		}

		LinearLayout colAmount = AppDialog.fieldColumn(activity, "MONTANT À VERSER €");
		LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
		clp.topMargin = DS.dp(activity, DS.GAP);
		colAmount.setLayoutParams(clp);

		EditText etAmount = UiFactory.inputNumeric(
				activity,
				goal.remaining() > 0 ? "Max : " + Fmt.money(goal.remaining()) : "Montant (€)"
		);

		colAmount.addView(etAmount);
		content.addView(colAmount);

		TextView tvFeedback = new TextView(activity);
		tvFeedback.setTextSize(DS.TEXT_SM);
		tvFeedback.setPadding(
				DS.dp(activity, 4),
				DS.dp(activity, 2),
				DS.dp(activity, 4),
				DS.dp(activity, 6)
		);
		tvFeedback.setVisibility(View.GONE);
		content.addView(tvFeedback);

		etAmount.addTextChangedListener(new TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
			@Override public void afterTextChanged(Editable s) {}

			@Override
			public void onTextChanged(CharSequence s, int a, int b, int c) {
				double entered = parseInput(s.toString(), 0);

				if (entered <= 0) {
					tvFeedback.setVisibility(View.GONE);
					return;
				}

				tvFeedback.setVisibility(View.VISIBLE);

				double after = goal.current + entered;

				if (after > goal.target) {
					tvFeedback.setText("Dépassement — versement limité à " + Fmt.money(goal.remaining()));
					tvFeedback.setTextColor(ThemeColors.danger());
				} else if (Math.abs(after - goal.target) < 0.01) {
					tvFeedback.setText("Objectif atteint avec ce versement");
					tvFeedback.setTextColor(ThemeColors.success());
				} else {
					tvFeedback.setText(
							"Après versement : " + Fmt.money(after)
									+ " — reste " + Fmt.money(goal.target - after)
					);
					tvFeedback.setTextColor(ThemeColors.subtext());
				}
			}
		});

		final AlertDialog[] dialogRef = new AlertDialog[1];

		dialogRef[0] = new AppDialog.Builder(activity)
				.icon(goal.emoji)
				.title("Verser sur l'objectif")
				.content(content)
				.primaryBtn("VERSER", () -> {
					double entered = parseInput(etAmount.getText().toString(), -1);

					if (entered <= 0) {
						AppToast.error(activity, "Montant invalide");
						return;
					}

					double actual = goal.remaining() > 0 ? Math.min(entered, goal.remaining()) : entered;
					double newCurrent = Math.min(
							goal.current + actual,
							goal.target > 0 ? goal.target : Double.MAX_VALUE
					);

					EpargneRepository.updateGoalCurrent(
							goal.docId,
							newCurrent,
							activity,
							new EpargneRepository.OnWriteComplete() {
								@Override
								public void onSuccess() {
										recordSavingDepositTransaction(activity, goal.name, actual);

									boolean goalReached = newCurrent >= goal.target && goal.target > 0;
									AppToast.success(activity, goalReached ? "Objectif atteint !" : "Versement de " + Fmt.money(actual) + " ajouté");
									if (goalReached) {
										try {
											String msg = "🎉 <b>Objectif atteint !</b>\n"
													+ goal.emoji + " <b>" + goal.name + "</b>\n"
													+ Fmt.money(goal.target) + " épargnés 🎉";
											TelegramManager.getInstance().sendMessage(msg, null);
										} catch (Exception ignored) {}
										try {
											com.couplefinance.utils.NotificationHelper
													.getInstance(activity)
													.notifySavingsGoalCompleted(goal.name);
										} catch (Exception ignored) {}
									}

									dismissThenReload(dialogRef[0], callback);
								}

								@Override
								public void onError(String e) {
									AppToast.error(activity, "Erreur : " + e);
								}
							}
					);
				})
				.build();

		dialogRef[0].show();
	}

	public static void showWithdrawDialog(Activity activity, EpargneModels.SavingsGoal goal, OnActionDone callback) {
		if (goal.current <= 0) {
			AppToast.info(activity, "Aucun fonds à retirer");
			return;
		}
		LinearLayout content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.VERTICAL);
		content.addView(buildGoalSummaryCard(activity, goal));

		LinearLayout colAmount = AppDialog.fieldColumn(activity, "MONTANT À RETIRER €");
		LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
		clp.topMargin = DS.dp(activity, DS.GAP);
		colAmount.setLayoutParams(clp);
		EditText etAmount = UiFactory.inputNumeric(activity, "Max : " + Fmt.money(goal.current));
		colAmount.addView(etAmount);
		content.addView(colAmount);

		TextView tvFeedback = new TextView(activity);
		tvFeedback.setTextSize(DS.TEXT_SM);
		tvFeedback.setPadding(DS.dp(activity, 4), DS.dp(activity, 2), DS.dp(activity, 4), DS.dp(activity, 6));
		tvFeedback.setVisibility(View.GONE);
		content.addView(tvFeedback);

		etAmount.addTextChangedListener(simpleTextWatcher(() -> {
			double entered = parseInput(etAmount.getText().toString(), 0);
			if (entered <= 0) { tvFeedback.setVisibility(View.GONE); return; }
			tvFeedback.setVisibility(View.VISIBLE);
			double after = goal.current - Math.min(entered, goal.current);
			tvFeedback.setText("Après retrait : " + Fmt.money(after));
			tvFeedback.setTextColor(ThemeColors.subtext());
		}));

		final AlertDialog[] dialogRef = new AlertDialog[1];
		dialogRef[0] = new AppDialog.Builder(activity)
				.icon(goal.emoji)
				.title("Retirer de l'objectif")
				.content(content)
				.primaryBtn("RETIRER", () -> {
					double entered = parseInput(etAmount.getText().toString(), -1);
					if (entered <= 0) { AppToast.error(activity, "Montant invalide"); return; }
					double actual = Math.min(entered, goal.current);
					double newCurrent = Math.max(0, goal.current - actual);
					EpargneRepository.updateGoalCurrent(goal.docId, newCurrent, activity,
							new EpargneRepository.OnWriteComplete() {
								public void onSuccess() {
									AppToast.success(activity, "Retrait de " + Fmt.money(actual) + " effectué");
									dismissThenReload(dialogRef[0], callback);
								}
								public void onError(String e) {
									AppToast.error(activity, "Erreur : " + e);
								}
							});
				})
				.build();
		dialogRef[0].show();
	}

	public static void showEditDialog(Activity activity, EpargneModels.SavingsGoal goal, OnActionDone callback) {
		LinearLayout content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.VERTICAL);

		LinearLayout colNom = AppDialog.fieldColumn(activity, "NOM DE L'OBJECTIF");
		EditText etName = UiFactory.input(activity, "Nom de l'objectif");
		etName.setText(goal.name);
		colNom.addView(etName);
		content.addView(colNom);

		LinearLayout colTarget = AppDialog.fieldColumn(activity, "MONTANT CIBLE €");
		LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
		clp.topMargin = DS.dp(activity, DS.GAP);
		colTarget.setLayoutParams(clp);
		EditText etTarget = UiFactory.inputNumeric(activity, "Montant cible");
		if (goal.target > 0) etTarget.setText(String.valueOf((int) goal.target));
		colTarget.addView(etTarget);
		content.addView(colTarget);

		final long[] selectedDateMs = { goal.targetDateMs };
		final Calendar cal = Calendar.getInstance();
		if (goal.targetDateMs > 0) cal.setTimeInMillis(goal.targetDateMs);

		Button btnDate = UiFactory.btnSecondary(activity, goal.hasDate()
				? capitalize(new java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.FRANCE).format(new java.util.Date(goal.targetDateMs)))
				: "Modifier la date cible");
		btnDate.setTextColor(goal.hasDate() ? ThemeColors.success() : ThemeColors.primary());
		if (goal.hasDate()) btnDate.setBackground(UiFactory.bg(ThemeColors.successBackground(), DS.R_SM, activity));
		btnDate.setAllCaps(false);
		PressAnimations.apply(btnDate);
		LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(-1, DS.dp(activity, DS.BTN_HEIGHT));
		dateLp.topMargin = DS.dp(activity, DS.GAP);
		btnDate.setLayoutParams(dateLp);

		btnDate.setOnClickListener(v -> {
			DatePickerDialog dpd = new DatePickerDialog(activity,
					(view, year, month, day) -> {
						cal.set(year, month, day, 0, 0, 0);
						cal.set(Calendar.MILLISECOND, 0);
						selectedDateMs[0] = cal.getTimeInMillis();
						String label = capitalize(new java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.FRANCE).format(cal.getTime()));
						btnDate.setText(label);
						btnDate.setBackground(UiFactory.bg(ThemeColors.successBackground(), DS.R_SM, activity));
						btnDate.setTextColor(ThemeColors.success());
					},
					cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
			dpd.getDatePicker().setMinDate(System.currentTimeMillis() + 86_400_000L);
			dpd.show();
		});
		content.addView(btnDate);

		final AlertDialog[] dialogRef = new AlertDialog[1];
		dialogRef[0] = new AppDialog.Builder(activity)
				.icon(goal.emoji)
				.title("Modifier l'objectif")
				.content(content)
				.primaryBtn("ENREGISTRER", () -> {
					String name = etName.getText().toString().trim();
					if (name.isEmpty()) { AppToast.error(activity, "Nom requis"); return; }
					double target = parseInput(etTarget.getText().toString(), -1);
					if (target <= 0) { AppToast.error(activity, "Montant cible invalide"); return; }
					EpargneRepository.updateGoalFull(
							goal.docId, name, target,
							EpargneModels.autoEmoji(name), EpargneModels.autoColor(name),
							selectedDateMs[0], activity,
							new EpargneRepository.OnWriteComplete() {
								public void onSuccess() {
									AppToast.success(activity, "Objectif mis à jour");
									dismissThenReload(dialogRef[0], callback);
								}
								public void onError(String e) {
									AppToast.error(activity, "Erreur : " + e);
								}
							});
				})
				.build();
		dialogRef[0].show();
	}

	public static void showOptionsMenu(Activity activity, EpargneModels.SavingsGoal goal, OnActionDone callback) {
		String[] options = { "✏️  Modifier", "↩  Retirer des fonds", "🗑️  Supprimer" };
		new android.app.AlertDialog.Builder(activity)
				.setTitle(goal.emoji + "  " + goal.name)
				.setItems(options, (dialog, which) -> {
					if (which == 0) showEditDialog(activity, goal, callback);
					else if (which == 1) showWithdrawDialog(activity, goal, callback);
					else showDeleteDialog(activity, goal, callback);
				})
				.show();
	}

	public static void showDeleteDialog(Activity activity, EpargneModels.SavingsGoal goal, OnActionDone callback) {
		LinearLayout info = AppDialog.infoCard(activity);

		TextView tvInfo = new TextView(activity);
		tvInfo.setText(
				goal.emoji + "  " + goal.name
						+ "\n" + Fmt.money(goal.current) + " épargnés sur " + Fmt.money(goal.target)
		);
		tvInfo.setTextColor(ThemeColors.text());
		tvInfo.setTextSize(DS.TEXT_BODY);
		tvInfo.setTypeface(null, Typeface.BOLD);

		info.addView(tvInfo);

		final AlertDialog[] dialogRef = new AlertDialog[1];

		dialogRef[0] = new AppDialog.Builder(activity)
				.icon("🗑️")
				.title("Supprimer l'objectif")
				.subtitle("Cette action supprime uniquement le suivi, pas les fonds.")
				.content(info)
				.primaryBtn("SUPPRIMER", () -> EpargneRepository.deleteGoal(
						goal.docId,
						activity,
						new EpargneRepository.OnWriteComplete() {
							@Override
							public void onSuccess() {
								AppToast.success(activity, "Objectif supprimé");

								dismissThenReload(dialogRef[0], callback);
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


	private static void recordSavingDepositTransaction(Activity activity, String goalName, double amount) {
		if (activity == null || amount <= 0) return;

		String person = "";
		try {
			person = UserSession.getInstance().getNameOrFallback();
		} catch (Exception ignored) {}

		if (person == null || person.trim().isEmpty() || person.contains("@")) {
			person = "Moi";
		}

		String cleanGoal = goalName == null || goalName.trim().isEmpty() ? "Versement" : goalName.trim();
		String label = "Épargne · " + cleanGoal;

		TransactionManager.getInstance().addTransactionWithDateAndShared(
				label,
				amount,
				"variable",
				"Épargne",
				System.currentTimeMillis(),
				person.trim(),
				false,
				false,
				new FirestoreManager.Callback() {
					public void onSuccess(String r) {}
					public void onError(String e) {}
				});
	}

	private static void dismissThenReload(final AlertDialog dialog, final OnActionDone callback) {
		if (dialog != null) {
			dialog.dismiss();
		}

		if (callback != null) {
			new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
					callback::reload,
					260
			);
		}
	}

	private static LinearLayout buildPreviewCard(Activity activity) {
		LinearLayout card = new LinearLayout(activity);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setPadding(
				DS.dp(activity, DS.PAD_INPUT),
				DS.dp(activity, DS.GAP_SM),
				DS.dp(activity, DS.PAD_INPUT),
				DS.dp(activity, DS.GAP_SM)
		);

		card.setBackground(UiFactory.bgBordered(
				ThemeColors.successBackground(),
				ThemeColors.success(),
				DS.R_SM,
				activity
		));

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.topMargin = DS.dp(activity, DS.GAP_SM);
		lp.bottomMargin = DS.dp(activity, 4);
		card.setLayoutParams(lp);

		TextView tvTitle = new TextView(activity);
		tvTitle.setText("Votre plan d'épargne");
		tvTitle.setTextColor(ThemeColors.success());
		tvTitle.setTextSize(DS.TEXT_SM);
		tvTitle.setTypeface(null, Typeface.BOLD);

		TextView tvBody = new TextView(activity);
		tvBody.setTag("body");
		tvBody.setTextColor(ThemeColors.success());
		tvBody.setTextSize(12);

		card.addView(tvTitle);
		card.addView(tvBody);

		return card;
	}

	private static LinearLayout buildGoalSummaryCard(Activity activity, EpargneModels.SavingsGoal goal) {
		int pct = EpargneCalculator.progressPercent(goal);

		LinearLayout card = new LinearLayout(activity);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setPadding(
				DS.dp(activity, DS.PAD_INPUT),
				DS.dp(activity, DS.GAP_SM),
				DS.dp(activity, DS.PAD_INPUT),
				DS.dp(activity, DS.GAP_SM)
		);

		card.setBackground(UiFactory.bgBordered(
				ThemeColors.successBackground(),
				ThemeColors.success(),
				DS.R_SM,
				activity
		));

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.bottomMargin = DS.dp(activity, DS.GAP_SM);
		card.setLayoutParams(lp);

		TextView tvTitle = new TextView(activity);
		tvTitle.setText(goal.emoji + "  " + goal.name);
		tvTitle.setTextColor(ThemeColors.text());
		tvTitle.setTextSize(DS.TEXT_BODY);
		tvTitle.setTypeface(null, Typeface.BOLD);
		card.addView(tvTitle);

		FrameLayout progressWrapper = new FrameLayout(activity);
		LinearLayout.LayoutParams pwp = new LinearLayout.LayoutParams(-1, DS.dp(activity, 6));
		pwp.topMargin = DS.dp(activity, DS.GAP_SM);
		pwp.bottomMargin = DS.dp(activity, DS.GAP_SM);
		progressWrapper.setLayoutParams(pwp);

		View track = new View(activity);
		track.setBackground(UiFactory.bg(ThemeColors.divider(), DS.R_XS, activity));
		track.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));

		View fill = new View(activity);
		fill.setBackground(UiFactory.bg(ThemeColors.success(), DS.R_XS, activity));
		fill.setLayoutParams(new FrameLayout.LayoutParams(0, -1));

		progressWrapper.addView(track);
		progressWrapper.addView(fill);

		final int pctFinal = Math.max(0, Math.min(100, pct));

		progressWrapper.post(() -> {
			int w = progressWrapper.getWidth();

			if (w > 0) {
				fill.setLayoutParams(new FrameLayout.LayoutParams((int) (w * pctFinal / 100f), -1));
			}
		});

		card.addView(progressWrapper);

		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);

		TextView tvCurrent = new TextView(activity);
		tvCurrent.setText(Fmt.money(goal.current));
		tvCurrent.setTextColor(ThemeColors.success());
		tvCurrent.setTextSize(DS.TEXT_SM);
		tvCurrent.setTypeface(null, Typeface.BOLD);
		tvCurrent.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

		TextView tvTarget = new TextView(activity);
		tvTarget.setText("sur " + Fmt.money(goal.target));
		tvTarget.setTextColor(ThemeColors.subtext());
		tvTarget.setTextSize(DS.TEXT_SM);

		row.addView(tvCurrent);
		row.addView(tvTarget);

		card.addView(row);

		return card;
	}

	private static View buildRemainingBanner(Activity activity, EpargneModels.SavingsGoal goal) {
		LinearLayout banner = new LinearLayout(activity);
		banner.setOrientation(LinearLayout.HORIZONTAL);
		banner.setGravity(android.view.Gravity.CENTER_VERTICAL);
		banner.setPadding(
				DS.dp(activity, DS.PAD_INPUT),
				DS.dp(activity, DS.GAP_SM),
				DS.dp(activity, DS.PAD_INPUT),
				DS.dp(activity, DS.GAP_SM)
		);

		LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
		bp.bottomMargin = DS.dp(activity, DS.GAP_SM);
		banner.setLayoutParams(bp);

		if (goal.remaining() > 0) {
			banner.setBackground(UiFactory.bgBordered(
					ThemeColors.warningBackground(),
					ThemeColors.warning(),
					DS.R_SM,
					activity
			));

			TextView tvIcon = new TextView(activity);
			tvIcon.setText("•");
			tvIcon.setTextSize(18);
			tvIcon.setTextColor(ThemeColors.warning());

			LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-2, -2);
			ip.rightMargin = DS.dp(activity, DS.GAP_SM);
			tvIcon.setLayoutParams(ip);

			banner.addView(tvIcon);

			TextView tvText = new TextView(activity);
			tvText.setText("Il vous manque " + Fmt.money(goal.remaining()) + " pour atteindre votre objectif.");
			tvText.setTextColor(ThemeColors.warning());
			tvText.setTextSize(DS.TEXT_SM);
			tvText.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

			banner.addView(tvText);
		} else {
			banner.setBackground(UiFactory.bgBordered(
					ThemeColors.successBackground(),
					ThemeColors.success(),
					DS.R_SM,
					activity
			));

			TextView tvDone = new TextView(activity);
			tvDone.setText("Objectif atteint. Vous pouvez quand même continuer à épargner.");
			tvDone.setTextColor(ThemeColors.success());
			tvDone.setTextSize(DS.TEXT_SM);
			tvDone.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

			banner.addView(tvDone);
		}

		return banner;
	}

	private static TextView buildInfoText(Activity activity, String text, int bgColor, int textColor) {
		TextView tv = new TextView(activity);
		tv.setText(text);
		tv.setTextColor(textColor);
		tv.setTextSize(DS.TEXT_SM);
		tv.setPadding(
				DS.dp(activity, DS.PAD_INPUT),
				DS.dp(activity, DS.GAP_SM),
				DS.dp(activity, DS.PAD_INPUT),
				DS.dp(activity, DS.GAP_SM)
		);
		tv.setBackground(UiFactory.bg(bgColor, DS.R_XS, activity));

		return tv;
	}

	private static double parseInput(String s, double fallback) {
		try {
			return Double.parseDouble(s.trim().replace(",", ".").replace(" ", ""));
		} catch (Exception e) {
			return fallback;
		}
	}

	private static String capitalize(String s) {
		if (s == null || s.isEmpty()) {
			return s;
		}

		return s.substring(0, 1).toUpperCase(Locale.FRANCE) + s.substring(1);
	}

	private static TextWatcher simpleTextWatcher(Runnable run) {
		return new TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
			@Override public void afterTextChanged(Editable s) {}

			@Override
			public void onTextChanged(CharSequence s, int a, int b, int c) {
				run.run();
			}
		};
	}
}
