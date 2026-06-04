package com.couplefinance.ui.transactions;

import java.util.ArrayList;
import java.util.List;

public final class TransactionsParser {

	private TransactionsParser() {}

	public static List<TransactionsModels.Transaction> parseTransactions(String json) {
		List<TransactionsModels.Transaction> result = new ArrayList<>();

		if (json == null || json.trim().isEmpty())
			return result;

		String[] docs = json.split("\"fields\"\\s*:");

		for (int i = 1; i < docs.length; i++) {
			String doc = docs[i];

			String label = extractFirestoreString(doc, "label");
			if (label.trim().isEmpty())
				continue;

			String docId = extractDocIdNearby(json, label);

			double amount = extractFirestoreDouble(doc, "amount");
			String type = normalizeType(extractFirestoreString(doc, "type"));
			String category = extractFirestoreString(doc, "category");
			long dateMs = extractFirestoreLong(doc, "date");
			long addedMs = extractFirestoreLong(doc, "addedMs");
			if (addedMs <= 0)
				addedMs = extractFirestoreLong(doc, "addedAt");

			String person = extractFirestoreString(doc, "person");
			String compte = extractFirestoreString(doc, "compte");

			if ((person == null || person.trim().isEmpty()) && label.contains(" · ")) {
				person = label.split(" · ")[0].trim();
			}

			boolean shared = extractFirestoreBool(doc, "shared");
			boolean isShareSplit = extractFirestoreBool(doc, "isShareSplit");
			boolean isReimbursement = extractFirestoreBool(doc, "isReimbursement");

			result.add(new TransactionsModels.Transaction(
					label,
					amount,
					type,
					category,
					dateMs,
					addedMs,
					person == null ? "" : person.trim(),
					shared,
					isShareSplit,
					isReimbursement,
					docId,
					compte == null ? "" : compte.trim()
			));
		}

		return result;
	}

	public static List<String> parseMembers(String json) {
		List<String> names = new ArrayList<>();

		if (json == null || json.trim().isEmpty())
			return names;

		String[] parts = json.split("\"fields\"\\s*:");

		for (int i = 1; i < parts.length; i++) {
			String name = extractFirestoreString(parts[i], "name");

			if (!name.isEmpty()
					&& !"Moi".equalsIgnoreCase(name)
					&& !"null".equalsIgnoreCase(name)
					&& !containsIgnoreCase(names, name)) {
				names.add(capitalize(name.trim()));
			}
		}

		return names;
	}

	public static List<String[]> parseCategories(String json) {
		List<String[]> result = new ArrayList<>();

		if (json == null || json.trim().isEmpty())
			return result;

		String[] parts = json.split("\"fields\"\\s*:");

		for (int i = 1; i < parts.length; i++) {
			String p = parts[i];

			String name = extractFirestoreString(p, "name");
			String emoji = extractFirestoreString(p, "emoji");
			String type = extractFirestoreString(p, "type");

			if (emoji.isEmpty())
				emoji = "📦";

			if (type.isEmpty())
				type = "variable";

			if (!name.isEmpty())
				result.add(new String[] { name, emoji, type });
		}

		return result;
	}

	private static String extractFirestoreString(String json, String fieldName) {
		String block = extractFieldBlock(json, fieldName);
		if (block.isEmpty())
			return "";

		String value = extractStr(block, "stringValue");
		if (!value.isEmpty())
			return value;

		value = extractStr(block, "integerValue");
		if (!value.isEmpty())
			return value;

		value = extractStr(block, "doubleValue");
		if (!value.isEmpty())
			return value;

		value = extractNum(block, "integerValue");
		if (!"0".equals(value))
			return value;

		value = extractNum(block, "doubleValue");
		if (!"0".equals(value))
			return value;

		return "";
	}

	private static double extractFirestoreDouble(String json, String fieldName) {
		String block = extractFieldBlock(json, fieldName);
		if (block.isEmpty())
			return 0;

		String value = extractStr(block, "doubleValue");
		if (value.isEmpty())
			value = extractStr(block, "integerValue");
		if (value.isEmpty())
			value = extractNum(block, "doubleValue");
		if ("0".equals(value))
			value = extractNum(block, "integerValue");
		if ("0".equals(value))
			value = extractStr(block, "stringValue");

		return parseD(value);
	}

