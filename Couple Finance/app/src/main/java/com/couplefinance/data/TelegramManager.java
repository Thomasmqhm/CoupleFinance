package com.couplefinance.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Envoi de messages Telegram depuis l'application (REST, HttpURLConnection, sans SDK).
 *
 * Usage côté Thomas :
 *  - Créer un bot via @BotFather → récupérer le token.
 *  - Mélissa envoie un message au bot (ex. /start).
 *  - fetchLatestChatId() récupère son chat_id automatiquement.
 *  - sendMessage(...) pousse un message (HTML).
 *
 * Le token + le chat_id sont stockés localement (SharedPreferences).
 */
public class TelegramManager {

    private static final String PREFS = "telegram_prefs";
    private static final String K_TOKEN = "bot_token";
    private static final String K_CHAT = "chat_id";
    private static final String API = "https://api.telegram.org/bot";

    private static TelegramManager instance;

    private Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(String response);
        void onError(String error);
    }

    public static synchronized TelegramManager getInstance() {
        if (instance == null) instance = new TelegramManager();
        return instance;
    }

    public void init(Context ctx) {
        if (ctx != null) context = ctx.getApplicationContext();
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ─── Configuration ─────────────────────────────────────────────

    public void setBotToken(String token) {
        if (context == null || token == null) return;
        prefs().edit().putString(K_TOKEN, token.trim()).apply();
    }

    public String getBotToken() {
        return context == null ? "" : prefs().getString(K_TOKEN, "");
    }

    public void setChatId(String chatId) {
        if (context == null || chatId == null) return;
        prefs().edit().putString(K_CHAT, chatId.trim()).apply();
    }

    public String getChatId() {
        return context == null ? "" : prefs().getString(K_CHAT, "");
    }

    public boolean isConfigured() {
        return !getBotToken().isEmpty() && !getChatId().isEmpty();
    }

    public void clear() {
        if (context != null) prefs().edit().clear().apply();
    }

    // ─── Envoi d'un message ────────────────────────────────────────

    /** Envoie au chat_id configuré. Le texte accepte du HTML simple (&lt;b&gt;, &lt;i&gt;...). */
    public void sendMessage(String text, Callback cb) {
        sendMessageTo(getChatId(), text, cb);
    }

    public void sendMessageTo(String chatId, String text, Callback cb) {
        final String token = getBotToken();
        if (token.isEmpty() || chatId == null || chatId.isEmpty()) {
            post(cb, false, "Telegram non configuré (token ou chat_id manquant)");
            return;
        }
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(API + token + "/sendMessage");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(12000);
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("chat_id", chatId);
                body.put("text", text != null ? text : "");
                body.put("parse_mode", "HTML");
                body.put("disable_web_page_preview", true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    post(cb, true, read(conn.getInputStream()));
                } else {
                    String err = read(conn.getErrorStream());
                    post(cb, false, "Erreur Telegram (" + code + ") : " + describe(err));
                }
            } catch (Exception e) {
                post(cb, false, "Échec d'envoi : " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    // ─── Récupération automatique du chat_id ───────────────────────

    /**
     * Appelle getUpdates et renvoie le chat_id du dernier message reçu par le bot.
     * Mélissa doit avoir envoyé au moins un message au bot au préalable.
     */
    public void fetchLatestChatId(Callback cb) {
        final String token = getBotToken();
        if (token.isEmpty()) {
            post(cb, false, "Renseigne d'abord le token du bot");
            return;
        }
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(API + token + "/getUpdates");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(12000);

                int code = conn.getResponseCode();
                String resp = read(code == 200 ? conn.getInputStream() : conn.getErrorStream());
                if (code != 200) {
                    post(cb, false, "Erreur Telegram (" + code + ") : " + describe(resp));
                    return;
                }

                JSONObject root = new JSONObject(resp);
                JSONArray result = root.optJSONArray("result");
                if (result == null || result.length() == 0) {
                    post(cb, false, "Aucun message reçu. Demande à ta partenaire d'écrire au bot (ex. /start), puis réessaie.");
                    return;
                }

                String chatId = null;
                String chatName = null;
                // on prend le message le plus récent
                for (int i = result.length() - 1; i >= 0; i--) {
                    JSONObject upd = result.optJSONObject(i);
                    if (upd == null) continue;
                    JSONObject msg = upd.optJSONObject("message");
                    if (msg == null) msg = upd.optJSONObject("edited_message");
                    if (msg == null) continue;
                    JSONObject chat = msg.optJSONObject("chat");
                    if (chat == null) continue;
                    chatId = String.valueOf(chat.opt("id"));
                    chatName = chat.optString("first_name", chat.optString("title", ""));
                    break;
                }

                if (chatId == null || chatId.isEmpty() || "null".equals(chatId)) {
                    post(cb, false, "Impossible de lire le chat_id. Réessaie après un nouveau message au bot.");
                    return;
                }

                setChatId(chatId);
                final String found = chatId + (chatName != null && !chatName.isEmpty() ? " (" + chatName + ")" : "");
                post(cb, true, found);
            } catch (Exception e) {
                post(cb, false, "Échec : " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    public void sendTest(Callback cb) {
        sendMessage("\u2705 <b>CoupleFinance</b> est bien connecté \u00e0 Telegram !", cb);
    }

    // ─── Helpers ───────────────────────────────────────────────────

    private String describe(String json) {
        if (json == null || json.isEmpty()) return "réponse vide";
        try {
            JSONObject o = new JSONObject(json);
            String d = o.optString("description", "");
            if (!d.isEmpty()) return d;
        } catch (Exception ignored) {
        }
        return json.length() > 160 ? json.substring(0, 160) : json;
    }

    private String read(InputStream is) {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        } catch (Exception ignored) {
        }
        return sb.toString();
    }

    private void post(Callback cb, boolean ok, String msg) {
        if (cb == null) return;
        handler.post(() -> {
            if (ok) cb.onSuccess(msg);
            else cb.onError(msg);
        });
    }
}
