package com.couplefinance.core.base;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.couplefinance.core.ui.DS;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║               BaseView — Classe mère des vues               ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Toutes les vues de l'app héritent de BaseView.             ║
 * ║  Elle fournit :                                             ║
 * ║    • dp() sans répétition                                   ║
 * ║    • makeScrollRoot() — scroll standard de l'app            ║
 * ║    • makePageRoot() — LinearLayout vertical padded           ║
 * ║    • runOnUi() — raccourci pour runOnUiThread               ║
 * ║    • Accès à activity (protégé)                             ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  USAGE :                                                    ║
 * ║    public class BudgetView extends BaseView {               ║
 * ║        public BudgetView(Activity activity) {               ║
 * ║            super(activity);                                 ║
 * ║        }                                                    ║
 * ║        @Override                                            ║
 * ║        public View getView() {                              ║
 * ║            ScrollView scroll = makeScrollRoot();            ║
 * ║            LinearLayout root = makePageRoot(scroll);        ║
 * ║            root.addView(PageHeader.forBudgets(ctx, ...));   ║
 * ║            ...                                              ║
 * ║            return scroll;                                   ║
 * ║        }                                                    ║
 * ║    }                                                        ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
public abstract class BaseView {

    /** L'activité parente — disponible dans toutes les vues filles. */
    protected final Activity activity;

    protected BaseView(Activity activity) {
        this.activity = activity;
    }

    // ─────────────────────────────────────────────────────────────
    // MÉTHODE PRINCIPALE — à implémenter dans chaque vue
    // ─────────────────────────────────────────────────────────────

    /**
     * Construit et retourne la vue complète de la page.
     * Appelé par DashboardActivity lors de la navigation.
     */
    public abstract View getView();

    // ─────────────────────────────────────────────────────────────
    // UTILITAIRES — Disponibles dans toutes les vues filles
    // ─────────────────────────────────────────────────────────────

    /**
     * Convertit des dp en pixels.
     * Remplace la méthode dp() copiée dans chaque vue.
     *
     * Usage : int padding = dp(DS.PAD_PAGE);
     */
    protected int dp(int value) {
        return DS.dp(activity, value);
    }

    /**
     * ScrollView standard de l'app.
     * Fond DS.BG, fillViewport activé.
     *
     * Usage :
     *   ScrollView scroll = makeScrollRoot();
     *   LinearLayout root = makePageRoot(scroll);
     *   return scroll;
     */
    protected ScrollView makeScrollRoot() {
        ScrollView sv = new ScrollView(activity);
        sv.setFillViewport(true);
        sv.setBackgroundColor(DS.BG);
        return sv;
    }

    /**
     * LinearLayout vertical standard pour le contenu d'une page.
     * Padding horizontal DS.PAD_PAGE, vertical standard.
     * Ajouté automatiquement au scrollRoot fourni.
     *
     * Usage :
     *   ScrollView scroll = makeScrollRoot();
     *   LinearLayout root = makePageRoot(scroll);
     *   // → ajouter les éléments dans root
     *   return scroll;
     */
    protected LinearLayout makePageRoot(ScrollView scrollRoot) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pH = dp(DS.PAD_PAGE);
        root.setPadding(pH, dp(24), pH, dp(42));
        scrollRoot.addView(root, new ScrollView.LayoutParams(-1, -2));
        return root;
    }

    /**
     * Exécute un Runnable sur le thread UI.
     * Raccourci pour activity.runOnUiThread().
     *
     * Usage dans les callbacks Firestore :
     *   runOnUi(() -> tvValue.setText(result));
     */
    protected void runOnUi(Runnable action) {
        activity.runOnUiThread(action);
    }

    /**
     * Retourne le Context de l'activité.
     * Alias lisible pour les appels aux factories.
     */
    protected Activity ctx() {
        return activity;
    }

    // ─────────────────────────────────────────────────────────────
    // LAYOUT PARAMS — Raccourcis courants
    // ─────────────────────────────────────────────────────────────

    /** LayoutParams match_parent × wrap_content */
    protected LinearLayout.LayoutParams lpFull() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    /** LayoutParams match_parent × height_dp */
    protected LinearLayout.LayoutParams lpFullH(int heightDp) {
        return new LinearLayout.LayoutParams(-1, dp(heightDp));
    }

    /** LayoutParams weight × wrap_content */
    protected LinearLayout.LayoutParams lpWeight(float weight) {
        return new LinearLayout.LayoutParams(0, -2, weight);
    }

    /** LayoutParams weight × match_parent */
    protected LinearLayout.LayoutParams lpWeightFull(float weight) {
        return new LinearLayout.LayoutParams(0, -1, weight);
    }

    /**
     * LayoutParams avec marges (top uniquement).
     * Courant pour espacer les sections.
     */
    protected LinearLayout.LayoutParams lpMarginTop(int topDp) {
        LinearLayout.LayoutParams lp = lpFull();
        lp.topMargin = dp(topDp);
        return lp;
    }

    /**
     * LayoutParams avec marges top + bottom.
     */
    protected LinearLayout.LayoutParams lpMargin(int topDp, int bottomDp) {
        LinearLayout.LayoutParams lp = lpFull();
        lp.topMargin    = dp(topDp);
        lp.bottomMargin = dp(bottomDp);
        return lp;
    }
}
