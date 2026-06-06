package com.couplefinance.ui.settings;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.couplefinance.AppToast;
import com.couplefinance.AuthManager;
import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.theme.ThemeManager;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.dialogs.PremiumDialog;
import com.couplefinance.data.FirestoreManager;
import com.couplefinance.data.HouseholdManager;
import com.couplefinance.data.JointAccountManager;
import com.couplefinance.UserRepository;
import com.couplefinance.UserSession;

import java.io.File;
import java.util.Locale;

/**
 * SettingsDialogs
 *
 * Dialogs premium utilisés par le menu Paramètres.
 * Compatible avec les signatures déjà appelées par toutes les sections Settings.
 */
public final class SettingsDialogs {

    private SettingsDialogs() {
    }

    public interface CategoryCallback {
        void onCategory(SettingsModels.Category category);
    }

    public interface DeleteCallback {
        void onDelete();
    }

    // ─────────────────────────────────────────────────────────────
    // Compte
    // ─────────────────────────────────────────────────────────────

    public static void showProfile(Activity activity, Runnable onChanged) {
        if (activity == null) return;

        SettingsModels.State state = SettingsCache.get();
        LinearLayout content = vertical(activity);

        EditText input = input(activity, "Votre prénom");
        String currentName = "";

        try {
            if (state != null && state.members != null && !state.members.isEmpty()) {
                currentName = state.members.get(0).name;
            }
        } catch (Exception ignored) {
        }

        if (currentName == null || currentName.trim().isEmpty()) {
            try {
                currentName = AuthManager.getInstance().getDisplayName();
            } catch (Exception ignored) {
                currentName = "";
            }
        }

        input.setText(currentName == null ? "" : currentName);
        content.addView(input);

        TextView hint = bodyMuted(activity,
                "Ce nom est utilisé dans l'affichage local et les écrans du foyer.");
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = DS.dp(activity, 10);
        content.addView(hint, hp);

        PremiumDialog.builder(activity)
                .icon("👤")
                .title("Profil personnel")
                .subtitle("Modifiez votre prénom affiché.")
                .content(content)
                .primary("Enregistrer", () -> {
                    String value = input.getText().toString().trim();

                    if (value.isEmpty()) {
                        AppToast.error(activity, "Nom invalide");
                        return;
                    }

                    try {
                        AuthManager.getInstance().setDisplayName(value);
                    } catch (Exception ignored) {
                    }

                    SharedPreferences prefs = activity.getSharedPreferences(
                            "couplefinance_profile",
                            Activity.MODE_PRIVATE
                    );
                    prefs.edit().putString("display_name", value).apply();

                    // Mettre à jour UserSession en mémoire et Firestore
                    try {
                        com.couplefinance.models.UserProfile p = UserSession.getInstance().getUser();
                        if (p == null) {
                            // Créer un profil minimal si absent (ex : premier login Melissa)
                            String uid = AuthManager.getInstance().getUserId();
                            String email = AuthManager.getInstance().getEmail();
                            p = new com.couplefinance.models.UserProfile(uid, value, email, System.currentTimeMillis());
                        }
                        p.displayName = value;
                        UserSession.getInstance().setUser(p);
                        UserRepository.getInstance().saveUser(p);
                    } catch (Exception ignored) {}

                    // Mettre à jour le nom du membre dans le foyer
                    try {
                        SettingsModels.State st = SettingsCache.get();
                        if (st != null && st.members != null && !st.members.isEmpty()) {
                            String myId = AuthManager.getInstance().getUserId();
                            for (SettingsModels.Member m : st.members) {
                                // Le premier membre est soi-même, ou celui dont le nom correspond
                                if (m.docPath != null && myId != null && m.docPath.contains(myId)) {
                                    m.name = value;
                                    SettingsMemberWriter.saveMember(m, new SettingsMemberWriter.Callback() {
                                        public void onSuccess() {}
                                        public void onError(String e) {}
                                    });
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {}

                    AppToast.success(activity, "Profil mis à jour");

                    if (onChanged != null) onChanged.run();
                })
                .secondary("Annuler", null)
                .show();
    }

    public static void showSecurity(Activity activity, Runnable onChanged) {
        if (activity == null) return;

        SharedPreferences prefs = activity.getSharedPreferences(
                "couplefinance_security",
                Activity.MODE_PRIVATE
        );

        LinearLayout content = vertical(activity);

        content.addView(switchRow(
                activity,
                "Déverrouillage biométrique",
                prefs.getBoolean("biometric_enabled", false),
                (buttonView, checked) -> prefs.edit()
                        .putBoolean("biometric_enabled", checked)
                        .apply()
        ));

        content.addView(switchRow(
                activity,
                "Masquer les montants au lancement",
                prefs.getBoolean("privacy_hide_amounts", false),
                (buttonView, checked) -> prefs.edit()
                        .putBoolean("privacy_hide_amounts", checked)
                        .apply()
        ));

        TextView note = bodyMuted(activity,
                "Le changement de mot de passe Firebase sera branché séparément pour éviter de casser l'authentification actuelle.");
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = DS.dp(activity, 12);
        content.addView(note, np);

        PremiumDialog.builder(activity)
                .icon("🔐")
                .title("Sécurité")
                .subtitle("Options locales de sécurité.")
                .content(content)
                .primary("Terminé", () -> {
                    AppToast.success(activity, "Sécurité mise à jour");
                    if (onChanged != null) onChanged.run();
                })
                .noSecondary()
                .show();
    }

    public static void showNotifications(Activity activity, Runnable onChanged) {
        if (activity == null) return;

        SharedPreferences prefs = activity.getSharedPreferences(
                "couplefinance_notifications",
                Activity.MODE_PRIVATE
        );

        LinearLayout content = vertical(activity);

        content.addView(switchRow(activity, "Alertes budget", prefs.getBoolean("budget_alerts", true),
                (buttonView, checked) -> prefs.edit().putBoolean("budget_alerts", checked).apply()));
        content.addView(switchRow(activity, "Dépenses élevées", prefs.getBoolean("large_expense_alerts", true),
                (buttonView, checked) -> prefs.edit().putBoolean("large_expense_alerts", checked).apply()));
        content.addView(switchRow(activity, "Charges fixes", prefs.getBoolean("fixed_charge_alerts", true),
                (buttonView, checked) -> prefs.edit().putBoolean("fixed_charge_alerts", checked).apply()));
        content.addView(switchRow(activity, "Objectifs épargne", prefs.getBoolean("savings_alerts", true),
                (buttonView, checked) -> prefs.edit().putBoolean("savings_alerts", checked).apply()));
        content.addView(switchRow(activity, "Résumé mensuel", prefs.getBoolean("monthly_summary", true),
                (buttonView, checked) -> prefs.edit().putBoolean("monthly_summary", checked).apply()));
        content.addView(switchRow(activity, "Synchronisation", prefs.getBoolean("sync_alerts", true),
                (buttonView, checked) -> prefs.edit().putBoolean("sync_alerts", checked).apply()));

        PremiumDialog.builder(activity)
                .icon("🔔")
                .title("Notifications")
                .subtitle("Choisissez les alertes à afficher dans CoupleFinance.")
                .content(content)
                .primary("Enregistrer", () -> {
                    AppToast.success(activity, "Notifications mises à jour");
                    if (onChanged != null) onChanged.run();
                })
                .noSecondary()
                .show();
    }

    // ─────────────────────────────────────────────────────────────
    // Foyer
    // ─────────────────────────────────────────────────────────────

    public static void showMembers(Activity activity) {
        showMembers(activity, null);
    }

    public static void showMembers(Activity activity, Runnable onChanged) {
        if (activity == null) return;

        new SettingsRepository(activity).load(new SettingsRepository.LoadCallback() {
            public void onLoaded(SettingsModels.State state) {
                activity.runOnUiThread(() -> showMembersLoaded(activity, state, onChanged));
            }

            public void onError(String error) {
                activity.runOnUiThread(() -> {
                    SettingsModels.State state = SettingsCache.get();
                    showMembersLoaded(activity, state, onChanged);
                });
            }
        });
    }

    private static void showMembersLoaded(Activity activity, SettingsModels.State state, Runnable onChanged) {
        if (activity == null) return;

        LinearLayout content = vertical(activity);

        TextView intro = bodyMuted(activity,
                "Chaque personne doit créer son propre compte avec son adresse mail. Ici, vous pouvez gérer uniquement l'affichage des membres déjà présents : couleur d'avatar et découvert.");
        content.addView(intro);

        boolean hasMember = false;

        try {
            if (state != null && state.members != null) {
                for (SettingsModels.Member member : state.members) {
                    if (member == null || member.name == null || member.name.trim().isEmpty()) continue;
                    content.addView(memberCard(activity, member, onChanged));
                    hasMember = true;
                }
            }
        } catch (Exception ignored) {
        }

        if (!hasMember) {
            LinearLayout empty = new LinearLayout(activity);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setPadding(DS.dp(activity, 18), DS.dp(activity, 18), DS.dp(activity, 18), DS.dp(activity, 18));
            empty.setBackground(dialogCardBg(activity));

            TextView title = body(activity, "Aucun membre chargé");
            title.setTypeface(null, Typeface.BOLD);
            empty.addView(title);

            TextView sub = bodyMuted(activity,
                    "Aucun membre n'a été trouvé dans Firestore pour ce foyer. Vérifiez que votre compte est bien enregistré dans households/{householdId}/persons.");
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
            sp.topMargin = DS.dp(activity, 6);
            empty.addView(sub, sp);

            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, -2);
            ep.topMargin = DS.dp(activity, 14);
            content.addView(empty, ep);
        }

        String code = "";
        try {
            code = HouseholdManager.getInstance().getHouseholdId();
        } catch (Exception ignored) {
            code = "";
        }

        final String inviteCode = code;

        PremiumDialog.builder(activity)
                .icon("🏠")
                .title("Membres du foyer")
                .subtitle(inviteCode != null && !inviteCode.isEmpty()
                        ? "Code d'invitation : " + inviteCode
                        : "Code d'invitation indisponible")
                .content(content)
                .primary("Copier le code", () -> {
                    if (inviteCode == null || inviteCode.isEmpty()) {
                        AppToast.error(activity, "Code indisponible");
                        return;
                    }

                    android.content.ClipboardManager cm =
                            (android.content.ClipboardManager)
                                    activity.getSystemService(Activity.CLIPBOARD_SERVICE);

                    if (cm != null) {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText(
                                "Code invitation",
                                inviteCode
                        ));
                        AppToast.success(activity, "Code copié");
                    }
                })
                .secondary("Fermer", null)
                .show();
    }

    private static View memberCard(Activity activity,
                                   SettingsModels.Member member,
                                   Runnable onChanged) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(DS.dp(activity, 16), DS.dp(activity, 16), DS.dp(activity, 16), DS.dp(activity, 16));
        card.setBackground(dialogCardBg(activity));

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = DS.dp(activity, 14);
        card.setLayoutParams(cp);

        // ── En-tête : avatar + nom + rôle ──
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView avatar = new TextView(activity);
        avatar.setText(member.initial());
        avatar.setGravity(Gravity.CENTER);
        avatar.setTextColor(Color.WHITE);
        avatar.setTextSize(17f);
        avatar.setTypeface(null, Typeface.BOLD);
        avatar.setBackground(circleBg(parseColor(member.color, ThemeColors.primary())));

        LinearLayout.LayoutParams avp = new LinearLayout.LayoutParams(DS.dp(activity, 48), DS.dp(activity, 48));
        avp.rightMargin = DS.dp(activity, 12);
        header.addView(avatar, avp);

        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView name = body(activity, member.name);
        name.setTypeface(null, Typeface.BOLD);
        name.setTextSize(17f);
        textCol.addView(name);

        TextView role = bodyMuted(activity,
                member.role != null && !member.role.trim().isEmpty()
                        ? member.role
                        : (member.admin ? "Administrateur" : "Membre"));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = DS.dp(activity, 2);
        textCol.addView(role, rp);

        header.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(header);

        // ── Solde de début du mois (par membre, depuis member.monthlyStartBalance) ──
        TextView monthLabel = bodyMuted(activity, "Solde de début du mois");
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
        mlp.topMargin = DS.dp(activity, 14);
        card.addView(monthLabel, mlp);

        EditText monthStartBalance = input(activity, "Montant saisi au lancement");
        monthStartBalance.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        monthStartBalance.setText(String.format(
                Locale.FRANCE,
                "%.0f",
                member.monthlyStartBalance
        ));

        LinearLayout.LayoutParams mbp = new LinearLayout.LayoutParams(-1, -2);
        mbp.topMargin = DS.dp(activity, 8);
        card.addView(monthStartBalance, mbp);

        // ── Découvert ──
        Switch overdraftEnabled = switchRow(activity,
                "Activer le découvert",
                member.overdraft > 0,
                null);
        LinearLayout.LayoutParams oep = new LinearLayout.LayoutParams(-1, -2);
        oep.topMargin = DS.dp(activity, 12);
        card.addView(overdraftEnabled, oep);

        EditText overdraft = input(activity, "Montant du découvert autorisé");
        overdraft.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        overdraft.setText(String.format(Locale.FRANCE, "%.0f", Math.max(0, member.overdraft)));

        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-1, -2);
        op.topMargin = DS.dp(activity, 8);
        card.addView(overdraft, op);

        Switch alert = switchRow(activity,
                "Alerte si le découvert est dépassé",
                member.overdraftAlert,
                null);
        card.addView(alert);

        overdraftEnabled.setOnCheckedChangeListener((buttonView, checked) -> {
            overdraft.setEnabled(checked);
            alert.setEnabled(checked);
            overdraft.setAlpha(checked ? 1f : 0.45f);
            alert.setAlpha(checked ? 1f : 0.45f);
        });
        overdraft.setEnabled(overdraftEnabled.isChecked());
        alert.setEnabled(overdraftEnabled.isChecked());
        overdraft.setAlpha(overdraftEnabled.isChecked() ? 1f : 0.45f);
        alert.setAlpha(overdraftEnabled.isChecked() ? 1f : 0.45f);

        // ── Couleur avatar ──
        TextView colorTitle = bodyMuted(activity, "Couleur de l'avatar");
        LinearLayout.LayoutParams ctp = new LinearLayout.LayoutParams(-1, -2);
        ctp.topMargin = DS.dp(activity, 10);
        card.addView(colorTitle, ctp);

        LinearLayout palette = new LinearLayout(activity);
        palette.setOrientation(LinearLayout.HORIZONTAL);
        palette.setGravity(Gravity.CENTER_VERTICAL);

        String[] colors = {
                "#C86B4A", "#6FA17D", "#5D8FA3", "#8065B3",
                "#B96B8C", "#4C9A8A", "#B9834F", "#8C7D76"
        };

        final String[] selectedColor = {
                member.color != null && !member.color.trim().isEmpty()
                        ? member.color
                        : "#C86B4A"
        };

        for (String c : colors) {
            TextView dot = colorCircle(activity, c, c.equalsIgnoreCase(selectedColor[0]));
            dot.setTag(c);
            dot.setOnClickListener(v -> {
                Object tag = v.getTag();
                if (!(tag instanceof String)) return;
                selectedColor[0] = (String) tag;
                avatar.setBackground(circleBg(parseColor(selectedColor[0], ThemeColors.primary())));
                for (int i = 0; i < palette.getChildCount(); i++) {
                    View child = palette.getChildAt(i);
                    Object childTag = child.getTag();
                    if (child instanceof TextView) {
                        ((TextView) child).setText(selectedColor[0].equalsIgnoreCase(String.valueOf(childTag)) ? "✓" : "");
                    }
                    child.setAlpha(selectedColor[0].equalsIgnoreCase(String.valueOf(childTag)) ? 1f : 0.55f);
                }
            });

            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(DS.dp(activity, 34), DS.dp(activity, 34));
            dp.rightMargin = DS.dp(activity, 9);
            palette.addView(dot, dp);
        }

        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
        pp.topMargin = DS.dp(activity, 8);
        card.addView(palette, pp);

        // ── Bouton Enregistrer ──
        TextView save = new TextView(activity);
        save.setText("Enregistrer ce membre");
        save.setTextColor(Color.WHITE);
        save.setTextSize(14f);
        save.setTypeface(null, Typeface.BOLD);
        save.setGravity(Gravity.CENTER);
        save.setPadding(DS.dp(activity, 14), DS.dp(activity, 10), DS.dp(activity, 14), DS.dp(activity, 10));
        save.setBackground(pillBg(ThemeColors.primary()));
        save.setOnClickListener(v -> {
            // Lecture du solde de début du mois depuis le champ
            double startBalance = 0;
            try {
                String raw = monthStartBalance.getText().toString().trim().replace(',', '.');
                if (!raw.isEmpty()) {
                    startBalance = Double.parseDouble(raw);
                }
            } catch (Exception ignored) {
                startBalance = 0;
            }

            // Lecture du découvert
            double overdraftAmount = 0;
            if (overdraftEnabled.isChecked()) {
                try {
                    overdraftAmount = Math.abs(Double.parseDouble(
                            overdraft.getText().toString().trim().replace(',', '.')));
                } catch (Exception ignored) {
                    overdraftAmount = 0;
                }
            }

            // Mise à jour du modèle member
            member.overdraft = overdraftAmount;
            member.overdraftAlert = overdraftEnabled.isChecked() && alert.isChecked();
            member.color = selectedColor[0];
            member.monthlyStartBalance = startBalance;

            // Si pas de docPath : mise à jour locale uniquement
            if (member.docPath == null || member.docPath.trim().isEmpty()) {
                AppToast.success(activity, "Membre mis à jour localement");
                if (onChanged != null) onChanged.run();
                return;
            }

            // Sauvegarde Firestore via SettingsMemberWriter
            SettingsMemberWriter.saveMember(member, new SettingsMemberWriter.Callback() {
                public void onSuccess() {
                    activity.runOnUiThread(() -> {
                        AppToast.success(activity, "Membre mis à jour");
                        if (onChanged != null) onChanged.run();
                    });
                }

                public void onError(String error) {
                    // Affichage de l'erreur réelle au lieu d'un message générique
                    activity.runOnUiThread(() ->
                            AppToast.error(activity, error != null && !error.trim().isEmpty()
                                    ? error
                                    : "Erreur lors de la sauvegarde"));
                }
            });
        });

        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = DS.dp(activity, 14);
        card.addView(save, sp);

        // ── Bouton Supprimer (masqué pour l'utilisateur courant) ──
        String myId = "";
        try { myId = AuthManager.getInstance().getUserId(); } catch (Exception ignored) {}
        boolean isMe = member.docPath != null && myId != null && !myId.isEmpty()
                && member.docPath.contains(myId);
        if (!isMe && member.docPath != null && !member.docPath.trim().isEmpty()) {
            final String myIdFinal = myId;
            TextView delete = new TextView(activity);
            delete.setText("Supprimer ce membre");
            delete.setTextColor(com.couplefinance.core.theme.ThemeColors.danger());
            delete.setTextSize(13f);
            delete.setGravity(Gravity.CENTER);
            delete.setPadding(DS.dp(activity, 14), DS.dp(activity, 10), DS.dp(activity, 14), DS.dp(activity, 10));
            delete.setOnClickListener(v -> new android.app.AlertDialog.Builder(activity)
                    .setTitle("Supprimer " + member.name + " ?")
                    .setMessage("Cette action supprime le membre du foyer. Son compte Firebase reste actif.")
                    .setPositiveButton("Supprimer", (d, w) ->
                            SettingsMemberWriter.deleteMember(member, new SettingsMemberWriter.Callback() {
                                public void onSuccess() {
                                    activity.runOnUiThread(() -> {
                                        AppToast.success(activity, member.name + " supprimé");
                                        if (onChanged != null) onChanged.run();
                                    });
                                }
                                public void onError(String e) {
                                    activity.runOnUiThread(() ->
                                            AppToast.error(activity, "Erreur : " + e));
                                }
                            }))
                    .setNegativeButton("Annuler", null)
                    .show());
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, -2);
            dp.topMargin = DS.dp(activity, 8);
            card.addView(delete, dp);
        }

