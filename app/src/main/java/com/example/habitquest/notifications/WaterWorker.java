package com.example.habitquest.notifications;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.habitquest.R;

/** 30 percenként futó vízivás-emlékeztető (a Profil oldalon kapcsolható ki). */
public class WaterWorker extends Worker {

    public WaterWorker(@NonNull Context context,
                       @NonNull WorkerParameters params) {
        super(context, params);
    }

    @SuppressLint("MissingPermission") // canPostNotifications ellenőrzi
    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        NotificationHelper.createChannels(ctx);

        // Android 13+: ellenőrizzük az engedélyt, különben SecurityException lehet
        if (!NotificationHelper.canPostNotifications(ctx)) {
            return Result.success(); // nincs engedély → nem küldünk notit
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(ctx, NotificationHelper.CHANNEL_WATER)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle(ctx.getString(R.string.notif_water_title))
                        .setContentText(ctx.getString(R.string.notif_water_text))
                        .setAutoCancel(true)
                        .setContentIntent(NotificationHelper.openHabitsIntent(ctx, 0));

        NotificationManagerCompat.from(ctx).notify((int) System.currentTimeMillis(), builder.build());
        return Result.success();
    }
}
