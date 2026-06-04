package com.couplefinance.ui.epargne;

import java.util.Calendar;
import java.util.List;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  EpargneCalculator — Calculs métier purs                    ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Toutes les méthodes sont statiques, sans dépendance        ║
 * ║  Android. 100% testables unitairement.                      ║
 * ║                                                             ║
 * ║  Appelé par : EpargneView, EpargneInsights                  ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
public final class EpargneCalculator {

    private EpargneCalculator() {}

    // ─────────────────────────────────────────────────────────────
    // Calculs par objectif
    // ─────────────────────────────────────────────────────────────

    /**
     * Pourcentage de progression d'un objectif (0-100, clampé).
     */
    public static int progressPercent(EpargneModels.SavingsGoal goal) {
        if (goal.target <= 0) return 0;
        return (int) Math.min(100, Math.max(0,
            Math.round((goal.current / goal.target) * 100)));
    }

    /**
     * Nombre de mois entre deux timestamps.
     * Retourne 0 si from >= to.
     */
    public static int monthsBetween(long fromMs, long toMs) {
        if (fromMs >= toMs) return 0;
        Calendar from = Calendar.getInstance(); from.setTimeInMillis(fromMs);
        Calendar to   = Calendar.getInstance(); to.setTimeInMillis(toMs);
        int months = (to.get(Calendar.YEAR) - from.get(Calendar.YEAR)) * 12
                   + (to.get(Calendar.MONTH) - from.get(Calendar.MONTH));
        if (to.get(Calendar.DAY_OF_MONTH) < from.get(Calendar.DAY_OF_MONTH)) months--;
        return Math.max(0, months);
    }

    /**
     * Mensualité recommandée pour atteindre l'objectif à la date cible.
     * Si pas de date → base sur 12 mois par défaut.
     * Si objectif atteint → retourne 0.
     */
    public static double smartMonthly(EpargneModels.SavingsGoal goal) {
        double remaining = goal.remaining();
        if (remaining <= 0) return 0;

        long now = System.currentTimeMillis();
        if (goal.hasDate()) {
            int months = monthsBetween(now, goal.targetDateMs);
            if (months > 0) return Math.ceil(remaining / months);
        }
        return Math.ceil(remaining / 12.0);
    }

    /**
     * Nombre de mois restants avant la date cible.
     * Retourne 0 si pas de date ou date dépassée.
     */
    public static int monthsLeft(EpargneModels.SavingsGoal goal) {
        if (!goal.hasDate()) return 0;
        return monthsBetween(System.currentTimeMillis(), goal.targetDateMs);
    }

    /**
     * Mois estimé de fin (ex: "Juin 2028") basé sur la mensualité actuelle.
     * Retourne une chaîne vide si la mensualité est nulle.
     */
    public static java.util.Date estimatedEndDate(EpargneModels.SavingsGoal goal) {
        double monthly = smartMonthly(goal);
        if (monthly <= 0) return null;
        int months = (int) Math.ceil(goal.remaining() / monthly);
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MONTH, months);
        return c.getTime();
    }

    /**
     * Label du badge affiché sur la carte objectif.
     * Ex: "5 mois" si date, "Prioritaire" si >75%, "Court terme" sinon.
     */
    public static String badgeLabel(EpargneModels.SavingsGoal goal) {
        if (goal.isCompleted()) return "✓ Atteint";
        if (goal.isLate())      return "⚠ En retard";
        if (goal.isAtRisk())    return "⏰ Urgent";
        if (goal.hasDate())     return monthsLeft(goal) + " mois";

        int pct = progressPercent(goal);
        if (pct >= 75)            return "Prioritaire";
        if (goal.target >= 5000)  return "Long terme";
        if (goal.target >= 2000)  return "Moyen terme";
        return "Court terme";
    }

    // ─────────────────────────────────────────────────────────────
    // Calculs sur la liste complète
    // ─────────────────────────────────────────────────────────────

    /** Somme de tous les montants épargnés. */
    public static double totalSaved(List<EpargneModels.SavingsGoal> goals) {
        double sum = 0;
        for (EpargneModels.SavingsGoal g : goals) sum += g.current;
        return sum;
    }

    /** Somme de tous les montants cibles. */
    public static double totalTarget(List<EpargneModels.SavingsGoal> goals) {
        double sum = 0;
        for (EpargneModels.SavingsGoal g : goals) sum += g.target;
        return sum;
    }

    /** Nombre d'objectifs atteints. */
    public static int countCompleted(List<EpargneModels.SavingsGoal> goals) {
        int count = 0;
        for (EpargneModels.SavingsGoal g : goals) if (g.isCompleted()) count++;
        return count;
    }

    /** Nombre d'objectifs en retard. */
    public static int countLate(List<EpargneModels.SavingsGoal> goals) {
        int count = 0;
        for (EpargneModels.SavingsGoal g : goals) if (g.isLate()) count++;
        return count;
    }

    /** Pourcentage global d'avancement (0-100). */
    public static int globalPercent(List<EpargneModels.SavingsGoal> goals) {
        double total  = totalTarget(goals);
        double saved  = totalSaved(goals);
        if (total <= 0) return 0;
        return (int) Math.min(100, Math.round((saved / total) * 100));
    }

    /**
     * Objectif avec la date cible la plus proche (et non atteint).
     * Retourne null si aucun objectif n'a de date dans le futur.
     */
    public static EpargneModels.SavingsGoal mostUrgent(List<EpargneModels.SavingsGoal> goals) {
        EpargneModels.SavingsGoal soonest = null;
        long minDate = Long.MAX_VALUE;
        long now     = System.currentTimeMillis();
        for (EpargneModels.SavingsGoal g : goals) {
            if (g.targetDateMs > now && !g.isCompleted() && g.targetDateMs < minDate) {
                minDate = g.targetDateMs;
                soonest = g;
            }
        }
        return soonest;
    }

    /**
     * Objectif le plus avancé en % (non complété).
     * Utile pour la suggestion de priorité quand aucun n'a de date.
     */
    public static EpargneModels.SavingsGoal mostAdvanced(List<EpargneModels.SavingsGoal> goals) {
        EpargneModels.SavingsGoal best = null;
        double bestPct = -1;
        for (EpargneModels.SavingsGoal g : goals) {
            if (!g.isCompleted()) {
                double pct = g.target > 0 ? g.current / g.target : 0;
                if (pct > bestPct) { bestPct = pct; best = g; }
            }
        }
        return best;
    }

    /**
     * Somme des mensualités recommandées pour tous les objectifs actifs.
     * Utile pour la projection annuelle.
     */
    public static double totalMonthlyNeeded(List<EpargneModels.SavingsGoal> goals) {
        double sum = 0;
        for (EpargneModels.SavingsGoal g : goals)
            if (!g.isCompleted()) sum += smartMonthly(g);
        return sum;
    }

    /**
     * Taux d'épargne estimé : (totalSaved / totalTarget) * 100.
     * Retourne 0 si totalTarget est nul.
     */
    public static double savingsRate(List<EpargneModels.SavingsGoal> goals) {
        double total = totalTarget(goals);
        if (total <= 0) return 0;
        return (totalSaved(goals) / total) * 100.0;
    }

    /** Meilleur mois dans l'historique (index + valeur). */
    public static int bestMonthIndex(double[] history) {
        if (history == null || history.length == 0) return -1;
        int    best    = 0;
        double bestVal = history[0];
        for (int i = 1; i < history.length; i++) {
            if (history[i] > bestVal) { bestVal = history[i]; best = i; }
        }
        return best;
    }
}
