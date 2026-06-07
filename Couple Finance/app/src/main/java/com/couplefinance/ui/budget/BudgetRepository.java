package com.couplefinance.ui.budget;

import com.couplefinance.data.CategoryManager;
import com.couplefinance.data.CycleManager;
import com.couplefinance.data.FirestoreManager;
import com.couplefinance.data.TransactionManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

public class BudgetRepository {

	public interface Callback {
		void onResult(List<BudgetModels.CategoryBudget> list);
		void onError(String error);
	}

	public interface SaveCallback {
		void onSuccess();
		void onError(String error);
	}

	public interface CategoryNamesCallback {
		void onResult(List<String> categories);
		void onError(String error);
	}

	public static void loadBudgets(Callback callback) {
		FirestoreManager fm   = FirestoreManager.getInstance();
		String           base = fm.getHouseholdPath();

		fm.getCollection(base + "/budgets", new FirestoreManager.Callback() {
			@Override
			public void onSuccess(String budgetResponse) {
				Map<String, Double> budgetMap = parseBudgets(budgetResponse);

				TransactionManager.getInstance().getTransactions(new FirestoreManager.Callback() {
					@Override
					public void onSuccess(String txResponse) {
						Map<String, Double> spentMap     = parseTransactions(txResponse);
						Map<String, Double> prevSpentMap = parsePrevMonthTransactions(txResponse);

						ArrayList<BudgetModels.CategoryBudget> result = new ArrayList<>();
						LinkedHashSet<String> categories = new LinkedHashSet<>();

						categories.addAll(budgetMap.keySet());
						categories.addAll(spentMap.keySet());

						for (String category : categories) {
							if (category == null || category.trim().isEmpty()) continue;
							double spent  = spentMap.containsKey(category)     ? spentMap.get(category)     : 0;
							double budget = budgetMap.containsKey(category)    ? budgetMap.get(category)    : 0;
							double prev   = prevSpentMap.containsKey(category) ? prevSpentMap.get(category) : 0;
							BudgetModels.CategoryBudget cb = new BudgetModels.CategoryBudget(
									makeId(category), category, spent, budget);
							cb.prevMonthSpent = prev;
							result.add(cb);
						}

						Collections.sort(result, (a, b) -> Double.compare(b.spent, a.spent));
						syncBudgetCategoriesToSettings(result);
						callback.onResult(result);
					}

					@Override
					public void onError(String error) { callback.onError(error); }
				});
			}

			@Override
			public void onError(String error) { callback.onError(error); }
		});
	}

	public static void saveBudget(String categoryName, double budget, SaveCallback callback) {
		if (categoryName == null || categoryName.trim().isEmpty()) {
			callback.onError("Nom de catégorie invalide");
			return;
		}

		final String cleanName = categoryName.trim();

		saveBudgetOnly(cleanName, budget, new SaveCallback() {
			@Override
			public void onSuccess() { ensureExpenseCategoryExists(cleanName, callback); }

			@Override
			public void onError(String error) { callback.onError(error); }
		});
	}

	private static void saveBudgetOnly(String categoryName, double budget, SaveCallback callback) {
		FirestoreManager fm   = FirestoreManager.getInstance();
		String path = fm.getHouseholdPath() + "/budgets/" + makeId(categoryName);

		String body = "{\"fields\":{"
				+ "\"name\":{\"stringValue\":\""     + fm.escape(categoryName) + "\"},"
				+ "\"category\":{\"stringValue\":\"" + fm.escape(categoryName) + "\"},"
				+ "\"budget\":{\"doubleValue\":"      + budget + "},"
				+ "\"updatedAt\":{\"integerValue\":\"" + System.currentTimeMillis() + "\"}"
				+ "}}";

		fm.patchDocument(path, body, null, new FirestoreManager.Callback() {
			@Override
			public void onSuccess(String response) { callback.onSuccess(); }

			@Override
			public void onError(String error) { callback.onError(error); }
		});
	}

