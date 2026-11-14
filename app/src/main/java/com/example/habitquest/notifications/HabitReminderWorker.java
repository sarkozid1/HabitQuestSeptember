package com.example.habitquest.notifications;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.habitquest.R;
import com.example.habitquest.model.Habit;
import com.example.habitquest.utils.StorageHelper;

import java.time.LocalDate;

/**
 * Egy szokás emlékeztetője. Elsüléskor a MENTETT állapotból dönt:
 * csak akkor szól, ha a szokás még létezik, ma esedékes és ma még nincs teljesítve.
 * Végül beütemezi a következő alkalmat (önmagát láncolja).
 */
public class HabitReminderWorker extends Worker {

    public static final String KEY_HABIT_ID = "habit_id";

    public HabitReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @SuppressLint("MissingPermission") // canPostNotifications ellenőrzi
    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        String habitId = getInputData().getString(KEY_HABIT_ID);
        if (habitId == null) return Result.success();

        Habit habit = null;
        for (Habit h : new StorageHelper(ctx).loadHabits()) {
            if (habitId.equals(h.getId())) { habit = h; break; }
        }
        if (habit == null || !habit.hasReminder()) return Result.success(); // törölték / kikapcsolták

        if (habit.shouldRemindOn(LocalDate.now()) && NotificationHelper.canPostNotifications(ctx)) {
            NotificationHelper.createChannels(ctx);
            int notifId = habitId.hashCode();
            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(ctx, NotificationHelper.CHANNEL_HABITS)
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .setContentTitle(ctx.getString(R.string.notif_habit_title, habit.getName()))
                            .setContentText(ctx.getString(R.string.notif_habit_text))
                            .setAutoCancel(true)
                            .setContentIntent(NotificationHelper.openHabitsIntent(ctx, notifId));
            NotificationManagerCompat.from(ctx).notify(notifId, builder.build());
        }

        // Utolsó lépésként: következő alkalom (REPLACE – a futó példány addigra végzett)
        new WorkManagerReminderScheduler(ctx).schedule(habit);
        return Result.success();
    }
}
