package com.couplefinance.ui.home;

import java.util.ArrayList;
import java.util.List;

/**
 * HomeParser — Parsing JSON Firestore → tableaux String[] pour HomeView.
 *
 * Extrait de HomeView.java pour séparer la responsabilité de parsing
 * des responsabilités d'affichage et de calcul.
 *
 * Aucune dépendance Android. 100% testable unitairement.
 */
public final class HomeParser {

    private HomeParser() {}

    // ─────────────────────────────────────────────────────────────
    // Transactions
    // ─────────────────────────────────────────────────────────────

    /**
     * Parse la réponse Firestore de la collection /transactions.
     *
     * Format du String[] retourné :
     *   [0] label
     *   [1] amount
     *   [2] type
     *   [3] category
     *   [4] date (timestamp ms)
     *   [5] isShareSplit ("true"/"false")
     *   [6] isReimbursement ("true"/"false")
     *   [7] userId
     *   [8] compte ("joint" ou "")
     */
    public static List<String[]> parseTransactions(String json) {
        List<String[]> list = new ArrayList<>();
        if (json == null || json.isEmpty()) return list;

        String[] parts = json.split("\"fields\":");

        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];

            String label          = p.contains("\"label\"")          ? extractStr(p.substring(p.indexOf("\"label\"")),          "stringValue") : "";
            String amount         = p.contains("\"amount\"")          ? extractNum(p.substring(p.indexOf("\"amount\"")),         "doubleValue")  : "0";
            String type           = p.contains("\"type\"")            ? extractStr(p.substring(p.indexOf("\"type\"")),           "stringValue") : "";
            String category       = p.contains("\"category\"")        ? extractStr(p.substring(p.indexOf("\"category\"")),       "stringValue") : "";
            String date           = p.contains("\"date\"")            ? extractDateValue(p.substring(p.indexOf("\"date\"")))                   : "0";
            String isShareSplit   = "false";
            String isReimbursement= "false";
            String userId         = p.contains("\"userId\"")          ? extractStr(p.substring(p.indexOf("\"userId\"")),         "stringValue") : "";
            String compte         = p.contains("\"compte\"")          ? extractStr(p.substring(p.indexOf("\"compte\"")),         "stringValue") : "";

            if (p.contains("\"isShareSplit\"")) {
                int s = p.indexOf("\"isShareSplit\"");
                String sub = p.substring(s, Math.min(s + 60, p.length()));
                if (sub.contains("booleanValue\":true") || sub.contains("booleanValue\": true"))
                    isShareSplit = "true";
            }

            if (p.contains("\"isReimbursement\"")) {
                int s = p.indexOf("\"isReimbursement\"");
                String sub = p.substring(s, Math.min(s + 60, p.length()));
                if (sub.contains("booleanValue\":true") || sub.contains("booleanValue\": true"))
                    isReimbursement = "true";
            }

