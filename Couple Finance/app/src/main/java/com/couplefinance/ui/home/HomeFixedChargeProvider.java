package com.couplefinance.ui.home;

import com.couplefinance.data.FixedChargeManager;
import com.couplefinance.data.FirestoreManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;

public class HomeFixedChargeProvider {

    public interface Callback {
        void onLoaded(ArrayList<String[]> plannedCharges);
        void onError(String error);
    }

    public static void loadPlannedChargesForCurrentMonth(Callback cb) {
        FixedChargeManager.getInstance().getFixedCharges(new FirestoreManager.Callback() {
            public void onSuccess(String response) {
                try {
                    ArrayList<String[]> result = new ArrayList<>();

                    JSONArray docs = new JSONObject(response).optJSONArray("documents");

                    if (docs != null) {
                        Calendar now = Calendar.getInstance();

                        for (int i = 0; i < docs.length(); i++) {
                            JSONObject doc = docs.optJSONObject(i);
                            if (doc == null) continue;

                            JSONObject fields = doc.optJSONObject("fields");
                            if (fields == null) continue;

                            String docPath = doc.optString("name", "");
                            String name = str(fields, "name");
                            double amount = number(fields, "amount");
                            String category = str(fields, "category");
                            String lastAppliedMonth = str(fields, "lastAppliedMonth");
                            int dayOfMonth = intVal(fields, "dayOfMonth", 1);

                            if (name.isEmpty() || amount <= 0) continue;

                            int safeDay = Math.max(1, Math.min(28, dayOfMonth));

                            Calendar date = Calendar.getInstance();
                            date.set(Calendar.YEAR, now.get(Calendar.YEAR));
                            date.set(Calendar.MONTH, now.get(Calendar.MONTH));
                            date.set(Calendar.DAY_OF_MONTH, safeDay);
                            date.set(Calendar.HOUR_OF_DAY, 9);
                            date.set(Calendar.MINUTE, 0);
                            date.set(Calendar.SECOND, 0);
                            date.set(Calendar.MILLISECOND, 0);

                            String currentMonth =
                                    now.get(Calendar.YEAR)
                                            + "-"
                                            + String.format("%02d", now.get(Calendar.MONTH) + 1);

                            boolean alreadyApplied = currentMonth.equals(lastAppliedMonth);

                            result.add(new String[]{
                                    name,
                                    String.valueOf(amount),
                                    alreadyApplied ? "fixed_done" : "fixed_planned",
                                    category == null || category.trim().isEmpty() ? "Charges fixes" : category,
                                    String.valueOf(date.getTimeInMillis()),
                                    "true",
                                    docPath
                            });
                        }
                    }

                    if (cb != null) cb.onLoaded(result);

                } catch (Exception e) {
                    if (cb != null) cb.onError(e.getMessage());
                }
            }

            public void onError(String error) {
                if (cb != null) cb.onError(error);
            }
        });
    }

    private static String str(JSONObject fields, String key) {
        try {
            JSONObject f = fields.optJSONObject(key);
            if (f == null) return "";
            return f.optString("stringValue", "").trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static double number(JSONObject fields, String key) {
        try {
            JSONObject f = fields.optJSONObject(key);
            if (f == null) return 0;

            if (f.has("doubleValue")) {
                return f.optDouble("doubleValue", 0);
            }

            if (f.has("integerValue")) {
                return Double.parseDouble(f.optString("integerValue", "0"));
            }

            return 0;

        } catch (Exception e) {
            return 0;
        }
    }

    private static int intVal(JSONObject fields, String key, int fallback) {
        try {
            JSONObject f = fields.optJSONObject(key);
            if (f == null) return fallback;

            if (f.has("integerValue")) {
                return Integer.parseInt(f.optString("integerValue", String.valueOf(fallback)));
            }

            if (f.has("doubleValue")) {
                return (int) f.optDouble("doubleValue", fallback);
            }

            return fallback;

        } catch (Exception e) {
            return fallback;
        }
    }
}