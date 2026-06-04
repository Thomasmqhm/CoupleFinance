package com.couplefinance.ui.repartition;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.AppToast;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.AppDialog;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.Fmt;
import com.couplefinance.core.ui.UiFactory;
import com.couplefinance.core.ui.animations.PressAnimations;

import java.util.List;

public final class RepartitionDialogs {

    private RepartitionDialogs() {
    }

    public interface OnActionDone {
        void reload();
    }

    public interface OnRatioChanged {
        void onChanged(int newRatio0);
    }

    public static void showRatioDialog(Activity activity,
                                       int[] currentRatio,
                                       List<String> members,
                                       OnRatioChanged callback) {
        String m0 = members.size() > 0 ? members.get(0) : "Membre 1";
        String m1 = members.size() > 1 ? members.get(1) : "Membre 2";

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);

        final int[] selected = { currentRatio[0] };

        TextView tvCurrent = new TextView(activity);
        tvCurrent.setText("Ratio actuel : " + selected[0] + " % / " + (100 - selected[0]) + " %");
        tvCurrent.setTextColor(ThemeColors.primary());
        tvCurrent.setTextSize(DS.TEXT_BODY);
        tvCurrent.setTypeface(null, Typeface.BOLD);

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.bottomMargin = DS.dp(activity, DS.GAP);
        tvCurrent.setLayoutParams(cp);

        content.addView(tvCurrent);

