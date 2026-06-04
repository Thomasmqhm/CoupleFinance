package com.couplefinance.ocr;

import com.couplefinance.utils.ParsedTransaction;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReceiptOcrParser — extraction d'une transaction depuis un ticket de caisse.
 *
 * <p>Un ticket de caisse correspond à <b>une seule dépense</b> : le parser
 * produit donc une liste contenant au plus une {@link ParsedTransaction}.</p>
 *
 * <h3>Heuristiques</h3>
 * <ul>
 *   <li><b>Montant total</b> : repéré via les mots-clés "TOTAL", "MONTANT",
 *       "CB", "CARTE BANCAIRE", "NET A PAYER". À défaut, le plus grand
 *       montant plausible du ticket.</li>
 *   <li><b>Date</b> : premier format jj/mm/aaaa ou jj-mm-aa rencontré.</li>
 *   <li><b>Magasin</b> : enseigne reconnue ({@link OcrMerchantRules}) ou
 *       première ligne significative du ticket.</li>
 *   <li><b>Catégorie</b> : déduite de l'enseigne.</li>
 * </ul>
 *
 * <p>Le parser reste robuste face à un OCR imparfait : il ne renvoie jamais
 * de transaction si aucun montant crédible n'a pu être détecté.</p>
 */
public final class ReceiptOcrParser {

	// jj/mm/aaaa, jj-mm-aaaa, jj.mm.aa …
	private static final Pattern DATE_PATTERN = Pattern.compile(
			"\\b(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{2,4})\\b");

	// Montant : 12,34 / 12.34 / 1 234,56 …
	private static final Pattern AMOUNT_PATTERN = Pattern.compile(
			"(\\d{1,3}(?:[ \\u00A0]\\d{3})*|\\d+)[,.](\\d{2})\\b");

	// Mots-clés indiquant le total à payer (priorité haute).
	private static final String[] TOTAL_KEYWORDS = {
			"net a payer", "net à payer", "total a payer", "total à payer",
			"montant du", "montant dû", "total ttc", "total", "montant",
			"carte bancaire", "paiement cb", " cb ", "cb "
	};

	/**
	 * Analyse le texte OCR d'un ticket de caisse.
	 *
	 * @param rawText texte brut reconnu.
	 * @return liste contenant 0 ou 1 transaction.
	 */
	public List<ParsedTransaction> parse(String rawText) {
		List<ParsedTransaction> result = new ArrayList<>();
		if (rawText == null || rawText.trim().isEmpty()) {
			return result;
		}

		String text = rawText.replace("\r\n", "\n").replace("\r", "\n");
		String[] lines = text.split("\n");

		double amount = detectTotal(lines);
		if (amount <= 0) {
			// Pas de total fiable → on ne devine pas, on laisse vide.
			return result;
		}

		long dateMs = detectDate(text);
		String merchant = detectMerchant(lines);
		String category = OcrMerchantRules.guessCategory(text);

		// Si la catégorie n'a rien donné mais qu'on a un magasin, on retente
		// la catégorisation sur le seul nom du magasin.
		if ("Autre".equals(category) && !merchant.isEmpty()) {
			category = OcrMerchantRules.guessCategory(merchant);
		}

		String label = merchant.isEmpty() ? "Ticket de caisse" : merchant;

		ParsedTransaction tx = new ParsedTransaction(label, amount, "expense", category, dateMs);
		tx.merchantKey = OcrMerchantRules.normalize(merchant.isEmpty() ? label : merchant);
		tx.selected = true;

		result.add(tx);
		return result;
	}

	// ─────────────────────────────────────────────────────────────
	// Détection du total
	// ─────────────────────────────────────────────────────────────

	private double detectTotal(String[] lines) {
		double keywordAmount = -1;
		double maxAmount = -1;

		for (String rawLine : lines) {
			if (rawLine == null) {
				continue;
			}
			String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}

			String normalized = OcrMerchantRules.normalize(line);
			double lineMax = maxAmountOnLine(line);

			if (lineMax > maxAmount) {
				maxAmount = lineMax;
			}

			if (lineMax > 0 && lineContainsTotalKeyword(normalized)) {
				// On garde le dernier total mot-clé rencontré : sur un ticket,
				// "TOTAL" apparaît généralement après les sous-totaux.
				keywordAmount = lineMax;
			}
		}

