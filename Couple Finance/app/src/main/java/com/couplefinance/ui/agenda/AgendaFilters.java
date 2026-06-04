package com.couplefinance.ui.agenda;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  AgendaFilters — État et logique des filtres                ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Centralise :                                               ║
 * ║    • Le filtre actif courant ("Tout voir", "Salaires"...)   ║
 * ║    • Les méthodes de filtrage des listes                    ║
 * ║    • La construction de la liste unifiée triée              ║
 * ║                                                             ║
 * ║  Aucune dépendance Android. 100% testable unitairement.     ║
 * ║  Appelé par : AgendaView                                    ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
public final class AgendaFilters {

    private AgendaFilters() {}

    // ─────────────────────────────────────────────────────────────
    // Filtrage des événements
    // ─────────────────────────────────────────────────────────────

    /**
     * Filtre les événements futurs selon le filtre actif.
     * Exclut les rendez-vous dans "Tout voir" (affichés séparément).
     *
     * @param events      liste complète des événements
     * @param filter      filtre actif (ex: "Tout voir", "Salaires")
     * @param horizonMs   timestamp limite (ne pas dépasser)
     * @param nowMs       timestamp courant
     */
    public static List<AgendaModels.AgendaEvent> filterEvents(
            List<AgendaModels.AgendaEvent> events,
            String filter, long nowMs, long horizonMs) {

        List<AgendaModels.AgendaEvent> result = new ArrayList<>();
        for (AgendaModels.AgendaEvent ev : events) {
            if (ev.dateMs <= nowMs || ev.dateMs > horizonMs) continue;

            boolean isRdv = ev.isRdv();
            boolean match;

            switch (filter) {
                case "Tout voir":     match = !isRdv; break;
                case "Rendez-vous":   match = isRdv;  break;
                default:              match = filterMatchesType(filter, ev.type);
            }
            if (match) result.add(ev);
        }
        return result;
    }

    /**
     * Filtre les transactions futures selon le filtre actif.
     * N'applique des filtres que pour les types financiers.
     */
    public static List<AgendaModels.AgendaTransaction> filterTransactions(
            List<AgendaModels.AgendaTransaction> transactions,
            String filter, long nowMs, long horizonMs) {

        // Les transactions ne s'affichent que pour ces filtres
        boolean txFilterActive = "Tout voir".equals(filter)
            || "Échéances".equals(filter)
            || "Charges fixes".equals(filter)
            || "Salaires".equals(filter);

        if (!txFilterActive) return new ArrayList<>();

        List<AgendaModels.AgendaTransaction> result = new ArrayList<>();
        for (AgendaModels.AgendaTransaction tx : transactions) {
            if (tx.dateMs <= nowMs || tx.dateMs > horizonMs) continue;

            if (!"Tout voir".equals(filter)) {
                if ("Salaires".equals(filter)       && !tx.isIncome()) continue;
                if ("Charges fixes".equals(filter)  && !tx.isFixed())  continue;
                if ("Échéances".equals(filter)      && tx.isIncome())  continue;
            }
            result.add(tx);
        }
        return result;
    }

    /**
     * Filtre uniquement les rendez-vous futurs.
     * Utilisé pour la section "Rendez-vous" en bas à gauche.
     */
    public static List<AgendaModels.AgendaEvent> filterRdv(
            List<AgendaModels.AgendaEvent> events, long nowMs, long horizonMs) {
        List<AgendaModels.AgendaEvent> result = new ArrayList<>();
        for (AgendaModels.AgendaEvent ev : events) {
            if (ev.dateMs > nowMs && ev.dateMs <= horizonMs && ev.isRdv())
                result.add(ev);
        }
        Collections.sort(result, (a, b) -> Long.compare(a.dateMs, b.dateMs));
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // Liste unifiée triée (pour "À venir")
    // ─────────────────────────────────────────────────────────────

    /**
     * Construit la liste unifiée événements + transactions, triée par date.
     * Chaque item est un wrapper avec son type ("ev" ou "tx") pour le rendu.
     */
    public static List<UnifiedItem> buildUnified(
            List<AgendaModels.AgendaEvent> filteredEvents,
            List<AgendaModels.AgendaTransaction> filteredTx) {

        List<UnifiedItem> result = new ArrayList<>();

        for (AgendaModels.AgendaEvent ev : filteredEvents)
            result.add(new UnifiedItem(ev));
        for (AgendaModels.AgendaTransaction tx : filteredTx)
            result.add(new UnifiedItem(tx));

        Collections.sort(result, (a, b) -> Long.compare(a.dateMs(), b.dateMs()));
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // Filtrage pour le calendrier (dots par jour)
    // ─────────────────────────────────────────────────────────────

    /**
     * Retourne les transactions dans une plage de timestamps.
     * Utilisé par AgendaCalendar pour calculer les dots d'un jour.
     */
    public static List<AgendaModels.AgendaTransaction> transactionsInRange(
            List<AgendaModels.AgendaTransaction> all, long from, long to) {
        List<AgendaModels.AgendaTransaction> result = new ArrayList<>();
        for (AgendaModels.AgendaTransaction tx : all)
            if (tx.dateMs >= from && tx.dateMs <= to) result.add(tx);
        return result;
    }

    /**
     * Retourne les événements dans une plage de timestamps.
     */
    public static List<AgendaModels.AgendaEvent> eventsInRange(
            List<AgendaModels.AgendaEvent> all, long from, long to) {
        List<AgendaModels.AgendaEvent> result = new ArrayList<>();
        for (AgendaModels.AgendaEvent ev : all)
            if (ev.dateMs >= from && ev.dateMs <= to) result.add(ev);
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // Comptage des événements d'un mois (pour le sous-titre)
    // ─────────────────────────────────────────────────────────────

    public static int countEventsInMonth(List<AgendaModels.AgendaEvent> events,
                                          Calendar month) {
        Calendar start = (Calendar) month.clone();
        start.set(Calendar.DAY_OF_MONTH, 1);
        start.set(Calendar.HOUR_OF_DAY, 0);
        Calendar end = (Calendar) month.clone();
        end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));
        end.set(Calendar.HOUR_OF_DAY, 23);
        int count = 0;
        for (AgendaModels.AgendaEvent ev : events)
            if (ev.dateMs >= start.getTimeInMillis() && ev.dateMs <= end.getTimeInMillis())
                count++;
        return count;
    }

    // ─────────────────────────────────────────────────────────────
    // UnifiedItem — wrapper pour la liste "À venir"
    // ─────────────────────────────────────────────────────────────

    public static class UnifiedItem {
        public final AgendaModels.AgendaEvent       event;       // null si tx
        public final AgendaModels.AgendaTransaction transaction; // null si event

        public UnifiedItem(AgendaModels.AgendaEvent ev) {
            this.event       = ev;
            this.transaction = null;
        }

        public UnifiedItem(AgendaModels.AgendaTransaction tx) {
            this.event       = null;
            this.transaction = tx;
        }

        public boolean isEvent() { return event != null; }

        public long dateMs() {
            return isEvent() ? event.dateMs : transaction.dateMs;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────

    private static boolean filterMatchesType(String filter, String type) {
        switch (filter) {
            case "Échéances":        return "Échéance".equals(type);
            case "Salaires":         return "Salaire".equals(type);
            case "Charges fixes":    return "Charge fixe".equals(type);
            case "Objectifs":        return "Objectif".equals(type);
            case "Anniversaires":    return "Anniversaire".equals(type);
            case "Rendez-vous":      return "Rendez-vous".equals(type);
            case "Événements perso": return "Événement perso".equals(type);
            default:                 return true;
        }
    }
}
