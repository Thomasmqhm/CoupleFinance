package com.couplefinance;

import android.os.Handler;
import android.os.Looper;

import com.couplefinance.models.UserProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class UserRepository {

    private static volatile UserRepository instance;

    private static final String PROJECT_ID = "couple-bacc7";
    private static final String BASE_URL = "https://firestore.googleapis.com/v1/projects/"
            + PROJECT_ID + "/databases/(default)/documents/";

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    public static UserRepository getInstance() {
        if (instance == null) {
            synchronized (UserRepository.class) {
                if (instance == null) instance = new UserRepository();
            }
        }
        return instance;
    }

    public interface OnUserLoaded {
        void onLoaded(UserProfile user);
    }

    public void saveUser(UserProfile user) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String token = AuthManager.getInstance().getToken();
                if (token == null || token.isEmpty()) return;

                // Inclure householdId si disponible — permet aux requêtes d'avatar de fonctionner
                String householdId = com.couplefinance.data.HouseholdManager.getInstance().getHouseholdId();
                boolean hasHousehold = householdId != null && !householdId.isEmpty();

                String maskUrl = BASE_URL + "users/" + user.uid
                        + "?updateMask.fieldPaths=displayName"
                        + "&updateMask.fieldPaths=email"
                        + "&updateMask.fieldPaths=color"
                        + "&updateMask.fieldPaths=avatar"
                        + "&updateMask.fieldPaths=createdAt"
                        + (hasHousehold ? "&updateMask.fieldPaths=householdId" : "");

                URL url = new URL(maskUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PATCH");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);

                // JSONObject pour éviter l'injection JSON
                JSONObject fields = new JSONObject();
                fields.put("displayName", sv(user.displayName));
                fields.put("email", sv(user.email));
                fields.put("color", sv(user.getColor()));
                fields.put("avatar", sv(user.getAvatar()));
                fields.put("createdAt", intv(user.createdAt));
                if (hasHousehold) fields.put("householdId", sv(householdId));
                String body = "{\"fields\":" + fields + "}";

                try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
                    dos.write(body.getBytes("UTF-8"));
                }
                conn.getResponseCode(); // fire & forget
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    public void loadUser(String uid, OnUserLoaded callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String token = AuthManager.getInstance().getToken();
                if (token == null || token.isEmpty()) return;

                URL url = new URL(BASE_URL + "users/" + uid);
                conn = (HttpURLConnection) url.openConnection();
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
                        AuthManager.getInstance().setDisplayName(displayName);
                        if (callback != null) handler.post(() -> callback.onLoaded(profile));
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    public interface OnAvatarsLoaded {
        void onLoaded(Map<String, String> avatarByName);
    }

    public void loadHouseholdAvatars(String householdId, OnAvatarsLoaded callback) {
        executor.execute(() -> {
            Map<String, String> map = new HashMap<>();
            HttpURLConnection conn = null;
            try {
                String token = AuthManager.getInstance().getToken();
                if (token == null || token.isEmpty() || householdId == null || householdId.isEmpty()) {
                    handler.post(() -> callback.onLoaded(map));
                    return;
                }

                URL url = new URL("https://firestore.googleapis.com/v1/projects/"
                        + PROJECT_ID + "/databases/(default)/documents:runQuery");
                conn = (HttpURLConnection) url.openConnection();
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

                try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
                    dos.write(body.getBytes("UTF-8"));
                }

                if (conn.getResponseCode() == 200) {
                    String response = safeRead(conn.getInputStream());
                    try {
                        JSONArray arr = new JSONArray(response);
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject el = arr.optJSONObject(i);
                            if (el == null || !el.has("document")) continue;
                            JSONObject fields = el.getJSONObject("document").optJSONObject("fields");
                            if (fields == null) continue;
                            String name = svField(fields, "displayName");
                            String avatar = svField(fields, "avatar");
                            if (!name.isEmpty() && !avatar.isEmpty()) map.put(name, avatar);
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
            handler.post(() -> callback.onLoaded(map));
        });
    }

    private String extractStr(String json, String key) {
        for (String search : new String[]{
                "\"" + key + "\":{\"stringValue\":\"",
                "\"" + key + "\": {\"stringValue\": \""}) {
            int i = json.indexOf(search);
            if (i >= 0) {
                i += search.length();
                int e = json.indexOf("\"", i);
                if (e > i) return json.substring(i, e);
            }
        }
        return "";
    }

    private String safeRead(InputStream is) {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private static String svField(JSONObject fields, String key) {
        JSONObject o = fields.optJSONObject(key);
        return o != null ? o.optString("stringValue", "") : "";
    }

    private static JSONObject sv(String value) throws Exception {
        JSONObject o = new JSONObject();
        o.put("stringValue", value != null ? value : "");
        return o;
    }

    private static JSONObject intv(long value) throws Exception {
        JSONObject o = new JSONObject();
        o.put("integerValue", value);
        return o;
    }
}
