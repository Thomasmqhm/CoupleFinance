package com.couplefinance.ui.agenda;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.couplefinance.core.base.BaseView;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.Fmt;
import com.couplefinance.core.ui.UiFactory;
import com.couplefinance.core.ui.components.PremiumEmptyState;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AgendaView extends BaseView {

    private Calendar displayedMonth = Calendar.getInstance();
    private int selectedDay = -1;
    private String activeFilter = "Tout voir";
    private AgendaModels.AgendaData currentData;

    private TextView tvLabel;
    private TextView tvSubtitle;
    private TextView tvMonthYear;
    private LinearLayout calendarGrid;
    private LinearLayout chipRow;
    private LinearLayout upcomingList;
    private LinearLayout rdvList;
    private TextView tvUpcomingCount;
    private TextView tvRdvCount;

    public AgendaView(Activity activity) {
        super(activity);
    }

    @Override
    public View getView() {
        LinearLayout root = UiFactory.vertical(activity);
        root.setBackgroundColor(DS.BG);
        root.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        root.setPadding(dp(DS.PAD_CARD), dp(DS.PAD_CARD), dp(DS.PAD_CARD), dp(DS.NAV_CLEARANCE));

        buildHeader(root);
        buildFilterChips(root);
        buildColumns(root);

        // Contenu défilant, sinon les cartes du bas passent sous la barre de nav.
        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(DS.BG);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(root, new android.widget.ScrollView.LayoutParams(-1, -2));

        load();
        return scroll;
    }

    private void buildHeader(LinearLayout root) {
        tvLabel = UiFactory.pageLabel(activity, "AGENDA · " + Fmt.monthLabel());
        tvLabel.setTextColor(ThemeColors.primary());
        root.addView(tvLabel, new LinearLayout.LayoutParams(-1, -2));

        TextView title = UiFactory.pageTitle(activity, "Le mois à venir");
        title.setTextColor(ThemeColors.text());
        title.setTextSize(26f);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = dp(4);
        root.addView(title, titleLp);

        tvSubtitle = UiFactory.bodyMuted(activity, "Chargement...");
        tvSubtitle.setTextColor(ThemeColors.subtext());
        tvSubtitle.setTypeface(null, Typeface.ITALIC);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(2);
        root.addView(tvSubtitle, subLp);

        Button btnAdd = UiFactory.btnPrimary(activity, "+ Nouvel événement");
        btnAdd.setBackground(UiFactory.bg(ThemeColors.primary(), DS.R_LG, activity));
        btnAdd.setTextColor(Color.WHITE);
        btnAdd.setAllCaps(false);
        btnAdd.setOnClickListener(v -> showAddEventDialog(null));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, dp(52));
        btnLp.topMargin = dp(14);
        btnLp.bottomMargin = dp(8);
        root.addView(btnAdd, btnLp);
    }

    private void buildFilterChips(LinearLayout root) {
        HorizontalScrollView hsv = new HorizontalScrollView(activity);
        hsv.setHorizontalScrollBarEnabled(false);

        LinearLayout.LayoutParams hsvp = lpFull();
        hsvp.topMargin = dp(DS.GAP_SM);
        hsvp.bottomMargin = dp(DS.GAP_SM);
        root.addView(hsv, hsvp);

        chipRow = UiFactory.horizontal(activity);
        hsv.addView(chipRow);

        for (String filter : AgendaModels.FILTERS) {
            TextView chip = UiFactory.chip(activity, filter, filter.equals(activeFilter));

            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, dp(36));
            cp.rightMargin = dp(8);
            chip.setLayoutParams(cp);

            styleChip(chip, filter.equals(activeFilter));

            chip.setOnClickListener(v -> {
                activeFilter = filter;
                refreshChipStyles();
                if (currentData != null) {
                    renderUpcoming(currentData);
                }
            });

            chipRow.addView(chip);
        }
    }

    private void refreshChipStyles() {
        for (int i = 0; i < chipRow.getChildCount(); i++) {
            View c = chipRow.getChildAt(i);
            if (c instanceof TextView) {
                String text = ((TextView) c).getText().toString();
                styleChip((TextView) c, text.equals(activeFilter));
            }
        }
    }

    private void buildColumns(LinearLayout root) {
        LinearLayout content = UiFactory.vertical(activity);
        content.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        // Calendrier
        LinearLayout calCard = makeCard();
        calCard.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        buildCalendarCard(calCard);
        content.addView(calCard);

        // À venir
        LinearLayout upCard = makeCard();
        LinearLayout.LayoutParams upLp = new LinearLayout.LayoutParams(-1, -2);
        upLp.topMargin = dp(DS.GAP_SM);
        upCard.setLayoutParams(upLp);
        buildUpcomingCard(upCard);
        content.addView(upCard);

        // Rendez-vous
        LinearLayout rdvCard = makeCard();
        LinearLayout.LayoutParams rdvLp = new LinearLayout.LayoutParams(-1, -2);
        rdvLp.topMargin = dp(DS.GAP_SM);
        rdvCard.setLayoutParams(rdvLp);
        buildRdvCard(rdvCard);
        content.addView(rdvCard);

        root.addView(content);
    }

    private void buildCalendarCard(LinearLayout card) {
        LinearLayout monthNav = UiFactory.horizontal(activity);

        LinearLayout.LayoutParams mnp = lpFull();
        mnp.bottomMargin = dp(DS.GAP_SM);
        monthNav.setLayoutParams(mnp);

        tvMonthYear = UiFactory.sectionTitle(activity, "");
        tvMonthYear.setTextColor(ThemeColors.text());
        tvMonthYear.setTextSize(17f);
        tvMonthYear.setLayoutParams(lpWeight(1f));
        monthNav.addView(tvMonthYear);

        TextView btnPrev = makeNavBtn("‹");
        btnPrev.setOnClickListener(v -> navigateMonth(-1));
        monthNav.addView(btnPrev);

        TextView btnNext = makeNavBtn("›");
        LinearLayout.LayoutParams nnp = new LinearLayout.LayoutParams(-2, -2);
        nnp.leftMargin = dp(6);
        btnNext.setLayoutParams(nnp);
        btnNext.setOnClickListener(v -> navigateMonth(+1));
        monthNav.addView(btnNext);

        card.addView(monthNav);
        card.addView(AgendaCalendar.buildDaysHeader(activity));

        calendarGrid = UiFactory.vertical(activity);
        calendarGrid.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        card.addView(calendarGrid);
    }

    private void buildRdvCard(LinearLayout card) {
        LinearLayout rdvHeader = UiFactory.horizontal(activity);

        LinearLayout.LayoutParams rhp = lpFull();
        rhp.bottomMargin = dp(DS.GAP_SM);
        rdvHeader.setLayoutParams(rhp);

        TextView tvRdvTitle = UiFactory.sectionTitle(activity, "Rendez-vous");
        tvRdvTitle.setTextColor(ThemeColors.text());
        tvRdvTitle.setTextSize(15f);
        tvRdvTitle.setLayoutParams(lpWeight(1f));
        rdvHeader.addView(tvRdvTitle);

        tvRdvCount = makeBadge(ThemeColors.primary());
        rdvHeader.addView(tvRdvCount);
        card.addView(rdvHeader);

        rdvList = UiFactory.vertical(activity);
        card.addView(rdvList, new LinearLayout.LayoutParams(-1, -2));
    }

    private void buildUpcomingCard(LinearLayout card) {
        LinearLayout upHeader = UiFactory.horizontal(activity);

        LinearLayout.LayoutParams uhp = lpFull();
        uhp.bottomMargin = dp(DS.GAP_SM);
        upHeader.setLayoutParams(uhp);

        TextView tvUpTitle = UiFactory.sectionTitle(activity, "À venir");
        tvUpTitle.setTextColor(ThemeColors.text());
        tvUpTitle.setLayoutParams(lpWeight(1f));
        upHeader.addView(tvUpTitle);

        tvUpcomingCount = makeBadge(ThemeColors.primary());
        upHeader.addView(tvUpcomingCount);
        card.addView(upHeader);

        upcomingList = UiFactory.vertical(activity);
        card.addView(upcomingList, new LinearLayout.LayoutParams(-1, -2));
    }

    private void load() {
        AgendaRepository.loadAll(activity, new AgendaRepository.OnDataLoaded() {
            public void onLoaded(AgendaModels.AgendaData data) {
                currentData = data;
                selectedDay = -1;
                refreshAll();
            }

            public void onError(String msg) {
                currentData = new AgendaModels.AgendaData(
                        new java.util.ArrayList<>(),
                        new java.util.ArrayList<>(),
                        new java.util.ArrayList<>()
                );
                refreshAll();
            }
        });
    }

    private void refreshAll() {
        if (currentData == null) {
            return;
        }

        refreshCalendar();
        renderUpcoming(currentData);
        renderRdv(currentData);
        updateSubtitle();
    }

    private void refreshCalendar() {
        if (calendarGrid == null || tvMonthYear == null) {
            return;
        }

        String[] frMonths = {
                "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
                "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"
        };

        tvMonthYear.setText(frMonths[displayedMonth.get(Calendar.MONTH)] + " " + displayedMonth.get(Calendar.YEAR));

        AgendaCalendar.buildGrid(activity, calendarGrid, displayedMonth, selectedDay,
                currentData != null ? currentData
                        : new AgendaModels.AgendaData(
                        new java.util.ArrayList<>(),
                        new java.util.ArrayList<>(),
                        new java.util.ArrayList<>()
                ),
                (day, month, year, dayStart, dayEnd) -> {
                    selectedDay = day;
                    refreshCalendar();

                    if (currentData != null) {
                        showDayDetail(day, month, year, dayStart, dayEnd);
                    }
                });
    }

    private void navigateMonth(int direction) {
        displayedMonth.add(Calendar.MONTH, direction);
        selectedDay = -1;
        updateHeaderLabel();
        refreshCalendar();

        if (currentData != null) {
            renderUpcoming(currentData);
            renderRdv(currentData);
        }

        updateSubtitle();
    }

    private void updateHeaderLabel() {
        if (tvLabel != null) {
            String[] frUpper = {
                    "JANVIER", "FÉVRIER", "MARS", "AVRIL", "MAI", "JUIN",
                    "JUILLET", "AOÛT", "SEPTEMBRE", "OCTOBRE", "NOVEMBRE", "DÉCEMBRE"
            };

            tvLabel.setText("AGENDA · "
                    + frUpper[displayedMonth.get(Calendar.MONTH)]
                    + " "
                    + displayedMonth.get(Calendar.YEAR));
            tvLabel.setTextColor(ThemeColors.primary());
        }
    }

    private void renderUpcoming(AgendaModels.AgendaData data) {
        if (upcomingList == null) {
            return;
        }

        if (selectedDay > 0) {
            updateSubtitle();
            return;
        }

        upcomingList.removeAllViews();

        long nowMs = System.currentTimeMillis();
        Calendar limit = Calendar.getInstance();
        limit.add(Calendar.MONTH, 3);
        long horizonMs = limit.getTimeInMillis();

        List<AgendaModels.AgendaEvent> filteredEvents =
                AgendaFilters.filterEvents(data.events, activeFilter, nowMs, horizonMs);

        List<AgendaModels.AgendaTransaction> filteredTx =
                AgendaFilters.filterTransactions(data.transactions, activeFilter, nowMs, horizonMs);

        List<AgendaFilters.UnifiedItem> unified =
                AgendaFilters.buildUnified(filteredEvents, filteredTx);

        updateBadge(tvUpcomingCount, unified.size());

        if (unified.isEmpty()) {
            upcomingList.addView(makeEmptyView("Aucun événement à venir"));
            updateSubtitle();
            return;
        }

        SimpleDateFormat sdfDay = new SimpleDateFormat("d MMM", Locale.FRANCE);
        SimpleDateFormat sdfMonth = new SimpleDateFormat("MMMM yyyy", Locale.FRANCE);
        String currentGroup = "";

        for (AgendaFilters.UnifiedItem item : unified) {
            long itemDate = item.dateMs();
            String group = capitalize(sdfMonth.format(new Date(itemDate)));

            if (!group.equals(currentGroup)) {
                currentGroup = group;

                if (upcomingList.getChildCount() > 0) {
                    upcomingList.addView(makeSeparator());
                }

                upcomingList.addView(makeGroupLabel(group));
            }

            String dateStr = sdfDay.format(new Date(itemDate));

            if (item.isEvent()) {
                upcomingList.addView(makeEventRow(item.event, dateStr));
            } else {
                upcomingList.addView(makeTxRow(item.transaction, dateStr));
            }
        }

        updateSubtitle();
    }

    private void renderRdv(AgendaModels.AgendaData data) {
        if (rdvList == null) {
            return;
        }

        rdvList.removeAllViews();

        long nowMs = System.currentTimeMillis();
        Calendar limit = Calendar.getInstance();
        limit.add(Calendar.MONTH, 3);

        List<AgendaModels.AgendaEvent> rdvs =
                AgendaFilters.filterRdv(data.events, nowMs, limit.getTimeInMillis());

        updateBadge(tvRdvCount, rdvs.size());

        if (rdvs.isEmpty()) {
            rdvList.addView(makeEmptyView("Aucun rendez-vous à venir"));
            return;
        }

        SimpleDateFormat sdfDay = new SimpleDateFormat("d MMM", Locale.FRANCE);

        for (AgendaModels.AgendaEvent ev : rdvs) {
            rdvList.addView(makeEventRow(ev, sdfDay.format(new Date(ev.dateMs))));
        }
    }

    private void showDayDetail(int day, int month, int year, long dayStart, long dayEnd) {
        upcomingList.removeAllViews();

        String[] dayNames = { "Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim" };
        Calendar dc = Calendar.getInstance();
        dc.set(year, month, day);

        int dow = dc.get(Calendar.DAY_OF_WEEK);
        String dowStr = dayNames[(dow == 1) ? 6 : dow - 2];

        String[] frShort = {
                "Jan", "Fév", "Mar", "Avr", "Mai", "Juin",
                "Jul", "Aoû", "Sep", "Oct", "Nov", "Déc"
        };

        TextView tvDayHeader = UiFactory.sectionTitle(activity, dowStr + " " + day + " " + frShort[month]);
        tvDayHeader.setTextColor(ThemeColors.text());
        tvDayHeader.setTextSize(14f);

        LinearLayout.LayoutParams dhp = lpFull();
        dhp.bottomMargin = dp(DS.GAP_SM);
        tvDayHeader.setLayoutParams(dhp);

        upcomingList.addView(tvDayHeader);

        TextView tvQuickAdd = UiFactory.badge(
                activity,
                "+ Ajouter un événement ce jour",
                ThemeColors.primarySoft(),
                ThemeColors.primary()
        );
        tvQuickAdd.setTextSize(12f);
        tvQuickAdd.setGravity(Gravity.CENTER);
        tvQuickAdd.setPadding(dp(DS.GAP_SM), dp(8), dp(DS.GAP_SM), dp(8));
        tvQuickAdd.setBackground(UiFactory.bgBordered(
                ThemeColors.primarySoft(),
                ThemeColors.primary(),
                DS.R_LG,
                activity
        ));

        LinearLayout.LayoutParams qap = lpFull();
        qap.bottomMargin = dp(DS.GAP_SM);
        tvQuickAdd.setLayoutParams(qap);

        tvQuickAdd.setOnClickListener(v -> showAddEventDialog(dc));
        upcomingList.addView(tvQuickAdd);

        if (currentData == null) {
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("d MMM", Locale.FRANCE);
        String dateStr = sdf.format(new Date(dayStart));
        boolean any = false;

        for (AgendaModels.AgendaEvent ev : AgendaFilters.eventsInRange(currentData.events, dayStart, dayEnd)) {
            upcomingList.addView(makeEventRow(ev, dateStr));
            any = true;
        }

        for (AgendaModels.AgendaTransaction tx : AgendaFilters.transactionsInRange(currentData.transactions, dayStart, dayEnd)) {
            upcomingList.addView(makeTxRow(tx, dateStr));
            any = true;
        }

        if (!any) {
            upcomingList.addView(makeEmptyView("Aucune opération ce jour"));
        }
    }

    private void updateSubtitle() {
        if (tvSubtitle == null || currentData == null) {
            return;
        }

        int cnt = AgendaFilters.countEventsInMonth(currentData.events, displayedMonth);
        tvSubtitle.setText(cnt + " échéance" + (cnt > 1 ? "s" : "") + " ce mois");
        tvSubtitle.setTextColor(ThemeColors.subtext());
    }

    private LinearLayout makeEventRow(AgendaModels.AgendaEvent ev, String dateStr) {
        LinearLayout row = buildBaseRow();

        row.setOnLongClickListener(v -> {
            AgendaDialogs.showDeleteConfirm(activity, ev, this::load);
            return true;
        });

        row.addView(makeIconCircle(
                AgendaModels.iconForType(ev.type),
                AgendaModels.bgColorForType(ev.type)
        ));

        LinearLayout centre = UiFactory.vertical(activity);

        TextView tvTitle = makeItemTitle(ev.title);
        String cap = dateStr.isEmpty() ? ev.type : ev.type + " · " + capitalize(dateStr);

        centre.addView(tvTitle);
        centre.addView(makeItemSub(cap));

        row.addView(centre, lpWeight(1f));

        if (ev.amount != 0) {
            row.addView(makeItemAmount(ev.amount, ev.isIncome()));
        }

        return row;
    }

    private LinearLayout makeTxRow(AgendaModels.AgendaTransaction tx, String dateStr) {
        LinearLayout row = buildBaseRow();

        String icon = tx.isFixed() ? "📌" : tx.isIncome() ? "💰" : "💸";
        int bg = tx.isFixed() ? ThemeColors.primarySoft() : tx.isIncome() ? DS.GREEN_LIGHT : DS.RED_LIGHT;

        row.addView(makeIconCircle(icon, bg));

        LinearLayout centre = UiFactory.vertical(activity);

        centre.addView(makeItemTitle(tx.description()));

        String subLabel = tx.isFixed()
                ? ("fixed_done".equals(tx.type) ? "Charge fixe passée · " : "Charge fixe prévue · ") + dateStr
                : (!tx.category.isEmpty() ? tx.category + " · " + dateStr : dateStr);

        centre.addView(makeItemSub(subLabel));

        row.addView(centre, lpWeight(1f));
        row.addView(makeItemAmount(tx.amount, tx.isIncome()));

        return row;
    }

    private LinearLayout buildBaseRow() {
        LinearLayout row = UiFactory.cardRow(activity);

        LinearLayout.LayoutParams rp = lpFull();
        rp.bottomMargin = dp(DS.GAP_SM);
        row.setLayoutParams(rp);

        row.setPadding(dp(DS.GAP_SM), dp(DS.GAP_SM), dp(DS.GAP_SM), dp(DS.GAP_SM));
        return row;
    }

    private TextView makeIconCircle(String icon, int bgColor) {
        TextView tv = UiFactory.circleIcon(activity, icon, bgColor, ThemeColors.text(), 40);

        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(dp(40), dp(40));
        wp.rightMargin = dp(DS.GAP_SM);
        tv.setLayoutParams(wp);

        return tv;
    }

    private TextView makeItemTitle(String text) {
        TextView tv = UiFactory.body(activity, text);
        tv.setTextColor(ThemeColors.text());
        tv.setTextSize(13f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return tv;
    }

    private TextView makeItemSub(String text) {
        TextView tv = UiFactory.bodyMuted(activity, text);
        tv.setTextColor(ThemeColors.subtext());
        tv.setTextSize(11f);
        return tv;
    }

    private TextView makeItemAmount(double amount, boolean isPositive) {
        TextView tv = isPositive
                ? UiFactory.amountIncome(activity, "+" + Fmt.money(Math.abs(amount)))
                : UiFactory.amountExpense(activity, "−" + Fmt.money(Math.abs(amount)));

        tv.setTextSize(12f);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(6);
        tv.setLayoutParams(lp);

        return tv;
    }

    private TextView makeEmptyView(String text) {
        TextView tv = UiFactory.bodyMuted(activity, text);
        tv.setTextColor(ThemeColors.subtext());
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(DS.PAD_CARD), 0, dp(DS.PAD_CARD));
        tv.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return tv;
    }

    private TextView makeGroupLabel(String text) {
        TextView tv = UiFactory.smallLabel(activity, text.toUpperCase(Locale.FRANCE));
        tv.setTextColor(ThemeColors.primary());

        LinearLayout.LayoutParams lp = lpFull();
        lp.bottomMargin = dp(6);
        tv.setLayoutParams(lp);

        return tv;
    }

    private View makeSeparator() {
        return UiFactory.divider(activity);
    }

    private LinearLayout makeCard() {
        LinearLayout card = UiFactory.card(activity);
        card.setPadding(dp(DS.PAD_INPUT), dp(DS.PAD_INPUT), dp(DS.PAD_INPUT), dp(12));
        return card;
    }

    private void styleChip(TextView chip, boolean active) {
        if (active) {
            chip.setTextColor(Color.WHITE);
            chip.setTypeface(null, Typeface.BOLD);
            chip.setBackground(UiFactory.bg(ThemeColors.primary(), DS.R_LG, activity));
        } else {
            chip.setTextColor(ThemeColors.text());
            chip.setTypeface(null, Typeface.NORMAL);
            chip.setBackground(UiFactory.bgBordered(
                    ThemeColors.card(),
                    ThemeColors.border(),
                    DS.R_LG,
                    activity
            ));
        }
    }

    private TextView makeNavBtn(String label) {
        TextView tv = UiFactory.badge(activity, label, ThemeColors.card(), ThemeColors.text());
        tv.setTextSize(18f);
        tv.setPadding(dp(DS.GAP_SM), dp(5), dp(DS.GAP_SM), dp(5));
        tv.setBackground(UiFactory.bgBordered(
                ThemeColors.card(),
                ThemeColors.border(),
                DS.R_XS,
                activity
        ));
        tv.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        return tv;
    }

    private TextView makeBadge(int color) {
        TextView tv = UiFactory.badge(activity, "0", color, Color.WHITE);
        tv.setVisibility(View.GONE);
        return tv;
    }

    private void updateBadge(TextView badge, int count) {
        if (badge == null) {
            return;
        }

        badge.setText(String.valueOf(count));
        badge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }

        return s.substring(0, 1).toUpperCase(Locale.FRANCE) + s.substring(1);
    }

    private void showAddEventDialog(Calendar preselectedDate) {
        List<String> members = currentData != null ? currentData.members : new java.util.ArrayList<>();
        AgendaDialogs.showAddEventDialog(activity, preselectedDate, members, this::load);
    }
}