package com.couplefinance.ui.settings;

import android.app.Activity;
import android.os.Environment;

import com.couplefinance.AuthManager;
import com.couplefinance.data.FirestoreManager;
import com.couplefinance.data.HouseholdManager;
import com.couplefinance.data.TransactionManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * SettingsDataExportManager — exports locaux de CoupleFinance.
 *
 * Ne modifie jamais Firestore.
 * Crée les fichiers dans le dossier Documents privé de l'application.
 */
public final class SettingsDataExportManager {

    public interface ExportCallback {
        void onSuccess(File file);
        void onError(String error);
    }

    private SettingsDataExportManager() {
    }

    public static void exportTransactionsCsv(Activity activity, ExportCallback callback) {
        TransactionManager.getInstance().getTransactions(new FirestoreManager.Callback() {
            public void onSuccess(String response) {
                try {
                    File file = createFile(activity, "transactions", "csv");
                    String csv = buildTransactionsCsv(response);
                    writeUtf8(file, csv);
                    if (callback != null) callback.onSuccess(file);
                } catch (Exception e) {
                    if (callback != null) callback.onError(e.getMessage());
                }
            }

            public void onError(String error) {
                if (callback != null) callback.onError(error);
            }
        });
    }

    public static void exportSettingsSummary(Activity activity, ExportCallback callback) {
        try {
            File file = createFile(activity, "resume_parametres", "csv");
            String csv = buildSettingsSummaryCsv(activity);
            writeUtf8(file, csv);
            if (callback != null) callback.onSuccess(file);
        } catch (Exception e) {
            if (callback != null) callback.onError(e.getMessage());
        }
    }

    public static File createFile(Activity activity, String prefix, String extension) {
        File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) dir = activity.getFilesDir();
        if (!dir.exists()) dir.mkdirs();

        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE)
                .format(new Date());

        return new File(dir, "couplefinance_" + prefix + "_" + stamp + "." + extension);
    }

    private static String buildTransactionsCsv(String response) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("date;label;montant;type;categorie;membre;partage;compte\n");

        if (response == null || response.trim().isEmpty()) {
            return sb.toString();
        }

        JSONObject root = new JSONObject(response);
        JSONArray docs = root.optJSONArray("documents");

        if (docs == null) {
            return sb.toString();
        }

        for (int i = 0; i < docs.length(); i++) {
            JSONObject doc = docs.optJSONObject(i);
            if (doc == null) continue;

            JSONObject fields = doc.optJSONObject("fields");
            if (fields == null) continue;

            long date = numberLong(fields, "date");
            String dateLabel = date > 0
                    ? new SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(new Date(date))
                    : "";

            sb.append(csv(dateLabel)).append(';')
                    .append(csv(str(fields, "label"))).append(';')
                    .append(csv(formatNumber(numberDouble(fields, "amount")))).append(';')
                    .append(csv(str(fields, "type"))).append(';')
                    .append(csv(str(fields, "category"))).append(';')
                    .append(csv(str(fields, "person"))).append(';')
                    .append(csv(String.valueOf(bool(fields, "shared")))).append(';')
                    .append(csv(str(fields, "compte")))
                    .append('\n');
        }

        return sb.toString();
    }

    private static String buildSettingsSummaryCsv(Activity activity) {
        StringBuilder sb = new StringBuilder();
        SettingsModels.State state = SettingsCache.get();

        sb.append("cle;valeur\n");
        sb.append(csv("date_export")).append(';')
                .append(csv(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.FRANCE).format(new Date())))
                .append('\n');
        sb.append(csv("user_id")).append(';').append(csv(safe(AuthManager.getInstance().getUserId()))).append('\n');
        sb.append(csv("email")).append(';').append(csv(safeGetEmail())).append('\n');
        sb.append(csv("household_id")).append(';').append(csv(safe(HouseholdManager.getInstance().getHouseholdId()))).append('\n');

        if (state != null) {
            sb.append(csv("foyer_nom")).append(';').append(csv(safe(state.householdName))).append('\n');
            sb.append(csv("description")).append(';').append(csv(safe(state.description))).append('\n');
            sb.append(csv("membres")).append(';').append(csv(String.valueOf(state.memberCount()))).append('\n');
            sb.append(csv("categories")).append(';').append(csv(String.valueOf(state.categories != null ? state.categories.size() : 0))).append('\n');
            sb.append(csv("charges_fixes_total")).append(';').append(csv(formatNumber(state.totalCharges()))).append('\n');
        }

        android.content.SharedPreferences settings = activity.getSharedPreferences("couplefinance_settings", Activity.MODE_PRIVATE);
        android.content.SharedPreferences theme = activity.getSharedPreferences("couplefinance_theme", Activity.MODE_PRIVATE);

        sb.append(csv("langue")).append(';').append(csv(settings.getString("language", "fr"))).append('\n');
        sb.append(csv("devise")).append(';').append(csv(settings.getString("currency", "EUR"))).append('\n');
        sb.append(csv("theme_sombre")).append(';').append(csv(String.valueOf(theme.getBoolean("dark_mode", false)))).append('\n');
        sb.append(csv("derniere_sync")).append(';').append(csv(settings.getString("last_sync_label", "Jamais"))).append('\n');

        return sb.toString();
    }

    private static void writeUtf8(File file, String content) throws Exception {
        FileOutputStream fos = new FileOutputStream(file);
        fos.write("\uFEFF".getBytes("UTF-8"));
        fos.write((content == null ? "" : content).getBytes("UTF-8"));
        fos.flush();
        fos.close();
    }

    private static String str(JSONObject fields, String key) {
        JSONObject obj = fields.optJSONObject(key);
        if (obj == null) return "";
        return obj.optString("stringValue", "");
    }

    private static double numberDouble(JSONObject fields, String key) {
        JSONObject obj = fields.optJSONObject(key);
        if (obj == null) return 0;
        if (obj.has("doubleValue")) return obj.optDouble("doubleValue", 0);
        if (obj.has("integerValue")) {
            try { return Double.parseDouble(obj.optString("integerValue", "0")); }
            catch (Exception ignored) { return 0; }
        }
        return 0;
    }

    private static long numberLong(JSONObject fields, String key) {
        JSONObject obj = fields.optJSONObject(key);
        if (obj == null) return 0;
        if (obj.has("integerValue")) {
            try { return Long.parseLong(obj.optString("integerValue", "0")); }
            catch (Exception ignored) { return 0; }
        }
        if (obj.has("doubleValue")) return (long) obj.optDouble("doubleValue", 0);
        return 0;
    }

    private static boolean bool(JSONObject fields, String key) {
        JSONObject obj = fields.optJSONObject(key);
        return obj != null && obj.optBoolean("booleanValue", false);
    }

    private static String csv(String value) {
        if (value == null) value = "";
        value = value.replace("\r", " ").replace("\n", " ").replace("\"", "\"\"");
        return "\"" + value + "\"";
    }

    private static String formatNumber(double value) {
        return String.format(Locale.FRANCE, "%.2f", value);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String safeGetEmail() {
        try {
            String email = AuthManager.getInstance().getEmail();
            return email == null ? "" : email;
        } catch (Exception e) {
            return "";
        }
    }
}
