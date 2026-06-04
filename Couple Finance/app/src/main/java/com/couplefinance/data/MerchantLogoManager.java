package com.couplefinance.ui.utils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.couplefinance.core.ui.DS;

import java.text.Normalizer;
import java.util.Locale;

public final class MerchantLogoManager {

    private MerchantLogoManager() {
    }

    public static View createMerchantBubble(Context context,
                                            String label,
                                            String category,
                                            boolean isIncome,
                                            int fallbackColor,
                                            int fallbackSoftBg,
                                            int fallbackBorder) {

        String merchantKey = detectMerchantKey(label, category);
        MerchantStyle style = getMerchantStyle(
                merchantKey,
                category,
                isIncome,
                fallbackColor,
                fallbackSoftBg,
                fallbackBorder
        );

        FrameLayout bubble = new FrameLayout(context);
        bubble.setPadding(0, 0, 0, 0);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(style.backgroundColor);
        bg.setStroke(DS.dp(context, 1), style.borderColor);
        bubble.setBackground(bg);

        int drawableId = context.getResources().getIdentifier(
                "merchant_" + merchantKey,
                "drawable",
                context.getPackageName()
        );

        if (drawableId != 0) {
            ImageView image = new ImageView(context);
            image.setImageResource(drawableId);
            image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            image.setAdjustViewBounds(false);
            image.setBackgroundColor(Color.TRANSPARENT);

            int innerSize = DS.dp(context, logoSizeFor(merchantKey));

            FrameLayout.LayoutParams imageLp = new FrameLayout.LayoutParams(
                    innerSize,
                    innerSize,
                    Gravity.CENTER
            );

            bubble.addView(image, imageLp);

            return bubble;
        }

        TextView logo = new TextView(context);
        logo.setText(style.logoText);
        logo.setTextColor(style.textColor);
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setIncludeFontPadding(false);
        logo.setSingleLine(true);
        logo.setTextSize(style.textSizeSp);
        logo.setBackgroundColor(Color.TRANSPARENT);

        bubble.addView(
                logo,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER
                )
        );