	private static long extractFirestoreLong(String json, String fieldName) {
		String block = extractFieldBlock(json, fieldName);
		if (block.isEmpty())
			return 0;

		String value = extractStr(block, "integerValue");
		if (value.isEmpty())
			value = extractStr(block, "doubleValue");
		if (value.isEmpty())
			value = extractNum(block, "integerValue");
		if ("0".equals(value))
			value = extractNum(block, "doubleValue");
		if ("0".equals(value))
			value = extractStr(block, "stringValue");

		return parseLong(value);
	}

	private static boolean extractFirestoreBool(String json, String fieldName) {
		String block = extractFieldBlock(json, fieldName);
		if (block.isEmpty())
			return false;

		return block.contains("\"booleanValue\":true")
				|| block.contains("\"booleanValue\": true");
	}

	private static String extractFieldBlock(String json, String fieldName) {
		if (json == null || fieldName == null)
			return "";

		String marker = "\"" + fieldName + "\"";
		int start = json.indexOf(marker);

		if (start < 0)
			return "";

		int braceStart = json.indexOf("{", start);
		if (braceStart < 0)
			return json.substring(start);

		int depth = 0;
		for (int i = braceStart; i < json.length(); i++) {
			char c = json.charAt(i);
			if (c == '{')
				depth++;
			else if (c == '}') {
				depth--;
				if (depth <= 0)
					return json.substring(start, i + 1);
			}
		}

		return json.substring(start);
	}

	private static String normalizeType(String type) {
		if (type == null || type.trim().isEmpty())
			return "variable";

		String t = type.trim();

		if ("expense".equalsIgnoreCase(t))
			return "variable";

		if ("fixed_done".equalsIgnoreCase(t))
			return "fixed";

		if ("fixed_planned".equalsIgnoreCase(t))
			return "fixed";

		return t;
	}

	private static String extractDocIdNearby(String fullJson, String label) {
		try {
			int labelIndex = fullJson.indexOf(label);
			if (labelIndex < 0)
				return "";

			int nameIndex = fullJson.lastIndexOf("\"name\"", labelIndex);
			if (nameIndex < 0)
				return "";

			int firstQuote = fullJson.indexOf("\"", nameIndex + 6);
			int secondQuote = fullJson.indexOf("\"", firstQuote + 1);
			if (firstQuote < 0 || secondQuote <= firstQuote)
				return "";

			String path = fullJson.substring(firstQuote + 1, secondQuote);
			int slash = path.lastIndexOf('/');
			return slash >= 0 ? path.substring(slash + 1) : "";
		} catch (Exception e) {
			return "";
		}
	}

	static String extractStr(String json, String key) {
		if (json == null || key == null)
			return "";

		String[] patterns = {
				"\"" + key + "\": \"",
				"\"" + key + "\":\""
		};

		for (String pattern : patterns) {
			int i = json.indexOf(pattern);
			if (i >= 0) {
				int start = i + pattern.length();
				int end = json.indexOf("\"", start);
				if (end > start)
					return json.substring(start, end).trim();
			}
		}

		return "";
	}

	static String extractNum(String json, String key) {
		if (json == null || key == null)
			return "0";

		String[] patterns = {
				"\"" + key + "\": \"",
				"\"" + key + "\":\"",
				"\"" + key + "\": ",
				"\"" + key + "\":"
		};

		for (String pattern : patterns) {
			int i = json.indexOf(pattern);

			if (i >= 0) {
				int start = i + pattern.length();
				String rest = json.substring(start).trim();

				if (rest.startsWith("\"")) {
					int end = rest.indexOf("\"", 1);
					if (end > 1)
						return rest.substring(1, end).trim();
				}

				int end = 0;
				while (end < rest.length()) {
					char c = rest.charAt(end);
					if (!Character.isDigit(c) && c != '.' && c != '-' && c != '+')
						break;
					end++;
				}

				if (end > 0)
					return rest.substring(0, end).trim();
			}
		}

		return "0";
	}

	static double parseD(String s) {
		if (s == null)
			return 0;

		try {
			return Double.parseDouble(s.trim().replace(",", "."));
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
			} catch (Exception e2) {
				return 0;
			}
		}
	}

	private static boolean containsIgnoreCase(List<String> list, String value) {
		if (list == null || value == null)
			return false;

		for (String s : list) {
			if (s != null && s.equalsIgnoreCase(value))
				return true;
		}

		return false;
	}

	private static String capitalize(String s) {
		if (s == null || s.isEmpty())
			return s;

		return s.substring(0, 1).toUpperCase(java.util.Locale.FRANCE) + s.substring(1);
	}
}