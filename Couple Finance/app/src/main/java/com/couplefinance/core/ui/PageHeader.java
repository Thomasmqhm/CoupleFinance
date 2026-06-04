package com.couplefinance.core.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║              PageHeader — En-tête de page                   ║
 * ║     Bannière label + titre + boutons d'action               ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Toutes les 9 pages de l'app ont le même header :           ║
 * ║    label terracotta (ex: "BUDGETS · MAI 2026")              ║
 * ║    titre grand (ex: "Le rythme du mois")                    ║
 * ║    boutons optionnels à droite                              ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Usage minimal (sans boutons) :                             ║
 * ║    root.addView(PageHeader.build(ctx,                       ║
 * ║        "BUDGETS · " + Fmt.monthLabel(),                     ║
 * ║        "Le rythme du mois"));                               ║
 * ║                                                             ║
 * ║  Usage avec boutons :                                       ║
 * ║    root.addView(PageHeader.build(ctx,                       ║
 * ║        "BUDGETS · " + Fmt.monthLabel(),                     ║
 * ║        "Le rythme du mois",                                 ║
 * ║        PageHeader.action("Modifier", false, this::onEdit),  ║
 * ║        PageHeader.action("+ Catégorie", true, this::onAdd)  ║
 * ║    ));                                                      ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
public final class PageHeader {

    private PageHeader() {} // Classe utilitaire

    // ─────────────────────────────────────────────────────────────
    // ACTION — Bouton d'action dans le header
    // ─────────────────────────────────────────────────────────────

    /** Définition d'un bouton d'action dans le header. */
    public static class Action {
        public final String   label;
        public final boolean  isPrimary; // true = terracotta, false = secondaire
        public final Runnable onClick;

        private Action(String label, boolean isPrimary, Runnable onClick) {
            this.label     = label;
            this.isPrimary = isPrimary;
            this.onClick   = onClick;
        }
    }

    /**
     * Crée un bouton d'action primaire (terracotta).
     * Ex: action("+ Catégorie", true, this::showCreateDialog)
     */
    public static Action primary(String label, Runnable onClick) {
        return new Action(label, true, onClick);
    }

    /**
     * Crée un bouton d'action secondaire (bordure).
     * Ex: action("Modifier", false, this::showEditDialog)
     */
    public static Action secondary(String label, Runnable onClick) {
        return new Action(label, false, onClick);
    }

    // ─────────────────────────────────────────────────────────────
    // BUILD — Constructeur de l'en-tête
    // ─────────────────────────────────────────────────────────────

    /**
     * Construit l'en-tête de page sans boutons.
     */
    public static View build(Context ctx, String label, String title) {
        return build(ctx, label, title, new Action[0]);
    }

    /**
     * Construit l'en-tête de page avec un ou plusieurs boutons.
     *
     * @param ctx     Contexte Android
     * @param label   Ex: "BUDGETS · MAI 2026" — affiché en terracotta
     * @param title   Ex: "Le rythme du mois" — titre principal
     * @param actions Boutons d'action (1-3 max) affichés à droite
     */
    public static View build(Context ctx, String label, String title, Action... actions) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        // ── Colonne gauche : label + titre ───────────────────────
        LinearLayout titles = new LinearLayout(ctx);
        titles.setOrientation(LinearLayout.VERTICAL);

        TextView tvLabel = UiFactory.pageLabel(ctx, label);
        TextView tvTitle = UiFactory.pageTitle(ctx, title);

        titles.addView(tvLabel);
        titles.addView(tvTitle);

