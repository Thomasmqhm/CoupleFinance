package com.couplefinance.core.theme;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import com.couplefinance.core.ui.DS;

public final class ThemeDrawable {

	private ThemeDrawable() {
	}

	public static GradientDrawable solid(Context ctx, int color, int radiusDp) {
		GradientDrawable g = new GradientDrawable();
		g.setColor(color);
		g.setCornerRadius(DS.dp(ctx, radiusDp));
		return g;
	}

	public static GradientDrawable bordered(Context ctx, int color, int borderColor, int radiusDp) {
		GradientDrawable g = solid(ctx, color, radiusDp);
		g.setStroke(DS.dp(ctx, 1), borderColor);
		return g;
	}

	public static GradientDrawable outline(Context ctx, int borderColor, int radiusDp) {
		return bordered(ctx, Color.TRANSPARENT, borderColor, radiusDp);
	}

	public static GradientDrawable circle(int color) {
		GradientDrawable g = new GradientDrawable();
		g.setShape(GradientDrawable.OVAL);
		g.setColor(color);
		return g;
	}

	public static GradientDrawable pill(Context ctx, int color) {
		return solid(ctx, color, 999);
	}

	public static GradientDrawable pillStroke(Context ctx, int color, int borderColor) {
		return bordered(ctx, color, borderColor, 999);
	}

	public static GradientDrawable card(Context ctx) {
		return bordered(ctx, ThemeColors.card(), ThemeColors.withAlpha(ThemeColors.border(), 130), 26);
	}

	public static GradientDrawable cardElevated(Context ctx) {
		return bordered(ctx, ThemeColors.cardElevated(), ThemeColors.withAlpha(ThemeColors.divider(), 120), 28);
	}

	public static GradientDrawable glassCard(Context ctx) {
		return bordered(ctx, ThemeColors.cardGlass(), ThemeColors.glassBorder(), 28);
	}

	public static GradientDrawable heroCard(Context ctx) {
		return gradientDiagonal(ctx, ThemeColors.heroGradientStart(), ThemeColors.heroGradientEnd(), DS.R_LG);
	}

	public static GradientDrawable modal(Context ctx) {
		return bordered(ctx, ThemeColors.modal(), ThemeColors.border(), DS.R_XL);
	}

	public static GradientDrawable primaryButton(Context ctx) {
		return solid(ctx, ThemeColors.primary(), 999);
	}

	public static GradientDrawable secondaryButton(Context ctx) {
		return bordered(ctx, ThemeColors.buttonSecondary(), ThemeColors.buttonSecondaryBorder(), 999);
	}

	public static GradientDrawable dangerButton(Context ctx) {
		return solid(ctx, ThemeColors.danger(), 999);
	}

	public static GradientDrawable input(Context ctx) {
		return bordered(ctx, ThemeColors.inputBackground(), ThemeColors.inputBorder(), 26);
	}

	public static GradientDrawable searchInput(Context ctx) {
		return bordered(ctx, ThemeColors.card(), ThemeColors.withAlpha(ThemeColors.border(), 120), 30);
	}

	public static GradientDrawable chip(Context ctx, boolean selected) {
		if (selected) {
			return solid(ctx, ThemeColors.primary(), 999);
		}

		return bordered(ctx, ThemeColors.card(), ThemeColors.withAlpha(ThemeColors.border(), 130), 999);
	}

	public static GradientDrawable successBadge(Context ctx) {
		return solid(ctx, ThemeColors.successSoft(), DS.R_XS);
	}

	public static GradientDrawable warningBadge(Context ctx) {
		return solid(ctx, ThemeColors.warningSoft(), DS.R_XS);
	}

	public static GradientDrawable dangerBadge(Context ctx) {
		return solid(ctx, ThemeColors.dangerSoft(), DS.R_XS);
	}

	public static GradientDrawable infoBadge(Context ctx) {
		return solid(ctx, ThemeColors.infoSoft(), DS.R_XS);
	}

	public static GradientDrawable gradientVertical(Context ctx, int topColor, int bottomColor, int radiusDp) {
		GradientDrawable g = new GradientDrawable(
				GradientDrawable.Orientation.TOP_BOTTOM,
				new int[]{topColor, bottomColor}
		);
		g.setCornerRadius(DS.dp(ctx, radiusDp));
		return g;
	}

	public static GradientDrawable gradientHorizontal(Context ctx, int startColor, int endColor, int radiusDp) {
		GradientDrawable g = new GradientDrawable(
				GradientDrawable.Orientation.LEFT_RIGHT,
				new int[]{startColor, endColor}
		);
		g.setCornerRadius(DS.dp(ctx, radiusDp));
		return g;
	}

	public static GradientDrawable gradientDiagonal(Context ctx, int startColor, int endColor, int radiusDp) {
		GradientDrawable g = new GradientDrawable(
				GradientDrawable.Orientation.TL_BR,
				new int[]{startColor, endColor}
		);
		g.setCornerRadius(DS.dp(ctx, radiusDp));
		return g;
	}

	public static GradientDrawable rounded(int color, int radiusDp) {
		GradientDrawable g = new GradientDrawable();
		g.setColor(color);
		g.setCornerRadius(radiusDp);
		return g;
	}

	public static GradientDrawable roundedStroke(int color, int radiusDp, int strokeWidth, int strokeColor) {
		GradientDrawable g = new GradientDrawable();
		g.setColor(color);
		g.setCornerRadius(radiusDp);
		g.setStroke(strokeWidth, strokeColor);
		return g;
	}

	public static GradientDrawable tintDanger(Context ctx, int radiusDp) {
		return bordered(
				ctx,
				ThemeColors.dangerSoft(),
				ThemeColors.withAlpha(ThemeColors.danger(), 45),
				radiusDp
		);
	}

	public static GradientDrawable tintPrimary(Context ctx, int radiusDp) {
		return bordered(
				ctx,
				ThemeColors.primarySoft(),
				ThemeColors.withAlpha(ThemeColors.primary(), 45),
				radiusDp
		);
	}

	public static GradientDrawable tintSuccess(Context ctx, int radiusDp) {
		return bordered(
				ctx,
				ThemeColors.successSoft(),
				ThemeColors.withAlpha(ThemeColors.success(), 45),
				radiusDp
		);
	}

	public static GradientDrawable tintWarning(Context ctx, int radiusDp) {
		return bordered(
				ctx,
				ThemeColors.warningSoft(),
				ThemeColors.withAlpha(ThemeColors.warning(), 45),
				radiusDp
		);
	}

	public static GradientDrawable tintInfo(Context ctx, int radiusDp) {
		return bordered(
				ctx,
				ThemeColors.infoSoft(),
				ThemeColors.withAlpha(ThemeColors.info(), 45),
				radiusDp
		);
	}
}