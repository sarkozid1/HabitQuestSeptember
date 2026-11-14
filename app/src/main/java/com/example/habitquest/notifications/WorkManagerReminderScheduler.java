package com.example.habitquest.notifications;

import android.content.Context;

import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.habitquest.model.Habit;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * WorkManager alapú emlékeztető-ütemező.
 *
 * Miért WorkManager és nem AlarmManager? A pontos riasztáshoz SCHEDULE_EXACT_ALARM kellene
 * (Android 14-től alapból tiltott, a USE_EXACT_ALARM-ot a Play csak óra/naptár appoknak
 * engedi), a pontatlan alarmokat pedig újraindítás után magunknak kellene visszaállítani.
 * A WorkManager túléli az újraindítást, nem kér engedélyt; az ára néhány perc csúszás
 * Doze módban, ami egy szokás-emlékeztetőnél elfogadható.
 */
public class WorkManagerReminderScheduler implements ReminderScheduler {

    private static final String WATER_WORK = "water_reminder";
    private static final String HABIT_WORK_PREFIX = "habit_reminder_";
    private static final String TAG_HABIT_REMINDER = "habit_reminder";

    private final Context context;

    public WorkManagerReminderScheduler(Context context) {
        this.context = context.getApplicationContext();
    }

    public static String uniqueWorkName(String habitId) {
        return HABIT_WORK_PREFIX + habitId;
    }

    @Override
    public void schedule(Habit habit) {
        if (habit == null || habit.getId() == null) return;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = habit.nextReminderAt(now);
        if (next == null) {
            cancel(habit.getId());
            return;
        }
        long delayMs = Math.max(0, Duration.between(now, next).toMillis());

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(HabitReminderWorker.class)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(new Data.Builder()
                        .putString(HabitReminderWorker.KEY_HABIT_ID, habit.getId())
                        .build())
                .addTag(TAG_HABIT_REMINDER)
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(habit.getId()), ExistingWorkPolicy.REPLACE, request);
    }

    @Override
    public void cancel(String habitId) {
        if (habitId == null) return;
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(habitId));
    }

    @Override
    public void rescheduleAll(List<Habit> habits) {
        // a már nem létező szokások (pl. import előtti) emlékeztetői is törlődjenek
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_HABIT_REMINDER);
        if (habits == null) return;
        for (Habit h : habits) schedule(h);
    }

    @Override
    public void setWaterReminderEnabled(boolean enabled) {
        WorkManager wm = WorkManager.getInstance(context);
        if (!enabled) {
            wm.cancelUniqueWork(WATER_WORK);
            return;
        }
        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(WaterWorker.class, 30, TimeUnit.MINUTES).build();
        wm.enqueueUniquePeriodicWork(WATER_WORK, ExistingPeriodicWorkPolicy.KEEP, request);
    }
}
