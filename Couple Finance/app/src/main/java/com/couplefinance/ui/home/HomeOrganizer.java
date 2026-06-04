package com.couplefinance.ui.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.UiFactory;
import com.couplefinance.AppToast;
import com.couplefinance.R;

import java.util.ArrayList;

/**
 * Dialogues "premium" du dashboard : sélecteur de widgets, organisateur,
 * réorganisation par sections, etc.
 *
 * Étape 8 du refactor : tout ce code (~430 lignes) vivait dans HomeView.
 * Comme il manipule uniquement les SharedPreferences du dashboard et appelle
 * HomeView en retour pour rafraîchir l'UI, il est extrait avec une petite
 * interface {@link Callbacks} qui expose les seules opérations dont les
 * dialogues ont besoin sur HomeView (animations, rechargement).
 *
 * Toute la production de drawables passe par DS / UiFactory pour rester
 * cohérent avec le reste du dashboard.
 */
public class HomeOrganizer {

    /**
     * Hooks que HomeView (ou son équivalent) doit fournir pour que les
     * dialogues puissent déclencher animations et rafraîchissements.
     * Toutes les méthodes sont appelées depuis le thread UI.
     */
    public interface Callbacks {
        /** Animation d'apparition après changement de visibilité de widgets. */
        void onApplyWidgetVisibilityAnimated();

        /** Animation d'apparition après réorganisation des sections. */
        void onApplyDashboardSectionOrderAnimated();

        /** Réorganisation immédiate sans animation (utilisé après un reset). */
        void onApplyDashboardSectionOrder();

        /** Doit relancer le pipeline de chargement complet (loadData()). */
        void onReloadData();

        /**
         * Indique si une clé donnée correspond à un widget actuellement
         * activé dans les préférences. Permet de pré-cocher les rangées
         * du sélecteur sans dupliquer la logique.
         */
        boolean isWidgetEnabled(String key);

        /**
         * Indique si une clé correspond à une "grande section" (par
         * opposition à un widget intelligent). Utilisé pour différencier
         * le sous-titre des rangées du picker.
         */
        boolean isMainSectionKey(String key);
    }

    private final Activity activity;
    private final SharedPreferences dashboardPrefs;
    private final Callbacks callbacks;

    // Clés de préférences (alignées sur HomeView)
    private final String prefOrderSections;
    private final String prefOrderDynamic;

    public HomeOrganizer(Activity activity,
                         SharedPreferences dashboardPrefs,
                         String prefOrderSections,
                         String prefOrderDynamic,
                         Callbacks callbacks) {
        this.activity = activity;
        this.dashboardPrefs = dashboardPrefs;
        this.prefOrderSections = prefOrderSections;
        this.prefOrderDynamic = prefOrderDynamic;
        this.callbacks = callbacks;
    }

    // ─────────────────────────────────────────────────────
    // Helpers prefs / order (anciennement dans HomeView)
    // ─────────────────────────────────────────────────────

    /**
     * Lit l'ordre stocké pour la clé donnée et le complète avec les éléments
     * manquants. Les éléments inconnus stockés en pref sont ignorés.
     */
    public ArrayList<String> getOrder(String prefKey, String[] defaults) {
        ArrayList<String> order = new ArrayList<>();
        String saved = dashboardPrefs == null ? null : dashboardPrefs.getString(prefKey, null);
        if (saved != null && !saved.trim().isEmpty()) {
            String[] parts = saved.split(",");
            for (String p : parts) {
                String k = p.trim();
                if (!k.isEmpty() && HomeWidgetRegistry.contains(defaults, k) && !order.contains(k))
                    order.add(k);
            }
        }
        for (String k : defaults) {
            if (!order.contains(k)) order.add(k);
        }
        return order;
    }

