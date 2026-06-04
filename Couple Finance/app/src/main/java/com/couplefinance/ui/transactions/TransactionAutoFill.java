package com.couplefinance.ui.transactions;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Moteur d'auto-remplissage local.
 *
 * V1 :
 * - apprend depuis l'historique déjà chargé dans TransactionsView ;
 * - complète avec des règles françaises courantes ;
 * - ne touche pas Firestore ;
 * - ne dépend pas d'Android pour pouvoir être réutilisé par l'import PDF.
 */
public final class TransactionAutoFill {

	private TransactionAutoFill() {
	}

	public static TransactionSuggestion suggest(String rawInput, ArrayList<String[]> history,
			ArrayList<String[]> categories, ArrayList<String> persons) {
		String normalizedInput = normalize(rawInput);

		if (normalizedInput.length() < 3) {
			return TransactionSuggestion.none();
		}

		TransactionSuggestion learned = suggestFromHistory(rawInput, normalizedInput, history);
		TransactionSuggestion rules = suggestFromRules(rawInput, normalizedInput, categories, persons);

		if (learned.found && rules.found) {
			return learned.confidence >= rules.confidence ? learned : rules;
		}

		if (learned.found)
			return learned;
		if (rules.found)
			return rules;

		return TransactionSuggestion.none();
	}

	private static TransactionSuggestion suggestFromHistory(String rawInput, String normalizedInput,
			ArrayList<String[]> history) {
		if (history == null || history.isEmpty())
			return TransactionSuggestion.none();

		Map<String, Stats> statsByKey = new HashMap<>();

		for (String[] tx : history) {
			if (tx == null || tx.length < 3)
				continue;

			String fullLabel = safe(tx[0]);
			String description = extractDescription(fullLabel);
			String normalizedDescription = normalize(description);

			if (normalizedDescription.length() < 3)
				continue;

			int match = matchScore(normalizedInput, normalizedDescription);

			if (match <= 0)
				continue;

			String amountText = tx.length > 1 ? safe(tx[1]).replace(",", ".") : "";
			double amount = parseDouble(amountText);
			String type = tx.length > 2 ? safe(tx[2]) : "variable";
			String category = tx.length > 3 ? safe(tx[3]) : "Sans catégorie";
			String person = extractPerson(fullLabel);
			String cleanKey = normalize(category) + "|" + normalize(type) + "|" + normalize(person);

			Stats stats = statsByKey.get(cleanKey);
			if (stats == null) {
				stats = new Stats();
				statsByKey.put(cleanKey, stats);
			}

			stats.count++;
			stats.totalAmount += Math.max(0d, amount);
			stats.bestMatch = Math.max(stats.bestMatch, match);
			stats.lastDescription = description;
			stats.type = type;
			stats.category = category;
			stats.person = person;
		}

		Stats best = null;

		for (Stats stats : statsByKey.values()) {
			if (best == null || stats.weight() > best.weight()) {
				best = stats;
			}
		}

		if (best == null)
			return TransactionSuggestion.none();

		int confidence = Math.min(96, 50 + best.bestMatch + Math.min(20, best.count * 5));
		double average = best.count <= 0 ? 0d : best.totalAmount / best.count;

		return TransactionSuggestion.of(rawInput, best.lastDescription, normalizeType(best.type),
				normalizeCategory(best.category), best.person, average, confidence, "Basé sur ton historique");
	}

	private static TransactionSuggestion suggestFromRules(String rawInput, String normalizedInput,
			ArrayList<String[]> categories, ArrayList<String> persons) {
		String type = "variable";
		String category = "Sans catégorie";
		int confidence = 0;

		Rule[] rules = new Rule[] {

				new Rule("income", "Salaire", 95, "salaire", "paie", "payroll", "remuneration", "vir salaire"),
				new Rule("income", "Aides", 92, "caf", "apl", "prime activite", "aide"),
				new Rule("income", "Remboursement", 90, "cpam", "ameli", "secu", "remboursement", "mutuelle"),

				new Rule("fixed", "Loyer", 95, "loyer", "bail", "agence"),
				new Rule("fixed", "Crédit", 95, "credit", "pret", "emprunt"),
				new Rule("fixed", "EDF", 95, "edf", "engie", "total energies", "electricite"),
				new Rule("fixed", "EAU", 95, "saur", "veolia", "suez", "eau"),
				new Rule("fixed", "Internet", 92, "orange", "sfr", "free", "bouygues", "fibre", "internet"),
				new Rule("fixed", "Téléphone", 90, "forfait", "mobile", "free mobile"),
				new Rule("fixed", "Assurance", 92, "assurance", "maif", "macif", "axa", "allianz", "gmf"),
				new Rule("fixed", "Mutuelle", 92, "apicil", "harmonie", "mutuelle", "sante"),
				new Rule("fixed", "Abonnements", 95, "netflix", "spotify", "deezer", "amazon prime", "prime video",
						"disney", "disney+", "youtube premium", "canal+", "occs", "apple music", "icloud",
						"google one"),

				new Rule("variable", "Courses", 95, "carrefour", "leclerc", "centre leclerc", "intermarche", "lidl",
						"aldi", "super u", "u express", "auchan", "netto", "casino", "spar", "biocoop", "grand frais"),
				new Rule("variable", "Restauration", 92, "restaurant", "resto", "bar", "brasserie", "snack", "mcdonald",
						"mcdo", "burger king", "kfc", "subway", "pizza hut", "dominos", "tacos", "boulangerie",
						"uber eats", "deliveroo"),
				new Rule("variable", "Transport", 90, "sncf", "ratp", "bus", "metro", "uber", "bolt"),
				new Rule("variable", "Carburant", 95, "total", "total energies", "esso", "bp", "avias",
						"station service"),
				new Rule("variable", "Recharge électrique", 95, "tesla", "ionity", "iecharge", "powerdot",
						"lidl charge", "freshmile", "electra"),
				new Rule("variable", "Péage", 92, "peage", "vinci autoroutes"),
				new Rule("variable", "Santé", 90, "pharmacie", "docteur", "medecin", "dentiste", "opticien"),
				new Rule("variable", "Shopping", 80, "zara", "h&m", "kiabi", "celio", "primark", "vinted", "shein",
						"zalando", "decathlon", "intersport", "fnac", "darty", "boulanger", "cultura"),
				new Rule("variable", "Shopping en ligne", 60, "amazon", "cdiscount", "aliexpress"),

				new Rule("saving", "Épargne", 95, "livret a", "epargne", "ldds"),
				new Rule("saving", "Investissement", 90, "boursorama", "trade republic", "binance", "coinbase"),

				new Rule("transfer", "Virement", 70, "virement", "vir", "transfer")};

		for (Rule rule : rules) {
			if (rule.matches(normalizedInput) && rule.confidence > confidence) {
				type = rule.type;
				category = bestExistingCategory(rule.category, categories);
				confidence = rule.confidence;
			}
		}

		if (confidence <= 0)
			return TransactionSuggestion.none();

		String person = "";
		if (persons != null && !persons.isEmpty())
			person = persons.get(0);

		String cleanLabel = cleanLabel(rawInput);
		return TransactionSuggestion.of(rawInput, cleanLabel, type, category, person, 0d, confidence,
				"Reconnu automatiquement");
	}

