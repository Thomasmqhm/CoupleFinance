package com.couplefinance.ocr;

import java.text.Normalizer;
import java.util.Locale;

/**
 * OcrMerchantRules — règles intelligentes de reconnaissance des marchands
 * et de catégorisation, partagées par les parsers ticket et screenshot.
 *
 * <p>Les règles sont volontairement simples et lisibles : enseigne détectée
 * dans le texte → catégorie probable + libellé propre. L'utilisateur peut
 * toujours corriger dans la modale de prévisualisation.</p>
 */
public final class OcrMerchantRules {

	private OcrMerchantRules() {
	}

	/**
	 * Devine une catégorie de dépense à partir d'un libellé / texte marchand.
	 * Retourne "Autre" si rien n'est reconnu.
	 */
	public static String guessCategory(String text) {
		String n = normalize(text);
		if (n.isEmpty()) {
			return "Autre";
		}

		// ── Alimentation / courses ──────────────────────────────
		if (containsAny(n, "lidl", "carrefour", "leclerc", "intermarche", "auchan",
				"super u", "hyper u", "casino", "monoprix", "franprix", "aldi",
				"netto", "cora", "grand frais", "biocoop", "picard")) {
			return "Alimentation";
		}

		// ── Restaurants / fast-food ─────────────────────────────
		if (containsAny(n, "mcdonald", "mcdo", "burger king", "kfc", "subway",
				"restaurant", "pizza", "brasserie", "boulangerie", "starbucks",
				"quick", "domino")) {
			return "Restaurants";
		}

		// ── Transports / carburant ──────────────────────────────
		if (containsAny(n, "total", "totalenergies", "esso", "bp ", "shell",
				"sncf", "ratp", "uber", "blablacar", "essence", "carburant",
				"station", "péage", "peage", "parking", "navigo")) {
			return "Transports";
		}

		// ── Énergie / logement ──────────────────────────────────
		if (containsAny(n, "edf", "engie", "totalenergies elec", "enercoop",
				"veolia", "saur", "suez", "loyer")) {
			return "Logement";
		}

		// ── Télécom / abonnements ───────────────────────────────
		if (containsAny(n, "free ", "orange", "sfr", "bouygues", "sosh",
				"netflix", "spotify", "disney", "amazon prime", "canal",
				"deezer", "youtube premium", "abonnement")) {
			return "Abonnements";
		}

		// ── Achats en ligne / divers ────────────────────────────
		if (containsAny(n, "amazon", "fnac", "darty", "cdiscount", "zalando",
				"aliexpress", "vinted", "leboncoin")) {
			return "Achats";
		}

		// ── Santé ───────────────────────────────────────────────
		if (containsAny(n, "pharmacie", "docteur", "medecin", "mutuelle",
				"laboratoire", "dentiste", "opticien", "hopital")) {
			return "Santé";
		}

		// ── Banque / frais ──────────────────────────────────────
		if (containsAny(n, "bnp", "credit mutuel", "cmb", "credit agricole",
				"caisse epargne", "la banque postale", "societe generale",
				"frais bancaire", "cotisation", "agios")) {
			return "Banque";
		}

		// ── Loisirs ─────────────────────────────────────────────
		if (containsAny(n, "cinema", "decathlon", "fitness", "salle de sport",
				"jeu", "steam", "playstation", "nintendo")) {
			return "Loisirs";
		}

		return "Autre";
	}

	/**
	 * Tente d'extraire un nom d'enseigne propre depuis un texte de ticket.
	 * Retourne une chaîne vide si rien d'évident n'est trouvé.
	 */
	public static String guessMerchantName(String text) {
		String n = normalize(text);

		String[][] known = {
				{"lidl", "Lidl"},
				{"carrefour", "Carrefour"},
				{"leclerc", "E.Leclerc"},
				{"intermarche", "Intermarché"},
				{"auchan", "Auchan"},
				{"monoprix", "Monoprix"},
				{"franprix", "Franprix"},
				{"casino", "Casino"},
				{"super u", "Super U"},
				{"hyper u", "Hyper U"},
				{"aldi", "Aldi"},
				{"picard", "Picard"},
				{"mcdonald", "McDonald's"},
				{"mcdo", "McDonald's"},
				{"burger king", "Burger King"},
				{"kfc", "KFC"},
				{"subway", "Subway"},
				{"starbucks", "Starbucks"},
				{"decathlon", "Decathlon"},
				{"fnac", "Fnac"},
				{"darty", "Darty"},
				{"amazon", "Amazon"},
				{"total", "TotalEnergies"},
				{"sncf", "SNCF"},
				{"pharmacie", "Pharmacie"},
		};

		for (String[] entry : known) {
			if (n.contains(entry[0])) {
				return entry[1];
			}
		}

		return "";
	}

	/**
	 * Devine une catégorie de revenu pour les virements/salaires détectés
	 * dans un screenshot bancaire.
	 */
	public static String guessIncomeCategory(String text) {
		String n = normalize(text);
		if (containsAny(n, "salaire", "paie", "remuneration", "paye")) {
			return "Salaire";
		}
		if (containsAny(n, "caf", "allocation", "pole emploi", "france travail")) {
			return "Allocations";
		}
		if (containsAny(n, "remboursement", "cpam", "secu", "mutuelle")) {
			return "Remboursement";
		}
		if (containsAny(n, "virement")) {
			return "Virements";
		}
		return "Revenus";
	}

	// ─────────────────────────────────────────────────────────────
	// Helpers
	// ─────────────────────────────────────────────────────────────

	/** Normalise : minuscules, sans accents, espaces compactés. */
	public static String normalize(String value) {
		if (value == null) {
			return "";
		}
		String lower = value.toLowerCase(Locale.FRANCE).trim();
		String noAccent = Normalizer.normalize(lower, Normalizer.Form.NFD)
				.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
		return noAccent.replaceAll("\\s+", " ").trim();
	}

	private static boolean containsAny(String haystack, String... needles) {
		if (haystack == null) {
			return false;
		}
		for (String needle : needles) {
			if (needle != null && !needle.isEmpty() && haystack.contains(needle)) {
				return true;
			}
		}
		return false;
	}
}
