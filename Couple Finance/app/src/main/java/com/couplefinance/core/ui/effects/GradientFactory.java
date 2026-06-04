package com.couplefinance.core.ui.effects;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;

public final class GradientFactory {

	private GradientFactory() {
	}

	public static GradientDrawable solid(Context ctx, int color, int radiusDp) {
		GradientDrawable drawable = new GradientDrawable();
		drawable.setColor(color);
		drawable.setCornerRadius(DS.dp(ctx, radiusDp));
		return drawable;
	}

	public static GradientDrawable bordered(Context ctx, int color, int borderColor, int radiusDp) {
		GradientDrawable drawable = solid(ctx, color, radiusDp);
		drawable.setStroke(DS.dp(ctx, 1), borderColor);
		return drawable;
	}

	public static GradientDrawable bordered(Context ctx, int color, int borderColor, int radiusDp, int strokeDp) {
		GradientDrawable drawable = solid(ctx, color, radiusDp);
		drawable.setStroke(DS.dp(ctx, strokeDp), borderColor);
		return drawable;
	}

	public static GradientDrawable outline(Context ctx, int borderColor, int radiusDp) {
		return bordered(ctx, Color.TRANSPARENT, borderColor, radiusDp);
	}

	public static GradientDrawable circle(int color) {
		GradientDrawable drawable = new GradientDrawable();
		drawable.setShape(GradientDrawable.OVAL);
		drawable.setColor(color);
		return drawable;
	}

	public static GradientDrawable circleBordered(Context ctx, int color, int borderColor, int strokeDp) {
		GradientDrawable drawable = circle(color);
		drawable.setStroke(DS.dp(ctx, strokeDp), borderColor);
		return drawable;
	}

	public static GradientDrawable vertical(Context ctx, int topColor, int bottomColor, int radiusDp) {
		GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
				new int[] { topColor, bottomColor });
		drawable.setCornerRadius(DS.dp(ctx, radiusDp));
		return drawable;
	}

	public static GradientDrawable horizontal(Context ctx, int startColor, int endColor, int radiusDp) {
		GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
				new int[] { startColor, endColor });
		drawable.setCornerRadius(DS.dp(ctx, radiusDp));
		return drawable;
	}

	public static GradientDrawable diagonal(Context ctx, int startColor, int endColor, int radiusDp) {
		GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
				new int[] { startColor, endColor });
		drawable.setCornerRadius(DS.dp(ctx, radiusDp));
		return drawable;
	}

	public static GradientDrawable primaryGradient(Context ctx, int radiusDp) {
		return diagonal(ctx, ThemeColors.primary(), ThemeColors.primaryDark(), radiusDp);
	}

	public static GradientDrawable primarySoftGradient(Context ctx, int radiusDp) {
		return diagonal(ctx, ThemeColors.primarySoft(), ThemeColors.card(), radiusDp);
	}

	public static GradientDrawable successGradient(Context ctx, int radiusDp) {
		return diagonal(ctx, ThemeColors.success(), ThemeColors.withAlpha(ThemeColors.success(), 210), radiusDp);
	}

	public static GradientDrawable dangerGradient(Context ctx, int radiusDp) {
		return diagonal(ctx, ThemeColors.danger(), ThemeColors.withAlpha(ThemeColors.danger(), 210), radiusDp);
	}

	public static GradientDrawable warningGradient(Context ctx, int radiusDp) {
		return diagonal(ctx, ThemeColors.warning(), ThemeColors.withAlpha(ThemeColors.warning(), 215), radiusDp);
	}

	public static GradientDrawable infoGradient(Context ctx, int radiusDp) {
		return diagonal(ctx, ThemeColors.info(), ThemeColors.withAlpha(ThemeColors.info(), 215), radiusDp);
	}

	public static GradientDrawable card(Context ctx) {
		return bordered(ctx, ThemeColors.card(), ThemeColors.border(), DS.R_MD);
	}

	public static GradientDrawable cardSoft(Context ctx) {
		return bordered(ctx, ThemeColors.cardElevated(), ThemeColors.divider(), DS.R_MD);
	}

	public static GradientDrawable glass(Context ctx, int radiusDp) {
		return bordered(ctx, ThemeColors.glassOverlay(), ThemeColors.glassBorder(), radiusDp);
	}

	public static GradientDrawable input(Context ctx) {
		return bordered(ctx, ThemeColors.inputBackground(), ThemeColors.inputBorder(), DS.R_SM);
	}

	public static GradientDrawable chip(Context ctx, boolean active) {
		if (active) {
			return solid(ctx, ThemeColors.chipActiveBackground(), DS.R_LG);
		}

		return bordered(ctx, ThemeColors.chipBackground(), ThemeColors.chipBorder(), DS.R_LG);
	}

	public static GradientDrawable gradientDiagonal(Context ctx, int startColor, int endColor, int radiusDp) {

		GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
				new int[] { startColor, endColor });

		gd.setCornerRadius(DS.dp(ctx, radiusDp));

		return gd;
	}

	public static GradientDrawable buttonPrimary(Context ctx) {
		return primaryGradient(ctx, 28);
	}

	public static GradientDrawable buttonSecondary(Context ctx) {
		return bordered(ctx, ThemeColors.buttonSecondary(), ThemeColors.buttonSecondaryBorder(), 28);
	}

	public static GradientDrawable buttonDanger(Context ctx) {
		return solid(ctx, ThemeColors.danger(), 28);
	}

	public static GradientDrawable badgeSuccess(Context ctx) {
		return solid(ctx, ThemeColors.successSoft(), DS.R_XS);
	}

	public static GradientDrawable badgeWarning(Context ctx) {
		return solid(ctx, ThemeColors.warningSoft(), DS.R_XS);
	}

	public static GradientDrawable badgeDanger(Context ctx) {
		return solid(ctx, ThemeColors.dangerSoft(), DS.R_XS);
	}

	public static GradientDrawable badgeInfo(Context ctx) {
		return solid(ctx, ThemeColors.infoSoft(), DS.R_XS);
	}

	public static GradientDrawable badgePrimary(Context ctx) {
		return solid(ctx, ThemeColors.primarySoft(), DS.R_XS);
	}
}