package com.couplefinance;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.couplefinance.core.theme.ThemeColors;

public class AppToast {

	private static View currentToast;

	public static void success(Activity activity, String message) {
		showIOS(activity, "✓", "Succès", message, ThemeColors.success());
	}

	public static void error(Activity activity, String message) {
		showIOS(activity, "!", "Erreur", message, ThemeColors.danger());
	}

	public static void info(Activity activity, String message) {
		showIOS(activity, "•", "Info", message, ThemeColors.primary());
	}

	public static void warning(Activity activity, String message) {
		showIOS(activity, "!", "Attention", message, ThemeColors.warning());
	}

	private static void showIOS(Activity activity, String iconText, String titleText, String message, int accent) {
		if (activity == null)
			return;

		activity.runOnUiThread(() -> {
			try {
				ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();

				if (currentToast != null) {
					decor.removeView(currentToast);
					currentToast = null;
				}

				FrameLayout overlay = new FrameLayout(activity);
				overlay.setClickable(false);
				overlay.setClipChildren(false);
				overlay.setClipToPadding(false);

				FrameLayout.LayoutParams overlayLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
						ViewGroup.LayoutParams.MATCH_PARENT);

				LinearLayout card = new LinearLayout(activity);
				card.setOrientation(LinearLayout.HORIZONTAL);
				card.setGravity(Gravity.CENTER_VERTICAL);
				card.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 16), dp(activity, 12));
				card.setElevation(dp(activity, 14));

				GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
						new int[] { ThemeColors.withAlpha(Color.WHITE, 245), ThemeColors.withAlpha(Color.WHITE, 232) });
				bg.setCornerRadius(dp(activity, 26));
				bg.setStroke(dp(activity, 1), ThemeColors.withAlpha(accent, 38));
				card.setBackground(bg);

				TextView icon = new TextView(activity);
				icon.setText(iconText);
				icon.setTextColor(Color.WHITE);
				icon.setTextSize(15f);
				icon.setTypeface(Typeface.DEFAULT_BOLD);
				icon.setGravity(Gravity.CENTER);
				icon.setIncludeFontPadding(false);
				icon.setBackground(circle(activity, accent));

				LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(activity, 34), dp(activity, 34));
				iconLp.rightMargin = dp(activity, 12);
				card.addView(icon, iconLp);

				LinearLayout texts = new LinearLayout(activity);
				texts.setOrientation(LinearLayout.VERTICAL);

				TextView title = new TextView(activity);
				title.setText(titleText);
				title.setTextColor(ThemeColors.text());
				title.setTextSize(13.5f);
				title.setTypeface(Typeface.DEFAULT_BOLD);
				title.setIncludeFontPadding(false);
				texts.addView(title);

				TextView body = new TextView(activity);
				body.setText(message == null ? "" : message);
				body.setTextColor(ThemeColors.subtext());
				body.setTextSize(12.5f);
				body.setMaxLines(2);
				body.setEllipsize(android.text.TextUtils.TruncateAt.END);
				body.setIncludeFontPadding(false);

				LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
						ViewGroup.LayoutParams.WRAP_CONTENT);
				bodyLp.topMargin = dp(activity, 3);
				texts.addView(body, bodyLp);

				card.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

				FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(dp(activity, 320),
						ViewGroup.LayoutParams.WRAP_CONTENT);

				cardLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
				cardLp.bottomMargin = dp(activity, 34);

				overlay.addView(card, cardLp);
				card.setMinimumHeight(dp(activity, 58));
				decor.addView(overlay, overlayLp);

				currentToast = overlay;

				card.setAlpha(0f);
				card.setTranslationY(-dp(activity, 30));
				card.setScaleX(0.96f);
				card.setScaleY(0.96f);

				card.animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f).setDuration(230)
						.setInterpolator(new DecelerateInterpolator()).start();

				new Handler(Looper.getMainLooper()).postDelayed(() -> {
					if (currentToast == overlay) {
						card.animate().alpha(0f).translationY(-dp(activity, 20)).scaleX(0.97f).scaleY(0.97f)
								.setDuration(180).withEndAction(() -> {
									try {
										decor.removeView(overlay);
									} catch (Exception ignored) {
									}
									if (currentToast == overlay) {
										currentToast = null;
									}
								}).start();
					}
				}, 2600);

			} catch (Exception e) {
				Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
			}
		});
	}

	private static GradientDrawable circle(Activity activity, int color) {
		GradientDrawable d = new GradientDrawable();
		d.setShape(GradientDrawable.OVAL);
		d.setColor(color);
		return d;
	}

	private static int dp(Activity activity, int value) {
		return (int) (value * activity.getResources().getDisplayMetrics().density);
	}
}