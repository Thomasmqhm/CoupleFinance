package com.couplefinance.workers;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.couplefinance.data.TelegramCommandHandler;
import com.couplefinance.data.TelegramManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * WorkManager periodic worker that polls Telegram getUpdates every ~15 minutes.
 * Handles incoming commands and inline keyboard callbacks.
 * Security: only messages from the configured chat_id are processed.
 */
public class TelegramPollingWorker extends Worker {

    private static final String PREFS           = "telegram_polling_prefs";
    private static final String K_LAST_UPDATE   = "last_update_id";
    private static final String API             = "https://api.telegram.org/bot";

    public TelegramPollingWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context app = getApplicationContext();
        TelegramManager tm = TelegramManager.getInstance();
        tm.init(app);

        if (!tm.isConfigured()) return Result.success(); // nothing to do

        String token       = tm.getBotToken();
        String configuredChat = tm.getChatId();
        long   offset      = prefs(app).getLong(K_LAST_UPDATE, 0);

        try {
            String url = API + token + "/getUpdates?offset=" + (offset + 1) + "&timeout=0&limit=50";
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int code = conn.getResponseCode();
            if (code != 200) { conn.disconnect(); return Result.retry(); }

            String body = readAll(conn);
            conn.disconnect();

            JSONObject root   = new JSONObject(body);
            JSONArray  result = root.optJSONArray("result");
            if (result == null || result.length() == 0) return Result.success();

            long maxUpdateId = offset;

            for (int i = 0; i < result.length(); i++) {
                JSONObject update = result.optJSONObject(i);
                if (update == null) continue;

                long updateId = update.optLong("update_id", 0);
                if (updateId > maxUpdateId) maxUpdateId = updateId;

                // ── Message text command ────────────────────────────────
                JSONObject msg = update.optJSONObject("message");
                if (msg != null) {
                    String fromChatId = chatIdOf(msg);
                    if (!configuredChat.equals(fromChatId)) {
                        // Security: ignore messages from unknown senders
                        tm.sendMessageTo(fromChatId, TelegramCommandHandler.REPLY_UNAUTHORIZED, null);
                        continue;
                    }
                    String text = msg.optString("text", "");
                    if (!text.isEmpty()) {
                        String reply = TelegramCommandHandler.handle(app, text);
                        if (reply != null && !reply.isEmpty()) {
                            tm.sendMessage(reply, null);
                        }
                    }
                    continue;
                }

                // ── Inline keyboard callback_query ──────────────────────
                JSONObject cbq = update.optJSONObject("callback_query");
                if (cbq != null) {
                    String cbChatId = chatIdOf(cbq.optJSONObject("message"));
                    if (cbChatId == null) cbChatId = chatIdOf(cbq.optJSONObject("from"));
                    if (!configuredChat.equals(cbChatId)) continue;

                    String cbId   = cbq.optString("id", "");
                    String cbData = cbq.optString("data", "");
                    String toast  = TelegramCommandHandler.handleCallback(app, cbData);
                    tm.answerCallbackQuery(cbId, toast);
                }
            }

            // Persist highest seen update_id so we don't reprocess
            if (maxUpdateId > offset) {
                prefs(app).edit().putLong(K_LAST_UPDATE, maxUpdateId).apply();
            }

            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────

    private static String chatIdOf(JSONObject msgOrChat) {
        if (msgOrChat == null) return "";
        JSONObject chat = msgOrChat.optJSONObject("chat");
        if (chat != null) return String.valueOf(chat.opt("id"));
        // fallback: msgOrChat IS a "from" user object
        Object id = msgOrChat.opt("id");
        return id != null ? String.valueOf(id) : "";
    }

    private static String readAll(HttpURLConnection conn) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
