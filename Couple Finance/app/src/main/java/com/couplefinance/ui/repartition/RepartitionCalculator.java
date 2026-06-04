package com.couplefinance.ui.repartition;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RepartitionCalculator {

	private RepartitionCalculator() {}

	public static RepartitionModels.RepartitionResult calculate(
			List<RepartitionModels.SharedTransaction> allTransactions,
			List<String> members,
			int[] ratio) {

		if (members == null || members.size() < 2) {
			return emptyResult(members);
		}

		Calendar now = Calendar.getInstance();
		int curMonth = now.get(Calendar.MONTH);
		int curYear = now.get(Calendar.YEAR);

		Map<String, Double> spentBy = new LinkedHashMap<>();
		Map<String, Double> catTotals = new LinkedHashMap<>();
		List<RepartitionModels.SharedTransaction> thisMonthTx = new ArrayList<>();

		for (String m : members) {
			spentBy.put(m, 0.0);
		}

		if (allTransactions == null) {
			allTransactions = new ArrayList<>();
		}

		double totalShared = 0;

		for (RepartitionModels.SharedTransaction tx : allTransactions) {
			if (tx == null) continue;
			if (tx.dateMs > 0 && !isSameMonth(tx.dateMs, curMonth, curYear)) continue;
			if ("income".equalsIgnoreCase(tx.type)) continue;
			if (isReimbursementTx(tx)) continue;
			if (tx.isShareSplit) continue;
			if (!tx.isShared) continue;
			if (tx.payer == null || tx.payer.trim().isEmpty()) continue;

			String matchedMember = findMember(tx.payer, members);
			if (matchedMember == null) continue;

			spentBy.put(matchedMember, spentBy.getOrDefault(matchedMember, 0.0) + tx.amount);

			totalShared += tx.amount;

			String category = tx.category == null || tx.category.trim().isEmpty()
					? "Autres"
					: tx.category.trim();

			catTotals.put(category, catTotals.getOrDefault(category, 0.0) + tx.amount);
			thisMonthTx.add(tx);
		}

		int r0 = ratio != null && ratio.length > 0 ? ratio[0] : 50;
		int r1 = ratio != null && ratio.length > 1 ? ratio[1] : 100 - r0;

		double ideal0 = totalShared * r0 / 100.0;
		double ideal1 = totalShared * r1 / 100.0;

		double spent0 = spentBy.getOrDefault(members.get(0), 0.0);
		double spent1 = spentBy.getOrDefault(members.get(1), 0.0);

		double balance0 = spent0 - ideal0;
		double balance1 = spent1 - ideal1;

		for (RepartitionModels.SharedTransaction tx : allTransactions) {
			if (tx == null) continue;
			if (tx.dateMs > 0 && !isSameMonth(tx.dateMs, curMonth, curYear)) continue;
			if ("income".equalsIgnoreCase(tx.type)) continue;
			if (!isReimbursementTx(tx)) continue;
			if (tx.payer == null || tx.payer.trim().isEmpty()) continue;

			String debtor = findMember(tx.payer, members);
			if (debtor == null) continue;

			if (debtor.equals(members.get(0))) {
				balance0 += tx.amount;
				balance1 -= tx.amount;
			} else if (debtor.equals(members.get(1))) {
				balance1 += tx.amount;
				balance0 -= tx.amount;
			}
		}

		double reimbursement = 0;
		String debtor = "";
		String creditor = "";

		if (balance0 < -0.5) {
			reimbursement = Math.abs(balance0);
			debtor = members.get(0);
			creditor = members.get(1);
		} else if (balance1 < -0.5) {
			reimbursement = Math.abs(balance1);
			debtor = members.get(1);
			creditor = members.get(0);
		}

		thisMonthTx.sort((a, b) -> Long.compare(b.dateMs, a.dateMs));

		List<Map.Entry<String, Double>> catList = new ArrayList<>(catTotals.entrySet());
		catList.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

		Map<String, Double> sortedCat = new LinkedHashMap<>();
		for (Map.Entry<String, Double> e : catList) {
			sortedCat.put(e.getKey(), e.getValue());
		}

		return new RepartitionModels.RepartitionResult(
				totalShared,
				spent0,
				spent1,
				ideal0,
				ideal1,
				balance0,
				balance1,
				reimbursement,
				debtor,
				creditor,
				thisMonthTx,
				sortedCat
		);
	}

	public static RepartitionModels.MonthHistory buildHistory(
			List<RepartitionModels.SharedTransaction> allTransactions,
			List<String> members,
			String[] labels) {

		int n = RepartitionModels.MonthHistory.SIZE;
		double[][] ecarts = new double[n][2];
		boolean[] overpayer = new boolean[n];

		Calendar now = Calendar.getInstance();

		if (members == null || members.size() < 2) {
			return new RepartitionModels.MonthHistory(ecarts, overpayer, labels);
		}

		if (allTransactions == null) {
			allTransactions = new ArrayList<>();
		}

		for (int m = 0; m < n; m++) {
			Calendar cal = (Calendar) now.clone();
			cal.add(Calendar.MONTH, -(n - 1 - m));

			int month = cal.get(Calendar.MONTH);
			int year = cal.get(Calendar.YEAR);

			double sp0 = 0;
			double sp1 = 0;
			double total = 0;

			for (RepartitionModels.SharedTransaction tx : allTransactions) {
				if (tx == null) continue;
				if (tx.dateMs <= 0 || !isSameMonth(tx.dateMs, month, year)) continue;
				if ("income".equalsIgnoreCase(tx.type)) continue;
				if (isReimbursementTx(tx)) continue;
				if (tx.isShareSplit) continue;
				if (!tx.isShared) continue;
				if (tx.payer == null || tx.payer.trim().isEmpty()) continue;

				String matched = findMember(tx.payer, members);
				if (matched == null) continue;

				total += tx.amount;

				if (matched.equals(members.get(0))) {
					sp0 += tx.amount;
				} else if (matched.equals(members.get(1))) {
					sp1 += tx.amount;
				}
			}

			double ideal = total / 2.0;
			double gap0 = sp0 - ideal;
			double gap1 = sp1 - ideal;

			if (gap0 > 0.5) {
				ecarts[m][0] = gap0;
				overpayer[m] = true;
			} else if (gap1 > 0.5) {
				ecarts[m][1] = gap1;
				overpayer[m] = false;
			}
		}

		return new RepartitionModels.MonthHistory(ecarts, overpayer, labels);
	}

	public static boolean isReimbursementTx(RepartitionModels.SharedTransaction tx) {
		if (tx == null) return false;

		String label = tx.label == null ? "" : tx.label.toLowerCase(java.util.Locale.FRANCE);
		String category = tx.category == null ? "" : tx.category.toLowerCase(java.util.Locale.FRANCE);

		return tx.isReimbursement
				|| category.contains("remboursement")
				|| label.contains("remboursement")
				|| label.contains("rééquilibrage")
				|| label.contains("reequilibrage");
	}

	public static String findMember(String name, List<String> members) {
		if (name == null || members == null) return null;

		for (String m : members) {
			if (m != null && m.equalsIgnoreCase(name.trim())) {
				return m;
			}
		}

		return null;
	}

	public static boolean isSameMonth(long ms, int month, int year) {
		Calendar c = Calendar.getInstance();
		c.setTimeInMillis(ms);

		return c.get(Calendar.MONTH) == month
				&& c.get(Calendar.YEAR) == year;
	}

	public static double spentPercent0(RepartitionModels.RepartitionResult r) {
		return r.totalShared > 0 ? r.spent0 / r.totalShared * 100 : 50;
	}

	public static double spentPercent1(RepartitionModels.RepartitionResult r) {
		return r.totalShared > 0 ? r.spent1 / r.totalShared * 100 : 50;
	}

	public static boolean member0Dominant(RepartitionModels.RepartitionResult r) {
		return spentPercent0(r) > 55;
	}

	public static RepartitionModels.RepartitionResult emptyResult(List<String> members) {
		return new RepartitionModels.RepartitionResult(
				0,
				0,
				0,
				0,
				0,
				0,
				0,
				0,
				"",
				"",
				new ArrayList<>(),
				new LinkedHashMap<>()
		);
	}
}