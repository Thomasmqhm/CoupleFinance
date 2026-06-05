package com.couplefinance.ui.home;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;

import java.util.ArrayList;
import java.util.List;

public class HomeNotifications {

	private final Activity activity;
	private final Runnable onWidgetsClick;
	private final Runnable onOrganizeLongClick;
	private final ArrayList<HomeNotificationItem> notifications = new ArrayList<>();

	private boolean open = false;
	private PopupWindow popup;
	private FrameLayout bellAnchor;
	private TextView badgeView;

	public HomeNotifications(Activity activity, Runnable onWidgetsClick, Runnable onOrganizeLongClick) {
		this.activity = activity;
		this.onWidgetsClick = onWidgetsClick;
		this.onOrganizeLongClick = onOrganizeLongClick;
	}

	public void install(LinearLayout dashboardContent) {
		if (dashboardContent == null || dashboardContent.getChildCount() == 0 || bellAnchor != null) return;

		// Sur tablette, le layout deux colonnes fournit un conteneur dédié
		// (notifActionsAnchor) en haut de la colonne droite. On y place la
		// barre d'icônes. Sinon (téléphone), on garde le placement historique
		// dans la colonne droite du header.
		LinearLayout target = null;

		View anchor = dashboardContent.findViewById(
				com.couplefinance.R.id.notifActionsAnchor);
		if (anchor instanceof LinearLayout) {
			target = (LinearLayout) anchor;
		} else {
			View header = dashboardContent.getChildAt(0);
			if (!(header instanceof LinearLayout)) return;

			LinearLayout headerRow = (LinearLayout) header;
			if (headerRow.getChildCount() < 2 || !(headerRow.getChildAt(1) instanceof LinearLayout)) return;

			target = (LinearLayout) headerRow.getChildAt(1);
		}

		LinearLayout rightColumn = target;

		LinearLayout actions = new LinearLayout(activity);
		actions.setOrientation(LinearLayout.HORIZONTAL);
		actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);

		LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT,
				DS.dp(activity, 42)
		);
		actionsLp.setMargins(0, 0, 0, DS.dp(activity, 10));

		View widgetSettings = createHeaderIconView(false);
		widgetSettings.setClickable(true);
		widgetSettings.setFocusable(true);
		widgetSettings.setOnClickListener(v -> {
			if (onWidgetsClick != null) onWidgetsClick.run();
		});
		widgetSettings.setOnLongClickListener(v -> {
			if (onOrganizeLongClick != null) onOrganizeLongClick.run();
			return true;
		});

		LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(
				DS.dp(activity, 38),
				DS.dp(activity, 38)
		);
		settingsLp.setMargins(0, 0, DS.dp(activity, 8), 0);
		actions.addView(widgetSettings, settingsLp);

		bellAnchor = new FrameLayout(activity);
		bellAnchor.setClipChildren(false);
		bellAnchor.setClipToPadding(false);
		bellAnchor.setClickable(true);
		bellAnchor.setFocusable(true);
		bellAnchor.setOnClickListener(v -> toggle());

		View bell = createHeaderIconView(true);
		bellAnchor.addView(
				bell,
				new FrameLayout.LayoutParams(
						DS.dp(activity, 38),
						DS.dp(activity, 38),
						Gravity.CENTER
				)
		);

		badgeView = new TextView(activity);
		badgeView.setTextColor(Color.WHITE);
		badgeView.setTextSize(9f);
		badgeView.setTypeface(Typeface.DEFAULT_BOLD);
		badgeView.setGravity(Gravity.CENTER);
		badgeView.setIncludeFontPadding(false);
		badgeView.setBackground(circle(ThemeColors.primary()));
		badgeView.setElevation(DS.dp(activity, 10));

		FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
				DS.dp(activity, 17),
				DS.dp(activity, 17),
				Gravity.RIGHT | Gravity.TOP
		);
		badgeLp.topMargin = -DS.dp(activity, 1);
		badgeLp.rightMargin = -DS.dp(activity, 1);
		bellAnchor.addView(badgeView, badgeLp);

		updateBadge();

		actions.addView(
				bellAnchor,
				new LinearLayout.LayoutParams(
						DS.dp(activity, 42),
						DS.dp(activity, 42)
				)
		);

		rightColumn.addView(actions, 0, actionsLp);
	}

	public void setNotifications(List<HomeNotificationItem> items) {
		notifications.clear();
		if (items != null) notifications.addAll(items);
		updateBadge();

		if (open && popup != null && popup.isShowing()) {
			dismiss();
			show();
		}
	}

	public int getUnreadCount() {
		return notifications.size();
	}

	private HomeNotificationItem[] getVisibleNotifications() {
		if (notifications.isEmpty()) {
			return new HomeNotificationItem[]{
					new HomeNotificationItem(
							"✓",
							"Aucune alerte",
							"Votre foyer est à jour.",
							ThemeColors.success(),
							ThemeColors.successSoft(),
							ThemeColors.successSoft()
					)
			};
		}

		return notifications.toArray(new HomeNotificationItem[0]);
	}

	private void updateBadge() {
		if (badgeView == null) return;

		int count = getUnreadCount();

		badgeView.setText(String.valueOf(Math.min(count, 9)));
		badgeView.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
	}

	private void toggle() {
		if (open) dismiss();
		else show();
	}

	public void dismiss() {
		open = false;

		if (popup != null && popup.isShowing()) {
			popup.dismiss();
		}
	}

	private void show() {
		if (bellAnchor == null) return;

		dismiss();
		open = true;

		LinearLayout panel = new LinearLayout(activity);
		panel.setOrientation(LinearLayout.VERTICAL);
		panel.setPadding(
				DS.dp(activity, 10),
				DS.dp(activity, 10),
				DS.dp(activity, 10),
				DS.dp(activity, 10)
		);
		panel.setBackground(notificationPanel());
		panel.setElevation(DS.dp(activity, 18));

		LinearLayout header = new LinearLayout(activity);
		header.setOrientation(LinearLayout.HORIZONTAL);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.setPadding(
				DS.dp(activity, 10),
				DS.dp(activity, 8),
				DS.dp(activity, 6),
				DS.dp(activity, 10)
		);

		TextView title = new TextView(activity);
		title.setText("Notifications");
		title.setTextColor(ThemeColors.text());
		title.setTextSize(18f);
		title.setTypeface(Typeface.DEFAULT_BOLD);
		title.setIncludeFontPadding(false);
		header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		TextView close = new TextView(activity);
		close.setText("×");
		close.setTextColor(ThemeColors.subtext());
		close.setTextSize(24f);
		close.setGravity(Gravity.CENTER);
		close.setIncludeFontPadding(false);
		close.setBackground(circle(ThemeColors.surfaceSoft()));
		close.setOnClickListener(v -> dismiss());

		header.addView(
				close,
				new LinearLayout.LayoutParams(
						DS.dp(activity, 34),
						DS.dp(activity, 34)
				)
		);

		panel.addView(header);

		for (HomeNotificationItem item : getVisibleNotifications()) {
			panel.addView(createNotificationRow(item));
		}

		TextView footer = new TextView(activity);
		footer.setText("Tout marquer comme lu");
		footer.setTextColor(ThemeColors.primary());
		footer.setTextSize(13f);
		footer.setTypeface(Typeface.DEFAULT_BOLD);
		footer.setGravity(Gravity.CENTER);
		footer.setIncludeFontPadding(false);
		footer.setPadding(0, DS.dp(activity, 12), 0, DS.dp(activity, 4));
		footer.setOnClickListener(v -> {
			notifications.clear();
			updateBadge();
			dismiss();
		});

		panel.addView(
				footer,
				new LinearLayout.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT,
						DS.dp(activity, 46)
				)
		);

		popup = new PopupWindow(
				panel,
				DS.dp(activity, 338),
				ViewGroup.LayoutParams.WRAP_CONTENT,
				true
		);

		popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
		popup.setOutsideTouchable(true);
		popup.setOnDismissListener(() -> open = false);

		panel.setAlpha(0f);
		panel.setScaleX(0.96f);
		panel.setScaleY(0.96f);
		panel.setTranslationY(-DS.dp(activity, 10));

		int xoff = -DS.dp(activity, 338) + DS.dp(activity, 42);

		popup.showAsDropDown(bellAnchor, xoff, DS.dp(activity, 10));

		panel.animate()
				.alpha(1f)
				.scaleX(1f)
				.scaleY(1f)
				.translationY(0)
				.setDuration(210)
				.setInterpolator(new DecelerateInterpolator())
				.start();
	}

	private View createNotificationRow(HomeNotificationItem item) {
		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(
				DS.dp(activity, 12),
				DS.dp(activity, 12),
				DS.dp(activity, 12),
				DS.dp(activity, 12)
		);
		row.setBackground(rowBg());
		row.setElevation(DS.dp(activity, 2));

		LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT
		);
		rowLp.bottomMargin = DS.dp(activity, 8);
		row.setLayoutParams(rowLp);

		TextView icon = new TextView(activity);
		icon.setText(item.icon);
		icon.setTextColor(Color.WHITE);
		icon.setTextSize(14f);
		icon.setTypeface(Typeface.DEFAULT_BOLD);
		icon.setGravity(Gravity.CENTER);
		icon.setIncludeFontPadding(false);
		icon.setBackground(circle(item.textColor));

		LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
				DS.dp(activity, 36),
				DS.dp(activity, 36)
		);
		iconLp.rightMargin = DS.dp(activity, 12);
		row.addView(icon, iconLp);

		LinearLayout texts = new LinearLayout(activity);
		texts.setOrientation(LinearLayout.VERTICAL);

		TextView title = new TextView(activity);
		title.setText(item.title);
		title.setTextColor(ThemeColors.text());
		title.setTextSize(13.5f);
		title.setTypeface(Typeface.DEFAULT_BOLD);
		title.setIncludeFontPadding(false);
		texts.addView(title);

		TextView sub = new TextView(activity);
		sub.setText(item.subtitle);
		sub.setTextColor(ThemeColors.subtext());
		sub.setTextSize(12f);
		sub.setLineSpacing(DS.dp(activity, 2), 1f);
		sub.setIncludeFontPadding(false);

		LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT
		);
		subLp.topMargin = DS.dp(activity, 4);
		texts.addView(sub, subLp);

		row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		return row;
	}

	private View createHeaderIconView(boolean bell) {
		HeaderIconView icon = new HeaderIconView(activity, bell);
		icon.setBackground(circle(ThemeColors.card()));
		icon.setElevation(DS.dp(activity, 6));
		return icon;
	}

	private class HeaderIconView extends View {

		private final boolean bell;
		private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

		HeaderIconView(android.content.Context context, boolean bell) {
			super(context);
			this.bell = bell;

			paint.setColor(ThemeColors.primary());
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeCap(Paint.Cap.ROUND);
			paint.setStrokeJoin(Paint.Join.ROUND);
		}

		@Override
		protected void onDraw(Canvas canvas) {
			super.onDraw(canvas);

			float w = getWidth();
			float h = getHeight();

			paint.setStrokeWidth(Math.max(2.1f, w * 0.058f));
			paint.setColor(ThemeColors.primary());

			if (bell) {
				drawBell(canvas, w, h);
			} else {
				drawGear(canvas, w, h);
			}
		}

		private void drawBell(Canvas canvas, float w, float h) {
			float cx = w / 2f;

			RectF dome = new RectF(
					w * 0.31f,
					h * 0.25f,
					w * 0.69f,
					h * 0.68f
			);

			canvas.drawArc(dome, 205, 130, false, paint);
			canvas.drawLine(w * 0.31f, h * 0.55f, w * 0.25f, h * 0.70f, paint);
			canvas.drawLine(w * 0.69f, h * 0.55f, w * 0.75f, h * 0.70f, paint);
			canvas.drawLine(w * 0.25f, h * 0.70f, w * 0.75f, h * 0.70f, paint);
			canvas.drawLine(cx, h * 0.22f, cx, h * 0.18f, paint);
			canvas.drawArc(
					new RectF(w * 0.43f, h * 0.70f, w * 0.57f, h * 0.83f),
					0,
					180,
					false,
					paint
			);
		}

		private void drawGear(Canvas canvas, float w, float h) {
			float cx = w / 2f;
			float cy = h / 2f;
			float outer = w * 0.22f;
			float inner = w * 0.075f;

			for (int i = 0; i < 8; i++) {
				double a = Math.toRadians(i * 45);

				float x1 = cx + (float) Math.cos(a) * (outer + w * 0.025f);
				float y1 = cy + (float) Math.sin(a) * (outer + w * 0.025f);
				float x2 = cx + (float) Math.cos(a) * (outer + w * 0.085f);
				float y2 = cy + (float) Math.sin(a) * (outer + w * 0.085f);

				canvas.drawLine(x1, y1, x2, y2, paint);
			}

			canvas.drawCircle(cx, cy, outer, paint);
			canvas.drawCircle(cx, cy, inner, paint);
		}
	}

	private GradientDrawable circle(int color) {
		GradientDrawable d = new GradientDrawable();
		d.setShape(GradientDrawable.OVAL);
		d.setColor(color);
		return d;
	}

	private GradientDrawable rowBg() {
		GradientDrawable d = new GradientDrawable(
				GradientDrawable.Orientation.TOP_BOTTOM,
				new int[]{
						ThemeColors.withAlpha(Color.WHITE, 250),
						ThemeColors.withAlpha(Color.WHITE, 235)
				}
		);
		d.setCornerRadius(DS.dp(activity, 22));
		d.setStroke(DS.dp(activity, 1), ThemeColors.withAlpha(ThemeColors.primary(), 18));
		return d;
	}

	private GradientDrawable notificationPanel() {
		GradientDrawable d = new GradientDrawable(
				GradientDrawable.Orientation.TOP_BOTTOM,
				new int[]{
						ThemeColors.withAlpha(Color.WHITE, 248),
						ThemeColors.withAlpha(Color.WHITE, 232)
				}
		);

		d.setCornerRadius(DS.dp(activity, 30));
		d.setStroke(DS.dp(activity, 1), ThemeColors.withAlpha(ThemeColors.primary(), 32));

		return d;
	}
}