package com.couplefinance.ui.home;

import com.couplefinance.data.CycleManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HomeCalculator — Calculs financiers purs extraits de HomeView.
 *
 * Cycle configurable :
 *   Les filtres "mois courant" et "mois précédent" délèguent désormais
 *   à CycleManager.isInCurrentCycle() / isInPreviousCycle().
 *   Si cycleStartDay=1, le comportement est identique à avant.
 *
 * Correction principale (inchangée) :
 *   Si un solde de départ a été saisi en cours de cycle, le solde actuel
 *   ne prend en compte que les transactions >= date de saisie du solde.
 */
public final class HomeCalculator {

    private HomeCalculator() {}

    public static class Result {
        public double totalIncome;
        public double totalExpenses;
        public double availableIncome;
        public double availableExpenses;
        public double prevIncome;
        public double prevExpenses;

        public int currentMonthTxCount;
        public int currentMonthIncomeCount;
        public int currentMonthExpenseCount;
        public int todayExpenseCount;
        public double todayExpenses;

        public double biggestExpenseAmount;
        public String biggestExpenseLabel    = "";
        public String biggestExpenseCategory = "";

        public long latestUnexpectedExpenseDate;

        public Map<String, double[]> personBalances        = new LinkedHashMap<>();
        public Map<String, Double>   categoryTotals        = new HashMap<>();
        public Map<String, Double>   incomeSources         = new HashMap<>();
        public Set<String>           activeExpenseCategories = new HashSet<>();
        public List<String[]>        recentTx              = new ArrayList<>();
    }

    public static Result compute(List<String[]> transactions,
                                 List<String>   allHouseholdMembers,
                                 String         myName,
                                 long           monthlyStartBalanceDate) {

        Result r = new Result();

        if (myName != null && !myName.isEmpty()) {
            r.personBalances.put(myName, new double[]{0, 0, 0});
        }

        if (allHouseholdMembers != null) {
            for (String member : allHouseholdMembers) {
                if (member == null || member.trim().isEmpty()) continue;
                boolean exists = false;
                for (String k : r.personBalances.keySet()) {
                    if (k.equalsIgnoreCase(member.trim())) { exists = true; break; }
                }
                if (!exists) r.personBalances.put(member.trim(), new double[]{0, 0, 0});
            }
        }

        // ── Obtenir le cycle courant et précédent via CycleManager ──────────
        // CycleManager est un singleton déjà initialisé au démarrage de l'app.
        CycleManager cycle = CycleManager.getInstance();

        // ── Calendar pour les comparaisons "aujourd'hui" (inchangé) ─────────
        Calendar todayCal = Calendar.getInstance();

        for (String[] tx : transactions) {
            if (tx == null || tx.length < 5) continue;

            double amount = 0;
            try { amount = Double.parseDouble(tx[1]); } catch (Exception ignored) {}

            String label    = tx[0] == null ? "" : tx[0];
            String type     = tx[2] == null ? "" : tx[2];
            String category = tx[3] == null ? "" : tx[3];

            long dateMs = 0;
            try { dateMs = Long.parseLong(tx[4]); } catch (Exception ignored) {}

            boolean isShareSplit     = tx.length > 5 && "true".equals(tx[5]);
            boolean isReimbursement  = tx.length > 6 && "true".equals(tx[6]);

            if (isShareSplit) continue;

            boolean isInternal = "Virement".equals(category)
                    && "income".equals(type)
                    && (label.contains("Virement reçu") || label.contains("Remboursement reçu"));

            // ── Résolution de la personne ────────────────────────────────────
            String person = null;
            if (label.contains(" · ")) {
                person = label.split(" · ")[0].trim();
                if (!person.isEmpty()) {
                    person = person.substring(0, 1).toUpperCase()
                           + person.substring(1);
                }
            }

            if (person != null && !person.isEmpty()) {
                String existingKey = null;
                for (String k : r.personBalances.keySet()) {
                    if (k.equalsIgnoreCase(person)) { existingKey = k; break; }
                }
                if (existingKey == null) {
                    r.personBalances.put(person, new double[]{0, 0, 0});
                } else {
                    person = existingKey;
                }
            }

            // ── Appartenance au cycle courant / précédent ────────────────────
            // Remplace les anciennes comparaisons Calendar.MONTH / Calendar.YEAR.
            boolean isCurCycle  = cycle.isInCurrentCycle(dateMs);
            boolean isPrevCycle = cycle.isInPreviousCycle(dateMs);

            // ── Appartenance à "aujourd'hui" (inchangé — toujours calendaire) ─
            Calendar txCal = Calendar.getInstance();
            if (dateMs > 0) txCal.setTimeInMillis(dateMs);
            boolean isToday =
                    txCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
                    && txCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR);

            boolean impactsBalance = shouldImpactBalance(dateMs, monthlyStartBalanceDate);

            if (isInternal) continue;

            // ── Remboursements ───────────────────────────────────────────────
            if (isReimbursement || label.contains("Rééquilibrage des dépenses")) {
                if (isCurCycle && impactsBalance && person != null && !"income".equals(type)) {
                    double[] vals = r.personBalances.get(person);
                    if (vals != null && vals.length > 2) vals[2] += amount;
                }
                r.recentTx.add(tx);
                continue;
            }

            // ── Statistiques du cycle courant ────────────────────────────────
            if (isCurCycle) {
                r.currentMonthTxCount++;

                if ("income".equals(type)) {
                    r.currentMonthIncomeCount++;
                } else {
                    r.currentMonthExpenseCount++;
                }

                if (!"income".equals(type)) {
                    if (amount > r.biggestExpenseAmount) {
                        r.biggestExpenseAmount   = amount;
                        r.biggestExpenseLabel    = label;
                        r.biggestExpenseCategory = category;
                    }

                    if (category != null && !category.isEmpty()) {
                        r.activeExpenseCategories.add(category);
                    }

                    if (isToday) {
                        r.todayExpenses += amount;
                        r.todayExpenseCount++;
                    }

                    if (dateMs > r.latestUnexpectedExpenseDate
                            && isUnexpectedExpense(category, label)) {
                        r.latestUnexpectedExpenseDate = dateMs;
                    }

                    if (category != null && !category.isEmpty()) {
                        Double prev = r.categoryTotals.get(category);
                        r.categoryTotals.put(category,
                                (prev == null ? 0.0 : prev) + amount);
                    }
                }

                if ("income".equals(type)) {
                    String source = category.isEmpty() ? "Revenus" : category;
                    Double prev   = r.incomeSources.get(source);
                    r.incomeSources.put(source, (prev == null ? 0.0 : prev) + amount);
                }
            }

            // ── Soldes par personne (cycle courant uniquement) ───────────────
            if (isCurCycle && impactsBalance && person != null) {
                String personKey = person;
                for (String k : r.personBalances.keySet()) {
                    if (k.equalsIgnoreCase(person)) { personKey = k; break; }
                }
                double[] vals = r.personBalances.get(personKey);
                if (vals != null) {
                    if ("income".equals(type)) {
                        r.totalIncome       += amount;
                        r.availableIncome   += amount;
                        vals[0]             += amount;
                    } else {
                        r.totalExpenses     += amount;
                        r.availableExpenses += amount;
                        vals[1]             += amount;
                    }
                }
            }

            // ── Statistiques du cycle précédent ──────────────────────────────
            if (isPrevCycle) {
                if ("income".equals(type)) {
                    r.prevIncome += amount;
                } else {
                    r.prevExpenses += amount;
                }
            }

            r.recentTx.add(tx);
        }

