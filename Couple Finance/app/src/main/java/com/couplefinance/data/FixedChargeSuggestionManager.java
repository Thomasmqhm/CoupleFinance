package com.couplefinance.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/**
 * Mémoire locale pour les suggestions de charges fixes détectées après import PDF.
 *
 * Objectif : éviter de reproposer éternellement une dépense que l'utilisateur
 * ne veut pas ajouter aux charges fixes, tout en gardant un système simple,
 * stable sur CodeAssist et sans nouvelle dépendance.
 */
public class FixedChargeSuggestionManager {

    private static final String PREFS_NAME = "couplefinance_fixed_charge_suggestions_v1";
    private static final String KEY_IGNORED = "ignored_";
    private static final String KEY_ACCEPTED = "accepted_";
    private static final String KEY_UPDATED_AT = "updated_at_";

    private static FixedChargeSuggestionManager instance;
    private Context appContext;

    public static FixedChargeSuggestionManager getInstance() {
        if (instance == null) {
            instance = new FixedChargeSuggestionManager();
        }
        return instance;
    }

    public void init(Context context) {
        if (context != null && appContext == null) {
            appContext = context.getApplicationContext();
        }
    }

    public boolean isIgnored(String merchantKey) {
        if (appContext == null) return false;
        String key = normalizeKey(merchantKey);
        if (key.length() == 0) return false;
        return prefs().getBoolean(KEY_IGNORED + key, false);
    }

    public boolean isAccepted(String merchantKey) {
        if (appContext == null) return false;
        String key = normalizeKey(merchantKey);
        if (key.length() == 0) return false;
        return prefs().getBoolean(KEY_ACCEPTED + key, false);
    }

    public void ignore(String merchantKey) {
        if (appContext == null) return;
        String key = normalizeKey(merchantKey);
        if (key.length() == 0) return;
        prefs().edit()
                .putBoolean(KEY_IGNORED + key, true)
                .putBoolean(KEY_ACCEPTED + key, false)
                .putLong(KEY_UPDATED_AT + key, System.currentTimeMillis())
                .apply();
    }

    public void markAccepted(String merchantKey) {
        if (appContext == null) return;
        String key = normalizeKey(merchantKey);
        if (key.length() == 0) return;
        prefs().edit()
                .putBoolean(KEY_ACCEPTED + key, true)
                .putBoolean(KEY_IGNORED + key, false)
                .putLong(KEY_UPDATED_AT + key, System.currentTimeMillis())
                .apply();
    }

    public void reset(String merchantKey) {
        if (appContext == null) return;
        String key = normalizeKey(merchantKey);
        if (key.length() == 0) return;
        prefs().edit()
                .remove(KEY_IGNORED + key)
                .remove(KEY_ACCEPTED + key)
                .remove(KEY_UPDATED_AT + key)
                .apply();
    }

    private SharedPreferences prefs() {
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private String normalizeKey(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.FRENCH);
        s = s.replaceAll("[^a-z0-9_]+", "_");
        s = s.replaceAll("_+", "_");
        s = s.replaceAll("^_+|_+$", "");
        return s;
    }
}
