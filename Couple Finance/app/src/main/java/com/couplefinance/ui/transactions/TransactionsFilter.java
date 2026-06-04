package com.couplefinance.ui.transactions;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public final class TransactionsFilter {

    private TransactionsFilter() {}

    public static List<TransactionsModels.Transaction> apply(
            List<TransactionsModels.Transaction> all,
            TransactionsModels.FilterState state) {

        Calendar now = Calendar.getInstance();
        int curMonth = now.get(Calendar.MONTH), curYear = now.get(Calendar.YEAR);
        Calendar prev = (Calendar) now.clone();
        prev.add(Calendar.MONTH, -1);
        int prevMonth = prev.get(Calendar.MONTH), prevYear = prev.get(Calendar.YEAR);

        int specificMonth = -1, specificYear = -1;
        if (state.period != null && state.period.startsWith("month:")) {
            int[] parsed = parseMonthPeriod(state.period);
            specificMonth = parsed[0];
            specificYear  = parsed[1];
        }

        List<TransactionsModels.Transaction> result = new ArrayList<>();

        for (TransactionsModels.Transaction tx : all) {
            if (!passesTypeFilter(tx, state.type))   continue;
            if (!passesPeriodFilterExtended(tx, state.period,
                    curMonth, curYear, prevMonth, prevYear,
                    specificMonth, specificYear))      continue;
            if (!passesPersonFilter(tx, state.person)) continue;
            if (!passesCategoryFilter(tx, state.category)) continue;
            if (!passesSearchFilter(tx, state.search)) continue;
            result.add(tx);
        }

        sortByDateDesc(result);
        return result;
    }

    public static boolean passesTypeFilter(TransactionsModels.Transaction tx, String type) {
        switch (type) {
            case "income":   return tx.isIncome();
            case "expenses": return !tx.isIncome() && !tx.isShareSplit;
            default:         return true;
        }
    }

    public static boolean passesPeriodFilter(TransactionsModels.Transaction tx, String period,
                                              int curMonth, int curYear,
                                              int prevMonth, int prevYear) {
        return passesPeriodFilterExtended(tx, period,
                curMonth, curYear, prevMonth, prevYear, -1, -1);
    }

    private static boolean passesPeriodFilterExtended(TransactionsModels.Transaction tx,
                                                       String period,
                                                       int curMonth, int curYear,
                                                       int prevMonth, int prevYear,
                                                       int specificMonth, int specificYear) {
        if ("all".equals(period)) return true;
        if (tx.dateMs <= 0) return true;

        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(tx.dateMs);
        int txMonth = c.get(Calendar.MONTH), txYear = c.get(Calendar.YEAR);

        if ("current".equals(period)) return txMonth == curMonth  && txYear == curYear;
        if ("last".equals(period))    return txMonth == prevMonth && txYear == prevYear;

        if (specificMonth >= 0 && specificYear >= 0)
            return txMonth == specificMonth && txYear == specificYear;

        return true;
    }

    public static boolean passesPersonFilter(TransactionsModels.Transaction tx, String person) {
        if ("all".equals(person)) return true;
        return person.equalsIgnoreCase(tx.person)
            || person.equalsIgnoreCase(tx.payerFromLabel());
    }

    public static boolean passesCategoryFilter(TransactionsModels.Transaction tx, String category) {
        if (category == null || "all".equals(category)) return true;
        if (tx.category == null) return false;
        return tx.category.equalsIgnoreCase(category);
    }

    public static boolean passesSearchFilter(TransactionsModels.Transaction tx, String query) {
        if (query == null || query.isEmpty()) return true;
        String q = query.toLowerCase(java.util.Locale.FRANCE);
        return tx.label.toLowerCase(java.util.Locale.FRANCE).contains(q)
            || tx.category.toLowerCase(java.util.Locale.FRANCE).contains(q)
            || tx.person.toLowerCase(java.util.Locale.FRANCE).contains(q);
    }

    public static TransactionsModels.Stats computeStats(
            List<TransactionsModels.Transaction> filtered) {
        return TransactionsModels.Stats.compute(filtered);
    }

    // ── Navigation historique ─────────────────────────────────────

    /**
     * Retourne tous les mois distincts présents dans les transactions,
     * triés du plus récent au plus ancien. Format : "month:YYYY-MM"
     */
    public static List<String> availableMonths(List<TransactionsModels.Transaction> all) {
        List<String> months = new ArrayList<>();
        if (all == null) return months;

        for (TransactionsModels.Transaction tx : all) {
            if (tx.dateMs <= 0) continue;
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(tx.dateMs);
            String key = "month:" + c.get(Calendar.YEAR) + "-"
                    + String.format("%02d", c.get(Calendar.MONTH) + 1);
            if (!months.contains(key)) months.add(key);
        }

        Collections.sort(months, Collections.reverseOrder());
        return months;
    }

    /**
     * Retourne la période précédente (plus ancienne) dans la liste.
     */
    public static String previousPeriod(List<String> available, String current) {
        if (available == null || available.isEmpty()) return null;
        int idx = available.indexOf(current);
        if (idx < 0 || idx >= available.size() - 1) return null;
        return available.get(idx + 1);
    }

    /**
     * Retourne la période suivante (plus récente) dans la liste.
     */
    public static String nextPeriod(List<String> available, String current) {
        if (available == null || available.isEmpty()) return null;
        int idx = available.indexOf(current);
        if (idx <= 0) return null;
        return available.get(idx - 1);
    }

    /**
     * Parse "month:2026-03" → [month 0-based, year]
     */
    public static int[] parseMonthPeriod(String period) {
        try {
            String[] parts = period.replace("month:", "").split("-");
            int year  = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]) - 1;
            return new int[]{ month, year };
        } catch (Exception e) {
            return new int[]{ -1, -1 };
        }
    }

    /**
     * Convertit "month:2026-03" → "Mars 2026"
     */
    public static String monthLabel(String period) {
        if (period == null)            return "";
        if ("all".equals(period))      return "Toutes les opérations";
        if ("current".equals(period))  return listTitle("current");
        if ("last".equals(period))     return listTitle("last");

        if (period.startsWith("month:")) {
            int[] parsed = parseMonthPeriod(period);
            if (parsed[0] >= 0 && parsed[1] >= 0) {
                String[] names = { "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
                        "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre" };
                return names[parsed[0]] + " " + parsed[1];
            }
        }
        return period;
    }

    // ── Compteurs par période ─────────────────────────────────────

    /**
     * Compte les transactions correspondant à une période donnée,
     * en appliquant les filtres type/person/category/search déjà actifs.
     * Utilisé pour afficher le badge sur chaque bouton de période.
     */
    public static int countForPeriod(List<TransactionsModels.Transaction> all,
                                     TransactionsModels.FilterState state,
                                     String period) {
        if (all == null) return 0;

        Calendar now = Calendar.getInstance();
        int curMonth = now.get(Calendar.MONTH), curYear = now.get(Calendar.YEAR);
        Calendar prev = (Calendar) now.clone();
        prev.add(Calendar.MONTH, -1);
        int prevMonth = prev.get(Calendar.MONTH), prevYear = prev.get(Calendar.YEAR);

        int specificMonth = -1, specificYear = -1;
        if (period != null && period.startsWith("month:")) {
            int[] parsed = parseMonthPeriod(period);
            specificMonth = parsed[0];
            specificYear  = parsed[1];
        }

        int count = 0;
        for (TransactionsModels.Transaction tx : all) {
            if (!passesTypeFilter(tx, state.type))   continue;
            if (!passesPeriodFilterExtended(tx, period,
                    curMonth, curYear, prevMonth, prevYear,
                    specificMonth, specificYear))      continue;
            if (!passesPersonFilter(tx, state.person)) continue;
            if (!passesCategoryFilter(tx, state.category)) continue;
            if (!passesSearchFilter(tx, state.search)) continue;
            count++;
        }
        return count;
    }

    /**
     * Retourne la liste des catégories distinctes présentes dans les transactions.
     * Triées alphabétiquement.
     */
    public static List<String> availableCategories(List<TransactionsModels.Transaction> all) {
        List<String> cats = new ArrayList<String>();
        if (all == null) return cats;
        for (TransactionsModels.Transaction tx : all) {
            if (tx.category == null || tx.category.trim().isEmpty()) continue;
            boolean found = false;
            for (String c : cats) {
                if (c.equalsIgnoreCase(tx.category)) { found = true; break; }
            }
            if (!found) cats.add(tx.category.trim());
        }
        java.util.Collections.sort(cats, String.CASE_INSENSITIVE_ORDER);
        return cats;
    }

    // ── Tri ───────────────────────────────────────────────────────

    public static void sortByDateDesc(List<TransactionsModels.Transaction> list) {
        Collections.sort(list, (a, b) -> {
            int cmp = Long.compare(b.dateMs, a.dateMs);
            if (cmp != 0) return cmp;
            return Long.compare(b.addedMs, a.addedMs);
        });
    }

    public static void sortByAddedDesc(List<TransactionsModels.Transaction> list) {
        Collections.sort(list, (a, b) -> Long.compare(b.addedMs, a.addedMs));
    }

    // ── Titre de liste ────────────────────────────────────────────

    public static String listTitle(String period) {
        if ("all".equals(period)) return "Toutes les opérations";

        if ("last".equals(period)) {
            Calendar last = Calendar.getInstance();
            last.add(Calendar.MONTH, -1);
            String[] months = { "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
                                "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre" };
            return "Opérations · " + months[last.get(Calendar.MONTH)] + " " + last.get(Calendar.YEAR);
        }

        if (period != null && period.startsWith("month:")) {
            return "Opérations · " + monthLabel(period);
        }

        Calendar now = Calendar.getInstance();
        String[] months = { "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
                            "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre" };
        return "Opérations · " + months[now.get(Calendar.MONTH)] + " " + now.get(Calendar.YEAR);
    }
}
