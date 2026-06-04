package com.couplefinance.ui.budget;

public class BudgetModels {

	public static class CategoryBudget {
		public String id;
		public String name;
		public double spent;
		public double budget;

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
	}
}