package com.couplefinance.core.ui.components;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.animations.PressAnimations;
import com.couplefinance.core.ui.effects.GradientFactory;

public final class PremiumSelector {

    private PremiumSelector() {
    }

    // ─────────────────────────────
    // SELECTOR
    // ─────────────────────────────

    public static AutoCompleteTextView selector(Context ctx,
                                                String[] items,
                                                int[] selectedIndex) {

        final String[] safeItems = (items != null) ? items : new String[0];

        final int[] safeSelectedIndex;
        if (selectedIndex == null || selectedIndex.length == 0) {
            safeSelectedIndex = new int[]{0};
        } else {
            safeSelectedIndex = selectedIndex;
        }

        if (safeSelectedIndex[0] < 0 || safeSelectedIndex[0] >= safeItems.length) {
            safeSelectedIndex[0] = 0;
        }

        AutoCompleteTextView acv = new AutoCompleteTextView(ctx);

        // Adapter non-filtrant : le dropdown affiche toujours la liste complète
        // quel que soit le texte courant.  Nécessaire car setFocusable(false)
        // empêche l'édition, et le filtre par défaut masquerait tout.
        ArrayAdapter<String> adapter = new NonFilteringAdapter<>(
                ctx,
                android.R.layout.simple_spinner_dropdown_item,
                safeItems);

        acv.setAdapter(adapter);

        // Initialisation du texte
        if (safeItems.length > 0) {
            setTextSafe(acv, safeItems[safeSelectedIndex[0]]);
        }

        acv.setTextColor(ThemeColors.text());
        acv.setHintTextColor(ThemeColors.inputHint());
        acv.setTextSize(DS.TEXT_BODY);
        acv.setTypeface(null, Typeface.NORMAL);
        acv.setSingleLine(true);

        // Non focusable = pas de clavier, mais toujours cliquable
        acv.setFocusable(false);
        acv.setClickable(true);

        // threshold 0 : montre toujours la liste complète sans attendre de saisie
        acv.setThreshold(1);

        acv.setPadding(
                DS.dp(ctx, DS.PAD_INPUT), 0,
                DS.dp(ctx, DS.PAD_INPUT), 0);

        acv.setBackground(GradientFactory.input(ctx));

        acv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                DS.dp(ctx, DS.INPUT_HEIGHT)));

        // Ouvre le dropdown au clic
        acv.setOnClickListener(v -> {
            acv.showDropDown();
        });

        // Sélection d'un item : met à jour index ET texte de façon fiable
        acv.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < safeItems.length) {
                safeSelectedIndex[0] = position;
                setTextSafe(acv, safeItems[position]);
                acv.dismissDropDown();
                PressAnimations.clickPulse(acv);
            }
        });

        return acv;
    }

    /**
     * Applique le texte sur un AutoCompleteTextView de façon fiable,
     * même quand il est non focusable.
     * setText(text, false) désactive le filtre pour cet appel,
     * évitant que l'adapter masque les items restants.
     */
    private static void setTextSafe(AutoCompleteTextView acv, String text) {
        try {
            acv.setText(text, false);
        } catch (Exception e) {
            acv.setText(text);
        }
    }

    // ─────────────────────────────
    // FIELD
    // ─────────────────────────────

    public static LinearLayout field(Context ctx,
                                     String label,
                                     String[] items,
                                     int[] selectedIndex) {

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView tvLabel = new TextView(ctx);
        tvLabel.setText(label);
        tvLabel.setTextColor(ThemeColors.subtext());
        tvLabel.setTextSize(DS.TEXT_XS);
        tvLabel.setTypeface(null, Typeface.BOLD);
        tvLabel.setLetterSpacing(0.08f);

        LinearLayout.LayoutParams lpLabel = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpLabel.bottomMargin = DS.dp(ctx, 8);
        col.addView(tvLabel, lpLabel);

        col.addView(selector(ctx, items, selectedIndex));

        return col;
    }

    // ─────────────────────────────
    // PILLS
    // ─────────────────────────────

    public static LinearLayout pills(Context ctx) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    public static TextView pill(Context ctx, String text, boolean selected) {
        TextView chip = PremiumChip.selectable(ctx, text, selected);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                DS.dp(ctx, 38));
        lp.rightMargin = DS.dp(ctx, 8);
        chip.setLayoutParams(lp);

        return chip;
    }

    public static void setSelected(TextView tv, Context ctx, boolean selected) {
        PremiumChip.setSelected(tv, ctx, selected);
    }

    // ─────────────────────────────
    // SEGMENTED
    // ─────────────────────────────

    public static LinearLayout segmented(Context ctx) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(
                DS.dp(ctx, 4), DS.dp(ctx, 4),
                DS.dp(ctx, 4), DS.dp(ctx, 4));
        row.setBackground(GradientFactory.bordered(
                ctx,
                ThemeColors.backgroundSecondary(),
                ThemeColors.border(),
                DS.R_LG));
        return row;
    }

    public static TextView segment(Context ctx, String text, boolean selected) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(DS.TEXT_SM);
        tv.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(selected ? ThemeColors.chipActiveText() : ThemeColors.subtext());
        tv.setPadding(
                DS.dp(ctx, 16), DS.dp(ctx, 10),
                DS.dp(ctx, 16), DS.dp(ctx, 10));

        if (selected) {
            tv.setBackground(GradientFactory.solid(ctx, ThemeColors.primary(), DS.R_LG));
        }

        PressAnimations.applySoft(tv);
        return tv;
    }

    // ─────────────────────────────
    // HELPERS
    // ─────────────────────────────

    public static void attachSelection(TextView view, Runnable onClick) {
        if (view == null) return;
        view.setOnClickListener(v -> {
            PressAnimations.clickPulse(v);
            if (onClick != null) onClick.run();
        });
    }

    public static void attachSelection(View view, Runnable onClick) {
        if (view == null) return;
        view.setOnClickListener(v -> {
            PressAnimations.clickPulse(v);
            if (onClick != null) onClick.run();
        });
    }

    // ─────────────────────────────
    // NonFilteringAdapter (interne)
    //
    // Un ArrayAdapter classique filtre la liste selon le texte courant
    // de l'AutoCompleteTextView.  Comme notre champ n'est pas éditable,
    // le filtre masquerait tout si le texte ne correspond à aucun item
    // en cours d'appel.  Cet adapter renvoie toujours la liste complète.
    // ─────────────────────────────
    private static final class NonFilteringAdapter<T> extends ArrayAdapter<T>
            implements Filterable {

        private final T[] items;

        NonFilteringAdapter(Context ctx, int resource, T[] items) {
            super(ctx, resource, items);
            this.items = items;
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    results.values = items;
                    results.count  = items.length;
                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    // Rien à faire : l'adapter garde toujours les mêmes items.
                    notifyDataSetChanged();
                }
            };
        }
    }
}