	private static String bestExistingCategory(String wanted, ArrayList<String[]> categories) {
		if (wanted == null || wanted.trim().isEmpty())
			return "Sans catégorie";

		if (categories == null || categories.isEmpty())
			return wanted;

		String normalizedWanted = normalize(wanted);

		for (String[] category : categories) {
			if (category == null || category.length < 2)
				continue;

			String name = safe(category[1]);
			if (normalize(name).equals(normalizedWanted))
				return name;
		}

		for (String[] category : categories) {
			if (category == null || category.length < 2)
				continue;

			String name = safe(category[1]);
			String normalizedName = normalize(name);

			if (normalizedName.contains(normalizedWanted) || normalizedWanted.contains(normalizedName))
				return name;
		}

		return wanted;
	}

	private static int matchScore(String input, String label) {
		if (input.equals(label))
			return 40;
		if (label.contains(input))
			return 34;
		if (input.contains(label) && label.length() >= 4)
			return 30;

		String[] inputWords = input.split(" ");
		String[] labelWords = label.split(" ");
		int score = 0;

		for (String iw : inputWords) {
			if (iw.length() < 3)
				continue;

			for (String lw : labelWords) {
				if (lw.length() < 3)
					continue;

				if (iw.equals(lw))
					score += 12;
				else if (lw.contains(iw) || iw.contains(lw))
					score += 8;
			}
		}

		return score;
	}

	private static String normalizeType(String type) {
		if ("income".equals(type))
			return "income";
		if ("fixed".equals(type))
			return "fixed";
		return "variable";
	}

	private static String normalizeCategory(String category) {
		if (category == null || category.trim().isEmpty())
			return "Sans catégorie";
		return category.trim();
	}

	private static String cleanLabel(String raw) {
		String value = safe(raw).trim();
		if (value.isEmpty())
			return "";

		String lower = value.toLowerCase(Locale.FRANCE);
		if (lower.length() <= 3)
			return value.toUpperCase(Locale.FRANCE);

		return value.substring(0, 1).toUpperCase(Locale.FRANCE) + value.substring(1);
	}

	private static String extractPerson(String fullLabel) {
		String label = safe(fullLabel);
		int idx = label.indexOf(" · ");
		if (idx > 0)
			return label.substring(0, idx).trim();
		return "";
	}

	private static String extractDescription(String fullLabel) {
		String label = safe(fullLabel);
		int idx = label.indexOf(" · ");
		if (idx >= 0 && idx + 3 < label.length())
			return label.substring(idx + 3).trim();
		return label.trim();
	}

	private static double parseDouble(String value) {
		try {
			return Double.parseDouble(value);
		} catch (Exception e) {
			return 0d;
		}
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	private static String normalize(String value) {
		String safe = safe(value).toLowerCase(Locale.FRANCE).trim();
		String withoutAccent = Normalizer.normalize(safe, Normalizer.Form.NFD)
				.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
		return withoutAccent.replaceAll("[^a-z0-9]+", " ").trim().replaceAll(" +", " ");
	}

	private static final class Rule {
		final String type;
		final String category;
		final int confidence;
		final String[] keywords;

		Rule(String type, String category, int confidence, String... keywords) {
			this.type = type;
			this.category = category;
			this.confidence = confidence;
			this.keywords = keywords;
		}

		boolean matches(String normalizedInput) {
			for (String keyword : keywords) {
				String normalizedKeyword = normalize(keyword);
				if (normalizedInput.contains(normalizedKeyword) || normalizedKeyword.contains(normalizedInput))
					return true;
			}
			return false;
		}
	}

	private static final class Stats {
		int count;
		int bestMatch;
		double totalAmount;
		String lastDescription = "";
		String type = "variable";
		String category = "Sans catégorie";
		String person = "";

		int weight() {
			return bestMatch + Math.min(30, count * 8);
		}
	}
}
