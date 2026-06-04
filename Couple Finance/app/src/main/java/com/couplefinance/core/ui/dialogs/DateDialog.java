package com.couplefinance.core.ui.dialogs;

import android.app.Activity;
import android.widget.CalendarView;
import android.widget.LinearLayout;

import com.couplefinance.core.ui.DS;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class DateDialog {

    private DateDialog() {
    }

    public interface OnDateSelected {
        void onSelected(long dateMs);
    }

    public static void show(Activity activity,
                            long initialDate,
                            OnDateSelected callback) {

        // Utilise la date passée ou aujourd'hui à midi si nulle/invalide
        long safeDate = initialDate > 0 ? initialDate : todayMidday();

        CalendarView calendarView = new CalendarView(activity);
        calendarView.setDate(safeDate);
        calendarView.setFirstDayOfWeek(Calendar.MONDAY);

        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        int p = DS.dp(activity, 6);
        wrapper.setPadding(p, p, p, p);
        wrapper.addView(calendarView);

        // selected[0] est pré-rempli avec safeDate :
        // si l'utilisateur confirme sans changer de jour,
        // c'est bien la date initiale qui est renvoyée.
        final long[] selected = {safeDate};

        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            Calendar c = Calendar.getInstance();
            c.set(year, month, dayOfMonth, 12, 0, 0);
            c.set(Calendar.MILLISECOND, 0);
            selected[0] = c.getTimeInMillis();
        });

        String subtitle = "Date actuelle : " + format(safeDate);

        PremiumDialog.builder(activity)
                .icon("📅")
                .title("Choisir une date")
                .subtitle(subtitle)
                .content(wrapper)
                .primary("CONFIRMER", () -> {
                    if (callback != null) {
                        callback.onSelected(selected[0]);
                    }
                })
                .show();
    }

    public static String format(long dateMs) {
        try {
            return new SimpleDateFormat("dd MMMM yyyy", Locale.FRANCE)
                    .format(new Date(dateMs));
        } catch (Exception e) {
            return "";
        }
    }

    public static String formatShort(long dateMs) {
        try {
            return new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
                    .format(new Date(dateMs));
        } catch (Exception e) {
            return "";
        }
    }

    public static long todayMidday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 12);
        c.set(Calendar.MINUTE,      0);
        c.set(Calendar.SECOND,      0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