	public static void loadExpenseCategories(CategoryNamesCallback callback) {
		CategoryManager.getInstance().getCategories(new FirestoreManager.Callback() {
			@Override
			public void onSuccess(String response) {
				ArrayList<String> result = new ArrayList<>();
				try {
					JSONObject root = new JSONObject(response);
					JSONArray  docs = root.optJSONArray("documents");
					if (docs != null) {
						for (int i = 0; i < docs.length(); i++) {
							JSONObject fields = docs.getJSONObject(i).optJSONObject("fields");
							if (fields == null) continue;
							String name = readString(fields, "name");
							String type = readString(fields, "type");
							if (name == null || name.trim().isEmpty()) continue;
							String cleanName = name.replace("|expense", "").trim();
							if (name.contains("|expense") || type.equalsIgnoreCase("expense")) {
								if (!result.contains(cleanName)) result.add(cleanName);
							}
						}
					}
				} catch (Exception ignored) {}
				callback.onResult(result);
			}

			@Override
			public void onError(String error) { callback.onError(error); }
		});
	}

	// ─────────────────────────────────────────────────────────────
	// Parsing
	// ─────────────────────────────────────────────────────────────

	private static Map<String, Double> parseBudgets(String response) {
		Map<String, Double> map = new LinkedHashMap<>();
		try {
			JSONObject root = new JSONObject(response);
			JSONArray  docs = root.optJSONArray("documents");
			if (docs == null) return map;

			for (int i = 0; i < docs.length(); i++) {
				JSONObject fields = docs.getJSONObject(i).optJSONObject("fields");
				if (fields == null) continue;
				String name   = firstString(fields, "name", "category", "categorie", "label", "title");
				double budget = firstDouble(fields, "budget", "amount", "limit", "montant");
				if (!name.isEmpty()) map.put(cleanCategoryName(name), budget);
			}
		} catch (Exception ignored) {}
		return map;
	}

	private static Map<String, Double> parseTransactions(String response) {
		Map<String, Double> map = new LinkedHashMap<>();
		try {
			JSONObject root = new JSONObject(response);
			JSONArray  docs = root.optJSONArray("documents");
			if (docs == null) return map;

			for (int i = 0; i < docs.length(); i++) {
				JSONObject fields = docs.getJSONObject(i).optJSONObject("fields");
				if (fields == null) continue;

				String type = firstString(fields, "type", "transactionType").toLowerCase(Locale.FRANCE);
				if (type.contains("income") || type.contains("revenu") || type.contains("recette")) {
					continue;
				}

				// ── Filtre par cycle — délègue à CycleManager ──────────────────
				if (!isInCurrentCycle(fields)) continue;

				String category = firstString(fields, "category", "categorie",
						"categoryName", "cat", "expenseCategory");
				if (category.isEmpty()) category = "Sans catégorie";
				category = cleanCategoryName(category);

				double amount = firstDouble(fields, "amount", "montant", "value", "prix", "total");
				amount = Math.abs(amount);
				if (amount <= 0) continue;

				double old = map.containsKey(category) ? map.get(category) : 0;
				map.put(category, old + amount);
			}
		} catch (Exception ignored) {}
		return map;
	}

	/** Dépenses du mois civil précédent par catégorie (pour calcul de tendance). */
	private static Map<String, Double> parsePrevMonthTransactions(String response) {
		Map<String, Double> map = new LinkedHashMap<>();
		try {
			java.util.Calendar cal = java.util.Calendar.getInstance();
			cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
			cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
			cal.set(java.util.Calendar.MINUTE, 0);
			cal.set(java.util.Calendar.SECOND, 0);
			cal.set(java.util.Calendar.MILLISECOND, 0);
			long thisMonthStart = cal.getTimeInMillis();
			cal.add(java.util.Calendar.MONTH, -1);
			long prevMonthStart = cal.getTimeInMillis();

			JSONObject root = new JSONObject(response);
			JSONArray  docs = root.optJSONArray("documents");
			if (docs == null) return map;

			for (int i = 0; i < docs.length(); i++) {
				JSONObject fields = docs.getJSONObject(i).optJSONObject("fields");
				if (fields == null) continue;

				String type = firstString(fields, "type", "transactionType").toLowerCase(Locale.FRANCE);
				if (type.contains("income") || type.contains("revenu") || type.contains("recette")) continue;

				long dateMs = 0;
				try { dateMs = Long.parseLong(firstString(fields, "date", "displayDate", "createdAtText", "dateText", "day", "createdAt")); } catch (Exception ignored) {}
				if (dateMs < prevMonthStart || dateMs >= thisMonthStart) continue;

				String category = firstString(fields, "category", "categorie", "categoryName", "cat", "expenseCategory");
				if (category.isEmpty()) category = "Sans catégorie";
				category = cleanCategoryName(category);

				double amount = Math.abs(firstDouble(fields, "amount", "montant", "value", "prix", "total"));
				if (amount <= 0) continue;

				map.put(category, (map.containsKey(category) ? map.get(category) : 0) + amount);
			}
		} catch (Exception ignored) {}
		return map;
	}

