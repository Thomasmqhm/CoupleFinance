package com.couplefinance.ocr;

import com.couplefinance.utils.ParsedTransaction;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BankScreenshotOcrParser {

    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "([+-]?\\s?[0-9]{1,4}(?:\\s?[0-9]{3})*[\\.,][0-9]{2})\\s*(?:€|EUR)?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "([0-3]?[0-9]/[0-1]?[0-9]/(?:20)?[0-9]{2})"
    );

    private static final Pattern OPERATION_PATTERN = Pattern.compile(
            "(?i).*\\b(CARTE|CB|PRLV|PRELEVEMENT|VIR|VIREMENT)\\b.*"
    );

    public List<ParsedTransaction> parse(String rawText) {
        List<ParsedTransaction> visual = parseUsingOcrCoordinates();

        if (visual.size() >= 3) {
            fixIncomeTypes(visual);
            deduplicate(visual);
            return visual;
        }

        if (rawText == null || rawText.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String raw = normalizeRaw(rawText);
        List<String> lines = cleanLines(raw);

        List<ParsedTransaction> fallback = parseByAmountWindows(lines);

        fixIncomeTypes(fallback);
        deduplicate(fallback);

        return fallback;
    }

    private List<ParsedTransaction> parseUsingOcrCoordinates() {
        List<TesseractOcrEngine.OcrLine> ocrLines = TesseractOcrEngine.getLastLinesSnapshot();
        int imageWidth = TesseractOcrEngine.getLastImageWidth();

        ArrayList<TesseractOcrEngine.OcrLine> lines = new ArrayList<>();

        if (ocrLines != null) {
            for (TesseractOcrEngine.OcrLine line : ocrLines) {
                if (line == null) continue;
                if (line.text == null || line.text.trim().isEmpty()) continue;
                if (isNoiseLine(line.text)) continue;

                lines.add(line);
            }
        }

        if (lines.isEmpty()) {
            return new ArrayList<>();
        }

        ArrayList<Integer> operationIndexes = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i).text;

            if (OPERATION_PATTERN.matcher(text).matches()) {
                operationIndexes.add(i);
            }
        }

        ArrayList<ParsedTransaction> result = new ArrayList<>();

        for (int i = 0; i < operationIndexes.size(); i++) {
            int opIndex = operationIndexes.get(i);
            TesseractOcrEngine.OcrLine operationLine = lines.get(opIndex);

            int previousCenter = i > 0 ? lines.get(operationIndexes.get(i - 1)).centerY() : -999999;
            int nextCenter = i < operationIndexes.size() - 1 ? lines.get(operationIndexes.get(i + 1)).centerY() : 999999;

            int blockTop = previousCenter == -999999
                    ? operationLine.top - 220
                    : (previousCenter + operationLine.centerY()) / 2;

            int blockBottom = nextCenter == 999999
                    ? operationLine.bottom + 260
                    : (operationLine.centerY() + nextCenter) / 2;

            ArrayList<TesseractOcrEngine.OcrLine> block = new ArrayList<>();

            for (TesseractOcrEngine.OcrLine line : lines) {
                int cy = line.centerY();

                if (cy >= blockTop && cy <= blockBottom) {
                    block.add(line);
                }
            }

            ParsedTransaction pt = parseVisualBlock(block, operationLine, imageWidth);

            if (pt != null && !alreadyExists(result, pt)) {
                result.add(pt);
            }
        }

        return result;
    }

    private ParsedTransaction parseVisualBlock(List<TesseractOcrEngine.OcrLine> block,
            TesseractOcrEngine.OcrLine operationLine, int imageWidth) {

        if (block == null || block.isEmpty() || operationLine == null) {
            return null;
        }

        String label = extractMerchant(operationLine.text);
        String category = extractCategoryFromVisualBlock(block);
        String amountRaw = extractBestVisualAmount(block, operationLine, imageWidth);
        long dateMs = extractVisualDateMs(block, operationLine);

        if (amountRaw == null) {
            return null;
        }

        double signedAmount = parseSignedAmount(amountRaw, label, category);

        ParsedTransaction pt = new ParsedTransaction();
        pt.amount = Math.abs(signedAmount);
        pt.type = signedAmount >= 0 ? "income" : "expense";
        pt.dateMs = dateMs;
        pt.label = cleanupLabel(label);

        if (category != null) {
            pt.category = normalizeCategory(category);
        } else {
            pt.category = guessCategory(pt.label);
        }

        if (isIncomeLike(pt.label, pt.category)) {
            pt.type = "income";
            pt.amount = Math.abs(pt.amount);
        }

        pt.selected = true;
        pt.merchantKey = normalize(pt.label);

        return pt;
    }

    private String extractBestVisualAmount(List<TesseractOcrEngine.OcrLine> block,
            TesseractOcrEngine.OcrLine operationLine, int imageWidth) {

        TesseractOcrEngine.OcrLine bestRightLine = null;
        String bestRightAmount = null;
        int bestRightScore = Integer.MAX_VALUE;

        TesseractOcrEngine.OcrLine bestAnyLine = null;
        String bestAnyAmount = null;
        int bestAnyScore = Integer.MAX_VALUE;

        int minRightX = imageWidth > 0 ? (int) (imageWidth * 0.52f) : operationLine.centerX();

        for (TesseractOcrEngine.OcrLine line : block) {
            if (line == null || line.text == null) continue;
            if (looksLikeDateOnly(line.text)) continue;

            Matcher matcher = AMOUNT_PATTERN.matcher(line.text);

            while (matcher.find()) {
                String amount = matcher.group(1);

                if (amount == null) continue;

                int verticalDistance = Math.abs(line.centerY() - operationLine.centerY());
                int horizontalBonus = Math.max(0, line.centerX() - operationLine.centerX());
                int score = verticalDistance - (horizontalBonus / 4);

                if (score < bestAnyScore) {
                    bestAnyScore = score;
                    bestAnyLine = line;
                    bestAnyAmount = amount;
                }

                boolean rightSide = line.centerX() >= minRightX || line.left > operationLine.right;

                if (rightSide && score < bestRightScore) {
                    bestRightScore = score;
                    bestRightLine = line;
                    bestRightAmount = amount;
                }
            }
        }

        if (bestRightLine != null) {
            return bestRightAmount;
        }

        if (bestAnyLine != null) {
            return bestAnyAmount;
        }

        return null;
    }

    private long extractVisualDateMs(List<TesseractOcrEngine.OcrLine> block,
            TesseractOcrEngine.OcrLine operationLine) {

        String bestDate = null;
        int bestScore = Integer.MAX_VALUE;

        for (TesseractOcrEngine.OcrLine line : block) {
            if (line == null || line.text == null) continue;

            Matcher matcher = DATE_PATTERN.matcher(line.text);

            if (matcher.find()) {
                String date = matcher.group(1);
                int score = Math.abs(line.centerY() - operationLine.centerY());

                if (score < bestScore) {
                    bestScore = score;
                    bestDate = date;
                }
            }
        }

        if (bestDate != null) {
            return parseDateMs(bestDate);
        }

        return System.currentTimeMillis();
    }

    private String extractCategoryFromVisualBlock(List<TesseractOcrEngine.OcrLine> block) {
        String best = null;

        for (TesseractOcrEngine.OcrLine line : block) {
            if (line == null || line.text == null) continue;

            String cleaned = removeAmounts(line.text).trim();

            if (isCategory(cleaned)) {
                best = cleaned;
            }
        }

        return best;
    }

    private List<ParsedTransaction> parseByAmountWindows(List<String> lines) {
        List<ParsedTransaction> result = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            if (!containsAmount(line)) continue;

            int from = Math.max(0, i - 5);
            int to = Math.min(lines.size() - 1, i + 5);

            List<String> block = new ArrayList<>();

            for (int j = from; j <= to; j++) {
                String blockLine = lines.get(j);

                if (blockLine == null) continue;
                if (isNoiseLine(blockLine)) continue;

                block.add(blockLine);
            }

            ParsedTransaction pt = parseBlock(block, line);

            if (pt != null && !alreadyExists(result, pt)) {
                result.add(pt);
            }
        }

        return result;
    }

    private ParsedTransaction parseBlock(List<String> block, String amountLine) {
        String amountRaw = extractAmountRaw(amountLine != null ? amountLine : join(block));

        if (amountRaw == null) return null;

        String label = extractLabel(block);
        String category = extractCategory(block);

        double signedAmount = parseSignedAmount(amountRaw, label, category);

        ParsedTransaction pt = new ParsedTransaction();
        pt.amount = Math.abs(signedAmount);
        pt.type = signedAmount >= 0 ? "income" : "expense";
        pt.dateMs = extractDateMs(block);
        pt.label = cleanupLabel(label != null ? label : "Opération bancaire");

        if (category != null) {
            pt.category = normalizeCategory(category);
        } else {
            pt.category = guessCategory(pt.label);
        }

        if (isIncomeLike(pt.label, pt.category)) {
            pt.type = "income";
            pt.amount = Math.abs(pt.amount);
        }

        pt.selected = true;
        pt.merchantKey = normalize(pt.label);

        return pt;
    }

    private String extractLabel(List<String> block) {
        String operationCandidate = null;
        String fallbackCandidate = null;

        for (String line : block) {
            if (line == null) continue;

            String cleaned = line.trim();

            if (cleaned.isEmpty()) continue;
            if (looksLikeDateOnly(cleaned)) continue;
            if (isCategory(cleaned)) continue;
            if (isNoiseLine(cleaned)) continue;

            if (OPERATION_PATTERN.matcher(cleaned).matches()) {
                operationCandidate = cleaned;
                break;
            }

            if (fallbackCandidate == null && !containsAmount(cleaned)) {
                fallbackCandidate = cleaned;
            }
        }

        if (operationCandidate != null) {
            return extractMerchant(operationCandidate);
        }

        if (fallbackCandidate != null) {
            return extractMerchant(fallbackCandidate);
        }

        return null;
    }

    private String extractMerchant(String raw) {
        if (raw == null) return "Opération bancaire";

        String cleaned = raw;

        cleaned = cleaned.replaceAll("(?i)^CARTE\\s+[0-9]{1,2}/[0-9]{1,2}\\s*", "");
        cleaned = cleaned.replaceAll("(?i)^CB\\s+[0-9]{1,2}/[0-9]{1,2}\\s*", "");
        cleaned = cleaned.replaceAll("(?i)^PRLV\\s*", "");
        cleaned = cleaned.replaceAll("(?i)^PRELEVEMENT\\s*", "");
        cleaned = cleaned.replaceAll("(?i)^VIR\\s*", "");
        cleaned = cleaned.replaceAll("(?i)^VIREMENT\\s*", "");

        cleaned = AMOUNT_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replace("EUR", "").replace("eur", "");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        String n = normalize(cleaned);

        if (n.contains("burger king")) return "Burger King";
        if (n.contains("mcdonald")) return "McDonald's";
        if (n.contains("kfc")) return "KFC";

        if (n.contains("leclerc")) return "E.Leclerc";
        if (n.contains("lidl")) return "Lidl";
        if (n.contains("carrefour")) return "Carrefour";
        if (n.contains("intermarche")) return "Intermarché";
        if (n.contains("auchan")) return "Auchan";

        if (n.contains("amazon prime")) return "Amazon Prime";
        if (n.contains("amazon")) return "Amazon";

        if (n.contains("pharma") || n.contains("pharmacie")) return "Pharmacie";
        if (n.contains("relay")) return "Relay";

        if (n.contains("edf")) return "EDF";
        if (n.contains("saur")) return "SAUR";
        if (n.contains("engie")) return "Engie";
        if (n.contains("bouygues")) return "Bouygues Telecom";

        if (n.contains("bnp paribas")) return "BNP Paribas Personal Finance";
        if (n.contains("diac")) return "DIAC";
        if (n.contains("suravenir")) return "Suravenir";

        if (n.contains("caf")) return "CAF des Côtes d'Armor";
        if (n.contains("dinan") || n.contains("eureka")) return "DINAN/EUREKA";

        if (n.contains("compte joint")) return "vers COMPTE JOINT";
        if (n.contains("brive")) return "Brive la Gaillarde";
        if (n.contains("sumup")) return "Paiement SumUp";
        if (n.contains("paddington")) return "Paddington Saint Vran";
        if (n.contains("sncf")) return "SNCF";
        if (n.contains("electra")) return "Electra Paris";
        if (n.contains("marcelano")) return "MARCELANO VERTOU";
        if (n.contains("octopus")) return "OCTOPUS";

        return cleaned.isEmpty() ? "Opération bancaire" : cleaned;
    }

    private String extractCategory(List<String> block) {
        String best = null;

        for (String line : block) {
            if (line == null) continue;

            String cleaned = removeAmounts(line).trim();

            if (isCategory(cleaned)) {
                best = cleaned;
            }
        }

        return best;
    }

    private String normalizeCategory(String category) {
        String c = normalize(category);

        if (c.contains("course")) return "Courses";
        if (c.contains("restaurant")) return "Restaurants";
        if (c.contains("hotel") || c.contains("bar")) return "Restaurants";
        if (c.contains("sante")) return "Santé";
        if (c.contains("energie")) return "Energie";
        if (c.contains("loisir")) return "Loisirs";
        if (c.contains("transport")) return "Transport";
        if (c.contains("shopping")) return "Shopping";
        if (c.contains("pret")) return "Autres prêts";
        if (c.contains("salaire")) return "Revenus";
        if (c.contains("pension")) return "Revenus";
        if (c.contains("retraite")) return "Revenus";
        if (c.contains("allocation")) return "Revenus";
        if (c.contains("virement")) return "Virements";
        if (c.contains("mouvement")) return "Mouvements internes";
        if (c.contains("abonnement")) return "Abonnements";
        if (c.contains("assurance")) return "Assurances";
        if (c.contains("facture")) return "Factures";
        if (c.contains("autres depenses")) return "Autre";

        return "Autre";
    }

    private String guessCategory(String label) {
        String l = normalize(label);

        if (l.contains("burger")
                || l.contains("restaurant")
                || l.contains("mcdonald")
                || l.contains("kfc")
                || l.contains("pizza")
                || l.contains("paddington")) {
            return "Restaurants";
        }

        if (l.contains("lidl")
                || l.contains("carrefour")
                || l.contains("leclerc")
                || l.contains("intermarche")
                || l.contains("auchan")
                || l.contains("brive")
                || l.contains("sumup")) {
            return "Courses";
        }

        if (l.contains("edf") || l.contains("saur") || l.contains("engie")) {
            return "Energie";
        }

        if (l.contains("pharma")) return "Santé";
        if (l.contains("relay")) return "Loisirs";

        if (l.contains("bnp paribas") || l.contains("diac")) {
            return "Autres prêts";
        }

        if (l.contains("bouygues") || l.contains("amazon prime")) {
            return "Abonnements";
        }

        if (l.contains("suravenir")) return "Assurances";

        if (l.contains("compte joint")) {
            return "Mouvements internes";
        }

        if (l.contains("caf") || l.contains("dinan") || l.contains("eureka")) {
            return "Revenus";
        }

        if (l.contains("electra") || l.contains("octopus")) {
            return "Transport";
        }

        return "Autre";
    }

    private String extractAmountRaw(String text) {
        if (text == null) return null;

        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        String raw = null;

        while (matcher.find()) {
            raw = matcher.group(1);
        }

        return raw;
    }

    private double parseSignedAmount(String raw) {
        return parseSignedAmount(raw, "", "");
    }

    private double parseSignedAmount(String raw, String label, String category) {
        if (raw == null) return 0;

        String compact = raw.replace(" ", "").trim();

        String value = compact
                .replace(",", ".")
                .replace("+", "")
                .replace("-", "");

        double parsed;

        try {
            parsed = Double.parseDouble(value);
        } catch (Exception e) {
            parsed = 0;
        }

        if (compact.contains("+") || isIncomeLike(label, category)) {
            return Math.abs(parsed);
        }

        return -Math.abs(parsed);
    }

    private boolean isIncomeLike(String label, String category) {
        String l = normalize(label);
        String c = normalize(category);

        return l.contains("caf")
                || l.contains("dinan")
                || l.contains("eureka")
                || c.contains("salaire")
                || c.contains("pension")
                || c.contains("retraite")
                || c.contains("allocation")
                || c.contains("revenu");
    }

    private void fixIncomeTypes(List<ParsedTransaction> list) {
        if (list == null) return;

        for (ParsedTransaction pt : list) {
            if (pt == null) continue;

            if (isIncomeLike(pt.label, pt.category)) {
                pt.type = "income";
                pt.amount = Math.abs(pt.amount);
            }
        }
    }

    private long extractDateMs(List<String> block) {
        for (String line : block) {
            if (line == null) continue;

            Matcher matcher = DATE_PATTERN.matcher(line);

            if (matcher.find()) {
                return parseDateMs(matcher.group(1));
            }
        }

        return System.currentTimeMillis();
    }

    private long parseDateMs(String rawDate) {
        try {
            String[] split = rawDate.split("/");

            int day = Integer.parseInt(split[0]);
            int month = Integer.parseInt(split[1]);
            int year = Integer.parseInt(split[2]);

            if (year < 100) {
                year += 2000;
            }

            Calendar c = Calendar.getInstance();
            c.set(Calendar.YEAR, year);
            c.set(Calendar.MONTH, month - 1);
            c.set(Calendar.DAY_OF_MONTH, day);
            c.set(Calendar.HOUR_OF_DAY, 12);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);

            return c.getTimeInMillis();

        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private boolean containsAmount(String line) {
        return line != null && AMOUNT_PATTERN.matcher(line).find();
    }

    private boolean looksLikeDateOnly(String text) {
        return text != null && DATE_PATTERN.matcher(text.trim()).matches();
    }

    private boolean isCategory(String text) {
        if (text == null) return false;

        String n = normalize(removeAmounts(text));

        return n.equals("courses")
                || n.equals("loisirs")
                || n.equals("sante")
                || n.equals("restaurants")
                || n.equals("restaurant")
                || n.equals("hotels bars restaurants")
                || n.equals("transport")
                || n.equals("energie")
                || n.equals("shopping")
                || n.equals("maison")
                || n.equals("salaire")
                || n.equals("salaires")
                || n.equals("pensions")
                || n.equals("retraites")
                || n.equals("allocations")
                || n.equals("virement")
                || n.equals("abonnement")
                || n.equals("abonnements")
                || n.equals("prets")
                || n.equals("autres prets")
                || n.equals("factures")
                || n.equals("mouvements internes")
                || n.equals("autres depenses")
                || n.contains("banque assurances mutuelles")
                || n.contains("remboursements sante")
                || n.contains("salaire pensions retraites");
    }

    private String removeAmounts(String text) {
        if (text == null) return "";

        return AMOUNT_PATTERN.matcher(text)
                .replaceAll("")
                .replace("EUR", "")
                .replace("eur", "")
                .trim();
    }

    private boolean alreadyExists(List<ParsedTransaction> list, ParsedTransaction candidate) {
        String key = buildKey(candidate);

        for (ParsedTransaction pt : list) {
            if (buildKey(pt).equals(key)) {
                return true;
            }
        }

        return false;
    }

    private void deduplicate(List<ParsedTransaction> list) {
        Set<String> keys = new LinkedHashSet<>();

        for (ParsedTransaction pt : list) {
            String key = buildKey(pt);

            if (keys.contains(key)) {
                pt.duplicate = true;
                pt.selected = false;
                pt.duplicateReason = "Doublon probable détecté dans l'import OCR.";
                pt.duplicateWarning = "Doublon probable";
            } else {
                keys.add(key);
            }
        }
    }

    private String buildKey(ParsedTransaction pt) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(pt.dateMs);

        return c.get(Calendar.YEAR) + "-"
                + c.get(Calendar.MONTH) + "-"
                + c.get(Calendar.DAY_OF_MONTH) + "_"
                + Math.round(pt.amount * 100) + "_"
                + normalize(pt.label);
    }

    private List<String> cleanLines(String rawText) {
        String[] rawLines = rawText.split("\n");
        List<String> lines = new ArrayList<>();

        for (String line : rawLines) {
            if (line == null) continue;

            String cleaned = line
                    .replaceAll("\\s+", " ")
                    .trim();

            if (cleaned.isEmpty()) continue;
            if (cleaned.length() < 2) continue;
            if (isNoiseLine(cleaned)) continue;

            lines.add(cleaned);
        }

        return lines;
    }

    private String normalizeRaw(String rawText) {
        return rawText
                .replace("\r", "")
                .replace("€", " EUR ")
                .replace("–", "-")
                .replace("—", "-")
                .replace("−", "-");
    }

    private boolean isNoiseLine(String line) {
        String l = normalize(line);

        return l.contains("mes comptes")
                || l.contains("deconnexion")
                || l.equals("menu")
                || l.equals("accueil")
                || l.equals("comptes")
                || l.equals("virement")
                || l.equals("virtualis")
                || l.equals("cartes")
                || l.contains("google play")
                || l.contains("credit mutuel de bretagne")
                || l.contains("suivi de compte")
                || l.contains("utiliser")
                || l.contains("la semaine derniere")
                || l.contains("plus tot en mai")
                || l.contains("gratuit")
                || l.contains("dans google play");
    }

    private String join(List<String> list) {
        StringBuilder sb = new StringBuilder();

        for (String s : list) {
            if (s != null && !s.trim().isEmpty()) {
                sb.append(s.trim()).append(" ");
            }
        }

        return sb.toString().trim();
    }

    private String cleanupLabel(String label) {
        if (label == null || label.trim().isEmpty()) {
            return "Opération bancaire";
        }

        label = label.replaceAll("\\s+", " ").trim();

        if (label.length() > 72) {
            label = label.substring(0, 72).trim();
        }

        return label;
    }

    private String normalize(String s) {
        if (s == null) return "";

        s = s.toLowerCase(Locale.getDefault());

        s = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "");

        return s.trim();
    }
}