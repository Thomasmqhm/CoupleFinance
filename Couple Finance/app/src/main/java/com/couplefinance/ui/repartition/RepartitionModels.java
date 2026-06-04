package com.couplefinance.ui.repartition;

import java.util.List;
import java.util.Map;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  RepartitionModels — Modèles de données du package          ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Remplace les String[] tx = { label, amount, type, ... }   ║
 * ║  et les multiples doubles passés entre méthodes.            ║
 * ║                                                             ║
 * ║  Utilisé par :                                              ║
 * ║    RepartitionParser      → produit RepartitionData         ║
 * ║    RepartitionRepository  → transmet les données            ║
 * ║    RepartitionCalculator  → reçoit/retourne des modèles     ║
 * ║    RepartitionDialogs     → affiche dans les forms          ║
 * ║    RepartitionInsights    → lit pour insights + chart       ║
 * ║    RepartitionView        → orchestre tout                  ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
public final class RepartitionModels {

    private RepartitionModels() {}

    // ─────────────────────────────────────────────────────────────
    // Transaction partagée du foyer
    // ─────────────────────────────────────────────────────────────

    public static class SharedTransaction {
        public final String label;
        public final double amount;
        public final String type;       // "variable", "income", etc.
        public final long   dateMs;
        public final String category;
        public final String payer;      // extrait du label "Payer · description"
        public final String description;// partie après " · "
        public final boolean isShareSplit;
        public final boolean isReimbursement;
        public final boolean isShared;

        public SharedTransaction(String label, double amount, String type,
                                  long dateMs, String category,
                                  boolean isShareSplit, boolean isReimbursement,
                                  boolean isShared) {
            this.label          = label;
            this.amount         = amount;
            this.type           = type;
            this.dateMs         = dateMs;
            this.category       = category.isEmpty() ? "Autres" : category;
            this.isShareSplit   = isShareSplit;
            this.isReimbursement = isReimbursement;
            this.isShared       = isShared;

            // Extraction payer / description depuis "NomPayer · Description"
            if (label.contains(" · ")) {
                String[] parts = label.split(" · ", 2);
                String raw = parts[0].trim();
                this.payer = raw.isEmpty() ? ""
                    : raw.substring(0, 1).toUpperCase(java.util.Locale.FRANCE) + raw.substring(1);
                this.description = parts.length > 1 ? parts[1].trim() : label;
            } else {
                this.payer       = "";
                this.description = label;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Résultat du calcul de répartition pour un mois
    // ─────────────────────────────────────────────────────────────

    public static class RepartitionResult {
        /** Total des dépenses communes du mois. */
        public final double totalShared;

        /** Montant dépensé par le membre 0. */
        public final double spent0;

        /** Montant dépensé par le membre 1. */
        public final double spent1;

        /** Montant idéal que le membre 0 aurait dû payer selon le ratio. */
        public final double ideal0;

        /** Montant idéal que le membre 1 aurait dû payer selon le ratio. */
        public final double ideal1;

        /** Balance du membre 0 (+ = trop payé, - = doit payer). */
        public final double balance0;

        /** Balance du membre 1. */
        public final double balance1;

        /** Montant du remboursement dû (0 si équilibré). */
        public final double reimbursement;

        /** Nom du débiteur (celui qui doit rembourser). */
        public final String debtor;

        /** Nom du créditeur (celui qui doit être remboursé). */
        public final String creditor;

        /** Transactions partagées du mois, triées par date desc. */
        public final List<SharedTransaction> thisMonthTx;

        /** Totaux par catégorie, triés par montant desc. */
        public final Map<String, Double> categoryTotals;

        public RepartitionResult(double totalShared,
                                  double spent0, double spent1,
                                  double ideal0, double ideal1,
                                  double balance0, double balance1,
                                  double reimbursement,
                                  String debtor, String creditor,
                                  List<SharedTransaction> thisMonthTx,
                                  Map<String, Double> categoryTotals) {
            this.totalShared    = totalShared;
            this.spent0         = spent0;
            this.spent1         = spent1;
            this.ideal0         = ideal0;
            this.ideal1         = ideal1;
            this.balance0       = balance0;
            this.balance1       = balance1;
            this.reimbursement  = reimbursement;
            this.debtor         = debtor;
            this.creditor       = creditor;
            this.thisMonthTx    = thisMonthTx;
            this.categoryTotals = categoryTotals;
        }

        /** Vrai si les comptes sont équilibrés (remboursement < 0.5€). */
        public boolean isBalanced() { return reimbursement <= 0.5; }
    }

    // ─────────────────────────────────────────────────────────────
    // Historique mensuel (4 derniers mois)
    // ─────────────────────────────────────────────────────────────

    public static class MonthHistory {
        /** Écarts par mois : ecarts[mois][membre]. */
        public final double[][] ecarts;

        /** Vrai si le membre 0 a trop payé ce mois-là. */
        public final boolean[] overpayer0;

        /** Labels courts des mois (ex: "Avr.", "Mai"). */
        public final String[] labels;

        public static final int SIZE = 4;

        public MonthHistory(double[][] ecarts, boolean[] overpayer0, String[] labels) {
            this.ecarts     = ecarts;
            this.overpayer0 = overpayer0;
            this.labels     = labels;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Données complètes de la page
    // ─────────────────────────────────────────────────────────────

    public static class RepartitionData {
        /** Noms des membres du foyer. */
        public final List<String> members;

        /** Toutes les transactions (mois courant + passés). */
        public final List<SharedTransaction> allTransactions;

        /** Ratio de répartition [membre0%, membre1%]. */
        public final int[] ratio;

        public RepartitionData(List<String> members,
                                List<SharedTransaction> allTransactions,
                                int[] ratio) {
            this.members         = members;
            this.allTransactions = allTransactions;
            this.ratio           = ratio;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Icônes par catégorie
    // ─────────────────────────────────────────────────────────────

    public static String iconForCategory(String category) {
        if (category == null) return "📦";
        switch (category.toLowerCase(java.util.Locale.FRANCE)) {
            case "logement":      return "🏠";
            case "alimentation":  return "🛒";
            case "restauration":
            case "restaurant":    return "🍽️";
            case "énergie":
            case "energie":       return "⚡";
            case "transport":     return "🚗";
            case "santé":
            case "sante":         return "💊";
            case "loisirs":
            case "loisir":        return "🎬";
            case "abonnements":
            case "abonnement":    return "📱";
            case "charges fixes":
            case "charges":       return "🏦";
            case "tabac":         return "🚬";
            default:              return "📦";
        }
    }
}
