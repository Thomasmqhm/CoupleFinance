package com.couplefinance.ui.transactions;

/**
 * Suggestion d'auto-remplissage pour une transaction.
 * Classe volontairement sans dépendance Android : utilisable plus tard par l'import PDF.
 */
public final class TransactionSuggestion {

    public final boolean found;
    public final String label;
    public final String cleanLabel;
    public final String type;
    public final String category;
    public final String person;
    public final double averageAmount;
    public final int confidence;
    public final String reason;

    private TransactionSuggestion(boolean found, String label, String cleanLabel, String type, String category,
            String person, double averageAmount, int confidence, String reason) {
        this.found = found;
        this.label = label == null ? "" : label;
        this.cleanLabel = cleanLabel == null ? "" : cleanLabel;
        this.type = type == null ? "" : type;
        this.category = category == null ? "" : category;
        this.person = person == null ? "" : person;
        this.averageAmount = averageAmount;
        this.confidence = confidence;
        this.reason = reason == null ? "" : reason;
    }

    public static TransactionSuggestion none() {
        return new TransactionSuggestion(false, "", "", "", "", "", 0d, 0, "");
    }

    public static TransactionSuggestion of(String label, String cleanLabel, String type, String category, String person,
            double averageAmount, int confidence, String reason) {
        return new TransactionSuggestion(true, label, cleanLabel, type, category, person, averageAmount, confidence,
                reason);
    }

    public boolean hasAmount() {
        return averageAmount > 0.009d;
    }

    public String typeLabel() {
        if ("income".equals(type))
            return "Revenu";
        if ("fixed".equals(type))
            return "Charge fixe";
        if ("variable".equals(type))
            return "Dépense";
        return "Transaction";
    }
}
