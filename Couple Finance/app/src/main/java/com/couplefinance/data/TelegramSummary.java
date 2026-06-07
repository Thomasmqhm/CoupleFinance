package com.couplefinance.data;

import android.app.Activity;
import android.content.Context;

import com.couplefinance.ui.agenda.AgendaModels;
import com.couplefinance.ui.agenda.AgendaRepository;
import com.couplefinance.ui.budget.BudgetModels;
import com.couplefinance.ui.budget.BudgetRepository;
import com.couplefinance.ui.epargne.EpargneCalculator;
import com.couplefinance.ui.epargne.EpargneModels;
import com.couplefinance.ui.epargne.EpargneParser;
import com.couplefinance.ui.transactions.TransactionsModels;
import com.couplefinance.ui.transactions.TransactionsRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Construit un résumé financier enrichi et l'envoie via Telegram.
 *
 * Chaîne : balances → transactions → prélèvements → budget → épargne → agenda → envoi.
 * Chaque étape est best-effort : si elle échoue, le résumé part quand même avec les
 * blocs déjà disponibles.
 */
public final class TelegramSummary {

    private TelegramSummary() { }

    public interface Callback {
        void onSuccess();
        void onError(String error);
    }

    public static void buildAndSend(final Activity activity, final Callback cb) {
        if (activity == null) {
            if (cb != null) cb.onError("Contexte indisponible");
            return;
        }
        if (!TelegramManager.getInstance().isConfigured()) {
            if (cb != null) cb.onError("Telegram n'est pas configuré (token + chat_id)");
            return;
        }

        final Context ctx = activity.getApplicationContext();
        final StringBuilder sb = new StringBuilder();

        sb.append("<b>CoupleFinance — résumé</b>\n");
        sb.append("<i>").append(esc(dateHeader())).append("</i>\n");

        if (TelegramScheduler.isShowBalances(ctx)) appendBalances(ctx, sb);

        // 1) Transactions → ce mois-ci + top catégories + dernières opérations
        TransactionsRepository.loadAll(activity, new TransactionsRepository.OnDataLoaded() {
            @Override
            public void onLoaded(List<TransactionsModels.Transaction> transactions,
                                 List<String> members, List<String[]> categories) {
                if (TelegramScheduler.isShowMonth(ctx))      appendMonth(sb, transactions);
                if (TelegramScheduler.isShowCategories(ctx)) appendTopCategories(sb, transactions);
                if (TelegramScheduler.isShowRecent(ctx))     appendRecent(sb, transactions);
                stepCharges(activity, sb, cb);
            }

            @Override
            public void onError(String error) {
                stepCharges(activity, sb, cb);
            }
        });
    }

    // 2) Prélèvements à venir
    private static void stepCharges(final Activity activity, final StringBuilder sb, final Callback cb) {
        try {
            RecurringChargeManager.getInstance().init(activity);
            RecurringChargeManager.getInstance().getUpcomingChargesForCurrentMonth(
                    new RecurringChargeManager.UpcomingChargesCallback() {
                        @Override
                        public void onResult(double total, int count) {
                            if (count > 0 && TelegramScheduler.isShowCharges(activity)) {
                                sb.append("\n<b>Prochains prélèvements</b>\n");
                                sb.append("• ").append(count)
                                        .append(count > 1 ? " prélèvements à venir : "
                                                          : " prélèvement à venir : ")
                                        .append(money(total)).append("\n");
                            }
                            stepBudget(activity, sb, cb);
                        }

                        @Override
                        public void onError(String error) {
                            stepBudget(activity, sb, cb);
                        }
                    });
        } catch (Exception e) {
            stepBudget(activity, sb, cb);
        }
    }

    // 3) Budget — catégories dépassées ou en alerte
    private static void stepBudget(final Activity activity, final StringBuilder sb, final Callback cb) {
        try {
            BudgetRepository.loadBudgets(new BudgetRepository.Callback() {
                @Override
                public void onResult(List<BudgetModels.CategoryBudget> list) {
                    if (TelegramScheduler.isShowBudget(activity)) appendBudget(sb, list);
                    stepSavings(activity, sb, cb);
                }

                @Override
                public void onError(String error) {
                    stepSavings(activity, sb, cb);
                }
            });
        } catch (Exception e) {
            stepSavings(activity, sb, cb);
        }
    }

