package com.couplefinance.core.ui.components;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.animations.PressAnimations;
import com.couplefinance.core.ui.effects.GradientFactory;

public final class PremiumChip {

	private PremiumChip() {
	}

	public static TextView normal(Context ctx, String text) {
		TextView chip = base(ctx, text);
		chip.setTextColor(ThemeColors.chipText());
		chip.setBackground(GradientFactory.chip(ctx, false));
		PressAnimations.applySoft(chip);
		return chip;
	}

	public static TextView active(Context ctx, String text) {
		TextView chip = base(ctx, text);
		chip.setTextColor(ThemeColors.chipActiveText());
		chip.setBackground(GradientFactory.chip(ctx, true));
		PressAnimations.applySoft(chip);
		return chip;
	}

	public static TextView selectable(Context ctx, String text, boolean active) {
		return active ? active(ctx, text) : normal(ctx, text);
	}

	public static TextView primary(Context ctx, String text) {
		TextView chip = base(ctx, text);
		chip.setTextColor(ThemeColors.primary());
		chip.setBackground(GradientFactory.badgePrimary(ctx));
		return chip;
	}

	public static TextView success(Context ctx, String text) {
		TextView chip = base(ctx, text);
		chip.setTextColor(ThemeColors.success());
		chip.setBackground(GradientFactory.badgeSuccess(ctx));
		return chip;
	}

	public static TextView warning(Context ctx, String text) {
		TextView chip = base(ctx, text);
		chip.setTextColor(ThemeColors.warning());
		chip.setBackground(GradientFactory.badgeWarning(ctx));
		return chip;
	}

	public static TextView danger(Context ctx, String text) {
		TextView chip = base(ctx, text);
		chip.setTextColor(ThemeColors.danger());
		chip.setBackground(GradientFactory.badgeDanger(ctx));
		return chip;
	}

	public static TextView info(Context ctx, String text) {
		TextView chip = base(ctx, text);
		chip.setTextColor(ThemeColors.info());
		chip.setBackground(GradientFactory.badgeInfo(ctx));
		return chip;
	}

	public static TextView compact(Context ctx, String text, int bgColor, int textColor) {
		TextView chip = base(ctx, text);
		chip.setTextSize(11);
		chip.setPadding(
				DS.dp(ctx, 10),
				DS.dp(ctx, 3),
				DS.dp(ctx, 10),
				DS.dp(ctx, 3)
		);
		chip.setTextColor(textColor);
		chip.setBackground(GradientFactory.solid(ctx, bgColor, DS.R_XS));
		return chip;
	}

	public static TextView badge(Context ctx, String text, int bgColor, int textColor) {
		return compact(ctx, text, bgColor, textColor);
	}

	public static TextView outlined(Context ctx, String text) {
		TextView chip = base(ctx, text);
		chip.setTextColor(ThemeColors.subtext());
		chip.setBackground(GradientFactory.bordered(
				ctx,
				Color.TRANSPARENT,
				ThemeColors.border(),
				DS.R_LG
		));
		PressAnimations.applySoft(chip);
		return chip;
	}

	public static TextView glass(Context ctx, String text) {
		TextView chip = base(ctx, text);
		chip.setTextColor(ThemeColors.text());
		chip.setBackground(GradientFactory.glass(ctx, DS.R_LG));
		PressAnimations.applySoft(chip);
		return chip;
	}

	public static LinearLayout row(Context ctx) {
		LinearLayout row = new LinearLayout(ctx);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		return row;
	}

	public static void setSelected(TextView chip, Context ctx, boolean active) {
		if (chip == null || ctx == null) {
			return;
		}

		chip.setTextColor(active ? ThemeColors.chipActiveText() : ThemeColors.chipText());
		chip.setBackground(GradientFactory.chip(ctx, active));
	}

	private static TextView base(Context ctx, String text) {
		TextView chip = new TextView(ctx);

		chip.setText(text);
		chip.setTextSize(12);
		chip.setTypeface(null, Typeface.BOLD);
		chip.setGravity(Gravity.CENTER);
		chip.setSingleLine(true);

		chip.setPadding(
				DS.dp(ctx, 14),
				DS.dp(ctx, 6),
				DS.dp(ctx, 14),
				DS.dp(ctx, 6)
		);

		chip.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
		));

		return chip;
	}
}