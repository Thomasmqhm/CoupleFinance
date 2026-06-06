package com.couplefinance.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Journal d'activité local — stocke les 30 derniers événements du foyer
 * dans SharedPreferences sous forme de tableau JSON.
 *
 * Événements enregistrés :
 *  - Transaction ajoutée (par qui, libellé, montant)
 *  - Budget catégorie dépassé
 *  - Objectif d'épargne atteint
 */
public class ActivityLogger {

    private static final String PREFS_NAME = "activity_log";
    private static final String KEY_EVENTS  = "events";
    private static final int    MAX_EVENTS  = 30;

    public static class Event {
        public final String icon;
        public final String title;
        public final String subtitle;
        public final long   timestampMs;
        public final String type; // "transaction", "budget", "savings"

        public Event(String icon, String title, String subtitle, long timestampMs, String type) {
            this.icon        = icon;
            this.title       = title;
            this.subtitle    = subtitle;
            this.timestampMs = timestampMs;
            this.type        = type;
        }

        /** Heure relative courte — "il y a 2h", "hier", etc. */
        public String relativeTime() {
            long diff = System.currentTimeMillis() - timestampMs;
            if (diff < 60_000)            return "À l'instant";
            if (diff < 3_600_000)         return "Il y a " + (diff / 60_000) + " min";
            if (diff < 86_400_000)        return "Il y a " + (diff / 3_600_000) + "h";
            if (diff < 2 * 86_400_000L)   return "Hier";
            SimpleDateFormat sdf = new SimpleDateFormat("d MMM", Locale.FRENCH);
            return sdf.format(new Date(timestampMs));
        }
    }

    private ActivityLogger() {}

    // ── Écriture ──────────────────────────────────────────────────

    public static void logTransaction(Context ctx, String person, String label, double amount, boolean isIncome) {
        String title    = (person != null && !person.isEmpty() ? person : "Foyer") + " · " +
                          (isIncome ? "Revenu" : "Dépense");
        String subtitle = label + " · " + String.format(Locale.FRANCE, "%.2f €", amount);
        append(ctx, new Event(isIncome ? "+" : "−", title, subtitle,
                System.currentTimeMillis(), "transaction"));
    }

    public static void logBudgetExceeded(Context ctx, String category, double spent, double budget) {
        String title    = "Budget dépassé : " + category;
        String subtitle = String.format(Locale.FRANCE, "%.0f € / %.0f €", spent, budget);
        append(ctx, new Event("!", title, subtitle, System.currentTimeMillis(), "budget"));
    }

    public static void logSavingsGoal(Context ctx, String goalName) {
        String title    = "Objectif atteint !";
        String subtitle = goalName != null ? goalName : "Objectif d'épargne";
        append(ctx, new Event("★", title, subtitle, System.currentTimeMillis(), "savings"));
    }

    // ── Lecture ───────────────────────────────────────────────────

    public static List<Event> getRecentEvents(Context ctx) {
        List<Event> result = new ArrayList<>();
        try {
            String json = prefs(ctx).getString(KEY_EVENTS, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                result.add(new Event(
                        o.optString("icon", "i"),
                        o.optString("title", ""),
                        o.optString("subtitle", ""),
                        o.optLong("ts", 0),
                        o.optString("type", "")
                ));
            }
        } catch (Exception ignored) {}
        return result;
    }

    public static void clearAll(Context ctx) {
        prefs(ctx).edit().remove(KEY_EVENTS).apply();
    }

    // ── Interne ───────────────────────────────────────────────────

    private static void append(Context ctx, Event event) {
        try {
            SharedPreferences sp = prefs(ctx);
            JSONArray arr = new JSONArray(sp.getString(KEY_EVENTS, "[]"));

            JSONObject o = new JSONObject();
            o.put("icon",     event.icon);
            o.put("title",    event.title);
            o.put("subtitle", event.subtitle);
            o.put("ts",       event.timestampMs);
            o.put("type",     event.type);

            // Insérer en tête, tronquer à MAX_EVENTS
            JSONArray newArr = new JSONArray();
            newArr.put(o);
            for (int i = 0; i < Math.min(arr.length(), MAX_EVENTS - 1); i++) {
                newArr.put(arr.get(i));
            }

            sp.edit().putString(KEY_EVENTS, newArr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
