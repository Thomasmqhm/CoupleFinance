package com.couplefinance.core.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.animations.PressAnimations;
import com.couplefinance.core.ui.effects.GradientFactory;

/**
 * PremiumEmptyState — États vides illustrés pour CoupleFinance.
 *
 * Chaque état vide a :
 *  • Une illustration SVG dessinée sur Canvas (pas de ressource externe)
 *  • Un titre contextuel
 *  • Un sous-titre encourageant
 *  • Une action optionnelle (CTA)
 *
 * Illustrations disponibles :
 *  • TRANSACTIONS  — deux silhouettes + flèche de transfert
 *  • BUDGET        — balance / pièces
 *  • SAVINGS       — tirelire / objectif
 *  • AGENDA        — calendrier avec étoile
 *  • MEMBERS       — deux personnages
 *  • GENERIC       — cercle avec emoji
 */
public final class PremiumEmptyState {

    public enum Illustration {
        TRANSACTIONS,
        BUDGET,
        SAVINGS,
        AGENDA,
        MEMBERS,
        GENERIC
    }

    private PremiumEmptyState() {}

    // ─────────────────────────────────────────────────────────────
    // Fabriques principales
    // ─────────────────────────────────────────────────────────────

    /** Compatibilité ascendante — ancien appel sans illustration. */
    public static LinearLayout create(Context ctx, String title, String subtitle) {
        return create(ctx, "", title, subtitle, null);
    }

    public static LinearLayout create(Context ctx, String icon, String title, String subtitle) {
        return create(ctx, icon, title, subtitle, null);
    }

    public static LinearLayout create(Context ctx, String icon, String title,
                                       String subtitle, View action) {
        // Si pas d'illustration spécifique demandée, on utilise l'emoji
        return buildWithEmoji(ctx, icon, title, subtitle, action);
    }

    // ─────────────────────────────────────────────────────────────
    // Nouvelles fabriques illustrées
    // ─────────────────────────────────────────────────────────────

    /** Crée un état vide avec illustration Canvas. */
    public static LinearLayout illustrated(Context ctx,
                                            Illustration type,
                                            String title,
                                            String subtitle) {
        return illustrated(ctx, type, title, subtitle, null, null);
    }

    public static LinearLayout illustrated(Context ctx,
                                            Illustration type,
                                            String title,
                                            String subtitle,
                                            String actionLabel,
                                            View.OnClickListener actionListener) {
        LinearLayout card = PremiumCard.standard(ctx);
        card.setGravity(Gravity.CENTER);
        card.setPadding(
                DS.dp(ctx, 28), DS.dp(ctx, 36),
                DS.dp(ctx, 28), DS.dp(ctx, 36)
        );

        // Illustration Canvas
        IllustrationView illustration = new IllustrationView(ctx, type);
        int size = DS.dp(ctx, 100);
        LinearLayout.LayoutParams ilLp = new LinearLayout.LayoutParams(size, size);
        ilLp.bottomMargin = DS.dp(ctx, 20);
        ilLp.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(illustration, ilLp);

        // Titre
        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(title);
        tvTitle.setTextColor(ThemeColors.text());
        tvTitle.setTextSize(DS.TEXT_SECTION);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        card.addView(tvTitle);

        // Sous-titre
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView tvSub = new TextView(ctx);
            tvSub.setText(subtitle);
            tvSub.setTextColor(ThemeColors.subtext());
            tvSub.setTextSize(DS.TEXT_SM);
            tvSub.setGravity(Gravity.CENTER);
            tvSub.setLineSpacing(3f, 1.05f);

            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
            subLp.topMargin = DS.dp(ctx, 8);
            card.addView(tvSub, subLp);
        }

        // CTA optionnel
        if (actionLabel != null && actionListener != null) {
            TextView action = PremiumChip.active(ctx, actionLabel);
            action.setOnClickListener(actionListener);
            PressAnimations.applySoft(action);

            LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(-2, DS.dp(ctx, 46));
            actionLp.topMargin  = DS.dp(ctx, 20);
            actionLp.gravity    = Gravity.CENTER_HORIZONTAL;
            card.addView(action, actionLp);
        }

