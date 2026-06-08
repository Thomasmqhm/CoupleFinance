package com.couplefinance.data;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Polls Telegram getUpdates every ~15 minutes via AlarmManager.
 * Replaces TelegramPollingWorker (WorkManager removed — CodeAssist can't unpack .aar).
 */
public class TelegramPollingReceiver extends BroadcastReceiver {

    private static final String ACTION_POLL   = "com.couplefinance.TELEGRAM_POLL";
    private static final int    REQUEST_CODE  = 5001;
    private static final String PREFS         = "telegram_polling_prefs";
    private static final String K_LAST_UPDATE = "last_update_id";
    private static final String API           = "https://api.telegram.org/bot";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (ctx == null || intent == null) return;
        String action = intent.getAction();

        if ("android.intent.action.BOOT_COMPLETED".equals(action)) {
            TelegramManager.getInstance().init(ctx);
            if (TelegramManager.getInstance().isConfigured()) schedule(ctx);
            return;
        }

        if (!ACTION_POLL.equals(action)) return;

        final PendingResult pr = goAsync();
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try { doPoll(app); }
            finally { pr.finish(); }
        }).start();
    }

    // ─── Scheduling ──────────────────────────────────────────────

    public static void schedule(Context ctx) {
        if (ctx == null) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(ctx, TelegramPollingReceiver.class);
        intent.setAction(ACTION_POLL);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // First poll in 30 s, then every 15 min
        long firstAt = System.currentTimeMillis() + 30_000L;
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, firstAt,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES, pi);
    }

    public static void cancel(Context ctx) {
        if (ctx == null) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(ctx, TelegramPollingReceiver.class);
        intent.setAction(ACTION_POLL);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, REQUEST_CODE, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) { am.cancel(pi); pi.cancel(); }
    }

    // ─── Poll logic (ported from TelegramPollingWorker) ──────────

    static void doPoll(Context app) {
        TelegramManager tm = TelegramManager.getInstance();
        tm.init(app);
        if (!tm.isConfigured()) return;

        String token        = tm.getBotToken();
        String configuredChat = tm.getChatId();
        long   offset       = prefs(app).getLong(K_LAST_UPDATE, 0);

        try {
            String url = API + token + "/getUpdates?offset=" + (offset + 1) + "&timeout=0&limit=50";
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int code = conn.getResponseCode();
            if (code != 200) { conn.disconnect(); return; }

            String body = readAll(conn);
            conn.disconnect();

            JSONObject root   = new JSONObject(body);
            JSONArray  result = root.optJSONArray("result");
            if (result == null || result.length() == 0) return;

            long maxUpdateId = offset;

            for (int i = 0; i < result.length(); i++) {
                JSONObject update = result.optJSONObject(i);
                if (update == null) continue;

                long updateId = update.optLong("update_id", 0);
                if (updateId > maxUpdateId) maxUpdateId = updateId;

                JSONObject msg = update.optJSONObject("message");
                if (msg != null) {
                    String fromChatId = chatIdOf(msg);
                    if (!configuredChat.equals(fromChatId)) {
                        tm.sendMessageTo(fromChatId, TelegramCommandHandler.REPLY_UNAUTHORIZED, null);
                        continue;
                    }
                    String text = msg.optString("text", "");
                    if (!text.isEmpty()) {
                        String reply = TelegramCommandHandler.handle(app, text);
                        if (reply != null && !reply.isEmpty()) tm.sendMessage(reply, null);
                    }
                    continue;
                }

                JSONObject cbq = update.optJSONObject("callback_query");
                if (cbq != null) {
                    String cbChatId = chatIdOf(cbq.optJSONObject("message"));
                    if (cbChatId == null || cbChatId.isEmpty())
                        cbChatId = chatIdOf(cbq.optJSONObject("from"));
                    if (!configuredChat.equals(cbChatId)) continue;

                    String cbId   = cbq.optString("id", "");
                    String cbData = cbq.optString("data", "");
                    String toast  = TelegramCommandHandler.handleCallback(app, cbData);
                    tm.answerCallbackQuery(cbId, toast);
                }
            }

            if (maxUpdateId > offset) {
                prefs(app).edit().putLong(K_LAST_UPDATE, maxUpdateId).apply();
            }
        } catch (Exception ignored) {}
    }

    // ─── Helpers ─────────────────────────────────────────────────

    private static String chatIdOf(JSONObject msgOrObj) {
        if (msgOrObj == null) return "";
        JSONObject chat = msgOrObj.optJSONObject("chat");
        if (chat != null) return String.valueOf(chat.opt("id"));
        Object id = msgOrObj.opt("id");
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
