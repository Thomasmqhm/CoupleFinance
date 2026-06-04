package com.couplefinance.ui.home;

/**
 * Registre central des widgets Home.
 *
 * Étapes 6/7/8 : les clés et titres ne sont plus éparpillés dans HomeView.
 * Le dashboard utilise cette classe pour éviter les erreurs de synchronisation
 * entre les préférences, l'organisateur et la bibliothèque de widgets.
 */
public final class HomeWidgetRegistry {
    private HomeWidgetRegistry() {}

    public static final String W_MONTH_PROGRESS = "widget_month_progress";
    public static final String W_STATS = "widget_stats";
    public static final String W_PERSONS = "widget_persons";
    public static final String W_TOP_CATEGORIES = "widget_top_categories";
    public static final String W_BOTTOM_LINE = "widget_bottom_line";
    public static final String W_QUICK_SUMMARY = "widget_quick_summary";
    public static final String W_BUDGET_HEALTH = "widget_budget_health";
    public static final String W_DAILY_BURN = "widget_daily_burn";
    public static final String W_MONTH_FORECAST = "widget_month_forecast";
    public static final String W_BIGGEST_EXPENSE = "widget_biggest_expense";
    public static final String W_SAVINGS_RATE = "widget_savings_rate";
    public static final String W_CATEGORY_COUNT = "widget_category_count";
    public static final String W_INCOME_SOURCES = "widget_income_sources";
    public static final String W_ACTIVITY = "widget_activity";
    public static final String W_DYNAMIC_LIBRARY = "widget_dynamic_library";

    public static String[] getAllWidgetKeys() {
        return new String[] {
                W_MONTH_PROGRESS,
                W_STATS,
                W_BOTTOM_LINE,
                W_PERSONS,
                W_TOP_CATEGORIES,
                W_QUICK_SUMMARY,
                W_BUDGET_HEALTH,
                W_DAILY_BURN,
                W_MONTH_FORECAST,
                W_BIGGEST_EXPENSE,
                W_SAVINGS_RATE,
                W_CATEGORY_COUNT,
                W_INCOME_SOURCES,
                W_ACTIVITY
        };
    }

    public static String[] getAllWidgetTitles() {
        return new String[] {
                "Progression du mois",
                "Cartes revenus / dépenses / épargne",
                "Dernières opérations + calendrier",
                "Cartes personnes",
                "Top 3 catégories",
                "Résumé express",
                "Santé du budget",
                "Rythme journalier",
                "Projection fin de mois",
                "Plus grosse dépense",
                "Taux d'épargne",
                "Nombre de catégories",
                "Sources de revenus",
                "Activité du mois"
        };
    }

    public static String[] getSectionKeys() {
        return new String[] {
                W_MONTH_PROGRESS,
                W_STATS,
                W_BOTTOM_LINE,
                W_PERSONS,
                W_TOP_CATEGORIES,
                W_DYNAMIC_LIBRARY
        };
    }

    public static String[] getSectionTitles() {
        return new String[] {
                "Progression du mois",
                "Vue financière",
                "Opérations + agenda",
                "Qui doit quoi",
                "Répartition dépenses",
                "Widgets intelligents"
        };
    }

    public static String[] getDynamicKeys() {
        return new String[] {
                W_QUICK_SUMMARY,
                W_BUDGET_HEALTH,
                W_DAILY_BURN,
                W_MONTH_FORECAST,
                W_BIGGEST_EXPENSE,
                W_SAVINGS_RATE,
                W_CATEGORY_COUNT,
                W_INCOME_SOURCES,
                W_ACTIVITY
        };
    }

    public static String[] getDynamicTitles() {
        return new String[] {
                "Résumé express",
                "Santé du budget",
                "Rythme journalier",
                "Projection fin de mois",
                "Plus grosse dépense",
                "Taux d'épargne",
                "Nombre de catégories",
                "Sources de revenus",
                "Activité du mois"
        };
    }

    public static boolean contains(String[] values, String value) {
        if (values == null || value == null) return false;
        for (String v : values) {
            if (value.equals(v)) return true;
        }
        return false;
    }

    public static String titleFor(String key, String[] keys, String[] titles) {
        if (key == null || keys == null || titles == null) return String.valueOf(key);
        for (int i = 0; i < keys.length && i < titles.length; i++) {
            if (key.equals(keys[i])) return titles[i];
        }
        return key;
    }
}
