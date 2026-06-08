package com.couplefinance.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.couplefinance.ui.transactions.TransactionsRepository;

import org.json.JSONObject;

import java.util.Locale;

/**
 * Handles incoming Telegram bot commands and returns reply strings.
 *
 * Supported commands (all checked against configured chat_id):
 *   /solde               — current account balances
 *   /resume              — trigger full Telegram digest
 *   /ajout 45.50 Libellé — add a joint expense
 *   /categorie NomCat    — change category of last bot-added transaction
 *   /aide                — list available commands
 *
 * Called from TelegramPollingWorker (background thread); all Firestore writes
 * are asynchronous via TransactionsRepository (null Activity path).
 */
public final class TelegramCommandHandler {

    private static final String PREFS          = "telegram_cmd_prefs";
    private static final String K_LAST_TX_ID   = "last_bot_tx_docid";
    private static final String K_LAST_TX_LABEL = "last_bot_tx_label";
    private static final String K_LAST_TX_AMT  = "last_bot_tx_amount";
    private static final String K_LAST_TX_DATE = "last_bot_tx_date";

    private TelegramCommandHandler() {}

    /** Reply sent when a message comes from an unauthorised chat. */
    public static final String REPLY_UNAUTHORIZED =
            "⛔ Commande refusée : expéditeur non autorisé.";

    /**
     * Dispatch a text command.
     * Returns the reply string to send back, or {@code null} for no reply.
     */
    public static String handle(Context ctx, String text) {
        if (ctx == null || text == null) return null;
        String trimmed = text.trim();

        if (trimmed.startsWith("/solde"))      return handleSolde(ctx);
        if (trimmed.startsWith("/resume"))     return handleResume(ctx);
        if (trimmed.startsWith("/ajout"))      return handleAjout(ctx, trimmed);
        if (trimmed.startsWith("/categorie"))  return handleCategorie(ctx, trimmed);
        if (trimmed.startsWith("/aide")
         || trimmed.startsWith("/help")
         || trimmed.startsWith("/start"))      return handleAide();
        return null; // not a known command, stay silent
    }

    /**
     * Handle a callback_query (inline keyboard button tap).
     * Returns the toast text to show the user (via answerCallbackQuery), or null.
     */
    public static String handleCallback(Context ctx, String data) {
        if (ctx == null || data == null) return null;
        if ("ack_expense".equals(data)) {
            return "✅ Dépense enregistrée.";
        }
        if (data.startsWith("cat_")) {
            // cat_<docId> — user tapped "Catégoriser" on a grosse dépense
            // Reply with instructions
            TelegramManager.getInstance().sendMessage(
                    "ℹ️ Pour changer la catégorie, répondez :\n"
                    + "<code>/categorie NomDeLaCategorie</code>",
                    null);
            return "Envoi des instructions…";
        }
        return null;
    }

    // ─── /solde ───────────────────────────────────────────────────

