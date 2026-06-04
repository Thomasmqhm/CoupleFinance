package com.couplefinance.data;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Moteur d'analyse financière légère.
 *
 * Objectif : produire des insights exploitables pour le dashboard sans dépendance
 * externe, sans appel réseau et sans modifier les écritures Firestore existantes.
 *
 * Format transaction attendu côté HomeView :
 * [0]=label, [1]=amount, [2]=type, [3]=category, [4]=dateMs,
 * [5]=isShareSplit, [6]=isReimbursement, [7]=userId.
 */
public final class FinancialInsightManager {

    public static final int SEVERITY_INFO = 0;
    public static final int SEVERITY_WARNING = 1;
    public static final int SEVERITY_RISK = 2;

    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final int MAX_INSIGHTS = 4;

    private FinancialInsightManager() {
    }

    public static final class Insight {
        public final int severity;
        public final String type;
        public final String title;
        public final String subtitle;
        public final double impactAmount;

        public Insight(int severity, String type, String title, String subtitle, double impactAmount) {
            this.severity = severity;
            this.type = safe(type);
            this.title = safe(title);
            this.subtitle = safe(subtitle);
            this.impactAmount = impactAmount;
        }
    }

    private static final class Tx {
        String label;
        String merchant;
        String category;
        String type;
        double amount;
        long dateMs;
        int monthKey;
        int dayOfMonth;
        boolean shareSplit;
        boolean reimbursement;
    }

    private static final class Stats {
        double current;
        double historyTotal;
        int historyMonths;
        int countCurrent;
        int countHistory;
        long latestDate;
        String latestLabel;
        String category;
    }

    public static List<Insight> analyze(List<String[]> transactions) {
        ArrayList<Tx> txs = normalize(transactions);
        ArrayList<Insight> insights = new ArrayList<>();

        if (txs.isEmpty()) return insights;

        Calendar now = Calendar.getInstance();
        int currentMonthKey = monthKey(now);

        detectMerchantAmountAnomalies(txs, currentMonthKey, insights);
        detectCategoryAnomalies(txs, currentMonthKey, insights);
        detectBankFees(txs, currentMonthKey, insights);
        detectDuplicateSubscriptions(txs, currentMonthKey, insights);

        Collections.sort(insights, new Comparator<Insight>() {
            @Override
            public int compare(Insight a, Insight b) {
                if (a.severity != b.severity) return Integer.compare(b.severity, a.severity);
                return Double.compare(Math.abs(b.impactAmount), Math.abs(a.impactAmount));
            }
        });

        if (insights.size() > MAX_INSIGHTS) {
            return new ArrayList<>(insights.subList(0, MAX_INSIGHTS));
        }
        return insights;
    }

    private static void detectMerchantAmountAnomalies(List<Tx> txs, int currentMonthKey, List<Insight> out) {
        Map<String, Stats> statsByMerchant = new LinkedHashMap<>();

        for (Tx tx : txs) {
            if (!isUsableExpense(tx)) continue;
            if (tx.merchant.length() < 3) continue;

            Stats stats = statsByMerchant.get(tx.merchant);
            if (stats == null) {
                stats = new Stats();
                stats.category = tx.category;
                statsByMerchant.put(tx.merchant, stats);
            }

            if (tx.monthKey == currentMonthKey) {
                stats.current += tx.amount;
                stats.countCurrent++;
                if (tx.dateMs > stats.latestDate) {
                    stats.latestDate = tx.dateMs;
                    stats.latestLabel = tx.label;
                    stats.category = tx.category;
                }
            } else {
                stats.historyTotal += tx.amount;
                stats.countHistory++;
            }
        }

        for (Map.Entry<String, Stats> entry : statsByMerchant.entrySet()) {
            Stats stats = entry.getValue();
            if (stats.current <= 0 || stats.countHistory < 2) continue;

            double avg = stats.historyTotal / Math.max(1, stats.countHistory);
            double diff = stats.current - avg;
            if (avg < 8 || diff < 15) continue;

            double ratio = stats.current / avg;
            if (ratio >= 1.65) {
                int pct = (int) Math.round((ratio - 1.0) * 100.0);
                int severity = ratio >= 2.15 || diff >= 80 ? SEVERITY_RISK : SEVERITY_WARNING;
                String merchant = prettyMerchant(entry.getKey());
                out.add(new Insight(severity, "merchant_spike", merchant + " en hausse",
                        money(stats.current) + " ce mois · environ +" + pct + "% vs habituel", diff));
            }
        }
    }

