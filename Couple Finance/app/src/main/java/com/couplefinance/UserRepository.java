package com.couplefinance;

import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import com.couplefinance.models.UserProfile;

public class UserRepository {

    private static UserRepository instance;
    private static final String PROJECT_ID = "couple-bacc7";
    private static final String BASE_URL = "https://firestore.googleapis.com/v1/projects/"
            + PROJECT_ID + "/databases/(default)/documents/";
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    public static UserRepository getInstance() {
        if (instance == null) instance = new UserRepository();
        return instance;
    }

    public interface OnUserLoaded {
        void onLoaded(UserProfile user);
    }

    // Sauvegarde le profil dans Firestore → users/{uid}
    public void saveUser(UserProfile user) {
        executor.execute(() -> {
            try {
                String token = AuthManager.getInstance().getToken();
                if (token == null || token.isEmpty()) return;
                URL url = new URL(BASE_URL + "users/" + user.uid
                        + "?updateMask.fieldPaths=displayName"
                        + "&updateMask.fieldPaths=email"
                        + "&updateMask.fieldPaths=color"
                        + "&updateMask.fieldPaths=avatar"
                        + "&updateMask.fieldPaths=createdAt");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PATCH");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);
                String body = "{\"fields\":{"
                        + "\"displayName\":{\"stringValue\":\"" + user.displayName + "\"},"
                        + "\"email\":{\"stringValue\":\"" + user.email + "\"},"
                        + "\"color\":{\"stringValue\":\"" + user.getColor() + "\"},"
                        + "\"avatar\":{\"stringValue\":\"" + user.getAvatar() + "\"},"
                        + "\"createdAt\":{\"integerValue\":" + user.createdAt + "}"
                        + "}}";
                conn.getOutputStream().write(body.getBytes());
                conn.getResponseCode(); // fire & forget
                conn.disconnect();
            } catch (Exception ignored) {}
        });
    }

    // Lit le profil depuis Firestore et le met dans UserSession
    public void loadUser(String uid, OnUserLoaded callback) {
        executor.execute(() -> {
            try {
                String token = AuthManager.getInstance().getToken();
                if (token == null || token.isEmpty()) return;
                URL url = new URL(BASE_URL + "users/" + uid);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                if (conn.getResponseCode() == 200) {
                    String response = safeRead(conn.getInputStream());
                    String displayName = extractStr(response, "displayName");
                    String email = extractStr(response, "email");
                    String color = extractStr(response, "color");
                    String avatar = extractStr(response, "avatar");
                    if (!displayName.isEmpty()) {
                        UserProfile profile = new UserProfile(uid, displayName, email, 0);
                        if (!color.isEmpty()) profile.color = color;
                        if (!avatar.isEmpty()) profile.avatar = avatar;
                        UserSession.getInstance().setUser(profile);
                        if (!avatar.isEmpty()) AuthManager.getInstance().setLocalAvatar(avatar);
                        // Synchroniser aussi dans AuthManager pour les fallbacks
                        AuthManager.getInstance().setDisplayName(displayName);
                        if (callback != null)
                            handler.post(() -> callback.onLoaded(profile));
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) {}
        });
    }

    private String extractStr(String json, String key) {
        String search = "\"" + key + "\":{\"stringValue\":\"";
        int i = json.indexOf(search);
        if (i < 0) {
            // essai avec espace
            search = "\"" + key + "\": {\"stringValue\": \"";
            i = json.indexOf(search);
        }
        if (i < 0) return "";
        i += search.length();
        int e = json.indexOf("\"", i);
        return e > i ? json.substring(i, e) : "";
    }

    private String safeRead(InputStream is) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    // ─── Avatars de tous les membres du foyer (query users par householdId) ───
    public interface OnAvatarsLoaded {
        void onLoaded(java.util.Map<String, String> avatarByName);
    }

    public void loadHouseholdAvatars(String householdId, OnAvatarsLoaded callback) {
        executor.execute(() -> {
            java.util.Map<String, String> map = new java.util.HashMap<>();
            try {
                String token = AuthManager.getInstance().getToken();
                if (token == null || token.isEmpty() || householdId == null || householdId.isEmpty()) {
                    handler.post(() -> callback.onLoaded(map));
                    return;
                }
                URL url = new URL("https://firestore.googleapis.com/v1/projects/"
                        + PROJECT_ID + "/databases/(default)/documents:runQuery");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);
                String body = "{\"structuredQuery\":{"
                        + "\"from\":[{\"collectionId\":\"users\"}],"
                        + "\"where\":{\"fieldFilter\":{"
                        + "\"field\":{\"fieldPath\":\"householdId\"},"
                        + "\"op\":\"EQUAL\","
                        + "\"value\":{\"stringValue\":\"" + householdId + "\"}}}"
                        + "}}";
                conn.getOutputStream().write(body.getBytes("UTF-8"));
                if (conn.getResponseCode() == 200) {
                    String response = safeRead(conn.getInputStream());
                    try {
                        org.json.JSONArray arr = new org.json.JSONArray(response);
                        for (int i = 0; i < arr.length(); i++) {
                            org.json.JSONObject el = arr.optJSONObject(i);
                            if (el == null || !el.has("document")) continue;
                            org.json.JSONObject fields = el.getJSONObject("document").optJSONObject("fields");
                            if (fields == null) continue;
                            String name = sv(fields, "displayName");
                            String avatar = sv(fields, "avatar");
                            if (!name.isEmpty() && !avatar.isEmpty()) map.put(name, avatar);
                        }
                    } catch (Exception ignored) {
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) {
            }
            handler.post(() -> callback.onLoaded(map));
        });
    }

    private static String sv(org.json.JSONObject fields, String key) {
        org.json.JSONObject o = fields.optJSONObject(key);
        return o != null ? o.optString("stringValue", "") : "";
    }
}