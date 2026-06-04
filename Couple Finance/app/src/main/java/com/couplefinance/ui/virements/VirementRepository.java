package com.couplefinance.ui.virements;

import android.app.Activity;

import com.couplefinance.data.BeneficiaryManager;
import com.couplefinance.data.FirestoreManager;
import com.couplefinance.data.HouseholdManager;
import com.couplefinance.data.TransactionManager;
import com.couplefinance.data.TransferManager;

import java.util.concurrent.atomic.AtomicInteger;

public class VirementRepository {

    public interface Callback {
        void onComplete(boolean success, String message);
    }

    public interface OnDataLoaded {
        void onLoaded(VirementModels.VirementData data);
    }

    public void loadAll(Activity activity, OnDataLoaded callback) {
        VirementModels.VirementData data = new VirementModels.VirementData();
        AtomicInteger completed = new AtomicInteger(0);

        Runnable finishOne = () -> {
            if (completed.incrementAndGet() >= 3) {
                activity.runOnUiThread(() -> callback.onLoaded(data));
            }
        };

        BeneficiaryManager.getInstance().getBeneficiaries(new FirestoreManager.Callback() {
            public void onSuccess(String response) {
                data.beneficiaries = VirementParser.parseBeneficiaries(response);
                finishOne.run();
            }

            public void onError(String error) {
                finishOne.run();
            }
        });

        TransferManager.getInstance().getTransfers(new FirestoreManager.Callback() {
            public void onSuccess(String response) {
                data.transfers = VirementParser.parseTransfers(response);
                finishOne.run();
            }

            public void onError(String error) {
                finishOne.run();
            }
        });

        HouseholdManager.getInstance().getMembers(new FirestoreManager.Callback() {
            public void onSuccess(String response) {
                data.members = VirementParser.parseMembersResponse(response);
                finishOne.run();
            }

            public void onError(String error) {
                finishOne.run();
            }
        });
    }

    public void addBeneficiary(String name, String iban, Activity activity, Callback callback) {
        BeneficiaryManager.getInstance().addBeneficiary(name, iban, new FirestoreManager.Callback() {
            public void onSuccess(String response) {
                post(activity, callback, true, response);
            }

            public void onError(String error) {
                post(activity, callback, false, error);
            }
        });
    }

    public void deleteBeneficiary(String docPath, Activity activity, Callback callback) {
        BeneficiaryManager.getInstance().deleteBeneficiary(docPath, new FirestoreManager.Callback() {
            public void onSuccess(String response) {
                post(activity, callback, true, response);
            }

            public void onError(String error) {
                post(activity, callback, false, error);
            }
        });
    }

    public void addTransfer(String from, String to, double amount, String motif, long dateMs, Activity activity, Callback callback) {
        TransferManager.getInstance().addTransfer(from, to, amount, motif, dateMs, new FirestoreManager.Callback() {
            public void onSuccess(String response) {
                post(activity, callback, true, response);
            }

            public void onError(String error) {
                post(activity, callback, false, error);
            }
        });
    }

    /**
     * Supprime un virement ET les transactions qu'il a générées.
     *
     * <p>Un virement interne crée 2 transactions liées (une dépense côté
     * émetteur, une recette côté receveur). Les supprimer en même temps que
     * le virement évite des transactions orphelines qui fausseraient les
     * soldes du Dashboard.</p>
     *
     * <p>Les transactions liées sont identifiées par correspondance : même
     * jour, même montant, et libellé de virement contenant les deux noms.</p>
     */
    public void deleteTransfer(VirementModels.Transfer transfer, Activity activity, Callback callback) {
        if (transfer == null) {
            post(activity, callback, false, "Virement introuvable");
            return;
        }

        final String docPath = transfer.docPath;
        final String transferId = VirementParser.extractDocId(transfer.docPath);
        final double amount = transfer.amount;
        final long dateMs = transfer.dateMs;
        final String from = transfer.from == null ? "" : transfer.from.trim();
        final String to = transfer.to == null ? "" : transfer.to.trim();

        // 1) Récupère les transactions pour retrouver les lignes liées.
        TransactionManager.getInstance().getTransactions(new FirestoreManager.Callback() {
            public void onSuccess(String response) {
                java.util.List<String> linkedIds = findLinkedTransactionIds(
                        response, transferId, amount, dateMs, from, to);

                // 2) Supprime les transactions liées, puis le virement.
                deleteLinkedThenTransfer(linkedIds, docPath, activity, callback);
            }

            public void onError(String error) {
                // Échec de lecture : on supprime au moins le virement.
                deleteTransferDocOnly(docPath, activity, callback);
            }
        });
    }

