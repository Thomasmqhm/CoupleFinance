package com.couplefinance.ui.settings;

import android.app.Activity;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.AppToast;
import com.couplefinance.core.theme.ThemeColors;

import java.util.ArrayList;
import java.util.Locale;

/**
 * SettingsCategoriesSection — gestion réelle des catégories.
 *
 * Fonctionnalités :
 * - recherche
 * - création
 * - édition nom / emoji / budget / type / actif
 * - suppression
 * - sauvegarde Firestore via SettingsCategoryWriter
 */
public class SettingsCategoriesSection {

    private final Activity activity;
    private LinearLayout root;
    private String searchQuery = "";

    public SettingsCategoriesSection(Activity activity) {
        this.activity = activity;
    }

    public View build() {
        SettingsStyles.syncWithGlobalTheme();

        root = new LinearLayout(activity);
        root.setOrientation(
                SettingsResponsive.useTwoColumns(activity)
                        ? LinearLayout.HORIZONTAL
                        : LinearLayout.VERTICAL
        );

        buildContent();

        return root;
    }

    private void buildContent() {
        if (root == null) return;

        SettingsModels.State state = SettingsCache.get();
        if (state == null) state = new SettingsModels.State();
        if (state.categories == null) state.categories = new ArrayList<>();

        root.removeAllViews();

        LinearLayout left = new LinearLayout(activity);
        left.setOrientation(LinearLayout.VERTICAL);

        root.addView(left, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        left.addView(buildSearch());
        left.addView(cardsRow(state));

        LinearLayout right = adviceColumn(state);

        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                SettingsResponsive.sideWidth(activity),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        if (SettingsResponsive.useTwoColumns(activity)) {
            rightParams.leftMargin = SettingsStyles.dp(activity, 24);
        } else {
            rightParams.topMargin = SettingsStyles.dp(activity, 24);
        }

        root.addView(right, rightParams);
    }

    private View buildSearch() {
        EditText search = new EditText(activity);
        search.setHint("🔍  Rechercher une catégorie...");
        search.setText(searchQuery);
        search.setTextSize(16);
        search.setSingleLine(true);
        search.setTextColor(SettingsStyles.text());
        search.setHintTextColor(SettingsStyles.subtext());
        search.setPadding(
                SettingsStyles.dp(activity, 22),
                0,
                SettingsStyles.dp(activity, 22),
                0
        );
        search.setBackground(SettingsStyles.card());

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s == null ? "" : s.toString();
                buildContent();
            }

            public void afterTextChanged(Editable s) {
            }
        });

        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                SettingsStyles.dp(activity, 62)
        );
        searchParams.bottomMargin = SettingsStyles.dp(activity, 22);
        search.setLayoutParams(searchParams);

        return search;
    }

    private LinearLayout cardsRow(SettingsModels.State state) {
        LinearLayout cardsRow = new LinearLayout(activity);
        cardsRow.setOrientation(
                SettingsResponsive.useTwoColumns(activity)
                        ? LinearLayout.HORIZONTAL
                        : LinearLayout.VERTICAL
        );

        View incomeBlock = categoryBlock(
                "↗️  REVENUS",
                filter(state.categories, "income"),
                true
        );

        cardsRow.addView(incomeBlock, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        View expenseBlock = categoryBlock(
                "🏷️  DÉPENSES",
                filter(state.categories, "expense"),
                false
        );

        LinearLayout.LayoutParams expenseParams;

        if (SettingsResponsive.useTwoColumns(activity)) {
            expenseParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );
            expenseParams.leftMargin = SettingsStyles.dp(activity, 20);
        } else {
            expenseParams = SettingsStyles.matchWrap();
            expenseParams.topMargin = SettingsStyles.dp(activity, 20);
        }

        cardsRow.addView(expenseBlock, expenseParams);

        return cardsRow;
    }

    private View categoryBlock(String title, ArrayList<SettingsModels.Category> items, boolean income) {
        LinearLayout card = SettingsCards.sectionCard(activity);

        ArrayList<SettingsModels.Category> visibleItems = applySearch(items);

        int activeCount = 0;
        double totalBudget = 0;

        for (SettingsModels.Category c : items) {
            if (c == null) continue;
            if (c.active) activeCount++;
            if (c.budget > 0) totalBudget += c.budget;
        }

        String right = activeCount + " / " + items.size();
        if (totalBudget > 0) {
            right += " · " + formatMoney(totalBudget);
        }

        card.addView(SettingsCards.titleRow(activity, title, right));

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams listParams = SettingsStyles.matchWrap();
        listParams.topMargin = SettingsStyles.dp(activity, 18);
        card.addView(list, listParams);

        if (visibleItems.isEmpty()) {
            TextView empty = infoText("Aucune catégorie à afficher.");
            list.addView(empty);
        } else {
            for (SettingsModels.Category category : visibleItems) {
                list.addView(categoryRow(category, income));
            }
        }

        TextView add = premiumAction(
                income ? "+ Nouvelle catégorie revenu" : "+ Nouvelle catégorie dépense",
                income
        );
        add.setOnClickListener(v -> showAddCategoryDialog(income ? "income" : "expense"));

        LinearLayout.LayoutParams addParams = SettingsStyles.matchWrap();
        addParams.topMargin = SettingsStyles.dp(activity, 14);
        card.addView(add, addParams);

        TextView info = infoText("Appuyez sur une catégorie pour la modifier. Le budget est utilisé par Budget, Home et les alertes.");
        LinearLayout.LayoutParams infoParams = SettingsStyles.matchWrap();
        infoParams.topMargin = SettingsStyles.dp(activity, 14);
        card.addView(info, infoParams);

        return card;
    }

    private View categoryRow(SettingsModels.Category category, boolean income) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(
                SettingsStyles.dp(activity, 14),
                SettingsStyles.dp(activity, 12),
                SettingsStyles.dp(activity, 14),
                SettingsStyles.dp(activity, 12)
        );
        row.setBackground(category.active ? SettingsStyles.secondaryButton() : SettingsStyles.card());
        row.setClickable(true);
        row.setOnClickListener(v -> showEditCategoryDialog(category));

        LinearLayout.LayoutParams rowParams = SettingsStyles.matchWrap();
        rowParams.bottomMargin = SettingsStyles.dp(activity, 10);
        row.setLayoutParams(rowParams);

        TextView emoji = new TextView(activity);
        emoji.setText(category.displayEmoji());
        emoji.setTextSize(20f);
        emoji.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams emojiParams = new LinearLayout.LayoutParams(
                SettingsStyles.dp(activity, 38),
                SettingsStyles.dp(activity, 38)
        );
        emojiParams.rightMargin = SettingsStyles.dp(activity, 12);
        row.addView(emoji, emojiParams);

        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(activity);
        name.setText(category.name == null ? "Catégorie" : category.name);
        name.setTextColor(category.active ? SettingsStyles.text() : SettingsStyles.subtext());
        name.setTextSize(15f);
        name.setTypeface(null, Typeface.BOLD);
        textCol.addView(name);

        TextView sub = new TextView(activity);
        String type = income ? "Revenu" : "Dépense";
        String budget = category.budget > 0 ? " · Budget " + formatMoney(category.budget) : " · Aucun budget";
        sub.setText(type + budget + (category.active ? "" : " · masquée"));
        sub.setTextColor(SettingsStyles.subtext());
        sub.setTextSize(12f);
        LinearLayout.LayoutParams subParams = SettingsStyles.matchWrap();
        subParams.topMargin = SettingsStyles.dp(activity, 2);
        textCol.addView(sub, subParams);

        row.addView(textCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView chevron = new TextView(activity);
        chevron.setText("›");
        chevron.setTextColor(SettingsStyles.subtext());
        chevron.setTextSize(22f);
        row.addView(chevron);

        return row;
    }

    private TextView premiumAction(String text, boolean income) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(14f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(income ? ThemeColors.success() : SettingsStyles.primary());
        tv.setPadding(
                SettingsStyles.dp(activity, 14),
                SettingsStyles.dp(activity, 13),
                SettingsStyles.dp(activity, 14),
                SettingsStyles.dp(activity, 13)
        );
        tv.setBackground(SettingsStyles.secondaryButton());
        return tv;
    }

    private TextView infoText(String text) {
        TextView info = new TextView(activity);
        info.setText(text);
        info.setTextColor(SettingsStyles.subtext());
        info.setTextSize(13f);
        info.setLineSpacing(2f, 1.05f);
        info.setPadding(
                SettingsStyles.dp(activity, 16),
                SettingsStyles.dp(activity, 13),
                SettingsStyles.dp(activity, 16),
                SettingsStyles.dp(activity, 13)
        );
        info.setBackground(SettingsStyles.secondaryButton());
        return info;
    }

    private void showAddCategoryDialog(String type) {
        SettingsDialogs.showCategoryEditor(activity, type, null, category -> addCategory(category), null);
    }

    private void showEditCategoryDialog(SettingsModels.Category category) {
        SettingsDialogs.showCategoryEditor(activity, category.type, category, updated -> {
            category.name = updated.name;
            category.type = updated.type;
            category.emoji = updated.emoji;
            category.color = updated.color;
            category.budget = updated.budget;
            category.active = updated.active;

            SettingsCache.set(SettingsCache.get());
            buildContent();

            SettingsCategoryWriter.saveCategory(category, new SettingsCategoryWriter.Callback() {
                public void onSuccess() {
                    activity.runOnUiThread(() ->
                            AppToast.success(activity, "Catégorie mise à jour"));
                }

                public void onError(String error) {
                    activity.runOnUiThread(() ->
                            AppToast.error(activity, "Sauvegarde impossible"));
                }
            });
        }, () -> deleteCategory(category));
    }

    private static boolean isSystemCategory(String name) {
        if (name == null) return false;
        String n = name.trim().toLowerCase(java.util.Locale.FRENCH);
        return n.equals("virements") || n.equals("virement")
                || n.equals("crédits") || n.equals("crédit")
                || n.equals("credits") || n.equals("credit");
    }

    private void addCategory(SettingsModels.Category category) {
        if (category != null && category.name != null && isSystemCategory(category.name)) {
            AppToast.error(activity, "\"" + category.name + "\" est une catégorie système réservée.");
            return;
        }
        SettingsModels.State state = SettingsCache.get();
        if (state == null) state = new SettingsModels.State();
        if (state.categories == null) state.categories = new ArrayList<>();

        for (SettingsModels.Category c : state.categories) {
            if (c.name != null
                    && category.name != null
                    && c.name.trim().equalsIgnoreCase(category.name.trim())
                    && category.type != null
                    && category.type.equals(c.type)) {
                AppToast.error(activity, "Catégorie déjà existante");
                return;
            }
        }

        state.categories.add(category);
        SettingsCache.set(state);
        buildContent();

        SettingsCategoryWriter.saveCategory(category, new SettingsCategoryWriter.Callback() {
            public void onSuccess() {
                activity.runOnUiThread(() ->
                        AppToast.success(activity, "Catégorie ajoutée"));
            }

            public void onError(String error) {
                activity.runOnUiThread(() ->
                        AppToast.error(activity, "Sauvegarde impossible"));
            }
        });
    }

    private void deleteCategory(SettingsModels.Category category) {
        if (category != null && category.name != null && isSystemCategory(category.name)) {
            AppToast.error(activity, "\"" + category.name + "\" est une catégorie système et ne peut pas être supprimée.");
            return;
        }
        SettingsModels.State state = SettingsCache.get();
        if (state == null || state.categories == null) return;

        state.categories.remove(category);
        SettingsCache.set(state);
        buildContent();

        SettingsCategoryWriter.deleteCategory(category, new SettingsCategoryWriter.Callback() {
            public void onSuccess() {
                activity.runOnUiThread(() ->
                        AppToast.success(activity, "Catégorie supprimée"));
            }

            public void onError(String error) {
                activity.runOnUiThread(() -> {
                    state.categories.add(category);
                    SettingsCache.set(state);
                    buildContent();
                    AppToast.error(activity, "Suppression impossible");
                });
            }
        });
    }

    private ArrayList<SettingsModels.Category> applySearch(ArrayList<SettingsModels.Category> items) {
        ArrayList<SettingsModels.Category> result = new ArrayList<>();

        if (items == null) return result;

        String q = searchQuery == null
                ? ""
                : searchQuery.trim().toLowerCase(Locale.FRANCE);

        for (SettingsModels.Category c : items) {
            if (c == null || c.name == null) continue;

            String haystack = (c.name + " " + c.displayEmoji()).toLowerCase(Locale.FRANCE);

            if (q.isEmpty() || haystack.contains(q)) result.add(c);
        }

        return result;
    }

    private ArrayList<SettingsModels.Category> filter(ArrayList<SettingsModels.Category> all, String type) {
        ArrayList<SettingsModels.Category> result = new ArrayList<>();

        if (all == null) return result;

        for (SettingsModels.Category c : all) {
            if (c != null && type.equals(c.type)) result.add(c);
        }

        return result;
    }

    private LinearLayout adviceColumn(SettingsModels.State state) {
        LinearLayout col = new LinearLayout(activity);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText("Conseils");
        SettingsStyles.section(title);
        col.addView(title);

        int total = state == null || state.categories == null ? 0 : state.categories.size();
        int active = 0;
        double budgets = 0;

        if (state != null && state.categories != null) {
            for (SettingsModels.Category c : state.categories) {
                if (c == null) continue;
                if (c.active) active++;
                if (c.budget > 0) budgets += c.budget;
            }
        }

        col.addView(adviceCard("🏷️", "Catégories actives", active + " catégorie(s) active(s) sur " + total + "."));
        col.addView(adviceCard("📊", "Budgets mensuels", budgets > 0 ? "Total budgété : " + formatMoney(budgets) + "." : "Ajoutez un budget pour améliorer les alertes."));
        col.addView(adviceCard("🔁", "Impact automatique", "Les catégories alimentent Budget, Transactions, Dashboard et l'import PDF."));

        return col;
    }

    private View adviceCard(String icon, String title, String text) {
        LinearLayout card = SettingsCards.sectionCard(activity);

        LinearLayout.LayoutParams params = SettingsStyles.matchWrap();
        params.topMargin = SettingsStyles.dp(activity, 14);
        card.setLayoutParams(params);

        TextView t = new TextView(activity);
        t.setText(icon + "  " + title);
        SettingsStyles.cardTitle(t);
        t.setTextSize(17);
        card.addView(t);

        TextView body = new TextView(activity);
        body.setText(text);
        SettingsStyles.cardSubtitle(body);
        body.setTextSize(15);

        LinearLayout.LayoutParams bodyParams = SettingsStyles.matchWrap();
        bodyParams.topMargin = SettingsStyles.dp(activity, 10);
        card.addView(body, bodyParams);

        return card;
    }

    private String formatMoney(double amount) {
        return String.format(Locale.FRANCE, "%,.0f €", amount);
    }

    public void setVisible(boolean visible) {
        if (root != null) root.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