            if (!label.isEmpty()) {
                list.add(new String[]{ label, amount, type, category, date,
                        isShareSplit, isReimbursement, userId, compte });
            }
        }

        return list;
    }

    // ─────────────────────────────────────────────────────────────
    // Membres
    // ─────────────────────────────────────────────────────────────

    public static class MemberEntry {
        public final String name;
        public final String userId;

        MemberEntry(String name, String userId) {
            this.name   = name;
            this.userId = userId;
        }
    }

    public static List<MemberEntry> parseMembers(String json) {
        List<MemberEntry> result = new ArrayList<>();
        if (json == null || json.isEmpty()) return result;

        String[] parts = json.split("\"fields\":");

        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];
            if (!p.contains("\"name\"")) continue;

            String name = extractStr(p.substring(p.indexOf("\"name\"")), "stringValue");
            if (name.isEmpty() || name.equals("null") || name.equals("Moi")) continue;

            String uid = "";
            if (p.contains("\"userId\""))
                uid = extractStr(p.substring(p.indexOf("\"userId\"")), "stringValue");

            result.add(new MemberEntry(name, uid));
        }

        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // Balances membres
    // ─────────────────────────────────────────────────────────────

    public static class BalanceEntry {
        public final String userId;
        public final double amount;
        public final long   anchorDate;
        public final String month;

        BalanceEntry(String userId, double amount, long anchorDate, String month) {
            this.userId     = userId;
            this.amount     = amount;
            this.anchorDate = anchorDate;
            this.month      = month;
        }
    }

    public static List<BalanceEntry> parseBalances(String json) {
        List<BalanceEntry> result = new ArrayList<>();
        if (json == null || json.isEmpty()) return result;

        String[] docs = json.split("\"fields\":");

        for (int i = 1; i < docs.length; i++) {
            String p = docs[i];
            if (!p.contains("\"month\"")) continue;

            String month  = extractStr(p.substring(p.indexOf("\"month\"")), "stringValue");
            String uid    = p.contains("\"userId\"")  ? extractStr(p.substring(p.indexOf("\"userId\"")),  "stringValue") : "";
            String val    = p.contains("\"balance\"") ? extractNum(p.substring(p.indexOf("\"balance\"")), "doubleValue")  : "0";

            long anchor = 0;
            if (p.contains("\"anchorDate\"")) {
                try {
                    String anchorPart = p.substring(p.indexOf("\"anchorDate\""));
                    anchor = Long.parseLong(extractDateValue(anchorPart));
                } catch (Exception ignored) {}
            }

            try {
                result.add(new BalanceEntry(uid, Double.parseDouble(val), anchor, month));
            } catch (Exception ignored) {}
        }

        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // Catégories
    // ─────────────────────────────────────────────────────────────

    public static class CategoryEntry {
        public final String name;
        public final String emoji;
        public final double budget;

        CategoryEntry(String name, String emoji, double budget) {
            this.name   = name;
            this.emoji  = emoji;
            this.budget = budget;
        }
    }

    public static List<CategoryEntry> parseCategories(String json) {
        List<CategoryEntry> result = new ArrayList<>();
        if (json == null || json.isEmpty()) return result;

        String[] parts = json.split("\"fields\":");

        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];
            String name  = p.contains("\"name\"")  ? extractStr(p.substring(p.indexOf("\"name\"")),  "stringValue") : "";
            String emoji = p.contains("\"emoji\"") ? extractStr(p.substring(p.indexOf("\"emoji\"")), "stringValue") : "📊";

            double budget = 0;
            if (p.contains("\"budget\"")) {
                try {
                    String bp = p.substring(p.indexOf("\"budget\""));
                    String bv = bp.contains("doubleValue")
                            ? extractNum(bp.substring(bp.indexOf("doubleValue")),   "doubleValue")
                            : extractNum(bp.substring(bp.indexOf("integerValue")), "integerValue");
                    budget = Double.parseDouble(bv);
                } catch (Exception ignored) {}
            }

            if (!name.isEmpty()) result.add(new CategoryEntry(name, emoji.isEmpty() ? "📊" : emoji, budget));
        }

        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers JSON
    // ─────────────────────────────────────────────────────────────

    public static String extractStr(String json, String key) {
        if (json == null || key == null) return "";

        String marker = "\"" + key + "\"";
        int ki = json.indexOf(marker);
        if (ki < 0) return "";

        int si = json.indexOf("\"stringValue\"", ki);
        if (si >= 0) {
            int colon = json.indexOf(":", si);
            int q1    = json.indexOf("\"", colon + 1);
            int q2    = json.indexOf("\"", q1 + 1);
            if (q1 >= 0 && q2 > q1) return json.substring(q1 + 1, q2).trim();
        }

        int ii = json.indexOf("\"integerValue\"", ki);
        if (ii >= 0) return extractNumberAfterColon(json, ii);

        int di = json.indexOf("\"doubleValue\"", ki);
        if (di >= 0) return extractNumberAfterColon(json, di);

        return "";
    }

    public static String extractNum(String json, String key) {
        if (json == null || key == null) return "0";

        String marker = "\"" + key + "\"";
        int ki = json.indexOf(marker);
        if (ki < 0) return "0";

        int di = json.indexOf("\"doubleValue\"",  ki);
        int ii = json.indexOf("\"integerValue\"", ki);

        if (di >= 0 && (ii < 0 || di < ii)) return extractNumberAfterColon(json, di);
        if (ii >= 0) return extractNumberAfterColon(json, ii);

        return "0";
    }

    public static String extractDateValue(String json) {
        if (json == null) return "0";

        for (String key : new String[]{ "integerValue", "doubleValue", "stringValue" }) {
            int k = json.indexOf("\"" + key + "\"");
            if (k < 0) continue;

            int colon = json.indexOf(":", k);
            if (colon < 0) continue;

            int sq = json.indexOf("\"", colon + 1);
            int eq = sq >= 0 ? json.indexOf("\"", sq + 1) : -1;

            if (sq >= 0 && eq > sq) {
                String v = json.substring(sq + 1, eq).trim();
                if (!v.isEmpty()) return v;
            }

            String sub = json.substring(colon + 1).trim();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < sub.length(); i++) {
                char c = sub.charAt(i);
                if ((c >= '0' && c <= '9') || c == '.' || c == '-') sb.append(c);
                else if (sb.length() > 0) break;
            }
            if (sb.length() > 0) return sb.toString();
        }

        return "0";
    }

    private static String extractNumberAfterColon(String json, int startIndex) {
        int colon = json.indexOf(":", startIndex);
        if (colon < 0) return "0";

        int i = colon + 1;
        while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == '"'
                || json.charAt(i) == '\n' || json.charAt(i) == '\r')) i++;

        int start = i;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (!Character.isDigit(c) && c != '.' && c != '-' && c != '+') break;
            i++;
        }

        if (i <= start) return "0";
        return json.substring(start, i).replace("\"", "").trim();
    }
}
