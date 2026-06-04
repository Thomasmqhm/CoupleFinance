package com.couplefinance.ui.settings;

import android.app.Activity;

import com.couplefinance.data.FirestoreManager;
import com.couplefinance.data.HouseholdManager;
import com.couplefinance.data.SettingsManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

public class SettingsMaintenance {

    public interface CleanupCallback {
        void onDone(int deletedCount);
        void onError(String error);
    }

    public static void cleanDuplicateMembers(Activity activity, CleanupCallback cb) {
        HouseholdManager.getInstance().getMembers(new FirestoreManager.Callback() {
            public void onSuccess(String response) {
                try {
                    ArrayList<String> duplicates = findDuplicateMemberPaths(response);

                    if (duplicates.isEmpty()) {
                        if (cb != null) cb.onDone(0);
                        return;
                    }

                    deleteNext(duplicates, 0, 0, cb);

                } catch (Exception e) {
                    if (cb != null) cb.onError(e.getMessage());
                }
            }

            public void onError(String error) {
                if (cb != null) cb.onError(error);
            }
        });
    }

    private static ArrayList<String> findDuplicateMemberPaths(String response) throws Exception {
        ArrayList<String> duplicates = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();

        JSONObject json = new JSONObject(response);
        JSONArray docs = json.optJSONArray("documents");

        if (docs == null) return duplicates;

        for (int i = 0; i < docs.length(); i++) {
            JSONObject doc = docs.optJSONObject(i);
            if (doc == null) continue;

            String fullPath = doc.optString("name", "");
            JSONObject fields = doc.optJSONObject("fields");
            if (fields == null || fullPath.isEmpty()) continue;

            String userId = str(fields, "userId");
            String name = firstNonEmpty(
                    str(fields, "name"),
                    str(fields, "displayName"),
                    str(fields, "prenom"),
                    str(fields, "firstName")
            );

            String key;

            if (userId != null && !userId.trim().isEmpty()) {
                key = "uid:" + normalize(userId);
            } else {
                key = "name:" + normalize(name);
            }

            if (key.equals("name:") || key.equals("uid:")) continue;

            if (seen.contains(key)) {
                duplicates.add(fullPath);
            } else {
                seen.add(key);
            }
        }

        return duplicates;
    }

    private static void deleteNext(
            ArrayList<String> paths,
            int index,
            int deleted,
            CleanupCallback cb
    ) {
        if (index >= paths.size()) {
            if (cb != null) cb.onDone(deleted);
            return;
        }

        String path = paths.get(index);

        SettingsManager.getInstance().deletePerson(path, new FirestoreManager.Callback() {
            public void onSuccess(String response) {
                deleteNext(paths, index + 1, deleted + 1, cb);
            }

            public void onError(String error) {
                deleteNext(paths, index + 1, deleted, cb);
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

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";

        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        }

        return "";
    }

    private static String normalize(String value) {
        if (value == null) return "";

        return value
                .trim()
                .toLowerCase(Locale.FRANCE)
                .replace("é", "e")
                .replace("è", "e")
                .replace("ê", "e")
                .replace("ë", "e")
                .replace("à", "a")
                .replace("â", "a")
                .replace("ù", "u")
                .replace("û", "u")
                .replace("î", "i")
                .replace("ï", "i")
                .replace("ô", "o")
                .replace("ç", "c")
                .replaceAll("\\s+", " ");
    }
}