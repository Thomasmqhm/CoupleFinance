package com.couplefinance.ui.repartition;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public final class RepartitionParser {

	private RepartitionParser() {
	}

	public static List<RepartitionModels.SharedTransaction> parseTransactions(String json) {
		List<RepartitionModels.SharedTransaction> result = new ArrayList<>();
		if (json == null || json.isEmpty())
			return result;

		String[] parts = json.split("\"fields\":");

		for (int i = 1; i < parts.length; i++) {
			String p = parts[i];

			String label = p.contains("\"label\"") ? extractStr(p.substring(p.indexOf("\"label\"")), "stringValue")
					: "";

			String amount = p.contains("\"amount\"") ? extractNum(p.substring(p.indexOf("\"amount\"")), "doubleValue")
					: "0";

			String type = p.contains("\"type\"") ? extractStr(p.substring(p.indexOf("\"type\"")), "stringValue") : "";

			String date = p.contains("\"date\"") ? extractNum(p.substring(p.indexOf("\"date\"")), "integerValue") : "0";

			String cat = p.contains("\"category\"") ? extractStr(p.substring(p.indexOf("\"category\"")), "stringValue")
					: "";

			String person = p.contains("\"person\"") ? extractStr(p.substring(p.indexOf("\"person\"")), "stringValue")
					: "";

			boolean shared = extractBool(p, "shared");
			boolean isShareSplit = extractBool(p, "isShareSplit");
			boolean isReimb = extractBool(p, "isReimbursement");

			if (label.isEmpty())
				continue;

			String normalizedLabel = label;

			if (shared && !person.trim().isEmpty() && !label.contains(" · ")) {
				normalizedLabel = capitalize(person.trim()) + " · " + label.trim();
			}

			if ("income".equalsIgnoreCase(type)) {
				continue;
			}

			if (!shared && !isShareSplit && !isReimb) {
				continue;
			}

			result.add(new RepartitionModels.SharedTransaction(normalizedLabel, parseD(amount), type, parseLong(date),
					cat, isShareSplit, isReimb, shared));
		}

		return result;
	}

	public static List<String> parseMembers(String json) {
		List<String> names = new ArrayList<>();
		if (json == null || json.isEmpty())
			return names;

		String[] parts = json.split("\"fields\":");

		for (int i = 1; i < parts.length; i++) {
			if (parts[i].contains("\"name\"")) {
				String name = extractStr(parts[i].substring(parts[i].indexOf("\"name\"")), "stringValue");
				if (!name.isEmpty())
					names.add(capitalize(name));
			}
		}

		return names;
	}

	public static String[] buildMonthLabels() {
		String[] labels = new String[RepartitionModels.MonthHistory.SIZE];
		Calendar now = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("MMM", Locale.FRANCE);

		for (int m = 0; m < RepartitionModels.MonthHistory.SIZE; m++) {
			Calendar c = (Calendar) now.clone();
			c.add(Calendar.MONTH, -(RepartitionModels.MonthHistory.SIZE - 1 - m));

			String raw = sdf.format(c.getTime());
			labels[m] = capitalize(raw.length() > 3 ? raw.substring(0, 3) + "." : raw);
		}

		return labels;
	}

	static String extractStr(String json, String key) {
		for (String s : new String[] { "\"" + key + "\": \"", "\"" + key + "\":\"" }) {
			int i = json.indexOf(s);

			if (i >= 0) {
				int st = i + s.length();
				int e = json.indexOf("\"", st);

				if (e > st) {
					return json.substring(st, e).trim();
				}
			}
		}

		return "";
	}

	static String extractNum(String json, String key) {
		for (String s : new String[] { "\"" + key + "\": ", "\"" + key + "\":" }) {
			int i = json.indexOf(s);

			if (i >= 0) {
				String rest = json.substring(i + s.length()).trim();

				if (rest.startsWith("\"")) {
					int e = rest.indexOf("\"", 1);
					return e > 1 ? rest.substring(1, e) : "0";
				}

				int e = 0;

				while (e < rest.length()
						&& (Character.isDigit(rest.charAt(e)) || rest.charAt(e) == '.' || rest.charAt(e) == '-')) {
					e++;
				}

				if (e > 0) {
					return rest.substring(0, e);
				}
			}
		}

		return "0";
	}

	static boolean extractBool(String json, String key) {
		int idx = json.indexOf("\"" + key + "\"");

		if (idx < 0)
			return false;

		String sub = json.substring(idx, Math.min(idx + 100, json.length()));

		return sub.contains("\"booleanValue\":true") || sub.contains("\"booleanValue\": true");
	}

	static double parseD(String s) {
		try {
			return Double.parseDouble(s.replace(",", "."));
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
			return 0;
		}
	}

	private static String capitalize(String s) {
		if (s == null || s.isEmpty())
			return s;

		return s.substring(0, 1).toUpperCase(Locale.FRANCE) + s.substring(1);
	}
}