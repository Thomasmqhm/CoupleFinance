package com.couplefinance.utils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser intelligent pour relevés PDF Crédit Mutuel / CMB.
 *
 * Objectifs :
 * - garder uniquement les vraies lignes d'opérations ;
 * - nettoyer les libellés banque trop longs ;
 * - catégoriser avec des règles métier réalistes ;
 * - fournir une merchantKey stable pour anti-doublons et charges fixes.
 */
public class PdfTransactionParser {

    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(20\\d{2})\\b");
    private static final Pattern DATE_SHORT_PATTERN = Pattern.compile("^\\s*(\\d{1,2})/(\\d{1,2})\\s+(?:(\\d{1,2})/(\\d{1,2})/(20\\d{2})\\s+)?(.+)$");
    private static final Pattern AMOUNT_AT_END_PATTERN = Pattern.compile("(.+?)\\s+([+-]?\\d{1,3}(?:[ \\u00A0.]\\d{3})*(?:,\\d{2})|[+-]?\\d+(?:,\\d{2}))\\s*(?:€)?\\s*$");

    private static final String SECTION_INCOME = "income";
    private static final String SECTION_EXPENSE = "expense";

    public List<ParsedTransaction> parse(String rawText) {
        List<ParsedTransaction> transactions = new ArrayList<>();
        if (rawText == null || rawText.trim().isEmpty()) return transactions;

        String text = rawText.replace("\r\n", "\n").replace("\r", "\n");
        int statementYear = detectStatementYear(text);
        String currentSection = SECTION_EXPENSE;
        Set<String> seenInsidePdf = new LinkedHashSet<>();

        String[] lines = text.split("\n");
        for (String rawLine : lines) {
            String line = cleanLine(rawLine);
            if (line.length() == 0) continue;

            String upper = normalize(line).toUpperCase(Locale.FRENCH);

            if (upper.contains("VIREMENTS RECUS")) {
                currentSection = SECTION_INCOME;
                continue;
            }
            if (upper.contains("VIREMENTS EMIS") || upper.contains("PRELEVEMENTS")
                    || upper.contains("PAIEMENTS PAR CARTE") || upper.contains("SERVICES ET FRAIS")) {
                currentSection = SECTION_EXPENSE;
                continue;
            }
            if (shouldIgnoreLine(upper)) continue;

            ParsedTransaction tx = parseCmbLine(line, currentSection, statementYear);
            if (tx == null) continue;

            String key = tx.dateMs + "|" + tx.type + "|" + cents(tx.amount) + "|" + tx.merchantKey;
            if (seenInsidePdf.add(key)) {
                transactions.add(tx);
            }
        }
        return transactions;
    }

    private ParsedTransaction parseCmbLine(String line, String currentSection, int statementYear) {
        Matcher mDate = DATE_SHORT_PATTERN.matcher(line);
        if (!mDate.find()) return null;

        int day = safeInt(mDate.group(1), -1);
        int month = safeInt(mDate.group(2), -1);
        int year = statementYear;
        if (mDate.group(5) != null) year = safeInt(mDate.group(5), statementYear);

        String rest = mDate.group(6) == null ? "" : cleanLine(mDate.group(6));
        if (rest.length() == 0) return null;

        Matcher mAmount = AMOUNT_AT_END_PATTERN.matcher(rest);
        if (!mAmount.find()) return null;

        String rawLabel = cleanLine(mAmount.group(1));
        double amount = parseAmount(mAmount.group(2));
        if (amount <= 0.0) return null;

        String type = detectType(rawLabel, currentSection);
        String label = cleanLabel(rawLabel);
        String category = detectCategory(rawLabel, label, type);

        if (day < 1 || day > 31 || month < 1 || month > 12) return null;

        long dateMs = makeDateMs(day, month, year);
        ParsedTransaction tx = new ParsedTransaction(label, amount, type, category, dateMs);
        tx.merchantKey = merchantKey(label);
        tx.recurringCandidate = isRecurringCandidate(label, category, type);
        return tx;
    }