        row.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        // ── Colonne droite : boutons ─────────────────────────────
        if (actions != null && actions.length > 0) {
            LinearLayout btnGroup = new LinearLayout(ctx);
            btnGroup.setOrientation(LinearLayout.HORIZONTAL);
            btnGroup.setGravity(Gravity.CENTER_VERTICAL);

            for (int i = 0; i < actions.length; i++) {
                Action action = actions[i];

                Button btn = action.isPrimary
                    ? UiFactory.btnPrimary(ctx, action.label)
                    : UiFactory.btnSecondary(ctx, action.label);

                int btnWidth  = DS.dp(ctx, labelToWidth(action.label));
                int btnHeight = DS.dp(ctx, DS.BTN_HEIGHT);

                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(btnWidth, btnHeight);
                if (i > 0) bp.leftMargin = DS.dp(ctx, DS.GAP_SM);
                btnGroup.addView(btn, bp);

                if (action.onClick != null) {
                    btn.setOnClickListener(v -> action.onClick.run());
                }
            }

            row.addView(btnGroup);
        }

        return row;
    }

    // ─────────────────────────────────────────────────────────────
    // VARIANTES PRÉDÉFINIES — Headers courants de l'app
    // ─────────────────────────────────────────────────────────────

    /**
     * Header de la page Transactions.
     * "TRANSACTIONS · MAI 2026" / "Transactions"
     * + bouton "Importer relevé" + bouton "+ Nouvelle transaction"
     */
    public static View forTransactions(Context ctx,
                                        Runnable onImport,
                                        Runnable onNew) {
        return build(ctx,
            "TRANSACTIONS · " + Fmt.monthLabel(),
            "Transactions",
            secondary("Importer relevé", onImport),
            primary("+ Nouvelle transaction", onNew)
        );
    }

    /**
     * Header de la page Budgets.
     */
    public static View forBudgets(Context ctx,
                                   Runnable onEdit,
                                   Runnable onAdd) {
        return build(ctx,
            "BUDGETS · " + Fmt.monthLabel(),
            "Le rythme du mois",
            secondary("Modifier", onEdit),
            primary("+ Catégorie", onAdd)
        );
    }

    /**
     * Header de la page Épargne.
     */
    public static View forEpargne(Context ctx, Runnable onNew) {
        return build(ctx,
            "ÉPARGNE · " + Fmt.monthLabel(),
            "Mes objectifs",
            primary("+ Nouvel objectif", onNew)
        );
    }

    /**
     * Header de la page Agenda.
     */
    public static View forAgenda(Context ctx, Runnable onNew) {
        return build(ctx,
            "AGENDA · " + Fmt.monthLabel(),
            "Le mois à venir",
            primary("+ Nouvel événement", onNew)
        );
    }

    /**
     * Header de la page Virements.
     */
    public static View forVirements(Context ctx,
                                     Runnable onBeneficiary,
                                     Runnable onNew) {
        return build(ctx,
            "VIREMENTS · " + Fmt.monthLabel(),
            "Virements",
            secondary("+ Bénéficiaire", onBeneficiary),
            primary("+ Virement", onNew)
        );
    }

    /**
     * Header de la page Crédits.
     */
    public static View forCredits(Context ctx, Runnable onNew) {
        return build(ctx,
            "CRÉDITS · " + Fmt.monthLabel(),
            "Mes crédits",
            primary("+ Nouveau crédit", onNew)
        );
    }

    /**
     * Header de la page Répartition.
     */
    public static View forRepartition(Context ctx, Runnable onRatio) {
        return build(ctx,
            "RÉPARTITION · " + Fmt.monthLabel(),
            "Qui doit combien à qui ?",
            secondary("Modifier le ratio 50/50", onRatio)
        );
    }

    /**
     * Header de la page Paramètres.
     */
    public static View forSettings(Context ctx, Runnable onInvite) {
        return build(ctx,
            "CONFIGURATION",
            "Paramètres du Foyer",
            primary("Inviter un membre", onInvite)
        );
    }

    // ─────────────────────────────────────────────────────────────
    // HELPER INTERNE
    // ─────────────────────────────────────────────────────────────

    /**
     * Estime la largeur dp d'un bouton selon la longueur de son label.
     * Simple heuristique : ~10dp par caractère + padding.
     */
    private static int labelToWidth(String label) {
        if (label == null) return 120;
        int chars = label.length();
        if (chars <= 8)  return 110;
        if (chars <= 14) return 140;
        if (chars <= 20) return 175;
        return 200;
    }
}
