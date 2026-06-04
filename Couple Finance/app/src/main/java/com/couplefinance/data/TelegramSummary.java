package com.couplefinance.data;

import android.app.Activity;
import android.content.Context;

import com.couplefinance.ui.agenda.AgendaModels;
import com.couplefinance.ui.agenda.AgendaRepository;
import com.couplefinance.ui.transactions.TransactionsModels;
import com.couplefinance.ui.transactions.TransactionsRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Construit un résumé financier (soldes, mois en cours, dernières opérations,
 * prélèvements à venir, agenda) et l'envoie via Telegram.
 *
 * Les sources sont lues en chaîne (best-effort) : si l'une échoue, le résumé
 * est tout de même envoyé avec les blocs disponibles.
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

        sb.append("<b>CoupleFinance \u2014 r\u00e9sum\u00e9</b>\n");
        sb.append("<i>").append(esc(dateHeader())).append("</i>\n");

        appendBalances(ctx, sb);

        // 1) Transactions → ce mois-ci + dernières opérations
        TransactionsRepository.loadAll(activity, new TransactionsRepository.OnDataLoaded() {
            @Override
            public void onLoaded(List<TransactionsModels.Transaction> transactions,
                                 List<String> members, List<String[]> categories) {
                appendMonth(sb, transactions);
                appendRecent(sb, transactions);
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
                            if (count > 0) {
                                sb.append("\n<b>Prochains pr\u00e9l\u00e8vements</b>\n");
                                sb.append("\u2022 ").append(count)
                                        .append(count > 1 ? " pr\u00e9l\u00e8vements \u00e0 venir : "
                                                          : " pr\u00e9l\u00e8vement \u00e0 venir : ")
                                        .append(money(total)).append("\n");
                            }
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

    // 3) Agenda → prochains rendez-vous
    private static void stepAgenda(final Activity activity, final StringBuilder sb, final Callback cb) {
        try {
            AgendaRepository.loadAll(activity, new AgendaRepository.OnDataLoaded() {
                @Override
                public void onLoaded(AgendaModels.AgendaData data) {
                    appendAgenda(sb, data);
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
            // Chat commun : on n'expose AUCUN solde personnel.
            // Seul le compte joint (réellement partagé) apparaît.
            double joint = BankAutoSyncManager.getLiveBalanceFor(ctx, "Compte joint");
            if (!Double.isNaN(joint)) {
                sb.append("\n<b>Compte joint</b>\n");
                sb.append("\u2022 Solde : ").append(money(joint)).append("\n");
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
        sb.append("\u2022 Revenus : +").append(money(income)).append("\n");
        sb.append("\u2022 D\u00e9penses : -").append(money(expense)).append("\n");
        sb.append("\u2022 Solde du mois : ").append(signed(income - expense)).append("\n");
    }

    private static void appendRecent(StringBuilder sb, List<TransactionsModels.Transaction> tx) {
        if (tx == null || tx.isEmpty()) return;
        List<TransactionsModels.Transaction> sorted = new ArrayList<>(tx);
        Collections.sort(sorted, (a, b) -> Long.compare(b.dateMs, a.dateMs));
        SimpleDateFormat f = new SimpleDateFormat("dd/MM", Locale.FRANCE);
        sb.append("\n<b>Derni\u00e8res op\u00e9rations</b>\n");
        int n = Math.min(5, sorted.size());
        for (int i = 0; i < n; i++) {
            TransactionsModels.Transaction t = sorted.get(i);
            String sign = "income".equals(t.type) ? "+" : "-";
            sb.append("\u2022 ").append(sign).append(money(Math.abs(t.amount)))
                    .append(" \u2014 ").append(esc(t.label))
                    .append(" (").append(f.format(new Date(t.dateMs))).append(")\n");
        }
    }

    private static void appendAgenda(StringBuilder sb, AgendaModels.AgendaData data) {
        if (data == null || data.events == null || data.events.isEmpty()) return;
        long since = System.currentTimeMillis() - 86400000L; // tolère aujourd'hui
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
            sb.append("\u2022 ").append(f.format(new Date(e.dateMs)))
                    .append(" \u2014 ").append(esc(e.title)).append("\n");
        }
    }

    // ───────────────────────────── Helpers ─────────────────────────────

    private static String money(double v) {
        return String.format(Locale.FRANCE, "%,.2f \u20ac", v).replace('\u00a0', ' ');
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
