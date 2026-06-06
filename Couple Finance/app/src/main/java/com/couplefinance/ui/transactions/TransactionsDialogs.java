package com.couplefinance.ui.transactions;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import com.couplefinance.AppToast;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.theme.ThemeDrawable;
import com.couplefinance.core.ui.AppDialog;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.Fmt;
import com.couplefinance.core.ui.UiFactory;
import com.couplefinance.core.ui.components.PremiumButton;
import com.couplefinance.core.ui.components.PremiumChip;
import com.couplefinance.core.ui.components.PremiumInput;
import com.couplefinance.core.ui.components.PremiumSelector;
import com.couplefinance.core.ui.dialogs.DateDialog;
import com.couplefinance.data.CategoryManager;
import com.couplefinance.data.FirestoreManager;
import com.couplefinance.data.JointAccountManager;
import com.couplefinance.data.MerchantRuleManager;
import com.couplefinance.data.RecurringChargeManager;
import com.couplefinance.ui.credits.CreditsDialogs;
import com.couplefinance.ui.settings.SettingsChargeWriter;
import com.couplefinance.ui.settings.SettingsModels;
import com.couplefinance.utils.ActivityLogger;

import android.text.Editable;
import android.text.TextWatcher;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class TransactionsDialogs {

    private TransactionsDialogs() {}

    /** Clé "label:amount" → timestamp de la dernière soumission. Détection de doublons. */
    private static final java.util.HashMap<String, Long> recentSubmissions = new java.util.HashMap<>();
    private static final long DUPLICATE_WINDOW_MS = 60_000; // 60 secondes

    private static boolean isDuplicate(String label, double amount) {
        String key = label.toLowerCase(Locale.FRANCE) + ":" + Math.round(amount * 100);
        Long last = recentSubmissions.get(key);
        return last != null && (System.currentTimeMillis() - last) < DUPLICATE_WINDOW_MS;
    }

    private static void markSubmitted(String label, double amount) {
        String key = label.toLowerCase(Locale.FRANCE) + ":" + Math.round(amount * 100);
        recentSubmissions.put(key, System.currentTimeMillis());
    }

    public interface OnActionDone {
        void reload();
    }

    // ─────────────────────────────────────────────────────────────
    // Dialogue AJOUT
    // ─────────────────────────────────────────────────────────────

    public static void showAddDialog(Activity activity,
                                     List<String> members,
                                     List<String[]> categories,
                                     OnActionDone callback) {

        // catNames est mutable — on y ajoutera les nouvelles catégories créées à la volée
        final ArrayList<String> catNames = buildCategoryNames(categories);
        ArrayList<String> persons = new ArrayList<>(
                members == null || members.isEmpty() ? Arrays.asList("Moi") : members);

        boolean jointEnabled = JointAccountManager.getInstance().isEnabledLocal();
        String jointName    = JointAccountManager.getInstance().getNameLocal();

        if (jointEnabled && !containsIgnoreCase(persons, jointName)) {
            persons.add(jointName);
        }

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);

        // ── Libellé ──────────────────────────────────────────────
        LinearLayout colLabel = AppDialog.fieldColumn(activity, "LIBELLÉ");
        EditText etLabel = PremiumInput.normal(activity, "Ex : Courses Carrefour");
        colLabel.addView(etLabel);
        content.addView(withBottomMargin(colLabel, activity, DS.GAP_SM));

        TextView tvAutoFill = buildAutoFillCard(activity);
        content.addView(tvAutoFill);

        // ── Montant + Type ────────────────────────────────────────
        LinearLayout row2 = AppDialog.fieldRow(activity);

        LinearLayout colAmt = AppDialog.fieldColumn(activity, "MONTANT €");
        EditText etAmount = PremiumInput.numeric(activity, "0.00");
        colAmt.addView(etAmount);
        row2.addView(colAmt, new LinearLayout.LayoutParams(0, -2, 1f));

        String[] typeLabels = {"Dépense", "Revenu", "Charge fixe"};
        final int[] typeIdx = {0};

        LinearLayout colType = AppDialog.fieldColumn(activity, "TYPE");
        AutoCompleteTextView acvType = PremiumSelector.selector(activity, typeLabels, typeIdx);
        colType.addView(acvType);

        LinearLayout.LayoutParams typeLp = new LinearLayout.LayoutParams(0, -2, 1f);
        typeLp.leftMargin = DS.dp(activity, DS.GAP);
        row2.addView(colType, typeLp);
        content.addView(row2);

        // ── Catégorie + Personne ──────────────────────────────────
        LinearLayout row3 = AppDialog.fieldRow(activity);

        // colCat est une colonne verticale : sélecteur + chip "+ Nouvelle catégorie"
        LinearLayout colCat = AppDialog.fieldColumn(activity, "CATÉGORIE");
        final int[] catIdx = {0};
        final AutoCompleteTextView[] acvCatHolder = {
                PremiumSelector.selector(activity, catNames.toArray(new String[0]), catIdx)
        };
        colCat.addView(acvCatHolder[0]);

        // Chip "+ Nouvelle catégorie"
        TextView tvNewCat = buildNewCategoryChip(activity);
        tvNewCat.setOnClickListener(v ->
                showInlineNewCategoryDialog(activity, catNames, catIdx, acvCatHolder, colCat, tvNewCat));
        colCat.addView(tvNewCat);

        row3.addView(colCat, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout colPerson = AppDialog.fieldColumn(activity, "PERSONNE");
        final int[] personIdx = {0};
        AutoCompleteTextView acvPerson = PremiumSelector.selector(activity, persons.toArray(new String[0]), personIdx);
        colPerson.addView(acvPerson);

        LinearLayout.LayoutParams perLp = new LinearLayout.LayoutParams(0, -2, 1f);
        perLp.leftMargin = DS.dp(activity, DS.GAP);
        row3.addView(colPerson, perLp);
        content.addView(row3);

        // ── Date ──────────────────────────────────────────────────
        final long[] dateMs = {normalizeDate(System.currentTimeMillis())};
        content.addView(buildDateSelector(activity, dateMs));

        // ── C1 : toggle abonnement récurrent (visible si type = Charge fixe) ──
        final boolean[] createRecurring = {false};
        LinearLayout recurringRow = buildRecurringToggle(activity, createRecurring);
        recurringRow.setVisibility(android.view.View.GONE);
        content.addView(recurringRow);

        // Afficher/masquer selon le type sélectionné
        acvType.setOnItemClickListener((parent, itemView, position, id) -> {
            String sel = (position >= 0 && position < typeLabels.length)
                    ? typeLabels[position] : "";
            typeIdx[0] = position;
            if ("Charge fixe".equalsIgnoreCase(sel)) {
                recurringRow.setVisibility(android.view.View.VISIBLE);
            } else {
                recurringRow.setVisibility(android.view.View.GONE);
                createRecurring[0] = false;
            }
        });

        // ── Auto-remplissage catégorie sur saisie du libellé ─────
        etLabel.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable s) {
                String input = s.toString().trim();
                if (input.length() < 3) {
                    tvAutoFill.setVisibility(View.GONE);
                    return;
                }
                TransactionSuggestion sug = TransactionAutoFill.suggest(
                        input,
                        new ArrayList<>(),
                        new ArrayList<>(categories != null ? categories : new ArrayList<>()),
                        new ArrayList<>(persons));
                if (sug.found) {
                    StringBuilder hint = new StringBuilder("💡 Suggestion : ");
                    if (sug.category != null && !sug.category.isEmpty())
                        hint.append("Catégorie → ").append(sug.category);
                    if (sug.averageAmount > 0)
                        hint.append("  ·  ").append(String.format(Locale.FRANCE, "%.2f €", sug.averageAmount));
                    tvAutoFill.setText(hint.toString());
                    tvAutoFill.setVisibility(View.VISIBLE);
                    tvAutoFill.setOnClickListener(v -> {
                        if (sug.category != null && !sug.category.isEmpty()) {
                            int idx = findIndex(catNames, sug.category);
                            if (idx >= 0) {
                                catIdx[0] = idx;
                                acvCatHolder[0].setText(sug.category, false);
                            }
                        }
                        if (sug.averageAmount > 0 && etAmount.getText().toString().trim().isEmpty()) {
                            etAmount.setText(Fmt.moneyInput(sug.averageAmount));
                        }
                        tvAutoFill.setVisibility(View.GONE);
                    });
                } else {
                    tvAutoFill.setVisibility(View.GONE);
                }
            }
        });

        // ── Partage ───────────────────────────────────────────────
        final boolean[] shared = {false};
        content.addView(buildShareToggle(activity, shared));

        if (jointEnabled) {
            content.addView(buildJointInfo(activity));
        }

        // ── Bouton Enregistrer ────────────────────────────────────
        new AppDialog.Builder(activity)
                .icon("💸")
                .title("Nouvelle transaction")
                .subtitle("Remplis les informations ci-dessous.")
                .content(content)
                .primaryBtn("ENREGISTRER", () -> {
                    String label = etLabel.getText().toString().trim();

                    if (label.isEmpty()) {
                        AppToast.error(activity, "Libellé requis");
                        return;
                    }

                    double amount;
                    try {
                        amount = Double.parseDouble(
                                etAmount.getText().toString().trim().replace(",", "."));
                    } catch (Exception e) {
                        AppToast.error(activity, "Montant invalide");
                        return;
                    }

                    if (amount <= 0) {
                        AppToast.error(activity, "Montant doit être supérieur à 0");
                        return;
                    }

                    String typeLabel = resolveText(acvType, typeLabels, typeIdx);
                    String category  = resolveText(acvCatHolder[0], catNames.toArray(new String[0]), catIdx);
                    String person    = resolveText(acvPerson, persons.toArray(new String[0]), personIdx);

                    String type = typeToCode(typeLabel);

                    boolean isJointSelected = jointEnabled
                            && person.equalsIgnoreCase(jointName);
                    String compte = isJointSelected ? "joint" : "";

                    if (isDuplicate(label, amount)) {
                        AppToast.error(activity, "Transaction similaire déjà ajoutée (moins d'1 min)");
                        return;
                    }
                    markSubmitted(label, amount);

                    TransactionsRepository.addTransaction(
                            label, amount, type, category,
                            dateMs[0], person, shared[0], false, compte,
                            activity,
                            new TransactionsRepository.OnWriteComplete() {
                                public void onSuccess() {
                                    // C1 : si Charge fixe + case cochée → créer abonnement
                                    if (createRecurring[0]) {
                                        String finalPerson = resolveText(
                                                acvPerson,
                                                persons.toArray(new String[0]),
                                                personIdx);
                                        createRecurringChargeFromDialog(
                                                activity, label, amount, category,
                                                dateMs[0], finalPerson);
                                    }
                                    ActivityLogger.logTransaction(
                                            activity, person, label, amount,
                                            "income".equals(type));
                                    // Alerte intelligente pour grosse dépense manuelle
                                    if (!"income".equals(type)) {
                                        try {
                                            com.couplefinance.data.SmartNotificationManager
                                                    .checkSingleExpense(activity, amount, label, category);
                                        } catch (Exception ignored) {}
                                    }
                                    AppToast.success(activity, "Transaction ajoutée");
                                    if (callback != null) callback.reload();
                                }
                                public void onError(String e) {
                                    AppToast.error(activity, "Erreur : " + e);
                                }
                            });
                })
                .show();
    }

    // ─────────────────────────────────────────────────────────────
    // Dialogue MODIFICATION
    // ─────────────────────────────────────────────────────────────

    public static void showEditDialog(Activity activity,
                                      TransactionsModels.Transaction tx,
                                      List<TransactionsModels.Transaction> allTx,
                                      List<String> members,
                                      List<String[]> categories,
                                      OnActionDone callback) {

        final ArrayList<String> catNames = buildCategoryNames(categories);
        ArrayList<String> persons = new ArrayList<>(
                members == null || members.isEmpty() ? Arrays.asList("Moi") : members);

        boolean jointEnabled = JointAccountManager.getInstance().isEnabledLocal();
        String jointName    = JointAccountManager.getInstance().getNameLocal();

        if (jointEnabled && !containsIgnoreCase(persons, jointName)) {
            persons.add(jointName);
        }

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);

        // ── Libellé ──────────────────────────────────────────────
        LinearLayout colLabel = AppDialog.fieldColumn(activity, "LIBELLÉ");
        EditText etLabel = PremiumInput.normal(activity, "Libellé");
        etLabel.setText(tx.description());
        etLabel.setSelection(etLabel.getText().length());
        colLabel.addView(etLabel);
        content.addView(withBottomMargin(colLabel, activity, DS.GAP_SM));

        // ── Montant + Type ────────────────────────────────────────
        LinearLayout row2 = AppDialog.fieldRow(activity);

        LinearLayout colAmt = AppDialog.fieldColumn(activity, "MONTANT €");
        EditText etAmount = PremiumInput.numeric(activity, "0.00");
        etAmount.setText(Fmt.moneyInput(tx.amount));
        colAmt.addView(etAmount);
        row2.addView(colAmt, new LinearLayout.LayoutParams(0, -2, 1f));

        String[] typeLabels = {"Dépense", "Revenu", "Charge fixe"};
        final int[] typeIdx = {tx.isIncome() ? 1 : tx.isFixed() ? 2 : 0};

        LinearLayout colType = AppDialog.fieldColumn(activity, "TYPE");
        AutoCompleteTextView acvType = PremiumSelector.selector(activity, typeLabels, typeIdx);
        colType.addView(acvType);

        LinearLayout.LayoutParams typeLp = new LinearLayout.LayoutParams(0, -2, 1f);
        typeLp.leftMargin = DS.dp(activity, DS.GAP);
        row2.addView(colType, typeLp);
        content.addView(row2);

        // ── Catégorie + Personne ──────────────────────────────────
        LinearLayout row3 = AppDialog.fieldRow(activity);

        LinearLayout colCat = AppDialog.fieldColumn(activity, "CATÉGORIE");
        final int[] catIdx = {findIndex(catNames, tx.category)};
        final AutoCompleteTextView[] acvCatHolder = {
                PremiumSelector.selector(activity, catNames.toArray(new String[0]), catIdx)
        };
        colCat.addView(acvCatHolder[0]);

        // Chip "+ Nouvelle catégorie"
        TextView tvNewCat = buildNewCategoryChip(activity);
        tvNewCat.setOnClickListener(v ->
                showInlineNewCategoryDialog(activity, catNames, catIdx, acvCatHolder, colCat, tvNewCat));
        colCat.addView(tvNewCat);

        row3.addView(colCat, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout colPerson = AppDialog.fieldColumn(activity, "PERSONNE");
        String initialPerson = tx.isJoint() ? jointName : tx.person;
        final int[] personIdx = {findIndex(persons, initialPerson)};
        AutoCompleteTextView acvPerson = PremiumSelector.selector(activity, persons.toArray(new String[0]), personIdx);
        colPerson.addView(acvPerson);

        LinearLayout.LayoutParams perLp = new LinearLayout.LayoutParams(0, -2, 1f);
        perLp.leftMargin = DS.dp(activity, DS.GAP);
        row3.addView(colPerson, perLp);
        content.addView(row3);

        // ── Date ──────────────────────────────────────────────────
        final long[] dateMs = {normalizeDate(tx.dateMs > 0
                ? tx.dateMs
                : System.currentTimeMillis())};
        content.addView(buildDateSelector(activity, dateMs));

        if (jointEnabled) {
            content.addView(buildJointInfo(activity));
        }

        // ── Bouton Dupliquer ──────────────────────────────────────
        TextView btnDuplicate = new TextView(activity);
        btnDuplicate.setText("⊕ Dupliquer avec la date du jour");
        btnDuplicate.setTextColor(com.couplefinance.core.theme.ThemeColors.primary());
        btnDuplicate.setTextSize(DS.TEXT_SM);
        btnDuplicate.setGravity(android.view.Gravity.CENTER);
        btnDuplicate.setPadding(0, DS.dp(activity, 12), 0, DS.dp(activity, 4));
        btnDuplicate.setTypeface(null, Typeface.BOLD);
        btnDuplicate.setOnClickListener(v -> {
            String dupLabel  = tx.description();
            double dupAmount = tx.amount;
            String dupType   = tx.type;
            String dupCat    = tx.category;
            String dupPerson = tx.person;
            if (isDuplicate(dupLabel, dupAmount)) {
                AppToast.error(activity, "Doublon détecté — transaction similaire récente");
                return;
            }
            markSubmitted(dupLabel, dupAmount);
            String compte = (jointEnabled && dupPerson.equalsIgnoreCase(jointName)) ? "joint" : "";
            TransactionsRepository.addTransaction(
                    dupLabel, dupAmount, dupType, dupCat,
                    System.currentTimeMillis(), dupPerson, tx.shared, false, compte,
                    activity,
                    new TransactionsRepository.OnWriteComplete() {
                        public void onSuccess() {
                            ActivityLogger.logTransaction(activity, dupPerson, dupLabel, dupAmount, tx.isIncome());
                            AppToast.success(activity, "Transaction dupliquée pour aujourd'hui");
                            if (callback != null) callback.reload();
                        }
                        public void onError(String e) {
                            AppToast.error(activity, "Erreur : " + e);
                        }
                    });
        });
        LinearLayout.LayoutParams dupLp = new LinearLayout.LayoutParams(-1, -2);
        dupLp.topMargin = DS.dp(activity, 8);
        content.addView(btnDuplicate, dupLp);

        // ── Bouton Enregistrer ────────────────────────────────────
        new AppDialog.Builder(activity)
                .icon("✎")
                .title("Modifier la transaction")
                .subtitle("Ajuste les informations de cette opération.")
                .content(content)
                .primaryBtn("ENREGISTRER", () -> {
                    String label = etLabel.getText().toString().trim();

                    if (label.isEmpty()) {
                        AppToast.error(activity, "Libellé requis");
                        return;
                    }

                    double amount;
                    try {
                        amount = Double.parseDouble(
                                etAmount.getText().toString().trim().replace(",", "."));
                    } catch (Exception e) {
                        AppToast.error(activity, "Montant invalide");
                        return;
                    }

                    if (amount <= 0) {
                        AppToast.error(activity, "Montant doit être supérieur à 0");
                        return;
                    }

                    String typeLabel = resolveText(acvType, typeLabels, typeIdx);
                    String category  = resolveText(acvCatHolder[0], catNames.toArray(new String[0]), catIdx);
                    String person    = resolveText(acvPerson, persons.toArray(new String[0]), personIdx);

                    String type = typeToCode(typeLabel);

                    boolean isJointSelected = jointEnabled
                            && person.equalsIgnoreCase(jointName);
                    String compte = isJointSelected ? "joint" : "";

                    final String finalLabel    = label;
                    final String finalCategory = category;
                    final String finalPerson   = person;

                    TransactionsRepository.updateTransaction(
                            tx.docId, label, amount, type, category,
                            dateMs[0], person, tx.shared, compte,
                            activity,
                            new TransactionsRepository.OnWriteComplete() {
                                public void onSuccess() {
                                    // Mémoriser la règle commerçant + appliquer aux similaires
                                    applyMerchantRuleToSimilar(activity, tx, finalLabel, finalCategory, allTx);

                                    // Si catégorie = Crédits, proposer de créer un suivi
                                    if ("Crédits".equals(finalCategory)
                                            && (tx.category == null || !tx.category.equals("Crédits"))) {
                                        new android.app.AlertDialog.Builder(activity)
                                                .setTitle("Suivi de crédit")
                                                .setMessage("Cette transaction est marquée \"Crédits\".\nVoulez-vous créer un suivi dans l'onglet Crédits ?")
                                                .setPositiveButton("Créer", (d, w) ->
                                                        CreditsDialogs.showAddDialog(activity, new CreditsDialogs.OnActionDone() {
                                            public void reload() {}
                                        }))
                                                .setNegativeButton("Non", null)
                                                .show();
                                    }

                                    AppToast.success(activity, "Transaction modifiée");
                                    if (callback != null) callback.reload();
                                }
                                public void onError(String e) {
                                    AppToast.error(activity, "Erreur : " + e);
                                }
                            });
                })
                .show();
    }

    // ─────────────────────────────────────────────────────────────
    // Dialogue SUPPRESSION
    // ─────────────────────────────────────────────────────────────

    public static void showDeleteDialog(Activity activity,
                                        TransactionsModels.Transaction tx,
                                        List<TransactionsModels.Transaction> allTx,
                                        OnActionDone callback) {

        LinearLayout info = AppDialog.infoCard(activity);
        info.setBackground(ThemeDrawable.tintDanger(activity, DS.R_MD));

        TextView tvInfo = UiFactory.body(activity,
                tx.description() + "\n" + Fmt.money(tx.amount)
                        + " · " + Fmt.dateRelative(tx.dateMs));
        tvInfo.setTypeface(null, Typeface.BOLD);
        tvInfo.setTextColor(ThemeColors.text());
        info.addView(tvInfo);

        new AppDialog.Builder(activity)
                .icon("🗑️")
                .title("Supprimer la transaction")
                .subtitle("Cette action est irréversible.")
                .content(info)
                .primaryBtn("SUPPRIMER", () ->
                        TransactionsRepository.deleteTransaction(
                                tx.docId,
                                activity,
                                new TransactionsRepository.OnWriteComplete() {
                                    public void onSuccess() {
                                        if (tx.shared) {
                                            TransactionsRepository.deleteLinkedShareSplits(
                                                    tx.label,
                                                    allTx,
                                                    activity,
                                                    new TransactionsRepository.OnWriteComplete() {
                                                        public void onSuccess() {
                                                            AppToast.success(activity, "Transaction supprimée");
                                                            if (callback != null) callback.reload();
                                                        }
                                                        public void onError(String e) {
                                                            AppToast.success(activity, "Transaction supprimée");
                                                            if (callback != null) callback.reload();
                                                        }
                                                    });
                                        } else {
                                            AppToast.success(activity, "Transaction supprimée");
                                            if (callback != null) callback.reload();
                                        }
                                    }
                                    public void onError(String e) {
                                        AppToast.error(activity, "Erreur : " + e);
                                    }
                                }))
                .show();
    }

    // ─────────────────────────────────────────────────────────────
    // Création de catégorie à la volée
    //
    // Affiche un mini-formulaire inline sous le chip : champ nom +
    // champ emoji + bouton Créer. Une fois la catégorie enregistrée
    // dans Firestore, elle est injectée dans catNames et le sélecteur
    // est reconstruit pour la présélectionner.
    // ─────────────────────────────────────────────────────────────

    private static void showInlineNewCategoryDialog(Activity activity,
                                                     ArrayList<String> catNames,
                                                     int[] catIdx,
                                                     AutoCompleteTextView[] acvCatHolder,
                                                     LinearLayout colCat,
                                                     TextView tvNewCatChip) {

        // Masquer le chip pour éviter le double-clic
        tvNewCatChip.setVisibility(View.GONE);

        // ── Mini-formulaire inline ────────────────────────────────
        LinearLayout inlineForm = new LinearLayout(activity);
        inlineForm.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams formLp = new LinearLayout.LayoutParams(-1, -2);
        formLp.topMargin = DS.dp(activity, DS.GAP_SM);
        inlineForm.setLayoutParams(formLp);

        // Rangée nom + emoji
        LinearLayout rowNameEmoji = new LinearLayout(activity);
        rowNameEmoji.setOrientation(LinearLayout.HORIZONTAL);

        EditText etCatName = PremiumInput.normal(activity, "Nom de la catégorie");
        rowNameEmoji.addView(etCatName, new LinearLayout.LayoutParams(0, -2, 3f));

        EditText etCatEmoji = PremiumInput.normal(activity, "🏷");
        etCatEmoji.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams emojiLp = new LinearLayout.LayoutParams(0, -2, 1f);
        emojiLp.leftMargin = DS.dp(activity, DS.GAP_SM);
        rowNameEmoji.addView(etCatEmoji, emojiLp);

        inlineForm.addView(rowNameEmoji);

        // Rangée boutons Créer / Annuler
        LinearLayout rowBtns = new LinearLayout(activity);
        rowBtns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(-1, -2);
        btnRowLp.topMargin = DS.dp(activity, DS.GAP_SM);
        rowBtns.setLayoutParams(btnRowLp);

        Button btnCreate = PremiumButton.primary(activity, "✓ CRÉER");
        btnCreate.setLayoutParams(new LinearLayout.LayoutParams(0, DS.dp(activity, DS.BTN_HEIGHT), 1f));

        Button btnCancel = PremiumButton.secondary(activity, "ANNULER");
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, DS.dp(activity, DS.BTN_HEIGHT), 1f);
        cancelLp.leftMargin = DS.dp(activity, DS.GAP_SM);
        btnCancel.setLayoutParams(cancelLp);

        rowBtns.addView(btnCreate);
        rowBtns.addView(btnCancel);
        inlineForm.addView(rowBtns);

        colCat.addView(inlineForm);

        // ── Annuler : retire le formulaire, ré-affiche le chip ────
        btnCancel.setOnClickListener(v -> {
            colCat.removeView(inlineForm);
            tvNewCatChip.setVisibility(View.VISIBLE);
        });

        // ── Créer : valide, envoie à Firestore, met à jour le sélecteur
        btnCreate.setOnClickListener(v -> {
            String newName = etCatName.getText().toString().trim();
            if (newName.isEmpty()) {
                AppToast.error(activity, "Le nom est requis");
                return;
            }
            if (containsIgnoreCase(catNames, newName)) {
                AppToast.error(activity, "Cette catégorie existe déjà");
                return;
            }

            String emoji = etCatEmoji.getText().toString().trim();
            if (emoji.isEmpty()) emoji = "🏷";

            btnCreate.setEnabled(false);
            btnCreate.setText("...");

            CategoryManager.getInstance().addCategory(newName, emoji,
                    new FirestoreManager.Callback() {
                        @Override
                        public void onSuccess(String json) {
                            // Injecter la nouvelle catégorie dans la liste
                            catNames.add(newName);
                            int newIdx = catNames.size() - 1;
                            catIdx[0] = newIdx;

                            // Reconstruire le sélecteur avec la nouvelle liste
                            // et le retirer / réinsérer dans colCat à la bonne position (index 1 : après le label)
                            AutoCompleteTextView oldSelector = acvCatHolder[0];
                            int selectorPos = colCat.indexOfChild(oldSelector);
                            colCat.removeView(oldSelector);
                            colCat.removeView(inlineForm);

                            AutoCompleteTextView newSelector =
                                    PremiumSelector.selector(activity, catNames.toArray(new String[0]), catIdx);
                            acvCatHolder[0] = newSelector;

                            if (selectorPos >= 0 && selectorPos <= colCat.getChildCount()) {
                                colCat.addView(newSelector, selectorPos);
                            } else {
                                colCat.addView(newSelector);
                            }
                            // tvNewCatChip est déjà enfant de colCat — on remet juste sa visibilité
                            tvNewCatChip.setVisibility(View.VISIBLE);

                            AppToast.success(activity, "Catégorie \"" + newName + "\" créée");
                        }

                        @Override
                        public void onError(String error) {
                            btnCreate.setEnabled(true);
                            btnCreate.setText("✓ CRÉER");
                            AppToast.error(activity, "Erreur création : " + error);
                        }
                    });
        });
    }

    // ─────────────────────────────────────────────────────────────
    // Résolution de la valeur d'un sélecteur
    // ─────────────────────────────────────────────────────────────
    private static String resolveText(AutoCompleteTextView acv,
                                      String[] values,
                                      int[] indexRef) {
        if (acv != null) {
            String text = acv.getText().toString().trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        if (indexRef != null && values != null
                && indexRef[0] >= 0 && indexRef[0] < values.length) {
            return values[indexRef[0]];
        }
        if (values != null && values.length > 0) {
            return values[0];
        }
        return "";
    }

    // ─────────────────────────────────────────────────────────────
    // Chip "+ Nouvelle catégorie"
    // ─────────────────────────────────────────────────────────────
    private static TextView buildNewCategoryChip(Activity activity) {
        TextView chip = PremiumChip.primary(activity, "+ Nouvelle catégorie");
        chip.setTypeface(null, Typeface.BOLD);
        chip.setTextSize(DS.TEXT_XS);
        chip.setPadding(
                DS.dp(activity, DS.PAD_SM), DS.dp(activity, 5),
                DS.dp(activity, DS.PAD_SM), DS.dp(activity, 5));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = DS.dp(activity, 4);
        chip.setLayoutParams(lp);
        return chip;
    }

    // ─────────────────────────────────────────────────────────────
    // Sélecteur de date
    // ─────────────────────────────────────────────────────────────
    private static LinearLayout buildDateSelector(Activity activity, long[] dateMs) {
        LinearLayout col = AppDialog.fieldColumn(activity, "DATE");

        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(-1, -2);
        dateLp.topMargin    = DS.dp(activity, DS.GAP_SM);
        dateLp.bottomMargin = DS.dp(activity, DS.GAP_SM);
        col.setLayoutParams(dateLp);

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE);

        Button btnDate = PremiumButton.secondary(
                activity, "📅  " + sdf.format(new Date(dateMs[0])));
        btnDate.setLayoutParams(
                new LinearLayout.LayoutParams(-1, DS.dp(activity, DS.BTN_HEIGHT)));

        btnDate.setOnClickListener(v ->
                DateDialog.show(activity, dateMs[0], selected -> {
                    dateMs[0] = normalizeDate(selected);
                    btnDate.setText("📅  " + sdf.format(new Date(dateMs[0])) + " ✓");
                }));

        col.addView(btnDate);
        return col;
    }

    // ─────────────────────────────────────────────────────────────
    // Toggle « dépense partagée »
    // ─────────────────────────────────────────────────────────────
    private static LinearLayout buildShareToggle(Activity activity, boolean[] shared) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(
                DS.dp(activity, DS.PAD_INPUT), DS.dp(activity, 8),
                DS.dp(activity, DS.PAD_INPUT), DS.dp(activity, 8));
        row.setBackground(ThemeDrawable.input(activity));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = DS.dp(activity, DS.GAP_SM);
        row.setLayoutParams(lp);

        CheckBox cb = new CheckBox(activity);
        cb.setText("Dépense partagée avec le foyer");
        cb.setTextColor(ThemeColors.text());
        cb.setTextSize(DS.TEXT_SM);
        cb.setTypeface(null, Typeface.BOLD);
        cb.setChecked(false);

        try {
            cb.setButtonTintList(
                    android.content.res.ColorStateList.valueOf(ThemeColors.primary()));
        } catch (Exception ignored) {}

        cb.setOnCheckedChangeListener((buttonView, checked) -> shared[0] = checked);
        row.addView(cb);
        return row;
    }

    // ─────────────────────────────────────────────────────────────
    // Info compte joint
    // ─────────────────────────────────────────────────────────────
    private static View buildJointInfo(Activity activity) {
        TextView tv = new TextView(activity);
        tv.setText("🏦  Sélectionnez \""
                + JointAccountManager.getInstance().getNameLocal()
                + "\" dans Personne pour attribuer au compte joint.");
        tv.setTextColor(ThemeColors.subtext());
        tv.setTextSize(11f);
        tv.setLineSpacing(2f, 1f);
        tv.setPadding(
                DS.dp(activity, DS.PAD_INPUT), DS.dp(activity, 8),
                DS.dp(activity, DS.PAD_INPUT), DS.dp(activity, 8));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = DS.dp(activity, DS.GAP_SM);
        tv.setLayoutParams(lp);
        return tv;
    }

    // ─────────────────────────────────────────────────────────────
    // Chip d'auto-complétion (non utilisé activement mais présent)
    // ─────────────────────────────────────────────────────────────
    private static TextView buildAutoFillCard(Activity activity) {
        TextView tv = PremiumChip.success(activity, "");
        tv.setVisibility(View.GONE);
        tv.setTextSize(DS.TEXT_SM);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(
                DS.dp(activity, DS.PAD_INPUT), DS.dp(activity, 8),
                DS.dp(activity, DS.PAD_INPUT), DS.dp(activity, 8));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = DS.dp(activity, DS.GAP_SM);
        tv.setLayoutParams(lp);
        return tv;
    }

    // ─────────────────────────────────────────────────────────────
    // Utilitaires
    // ─────────────────────────────────────────────────────────────

    private static View withBottomMargin(View view, Activity activity, int marginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = DS.dp(activity, marginDp);
        view.setLayoutParams(lp);
        return view;
    }

    private static ArrayList<String> buildCategoryNames(List<String[]> categories) {
        ArrayList<String> names = new ArrayList<>();
        if (categories != null) {
            for (String[] cat : categories) {
                if (cat != null && cat.length > 0
                        && cat[0] != null && !cat[0].trim().isEmpty()
                        && !containsIgnoreCase(names, cat[0].trim())) {
                    names.add(cat[0].trim());
                }
            }
        }
        if (names.isEmpty()) {
            names.add("Autre");
        }
        return names;
    }

    private static int findIndex(List<String> list, String value) {
        if (list == null || list.isEmpty() || value == null) return 0;
        for (int i = 0; i < list.size(); i++) {
            String item = list.get(i);
            if (item != null && item.equalsIgnoreCase(value)) return i;
        }
        return 0;
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        if (list == null || value == null) return false;
        for (String item : list) {
            if (item != null && item.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    /**
     * Fixe l'heure à 12:00:00.000 pour éviter les décalages liés
     * au changement d'heure (DST) lors du tri ou de l'affichage des dates.
     */
    private static long normalizeDate(long value) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(value > 0 ? value : System.currentTimeMillis());
        c.set(Calendar.HOUR_OF_DAY, 12);
        c.set(Calendar.MINUTE,      0);
        c.set(Calendar.SECOND,      0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static String typeToCode(String label) {
        if (label == null) return "variable";
        String clean = label.trim();
        if ("Revenu".equalsIgnoreCase(clean))      return "income";
        if ("Charge fixe".equalsIgnoreCase(clean)) return "fixed";
        return "variable";
    }
    // ─────────────────────────────────────────────────────────────
    // C1 : toggle « créer un abonnement récurrent »
    // ─────────────────────────────────────────────────────────────
    private static LinearLayout buildRecurringToggle(Activity activity, boolean[] createRecurring) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(
                DS.dp(activity, DS.PAD_INPUT), DS.dp(activity, 8),
                DS.dp(activity, DS.PAD_INPUT), DS.dp(activity, 8));
        row.setBackground(ThemeDrawable.input(activity));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = DS.dp(activity, DS.GAP_SM);
        row.setLayoutParams(lp);

        CheckBox cb = new CheckBox(activity);
        cb.setText("Créer un abonnement mensuel récurrent");
        cb.setTextColor(ThemeColors.text());
        cb.setTextSize(DS.TEXT_SM);
        cb.setTypeface(null, android.graphics.Typeface.BOLD);
        cb.setChecked(false);

        try {
            cb.setButtonTintList(
                    android.content.res.ColorStateList.valueOf(ThemeColors.primary()));
        } catch (Exception ignored) {}

        cb.setOnCheckedChangeListener((buttonView, checked) -> createRecurring[0] = checked);
        row.addView(cb);
        return row;
    }

    // ─────────────────────────────────────────────────────────────
    // C1 : créer l'abonnement dans Firestore après la transaction
    //
    // Construit un SettingsModels.FixedCharge depuis les données du
    // formulaire et délègue l'écriture à SettingsChargeWriter.
    // Déclenche ensuite checkAndApplyRecurringCharges pour que la
    // transaction du mois courant soit générée immédiatement.
    // ─────────────────────────────────────────────────────────────
    private static void createRecurringChargeFromDialog(Activity activity,
                                                         String label,
                                                         double amount,
                                                         String category,
                                                         long dateMs,
                                                         String person) {
        SettingsModels.FixedCharge charge = new SettingsModels.FixedCharge();
        charge.name     = label;
        charge.amount   = Math.abs(amount);
        charge.category = (category == null || category.trim().isEmpty())
                ? "Charges fixes" : category.trim();
        charge.icon     = guessIconForLabel(label);
        charge.paidBy   = (person == null || person.trim().isEmpty()) ? "" : person.trim();
        charge.frequency = "Mensuel";
        charge.ratioA   = 50;
        charge.ratioB   = 50;
        charge.lastAppliedMonth = "";

        // Jour de prélèvement = jour de la date choisie dans le formulaire
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(dateMs > 0 ? dateMs : System.currentTimeMillis());
        int rawDay = c.get(java.util.Calendar.DAY_OF_MONTH);
        charge.dayOfMonth = Math.max(1, Math.min(28, rawDay));

        SettingsChargeWriter.saveCharge(charge, new SettingsChargeWriter.Callback() {
            public void onSuccess() {
                // Déclencher la génération immédiate de la transaction récurrente
                RecurringChargeManager.getInstance().init(activity);
                RecurringChargeManager.getInstance().checkAndApplyRecurringCharges(null);
                AppToast.success(activity, "Abonnement \"" + label + "\" créé ✓");
            }
            public void onError(String e) {
                AppToast.error(activity, "Abonnement non créé : " + e);
            }
        });
    }

    /**
     * Mémorise la règle commerçant (label + catégorie) et l'applique en mémoire
     * à toutes les transactions dont la clé commerçant correspond.
     * Les mises à jour Firestore sont faites de manière best-effort, en arrière-plan.
     */
    private static void applyMerchantRuleToSimilar(Activity activity,
            TransactionsModels.Transaction edited,
            String newLabel, String newCategory,
            List<TransactionsModels.Transaction> allTx) {

        MerchantRuleManager mgr = MerchantRuleManager.getInstance();
        String key = mgr.resolveMerchantKey(edited.description());
        if (key == null || key.isEmpty()) return;

        boolean labelChanged    = !newLabel.equals(edited.description());
        boolean categoryChanged = newCategory != null && !newCategory.equals(edited.category);

        if (labelChanged)    mgr.saveLabelRule(key, newLabel);
        if (categoryChanged) mgr.saveCategoryRule(key, newCategory);

        if (allTx == null || allTx.isEmpty()) return;
        if (!labelChanged && !categoryChanged) return;

        for (TransactionsModels.Transaction t : allTx) {
            if (t.docId.equals(edited.docId)) continue;
            String tKey = mgr.resolveMerchantKey(t.description());
            if (!key.equals(tKey)) continue;

            String updLabel = labelChanged    ? newLabel    : t.description();
            String updCat   = categoryChanged ? newCategory : t.category;

            TransactionsRepository.updateTransaction(
                    t.docId, updLabel, t.amount, t.type, updCat,
                    t.dateMs, t.person, t.shared, t.compte, activity,
                    new TransactionsRepository.OnWriteComplete() {
                        public void onSuccess() {}
                        public void onError(String e) {}
                    });
        }

        if (labelChanged || categoryChanged) {
            int count = 0;
            for (TransactionsModels.Transaction t : allTx) {
                if (!t.docId.equals(edited.docId)
                        && key.equals(mgr.resolveMerchantKey(t.description()))) count++;
            }
            if (count > 0) {
                AppToast.success(activity, count + " transaction(s) similaire(s) mise(s) à jour ✓");
            }
        }
    }

    /** Devine une icône simple selon le libellé pour le FixedCharge. */
    private static String guessIconForLabel(String label) {
        if (label == null) return "💳";
        String l = label.toLowerCase(java.util.Locale.FRANCE);
        if (l.contains("loyer") || l.contains("bail"))       return "🏠";
        if (l.contains("edf") || l.contains("électricité"))  return "⚡";
        if (l.contains("gaz"))                               return "🔥";
        if (l.contains("eau"))                               return "💧";
        if (l.contains("internet") || l.contains("fibre"))   return "🌐";
        if (l.contains("téléphone") || l.contains("mobile")) return "📱";
        if (l.contains("netflix") || l.contains("canal"))    return "📺";
        if (l.contains("spotify") || l.contains("musique"))  return "🎵";
        if (l.contains("assurance"))                         return "🛡️";
        if (l.contains("crédit") || l.contains("prêt"))      return "🏦";
        if (l.contains("salle") || l.contains("sport"))      return "🏋️";
        if (l.contains("transport") || l.contains("navigo")) return "🚆";
        return "💳";
    }

}
