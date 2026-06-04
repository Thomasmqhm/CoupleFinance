package com.couplefinance.ui.settings;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;

public class SettingsCards {

	public static LinearLayout chipFlow(Activity a) {
		LinearLayout flow = new LinearLayout(a);
		flow.setOrientation(LinearLayout.VERTICAL);
		return flow;
	}

	public static LinearLayout chipRow(Activity a) {
		LinearLayout row = new LinearLayout(a);
		row.setOrientation(LinearLayout.HORIZONTAL);

		LinearLayout.LayoutParams params = SettingsStyles.matchWrap();
		params.bottomMargin = SettingsStyles.dp(a, 10);
		row.setLayoutParams(params);

		return row;
	}

	public static TextView chip(Activity a, String text, boolean active, boolean income) {
		TextView tv = new TextView(a);

		tv.setText("● " + text);
		tv.setTextSize(14);
		tv.setTypeface(Typeface.DEFAULT_BOLD);
		tv.setGravity(Gravity.CENTER);
		tv.setSingleLine(true);
		tv.setPadding(
				SettingsStyles.dp(a, 18),
				SettingsStyles.dp(a, 11),
				SettingsStyles.dp(a, 18),
				SettingsStyles.dp(a, 11)
		);

		GradientDrawable bg = new GradientDrawable();
		bg.setCornerRadius(100);

		int accent = income ? ThemeColors.success() : SettingsStyles.primary();

		if (active) {
			bg.setColor(ThemeColors.backgroundSecondary());
			bg.setStroke(2, accent);
		} else {
			bg.setColor(Color.TRANSPARENT);
			bg.setStroke(2, SettingsStyles.border());
		}

		tv.setBackground(bg);
		tv.setTextColor(accent);

		LinearLayout.LayoutParams params = SettingsStyles.wrapWrap();
		params.rightMargin = SettingsStyles.dp(a, 10);
		tv.setLayoutParams(params);

		return tv;
	}

	public static View premiumSwitch(Activity a, boolean checked) {
		LinearLayout root = new LinearLayout(a);
		root.setGravity(Gravity.CENTER_VERTICAL);

		GradientDrawable bg = new GradientDrawable();
		bg.setCornerRadius(100);
		bg.setColor(checked ? ThemeColors.switchActive() : ThemeColors.switchInactive());
		root.setBackground(bg);

		LinearLayout.LayoutParams rootParams =
				new LinearLayout.LayoutParams(SettingsStyles.dp(a, 54), SettingsStyles.dp(a, 32));
		root.setLayoutParams(rootParams);

		View circle = new View(a);

		GradientDrawable c = new GradientDrawable();
		c.setShape(GradientDrawable.OVAL);
		c.setColor(Color.WHITE);
		circle.setBackground(c);

		LinearLayout.LayoutParams cp =
				new LinearLayout.LayoutParams(SettingsStyles.dp(a, 24), SettingsStyles.dp(a, 24));
		cp.leftMargin = checked ? SettingsStyles.dp(a, 26) : SettingsStyles.dp(a, 4);
		circle.setLayoutParams(cp);

		root.addView(circle);
		root.setSelected(checked);

		root.setOnClickListener(v -> {
			boolean newState = !v.isSelected();

			v.setSelected(newState);

			bg.setColor(newState ? ThemeColors.switchActive() : ThemeColors.switchInactive());

			LinearLayout.LayoutParams newCp =
					new LinearLayout.LayoutParams(SettingsStyles.dp(a, 24), SettingsStyles.dp(a, 24));

			newCp.leftMargin = newState ? SettingsStyles.dp(a, 26) : SettingsStyles.dp(a, 4);
			circle.setLayoutParams(newCp);
		});

		return root;
	}

	public static LinearLayout sectionCard(Activity a) {
		LinearLayout card = new LinearLayout(a);

		card.setOrientation(LinearLayout.VERTICAL);
		card.setPadding(
				SettingsStyles.dp(a, 20),
				SettingsStyles.dp(a, 20),
				SettingsStyles.dp(a, 20),
				SettingsStyles.dp(a, 20)
		);

		SettingsStyles.applyCardElevation(card);
		card.setBackground(SettingsStyles.card());

		return card;
	}

	public static View titleRow(Activity a, String left, String right) {
		LinearLayout row = new LinearLayout(a);

		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);

		TextView l = new TextView(a);
		l.setText(left);
		l.setTextColor(SettingsStyles.text());
		l.setTextSize(18);
		l.setTypeface(Typeface.DEFAULT_BOLD);

		row.addView(l, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

		TextView r = new TextView(a);
		r.setText(right);
		r.setTextColor(SettingsStyles.subtext());
		r.setTextSize(14);
		r.setTypeface(Typeface.DEFAULT_BOLD);

		row.addView(r);

		return row;
	}
}