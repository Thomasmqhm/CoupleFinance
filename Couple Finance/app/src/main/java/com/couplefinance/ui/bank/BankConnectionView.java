package com.couplefinance.ui.bank;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.couplefinance.AppToast;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.AppDialog;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.UiFactory;
import com.couplefinance.data.BankImportPipeline;
import com.couplefinance.data.CategoryManager;
import com.couplefinance.data.CreditManager;
import com.couplefinance.ui.credits.CreditsModels;
import com.couplefinance.ui.credits.CreditsParser;
import com.couplefinance.data.CycleManager;
import com.couplefinance.ui.credits.CreditsModels;
import com.couplefinance.ui.credits.CreditsParser;
import com.couplefinance.data.FirestoreManager;
import com.couplefinance.data.EnableBankingManager;
import com.couplefinance.data.JointAccountManager;
import com.couplefinance.data.MerchantRuleManager;
import com.couplefinance.ui.transactions.TransactionsModels;
import com.couplefinance.ui.transactions.TransactionsRepository;
import com.couplefinance.utils.ParsedTransaction;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * BankConnectionView — UI de connexion bancaire Enable Banking.
 *
 * Flux :
 *   1. Setup (Application ID + clé privée)
 *   2. Sélection banque (mémorisée après premier choix)
 *   3. WebView OAuth
 *   4. Attribution des comptes (IBAN → Thomas / Mélissa / Joint)
 *   5. Sync / Preview / Import
 *   6. Affichage compact des soldes
 */
public final class BankConnectionView {

    private BankConnectionView() {}

    // ─────────────────────────────────────────────────────────────
    // Point d'entrée
    // ─────────────────────────────────────────────────────────────

    private static Runnable sOnComplete;

    private static void fireComplete(Activity a) {
        Runnable r = sOnComplete;
        sOnComplete = null;
        if (r == null) return;
        if (a != null) a.runOnUiThread(r); else r.run();
    }

    public static void show(Activity a, List<TransactionsModels.Transaction> existing) {
        show(a, existing, null);
    }

    public static void show(Activity a, List<TransactionsModels.Transaction> existing, Runnable onComplete) {
        if (a == null) return;
        sOnComplete = onComplete;
        EnableBankingManager.getInstance().init(a);
        MerchantRuleManager.getInstance().init(a);
        if (!EnableBankingManager.getInstance().isConfigured()) showSetupDialog(a, existing);
        else if (!EnableBankingManager.getInstance().isConnected())   showConnectMenu(a, existing);
        else                                                           showSyncMenu(a, existing);
    }

    // ─────────────────────────────────────────────────────────────
    // Étape 0 — Setup credentials
    // ─────────────────────────────────────────────────────────────

