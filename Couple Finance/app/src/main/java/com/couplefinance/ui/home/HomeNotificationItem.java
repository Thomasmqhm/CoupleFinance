package com.couplefinance.ui.home;

/**
 * Modèle d'une notification du dashboard Home.
 *
 * Tous les champs sont {@code public final} pour rester immuables et
 * accessibles directement depuis {@link HomeNotifications} sans getters.
 * Aucune dépendance Android : la classe ne fait que transporter les données
 * (textes + couleurs).
 */
public class HomeNotificationItem {

    /** Petit pictogramme texte affiché à gauche (ex : "✓", "!", "💸"). */
    public final String icon;

    /** Titre court en gras. */
    public final String title;

    /** Description sur 1 à 3 lignes affichée sous le titre. */
    public final String subtitle;

    /** Couleur du texte de l'icône (et accent général). */
    public final int textColor;

    /** Couleur de fond du cercle de l'icône. */
    public final int backgroundColor;

    /** Couleur du contour (stroke) du cercle de l'icône. */
    public final int borderColor;

    public HomeNotificationItem(String icon, String title, String subtitle,
                                int textColor, int backgroundColor, int borderColor) {
        this.icon = icon;
        this.title = title;
        this.subtitle = subtitle;
        this.textColor = textColor;
        this.backgroundColor = backgroundColor;
        this.borderColor = borderColor;
    }
}
