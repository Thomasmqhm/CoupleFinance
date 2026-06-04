package com.couplefinance.ui.credits;

import java.util.List;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  CreditsCalculator — Calculs métier purs                    ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Toutes les méthodes sont statiques, sans dépendance        ║
 * ║  Android. 100% testables unitairement.                      ║
 * ║                                                             ║
 * ║  Appelé par : CreditsView, CreditsInsights                  ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
public final class CreditsCalculator {

    private CreditsCalculator() {}

    // ─────────────────────────────────────────────────────────────
    // Capital et mensualités
    // ─────────────────────────────────────────────────────────────

    /**
     * Estime le capital restant dû d'un crédit à l'instant présent.
     * Formule simplifiée linéaire : proportionnelle au temps restant.
     */
    public static double computeRemaining(CreditsModels.Credit c) {
        if (c.startDateMs <= 0 || c.durationMonths <= 0) return c.totalAmount;
        long endMs = c.startDateMs + (long) c.durationMonths * 30L * 24L * 3600L * 1000L;
        long now   = System.currentTimeMillis();
        if (now >= endMs) return 0;
        double monthsLeft = (endMs - now) / (30.0 * 24.0 * 3600.0 * 1000.0);
        return Math.max(0, Math.min(c.totalAmount, c.monthlyPayment * monthsLeft));
    }

    /**
     * Retourne le nombre de mois restants avant la fin du crédit.
     * Retourne 0 si le crédit est terminé.
     */
    public static int monthsLeft(CreditsModels.Credit c) {
        if (c.startDateMs <= 0 || c.durationMonths <= 0) return 0;
        long endMs = c.startDateMs + (long) c.durationMonths * 30L * 24L * 3600L * 1000L;
        long now   = System.currentTimeMillis();
        return (int) Math.max(0, (endMs - now) / (30L * 24L * 3600L * 1000L));
    }

    /**
     * Timestamp de fin du crédit (en ms).
     */
    public static long endDateMs(CreditsModels.Credit c) {
        return c.startDateMs + (long) c.durationMonths * 30L * 24L * 3600L * 1000L;
    }

    /**
     * Montant déjà remboursé (total - restant).
     */
    public static double amountPaid(CreditsModels.Credit c) {
        return Math.max(0, c.totalAmount - computeRemaining(c));
    }

    /**
     * Pourcentage remboursé (0-100), clampé.
     */
    public static int percentPaid(CreditsModels.Credit c) {
        if (c.totalAmount <= 0) return 0;
        return (int) Math.min(100, Math.round((amountPaid(c) / c.totalAmount) * 100));
    }

    // ─────────────────────────────────────────────────────────────
    // Taux d'endettement
    // ─────────────────────────────────────────────────────────────

    /**
     * Calcule le taux d'endettement réel :
     *   (mensualités crédits + charges fixes) / revenus × 100
     *
     * Retourne 0 si les revenus sont nuls (calcul impossible).
     */
    public static double debtRatio(double totalMonthly, double totalFixedCharges,
                                   double totalRevenue) {
        if (totalRevenue <= 0) return 0;
        return ((totalMonthly + totalFixedCharges) / totalRevenue) * 100.0;
    }

    /** Vrai si le taux d'endettement dépasse le seuil légal de 35%. */
    public static boolean isOverLegalLimit(double debtRatio) {
        return debtRatio > 35;
    }

    /** Vrai si le taux d'endettement est dans la zone de vigilance (25-35%). */
    public static boolean isInWatchZone(double debtRatio) {
        return debtRatio > 25 && debtRatio <= 35;
    }

    // ─────────────────────────────────────────────────────────────
    // Agrégats sur la liste complète
    // ─────────────────────────────────────────────────────────────

    /** Somme des mensualités de tous les crédits actifs. */
    public static double totalMonthly(List<CreditsModels.Credit> credits) {
        double sum = 0;
        for (CreditsModels.Credit c : credits) sum += c.monthlyPayment;
        return sum;
    }

    /** Somme du capital restant de tous les crédits. */
    public static double totalRemaining(List<CreditsModels.Credit> credits) {
        double sum = 0;
        for (CreditsModels.Credit c : credits) sum += computeRemaining(c);
        return sum;
    }

    /**
     * Retourne le crédit qui sera soldé en premier (le moins de mois restants).
     * Retourne null si la liste est vide ou tous les crédits sont terminés.
     */
    public static CreditsModels.Credit soonestToFinish(List<CreditsModels.Credit> credits) {
        CreditsModels.Credit soonest = null;
        int minMonths = Integer.MAX_VALUE;
        for (CreditsModels.Credit c : credits) {
            int ml = monthsLeft(c);
            if (ml > 0 && ml < minMonths) {
                minMonths = ml;
                soonest   = c;
            }
        }
        return soonest;
    }

    /**
     * Retourne le crédit avec la durée restante la plus longue.
     * Utile pour suggérer un remboursement anticipé.
     */
    public static CreditsModels.Credit longestRemaining(List<CreditsModels.Credit> credits) {
        CreditsModels.Credit longest = null;
        int maxMonths = 0;
        for (CreditsModels.Credit c : credits) {
            int ml = monthsLeft(c);
            if (ml > maxMonths) { maxMonths = ml; longest = c; }
        }
        return longest;
    }

    // ─────────────────────────────────────────────────────────────
    // Intérêts estimés
    // ─────────────────────────────────────────────────────────────

    /**
     * Estime les intérêts restants à payer sur un crédit.
     * Formule : (mensualité × mois restants) - capital restant.
     */
    public static double estimatedInterestsLeft(CreditsModels.Credit c) {
        int ml        = monthsLeft(c);
        double remaining = computeRemaining(c);
        return Math.max(0, c.monthlyPayment * ml - remaining);
    }

    /** Somme des intérêts estimés restants sur tous les crédits. */
    public static double totalEstimatedInterests(List<CreditsModels.Credit> credits) {
        double sum = 0;
        for (CreditsModels.Credit c : credits) sum += estimatedInterestsLeft(c);
        return sum;
    }

    // ─────────────────────────────────────────────────────────────
    // Projection des mensualités par année
    // ─────────────────────────────────────────────────────────────

    /**
     * Calcule les mensualités totales pour chaque année future.
     * Utilisé par CreditsInsights pour le graphique de projection.
     *
     * @param credits    liste des crédits actifs
     * @param maxYears   nombre d'années à projeter
     * @return tableau de taille maxYears, chaque case = mensualités totales cette année
     */
    public static double[] projectionByYear(List<CreditsModels.Credit> credits, int maxYears) {
        double[] result = new double[maxYears];
        for (int y = 0; y < maxYears; y++) {
            int monthOffset = y * 12;
            for (CreditsModels.Credit c : credits)
                if (monthsLeft(c) > monthOffset) result[y] += c.monthlyPayment;
        }
        return result;
    }

    /**
     * Retourne le nombre d'années max nécessaires pour couvrir tous les crédits.
     * Clampé à maxYears.
     */
    public static int maxYearsNeeded(List<CreditsModels.Credit> credits, int maxYears) {
        int max = 0;
        for (CreditsModels.Credit c : credits)
            max = Math.max(max, (int) Math.ceil(monthsLeft(c) / 12.0));
        return Math.min(max + 1, maxYears);
    }
}
