package com.couplefinance.data;

import android.content.Context;

import com.couplefinance.ocr.OcrMerchantRules;
import com.couplefinance.ui.transactions.TransactionsModels;
import com.couplefinance.utils.ParsedTransaction;
import com.couplefinance.utils.PdfTransactionParser;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * BankImportPipeline — Pont entre les transactions GoCardless et le système CoupleFinance.
 *
 * Cette classe applique EXACTEMENT les mêmes règles que l'import PDF/OCR :
 *
 *   1. Conversion BankTransaction → ParsedTransaction (format interne)
 *   2. Catégorisation automatique via OcrMerchantRules.guessCategory()
 *   3. Application des règles apprises via MerchantRuleManager
 *   4. Détection de doublons vs transactions Firestore existantes
 *   5. Détection des charges fixes récurrentes (createFixedChargeFromTransaction)
 *
 * Les résultats sont une List<ParsedTransaction> prêts à être prévisualisés
 * et sauvegardés via TransactionsRepository.importBatch().
 *
 * Remarque : la détection des charges récurrentes se fait APRÈS import,
 * de manière asynchrone et silencieuse (fire-and-forget).
 */
public final class BankImportPipeline {

    private BankImportPipeline() {}

    // ─────────────────────────────────────────────────────────────
    // Conversion + enrichissement
    // ─────────────────────────────────────────────────────────────

    /**
     * Convertit une liste de BankTransaction en ParsedTransaction enrichies.
     *
     * Pour chaque transaction :
     *   - Type déterminé par le signe du montant (négatif = dépense)
     *   - Catégorie devinée par OcrMerchantRules + MerchantRuleManager
     *   - MerchantKey calculée (pour future correction et apprentissage)
     *
     * @param transactions  Transactions brutes depuis Enable Banking
     * @param ctx           Context pour MerchantRuleManager
     * @param allowedCategories Catégories existantes dans le foyer (pour filtre)
     * @return Liste ParsedTransaction prêts pour OcrTransactionPreviewDialog
     */
    public static List<ParsedTransaction> enrich(
        List<EnableBankingManager.BankTransaction> transactions,
        Context ctx,
        List<String> allowedCategories) {

    if (transactions == null || transactions.isEmpty()) {
        android.util.Log.d("BANK_PIPELINE", "❌ Aucune transaction reçue");
        return new ArrayList<>();
    }

    android.util.Log.d("BANK_PIPELINE", "✓ " + transactions.size() + " transactions reçues");

    MerchantRuleManager.getInstance().init(ctx);
    List<ParsedTransaction> result = new ArrayList<>();

    for (EnableBankingManager.BankTransaction bt : transactions) {
        if (bt == null || Math.abs(bt.amount) < 0.01) continue;

        ParsedTransaction pt = convert(bt);
        // Appliquer les règles apprises (libellé + catégorie + type) → priorité
        MerchantRuleManager.getInstance().applyKnownRule(pt, allowedCategories);
        // Compléter la catégorie si aucune règle ne l'a définie
        applyCategorization(pt, allowedCategories);
        // FINAL : un virement reste un virement, même si une règle apprise a réécrit
        // le libellé/catégorie. La détection bancaire a le dernier mot.
        String vir = detectTransferName(bt.label, PdfTransactionParser.cleanBankLabel(bt.label));
        if (vir != null && !vir.isEmpty()) {
            pt.label = "Virement " + vir;
            pt.category = "Virements";
        }
        result.add(pt);
    }

    android.util.Log.d("BANK_PIPELINE", "✓ " + result.size() + " transactions enrichies");
    return result;
}

    // ─────────────────────────────────────────────────────────────
    // Détection des doublons
    // ─────────────────────────────────────────────────────────────

