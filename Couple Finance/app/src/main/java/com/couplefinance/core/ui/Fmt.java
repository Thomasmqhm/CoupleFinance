package com.couplefinance.core.ui;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║                   Fmt — Formatters                          ║
 * ║         Toutes les mises en forme de données               ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  RÈGLE : on n'écrit plus jamais String.format("%.2f €", v) ║
 * ║  dans une vue. On appelle Fmt.money(v).                    ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Remplace les 24+ copies de formatMoney() dispersées       ║
 * ║  dans BudgetView, CreditsView, EpargneView, etc.           ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
public final class Fmt {

    private Fmt() {} // Classe utilitaire

    private static final Locale FR = Locale.FRANCE;

    private static final String[] MONTHS_FR = {
        "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
        "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"
    };

    private static final String[] MONTHS_FR_SHORT = {
        "Jan.", "Fév.", "Mar.", "Avr.", "Mai", "Juin",
        "Juil.", "Août", "Sep.", "Oct.", "Nov.", "Déc."
    };

    private static final String[] MONTHS_FR_UPPER = {
        "JANVIER", "FÉVRIER", "MARS", "AVRIL", "MAI", "JUIN",
        "JUILLET", "AOÛT", "SEPTEMBRE", "OCTOBRE", "NOVEMBRE", "DÉCEMBRE"
    };

    // ─────────────────────────────────────────────────────────────
    // ARGENT
    // ─────────────────────────────────────────────────────────────

    /**
     * Formate un montant sans signe.
     * "1 250 €"  ou  "1 250,50 €"
     * Les centimes sont affichés seulement si non nuls.
     */
    public static String money(double value) {
        if (isWhole(value)) {
            return String.format(FR, "%,.0f €", value).replace(',', ' ');
        }
        return String.format(FR, "%,.2f €", value).replace(',', ' ').replaceFirst(" ", ",");
    }

    /**
     * Formate un montant avec signe explicite.
     * "+2 400 €"  ou  "-59,80 €"
     */
    public static String moneySigned(double value) {
        String base = money(Math.abs(value));
        return value >= 0 ? "+" + base : "-" + base;
    }

    /**
     * Formate un montant coloré selon le signe — retourne le String seul,
     * la couleur est à appliquer via DS.INCOME / DS.EXPENSE dans la vue.
     * "+2 400 €"  ou  "-59,80 €"
     */
    public static String moneySignedForDisplay(double value) {
        return moneySigned(value);
    }

    /**
     * Formate un montant court pour les stat boxes (sans décimales si entier).
     * "1 250 €"
     */
    public static String moneyShort(double value) {
        return money(value);
    }

    /**
     * Retourne le String brut d'un montant pour un EditText (sans €).
     * "1250" ou "1250,50"
     */
    public static String moneyInput(double value) {
        if (isWhole(value)) {
            return String.valueOf((int) Math.round(value));
        }
        return String.format(FR, "%.2f", value);
    }

    // ─────────────────────────────────────────────────────────────
    // DATES
    // ─────────────────────────────────────────────────────────────

    /**
     * Retourne le label de mois en MAJUSCULES avec l'année.
     * "MAI 2026"
     *
     * Usage : Fmt.monthLabel() → mois courant
     */
    public static String monthLabel() {
        Calendar c = Calendar.getInstance();
        return MONTHS_FR_UPPER[c.get(Calendar.MONTH)] + " " + c.get(Calendar.YEAR);
    }

