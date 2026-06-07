package com.couplefinance.core.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;

public final class DS {

	private DS() {
	}

	// COLORS legacy
	public static final int BG = 0xFFF7F3EE;
	public static final int CARD = 0xFFFFFFFF;
	public static final int BORDER = 0xFFEAE1D8;
	public static final int DARK = 0xFF1E1E1E;
	public static final int MUTED = 0xFF8F959E;
	public static final int TERRA = 0xFFC0614A;
	public static final int BLUE = 0xFF4A6B9A;
	public static final int GOLD = 0xFFB97725;

	// COLORS legacy complémentaires
	public static final int GREEN = 0xFF2D7D55;
	public static final int RED = 0xFFB04A3A;
	public static final int AMBER = 0xFFA67C3A;

	public static final int TERRA_LIGHT = 0xFFE8B7A6;
	public static final int GREEN_LIGHT = 0xFFEAF7EF;
	public static final int RED_LIGHT = 0xFFFFEEEE;
	public static final int AMBER_LIGHT = 0xFFFFF6E5;

	public static final int DIVIDER = 0xFFEFE7DF;
	public static final int BORDER_LIGHT = 0xFFF1EAE4;

	// SPACING legacy
	public static final int GAP_XS = 6;
	public static final int GAP_SM = 12;
	public static final int GAP = 24;
	public static final int GAP_LG = 32;

	public static final int PAD_PAGE = 20;
	public static final int PAD_INPUT = 16;
	public static final int PAD_DIALOG = 24;

	// Hauteur de la barre de navigation (72dp) + marge de respiration.
	// Toute page défilante réserve cet espace en bas pour ne rien masquer.
	public static final int NAV_BAR_HEIGHT = 72;
	public static final int NAV_CLEARANCE = 92;

	// PADDINGS legacy complémentaires
	public static final int PAD = 16;
	public static final int PAD_SM = 12;
	public static final int PAD_XS = 8;
	public static final int PAD_CARD = 18;
	public static final int PAD_RIPPLE = 10;

	// SPACING premium
	public static final int SPACE_2 = 2;
	public static final int SPACE_4 = 4;
	public static final int SPACE_6 = 6;
	public static final int SPACE_8 = 8;
	public static final int SPACE_10 = 10;
	public static final int SPACE_12 = 12;
	public static final int SPACE_14 = 14;
	public static final int SPACE_16 = 16;
	public static final int SPACE_18 = 18;
	public static final int SPACE_20 = 20;
	public static final int SPACE_24 = 24;
	public static final int SPACE_28 = 28;
	public static final int SPACE_32 = 32;
	public static final int SPACE_36 = 36;
	public static final int SPACE_40 = 40;
	public static final int SPACE_48 = 48;
	public static final int SPACE_56 = 56;
	public static final int SPACE_64 = 64;
	public static final int SPACE_72 = 72;

	// SCREEN / LAYOUT
	public static final int SCREEN_HORIZONTAL = 22;
	public static final int SCREEN_TOP = 20;
	public static final int SCREEN_BOTTOM = 120;

	public static final int SECTION_GAP = 32;
	public static final int BLOCK_GAP = 20;
	public static final int CARD_GAP = 16;

	public static final int CARD_PADDING = 20;
	public static final int CARD_PADDING_LARGE = 28;
	public static final int CONTENT_PADDING = 18;

	// RADIUS legacy
	public static final int R_XS = 8;
	public static final int R_SM = 12;
	public static final int R_MD = 18;
	public static final int R_LG = 24;
	public static final int R_XL = 32;
	public static final int R_PILL = 999;

	// RADIUS premium
	public static final int RADIUS_XS = 10;
	public static final int RADIUS_SM = 14;
	public static final int RADIUS_MD = 18;
	public static final int RADIUS_LG = 24;
	public static final int RADIUS_XL = 30;
	public static final int RADIUS_2XL = 36;
	public static final int RADIUS_3XL = 42;
	public static final int RADIUS_PILL = 999;

	// TYPOGRAPHY legacy
	public static final float TEXT_XS = 10f;
	public static final float TEXT_SM = 12f;
	public static final float TEXT_MD = 14f;
	public static final float TEXT_LG = 18f;

	public static final float TEXT_LABEL = 11f;
	public static final float TEXT_SECTION = 20f;
	public static final float TEXT_BALANCE = 48f;
	public static final float TEXT_STAT = 24f;

