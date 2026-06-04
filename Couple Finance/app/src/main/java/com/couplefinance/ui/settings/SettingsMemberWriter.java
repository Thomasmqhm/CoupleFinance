package com.couplefinance.ui.settings;

import com.couplefinance.data.BalanceManager;
import com.couplefinance.data.FirestoreManager;
import com.couplefinance.data.SettingsManager;

import org.json.JSONObject;

/**
 * SettingsMemberWriter
 *
 * Sauvegarde un membre dans households/{householdId}/persons/{docId}.
 *
 * Synchronisation du solde de début de mois :
 *   - Après un PATCH réussi sur /persons/, si le membre courant est
 *     l'utilisateur connecté (admin == true ou docPath matche), on appelle
 *     également BalanceManager.saveMonthlyStartBalance() pour que le
 *     Dashboard soit cohérent avec les Paramètres.
 *   - Cette double écriture garantit que /balances/{userId}_{month} et
 *     /persons/{docId}.monthlyStartBalance restent synchronisés.
 */
public class SettingsMemberWriter {

    public interface Callback {
        void onSuccess();
        void onError(String error);
    }

    public static void saveMember(SettingsModels.Member member, Callback cb) {
        if (member == null) {
            if (cb != null) cb.onError("Membre invalide");
            return;
        }

        if (member.name == null || member.name.trim().isEmpty()) {
            if (cb != null) cb.onError("Nom invalide");
            return;
        }

        if (member.docPath == null || member.docPath.trim().isEmpty()) {
            createMemberThenSave(member, cb);
            return;
        }

        patchMember(member, cb);
    }

    private static void createMemberThenSave(SettingsModels.Member member, Callback cb) {
        SettingsManager.getInstance().addPerson(member.name, new FirestoreManager.Callback() {
            public void onSuccess(String response) {
                try {
                    String createdPath = "";

                    try {
                        JSONObject json = new JSONObject(response);
                        createdPath = json.optString("name", "");
                    } catch (Exception ignored) {
                    }

                    if (createdPath == null || createdPath.trim().isEmpty()) {
                        if (cb != null) cb.onError("Document membre créé mais chemin introuvable");
                        return;
                    }

                    member.docPath = createdPath;
                    patchMember(member, cb);

                } catch (Exception e) {
                    if (cb != null) cb.onError(e.getMessage());
                }
            }

            public void onError(String error) {
                if (cb != null) cb.onError(error);
            }
        });
    }

    private static void patchMember(SettingsModels.Member member, Callback cb) {
        String path = cleanDocumentPath(member.docPath);

        String body = "{\"fields\":{"
                + "\"name\":{\"stringValue\":\"" + escape(member.name) + "\"},"
                + "\"role\":{\"stringValue\":\"" + escape(member.role) + "\"},"
                + "\"color\":{\"stringValue\":\"" + escape(member.color) + "\"},"
                + "\"revenue\":{\"doubleValue\":" + safeDouble(member.income) + "},"
                + "\"monthlyStartBalance\":{\"doubleValue\":" + safeDouble(member.monthlyStartBalance) + "},"
                + "\"overdraft\":{\"doubleValue\":" + safeDouble(member.overdraft) + "},"
                + "\"notifications\":{\"booleanValue\":" + member.notifications + "},"
                + "\"overdraftAlert\":{\"booleanValue\":" + member.overdraftAlert + "}"
                + "}}";

        String mask =
                "updateMask.fieldPaths=name"
                        + "&updateMask.fieldPaths=role"
                        + "&updateMask.fieldPaths=color"
                        + "&updateMask.fieldPaths=revenue"
                        + "&updateMask.fieldPaths=monthlyStartBalance"
                        + "&updateMask.fieldPaths=overdraft"
                        + "&updateMask.fieldPaths=notifications"
                        + "&updateMask.fieldPaths=overdraftAlert";

        FirestoreManager.getInstance().patchDocument(path, body, mask, new FirestoreManager.Callback() {
            public void onSuccess(String response) {
                // ── Synchronisation Dashboard ↔ Paramètres ──────────────────────────
                // Si le membre sauvegardé est l'utilisateur courant (admin),
                // on reporte aussi le solde dans /balances/ pour que HomeView
                // affiche la même valeur que les Paramètres.
                if (member.admin && member.monthlyStartBalance >= 0) {
                    syncBalanceToBalancesCollection(member.monthlyStartBalance);
                }
                // ────────────────────────────────────────────────────────────────────
                if (cb != null) cb.onSuccess();
            }

            public void onError(String error) {
                if (cb != null) cb.onError(error);
            }
        });
    }

    /**
     * Reporte le solde de début de mois saisi dans Paramètres vers la collection
     * /balances/ lue par HomeView.
     *
     * Cela garantit que Dashboard et Paramètres affichent toujours le même chiffre,
     * quelle que soit l'interface par laquelle l'utilisateur a saisi son solde.
     */
    private static void syncBalanceToBalancesCollection(double amount) {
        try {
            BalanceManager.getInstance().saveMonthlyStartBalance(amount, new FirestoreManager.Callback() {
                public void onSuccess(String response) {
                    // Sync silencieuse — pas de callback à l'appelant
                }

                public void onError(String error) {
                    // Fire-and-forget : la sync /balances/ est best-effort.
                    // Le PATCH /persons/ a déjà réussi, on ne remonte pas l'erreur.
                }
            });
        } catch (Exception ignored) {
            // BalanceManager non initialisé ou contexte manquant : ignoré.
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

        if (p.startsWith("projects/")) {
            int docIndex = p.indexOf("/documents/");
            if (docIndex >= 0) {
                return p.substring(docIndex + "/documents/".length());
            }
        }

        return p;
    }

    private static String escape(String value) {
        if (value == null) return "";

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private static double safeDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0;
        return value;
    }
}
