package com.couplefinance.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Receives the daily digest alarm and runs Telegram alerts/digest in the background.
 * Scheduled by TelegramScheduler.schedulePeriodicWork().
 */
public class TelegramDigestReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (ctx == null || intent == null) return;
        String action = intent.getAction();
        final Context app = ctx.getApplicationContext();

        if ("android.intent.action.BOOT_COMPLETED".equals(action)) {
            TelegramManager.getInstance().init(app);
            if (TelegramManager.getInstance().isConfigured())
                TelegramScheduler.schedulePeriodicWork(app);
            return;
        }

        if (!"com.couplefinance.TELEGRAM_DIGEST".equals(action)) return;

        // Reschedule for tomorrow immediately so the chain holds even if work below fails
        TelegramScheduler.schedulePeriodicWork(app);

        final PendingResult pr = goAsync();
        new Thread(() -> {
            try { TelegramScheduler.checkAlertsBackground(app); }
            finally { pr.finish(); }
        }).start();
    }
}
