package com.couplefinance.ui.epargne;

import android.app.Activity;

import com.couplefinance.data.FirestoreManager;
import com.couplefinance.data.SavingsManager;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  EpargneRepository — Accès données Firestore                ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Centralise TOUTES les opérations réseau du package.        ║
 * ║  Retourne toujours des modèles typés, jamais de JSON brut.  ║
 * ║                                                             ║
 * ║  Appelé par : EpargneView uniquement                        ║
 * ║  Appelle    : EpargneParser pour transformer le JSON        ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
public final class EpargneRepository {

    private EpargneRepository() {}

    // ─────────────────────────────────────────────────────────────
    // Callbacks
    // ─────────────────────────────────────────────────────────────

    public interface OnDataLoaded {
        void onLoaded(EpargneModels.EpargneData data);
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
     * Charge les objectifs d'épargne depuis Firestore.
     * Construit aussi l'historique mensuel.
     * Rappelle callback.onLoaded() sur le thread UI.
     */
    public static void loadAll(Activity activity, OnDataLoaded callback) {
        SavingsManager.getInstance().getSavings(new FirestoreManager.Callback() {
            public void onSuccess(String json) {
                EpargneModels.EpargneData data = new EpargneModels.EpargneData(
                    EpargneParser.parseSavings(json),
                    EpargneParser.buildMonthHistory(json),
                    EpargneParser.buildMonthLabels()
                );
                activity.runOnUiThread(() -> callback.onLoaded(data));
            }
            public void onError(String e) {
                EpargneModels.EpargneData empty = new EpargneModels.EpargneData(
                    new java.util.ArrayList<>(),
                    new double[]{ 0, 0, 0, 0 },
                    EpargneParser.buildMonthLabels()
                );
                activity.runOnUiThread(() -> callback.onLoaded(empty));
            }
        });
    }

    // ─────────────────────────────────────────────────────────────
    // Écriture
    // ─────────────────────────────────────────────────────────────

    /**
     * Crée un nouvel objectif d'épargne dans Firestore.
     */
    public static void addGoal(String name, double target, double current,
                                String emoji, String colorHex, long targetDateMs,
                                Activity activity, OnWriteComplete callback) {
        SavingsManager.getInstance().addSaving(
            name, target, current, emoji, colorHex, targetDateMs,
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
     * Met à jour le montant épargné sur un objectif existant (versement).
     */
    public static void updateGoalCurrent(String docId, double newCurrent,
                                          Activity activity, OnWriteComplete callback) {
        SavingsManager.getInstance().updateSavingCurrent(docId, newCurrent,
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
     * Supprime un objectif d'épargne par son docId.
     */
    public static void deleteGoal(String docId, Activity activity, OnWriteComplete callback) {
        SavingsManager.getInstance().deleteSaving(docId,
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
