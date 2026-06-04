package com.couplefinance.core.theme;

import android.graphics.Color;

/**
 * ThemeColors
 *
 * IMPORTANT :
 * - Point d'entrée UNIQUE pour toutes les couleurs
 * - Toute l'application doit passer par ici
 * - Aucun Color.parseColor dans les vues
 *
 * Direction artistique :
 * - Apple Wallet
 * - iOS 18
 * - Revolut
 * - Copilot Money
 * - Fintech premium
 */
public final class ThemeColors {

	private ThemeColors() {
	}

	private static ThemePalette t() {
		return ThemeManager.getInstance().getTheme();
	}

	/*
	|--------------------------------------------------------------------------
	| BASE
	|--------------------------------------------------------------------------
	*/

	public static int primary() {
		return t().primary;
	}

	public static int primaryDark() {
		return t().primaryDark;
	}

	public static int primaryLight() {
		return blend(t().primary, Color.WHITE, 0.72f);
	}

	public static int primarySoft() {
		return blend(t().primary, background(), 0.82f);
	}

	public static int primaryMuted() {
		return withAlpha(t().primary, 42);
	}

	public static int accent() {
		return t().accent;
	}

	/*
	|--------------------------------------------------------------------------
	| BACKGROUNDS
	|--------------------------------------------------------------------------
	*/

	public static int background() {
		return t().background;
	}

	public static int backgroundSecondary() {
		return t().backgroundSecondary;
	}

	public static int backgroundElevated() {
		return blend(background(), Color.WHITE, 0.22f);
	}

	public static int backgroundSoft() {
		return blend(background(), Color.WHITE, 0.38f);
	}

	/*
	|--------------------------------------------------------------------------
	| SURFACES
	|--------------------------------------------------------------------------
	|
	| Nouvelle génération de surfaces premium
	|
	*/

	public static int surface() {
		return t().card;
	}

	public static int surfaceSecondary() {
		return t().cardAlt;
	}

	public static int surfaceElevated() {
		return blend(t().cardAlt, Color.WHITE, 0.20f);
	}

	public static int surfaceFloating() {
		return blend(t().card, Color.WHITE, 0.34f);
	}

	public static int surfaceGlass() {
		return withAlpha(Color.WHITE, 205);
	}

	public static int surfaceSoft() {
		return blend(background(), Color.WHITE, 0.56f);
	}

	/*
	|--------------------------------------------------------------------------
	| LEGACY
	|--------------------------------------------------------------------------
	|
	| Compatibilité ancien système
	|
	*/

	public static int card() {
		return surface();
	}

	public static int cardAlt() {
		return surfaceSecondary();
	}

	public static int cardElevated() {
		return surfaceElevated();
	}

	public static int modal() {
		return surfaceFloating();
	}

	public static int widget() {
		return surface();
	}

	public static int sidebar() {
		return surface();
	}

	public static int toolbar() {
		return surfaceFloating();
	}

	public static int navigation() {
		return surfaceGlass();
	}

	/*
	|--------------------------------------------------------------------------
	| TEXTES
	|--------------------------------------------------------------------------
	*/

	public static int text() {
		return t().text;
	}

	public static int textPrimary() {
		return text();
	}

	public static int textSecondary() {
		return blend(t().subtext, text(), 0.35f);
	}

	public static int textMuted() {
		return withAlpha(text(), 145);
	}

	public static int textSoft() {
		return withAlpha(text(), 105);
	}

	public static int subtext() {
		return textSecondary();
	}

	public static int muted() {
		return textMuted();
	}

	public static int onPrimary() {
		return Color.WHITE;
	}

	public static int onPrimarySecondary() {
		return withAlpha(Color.WHITE, 210);
	}

	/*
	|--------------------------------------------------------------------------
	| BORDERS / DIVIDERS
	|--------------------------------------------------------------------------
	*/

	public static int border() {
		return blend(t().border, Color.WHITE, 0.18f);
	}

	public static int borderSoft() {
		return withAlpha(border(), 90);
	}

	public static int borderStrong() {
		return withAlpha(border(), 190);
	}

	public static int divider() {
		return withAlpha(text(), 16);
	}

	public static int dividerStrong() {
		return withAlpha(text(), 28);
	}

	public static int shadow() {
		return withAlpha(Color.BLACK, 26);
	}

	public static int shadowSoft() {
		return withAlpha(Color.BLACK, 14);
	}

	public static int shadowHero() {
		return withAlpha(Color.BLACK, 34);
	}

	public static int cardBorder() {
		return borderSoft();
	}

	/*
	|--------------------------------------------------------------------------
	| STATUS
	|--------------------------------------------------------------------------
	*/

	public static int success() {
		return t().success;
	}

	public static int warning() {
		return t().warning;
	}

	public static int danger() {
		return t().danger;
	}

	public static int info() {
		return 0xFF5B7FFF;
	}

	public static int income() {
		return success();
	}

	public static int expense() {
		return primary();
	}

	/*
	|--------------------------------------------------------------------------
	| STATUS BACKGROUNDS
	|--------------------------------------------------------------------------
	*/

	public static int successBackground() {
		return blend(success(), Color.WHITE, 0.88f);
	}

	public static int warningBackground() {
		return blend(warning(), Color.WHITE, 0.90f);
	}

	public static int dangerBackground() {
		return blend(danger(), Color.WHITE, 0.91f);
	}

	public static int infoBackground() {
		return blend(info(), Color.WHITE, 0.90f);
	}

