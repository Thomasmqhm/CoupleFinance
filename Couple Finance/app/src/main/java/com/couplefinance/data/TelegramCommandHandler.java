package com.couplefinance.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.couplefinance.ui.transactions.TransactionsRepository;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles incoming Telegram bot commands and returns reply strings.
 *
 * Supported commands (chat_id security enforced by TelegramPollingWorker):
 *   /solde               — current balances (all accounts via BankAutoSyncManager)
 *   /resume              — trigger full digest
 *   /ajout 45.50 Label [@joint|@perso|@Prenom]  — add transaction
 *   /ajout +200 Salaire                          — income (positive = income)
 *   /categorie NomCat    — recategorize last bot-added transaction
 *   /aide                — list commands
 */
public final class TelegramCommandHandler {

    private static final String PREFS           = "telegram_cmd_prefs";
    private static final String K_LAST_TX_ID    = "last_bot_tx_docid";
    private static final String K_LAST_TX_LABEL = "last_bot_tx_label";
    private static final String K_LAST_TX_AMT   = "last_bot_tx_amount";
    private static final String K_LAST_TX_DATE  = "last_bot_tx_date";
    private static final String K_LAST_TX_TYPE  = "last_bot_tx_type";
    private static final String K_LAST_TX_ACCT  = "last_bot_tx_compte";

    private TelegramCommandHandler() {}

    public static final String REPLY_UNAUTHORIZED =
            "⛔ Commande refusée : expéditeur non autorisé.";

    // ─── Dispatch ────────────────────────────────────────────────

    public static String handle(Context ctx, String text) {
        if (ctx == null || text == null) return null;
        String t = text.trim();
        if (t.startsWith("/solde"))     return handleSolde(ctx);
        if (t.startsWith("/resume"))    return handleResume(ctx);
        if (t.startsWith("/ajout"))     return handleAjout(ctx, t);
        if (t.startsWith("/categorie")) return handleCategorie(ctx, t);
        if (t.startsWith("/aide") || t.startsWith("/help") || t.startsWith("/start"))
            return handleAide();
        return null;
    }

    public static String handleCallback(Context ctx, String data) {
        if (data == null) return null;
        if ("ack_expense".equals(data)) return "✅ Dépense enregistrée.";
        if (data.startsWith("cat_")) {
            TelegramManager.getInstance().sendMessage(
                    "ℹ️ Pour changer la catégorie, répondez :\n"
                    + "<code>/categorie NomDeLaCategorie</code>", null);
            return "Instructions envoyées.";
        }
        return null;
    }

    // ─── /solde ──────────────────────────────────────────────────

    private static String handleSolde(Context ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("🏦 <b>Soldes actuels</b>\n").append(sep());

        String live = BankAutoSyncManager.getLiveBalances(ctx);
        if (live != null && !live.isEmpty()) {
            // live format: "Joint : 1 234,00 €  ·  Thomas : 567,00 €"
            for (String part : live.split("\\s{2,}[·⋅]\\s{2,}|\\s[·⋅]\\s|  ·  ")) {
                String p = part.trim();
                if (!p.isEmpty()) sb.append("• ").append(p).append("\n");
            }
        } else {
            try {
                JointAccountManager.getInstance().init(ctx);
                double joint = JointAccountManager.getInstance().getBalanceLocal(ctx);
                if (!Double.isNaN(joint))
                    sb.append("• Compte joint : <b>").append(money(joint)).append("</b>\n");
                else
                    sb.append("• Solde non disponible (synchronisation inactive)\n");
            } catch (Exception e) {
                sb.append("• Solde non disponible\n");
            }
        }
        return sb.toString().trim();
    }

    // ─── /resume ─────────────────────────────────────────────────

    private static String handleResume(Context ctx) {
        TelegramScheduler.checkAlertsBackground(ctx);
        return null; // digest sends its own message
    }

    // ─── /ajout ──────────────────────────────────────────────────

