package com.couplefinance.data;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Receives Android notification action button taps from "grosse dépense" alerts.
 *
 * Actions:
 *   ACTION_ACK    — "✅ Noté" — dismisses the notification, sends Telegram ack
 *   ACTION_CATEGORIZE — "📂 Catégoriser" — launches DashboardActivity on Transactions tab
 *
 * Declare in AndroidManifest.xml:
 *   <receiver android:name=".data.TelegramActionReceiver" android:exported="false"/>
 */
public class TelegramActionReceiver extends BroadcastReceiver {

    public static final String ACTION_ACK         = "com.couplefinance.ACTION_EXPENSE_ACK";
    public static final String ACTION_CATEGORIZE  = "com.couplefinance.ACTION_EXPENSE_CATEGORIZE";
    public static final String EXTRA_NOTIF_ID     = "notif_id";
    public static final String EXTRA_LABEL        = "expense_label";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (ctx == null || intent == null) return;
        String action = intent.getAction();
        int notifId   = intent.getIntExtra(EXTRA_NOTIF_ID, -1);
        String label  = intent.getStringExtra(EXTRA_LABEL);

        // Dismiss notification
        if (notifId >= 0) {
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(notifId);
        }

        if (ACTION_ACK.equals(action)) {
            // Send a Telegram ack message
            try {
                TelegramManager tm = TelegramManager.getInstance();
                tm.init(ctx);
                if (tm.isConfigured()) {
                    String msg = "✅ Dépense notée"
                            + (label != null && !label.isEmpty() ? " : <b>" + label + "</b>" : "")
                            + ".";
                    tm.sendMessage(msg, null);
                }
            } catch (Exception ignored) {}

        } else if (ACTION_CATEGORIZE.equals(action)) {
            // Open DashboardActivity on Transactions tab
            Intent launch = new Intent(ctx, com.couplefinance.ui.DashboardActivity.class);
            launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            launch.putExtra("open_tab", "transactions");
            ctx.startActivity(launch);
        }
    }
}
