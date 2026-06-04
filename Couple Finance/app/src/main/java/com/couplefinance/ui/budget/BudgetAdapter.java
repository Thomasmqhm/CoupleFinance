package com.couplefinance.ui.budget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.couplefinance.R;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.theme.ThemeDrawable;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.Fmt;
import com.couplefinance.core.ui.animations.PressAnimations;

import java.util.List;

public class BudgetAdapter extends BaseAdapter {

	private final Context context;
	private final List<BudgetModels.CategoryBudget> list;

	public BudgetAdapter(Context context, List<BudgetModels.CategoryBudget> list) {
		this.context = context;
		this.list = list;
	}

	@Override
	public int getCount() {
		return list == null ? 0 : list.size();
	}

	@Override
	public Object getItem(int i) {
		return list.get(i);
	}

	@Override
	public long getItemId(int i) {
		return i;
	}

	@Override
	public View getView(int i, View view, ViewGroup parent) {
		if (view == null) {
			view = LayoutInflater.from(context).inflate(R.layout.item_budget, parent, false);
		}

		BudgetModels.CategoryBudget c = list.get(i);

		TextView name = view.findViewById(R.id.name);
		TextView amount = view.findViewById(R.id.amount);
		ProgressBar bar = view.findViewById(R.id.progress);

		if (name != null) {
			name.setText(c.name);
			name.setTextColor(ThemeColors.text());
			name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
			name.setTextSize(15);
		}

		if (amount != null) {
			if (c.budget <= 0) {
				amount.setText(Fmt.money(c.spent) + " · toucher pour définir un budget");
			} else {
				amount.setText(Fmt.money(c.spent) + " / " + Fmt.money(c.budget) + " · " + c.getPercent() + "%");
			}
			amount.setTextColor(ThemeColors.muted());
			amount.setTextSize(13);
		}

		if (bar != null) {
			int progress = Math.min(100, Math.max(0, c.getPercent()));
			bar.setProgress(progress);

			int color;
			if (c.isExceeded()) {
				color = DS.RED;
			} else if (c.isWarning()) {
				color = DS.AMBER;
			} else {
				color = DS.GREEN;
			}

			bar.setProgressTintList(ColorStateList.valueOf(color));
			bar.setProgressBackgroundTintList(ColorStateList.valueOf(ThemeColors.border()));
		}

		view.setBackground(ThemeDrawable.card(context));
		view.setPadding(DS.dp(context, 18), DS.dp(context, 14), DS.dp(context, 18), DS.dp(context, 14));
		PressAnimations.apply(view);

		return view;
	}
}