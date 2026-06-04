package com.couplefinance.ui.agenda;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.couplefinance.AppToast;
import com.couplefinance.R;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.AppDialog;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.Fmt;
import com.couplefinance.core.ui.UiFactory;
import com.couplefinance.core.ui.animations.PressAnimations;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class AgendaDialogs {

    private AgendaDialogs() {
    }

    public interface OnActionDone {
        void reload();
    }

    public static void showAddEventDialog(Activity activity,
                                          Calendar preselectedDate,
                                          List<String> members,
                                          OnActionDone callback) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row1 = new LinearLayout(activity);
        row1.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams r1p = new LinearLayout.LayoutParams(-1, -2);
        r1p.bottomMargin = DS.dp(activity, DS.GAP_SM);
        row1.setLayoutParams(r1p);

        LinearLayout colTitre = AppDialog.fieldColumn(activity, "TITRE");
        EditText etTitre = UiFactory.input(activity, "Ex : Anniversaire Marie");
        colTitre.addView(etTitre);

        LinearLayout.LayoutParams titlLP = new LinearLayout.LayoutParams(0, -2, 1f);
        titlLP.rightMargin = DS.dp(activity, DS.GAP);
        row1.addView(colTitre, titlLP);

        ArrayList<String> typeList = new ArrayList<>(Arrays.asList(AgendaModels.EVENT_TYPES));
        final int[] typeIdx = { 0 };

        LinearLayout colType = AppDialog.fieldColumn(activity, "TYPE");
        AutoCompleteTextView acvType = makeAutoComplete(activity, typeList, typeIdx);
        colType.addView(acvType);
        row1.addView(colType, new LinearLayout.LayoutParams(0, -2, 1f));

        content.addView(row1);

        ArrayList<String> personList = new ArrayList<>(
                members == null || members.isEmpty()
                        ? java.util.Collections.singletonList("Moi")
                        : members
        );
        final int[] personIdx = { 0 };

        LinearLayout row2 = new LinearLayout(activity);
        row2.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams r2p = new LinearLayout.LayoutParams(-1, -2);
        r2p.bottomMargin = DS.dp(activity, DS.GAP_SM);
        row2.setLayoutParams(r2p);

        LinearLayout colPerson = AppDialog.fieldColumn(activity, "PERSONNE");
        AutoCompleteTextView acvPerson = makeAutoComplete(activity, personList, personIdx);
        colPerson.addView(acvPerson);

        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, -2, 1f);
        plp.rightMargin = DS.dp(activity, DS.GAP);
        row2.addView(colPerson, plp);

        LinearLayout colMontant = AppDialog.fieldColumn(activity, "MONTANT €");
        EditText etMontant = UiFactory.inputNumeric(activity, "0.00");
        colMontant.addView(etMontant);
        row2.addView(colMontant, new LinearLayout.LayoutParams(0, -2, 1f));

        content.addView(row2);

        Runnable updateMontant = () -> {
            boolean isFinancial = AgendaModels.FINANCIAL_TYPES.contains(typeList.get(typeIdx[0]));
            colMontant.setVisibility(isFinancial ? android.view.View.VISIBLE : android.view.View.GONE);
        };

        updateMontant.run();

        acvType.setOnItemClickListener((p, v, pos, id) -> {
            typeIdx[0] = pos;
            acvType.setText(typeList.get(pos));
            acvType.dismissDropDown();
            updateMontant.run();
        });

        final long[] selectedDate = {
                preselectedDate != null
                        ? preselectedDate.getTimeInMillis()
                        : System.currentTimeMillis()
        };

        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(-1, -2);
        dateLp.bottomMargin = DS.dp(activity, DS.GAP_SM);

        LinearLayout colDate = AppDialog.fieldColumn(activity, "DATE");
        colDate.setLayoutParams(dateLp);

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE);

        Button btnDate = UiFactory.btnSecondary(activity, sdf.format(new Date(selectedDate[0])));
        btnDate.setLayoutParams(new LinearLayout.LayoutParams(-1, DS.dp(activity, DS.BTN_HEIGHT)));
        btnDate.setTextColor(ThemeColors.primary());
        btnDate.setAllCaps(false);
        PressAnimations.apply(btnDate);

        colDate.addView(btnDate);
        content.addView(colDate);

        btnDate.setOnClickListener(v -> {
            CalendarView cv = new CalendarView(activity);
            cv.setDate(selectedDate[0]);
            cv.setFirstDayOfWeek(2);

            cv.setOnDateChangeListener((view, y, m, d) -> {
                Calendar c = Calendar.getInstance();
                c.set(y, m, d, 12, 0, 0);
                c.set(Calendar.MILLISECOND, 0);
                selectedDate[0] = c.getTimeInMillis();
            });

            new AlertDialog.Builder(activity)
                    .setTitle("Choisir une date")
                    .setView(cv)
                    .setPositiveButton("Confirmer", (d, w) -> {
                        btnDate.setText(sdf.format(new Date(selectedDate[0])));
                        btnDate.setBackground(UiFactory.bg(
                                ThemeColors.primarySoft(),
                                DS.R_SM,
                                activity
                        ));
                        btnDate.setTextColor(ThemeColors.primary());
                    })
                    .setNegativeButton("Annuler", null)
                    .show();
        });

        LinearLayout colNote = AppDialog.fieldColumn(activity, "NOTE (OPTIONNEL)");
        EditText etNote = UiFactory.input(activity, "Ajouter une note...");
        colNote.addView(etNote);
        content.addView(colNote);

        final AlertDialog[] dialogRef = new AlertDialog[1];

        dialogRef[0] = new AppDialog.Builder(activity)
                .icon("📅")
                .title("Nouvel événement")
                .subtitle("Remplis les informations ci-dessous.")
                .content(content)
                .primaryBtn("ENREGISTRER", () -> {
                    String title = etTitre.getText().toString().trim();

                    if (title.isEmpty()) {
                        AppToast.error(activity, "Le titre est requis");
                        return;
                    }

                    double amount = 0;

                    try {
                        String rawAmount = etMontant.getText().toString().trim().replace(",", ".").replace(" ", "");
                        if (!rawAmount.isEmpty()) {
                            amount = Double.parseDouble(rawAmount);
                        }
                    } catch (Exception ignored) {
                    }

                    String note = etNote.getText().toString().trim();

                    AgendaRepository.addEvent(
                            title,
                            typeList.get(typeIdx[0]),
                            amount,
                            selectedDate[0],
                            personList.get(personIdx[0]),
                            note,
                            activity,
                            new AgendaRepository.OnWriteComplete() {
                                @Override
                                public void onSuccess() {
                                    AppToast.success(activity, "Événement ajouté");

                                    if (dialogRef[0] != null) {
                                        dialogRef[0].dismiss();
                                    }

                                    callback.reload();
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

    public static void showDeleteConfirm(Activity activity,
                                         AgendaModels.AgendaEvent event,
                                         OnActionDone callback) {
        final AlertDialog[] dialogRef = new AlertDialog[1];

        dialogRef[0] = new AppDialog.Builder(activity)
                .icon("🗑")
                .title("Supprimer \"" + event.title + "\" ?")
                .subtitle("Cette action est irréversible.")
                .primaryBtn("SUPPRIMER", () -> AgendaRepository.deleteEvent(
                        event.docPath,
                        activity,
                        new AgendaRepository.OnWriteComplete() {
                            @Override
                            public void onSuccess() {
                                AppToast.success(activity, "Supprimé");

                                if (dialogRef[0] != null) {
                                    dialogRef[0].dismiss();
                                }

                                callback.reload();
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

    private static AutoCompleteTextView makeAutoComplete(Activity activity,
                                                         ArrayList<String> items,
                                                         int[] selectedIdx) {
        AutoCompleteTextView acv = new AutoCompleteTextView(activity);

        acv.setText(items.isEmpty() ? "" : items.get(selectedIdx[0]));
        acv.setTextSize(DS.TEXT_BODY);
        acv.setTextColor(ThemeColors.text());
        acv.setSingleLine(true);
        acv.setFocusable(false);
        acv.setClickable(true);
        acv.setThreshold(100);

        acv.setBackground(UiFactory.bgBordered(
                ThemeColors.card(),
                ThemeColors.border(),
                DS.R_SM,
                activity
        ));

        acv.setPadding(
                DS.dp(activity, DS.PAD_INPUT),
                DS.dp(activity, 12),
                DS.dp(activity, DS.PAD_INPUT),
                DS.dp(activity, 12)
        );

        acv.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        GradientDrawable dropBg = new GradientDrawable();
        dropBg.setCornerRadius(DS.dp(activity, DS.R_MD));
        dropBg.setColor(ThemeColors.background());
        dropBg.setStroke(DS.dp(activity, 1), ThemeColors.border());
        acv.setDropDownBackgroundDrawable(dropBg);
        acv.setDropDownWidth(ViewGroup.LayoutParams.WRAP_CONTENT);

        ArrayAdapter<String> adp = new ArrayAdapter<>(
                activity,
                R.layout.spinner_dropdown_item,
                items
        );

        acv.setAdapter(adp);

        acv.setOnClickListener(v -> acv.showDropDown());

        acv.setOnItemClickListener((p, v, pos, id) -> {
            selectedIdx[0] = pos;
            acv.setText(items.get(pos));
            acv.dismissDropDown();
        });

        return acv;
    }
}