    private int detectStatementYear(String text) {
        Matcher m = YEAR_PATTERN.matcher(text);
        int year = Calendar.getInstance().get(Calendar.YEAR);
        while (m.find()) {
            int candidate = safeInt(m.group(1), year);
            if (candidate >= 2020 && candidate <= 2099) year = candidate;
        }
        return year;
    }

    private boolean shouldIgnoreLine(String upper) {
        if (upper.contains("ANCIEN SOLDE") || upper.contains("NOUVEAU SOLDE")) return true;
        if (upper.contains("TOTAL DES OPERATIONS") || upper.contains("TOTAL FACTURE")) return true;
        if (upper.contains("SOUS-TOTAL")) return true;
        if (upper.contains("DATE DE VALEUR") || upper.equals("DATE")) return true;
        if (upper.contains("RELEVE DE COMPTE") || upper.contains("TITULAIRE")) return true;
        if (upper.contains("NUMERO DE COMPTE") || upper.contains("IBAN")) return true;
        if (upper.contains("GARANTIE") || upper.contains("FGDR")) return true;
        if (upper.contains("PLAFOND DE VOTRE AUTORISATION")) return true;
        if (upper.contains("COMPTE COURANT")) return true;
        if (upper.contains("FR37ZZZ") || upper.contains("FR86ZZZ") || upper.contains("FR35ZZZ")) return true;
        if (upper.matches(".*[A-Z]{2}\\d{2}ZZZ.*")) return true;
        return false;
    }

    private String detectType(String rawLabel, String currentSection) {
        String n = normalize(rawLabel).toLowerCase(Locale.FRENCH);
        if (n.startsWith("vir de ") || n.contains(" salaire") || n.contains("dinan/eureka")
                || n.contains("caf") || n.contains("cpam") || n.contains("remboursement")) return "income";
        if (n.startsWith("vir vers") || n.startsWith("prlv") || n.startsWith("carte")
                || n.startsWith("f ") || n.contains("commission") || n.contains("cotisation")) return "expense";
        return SECTION_INCOME.equals(currentSection) ? "income" : "expense";
    }

    private String detectCategory(String rawLabel, String cleanLabel, String type) {
        String n = normalize((rawLabel == null ? "" : rawLabel) + " " + (cleanLabel == null ? "" : cleanLabel)).toLowerCase(Locale.FRENCH);

        if ("income".equals(type)) return "Revenus";

        // Ordre important : les banques/crédits passent avant les mots génériques.
        if (containsAny(n, "suravenir", "cotisation eurocompte", "commission d'intervention", "frais prlv", "frais bancaire", "frais"))
            return "Frais bancaires";
        if (containsAny(n, "diac", "bnp paribas personal finance", "personal finance", "cofidis", "cetelem", "younited", "credit auto", "credit conso"))
            return "Crédit";
        if (containsAny(n, "relay", "relais h", "tabac", "presse", "fdj", "pmu", "paddington"))
            return "Tabac";
        if (containsAny(n, "edf", "engie", "saur", "veolia", "eau", "electricite", "loyer", "assurance habitation"))
            return "Logement";
        if (containsAny(n, "bouygues", "orange", "sfr", "free mobile", "freebox", "amazon prime", "google play", "netflix", "spotify", "disney", "boardgamearena", "apple", "youtube", "canal", "deezer"))
            return "Abonnements";
        if (containsAny(n, "lidl", "leclerc", "super u", "netto", "carrefour", "intermarche", "auchan", "aldi", "casino", "courses"))
            return "Alimentation";
        if (containsAny(n, "parking", "sncf", "total", "esso", "shell", "carburant", "essence", "tesla", "powerdot", "ionity", "charge"))
            return "Transport";
        if (containsAny(n, "pharmacie", "medecin", "hopital", "clinique", "mutuelle", "apicil"))
            return "Santé";
        if (containsAny(n, "central bar", "jean bart", "paddington", "restaurant", "bar ", "pizza", "burger", "mcdonald", "kebab", "cafe"))
            return "Sorties";
        return "Autre";
    }

