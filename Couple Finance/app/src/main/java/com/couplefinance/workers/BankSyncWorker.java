package com.couplefinance.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.couplefinance.data.BankAutoSyncManager;
import com.couplefinance.data.CycleManager;
import com.couplefinance.data.MerchantRuleManager;
import com.couplefinance.data.RecurringChargeManager;

/**
 * WorkManager worker for daily automatic bank synchronisation.
 * Replaces AlarmManager + BankSyncReceiver.
 * Survives Doze mode and Samsung aggressive process kill.
 */
public class BankSyncWorker extends Worker {

    public BankSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context app = getApplicationContext();
        try {
            CycleManager.getInstance().init(app);
            RecurringChargeManager.getInstance().init(app);
            MerchantRuleManager.getInstance().init(app);
            BankAutoSyncManager.runSync(app);
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}
