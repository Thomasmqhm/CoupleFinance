package com.couplefinance.ui.abonnements;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;

import com.couplefinance.AppToast;
import com.couplefinance.AuthManager;
import com.couplefinance.UserSession;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.Fmt;
import com.couplefinance.core.ui.effects.GradientFactory;
import com.couplefinance.core.ui.effects.ShadowFactory;
import com.couplefinance.data.RecurringChargeManager;
import com.couplefinance.ui.settings.SettingsCache;
import com.couplefinance.ui.settings.SettingsChargeWriter;
import com.couplefinance.ui.settings.SettingsDialog;
import com.couplefinance.ui.settings.SettingsModels;
import com.couplefinance.ui.settings.SettingsRepository;
import com.couplefinance.data.JointAccountManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AbonnementsView {

    private final Activity activity;
    private ScrollView scroll;
    private LinearLayout content;
    private LinearLayout chargesContainer;
    private LinearLayout pillsRow;
    private String selectedCategory = "Tous";
    private List<SettingsModels.FixedCharge> charges = new ArrayList<>();

    public AbonnementsView(Activity activity) {
        this.activity = activity;
    }

    public View getView() {
        buildLayout();
        loadData();
        return scroll;
    }

    private void buildLayout() {
        scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(ThemeColors.background());

        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                DS.dp(activity, DS.PAD_PAGE),
                DS.dp(activity, 20),
                DS.dp(activity, DS.PAD_PAGE),
                DS.dp(activity, DS.NAV_CLEARANCE)
        );

        scroll.addView(content);

        content.addView(buildPageHeader());
        content.addView(buildHeroCard());
        content.addView(buildCategoryPills());

        chargesContainer = new LinearLayout(activity);
        chargesContainer.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = DS.dp(activity, 8);
        chargesContainer.setLayoutParams(clp);

        content.addView(chargesContainer);
    }

    private View buildPageHeader() {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = DS.dp(activity, 20);
        header.setLayoutParams(lp);

        TextView badge = new TextView(activity);
        badge.setText("PRÉLÈVEMENTS");
        badge.setTextColor(ThemeColors.primary());
        badge.setTextSize(DS.TEXT_XS);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setLetterSpacing(0.10f);
        header.addView(badge);

        TextView title = new TextView(activity);
        title.setText("Abonnements");
        title.setTextColor(ThemeColors.text());
        title.setTextSize(DS.TEXT_TITLE);
        title.setTypeface(null, Typeface.BOLD);

        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(-1, -2);
        tLp.topMargin = DS.dp(activity, 5);
        header.addView(title, tLp);

        TextView sub = new TextView(activity);
        sub.setText("Charges récurrentes du foyer");
        sub.setTextColor(ThemeColors.subtext());
        sub.setTextSize(DS.TEXT_SM);

        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(-1, -2);
        sLp.topMargin = DS.dp(activity, 3);
        header.addView(sub, sLp);

        View divider = new View(activity);
        divider.setBackgroundColor(ThemeColors.divider());

        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(-1, DS.dp(activity, 1));
        dLp.topMargin = DS.dp(activity, 16);
        header.addView(divider, dLp);

        return header;
    }

    private View buildHeroCard() {
        double typique = totalTypique();
        double minTotal = totalMin();
        double maxTotal = totalMax();
        int members = Math.max(1, memberCount());
        double perPers = typique / members;
        double annual  = typique * 12;
        boolean hasVar = hasVariableCharges();

        Calendar cal = Calendar.getInstance();
        int day    = cal.get(Calendar.DAY_OF_MONTH);
        int maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        float progress = Math.min(1f, (float) day / maxDay);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                DS.dp(activity, 22),
                DS.dp(activity, 22),
                DS.dp(activity, 22),
                DS.dp(activity, 20)
        );

        GradientDrawable heroBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{ThemeColors.primary(), ThemeColors.primaryDark()}
        );
        heroBg.setCornerRadius(DS.dp(activity, DS.R_XL));
        card.setBackground(heroBg);
        card.setElevation(DS.dp(activity, 8));

        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, -2);
        heroLp.bottomMargin = DS.dp(activity, 16);
        card.setLayoutParams(heroLp);

        TextView tvLabel = new TextView(activity);
        tvLabel.setText("TOTAL TYPIQUE");
        tvLabel.setTextColor(ThemeColors.withAlpha(Color.WHITE, 180));
        tvLabel.setTextSize(DS.TEXT_XS);
        tvLabel.setTypeface(null, Typeface.BOLD);
        tvLabel.setLetterSpacing(0.10f);
        card.addView(tvLabel);

        TextView tvTotal = new TextView(activity);
        tvTotal.setText(Fmt.money(typique));
        tvTotal.setTextColor(Color.WHITE);
        tvTotal.setTextSize(DS.TEXT_BALANCE);
        tvTotal.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));

        LinearLayout.LayoutParams totalLp = new LinearLayout.LayoutParams(-1, -2);
        totalLp.topMargin = DS.dp(activity, 6);
        totalLp.bottomMargin = DS.dp(activity, hasVar ? 8 : 16);
        card.addView(tvTotal, totalLp);

        // Fourchette seuil bas / seuil haut (visible uniquement si charges variables)
        if (hasVar) {
            LinearLayout rangeRow = new LinearLayout(activity);
            rangeRow.setOrientation(LinearLayout.HORIZONTAL);
            rangeRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

            LinearLayout.LayoutParams rangeLp = new LinearLayout.LayoutParams(-1, -2);
            rangeLp.bottomMargin = DS.dp(activity, 14);
            rangeRow.setLayoutParams(rangeLp);

            TextView tvRange = new TextView(activity);
            tvRange.setText("Fourchette  ");
            tvRange.setTextColor(ThemeColors.withAlpha(Color.WHITE, 160));
            tvRange.setTextSize(DS.TEXT_XS);
            rangeRow.addView(tvRange);

            rangeRow.addView(rangePill("Seuil bas", Fmt.money(minTotal)));

            LinearLayout.LayoutParams rp2 = new LinearLayout.LayoutParams(-2, -2);
            rp2.leftMargin = DS.dp(activity, 8);
            rangeRow.addView(rangePill("Seuil haut", Fmt.money(maxTotal)), rp2);

            card.addView(rangeRow);
        }

        LinearLayout statsRow = new LinearLayout(activity);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);

        statsRow.addView(statPill("Par personne", Fmt.money(perPers)));

        LinearLayout.LayoutParams s2 = new LinearLayout.LayoutParams(-2, -2);
        s2.leftMargin = DS.dp(activity, 10);
        statsRow.addView(statPill("Annuel", Fmt.money(annual)), s2);

        LinearLayout.LayoutParams s3 = new LinearLayout.LayoutParams(-2, -2);
        s3.leftMargin = DS.dp(activity, 10);
        statsRow.addView(statPill("Charges", String.valueOf(charges.size())), s3);

        LinearLayout.LayoutParams statsLp = new LinearLayout.LayoutParams(-1, -2);
        statsLp.bottomMargin = DS.dp(activity, 20);
        card.addView(statsRow, statsLp);

        card.addView(buildMonthProgress(progress, day, maxDay));

        TextView btnAdd = new TextView(activity);
        btnAdd.setText("+ Nouvel abonnement");
        btnAdd.setTextColor(ThemeColors.primary());
        btnAdd.setTextSize(DS.TEXT_SM);
        btnAdd.setTypeface(null, Typeface.BOLD);
        btnAdd.setGravity(Gravity.CENTER);
        btnAdd.setPadding(
                DS.dp(activity, 20),
                DS.dp(activity, 12),
                DS.dp(activity, 20),
                DS.dp(activity, 12)
        );

        GradientDrawable addBg = new GradientDrawable();
        addBg.setColor(Color.WHITE);
        addBg.setCornerRadius(DS.dp(activity, DS.R_LG));
        btnAdd.setBackground(addBg);

        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(-1, DS.dp(activity, DS.BTN_HEIGHT));
        addLp.topMargin = DS.dp(activity, 16);
        btnAdd.setLayoutParams(addLp);
        btnAdd.setOnClickListener(v -> showAddChargeNameDialog());

        card.addView(btnAdd);

        return card;
    }

    private View rangePill(String label, String value) {
        LinearLayout pill = new LinearLayout(activity);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setPadding(
                DS.dp(activity, 10),
                DS.dp(activity, 6),
                DS.dp(activity, 10),
                DS.dp(activity, 6)
        );

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ThemeColors.withAlpha(Color.WHITE, 20));
        bg.setCornerRadius(DS.dp(activity, DS.R_SM));
        bg.setStroke(DS.dp(activity, 1), ThemeColors.withAlpha(Color.WHITE, 50));
        pill.setBackground(bg);

        TextView tvValue = new TextView(activity);
        tvValue.setText(value);
        tvValue.setTextColor(Color.WHITE);
        tvValue.setTextSize(DS.TEXT_SM);
        tvValue.setTypeface(null, Typeface.BOLD);
        pill.addView(tvValue);

        TextView tvLabel = new TextView(activity);
        tvLabel.setText(label);
        tvLabel.setTextColor(ThemeColors.withAlpha(Color.WHITE, 160));
        tvLabel.setTextSize(DS.TEXT_XS);
        pill.addView(tvLabel);

        return pill;
    }

    private View statPill(String label, String value) {
        LinearLayout pill = new LinearLayout(activity);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setPadding(
                DS.dp(activity, 12),
                DS.dp(activity, 8),
                DS.dp(activity, 12),
                DS.dp(activity, 8)
        );

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ThemeColors.withAlpha(Color.WHITE, 24));
        bg.setCornerRadius(DS.dp(activity, DS.R_MD));
        pill.setBackground(bg);

        TextView tvValue = new TextView(activity);
        tvValue.setText(value);
        tvValue.setTextColor(Color.WHITE);
        tvValue.setTextSize(DS.TEXT_SM);
        tvValue.setTypeface(null, Typeface.BOLD);
        pill.addView(tvValue);

        TextView tvLabel = new TextView(activity);
        tvLabel.setText(label);
        tvLabel.setTextColor(ThemeColors.withAlpha(Color.WHITE, 160));
        tvLabel.setTextSize(DS.TEXT_XS);
        pill.addView(tvLabel);

        return pill;
    }

    private View buildMonthProgress(float progress, int day, int maxDay) {
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout labelRow = new LinearLayout(activity);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView tvLabel = new TextView(activity);
        tvLabel.setText("Progression du mois");
        tvLabel.setTextColor(ThemeColors.withAlpha(Color.WHITE, 160));
        tvLabel.setTextSize(DS.TEXT_XS);
        labelRow.addView(tvLabel, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView tvPct = new TextView(activity);
        tvPct.setText("Jour " + day + "/" + maxDay);
        tvPct.setTextColor(ThemeColors.withAlpha(Color.WHITE, 180));
        tvPct.setTextSize(DS.TEXT_XS);
        tvPct.setTypeface(null, Typeface.BOLD);
        labelRow.addView(tvPct);

        LinearLayout.LayoutParams lblLp = new LinearLayout.LayoutParams(-1, -2);
        lblLp.bottomMargin = DS.dp(activity, 6);
        wrap.addView(labelRow, lblLp);

        FrameLayout track = new FrameLayout(activity);

        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setColor(ThemeColors.withAlpha(Color.WHITE, 25));
        trackBg.setCornerRadius(DS.dp(activity, 4));
        track.setBackground(trackBg);

        final View fill = new View(activity);
        fill.setLayoutParams(new FrameLayout.LayoutParams(0, -1));

        GradientDrawable fillBg = new GradientDrawable();
        fillBg.setColor(Color.WHITE);
        fillBg.setCornerRadius(DS.dp(activity, 4));
        fill.setBackground(fillBg);

        track.addView(fill);
        wrap.addView(track, new LinearLayout.LayoutParams(-1, DS.dp(activity, 5)));

        final float fp = progress;

        track.post(() -> {
            int w = track.getWidth();
            if (w <= 0) return;

            ValueAnimator anim = ValueAnimator.ofInt(0, Math.round(w * fp));
            anim.setDuration(700);
            anim.setInterpolator(new DecelerateInterpolator());
            anim.addUpdateListener(a -> {
                ViewGroup.LayoutParams lp = fill.getLayoutParams();
                lp.width = (int) a.getAnimatedValue();
                fill.setLayoutParams(lp);
            });
            anim.start();
        });

        return wrap;
    }

    private View buildCategoryPills() {
        HorizontalScrollView hsv = new HorizontalScrollView(activity);
        hsv.setHorizontalScrollBarEnabled(false);

        LinearLayout.LayoutParams hsvLp = new LinearLayout.LayoutParams(-1, -2);
        hsvLp.bottomMargin = DS.dp(activity, 8);
        hsv.setLayoutParams(hsvLp);

        pillsRow = new LinearLayout(activity);
        pillsRow.setOrientation(LinearLayout.HORIZONTAL);
        pillsRow.setPadding(0, DS.dp(activity, 4), 0, DS.dp(activity, 4));

        hsv.addView(pillsRow);
        refreshPills();

        return hsv;
    }

    private void refreshPills() {
        if (pillsRow == null) return;

        pillsRow.removeAllViews();

        List<String> categories = new ArrayList<>();
        categories.add("Tous");

        for (SettingsModels.FixedCharge c : charges) {
            if (c != null && c.category != null && !c.category.isEmpty()
                    && !categories.contains(c.category)) {
                categories.add(c.category);
            }
        }

        for (String cat : categories) {
            boolean selected = cat.equals(selectedCategory);

            TextView pill = buildPill(cat, selected);
            final String fc = cat;

            pill.setOnClickListener(v -> {
                selectedCategory = fc;
                refreshPills();
                renderCharges();
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (categories.indexOf(cat) > 0) {
                lp.leftMargin = DS.dp(activity, 8);
            }

            pillsRow.addView(pill, lp);
        }
    }

    private TextView buildPill(String text, boolean selected) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextSize(DS.TEXT_SM);
        tv.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(
                DS.dp(activity, 16),
                DS.dp(activity, 8),
                DS.dp(activity, 16),
                DS.dp(activity, 8)
        );

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(DS.dp(activity, DS.R_MD));

        if (selected) {
            tv.setTextColor(ThemeColors.primary());
            bg.setColor(ThemeColors.withAlpha(ThemeColors.primary(), 18));
            bg.setStroke(DS.dp(activity, 1), ThemeColors.withAlpha(ThemeColors.primary(), 50));
        } else {
            tv.setTextColor(ThemeColors.subtext());
            bg.setColor(ThemeColors.backgroundSecondary());
        }

        tv.setBackground(bg);

        return tv;
    }

    private void renderCharges() {
        if (chargesContainer == null) return;

        chargesContainer.removeAllViews();

        List<SettingsModels.FixedCharge> filtered = new ArrayList<>();

        for (SettingsModels.FixedCharge c : charges) {
            if (c == null) continue;

            if ("Tous".equals(selectedCategory) || selectedCategory.equals(c.category)) {
                filtered.add(c);
            }
        }

        if (filtered.isEmpty()) {
            chargesContainer.addView(buildEmptyState());
            return;
        }

        LinkedHashMap<String, List<SettingsModels.FixedCharge>> grouped = new LinkedHashMap<>();

        for (SettingsModels.FixedCharge c : filtered) {
            String cat = c.category != null && !c.category.isEmpty() ? c.category : "Général";

            if (!grouped.containsKey(cat)) {
                grouped.put(cat, new ArrayList<>());
            }

            grouped.get(cat).add(c);
        }

        for (Map.Entry<String, List<SettingsModels.FixedCharge>> entry : grouped.entrySet()) {
            chargesContainer.addView(buildGroupHeader(entry.getKey(), entry.getValue()));

            for (SettingsModels.FixedCharge charge : entry.getValue()) {
                chargesContainer.addView(buildChargeCard(charge));
            }
        }
    }

    private View buildGroupHeader(String category, List<SettingsModels.FixedCharge> items) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = DS.dp(activity, 18);
        lp.bottomMargin = DS.dp(activity, 10);
        row.setLayoutParams(lp);

        TextView tvCat = new TextView(activity);
        tvCat.setText(category.toUpperCase(Locale.FRANCE));
        tvCat.setTextColor(ThemeColors.subtext());
        tvCat.setTextSize(DS.TEXT_XS);
        tvCat.setTypeface(null, Typeface.BOLD);
        tvCat.setLetterSpacing(0.08f);

        row.addView(tvCat, new LinearLayout.LayoutParams(0, -2, 1f));

        double groupTotal = 0;

        for (SettingsModels.FixedCharge c : items) {
            groupTotal += c.amount;
        }

        TextView tvTotal = new TextView(activity);
        tvTotal.setText(Fmt.money(groupTotal) + "/mois");
        tvTotal.setTextColor(ThemeColors.primary());
        tvTotal.setTextSize(DS.TEXT_XS);
        tvTotal.setTypeface(null, Typeface.BOLD);

        row.addView(tvTotal);

        return row;
    }

    private View buildChargeCard(SettingsModels.FixedCharge charge) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(
                DS.dp(activity, 16),
                DS.dp(activity, 16),
                DS.dp(activity, 16),
                DS.dp(activity, 16)
        );
        card.setBackground(GradientFactory.bordered(
                activity,
                ThemeColors.card(),
                ThemeColors.border(),
                DS.R_LG
        ));
        card.setElevation(DS.dp(activity, 3));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.bottomMargin = DS.dp(activity, 10);
        card.setLayoutParams(cardLp);

        TextView tvIcon = new TextView(activity);
        tvIcon.setText(charge.icon != null && !charge.icon.isEmpty() ? charge.icon : "💳");
        tvIcon.setTextSize(22f);
        tvIcon.setGravity(Gravity.CENTER);
        tvIcon.setBackground(GradientFactory.circle(ThemeColors.primarySoft()));

        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                DS.dp(activity, 48),
                DS.dp(activity, 48)
        );
        iconLp.rightMargin = DS.dp(activity, 14);
        card.addView(tvIcon, iconLp);

        LinearLayout infoCol = new LinearLayout(activity);
        infoCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvName = new TextView(activity);
        tvName.setText(charge.name != null ? charge.name : "Charge");
        tvName.setTextColor(ThemeColors.text());
        tvName.setTextSize(DS.TEXT_BODY);
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setSingleLine(false);
        tvName.setMaxLines(2);
        infoCol.addView(tvName);

        int dayOfMonth = normalizeDayOfMonth(charge.dayOfMonth);
        int today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
        boolean alreadyDone = today >= dayOfMonth;

        String statusText = alreadyDone
                ? "Prélevé le " + dayOfMonth
                : "Dans " + (dayOfMonth - today) + " jour" + ((dayOfMonth - today) > 1 ? "s" : "");

        int statusColor = alreadyDone ? ThemeColors.success() : ThemeColors.warning();

        int statusBgColor = alreadyDone
                ? ThemeColors.withAlpha(ThemeColors.success(), 18)
                : ThemeColors.withAlpha(ThemeColors.warning(), 18);

        LinearLayout metaRow = new LinearLayout(activity);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(-1, -2);
        metaLp.topMargin = DS.dp(activity, 5);
        metaRow.setLayoutParams(metaLp);

        TextView tvStatus = new TextView(activity);
        tvStatus.setText(statusText);
        tvStatus.setTextColor(statusColor);
        tvStatus.setTextSize(DS.TEXT_XS);
        tvStatus.setTypeface(null, Typeface.BOLD);
        tvStatus.setPadding(
                DS.dp(activity, 8),
                DS.dp(activity, 3),
                DS.dp(activity, 8),
                DS.dp(activity, 3)
        );

        GradientDrawable statusBg = new GradientDrawable();
        statusBg.setColor(statusBgColor);
        statusBg.setCornerRadius(DS.dp(activity, DS.R_SM));
        tvStatus.setBackground(statusBg);

        metaRow.addView(tvStatus);

        TextView tvFreq = new TextView(activity);
        tvFreq.setText("  · " + (charge.frequency != null ? charge.frequency : "Mensuel"));
        tvFreq.setTextColor(ThemeColors.subtext());
        tvFreq.setTextSize(DS.TEXT_XS);
        metaRow.addView(tvFreq);

        infoCol.addView(metaRow);

        TextView tvPaidBy = new TextView(activity);
        tvPaidBy.setText("Payé par " + safePaidBy(charge));
        tvPaidBy.setTextColor(ThemeColors.primary());
        tvPaidBy.setTextSize(DS.TEXT_XS);
        tvPaidBy.setTypeface(null, Typeface.BOLD);

        LinearLayout.LayoutParams paidLp = new LinearLayout.LayoutParams(-1, -2);
        paidLp.topMargin = DS.dp(activity, 6);
        infoCol.addView(tvPaidBy, paidLp);

        card.addView(infoCol, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout rightCol = new LinearLayout(activity);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setGravity(Gravity.END);

        TextView tvAmount = new TextView(activity);
        if (charge.isVariable()) {
            tvAmount.setText(Fmt.money(charge.amountMin) + " – " + Fmt.money(charge.amountMax));
        } else {
            tvAmount.setText(Fmt.money(charge.amount));
        }
        tvAmount.setTextColor(ThemeColors.text());
        tvAmount.setTextSize(charge.isVariable() ? DS.TEXT_SM : DS.TEXT_STAT);
        tvAmount.setTypeface(null, Typeface.BOLD);
        tvAmount.setGravity(Gravity.END);
        rightCol.addView(tvAmount);

        if (charge.isVariable()) {
            TextView tvTypical = new TextView(activity);
            tvTypical.setText("≈ " + Fmt.money(charge.amount) + " typique");
            tvTypical.setTextColor(ThemeColors.subtext());
            tvTypical.setTextSize(DS.TEXT_XS);
            tvTypical.setGravity(Gravity.END);
            rightCol.addView(tvTypical);
        }

        TextView tvPer = new TextView(activity);
        double displayAmt = charge.isVariable() ? charge.amountMax : charge.amount;
        tvPer.setText(Fmt.money(displayAmt / Math.max(1, memberCount())) + "/pers.");
        tvPer.setTextColor(ThemeColors.primary());
        tvPer.setTextSize(DS.TEXT_XS);
        tvPer.setTypeface(null, Typeface.BOLD);
        tvPer.setGravity(Gravity.END);
        rightCol.addView(tvPer);

        LinearLayout actRow = new LinearLayout(activity);
        actRow.setOrientation(LinearLayout.HORIZONTAL);
        actRow.setGravity(Gravity.END);

        LinearLayout.LayoutParams arLp = new LinearLayout.LayoutParams(-1, -2);
        arLp.topMargin = DS.dp(activity, 8);
        actRow.setLayoutParams(arLp);

        TextView btnLabel = miniAction("Libellé", ThemeColors.primary());
        btnLabel.setOnClickListener(v -> showEditLabelDialog(charge));
        actRow.addView(btnLabel);

        TextView btnAmt = miniAction("Montant", ThemeColors.primary());
        LinearLayout.LayoutParams amtLp = new LinearLayout.LayoutParams(-2, -2);
        amtLp.leftMargin = DS.dp(activity, 12);
        btnAmt.setOnClickListener(v -> showEditAmountDialog(charge));
        actRow.addView(btnAmt, amtLp);

        TextView btnPayer = miniAction("Payeur", ThemeColors.primary());
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(-2, -2);
        pLp.leftMargin = DS.dp(activity, 12);
        btnPayer.setOnClickListener(v -> showEditPaidByDialog(charge));
        actRow.addView(btnPayer, pLp);

        TextView btnDay = miniAction("Jour", ThemeColors.primary());
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(-2, -2);
        dLp.leftMargin = DS.dp(activity, 12);
        btnDay.setOnClickListener(v -> showEditDayDialog(charge));
        actRow.addView(btnDay, dLp);

        TextView btnDel = miniAction("Supprimer", ThemeColors.danger());
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(-2, -2);
        delLp.leftMargin = DS.dp(activity, 12);
        btnDel.setOnClickListener(v -> confirmDelete(charge));
        actRow.addView(btnDel, delLp);

        rightCol.addView(actRow);
        card.addView(rightCol);

        ShadowFactory.card(card, activity);

        return card;
    }

    private TextView miniAction(String text, int color) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(DS.TEXT_XS);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.END);
        return tv;
    }

    private View buildEmptyState() {
        LinearLayout empty = new LinearLayout(activity);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(
                DS.dp(activity, 24),
                DS.dp(activity, 48),
                DS.dp(activity, 24),
                DS.dp(activity, 48)
        );
        empty.setBackground(GradientFactory.bordered(
                activity,
                ThemeColors.card(),
                ThemeColors.border(),
                DS.R_XL
        ));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = DS.dp(activity, 16);
        empty.setLayoutParams(lp);

        TextView tvEmoji = new TextView(activity);
        tvEmoji.setText("🔁");
        tvEmoji.setTextSize(40f);
        tvEmoji.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams eLp = new LinearLayout.LayoutParams(-2, -2);
        eLp.gravity = Gravity.CENTER_HORIZONTAL;
        eLp.bottomMargin = DS.dp(activity, 16);
        empty.addView(tvEmoji, eLp);

        TextView tvTitle = new TextView(activity);
        tvTitle.setText("Aucun abonnement");
        tvTitle.setTextColor(ThemeColors.text());
        tvTitle.setTextSize(DS.TEXT_SECTION);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        empty.addView(tvTitle);

        TextView tvSub = new TextView(activity);
        tvSub.setText("Ajoutez votre première charge récurrente\n(loyer, internet, streaming…)");
        tvSub.setTextColor(ThemeColors.subtext());
        tvSub.setTextSize(DS.TEXT_SM);
        tvSub.setGravity(Gravity.CENTER);
        tvSub.setLineSpacing(4f, 1f);

        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = DS.dp(activity, 8);
        subLp.bottomMargin = DS.dp(activity, 20);
        empty.addView(tvSub, subLp);

        TextView btnAdd = new TextView(activity);
        btnAdd.setText("+ Ajouter un abonnement");
        btnAdd.setTextColor(Color.WHITE);
        btnAdd.setTextSize(DS.TEXT_SM);
        btnAdd.setTypeface(null, Typeface.BOLD);
        btnAdd.setGravity(Gravity.CENTER);
        btnAdd.setPadding(
                DS.dp(activity, 24),
                DS.dp(activity, 12),
                DS.dp(activity, 24),
                DS.dp(activity, 12)
        );

        GradientDrawable addBg = new GradientDrawable();
        addBg.setColor(ThemeColors.primary());
        addBg.setCornerRadius(DS.dp(activity, DS.R_LG));
        btnAdd.setBackground(addBg);
        btnAdd.setOnClickListener(v -> showAddChargeNameDialog());

        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(-2, DS.dp(activity, DS.BTN_HEIGHT));
        addLp.gravity = Gravity.CENTER_HORIZONTAL;
        empty.addView(btnAdd, addLp);

        return empty;
    }

    private void showAddChargeNameDialog() {
        SettingsDialog.showTextDialog(
                activity,
                "",
                "Nouvel abonnement",
                "Nom du service ou de la charge récurrente.",
                "",
                "Ex : Netflix, Loyer, EDF…",
                name -> showAddAmountDialog(name)
        );
    }

    private void showAddAmountDialog(String name) {
        SettingsDialog.showAmountDialog(
                activity,
                "€",
                "Montant mensuel",
                "Montant de " + name + " par mois.",
                0,
                amount -> showAddPaidByDialog(name, amount)
        );
    }

    private void showAddPaidByDialog(String name, double amount) {
        showPaidByPicker(
                "Payé par",
                "Qui paie réellement " + name + " ?",
                getDefaultPaidBy(),
                paidBy -> showAddDayDialog(name, amount, paidBy)
        );
    }

    private void showAddDayDialog(String name, double amount, String paidBy) {
        SettingsDialog.showDayDialog(activity, 1, day -> addCharge(name, amount, day, paidBy));
    }

    private void addCharge(String name, double amount, int day, String paidBy) {
        SettingsModels.FixedCharge charge = new SettingsModels.FixedCharge(
                guessIcon(name),
                name,
                guessCategory(name),
                amount
        );

        charge.dayOfMonth = normalizeDayOfMonth(day);
        charge.frequency = "Mensuel";
        charge.ratioA = 50;
        charge.ratioB = 50;
        charge.lastAppliedMonth = "";
        charge.paidBy = safeName(paidBy, getDefaultPaidBy());

        charges.add(charge);

        SettingsModels.State state = SettingsCache.get();
        if (state.charges == null) {
            state.charges = new ArrayList<>();
        }
        state.charges.add(charge);
        SettingsCache.set(state);

        refresh();

        SettingsChargeWriter.saveCharge(charge, new SettingsChargeWriter.Callback() {
            public void onSuccess() {
                activity.runOnUiThread(() -> {
                    RecurringChargeManager.getInstance().init(activity);
                    RecurringChargeManager.getInstance().checkAndApplyRecurringCharges(
                            () -> AppToast.success(activity, "Abonnement ajouté et synchronisé")
                    );
                });
            }

            public void onError(String e) {
                activity.runOnUiThread(() -> AppToast.error(activity, "Sauvegarde impossible"));
            }
        });
    }

    private void showEditLabelDialog(SettingsModels.FixedCharge charge) {
        // Find other charges with same label to warn the user
        List<SettingsModels.FixedCharge> sameLabel = new ArrayList<>();
        for (SettingsModels.FixedCharge c : charges) {
            if (c != charge && c.name != null && c.name.equalsIgnoreCase(charge.name)) {
                sameLabel.add(c);
            }
        }

        android.widget.EditText input = new android.widget.EditText(activity);
        input.setText(charge.name);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int p = DS.dp(activity, 20);
        wrap.setPadding(p, DS.dp(activity, 8), p, 0);

        if (!sameLabel.isEmpty()) {
            // Several charges share this label — show amount + day to disambiguate
            TextView warn = new TextView(activity);
            warn.setText("⚠️  " + sameLabel.size() + " autre(s) charge(s) portent ce libellé.\n"
                    + "Vous modifiez celle-ci : "
                    + Fmt.money(charge.amount) + " · le " + charge.dayOfMonth + " du mois.");
            warn.setTextColor(0xFFF59E0B);
            warn.setTextSize(DS.TEXT_SM);
            warn.setLineSpacing(DS.dp(activity, 3), 1f);
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-1, -2);
            wlp.bottomMargin = DS.dp(activity, 12);
            wrap.addView(warn, wlp);
        }

        wrap.addView(input);

        new AlertDialog.Builder(activity)
                .setTitle("Renommer la charge")
                .setView(wrap)
                .setPositiveButton("Enregistrer", (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) return;
                    charge.name = newName;
                    saveAndRefresh(charge, "Libellé mis à jour ✓");
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void showEditAmountDialog(SettingsModels.FixedCharge charge) {
        String[] options = {"Montant fixe", "Fourchette (min / max)"};
        int current = charge.isVariable() ? 1 : 0;
        new AlertDialog.Builder(activity)
                .setTitle("Type de montant — " + charge.name)
                .setSingleChoiceItems(options, current, null)
                .setPositiveButton("Suivant", (d, w) -> {
                    int sel = ((AlertDialog) d).getListView().getCheckedItemPosition();
                    if (sel == 1) {
                        showRangeAmountDialog(charge);
                    } else {
                        SettingsDialog.showAmountDialog(
                                activity, "€",
                                "Modifier le montant",
                                "Nouveau montant de " + charge.name + ".",
                                charge.amount,
                                value -> {
                                    charge.amount    = value;
                                    charge.amountMin = 0;
                                    charge.amountMax = 0;
                                    saveAndRefresh(charge, "Montant mis à jour");
                                });
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void showRangeAmountDialog(SettingsModels.FixedCharge charge) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = DS.dp(activity, 20);
        layout.setPadding(pad, pad / 2, pad, 0);

        android.widget.EditText etMin = new android.widget.EditText(activity);
        etMin.setHint("Montant minimum (ex. 170)");
        etMin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (charge.amountMin > 0) etMin.setText(String.valueOf(charge.amountMin));
        layout.addView(etMin);

        android.widget.EditText etTypical = new android.widget.EditText(activity);
        etTypical.setHint("Montant typique (ex. 190)");
        etTypical.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etTypical.setText(String.valueOf(charge.amount > 0 ? charge.amount : ""));
        layout.addView(etTypical);

        android.widget.EditText etMax = new android.widget.EditText(activity);
        etMax.setHint("Montant maximum (ex. 220)");
        etMax.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (charge.amountMax > 0) etMax.setText(String.valueOf(charge.amountMax));
        layout.addView(etMax);

        TextView hint = new TextView(activity);
        hint.setText("Le maximum est utilisé pour la projection budgétaire (pire cas).");
        hint.setTextColor(ThemeColors.subtext());
        hint.setTextSize(DS.TEXT_XS);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(-1, -2);
        hLp.topMargin = DS.dp(activity, 8);
        layout.addView(hint, hLp);

        new AlertDialog.Builder(activity)
                .setTitle("Fourchette — " + charge.name)
                .setView(layout)
                .setPositiveButton("Enregistrer", (d, w) -> {
                    try {
                        double min = Double.parseDouble(etMin.getText().toString().replace(",", ".").trim());
                        double max = Double.parseDouble(etMax.getText().toString().replace(",", ".").trim());
                        String typicalStr = etTypical.getText().toString().replace(",", ".").trim();
                        double typical = typicalStr.isEmpty() ? (min + max) / 2.0
                                : Double.parseDouble(typicalStr);
                        if (min <= 0 || max <= 0 || max <= min) {
                            AppToast.error(activity, "Le maximum doit être supérieur au minimum.");
                            return;
                        }
                        charge.amountMin = min;
                        charge.amountMax = max;
                        charge.amount    = typical;
                        saveAndRefresh(charge, "Fourchette enregistrée");
                    } catch (NumberFormatException e) {
                        AppToast.error(activity, "Montant invalide.");
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void showEditPaidByDialog(SettingsModels.FixedCharge charge) {
        showPaidByPicker(
                "Modifier le payeur",
                "Qui paie réellement " + charge.name + " ?",
                safePaidBy(charge),
                paidBy -> {
                    charge.paidBy = safeName(paidBy, getDefaultPaidBy());
                    saveAndRefresh(charge, "Payeur mis à jour");
                }
        );
    }

    private void showEditDayDialog(SettingsModels.FixedCharge charge) {
        SettingsDialog.showDayDialog(activity, normalizeDayOfMonth(charge.dayOfMonth), day -> {
            charge.dayOfMonth = normalizeDayOfMonth(day);
            saveAndRefresh(charge, "Jour mis à jour");
        });
    }

    private interface PaidByCallback {
        void onSelected(String paidBy);
    }

    private void showPaidByPicker(String title, String subtitle, String current, PaidByCallback callback) {
        List<String> names = getMemberNames();

        if (names.isEmpty()) {
            names.add(getDefaultPaidBy());
        }

        String[] items = names.toArray(new String[0]);

        int selected = 0;
        for (int i = 0; i < items.length; i++) {
            if (items[i].equalsIgnoreCase(current)) {
                selected = i;
                break;
            }
        }

        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(
                DS.dp(activity, 20),
                DS.dp(activity, 12),
                DS.dp(activity, 20),
                DS.dp(activity, 6)
        );

        TextView tvSub = new TextView(activity);
        tvSub.setText(subtitle);
        tvSub.setTextColor(ThemeColors.subtext());
        tvSub.setTextSize(DS.TEXT_SM);
        tvSub.setLineSpacing(DS.dp(activity, 3), 1f);
        wrap.addView(tvSub);

        final String[] selectedValue = {items[selected]};

        RadioGroup group = new RadioGroup(activity);
        group.setOrientation(RadioGroup.VERTICAL);

        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, -2);
        gp.topMargin = DS.dp(activity, 14);
        wrap.addView(group, gp);

        for (int i = 0; i < items.length; i++) {
            RadioButton rb = new RadioButton(activity);
            rb.setText(items[i]);
            rb.setTextColor(ThemeColors.text());
            rb.setTextSize(DS.TEXT_BODY);
            rb.setTypeface(null, Typeface.BOLD);
            rb.setPadding(0, DS.dp(activity, 8), 0, DS.dp(activity, 8));
            rb.setId(2000 + i);

            try {
                rb.setButtonTintList(android.content.res.ColorStateList.valueOf(ThemeColors.primary()));
            } catch (Exception ignored) {}

            group.addView(rb, new RadioGroup.LayoutParams(-1, -2));

            if (i == selected) {
                rb.setChecked(true);
            }
        }

        group.setOnCheckedChangeListener((g, checkedId) -> {
            int idx = checkedId - 2000;
            if (idx >= 0 && idx < items.length) {
                selectedValue[0] = items[idx];
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(wrap)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Valider", (d, which) -> {
                    if (callback != null) {
                        callback.onSelected(selectedValue[0]);
                    }
                })
                .create();

        dialog.setOnShowListener(d -> {
            try {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ThemeColors.primary());
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ThemeColors.subtext());
            } catch (Exception ignored) {}
        });

        dialog.show();
    }

    private void confirmDelete(SettingsModels.FixedCharge charge) {
        SettingsDialog.showConfirmDialog(
                activity,
                "",
                "Supprimer cet abonnement ?",
                "\"" + charge.name + "\" sera retiré du foyer.",
                "Supprimer",
                () -> deleteCharge(charge)
        );
    }

    private void deleteCharge(SettingsModels.FixedCharge charge) {
        charges.remove(charge);

        SettingsModels.State state = SettingsCache.get();
        if (state.charges != null) {
            state.charges.remove(charge);
        }
        SettingsCache.set(state);

        refresh();

        SettingsChargeWriter.deleteCharge(charge, new SettingsChargeWriter.Callback() {
            public void onSuccess() {
                activity.runOnUiThread(() -> AppToast.success(activity, "Abonnement supprimé"));
            }

            public void onError(String e) {
                activity.runOnUiThread(() -> AppToast.error(activity, "Suppression impossible"));
            }
        });
    }

    private void saveAndRefresh(SettingsModels.FixedCharge charge, String msg) {
        SettingsCache.set(SettingsCache.get());
        refresh();

        SettingsChargeWriter.saveCharge(charge, new SettingsChargeWriter.Callback() {
            public void onSuccess() {
                activity.runOnUiThread(() -> {
                    RecurringChargeManager.getInstance().init(activity);
                    RecurringChargeManager.getInstance().checkAndApplyRecurringCharges(
                            () -> AppToast.success(activity, msg)
                    );
                });
            }

            public void onError(String e) {
                activity.runOnUiThread(() -> AppToast.error(activity, "Sauvegarde impossible"));
            }
        });
    }

    /** Exclut les charges dont la catégorie est "Crédits" (géré par l'onglet Crédits). */
    private List<SettingsModels.FixedCharge> filterOutCreditCharges(List<SettingsModels.FixedCharge> all) {
        List<SettingsModels.FixedCharge> result = new ArrayList<>();
        for (SettingsModels.FixedCharge c : all) {
            if (c == null) continue;
            String cat = c.category == null ? "" : c.category.trim().toLowerCase(java.util.Locale.FRENCH);
            if (cat.equals("crédits") || cat.equals("crédit") || cat.equals("credits") || cat.equals("credit")) continue;
            result.add(c);
        }
        return result;
    }

    private void loadData() {
        SettingsModels.State cached = SettingsCache.get();

        if (cached.charges != null && !cached.charges.isEmpty()) {
            charges = filterOutCreditCharges(new ArrayList<>(cached.charges));
            refresh();
        }

        new SettingsRepository(activity).load(new SettingsRepository.LoadCallback() {
            public void onLoaded(SettingsModels.State state) {
                activity.runOnUiThread(() -> {
                    List<SettingsModels.FixedCharge> raw = state.charges != null
                            ? new ArrayList<>(state.charges) : new ArrayList<>();
                    charges = filterOutCreditCharges(raw);
                    refresh();
                });
            }

            public void onError(String error) {}
        });
    }

    private void refresh() {
        if (content == null) return;

        content.removeAllViews();

        content.addView(buildPageHeader());
        content.addView(buildHeroCard());
        content.addView(buildCategoryPills());

        chargesContainer = new LinearLayout(activity);
        chargesContainer.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = DS.dp(activity, 8);
        chargesContainer.setLayoutParams(clp);

        content.addView(chargesContainer);

        refreshPills();
        renderCharges();
    }

    /** Montant typique effectif (lastActualAmount si confirmé, sinon amount configuré). */
    private double totalTypique() {
        double t = 0;
        for (SettingsModels.FixedCharge c : charges) {
            if (c != null) t += c.effectiveTypique();
        }
        return t;
    }

    /** Seuil bas : amountMin pour les charges variables, effectiveTypique pour les fixes. */
    private double totalMin() {
        double t = 0;
        for (SettingsModels.FixedCharge c : charges) {
            if (c == null) continue;
            t += c.isVariable() ? c.amountMin : c.effectiveTypique();
        }
        return t;
    }

    /** Seuil haut : amountMax pour les charges variables (pire cas), amount pour les fixes. */
    private double totalMax() {
        double t = 0;
        for (SettingsModels.FixedCharge c : charges) {
            if (c != null) t += c.amountForProjection();
        }
        return t;
    }

    private double totalCharges() {
        return totalTypique();
    }

    /** Vrai si au moins une charge variable est présente. */
    private boolean hasVariableCharges() {
        for (SettingsModels.FixedCharge c : charges) {
            if (c != null && c.isVariable()) return true;
        }
        return false;
    }

private int memberCount() {
	int count = 0;

	SettingsModels.State s = SettingsCache.get();

	if (s != null && s.members != null) {
		for (SettingsModels.Member m : s.members) {
			if (m != null && m.name != null && !m.name.trim().isEmpty() && !isJointName(m.name)) {
				count++;
			}
		}
	}

	return count > 0 ? count : 2;
}

    private int normalizeDayOfMonth(int day) {
        if (day < 1) return 1;
        if (day > 28) return 28;
        return day;
    }

    private List<String> getMemberNames() {
	ArrayList<String> result = new ArrayList<>();

	SettingsModels.State state = SettingsCache.get();

	if (state != null && state.members != null) {
		for (SettingsModels.Member member : state.members) {
			if (member == null || member.name == null || member.name.trim().isEmpty())
				continue;

			String name = member.name.trim();

			if (isJointName(name))
				continue;

			boolean exists = false;
			for (String existing : result) {
				if (existing.equalsIgnoreCase(name)) {
					exists = true;
					break;
				}
			}

			if (!exists) {
				result.add(name);
			}
		}
	}

	String fallback = getDefaultPaidBy();

	boolean containsFallback = false;
	for (String n : result) {
		if (n.equalsIgnoreCase(fallback)) {
			containsFallback = true;
			break;
		}
	}

	if (!containsFallback && fallback != null && !fallback.trim().isEmpty() && !isJointName(fallback)) {
		result.add(0, fallback);
	}

	try {
		JointAccountManager jm = JointAccountManager.getInstance();
		if (jm.isEnabledLocal()) {
			String jointName = jm.getNameLocal();
			if (jointName == null || jointName.trim().isEmpty()) {
				jointName = "Compte joint";
			}

			boolean hasJoint = false;
			for (String n : result) {
				if (isJointName(n) || n.equalsIgnoreCase(jointName)) {
					hasJoint = true;
					break;
				}
			}

			if (!hasJoint) {
				result.add(jointName.trim());
			}
		}
	} catch (Exception ignored) {
	}

	if (result.isEmpty()) {
		result.add(getDefaultPaidBy());
	}

	return result;
}

    private String getDefaultPaidBy() {
        String name = "";

        try {
            name = UserSession.getInstance().getNameOrFallback();
        } catch (Exception ignored) {}

        if (name == null || name.trim().isEmpty() || name.contains("@") || "Moi".equals(name.trim())) {
            try {
                name = AuthManager.getInstance().getDisplayName();
            } catch (Exception ignored) {}
        }

        if (name == null || name.trim().isEmpty() || name.contains("@") || "Moi".equals(name.trim())) {
            // Dernière tentative : premier membre du foyer depuis SettingsCache
            try {
                SettingsModels.State state = SettingsCache.get();
                if (state != null && state.members != null) {
                    for (SettingsModels.Member m : state.members) {
                        if (m != null && m.name != null && !m.name.trim().isEmpty()
                                && !"Moi".equals(m.name.trim())) {
                            return m.name.trim();
                        }
                    }
                }
            } catch (Exception ignored) {}
            return "Moi";
        }

        return name.trim();
    }

    private String safePaidBy(SettingsModels.FixedCharge charge) {
        if (charge == null) return getDefaultPaidBy();
        return safeName(charge.paidBy, getDefaultPaidBy());
    }

    private String safeName(String value, String fallback) {
        if (value == null || value.trim().isEmpty() || "Moi".equals(value.trim())) {
            if (fallback != null && !fallback.trim().isEmpty() && !"Moi".equals(fallback.trim())) {
                return fallback.trim();
            }
            return getDefaultPaidBy();
        }
        return value.trim();
    }

    private String guessCategory(String name) {
        if (name == null) return "Général";

        String n = name.toLowerCase(Locale.FRANCE);

        if (n.contains("loyer") || n.contains("edf") || n.contains("électricité")
                || n.contains("gaz") || n.contains("internet") || n.contains("assurance")
                || n.contains("eau") || n.contains("saur")) {
            return "Logement";
        }

        if (n.contains("netflix") || n.contains("spotify") || n.contains("disney")
                || n.contains("amazon") || n.contains("canal") || n.contains("youtube")
                || n.contains("deezer")) {
            return "Loisirs";
        }

        if (n.contains("mutuelle") || n.contains("santé")) {
            return "Santé";
        }

        if (n.contains("crédit") || n.contains("prêt")) {
            return "Crédit";
        }

        return "Général";
    }
    
    private boolean isJointName(String value) {
	if (value == null)
		return false;

	String n = value.trim().toLowerCase(Locale.FRANCE);

	return n.equals("compte joint")
			|| n.equals("joint")
			|| n.equals("compte commun");
}

    private String guessIcon(String name) {
        if (name == null) return "💳";

        String n = name.toLowerCase(Locale.FRANCE);

        if (n.contains("loyer")) return "🏠";
        if (n.contains("edf") || n.contains("électricité") || n.contains("gaz")) return "⚡";
        if (n.contains("eau")) return "💧";
        if (n.contains("internet") || n.contains("box")) return "📶";
        if (n.contains("assurance")) return "🔒";
        if (n.contains("netflix") || n.contains("disney") || n.contains("canal")) return "🎬";
        if (n.contains("spotify") || n.contains("deezer")) return "🎵";
        if (n.contains("mutuelle")) return "🏥";
        if (n.contains("amazon")) return "📦";
        if (n.contains("crédit") || n.contains("prêt")) return "🏦";

        return "💳";
    }
}