    private boolean isRecurringCandidate(String label, String category, String type) {
        if (!"expense".equals(type)) return false;
        String n = normalize(label).toLowerCase(Locale.FRENCH);
        if (containsAny(n, "prelevement", "prlv", "edf", "saur", "bouygues", "orange", "sfr", "free", "diac", "bnp paribas", "suravenir", "amazon prime", "google play", "netflix", "spotify", "disney", "boardgamearena", "apicil")) return true;
        return containsAny(category == null ? "" : category.toLowerCase(Locale.FRENCH), "abonnements", "logement", "credit", "frais bancaires", "sante");
    }

    private String cleanLabel(String raw) {
        String original = cleanLine(raw);
        String n = normalize(original).toLowerCase(Locale.FRENCH);

        // Règles ultra ciblées : affichage court, propre, stable d'un mois à l'autre.
        if (n.contains("edf clients particuliers")) return "Prélèvement - EDF";
        if (n.contains("prlv sepa saur") || n.matches(".*\\bsaur\\b.*")) return "Prélèvement - SAUR";
        if (n.contains("prlv sepa diac") || n.matches(".*\\bdiac\\b.*")) return "Prélèvement - DIAC";
        if (n.contains("bnp paribas personal finance")) return "Prélèvement - BNP Paribas Personal Finance";
        if (n.contains("suravenir")) return "Prélèvement - SURAVENIR";
        if (n.contains("bouygues telecom")) return "Prélèvement - Bouygues Telecom";
        if (n.contains("amazon prime")) return "Carte - Amazon Prime";
        if (n.contains("amazon payments")) return "Carte - Amazon Payments";
        if (n.contains("google play")) return "Carte - Google Play";
        if (n.contains("boardgamearena")) return "Carte - BoardGameArena";
        if (n.contains("relay")) return "Carte - RELAY";
        if (n.contains("lidl")) return "Carte - LIDL";
        if (n.contains("super u")) return "Carte - SUPER U";
        if (n.contains("leclerc")) return "Carte - LECLERC";
        if (n.contains("netto")) return "Carte - NETTO";
        if (n.contains("central bar")) return "Carte - LE CENTRAL BAR";
        if (n.contains("jean bart")) return "Carte - LE JEAN BART";
        if (n.contains("paddington")) return "Carte - PADDINGTON";
        if (n.contains("cotisation eurocompte")) return "Frais - COTISATION EUROCOMPTE";
        if (n.contains("commission d'intervention")) return "Frais - COMMISSION D'INTERVENTION";
        if (n.contains("frais prlv impaye sepa diac")) return "Frais - PRLV IMPAYÉ SEPA DIAC";
        if (n.contains("vir vers compte joint")) return "Virement vers - COMPTE JOINT";
        if (n.contains("vir de goubard melissa")) return "Virement reçu - GOUBARD MELISSA";
        if (n.contains("vir dinan/eureka")) return "Virement reçu - DINAN/EUREKA";

        String label = original;
        label = label.replaceAll("^(?:\\d{1,2}/\\d{1,2}/20\\d{2}\\s+)", "").trim();
        label = label.replaceAll("^[-–—:;,.\\s]+", "").trim();
        label = label.replaceAll("[-–—:;,.\\s]+$", "").trim();

        if (label.startsWith("CARTE ")) {
            label = label.replaceFirst("^CARTE\\s+\\d{1,2}/\\d{1,2}\\s+", "Carte - ");
        } else if (label.startsWith("PRLV SEPA ")) {
            label = label.replaceFirst("^PRLV SEPA\\s+", "Prélèvement - ");
        } else if (label.startsWith("VIR vers ")) {
            label = label.replaceFirst("^VIR vers\\s+", "Virement vers - ");
        } else if (label.startsWith("VIR de ")) {
            label = label.replaceFirst("^VIR de\\s+", "Virement reçu - ");
        } else if (label.startsWith("F ")) {
            label = label.replaceFirst("^F\\s+", "Frais - ");
        }

        label = label.replaceAll("\\s+[A-Z]{2,}[A-Z0-9/.-]{4,}$", "");
        label = label.replaceAll("\\s+\\d{4,}.*$", "");
        label = cleanLine(label);
        if (label.length() > 70) label = label.substring(0, 70).trim();
        if (label.length() == 0) label = "Transaction";
        return label;
    }

