package com.couplefinance.utils;

/**
 * Représente une transaction extraite d'un OCR bancaire,
 * ticket de caisse ou relevé PDF.
 */
public class ParsedTransaction {

    /** Libellé affiché à l'utilisateur. */
    public String label;

    /**
     * Montant TOUJOURS positif.
     * Le signe est porté par "type".
     */
    public double amount;

    /** "expense" ou "income". */
    public String type;

    /** Catégorie détectée automatiquement. */
    public String category;

    /** Timestamp transaction en millisecondes. */
    public long dateMs;

    /** Transaction sélectionnée pour import. */
    public boolean selected;

    /** Doublon exact détecté. */
    public boolean duplicate;

    /** Doublon probable détecté. */
    public boolean suspectedDuplicate;

    /** Message détaillé de doublon. */
    public String duplicateReason;

    /** Message court affichable UI. */
    public String duplicateWarning;

    /** Clé commerçant stable. */
    public String merchantKey;

    /** Peut devenir une charge fixe. */
    public boolean recurringCandidate;

    /** Propriétaire du compte source : "thomas", "melissa", "joint" ou "". */
    public String owner = "";

    /** Index du compte bancaire source (0, 1, ...). */
    public int accountIndex = -1;

    /** IBAN court du compte source (ex: "...04036"). */
    public String accountIban = "";

    public ParsedTransaction(
            String label,
            double amount,
            String type,
            String category,
            long dateMs
    ) {

        this.label = label != null ? label : "";
        this.amount = Math.abs(amount);

        this.type = type != null && !type.trim().isEmpty()
                ? type
                : "expense";

        this.category = category != null && !category.trim().isEmpty()
                ? category
                : "Autre";

        this.dateMs = dateMs;

        this.selected = true;

        this.duplicate = false;
        this.suspectedDuplicate = false;

        this.duplicateReason = "";
        this.duplicateWarning = "";

        this.merchantKey = "";

        this.recurringCandidate = false;
    }

    public ParsedTransaction() {

        this.label = "";
        this.amount = 0;
        this.type = "expense";
        this.category = "Autre";
        this.dateMs = System.currentTimeMillis();

        this.selected = true;

        this.duplicate = false;
        this.suspectedDuplicate = false;

        this.duplicateReason = "";
        this.duplicateWarning = "";

        this.merchantKey = "";

        this.recurringCandidate = false;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label != null ? label : "";
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = Math.abs(amount);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {

        if (type == null || type.trim().isEmpty()) {
            this.type = "expense";
            return;
        }

        this.type = type;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {

        this.category = category != null && !category.trim().isEmpty()
                ? category
                : "Autre";
    }

    public long getDateMs() {
        return dateMs;
    }

    public void setDateMs(long dateMs) {
        this.dateMs = dateMs;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public void setDuplicate(boolean duplicate) {
        this.duplicate = duplicate;
    }

    public boolean isSuspectedDuplicate() {
        return suspectedDuplicate;
    }

    public void setSuspectedDuplicate(boolean suspectedDuplicate) {
        this.suspectedDuplicate = suspectedDuplicate;
    }

    public String getDuplicateReason() {
        return duplicateReason;
    }

    public void setDuplicateReason(String duplicateReason) {

        this.duplicateReason = duplicateReason != null
                ? duplicateReason
                : "";
    }

    public String getDuplicateWarning() {
        return duplicateWarning;
    }

    public void setDuplicateWarning(String duplicateWarning) {

        this.duplicateWarning = duplicateWarning != null
                ? duplicateWarning
                : "";
    }

    public String getMerchantKey() {
        return merchantKey;
    }

    public void setMerchantKey(String merchantKey) {

        this.merchantKey = merchantKey != null
                ? merchantKey
                : "";
    }

    public boolean isRecurringCandidate() {
        return recurringCandidate;
    }

    public void setRecurringCandidate(boolean recurringCandidate) {
        this.recurringCandidate = recurringCandidate;
    }

    @Override
    public String toString() {

        return "ParsedTransaction{" +
                "label='" + label + '\'' +
                ", amount=" + amount +
                ", type='" + type + '\'' +
                ", category='" + category + '\'' +
                ", dateMs=" + dateMs +
                ", selected=" + selected +
                ", duplicate=" + duplicate +
                ", suspectedDuplicate=" + suspectedDuplicate +
                ", merchantKey='" + merchantKey + '\'' +
                '}';
    }
}