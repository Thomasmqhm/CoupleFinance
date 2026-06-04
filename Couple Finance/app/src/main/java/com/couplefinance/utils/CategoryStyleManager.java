package com.couplefinance.ui.utils;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Style centralisé des catégories.
 *
 * Objectif : un seul endroit pour décider automatiquement :
 * - icône / logo texte ;
 * - couleur principale ;
 * - couleur de fond douce ;
 * - rendu badge premium.
 *
 * Aucune dépendance externe, compatible CodeAssist.
 */
public final class CategoryStyleManager {

    private CategoryStyleManager() { }

    public static final class Style {
        public final String icon;
        public final int color;
        public final int softColor;
        public final int textColor;

        private Style(String icon, String color, String softColor, String textColor) {
            this.icon = icon;
            this.color = Color.parseColor(color);
            this.softColor = Color.parseColor(softColor);
            this.textColor = Color.parseColor(textColor);
        }
    }

    public static Style getStyle(String category) {
        String n = normalize(category);

        if (isEmptyOrNone(n)) return style("•", "#94A3B8", "#F1F5F9", "#475569");

        if (has(n, "revenu", "salaire", "income", "paie", "virement recu"))
            return style("💰", "#059669", "#ECFDF5", "#047857");

        if (has(n, "alimentation", "course", "courses", "supermarche", "epicerie"))
            return style("🛒", "#16A34A", "#F0FDF4", "#15803D");

        if (has(n, "restaurant", "restauration", "fast", "bar", "cafe", "sortie", "sorties"))
            return style("🍽️", "#D97706", "#FFFBEB", "#B45309");

        if (has(n, "tabac", "presse", "relay", "bureau de tabac"))
            return style("🚬", "#92400E", "#FFF7ED", "#92400E");

        if (has(n, "transport", "essence", "carburant", "parking", "peage", "train", "bus", "taxi", "uber"))
            return style("⛽", "#2563EB", "#EFF6FF", "#1D4ED8");

        if (has(n, "auto", "voiture", "credit auto", "diac", "leasing"))
            return style("🚗", "#1D4ED8", "#EFF6FF", "#1E40AF");

        if (has(n, "logement", "loyer", "maison", "habitation", "immobilier"))
            return style("🏠", "#7C3AED", "#F5F3FF", "#6D28D9");

        if (has(n, "edf", "energie", "electricite", "gaz", "eau", "saur", "facture"))
            return style("⚡", "#CA8A04", "#FEFCE8", "#A16207");

        if (has(n, "abonnement", "netflix", "spotify", "amazon prime", "google play", "apple", "disney", "canal"))
            return style("🔁", "#DB2777", "#FDF2F8", "#BE185D");

        if (has(n, "credit", "pret", "emprunt", "mensualite", "bnp", "diac"))
            return style("🏦", "#7F1D1D", "#FEF2F2", "#991B1B");

        if (has(n, "frais bancaire", "frais bancaires", "banque", "commission", "intervention", "suravenir", "cotisation"))
            return style("🏛️", "#4B5563", "#F3F4F6", "#374151");

        if (has(n, "sante", "pharma", "mutuelle", "medecin", "cpam", "apicil"))
            return style("💊", "#DC2626", "#FEF2F2", "#B91C1C");

        if (has(n, "shopping", "vetement", "mode", "chaussure", "achat"))
            return style("🛍️", "#9333EA", "#FAF5FF", "#7E22CE");

        if (has(n, "loisir", "gaming", "jeu", "cinema", "boardgame", "sport"))
            return style("🎮", "#EA580C", "#FFF7ED", "#C2410C");

        if (has(n, "enfant", "ecole", "scolarite", "creche"))
            return style("🧸", "#0891B2", "#ECFEFF", "#0E7490");

        if (has(n, "epargne", "livret", "placement", "objectif"))
            return style("🌱", "#0F766E", "#F0FDFA", "#0F766E");

        if (has(n, "remboursement", "refund", "rembourse"))
            return style("↩️", "#059669", "#ECFDF5", "#047857");

        if (has(n, "cadeau", "don"))
            return style("🎁", "#E11D48", "#FFF1F2", "#BE123C");

        return style("💸", "#64748B", "#F8FAFC", "#475569");
    }

    public static String getIcon(String category) {
        return getStyle(category).icon;
    }

    public static int getColor(String category) {
        return getStyle(category).color;
    }

    public static int getSoftColor(String category) {
        return getStyle(category).softColor;
    }

    public static int getTextColor(String category) {
        return getStyle(category).textColor;
    }

    public static String labelWithIcon(String category) {
        String clean = category == null || category.trim().isEmpty() ? "Sans catégorie" : category.trim();
        if (clean.startsWith("+")) return clean;
        return getIcon(clean) + "  " + clean;
    }

    public static void applySolidBadge(TextView view, String category, int radiusPx, int padHPx, int padVPx) {
        if (view == null) return;
        Style s = getStyle(category);
        view.setText(labelWithIcon(category));
        view.setTextColor(Color.WHITE);
        view.setTypeface(null, Typeface.BOLD);
        view.setPadding(padHPx, padVPx, padHPx, padVPx);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(s.color);
        bg.setCornerRadius(radiusPx);
        view.setBackground(bg);
    }

    public static void applySoftBadge(TextView view, String category, int radiusPx, int padHPx, int padVPx) {
        if (view == null) return;
        Style s = getStyle(category);
        view.setText(labelWithIcon(category));
        view.setTextColor(s.textColor);
        view.setTypeface(null, Typeface.BOLD);
        view.setPadding(padHPx, padVPx, padHPx, padVPx);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(s.softColor);
        bg.setCornerRadius(radiusPx);
        bg.setStroke(Math.max(1, radiusPx / 16), lightenStroke(s.color));
        view.setBackground(bg);
    }

    public static String guessEmoji(String categoryName, boolean isIncome) {
        if (isIncome) return "💰";
        return getIcon(categoryName);
    }

    private static Style style(String icon, String color, String softColor, String textColor) {
        return new Style(icon, color, softColor, textColor);
    }

    private static boolean has(String normalized, String... needles) {
        if (normalized == null) return false;
        for (String n : needles) {
            if (normalized.contains(normalize(n))) return true;
        }
        return false;
    }

    private static boolean isEmptyOrNone(String n) {
        return n == null || n.length() == 0 || n.equals("sans categorie") || n.equals("aucune") || n.equals("autre");
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return n.toLowerCase(Locale.FRANCE).trim();
    }

    private static int lightenStroke(int color) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        r = Math.min(255, (int) (r + (255 - r) * 0.55f));
        g = Math.min(255, (int) (g + (255 - g) * 0.55f));
        b = Math.min(255, (int) (b + (255 - b) * 0.55f));
        return Color.rgb(r, g, b);
    }
}
