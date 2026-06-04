package com.couplefinance.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.couplefinance.utils.ParsedTransaction;
import com.couplefinance.utils.PdfTransactionParser;

import java.util.Locale;

/**
 * Règles d'apprentissage pour l'import PDF.
 *
 * Objectif : quand l'utilisateur corrige une opération dans le modal PDF
 * (ex : PADDINGTON -> Tabac), l'application mémorise ce choix et l'applique :
 * - aux autres lignes identiques dans le modal courant ;
 * - aux prochains imports PDF ;
 * - sans modifier les appels Firestore existants.
 *
 * Stockage local volontairement simple avec SharedPreferences pour rester stable
 * dans CodeAssist et éviter toute dépendance supplémentaire.
 */
public class MerchantRuleManager {

    private static final String PREFS_NAME = "couplefinance_merchant_rules_v1";

    private static final String KEY_CATEGORY = "category_";
    private static final String KEY_LABEL = "label_";
    private static final String KEY_TYPE = "type_";
    private static final String KEY_UPDATED_AT = "updated_at_";

    private static MerchantRuleManager instance;

    private Context appContext;

    public static MerchantRuleManager getInstance() {
        if (instance == null) {
            instance = new MerchantRuleManager();
        }
        return instance;
    }

    public void init(Context context) {
        if (context != null && appContext == null) {
            appContext = context.getApplicationContext();
        }
    }

    /**
     * Retourne une clé stable à partir du libellé ou de la merchantKey déjà calculée.
     */
    public String resolveMerchantKey(ParsedTransaction tx) {
        if (tx == null) return "";

        String key = safe(tx.merchantKey);
        if (key.length() == 0) {
            key = PdfTransactionParser.merchantKey(tx.label);
        }
        return normalizeKey(key);
    }

    public String resolveMerchantKey(String label) {
        return normalizeKey(PdfTransactionParser.merchantKey(label));
    }

    /**
     * Applique une règle connue à une transaction PDF.
     * Les catégories sont appliquées uniquement si elles existent encore dans les Paramètres.
     */
    public boolean applyKnownRule(ParsedTransaction tx, java.util.List<String> allowedCategories) {
        if (tx == null || appContext == null) return false;

        String merchantKey = resolveMerchantKey(tx);
        if (merchantKey.length() == 0) return false;

        SharedPreferences prefs = prefs();
        boolean changed = false;

        String learnedCategory = prefs.getString(KEY_CATEGORY + merchantKey, "");
        if (learnedCategory != null && learnedCategory.trim().length() > 0) {
            if (allowedCategories == null || containsIgnoreCase(allowedCategories, learnedCategory)) {
                tx.category = learnedCategory.trim();
                changed = true;
            }
        }

        String learnedLabel = prefs.getString(KEY_LABEL + merchantKey, "");
        if (learnedLabel != null && learnedLabel.trim().length() > 0) {
            tx.label = learnedLabel.trim();
            changed = true;
        }

        String learnedType = prefs.getString(KEY_TYPE + merchantKey, "");
        if ("income".equals(learnedType) || "expense".equals(learnedType)) {
            tx.type = learnedType;
            changed = true;
        }

        tx.merchantKey = merchantKey;
        return changed;
    }

    /**
     * Enregistre une règle complète depuis une transaction validée/importée.
     */
    public void saveRuleFromTransaction(ParsedTransaction tx) {
        if (tx == null || appContext == null) return;

        String merchantKey = resolveMerchantKey(tx);
        if (merchantKey.length() == 0) return;

        String category = safe(tx.category);
        String label = safe(tx.label);
        String type = safe(tx.type);

        SharedPreferences.Editor editor = prefs().edit();

        if (category.length() > 0) editor.putString(KEY_CATEGORY + merchantKey, category);
        if (label.length() > 0 && !"Transaction".equalsIgnoreCase(label)) editor.putString(KEY_LABEL + merchantKey, label);
        if ("income".equals(type) || "expense".equals(type)) editor.putString(KEY_TYPE + merchantKey, type);

        editor.putLong(KEY_UPDATED_AT + merchantKey, System.currentTimeMillis());
        editor.apply();
    }

    /**
     * Enregistre uniquement une catégorie choisie dans le modal.
     */
    public void saveCategoryRule(String merchantKey, String category) {
        if (appContext == null) return;

        merchantKey = normalizeKey(merchantKey);
        category = safe(category);

        if (merchantKey.length() == 0 || category.length() == 0) return;

        prefs().edit()
                .putString(KEY_CATEGORY + merchantKey, category)
                .putLong(KEY_UPDATED_AT + merchantKey, System.currentTimeMillis())
                .apply();
    }

    /**
     * Enregistre un libellé propre choisi par l'utilisateur.
     */
    public void saveLabelRule(String merchantKey, String cleanLabel) {
        if (appContext == null) return;

        merchantKey = normalizeKey(merchantKey);
        cleanLabel = safe(cleanLabel);

        if (merchantKey.length() == 0 || cleanLabel.length() == 0) return;

        prefs().edit()
                .putString(KEY_LABEL + merchantKey, cleanLabel)
                .putLong(KEY_UPDATED_AT + merchantKey, System.currentTimeMillis())
                .apply();
    }

    public String getKnownCategory(String merchantKey) {
        if (appContext == null) return "";
        merchantKey = normalizeKey(merchantKey);
        if (merchantKey.length() == 0) return "";
        return prefs().getString(KEY_CATEGORY + merchantKey, "");
    }

    private SharedPreferences prefs() {
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private String normalizeKey(String raw) {
        String s = safe(raw).toLowerCase(Locale.FRENCH).trim();
        s = s.replaceAll("[^a-z0-9_]+", "_");
        s = s.replaceAll("_+", "_");
        s = s.replaceAll("^_+|_+$", "");
        return s;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean containsIgnoreCase(java.util.List<String> list, String value) {
        if (list == null || value == null) return false;
        String target = value.trim();
        for (String item : list) {
            if (item != null && item.trim().equalsIgnoreCase(target)) return true;
        }
        return false;
    }
}