	// TYPOGRAPHY premium
	public static final float TEXT_DISPLAY = 42f;
	public static final float TEXT_HERO = 34f;
	public static final float TEXT_TITLE = 26f;
	public static final float TEXT_SUBTITLE = 20f;
	public static final float TEXT_BODY = 16f;
	public static final float TEXT_BODY_SMALL = 14f;
	public static final float TEXT_CAPTION = 13f;
	public static final float TEXT_MICRO = 11f;

	// FONT WEIGHTS
	public static final int FONT_LIGHT = Typeface.NORMAL;
	public static final int FONT_REGULAR = Typeface.NORMAL;
	public static final int FONT_MEDIUM = Typeface.BOLD;
	public static final int FONT_SEMIBOLD = Typeface.BOLD;
	public static final int FONT_BOLD = Typeface.BOLD;

	// ICON SIZES
	public static final int ICON_2XS = 12;
	public static final int ICON_XS = 16;
	public static final int ICON_SM = 18;
	public static final int ICON_MD = 22;
	public static final int ICON_LG = 28;
	public static final int ICON_XL = 36;
	public static final int ICON_2XL = 48;

	// AVATAR SIZES
	public static final int AVATAR_XS = 24;
	public static final int AVATAR_SM = 36;
	public static final int AVATAR_MD = 48;
	public static final int AVATAR_LG = 64;
	public static final int AVATAR_XL = 84;

	// CARD HEIGHTS
	public static final int CARD_MINI = 92;
	public static final int CARD_MEDIUM = 140;
	public static final int CARD_LARGE = 220;
	public static final int CARD_HERO = 280;

	// BUTTONS / INPUTS
	public static final int BTN_HEIGHT = 52;
	public static final int BUTTON_SM = 42;
	public static final int BUTTON_MD = 52;
	public static final int BUTTON_LG = 60;

	public static final int INPUT_HEIGHT = 56;
	public static final int INPUT_MD = 56;
	public static final int INPUT_LG = 64;

	// SHADOW
	public static final float SHADOW_SOFT = 0.06f;
	public static final float SHADOW_CARD = 0.10f;
	public static final float SHADOW_FLOATING = 0.14f;
	public static final float SHADOW_HERO = 0.18f;

	// GLASS / ALPHA
	public static final float GLASS_ALPHA = 0.72f;
	public static final float GLASS_BORDER_ALPHA = 0.12f;
	public static final float GLASS_HIGHLIGHT_ALPHA = 0.18f;

	public static final float OVERLAY_LIGHT = 0.04f;
	public static final float OVERLAY_MEDIUM = 0.08f;
	public static final float OVERLAY_STRONG = 0.16f;

	public static final float ALPHA_DISABLED = 0.32f;
	public static final float ALPHA_MUTED = 0.55f;
	public static final float ALPHA_SOFT = 0.72f;
	public static final float ALPHA_GLASS = 0.88f;

	// ANIMATIONS
	public static final int ANIM_XS = 100;
	public static final int ANIM_FAST = 160;
	public static final int ANIM_NORMAL = 240;
	public static final int ANIM_SLOW = 420;
	public static final int ANIM_HERO = 650;

	public static final float PRESS_SCALE = 0.985f;
	public static final float PRESS_ALPHA = 0.92f;

	// GRID
	public static final int GRID_COLUMNS_PHONE = 2;
	public static final int GRID_SPACING = 16;

	// DIVIDER
	public static final int DIVIDER_HEIGHT = 1;
	public static final float DIVIDER_ALPHA = 0.08f;

	// CHARTS
	public static final int CHART_HEIGHT_SMALL = 120;
	public static final int CHART_HEIGHT_MEDIUM = 180;
	public static final int CHART_HEIGHT_LARGE = 260;

	// CALENDAR
	public static final int CALENDAR_DAY_SIZE = 58;
	public static final int CALENDAR_DAY_RADIUS = 20;

	// BOTTOM BAR
	public static final int BOTTOM_BAR_HEIGHT = 76;
	public static final int BOTTOM_BAR_RADIUS = 32;

	// DIALOGS / SHEETS
	public static final int DIALOG_RADIUS = 34;
	public static final int SHEET_RADIUS = 38;

	public static int dp(Context context, float value) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
				context.getResources().getDisplayMetrics());
	}

	public static float sp(Context context, float value) {
		return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, context.getResources().getDisplayMetrics());
	}

	public static int avatarColor(int index) {
		int[] colors = new int[] { 0xFFC0614A, 0xFF2D7D55, 0xFFB97725, 0xFF4A6B9A, 0xFF7C5FB0, 0xFFC76F8A, 0xFF2D7D6F,
				0xFF8C7D76 };
		return colors[Math.abs(index) % colors.length];
	}
}