        // Tri par date décroissante
        Collections.sort(r.recentTx, (a, b) -> {
            long da = 0, db = 0;
            try { da = Long.parseLong(a[4]); } catch (Exception ignored) {}
            try { db = Long.parseLong(b[4]); } catch (Exception ignored) {}
            return Long.compare(db, da);
        });

        return r;
    }

    public static boolean isUnexpectedExpense(String category, String label) {
        if (category == null || label == null) return false;
        String c = category.toLowerCase();
        String l = label.toLowerCase();
        return c.contains("médical")
            || c.contains("santé")
            || c.contains("urgence")
            || c.contains("réparation")
            || l.contains("urgence")
            || l.contains("réparation");
    }

    public static int financialScoreDetailed(double income,
                                             double expenses,
                                             double balance,
                                             boolean overdraftDefined,
                                             double overdraftLimit) {
        return financialScoreDetailed(income, expenses, balance,
                overdraftDefined, overdraftLimit, 0);
    }

    /**
     * Calcule le score de santé financière du foyer.
     *
     * @param membersInOverdraft nombre de membres individuellement à solde négatif.
     */
    public static int financialScoreDetailed(double income,
                                             double expenses,
                                             double balance,
                                             boolean overdraftDefined,
                                             double overdraftLimit,
                                             int membersInOverdraft) {

        if (income <= 0 && expenses <= 0) return 50;

        int score = 100;

        if (income > 0) {
            double ratio = expenses / income;
            if      (ratio > 1.0) score -= 40;
            else if (ratio > 0.9) score -= 30;
            else if (ratio > 0.8) score -= 20;
            else if (ratio > 0.7) score -= 10;
            else if (ratio > 0.6) score -= 5;
        } else if (expenses > 0) {
            score -= 30;
        }

        if (balance < 0) {
            if (overdraftDefined && (-balance) < overdraftLimit) score -= 10;
            else score -= 25;
        }

        if (membersInOverdraft > 0) score -= membersInOverdraft * 20;

        return Math.max(0, Math.min(100, score));
    }

    private static boolean shouldImpactBalance(long transactionDateMs,
                                               long monthlyStartBalanceDate) {
        if (monthlyStartBalanceDate <= 0) return true;
        if (transactionDateMs <= 0)       return true;
        return transactionDateMs >= monthlyStartBalanceDate;
    }
}
