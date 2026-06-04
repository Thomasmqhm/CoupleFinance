package com.couplefinance.ui.settings;

import com.couplefinance.data.FirestoreManager;
import com.couplefinance.data.FixedChargeManager;

import org.json.JSONObject;

public class SettingsChargeWriter {

    public interface Callback {
        void onSuccess();
        void onError(String error);
    }

    public static void saveCharge(SettingsModels.FixedCharge charge, Callback cb) {
        if (charge == null || charge.name == null || charge.name.trim().isEmpty()) {
            if (cb != null) cb.onError("Charge invalide");
            return;
        }

        normalizeCharge(charge);

        if (charge.docPath == null || charge.docPath.trim().isEmpty()) {
            createChargeThenSave(charge, cb);
            return;
        }

        patchCharge(charge, cb);
    }

    private static void createChargeThenSave(SettingsModels.FixedCharge charge, Callback cb) {
        FixedChargeManager.getInstance().addFixedCharge(
                charge.name,
                charge.amount,
                new FirestoreManager.Callback() {
                    public void onSuccess(String response) {
                        try {
                            JSONObject json = new JSONObject(response);
                            charge.docPath = json.optString("name", "");
                        } catch (Exception ignored) {
                        }

                        if (charge.docPath == null || charge.docPath.trim().isEmpty()) {
                            if (cb != null) cb.onError("Document charge introuvable");
                            return;
                        }

                        patchCharge(charge, cb);
                    }

                    public void onError(String error) {
                        if (cb != null) cb.onError(error);
                    }
                }
        );
    }

    public static void deleteCharge(SettingsModels.FixedCharge charge, Callback cb) {
        if (charge == null || charge.docPath == null || charge.docPath.trim().isEmpty()) {
            if (cb != null) cb.onError("Charge non synchronisée");
            return;
        }

        FirestoreManager.getInstance().deleteDocument(
                cleanDocumentPath(charge.docPath),
                new FirestoreManager.Callback() {
                    public void onSuccess(String response) {
                        if (cb != null) cb.onSuccess();
                    }

                    public void onError(String error) {
                        if (cb != null) cb.onError(error);
                    }
                }
        );
    }

    private static void patchCharge(SettingsModels.FixedCharge charge, Callback cb) {
        normalizeCharge(charge);

        String path = cleanDocumentPath(charge.docPath);

        String body = "{\"fields\":{"
                + "\"name\":{\"stringValue\":\"" + escape(charge.name) + "\"},"
                + "\"amount\":{\"doubleValue\":" + charge.amount + "},"
                + "\"dayOfMonth\":{\"integerValue\":\"" + charge.dayOfMonth + "\"},"
                + "\"category\":{\"stringValue\":\"" + escape(charge.category) + "\"},"
                + "\"icon\":{\"stringValue\":\"" + escape(charge.icon) + "\"},"
                + "\"frequency\":{\"stringValue\":\"" + escape(charge.frequency) + "\"},"
                + "\"ratioA\":{\"integerValue\":\"" + charge.ratioA + "\"},"
                + "\"ratioB\":{\"integerValue\":\"" + charge.ratioB + "\"},"
                + "\"paidBy\":{\"stringValue\":\"" + escape(charge.paidBy) + "\"},"
                + "\"lastAppliedMonth\":{\"stringValue\":\"" + escape(charge.lastAppliedMonth) + "\"}"
                + "}}";

        String mask =
                "updateMask.fieldPaths=name"
                        + "&updateMask.fieldPaths=amount"
                        + "&updateMask.fieldPaths=dayOfMonth"
                        + "&updateMask.fieldPaths=category"
                        + "&updateMask.fieldPaths=icon"
                        + "&updateMask.fieldPaths=frequency"
                        + "&updateMask.fieldPaths=ratioA"
                        + "&updateMask.fieldPaths=ratioB"
                        + "&updateMask.fieldPaths=paidBy"
                        + "&updateMask.fieldPaths=lastAppliedMonth";

        FirestoreManager.getInstance().patchDocument(path, body, mask, new FirestoreManager.Callback() {
            public void onSuccess(String response) {
                if (cb != null) cb.onSuccess();
            }

            public void onError(String error) {
                if (cb != null) cb.onError(error);
            }
        });
    }

    private static void normalizeCharge(SettingsModels.FixedCharge charge) {
        if (charge.category == null || charge.category.trim().isEmpty()) {
            charge.category = "Général";
        }

        if (charge.icon == null || charge.icon.trim().isEmpty()) {
            charge.icon = "💳";
        }

        if (charge.frequency == null || charge.frequency.trim().isEmpty()) {
            charge.frequency = "Mensuel";
        }

        if (charge.ratioA <= 0 && charge.ratioB <= 0) {
            charge.ratioA = 50;
            charge.ratioB = 50;
        }

        if (charge.dayOfMonth < 1) {
            charge.dayOfMonth = 1;
        }

        if (charge.dayOfMonth > 28) {
            charge.dayOfMonth = 28;
        }

        if (charge.lastAppliedMonth == null) {
            charge.lastAppliedMonth = "";
        }

        if (charge.paidBy == null) {
            charge.paidBy = "";
        }
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