    // 4) Épargne — progression des objectifs actifs
    private static void stepSavings(final Activity activity, final StringBuilder sb, final Callback cb) {
        try {
            SavingsManager.getInstance().getSavings(new FirestoreManager.Callback() {
                @Override
                public void onSuccess(String json) {
                    List<EpargneModels.SavingsGoal> goals = EpargneParser.parseSavings(json);
                    if (TelegramScheduler.isShowSavings(activity)) appendSavings(sb, goals);
                    stepCredits(activity, sb, cb);
                }

                @Override
                public void onError(String error) {
                    stepCredits(activity, sb, cb);
                }
            });
        } catch (Exception e) {
            stepCredits(activity, sb, cb);
        }
    }

    // 4bis) Crédits → mensualités et capital restant
    private static void stepCredits(final Activity activity, final StringBuilder sb, final Callback cb) {
        try {
            CreditManager.getInstance().init(activity);
            CreditManager.getInstance().getCredits(new FirestoreManager.Callback() {
                @Override
                public void onSuccess(String json) {
                    if (TelegramScheduler.isShowCredits(activity))
                        appendCredits(sb,
                                com.couplefinance.ui.credits.CreditsParser.parseCredits(json));
                    stepAgenda(activity, sb, cb);
                }

                @Override
                public void onError(String error) {
                    stepAgenda(activity, sb, cb);
                }
            });
        } catch (Exception e) {
            stepAgenda(activity, sb, cb);
        }
    }

    // 5) Agenda → prochains rendez-vous
    private static void stepAgenda(final Activity activity, final StringBuilder sb, final Callback cb) {
        try {
            AgendaRepository.loadAll(activity, new AgendaRepository.OnDataLoaded() {
                @Override
                public void onLoaded(AgendaModels.AgendaData data) {
                    if (TelegramScheduler.isShowAgenda(activity)) appendAgenda(sb, data);
                    send(sb, cb);
                }

                @Override
                public void onError(String message) {
                    send(sb, cb);
                }
            });
        } catch (Exception e) {
            send(sb, cb);
        }
    }

    private static void send(StringBuilder sb, final Callback cb) {
        TelegramManager.getInstance().sendMessage(sb.toString(), new TelegramManager.Callback() {
            @Override public void onSuccess(String response) { if (cb != null) cb.onSuccess(); }
            @Override public void onError(String error) { if (cb != null) cb.onError(error); }
        });
    }

    // ───────────────────────────── Sections ─────────────────────────────

    private static void appendBalances(Context ctx, StringBuilder sb) {
        try {
            double joint = BankAutoSyncManager.getLiveBalanceFor(ctx, "Compte joint");
            if (!Double.isNaN(joint)) {
                sb.append("\n<b>Compte joint</b>\n");
                sb.append("• Solde : ").append(money(joint)).append("\n");
            }
        } catch (Exception ignored) {
        }
    }

    private static void appendMonth(StringBuilder sb, List<TransactionsModels.Transaction> tx) {
        if (tx == null) return;
        Calendar now = Calendar.getInstance();
        int month = now.get(Calendar.MONTH);
        int year = now.get(Calendar.YEAR);
        double income = 0, expense = 0;
        Calendar c = Calendar.getInstance();
        for (TransactionsModels.Transaction t : tx) {
            if (t == null) continue;
            c.setTimeInMillis(t.dateMs);
            if (c.get(Calendar.MONTH) != month || c.get(Calendar.YEAR) != year) continue;
            if ("income".equals(t.type)) income += Math.abs(t.amount);
            else expense += Math.abs(t.amount);
        }
        sb.append("\n<b>Ce mois-ci</b>\n");
        sb.append("• Revenus : +").append(money(income)).append("\n");
        sb.append("• Dépenses : -").append(money(expense)).append("\n");
        sb.append("• Solde du mois : ").append(signed(income - expense)).append("\n");
    }

