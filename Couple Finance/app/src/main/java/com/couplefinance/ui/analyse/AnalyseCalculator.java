package com.couplefinance.ui.analyse;

import com.couplefinance.data.CycleManager;
import com.couplefinance.data.FinancialInsightManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Calcule toutes les métriques de l'écran Analyse à partir d'une liste brute de transactions.
 * Format attendu : [0]=label, [1]=amount, [2]=type, [3]=category, [4]=dateMs,
 *                  [5]=isShareSplit, [6]=isReimbursement, [7]=userId
 */
public class AnalyseCalculator {

    public static final class MonthData {
        public final String label;
        public final int monthKey;
        public double income;
        public double expenses;

        MonthData(String label, int monthKey) {
            this.label = label;
            this.monthKey = monthKey;
        }
    }

    public static final class Forecast {
        public final double projectedExpenses;
        public final double dailyRate;
        public final boolean isOverspend;
        public final String message;
        public final double progressPct;

        Forecast(double projected, double dailyRate, boolean isOverspend, String message, double progressPct) {
            this.projectedExpenses = projected;
            this.dailyRate = dailyRate;
            this.isOverspend = isOverspend;
            this.message = message;
            this.progressPct = progressPct;
        }
    }

    private static final class Tx {
        String label, type, category;
        double amount;
        long dateMs;
        int monthKey;
        boolean shareSplit, reimbursement;
        String merchant;
    }

    private final List<Tx> txs;
    private final int currentMonthKey;
    private final long cycleStart;
    private final long cycleEnd;
    private final long now;