    private static void showSetupDialog(Activity a, List<TransactionsModels.Transaction> existing) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);

        c.addView(label(a, "Application ID"));
        EditText inputId = editText(a, "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx", false);
        c.addView(inputId, marginBottom(a));

        c.addView(label(a, "Clé privée RSA (PEM)"));
        EditText inputKey = editText(a, "-----BEGIN PRIVATE KEY-----\nMIIE...", true);
        c.addView(inputKey);

        final AlertDialog[] h = {null};
        h[0] = new AppDialog.Builder(a)
                .icon("🔑").title("Connexion bancaire").subtitle("Configurer Enable Banking")
                .content(c)
                .primaryBtn("CONTINUER", () -> {
                    String id  = inputId.getText().toString().trim();
                    String key = inputKey.getText().toString().trim();
                    if (id.isEmpty() || key.isEmpty()) {
                        AppToast.error(a, "Application ID et clé obligatoires");
                        return;
                    }
                    EnableBankingManager.getInstance().configure(id, key);
                    dismiss(h); showConnectMenu(a, existing);
                }).show();
    }

    // ─────────────────────────────────────────────────────────────
    // Étape 1 — Menu connexion (banque pas encore connectée)
    // ─────────────────────────────────────────────────────────────

    private static void showConnectMenu(Activity a, List<TransactionsModels.Transaction> existing) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        final AlertDialog[] h = {null};

        String savedBank = EnableBankingManager.getInstance().getSavedBankName();

        if (!savedBank.isEmpty()) {
            // Banque mémorisée → bouton express
            View reconnect = choiceRow(a, "🏦  " + savedBank,
                    "Reconnecter avec cette banque (mémorisée)");
            reconnect.setOnClickListener(v -> {
                dismiss(h);
                startBankOAuth(a, savedBank, savedBank, existing);
            });
            c.addView(reconnect);

            View change = choiceRow(a, "🔎  Choisir une autre banque",
                    "Sélectionner un autre établissement");
            change.setOnClickListener(v -> { dismiss(h); showBankPickerLoading(a, existing); });
            c.addView(change, marginTop(a));
        } else {
            View connect = choiceRow(a, "🏦  Connecter ma banque",
                    "Choisir votre banque française");
            connect.setOnClickListener(v -> { dismiss(h); showBankPickerLoading(a, existing); });
            c.addView(connect);
        }

        View settings = choiceRow(a, "⚙️  Modifier les identifiants",
                "Changer Application ID / Clé privée");
        settings.setOnClickListener(v -> {
            dismiss(h);
            EnableBankingManager.getInstance().clearAll();
            showSetupDialog(a, existing);
        });
        c.addView(settings, marginTop(a));

        h[0] = new AppDialog.Builder(a).icon("🏦").title("Connexion bancaire")
                .subtitle("Open Banking · Enable Banking")
                .content(c).primaryBtn("FERMER", () -> dismiss(h)).show();
    }

    // ─────────────────────────────────────────────────────────────
    // Étape 2 — Chargement + sélection de la banque
    // ─────────────────────────────────────────────────────────────

    private static void showBankPickerLoading(Activity a, List<TransactionsModels.Transaction> existing) {
        final AlertDialog[] h = spinnerDialog(a, "🔎", "Chargement des banques…", "");
        EnableBankingManager.getInstance().getInstitutions("FR", new EnableBankingManager.InstitutionsCallback() {
            @Override public void onResult(List<EnableBankingManager.Institution> list) {
                dismiss(h);
                if (list.isEmpty()) { AppToast.error(a, "Aucune banque disponible"); return; }
                showBankPicker(a, list, existing);
            }
            @Override public void onError(String error) {
                dismiss(h); AppToast.error(a, "Erreur : " + error);
            }
        });
    }

    // Familles régionales à regrouper : libellé affiché + mot-clé (sans accents/min.)
    private static final String[][] BANK_GROUPS = {
            {"Caisse d'Épargne", "caisse d'epargne"},
            {"Crédit Agricole", "credit agricole"},
            {"Crédit Mutuel", "credit mutuel"}
    };

    // Banques importantes individuelles (mots-clés, sans accents/min.)
    private static final String[] FEATURED_SINGLE = {
            "boursorama", "bnp paribas", "fortuneo", "hello bank", "la banque postale",
            "lcl", "monabanq", "n26", "revolut", "societe generale"
    };

    private static String stripAccents(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s.toLowerCase(Locale.FRANCE), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private static View makeBankRow(Activity a, AlertDialog[] h,
            EnableBankingManager.Institution bank, List<TransactionsModels.Transaction> existing) {
        View row = choiceRow(a, "🏦  " + bank.name,
                bank.bic.isEmpty() ? "Banque française" : "BIC : " + bank.bic);
        row.setOnClickListener(v -> {
            dismiss(h);
            EnableBankingManager.getInstance().saveBankSelection(bank.name, bank.id);
            startBankOAuth(a, bank.id, bank.name, existing);
        });
        return row;
    }

    private static void showBankPicker(Activity a, List<EnableBankingManager.Institution> banks,
                                        List<TransactionsModels.Transaction> existing) {
        ScrollView scroll = new ScrollView(a);
        final LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(c);
        final AlertDialog[] h = {null};

        // Répartition : familles régionales / banques importantes / reste
        java.util.LinkedHashMap<String, List<EnableBankingManager.Institution>> groups = new java.util.LinkedHashMap<>();
        for (String[] g : BANK_GROUPS) groups.put(g[0], new ArrayList<>());
        List<EnableBankingManager.Institution> singles = new ArrayList<>();
        final List<EnableBankingManager.Institution> others = new ArrayList<>();

        for (EnableBankingManager.Institution bank : banks) {
            String n = stripAccents(bank.name);
            String groupLabel = null;
            for (String[] g : BANK_GROUPS) if (n.contains(g[1])) { groupLabel = g[0]; break; }
            if (groupLabel != null) { groups.get(groupLabel).add(bank); continue; }
            boolean feat = false;
            for (String k : FEATURED_SINGLE) if (n.contains(k)) { feat = true; break; }
            if (feat) singles.add(bank); else others.add(bank);
        }

        // Entrées primaires : {libellé, banques, estGroupe}
        List<Object[]> entries = new ArrayList<>();
        for (java.util.Map.Entry<String, List<EnableBankingManager.Institution>> ge : groups.entrySet()) {
            List<EnableBankingManager.Institution> list = ge.getValue();
            if (list.isEmpty()) continue;
            if (list.size() == 1) entries.add(new Object[]{ list.get(0).name, list, Boolean.FALSE });
            else entries.add(new Object[]{ ge.getKey(), list, Boolean.TRUE });
        }
        for (EnableBankingManager.Institution b : singles)
            entries.add(new Object[]{ b.name, java.util.Collections.singletonList(b), Boolean.FALSE });

        // Si rien n'a matché (ex. autre pays), afficher tout
        if (entries.isEmpty()) {
            for (EnableBankingManager.Institution bank : banks) c.addView(makeBankRow(a, h, bank, existing));
            h[0] = new AppDialog.Builder(a).icon("🏦")
                    .title("Choisir votre banque").subtitle(banks.size() + " établissements")
                    .content(scroll).primaryBtn("ANNULER", () -> dismiss(h)).show();
            return;
        }

        // Tri alphabétique (sans accents)
        java.util.Collections.sort(entries, (x, y) ->
                stripAccents((String) x[0]).compareTo(stripAccents((String) y[0])));

        for (Object[] e : entries) {
            final String label = (String) e[0];
            @SuppressWarnings("unchecked")
            final List<EnableBankingManager.Institution> list = (List<EnableBankingManager.Institution>) e[1];
            boolean isGroup = (Boolean) e[2];
            if (isGroup) {
                final View groupRow = choiceRow(a, "🏦  " + label, list.size() + " établissements  ›");
                groupRow.setOnClickListener(v -> {
                    int[] at = { c.indexOfChild(groupRow) };
                    c.removeView(groupRow);
                    for (EnableBankingManager.Institution b : list)
                        c.addView(makeBankRow(a, h, b, existing), at[0]++);
                });
                c.addView(groupRow);
            } else {
                c.addView(makeBankRow(a, h, list.get(0), existing));
            }
        }

        if (!others.isEmpty()) {
            final TextView more = new TextView(a);
            more.setText("+  Afficher toutes les banques (" + others.size() + ")");
            more.setGravity(Gravity.CENTER);
            more.setTextColor(ThemeColors.primary());
            more.setTypeface(null, Typeface.BOLD);
            more.setTextSize(DS.TEXT_SM);
            more.setPadding(0, DS.dp(a, 16), 0, DS.dp(a, 8));
            more.setOnClickListener(v -> {
                c.removeView(more);
                for (EnableBankingManager.Institution bank : others) c.addView(makeBankRow(a, h, bank, existing));
            });
            c.addView(more);
        }

        h[0] = new AppDialog.Builder(a).icon("🏦")
                .title("Choisir votre banque").subtitle(banks.size() + " établissements")
                .content(scroll).primaryBtn("ANNULER", () -> dismiss(h)).show();
    }

    // ─────────────────────────────────────────────────────────────
    // Étape 3 — WebView OAuth
    // ─────────────────────────────────────────────────────────────

    private static void startBankOAuth(Activity a, String bankId, String bankName,
                                        List<TransactionsModels.Transaction> existing) {
        final AlertDialog[] h = spinnerDialog(a, "🔗", "Connexion à " + bankName, "Création du lien…");
        EnableBankingManager.getInstance().createRequisition(bankId, new EnableBankingManager.Callback() {
            @Override public void onSuccess(String oauthLink) {
                dismiss(h); showWebView(a, oauthLink, bankName, existing);
            }
            @Override public void onError(String error) {
                dismiss(h); AppToast.error(a, "Connexion impossible : " + error);
            }
        });
    }

    private static void showWebView(Activity a, String oauthLink, String bankName,
                                     List<TransactionsModels.Transaction> existing) {
        WebView wv = new WebView(a);
        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true); ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true); ws.setUseWideViewPort(true);
        final AlertDialog[] h = {null};

        wv.setWebViewClient(new WebViewClient() {
            private void intercept(String url) {
                if (url.startsWith(EnableBankingManager.REDIRECT_URI)) {
                    dismiss(h); handleOAuthCallback(a, url, existing);
                }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                intercept(r.getUrl().toString()); return false;
            }
            @Override @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                intercept(url); return false;
            }
        });
        wv.loadUrl(oauthLink);

        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        TextView hint = UiFactory.bodyMuted(a,
                "Connectez-vous à " + bankName + " et autorisez l'accès.");
        hint.setTextSize(DS.TEXT_XS);
        c.addView(hint, marginBottom(a));
        c.addView(wv, new LinearLayout.LayoutParams(-1, DS.dp(a, 500)));

        h[0] = new AppDialog.Builder(a).icon("🔐")
                .title("Autorisation " + bankName).subtitle("Connexion sécurisée")
                .content(c).primaryBtn("ANNULER", () -> dismiss(h)).show();
    }

    // ─────────────────────────────────────────────────────────────
    // Étape 4 — Callback OAuth → récupération des comptes
    // ─────────────────────────────────────────────────────────────

    private static void handleOAuthCallback(Activity a, String callbackUrl,
                                             List<TransactionsModels.Transaction> existing) {
        final AlertDialog[] h = spinnerDialog(a, "☁️", "Synchronisation", "Récupération des comptes…");
        EnableBankingManager.getInstance().fetchAccounts(callbackUrl, new EnableBankingManager.Callback() {
            @Override public void onSuccess(String accountsCsv) {
                dismiss(h);
                // Afficher l'écran d'attribution des comptes
                showAccountAssignment(a, existing);
            }
            @Override public void onError(String error) {
                dismiss(h); AppToast.error(a, "Erreur : " + error);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────
    // Étape 5 — Attribution des comptes (IBAN → propriétaire)
    // ─────────────────────────────────────────────────────────────

    /**
     * Charge les vrais membres du foyer, puis affiche le dialog d'attribution.
     * Les options de chaque compte = noms réels des membres + « Compte joint ».
     */
    private static void showAccountAssignment(Activity a, List<TransactionsModels.Transaction> existing) {
        int count = EnableBankingManager.getInstance().getSavedAccountIds().size();
        if (count == 0) { startSync(a, existing, 1); return; }

        // Charger les membres réels du foyer
        TransactionsRepository.loadAll(a, new TransactionsRepository.OnDataLoaded() {
            @Override public void onLoaded(List<TransactionsModels.Transaction> tx,
                                           List<String> members, List<String[]> cats) {
                List<String> names = new ArrayList<>();
                if (members != null) for (String m : members)
                    if (m != null && !m.trim().isEmpty()) names.add(m.trim());
                buildAssignmentDialog(a, existing, names);
            }
            @Override public void onError(String message) {
                buildAssignmentDialog(a, existing, new ArrayList<>());
            }
        });
    }

    /**
     * Construit le dialog d'attribution avec les vrais noms de membres.
     * Valeur stockée par compte : le nom réel du membre, ou "joint".
     */
    private static void buildAssignmentDialog(Activity a,
            List<TransactionsModels.Transaction> existing, List<String> memberNames) {

        List<String> ibans  = EnableBankingManager.getInstance().getAccountIbans();
        List<String> names  = EnableBankingManager.getInstance().getAccountNames();
        List<String> owners = EnableBankingManager.getInstance().getAccountOwners();
        int count = EnableBankingManager.getInstance().getSavedAccountIds().size();

        // Options = membres réels + "Compte joint"
        List<String> options = new ArrayList<>(memberNames);
        options.add("Compte joint");

        ScrollView scroll = new ScrollView(a);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root);

        TextView intro = UiFactory.bodyMuted(a,
                "Associez chaque compte à son propriétaire. "
                + "Décochez un compte pour l'exclure de la synchronisation.");
        intro.setTextSize(DS.TEXT_SM);
        root.addView(intro, marginBottom(a));

        List<RadioGroup> radioGroups = new ArrayList<>();
        final List<android.widget.CheckBox> includeBoxes = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String iban     = i < ibans.size()  ? ibans.get(i)  : "";
            String accName  = i < names.size()  ? names.get(i)  : "Compte " + (i+1);
            String curOwner = i < owners.size() ? owners.get(i) : "";

            TextView header = new TextView(a);
            String ibanShort = iban.length() > 4 ? "..." + iban.substring(iban.length()-5) : iban;
            header.setText("🏦 " + accName + (!ibanShort.isEmpty() ? "  (" + ibanShort + ")" : ""));
            header.setTextSize(DS.TEXT_SM);
            header.setTypeface(null, Typeface.BOLD);
            header.setTextColor(ThemeColors.text());
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1,-2);
            hp.topMargin = i > 0 ? DS.dp(a, DS.GAP) : 0;
            hp.bottomMargin = DS.dp(a, DS.GAP_SM);
            root.addView(header, hp);

            final android.widget.CheckBox include = new android.widget.CheckBox(a);
            include.setText("Inclure ce compte");
            include.setTextColor(ThemeColors.subtext());
            include.setTextSize(DS.TEXT_SM);
            include.setChecked(!"__skip__".equals(curOwner));
            root.addView(include);
            includeBoxes.add(include);

            RadioGroup rg = new RadioGroup(a);
            rg.setOrientation(RadioGroup.HORIZONTAL);
            int selectedId = -1;

            for (int j = 0; j < options.size(); j++) {
                String label = options.get(j);
                // Valeur stockée : "joint" pour le compte joint, sinon le nom réel
                String value = (j == options.size() - 1) ? "joint" : label;

                RadioButton rb = new RadioButton(a);
                rb.setId(i * 100 + j);
                rb.setText(label);
                rb.setTextColor(ThemeColors.text());
                rb.setTextSize(DS.TEXT_SM);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2,-2);
                rp.rightMargin = DS.dp(a, DS.GAP);
                rg.addView(rb, rp);
                if (value.equals(curOwner)) selectedId = rb.getId();
            }
            if (selectedId != -1) rg.check(selectedId);
            root.addView(rg);
            radioGroups.add(rg);

            final RadioGroup rgF = rg;
            setGroupEnabled(rgF, include.isChecked());
            include.setOnCheckedChangeListener((b, checked) -> setGroupEnabled(rgF, checked));
        }

        final List<String> optionsFinal = options;
        final AlertDialog[] h = {null};
        h[0] = new AppDialog.Builder(a).icon("👥")
                .title("Attribuer les comptes").subtitle("Qui possède chaque compte ?")
                .content(scroll)
                .primaryBtn("CONTINUER", () -> {
                    for (int i = 0; i < radioGroups.size(); i++) {
                        if (i < includeBoxes.size() && !includeBoxes.get(i).isChecked()) {
                            EnableBankingManager.getInstance().saveAccountOwner(i, "__skip__");
                            continue;
                        }
                        int checked = radioGroups.get(i).getCheckedRadioButtonId();
                        if (checked >= 0) {
                            int j = checked - i * 100;
                            if (j >= 0 && j < optionsFinal.size()) {
                                String value = (j == optionsFinal.size() - 1)
                                        ? "joint" : optionsFinal.get(j);
                                EnableBankingManager.getInstance().saveAccountOwner(i, value);
                            }
                        }
                    }
                    dismiss(h);
                    AppToast.success(a, "Comptes attribués ✓");
                    startSync(a, existing, 1);
                }).show();
    }

    private static void setGroupEnabled(RadioGroup rg, boolean enabled) {
        rg.setAlpha(enabled ? 1f : 0.4f);
        for (int k = 0; k < rg.getChildCount(); k++) rg.getChildAt(k).setEnabled(enabled);
    }

    // ─────────────────────────────────────────────────────────────
    // Étape 6 — Sync
    // ─────────────────────────────────────────────────────────────

    public static void startSync(Activity a, List<TransactionsModels.Transaction> existing,
                                  int monthsBack) {
        String[] range = monthsBack == 1
                ? BankImportPipeline.getCurrentCycleDateRange()
                : BankImportPipeline.getLastMonthsDateRange(monthsBack);
        String dateFrom = range[0]; String dateTo = range[1];

        final AlertDialog[] h = spinnerDialog(a, "⬇️", "Téléchargement",
                "Open Banking · " + dateFrom + " → " + dateTo);

        EnableBankingManager.getInstance().syncAllAccounts(dateFrom, dateTo,
                new EnableBankingManager.TransactionsCallback() {
                    @Override public void onResult(List<EnableBankingManager.BankTransaction> txList) {
                        dismiss(h);
                        if (txList == null || txList.isEmpty()) {
                            AppToast.info(a, "Aucune transaction sur la période.");
                            fireComplete(a);
                            return;
                        }
                        loadCategoriesAndPreview(a, txList, existing);
                    }
                    @Override public void onError(String error) {
                        dismiss(h);
                        if (error != null && error.contains("ASPSP_ERROR")) {
                            showAspspErrorDialog(a, existing, error);
                        } else {
                            AppToast.error(a, "Sync échouée : " + error);
                        }
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────
    // Pipeline catégories + preview
    // ─────────────────────────────────────────────────────────────

    private static void loadCategoriesAndPreview(Activity a,
            List<EnableBankingManager.BankTransaction> bankTx,
            List<TransactionsModels.Transaction> existing) {

        CategoryManager.getInstance().getCategories(new FirestoreManager.Callback() {
            @Override public void onSuccess(String response) {
                List<String> cats = parseCategoryNames(response);
                List<ParsedTransaction> parsed = BankImportPipeline.enrich(bankTx, a, cats);
                BankImportPipeline.detectDuplicates(parsed, existing);
                // Cross-check against managed credits to auto-uncheck matching transactions
                CreditManager.getInstance().getCredits(new FirestoreManager.Callback() {
                    @Override public void onSuccess(String creditsJson) {
                        List<CreditsModels.Credit> credits = CreditsParser.parseCredits(creditsJson);
                        BankImportPipeline.markCreditDuplicates(parsed, credits);
                        showPreview(a, parsed, existing, cats);
                    }
                    @Override public void onError(String e) {
                        showPreview(a, parsed, existing, cats);
                    }
                });
            }
            @Override public void onError(String error) {
                List<ParsedTransaction> parsed = BankImportPipeline.enrich(bankTx, a, null);
                BankImportPipeline.detectDuplicates(parsed, existing);
                showPreview(a, parsed, existing, new ArrayList<>());
            }
        });
    }

    private static void showPreview(Activity a, List<ParsedTransaction> parsed,
                                     List<TransactionsModels.Transaction> existing,
                                     List<String> categories) {
        if (parsed.isEmpty()) { AppToast.info(a, "Aucune transaction à importer"); return; }

        int dupCount = 0; int selCount = 0;
        for (ParsedTransaction pt : parsed) { if (pt.duplicate) dupCount++; else selCount++; }

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView header = UiFactory.bodyMuted(a, selCount + " nouvelle(s)"
                + (dupCount > 0 ? " · " + dupCount + " doublon(s)" : "")
                + "  ·  Touchez une ligne pour l'éditer");
        header.setTextSize(DS.TEXT_SM);
        root.addView(header, marginBottom(a));

        // ── Comptes présents ─────────────────────────────────────────
        List<Integer> accountIdx = new ArrayList<>();
        for (ParsedTransaction pt : parsed)
            if (!accountIdx.contains(pt.accountIndex)) accountIdx.add(pt.accountIndex);
        java.util.Collections.sort(accountIdx);

        // ── Mois présents (clé "YYYY-MM", triés décroissant) ─────────
        List<String> monthKeys = new ArrayList<>();
        for (ParsedTransaction pt : parsed) {
            String mk = monthKey(pt.dateMs);
            if (!monthKeys.contains(mk)) monthKeys.add(mk);
        }
        java.util.Collections.sort(monthKeys, java.util.Collections.reverseOrder());

        // Conteneur de la liste (rempli/filtré dynamiquement)
        final LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);

        final int[] activeAccount = { -2 };       // -2 = tous comptes
        final String[] activeMonth = { "" };       // "" = tous mois
        final List<TextView> accTabs   = new ArrayList<>();
        final List<TextView> monthTabs = new ArrayList<>();

        // Renvoie true si pt passe les filtres actifs
        final java.util.function.Predicate<ParsedTransaction> visible = pt -> {
            if (activeAccount[0] != -2 && pt.accountIndex != activeAccount[0]) return false;
            if (!activeMonth[0].isEmpty() && !monthKey(pt.dateMs).equals(activeMonth[0])) return false;
            return true;
        };

        final Runnable[] refilterRef = new Runnable[1];
        refilterRef[0] = () -> {
            list.removeAllViews();
            for (ParsedTransaction pt : parsed) {
                if (!visible.test(pt)) continue;
                list.addView(previewRow(a, pt, categories, parsed, refilterRef[0]));
            }
            if (list.getChildCount() == 0) {
                TextView empty = UiFactory.bodyMuted(a, "Aucune opération ici");
                empty.setTextSize(DS.TEXT_SM);
                list.addView(empty);
            }
        };

        // ── Onglets COMPTE ───────────────────────────────────────────
        if (accountIdx.size() > 1) {
            root.addView(buildTabRow(a, "Compte :", accTabs, () -> refilterRef[0].run(),
                    activeAccount, accountIdx, true));
        }

        // ── Onglets MOIS ─────────────────────────────────────────────
        if (monthKeys.size() > 1) {
            HorizontalScrollView ms = new HorizontalScrollView(a);
            ms.setHorizontalScrollBarEnabled(false);
            LinearLayout mtabs = new LinearLayout(a);
            mtabs.setOrientation(LinearLayout.HORIZONTAL);

            TextView mAll = makeTab(a, "Tous mois", true);
            monthTabs.add(mAll);
            mAll.setOnClickListener(v -> {
                activeMonth[0] = "";
                for (TextView t : monthTabs) styleTab(a, t, false);
                styleTab(a, mAll, true);
                refilterRef[0].run();
            });
            mtabs.addView(mAll);

            for (String mk : monthKeys) {
                final String key = mk;
                TextView t = makeTab(a, monthLabel(mk), false);
                monthTabs.add(t);
                t.setOnClickListener(v -> {
                    activeMonth[0] = key;
                    for (TextView tv : monthTabs) styleTab(a, tv, false);
                    styleTab(a, t, true);
                    refilterRef[0].run();
                });
                LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2,-2);
                tp.leftMargin = DS.dp(a, DS.GAP_SM);
                mtabs.addView(t, tp);
            }
            ms.addView(mtabs);
            root.addView(ms, marginBottom(a));
        }

        // ── Bouton tout cocher / décocher (sur les lignes visibles) ──
        TextView selectAll = new TextView(a);
        selectAll.setText("☑️  Tout cocher / décocher");
        selectAll.setTextColor(ThemeColors.primary());
        selectAll.setTextSize(DS.TEXT_SM);
        selectAll.setTypeface(null, Typeface.BOLD);
        selectAll.setPadding(0, 0, 0, DS.dp(a, DS.GAP_SM));
        selectAll.setOnClickListener(v -> {
            // Détermine la cible : si au moins une visible est cochée → tout décocher
            boolean anyChecked = false;
            for (ParsedTransaction pt : parsed)
                if (visible.test(pt) && !pt.duplicate && pt.selected) { anyChecked = true; break; }
            boolean target = !anyChecked;
            for (ParsedTransaction pt : parsed)
                if (visible.test(pt) && !pt.duplicate) pt.selected = target;
            refilterRef[0].run();
        });
        root.addView(selectAll);

        refilterRef[0].run();

        ScrollView sv = new ScrollView(a);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, DS.dp(a, 380)));

        TextView cycleNote = UiFactory.bodyMuted(a,
                "Cycle : " + CycleManager.getInstance().getCurrentCycleLabel());
        cycleNote.setTextSize(DS.TEXT_XS);
        root.addView(cycleNote, marginTop(a));

        final AlertDialog[] h = {null};
        h[0] = new AppDialog.Builder(a).icon("📋")
                .title("Aperçu").subtitle("Vérifiez avant d'importer")
                .content(root)
                .primaryBtn("IMPORTER", () -> {
                    dismiss(h); executeImport(a, parsed, existing);
                }).show();
    }

    /** Construit une rangée d'onglets pour les comptes. */
    private static HorizontalScrollView buildTabRow(Activity a, String prefix,
            List<TextView> store, Runnable refilter, int[] active,
            List<Integer> accountIdx, boolean isAccount) {
        HorizontalScrollView scroll = new HorizontalScrollView(a);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(a);
        tabs.setOrientation(LinearLayout.HORIZONTAL);

        TextView tAll = makeTab(a, "Tous", true);
        store.add(tAll);
        tAll.setOnClickListener(v -> {
            active[0] = -2;
            for (TextView t : store) styleTab(a, t, false);
            styleTab(a, tAll, true);
            refilter.run();
        });
        tabs.addView(tAll);

        for (int idx : accountIdx) {
            final int accIndex = idx;
            TextView t = makeTab(a, accountTabLabel(accIndex), false);
            store.add(t);
            t.setOnClickListener(v -> {
                active[0] = accIndex;
                for (TextView tv : store) styleTab(a, tv, false);
                styleTab(a, t, true);
                refilter.run();
            });
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2,-2);
            tp.leftMargin = DS.dp(a, DS.GAP_SM);
            tabs.addView(t, tp);
        }
        scroll.addView(tabs);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin = DS.dp(a, DS.GAP_SM);
        scroll.setLayoutParams(lp);
        return scroll;
    }

    /** Clé de mois "YYYY-MM" depuis un timestamp. */
    private static String monthKey(long ms) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(ms);
        return String.format(Locale.US, "%04d-%02d",
                c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1);
    }

    /** Libellé lisible d'un mois ("mai 2026"). */
    private static String monthLabel(String key) {
        String[] mois = {"janv.","févr.","mars","avr.","mai","juin",
                "juil.","août","sept.","oct.","nov.","déc."};
        try {
            String[] p = key.split("-");
            int y = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]);
            return mois[m - 1] + " " + y;
        } catch (Exception e) { return key; }
    }

    /** Libellé d'un onglet de compte : nom réel du membre / "Joint" / nom du compte. */
    private static String accountTabLabel(int accIndex) {
        if (accIndex < 0) return "Autre";
        List<String> owners = EnableBankingManager.getInstance().getAccountOwners();
        List<String> names  = EnableBankingManager.getInstance().getAccountNames();
        if (accIndex < owners.size()) {
            String o = owners.get(accIndex);
            if ("joint".equals(o)) return "Compte joint";
            if (o != null && !o.isEmpty()) return o;
        }
        if (accIndex < names.size() && names.get(accIndex) != null && !names.get(accIndex).isEmpty())
            return names.get(accIndex);
        return "Compte " + (accIndex + 1);
    }

    /** Crée un onglet pill (chip) du modal d'aperçu. */
    private static TextView makeTab(Activity a, String label, boolean active) {
        TextView t = new TextView(a);
        t.setText(label);
        t.setTextSize(DS.TEXT_SM);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(DS.dp(a,16), DS.dp(a,8), DS.dp(a,16), DS.dp(a,8));
        styleTab(a, t, active);
        return t;
    }

    private static void styleTab(Activity a, TextView t, boolean active) {
        if (active) {
            t.setTextColor(Color.WHITE);
            t.setBackground(pillBg(ThemeColors.primary()));
        } else {
            t.setTextColor(ThemeColors.subtext());
            t.setBackground(pillBg(ThemeColors.card()));
        }
    }

    /** Fond arrondi plein (pilule) pour les onglets. */
    private static android.graphics.drawable.GradientDrawable pillBg(int color) {
        android.graphics.drawable.GradientDrawable g =
                new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        g.setColor(color);
        g.setCornerRadius(999f);
        return g;
    }

    private static View previewRow(Activity a, ParsedTransaction pt, List<String> categories,
                                    List<ParsedTransaction> all, Runnable onChanged) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(DS.dp(a,10), DS.dp(a,8), DS.dp(a,10), DS.dp(a,8));
        row.setBackground(UiFactory.bgBordered(ThemeColors.card(), ThemeColors.border(), DS.R_SM, a));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1,-2);
        rp.bottomMargin = DS.dp(a,4); row.setLayoutParams(rp);

        android.widget.CheckBox cb = new android.widget.CheckBox(a);
        cb.setChecked(pt.selected); cb.setEnabled(!pt.duplicate);
        cb.setOnCheckedChangeListener((v,c) -> pt.selected = c);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2,-2);
        cp.rightMargin = DS.dp(a,6); row.addView(cb, cp);

        LinearLayout info = new LinearLayout(a);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));

        // Libellé tappable → édition
        TextView lv = new TextView(a);
        String ownerBadge = "joint".equals(pt.owner) ? "🏠 "
                : (pt.owner != null && !pt.owner.isEmpty()) ? "👤 " : "";
        lv.setText(pt.duplicate ? "⚠️ " + pt.label : ownerBadge + pt.label + "  ✎");
        lv.setTextSize(DS.TEXT_SM);
        lv.setTextColor(pt.duplicate ? ThemeColors.subtext() : ThemeColors.text());
        lv.setTypeface(null, Typeface.BOLD); lv.setSingleLine(true);
        lv.setOnClickListener(v -> showEditTransactionDialog(a, pt, categories, all, onChanged));
        info.addView(lv);

        String ownerLbl = "joint".equals(pt.owner) ? "Compte joint"
                : (pt.owner != null && !pt.owner.isEmpty()) ? pt.owner : "";

        // Catégorie tappable (chip)
        final TextView mv = new TextView(a);
        mv.setTextColor(ThemeColors.primary());
        mv.setTextSize(DS.TEXT_XS);
        mv.setTypeface(null, Typeface.BOLD);
        updateCategoryChip(mv, pt, ownerLbl);
        mv.setOnClickListener(v -> showCategoryPicker(a, pt, categories, () -> {
            // Appliquer la catégorie aux transactions similaires + mémoriser
            rememberCategoryAndApplyToSimilar(pt, all);
            if (onChanged != null) onChanged.run();
        }));
        info.addView(mv);
        row.addView(info);

        TextView av = new TextView(a);
        boolean inc = "income".equals(pt.type);
        av.setText((inc?"+":"-") + String.format(Locale.FRANCE,"%.2f €",pt.amount));
        av.setTextSize(DS.TEXT_SM);
        av.setTextColor(inc ? Color.parseColor("#059669") : ThemeColors.text());
        av.setTypeface(null, Typeface.BOLD); av.setGravity(Gravity.END);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-2,-2);
        ap.leftMargin = DS.dp(a,6); row.addView(av,ap);
        return row;
    }

    /** Dialog d'édition : libellé + catégorie (comme l'import PDF). */
    private static void showEditTransactionDialog(Activity a, ParsedTransaction pt,
            List<String> categories, List<ParsedTransaction> all, Runnable onChanged) {

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);

        TextView lblHint = UiFactory.bodyMuted(a, "Libellé");
        lblHint.setTextSize(DS.TEXT_XS);
        box.addView(lblHint);

        EditText input = editText(a, "Libellé", false);
        input.setText(pt.label);
        box.addView(input);

        TextView note = UiFactory.bodyMuted(a,
                "Les transactions similaires (même commerçant) seront aussi mises à jour, "
                + "maintenant et lors des prochains imports.");
        note.setTextSize(DS.TEXT_XS);
        box.addView(note, marginTop(a));

        // Bouton catégorie
        final TextView catBtn = new TextView(a);
        catBtn.setTextColor(ThemeColors.primary());
        catBtn.setTextSize(DS.TEXT_SM);
        catBtn.setTypeface(null, Typeface.BOLD);
        catBtn.setPadding(0, DS.dp(a, DS.GAP_SM), 0, 0);
        catBtn.setText("🏷️ Catégorie : " + (pt.category == null || pt.category.isEmpty()
                ? "Choisir" : pt.category) + "  ✎");
        catBtn.setOnClickListener(v -> showCategoryPicker(a, pt, categories,
                () -> catBtn.setText("🏷️ Catégorie : " + pt.category + "  ✎")));
        box.addView(catBtn, marginTop(a));

        // Charge fixe (abonnement récurrent) — comme dans les transactions
        final android.widget.CheckBox fixe = new android.widget.CheckBox(a);
        fixe.setText("  Charge fixe → abonnement récurrent");
        fixe.setTextColor(ThemeColors.text());
        fixe.setTextSize(DS.TEXT_SM);
        fixe.setChecked(pt.recurringCandidate);
        box.addView(fixe, marginTop(a));

        final AlertDialog[] h = {null};
        h[0] = new AppDialog.Builder(a).icon("✏️")
                .title("Modifier").subtitle("Libellé, catégorie, charge fixe")
                .content(box)
                .primaryBtn("ENREGISTRER", () -> {
                    String newLabel = input.getText().toString().trim();
                    if (!newLabel.isEmpty()) {
                        pt.label = newLabel;
                        rememberLabelAndApplyToSimilar(pt, all);
                    }
                    rememberCategoryAndApplyToSimilar(pt, all);
                    pt.recurringCandidate = fixe.isChecked();
                    dismiss(h);
                    if (onChanged != null) onChanged.run();
                    AppToast.success(a, "Modifié ✓");
                }).show();
    }

    /** Mémorise le libellé et l'applique aux transactions du même commerçant. */
    private static void rememberLabelAndApplyToSimilar(ParsedTransaction source,
            List<ParsedTransaction> all) {
        String key = MerchantRuleManager.getInstance().resolveMerchantKey(source);
        if (key.isEmpty()) return;
        MerchantRuleManager.getInstance().saveLabelRule(key, source.label);
        if (all == null) return;
        for (ParsedTransaction tx : all) {
            if (key.equals(MerchantRuleManager.getInstance().resolveMerchantKey(tx)))
                tx.label = source.label;
        }
    }

    /** Mémorise la catégorie et l'applique aux transactions du même commerçant. */
    private static void rememberCategoryAndApplyToSimilar(ParsedTransaction source,
            List<ParsedTransaction> all) {
        if (source.category == null || source.category.isEmpty()) return;
        String key = MerchantRuleManager.getInstance().resolveMerchantKey(source);
        if (key.isEmpty()) return;
        MerchantRuleManager.getInstance().saveCategoryRule(key, source.category);
        if (all == null) return;
        for (ParsedTransaction tx : all) {
            if (key.equals(MerchantRuleManager.getInstance().resolveMerchantKey(tx)))
                tx.category = source.category;
        }
    }

    private static void updateCategoryChip(TextView mv, ParsedTransaction pt, String ownerLbl) {
        String cat = pt.category == null || pt.category.isEmpty() ? "Choisir" : pt.category;
        String fixe = pt.recurringCandidate ? "  🔁" : "";
        mv.setText("🏷️ " + cat + (ownerLbl.isEmpty() ? "" : "  ·  " + ownerLbl) + "  ✎" + fixe);
    }

    /** Picker de catégorie : liste des existantes + création d'une nouvelle. */
    private static void showCategoryPicker(Activity a, ParsedTransaction pt,
                                            List<String> categories, Runnable onPicked) {
        ScrollView scroll = new ScrollView(a);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(box);

        final AlertDialog[] h = {null};

        // Créer une nouvelle catégorie
        View createRow = choiceRow(a, "➕  Créer une catégorie", "Saisir un nouveau nom");
        final List<String> sharedCats = categories != null ? categories : new ArrayList<>();
        createRow.setOnClickListener(v -> {
            dismiss(h);
            showCreateCategoryDialog(a, pt, sharedCats, onPicked);
        });
        box.addView(createRow);

        // Catégories existantes
        List<String> cats = categories != null ? categories : new ArrayList<>();
        for (String cat : cats) {
            if (cat == null || cat.trim().isEmpty()) continue;
            final String c = cat.trim();
            View r = choiceRow(a, "🏷️  " + c, "");
            r.setOnClickListener(v -> {
                pt.category = c;
                dismiss(h);
                if (onPicked != null) onPicked.run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
            lp.topMargin = DS.dp(a, DS.GAP_SM);
            box.addView(r, lp);
        }

        h[0] = new AppDialog.Builder(a).icon("🏷️")
                .title("Catégorie").subtitle("Pour : " + pt.label)
                .content(scroll)
                .primaryBtn("ANNULER", () -> dismiss(h)).show();
    }

    private static void showCreateCategoryDialog(Activity a, ParsedTransaction pt,
                                                   List<String> sharedCategories, Runnable onPicked) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);

        EditText etName = editText(a, "Nom de la catégorie", false);
        box.addView(etName);

        EditText etEmoji = editText(a, "Emoji (optionnel)", false);
        LinearLayout.LayoutParams eLp = new LinearLayout.LayoutParams(-1, -2);
        eLp.topMargin = DS.dp(a, DS.GAP_SM);
        box.addView(etEmoji, eLp);

        final AlertDialog[] h = {null};
        h[0] = new AppDialog.Builder(a).icon("➕")
                .title("Nouvelle catégorie").subtitle("Sera enregistrée et disponible partout")
                .content(box)
                .primaryBtn("CRÉER", () -> {
                    String rawInput = etName.getText().toString();
                    String name = rawInput.trim();
                    if (name.isEmpty()) { AppToast.error(a, "Nom requis"); return; }
                    if (!rawInput.equals(name)) { AppToast.error(a, "Pas d'espace en début ou en fin de nom"); return; }
                    String nLow = name.toLowerCase(java.util.Locale.FRENCH);
                    if (nLow.equals("virements") || nLow.equals("virement")
                            || nLow.equals("crédits") || nLow.equals("crédit")
                            || nLow.equals("credits") || nLow.equals("credit")) {
                        AppToast.error(a, "\"" + name + "\" est une catégorie système réservée");
                        return;
                    }
                    String emoji = etEmoji.getText().toString().trim();
                    if (emoji.isEmpty()) emoji = "🏷️";
                    dismiss(h);
                    final String finalEmoji = emoji;
                    // Ajouter immédiatement dans la liste partagée pour les autres lignes
                    boolean alreadyThere = false;
                    for (String s : sharedCategories) { if (s.equalsIgnoreCase(name)) { alreadyThere = true; break; } }
                    if (!alreadyThere) sharedCategories.add(name);
                    pt.category = name;
                    if (onPicked != null) onPicked.run();
                    // Sauvegarder dans Firestore en arrière-plan
                    com.couplefinance.data.CategoryManager.getInstance().addCategory(
                            name, finalEmoji,
                            new com.couplefinance.data.FirestoreManager.Callback() {
                                @Override public void onSuccess(String r) {
                                    AppToast.success(a, "Catégorie \"" + name + "\" créée ✓");
                                }
                                @Override public void onError(String e) {
                                    AppToast.error(a, "Sauvegarde échouée : " + e);
                                }
                            });
                }).show();
    }

    // ─────────────────────────────────────────────────────────────
    // Import
    // ─────────────────────────────────────────────────────────────

    private static void executeImport(Activity a, List<ParsedTransaction> all,
                                       List<TransactionsModels.Transaction> existing) {
        List<ParsedTransaction> confirmed = new ArrayList<>();
        for (ParsedTransaction pt : all) if (pt.selected && !pt.duplicate) confirmed.add(pt);
        if (confirmed.isEmpty()) { AppToast.info(a, "Aucune transaction sélectionnée"); return; }

        String me = getCurrentPersonName(a);
        List<TransactionsModels.Transaction> toImport = new ArrayList<>();
        for (ParsedTransaction pt : confirmed) {
            MerchantRuleManager.getInstance().saveRuleFromTransaction(pt);
            String type = "income".equals(pt.type) ? "income" : "variable";
            String cat  = pt.category == null || pt.category.isEmpty()
                    ? ("income".equals(pt.type) ? "Revenus" : "Autre") : pt.category;

            // ── Attribution selon le propriétaire du compte ───────────
            String person = me;
            String compte = "";
            if ("joint".equals(pt.owner)) {
                compte = "joint";              // → compte joint
                person = me;
            } else if (pt.owner != null && !pt.owner.isEmpty()) {
                person = pt.owner;             // nom réel du membre
            }

            toImport.add(new TransactionsModels.Transaction(
                    pt.label, pt.amount, type, cat,
                    pt.dateMs, System.currentTimeMillis(),
                    person, false, false, false, "", compte));
        }
        ensureCategoriesExist(toImport);

        final int total = toImport.size();
        LinearLayout pc = new LinearLayout(a);
        pc.setOrientation(LinearLayout.VERTICAL);
        TextView pv = UiFactory.bodyMuted(a, "0 / " + total);
        pv.setTextSize(DS.TEXT_SM); pv.setTypeface(null, Typeface.BOLD);
        pv.setTextColor(ThemeColors.primary()); pc.addView(pv);

        final AlertDialog[] h = {null};
        h[0] = new AppDialog.Builder(a).icon("☁️").title("Import…")
                .subtitle("Enregistrement").content(pc).show();

        final List<ParsedTransaction> fc = confirmed;
        TransactionsRepository.importBatch(toImport, a,
                (done,t) -> pv.setText(done + " / " + t),
                new TransactionsRepository.OnWriteComplete() {
                    @Override public void onSuccess() {
                        dismiss(h);
                        AppToast.success(a, total + " transaction(s) importée(s) ✓");
                        BankImportPipeline.autoDetectRecurringCharges(fc, a);
                        BankImportPipeline.autoCreateTransfers(fc, a);
                        fireComplete(a);
                    }
                    @Override public void onError(String e) {
                        dismiss(h); AppToast.error(a, "Erreur import : " + e);
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────
    // Menu sync (banque connectée)
    // ─────────────────────────────────────────────────────────────

    private static void showSyncMenu(Activity a, List<TransactionsModels.Transaction> existing) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        final AlertDialog[] h = {null};

        String bankName   = EnableBankingManager.getInstance().getSavedBankName();
        String cycleLabel = CycleManager.getInstance().getCurrentCycleLabel();

        // Afficher les comptes attribués
        List<String> accNames = EnableBankingManager.getInstance().getAccountNames();
        List<String> ibans    = EnableBankingManager.getInstance().getAccountIbans();
        List<String> owners   = EnableBankingManager.getInstance().getAccountOwners();
        if (!accNames.isEmpty()) {
            for (int i = 0; i < accNames.size(); i++) {
                String iban  = i < ibans.size()  ? ibans.get(i)  : "";
                String short4 = iban.length()>4 ? "..." + iban.substring(iban.length()-5) : iban;
                String owner = i < owners.size() ? owners.get(i) : "";
                String ownerLabel = "joint".equals(owner) ? "Compte joint"
                        : (owner != null && !owner.isEmpty()) ? owner : "Non attribué";
                TextView tv = UiFactory.bodyMuted(a,
                        "• " + accNames.get(i) + (short4.isEmpty() ? "" : "  " + short4)
                        + "  →  " + ownerLabel);
                tv.setTextSize(DS.TEXT_XS);
                LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1,-2);
                tp.bottomMargin = DS.dp(a,2); c.addView(tv, tp);
            }
            View div = new View(a);
            div.setBackgroundColor(ThemeColors.divider());
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1,1);
            dp.topMargin = DS.dp(a, DS.GAP_SM); dp.bottomMargin = DS.dp(a, DS.GAP_SM);
            c.addView(div, dp);
        }

        View syncCycle = choiceRow(a, "🔄  " + cycleLabel, "Transactions du cycle courant");
        syncCycle.setOnClickListener(v -> { dismiss(h); startSync(a, existing, 1); });
        c.addView(syncCycle);

        View sync3m = choiceRow(a, "📅  3 derniers mois", "Importer les 90 derniers jours");
        sync3m.setOnClickListener(v -> { dismiss(h); startSync(a, existing, 3); });
        c.addView(sync3m, marginTop(a));

        View balBtn = choiceRow(a, "💰  Voir les soldes", "Soldes actuels de vos comptes");
        balBtn.setOnClickListener(v -> { dismiss(h); showBalances(a); });
        c.addView(balBtn, marginTop(a));

        View assign = choiceRow(a, "👥  Réattribuer les comptes",
                "Modifier qui possède chaque compte");
        assign.setOnClickListener(v -> { dismiss(h); showAccountAssignment(a, existing); });
        c.addView(assign, marginTop(a));

        View addBank = choiceRow(a, "➕  Ajouter une banque", "Connecter un autre établissement");
        addBank.setOnClickListener(v -> { dismiss(h); showBankPickerLoading(a, existing); });
        c.addView(addBank, marginTop(a));

        View disc = choiceRow(a, "🔌  Déconnecter",
                "Supprimer la connexion (transactions conservées)");
        disc.setOnClickListener(v -> {
            EnableBankingManager.getInstance().clearConnection();
            dismiss(h); AppToast.success(a, "Banque déconnectée");
        });
        c.addView(disc, marginTop(a));

        String allBanks = EnableBankingManager.getInstance().getConnectedBankNames();
        String subtitle = allBanks.isEmpty() ? "Open Banking · DSP2" : allBanks + " · Connectée(s) ✓";
        h[0] = new AppDialog.Builder(a).icon("🏦")
                .title("Connexion bancaire").subtitle(subtitle)
                .content(c).primaryBtn("FERMER", () -> dismiss(h)).show();
    }

    // ─────────────────────────────────────────────────────────────
    // Soldes — affichage compact
    // ─────────────────────────────────────────────────────────────

    public static void showBalances(Activity a) {
        if (!EnableBankingManager.getInstance().isConnected()) {
            AppToast.error(a, "Aucune banque connectée."); return;
        }
        final AlertDialog[] h = spinnerDialog(a, "💰", "Soldes bancaires", "Récupération…");
        EnableBankingManager.getInstance().getAllBalances(new EnableBankingManager.BalancesCallback() {
            @Override public void onResult(List<EnableBankingManager.AccountBalance> balances, double main) {
                dismiss(h); showBalanceResult(a, balances);
            }
            @Override public void onError(String error) {
                dismiss(h); AppToast.error(a, "Solde indisponible : " + error);
            }
        });
    }

    private static void showBalanceResult(Activity a,
                                           List<EnableBankingManager.AccountBalance> balances) {
        if (balances == null || balances.isEmpty()) {
            AppToast.info(a, "Aucun solde disponible (CMB mode restreint).");
            return;
        }

        int maxIdx = 0;
        for (EnableBankingManager.AccountBalance b : balances)
            if (b.accountIndex > maxIdx) maxIdx = b.accountIndex;

        ScrollView scroll = new ScrollView(a);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root);

        List<String> names  = EnableBankingManager.getInstance().getAccountNames();
        List<String> owners = EnableBankingManager.getInstance().getAccountOwners();

        for (int idx = 0; idx <= maxIdx; idx++) {
            // Meilleur solde pour ce compte (ITBD > CLBD > non-nul > premier)
            double best = 0; String bestType = "";
            for (EnableBankingManager.AccountBalance b : balances)
                if (b.accountIndex==idx && "ITBD".equals(b.type) && Math.abs(b.signedAmount())>0.001) { best=b.signedAmount(); bestType=b.type; break; }
            if (bestType.isEmpty()) for (EnableBankingManager.AccountBalance b : balances)
                if (b.accountIndex==idx && "CLBD".equals(b.type) && Math.abs(b.signedAmount())>0.001) { best=b.signedAmount(); bestType=b.type; break; }
            if (bestType.isEmpty()) for (EnableBankingManager.AccountBalance b : balances)
                if (b.accountIndex==idx && Math.abs(b.signedAmount())>0.001) { best=b.signedAmount(); bestType=b.type; break; }
            if (bestType.isEmpty()) for (EnableBankingManager.AccountBalance b : balances)
                if (b.accountIndex==idx) { best=b.signedAmount(); bestType=b.type; break; }

            final double amount = best;
            String accName  = idx < names.size()  ? names.get(idx)  : "Compte " + (idx+1);
            String owner    = idx < owners.size() ? owners.get(idx) : "";
            String ownerLbl = "joint".equals(owner) ? "Compte joint"
                    : (owner != null && !owner.isEmpty()) ? owner : "Non attribué";
            String typeStr  = bestType.isEmpty() ? "" : " · " + bestType;

            // ── Carte unique du compte ────────────────────────────────
            LinearLayout card = new LinearLayout(a);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(DS.dp(a,14), DS.dp(a,12), DS.dp(a,14), DS.dp(a,10));
            card.setBackground(UiFactory.bgBordered(ThemeColors.card(), ThemeColors.border(), DS.R_MD, a));
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1,-2);
            cardLp.bottomMargin = DS.dp(a, DS.GAP_SM);
            cardLp.topMargin    = idx > 0 ? DS.dp(a,4) : 0;

            // Ligne haut : nom + montant
            LinearLayout topLine = new LinearLayout(a);
            topLine.setOrientation(LinearLayout.HORIZONTAL);
            topLine.setGravity(Gravity.CENTER_VERTICAL);

            TextView nameV = new TextView(a);
            nameV.setText("🏦 " + accName + "  →  " + ownerLbl + typeStr);
            nameV.setTextSize(DS.TEXT_SM);
            nameV.setTypeface(null, Typeface.BOLD);
            nameV.setTextColor(ThemeColors.text());
            topLine.addView(nameV, new LinearLayout.LayoutParams(0,-2,1f));

            TextView amtV = new TextView(a);
            amtV.setText(String.format(Locale.FRANCE, "%.2f €", amount));
            amtV.setTextSize(22f);
            amtV.setTypeface(null, Typeface.BOLD);
            amtV.setTextColor(amount >= 0 ? Color.parseColor("#059669") : Color.parseColor("#DC2626"));
            amtV.setGravity(Gravity.END);
            topLine.addView(amtV);
            card.addView(topLine);

            // Ligne boutons +/− (créés et ajoutés UNE seule fois)
            LinearLayout btns = new LinearLayout(a);
            btns.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams btnsLp = new LinearLayout.LayoutParams(-1,-2);
            btnsLp.topMargin = DS.dp(a,8);

            double abs = Math.abs(amount);
            String absStr = String.format(Locale.FRANCE,"%.0f",abs);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2,-2);
            sp.rightMargin = DS.dp(a,4);

            btns.addView(saveBtn(a, "Perso +"+absStr, () -> savePersonalBalance(a, abs)), sp);
            btns.addView(saveBtn(a, "Perso −"+absStr, () -> savePersonalBalance(a, -abs)), sp);
            btns.addView(saveBtn(a, "Joint +"+absStr, () -> saveJointBalance(a, abs)), sp);
            btns.addView(saveBtn(a, "Joint −"+absStr, () -> saveJointBalance(a, -abs)));
            card.addView(btns, btnsLp);

            root.addView(card, cardLp);
        }

        new AppDialog.Builder(a).icon("💰")
                .title("Soldes · " + EnableBankingManager.getInstance().getSavedBankName())
                .subtitle("Touchez un bouton pour enregistrer")
                .content(scroll)
                .primaryBtn("FERMER", () -> {})
                .show();
    }

    // ─────────────────────────────────────────────────────────────
    // ASPSP_ERROR dialog
    // ─────────────────────────────────────────────────────────────

    private static void showAspspErrorDialog(Activity a, List<TransactionsModels.Transaction> existing,
                                              String rawError) {
        LinearLayout c = new LinearLayout(a); c.setOrientation(LinearLayout.VERTICAL);
        TextView t = UiFactory.bodyMuted(a,
                "La banque a refusé l'accès (ASPSP_ERROR).\n\n"
                + "• Autorisation expirée → reconnectez\n"
                + "• Ou période de dates trop large\n\n"
                + "Cliquez « Reconnecter » pour relancer l'autorisation.");
        t.setTextSize(DS.TEXT_SM); c.addView(t);

        final AlertDialog[] h = {null};
        h[0] = new AppDialog.Builder(a).icon("⚠️")
                .title("Accès refusé").subtitle("ASPSP_ERROR · CMB/Arkéa")
                .content(c)
                .primaryBtn("RECONNECTER", () -> {
                    dismiss(h);
                    EnableBankingManager.getInstance().clearConnection();
                    showConnectMenu(a, existing);
                }).show();
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers de sauvegarde des soldes
    // ─────────────────────────────────────────────────────────────

    private static void savePersonalBalance(Activity a, double amount) {
        com.couplefinance.data.BalanceManager.getInstance().saveMonthlyStartBalance(
                amount, new FirestoreManager.Callback() {
                    @Override public void onSuccess(String r) {
                        AppToast.success(a, "Solde perso : "
                                + String.format(Locale.FRANCE,"%.2f €",amount) + " ✓");
                    }
                    @Override public void onError(String e) {
                        AppToast.info(a, "Enregistré localement");
                    }
                });
    }

    private static void saveJointBalance(Activity a, double amount) {
        JointAccountManager.getInstance().saveMonthlyStartBalance(a, amount,
                new JointAccountManager.Callback() {
                    @Override public void onSuccess() {
                        AppToast.success(a, "Solde joint : "
                                + String.format(Locale.FRANCE,"%.2f €",amount) + " ✓");
                    }
                    @Override public void onError(String e) {
                        AppToast.info(a, "Enregistré localement");
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers UI
    // ─────────────────────────────────────────────────────────────

    private static View saveBtn(Activity a, String text, Runnable action) {
        TextView btn = new TextView(a);
        btn.setText(text);
        btn.setTextSize(11f);
        btn.setTextColor(ThemeColors.primary());
        btn.setPadding(DS.dp(a,8), DS.dp(a,4), DS.dp(a,8), DS.dp(a,4));
        btn.setBackground(UiFactory.bgBordered(ThemeColors.primarySoft(), ThemeColors.border(), DS.R_SM, a));
        btn.setOnClickListener(v -> action.run());
        return btn;
    }

    private static View choiceRow(Activity a, String title, String subtitle) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(DS.dp(a,DS.PAD), DS.dp(a,12), DS.dp(a,DS.PAD), DS.dp(a,12));
        row.setBackground(UiFactory.bgBordered(ThemeColors.card(), ThemeColors.border(), DS.R_MD, a));
        row.setClickable(true); row.setFocusable(true);

        TextView tv = new TextView(a);
        tv.setText(title); tv.setTextSize(15f);
        tv.setTypeface(null, Typeface.BOLD); tv.setTextColor(ThemeColors.text());
        row.addView(tv);
        TextView sv = UiFactory.bodyMuted(a, subtitle);
        sv.setTextSize(DS.TEXT_XS);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,-2);
        sp.topMargin = DS.dp(a,2); row.addView(sv, sp);
        return row;
    }

    private static AlertDialog[] spinnerDialog(Activity a, String icon, String title, String sub) {
        LinearLayout c = new LinearLayout(a); c.setOrientation(LinearLayout.VERTICAL);
        if (!sub.isEmpty()) {
            TextView t = UiFactory.bodyMuted(a, sub); t.setTextSize(DS.TEXT_SM); c.addView(t);
        }
        AlertDialog d = new AppDialog.Builder(a).icon(icon).title(title).subtitle(sub).content(c).show();
        return new AlertDialog[]{d};
    }

    private static TextView label(Activity a, String text) {
        TextView tv = new TextView(a);
        tv.setText(text); tv.setTextSize(DS.TEXT_SM);
        tv.setTextColor(ThemeColors.subtext());
        tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }

    private static EditText editText(Activity a, String hint, boolean multiline) {
        EditText et = new EditText(a);
        et.setHint(hint); et.setTextSize(DS.TEXT_XS);
        et.setTextColor(ThemeColors.text()); et.setHintTextColor(ThemeColors.subtext());
        et.setPadding(DS.dp(a,DS.PAD_INPUT), DS.dp(a,10), DS.dp(a,DS.PAD_INPUT), DS.dp(a,10));
        et.setBackground(UiFactory.bgBordered(ThemeColors.backgroundSecondary(), ThemeColors.border(), DS.R_MD, a));
        if (multiline) {
            et.setMinLines(4); et.setMaxLines(8);
            et.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        } else {
            et.setSingleLine(true);
            et.setInputType(InputType.TYPE_CLASS_TEXT);
        }
        return et;
    }

    private static LinearLayout.LayoutParams marginBottom(Activity a) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin = DS.dp(a, DS.GAP); return lp;
    }
    private static LinearLayout.LayoutParams marginTop(Activity a) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin = DS.dp(a, DS.GAP_SM); return lp;
    }

    private static void dismiss(AlertDialog[] h) {
        try { if (h!=null&&h.length>0&&h[0]!=null&&h[0].isShowing()) h[0].dismiss(); }
        catch (Exception ignored) {}
    }

    private static String getCurrentPersonName(Activity a) {
        try {
            String n = com.couplefinance.UserSession.getInstance().getNameOrFallback();
            if (n!=null&&!n.trim().isEmpty()&&!n.contains("@")) return n.trim();
        } catch (Exception ignored) {}
        try {
            String n = com.couplefinance.AuthManager.getInstance().getDisplayName();
            if (n!=null&&!n.trim().isEmpty()&&!n.contains("@")) return n.trim();
        } catch (Exception ignored) {}
        return "Moi";
    }

    private static List<String> parseCategoryNames(String response) {
        // Les catégories système sont toujours présentes en premier
        List<String> names = new ArrayList<>();
        names.add("Virements");
        names.add("Crédits");
        try {
            org.json.JSONObject root = new org.json.JSONObject(response);
            org.json.JSONArray docs  = root.optJSONArray("documents");
            if (docs==null) return names;
            for (int i=0;i<docs.length();i++) {
                org.json.JSONObject fields = docs.getJSONObject(i).optJSONObject("fields");
                if (fields==null) continue;
                org.json.JSONObject no = fields.optJSONObject("name");
                if (no==null) continue;
                String name = no.optString("stringValue","").trim();
                String clean = name.replace("|expense","").replace("|income","").trim();
                boolean dup = false;
                for (String n : names) { if (n.equalsIgnoreCase(clean)) { dup = true; break; } }
                if (!clean.isEmpty() && !dup) names.add(clean);
            }
        } catch (Exception ignored) {}
        return names;
    }

    private static void ensureCategoriesExist(List<TransactionsModels.Transaction> transactions) {
        List<String> seen = new ArrayList<>();
        for (TransactionsModels.Transaction tx : transactions) {
            if (tx==null||tx.category==null||tx.category.trim().isEmpty()) continue;
            String cat = tx.category.trim();
            if (cat.equalsIgnoreCase("Autre")||seen.contains(cat)) continue;
            seen.add(cat);
            String nt = tx.isIncome() ? cat+"|income" : cat+"|expense";
            CategoryManager.getInstance().addCategory(nt, "🏷️",
                    new FirestoreManager.Callback() {
                        @Override public void onSuccess(String r) {}
                        @Override public void onError(String e) {}
                    });
        }
    }
}