    private static void detectCategoryAnomalies(List<Tx> txs, int currentMonthKey, List<Insight> out) {
        Map<String, Double> currentByCat = new HashMap<>();
        Map<String, Double> historyByCat = new HashMap<>();
        Map<String, Integer> historyCountByCat = new HashMap<>();

        for (Tx tx : txs) {
            if (!isUsableExpense(tx)) continue;
            String cat = tx.category == null || tx.category.trim().isEmpty() ? "Autres" : tx.category.trim();
            if (tx.monthKey == currentMonthKey) {
                currentByCat.put(cat, get(currentByCat, cat) + tx.amount);
            } else {
                historyByCat.put(cat, get(historyByCat, cat) + tx.amount);
                historyCountByCat.put(cat, getInt(historyCountByCat, cat) + 1);
            }
        }

        for (Map.Entry<String, Double> entry : currentByCat.entrySet()) {
            String cat = entry.getKey();
            double current = entry.getValue() == null ? 0 : entry.getValue();
            double history = get(historyByCat, cat);
            int count = getInt(historyCountByCat, cat);
            if (current <= 0 || count < 3) continue;

            double avg = history / Math.max(1, count);
            double diff = current - avg;
            if (avg < 20 || diff < 50) continue;

            double ratio = current / avg;
            if (ratio >= 1.55) {
                int pct = (int) Math.round((ratio - 1.0) * 100.0);
                out.add(new Insight(SEVERITY_WARNING, "category_spike", cat + " plus élevé que d’habitude",
                        money(current) + " ce mois · environ +" + pct + "%", diff));
            }
        }
    }

    private static void detectBankFees(List<Tx> txs, int currentMonthKey, List<Insight> out) {
        double currentFees = 0;
        int feeCount = 0;
        String latest = "";
        long latestDate = 0;

        for (Tx tx : txs) {
            if (!isUsableExpense(tx)) continue;
            if (tx.monthKey != currentMonthKey) continue;
            if (!isBankFee(tx)) continue;

            currentFees += tx.amount;
            feeCount++;
            if (tx.dateMs > latestDate) {
                latestDate = tx.dateMs;
                latest = tx.label;
            }
        }

        if (currentFees >= 5.0) {
            int severity = currentFees >= 25.0 ? SEVERITY_RISK : SEVERITY_WARNING;
            String detail = feeCount + " ligne" + (feeCount > 1 ? "s" : "") + " détectée" + (feeCount > 1 ? "s" : "");
            if (latest != null && latest.length() > 0) detail += " · " + compactLabel(latest);
            out.add(new Insight(severity, "bank_fees", "Frais bancaires détectés", money(currentFees) + " ce mois · " + detail,
                    currentFees));
        }
    }

    private static void detectDuplicateSubscriptions(List<Tx> txs, int currentMonthKey, List<Insight> out) {
        Map<String, List<Tx>> byMerchant = new HashMap<>();

        for (Tx tx : txs) {
            if (!isUsableExpense(tx)) continue;
            if (tx.monthKey != currentMonthKey) continue;
            if (!looksLikeSubscription(tx)) continue;

            List<Tx> list = byMerchant.get(tx.merchant);
            if (list == null) {
                list = new ArrayList<>();
                byMerchant.put(tx.merchant, list);
            }
            list.add(tx);
        }

        for (Map.Entry<String, List<Tx>> entry : byMerchant.entrySet()) {
            List<Tx> list = entry.getValue();
            if (list == null || list.size() < 2) continue;

            double total = 0;
            long min = Long.MAX_VALUE;
            long max = 0;
            for (Tx tx : list) {
                total += tx.amount;
                min = Math.min(min, tx.dateMs);
                max = Math.max(max, tx.dateMs);
            }

            if (max - min <= 12L * DAY_MS) {
                out.add(new Insight(SEVERITY_WARNING, "duplicate_subscription",
                        "Abonnement peut-être doublé", prettyMerchant(entry.getKey()) + " apparaît " + list.size()
                                + " fois ce mois · total " + money(total), total));
            }
        }
    }

