package com.couplefinance.core.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.theme.ThemeDrawable;
import com.couplefinance.core.ui.components.PremiumButton;
import com.couplefinance.core.ui.components.PremiumCard;
import com.couplefinance.core.ui.components.PremiumChip;
import com.couplefinance.core.ui.components.PremiumEmptyState;
import com.couplefinance.core.ui.components.PremiumInput;
import com.couplefinance.core.ui.components.PremiumInfoRow;

public final class UiFactory {

    private UiFactory() {
    }

    // ─────────────────────────────
    // BACKGROUNDS
    // ─────────────────────────────

    public static GradientDrawable bg(int color, int radiusDp, Context ctx) {
        return ThemeDrawable.solid(ctx, color, radiusDp);
    }

    public static GradientDrawable bgBordered(int color, int strokeColor, int radiusDp, Context ctx) {
        return ThemeDrawable.bordered(ctx, color, strokeColor, radiusDp);
    }

    public static GradientDrawable bgOutlined(int strokeColor, int radiusDp, Context ctx) {
        return ThemeDrawable.outline(ctx, strokeColor, radiusDp);
    }

    public static GradientDrawable circle(int color) {
        return ThemeDrawable.circle(color);
    }

    public static int withAlpha(int color, int alpha) {
        return ThemeColors.withAlpha(color, alpha);
    }

    // ─────────────────────────────
    // ROOTS
    // ─────────────────────────────

    public static ScrollView scrollRoot(Context ctx) {
        ScrollView scroll = new ScrollView(ctx);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setBackgroundColor(ThemeColors.background());
        return scroll;
    }

