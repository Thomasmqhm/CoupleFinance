package com.couplefinance.ui.home;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Résultat calculé pour le widget "Qui doit quoi".
 * Aucune dépendance Android : cette classe reste testable et réutilisable.
 */
public class HomeDebtSummary {
    public final Map<String, Double> expensesByPerson = new LinkedHashMap<>();
    public final Map<String, Double> adjustedContributionByPerson = new LinkedHashMap<>();

    public double totalExpenses;
    public double idealShare;
    public String debtor;
    public String creditor;
    public double maxDebt;
    public double maxCredit;

    public double amountToTransfer() {
        return Math.min(maxCredit, maxDebt);
    }

    public boolean hasTransfer() {
        return expensesByPerson.size() >= 2 && totalExpenses > 0.01 && amountToTransfer() > 0.01;
    }
}