    /**
     * Syntax:
     *   /ajout 45.50 Courses             → joint expense (default)
     *   /ajout 45.50 Courses @joint      → joint (explicit)
     *   /ajout 45.50 Courses @perso      → personal account of current user
     *   /ajout 45.50 Courses @Thomas     → attributed to member Thomas
     *   /ajout +200 Salaire              → income, joint
     *   /ajout +200 Salaire @perso       → income, personal
     */
    private static String handleAjout(Context ctx, String text) {
        String args = text.replaceFirst("^/ajout\\b", "").trim();
        if (args.isEmpty()) return usageAjout();

        String[] parts = args.split("\\s+", 2);
        if (parts.length < 2) return usageAjout();

        double rawAmount;
        try {
            rawAmount = Double.parseDouble(parts[0].replace(",", "."));
        } catch (NumberFormatException e) {
            return "❌ Montant invalide : " + parts[0];
        }

        // Parse optional @tag at end of label
        String rest   = parts[1].trim();
        String compte = "joint";   // default
        String person = "";

        Matcher m = Pattern.compile("@(\\S+)$").matcher(rest);
        if (m.find()) {
            String tag = m.group(1).toLowerCase(Locale.FRENCH);
            rest = rest.substring(0, m.start()).trim();
            switch (tag) {
                case "joint":
                    compte = "joint"; person = ""; break;
                case "perso": case "moi":
                    compte = ""; person = currentUser(ctx); break;
                default:
                    // Named member (@Thomas, @Melissa…)
                    person = tag.substring(0, 1).toUpperCase(Locale.FRENCH) + tag.substring(1);
                    compte = "";
            }
        }

        if (rest.isEmpty()) return "❌ Libellé manquant.";

        boolean isIncome  = rawAmount > 0;
        double finalAmt   = isIncome ? rawAmount : -Math.abs(rawAmount);
        String type       = isIncome ? "income" : "variable";
        String defaultCat = isIncome ? "Revenus" : "Autre";
        String label      = rest;
        long now          = System.currentTimeMillis();
        String finalCpt   = compte;
        String finalPer   = person;

        TransactionsRepository.addTransaction(
                label, finalAmt, type, defaultCat, now,
                finalPer, false, false, finalCpt, null,
                new TransactionsRepository.OnWriteComplete() {
                    @Override public void onSuccess() {
                        prefs(ctx).edit()
                                .putString(K_LAST_TX_ID,    "")
                                .putString(K_LAST_TX_LABEL, label)
                                .putString(K_LAST_TX_AMT,   String.valueOf(finalAmt))
                                .putLong(K_LAST_TX_DATE,    now)
                                .putString(K_LAST_TX_TYPE,  type)
                                .putString(K_LAST_TX_ACCT,  finalCpt)
                                .apply();
                        String compteLabel = "joint".equals(finalCpt)
                                ? "compte joint"
                                : (!finalPer.isEmpty() ? "compte de " + finalPer : "compte perso");
                        String sign = isIncome ? "+" : "−";
                        String msg = "✅ Transaction ajoutée (" + compteLabel + ") :\n"
                                + "• <b>" + label + "</b>\n"
                                + "• " + sign + money(Math.abs(finalAmt)) + "\n"
                                + "<i>Utilisez /categorie pour changer la catégorie.</i>";
                        TelegramManager.getInstance().sendMessage(msg, null);
                    }
                    @Override public void onError(String e) {
                        TelegramManager.getInstance().sendMessage(
                                "❌ Erreur lors de l'ajout : " + e, null);
                    }
                });

        return "⏳ Ajout en cours…";
    }

    private static String usageAjout() {
        return "❌ Usage :\n"
             + "<code>/ajout 45.50 Courses</code> — dépense joint\n"
             + "<code>/ajout 45.50 Courses @perso</code> — dépense perso\n"
             + "<code>/ajout 45.50 Courses @Thomas</code> — attribuer à un membre\n"
             + "<code>/ajout +200 Salaire @perso</code> — revenu perso";
    }

    // ─── /categorie ──────────────────────────────────────────────

    private static String handleCategorie(Context ctx, String text) {
        String cat = text.replaceFirst("^/categorie\\b", "").trim();
        if (cat.isEmpty()) {
            return "❌ Usage : <code>/categorie Alimentation</code>";
        }

        String txLabel = prefs(ctx).getString(K_LAST_TX_LABEL, "");
        String docId   = prefs(ctx).getString(K_LAST_TX_ID,    "");
        double txAmt;
        try { txAmt = Double.parseDouble(prefs(ctx).getString(K_LAST_TX_AMT, "0")); }
        catch (Exception e) { txAmt = 0; }
        long   txDate  = prefs(ctx).getLong(K_LAST_TX_DATE, 0);
        String txType  = prefs(ctx).getString(K_LAST_TX_TYPE, "variable");
        String txAcct  = prefs(ctx).getString(K_LAST_TX_ACCT, "joint");

        if (txLabel.isEmpty()) {
            return "❌ Aucune transaction récente. Utilisez <code>/ajout</code> d'abord.";
        }

        if (!docId.isEmpty()) {
            // Have docId: direct update
            TransactionsRepository.updateTransaction(
                    docId, txLabel, txAmt, txType, cat, txDate,
                    "", false, txAcct, null,
                    new TransactionsRepository.OnWriteComplete() {
                        @Override public void onSuccess() {
                            TelegramManager.getInstance().sendMessage(
                                    "✅ Catégorie mise à jour : <b>" + cat + "</b>", null);
                        }
                        @Override public void onError(String e) {
                            TelegramManager.getInstance().sendMessage(
                                    "❌ Mise à jour échouée : " + e, null);
                        }
                    });
            return "⏳ Mise à jour en cours…";
        }

        // No docId: search by label
        updateLastByLabel(ctx, txLabel, cat);
        return null;
    }

