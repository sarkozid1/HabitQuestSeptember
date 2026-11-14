package com.example.habitquest.notifications;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.example.habitquest.MainActivity;
import com.example.habitquest.R;

/** Közös értesítési segédfüggvények (csatornák, engedély, koppintásra nyíló Intent). */
public final class NotificationHelper {

    public static final String CHANNEL_WATER = "water";
    public static final String CHANNEL_HABITS = "habit_reminders";

    private NotificationHelper() { }

    public static void createChannels(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_WATER,
                context.getString(R.string.notif_channel_water),
                NotificationManager.IMPORTANCE_DEFAULT));
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_HABITS,
                context.getString(R.string.notif_channel_habits),
                NotificationManager.IMPORTANCE_DEFAULT));
    }

    /** Android 13+: POST_NOTIFICATIONS engedély nélkül nem küldünk értesítést. */
    public static boolean canPostNotifications(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Koppintásra az app a Szokások képernyőn nyílik meg. */
    public static PendingIntent openHabitsIntent(Context context, int requestCode) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_HABITS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
