package com.couplefinance.ui.agenda;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  AgendaModels — Modèles de données du package agenda        ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Remplace les String[] ev = { title, type, amount, ... }   ║
 * ║  et les String[] tx = { label, amount, type, ... }         ║
 * ║                                                             ║
 * ║  Utilisé par :                                              ║
 * ║    AgendaParser      → produit List<AgendaEvent/AgendaTx>  ║
 * ║    AgendaRepository  → transmet les données                 ║
 * ║    AgendaFilters     → filtre les listes                    ║
 * ║    AgendaCalendar    → lit les dates pour les dots          ║
 * ║    AgendaDialogs     → crée/supprime des événements         ║
 * ║    AgendaView        → orchestre tout                       ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
public final class AgendaModels {

    private AgendaModels() {}

    // ─────────────────────────────────────────────────────────────
    // AgendaEvent — un événement créé par l'utilisateur
    // ─────────────────────────────────────────────────────────────

    public static class AgendaEvent {
        /** Titre de l'événement. */
        public final String title;
        /** Type : "Échéance", "Salaire", "Charge fixe", "Objectif",
         *  "Anniversaire", "Rendez-vous", "Événement perso". */
        public final String type;
        /** Montant associé (0 si non financier). */
        public final double amount;
        /** Timestamp de l'événement (ms). */
        public final long dateMs;
        /** Membre associé. */
        public final String person;
        /** Note optionnelle. */
        public final String note;
        /** Chemin Firestore complet (pour suppression). */
        public final String docPath;

        public AgendaEvent(String title, String type, double amount, long dateMs,
                           String person, String note, String docPath) {
            this.title   = title;
            this.type    = type;
            this.amount  = amount;
            this.dateMs  = dateMs;
            this.person  = person;
            this.note    = note;
            this.docPath = docPath;
        }

        public boolean isRdv()       { return "Rendez-vous".equals(type); }
        public boolean isFinancial() { return FINANCIAL_TYPES.contains(type); }
        public boolean isIncome()    { return "Salaire".equals(type); }
    }

    // ─────────────────────────────────────────────────────────────
    // AgendaTransaction — une transaction du foyer (pour le calendrier)
    // ─────────────────────────────────────────────────────────────

    public static class AgendaTransaction {
        public final String label;
        public final double amount;
        public final String type;   // "income", "variable", "fixed", "fixed_planned", "fixed_done"
        public final String category;
        public final long   dateMs;

        public AgendaTransaction(String label, double amount, String type,
                                  String category, long dateMs) {
            this.label    = label;
            this.amount   = amount;
            this.type     = type;
            this.category = category;
            this.dateMs   = dateMs;
        }

        public boolean isIncome()    { return "income".equals(type); }
        public boolean isFixed()     {
            return "fixed".equals(type) || "fixed_planned".equals(type) || "fixed_done".equals(type);
        }

        /** Description sans le préfixe "Payer · " */
        public String description() {
            return label.contains(" · ") ? label.substring(label.indexOf(" · ") + 3) : label;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // AgendaData — données complètes chargées pour la page
    // ─────────────────────────────────────────────────────────────

    public static class AgendaData {
        public final java.util.List<AgendaEvent>       events;
        public final java.util.List<AgendaTransaction> transactions;
        public final java.util.List<String>            members;

        public AgendaData(java.util.List<AgendaEvent> events,
                          java.util.List<AgendaTransaction> transactions,
                          java.util.List<String> members) {
            this.events       = events;
            this.transactions = transactions;
            this.members      = members;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Constantes — types d'événements
    // ─────────────────────────────────────────────────────────────

    public static final String[] FILTERS = {
        "Tout voir", "Échéances", "Salaires", "Charges fixes",
        "Objectifs", "Anniversaires", "Rendez-vous", "Événements perso"
    };

    public static final String[] EVENT_TYPES = {
        "Échéance", "Salaire", "Charge fixe", "Objectif",
        "Anniversaire", "Rendez-vous", "Événement perso"
    };

    public static final Set<String> FINANCIAL_TYPES = new HashSet<>(Arrays.asList(
        "Échéance", "Salaire", "Charge fixe", "Objectif"
    ));

    // ─────────────────────────────────────────────────────────────
    // Styles par type d'événement
    // ─────────────────────────────────────────────────────────────

    /** Emoji pour chaque type (même index que EVENT_TYPES). */
    public static final String[] EVENT_ICONS = {
        "🏠", "💼", "🚗", "❤️", "🎂", "📋", "🎁"
    };

    /** Couleur d'icône pour chaque type. */
    public static final int[] EVENT_COLORS = {
        0xFFCC4444, 0xFF22AA66, 0xFF4A6B9A,
        0xFFC0614A, 0xFFE91E8C, 0xFF7B61FF, 0xFF8B6914
    };

    /** Couleur de fond d'icône pour chaque type. */
    public static final int[] EVENT_BG_COLORS = {
        0xFFFFF1F0, 0xFFEFFAF4, 0xFFEEF2FA,
        0xFFFEEEEA, 0xFFFCE4F0, 0xFFF0EEFF, 0xFFFAF3E0
    };

    /**
     * Retourne l'index dans EVENT_TYPES pour un type donné.
     * Retourne -1 si non trouvé.
     */
    public static int typeIndex(String type) {
        for (int i = 0; i < EVENT_TYPES.length; i++)
            if (EVENT_TYPES[i].equals(type)) return i;
        return -1;
    }

    public static String iconForType(String type) {
        int idx = typeIndex(type);
        return idx >= 0 ? EVENT_ICONS[idx] : "📌";
    }

    public static int colorForType(String type) {
        int idx = typeIndex(type);
        return idx >= 0 ? EVENT_COLORS[idx] : 0xFF94A3B8;
    }

    public static int bgColorForType(String type) {
        int idx = typeIndex(type);
        return idx >= 0 ? EVENT_BG_COLORS[idx] : 0xFFF1F5F9;
    }
}