    public AnalyseCalculator(List<String[]> raw) {
        txs = normalize(raw);
        Calendar cal = Calendar.getInstance();
        currentMonthKey = cal.get(Calendar.YEAR) * 100 + (cal.get(Calendar.MONTH) + 1);
        cycleStart = CycleManager.getInstance().getCurrentCycleStartMillis();
        cycleEnd = CycleManager.getInstance().getCurrentCycleEndMillis();
        now = System.currentTimeMillis();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Score de santé (0-100)
    // ─────────────────────────────────────────────────────────────────────────

    public int getHealthScore() {
        double income = getCycleIncome();
        double expenses = getCycleExpenses();
        if (income <= 0 && expenses <= 0) return 50;

        int score = 100;

        // Ratio dépenses/revenus
        if (income > 0) {
            double ratio = expenses / income;
            if (ratio > 1.0) score -= 35;
            else if (ratio > 0.9) score -= 25;
            else if (ratio > 0.75) score -= 15;
            else if (ratio > 0.6) score -= 5;
        } else if (expenses > 0) {
            score -= 30;
        }

        // Épargne
        double savings = income - expenses;
        if (savings < 0) score -= 15;
        else if (savings < income * 0.1) score -= 8;

        // Forecast overspend warning
        Forecast f = getForecast();
        if (f.isOverspend) score -= 10;

        // Anomalies
        List<FinancialInsightManager.Insight> insights = FinancialInsightManager.analyze(toStringArray());
        for (FinancialInsightManager.Insight i : insights) {
            if (i.severity == FinancialInsightManager.SEVERITY_RISK) score -= 8;
            else if (i.severity == FinancialInsightManager.SEVERITY_WARNING) score -= 4;
        }

        return Math.max(0, Math.min(100, score));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cycle summary
    // ─────────────────────────────────────────────────────────────────────────

    public double getCycleIncome() {
        double total = 0;
        for (Tx tx : txs) {
            if (!isInCycle(tx)) continue;
            if ("income".equalsIgnoreCase(tx.type) && !tx.reimbursement) total += tx.amount;
        }
        return total;
    }

    public double getCycleExpenses() {
        double total = 0;
        for (Tx tx : txs) {
            if (!isInCycle(tx)) continue;
            if (isUsableExpense(tx)) total += tx.amount;
        }
        return total;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Évolution 6 mois
    // ─────────────────────────────────────────────────────────────────────────

    public List<MonthData> getLast6Months() {
        // Build list of last 6 month keys
        List<Integer> keys = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        Calendar c = Calendar.getInstance();
        String[] monthNames = { "jan", "fév", "mar", "avr", "mai", "jun",
                                 "jul", "aoû", "sep", "oct", "nov", "déc" };
        for (int i = 5; i >= 0; i--) {
            Calendar ref = Calendar.getInstance();
            ref.add(Calendar.MONTH, -i);
            int key = ref.get(Calendar.YEAR) * 100 + (ref.get(Calendar.MONTH) + 1);
            keys.add(key);
            labels.add(monthNames[ref.get(Calendar.MONTH)]);
        }

        Map<Integer, MonthData> map = new LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            map.put(keys.get(i), new MonthData(labels.get(i), keys.get(i)));
        }

        for (Tx tx : txs) {
            if (!map.containsKey(tx.monthKey)) continue;
            MonthData md = map.get(tx.monthKey);
            if ("income".equalsIgnoreCase(tx.type) && !tx.reimbursement) md.income += tx.amount;
            else if (isUsableExpense(tx)) md.expenses += tx.amount;
        }

        // Only return months that have data
        List<MonthData> result = new ArrayList<>();
        for (MonthData md : map.values()) {
            if (md.income > 0 || md.expenses > 0) result.add(md);
        }
        // Need at least 2 points for a chart
        return result.size() >= 2 ? result : new ArrayList<>();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Catégories du cycle
    // ─────────────────────────────────────────────────────────────────────────

    public Map<String, Double> getCycleExpensesByCategory() {
        Map<String, Double> map = new HashMap<>();
        for (Tx tx : txs) {
            if (!isInCycle(tx) || !isUsableExpense(tx)) continue;
            String cat = (tx.category == null || tx.category.trim().isEmpty()) ? "Autres" : tx.category.trim();
            map.put(cat, getD(map, cat) + tx.amount);
        }
        return map;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Prévision fin de cycle
    // ─────────────────────────────────────────────────────────────────────────

    public Forecast getForecast() {
        double cycleExpenses = getCycleExpenses();
        long cycleLen = cycleEnd - cycleStart;
        long elapsed = Math.max(1, now - cycleStart);
        if (elapsed > cycleLen) elapsed = cycleLen;

        double daysFull = cycleLen / 86400000.0;
        double daysElapsed = elapsed / 86400000.0;
        if (daysElapsed < 1) daysElapsed = 1;

        double dailyRate = cycleExpenses / daysElapsed;
        double projected = dailyRate * daysFull;

        double income = getCycleIncome();
        double progressPct = income > 0 ? (cycleExpenses / income) * 100.0 : 0;

        boolean overspend = income > 0 && projected > income;
        String msg;
        if (income <= 0) {
            msg = String.format(Locale.FRENCH, "%.0f j écoulés sur %.0f j · %.2f €/j", daysElapsed, daysFull, dailyRate);
        } else if (overspend) {
            double over = projected - income;
            msg = String.format(Locale.FRENCH, "Dépassement prévu de %.0f € si ce rythme continue", over);
        } else {
            double margin = income - projected;
            msg = String.format(Locale.FRENCH, "Marge prévisionnelle : +%.0f €", margin);
        }

        return new Forecast(projected, dailyRate, overspend, msg, progressPct);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Top commerçants
    // ─────────────────────────────────────────────────────────────────────────

    public List<Map.Entry<String, Double>> getTopMerchants(int limit) {
        Map<String, Double> map = new HashMap<>();
        for (Tx tx : txs) {
            if (!isInCycle(tx) || !isUsableExpense(tx)) continue;
            String m = tx.merchant != null && !tx.merchant.isEmpty() ? tx.merchant : "Autre";
            map.put(m, getD(map, m) + tx.amount);
        }
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(map.entrySet());
        Collections.sort(sorted, (a, b) -> Double.compare(b.getValue(), a.getValue()));
        return sorted.size() > limit ? sorted.subList(0, limit) : sorted;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Statistiques avancées
    // ─────────────────────────────────────────────────────────────────────────

    /** Moyenne journalière de dépenses sur le cycle courant. */
    public double getDailyAverageExpenses() {
        double exp = getCycleExpenses();
        long elapsed = Math.max(1, now - cycleStart);
        double daysElapsed = elapsed / 86400000.0;
        if (daysElapsed < 1) daysElapsed = 1;
        return exp / daysElapsed;
    }

    /** La transaction la plus élevée du cycle (dépense). Null si aucune. */
    public Tx getLargestExpense() {
        Tx max = null;
        for (Tx tx : txs) {
            if (!isInCycle(tx) || !isUsableExpense(tx)) continue;
            if (max == null || tx.amount > max.amount) max = tx;
        }
        return max;
    }

    /** Label de la plus grosse transaction (ou "" si aucune). */
    public String getLargestExpenseLabel() {
        Tx t = getLargestExpense();
        return t != null ? (t.merchant != null && !t.merchant.isEmpty() ? t.merchant : t.label) : "";
    }

    /** Montant de la plus grosse transaction (0 si aucune). */
    public double getLargestExpenseAmount() {
        Tx t = getLargestExpense();
        return t != null ? t.amount : 0;
    }

    /** Nombre de transactions de dépense dans le cycle. */
    public int getExpenseCount() {
        int count = 0;
        for (Tx tx : txs) {
            if (isInCycle(tx) && isUsableExpense(tx)) count++;
        }
        return count;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dépenses par jour de semaine (0=Lun, 6=Dim)
    // ─────────────────────────────────────────────────────────────────────────

    /** Retourne un tableau [7] : total dépenses (non-income) par jour de semaine du cycle courant. */
    public double[] getSpendingByDayOfWeek() {
        double[] byDay = new double[7];
        Calendar cal = Calendar.getInstance();
        for (Tx tx : txs) {
            if (!isInCycle(tx) || !isUsableExpense(tx)) continue;
            cal.setTimeInMillis(tx.dateMs);
            int dow = cal.get(Calendar.DAY_OF_WEEK); // 1=Sun…7=Sat
            int idx = (dow == Calendar.SUNDAY) ? 6 : dow - 2; // 0=Lun…6=Dim
            if (idx < 0) idx = 0;
            byDay[idx] += tx.amount;
        }
        return byDay;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isInCycle(Tx tx) {
        return tx.dateMs >= cycleStart && tx.dateMs <= cycleEnd;
    }

    private boolean isUsableExpense(Tx tx) {
        if (tx.shareSplit || tx.reimbursement) return false;
        if ("income".equalsIgnoreCase(tx.type)) return false;
        if (tx.amount <= 0) return false;
        String cat = lower(tx.category);
        if (cat.contains("virement") || cat.contains("epargne") || cat.contains("épargne")) return false;
        String label = lower(tx.label);
        if (label.contains("rééquilibrage") || label.contains("reequilibrage")) return false;
        return true;
    }

    private List<String[]> toStringArray() {
        List<String[]> list = new ArrayList<>();
        for (Tx tx : txs) {
            list.add(new String[]{ tx.label, String.valueOf(tx.amount), tx.type, tx.category,
                    String.valueOf(tx.dateMs),
                    tx.shareSplit ? "true" : "false",
                    tx.reimbursement ? "true" : "false" });
        }
        return list;
    }

    private List<Tx> normalize(List<String[]> raw) {
        List<Tx> list = new ArrayList<>();
        if (raw == null) return list;
        for (String[] arr : raw) {
            if (arr == null || arr.length < 5) continue;
            Tx tx = new Tx();
            tx.label = safe(arr[0]);
            tx.amount = parseDouble(arr[1]);
            tx.type = safe(arr[2]);
            tx.category = safe(arr[3]);
            tx.dateMs = parseLong(arr[4]);
            tx.shareSplit = arr.length > 5 && "true".equalsIgnoreCase(safe(arr[5]));
            tx.reimbursement = arr.length > 6 && "true".equalsIgnoreCase(safe(arr[6]));
            if (tx.dateMs <= 0 || tx.amount < 0) continue;
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(tx.dateMs);
            tx.monthKey = c.get(Calendar.YEAR) * 100 + (c.get(Calendar.MONTH) + 1);
            tx.merchant = FinancialInsightManager.normalizeMerchant(tx.label);
            list.add(tx);
        }
        return list;
    }

    private double getD(Map<String, Double> map, String key) {
        Double v = map.get(key);
        return v == null ? 0 : v;
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }
    private String lower(String s) { return safe(s).toLowerCase(Locale.ROOT); }

    private double parseDouble(String s) {
        try { return Double.parseDouble(safe(s).replace(',', '.')); } catch (Exception e) { return 0; }
    }

    private long parseLong(String s) {
        try { return Long.parseLong(safe(s)); } catch (Exception e) { return 0; }
    }
}
