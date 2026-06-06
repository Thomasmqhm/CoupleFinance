package com.couplefinance.ui.credits;

import java.util.ArrayList;
import java.util.List;

public final class CreditsParser {

	private CreditsParser() {
	}

	public static List<CreditsModels.Credit> parseCredits(String json) {
		List<CreditsModels.Credit> result = new ArrayList<>();

		if (json == null || json.isEmpty())
			return result;

		String[] parts = json.split("\"fields\":");

		for (int i = 1; i < parts.length; i++) {
			String before = parts[i - 1];
			String p = parts[i];

			String docId = extractDocId(before);

			String name = p.contains("\"name\"")
					? extractStr(p.substring(p.indexOf("\"name\"")), "stringValue")
					: "";

			String emoji = p.contains("\"emoji\"")
					? extractStr(p.substring(p.indexOf("\"emoji\"")), "stringValue")
					: "";

			String bank = p.contains("\"bank\"")
					? extractStr(p.substring(p.indexOf("\"bank\"")), "stringValue")
					: "";

			String type = p.contains("\"type\"")
					? extractStr(p.substring(p.indexOf("\"type\"")), "stringValue")
					: "Autre";

			String paidBy = p.contains("\"paidBy\"")
					? extractStr(p.substring(p.indexOf("\"paidBy\"")), "stringValue")
					: "";

			String compte = p.contains("\"compte\"")
					? extractStr(p.substring(p.indexOf("\"compte\"")), "stringValue")
					: "";

			double totalAmount = parseD(extractFieldValue(p, "totalAmount"));
			double monthlyPayment = parseD(extractFieldValue(p, "monthlyPayment"));
			long startDateMs = parseLong(extractFieldValue(p, "startDate"));
			int durationMonths = (int) parseLong(extractFieldValue(p, "durationMonths"));
			double rate = parseD(extractFieldValue(p, "rate"));
			int paymentDay = (int) parseLong(extractFieldValue(p, "paymentDay"));

			if (name.isEmpty())
				continue;

			if (emoji.isEmpty())
				emoji = CreditsModels.emojiForName(name);

			if (paymentDay <= 0)
				paymentDay = 1;

			result.add(new CreditsModels.Credit(
					docId,
					name,
					totalAmount,
					monthlyPayment,
					startDateMs,
					durationMonths,
					emoji,
					bank,
					type,
					rate,
					paidBy,
					compte,
					paymentDay
			));
		}

		return result;
	}

	public static double parsePersonsRevenue(String json) {
		if (json == null || json.isEmpty())
			return 0;

		double total = 0;
		String[] parts = json.split("\"fields\":");

		for (int i = 1; i < parts.length; i++) {
			total += parseD(extractFieldValue(parts[i], "revenue"));
		}

		return total;
	}

	public static double parseFixedChargesTotal(String json) {
		if (json == null || json.isEmpty())
			return 0;

		double total = 0;
		String[] parts = json.split("\"fields\":");

		for (int i = 1; i < parts.length; i++) {
			total += parseD(extractFieldValue(parts[i], "amount"));
		}

		return total;
	}

	static String extractDocId(String before) {
		if (before == null)
			return "";

		for (String marker : new String[]{"\"name\": \"", "\"name\":\""}) {
			int ns = before.lastIndexOf(marker);

			if (ns < 0)
				continue;

			// marker already ends with the opening quote — value starts right after
			int s = ns + marker.length();
			int e = before.indexOf("\"", s);

			if (e > s) {
				String d = before.substring(s, e);

				if (d.contains("/"))
					d = d.substring(d.lastIndexOf("/") + 1);

				return d;
			}
		}

		return "";
	}

	static String extractFieldValue(String json, String field) {
		if (json == null || field == null)
			return "0";

		for (String marker : new String[]{"\"" + field + "\":", "\"" + field + "\": "}) {
			int idx = json.indexOf(marker);

			if (idx < 0)
				continue;

			int bs = json.indexOf("{", idx + marker.length());
			int be = json.indexOf("}", bs);

			if (bs < 0 || be < 0)
				continue;

			String block = json.substring(bs, be + 1);

			String v = extractNum(block, "doubleValue");
			if (!v.equals("0"))
				return v;

			v = extractNum(block, "integerValue");
			if (!v.equals("0"))
				return v;

			v = extractStr(block, "stringValue");
			if (!v.isEmpty())
				return v;
		}

		return "0";
	}

	static String extractStr(String json, String key) {
		if (json == null || key == null)
			return "";

		for (String s : new String[]{"\"" + key + "\": \"", "\"" + key + "\":\""}) {
			int i = json.indexOf(s);

			if (i >= 0) {
				int st = i + s.length();
				int e = json.indexOf("\"", st);

				if (e > st)
					return json.substring(st, e).trim();
			}
		}

		return "";
	}

	static String extractNum(String json, String key) {
		if (json == null || key == null)
			return "0";

		for (String s : new String[]{
				"\"" + key + "\": \"",
				"\"" + key + "\":\"",
				"\"" + key + "\": ",
				"\"" + key + "\":"
		}) {
			int i = json.indexOf(s);

			if (i >= 0) {
				String rest = json.substring(i + s.length()).trim();

				if (rest.startsWith("\"")) {
					int e = rest.indexOf("\"", 1);
					return e > 1 ? rest.substring(1, e).trim() : "0";
				}

				int e = 0;

				while (e < rest.length()
						&& (Character.isDigit(rest.charAt(e))
						|| rest.charAt(e) == '.'
						|| rest.charAt(e) == '-'
						|| rest.charAt(e) == '+')) {
					e++;
				}

				if (e > 0)
					return rest.substring(0, e).trim();
			}
		}

		return "0";
	}

	static double parseD(String s) {
		try {
			return Double.parseDouble(s == null ? "0" : s.trim().replace(",", "."));
		} catch (Exception e) {
			return 0;
		}
	}

	static long parseLong(String s) {
		if (s == null)
			return 0;

		try {
			return Long.parseLong(s.trim().replace(".0", ""));
		} catch (Exception e) {
			try {
				return (long) Double.parseDouble(s.trim());
			} catch (Exception ignored) {
				return 0;
			}
		}
	}
}