    public static LinearLayout pageRoot(Context ctx) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipToPadding(false);
        root.setPadding(
                DS.dp(ctx, DS.SCREEN_HORIZONTAL),
                DS.dp(ctx, DS.SCREEN_TOP),
                DS.dp(ctx, DS.SCREEN_HORIZONTAL),
                DS.dp(ctx, DS.SCREEN_BOTTOM)
        );
        return root;
    }

    public static LinearLayout horizontal(Context ctx) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    public static LinearLayout vertical(Context ctx) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        return col;
    }

    public static LinearLayout premiumPage(Context ctx) {
        LinearLayout root = pageRoot(ctx);
        root.setBackgroundColor(ThemeColors.background());
        return root;
    }

    // ─────────────────────────────
    // CARDS / SURFACES
    // ─────────────────────────────

    public static LinearLayout card(Context ctx) {
        LinearLayout card = PremiumCard.standard(ctx);
        applyPremiumSurface(card, ctx, ThemeColors.surface(), DS.RADIUS_XL);
        return card;
    }

    public static LinearLayout cardRow(Context ctx) {
        LinearLayout card = PremiumCard.row(ctx);
        applyPremiumSurface(card, ctx, ThemeColors.surface(), DS.RADIUS_XL);
        return card;
    }

    public static LinearLayout glassCard(Context ctx) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                DS.dp(ctx, DS.CARD_PADDING),
                DS.dp(ctx, DS.CARD_PADDING),
                DS.dp(ctx, DS.CARD_PADDING),
                DS.dp(ctx, DS.CARD_PADDING)
        );
        card.setBackground(ThemeDrawable.bordered(
                ctx,
                ThemeColors.surfaceGlass(),
                ThemeColors.glassBorder(),
                DS.RADIUS_2XL
        ));
        applySoftElevation(card, DS.SHADOW_CARD);
        applyPressEffect(card);
        return card;
    }

    public static LinearLayout floatingCard(Context ctx) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                DS.dp(ctx, DS.CARD_PADDING),
                DS.dp(ctx, DS.CARD_PADDING),
                DS.dp(ctx, DS.CARD_PADDING),
                DS.dp(ctx, DS.CARD_PADDING)
        );
        card.setBackground(ThemeDrawable.bordered(
                ctx,
                ThemeColors.surfaceFloating(),
                ThemeColors.borderSoft(),
                DS.RADIUS_2XL
        ));
        applySoftElevation(card, DS.SHADOW_FLOATING);
        applyPressEffect(card);
        return card;
    }

    public static LinearLayout premiumSection(Context ctx) {
        LinearLayout section = new LinearLayout(ctx);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, 0, 0, 0);
        return section;
    }

    public static LinearLayout heroSurface(Context ctx) {
        LinearLayout hero = new LinearLayout(ctx);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(
                DS.dp(ctx, DS.CARD_PADDING_LARGE),
                DS.dp(ctx, DS.CARD_PADDING_LARGE),
                DS.dp(ctx, DS.CARD_PADDING_LARGE),
                DS.dp(ctx, DS.CARD_PADDING_LARGE)
        );

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        ThemeColors.heroGradientStart(),
                        ThemeColors.heroGradientMiddle(),
                        ThemeColors.heroGradientEnd()
                }
        );
        bg.setCornerRadius(DS.dp(ctx, DS.RADIUS_2XL));
        hero.setBackground(bg);

        applySoftElevation(hero, DS.SHADOW_HERO);
        applyPressEffect(hero);

        return hero;
    }

    public static LinearLayout heroCard(Context ctx, int backgroundColor) {
        LinearLayout hero = new LinearLayout(ctx);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(
                DS.dp(ctx, DS.CARD_PADDING_LARGE),
                DS.dp(ctx, DS.CARD_PADDING_LARGE),
                DS.dp(ctx, DS.CARD_PADDING_LARGE),
                DS.dp(ctx, DS.CARD_PADDING_LARGE)
        );
        hero.setBackground(ThemeDrawable.solid(ctx, backgroundColor, DS.RADIUS_2XL));
        applySoftElevation(hero, DS.SHADOW_HERO);
        applyPressEffect(hero);
        return hero;
    }

    public static LinearLayout statCard(Context ctx, int bgColor) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        int p = DS.dp(ctx, DS.SPACE_16);
        card.setPadding(p, p, p, p);
        card.setBackground(ThemeDrawable.bordered(ctx, bgColor, ThemeColors.borderSoft(), DS.RADIUS_XL));
        applySoftElevation(card, DS.SHADOW_SOFT);
        applyPressEffect(card);
        return card;
    }

    public static LinearLayout emptyCard(Context ctx, String text) {
        return PremiumEmptyState.compact(ctx, "Aucun élément", text);
    }

    // ─────────────────────────────
    // BUTTONS
    // ─────────────────────────────

    public static Button btnPrimary(Context ctx, String label) {
        Button b = PremiumButton.primary(ctx, label);
        applyButtonPolish(b, ctx);
        return b;
    }

    public static Button btnSecondary(Context ctx, String label) {
        Button b = PremiumButton.secondary(ctx, label);
        applyButtonPolish(b, ctx);
        return b;
    }

    public static Button btnDanger(Context ctx, String label) {
        Button b = PremiumButton.danger(ctx, label);
        applyButtonPolish(b, ctx);
        return b;
    }

    public static Button btnText(Context ctx, String label) {
        Button b = PremiumButton.text(ctx, label);
        applyPressEffect(b);
        return b;
    }

    public static Button primaryButton(Context ctx, String label) {
        return btnPrimary(ctx, label);
    }

    public static Button secondaryButton(Context ctx, String label) {
        return btnSecondary(ctx, label);
    }

    public static Button dangerButton(Context ctx, String label) {
        return btnDanger(ctx, label);
    }

    public static Button floatingButton(Context ctx, String label) {
        Button b = new Button(ctx);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(DS.TEXT_BODY_SMALL);
        b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(ThemeColors.buttonTextOnPrimary());
        b.setGravity(Gravity.CENTER);
        b.setPadding(
                DS.dp(ctx, DS.SPACE_20),
                0,
                DS.dp(ctx, DS.SPACE_20),
                0
        );
        b.setBackground(ThemeDrawable.solid(ctx, ThemeColors.buttonPrimary(), DS.RADIUS_PILL));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        applySoftElevation(b, DS.SHADOW_FLOATING);
        applyPressEffect(b);
        return b;
    }

    public static TextView chip(Context ctx, String label, boolean selected) {
        TextView chip = PremiumChip.selectable(ctx, label, selected);
        applyPressEffect(chip);
        return chip;
    }

    // ─────────────────────────────
    // INPUTS
    // ─────────────────────────────

    public static EditText input(Context ctx, String hint) {
        return PremiumInput.normal(ctx, hint);
    }

    public static EditText searchInput(Context ctx, String hint) {
        return PremiumInput.search(ctx, hint);
    }

    public static EditText inputNumeric(Context ctx, String hint) {
        return PremiumInput.numeric(ctx, hint);
    }

    public static EditText inputEmail(Context ctx, String hint) {
        return PremiumInput.email(ctx, hint);
    }

    public static EditText inputPassword(Context ctx, String hint) {
        return PremiumInput.password(ctx, hint);
    }

    // ─────────────────────────────
    // TEXTS
    // ─────────────────────────────

    public static TextView pageLabel(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(ThemeColors.primary());
        tv.setTextSize(DS.TEXT_CAPTION);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setLetterSpacing(0.08f);
        tv.setIncludeFontPadding(false);
        return tv;
    }

    public static TextView pageTitle(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(ThemeColors.textPrimary());
        tv.setTextSize(DS.TEXT_TITLE);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setIncludeFontPadding(false);
        return tv;
    }

    public static TextView heroTitle(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(ThemeColors.textPrimary());
        tv.setTextSize(DS.TEXT_HERO);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setIncludeFontPadding(false);
        return tv;
    }

    public static TextView displayAmount(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(ThemeColors.textPrimary());
        tv.setTextSize(DS.TEXT_DISPLAY);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setIncludeFontPadding(false);
        tv.setSingleLine(true);
        return tv;
    }

    public static TextView sectionTitle(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(ThemeColors.textPrimary());
        tv.setTextSize(DS.TEXT_SUBTITLE);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setIncludeFontPadding(false);
        return tv;
    }

    public static TextView body(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(ThemeColors.textPrimary());
        tv.setTextSize(DS.TEXT_BODY);
        tv.setIncludeFontPadding(true);
        return tv;
    }

    public static TextView bodyMuted(Context ctx, String text) {
        TextView tv = body(ctx, text);
        tv.setTextColor(ThemeColors.textSecondary());
        tv.setTextSize(DS.TEXT_BODY_SMALL);
        return tv;
    }

    public static TextView caption(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(ThemeColors.textMuted());
        tv.setTextSize(DS.TEXT_CAPTION);
        tv.setIncludeFontPadding(false);
        return tv;
    }

    public static TextView smallLabel(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(ThemeColors.textMuted());
        tv.setTextSize(DS.TEXT_MICRO);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setLetterSpacing(0.08f);
        tv.setIncludeFontPadding(false);
        return tv;
    }

    public static TextView amountIncome(Context ctx, String text) {
        TextView tv = body(ctx, text);
        tv.setTextColor(ThemeColors.income());
        tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }

    public static TextView amountExpense(Context ctx, String text) {
        TextView tv = body(ctx, text);
        tv.setTextColor(ThemeColors.expense());
        tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }

    public static TextView amountNeutral(Context ctx, String text) {
        TextView tv = body(ctx, text);
        tv.setTextColor(ThemeColors.textPrimary());
        tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }

    // ─────────────────────────────
    // BADGES
    // ─────────────────────────────

    public static TextView badge(Context ctx, String label, int bgColor, int textColor) {
        TextView badge = PremiumChip.badge(ctx, label, bgColor, textColor);
        applyPressEffect(badge);
        return badge;
    }

    public static TextView successBadge(Context ctx, String text) {
        return PremiumChip.success(ctx, text);
    }

    public static TextView warningBadge(Context ctx, String text) {
        return PremiumChip.warning(ctx, text);
    }

    public static TextView dangerBadge(Context ctx, String text) {
        return PremiumChip.danger(ctx, text);
    }

    public static TextView terraBadge(Context ctx, String text) {
        return PremiumChip.primary(ctx, text);
    }

    public static TextView categoryBadge(Context ctx, String category) {
        int[] colors = categoryColors(category);
        return badge(ctx, category, colors[0], colors[1]);
    }

    // ─────────────────────────────
    // PROGRESS
    // ─────────────────────────────

    public static ProgressBar progressBar(Context ctx, int color, int heightDp) {
        ProgressBar pb = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setProgress(0);
        pb.setProgressTintList(ColorStateList.valueOf(color));
        pb.setProgressBackgroundTintList(ColorStateList.valueOf(ThemeColors.borderSoft()));
        pb.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                DS.dp(ctx, heightDp)
        ));
        return pb;
    }

    public static ProgressBar progressBar(Context ctx, int color) {
        return progressBar(ctx, color, 9);
    }

    public static void setProgress(ProgressBar bar, int value) {
        if (bar == null) {
            return;
        }
        bar.setProgress(Math.max(0, Math.min(100, value)));
    }

    // ─────────────────────────────
    // DIVIDER / SPACING
    // ─────────────────────────────

    public static View divider(Context ctx) {
        View v = new View(ctx);
        v.setBackgroundColor(ThemeColors.divider());
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                DS.dp(ctx, DS.DIVIDER_HEIGHT)
        ));
        return v;
    }

    public static View spacer(Context ctx, int heightDp) {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                DS.dp(ctx, heightDp)
        ));
        return v;
    }

    // ─────────────────────────────
    // AVATAR / ICONS
    // ─────────────────────────────

    public static TextView avatar(Context ctx, String name, int colorIndex, int sizeDp) {
        TextView tv = new TextView(ctx);
        tv.setText(Fmt.initial(name));
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(sizeDp / 3.4f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setIncludeFontPadding(false);
        tv.setSingleLine(true);
        tv.setPadding(0, 0, 0, 0);

        int size = DS.dp(ctx, sizeDp);
        tv.setBackground(ThemeDrawable.circle(avatarColor(colorIndex)));
        tv.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        applySoftElevation(tv, DS.SHADOW_SOFT);
        return tv;
    }

    public static TextView circleIcon(Context ctx, String icon, int bgColor, int textColor, int sizeDp) {
        TextView tv = new TextView(ctx);
        tv.setText(icon);
        tv.setTextColor(textColor);
        tv.setTextSize(sizeDp / 2.6f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(ThemeDrawable.circle(bgColor));
        tv.setIncludeFontPadding(false);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                DS.dp(ctx, sizeDp),
                DS.dp(ctx, sizeDp)
        ));
        applyPressEffect(tv);
        return tv;
    }

    // ─────────────────────────────
    // METRIC BOX
    // ─────────────────────────────

    public static TextView[] metricBox(LinearLayout parent,
                                       Context ctx,
                                       String label,
                                       String value,
                                       int valueColor) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvLabel = new TextView(ctx);
        tvLabel.setText(label);
        tvLabel.setTextColor(Color.argb(205, 255, 255, 255));
        tvLabel.setTextSize(DS.TEXT_MICRO);
        tvLabel.setTypeface(null, Typeface.BOLD);
        tvLabel.setLetterSpacing(0.08f);
        tvLabel.setIncludeFontPadding(false);

        TextView tvValue = new TextView(ctx);
        tvValue.setText(value);
        tvValue.setTextColor(valueColor);
        tvValue.setTextSize(DS.TEXT_SUBTITLE);
        tvValue.setTypeface(null, Typeface.BOLD);
        tvValue.setIncludeFontPadding(false);
        tvValue.setSingleLine(true);

        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-2, -2);
        vp.topMargin = DS.dp(ctx, DS.SPACE_4);

        col.addView(tvLabel);
        col.addView(tvValue, vp);

        parent.addView(col, new LinearLayout.LayoutParams(0, -1, 1));

        return new TextView[]{tvLabel, tvValue};
    }

    public static LinearLayout insightRow(Context ctx, String icon, String title, String subtitle, int color) {
        LinearLayout row = PremiumInfoRow.create(ctx, icon, title, subtitle, color);
        applyPressEffect(row);
        return row;
    }

    public static LinearLayout emptyState(Context ctx, String title, String subtitle) {
        return PremiumEmptyState.create(ctx, title, subtitle);
    }

    // ─────────────────────────────
    // PREMIUM HELPERS
    // ─────────────────────────────

    public static void applyPremiumSurface(View view, Context ctx, int color, int radiusDp) {
        if (view == null || ctx == null) {
            return;
        }
        view.setBackground(ThemeDrawable.bordered(
                ctx,
                color,
                ThemeColors.borderSoft(),
                radiusDp
        ));
        applySoftElevation(view, DS.SHADOW_CARD);
    }

    public static void applySoftElevation(View view, float strength) {
        if (view == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            float elevation = 0f;

            if (strength >= DS.SHADOW_HERO) {
                elevation = 14f;
            } else if (strength >= DS.SHADOW_FLOATING) {
                elevation = 10f;
            } else if (strength >= DS.SHADOW_CARD) {
                elevation = 7f;
            } else {
                elevation = 4f;
            }

            view.setElevation(elevation);
            view.setTranslationZ(0f);
        }
    }

    public static void applyPressEffect(final View view) {
        if (view == null) {
            return;
        }

        view.setClickable(true);

        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!v.isEnabled()) {
                    return false;
                }

                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    v.animate()
                            .scaleX(DS.PRESS_SCALE)
                            .scaleY(DS.PRESS_SCALE)
                            .alpha(DS.PRESS_ALPHA)
                            .setDuration(DS.ANIM_XS)
                            .start();
                } else if (event.getAction() == MotionEvent.ACTION_UP
                        || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(DS.ANIM_FAST)
                            .start();
                }

                return false;
            }
        });
    }

    public static void applyButtonPolish(Button button, Context ctx) {
        if (button == null || ctx == null) {
            return;
        }

        button.setAllCaps(false);
        button.setTextSize(DS.TEXT_BODY_SMALL);
        button.setTypeface(null, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(
                DS.dp(ctx, DS.SPACE_18),
                0,
                DS.dp(ctx, DS.SPACE_18),
                0
        );

        applyPressEffect(button);
    }

    // ─────────────────────────────
    // INTERNAL
    // ─────────────────────────────

    private static int avatarColor(int index) {
        int[] colors = new int[]{
                0xFFC0614A,
                0xFF2D7D55,
                0xFFB97725,
                0xFF4A6B9A,
                0xFF7C5FB0,
                0xFFC76F8A,
                0xFF2D7D6F,
                0xFF8C7D76
        };

        int safe = Math.abs(index) % colors.length;
        return colors[safe];
    }

    private static int[] categoryColors(String category) {
        if (category == null) {
            return new int[]{ThemeColors.borderSoft(), ThemeColors.textMuted()};
        }

        String c = category.toLowerCase(java.util.Locale.FRANCE);

        if (c.contains("tabac")) {
            return new int[]{0xFFE8E0F0, 0xFF6B4FA0};
        }

        if (c.contains("alimentation") || c.contains("courses")) {
            return new int[]{ThemeColors.successSoft(), ThemeColors.success()};
        }

        if (c.contains("transport")) {
            return new int[]{ThemeColors.infoSoft(), ThemeColors.info()};
        }

        if (c.contains("loisir")) {
            return new int[]{0xFFF0E8FF, 0xFF7D2D9A};
        }

        if (c.contains("restaurant") || c.contains("sortie")) {
            return new int[]{0xFFFFF0E8, 0xFF9A5C2D};
        }

        if (c.contains("santé") || c.contains("sante")) {
            return new int[]{ThemeColors.dangerSoft(), ThemeColors.danger()};
        }

        if (c.contains("logement") || c.contains("loyer")) {
            return new int[]{0xFFF5F0E8, 0xFFB97725};
        }

        if (c.contains("revenu")) {
            return new int[]{ThemeColors.successSoft(), ThemeColors.success()};
        }

        if (c.contains("virement")) {
            return new int[]{ThemeColors.infoSoft(), ThemeColors.info()};
        }

        if (c.contains("charges")) {
            return new int[]{ThemeColors.warningSoft(), ThemeColors.warning()};
        }

        return new int[]{ThemeColors.primarySoft(), ThemeColors.primary()};
    }
}