    private static ArrayList<Tx> normalize(List<String[]> raw) {
        ArrayList<Tx> txs = new ArrayList<>();
        if (raw == null) return txs;

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

            if (tx.dateMs <= 0 || tx.amount <= 0) continue;

            Calendar c = Calendar.getInstance();
            c.setTime(new Date(tx.dateMs));
            tx.monthKey = monthKey(c);
            tx.dayOfMonth = c.get(Calendar.DAY_OF_MONTH);
            tx.merchant = normalizeMerchant(tx.label);

            txs.add(tx);
        }
        return txs;
    }

    private static boolean isUsableExpense(Tx tx) {
        if (tx == null) return false;
        if (tx.shareSplit || tx.reimbursement) return false;
        if ("income".equalsIgnoreCase(tx.type)) return false;
        if (tx.amount <= 0) return false;

        String label = lower(tx.label);
        String category = lower(tx.category);
        if (label.contains("rééquilibrage") || label.contains("reequilibrage")) return false;
        if (category.contains("virement") || category.contains("epargne") || category.contains("épargne")) return false;
        return true;
    }

    private static boolean isBankFee(Tx tx) {
        String l = lower(tx.label);
        String c = lower(tx.category);
        return c.contains("frais") || c.contains("banque") || l.contains("commission") || l.contains("intervention")
                || l.contains("frais") || l.contains("cotisation eurocompte") || l.contains("impaye")
                || l.contains("impayé") || l.contains("agios") || l.contains("incident");
    }

    private static boolean looksLikeSubscription(Tx tx) {
        String l = lower(tx.label + " " + tx.category);
        if (l.contains("abonnement") || l.contains("prime") || l.contains("netflix") || l.contains("spotify")
                || l.contains("disney") || l.contains("google play") || l.contains("apple") || l.contains("canal")
                || l.contains("deezer") || l.contains("boardgamearena") || l.contains("amazon")) {
            return true;
        }
        return tx.amount > 0 && tx.amount <= 80 && (l.contains("prlv") || l.contains("sepa"));
    }

    public static String normalizeMerchant(String label) {
        String s = safe(label).toUpperCase(Locale.ROOT);
        s = removeAccents(s);

        int sep = s.indexOf(" · ");
        if (sep >= 0 && sep + 3 < s.length()) s = s.substring(sep + 3);

        s = s.replace("PAIEMENT PAR CARTE", " ");
        s = s.replace("CARTE", " ");
        s = s.replace("PRLV SEPA", " ");
        s = s.replace("VIR DE", " ");
        s = s.replace("VIR VERS", " ");
        s = s.replace("F COTISATION", "COTISATION");

        s = s.replaceAll("\\b\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?\\b", " ");
        s = s.replaceAll("\\bPAYLI[0-9A-Z/.-]*\\b", " ");
        s = s.replaceAll("\\b[A-Z]{2,}[0-9]{3,}[A-Z0-9/.-]*\\b", " ");
        s = s.replaceAll("\\b[0-9]{4,}[A-Z0-9/.-]*\\b", " ");
        s = s.replaceAll("[^A-Z0-9 ]", " ");
        s = s.replaceAll("\\s+", " ").trim();

        String known = knownMerchant(s);
        if (!known.isEmpty()) return known;

        String[] parts = s.split(" ");
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p.length() <= 1) continue;
            if (isCityOrNoise(p)) continue;
            if (b.length() > 0) b.append(' ');
            b.append(p);
            if (b.length() >= 18) break;
        }
        String r = b.toString().trim();
        return r.isEmpty() ? s : r;
    }

    private static String knownMerchant(String s) {
        if (s.contains("EDF")) return "EDF";
        if (s.contains("SAUR")) return "SAUR";
        if (s.contains("DIAC")) return "DIAC";
        if (s.contains("BNP PARIBAS PERSONAL FINANCE")) return "BNP PARIBAS PERSONAL FINANCE";
        if (s.contains("SURAVENIR")) return "SURAVENIR";
        if (s.contains("LIDL")) return "LIDL";
        if (s.contains("SUPER U")) return "SUPER U";
        if (s.contains("LECLERC")) return "LECLERC";
        if (s.contains("RELAY")) return "RELAY";
        if (s.contains("PADDINGTON")) return "PADDINGTON";
        if (s.contains("BOARDGAMEARENA")) return "BOARDGAMEARENA";
        if (s.contains("GOOGLE PLAY")) return "GOOGLE PLAY";
        if (s.contains("AMAZON PRIME")) return "AMAZON PRIME";
        if (s.contains("AMAZON")) return "AMAZON";
        if (s.contains("BOUYGUES")) return "BOUYGUES TELECOM";
        if (s.contains("NETFLIX")) return "NETFLIX";
        if (s.contains("SPOTIFY")) return "SPOTIFY";
        return "";
    }

    private static boolean isCityOrNoise(String p) {
        return p.equals("LOUDEAC") || p.equals("LOUD") || p.equals("LAMBALLE") || p.equals("MERDRIGNAC")
                || p.equals("SAINT") || p.equals("VRAN") || p.equals("GUYANCOURT") || p.equals("DUBLIN")
                || p.equals("CLIENTS") || p.equals("PARTICULIERS") || p.equals("NUMERO") || p.equals("COMPTE")
                || p.equals("FACTURE") || p.equals("FR") || p.equals("SEPA") || p.equals("PRLV") || p.equals("CARTE");
    }

    private static int monthKey(Calendar c) {
        return c.get(Calendar.YEAR) * 100 + (c.get(Calendar.MONTH) + 1);
    }

    private static double get(Map<String, Double> map, String key) {
        Double v = map.get(key);
        return v == null ? 0 : v;
    }

    private static int getInt(Map<String, Integer> map, String key) {
        Integer v = map.get(key);
        return v == null ? 0 : v;
    }

    private static String compactLabel(String label) {
        String merchant = prettyMerchant(normalizeMerchant(label));
        if (!merchant.isEmpty()) return merchant;
        String s = safe(label).trim();
        return s.length() > 32 ? s.substring(0, 32) + "…" : s;
    }

    private static String prettyMerchant(String merchant) {
        String s = safe(merchant).trim();
        if (s.length() == 0) return "Opération";
        if (s.equals(s.toUpperCase(Locale.ROOT))) {
            String[] words = s.split(" ");
            StringBuilder b = new StringBuilder();
            for (String w : words) {
                if (w.length() == 0) continue;
                if (b.length() > 0) b.append(' ');
                if (w.length() <= 3) b.append(w);
                else b.append(w.substring(0, 1)).append(w.substring(1).toLowerCase(Locale.ROOT));
            }
            return b.toString();
        }
        return s;
    }

    private static String money(double value) {
        DecimalFormat df = new DecimalFormat("#,##0.00 €");
        return df.format(value).replace(',', ' ').replace('.', ',');
    }

    private static String lower(String s) {
        return safe(s).toLowerCase(Locale.ROOT);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(safe(s).replace(',', '.'));
        } catch (Exception e) {
            return 0;
        }
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(safe(s));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String removeAccents(String input) {
        String s = safe(input);
        s = s.replace('É', 'E').replace('È', 'E').replace('Ê', 'E').replace('Ë', 'E');
        s = s.replace('À', 'A').replace('Â', 'A').replace('Ä', 'A');
        s = s.replace('Î', 'I').replace('Ï', 'I');
        s = s.replace('Ô', 'O').replace('Ö', 'O');
        s = s.replace('Ù', 'U').replace('Û', 'U').replace('Ü', 'U');
        s = s.replace('Ç', 'C');
        return s;
    }
}