    private static void appendTopCategories(StringBuilder sb, List<TransactionsModels.Transaction> tx) {
        if (tx == null || tx.isEmpty()) return;
        Calendar now = Calendar.getInstance();
        int month = now.get(Calendar.MONTH), year = now.get(Calendar.YEAR);
        Calendar c = Calendar.getInstance();
        Map<String, Double> byCategory = new HashMap<>();
        for (TransactionsModels.Transaction t : tx) {
            if (t == null || "income".equals(t.type)) continue;
            c.setTimeInMillis(t.dateMs);
            if (c.get(Calendar.MONTH) != month || c.get(Calendar.YEAR) != year) continue;
            String cat = (t.category != null && !t.category.isEmpty()) ? t.category : "Autre";
            Double cur = byCategory.get(cat);
            byCategory.put(cat, (cur == null ? 0 : cur) + Math.abs(t.amount));
        }
        if (byCategory.isEmpty()) return;

        List<Map.Entry<String, Double>> entries = new ArrayList<>(byCategory.entrySet());
        Collections.sort(entries, (a, b) -> Double.compare(b.getValue(), a.getValue()));

        sb.append("\n<b>Top dépenses par catégorie</b>\n");
        int n = Math.min(5, entries.size());
        for (int i = 0; i < n; i++) {
            Map.Entry<String, Double> e = entries.get(i);
            sb.append("• ").append(esc(e.getKey())).append(" : -").append(money(e.getValue())).append("\n");
        }
    }

    private static void appendBudget(StringBuilder sb, List<BudgetModels.CategoryBudget> list) {
        if (list == null || list.isEmpty()) return;

        List<BudgetModels.CategoryBudget> exceeded = new ArrayList<>();
        List<BudgetModels.CategoryBudget> warning  = new ArrayList<>();
        for (BudgetModels.CategoryBudget b : list) {
            if (b.isExceeded()) exceeded.add(b);
            else if (b.isWarning()) warning.add(b);
        }

        if (exceeded.isEmpty() && warning.isEmpty()) {
            sb.append("\n<b>Budget</b>\n");
            sb.append("• ✅ Toutes les catégories sont dans les limites\n");
            return;
        }

        sb.append("\n<b>Budget</b>\n");
        for (BudgetModels.CategoryBudget b : exceeded) {
            double over = b.spent - b.budget;
            sb.append("• 🔴 ").append(esc(b.name))
              .append(" : dépassé de ").append(money(over))
              .append(" (").append(b.getPercent()).append("%)\n");
        }
        for (BudgetModels.CategoryBudget b : warning) {
            sb.append("• ⚠️ ").append(esc(b.name))
              .append(" : ").append(b.getPercent()).append("% utilisé, reste ")
              .append(money(b.getRemaining())).append("\n");
        }
    }

    private static void appendSavings(StringBuilder sb, List<EpargneModels.SavingsGoal> goals) {
        if (goals == null || goals.isEmpty()) return;

        List<EpargneModels.SavingsGoal> active = new ArrayList<>();
        int completed = 0;
        for (EpargneModels.SavingsGoal g : goals) {
            if (g.isCompleted()) completed++;
            else active.add(g);
        }

        if (active.isEmpty() && completed == 0) return;

        sb.append("\n<b>🌱 Épargne</b>\n");
        if (completed > 0) {
            sb.append("• ✅ ").append(completed)
              .append(completed > 1 ? " objectifs atteints\n" : " objectif atteint\n");
        }

        Collections.sort(active, (a, b) -> {
            int pa = EpargneCalculator.progressPercent(a);
            int pb = EpargneCalculator.progressPercent(b);
            return Integer.compare(pb, pa);
        });

        int n = Math.min(4, active.size());
        for (int i = 0; i < n; i++) {
            EpargneModels.SavingsGoal g = active.get(i);
            int pct = EpargneCalculator.progressPercent(g);
            String bar = progressBar(pct);
            sb.append("• ").append(esc(g.name)).append(" ").append(bar)
              .append(" ").append(pct).append("%");
            if (g.hasDate()) {
                int months = EpargneCalculator.monthsLeft(g);
                sb.append(" — ").append(months).append(months > 1 ? " mois" : " mois");
            }
            sb.append("\n");
        }
    }