    /** Ancienne signature conservée pour compatibilité. */
    public void deleteTransfer(String docPath, String txId, Activity activity, Callback callback) {
        deleteTransferDocOnly(docPath, activity, callback);
    }

    private void deleteLinkedThenTransfer(java.util.List<String> linkedIds, String docPath,
            Activity activity, Callback callback) {

        if (linkedIds == null || linkedIds.isEmpty()) {
            deleteTransferDocOnly(docPath, activity, callback);
            return;
        }

        AtomicInteger remaining = new AtomicInteger(linkedIds.size());

        for (String txId : linkedIds) {
            TransactionManager.getInstance().deleteTransaction(txId, new FirestoreManager.Callback() {
                public void onSuccess(String r) {
                    if (remaining.decrementAndGet() <= 0) {
                        deleteTransferDocOnly(docPath, activity, callback);
                    }
                }

                public void onError(String e) {
                    // Même en cas d'échec d'une transaction, on poursuit :
                    // le virement doit quand même être supprimé.
                    if (remaining.decrementAndGet() <= 0) {
                        deleteTransferDocOnly(docPath, activity, callback);
                    }
                }
            });
        }
    }

    private void deleteTransferDocOnly(String docPath, Activity activity, Callback callback) {
        String docId = VirementParser.extractDocId(docPath);

        TransferManager.getInstance().deleteTransfer(docId, new FirestoreManager.Callback() {
            public void onSuccess(String response) {
                post(activity, callback, true, response);
            }

            public void onError(String error) {
                post(activity, callback, false, error);
            }
        });
    }

    /**
     * Repère, dans la réponse Firestore brute des transactions, les
     * identifiants des lignes générées par un virement donné.
     *
     * <p>Identification <b>exacte</b> via le champ {@code transferId} que
     * chaque transaction de virement porte désormais. En l'absence de ce
     * champ (transactions créées avant cette version), on retombe sur une
     * correspondance approximative (montant + jour + libellé).</p>
     */
    private java.util.List<String> findLinkedTransactionIds(String json, String transferId,
            double amount, long dateMs, String from, String to) {

        java.util.List<String> ids = new java.util.ArrayList<>();
        if (json == null || json.isEmpty()) {
            return ids;
        }

        String wantedTransferId = transferId == null ? "" : transferId.trim();
        String transferDay = dayKey(dateMs);
        long amountCents = Math.round(amount * 100);

        String[] docs = json.split("\\{\\s*\"name\"");
        for (int i = 1; i < docs.length; i++) {
            String doc = docs[i];

            String docId = extractDocIdFromName(doc);
            if (docId.isEmpty()) {
                continue;
            }

            // 1) Correspondance EXACTE par transferId (fiable).
            String docTransferId = extractString(doc, "transferId");
            if (!wantedTransferId.isEmpty() && !docTransferId.isEmpty()) {
                if (docTransferId.equals(wantedTransferId)) {
                    ids.add(docId);
                }
                // Si la transaction porte un transferId mais qu'il ne
                // correspond pas, elle appartient à un autre virement :
                // on ne l'inclut pas et on ne tente pas l'approximatif.
                continue;
            }

            // 2) Repli approximatif pour les transactions sans transferId
            //    (créées avant l'introduction du champ).
            double docAmount = extractDouble(doc, "amount");
            if (Math.round(docAmount * 100) != amountCents) {
                continue;
            }

            long docDate = extractLong(doc, "date");
            if (docDate <= 0 || !dayKey(docDate).equals(transferDay)) {
                continue;
            }

            String label = extractString(doc, "label");
            String n = label.toLowerCase(java.util.Locale.FRANCE);
            boolean looksLikeTransfer = n.contains("virement");
            boolean mentionsBoth =
                    (from.isEmpty() || n.contains(from.toLowerCase(java.util.Locale.FRANCE)))
                            && (to.isEmpty() || n.contains(to.toLowerCase(java.util.Locale.FRANCE)));

            if (looksLikeTransfer && mentionsBoth) {
                ids.add(docId);
            }
        }

        return ids;
    }