    /**
     * Retourne le label de mois pour un timestamp donné.
     * "MAI 2026"
     */
    public static String monthLabel(long timestampMs) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timestampMs);
        return MONTHS_FR_UPPER[c.get(Calendar.MONTH)] + " " + c.get(Calendar.YEAR);
    }

    /**
     * Retourne le nom du mois avec première lettre majuscule.
     * "Mai 2026"
     */
    public static String monthLabelNormal() {
        Calendar c = Calendar.getInstance();
        return MONTHS_FR[c.get(Calendar.MONTH)] + " " + c.get(Calendar.YEAR);
    }

    /**
     * Retourne le mois abrégé.
     * "Mai", "Fév.", "Déc."
     */
    public static String monthShort(int monthIndex) {
        if (monthIndex < 0 || monthIndex > 11) return "";
        return MONTHS_FR_SHORT[monthIndex];
    }

    /**
     * Formate une date relative : "Aujourd'hui", "Hier", "04 mai", "04 mai 2025"
     * Pour les transactions — affichage naturel.
     */
    public static String dateRelative(long timestampMs) {
        if (timestampMs <= 0) return "";

        Calendar now = Calendar.getInstance();
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(timestampMs);

        int todayYear  = now.get(Calendar.YEAR);
        int todayDay   = now.get(Calendar.DAY_OF_YEAR);
        int dateYear   = date.get(Calendar.YEAR);
        int dateDay    = date.get(Calendar.DAY_OF_YEAR);

        if (dateYear == todayYear && dateDay == todayDay)    return "Aujourd'hui";
        if (dateYear == todayYear && dateDay == todayDay - 1) return "Hier";

        String day   = String.format(FR, "%02d", date.get(Calendar.DAY_OF_MONTH));
        String month = MONTHS_FR_SHORT[date.get(Calendar.MONTH)].toLowerCase();

        if (dateYear == todayYear) {
            return day + " " + month;
        }
        return day + " " + month + " " + dateYear;
    }

    /**
     * Formate une date courte : "07/05/2026"
     */
    public static String dateShort(long timestampMs) {
        if (timestampMs <= 0) return "";
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", FR);
        return sdf.format(new Date(timestampMs));
    }

    /**
     * Formate une date longue : "7 mai 2026"
     */
    public static String dateLong(long timestampMs) {
        if (timestampMs <= 0) return "";
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timestampMs);
        int day    = c.get(Calendar.DAY_OF_MONTH);
        String month = MONTHS_FR[c.get(Calendar.MONTH)].toLowerCase();
        int year   = c.get(Calendar.YEAR);
        return day + " " + month + " " + year;
    }

    /**
     * Formate une date de prélèvement : "le 5 du mois"
     */
    public static String dayOfMonth(int day) {
        return "Le " + day + " du mois";
    }

    // ─────────────────────────────────────────────────────────────
    // POURCENTAGES
    // ─────────────────────────────────────────────────────────────

    /**
     * Formate un pourcentage entier : "81%"
     */
    public static String percent(int value) {
        return value + "%";
    }

    /**
     * Calcule et formate un ratio en pourcentage : "81%"
     * Retourne "—" si le diviseur est nul.
     */
    public static String percent(double numerator, double denominator) {
        if (denominator <= 0) return "—";
        int pct = (int) Math.round((numerator / denominator) * 100);
        return pct + "%";
    }

    /**
     * Calcule un ratio en pourcentage (int, non formaté).
     * Utile pour ProgressBar.setProgress().
     * Clampé entre 0 et 100.
     */
    public static int percentInt(double numerator, double denominator) {
        if (denominator <= 0) return 0;
        int pct = (int) Math.round((numerator / denominator) * 100);
        return Math.max(0, Math.min(100, pct));
    }

    /**
     * Retourne la progression du mois courant en %.
     * "23%" si on est au 7e jour d'un mois de 31 jours.
     * Utile pour la barre de progression du budget.
     */
    public static int monthProgressPercent() {
        Calendar c   = Calendar.getInstance();
        int day      = c.get(Calendar.DAY_OF_MONTH);
        int maxDay   = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        return Math.max(1, (int) Math.round((day * 100.0) / maxDay));
    }

    // ─────────────────────────────────────────────────────────────
    // TEXTE
    // ─────────────────────────────────────────────────────────────

    /**
     * Initiale en majuscule d'un nom : "Thomas" → "T"
     */
    public static String initial(String name) {
        if (name == null || name.isEmpty()) return "?";
        return name.substring(0, 1).toUpperCase(FR);
    }

    /**
     * Tronque un texte avec "..." si trop long.
     */
    public static String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 1).trim() + "…";
    }

    /**
     * Retourne "1 membre" ou "2 membres" (pluriel automatique).
     */
    public static String members(int count) {
        return count + (count <= 1 ? " membre" : " membres");
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS INTERNES
    // ─────────────────────────────────────────────────────────────

    /** Vrai si la valeur n'a pas de centimes significatifs */
    private static boolean isWhole(double value) {
        return Math.abs(value - Math.round(value)) < 0.001;
    }
}