	/**
	 * Vérifie si une transaction appartient au cycle financier courant.
	 *
	 * Ancienne logique : comparaison Calendar.MONTH == currentMonth.
	 * Nouvelle logique : délègue à CycleManager.isInCurrentCycle(dateMs).
	 *
	 * Rétrocompatibilité :
	 *   Si le champ date est absent ou illisible, on inclut la transaction
	 *   (comportement inchangé par rapport à l'ancienne implémentation).
	 */
	private static boolean isInCurrentCycle(JSONObject fields) {
		String rawDate = firstString(fields,
				"date", "displayDate", "createdAtText", "dateText", "day", "createdAt")
				.toLowerCase(Locale.FRANCE).trim();

		if (rawDate.isEmpty()) return true;

		// ── Cas principal : timestamp millis ────────────────────────────────
		try {
			long millis = Long.parseLong(rawDate);
			return CycleManager.getInstance().isInCurrentCycle(millis);
		} catch (Exception ignored) {}

		// ── Fallback : formats texte (yyyy-MM-dd, dd/MM/yyyy, nom de mois) ─
		// On reconstruit un millis approximatif et on délègue à CycleManager.
		long approxMillis = parseTextDate(rawDate);
		if (approxMillis > 0) {
			return CycleManager.getInstance().isInCurrentCycle(approxMillis);
		}

		// Impossible à parser : inclure par défaut
		return true;
	}

	/**
	 * Tente de parser un timestamp en millis depuis un format texte courant.
	 * Retourne 0 si la date ne peut pas être déterminée.
	 */
	private static long parseTextDate(String raw) {
		if (raw == null || raw.isEmpty()) return 0;

		// Format : "2026-06-10" ou "2026-06-10T..."
		if (raw.length() >= 10 && raw.charAt(4) == '-') {
			try {
				int year  = Integer.parseInt(raw.substring(0, 4));
				int month = Integer.parseInt(raw.substring(5, 7)) - 1;
				int day   = Integer.parseInt(raw.substring(8, 10));
				java.util.Calendar c = java.util.Calendar.getInstance();
				c.set(year, month, day, 12, 0, 0);
				c.set(java.util.Calendar.MILLISECOND, 0);
				return c.getTimeInMillis();
			} catch (Exception ignored) {}
		}

		// Format : "10/06/2026" ou "10/6/2026"
		String[] parts = raw.split("/");
		if (parts.length == 3) {
			try {
				int day   = Integer.parseInt(parts[0].trim());
				int month = Integer.parseInt(parts[1].trim()) - 1;
				int year  = Integer.parseInt(parts[2].trim());
				java.util.Calendar c = java.util.Calendar.getInstance();
				c.set(year, month, day, 12, 0, 0);
				c.set(java.util.Calendar.MILLISECOND, 0);
				return c.getTimeInMillis();
			} catch (Exception ignored) {}
		}

		return 0;
	}

	// ─────────────────────────────────────────────────────────────
	// Catégories — helpers
	// ─────────────────────────────────────────────────────────────

	private static void ensureExpenseCategoryExists(String categoryName, SaveCallback callback) {
		CategoryManager.getInstance().getCategories(new FirestoreManager.Callback() {
			@Override
			public void onSuccess(String response) {
				if (categoryAlreadyExists(response, categoryName)) {
					callback.onSuccess();
					return;
				}
				CategoryManager.getInstance().addCategory(
						categoryName, getEmoji(categoryName),
						new FirestoreManager.Callback() {
							@Override public void onSuccess(String r) { callback.onSuccess(); }
							@Override public void onError(String e)   { callback.onError(e);   }
						});
			}

			@Override
			public void onError(String error) { callback.onError(error); }
		});
	}

	private static boolean categoryAlreadyExists(String response, String categoryName) {
		try {
			JSONObject root = new JSONObject(response);
			JSONArray  docs = root.optJSONArray("documents");
			if (docs == null) return false;
			for (int i = 0; i < docs.length(); i++) {
				JSONObject fields = docs.getJSONObject(i).optJSONObject("fields");
				if (fields == null) continue;
				String name = cleanCategoryName(readString(fields, "name"));
				if (name.equalsIgnoreCase(cleanCategoryName(categoryName))) return true;
			}
		} catch (Exception ignored) {}
		return false;
	}

