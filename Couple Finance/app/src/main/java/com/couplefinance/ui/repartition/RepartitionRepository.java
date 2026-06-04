package com.couplefinance.ui.repartition;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.couplefinance.AuthManager;
import com.couplefinance.UserSession;
import com.couplefinance.data.FirestoreManager;
import com.couplefinance.data.HouseholdManager;
import com.couplefinance.data.TransactionManager;
import com.couplefinance.data.TransferManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  RepartitionRepository — Accès données + persistance ratio  ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Centralise :                                               ║
 * ║    • Chargement Firestore (membres + transactions)          ║
 * ║    • Sauvegarde du ratio dans SharedPreferences             ║
 * ║    • Création du remboursement (transactions + transfer)    ║
 * ║                                                             ║
 * ║  Appelé par : RepartitionView uniquement                    ║
 * ║  Appelle    : RepartitionParser pour transformer le JSON    ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
public final class RepartitionRepository {

    private static final String PREFS     = "couplefinance_prefs";
    private static final String KEY_RATIO = "repartition_ratio";

    private RepartitionRepository() {}

    // ─────────────────────────────────────────────────────────────
    // Callbacks
    // ─────────────────────────────────────────────────────────────

    public interface OnDataLoaded {
        void onLoaded(RepartitionModels.RepartitionData data);
        void onError(String message);
    }

    public interface OnWriteComplete {
        void onSuccess();
        void onError(String message);
    }

    // ─────────────────────────────────────────────────────────────
    // Chargement
    // ─────────────────────────────────────────────────────────────

    /**
     * Charge en parallèle membres + transactions, lit le ratio local,
     * puis appelle callback.onLoaded() sur le thread UI.
     */
    public static void loadAll(Activity activity, OnDataLoaded callback) {
        AtomicInteger loaded = new AtomicInteger(0);

        final List<String>[] members      = new List[1];
        final List<RepartitionModels.SharedTransaction>[] txList = new List[1];

        Runnable checkAndDeliver = () -> {
            if (loaded.incrementAndGet() == 2) {
                int[] ratio = loadRatio(activity);

                // Ajouter "moi" en premier si absent
                String myName = getMyName();
                if (!myName.isEmpty()) {
                    boolean found = false;
                    for (String m : members[0])
                        if (m.equalsIgnoreCase(myName)) { found = true; break; }
                    if (!found) members[0].add(0, myName);
                }

                RepartitionModels.RepartitionData data = new RepartitionModels.RepartitionData(
                    members[0] != null ? members[0] : new ArrayList<>(),
                    txList[0]  != null ? txList[0]  : new ArrayList<>(),
                    ratio
                );
                activity.runOnUiThread(() -> callback.onLoaded(data));
            }
        };

        // 1. Membres du foyer
        members[0] = new ArrayList<>();
        HouseholdManager.getInstance().getMembers(new FirestoreManager.Callback() {
            public void onSuccess(String json) {
                members[0] = RepartitionParser.parseMembers(json);
                checkAndDeliver.run();
            }
            public void onError(String e) {
                checkAndDeliver.run();
            }
        });

        // 2. Transactions
        txList[0] = new ArrayList<>();
        TransactionManager.getInstance().getTransactions(new FirestoreManager.Callback() {
            public void onSuccess(String json) {
                txList[0] = RepartitionParser.parseTransactions(json);
                checkAndDeliver.run();
            }
            public void onError(String e) {
                checkAndDeliver.run();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────
    // Remboursement
    // ─────────────────────────────────────────────────────────────

    /**
     * Crée le remboursement complet :
     *   1. Transaction dépense pour le débiteur
     *   2. Transaction revenu pour le créditeur
     *   3. Virement enregistré dans TransferManager
     *
     * Appelle callback.onSuccess() sur le thread UI après les 3 opérations.
     */
    public static void effectuerRemboursement(String from, String to,
                                               double amount, String motif,
                                               Activity activity,
                                               OnWriteComplete callback) {
        long date = System.currentTimeMillis();
        String labelExp = from + " · " + motif + " → " + to;
        String labelInc = to  + " · Remboursement reçu de " + from + " (" + motif + ")";

        TransactionManager.getInstance().addReimbursementTransaction(
            labelExp, amount, "variable", "Remboursement", date,
            new FirestoreManager.Callback() {
                public void onSuccess(String r1) {
                    TransactionManager.getInstance().addReimbursementTransaction(
                        labelInc, amount, "income", "Remboursement", date,
                        new FirestoreManager.Callback() {
                            public void onSuccess(String r2) {
                                TransferManager.getInstance().addTransfer(
                                    from, to, amount, motif, date,
                                    new FirestoreManager.Callback() {
                                        public void onSuccess(String r3) {
                                            activity.runOnUiThread(callback::onSuccess);
                                        }
                                        public void onError(String e3) {
                                            // Virement échoué mais transactions OK → succès quand même
                                            activity.runOnUiThread(callback::onSuccess);
                                        }
                                    });
                            }
                            public void onError(String e2) {
                                activity.runOnUiThread(() -> callback.onError(e2));
                            }
                        });
                }
                public void onError(String e1) {
                    activity.runOnUiThread(() -> callback.onError(e1));
                }
            });
    }

    // ─────────────────────────────────────────────────────────────
    // Persistance du ratio (SharedPreferences)
    // ─────────────────────────────────────────────────────────────

    /** Sauvegarde le ratio en SharedPreferences. */
    public static void saveRatio(Activity activity, int ratio0) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_RATIO, ratio0).apply();
    }

    /** Lit le ratio depuis SharedPreferences. Défaut : [50, 50]. */
    public static int[] loadRatio(Activity activity) {
        int r = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_RATIO, 50);
        return new int[]{ r, 100 - r };
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers privés
    // ─────────────────────────────────────────────────────────────

    private static String getMyName() {
        String name = UserSession.getInstance().getName();
        if (name != null && !name.isEmpty() && !name.contains("@"))
            return capitalize(name);
        name = AuthManager.getInstance().getDisplayName();
        if (name != null && !name.isEmpty() && !name.contains("@"))
            return capitalize(name);
        return "";
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(java.util.Locale.FRANCE) + s.substring(1);
    }
}
