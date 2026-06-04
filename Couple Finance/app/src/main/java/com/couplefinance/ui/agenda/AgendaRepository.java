package com.couplefinance.ui.agenda;

import android.app.Activity;

import com.couplefinance.AuthManager;
import com.couplefinance.data.EventManager;
import com.couplefinance.data.FirestoreManager;
import com.couplefinance.data.HouseholdManager;
import com.couplefinance.data.TransactionManager;
import com.couplefinance.ui.home.HomeFixedChargeProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  AgendaRepository — Accès données Firestore                 ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Charge en parallèle :                                      ║
 * ║    1. Membres du foyer                                      ║
 * ║    2. Événements (/events)                                  ║
 * ║    3. Transactions (/transactions)                          ║
 * ║    4. Charges fixes planifiées (HomeFixedChargeProvider)    ║
 * ║                                                             ║
 * ║  Appelé par : AgendaView uniquement                         ║
 * ║  Appelle    : AgendaParser pour transformer le JSON         ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
public final class AgendaRepository {

    private AgendaRepository() {}

    // ─────────────────────────────────────────────────────────────
    // Callbacks
    // ─────────────────────────────────────────────────────────────

    public interface OnDataLoaded {
        void onLoaded(AgendaModels.AgendaData data);
        void onError(String message);
    }

    public interface OnWriteComplete {
        void onSuccess();
        void onError(String message);
    }

    // ─────────────────────────────────────────────────────────────
    // Chargement complet
    // ─────────────────────────────────────────────────────────────

    /**
     * Charge en parallèle membres + événements + transactions + charges fixes.
     * Rappelle callback.onLoaded() sur le thread UI quand tout est prêt.
     */
    public static void loadAll(Activity activity, OnDataLoaded callback) {
        AtomicInteger done = new AtomicInteger(0);

        final List<AgendaModels.AgendaEvent>[]       events = new List[1];
        final List<AgendaModels.AgendaTransaction>[] txList = new List[1];
        final List<String>[]                          members = new List[1];

        events[0]  = new ArrayList<>();
        txList[0]  = new ArrayList<>();
        members[0] = new ArrayList<>();

        // Déclenche la livraison quand les 3 premiers sont chargés
        Runnable checkPhase1 = () -> {
            if (done.incrementAndGet() == 3) {
                // Phase 2 : charger les charges fixes planifiées
                loadFixedCharges(activity, txList[0], () -> {
                    AgendaModels.AgendaData data = new AgendaModels.AgendaData(
                        events[0], txList[0], members[0]);
                    activity.runOnUiThread(() -> callback.onLoaded(data));
                });
            }
        };

        // 1. Membres
        HouseholdManager.getInstance().getMembers(new FirestoreManager.Callback() {
            public void onSuccess(String json) {
                members[0] = AgendaParser.parseMembers(json);

                // Ajouter "moi" si absent
                String me = AuthManager.getInstance().getDisplayName();
                if (me != null && !me.isEmpty()) {
                    boolean found = false;
                    for (String m : members[0]) if (m.equalsIgnoreCase(me)) { found = true; break; }
                    if (!found) members[0].add(0, me);
                }
                if (members[0].isEmpty()) members[0].add("Moi");
                checkPhase1.run();
            }
            public void onError(String e) {
                String me = AuthManager.getInstance().getDisplayName();
                members[0].add(me != null && !me.isEmpty() ? me : "Moi");
                checkPhase1.run();
            }
        });

        // 2. Événements
        EventManager.getInstance().getEvents(new FirestoreManager.Callback() {
            public void onSuccess(String json) {
                events[0] = AgendaParser.parseEvents(json);
                checkPhase1.run();
            }
            public void onError(String e) { checkPhase1.run(); }
        });

        // 3. Transactions
        TransactionManager.getInstance().getTransactions(new FirestoreManager.Callback() {
            public void onSuccess(String json) {
                txList[0] = AgendaParser.parseTransactions(json);
                checkPhase1.run();
            }
            public void onError(String e) { checkPhase1.run(); }
        });
    }

    // ─────────────────────────────────────────────────────────────
    // Charges fixes planifiées (phase 2)
    // ─────────────────────────────────────────────────────────────

    /**
     * Charge les charges fixes planifiées et les ajoute à la liste txList.
     * Supprime d'abord les anciennes charges planifiées pour éviter les doublons.
     */
    private static void loadFixedCharges(Activity activity,
                                          List<AgendaModels.AgendaTransaction> txList,
                                          Runnable onComplete) {
        // Supprimer les charges planifiées déjà présentes (évite les doublons)
        txList.removeIf(tx -> "fixed_planned".equals(tx.type) || "fixed_done".equals(tx.type));

        HomeFixedChargeProvider.loadPlannedChargesForCurrentMonth(
            new HomeFixedChargeProvider.Callback() {
                public void onLoaded(ArrayList<String[]> plannedCharges) {
                    if (plannedCharges != null) {
                        for (String[] charge : plannedCharges) {
                            if (charge == null || charge.length < 5) continue;
                            // Format : { label, amount, type, category, dateMs }
                            try {
                                txList.add(new AgendaModels.AgendaTransaction(
                                    charge[0],
                                    Double.parseDouble(charge[1]),
                                    charge[2],
                                    charge.length > 3 ? charge[3] : "",
                                    Long.parseLong(charge[4])
                                ));
                            } catch (Exception ignored) {}
                        }
                    }
                    onComplete.run();
                }
                public void onError(String error) { onComplete.run(); }
            });
    }

    // ─────────────────────────────────────────────────────────────
    // Écriture événements
    // ─────────────────────────────────────────────────────────────

    /**
     * Crée un nouvel événement dans Firestore.
     */
    public static void addEvent(String title, String type, double amount,
                                 long dateMs, String person, String note,
                                 Activity activity, OnWriteComplete callback) {
        EventManager.getInstance().addEvent(title, type, amount, dateMs, person, note,
            new FirestoreManager.Callback() {
                public void onSuccess(String r) {
                    activity.runOnUiThread(callback::onSuccess);
                }
                public void onError(String e) {
                    activity.runOnUiThread(() -> callback.onError(e));
                }
            });
    }

    /**
     * Supprime un événement par son chemin Firestore.
     */
    public static void deleteEvent(String docPath, Activity activity,
                                    OnWriteComplete callback) {
        EventManager.getInstance().deleteEvent(docPath,
            new FirestoreManager.Callback() {
                public void onSuccess(String r) {
                    activity.runOnUiThread(callback::onSuccess);
                }
                public void onError(String e) {
                    activity.runOnUiThread(() -> callback.onError(e));
                }
            });
    }
}