    public void saveOrder(String prefKey, ArrayList<String> order) {
        if (dashboardPrefs == null) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(order.get(i));
        }
        dashboardPrefs.edit().putString(prefKey, sb.toString()).apply();
    }

    // ─────────────────────────────────────────────────────
    // Sélecteur de widgets
    // ─────────────────────────────────────────────────────

    public void showWidgetPicker() {
        final String[] titles = HomeWidgetRegistry.getAllWidgetTitles();
        final String[] keys = HomeWidgetRegistry.getAllWidgetKeys();
        final boolean[] checked = new boolean[keys.length];
        for (int i = 0; i < keys.length; i++)
            checked[i] = callbacks.isWidgetEnabled(keys[i]);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout root = createPremiumDialogRoot(scroll);
        addPremiumDialogHeader(root, "💎", "Studio widgets",
                "Active, masque et organise ton dashboard comme une vraie app premium.");

        TextView info = new TextView(activity);
        info.setText("Mode premium : chaque bloc est activable, réorganisable et animé. Garde uniquement ce qui t’aide vraiment au quotidien.");
        info.setTextColor(DS.MUTED);
        info.setTextSize(12f);
        info.setLineSpacing(DS.dp(activity, 2), 1f);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoLp.setMargins(0, 0, 0, DS.dp(activity, 18));
        root.addView(info, infoLp);

        for (int i = 0; i < titles.length; i++) {
            final int index = i;
            root.addView(createWidgetToggleRow(titles[i], keys[i], checked, index));
        }

        LinearLayout buttons = createDialogButtonsRow();
        Button btnOrganize = makePremiumDialogButton("Organiser", false);
        Button btnCancel = makePremiumDialogButton("Annuler", false);
        Button btnApply = makePremiumDialogButton("Appliquer", true);
        buttons.addView(btnOrganize, new LinearLayout.LayoutParams(0, DS.dp(activity, 52), 1f));
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, DS.dp(activity, 52), 1f);
        cancelLp.setMargins(DS.dp(activity, 10), 0, DS.dp(activity, 10), 0);
        buttons.addView(btnCancel, cancelLp);
        buttons.addView(btnApply, new LinearLayout.LayoutParams(0, DS.dp(activity, 52), 1f));
        root.addView(buttons);

        AlertDialog dialog = new AlertDialog.Builder(activity).setView(scroll).create();
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnOrganize.setOnClickListener(v -> {
            dialog.dismiss();
            showDashboardOrganizer();
        });
        btnApply.setOnClickListener(v -> {
            SharedPreferences.Editor editor = dashboardPrefs.edit();
            for (int i = 0; i < keys.length; i++)
                editor.putBoolean(keys[i], checked[i]);
            editor.apply();
            callbacks.onApplyWidgetVisibilityAnimated();
            callbacks.onApplyDashboardSectionOrderAnimated();
            callbacks.onReloadData();
            dialog.dismiss();
            AppToast.success(activity, "Dashboard mis à jour");
        });
        dialog.show();
        setPremiumDialogWidth(dialog, 0.72f, 0.88f);
    }

    private View createWidgetToggleRow(String title, String key, boolean[] checked, int index) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(DS.dp(activity, 16), DS.dp(activity, 12),
                DS.dp(activity, 16), DS.dp(activity, 12));
        row.setBackground(widgetRowBackground(checked[index]));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 0, 0, DS.dp(activity, 10));
        row.setLayoutParams(rowLp);

        TextView check = new TextView(activity);
        check.setText(checked[index] ? "✓" : "");
        check.setTextSize(16f);
        check.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        check.setTextColor(Color.WHITE);
        check.setGravity(Gravity.CENTER);
        check.setBackground(circle(checked[index] ? DS.GREEN : DS.BORDER));
        LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(
                DS.dp(activity, 28), DS.dp(activity, 28));
        checkLp.setMargins(0, 0, DS.dp(activity, 14), 0);
        row.addView(check, checkLp);

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView tvTitle = new TextView(activity);
        tvTitle.setText(title);
        tvTitle.setTextColor(DS.DARK);
        tvTitle.setTextSize(14.5f);
        tvTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        texts.addView(tvTitle);
        TextView tvSub = new TextView(activity);
        tvSub.setText(callbacks.isMainSectionKey(key)
                ? "Grande section du dashboard"
                : "Widget intelligent personnalisable");
        tvSub.setTextColor(DS.MUTED);
        tvSub.setTextSize(11f);
        texts.addView(tvSub);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView badge = new TextView(activity);
        badge.setText(checked[index] ? "ACTIF" : "MASQUÉ");
        badge.setTextColor(checked[index] ? DS.GREEN : DS.MUTED);
        badge.setTextSize(10f);
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(DS.dp(activity, 10), DS.dp(activity, 5),
                DS.dp(activity, 10), DS.dp(activity, 5));
        badge.setBackground(pill(
                checked[index] ? withAlpha(DS.GREEN, 20) : DS.DIVIDER,
                checked[index] ? withAlpha(DS.GREEN, 65) : DS.BORDER));
        row.addView(badge);

        row.setOnClickListener(v -> {
            checked[index] = !checked[index];
            check.setText(checked[index] ? "✓" : "");
            check.setBackground(circle(checked[index] ? DS.GREEN : DS.BORDER));
            badge.setText(checked[index] ? "ACTIF" : "MASQUÉ");
            badge.setTextColor(checked[index] ? DS.GREEN : DS.MUTED);
            badge.setBackground(pill(
                    checked[index] ? withAlpha(DS.GREEN, 20) : DS.DIVIDER,
                    checked[index] ? withAlpha(DS.GREEN, 65) : DS.BORDER));
            row.setBackground(widgetRowBackground(checked[index]));
            pulse(row);
        });

        return row;
    }

    // ─────────────────────────────────────────────────────
    // Organisateur (menu général)
    // ─────────────────────────────────────────────────────

    public void showDashboardOrganizer() {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout root = createPremiumDialogRoot(scroll);
        addPremiumDialogHeader(root, "🧩", "Organiser le dashboard",
                "Choisis ce que tu veux déplacer. L'ordre est sauvegardé automatiquement.");

        root.addView(createActionRow("Dernières opérations + agenda en haut",
                "Place le calendrier juste sous l'accueil", "⤒", v -> moveAgendaToTop()));
        root.addView(createActionRow("Déplacer les grandes sections",
                "Progression, cartes, agenda, personnes, catégories", "☰",
                v -> showReorderDialog("Grandes sections", prefOrderSections,
                        HomeWidgetRegistry.getSectionKeys(),
                        HomeWidgetRegistry.getSectionTitles())));
        root.addView(createActionRow("Réordonner les widgets intelligents",
                "Résumé, santé, cashflow, projection...", "⋮⋮",
                v -> showReorderDialog("Widgets intelligents", prefOrderDynamic,
                        HomeWidgetRegistry.getDynamicKeys(),
                        HomeWidgetRegistry.getDynamicTitles())));
        root.addView(createActionRow("Réinitialiser l'ordre",
                "Revenir à l'organisation par défaut", "↺",
                v -> resetDashboardOrder()));

        LinearLayout buttons = createDialogButtonsRow();
        Button close = makePremiumDialogButton("Fermer", true);
        buttons.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, DS.dp(activity, 52)));
        root.addView(buttons);

        AlertDialog dialog = new AlertDialog.Builder(activity).setView(scroll).create();
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        setPremiumDialogWidth(dialog, 0.58f, 0.78f);
    }

    private void showReorderDialog(String title, String prefKey, String[] defaultKeys, String[] defaultTitles) {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout root = createPremiumDialogRoot(scroll);
        addPremiumDialogHeader(root, "↕", title,
                "Utilise les flèches pour placer les widgets où tu veux.");

        ArrayList<String> order = getOrder(prefKey, defaultKeys);
        for (int i = 0; i < order.size(); i++) {
            final String key = order.get(i);
            final int index = i;
            root.addView(createReorderRow(index + 1,
                    HomeWidgetRegistry.titleFor(key, defaultKeys, defaultTitles),
                    v -> moveOrderItem(prefKey, defaultKeys, key, -1, title, defaultTitles),
                    v -> moveOrderItem(prefKey, defaultKeys, key, 1, title, defaultTitles),
                    v -> moveOrderItemToEdge(prefKey, defaultKeys, key, true, title, defaultTitles)));
        }

        LinearLayout buttons = createDialogButtonsRow();
        Button close = makePremiumDialogButton("Terminé", true);
        buttons.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, DS.dp(activity, 52)));
        root.addView(buttons);

        AlertDialog dialog = new AlertDialog.Builder(activity).setView(scroll).create();
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        setPremiumDialogWidth(dialog, 0.66f, 0.86f);
    }

    private void moveOrderItem(String prefKey, String[] defaultKeys, String selectedKey,
                               int delta, String title, String[] defaultTitles) {
        ArrayList<String> order = getOrder(prefKey, defaultKeys);
        int index = order.indexOf(selectedKey);
        if (index < 0) return;
        int newIndex = Math.max(0, Math.min(order.size() - 1, index + delta));
        if (newIndex == index) return;
        order.remove(index);
        order.add(newIndex, selectedKey);
        saveOrder(prefKey, order);
        callbacks.onApplyDashboardSectionOrderAnimated();
        callbacks.onReloadData();
        AppToast.success(activity, "Ordre mis à jour");
    }

    private void moveOrderItemToEdge(String prefKey, String[] defaultKeys, String selectedKey,
                                     boolean top, String title, String[] defaultTitles) {
        ArrayList<String> order = getOrder(prefKey, defaultKeys);
        if (!order.remove(selectedKey)) return;
        if (top) order.add(0, selectedKey);
        else order.add(selectedKey);
        saveOrder(prefKey, order);
        callbacks.onApplyDashboardSectionOrderAnimated();
        callbacks.onReloadData();
        AppToast.success(activity, "Placée en haut");
    }

    public void moveAgendaToTop() {
        ArrayList<String> order = getOrder(prefOrderSections, HomeWidgetRegistry.getSectionKeys());
        order.remove(HomeWidgetRegistry.W_BOTTOM_LINE);
        order.add(0, HomeWidgetRegistry.W_BOTTOM_LINE);
        saveOrder(prefOrderSections, order);
        callbacks.onApplyDashboardSectionOrder();
        AppToast.success(activity, "Agenda déplacé en haut");
    }

    private void resetDashboardOrder() {
        if (dashboardPrefs != null) {
            dashboardPrefs.edit()
                    .remove(prefOrderSections)
                    .remove(prefOrderDynamic)
                    .apply();
        }
        callbacks.onApplyDashboardSectionOrder();
        callbacks.onReloadData();
        AppToast.success(activity, "Ordre réinitialisé");
    }

    // ─────────────────────────────────────────────────────
    // UI helpers (rangées + dialogues)
    // ─────────────────────────────────────────────────────

    private View createReorderRow(int number, String title, View.OnClickListener up,
                                  View.OnClickListener down, View.OnClickListener top) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(DS.dp(activity, 14), DS.dp(activity, 12),
                DS.dp(activity, 14), DS.dp(activity, 12));
        row.setBackground(widgetRowBackground(true));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 0, 0, DS.dp(activity, 10));
        row.setLayoutParams(rowLp);

        TextView num = new TextView(activity);
        num.setText(String.valueOf(number));
        num.setTextColor(Color.WHITE);
        num.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        num.setGravity(Gravity.CENTER);
        num.setBackground(circle(DS.TERRA));
        LinearLayout.LayoutParams numLp = new LinearLayout.LayoutParams(
                DS.dp(activity, 32), DS.dp(activity, 32));
        numLp.setMargins(0, 0, DS.dp(activity, 14), 0);
        row.addView(num, numLp);

        TextView label = new TextView(activity);
        label.setText(title);
        label.setTextColor(DS.DARK);
        label.setTextSize(14.5f);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(makeMiniIconButton("⤒", top));
        row.addView(makeMiniIconButton("↑", up));
        row.addView(makeMiniIconButton("↓", down));
        return row;
    }

    private TextView makeMiniIconButton(String text, View.OnClickListener listener) {
        TextView b = new TextView(activity);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(15f);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(DS.GREEN);
        b.setBackground(pill(
                withAlpha(DS.GREEN, 16),
                withAlpha(DS.GREEN, 60)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                DS.dp(activity, 38), DS.dp(activity, 34));
        lp.setMargins(DS.dp(activity, 8), 0, 0, 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(listener);
        return b;
    }

    private View createActionRow(String title, String subtitle, String iconText,
                                 View.OnClickListener click) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(DS.dp(activity, 16), DS.dp(activity, 14),
                DS.dp(activity, 16), DS.dp(activity, 14));
        row.setBackground(widgetRowBackground(true));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, DS.dp(activity, 12));
        row.setLayoutParams(lp);

        TextView icon = new TextView(activity);
        icon.setText(iconText);
        icon.setTextSize(18f);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(DS.GREEN);
        icon.setBackground(circle(withAlpha(DS.GREEN, 22)));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                DS.dp(activity, 42), DS.dp(activity, 42));
        iconLp.setMargins(0, 0, DS.dp(activity, 14), 0);
        row.addView(icon, iconLp);

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(activity);
        t.setText(title);
        t.setTextColor(DS.DARK);
        t.setTextSize(15f);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        texts.addView(t);
        TextView sub = new TextView(activity);
        sub.setText(subtitle);
        sub.setTextColor(DS.MUTED);
        sub.setTextSize(11.5f);
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = new TextView(activity);
        arrow.setText("›");
        arrow.setTextSize(28f);
        arrow.setTextColor(DS.MUTED);
        row.addView(arrow);
        row.setOnClickListener(click);
        return row;
    }

    private LinearLayout createPremiumDialogRoot(ScrollView scroll) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(DS.dp(activity, 28), DS.dp(activity, 28),
                DS.dp(activity, 28), DS.dp(activity, 24));
        root.setBackgroundResource(R.drawable.dialog_background);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private void addPremiumDialogHeader(LinearLayout root, String emoji, String title, String subtitle) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerLp.setMargins(0, 0, 0, DS.dp(activity, 22));
        header.setLayoutParams(headerLp);

        TextView icon = new TextView(activity);
        icon.setText(emoji);
        icon.setTextSize(22f);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(circle(withAlpha(DS.TERRA, 24)));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                DS.dp(activity, 52), DS.dp(activity, 52));
        iconLp.setMargins(0, 0, DS.dp(activity, 16), 0);
        header.addView(icon, iconLp);

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView tvTitle = new TextView(activity);
        tvTitle.setText(title);
        tvTitle.setTextColor(DS.DARK);
        tvTitle.setTextSize(20f);
        tvTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        texts.addView(tvTitle);
        TextView tvSub = new TextView(activity);
        tvSub.setText(subtitle);
        tvSub.setTextColor(DS.MUTED);
        tvSub.setTextSize(12.5f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.setMargins(0, DS.dp(activity, 4), 0, 0);
        texts.addView(tvSub, subLp);
        header.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(header);
    }

    private LinearLayout createDialogButtonsRow() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, DS.dp(activity, 12), 0, 0);
        row.setLayoutParams(lp);
        return row;
    }

    private Button makePremiumDialogButton(String text, boolean primary) {
        Button b = new Button(activity);
        b.setText(text);
        b.setTextSize(13.5f);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        if (primary) {
            b.setTextColor(Color.WHITE);
            b.setBackgroundResource(R.drawable.btn_dialog_save);
        } else {
            b.setTextColor(DS.DARK);
            b.setBackgroundResource(R.drawable.btn_dialog_cancel);
        }
        return b;
    }

    private void setPremiumDialogWidth(AlertDialog dialog, float widthFraction, float heightFraction) {
        if (dialog == null || dialog.getWindow() == null) return;
        android.view.WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.width = (int) (activity.getResources().getDisplayMetrics().widthPixels * widthFraction);
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        dialog.getWindow().setAttributes(params);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }


    private GradientDrawable pill(int color, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(DS.dp(activity, 999));
        if (strokeColor != Color.TRANSPARENT) {
            d.setStroke(DS.dp(activity, 1), strokeColor);
        }
        return d;
    }

    private GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    private GradientDrawable widgetRowBackground(boolean active) {
        return UiFactory.bgBordered(active ? DS.TERRA_LIGHT : DS.CARD, active ? DS.TERRA : DS.BORDER, DS.R_MD, activity);
    }

    private void pulse(View view) {
        if (view == null) return;
        view.animate()
                .scaleX(0.98f).scaleY(0.98f)
                .setDuration(70)
                .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
                .start();
    }
}