        return card;
    }

    public static void showRatio(Activity activity, Runnable onChanged) {
        if (activity == null) return;

        int[] ratio = com.couplefinance.ui.repartition.RepartitionRepository.loadRatio(activity);
        final int[] value = {ratio != null && ratio.length > 0 ? ratio[0] : 50};

        LinearLayout content = vertical(activity);

        TextView preview = body(activity, ratioLabel(value[0]));
        preview.setGravity(Gravity.CENTER);
        preview.setTextSize(22f);
        preview.setTypeface(null, Typeface.BOLD);
        preview.setTextColor(ThemeColors.primary());
        content.addView(preview);

        android.widget.SeekBar seekBar = new android.widget.SeekBar(activity);
        seekBar.setMax(100);
        seekBar.setProgress(value[0]);

        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = DS.dp(activity, 16);
        content.addView(seekBar, sp);

        TextView hint = bodyMuted(activity, "Déplacez le curseur pour définir la part du premier membre.");
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = DS.dp(activity, 10);
        content.addView(hint, hp);

        seekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                value[0] = progress;
                preview.setText(ratioLabel(value[0]));
            }

            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {
            }

            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
            }
        });

        PremiumDialog.builder(activity)
                .icon("%")
                .title("Ratio de répartition")
                .subtitle("Ajustez la répartition des dépenses communes.")
                .content(content)
                .primary("Enregistrer", () -> {
                    com.couplefinance.ui.repartition.RepartitionRepository.saveRatio(activity, value[0]);
                    AppToast.success(activity, "Ratio : " + ratioLabel(value[0]));
                    if (onChanged != null) onChanged.run();
                })
                .secondary("Annuler", null)
                .show();
    }

    public static void showJointAccount(Activity activity, Runnable onChanged) {
        if (activity == null) return;

        LinearLayout content = vertical(activity);
        final JointAccountManager manager = JointAccountManager.getInstance();
        manager.init(activity);

        final Switch enabled = switchRow(activity, "Activer le compte joint", manager.isEnabledLocal(), null);

        final EditText name = input(activity, "Nom du compte joint");
        String currentName = manager.getNameLocal(activity);
        if (currentName == null || currentName.trim().isEmpty()) currentName = JointAccountManager.DEFAULT_NAME;
        name.setText(currentName);

        final EditText balance = input(activity, "Solde de début de mois");
        balance.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        try {
            balance.setText(String.valueOf(manager.getBalanceLocal(activity)));
        } catch (Exception ignored) {
            balance.setText("0");
        }

        content.addView(enabled);
        content.addView(space(activity, 12));
        content.addView(name);
        content.addView(space(activity, 12));
        content.addView(balance);

        final TextView hint = bodyMuted(activity,
                "Le compte joint est partagé entre tous les membres du foyer. "
                        + "Le solde de début de mois est synchronisé via Firestore : "
                        + "il est visible et modifiable par chaque membre.");
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = DS.dp(activity, 12);
        content.addView(hint, hp);

        // Recharge la valeur partagée depuis Firestore avant édition,
        // afin que X et Y voient toujours la même donnée à jour.
        manager.refresh(activity, () -> {
            if (activity.isFinishing()) return;
            try {
                enabled.setChecked(manager.isEnabledLocal());
                String n = manager.getNameLocal();
                name.setText((n == null || n.trim().isEmpty()) ? JointAccountManager.DEFAULT_NAME : n);
                balance.setText(String.valueOf(manager.getBalanceLocal()));
            } catch (Exception ignored) {
            }
        });

        PremiumDialog.builder(activity)
                .icon("🏦")
                .title("Compte joint")
                .subtitle("Gérez le compte commun du foyer.")
                .content(content)
                .primary("Enregistrer", () -> {
                    String cleanName = name.getText().toString().trim();
                    if (cleanName.isEmpty()) cleanName = JointAccountManager.DEFAULT_NAME;

                    double amount = 0;
                    try { amount = Double.parseDouble(balance.getText().toString().trim().replace(",", ".")); }
                    catch (Exception ignored) { amount = 0; }

                    final boolean isEnabled = enabled.isChecked();
                    final String finalName = cleanName;
                    final double finalAmount = amount;

                    // Sauvegarde partagée Firestore (settings/current + monthlyBalances).
                    manager.saveSettings(activity, isEnabled, finalName, finalAmount,
                            new JointAccountManager.Callback() {
                                public void onSuccess() {
                                    AppToast.success(activity, "Compte joint synchronisé");
                                    if (onChanged != null) onChanged.run();
                                }

                                public void onError(String error) {
                                    // Le snapshot local a déjà été mis à jour (optimiste) :
                                    // la modification ne sera simplement pas encore propagée.
                                    // On affiche la cause réelle pour faciliter le diagnostic.
                                    AppToast.error(activity,
                                            error == null || error.trim().isEmpty()
                                                    ? "Compte joint : échec de synchronisation"
                                                    : error);
                                    if (onChanged != null) onChanged.run();
                                }
                            });
                })
                .secondary("Annuler", null)
                .show();
    }

    public static void showCategories(Activity activity) {
        if (activity == null) return;

        android.app.Dialog dialog = new android.app.Dialog(activity,
                android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
        dialog.setContentView(buildCategoriesContent(activity, dialog));
        dialog.show();
    }

    private static android.view.ViewGroup buildCategoriesContent(
            Activity activity, android.app.Dialog dialog) {

        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        scroll.setBackgroundColor(ThemeColors.background());

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = DS.dp(activity, 16);
        root.setPadding(p, DS.dp(activity, 48), p, p);
        scroll.addView(root);

        // Header row: back button + title
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView btnBack = new TextView(activity);
        btnBack.setText("←");
        btnBack.setTextSize(22);
        btnBack.setTextColor(ThemeColors.accent());
        btnBack.setPadding(0, 0, DS.dp(activity, 12), 0);
        btnBack.setOnClickListener(v -> dialog.dismiss());
        header.addView(btnBack);

        TextView title = new TextView(activity);
        title.setText("Catégories");
        title.setTextSize(20);
        title.setTextColor(ThemeColors.text());
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title);

        root.addView(header);

        // Spacer
        View spacer = new View(activity);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, DS.dp(activity, 16)));
        root.addView(spacer);

        // SettingsCategoriesSection content
        View section = new SettingsCategoriesSection(activity).build();
        root.addView(section);

        return scroll;
    }

    public static void showCategoryEditor(
            Activity activity,
            String type,
            SettingsModels.Category existing,
            CategoryCallback onSave,
            DeleteCallback onDelete
    ) {
        if (activity == null) return;

        LinearLayout content = vertical(activity);

        EditText name = input(activity, "Nom de la catégorie");
        EditText emoji = input(activity, "Emoji");
        EditText budget = input(activity, "Budget mensuel");
        EditText color = input(activity, "Couleur hexadécimale");

        budget.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        color.setSingleLine(true);

        if (existing != null) {
            name.setText(existing.name != null ? existing.name : "");
            emoji.setText(existing.emoji != null ? existing.emoji : "");
            budget.setText(String.valueOf(existing.budget));
            color.setText(existing.color != null ? existing.color : "");
        } else {
            emoji.setText("income".equals(type) ? "↗️" : "🏷️");
            color.setText("#C0614A");
        }

        content.addView(name);
        content.addView(space(activity, 10));
        content.addView(emoji);
        content.addView(space(activity, 10));
        content.addView(budget);
        content.addView(space(activity, 10));
        content.addView(color);

        PremiumDialog.builder(activity)
                .icon("🏷️")
                .title(existing == null ? "Nouvelle catégorie" : "Modifier catégorie")
                .subtitle(existing == null ? "Créer une nouvelle catégorie." : "Modifier cette catégorie.")
                .content(content)
                .primary("Enregistrer", () -> {
                    String cleanName = name.getText().toString().trim();
                    if (cleanName.isEmpty()) {
                        AppToast.error(activity, "Nom invalide");
                        return;
                    }

                    SettingsModels.Category c = existing != null ? existing : new SettingsModels.Category();
                    c.name = cleanName;
                    c.type = type != null && !type.trim().isEmpty() ? type : c.type;
                    c.emoji = emoji.getText().toString().trim();
                    c.color = color.getText().toString().trim();
                    c.active = true;

                    try { c.budget = Double.parseDouble(budget.getText().toString().trim()); }
                    catch (Exception ignored) { c.budget = 0; }

                    if (onSave != null) onSave.onCategory(c);
                })
                .secondary(existing != null ? "Supprimer" : "Annuler", () -> {
                    if (existing != null && onDelete != null) onDelete.onDelete();
                })
                .show();
    }

    // ─────────────────────────────────────────────────────────────
    // Apparence / données
    // ─────────────────────────────────────────────────────────────

    public static void showThemeColors(Activity activity, Runnable onChanged) {
        if (activity == null) return;

        LinearLayout content = vertical(activity);

        TextView hint = bodyMuted(activity,
                "Choisissez une couleur pastel. Elle sera appliquée aux bannières, boutons, cartes et modals via ThemeManager.");
        content.addView(hint);

        LinearLayout palette = new LinearLayout(activity);
        palette.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row1 = new LinearLayout(activity);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout row2 = new LinearLayout(activity);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = DS.dp(activity, 18);
        palette.addView(row1, rp);

        LinearLayout.LayoutParams r2p = new LinearLayout.LayoutParams(-1, -2);
        r2p.topMargin = DS.dp(activity, 12);
        palette.addView(row2, r2p);

        String[][] themes = {
                {"Terracotta", "terracotta", "#C86B4A"},
                {"Sauge", "sage", "#6FA17D"},
                {"Bleu", "ocean", "#5D8FA3"},
                {"Lavande", "lavender", "#8065B3"},
                {"Rose", "rose", "#B96B8C"},
                {"Menthe", "mint", "#4C9A8A"},
                {"Sable", "sand", "#B9834F"}
        };

        for (int i = 0; i < themes.length; i++) {
            View item = themeCircleItem(activity, themes[i][0], themes[i][1], themes[i][2], onChanged);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i % 4 != 0) ip.leftMargin = DS.dp(activity, 10);
            if (i < 4) row1.addView(item, ip);
            else row2.addView(item, ip);
        }

        content.addView(palette);

        PremiumDialog.builder(activity)
                .icon("◍")
                .title("Couleur du thème")
                .subtitle("Palette pastel premium")
                .content(content)
                .primary("Fermer", null)
                .noSecondary()
                .show();
    }

    private static View themeCircleItem(Activity activity,
                                        String label,
                                        String id,
                                        String hex,
                                        Runnable onChanged) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(0, DS.dp(activity, 4), 0, DS.dp(activity, 4));

        TextView circle = colorCircle(activity, hex, isCurrentTheme(activity, id, hex));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(DS.dp(activity, 44), DS.dp(activity, 44));
        box.addView(circle, cp);

        TextView tv = new TextView(activity);
        tv.setText(label);
        tv.setTextSize(11f);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(ThemeColors.subtext());
        tv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = DS.dp(activity, 7);
        box.addView(tv, tp);

        box.setOnClickListener(v -> {
            try {
                ThemeManager.getInstance().applyTheme(activity, id);
                SettingsStyles.syncWithGlobalTheme();
                AppToast.success(activity, "Thème : " + label);
                if (onChanged != null) onChanged.run();
            } catch (Exception e) {
                try {
                    SettingsStyles.applyTheme(activity, hex);
                    SettingsStyles.syncWithGlobalTheme();
                    AppToast.success(activity, "Thème : " + label);
                    if (onChanged != null) onChanged.run();
                } catch (Exception ignored) {
                    AppToast.error(activity, "Impossible d'appliquer le thème");
                }
            }
        });

        return box;
    }

    private static boolean isCurrentTheme(Activity activity, String id, String hex) {
        try {
            String current = ThemeManager.getInstance().getCurrentThemeId();
            if (id.equalsIgnoreCase(current)) return true;
        } catch (Exception ignored) {
        }

        try {
            return colorToHex(ThemeColors.primary()).equalsIgnoreCase(hex);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void toggleDarkMode(Activity activity, boolean checked) {
        if (activity == null) return;

        SharedPreferences prefs = activity.getSharedPreferences("couplefinance_theme", Activity.MODE_PRIVATE);
        prefs.edit().putBoolean("dark_mode", checked).apply();
        activity.recreate();
    }

    public static void showLanguage(Activity activity, Runnable onChanged) {
        if (activity == null) return;

        SharedPreferences prefs = activity.getSharedPreferences("couplefinance_language", Activity.MODE_PRIVATE);
        final String[] selected = {prefs.getString("language", "fr")};

        LinearLayout content = vertical(activity);
        TextView fr = selectable(activity, "Français", "fr".equals(selected[0]));
        TextView en = selectable(activity, "English", "en".equals(selected[0]));

        fr.setOnClickListener(v -> {
            selected[0] = "fr";
            fr.setText("✓ Français");
            en.setText("English");
        });

        en.setOnClickListener(v -> {
            selected[0] = "en";
            en.setText("✓ English");
            fr.setText("Français");
        });

        content.addView(fr);
        content.addView(en);

        PremiumDialog.builder(activity)
                .icon("🌍")
                .title("Langue")
                .subtitle("Choisissez la langue de l'application.")
                .content(content)
                .primary("Enregistrer", () -> {
                    prefs.edit().putString("language", selected[0]).apply();
                    AppToast.success(activity, "Langue mise à jour");
                    if (onChanged != null) onChanged.run();
                })
                .secondary("Annuler", null)
                .show();
    }

    public static void showCurrency(Activity activity, Runnable onChanged) {
        if (activity == null) return;

        SharedPreferences prefs = activity.getSharedPreferences("couplefinance_currency", Activity.MODE_PRIVATE);
        final String[] selected = {prefs.getString("currency", "EUR")};

        LinearLayout content = vertical(activity);
        String[] currencies = {"EUR", "USD", "GBP", "CHF"};

        for (String c : currencies) {
            TextView tv = selectable(activity, currencyLabel(c), c.equals(selected[0]));
            tv.setTag(c);
            tv.setOnClickListener(v -> {
                Object tag = v.getTag();
                if (!(tag instanceof String)) return;
                selected[0] = (String) tag;

                for (int i = 0; i < content.getChildCount(); i++) {
                    View child = content.getChildAt(i);
                    if (child instanceof TextView) {
                        TextView t = (TextView) child;
                        Object childTag = t.getTag();
                        if (childTag instanceof String) {
                            String code = (String) childTag;
                            t.setText(code.equals(selected[0]) ? "✓ " + currencyLabel(code) : currencyLabel(code));
                        }
                    }
                }
            });
            content.addView(tv);
        }

        PremiumDialog.builder(activity)
                .icon("€")
                .title("Devise")
                .subtitle("Choisissez la devise d'affichage.")
                .content(content)
                .primary("Enregistrer", () -> {
                    prefs.edit().putString("currency", selected[0]).apply();
                    AppToast.success(activity, "Devise : " + selected[0]);
                    if (onChanged != null) onChanged.run();
                })
                .secondary("Annuler", null)
                .show();
    }

    public static void showSync(Activity activity, Runnable onChanged) {
        if (activity == null) return;

        PremiumDialog.builder(activity)
                .icon("↻")
                .title("Synchronisation")
                .subtitle("Recharge les paramètres depuis Firestore.")
                .primary("Synchroniser", () -> {
                    new SettingsRepository(activity).load(new SettingsRepository.LoadCallback() {
                        public void onLoaded(SettingsModels.State state) {
                            activity.runOnUiThread(() -> {
                                SharedPreferences prefs = activity.getSharedPreferences("couplefinance_settings", Activity.MODE_PRIVATE);
                                String label = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.FRANCE)
                                        .format(new java.util.Date());
                                prefs.edit()
                                        .putLong("last_sync_ts", System.currentTimeMillis())
                                        .putString("last_sync_label", label)
                                        .apply();
                                AppToast.success(activity, "Synchronisé");
                                if (onChanged != null) onChanged.run();
                            });
                        }

                        public void onError(String error) {
                            activity.runOnUiThread(() -> AppToast.error(activity, "Erreur de synchronisation"));
                        }
                    });
                })
                .secondary("Annuler", null)
                .show();
    }

    public static void showImport(Activity activity) {
        if (activity == null) return;
        PremiumDialog.builder(activity)
                .icon("↥")
                .title("Importer")
                .subtitle("L'import bancaire se fait depuis l'onglet Transactions pour conserver le pipeline PDF existant.")
                .primary("OK", null)
                .noSecondary()
                .show();
    }

    public static void showExport(Activity activity) {
        if (activity == null) return;

        LinearLayout content = vertical(activity);
        TextView csv = selectable(activity, "Exporter les transactions CSV", false);
        TextView summary = selectable(activity, "Exporter le résumé Paramètres", false);
        content.addView(csv);
        content.addView(summary);

        csv.setOnClickListener(v -> SettingsDataExportManager.exportTransactionsCsv(activity, new SettingsDataExportManager.ExportCallback() {
            public void onSuccess(File file) {
                activity.runOnUiThread(() -> AppToast.success(activity, "Export créé : " + file.getName()));
            }

            public void onError(String error) {
                activity.runOnUiThread(() -> AppToast.error(activity, "Export impossible"));
            }
        }));

        summary.setOnClickListener(v -> SettingsDataExportManager.exportSettingsSummary(activity, new SettingsDataExportManager.ExportCallback() {
            public void onSuccess(File file) {
                activity.runOnUiThread(() -> AppToast.success(activity, "Export créé : " + file.getName()));
            }

            public void onError(String error) {
                activity.runOnUiThread(() -> AppToast.error(activity, "Export impossible"));
            }
        }));

        PremiumDialog.builder(activity)
                .icon("↧")
                .title("Exporter les données")
                .subtitle("Choisissez le type d'export local.")
                .content(content)
                .primary("Fermer", null)
                .noSecondary()
                .show();
    }

    // ─────────────────────────────────────────────────────────────
    // Danger
    // ─────────────────────────────────────────────────────────────

    public static void confirmLogout(Activity activity) {
        if (activity == null) return;

        PremiumDialog.builder(activity)
                .icon("🚪")
                .title("Déconnexion")
                .subtitle("Vous allez quitter votre session. Les données locales temporaires seront nettoyées.")
                .primary("Se déconnecter", () -> SettingsSessionCleaner.logout(activity))
                .secondary("Annuler", null)
                .show();
    }

    public static void confirmDeleteAccount(Activity activity) {
        if (activity == null) return;

        LinearLayout content = vertical(activity);
        TextView warning = bodyMuted(activity,
                "Cette action est irréversible. Pour éviter une suppression accidentelle, tapez SUPPRIMER ci-dessous.");
        content.addView(warning);

        EditText input = input(activity, "SUPPRIMER");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
        ip.topMargin = DS.dp(activity, 14);
        content.addView(input, ip);

        PremiumDialog.builder(activity)
                .icon("⚠️")
                .title("Supprimer le compte")
                .subtitle("Confirmation forte requise.")
                .content(content)
                .primary("Supprimer", () -> {
                    String typed = input.getText().toString().trim();
                    if (!"SUPPRIMER".equals(typed)) {
                        AppToast.error(activity, "Confirmation incorrecte");
                        return;
                    }
                    SettingsSessionCleaner.clearAllLocalUserData(activity);
                    AppToast.success(activity, "Données locales supprimées");
                    SettingsSessionCleaner.logout(activity);
                })
                .secondary("Annuler", null)
                .show();
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers UI
    // ─────────────────────────────────────────────────────────────

    private static LinearLayout vertical(Activity activity) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, DS.dp(activity, 4), 0, 0);
        return root;
    }

    private static TextView body(Activity activity, String text) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextColor(ThemeColors.text());
        tv.setTextSize(DS.TEXT_BODY);
        return tv;
    }

    private static TextView bodyMuted(Activity activity, String text) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextColor(ThemeColors.subtext());
        tv.setTextSize(DS.TEXT_SM);
        tv.setLineSpacing(DS.dp(activity, 2), 1.0f);
        return tv;
    }

    private static EditText input(Activity activity, String hint) {
        EditText input = new EditText(activity);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setTextColor(ThemeColors.text());
        input.setHintTextColor(ThemeColors.subtext());
        input.setTextSize(DS.TEXT_BODY);
        input.setPadding(DS.dp(activity, 14), DS.dp(activity, 10), DS.dp(activity, 14), DS.dp(activity, 10));

        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(ThemeColors.card());
        bg.setCornerRadius(DS.dp(activity, DS.R_MD));
        bg.setStroke(DS.dp(activity, 1), ThemeColors.border());
        input.setBackground(bg);
        return input;
    }

    private static Switch switchRow(Activity activity,
                                    String label,
                                    boolean checked,
                                    CompoundButton.OnCheckedChangeListener listener) {
        Switch sw = new Switch(activity);
        sw.setText(label);
        sw.setTextColor(ThemeColors.text());
        sw.setTextSize(DS.TEXT_BODY);
        sw.setChecked(checked);
        sw.setPadding(0, DS.dp(activity, 8), 0, DS.dp(activity, 8));
        if (listener != null) sw.setOnCheckedChangeListener(listener);

        try {
            sw.setThumbTintList(android.content.res.ColorStateList.valueOf(ThemeColors.primary()));
        } catch (Exception ignored) {
        }

        return sw;
    }

    private static TextView colorCircle(Activity activity, String hex, boolean selected) {
        TextView dot = new TextView(activity);
        dot.setText(selected ? "✓" : "");
        dot.setGravity(Gravity.CENTER);
        dot.setTextSize(13f);
        dot.setTypeface(null, Typeface.BOLD);
        dot.setTextColor(Color.WHITE);
        dot.setBackground(circleBg(parseColor(hex, ThemeColors.primary())));
        dot.setAlpha(selected ? 1f : 0.72f);
        return dot;
    }

    private static TextView selectable(Activity activity, String label, boolean selected) {
        TextView tv = body(activity, selected ? "✓ " + label : label);
        tv.setPadding(DS.dp(activity, 14), DS.dp(activity, 12), DS.dp(activity, 14), DS.dp(activity, 12));
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setClickable(true);
        tv.setFocusable(true);

        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(selected ? ThemeColors.primarySoft() : ThemeColors.card());
        bg.setCornerRadius(DS.dp(activity, DS.R_MD));
        bg.setStroke(DS.dp(activity, 1), selected ? ThemeColors.primary() : ThemeColors.border());
        tv.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = DS.dp(activity, 8);
        tv.setLayoutParams(lp);
        return tv;
    }

    private static View space(Activity activity, int dp) {
        View v = new View(activity);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, DS.dp(activity, dp)));
        return v;
    }

    private static android.graphics.drawable.GradientDrawable dialogCardBg(Activity activity) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ThemeColors.card());
        bg.setCornerRadius(DS.dp(activity, 18));
        bg.setStroke(DS.dp(activity, 1), ThemeColors.border());
        return bg;
    }

    private static GradientDrawable circleBg(int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color);
        return bg;
    }

    private static GradientDrawable pillBg(int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(999);
        return bg;
    }

    private static GradientDrawable choiceBg(Activity activity, int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ThemeColors.card());
        bg.setCornerRadius(DS.dp(activity, 14));
        bg.setStroke(DS.dp(activity, 2), color);
        return bg;
    }

    private static int parseColor(String hex, int fallback) {
        try {
            if (hex == null || hex.trim().isEmpty()) return fallback;
            return Color.parseColor(hex.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String ratioLabel(int first) {
        int safe = Math.max(0, Math.min(100, first));
        return safe + " / " + (100 - safe);
    }

    private static String currencyLabel(String currency) {
        if ("USD".equals(currency)) return "USD ($)";
        if ("GBP".equals(currency)) return "GBP (£)";
        if ("CHF".equals(currency)) return "CHF (CHF)";
        return "EUR (€)";
    }

    private static String colorToHex(int color) {
        return String.format(Locale.FRANCE, "#%06X", (0xFFFFFF & color));
    }
}
