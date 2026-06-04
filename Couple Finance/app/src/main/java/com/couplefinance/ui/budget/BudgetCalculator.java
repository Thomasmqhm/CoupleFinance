package com.couplefinance.ui.budget;

import java.util.List;

public class BudgetCalculator {

	public static double totalSpent(List<BudgetModels.CategoryBudget> list) {
		double total = 0;
		for (BudgetModels.CategoryBudget c : list) total += c.spent;
		return total;
	}

	public static double totalBudget(List<BudgetModels.CategoryBudget> list) {
		double total = 0;
		for (BudgetModels.CategoryBudget c : list) total += c.budget;
		return total;
	}

	public static int countExceeded(List<BudgetModels.CategoryBudget> list) {
		int count = 0;
		for (BudgetModels.CategoryBudget c : list)
			if (c.isExceeded()) count++;
		return count;
	}

	public static int countWarning(List<BudgetModels.CategoryBudget> list) {
		int count = 0;
		for (BudgetModels.CategoryBudget c : list)
			if (c.isWarning()) count++;
		return count;
	}

	public static int countSafe(List<BudgetModels.CategoryBudget> list) {
		int count = 0;
		for (BudgetModels.CategoryBudget c : list)
			if (c.budget > 0 && c.getPercent() < 80) count++;
		return count;
	}
}