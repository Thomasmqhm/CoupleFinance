package com.couplefinance.ui.settings;

import com.couplefinance.data.FirestoreManager;

/**
 * SettingsCategoryWriter — écriture Firestore des catégories Settings.
 *
 * Compatible avec :
 * - BudgetView
 * - Transactions
 * - Import PDF
 * - Dashboard/HomeWidgets
 *
 * Collection : households/{householdId}/categories
 */
public final class SettingsCategoryWriter {

    public interface Callback {
        void onSuccess();
        void onError(String error);
    }

    private SettingsCategoryWriter() {
    }

    public static void saveCategory(SettingsModels.Category category, Callback cb) {
        if (category == null) {
            if (cb != null) cb.onError("Catégorie invalide");
            return;
        }

        if (category.name == null || category.name.trim().isEmpty()) {
            if (cb != null) cb.onError("Nom de catégorie invalide");
            return;
        }

        if (category.type == null || category.type.trim().isEmpty()) {
            category.type = "expense";
        }

        if (category.emoji == null || category.emoji.trim().isEmpty()) {
            category.emoji = "income".equals(category.type) ? "↗️" : "🏷️";
        }

        if (category.color == null || category.color.trim().isEmpty()) {
            category.color = "income".equals(category.type) ? "#2D7D55" : "#C0614A";
        }

        if (category.docPath == null || category.docPath.trim().isEmpty()) {
            createCategory(category, cb);
        } else {
            patchCategory(category, cb);
        }
    }

    public static void deleteCategory(SettingsModels.Category category, Callback cb) {
        if (category == null || category.docPath == null || category.docPath.trim().isEmpty()) {
            if (cb != null) cb.onError("Document catégorie introuvable");
            return;
        }

        String path = cleanDocumentPath(category.docPath);

        FirestoreManager.getInstance().deleteDocument(path, new FirestoreManager.Callback() {
            public void onSuccess(String response) {
                if (cb != null) cb.onSuccess();
            }

            public void onError(String error) {
                if (cb != null) cb.onError(error);
            }
        });
    }

    private static void createCategory(SettingsModels.Category category, Callback cb) {
        String path = FirestoreManager.getInstance().getHouseholdPath() + "/categories";

        FirestoreManager.getInstance().postDocument(path, buildBody(category), new FirestoreManager.Callback() {
            public void onSuccess(String response) {
                String name = extractDocumentName(response);

                if (name != null && !name.trim().isEmpty()) {
                    category.docPath = name;
                }

                if (cb != null) cb.onSuccess();
            }

            public void onError(String error) {
                if (cb != null) cb.onError(error);
            }
        });
    }

    private static void patchCategory(SettingsModels.Category category, Callback cb) {
        String path = cleanDocumentPath(category.docPath);

        String mask = "updateMask.fieldPaths=name"
                + "&updateMask.fieldPaths=type"
                + "&updateMask.fieldPaths=active"
                + "&updateMask.fieldPaths=emoji"
                + "&updateMask.fieldPaths=color"
                + "&updateMask.fieldPaths=budget";

        FirestoreManager.getInstance().patchDocument(path, buildBody(category), mask, new FirestoreManager.Callback() {
            public void onSuccess(String response) {
                if (cb != null) cb.onSuccess();
            }

            public void onError(String error) {
                if (cb != null) cb.onError(error);
            }
        });
    }

    private static String buildBody(SettingsModels.Category category) {
        double budget = Math.max(0, category.budget);

        return "{\"fields\":{" 
                + "\"name\":{\"stringValue\":\"" + escape(category.name.trim()) + "\"},"
                + "\"type\":{\"stringValue\":\"" + escape(category.type.trim()) + "\"},"
                + "\"emoji\":{\"stringValue\":\"" + escape(category.emoji) + "\"},"
                + "\"color\":{\"stringValue\":\"" + escape(category.color) + "\"},"
                + "\"budget\":{\"doubleValue\":" + budget + "},"
                + "\"active\":{\"booleanValue\":" + category.active + "}"
                + "}}";
    }

    private static String extractDocumentName(String response) {
        if (response == null) return "";

        String marker = "\"name\"";
        int i = response.indexOf(marker);
        if (i < 0) return "";

        int colon = response.indexOf(":", i);
        if (colon < 0) return "";

        int start = response.indexOf("\"", colon + 1);
        if (start < 0) return "";

        int end = response.indexOf("\"", start + 1);
        if (end <= start) return "";

        return response.substring(start + 1, end).trim();
    }

    private static String cleanDocumentPath(String fullPath) {
        if (fullPath == null) return "";

        String p = fullPath.trim();
        String marker = "/documents/";
        int index = p.indexOf(marker);

        if (index >= 0) {
            return p.substring(index + marker.length());
        }

        return p;
    }

    private static String escape(String value) {
        if (value == null) return "";

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
