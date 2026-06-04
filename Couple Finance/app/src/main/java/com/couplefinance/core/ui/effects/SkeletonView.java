package com.couplefinance.core.ui.effects;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.R;

/**
 * SkeletonView — Barres de chargement animées (shimmer pulse).
 *
 * Usage :
 *   // Remplacer un TextView pendant le chargement
 *   SkeletonView.pulse(myTextView);
 *
 *   // Créer une barre standalone
 *   View bar = SkeletonView.bar(ctx, widthDp, heightDp);
 *   parent.addView(bar);
 *
 *   // Arrêter l'animation
 *   SkeletonView.stop(myTextView);
 */
public final class SkeletonView {

    private static final String TAG_ANIMATOR = "skeleton_animator";
    private static final int    DURATION_MS  = 900;

    private SkeletonView() {}

    // ── Pulse sur une view existante ──────────────────────────────

    /**
     * Applique l'animation pulse sur n'importe quelle view.
     * Mémorise l'état original (texte, fond) via tag pour pouvoir
     * restaurer avec stop().
     */
    public static void pulse(View view) {
        if (view == null) return;

        // Sauvegarde du fond original
        view.setTag(R.id.tag_skeleton_original_bg, view.getBackground());

        // Fond skeleton
        GradientDrawable bg = buildSkeletonBg(view.getContext());
        view.setBackground(bg);
        view.setAlpha(1f);

        // Animation alpha 0.4 ↔ 1.0
        ValueAnimator animator = ValueAnimator.ofFloat(0.35f, 0.85f);
        animator.setDuration(DURATION_MS);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            view.setAlpha(v);
        });
        animator.start();

        view.setTag(R.id.tag_skeleton_animator, animator);
    }

    /**
     * Arrête l'animation et restaure la view dans son état original.
     */
    public static void stop(View view) {
        if (view == null) return;

        Object animObj = view.getTag(R.id.tag_skeleton_animator);
        if (animObj instanceof ValueAnimator) {
            ((ValueAnimator) animObj).cancel();
        }

        Object bgObj = view.getTag(R.id.tag_skeleton_original_bg);
        if (bgObj instanceof android.graphics.drawable.Drawable) {
            view.setBackground((android.graphics.drawable.Drawable) bgObj);
        } else {
            view.setBackground(null);
        }

        view.setAlpha(1f);
        view.setTag(R.id.tag_skeleton_animator, null);
        view.setTag(R.id.tag_skeleton_original_bg, null);
    }

    /**
     * Arrête l'animation sur toutes les views du tableau.
     */
    public static void stopAll(View... views) {
        for (View v : views) stop(v);
    }

    // ── Barres standalone ─────────────────────────────────────────

    /**
     * Crée une barre skeleton animée à insérer dans un layout.
     *
     * @param widthDp   largeur en dp (-1 = MATCH_PARENT)
     * @param heightDp  hauteur en dp
     */
    public static View bar(Context ctx, int widthDp, int heightDp) {
        View bar = new View(ctx);
        bar.setBackground(buildSkeletonBg(ctx));

        int w = widthDp  == -1 ? LinearLayout.LayoutParams.MATCH_PARENT : DS.dp(ctx, widthDp);
        int h = DS.dp(ctx, heightDp);
        bar.setLayoutParams(new LinearLayout.LayoutParams(w, h));

        animate(bar);
        return bar;
    }

    /**
     * Crée une ligne composée d'une barre skeleton (pratique pour les listes).
     *
     * @param widthPercent  0.0 → 1.0 (ex: 0.6 = 60% du parent)
     */
    public static View barPercent(Context ctx, float widthPercent, int heightDp) {
        View bar = new View(ctx);
        bar.setBackground(buildSkeletonBg(ctx));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, DS.dp(ctx, heightDp), widthPercent);
        bar.setLayoutParams(lp);

        animate(bar);
        return bar;
    }

    // ── Layout skeleton complet pour le dashboard home ────────────

    /**
     * Construit un layout skeleton complet imitant la structure du dashboard home.
     * Affiché pendant loadData(), remplacé par le vrai contenu une fois chargé.
     */
    public static LinearLayout buildHomeSkeleton(Context ctx) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(
                DS.dp(ctx, 22), DS.dp(ctx, 20),
                DS.dp(ctx, 22), DS.dp(ctx, 40)
        );

        // ── Ligne de salutation ───────────────────────────────────
        root.addView(bar(ctx, 140, 14));
        root.addView(spacer(ctx, 10));

        // ── Hero balance ──────────────────────────────────────────
        LinearLayout heroCard = card(ctx, DS.R_XL, DS.dp(ctx, 140));
        LinearLayout heroInner = new LinearLayout(ctx);
        heroInner.setOrientation(LinearLayout.VERTICAL);
        heroInner.setPadding(DS.dp(ctx, 20), DS.dp(ctx, 20),
                DS.dp(ctx, 20), DS.dp(ctx, 20));

        heroInner.addView(barLine(ctx, 100, 12));        // label "Solde du foyer"
        heroInner.addView(spacer(ctx, 12));
        heroInner.addView(barLine(ctx, 200, 36));        // gros chiffre solde
        heroInner.addView(spacer(ctx, 16));

        // Ligne revenus / dépenses
        LinearLayout statsRow = new LinearLayout(ctx);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.addView(barLine(ctx, 90, 14));
        statsRow.addView(spacerH(ctx, 24));
        statsRow.addView(barLine(ctx, 90, 14));
        heroInner.addView(statsRow);

        heroCard.addView(heroInner);
        root.addView(heroCard);
        root.addView(spacer(ctx, 14));

        // ── Stat cards (3 en ligne) ───────────────────────────────
        LinearLayout statCards = new LinearLayout(ctx);
        statCards.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 3; i++) {
            LinearLayout sc = card(ctx, DS.R_LG, DS.dp(ctx, 80));
            LinearLayout inner = new LinearLayout(ctx);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setPadding(DS.dp(ctx, 14), DS.dp(ctx, 14),
                    DS.dp(ctx, 14), DS.dp(ctx, 14));
            inner.addView(barLine(ctx, 50, 10));
            inner.addView(spacer(ctx, 8));
            inner.addView(barLine(ctx, 80, 18));
            sc.addView(inner);

            LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) scLp.leftMargin = DS.dp(ctx, 10);
            statCards.addView(sc, scLp);
        }
        root.addView(statCards);
        root.addView(spacer(ctx, 14));

        // ── Transactions récentes ─────────────────────────────────
        root.addView(barLine(ctx, 160, 14));   // titre section
        root.addView(spacer(ctx, 10));

        for (int i = 0; i < 4; i++) {
            LinearLayout txRow = new LinearLayout(ctx);
            txRow.setOrientation(LinearLayout.HORIZONTAL);
            txRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            txRow.setPadding(0, DS.dp(ctx, 8), 0, DS.dp(ctx, 8));

            // Avatar circle
            View circle = new View(ctx);
            circle.setBackground(buildSkeletonBgCircle(ctx));
            int sz = DS.dp(ctx, 38);
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(sz, sz);
            cLp.rightMargin = DS.dp(ctx, 12);
            txRow.addView(circle, cLp);
            animate(circle);

            // Label + sous-label
            LinearLayout txCol = new LinearLayout(ctx);
            txCol.setOrientation(LinearLayout.VERTICAL);
            txCol.addView(barLine(ctx, (int)(120 + Math.random() * 80), 12));
            txCol.addView(spacer(ctx, 5));
            txCol.addView(barLine(ctx, 70, 10));
            txRow.addView(txCol, new LinearLayout.LayoutParams(0, -2, 1f));

            // Montant
            txRow.addView(barLine(ctx, 60, 14));

            root.addView(txRow);

            // Séparateur
            if (i < 3) {
                View div = new View(ctx);
                div.setBackgroundColor(ThemeColors.divider());
                root.addView(div, new LinearLayout.LayoutParams(-1, DS.dp(ctx, 1)));
            }
        }

        return root;
    }

    // ── Helpers privés ────────────────────────────────────────────

    private static void animate(View view) {
        view.post(() -> {
            ValueAnimator animator = ValueAnimator.ofFloat(0.35f, 0.85f);
            animator.setDuration(DURATION_MS);
            animator.setRepeatMode(ValueAnimator.REVERSE);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a -> view.setAlpha((float) a.getAnimatedValue()));
            animator.start();
            view.setTag(R.id.tag_skeleton_animator, animator);
        });
    }

    private static GradientDrawable buildSkeletonBg(Context ctx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(ThemeColors.divider());
        gd.setCornerRadius(DS.dp(ctx, 6));
        return gd;
    }

    private static GradientDrawable buildSkeletonBgCircle(Context ctx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(ThemeColors.divider());
        return gd;
    }

    /** Barre avec largeur fixe en dp, dans un LinearLayout horizontal. */
    private static View barLine(Context ctx, int widthDp, int heightDp) {
        View bar = new View(ctx);
        bar.setBackground(buildSkeletonBg(ctx));
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                DS.dp(ctx, widthDp), DS.dp(ctx, heightDp)));
        animate(bar);
        return bar;
    }

    private static LinearLayout card(Context ctx, int radiusDp, int minHeightPx) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setMinimumHeight(minHeightPx);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ThemeColors.card());
        bg.setCornerRadius(DS.dp(ctx, radiusDp));
        bg.setStroke(DS.dp(ctx, 1), ThemeColors.border());
        card.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = 0;
        card.setLayoutParams(lp);
        return card;
    }

    private static View spacer(Context ctx, int heightDp) {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, DS.dp(ctx, heightDp)));
        return v;
    }

    private static View spacerH(Context ctx, int widthDp) {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(DS.dp(ctx, widthDp), -1));
        return v;
    }
}
