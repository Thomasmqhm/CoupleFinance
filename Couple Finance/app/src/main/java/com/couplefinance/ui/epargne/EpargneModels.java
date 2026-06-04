package com.couplefinance.ui.epargne;

import java.util.List;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  EpargneModels — Modèles de données du package épargne      ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Remplace les String[] s = { name, target, current, ... }   ║
 * ║  de l'ancienne EpargneView.                                 ║
 * ║                                                             ║
 * ║  Utilisé par :                                              ║
 * ║    EpargneParser      → produit List<SavingsGoal>           ║
 * ║    EpargneRepository  → transmet les données                ║
 * ║    EpargneCalculator  → reçoit SavingsGoal, retourne double ║
 * ║    EpargneDialogs     → affiche SavingsGoal dans les forms  ║
 * ║    EpargneChart       → lit l'historique mensuel            ║
 * ║    EpargneView        → orchestre tout                      ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
public final class EpargneModels {

    private EpargneModels() {}

    // ─────────────────────────────────────────────────────────────
    // SavingsGoal — un objectif d'épargne du foyer
    // ─────────────────────────────────────────────────────────────

    public static class SavingsGoal {
        /** Identifiant Firestore du document. */
        public final String docId;

        /** Nom de l'objectif (ex: "Voyage Japon"). */
        public final String name;

        /** Montant cible (€). */
        public final double target;

        /** Montant déjà épargné (€). */
        public final double current;

        /** Emoji représentant l'objectif (ex: "✈️"). */
        public final String emoji;

        /** Couleur hex de l'objectif (ex: "#C0614A"). */
        public final String colorHex;

        /** Timestamp de la date cible (ms). 0 si pas de date. */
        public final long targetDateMs;

        public SavingsGoal(String docId, String name, double target, double current,
                           String emoji, String colorHex, long targetDateMs) {
            this.docId        = docId;
            this.name         = name;
            this.target       = target;
            this.current      = current;
            this.emoji        = emoji;
            this.colorHex     = colorHex;
            this.targetDateMs = targetDateMs;
        }

        // Raccourcis de lecture fréquents

        /** Montant restant à atteindre (jamais négatif). */
        public double remaining() { return Math.max(0, target - current); }

        /** Vrai si l'objectif est atteint ou dépassé. */
        public boolean isCompleted() { return current >= target && target > 0; }

        /** Vrai si la date cible est dépassée et l'objectif non atteint. */
        public boolean isLate() {
            return targetDateMs > 0 && targetDateMs <= System.currentTimeMillis() && !isCompleted();
        }

        /** Vrai si la date cible est dans 2 mois ou moins (et non atteint). */
        public boolean isAtRisk() {
            if (isCompleted() || targetDateMs <= 0) return false;
            long now = System.currentTimeMillis();
            return targetDateMs > now
                && (targetDateMs - now) <= 2L * 30L * 24L * 3600L * 1000L;
        }

        /** Vrai si une date cible est définie et dans le futur. */
        public boolean hasDate() {
            return targetDateMs > 0 && targetDateMs > System.currentTimeMillis();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // EpargneData — résultat global chargé depuis Firestore
    // ─────────────────────────────────────────────────────────────

    public static class EpargneData {
        /** Objectifs d'épargne du foyer. */
        public final List<SavingsGoal> goals;

        /** Historique mensuel des montants épargnés. */
        public final double[] monthHistory;

        /** Labels des mois correspondant à monthHistory. */
        public final String[] monthLabels;

        public EpargneData(List<SavingsGoal> goals,
                           double[] monthHistory,
                           String[] monthLabels) {
            this.goals        = goals;
            this.monthHistory = monthHistory;
            this.monthLabels  = monthLabels;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Constantes — emojis et couleurs disponibles
    // ─────────────────────────────────────────────────────────────

    public static final String[] EMOJIS = {
        "🏠", "✈️", "🚗", "💍", "🎓", "📱", "🏖️", "🎁",
        "💰", "🏥", "🛋️", "🐾", "🎸", "📸", "⛵", "🌍"
    };

    public static final String[] COLORS = {
        "#C0614A", "#2D5A4E", "#8B6914", "#4A6B9A",
        "#7A9E8E", "#9B59B6", "#E67E22", "#27AE60"
    };

    /** Retourne emoji et couleur automatiques basés sur le nom. */
    public static String autoEmoji(String name) {
        if (name == null || name.isEmpty()) return "💰";
        return EMOJIS[Math.abs(name.hashCode()) % EMOJIS.length];
    }

    public static String autoColor(String name) {
        if (name == null || name.isEmpty()) return "#C0614A";
        return COLORS[Math.abs(name.hashCode()) % COLORS.length];
    }
}
