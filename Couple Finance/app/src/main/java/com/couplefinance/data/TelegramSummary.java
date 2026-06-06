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
import com.couplefinance.ui.settings.SettingsCache;
import com.couplefinance.ui.settings.SettingsModels;
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
 * Sections modulaires : chaque section est activable/désactivable via TelegramScheduler prefs.
 * Chaîne : balances → mois → charges → budget → épargne → crédits → agenda → projection → envoi.
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

        // En-tête
        sb.append("🏠 <b>CoupleFinance</b>\n");
        sb.append("📅 <i>").append(esc(dateHeader())).append("</i>\n");
        sb.append(sep());

        // Soldes
        if (TelegramScheduler.isShowBalances(ctx)) {
            appendBalances(ctx, sb);
        }

        // 1) Transactions → mois + catégories + récentes
        TransactionsRepository.loadAll(activity, new TransactionsRepository.OnDataLoaded() {
            @Override
            public void onLoaded(List<TransactionsModels.Transaction> transactions,
                                 List<String> members, List<String[]> categories) {
                if (TelegramScheduler.isShowMonth(ctx)) {
                    appendMonth(ctx, sb, transactions);
                }
                if (TelegramScheduler.isShowCategories(ctx)) {
                    appendTopCategories(sb, transactions);
                }
                if (TelegramScheduler.isShowRecent(ctx)) {
                    appendRecent(sb, transactions);
                }
                stepCharges(activity, ctx, sb, transactions, cb);
            }

            @Override
            public void onError(String error) {
                stepCharges(activity, ctx, sb, null, cb);
            }
        });
    }

    // 2) Prélèvements à venir (liste détaillée)
    private static void stepCharges(final Activity activity, final Context ctx,
                                    final StringBuilder sb,
                                    final List<TransactionsModels.Transaction> txList,
                                    final Callback cb) {
        if (!TelegramScheduler.isShowCharges(ctx)) {
            stepBudget(activity, ctx, sb, txList, 0, cb);
            return;
        }
        try {
            RecurringChargeManager.getInstance().init(activity);
            RecurringChargeManager.getInstance().getUpcomingChargesForCurrentMonth(
                    new RecurringChargeManager.UpcomingChargesCallback() {
                        @Override
                        public void onResult(double total, int count) {
                            appendCharges(ctx, sb, total, count);
                            stepBudget(activity, ctx, sb, txList, total, cb);
                        }
                        @Override
                        public void onError(String error) {
                            stepBudget(activity, ctx, sb, txList, 0, cb);
                        }
                    });
        } catch (Exception e) {
            stepBudget(activity, ctx, sb, txList, 0, cb);
        }
    }

    // 3) Budget
    private static void stepBudget(final Activity activity, final Context ctx,
                                   final StringBuilder sb,
                                   final List<TransactionsModels.Transaction> txList,
                                   final double remainingCharges,
                                   final Callback cb) {
        if (!TelegramScheduler.isShowBudget(ctx)) {
            stepSavings(activity, ctx, sb, txList, remainingCharges, cb);
            return;
        }
        try {
            BudgetRepository.loadBudgets(new BudgetRepository.Callback() {
                @Override
                public void onResult(List<BudgetModels.CategoryBudget> list) {
                    appendBudget(sb, list);
                    stepSavings(activity, ctx, sb, txList, remainingCharges, cb);
                }
                @Override
                public void onError(String error) {
                    stepSavings(activity, ctx, sb, txList, remainingCharges, cb);
                }
            });
        } catch (Exception e) {
            stepSavings(activity, ctx, sb, txList, remainingCharges, cb);
        }
    }

    // 4) Épargne
    private static void stepSavings(final Activity activity, final Context ctx,
                                    final StringBuilder sb,
                                    final List<TransactionsModels.Transaction> txList,
                                    final double remainingCharges,
                                    final Callback cb) {
        if (!TelegramScheduler.isShowSavings(ctx)) {
            stepCredits(activity, ctx, sb, txList, remainingCharges, cb);
            return;
        }
        try {
            SavingsManager.getInstance().getSavings(new FirestoreManager.Callback() {
                @Override
                public void onSuccess(String json) {
                    List<EpargneModels.SavingsGoal> goals = EpargneParser.parseSavings(json);
                    appendSavings(sb, goals);
                    stepCredits(activity, ctx, sb, txList, remainingCharges, cb);
                }
                @Override
                public void onError(String error) {
                    stepCredits(activity, ctx, sb, txList, remainingCharges, cb);
                }
            });
        } catch (Exception e) {
            stepCredits(activity, ctx, sb, txList, remainingCharges, cb);
        }
    }

    // 5) Crédits
    private static void stepCredits(final Activity activity, final Context ctx,
                                    final StringBuilder sb,
                                    final List<TransactionsModels.Transaction> txList,
                                    final double remainingCharges,
                                    final Callback cb) {
        if (!TelegramScheduler.isShowCredits(ctx)) {
            stepAgenda(activity, ctx, sb, txList, remainingCharges, cb);
            return;
        }
        try {
            CreditManager.getInstance().init(activity);
            CreditManager.getInstance().getCredits(new FirestoreManager.Callback() {
                @Override
                public void onSuccess(String json) {
                    appendCredits(sb,
                            com.couplefinance.ui.credits.CreditsParser.parseCredits(json));
                    stepAgenda(activity, ctx, sb, txList, remainingCharges, cb);
                }
                @Override
                public void onError(String error) {
                    stepAgenda(activity, ctx, sb, txList, remainingCharges, cb);
                }
            });
        } catch (Exception e) {
            stepAgenda(activity, ctx, sb, txList, remainingCharges, cb);
        }
    }

    // 6) Agenda
    private static void stepAgenda(final Activity activity, final Context ctx,
                                   final StringBuilder sb,
                                   final List<TransactionsModels.Transaction> txList,
                                   final double remainingCharges,
                                   final Callback cb) {
        if (!TelegramScheduler.isShowAgenda(ctx)) {
            appendProjection(ctx, sb, txList, remainingCharges);
            send(sb, cb);
            return;
        }
        try {
            AgendaRepository.loadAll(activity, new AgendaRepository.OnDataLoaded() {
                @Override
                public void onLoaded(AgendaModels.AgendaData data) {
                    appendAgenda(sb, data);
                    appendProjection(ctx, sb, txList, remainingCharges);
                    send(sb, cb);
                }
                @Override
                public void onError(String message) {
                    appendProjection(ctx, sb, txList, remainingCharges);
                    send(sb, cb);
                }
            });
        } catch (Exception e) {
            appendProjection(ctx, sb, txList, remainingCharges);
            send(sb, cb);
        }
    }

    private static void send(StringBuilder sb, final Callback cb) {
        TelegramManager.getInstance().sendMessage(sb.toString(), new TelegramManager.Callback() {
            @Override public void onSuccess(String response) { if (cb != null) cb.onSuccess(); }
            @Override public void onError(String error) { if (cb != null) cb.onError(error); }
        });
    }

    // ─────────────────────────── Sections ───────────────────────────

    private static void appendBalances(Context ctx, StringBuilder sb) {
        List<String> selected = TelegramScheduler.getSelectedAccounts(ctx);
        boolean hasAny = false;

        // Compte joint
        boolean showJoint = selected.isEmpty() || containsIgnoreCase(selected, "Compte joint");
        if (showJoint) {
            double joint = Double.NaN;
            try { joint = BankAutoSyncManager.getLiveBalanceFor(ctx, "Compte joint"); } catch (Exception ignored) {}
            if (Double.isNaN(joint)) {
                try {
                    JointAccountManager.getInstance().init(ctx);
                    joint = JointAccountManager.getInstance().getBalanceLocal(ctx);
                } catch (Exception ignored) {}
            }
            if (!Double.isNaN(joint)) {
                if (!hasAny) { sb.append("\n💰 <b>Soldes</b>\n"); hasAny = true; }
                sb.append("• Compte joint : <b>").append(money(joint)).append("</b>\n");
            }
        }

        // Membres
        try {
            SettingsModels.State state = SettingsCache.get();
            if (state != null && state.members != null) {
                for (SettingsModels.Member m : state.members) {
                    if (m == null || m.name == null || m.name.trim().isEmpty()) continue;
                    String name = m.name.trim();
                    if (!selected.isEmpty() && !containsIgnoreCase(selected, name)) continue;
                    double bal = Double.NaN;
                    try { bal = BankAutoSyncManager.getLiveBalanceFor(ctx, name); } catch (Exception ignored) {}
                    if (!Double.isNaN(bal)) {
                        if (!hasAny) { sb.append("\n💰 <b>Soldes</b>\n"); hasAny = true; }
                        sb.append("• ").append(esc(name)).append(" : <b>").append(money(bal)).append("</b>\n");
                    }
                }
            }
        } catch (Exception ignored) {}

        if (hasAny) sb.append(sep());
    }

    private static void appendMonth(Context ctx, StringBuilder sb,
                                    List<TransactionsModels.Transaction> tx) {
        if (tx == null) return;
        Calendar now = Calendar.getInstance();
        int month = now.get(Calendar.MONTH);
        int year  = now.get(Calendar.YEAR);
        double income = 0, expense = 0;
        Calendar c = Calendar.getInstance();
        for (TransactionsModels.Transaction t : tx) {
            if (t == null) continue;
            c.setTimeInMillis(t.dateMs);
            if (c.get(Calendar.MONTH) != month || c.get(Calendar.YEAR) != year) continue;
            if ("income".equals(t.type)) income += Math.abs(t.amount);
            else expense += Math.abs(t.amount);
        }
        String monthName = new SimpleDateFormat("MMMM yyyy", Locale.FRANCE).format(new Date());
        sb.append("\n📊 <b>Bilan ").append(esc(monthName)).append("</b>\n");
        sb.append("• Revenus : <b>+").append(money(income)).append("</b>\n");
        sb.append("• Dépenses : <b>-").append(money(expense)).append("</b>\n");
        double balance = income - expense;
        String sign = balance >= 0 ? "+" : "";
        sb.append("• Solde du mois : <b>").append(sign).append(money(balance)).append("</b>\n");
        sb.append(sep());
    }

    private static void appendTopCategories(StringBuilder sb,
                                            List<TransactionsModels.Transaction> tx) {
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

        sb.append("\n🏷️ <b>Top dépenses par catégorie</b>\n");
        int n = Math.min(5, entries.size());
        for (int i = 0; i < n; i++) {
            Map.Entry<String, Double> e = entries.get(i);
            sb.append("• ").append(esc(e.getKey())).append(" : -").append(money(e.getValue())).append("\n");
        }
        sb.append(sep());
    }

    private static void appendCharges(Context ctx, StringBuilder sb, double total, int count) {
        if (count <= 0) return;

        sb.append("\n📋 <b>Prélèvements à venir</b>\n");

        // Détail depuis SettingsCache
        try {
            SettingsModels.State state = SettingsCache.get();
            if (state != null && state.charges != null && !state.charges.isEmpty()) {
                int today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
                List<SettingsModels.FixedCharge> upcoming = new ArrayList<>();
                for (SettingsModels.FixedCharge fc : state.charges) {
                    if (fc == null) continue;
                    if (fc.dayOfMonth > today) upcoming.add(fc);
                }
                Collections.sort(upcoming, (a, b) -> Integer.compare(a.dayOfMonth, b.dayOfMonth));
                for (SettingsModels.FixedCharge fc : upcoming) {
                    sb.append("• J").append(fc.dayOfMonth)
                      .append(" — ").append(esc(fc.name))
                      .append(" : -").append(money(fc.amount)).append("\n");
                }
                sb.append("──\n");
            }
        } catch (Exception ignored) {}

        sb.append("• Total : <b>-").append(money(total)).append("</b>")
          .append(" (").append(count).append(count > 1 ? " charges)" : " charge)").append("\n");
        sb.append(sep());
    }

    private static void appendBudget(StringBuilder sb, List<BudgetModels.CategoryBudget> list) {
        if (list == null || list.isEmpty()) return;

        List<BudgetModels.CategoryBudget> exceeded = new ArrayList<>();
        List<BudgetModels.CategoryBudget> warning  = new ArrayList<>();
        for (BudgetModels.CategoryBudget b : list) {
            if (b.isExceeded()) exceeded.add(b);
            else if (b.isWarning()) warning.add(b);
        }

        sb.append("\n📉 <b>Budget</b>\n");
        if (exceeded.isEmpty() && warning.isEmpty()) {
            sb.append("• ✅ Toutes les catégories sont dans les limites\n");
        } else {
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
        sb.append(sep());
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

        sb.append("\n🌱 <b>Épargne</b>\n");
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
        sb.append(sep());
    }

    private static void appendCredits(StringBuilder sb,
            List<com.couplefinance.ui.credits.CreditsModels.Credit> credits) {
        if (credits == null || credits.isEmpty()) return;

        double totalMonthly   = com.couplefinance.ui.credits.CreditsCalculator.totalMonthly(credits);
        double totalRemaining = com.couplefinance.ui.credits.CreditsCalculator.totalRemaining(credits);

        sb.append("\n🏦 <b>Crédits</b>\n");
        sb.append("• Mensualités totales : <b>").append(money(totalMonthly)).append("/mois</b>\n");
        sb.append("• Capital restant dû : <b>").append(money(totalRemaining)).append("</b>\n");

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
            if (months > 0) sb.append(" — ").append(months)
                              .append(months > 1 ? " mois restants" : " mois restant");
            sb.append("\n");
        }
        sb.append(sep());
    }

    private static void appendRecent(StringBuilder sb, List<TransactionsModels.Transaction> tx) {
        if (tx == null || tx.isEmpty()) return;
        List<TransactionsModels.Transaction> sorted = new ArrayList<>(tx);
        Collections.sort(sorted, (a, b) -> Long.compare(b.dateMs, a.dateMs));
        SimpleDateFormat f = new SimpleDateFormat("dd/MM", Locale.FRANCE);
        sb.append("\n🧾 <b>Dernières opérations</b>\n");
        int n = Math.min(5, sorted.size());
        for (int i = 0; i < n; i++) {
            TransactionsModels.Transaction t = sorted.get(i);
            String sign = "income".equals(t.type) ? "+" : "-";
            sb.append("• ").append(f.format(new Date(t.dateMs)))
              .append(" — ").append(esc(t.label))
              .append(" : ").append(sign).append(money(Math.abs(t.amount))).append("\n");
        }
        sb.append(sep());
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
        sb.append("\n📆 <b>Agenda</b>\n");
        int n = Math.min(5, upcoming.size());
        for (int i = 0; i < n; i++) {
            AgendaModels.AgendaEvent e = upcoming.get(i);
            sb.append("• ").append(f.format(new Date(e.dateMs)))
              .append(" — ").append(esc(e.title)).append("\n");
        }
        sb.append(sep());
    }

    private static void appendProjection(Context ctx, StringBuilder sb,
                                         List<TransactionsModels.Transaction> tx,
                                         double remainingCharges) {
        if (!TelegramScheduler.isShowProjection(ctx)) return;

        // Solde joint actuel
        double joint = Double.NaN;
        try { joint = BankAutoSyncManager.getLiveBalanceFor(ctx, "Compte joint"); } catch (Exception ignored) {}
        if (Double.isNaN(joint)) {
            try {
                JointAccountManager.getInstance().init(ctx);
                joint = JointAccountManager.getInstance().getBalanceLocal(ctx);
            } catch (Exception ignored) {}
        }

        if (Double.isNaN(joint) && remainingCharges <= 0) return;

        sb.append("\n🔮 <b>Projection fin de mois</b>\n");

        if (!Double.isNaN(joint)) {
            sb.append("• Solde actuel : ").append(money(joint)).append("\n");
        }
        if (remainingCharges > 0) {
            sb.append("• Prélèvements restants : -").append(money(remainingCharges)).append("\n");
        }
        if (!Double.isNaN(joint) && remainingCharges > 0) {
            double projected = joint - remainingCharges;
            String sign = projected >= 0 ? "" : "";
            sb.append("• Solde projeté : <b>").append(money(projected)).append("</b>");
            if (projected < 0) sb.append(" ⚠️");
            sb.append("\n");
        }
        sb.append(sep());
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private static String sep() {
        return "─────────────────────\n";
    }

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

    private static String dateHeader() {
        return new SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRANCE).format(new Date());
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        if (list == null || value == null) return false;
        for (String s : list) {
            if (value.equalsIgnoreCase(s)) return true;
        }
        return false;
    }
}