	private static void syncBudgetCategoriesToSettings(List<BudgetModels.CategoryBudget> list) {
		for (BudgetModels.CategoryBudget item : list) {
			if (item == null || item.name == null) continue;
			String name = cleanCategoryName(item.name);
			if (name.isEmpty() || name.equalsIgnoreCase("Sans catégorie")) continue;
			ensureExpenseCategoryExists(name, new SaveCallback() {
				@Override public void onSuccess() {}
				@Override public void onError(String error) {}
			});
		}
	}

	public static void deleteBudget(String categoryName, SaveCallback callback) {
		if (categoryName == null || categoryName.trim().isEmpty()) {
			callback.onError("Budget invalide");
			return;
		}
		FirestoreManager fm   = FirestoreManager.getInstance();
		String path = fm.getHouseholdPath() + "/budgets/" + makeId(categoryName);
		fm.deleteDocument(path, new FirestoreManager.Callback() {
			@Override public void onSuccess(String response) { callback.onSuccess(); }
			@Override public void onError(String error)      { callback.onError(error); }
		});
	}

	// ─────────────────────────────────────────────────────────────
	// Helpers JSON
	// ─────────────────────────────────────────────────────────────

	private static String firstString(JSONObject fields, String... keys) {
		for (String key : keys) {
			String value = readString(fields, key);
			if (value != null && !value.trim().isEmpty()) return value.trim();
		}
		return "";
	}

	private static double firstDouble(JSONObject fields, String... keys) {
		for (String key : keys) {
			double value = readDouble(fields, key);
			if (Math.abs(value) > 0.0001) return value;
		}
		return 0;
	}

	private static String readString(JSONObject fields, String key) {
		try {
			if (!fields.has(key)) return "";
			JSONObject f = fields.optJSONObject(key);
			if (f == null) return "";
			if (f.has("stringValue"))    return f.optString("stringValue", "").trim();
			if (f.has("integerValue"))   return f.optString("integerValue", "").trim();
			if (f.has("doubleValue"))    return String.valueOf(f.optDouble("doubleValue"));
			if (f.has("timestampValue")) return f.optString("timestampValue", "").trim();
			if (f.has("booleanValue"))   return String.valueOf(f.optBoolean("booleanValue"));
		} catch (Exception ignored) {}
		return "";
	}

	private static double readDouble(JSONObject fields, String key) {
		try {
			if (!fields.has(key)) return 0;
			JSONObject f = fields.optJSONObject(key);
			if (f == null) return 0;
			if (f.has("doubleValue"))  return f.optDouble("doubleValue", 0);
			if (f.has("integerValue")) return Double.parseDouble(f.optString("integerValue", "0"));
			if (f.has("stringValue"))  return Double.parseDouble(
					f.optString("stringValue", "0").replace("€", "").replace(" ", "").replace(",", "."));
		} catch (Exception ignored) {}
		return 0;
	}

	// ─────────────────────────────────────────────────────────────
	// Helpers noms
	// ─────────────────────────────────────────────────────────────

	private static String cleanCategoryName(String value) {
		if (value == null) return "";
		return value.replace("|expense", "").replace("|income", "").trim();
	}

	private static String makeId(String value) {
		if (value == null || value.trim().isEmpty()) return "sans_categorie";
		String s = value.toLowerCase(Locale.FRANCE)
				.replace("à","a").replace("â","a").replace("ä","a")
				.replace("é","e").replace("è","e").replace("ê","e").replace("ë","e")
				.replace("î","i").replace("ï","i")
				.replace("ô","o").replace("ö","o")
				.replace("ù","u").replace("û","u").replace("ü","u")
				.replace("ç","c")
				.replaceAll("[^a-z0-9]+","_")
				.replaceAll("^_+","").replaceAll("_+$","");
		return s.isEmpty() ? "sans_categorie" : s;
	}

	private static String getEmoji(String name) {
		String n = name == null ? "" : name.toLowerCase(Locale.FRANCE);
		if (n.contains("loyer"))                      return "🏠";
		if (n.contains("course") || n.contains("aliment")) return "🛒";
		if (n.contains("transport") || n.contains("essence")) return "🚗";
		if (n.contains("restaurant"))                 return "🍽";
		if (n.contains("loisir"))                     return "🎮";
		if (n.contains("abonnement"))                 return "📱";
		if (n.contains("santé") || n.contains("sante")) return "💊";
		if (n.contains("assurance"))                  return "🛡";
		return "💸";
	}
}