    private static String handleSolde(Context ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("🏦 <b>Soldes actuels</b>\n");
        sb.append(sep());

        String live = BankAutoSyncManager.getLiveBalances(ctx);
        if (live != null && !live.isEmpty()) {
            for (String part : live.split("\\s{2,}·\\s{2,}|\\s·\\s")) {
                sb.append("• ").append(part.trim()).append("\n");
            }
        } else {
            try {
                JointAccountManager.getInstance().init(ctx);
                double joint = JointAccountManager.getInstance().getBalanceLocal(ctx);
                if (!Double.isNaN(joint))
                    sb.append("• Compte joint : <b>").append(money(joint)).append("</b>\n");
                else
                    sb.append("• Solde non disponible (synchro bancaire désactivée)\n");
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

    private static String handleAjout(Context ctx, String text) {
        // /ajout <montant> <libellé…>
        String args = text.replaceFirst("^/ajout\\b", "").trim();
        if (args.isEmpty()) {
            return "❌ Usage : <code>/ajout 45.50 Libellé de la dépense</code>";
        }
        String[] parts = args.split("\\s+", 2);
        if (parts.length < 2) {
            return "❌ Usage : <code>/ajout 45.50 Libellé de la dépense</code>";
        }
        double amount;
        try {
            amount = Double.parseDouble(parts[0].replace(",", "."));
        } catch (NumberFormatException e) {
            return "❌ Montant invalide : " + parts[0];
        }
        String label = parts[1].trim();
        if (label.isEmpty()) return "❌ Libellé manquant.";

        double finalAmount = -Math.abs(amount); // always an expense
        long now = System.currentTimeMillis();

        TransactionsRepository.addTransaction(
                label, finalAmount, "variable", "Autre", now,
                "", false, false, "joint", null,
                new TransactionsRepository.OnWriteComplete() {
                    @Override public void onSuccess() {
                        // Firestore response carries the doc path; we can't get docId here
                        // since the callback above has no response param. Store a placeholder.
                        prefs(ctx).edit()
                                .putString(K_LAST_TX_LABEL, label)
                                .putString(K_LAST_TX_ID, "")      // filled by listener below
                                .putDouble(K_LAST_TX_AMT, finalAmount)
                                .putLong(K_LAST_TX_DATE, now)
                                .apply();
                        String confirm = "✅ Dépense ajoutée au compte joint :\n"
                                + "• <b>" + label + "</b> · " + money(Math.abs(finalAmount)) + "\n"
                                + "<i>Utilisez /categorie pour changer la catégorie.</i>";
                        TelegramManager.getInstance().sendMessage(confirm, null);
                    }
                    @Override public void onError(String e) {
                        TelegramManager.getInstance().sendMessage(
                                "❌ Erreur lors de l'ajout : " + e, null);
                    }
                });

        return "⏳ Ajout en cours…";
    }

    // ─── /categorie ──────────────────────────────────────────────

    private static String handleCategorie(Context ctx, String text) {
        // /categorie <NomCatégorie>
        String cat = text.replaceFirst("^/categorie\\b", "").trim();
        if (cat.isEmpty()) {
            return "❌ Usage : <code>/categorie Alimentation</code>";
        }

        String docId    = prefs(ctx).getString(K_LAST_TX_ID, "");
        String txLabel  = prefs(ctx).getString(K_LAST_TX_LABEL, "");
        double txAmount;
        try { txAmount = Double.parseDouble(prefs(ctx).getString(K_LAST_TX_AMT, "0")); }
        catch (Exception e) { txAmount = 0; }
        long   txDate   = prefs(ctx).getLong(K_LAST_TX_DATE, 0);

        if (txLabel.isEmpty()) {
            return "❌ Aucune transaction récente trouvée. Utilisez <code>/ajout</code> d'abord.";
        }

        if (docId.isEmpty()) {
            // docId not stored (TransactionsRepository.addTransaction doesn't return it).
            // Fall back: update via label search in Firestore.
            updateLastByLabel(ctx, txLabel, txAmount, txDate, cat);
            return null; // async reply
        }

        TransactionsRepository.updateTransaction(
                docId, txLabel, txAmount, "variable", cat, txDate,
                "", false, "joint", null,
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

    /**
     * Fallback for when docId is unknown: load recent transactions and
     * update the most recent one matching the stored label.
     */
    private static void updateLastByLabel(Context ctx, String txLabel, double txAmount,
                                           long txDate, String newCategory) {
        TransactionsRepository.loadAll(null, new TransactionsRepository.OnDataLoaded() {
            @Override
            public void onLoaded(java.util.List<com.couplefinance.ui.transactions.TransactionsModels.Transaction> txs,
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
                final com.couplefinance.ui.transactions.TransactionsModels.Transaction found = best;
                TransactionsRepository.updateTransaction(
                        found.docId, found.label, found.amount, found.type, newCategory,
                        found.dateMs, found.person, found.shared, found.compte, null,
                        new TransactionsRepository.OnWriteComplete() {
                            @Override public void onSuccess() {
                                TelegramManager.getInstance().sendMessage(
                                        "✅ Catégorie de <b>" + found.label
                                        + "</b> mise à jour : <b>" + newCategory + "</b>", null);
                            }
                            @Override public void onError(String e) {
                                TelegramManager.getInstance().sendMessage(
                                        "❌ Mise à jour échouée : " + e, null);
                            }
                        });
            }
            @Override public void onError(String msg) {
                TelegramManager.getInstance().sendMessage(
                        "❌ Erreur chargement transactions : " + msg, null);
            }
        });
    }

    // ─── /aide ────────────────────────────────────────────────────

    private static String handleAide() {
        return "🦸 <b>CoupleFinance Bot</b>\n\n"
             + "Commandes disponibles :\n"
             + "• <code>/solde</code> — afficher les soldes\n"
             + "• <code>/resume</code> — envoyer le résumé complet\n"
             + "• <code>/ajout 45.50 Courses</code> — ajouter une dépense joint\n"
             + "• <code>/categorie Alimentation</code> — changer la catégorie du dernier ajout\n"
             + "• <code>/aide</code> — cette aide";
    }

    // ─── Helpers ─────────────────────────────────────────────────

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String money(double v) {
        return String.format(Locale.FRANCE, "%,.2f €", v).replace(' ', ' ');
    }

    private static String sep() {
        return "─────────────\n";
    }

    /** Save last bot-added transaction docId (called from BankAutoSyncManager or after /ajout). */
    public static void saveLastTxDocId(Context ctx, String docId, String label,
                                        double amount, long dateMs) {
        if (ctx == null) return;
        prefs(ctx).edit()
                .putString(K_LAST_TX_ID,    docId != null ? docId : "")
                .putString(K_LAST_TX_LABEL, label != null ? label : "")
                .putString(K_LAST_TX_AMT, String.valueOf(amount))
                .putLong(K_LAST_TX_DATE,    dateMs)
                .apply();
    }
}