		if (keywordAmount > 0) {
			return round2(keywordAmount);
		}
		// Repli : le plus gros montant du ticket est presque toujours le total.
		return maxAmount > 0 ? round2(maxAmount) : -1;
	}

	private boolean lineContainsTotalKeyword(String normalizedLine) {
		for (String keyword : TOTAL_KEYWORDS) {
			if (normalizedLine.contains(keyword.trim())) {
				return true;
			}
		}
		return false;
	}

	private double maxAmountOnLine(String line) {
		double max = -1;
		Matcher m = AMOUNT_PATTERN.matcher(line);
		while (m.find()) {
			double value = parseAmount(m.group(1), m.group(2));
			if (value > max) {
				max = value;
			}
		}
		return max;
	}

	// ─────────────────────────────────────────────────────────────
	// Détection de la date
	// ─────────────────────────────────────────────────────────────

	private long detectDate(String text) {
		Matcher m = DATE_PATTERN.matcher(text);
		while (m.find()) {
			Long ts = toTimestamp(m.group(1), m.group(2), m.group(3));
			if (ts != null) {
				return ts;
			}
		}
		// Aucune date lisible → date du jour (modifiable dans la preview).
		return System.currentTimeMillis();
	}

	private Long toTimestamp(String dayStr, String monthStr, String yearStr) {
		try {
			int day = Integer.parseInt(dayStr);
			int month = Integer.parseInt(monthStr);
			int year = Integer.parseInt(yearStr);

			if (year < 100) {
				year += 2000;
			}

			if (day < 1 || day > 31 || month < 1 || month > 12) {
				return null;
			}
			if (year < 2000 || year > 2100) {
				return null;
			}

			Calendar c = Calendar.getInstance();
			c.clear();
			c.set(year, month - 1, day, 12, 0, 0);
			return c.getTimeInMillis();
		} catch (Exception e) {
			return null;
		}
	}

	// ─────────────────────────────────────────────────────────────
	// Détection du magasin
	// ─────────────────────────────────────────────────────────────

	private String detectMerchant(String[] lines) {
		// 1) Enseigne connue n'importe où dans le ticket.
		StringBuilder all = new StringBuilder();
		for (String line : lines) {
			if (line != null) {
				all.append(line).append(' ');
			}
		}
		String known = OcrMerchantRules.guessMerchantName(all.toString());
		if (!known.isEmpty()) {
			return known;
		}

		// 2) Première ligne "significative" du ticket (souvent l'enseigne).
		for (String rawLine : lines) {
			if (rawLine == null) {
				continue;
			}
			String line = rawLine.trim();
			if (isMeaningfulHeaderLine(line)) {
				return prettify(line);
			}
		}

		return "";
	}

	private boolean isMeaningfulHeaderLine(String line) {
		if (line == null) {
			return false;
		}
		String clean = line.trim();
		if (clean.length() < 3 || clean.length() > 32) {
			return false;
		}
		// Doit contenir des lettres et ne pas être surtout des chiffres.
		int letters = 0;
		int digits = 0;
		for (char c : clean.toCharArray()) {
			if (Character.isLetter(c)) {
				letters++;
			} else if (Character.isDigit(c)) {
				digits++;
			}
		}
		if (letters < 3 || digits > letters) {
			return false;
		}
		// Exclure les en-têtes administratifs courants.
		String n = OcrMerchantRules.normalize(clean);
		return !n.contains("ticket") && !n.contains("siret") && !n.contains("tva")
				&& !n.contains("merci") && !n.contains("caisse");
	}

	private String prettify(String value) {
		String clean = value.trim().replaceAll("\\s+", " ");
		if (clean.isEmpty()) {
			return clean;
		}
		// Capitalise la première lettre pour un rendu propre.
		return clean.substring(0, 1).toUpperCase(Locale.FRANCE) + clean.substring(1);
	}

	// ─────────────────────────────────────────────────────────────
	// Helpers numériques
	// ─────────────────────────────────────────────────────────────

	private double parseAmount(String intPart, String decPart) {
		try {
			String cleanInt = intPart.replace(" ", "").replace("\u00A0", "");
			return Double.parseDouble(cleanInt + "." + decPart);
		} catch (Exception e) {
			return -1;
		}
	}

	private double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}
