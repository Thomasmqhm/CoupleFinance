package com.couplefinance.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.couplefinance.data.TelegramScheduler;

/**
 * WorkManager worker that fires Telegram alerts + background digest.
 * Replaces the daily AlarmManager → ChargeAlarmReceiver TG trigger.
 * Survives Doze mode and Samsung aggressive process kill.
 */
public class TelegramDigestWorker extends Worker {

    public TelegramDigestWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            TelegramScheduler.checkAlertsBackground(getApplicationContext());
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}
