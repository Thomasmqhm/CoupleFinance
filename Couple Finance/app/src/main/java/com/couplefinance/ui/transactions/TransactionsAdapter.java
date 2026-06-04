package com.couplefinance.ui.transactions;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.couplefinance.R;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.theme.ThemeDrawable;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.Fmt;
import com.couplefinance.core.ui.animations.PressAnimations;
import com.couplefinance.ui.utils.CategoryStyleManager;
import com.couplefinance.ui.utils.MerchantLogoManager;

import java.util.List;

public class TransactionsAdapter extends BaseAdapter {

	private final Activity activity;
	private List<TransactionsModels.Transaction> transactions;
	private List<String> members;

	private static final String LOGO_TAG = "merchant_logo_bubble";

	public TransactionsAdapter(Activity activity,
			List<TransactionsModels.Transaction> transactions,
			List<String> members) {
		this.activity = activity;
		this.transactions = transactions;
		this.members = members;
	}

	public void update(List<TransactionsModels.Transaction> transactions, List<String> members) {
		this.transactions = transactions;
		this.members = members;
		notifyDataSetChanged();
	}

	public int getCount() {
		return transactions == null ? 0 : transactions.size();
	}

	public Object getItem(int pos) {
		return transactions.get(pos);
	}

	public long getItemId(int pos) {
		return pos;
	}

	public View getView(int pos, View convertView, ViewGroup parent) {
		if (convertView == null) {
			convertView = LayoutInflater.from(activity)
					.inflate(R.layout.item_transaction, parent, false);
		}

		TransactionsModels.Transaction tx = transactions.get(pos);

		FrameLayout avatarContainer = convertView.findViewById(R.id.avatarContainer);
		TextView tvLabel = convertView.findViewById(R.id.tvLabel);
		TextView tvCategory = convertView.findViewById(R.id.tvCategory);
		TextView tvType = convertView.findViewById(R.id.tvType);
		TextView tvAmountArrow = convertView.findViewById(R.id.tvAmountArrow);
		TextView tvAmount = convertView.findViewById(R.id.tvAmount);
		TextView tvPerson = convertView.findViewById(R.id.tvAvatarLetter);
		View vDot = convertView.findViewById(R.id.viewIndicator);

		convertView.setBackground(ThemeDrawable.card(activity));
		convertView.setPadding(dp(14), dp(10), dp(14), dp(10));
		convertView.setElevation(dp(1));
		PressAnimations.applySoft(convertView);

		String title = tx.description();
		if (title == null || title.trim().isEmpty()) {
			title = tx.label;
		}
		if (title == null || title.trim().isEmpty()) {
			title = "Opération";
		}

		tvLabel.setText(title);
		tvLabel.setTextColor(ThemeColors.text());
		tvLabel.setTypeface(null, Typeface.BOLD);
		tvLabel.setTextSize(16);
		tvLabel.setSingleLine(true);

		CategoryStyleManager.Style style = CategoryStyleManager.getStyle(tx.category);

		String category = tx.category == null || tx.category.trim().isEmpty()
				? "Autre"
				: tx.category.trim();

		tvCategory.setText(category);
		tvCategory.setBackground(ThemeDrawable.solid(activity, style.softColor, DS.R_XS));
		tvCategory.setTextColor(style.textColor);
		tvCategory.setTypeface(null, Typeface.BOLD);
		tvCategory.setTextSize(9);
		tvCategory.setPadding(dp(7), dp(2), dp(7), dp(2));
		tvCategory.setSingleLine(true);

		tvType.setText(Fmt.dateRelative(tx.dateMs));
		tvType.setTextColor(ThemeColors.subtext());
		tvType.setTextSize(11);
		tvType.setSingleLine(true);

		boolean income = tx.isIncome();
		int amountColor = income ? ThemeColors.success() : ThemeColors.text();

		tvAmountArrow.setText(income ? "↗" : "↘");
		tvAmountArrow.setTextColor(amountColor);
		tvAmountArrow.setTypeface(null, Typeface.BOLD);
		tvAmountArrow.setTextSize(11);

		tvAmount.setText(Fmt.moneySigned(income ? tx.amount : -tx.amount));
		tvAmount.setTextColor(amountColor);
		tvAmount.setTypeface(null, Typeface.BOLD);
		tvAmount.setTextSize(15);
		tvAmount.setSingleLine(true);

		removeOldMerchantBubble(avatarContainer);

		if (avatarContainer != null) {
			avatarContainer.setPadding(0, 0, 0, 0);

			View bubble = MerchantLogoManager.createMerchantBubble(
					activity,
					tx.label != null ? tx.label : title,
					category,
					income,
					DS.avatarColor(findMemberIndex(tx.person)),
					income ? ThemeColors.successSoft() : ThemeColors.primarySoft(),
					ThemeColors.withAlpha(income ? ThemeColors.success() : ThemeColors.primary(), 35)
			);

			bubble.setTag(LOGO_TAG);

			FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(42), dp(42));
			lp.gravity = android.view.Gravity.CENTER;
			avatarContainer.addView(bubble, lp);
		}

		if (vDot != null) {
			vDot.setVisibility(View.GONE);
		}

		if (tvPerson != null) {
			tvPerson.setVisibility(View.GONE);
		}

		return convertView;
	}

	private void removeOldMerchantBubble(FrameLayout container) {
		if (container == null) return;

		for (int i = container.getChildCount() - 1; i >= 0; i--) {
			View child = container.getChildAt(i);
			Object tag = child.getTag();

			if (tag != null && LOGO_TAG.equals(tag.toString())) {
				container.removeViewAt(i);
			}
		}
	}

	private int findMemberIndex(String person) {
		if (person == null || person.isEmpty() || members == null) return 0;

		for (int i = 0; i < members.size(); i++) {
			String member = members.get(i);
			if (member != null && member.equalsIgnoreCase(person)) {
				return i;
			}
		}

		return 0;
	}

	private int dp(int value) {
		return DS.dp(activity, value);
	}
}