    // ── Helpers d'extraction Firestore ────────────────────────────

    private String extractDocIdFromName(String docFragment) {
        // docFragment commence après "name" ; la valeur est : "...":"path".
        int colon = docFragment.indexOf(':');
        if (colon < 0) {
            return "";
        }
        int firstQuote = docFragment.indexOf('"', colon);
        if (firstQuote < 0) {
            return "";
        }
        int secondQuote = docFragment.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) {
            return "";
        }
        String fullPath = docFragment.substring(firstQuote + 1, secondQuote);
        int slash = fullPath.lastIndexOf('/');
        return slash >= 0 ? fullPath.substring(slash + 1) : fullPath;
    }

    private String extractString(String doc, String field) {
        int idx = doc.indexOf("\"" + field + "\"");
        if (idx < 0) {
            return "";
        }
        int sv = doc.indexOf("\"stringValue\"", idx);
        if (sv < 0) {
            return "";
        }
        int firstQuote = doc.indexOf('"', sv + 14);
        if (firstQuote < 0) {
            return "";
        }
        int secondQuote = doc.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) {
            return "";
        }
        return doc.substring(firstQuote + 1, secondQuote);
    }

    private double extractDouble(String doc, String field) {
        String raw = extractNumberRaw(doc, field);
        if (raw.isEmpty()) {
            return -1;
        }
        try {
            return Double.parseDouble(raw);
        } catch (Exception e) {
            return -1;
        }
    }

    private long extractLong(String doc, String field) {
        String raw = extractNumberRaw(doc, field);
        if (raw.isEmpty()) {
            return -1;
        }
        try {
            return Long.parseLong(raw.split("\\.")[0]);
        } catch (Exception e) {
            return -1;
        }
    }

    private String extractNumberRaw(String doc, String field) {
        int idx = doc.indexOf("\"" + field + "\"");
        if (idx < 0) {
            return "";
        }
        // Cherche doubleValue ou integerValue après le champ.
        int dv = doc.indexOf("\"doubleValue\"", idx);
        int iv = doc.indexOf("\"integerValue\"", idx);
        int valIdx;
        int skip;
        if (dv >= 0 && (iv < 0 || dv < iv)) {
            valIdx = dv;
            skip = 14;
        } else if (iv >= 0) {
            valIdx = iv;
            skip = 15;
        } else {
            return "";
        }
        // La valeur peut être nombre nu (123) ou chaîne ("123").
        int p = valIdx + skip;
        StringBuilder sb = new StringBuilder();
        boolean started = false;
        while (p < doc.length()) {
            char c = doc.charAt(p);
            if (c == '"') {
                p++;
                continue;
            }
            if (c == ':' || c == ' ') {
                p++;
                continue;
            }
            if (Character.isDigit(c) || c == '-' || c == '.') {
                sb.append(c);
                started = true;
            } else if (started) {
                break;
            }
            p++;
        }
        return sb.toString();
    }

    private String dayKey(long ms) {
        if (ms <= 0) {
            return "";
        }
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(ms);
        return c.get(java.util.Calendar.YEAR) + "-"
                + c.get(java.util.Calendar.MONTH) + "-"
                + c.get(java.util.Calendar.DAY_OF_MONTH);
    }

    private void post(Activity activity, Callback callback, boolean success, String message) {
        activity.runOnUiThread(() -> callback.onComplete(success, message));
    }
}
