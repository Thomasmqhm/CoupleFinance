package com.couplefinance.ui.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.effects.ElevationSystem;

/**
 * HomeDashboardStyle — Système de style premium CoupleFinance
 *
 * Direction artistique : Apple Wallet · iOS 18 · Revolut · Monzo
 *
 * Principes :
 * - Glassmorphism léger sur surfaces claires
 * - Coins très arrondis (RADIUS_2XL = 36dp par défaut)
 * - Ombres douces colorées
 * - Micro-interactions premium sur press
 * - Animations d'apparition fluides avec stagger
 * - Gradients subtils sur hero cards
 * - Typographie moderne avec letter-spacing
 */
public final class HomeDashboardStyle {

    private HomeDashboardStyle() {}

    // ─────────────────────────────────────────────────────────────
    // DRAWABLES — Surfaces premium
    // ─────────────────────────────────────────────────────────────

    /**
     * Surface standard flottante — card principale du dashboard
     * Fond blanc/clair + bord verre subtil
     */
    public static GradientDrawable card(Context context) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        ThemeColors.blend(ThemeColors.surfaceFloating(), Color.WHITE, 0.15f),
                        ThemeColors.surfaceFloating()
                }
        );
        d.setCornerRadius(DS.dp(context, DS.RADIUS_2XL));
        d.setStroke(DS.dp(context, 1), ThemeColors.withAlpha(Color.WHITE, 180));
        return d;
    }

    /**
     * Surface glass — effet glassmorphism avec accent coloré
     * Utilisé pour les widgets spéciaux et hero sections secondaires
     */
    public static GradientDrawable glass(Context context, int accent) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        ThemeColors.withAlpha(Color.WHITE, 242),
                        ThemeColors.withAlpha(accent, 18),
                        ThemeColors.withAlpha(Color.WHITE, 220)
                }
        );
        d.setCornerRadius(DS.dp(context, DS.RADIUS_2XL));
        d.setStroke(DS.dp(context, 1), ThemeColors.withAlpha(Color.WHITE, 160));
        return d;
    }

    /**
     * Pill — bouton, badge ou tag arrondi
     */
    public static GradientDrawable pill(Context context, int backgroundColor, int borderColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(backgroundColor);
        d.setCornerRadius(DS.dp(context, DS.RADIUS_PILL));
        if (borderColor != Color.TRANSPARENT) {
            d.setStroke(DS.dp(context, 1), borderColor);
        }
        return d;
    }

    /**
     * Cercle plein — avatar, dot indicator
     */
    public static GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setShape(GradientDrawable.OVAL);
        return d;
    }

    /**
     * Background widget — dégradé subtil avec accent de couleur thème
     * Style iOS 18 / Revolut widget premium
     */
    public static GradientDrawable widgetBackground(Context context, int accent) {
        int base   = ThemeColors.surfaceFloating();
        int tinted = ThemeColors.blend(base, accent, 0.06f);
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        ThemeColors.blend(tinted, Color.WHITE, 0.12f),
                        tinted,
                        ThemeColors.blend(tinted, Color.BLACK, 0.03f)
                }
        );
        d.setCornerRadius(DS.dp(context, DS.RADIUS_2XL));
        d.setStroke(DS.dp(context, 1), ThemeColors.withAlpha(accent, 32));
        return d;
    }

    /**
     * Row de widget actif/inactif — sélection, option cochée
     */
    public static GradientDrawable widgetRow(Context context, boolean active) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(active
                ? ThemeColors.blend(ThemeColors.primaryMuted(), Color.WHITE, 0.08f)
                : ThemeColors.surfaceFloating());
        d.setCornerRadius(DS.dp(context, DS.RADIUS_LG));
        d.setStroke(
                DS.dp(context, 1),
                active ? ThemeColors.withAlpha(ThemeColors.primary(), 55) : ThemeColors.borderSoft()
        );
        return d;
    }

    /**
     * Modal card — bottom sheet, dialog premium
     */
    public static GradientDrawable modalCard(Context context) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(ThemeColors.blend(ThemeColors.surfaceFloating(), Color.WHITE, 0.20f));
        d.setCornerRadius(DS.dp(context, DS.SHEET_RADIUS));
        d.setStroke(DS.dp(context, 1), ThemeColors.withAlpha(Color.WHITE, 140));
        return d;
    }

    /**
     * Toast gradient — notification premium
     */
    public static GradientDrawable toastGradient(Context context) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        ThemeColors.blend(ThemeColors.heroGradientStart(), Color.WHITE, 0.06f),
                        ThemeColors.heroGradientMiddle(),
                        ThemeColors.blend(ThemeColors.heroGradientEnd(), Color.BLACK, 0.05f)
                }
        );
        d.setCornerRadius(DS.dp(context, DS.RADIUS_XL));
        d.setStroke(DS.dp(context, 1), ThemeColors.withAlpha(Color.WHITE, 80));
        return d;
    }

    /**
     * Hero gradient — carte principale du solde commun
     * Gradient riche avec profondeur inspiré Apple Wallet
     */
    public static GradientDrawable heroGradient(Context context) {
        // N26 épuré : bloc de marque propre, dégradé diagonal 2 tons primary → primaryDark,
        // sans liseré blanc dur. Met le grand solde en valeur sans bruit visuel.
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        ThemeColors.primary(),
                        ThemeColors.primaryDark()
                }
        );
        d.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        d.setCornerRadius(DS.dp(context, DS.RADIUS_2XL));
        return d;
    }

    /**
     * Hero surface subtile — hero section secondaire
     * Dégradé doux fond vers primary
     */
    public static GradientDrawable heroSoftGradient(Context context) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        ThemeColors.heroSoftGradientStart(),
                        ThemeColors.heroSoftGradientEnd()
                }
        );
        d.setCornerRadius(DS.dp(context, DS.RADIUS_2XL));
        d.setStroke(DS.dp(context, 1), ThemeColors.withAlpha(ThemeColors.primary(), 28));
        return d;
    }

    /**
     * Inset soft — zone en retrait, input-like, fond doux
     */
    public static GradientDrawable softInset(Context context) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(ThemeColors.surfaceSoft());
        d.setCornerRadius(DS.dp(context, DS.RADIUS_XL));
        d.setStroke(DS.dp(context, 1), ThemeColors.borderSoft());
        return d;
    }

    /**
     * Progress track — barre de progression fond
     */
    public static GradientDrawable progressTrack(Context context) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(ThemeColors.withAlpha(ThemeColors.border(), 60));
        d.setCornerRadius(DS.dp(context, DS.RADIUS_PILL));
        return d;
    }

    /**
     * Progress fill — barre de progression remplie avec gradient
     */
    public static GradientDrawable progressFill(Context context, int color) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        ThemeColors.blend(color, Color.WHITE, 0.22f),
                        color,
                        ThemeColors.blend(color, Color.BLACK, 0.08f)
                }
        );
        d.setCornerRadius(DS.dp(context, DS.RADIUS_PILL));
        return d;
    }

    /**
     * Section chip — étiquette de section premium
     */
    public static GradientDrawable sectionChip(Context context) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(ThemeColors.primaryMuted());
        d.setCornerRadius(DS.dp(context, DS.RADIUS_PILL));
        d.setStroke(DS.dp(context, 1), ThemeColors.withAlpha(ThemeColors.primary(), 38));
        return d;
    }

    /**
     * Avatar gradient — fond avatar circulaire premium
     */
    public static GradientDrawable avatarGradient(int baseColor) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        ThemeColors.blend(baseColor, Color.WHITE, 0.18f),
                        baseColor,
                        ThemeColors.blend(baseColor, Color.BLACK, 0.14f)
                }
        );
        d.setShape(GradientDrawable.OVAL);
        return d;
    }

    /**
     * Icon container — fond icône circular avec accent
     */
    public static GradientDrawable iconContainer(Context context, int accent) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(ThemeColors.withAlpha(accent, 22));
        d.setStroke(DS.dp(context, 1), ThemeColors.withAlpha(accent, 44));
        return d;
    }

    /**
     * Card transaction — fond card pour lignes de transactions
     */
    public static GradientDrawable transactionCard(Context context, int mainColor, boolean isIncome) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        ThemeColors.blend(ThemeColors.surfaceFloating(), Color.WHITE, 0.18f),
                        ThemeColors.surfaceFloating()
                }
        );
        d.setCornerRadius(DS.dp(context, DS.RADIUS_XL));
        d.setStroke(DS.dp(context, 1), ThemeColors.withAlpha(mainColor, 22));
        return d;
    }

    /**
     * Empty state container — fond état vide
     */
    public static GradientDrawable emptyStateCard(Context context) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(ThemeColors.blend(ThemeColors.surfaceFloating(), ThemeColors.background(), 0.40f));
        d.setCornerRadius(DS.dp(context, DS.RADIUS_2XL));
        d.setStroke(DS.dp(context, 1), ThemeColors.withAlpha(ThemeColors.border(), 55));
        return d;
    }

    // ─────────────────────────────────────────────────────────────
    // COULEURS
    // ─────────────────────────────────────────────────────────────

    public static int balanceColor(double balance, boolean overdraftDefined, double overdraftLimit) {
        if (!overdraftDefined) {
            return balance < 0 ? ThemeColors.danger() : ThemeColors.textPrimary();
        }
        if (balance >= 0)                        return ThemeColors.textPrimary();
        if (balance >= -Math.abs(overdraftLimit)) return ThemeColors.primary();
        return ThemeColors.danger();
    }

    // ─────────────────────────────────────────────────────────────
    // TYPOGRAPHIE premium
    // ─────────────────────────────────────────────────────────────

    /** Solde hero — très grand, bold, white, tracking serré */
    public static void premiumBalance(TextView tv) {
        if (tv == null) return;
        tv.setTextColor(Color.WHITE);
        tv.setAlpha(1f);
        tv.setTextSize(46f);
        tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tv.setLetterSpacing(-0.04f);
        tv.setIncludeFontPadding(false);
    }

    /** Titre de page/section — grande, bold */
    public static void premiumTitle(TextView tv) {
        if (tv == null) return;
        tv.setTextColor(ThemeColors.textPrimary());
        tv.setTextSize(DS.TEXT_TITLE);
        tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tv.setLetterSpacing(-0.018f);
        tv.setIncludeFontPadding(false);
    }

    /** Titre de section dashboard — 20sp bold */
    public static void premiumSection(TextView tv) {
        if (tv == null) return;
        tv.setTextColor(ThemeColors.textPrimary());
        tv.setTextSize(DS.TEXT_SUBTITLE);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setLetterSpacing(-0.012f);
        tv.setIncludeFontPadding(false);
    }

    /** Stat numérique — valeur chiffrée principale */
    public static void premiumStat(TextView tv) {
        if (tv == null) return;
        tv.setTextColor(ThemeColors.textPrimary());
        tv.setTextSize(DS.TEXT_SUBTITLE);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setLetterSpacing(-0.015f);
        tv.setIncludeFontPadding(false);
    }

    /** Sous-titre / description */
    public static void premiumSubtitle(TextView tv) {
        if (tv == null) return;
        tv.setTextColor(ThemeColors.textSecondary());
        tv.setTextSize(DS.TEXT_BODY_SMALL);
        tv.setIncludeFontPadding(true);
        tv.setLineSpacing(DS.dp(tv.getContext(), 2), 1f);
    }

    /** Label small primary — tag, badge, label coloré */
    public static void premiumSmallPrimary(TextView tv) {
        if (tv == null) return;
        tv.setTextColor(ThemeColors.primary());
        tv.setTextSize(DS.TEXT_MICRO);
        tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tv.setLetterSpacing(0.08f);
        tv.setIncludeFontPadding(false);
    }

    /** Micro texte — caption, date, info secondaire */
    public static void premiumMicro(TextView tv) {
        if (tv == null) return;
        tv.setTextColor(ThemeColors.textMuted());
        tv.setTextSize(DS.TEXT_MICRO);
        tv.setLetterSpacing(0.04f);
        tv.setIncludeFontPadding(false);
    }

    /** Label uppercase de widget — catégorie, groupe */
    public static void premiumWidgetLabel(TextView tv) {
        if (tv == null) return;
        tv.setTextColor(ThemeColors.textMuted());
        tv.setTextSize(10.5f);
        tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tv.setLetterSpacing(0.09f);
        tv.setIncludeFontPadding(false);
    }

    /** Valeur de widget — chiffre ou texte principal */
    public static void premiumWidgetValue(TextView tv, int accentColor) {
        if (tv == null) return;
        tv.setTextColor(accentColor);
        tv.setTextSize(15.5f);
        tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tv.setLetterSpacing(-0.01f);
        tv.setIncludeFontPadding(false);
    }

    /** Bouton pill premium */
    public static void pillButton(TextView tv, Context context) {
        if (tv == null) return;
        tv.setTextColor(Color.WHITE);
        tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setIncludeFontPadding(false);
        tv.setBackground(pill(context, ThemeColors.primary(), withAlpha(Color.WHITE, 50)));
        applyPressEffect(tv);
    }

    /** Lien/action texte — voir tout, voir plus */
    public static void premiumLink(TextView tv) {
        if (tv == null) return;
        tv.setTextColor(ThemeColors.primary());
        tv.setTextSize(DS.TEXT_BODY_SMALL);
        tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tv.setIncludeFontPadding(false);
    }

    // ─────────────────────────────────────────────────────────────
    // ÉLÉVATIONS — Système hiérarchique
    // ─────────────────────────────────────────────────────────────

    public static void softenCards(Context ctx, ViewGroup parent) {
        if (parent == null || ctx == null) return;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child == null) continue;

            if (i == 0) {
                ElevationSystem.applyL3(child, ctx);
            } else if (i <= 2) {
                ElevationSystem.applyL2(child, ctx);
            } else {
                ElevationSystem.applyL1(child, ctx);
            }
            applyPressEffect(child);
        }
    }

    public static void applyNativeElevation(View view, float elevation) {
        if (view == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setElevation(DS.dp(view.getContext(), (int) elevation));
            view.setTranslationZ(0f);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // INTERACTIONS — Press, tap, longpress
    // ─────────────────────────────────────────────────────────────

    /**
     * Micro-interaction press premium — scale + alpha + légère translation
     * Inspiré des interactions iOS 18 / Revolut
     */
    public static void applyPressEffect(final View view) {
        if (view == null) return;
        view.setClickable(true);

        view.setOnTouchListener(new View.OnTouchListener() {
            private static final float SCALE_DOWN = 0.978f;
            private static final float ALPHA_DOWN = 0.90f;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!v.isEnabled()) return false;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.animate()
                                .scaleX(SCALE_DOWN)
                                .scaleY(SCALE_DOWN)
                                .alpha(ALPHA_DOWN)
                                .translationY(DS.dp(v.getContext(), 1))
                                .setDuration(DS.ANIM_XS)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .alpha(1f)
                                .translationY(0f)
                                .setDuration(DS.ANIM_FAST)
                                .setInterpolator(new OvershootInterpolator(1.5f))
                                .start();
                        break;
                }
                return false;
            }
        });
    }

    /**
     * Press effect héros — plus prononcé pour la hero card
     */
    public static void applyHeroPressEffect(final View view) {
        if (view == null) return;
        view.setClickable(true);

        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!v.isEnabled()) return false;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.animate()
                                .scaleX(0.972f)
                                .scaleY(0.972f)
                                .alpha(0.88f)
                                .setDuration(DS.ANIM_XS)
                                .setInterpolator(new DecelerateInterpolator(1.5f))
                                .start();
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .alpha(1f)
                                .setDuration(200)
                                .setInterpolator(new OvershootInterpolator(2f))
                                .start();
                        break;
                }
                return false;
            }
        });
    }

    // ─────────────────────────────────────────────────────────────
    // ANIMATIONS D'APPARITION
    // ─────────────────────────────────────────────────────────────

    /**
     * Fade-in + slide-up — animation d'apparition standard
     */
    public static void fadeIn(View view, long delay) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setTranslationY(DS.dp(view.getContext(), DS.SPACE_14));
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(DS.ANIM_NORMAL)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
    }

    /**
     * Fade-in rapide — pour éléments légers
     */
    public static void fadeInFast(View view, long delay) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setTranslationY(DS.dp(view.getContext(), DS.SPACE_8));
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(DS.ANIM_FAST)
                .setInterpolator(new DecelerateInterpolator(1.4f))
                .start();
    }

    /**
     * Scale-in — apparition avec effet ressort pour icônes/badges
     */
    public static void scaleIn(View view, long delay) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setScaleX(0.78f);
        view.setScaleY(0.78f);
        view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(delay)
                .setDuration(DS.ANIM_NORMAL)
                .setInterpolator(new OvershootInterpolator(1.8f))
                .start();
    }

    /**
     * Stagger batch — animation en cascade sur les enfants d'un ViewGroup
     * Crée un effet de "waterfall" premium très soigné
     */
    public static void staggerChildren(ViewGroup parent, long baseDelay, long stepDelay) {
        if (parent == null) return;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child != null) {
                fadeIn(child, baseDelay + (i * stepDelay));
            }
        }
    }

    /**
     * Hero entrance — animation spectaculaire pour la hero card
     * Fade + scale depuis légèrement réduit
     */
    public static void heroEntrance(View view) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setScaleX(0.94f);
        view.setScaleY(0.94f);
        view.setTranslationY(DS.dp(view.getContext(), DS.SPACE_18));
        view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(DS.ANIM_HERO)
                .setInterpolator(new DecelerateInterpolator(2.0f))
                .start();
    }

    /**
     * Pulse — animation de pulsation sur un élément (insight, alerte)
     */
    public static void pulse(final View view) {
        if (view == null) return;
        view.animate()
                .scaleX(1.04f)
                .scaleY(1.04f)
                .setDuration(400)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        view.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(400)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                    }
                })
                .start();
    }

    // ─────────────────────────────────────────────────────────────
    // SETUP — Application globale
    // ─────────────────────────────────────────────────────────────

    public static void ensureDefaults(Context ctx, SharedPreferences prefs) {
        // Les valeurs par défaut restent gérées par HomeOrganizer.
    }

    public static void applyPremium(Context ctx,
                                    View rootView,
                                    SharedPreferences prefs,
                                    boolean overdraftDefined,
                                    double overdraftLimit) {
        if (rootView == null || ctx == null) return;
        rootView.setBackgroundColor(ThemeColors.background());

        if (rootView instanceof ViewGroup) {
            softenCards(ctx, (ViewGroup) rootView);
        }
    }

    public static void setupActions(Context ctx,
                                    View rootView,
                                    View.OnClickListener transactionsClick) {
        // Les actions restent gérées dans HomeView.setupDashboardActions().
    }

    // ─────────────────────────────────────────────────────────────
    // UTILS
    // ─────────────────────────────────────────────────────────────

    public static int withAlpha(int color, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