	public static int successSoft() {
		return successBackground();
	}

	public static int warningSoft() {
		return warningBackground();
	}

	public static int dangerSoft() {
		return dangerBackground();
	}

	public static int infoSoft() {
		return infoBackground();
	}

	/*
	|--------------------------------------------------------------------------
	| SWITCH / TOGGLE
	|--------------------------------------------------------------------------
	*/

	public static int switchActive() {
		return t().switchActive;
	}

	public static int switchInactive() {
		return withAlpha(t().switchInactive, 180);
	}

	/*
	|--------------------------------------------------------------------------
	| GLASSMORPHISM
	|--------------------------------------------------------------------------
	*/

	public static int glassOverlay() {
		return withAlpha(Color.WHITE, 185);
	}

	public static int glassOverlayStrong() {
		return withAlpha(Color.WHITE, 220);
	}

	public static int glassBorder() {
		return withAlpha(Color.WHITE, 120);
	}

	public static int glassHighlight() {
		return withAlpha(Color.WHITE, 80);
	}

	public static int blurTint() {
		return withAlpha(background(), 235);
	}

	public static int cardGlass() {
		return glassOverlay();
	}

	/*
	|--------------------------------------------------------------------------
	| OVERLAYS / SCRIMS
	|--------------------------------------------------------------------------
	*/

	public static int overlay() {
		return 0x52000000;
	}

	public static int overlayLight() {
		return 0x22000000;
	}

	public static int overlayMedium() {
		return 0x44000000;
	}

	public static int overlayStrong() {
		return 0x88000000;
	}

	public static int scrim() {
		return 0x99000000;
	}

	/*
	|--------------------------------------------------------------------------
	| HERO GRADIENTS
	|--------------------------------------------------------------------------
	*/

	public static int heroGradientStart() {
		return blend(primary(), Color.WHITE, 0.06f);
	}

	public static int heroGradientMiddle() {
		return primary();
	}

	public static int heroGradientEnd() {
		return blend(primaryDark(), Color.BLACK, 0.08f);
	}

	public static int heroSoftGradientStart() {
		return blend(primary(), Color.WHITE, 0.74f);
	}

	public static int heroSoftGradientEnd() {
		return surface();
	}

	/*
	|--------------------------------------------------------------------------
	| BUTTONS
	|--------------------------------------------------------------------------
	*/

	public static int buttonPrimary() {
		return primary();
	}

	public static int buttonPrimaryPressed() {
		return blend(primaryDark(), Color.BLACK, 0.12f);
	}

	public static int buttonSecondary() {
		return surfaceFloating();
	}

	public static int buttonSecondaryPressed() {
		return surfaceElevated();
	}

	public static int buttonSecondaryBorder() {
		return borderSoft();
	}

	public static int buttonText() {
		return text();
	}

	public static int buttonTextOnPrimary() {
		return Color.WHITE;
	}

	/*
	|--------------------------------------------------------------------------
	| INPUTS
	|--------------------------------------------------------------------------
	*/

	public static int input() {
		return surfaceFloating();
	}

	public static int inputBackground() {
		return surfaceFloating();
	}

	public static int inputBorder() {
		return borderSoft();
	}

	public static int inputBorderFocused() {
		return withAlpha(primary(), 160);
	}

	public static int inputText() {
		return text();
	}

	public static int inputHint() {
		return textMuted();
	}

	/*
	|--------------------------------------------------------------------------
	| CHIPS / PILLS
	|--------------------------------------------------------------------------
	*/

	public static int chipBackground() {
		return surfaceSoft();
	}

	public static int chipBorder() {
		return borderSoft();
	}

	public static int chipText() {
		return textSecondary();
	}

	public static int chipActiveBackground() {
		return primary();
	}

	public static int chipActiveText() {
		return Color.WHITE;
	}

	/*
	|--------------------------------------------------------------------------
	| CALENDAR
	|--------------------------------------------------------------------------
	*/

	public static int calendarToday() {
		return primary();
	}

	public static int calendarSelected() {
		return primarySoft();
	}

	public static int calendarEvent() {
		return success();
	}

	/*
	|--------------------------------------------------------------------------
	| NAVIGATION
	|--------------------------------------------------------------------------
	*/

	public static int navigationBackground() {
		return withAlpha(Color.WHITE, 232);
	}

	public static int navigationActive() {
		return primary();
	}

	public static int navigationInactive() {
		return textMuted();
	}

	/*
	|--------------------------------------------------------------------------
	| HELPERS
	|--------------------------------------------------------------------------
	*/

	public static int withAlpha(int color, int alpha) {
		alpha = Math.max(0, Math.min(255, alpha));
		return (color & 0x00FFFFFF) | (alpha << 24);
	}

	public static int blend(int color1, int color2, float ratio) {
		ratio = Math.max(0f, Math.min(1f, ratio));

		float inverse = 1f - ratio;

		int r = (int) ((Color.red(color1) * inverse) + (Color.red(color2) * ratio));
		int g = (int) ((Color.green(color1) * inverse) + (Color.green(color2) * ratio));
		int b = (int) ((Color.blue(color1) * inverse) + (Color.blue(color2) * ratio));

		return Color.rgb(r, g, b);
	}

	public static int transparent() {
		return Color.TRANSPARENT;
	}

	public static int white() {
		return Color.WHITE;
	}

	public static int black() {
		return Color.BLACK;
	}
}