        return card;
    }

    // ── États vides prédéfinis ────────────────────────────────────

    /** "Aucune transaction ce mois-ci." */
    public static LinearLayout noTransactions(Context ctx, View.OnClickListener addAction) {
        return illustrated(ctx,
                Illustration.TRANSACTIONS,
                "Aucune transaction",
                "Ajoutez votre première dépense\nou votre premier revenu du mois.",
                addAction != null ? "+ Ajouter" : null,
                addAction);
    }

    /** "Aucun budget configuré." */
    public static LinearLayout noBudget(Context ctx, View.OnClickListener setupAction) {
        return illustrated(ctx,
                Illustration.BUDGET,
                "Pas encore de budget",
                "Définissez des budgets par catégorie\npour mieux gérer vos dépenses communes.",
                setupAction != null ? "Configurer" : null,
                setupAction);
    }

    /** "Aucun objectif d'épargne." */
    public static LinearLayout noSavings(Context ctx, View.OnClickListener addAction) {
        return illustrated(ctx,
                Illustration.SAVINGS,
                "Aucun objectif",
                "Créez votre premier objectif d'épargne commun\n— vacances, projet, sécurité.",
                addAction != null ? "+ Créer" : null,
                addAction);
    }

    /** "Aucune charge fixe." */
    public static LinearLayout noCharges(Context ctx, View.OnClickListener addAction) {
        return illustrated(ctx,
                Illustration.AGENDA,
                "Aucune charge fixe",
                "Ajoutez vos charges récurrentes\n— loyer, abonnements, crédits.",
                addAction != null ? "+ Ajouter" : null,
                addAction);
    }

    /** "Aucun virement." */
    public static LinearLayout noVirements(Context ctx) {
        return illustrated(ctx,
                Illustration.TRANSACTIONS,
                "Aucun virement",
                "Les virements entre membres\napparaîtront ici.");
    }

    /** "Aucun membre." */
    public static LinearLayout noMembers(Context ctx) {
        return illustrated(ctx,
                Illustration.MEMBERS,
                "Foyer incomplet",
                "Invitez votre partenaire avec\nle code de votre foyer.");
    }

    // ─────────────────────────────────────────────────────────────
    // Compat legacy
    // ─────────────────────────────────────────────────────────────

    public static LinearLayout compact(Context ctx, String title, String subtitle) {
        LinearLayout card = create(ctx, "", title, subtitle, null);
        card.setPadding(DS.dp(ctx, 18), DS.dp(ctx, 22), DS.dp(ctx, 18), DS.dp(ctx, 22));
        return card;
    }

    public static LinearLayout withAction(Context ctx, String icon, String title,
                                           String subtitle, String actionText,
                                           View.OnClickListener listener) {
        TextView action = PremiumChip.active(ctx, actionText);
        action.setOnClickListener(listener);
        return create(ctx, icon, title, subtitle, action);
    }

    // ─────────────────────────────────────────────────────────────
    // Builder interne avec emoji (legacy)
    // ─────────────────────────────────────────────────────────────

    private static LinearLayout buildWithEmoji(Context ctx, String icon, String title,
                                                String subtitle, View action) {
        LinearLayout card = PremiumCard.standard(ctx);
        card.setGravity(Gravity.CENTER);
        card.setPadding(DS.dp(ctx, 24), DS.dp(ctx, 34), DS.dp(ctx, 24), DS.dp(ctx, 34));

        if (icon != null && !icon.trim().isEmpty()) {
            TextView tvIcon = new TextView(ctx);
            tvIcon.setText(icon);
            tvIcon.setTextSize(28f);
            tvIcon.setGravity(Gravity.CENTER);
            tvIcon.setBackground(GradientFactory.circle(ThemeColors.primarySoft()));

            int size = DS.dp(ctx, 58);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(size, size);
            iconLp.bottomMargin = DS.dp(ctx, 14);
            iconLp.gravity = Gravity.CENTER_HORIZONTAL;
            card.addView(tvIcon, iconLp);
        }

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(title);
        tvTitle.setTextColor(ThemeColors.text());
        tvTitle.setTextSize(DS.TEXT_SECTION);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        card.addView(tvTitle);

        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView tvSub = new TextView(ctx);
            tvSub.setText(subtitle);
            tvSub.setTextColor(ThemeColors.subtext());
            tvSub.setTextSize(DS.TEXT_SM);
            tvSub.setGravity(Gravity.CENTER);
            tvSub.setLineSpacing(2f, 1.05f);

            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
            subLp.topMargin = DS.dp(ctx, 8);
            card.addView(tvSub, subLp);
        }

        if (action != null) {
            LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(-2, DS.dp(ctx, 46));
            actionLp.topMargin = DS.dp(ctx, 18);
            actionLp.gravity   = Gravity.CENTER_HORIZONTAL;
            card.addView(action, actionLp);
            PressAnimations.applySoft(action);
        }

        return card;
    }

    // ─────────────────────────────────────────────────────────────
    // IllustrationView — dessin Canvas
    // ─────────────────────────────────────────────────────────────

    private static class IllustrationView extends View {

        private final Illustration type;
        private final Paint paint;
        private final Paint paintStroke;
        private final Paint paintAccent;
        private final Paint paintBg;

        IllustrationView(Context ctx, Illustration type) {
            super(ctx);
            this.type = type;

            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ThemeColors.primary());

            paintStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            paintStroke.setStyle(Paint.Style.STROKE);
            paintStroke.setStrokeWidth(6f);
            paintStroke.setStrokeCap(Paint.Cap.ROUND);
            paintStroke.setColor(ThemeColors.primary());

            paintAccent = new Paint(Paint.ANTI_ALIAS_FLAG);
            paintAccent.setStyle(Paint.Style.FILL);
            paintAccent.setColor(ThemeColors.success());

            paintBg = new Paint(Paint.ANTI_ALIAS_FLAG);
            paintBg.setStyle(Paint.Style.FILL);
            paintBg.setColor(ThemeColors.primarySoft());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;

            // Fond circulaire doux
            canvas.drawCircle(cx, cy, Math.min(w, h) / 2f * 0.95f, paintBg);

            switch (type) {
                case TRANSACTIONS: drawTransactions(canvas, w, h, cx, cy); break;
                case BUDGET:       drawBudget(canvas, w, h, cx, cy);       break;
                case SAVINGS:      drawSavings(canvas, w, h, cx, cy);      break;
                case AGENDA:       drawAgenda(canvas, w, h, cx, cy);       break;
                case MEMBERS:      drawMembers(canvas, w, h, cx, cy);      break;
                default:           drawGeneric(canvas, w, h, cx, cy);      break;
            }
        }

        /** Deux ronds + flèche entre eux (transactions). */
        private void drawTransactions(Canvas c, int w, int h, float cx, float cy) {
            float r = w * 0.14f;
            float gap = w * 0.22f;

            // Rond gauche (personne 1)
            paint.setColor(ThemeColors.primary());
            c.drawCircle(cx - gap, cy, r, paint);

            // Rond droit (personne 2)
            paint.setColor(ThemeColors.withAlpha(ThemeColors.primary(), 180));
            c.drawCircle(cx + gap, cy, r, paint);

            // Flèche centrale
            paintStroke.setColor(ThemeColors.success());
            paintStroke.setStrokeWidth(5f);
            float arrowY = cy;
            float x1 = cx - gap + r + 6f;
            float x2 = cx + gap - r - 6f;
            c.drawLine(x1, arrowY, x2, arrowY, paintStroke);

            // Pointe de flèche droite
            Path arrow = new Path();
            arrow.moveTo(x2, arrowY);
            arrow.lineTo(x2 - 16f, arrowY - 12f);
            arrow.moveTo(x2, arrowY);
            arrow.lineTo(x2 - 16f, arrowY + 12f);
            c.drawPath(arrow, paintStroke);

            // Labels "€" dans les ronds
            Paint textP = new Paint(Paint.ANTI_ALIAS_FLAG);
            textP.setColor(Color.WHITE);
            textP.setTextSize(r * 0.9f);
            textP.setTextAlign(Paint.Align.CENTER);
            textP.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText("€", cx - gap, cy + r * 0.35f, textP);
            c.drawText("€", cx + gap, cy + r * 0.35f, textP);
        }

        /** Balance / balance (budget). */
        private void drawBudget(Canvas c, int w, int h, float cx, float cy) {
            float r = w * 0.10f;
            float armLen = w * 0.30f;

            // Barre centrale
            paint.setColor(ThemeColors.primary());
            RectF bar = new RectF(cx - 4f, cy - h * 0.26f, cx + 4f, cy + 4f);
            c.drawRoundRect(bar, 4f, 4f, paint);

            // Bras gauche
            paintStroke.setColor(ThemeColors.primary());
            paintStroke.setStrokeWidth(4f);
            c.drawLine(cx, cy - h * 0.26f, cx - armLen, cy - h * 0.10f, paintStroke);
            c.drawLine(cx, cy - h * 0.26f, cx + armLen, cy - h * 0.10f, paintStroke);

            // Plateaux
            c.drawCircle(cx - armLen, cy - h * 0.08f, r, paint);
            paint.setColor(ThemeColors.success());
            c.drawCircle(cx + armLen, cy - h * 0.08f, r * 0.75f, paint);

            // Pied
            RectF base = new RectF(cx - w * 0.18f, cy + 6f, cx + w * 0.18f, cy + 18f);
            paint.setColor(ThemeColors.primary());
            c.drawRoundRect(base, 8f, 8f, paint);

            // Symbole € dans le plateau gauche
            Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
            tp.setColor(Color.WHITE);
            tp.setTextSize(r * 0.9f);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText("€", cx - armLen, cy - h * 0.08f + r * 0.35f, tp);
        }

        /** Tirelire / objectif (savings). */
        private void drawSavings(Canvas c, int w, int h, float cx, float cy) {
            float rBig = w * 0.28f;

            // Corps tirelire
            paint.setColor(ThemeColors.primary());
            c.drawCircle(cx, cy + 6f, rBig, paint);

            // Oreille
            paint.setColor(ThemeColors.withAlpha(ThemeColors.primary(), 200));
            c.drawCircle(cx + rBig * 0.9f, cy - rBig * 0.3f, rBig * 0.22f, paint);

            // Fente
            paintStroke.setColor(ThemeColors.withAlpha(ThemeColors.primaryDark(), 200));
            paintStroke.setStrokeWidth(5f);
            c.drawLine(cx - 14f, cy - rBig * 0.6f, cx + 14f, cy - rBig * 0.6f, paintStroke);

            // Pied
            paint.setColor(ThemeColors.primaryDark());
            c.drawRoundRect(new RectF(cx - 22f, cy + rBig - 4f, cx - 6f, cy + rBig + 14f),
                    4f, 4f, paint);
            c.drawRoundRect(new RectF(cx + 6f, cy + rBig - 4f, cx + 22f, cy + rBig + 14f),
                    4f, 4f, paint);

            // Pièce au-dessus
            paintAccent.setColor(ThemeColors.success());
            c.drawCircle(cx, cy - rBig * 0.6f - 20f, 14f, paintAccent);
            Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
            tp.setColor(Color.WHITE);
            tp.setTextSize(16f);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText("€", cx, cy - rBig * 0.6f - 14f, tp);
        }

        /** Calendrier avec étoile (agenda). */
        private void drawAgenda(Canvas c, int w, int h, float cx, float cy) {
            float cardW = w * 0.62f;
            float cardH = h * 0.60f;
            float left  = cx - cardW / 2f;
            float top   = cy - cardH / 2f;

            // Corps du calendrier
            paint.setColor(ThemeColors.card());
            c.drawRoundRect(new RectF(left, top, left + cardW, top + cardH), 12f, 12f, paint);

            // En-tête coloré
            paint.setColor(ThemeColors.primary());
            c.drawRoundRect(new RectF(left, top, left + cardW, top + cardH * 0.34f), 12f, 12f, paint);
            // Carrés bas du header pour effacer les coins inférieurs ronds
            c.drawRect(left, top + cardH * 0.20f, left + cardW, top + cardH * 0.34f, paint);

            // Anneaux
            paint.setColor(ThemeColors.primaryDark());
            float ringY = top + 2f;
            c.drawRoundRect(new RectF(cx - cardW * 0.22f - 5f, ringY, cx - cardW * 0.22f + 5f, ringY + 20f), 4f, 4f, paint);
            c.drawRoundRect(new RectF(cx + cardW * 0.22f - 5f, ringY, cx + cardW * 0.22f + 5f, ringY + 20f), 4f, 4f, paint);

            // Grille de jours (6 pts 2×3)
            paint.setColor(ThemeColors.border());
            float dotR = 5f;
            float gx   = left + cardW * 0.18f;
            float gy   = top  + cardH * 0.52f;
            float step = cardW * 0.22f;
            for (int row = 0; row < 2; row++) {
                for (int col = 0; col < 3; col++) {
                    c.drawCircle(gx + col * step, gy + row * step, dotR, paint);
                }
            }

            // Étoile dorée (jour important)
            paint.setColor(ThemeColors.warning());
            c.drawCircle(gx + 2 * step, gy, dotR * 1.6f, paint);
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setColor(Color.WHITE);
            sp.setTextSize(13f);
            sp.setTextAlign(Paint.Align.CENTER);
            c.drawText("★", gx + 2 * step, gy + 5f, sp);
        }

        /** Deux personnages (members). */
        private void drawMembers(Canvas c, int w, int h, float cx, float cy) {
            float gap = w * 0.18f;

            // Personne 1 (gauche, terracotta)
            drawPerson(c, cx - gap, cy, ThemeColors.primary(), 0.85f);
            // Personne 2 (droite, vert)
            drawPerson(c, cx + gap, cy, ThemeColors.success(), 1.0f);

            // Cœur entre les deux
            paint.setColor(ThemeColors.danger());
            Paint heartP = new Paint(Paint.ANTI_ALIAS_FLAG);
            heartP.setColor(ThemeColors.danger());
            heartP.setStyle(Paint.Style.FILL);
            drawHeart(c, cx, cy - h * 0.15f, 14f, heartP);
        }

        private void drawPerson(Canvas c, float x, float cy, int color, float scale) {
            float headR  = 18f * scale;
            float bodyH  = 26f * scale;
            float bodyW  = 28f * scale;
            float headY  = cy - bodyH * 0.5f - headR;
            float bodyTop = cy - bodyH * 0.5f;

            paint.setColor(color);
            c.drawCircle(x, headY, headR, paint);

            RectF body = new RectF(x - bodyW / 2f, bodyTop,
                    x + bodyW / 2f, bodyTop + bodyH);
            c.drawRoundRect(body, 10f, 10f, paint);
        }

        private void drawHeart(Canvas c, float cx, float cy, float size, Paint p) {
            Path path = new Path();
            path.moveTo(cx, cy + size * 0.7f);
            path.cubicTo(cx - size * 1.5f, cy + size * 0.2f,
                    cx - size * 1.5f, cy - size * 0.8f,
                    cx, cy - size * 0.1f);
            path.cubicTo(cx + size * 1.5f, cy - size * 0.8f,
                    cx + size * 1.5f, cy + size * 0.2f,
                    cx, cy + size * 0.7f);
            c.drawPath(path, p);
        }

        /** Générique — emoji sur fond coloré. */
        private void drawGeneric(Canvas c, int w, int h, float cx, float cy) {
            paint.setColor(ThemeColors.primary());
            c.drawCircle(cx, cy, w * 0.28f, paint);

            Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
            tp.setColor(Color.WHITE);
            tp.setTextSize(w * 0.26f);
            tp.setTextAlign(Paint.Align.CENTER);
            c.drawText("✓", cx, cy + w * 0.10f, tp);
        }

        private int primaryDark() {
            return ThemeColors.primaryDark();
        }
    }
}