    private static void updateLastByLabel(Context ctx, String txLabel, String newCat) {
        TransactionsRepository.loadAll(null, new TransactionsRepository.OnDataLoaded() {
            @Override
            public void onLoaded(
                    java.util.List<com.couplefinance.ui.transactions.TransactionsModels.Transaction> txs,
                    java.util.List<String> members,
                    java.util.List<String[]> cats) {
                String target = txLabel.toLowerCase(Locale.FRENCH);
                com.couplefinance.ui.transactions.TransactionsModels.Transaction best = null;
                for (com.couplefinance.ui.transactions.TransactionsModels.Transaction t : txs) {
                    if (t.label != null && t.label.toLowerCase(Locale.FRENCH).contains(target)) {
                        if (best == null || t.addedMs > best.addedMs) best = t;
                    }
                }
                if (best == null) {
                    TelegramManager.getInstance().sendMessage(
                            "❌ Transaction introuvable : " + txLabel, null);
                    return;
                }
                final com.couplefinance.ui.transactions.TransactionsModels.Transaction f = best;
                TransactionsRepository.updateTransaction(
                        f.docId, f.label, f.amount, f.type, newCat,
                        f.dateMs, f.person, f.shared, f.compte, null,
                        new TransactionsRepository.OnWriteComplete() {
                            @Override public void onSuccess() {
                                TelegramManager.getInstance().sendMessage(
                                        "✅ Catégorie de <b>" + f.label
                                        + "</b> → <b>" + newCat + "</b>", null);
                            }
                            @Override public void onError(String e) {
                                TelegramManager.getInstance().sendMessage(
                                        "❌ Échec : " + e, null);
                            }
                        });
            }
            @Override public void onError(String msg) {
                TelegramManager.getInstance().sendMessage(
                        "❌ Erreur chargement : " + msg, null);
            }
        });
    }

    // ─── /aide ───────────────────────────────────────────────────

    private static String handleAide() {
        return "🦸 <b>CoupleFinance Bot</b>\n\n"
             + "Commandes disponibles :\n"
             + "• <code>/solde</code> — tous les soldes\n"
             + "• <code>/resume</code> — résumé complet\n"
             + "• <code>/ajout 45.50 Courses</code> — dépense joint\n"
             + "• <code>/ajout 45.50 Courses @perso</code> — dépense perso\n"
             + "• <code>/ajout +200 Salaire @perso</code> — revenu perso\n"
             + "• <code>/ajout 45.50 Courses @Thomas</code> — attribuer à Thomas\n"
             + "• <code>/categorie Alimentation</code> — changer catégorie\n"
             + "• <code>/aide</code> — cette aide";
    }

    // ─── Helpers ─────────────────────────────────────────────────

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String money(double v) {
        return String.format(Locale.FRANCE, "%,.2f €", v);
    }

    private static String sep() { return "─────────\n"; }

    private static String currentUser(Context ctx) {
        try {
            String n = com.couplefinance.UserSession.getInstance().getNameOrFallback();
            if (n != null && !n.contains("@") && !n.trim().isEmpty()) return n.trim();
        } catch (Exception ignored) {}
        try {
            String n = com.couplefinance.AuthManager.getInstance().getDisplayName();
            if (n != null && !n.contains("@") && !n.trim().isEmpty()) return n.trim();
        } catch (Exception ignored) {}
        return "";
    }

    /** Store last bot-added docId (can be called externally once docId is known). */
    public static void saveLastTxDocId(Context ctx, String docId, String label,
                                        double amount, long dateMs) {
        if (ctx == null) return;
        prefs(ctx).edit()
                .putString(K_LAST_TX_ID,    docId != null ? docId : "")
                .putString(K_LAST_TX_LABEL, label != null ? label : "")
                .putString(K_LAST_TX_AMT,   String.valueOf(amount))
                .putLong(K_LAST_TX_DATE,    dateMs)
                .apply();
    }
}