        TextView presetLabel = UiFactory.bodyMuted(activity, "PRÉRÉGLAGES");
        presetLabel.setTextColor(ThemeColors.subtext());
        presetLabel.setLetterSpacing(0.08f);

        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2);
        plp.bottomMargin = DS.dp(activity, DS.GAP_SM);
        presetLabel.setLayoutParams(plp);

        content.addView(presetLabel);

        LinearLayout presetRow = new LinearLayout(activity);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams prp = new LinearLayout.LayoutParams(-1, -2);
        prp.bottomMargin = DS.dp(activity, DS.GAP);
        presetRow.setLayoutParams(prp);

        content.addView(presetRow);

        final EditText[] customInputRef = new EditText[1];

        int[][] presets = { { 50, 50 }, { 60, 40 }, { 70, 30 }, { 33, 67 } };

        for (int i = 0; i < presets.length; i++) {
            int[] preset = presets[i];

            Button btn = new Button(activity);
            btn.setText(preset[0] + "/" + preset[1]);
            btn.setAllCaps(false);
            btn.setTextSize(DS.TEXT_SM);

            boolean isActive = selected[0] == preset[0];

            btn.setTextColor(isActive ? Color.WHITE : ThemeColors.primary());
            btn.setBackground(UiFactory.bg(
                    isActive ? ThemeColors.primary() : ThemeColors.primarySoft(),
                    DS.R_XS,
                    activity
            ));
            btn.setStateListAnimator(null);
            PressAnimations.apply(btn);

            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, DS.dp(activity, 40), 1f);

            if (i < presets.length - 1) {
                bp.rightMargin = DS.dp(activity, DS.GAP_SM);
            }

            btn.setLayoutParams(bp);

            btn.setOnClickListener(v -> {
                selected[0] = preset[0];
                tvCurrent.setText("Ratio sélectionné : " + preset[0] + " % / " + preset[1] + " %");
                if (customInputRef[0] != null) {
                    customInputRef[0].setText(String.valueOf(preset[0]));
                    customInputRef[0].setSelection(customInputRef[0].getText().length());
                }
            });

            presetRow.addView(btn);
        }

        LinearLayout colCustom = AppDialog.fieldColumn(
                activity,
                "OU PERSONNALISÉ — Part de " + m0 + " (%)"
        );

        EditText etCustom = UiFactory.inputNumeric(activity, "Ex : 60 (l'autre aura 40%)");
        etCustom.setText(String.valueOf(currentRatio[0]));
        etCustom.setSelection(etCustom.getText().length());
        customInputRef[0] = etCustom;

        colCustom.addView(etCustom);
        content.addView(colCustom);

        final AlertDialog[] dialogRef = new AlertDialog[1];

        dialogRef[0] = new AppDialog.Builder(activity)
                .icon("⚙")
                .title("Modifier le ratio")
                .subtitle("Répartition des dépenses entre " + m0 + " et " + m1 + ".")
                .content(content)
                .primaryBtn("ENREGISTRER", () -> {
                    String val = etCustom.getText().toString().trim();

                    int r = selected[0];

                    if (!val.isEmpty()) {
                        try {
                            r = Integer.parseInt(val);
                        } catch (Exception e) {
                            AppToast.error(activity, "Ratio invalide");
                            return;
                        }
                    }

                    if (r <= 0 || r >= 100) {
                        AppToast.error(activity, "Le ratio doit être entre 1 et 99");
                        return;
                    }

                    callback.onChanged(r);

                    if (dialogRef[0] != null) {
                        dialogRef[0].dismiss();
                    }
                })
                .build();

        dialogRef[0].show();
    }

    public static void showSettlementDialog(Activity activity,
                                            String debtor,
                                            String creditor,
                                            double suggestedAmount,
                                            List<String> members,
                                            OnActionDone callback) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);

        LinearLayout infoCard = new LinearLayout(activity);
        infoCard.setOrientation(LinearLayout.HORIZONTAL);
        infoCard.setGravity(Gravity.CENTER_VERTICAL);
        infoCard.setPadding(
                DS.dp(activity, DS.PAD_INPUT),
                DS.dp(activity, DS.GAP_SM),
                DS.dp(activity, DS.PAD_INPUT),
                DS.dp(activity, DS.GAP_SM)
        );
        infoCard.setBackground(UiFactory.bgBordered(
                ThemeColors.primarySoft(),
                ThemeColors.primary(),
                DS.R_SM,
                activity
        ));

        LinearLayout.LayoutParams icp = new LinearLayout.LayoutParams(-1, -2);
        icp.bottomMargin = DS.dp(activity, DS.GAP);
        infoCard.setLayoutParams(icp);

        int fromIdx = Math.max(0, members.indexOf(debtor));
        int toIdx = Math.max(1, members.indexOf(creditor));

        TextView avFrom = UiFactory.avatar(activity, debtor, fromIdx, 36);
        LinearLayout.LayoutParams afp = new LinearLayout.LayoutParams(
                DS.dp(activity, 36),
                DS.dp(activity, 36)
        );
        avFrom.setLayoutParams(afp);
        infoCard.addView(avFrom);

        TextView tvArrow = new TextView(activity);
        tvArrow.setText("  →  ");
        tvArrow.setTextColor(ThemeColors.subtext());
        tvArrow.setTextSize(18);
        infoCard.addView(tvArrow);

        TextView avTo = UiFactory.avatar(activity, creditor, toIdx, 36);
        LinearLayout.LayoutParams atp = new LinearLayout.LayoutParams(
                DS.dp(activity, 36),
                DS.dp(activity, 36)
        );
        avTo.setLayoutParams(atp);
        infoCard.addView(avTo);

        View spacer = new View(activity);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        infoCard.addView(spacer);

        TextView tvInfo = new TextView(activity);
        tvInfo.setText(debtor + " → " + creditor);
        tvInfo.setTextColor(ThemeColors.text());
        tvInfo.setTextSize(DS.TEXT_SM);
        tvInfo.setTypeface(null, Typeface.BOLD);
        infoCard.addView(tvInfo);

        content.addView(infoCard);

        LinearLayout colAmt = AppDialog.fieldColumn(activity, "MONTANT À REMBOURSER €");

        EditText etAmount = UiFactory.inputNumeric(activity, "Montant (€)");
        etAmount.setText(Fmt.moneyInput(suggestedAmount));
        etAmount.setSelection(etAmount.getText().length());

        colAmt.addView(etAmount);
        content.addView(colAmt);

        LinearLayout.LayoutParams motifLp = new LinearLayout.LayoutParams(-1, -2);
        motifLp.topMargin = DS.dp(activity, DS.GAP);

        LinearLayout colMotif = AppDialog.fieldColumn(activity, "MOTIF (OPTIONNEL)");
        colMotif.setLayoutParams(motifLp);

        EditText etMotif = UiFactory.input(activity, "Ex : Rééquilibrage mai 2026");
        colMotif.addView(etMotif);

        content.addView(colMotif);

        final AlertDialog[] dialogRef = new AlertDialog[1];

        dialogRef[0] = new AppDialog.Builder(activity)
                .icon("💸")
                .title("Confirmer le remboursement")
                .subtitle("Enregistre un virement de " + debtor + " vers " + creditor + " pour équilibrer les comptes.")
                .content(content)
                .primaryBtn("CONFIRMER", () -> {
                    String amtStr = etAmount.getText().toString().trim().replace(",", ".");

                    if (amtStr.isEmpty()) {
                        AppToast.error(activity, "Montant requis");
                        return;
                    }

                    double amount;

                    try {
                        amount = Double.parseDouble(amtStr.replace(" ", ""));
                    } catch (Exception e) {
                        AppToast.error(activity, "Montant invalide");
                        return;
                    }

                    if (amount <= 0) {
                        AppToast.error(activity, "Montant doit être > 0");
                        return;
                    }

                    String motif = etMotif.getText().toString().trim();

                    if (motif.isEmpty()) {
                        motif = "Rééquilibrage des dépenses";
                    }

                    AppToast.info(activity, "Remboursement en cours...");

                    RepartitionRepository.effectuerRemboursement(
                            debtor,
                            creditor,
                            amount,
                            motif,
                            activity,
                            new RepartitionRepository.OnWriteComplete() {
                                @Override
                                public void onSuccess() {
                                    AppToast.success(activity, Fmt.money(amount) + " remboursé");

                                    if (dialogRef[0] != null) {
                                        dialogRef[0].dismiss();
                                    }

                                    new android.os.Handler(android.os.Looper.getMainLooper())
                                            .postDelayed(callback::reload, 1200);
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
}