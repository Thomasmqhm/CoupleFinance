package com.couplefinance.ui.settings;

import android.app.Activity;

public class SettingsResponsive {

    public static boolean isTablet(Activity a) {

        return a.getResources()
                .getDisplayMetrics()
                .widthPixels
                / a.getResources().getDisplayMetrics().density >= 900;
    }

    public static boolean isSmallTablet(Activity a) {

        float dp =
                a.getResources()
                        .getDisplayMetrics()
                        .widthPixels
                        / a.getResources()
                        .getDisplayMetrics()
                        .density;

        return dp >= 700 && dp < 900;
    }

    public static boolean isPhone(Activity a) {

        return !isTablet(a)
                && !isSmallTablet(a);
    }

    public static int sideWidth(Activity a) {

        if (isTablet(a))
            return SettingsStyles.dp(a, 320);

        if (isSmallTablet(a))
            return SettingsStyles.dp(a, 280);

        return -1;
    }

    public static boolean useTwoColumns(Activity a) {

        return !isPhone(a);
    }
}