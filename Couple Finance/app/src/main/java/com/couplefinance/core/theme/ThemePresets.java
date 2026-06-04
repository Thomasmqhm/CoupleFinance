package com.couplefinance.core.theme;

import android.graphics.Color;

public class ThemePresets {

    public static ThemePalette terracotta() {
        return pastel(
                "terracotta",
                "Terracotta doux",
                "#C86B4A",
                "#A95438",
                "#EBC4B6",
                "#FAF4EF",
                "#F3E8DF",
                "#2A211C",
                "#8E7B6E"
        );
    }

    public static ThemePalette ocean() {
        return pastel(
                "ocean",
                "Bleu pastel",
                "#5D8FA3",
                "#3F6E80",
                "#C8E2EA",
                "#F3F8FA",
                "#E8F2F5",
                "#183238",
                "#6F858A"
        );
    }

    public static ThemePalette sage() {
        return pastel(
                "sage",
                "Sauge pastel",
                "#6FA17D",
                "#4E7E5E",
                "#CFE6D5",
                "#F4F9F5",
                "#E8F2EA",
                "#1F3026",
                "#718276"
        );
    }

    public static ThemePalette lavender() {
        return pastel(
                "lavender",
                "Lavande pastel",
                "#8065B3",
                "#60458F",
                "#D8CDEF",
                "#F7F4FC",
                "#EDE8F7",
                "#282136",
                "#7C718E"
        );
    }

    public static ThemePalette rose() {
        return pastel(
                "rose",
                "Rose poudré",
                "#B96B8C",
                "#944D6D",
                "#ECCAD8",
                "#FCF5F8",
                "#F5E7ED",
                "#33232A",
                "#8B7380"
        );
    }

    public static ThemePalette mint() {
        return pastel(
                "mint",
                "Menthe pastel",
                "#4C9A8A",
                "#337669",
                "#C6E8E1",
                "#F2FAF8",
                "#E3F3EF",
                "#17332E",
                "#6E8580"
        );
    }

    public static ThemePalette sand() {
        return pastel(
                "sand",
                "Sable premium",
                "#B9834F",
                "#91643A",
                "#EBD2B8",
                "#FAF6F0",
                "#F1E7D9",
                "#30261D",
                "#887766"
        );
    }

    public static ThemePalette dark() {
        ThemePalette t = terracotta();

        t.id = "dark";
        t.name = "Dark";

        t.primary = Color.parseColor("#D28A70");
        t.primaryDark = Color.parseColor("#B66B52");
        t.accent = Color.parseColor("#4A332C");

        t.background = Color.parseColor("#15110F");
        t.backgroundSecondary = Color.parseColor("#201A17");

        t.card = Color.parseColor("#241D1A");
        t.cardAlt = Color.parseColor("#2C2420");

        t.text = Color.parseColor("#F7EFEA");
        t.subtext = Color.parseColor("#B8A9A0");

        t.border = Color.parseColor("#3A302B");
        t.divider = Color.parseColor("#332A26");

        t.sidebar = Color.parseColor("#1D1715");
        t.widget = t.card;
        t.modal = t.card;

        t.switchActive = t.primary;
        t.switchInactive = Color.parseColor("#5A4B43");
        t.shadow = Color.parseColor("#66000000");

        return t;
    }

    private static ThemePalette pastel(String id,
                                       String name,
                                       String primary,
                                       String primaryDark,
                                       String accent,
                                       String background,
                                       String backgroundSecondary,
                                       String text,
                                       String subtext) {
        ThemePalette t = new ThemePalette();

        t.id = id;
        t.name = name;

        t.primary = Color.parseColor(primary);
        t.primaryDark = Color.parseColor(primaryDark);
        t.accent = Color.parseColor(accent);

        t.background = Color.parseColor(background);
        t.backgroundSecondary = Color.parseColor(backgroundSecondary);

        t.card = Color.WHITE;
        t.cardAlt = Color.parseColor("#FFFDFC");

        t.text = Color.parseColor(text);
        t.subtext = Color.parseColor(subtext);

        t.border = Color.parseColor("#E8DED6");
        t.divider = Color.parseColor("#EFE7E1");

        t.success = Color.parseColor("#4F9D72");
        t.warning = Color.parseColor("#D49A46");
        t.danger = Color.parseColor("#D76A6A");

        t.sidebar = Color.parseColor(backgroundSecondary);
        t.widget = Color.WHITE;
        t.modal = Color.WHITE;

        t.switchActive = t.primary;
        t.switchInactive = Color.parseColor("#D8CEC7");

        t.shadow = Color.parseColor("#14000000");

        return t;
    }
}