    public static String merchantKey(String label) {
        if (label == null) return "";

        String s = normalizeStatic(label).toLowerCase(Locale.FRENCH);
        s = s.replace("prelevement -", " ")
                .replace("prélèvement -", " ")
                .replace("carte -", " ")
                .replace("frais -", " ")
                .replace("virement vers -", " ")
                .replace("virement recu -", " ")
                .replace("virement reçu -", " ");

        s = s.replaceAll("\\bcarte\\b", " ");
        s = s.replaceAll("\\bprlv\\b", " ");
        s = s.replaceAll("\\bsepa\\b", " ");
        s = s.replaceAll("\\bvir\\b", " ");
        s = s.replaceAll("\\bvers\\b", " ");
        s = s.replaceAll("\\bde\\b", " ");

        // Références variables de relevés : PAYLIxxxx, codes magasins, RUM, longs identifiants.
        s = s.replaceAll("payli\\d+", " ");
        s = s.replaceAll("[a-z]{2,}\\d{4,}[a-z0-9/.-]*", " ");
        s = s.replaceAll("\\b\\d{4,}\\b", " ");
        s = s.replaceAll("\\b\\d{1,2}/\\d{1,2}(/20\\d{2})?\\b", " ");
        s = s.replaceAll("[^a-z0-9]+", " ").trim();

        if (s.contains("edf")) return "edf";
        if (s.contains("saur")) return "saur";
        if (s.contains("diac")) return "diac";
        if (s.contains("bnp paribas personal finance") || s.contains("personal finance")) return "bnp_personal_finance";
        if (s.contains("suravenir")) return "suravenir";
        if (s.contains("bouygues")) return "bouygues";
        if (s.contains("amazon prime")) return "amazon_prime";
        if (s.contains("amazon payments") || s.equals("amazon") || s.contains("amazon ")) return "amazon";
        if (s.contains("google play")) return "google_play";
        if (s.contains("boardgamearena")) return "boardgamearena";
        if (s.contains("relay")) return "relay";
        if (s.contains("lidl")) return "lidl";
        if (s.contains("super u") || s.contains("superu")) return "super_u";
        if (s.contains("leclerc") || s.contains("l eclerc") || s.contains("e leclerc")) return "leclerc";
        if (s.contains("netto")) return "netto";
        if (s.contains("paddington")) return "paddington";
        if (s.contains("central bar")) return "central_bar";
        if (s.contains("jean bart")) return "jean_bart";
        if (s.contains("dap35") || s.contains("laurenan")) return "dap35";
        if (s.contains("cotisation eurocompte")) return "cotisation_eurocompte";
        if (s.contains("commission d intervention")) return "commission_intervention";
        if (s.contains("frais prlv impaye") || s.contains("prlv impaye")) return "frais_prlv_impaye";
        if (s.contains("compte joint")) return "compte_joint";
        if (s.contains("goubard melissa")) return "goubard_melissa";
        if (s.contains("dinan eureka") || s.contains("dinan/eureka")) return "dinan_eureka";
        if (s.contains("octopus")) return "octopus";
        if (s.contains("fastt")) return "fastt";
        if (s.contains("alter interim") || s.contains("alter interimaire")) return "alter_interim";
        if (s.contains("apicil")) return "apicil";
        if (s.contains("orange")) return "orange";

        // Fallback : garde seulement les premiers mots utiles pour éviter les références finales.
        String[] parts = s.split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.length() <= 1) continue;
            if (out.length() > 0) out.append('_');
            out.append(part);
            if (out.length() >= 32) break;
        }
        return out.toString();
    }

    /**
     * Détecte si une transaction est un VRAI abonnement / charge fixe récurrente.
     * Conservateur : uniquement prélèvements connus et abonnements identifiables.
     * Un paiement carte ponctuel (restaurant, courses) n'est JAMAIS récurrent.
     */
    public static boolean isRecurringCandidateStatic(String label, String category) {
        if (label == null) label = "";
        String n = normalizeStatic(label).toLowerCase(Locale.FRENCH);

        // Abonnements / prélèvements récurrents connus
        String[] recurringMerchants = {
            "edf", "engie", "saur", "veolia", "suez",                 // énergie/eau
            "bouygues", "orange", "sfr", "free",                      // télécom
            "netflix", "spotify", "disney", "amazon prime",
            "deezer", "canal", "youtube premium", "apple.com/bill",   // streaming
            "google play", "boardgamearena",
            "apicil", "mutuelle", "harmonie", "maaf", "macif",
            "matmut", "groupama", "axa", "allianz", "gmf",            // assurances
            "suravenir", "diac", "cofidis", "cetelem", "younited",
            "personal finance",                                        // crédits
            "loyer", "bail", "syndic",                                // logement
            "abonnement", "cotisation"
        };
        for (String m : recurringMerchants) if (n.contains(m)) return true;

        // Préfixe prélèvement (PRLV) = souvent récurrent
        if (n.startsWith("prlv") || n.startsWith("prelevement")) return true;

        // Catégories typiquement récurrentes
        if (category != null) {
            String c = normalizeStatic(category).toLowerCase(Locale.FRENCH);
            if (c.contains("abonnement") || c.contains("logement")
                    || c.contains("credit") || c.contains("assurance")
                    || c.contains("telecom") || c.contains("energie")) return true;
        }
        return false;
    }

    private boolean containsAny(String text, String... keys) {
        if (text == null) return false;
        for (String key : keys) if (text.contains(key)) return true;
        return false;
    }

    private int cents(double amount) {
        return (int) Math.round(amount * 100.0);
    }

    private double parseAmount(String raw) {
        if (raw == null) return 0.0;
        String s = raw.replace("€", "").replace(" ", "").replace("\u00A0", "").replace(".", "").replace(",", ".").replace("+", "").trim();
        try { return Math.abs(Double.parseDouble(s)); } catch (Exception e) { return 0.0; }
    }

    private long makeDateMs(int day, int month, int year) {
        Calendar cal = Calendar.getInstance(Locale.FRENCH);
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month - 1);
        cal.set(Calendar.DAY_OF_MONTH, day);
        cal.set(Calendar.HOUR_OF_DAY, 12);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private String cleanLine(String raw) {
        if (raw == null) return "";
        return raw.replace('\t', ' ').replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String normalize(String input) { return normalizeStatic(input); }

    private static String normalizeStatic(String input) {
        if (input == null) return "";
        String s = Normalizer.normalize(input, Normalizer.Form.NFD);
        return s.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    /**
     * Nettoie un libellé brut Enable Banking pour affichage court et propre.
     * Réutilise les règles merchant connues, puis applique un nettoyage générique.
     *
     * Exemples :
     *   "VIR APICIL MUTUELLE Dec/n. 26000095g2P de 25.35 EUR du 22/05/2026 - Sante - APICIL MUTUELLE JS5"
     *       → "APICIL Mutuelle"
     *   "CARTE 29/05 PADDINGTON SAINT VRAN" → "Paddington Saint Vran"
     *   "VIR vers GOUBARD MELISSA de GOUBARD MELISSA / MAQ... 2615..." → "Goubard Melissa"
     */
    public static String cleanBankLabel(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "Transaction";
        String original = raw.trim().replaceAll("\\s+", " ");
        // Pour le matching des règles : minuscules + apostrophes/ponctuation → espaces
        String n = normalizeStatic(original).toLowerCase(Locale.FRENCH)
                .replaceAll("[''`.,/\\-]", " ").replaceAll("\\s+", " ");

        // ── 1. Règles merchant ciblées AVANT la détection virement ──
        // (certains prélèvements arrivent préfixés "VIR INST ..." alors que
        //  ce sont de vrais paiements, pas des virements entre comptes)
        if (n.contains("edf"))               return "EDF";
        if (n.matches(".*\\bsaur\\b.*"))     return "SAUR";
        if (n.matches(".*\\bdiac\\b.*"))     return "DIAC";
        if (n.contains("personal finance"))  return "BNP Paribas Personal Finance";
        if (n.contains("suravenir"))         return "Suravenir";
        if (n.contains("bouygues"))          return "Bouygues Telecom";
        if (n.contains("apicil"))            return "APICIL Mutuelle";
        if (n.contains("anthropic") || n.contains("claude.ai")) return "Anthropic Claude";
        if (n.contains("octopus"))           return "Octopus";
        if (n.contains("fastt"))             return "FASTT";
        if (n.contains("alter interim"))     return "ALTER Intérim";
        if (n.contains("free mobile"))       return "Free Mobile";
        if (n.contains("free "))             return "Free";
        if (n.contains("sfr"))               return "SFR";
        if (n.contains("orange"))            return "Orange";
        if (n.contains("netflix"))           return "Netflix";
        if (n.contains("spotify"))           return "Spotify";
        if (n.contains("disney"))            return "Disney+";
        if (n.contains("amazon prime"))      return "Amazon Prime";
        if (n.contains("amazon"))            return "Amazon";
        if (n.contains("google play"))       return "Google Play";
        if (n.contains("apple com") || n.contains("itunes")) return "Apple";
        if (n.contains("paypal"))            return "PayPal";
        if (n.contains("boardgamearena"))    return "BoardGameArena";
        if (n.contains("lidl"))              return "Lidl";
        if (n.contains("carrefour"))         return "Carrefour";
        if (n.contains("super u") || n.contains("superu")) return "Super U";
        if (n.contains("leclerc"))           return "Leclerc";
        if (n.contains("intermarche"))       return "Intermarché";
        if (n.contains("netto"))             return "Netto";
        if (n.contains("auchan"))            return "Auchan";
        if (n.contains("cotisation eurocompte")) return "Cotisation Eurocompte";
        if (n.contains("commission d intervention")) return "Commission d'intervention";
        if (n.contains("prlv impaye") || n.contains("frais prlv")) return "Frais prélèvement impayé";

        // ── 1b. Virement entre comptes/personnes → "Virement {Nom}" ──
        if (isVirementRaw(original)) {
            String party = virementCounterparty(original);
            return party.isEmpty() ? "Virement" : "Virement " + party;
        }
        if (n.contains("paddington"))        return "Paddington";
        if (n.contains("central bar"))       return "Le Central Bar";
        if (n.contains("jean bart"))         return "Le Jean Bart";

        // ── 2. Nettoyage générique ───────────────────────────────────
        String s = original;

        // Retirer le préfixe de type d'opération
        s = s.replaceFirst("(?i)^(VIREMENT|VIR|CARTE|CB|PRELEVEMENT|PRLV|PRELVT|ACHAT|RETRAIT|RET|PAIEMENT|PAImnt|FACTURE|FAC|CHEQUE|CHQ|COTISATION|COTIS|REMISE|REM)\\b\\.?\\s*", "");
        s = s.replaceFirst("(?i)^SEPA\\s+", "");
        s = s.replaceFirst("(?i)^(vers|de|du)\\s+", "");
        // Date après le préfixe (ex: CARTE 29/05 ...)
        s = s.replaceFirst("^\\d{1,2}/\\d{1,2}(/20\\d{2})?\\s+", "");
        // Préfixe "F " des frais (ex: "F Commission...")
        s = s.replaceFirst("^F\\s+(?=[A-Z])", "");

        // Couper aux marqueurs de "junk" (références, montants, dates internes)
        int cut = s.length();
        String[] markers = {
            " de ", " De ", " DE ",
            " Dec/", " dec/", " DEC/",
            " n.", " N.", " ref", " Ref", " REF",
            " / ", " // ", " du ", " Du ", " DU ",
            " EUR", " eur"
        };
        for (String mk : markers) {
            int i = s.indexOf(mk);
            if (i > 2 && i < cut) cut = i;
        }
        // Premier nombre de 4+ chiffres = début des références
        Matcher dm = Pattern.compile("\\d{4,}").matcher(s);
        if (dm.find() && dm.start() < cut) cut = dm.start();
        if (cut < s.length()) s = s.substring(0, cut);

        // Retirer un montant en fin de libellé (ex: "Octopus ... 10,29")
        s = s.replaceAll("\\s+\\d{1,3}[.,]\\d{2}\\s*$", "");
        // Retirer des codes courts résiduels en fin (ex: "JS5", "W1d")
        s = s.replaceAll("\\s+[A-Za-z0-9]{1,4}\\d[A-Za-z0-9]*\\s*$", "");

        // Nettoyage final des bords
        s = s.replaceAll("[-–—:;,.\\s]+$", "").trim();
        s = s.replaceAll("^[-–—:;,.\\s]+", "").trim();
        if (s.isEmpty()) s = original;

        // ── 2b. Ville en fin de libellé → entre parenthèses ─────────
        s = extractCityFromLabel(s);

        // ── 3. Expansions d'abréviations courantes ───────────────────
        s = expandAbbreviations(s);

        // Title case (PADDINGTON SAINT VRAN → Paddington Saint Vran)
        s = titleCase(s);
        if (s.length() > 50) s = s.substring(0, 50).trim();
        return s.isEmpty() ? "Transaction" : s;
    }

    /** Développe les abréviations bancaires courantes en gardant la ville. */
    private static String expandAbbreviations(String s) {
        // "Pharma X" / "Pharmacie X" → "Pharmacie X"
        s = s.replaceAll("(?i)^pharma\\b\\.?", "Pharmacie");
        s = s.replaceAll("(?i)^dr\\b\\.?", "Dr");           // Docteur
        s = s.replaceAll("(?i)^boulang\\b\\.?", "Boulangerie");
        s = s.replaceAll("(?i)^restau?\\b\\.?", "Restaurant");
        s = s.replaceAll("(?i)^superm\\b\\.?", "Supermarché");
        s = s.replaceAll("(?i)^stat\\b\\.?", "Station");
        s = s.replaceAll("(?i)^ent\\b\\.?", "Entreprise");
        return s;
    }

    /** Vrai si le libellé brut est un virement (VIR / VIREMENT). */
    public static boolean isVirementRaw(String raw) {
        if (raw == null) return false;
        String n = normalizeStatic(raw).toLowerCase(Locale.FRENCH).trim();
        return n.startsWith("vir ") || n.startsWith("virement")
                || n.startsWith("vir vers") || n.startsWith("vir de");
    }

    /** Vrai si un libellé DÉJÀ nettoyé est un virement. */
    public static boolean isVirementLabel(String label) {
        if (label == null) return false;
        return normalizeStatic(label).toLowerCase(Locale.FRENCH).startsWith("virement");
    }

    /** Extrait le nom du tiers d'un virement (ex: "VIR vers GOUBARD MELISSA de…" → "Melissa"). */
    private static String virementCounterparty(String raw) {
        String s = raw.trim();
        s = s.replaceFirst("(?i)^(VIREMENT|VIR)\\b\\.?\\s*", "");
        s = s.replaceFirst("(?i)^(vers|de|en faveur de|au profit de|pour)\\s+", "");
        s = s.replaceFirst("(?i)^(compte|cpte|mr|mme|m\\.|melle)\\s+", "");

        int cut = s.length();
        for (String mk : new String[]{" de ", " De ", " DE ", " / ", " ref", " Ref",
                " du ", " EUR", " Dec/", " n."}) {
            int i = s.indexOf(mk);
            if (i > 1 && i < cut) cut = i;
        }
        Matcher dm = Pattern.compile("\\d{3,}").matcher(s);
        if (dm.find() && dm.start() < cut) cut = dm.start();
        if (cut < s.length()) s = s.substring(0, cut);
        s = s.replaceAll("[-–—:;,.\\s]+$", "").trim();
        if (s.isEmpty()) return "";

        // Format banque "NOM Prénom" : garder le dernier mot (prénom) si 2-3 mots
        String[] words = s.split("\\s+");
        String name = (words.length >= 2 && words.length <= 3)
                ? words[words.length - 1]
                : s;
        return titleCase(name);
    }

    /** Met en forme « TITRE EXEMPLE » → « Titre Exemple ». */
    /**
     * Détecte une ville en fin de libellé brut (avant titleCase) et la met entre parenthèses.
     * Dédoublonne aussi la ville si elle apparaît deux fois consécutivement.
     * Ex: "LA FRITERIE LOUDEAC" → "LA FRITERIE (LOUDEAC)"
     * Ex: "CENTRAKOR LOUDEAC LOUDEAC" → "CENTRAKOR (LOUDEAC)"
     */
    private static String extractCityFromLabel(String s) {
        if (s == null || s.contains("(")) return s; // Déjà formaté
        String trimmed = s.trim();
        String[] words = trimmed.split("\\s+");
        if (words.length < 2) return trimmed;

        // Dédoublonnage : "LOUDEAC LOUDEAC" → "LOUDEAC"
        if (words.length >= 3
                && words[words.length - 1].equalsIgnoreCase(words[words.length - 2])) {
            int lastSpace = trimmed.lastIndexOf(' ');
            trimmed = trimmed.substring(0, lastSpace).trim();
            words = trimmed.split("\\s+");
            if (words.length < 2) return trimmed;
        }

        String lastWord = words[words.length - 1];

        // Pas une ville : trop court, contient des chiffres, ou suffixe entreprise connu
        if (lastWord.length() < 4) return trimmed;
        if (!lastWord.matches("[A-ZÀ-Ÿa-zà-ÿ][A-ZÀ-Ÿa-zà-ÿ\\-]+")) return trimmed;

        // Suffixes non géographiques à exclure
        String upLast = lastWord.toUpperCase(Locale.FRENCH);
        switch (upLast) {
            case "SA": case "SAS": case "SARL": case "SCM": case "SNC": case "SNCF":
            case "ETS": case "MAG": case "SHOP": case "FRANCE": case "SERVICE":
            case "SERVICES": case "GROUP": case "GROUPE": case "CENTRE": case "CENTER":
            case "PRO": case "PLUS": case "MARKET": case "INTER": case "DRIVE":
            case "CONTACT": case "EXPRESS": case "ONLINE": case "MOBILE":
            case "DIGITAL": case "STORE": case "DIRECT": case "CLICK":
                return trimmed;
        }

        // Construire "NOM MARCHAND (VILLE)"
        int lastSpace = trimmed.lastIndexOf(' ');
        String namePart = trimmed.substring(0, lastSpace).trim();
        if (namePart.length() < 3) return trimmed;

        return namePart + " (" + lastWord + ")";
    }

    private static String titleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        // Ne re-cap que si le texte est majoritairement en majuscules
        long upper = s.chars().filter(Character::isUpperCase).count();
        long lower = s.chars().filter(Character::isLowerCase).count();
        if (lower > upper) return s; // déjà mixte, on garde

        StringBuilder out = new StringBuilder();
        boolean newWord = true;
        for (char c : s.toCharArray()) {
            // Traiter '(' comme délimiteur de mot pour capitaliser la ville
            if (Character.isWhitespace(c) || c == '-' || c == '\'' || c == '(') {
                out.append(c); newWord = true;
            } else if (newWord) {
                out.append(Character.toUpperCase(c)); newWord = false;
            } else {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString();
    }

    private int safeInt(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (Exception e) { return fallback; }
    }
}