        return bubble;
    }

    private static int logoSizeFor(String key) {
        if (key == null) {
            return 34;
        }

        if ("lidl".equals(key)) {
            return 36;
        }

        if ("carrefour".equals(key)) {
            return 37;
        }

        if ("leclerc".equals(key)) {
            return 36;
        }

        if ("superu".equals(key)) {
            return 36;
        }

        if ("amazon".equals(key)) {
            return 36;
        }

        if ("netflix".equals(key)) {
            return 35;
        }

        if ("spotify".equals(key)) {
            return 35;
        }

        if ("disney".equals(key)) {
            return 36;
        }

        if ("apple".equals(key)) {
            return 34;
        }

        if ("edf".equals(key) || "saur".equals(key)) {
            return 35;
        }

        if ("orange".equals(key) || "free".equals(key) || "bouygues".equals(key)) {
            return 35;
        }

        if ("paypal".equals(key) || "revolut".equals(key) || "bnp".equals(key)) {
            return 35;
        }

        return 34;
    }

    public static String detectMerchantKey(String label, String category) {
        String n = normalize((label == null ? "" : label) + " " + (category == null ? "" : category));

        if (has(n, "lidl"))
            return "lidl";
        if (has(n, "leclerc", "e.leclerc", "e leclerc"))
            return "leclerc";
        if (has(n, "super u", "hyper u", "utile"))
            return "superu";
        if (has(n, "carrefour", "carrefour market", "carrefour city"))
            return "carrefour";
        if (has(n, "amazon prime", "amazon payments", "amazon", "amzn"))
            return "amazon";
        if (has(n, "google play", "google"))
            return "google";
        if (has(n, "netflix"))
            return "netflix";
        if (has(n, "spotify"))
            return "spotify";
        if (has(n, "disney"))
            return "disney";
        if (has(n, "apple"))
            return "apple";
        if (has(n, "edf"))
            return "edf";
        if (has(n, "saur"))
            return "saur";
        if (has(n, "bouygues"))
            return "bouygues";
        if (has(n, "orange"))
            return "orange";
        if (has(n, "free mobile", "free telecom", " free "))
            return "free";
        if (has(n, "diac"))
            return "diac";
        if (has(n, "bnp paribas", "bnp"))
            return "bnp";
        if (has(n, "relay"))
            return "relay";
        if (has(n, "paddington", "padington"))
            return "paddington";
        if (has(n, "boardgamearena", "board game arena"))
            return "boardgamearena";
        if (has(n, "paypal"))
            return "paypal";
        if (has(n, "revolut"))
            return "revolut";
        if (has(n, "credit mutuel", "cmb"))
            return "creditmutuel";

        if (has(n, "revenu", "salaire", "virement recu"))
            return "income";
        if (has(n, "tabac", "presse"))
            return "tabac";
        if (has(n, "alimentation", "courses"))
            return "food";
        if (has(n, "transport", "essence", "carburant"))
            return "transport";
        if (has(n, "logement", "loyer"))
            return "home";
        if (has(n, "credit", "pret"))
            return "credit";
        if (has(n, "frais bancaire", "commission", "cotisation"))
            return "bankfees";

        return "default";
    }

    private static MerchantStyle getMerchantStyle(String key,
                                                  String category,
                                                  boolean isIncome,
                                                  int fallbackColor,
                                                  int fallbackSoftBg,
                                                  int fallbackBorder) {

        if ("lidl".equals(key))
            return style("LIDL", "#0050AA", "#FFF7CC", "#F2D200", 10.5f);
        if ("leclerc".equals(key))
            return style("E.L", "#005BAA", "#EAF3FF", "#B9D7F5", 11.5f);
        if ("superu".equals(key))
            return style("U", "#E30613", "#FFF1F2", "#F7C7CC", 18f);
        if ("carrefour".equals(key))
            return style("C", "#0050A4", "#EEF5FF", "#C7DBF5", 18f);
        if ("amazon".equals(key))
            return style("a", "#111827", "#FFF7ED", "#FDBA74", 20f);
        if ("google".equals(key))
            return style("G", "#4285F4", "#F8FAFC", "#D1D5DB", 18f);
        if ("netflix".equals(key))
            return style("N", "#E50914", "#FEF2F2", "#FECACA", 18f);
        if ("spotify".equals(key))
            return style("S", "#1DB954", "#ECFDF5", "#BBF7D0", 18f);
        if ("disney".equals(key))
            return style("D+", "#113CCF", "#EEF2FF", "#C7D2FE", 11.5f);
        if ("apple".equals(key))
            return style("", "#111827", "#F8FAFC", "#CBD5E1", 17f);
        if ("edf".equals(key))
            return style("EDF", "#005BBB", "#EFF6FF", "#BFDBFE", 10.5f);
        if ("saur".equals(key))
            return style("EAU", "#0284C7", "#ECFEFF", "#BAE6FD", 10.5f);
        if ("bouygues".equals(key))
            return style("BT", "#009FE3", "#ECFEFF", "#BAE6FD", 11f);
        if ("orange".equals(key))
            return style("O", "#FF7900", "#FFF7ED", "#FED7AA", 18f);
        if ("free".equals(key))
            return style("F", "#D71920", "#FEF2F2", "#FECACA", 18f);
        if ("diac".equals(key))
            return style("DIAC", "#1D4ED8", "#EFF6FF", "#BFDBFE", 9.5f);
        if ("bnp".equals(key))
            return style("BNP", "#00915A", "#ECFDF5", "#BBF7D0", 10f);
        if ("relay".equals(key))
            return style("R", "#C1121F", "#FFF7ED", "#FED7AA", 18f);
        if ("paddington".equals(key))
            return style("P", "#92400E", "#FFF7ED", "#FED7AA", 18f);
        if ("boardgamearena".equals(key))
            return style("BGA", "#EA580C", "#FFF7ED", "#FED7AA", 9.5f);
        if ("paypal".equals(key))
            return style("P", "#003087", "#EFF6FF", "#BFDBFE", 18f);
        if ("revolut".equals(key))
            return style("R", "#111827", "#F8FAFC", "#CBD5E1", 18f);
        if ("creditmutuel".equals(key))
            return style("CM", "#C0614A", "#FFF1EC", "#F2CDBD", 11f);

        if ("income".equals(key))
            return style("+", "#047857", "#ECFDF5", "#BBF7D0", 20f);
        if ("tabac".equals(key))
            return style("TAB", "#92400E", "#FFF7ED", "#FED7AA", 10f);
        if ("food".equals(key))
            return style("🍽", "#16A34A", "#F0FDF4", "#BBF7D0", 17f);
        if ("transport".equals(key))
            return style("⛽", "#2563EB", "#EFF6FF", "#BFDBFE", 17f);
        if ("home".equals(key))
            return style("⌂", "#7C3AED", "#F5F3FF", "#DDD6FE", 17f);
        if ("credit".equals(key))
            return style("€", "#7F1D1D", "#FEF2F2", "#FECACA", 18f);
        if ("bankfees".equals(key))
            return style("€", "#4B5563", "#F3F4F6", "#D1D5DB", 18f);

        return style(
                isIncome ? "+" : "•",
                toHex(fallbackColor),
                toHex(fallbackSoftBg),
                toHex(fallbackBorder),
                18f
        );
    }

    private static MerchantStyle style(String logoText,
                                       String textColor,
                                       String bgColor,
                                       String borderColor,
                                       float textSizeSp) {

        return new MerchantStyle(
                logoText,
                Color.parseColor(textColor),
                Color.parseColor(bgColor),
                Color.parseColor(borderColor),
                textSizeSp
        );
    }

    private static String normalize(String s) {
        if (s == null)
            return "";

        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        return n.toLowerCase(Locale.FRANCE)
                .replaceAll("[^a-z0-9+ ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean has(String normalized, String... needles) {
        if (normalized == null)
            return false;

        for (String needle : needles) {
            String n = normalize(needle);
            if (normalized.contains(n))
                return true;
        }

        return false;
    }

    private static String toHex(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }

    private static final class MerchantStyle {
        final String logoText;
        final int textColor;
        final int backgroundColor;
        final int borderColor;
        final float textSizeSp;

        MerchantStyle(String logoText,
                      int textColor,
                      int backgroundColor,
                      int borderColor,
                      float textSizeSp) {

            this.logoText = logoText;
            this.textColor = textColor;
            this.backgroundColor = backgroundColor;
            this.borderColor = borderColor;
            this.textSizeSp = textSizeSp;
        }
    }
}