    private static void appendCredits(StringBuilder sb,
            List<com.couplefinance.ui.credits.CreditsModels.Credit> credits) {
        if (credits == null || credits.isEmpty()) return;

        double totalMonthly = com.couplefinance.ui.credits.CreditsCalculator.totalMonthly(credits);
        double totalRemaining = com.couplefinance.ui.credits.CreditsCalculator.totalRemaining(credits);

        sb.append("\n<b>🏦 Crédits</b>\n");
        sb.append("• Mensualités : ").append(money(totalMonthly)).append("/mois\n");
        sb.append("• Capital restant dû : ").append(money(totalRemaining)).append("\n");

        // Détail des crédits actifs (capital restant > 0), triés par mensualité décroissante
        List<com.couplefinance.ui.credits.CreditsModels.Credit> active = new ArrayList<>();
        for (com.couplefinance.ui.credits.CreditsModels.Credit c : credits) {
            if (c != null
                    && com.couplefinance.ui.credits.CreditsCalculator.computeRemaining(c) > 0.01) {
                active.add(c);
            }
        }
        Collections.sort(active, (a, b) -> Double.compare(b.monthlyPayment, a.monthlyPayment));
        int n = Math.min(4, active.size());
        for (int i = 0; i < n; i++) {
            com.couplefinance.ui.credits.CreditsModels.Credit c = active.get(i);
            int months = com.couplefinance.ui.credits.CreditsCalculator.monthsLeft(c);
            sb.append("• ").append(esc(c.name))
              .append(" : ").append(money(c.monthlyPayment)).append("/mois");
            if (months > 0) sb.append(" — ").append(months).append(months > 1 ? " mois restants" : " mois restant");
            sb.append("\n");
        }
    }

    private static void appendRecent(StringBuilder sb, List<TransactionsModels.Transaction> tx) {
        if (tx == null || tx.isEmpty()) return;
        List<TransactionsModels.Transaction> sorted = new ArrayList<>(tx);
        Collections.sort(sorted, (a, b) -> Long.compare(b.dateMs, a.dateMs));
        SimpleDateFormat f = new SimpleDateFormat("dd/MM", Locale.FRANCE);
        sb.append("\n<b>Dernières opérations</b>\n");
        int n = Math.min(5, sorted.size());
        for (int i = 0; i < n; i++) {
            TransactionsModels.Transaction t = sorted.get(i);
            String sign = "income".equals(t.type) ? "+" : "-";
            sb.append("• ").append(sign).append(money(Math.abs(t.amount)))
                    .append(" — ").append(esc(t.label))
                    .append(" (").append(f.format(new Date(t.dateMs))).append(")\n");
        }
    }

    private static void appendAgenda(StringBuilder sb, AgendaModels.AgendaData data) {
        if (data == null || data.events == null || data.events.isEmpty()) return;
        long since = System.currentTimeMillis() - 86400000L;
        List<AgendaModels.AgendaEvent> upcoming = new ArrayList<>();
        for (AgendaModels.AgendaEvent e : data.events) {
            if (e != null && e.dateMs >= since) upcoming.add(e);
        }
        if (upcoming.isEmpty()) return;
        Collections.sort(upcoming, (a, b) -> Long.compare(a.dateMs, b.dateMs));
        SimpleDateFormat f = new SimpleDateFormat("dd/MM", Locale.FRANCE);
        sb.append("\n<b>Prochains rendez-vous</b>\n");
        int n = Math.min(5, upcoming.size());
        for (int i = 0; i < n; i++) {
            AgendaModels.AgendaEvent e = upcoming.get(i);
            sb.append("• ").append(f.format(new Date(e.dateMs)))
                    .append(" — ").append(esc(e.title)).append("\n");
        }
    }

    // ───────────────────────────── Helpers ─────────────────────────────

    private static String progressBar(int pct) {
        int filled = Math.min(10, (int) Math.round(pct / 10.0));
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < 10; i++) bar.append(i < filled ? "█" : "░");
        bar.append("]");
        return bar.toString();
    }

    private static String money(double v) {
        return String.format(Locale.FRANCE, "%,.2f €", v).replace(' ', ' ');
    }

    private static String signed(double v) {
        return (v >= 0 ? "+" : "-") + money(Math.abs(v));
    }

    private static String dateHeader() {
        return new SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(new Date());
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
