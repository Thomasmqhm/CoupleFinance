package com.couplefinance.ui.transactions;

import java.util.List;

public final class TransactionsModels {

    private TransactionsModels() {}

    // ─────────────────────────────────────────────────────────────
    // Transaction
    // ─────────────────────────────────────────────────────────────

    public static class Transaction {
        public final String  label;
        public final double  amount;
        public final String  type;
        public final String  category;
        public final long    dateMs;
        public final long    addedMs;
        public final String  person;
        public final boolean shared;
        public final boolean isShareSplit;
        public final boolean isReimbursement;
        public final String  docId;

        /**
         * Compte auquel est rattachée cette transaction.
         * null / ""  → transaction normale d'un membre
         * "joint"    → transaction du compte joint
         */
        public final String compte;

        // ── Constructeur complet (avec compte) ────────────────────
        public Transaction(String label, double amount, String type, String category,
                           long dateMs, long addedMs, String person, boolean shared,
                           boolean isShareSplit, boolean isReimbursement,
                           String docId, String compte) {
            this.label           = label;
            this.amount          = amount;
            this.type            = type;
            this.category        = category.isEmpty() ? "Autres" : category;
            this.dateMs          = dateMs;
            this.addedMs         = addedMs;
            this.person          = person;
            this.shared          = shared;
            this.isShareSplit    = isShareSplit;
            this.isReimbursement = isReimbursement;
            this.docId           = docId;
            this.compte          = compte != null ? compte.trim() : "";
        }

        // ── Constructeur rétrocompat (sans compte) ────────────────
        public Transaction(String label, double amount, String type, String category,
                           long dateMs, long addedMs, String person, boolean shared,
                           boolean isShareSplit, boolean isReimbursement, String docId) {
            this(label, amount, type, category, dateMs, addedMs, person, shared,
                    isShareSplit, isReimbursement, docId, "");
        }

        // ── Helpers ───────────────────────────────────────────────
        public boolean isIncome()  { return "income".equals(type); }
        public boolean isFixed()   {
            return "fixed".equals(type)
                    || "fixed_planned".equals(type)
                    || "fixed_done".equals(type);
        }
        public boolean isExpense() { return !isIncome() && !isShareSplit; }

        /** Transaction rattachée au compte joint. */
        public boolean isJoint()   { return "joint".equals(compte); }

        public String description() {
            return label.contains(" · ")
                    ? label.substring(label.indexOf(" · ") + 3)
                    : label;
        }

        public String payerFromLabel() {
            if (!label.contains(" · ")) return "";
            String raw = label.split(" · ")[0].trim();
            if (raw.isEmpty()) return "";
            return raw.substring(0, 1).toUpperCase(java.util.Locale.FRANCE) + raw.substring(1);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Stats
    // ─────────────────────────────────────────────────────────────

    public static class Stats {
	public final double revenues;
	public final double expenses;
	public final double net;
	public final int count;

	public Stats(double revenues, double expenses, int count) {
		this.revenues = revenues;
		this.expenses = expenses;
		this.net = revenues - expenses;
		this.count = count;
	}

	public static Stats compute(List<Transaction> transactions) {
		double rev = 0;
		double exp = 0;
		int count = 0;

		if (transactions == null) {
			return new Stats(0, 0, 0);
		}

		for (Transaction t : transactions) {
			if (t == null)
				continue;

			count++;

			if (t.isShareSplit)
				continue;

			if (isInternalTransfer(t))
				continue;

			if (t.isIncome()) {
				rev += t.amount;
			} else {
				exp += t.amount;
			}
		}

		return new Stats(rev, exp, count);
	}

	private static boolean isInternalTransfer(Transaction t) {
		if (t == null)
			return false;

		String category = t.category == null ? "" : t.category.trim().toLowerCase(java.util.Locale.FRANCE);
		String label = t.label == null ? "" : t.label.trim().toLowerCase(java.util.Locale.FRANCE);

		return category.equals("virement")
				|| category.equals("virements")
				|| label.contains("virement →")
				|| label.contains("virement reçu")
				|| label.contains("virement vers")
				|| label.contains("compte joint");
	}
}

    // ─────────────────────────────────────────────────────────────
    // FilterState
    // ─────────────────────────────────────────────────────────────

    public static class FilterState {
        public String type     = "all";
        public String period   = "current";
        public String person   = "all";
        public String category = "all";  // "all" ou nom exact de catégorie
        public String search   = "";

        public FilterState() {}
    }

    // ─────────────────────────────────────────────────────────────
    // FixedChargeSuggestion
    // ─────────────────────────────────────────────────────────────

    public static class FixedChargeSuggestion {
        public final String label;
        public final double amount;
        public final long   dateMs;
        public final String category;
        public final int    occurrences;

        public FixedChargeSuggestion(String label, double amount, long dateMs,
                                      String category, int occurrences) {
            this.label       = label;
            this.amount      = amount;
            this.dateMs      = dateMs;
            this.category    = category;
            this.occurrences = occurrences;
        }
    }
}