    /**
     * Marque les ParsedTransaction qui existent déjà dans Firestore.
     *
     * Clé de doublon : "YYYY-MM-DD|type|montant_centimes|label_normalisé"
     * — identique à l'algorithme de TransactionOcrImporter.
     *
     * Les transactions dupliquées ont pt.duplicate=true et pt.selected=false.
     *
     * @param parsed    Transactions à analyser (modifiées en place)
     * @param existing  Transactions Firestore du foyer (pour comparaison)
     */
    public static void detectDuplicates(
            List<ParsedTransaction> parsed,
            List<TransactionsModels.Transaction> existing) {

        HashSet<String> existingKeys = new HashSet<>();
        HashSet<String> importKeys   = new HashSet<>();

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

            boolean isDup = existingKeys.contains(key) || importKeys.contains(key);
            if (isDup) {
                pt.duplicate        = true;
                pt.selected         = false;
                pt.duplicateReason  = "Transaction déjà importée dans CoupleFinance";
                pt.duplicateWarning = "Doublon";
            }

            importKeys.add(key);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Charges récurrentes — détection post-import
    // ─────────────────────────────────────────────────────────────

    /**
     * Pour chaque dépense importée, tente de créer une charge fixe correspondante.
     *
     * RecurringChargeManager.createFixedChargeFromTransaction() gère lui-même
     * la déduplication (si la charge existe déjà, elle est ignorée silencieusement).
     *
     * À appeler APRÈS l'import, en fire-and-forget.
     *
     * @param imported  Transactions effectivement importées (non nulles, non income)
     * @param ctx       Context pour RecurringChargeManager
     */
    public static void autoDetectRecurringCharges(
            List<ParsedTransaction> imported,
            Context ctx) {

        if (imported == null || ctx == null) return;

        RecurringChargeManager.getInstance().init(ctx);

        // Collecter les candidats (dépenses récurrentes ou marquées charge fixe)
        List<ParsedTransaction> candidates = new ArrayList<>();
        for (ParsedTransaction pt : imported) {
            if (pt == null) continue;
            if ("income".equals(pt.type)) continue;       // Seulement les dépenses
            if (Math.abs(pt.amount) < 5.0) continue;       // Ignorer les micro-transactions
            if (!pt.recurringCandidate) continue;          // SEULEMENT les vrais abonnements
            candidates.add(pt);
        }

        if (candidates.isEmpty()) return;

        final int[] created = {0};
        final int total = candidates.size();
        final int[] done = {0};

        for (ParsedTransaction pt : candidates) {
            RecurringChargeManager.getInstance().createFixedChargeFromTransaction(
                    pt.label, pt.amount, pt.category, pt.dateMs, pt.owner,
                    new FirestoreManager.Callback() {
                        @Override public void onSuccess(String r) {
                            if (!"EXISTS".equals(r)) created[0]++;
                            finish();
                        }
                        @Override public void onError(String e) { finish(); }
                        private void finish() {
                            done[0]++;
                            if (done[0] >= total) onAllChargesProcessed(ctx, created[0]);
                        }
                    });
        }
    }

    /**
     * Crée une entrée dans l'onglet VIREMENTS pour chaque virement détecté.
     * Le sens (entrant/sortant) est déduit du type ; la transaction reste
     * aussi présente dans TRANSACTIONS.
     */
    public static void autoCreateTransfers(List<ParsedTransaction> imported, Context ctx) {
        if (imported == null || ctx == null) return;
        String me = currentUserName();
        for (ParsedTransaction pt : imported) {
            if (pt == null) continue;
            if (!PdfTransactionParser.isVirementLabel(pt.label)) continue;

            // Tiers = nom après "Virement "
            String other = pt.label.replaceFirst("(?i)^virement\\s*", "").trim();
            if (other.isEmpty()) other = "Externe";

            // "Soi" = propriétaire du compte (membre réel / joint / utilisateur courant)
            String self;
            if ("joint".equals(pt.owner)) self = "Compte joint";
            else if (pt.owner != null && !pt.owner.isEmpty()) self = pt.owner;
            else self = me.isEmpty() ? "Compte joint" : me;

            String from, to;
            if ("income".equals(pt.type)) { from = other; to = self; }  // entrant
            else                          { from = self;  to = other; } // sortant

            TransferManager.getInstance().addTransfer(
                    from, to, Math.abs(pt.amount), pt.label, pt.dateMs,
                    new FirestoreManager.Callback() {
                        @Override public void onSuccess(String r) {}
                        @Override public void onError(String e) {}
                    });
        }
    }

    /** Nom de l'utilisateur courant (UserSession / AuthManager), "" si inconnu. */
    private static String currentUserName() {
        try {
            String n = com.couplefinance.UserSession.getInstance().getNameOrFallback();
            if (n != null && !n.trim().isEmpty() && !n.contains("@")) return n.trim();
        } catch (Exception ignored) {}
        try {
            String n = com.couplefinance.AuthManager.getInstance().getDisplayName();
            if (n != null && !n.trim().isEmpty() && !n.contains("@")) return n.trim();
        } catch (Exception ignored) {}
        return "";
    }

    /** Après création : applique les charges du mois courant + feedback. */
    private static void onAllChargesProcessed(Context ctx, int createdCount) {
        // Appliquer immédiatement les charges dues ce mois-ci (et préparer la répétition)
        RecurringChargeManager.getInstance().checkAndApplyRecurringCharges(() -> {
            if (createdCount > 0 && ctx instanceof android.app.Activity) {
                final android.app.Activity a = (android.app.Activity) ctx;
                a.runOnUiThread(() -> {
                    try {
                        com.couplefinance.AppToast.success(a,
                                createdCount + " charge" + (createdCount > 1 ? "s" : "")
                                + " fixe" + (createdCount > 1 ? "s" : "") + " créée"
                                + (createdCount > 1 ? "s" : "") + " ✓ (voir Abonnements)");
                    } catch (Exception ignored) {}
                });
            }
        });
    }

    // ─────────────────────────────────────────────────────────────
    // Plage de dates du cycle courant
    // ─────────────────────────────────────────────────────────────

    /**
     * Retourne la plage de dates du cycle courant au format "YYYY-MM-DD".
     * Utilisé pour limiter la requête Enable Banking au cycle financier actuel.
     *
     * @return String[2] = { dateFrom, dateTo }
     */
    public static String[] getCurrentCycleDateRange() {
        long start = CycleManager.getInstance().getCurrentCycleStartMillis();
        long end   = CycleManager.getInstance().getCurrentCycleEndMillis();
        return new String[]{
                EnableBankingManager.millisToDateStr(start),
                EnableBankingManager.millisToDateStr(end)
        };
    }

    /**
     * Retourne la plage de dates des N derniers mois.
     *
     * @param monthsBack Nombre de mois en arrière (ex : 3)
     * @return String[2] = { dateFrom, dateTo }
     */
    public static String[] getLastMonthsDateRange(int monthsBack) {
        Calendar cal = Calendar.getInstance();
        String dateTo = EnableBankingManager.millisToDateStr(cal.getTimeInMillis());
        cal.add(Calendar.MONTH, -Math.abs(monthsBack));
        String dateFrom = EnableBankingManager.millisToDateStr(cal.getTimeInMillis());
        return new String[]{ dateFrom, dateTo };
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers privés
    // ─────────────────────────────────────────────────────────────

    /** Convertit une BankTransaction en ParsedTransaction sans catégorisation. */
    private static ParsedTransaction convert(EnableBankingManager.BankTransaction bt) {
        ParsedTransaction pt = new ParsedTransaction();
        pt.label   = PdfTransactionParser.cleanBankLabel(bt.label);
        pt.dateMs  = bt.dateMs;
        pt.amount  = Math.abs(bt.amount);
        // Négatif dans Enable Banking = débit = dépense (type "variable")
        // Positif dans Enable Banking = crédit = revenu (type "income")
        pt.type    = bt.amount < 0 ? "variable" : "income";
        pt.selected = true;
        pt.duplicate = false;

        // Détection virement : nomme TOUJOURS le tiers (l'autre compte), jamais soi.
        //  -60 Joint→Melissa  → "Virement Melissa"
        //  +100 Melissa→Joint → "Virement Melissa"
        //  (+60 vu côté Melissa, libellé "Compte Joint" → "Virement Compte joint")
        String virName = detectTransferName(bt.label, pt.label);
        if (virName != null && !virName.isEmpty()) {
            pt.label = "Virement " + virName;
        }

        // merchantKey : calculée sur le libellé BRUT (plus de signal pour les règles)
        String keySource = (bt.label != null && !bt.label.isEmpty()) ? bt.label : pt.label;
        pt.merchantKey = keySource.isEmpty() ? "" : PdfTransactionParser.merchantKey(keySource);

        // Attribution du compte (Thomas / Mélissa / Joint)
        pt.owner        = bt.owner != null ? bt.owner : "";
        pt.accountIndex = bt.accountIndex;
        pt.accountIban  = bt.accountIban != null ? bt.accountIban : "";

        // Candidat charge fixe SEULEMENT si vrai abonnement/prélèvement récurrent
        pt.recurringCandidate = "variable".equals(pt.type)
                && PdfTransactionParser.isRecurringCandidateStatic(pt.label, pt.category);

        return pt;
    }

    /**
     * Détecte un virement et renvoie le nom du TIERS (l'autre compte) à afficher,
     * sinon null. Priorité : membre du foyer → compte joint → nom de tiers nettoyé.
     */
    private static String detectTransferName(String rawLabel, String cleanLabel) {
        String raw   = rawLabel  == null ? "" : rawLabel;
        String clean = cleanLabel == null ? "" : cleanLabel;
        String hay   = stripAccents((raw + " " + clean).toLowerCase(Locale.FRENCH));

        // 1. Membre du foyer présent → c'est un virement avec ce membre
        for (String fn : householdMemberFirstNames()) {
            if (fn.length() < 3) continue;
            if (containsWord(hay, stripAccents(fn.toLowerCase(Locale.FRENCH)))) return capitalize(fn);
        }

        // 2. Sinon, est-ce que ça ressemble à un virement entre comptes ?
        boolean looksTransfer = PdfTransactionParser.isVirementRaw(raw)
                || hay.contains("compte ") || hay.startsWith("compte")
                || hay.contains("cpte ");
        if (!looksTransfer) return null;

        // 3. Compte commun / joint
        if (hay.contains("joint") || hay.contains("commun")) return "Compte joint";

        // 4. Tiers générique : retirer les préfixes et garder le nom
        String name = clean.replaceFirst("(?i)^(virement|vir|compte|cpte)\\s+", "").trim();
        return name.isEmpty() ? "Externe" : name;
    }

    private static String stripAccents(String s) {
        if (s == null) return "";
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        return n.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    /** Vrai si {needle} apparaît comme mot entier dans {hay} (bordures non-alphabétiques). */
    private static boolean containsWord(String hay, String needle) {
        int from = 0;
        while (true) {
            int i = hay.indexOf(needle, from);
            if (i < 0) return false;
            boolean leftOk  = (i == 0) || !isLetter(hay.charAt(i - 1));
            int end = i + needle.length();
            boolean rightOk = (end >= hay.length()) || !isLetter(hay.charAt(end));
            if (leftOk && rightOk) return true;
            from = i + 1;
        }
    }

    private static boolean isLetter(char c) {
        return Character.isLetter(c);
    }

    /** Prénoms (premier mot) des membres du foyer, via le cache des paramètres. */
    private static java.util.List<String> householdMemberFirstNames() {
        java.util.List<String> out = new ArrayList<>();
        try {
            com.couplefinance.ui.settings.SettingsModels.State st =
                    com.couplefinance.ui.settings.SettingsCache.get();
            if (st != null && st.members != null) {
                for (com.couplefinance.ui.settings.SettingsModels.Member m : st.members) {
                    if (m == null || m.name == null) continue;
                    String first = m.name.trim().split("\\s+")[0];
                    if (!first.isEmpty()) out.add(first);
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(Locale.FRENCH) + s.substring(1).toLowerCase(Locale.FRENCH);
    }

    /**
     * Applique la catégorisation dans cet ordre de priorité :
     *   1. Règle mémorisée (MerchantRuleManager.getKnownCategory)
     *   2. Règles statiques OcrMerchantRules.guessCategory / guessIncomeCategory
     *   3. Fallback : "Autre" pour dépenses, "Revenus" pour revenus
     */
    private static void applyCategorization(ParsedTransaction pt,
                                             List<String> allowedCategories) {
        // 0. Virement détecté → catégorie "Virements" (priorité)
        if (PdfTransactionParser.isVirementLabel(pt.label)) {
            pt.category = "Virements";
            return;
        }

        // 1. Règle mémorisée
        String known = MerchantRuleManager.getInstance().getKnownCategory(pt.merchantKey);
        if (known != null && !known.isEmpty()) {
            boolean allowed = allowedCategories == null
                    || allowedCategories.isEmpty()
                    || containsIgnoreCase(allowedCategories, known);
            if (allowed) {
                pt.category = known;
                return;
            }
        }

        // 2. Règles statiques selon le type
        if ("income".equals(pt.type)) {
            pt.category = OcrMerchantRules.guessIncomeCategory(pt.label);
        } else {
            String guessed = OcrMerchantRules.guessCategory(pt.label);
            pt.category = "Autre".equals(guessed) ? guessFromContext(pt.label) : guessed;
        }

        // 3. Fallback
        if (pt.category == null || pt.category.isEmpty() || "Autre".equals(pt.category)) {
            pt.category = "income".equals(pt.type) ? "Revenus" : "Autre";
        }
    }

    /**
     * Catégorisation complémentaire pour les libellés bancaires typiques français.
     * Complète OcrMerchantRules avec des patterns spécifiques aux relevés.
     */
    private static String guessFromContext(String label) {
        if (label == null) return "Autre";
        String n = OcrMerchantRules.normalize(label);

        // Logement / loyer
        if (containsAny(n, "loyer", "locataire", "bail", "syndic", "charges copro")) return "Logement";
        // Crédit / prêt
        if (containsAny(n, "echeance credit", "remboursement pret", "mensualite",
                           "credit immo", "credit conso")) return "Crédit";
        // Impôts / taxes
        if (containsAny(n, "direction generale des finances", "dgfip",
                           "impots", "tva", "taxe fonciere", "cfe")) return "Impôts";
        // Assurances
        if (containsAny(n, "assurance", "maaf", "axa", "macif", "matmut",
                           "groupama", "allianz", "ag2r")) return "Assurance";
        // Santé
        if (containsAny(n, "cpam", "secu", "ameli", "mutuelle")) return "Santé";
        // Salaire / revenus
        if (containsAny(n, "salaire", "paie", "bulletin")) return "Salaire";
        // Remboursement
        if (containsAny(n, "remboursement cpam", "rbt", "avoir")) return "Remboursement";
        // Virement reçu
        if (containsAny(n, "virement recu", "virement entrant", "virt recu")) return "Virements";
        // Retraite / allocations
        if (containsAny(n, "retraite", "caf ", "pole emploi", "france travail",
                           "agirc", "arrco")) return "Allocations";

        return "Autre";
    }

    private static String duplicateKey(long dateMs, String type, double amount, String label) {
        String day = "";
        try {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(dateMs);
            day = c.get(Calendar.YEAR) + "-"
                    + (c.get(Calendar.MONTH) + 1) + "-"
                    + c.get(Calendar.DAY_OF_MONTH);
        } catch (Exception ignored) {}

        String labelKey = label == null ? "" :
                label.toLowerCase(Locale.FRANCE)
                        .replaceAll("[^a-z0-9]", " ")
                        .replaceAll("\\s+", " ")
                        .trim();

        return day + "|" + type + "|" + Math.round(amount * 100) + "|" + labelKey;
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        if (list == null || value == null) return false;
        for (String s : list) {
            if (s != null && s.equalsIgnoreCase(value.trim())) return true;
        }
        return false;
    }

    private static boolean containsAny(String haystack, String... needles) {
        if (haystack == null) return false;
        for (String n : needles) {
            if (n != null && !n.isEmpty() && haystack.contains(n)) return true;
        }
        return false;
    }
}