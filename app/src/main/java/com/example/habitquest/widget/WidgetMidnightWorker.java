package com.example.habitquest.widget;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Napfordulókor frissíti a widgeteket (új nap = nullázott napi állapot), majd a
 * frissítés során beütemezi a következő éjfélt. WorkManager: túléli az újraindítást,
 * nem kér engedélyt; Doze alatt pár perc késés előfordulhat.
 */
public class WidgetMidnightWorker extends Worker {

    private static final String UNIQUE_NAME = "widget_midnight_refresh";
    /** Egy kis ráhagyás éjfél után, hogy biztosan az új napot lássa. */
    private static final long MARGIN_SECONDS = 30;

    public WidgetMidnightWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        HabitWidgets.updateAll(getApplicationContext()); // ez ütemezi a következőt is
        return Result.success();
    }

    /** Késleltetés a következő éjfélig (+ráhagyás) – tiszta függvény, tesztelhető. */
    static long delayUntilNextMidnightMillis(LocalDateTime now) {
        LocalDateTime nextMidnight = LocalDate.from(now).plusDays(1).atStartOfDay()
                .plusSeconds(MARGIN_SECONDS);
        return Math.max(0, Duration.between(now, nextMidnight).toMillis());
    }

    static void scheduleNext(Context context) {
        try {
            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(WidgetMidnightWorker.class)
                    .setInitialDelay(delayUntilNextMidnightMillis(LocalDateTime.now()), TimeUnit.MILLISECONDS)
                    .build();
            WorkManager.getInstance(context)
                    .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request);
        } catch (IllegalStateException e) {
            Log.w("WidgetMidnightWorker", "WorkManager nem elérhető", e); // pl. unit tesztben
        }
    }

    static void cancel(Context context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME);
        } catch (IllegalStateException e) {
            Log.w("WidgetMidnightWorker", "WorkManager nem elérhető", e);
        }
    }
}
