package com.couplefinance.ui.budget;

public class BudgetModels {

	public static class CategoryBudget {
		public String id;
		public String name;
		public double spent;
		public double budget;
		/** Dépenses du mois précédent pour cette catégorie (0 si inconnu). */
		public double prevMonthSpent = 0;

		public CategoryBudget(String id, String name, double spent, double budget) {
			this.id = id;
			this.name = name;
			this.spent = spent;
			this.budget = budget;
		}

		public int getPercent() {
			if (budget <= 0) return 0;
			return (int) Math.round((spent / budget) * 100.0);
		}

		public double getRemaining() {
			return budget - spent;
		}

		public boolean isExceeded() {
			return budget > 0 && spent > budget;
		}

		public boolean isWarning() {
			return budget > 0 && getPercent() >= 80 && !isExceeded();
		}

		/** +1 = hausse, -1 = baisse, 0 = stable ou données insuffisantes. */
		public int getTrend() {
			if (prevMonthSpent <= 0 || spent <= 0) return 0;
			double delta = (spent - prevMonthSpent) / prevMonthSpent;
			if (delta > 0.05)  return  1;
			if (delta < -0.05) return -1;
			return 0;
		}
	}
}