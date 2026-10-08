package com.example.habitquest.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.content.Context;
import android.app.PendingIntent;
import android.content.Intent;

import com.example.habitquest.MainActivity;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.habitquest.R;

public class WaterWorker extends Worker {

    public WaterWorker(@NonNull Context context,
                       @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {

        createChannel();

        // Android 13+: ellenőrizzük az engedélyt, különben SecurityException lehet
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    getApplicationContext(),
                    "android.permission.POST_NOTIFICATIONS"
            ) != PackageManager.PERMISSION_GRANTED) {
                return Result.success(); // nincs engedély → nem küldünk notit
            }
        }

        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                getApplicationContext(),
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(getApplicationContext(), "water")
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle("Igyál egy kis vizet 💧")
                        .setContentText("Eltelt fél óra!")
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent);


        NotificationManagerCompat manager =
                NotificationManagerCompat.from(getApplicationContext());

        manager.notify((int) System.currentTimeMillis(), builder.build());



        return Result.success();
    }


    private void createChannel() {
        NotificationChannel channel =
                new NotificationChannel(
                        "water",
                        "Vízivás emlékeztető",
                        NotificationManager.IMPORTANCE_DEFAULT
                );

        NotificationManager manager =
                getApplicationContext().getSystemService(NotificationManager.class);

        if (manager != null) manager.createNotificationChannel(channel);
    }
}
