package com.couplefinance.core.ui.components;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.effects.GradientFactory;

public final class PremiumInput {

    private PremiumInput() {
    }

    // ─────────────────────────────
    // INPUTS
    // ─────────────────────────────

    public static EditText normal(Context ctx, String hint) {
        EditText et = base(ctx, hint);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        return et;
    }

    public static EditText search(Context ctx, String hint) {
        EditText et = base(ctx, "🔍  " + hint);

        et.setBackground(
                GradientFactory.bordered(
                        ctx,
                        ThemeColors.backgroundSecondary(),
                        ThemeColors.border(),
                        DS.R_SM
                )
        );

        return et;
    }

    public static EditText numeric(Context ctx, String hint) {
        EditText et = base(ctx, hint);

        et.setInputType(
                InputType.TYPE_CLASS_NUMBER |
                InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        return et;
    }

    public static EditText email(Context ctx, String hint) {
        EditText et = base(ctx, hint);

        et.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        );

        return et;
    }

    public static EditText password(Context ctx, String hint) {
        EditText et = base(ctx, hint);

        et.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        return et;
    }

    public static EditText multiline(Context ctx, String hint) {
        EditText et = base(ctx, hint);

        et.setSingleLine(false);
        et.setMinLines(3);

        et.setGravity(Gravity.TOP | Gravity.START);

        et.setPadding(
                DS.dp(ctx, DS.PAD_INPUT),
                DS.dp(ctx, 14),
                DS.dp(ctx, DS.PAD_INPUT),
                DS.dp(ctx, 14)
        );

        et.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                DS.dp(ctx, 110)
        ));

        return et;
    }

    // ─────────────────────────────
    // FIELD BLOCK
    // ─────────────────────────────

    public static LinearLayout field(Context ctx,
                                     String label,
                                     EditText input) {

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView tvLabel = label(ctx, label);

        LinearLayout.LayoutParams lpLabel =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        lpLabel.bottomMargin = DS.dp(ctx, 8);

        col.addView(tvLabel, lpLabel);

        if (input != null) {
            col.addView(input);
        }

        return col;
    }

    public static TextView label(Context ctx, String text) {
        TextView tv = new TextView(ctx);

        tv.setText(text);

        tv.setTextColor(ThemeColors.primary());
        tv.setTextSize(DS.TEXT_XS);

        tv.setTypeface(null, Typeface.BOLD);
        tv.setLetterSpacing(0.08f);

        return tv;
    }

    // ─────────────────────────────
    // INTERNAL
    // ─────────────────────────────

    private static EditText base(Context ctx, String hint) {
        EditText et = new EditText(ctx);

        et.setHint(hint);

        et.setHintTextColor(ThemeColors.inputHint());
        et.setTextColor(ThemeColors.inputText());

        et.setTextSize(DS.TEXT_BODY);
        et.setTypeface(null, Typeface.NORMAL);

        et.setSingleLine(true);

        et.setGravity(Gravity.CENTER_VERTICAL);

        et.setBackground(
                GradientFactory.input(ctx)
        );

        et.setIncludeFontPadding(true);

        et.setMinHeight(
                DS.dp(ctx, DS.INPUT_HEIGHT)
        );

        et.setPadding(
                DS.dp(ctx, DS.PAD_INPUT),
                DS.dp(ctx, 10),
                DS.dp(ctx, DS.PAD_INPUT),
                DS.dp(ctx, 10)
        );

        et.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                DS.dp(ctx, DS.INPUT_HEIGHT)
        ));

        return et;
    }
}