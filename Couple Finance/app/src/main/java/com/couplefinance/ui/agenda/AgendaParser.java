package com.couplefinance.ui.agenda;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  AgendaParser — Parsing JSON Firestore → modèles            ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Aucune dépendance Android. 100% testable unitairement.     ║
 * ║  Appelé par : AgendaRepository uniquement                   ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
public final class AgendaParser {

    private AgendaParser() {}

    // ─────────────────────────────────────────────────────────────
    // Parsing événements (via JSONObject — format Firestore propre)
    // ─────────────────────────────────────────────────────────────

    /**
     * Parse la réponse Firestore de la collection /events.
     * Utilise JSONObject pour un parsing fiable.
     */
    public static List<AgendaModels.AgendaEvent> parseEvents(String json) {
        List<AgendaModels.AgendaEvent> result = new ArrayList<>();
        if (json == null || json.isEmpty()) return result;

        try {
            JSONObject root = new JSONObject(json);
            if (!root.has("documents")) return result;
            JSONArray docs = root.getJSONArray("documents");

            for (int i = 0; i < docs.length(); i++) {
                JSONObject doc    = docs.getJSONObject(i);
                JSONObject fields = doc.getJSONObject("fields");

                String title    = getStr(fields, "title");
                String type     = getStr(fields, "type");
                String amount   = getDbl(fields, "amount");
                String date     = getLng(fields, "date");
                String person   = getStr(fields, "person");
                String note     = getStr(fields, "note");
                String docPath  = doc.getString("name");

                if (title.isEmpty()) continue;

                result.add(new AgendaModels.AgendaEvent(
                    title, type, parseD(amount), parseLong(date),
                    person, note, docPath));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // Parsing transactions (via split — format raw Firestore)
    // ─────────────────────────────────────────────────────────────

    /**
     * Parse la réponse Firestore des transactions.
     * Exclut les isShareSplit = true.
     */
    public static List<AgendaModels.AgendaTransaction> parseTransactions(String json) {
        List<AgendaModels.AgendaTransaction> result = new ArrayList<>();
        if (json == null || json.isEmpty()) return result;

        String[] parts = json.split("\"fields\":");
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];

            String label    = p.contains("\"label\"")    ? extractStr(p.substring(p.indexOf("\"label\"")),    "stringValue") : "";
            String amount   = p.contains("\"amount\"")   ? extractNum(p.substring(p.indexOf("\"amount\"")),   "doubleValue")  : "0";
            String type     = p.contains("\"type\"")     ? extractStr(p.substring(p.indexOf("\"type\"")),     "stringValue") : "";
            String category = p.contains("\"category\"") ? extractStr(p.substring(p.indexOf("\"category\"")), "stringValue") : "";
            String date     = p.contains("\"date\"")     ? extractDateValue(p.substring(p.indexOf("\"date\""))) : "0";

            // Exclure les share splits
            boolean isSplit = p.contains("\"isShareSplit\"")
                && p.substring(p.indexOf("\"isShareSplit\""),
                    Math.min(p.indexOf("\"isShareSplit\"") + 80, p.length()))
                    .contains("true");

            if (label.isEmpty() || isSplit) continue;

            result.add(new AgendaModels.AgendaTransaction(
                label, parseD(amount), type, category, parseLong(date)));
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // Parsing membres du foyer
    // ─────────────────────────────────────────────────────────────

    public static List<String> parseMembers(String json) {
        List<String> names = new ArrayList<>();
        if (json == null || json.isEmpty()) return names;

        int cursor = 0;
        while (true) {
            String m1 = "\"name\": \"projects/", m2 = "\"name\":\"projects/";
            int i1 = json.indexOf(m1, cursor), i2 = json.indexOf(m2, cursor);
            if (i1 < 0 && i2 < 0) break;

            int idx; String marker;
            if      (i1 < 0) { idx = i2; marker = m2; }
            else if (i2 < 0) { idx = i1; marker = m1; }
            else             { idx = Math.min(i1, i2); marker = (i1 < i2) ? m1 : m2; }

            int pathStart = idx + marker.length();
            int pathEnd   = json.indexOf("\"", pathStart);
            if (pathEnd < 0) break;

            String docPath = json.substring(pathStart, pathEnd);
            cursor = pathEnd;
            if (!docPath.contains("/persons/")) continue;

            int fieldsIdx = json.indexOf("\"fields\"", cursor);
            if (fieldsIdx < 0) break;

            String fieldsBlock = json.substring(fieldsIdx);
            int nameIdx = fieldsBlock.indexOf("\"name\"");
            if (nameIdx < 0) continue;

            String name = extractStr(fieldsBlock.substring(nameIdx), "stringValue");
            if (!name.isEmpty() && !name.equals("null") && !names.contains(name))
                names.add(name);
        }
        return names;
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers JSONObject
    // ─────────────────────────────────────────────────────────────

    private static String getStr(JSONObject f, String k) {
        try { return f.getJSONObject(k).getString("stringValue"); }
        catch (Exception e) { return ""; }
    }

    private static String getDbl(JSONObject f, String k) {
        try { return f.getJSONObject(k).getString("doubleValue"); }
        catch (Exception e) { return "0"; }
    }

    private static String getLng(JSONObject f, String k) {
        try { return f.getJSONObject(k).getString("integerValue"); }
        catch (Exception e) { return "0"; }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers extraction JSON raw
    // ─────────────────────────────────────────────────────────────

    /**
     * Extraction robuste d'une date Firestore.
     * Gère integerValue, doubleValue, stringValue.
     */
    static String extractDateValue(String json) {
        if (json == null) return "0";
        for (String key : new String[]{ "integerValue", "doubleValue", "stringValue" }) {
            int k = json.indexOf("\"" + key + "\"");
            if (k < 0) continue;
            int colon = json.indexOf(":", k);
            if (colon < 0) continue;

            int sq = json.indexOf("\"", colon + 1);
            int eq = sq >= 0 ? json.indexOf("\"", sq + 1) : -1;
            if (sq >= 0 && eq > sq) {
                String val = json.substring(sq + 1, eq).trim();
                if (!val.isEmpty()) return val;
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

    static String extractStr(String json, String key) {
        for (String s : new String[]{ "\"" + key + "\": \"", "\"" + key + "\":\"" }) {
            int i = json.indexOf(s);
            if (i >= 0) {
                int st = i + s.length(), e = json.indexOf("\"", st);
                if (e > st) return json.substring(st, e).trim();
            }
        }
        return "";
    }

    static String extractNum(String json, String key) {
        for (String s : new String[]{ "\"" + key + "\": ", "\"" + key + "\":" }) {
            int i = json.indexOf(s);
            if (i >= 0) {
                String rest = json.substring(i + s.length()).trim();
                if (rest.startsWith("\"")) {
                    int e = rest.indexOf("\"", 1);
                    return e > 1 ? rest.substring(1, e) : "0";
                }
                int e = 0;
                while (e < rest.length() && (Character.isDigit(rest.charAt(e))
                        || rest.charAt(e) == '.' || rest.charAt(e) == '-')) e++;
                if (e > 0) return rest.substring(0, e);
            }
        }
        return "0";
    }

    static double parseD(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

    static long parseLong(String s) {
        if (s == null) return 0;
        try {
            String clean = s.trim().replace(".0", "");
            return Long.parseLong(clean);
        } catch (Exception e) {
            try { return (long) Double.parseDouble(s.trim()); }
            catch (Exception e2) { return 0; }
        }
    }
}
