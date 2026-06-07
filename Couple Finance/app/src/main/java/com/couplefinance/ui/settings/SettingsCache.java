package com.couplefinance.ui.settings;

public class SettingsCache {

    private static SettingsModels.State state;

    public static SettingsModels.State get() {
        if (state == null) {
            state = createEmpty();
        }
        return state;
    }

    public static void set(SettingsModels.State newState) {
        if (newState == null) {
            state = createEmpty();
        } else {
            state = newState;
        }
    }

    public static void clear() {
        state = createEmpty();
    }

    private static SettingsModels.State createEmpty() {
        SettingsModels.State s = new SettingsModels.State();

        /*
         * IMPORTANT :
         * Ne jamais créer de membres par défaut ici.
         *
         * Les membres du foyer doivent venir uniquement de Firestore :
         * households/{householdId}/persons
         *
         * Chaque personne crée son propre compte avec son adresse mail.
         * Le cache ne doit jamais inventer Thomas, Mélissa, "Membre 1",
         * ni aucun autre membre fictif.
         */

        s.categories.add(new SettingsModels.Category("Salaire", "income", true));
        s.categories.add(new SettingsModels.Category("Freelance", "income", true));
        s.categories.add(new SettingsModels.Category("Allocations", "income", true));
        s.categories.add(new SettingsModels.Category("Investissements", "income", true));
        s.categories.add(new SettingsModels.Category("Loyer perçu", "income", true));
        s.categories.add(new SettingsModels.Category("Prime", "income", true));

        s.categories.add(new SettingsModels.Category("Alimentation", "expense", true));
        s.categories.add(new SettingsModels.Category("Transports", "expense", true));
        s.categories.add(new SettingsModels.Category("Loisirs", "expense", true));
        s.categories.add(new SettingsModels.Category("Santé", "expense", true));
        s.categories.add(new SettingsModels.Category("Vêtements", "expense", true));
        s.categories.add(new SettingsModels.Category("Vacances", "expense", true));
        s.categories.add(new SettingsModels.Category("Abonnements", "expense", true));
        s.categories.add(new SettingsModels.Category("Restaurants", "expense", true));
        s.charges.add(new SettingsModels.FixedCharge("🏠", "Loyer", "Logement", 1200));
        s.charges.add(new SettingsModels.FixedCharge("⚡", "Électricité / Gaz", "Logement", 85));
        s.charges.add(new SettingsModels.FixedCharge("📶", "Internet", "Logement", 35));
        s.charges.add(new SettingsModels.FixedCharge("🔒", "Assurance habitation", "Logement", 28));
        s.charges.add(new SettingsModels.FixedCharge("🎬", "Netflix", "Loisirs", 18));
        s.charges.add(new SettingsModels.FixedCharge("🎵", "Spotify", "Loisirs", 12));

        return s;
    }
}