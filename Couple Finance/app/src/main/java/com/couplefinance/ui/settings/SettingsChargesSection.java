package com.couplefinance.ui.settings;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;

/**
 * SettingsChargesSection — Vidé intentionnellement.
 *
 * Les charges fixes / abonnements ont été déplacés dans l'onglet dédié
 * AbonnementsView (accessible directement depuis le dashboard).
 *
 * Ce fichier est conservé pour maintenir la compatibilité avec les imports
 * existants dans SettingsView.java (ancien code).
 */
public class SettingsChargesSection {

    private final Activity activity;
    private LinearLayout root;

    public SettingsChargesSection(Activity activity) {
        this.activity = activity;
    }

    public View build() {
        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setVisibility(View.GONE);
        return root;
    }

    public void setVisible(boolean visible) {
        if (root != null) root.setVisibility(View.GONE); // toujours caché
    }
}
