package com.example.habitquest.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;

import com.example.habitquest.R;
import com.example.habitquest.data.HabitRepository;
import com.example.habitquest.notifications.NotificationHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * A kezdőképernyő-widgetek (kicsi és közepes) felépítése és frissítése.
 *
 * A közepes widget listája: Android 12+ alatt a natív RemoteViews.RemoteCollectionItems
 * (a sorok a RemoteViews részei, egy frissítésben a fejléccel), Android 10–11-en a
 * {@link HabitListWidgetService}. Új függőség egyik úthoz sem kell.
 */
public final class HabitWidgets {

    private static final String TAG = "HabitWidgets";

    public static final String ACTION_COMPLETE = "com.example.habitquest.widget.ACTION_COMPLETE";
    public static final String EXTRA_HABIT_ID = "habit_id";

    /** Az avatar a widgetben kis felbontású bitmapként megy (a forráskép 1024×1536). */
    private static final int AVATAR_TARGET_PX = 192;
    private static final Map<Integer, Bitmap> AVATAR_CACHE = new HashMap<>();

    private HabitWidgets() { }

    /** Minden kihelyezett HabitQuest widget frissítése (appbeli változáskor, éjfélkor stb.). */
    public static void updateAll(Context context) {
        Context ctx = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(ctx);
        if (manager == null) return;

        int[] smallIds = manager.getAppWidgetIds(new ComponentName(ctx, SmallHabitWidgetProvider.class));
        int[] listIds = manager.getAppWidgetIds(new ComponentName(ctx, HabitListWidgetProvider.class));
        if (smallIds.length == 0 && listIds.length == 0) {
            WidgetMidnightWorker.cancel(ctx);
            return;
        }

        HabitRepository repo = HabitRepository.getInstance(ctx);
        WidgetState state = WidgetState.from(
                repo.getCharacterSnapshot(), repo.getHabitsSnapshot(), repo.today());

        for (int id : smallIds) manager.updateAppWidget(id, buildSmall(ctx, state));
        for (int id : listIds) manager.updateAppWidget(id, buildList(ctx, state, id));
        if (listIds.length > 0 && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // csak a régi (RemoteViewsService) úton kell: a Factory onDataSetChanged-je újratölt.
            // Android 12+ alatt a sorok a RemoteViews részei, külön értesítés nem kell – sőt,
            // Android 15+ alatt a régi úton ez egy korábbi (elavult fejlécű) nézetet küldhet újra.
            manager.notifyAppWidgetViewDataChanged(listIds, R.id.widgetList);
        }

        // napfordulókor új nap = nullázott napi állapot
        WidgetMidnightWorker.scheduleNext(ctx);
    }

    // -------------------------
    // Kicsi widget
    // -------------------------
    static RemoteViews buildSmall(Context ctx, WidgetState state) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_small);

        Bitmap avatar = avatarBitmap(ctx, state.characterImageResId);
        if (avatar != null) rv.setImageViewBitmap(R.id.widgetAvatar, avatar);

        rv.setTextViewText(R.id.widgetLevel, ctx.getString(R.string.widget_level, state.level));
        rv.setProgressBar(R.id.widgetXpBar, Math.max(1, state.xpToNext), state.xp, false);
        rv.setTextViewText(R.id.widgetToday,
                ctx.getString(R.string.widget_today_progress, state.doneToday, state.dueToday));
        rv.setOnClickPendingIntent(R.id.widgetRoot, openAppIntent(ctx));
        return rv;
    }

    // -------------------------
    // Közepes widget (lista)
    // -------------------------
    static RemoteViews buildList(Context ctx, WidgetState state, int appWidgetId) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_list);
        rv.setTextViewText(R.id.widgetListProgress,
                ctx.getString(R.string.widget_list_progress, state.doneToday, state.dueToday));
        rv.setOnClickPendingIntent(R.id.widgetHeader, openAppIntent(ctx));
        rv.setOnClickPendingIntent(R.id.widgetEmpty, openAppIntent(ctx));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: a sorok közvetlenül a RemoteViews-ban → a fejléc és a lista
            // egyetlen, atomi frissítéssel megy ki (nincs szolgáltatás, nincs külön notify)
            RemoteViews.RemoteCollectionItems.Builder items = new RemoteViews.RemoteCollectionItems.Builder()
                    .setHasStableIds(false)
                    .setViewTypeCount(1);
            long position = 0;
            for (WidgetState.Row row : state.rows) items.addItem(position++, buildRow(ctx, row));
            rv.setRemoteAdapter(R.id.widgetList, items.build());
        } else {
            // Android 10–11: RemoteViewsService (lásd HabitListWidgetService)
            Intent service = new Intent(ctx, HabitListWidgetService.class);
            service.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            // widgetenként egyedi intent, különben a rendszer összevonná a factory-kat
            service.setData(Uri.parse(service.toUri(Intent.URI_INTENT_SCHEME)));
            setRemoteAdapterCompat(rv, service);
        }
        rv.setEmptyView(R.id.widgetList, R.id.widgetEmpty);
        // a sorok fill-in intentje ehhez a sablonhoz adja hozzá a szokás id-ját
        rv.setPendingIntentTemplate(R.id.widgetList, completeTemplate(ctx, appWidgetId));
        return rv;
    }

    @SuppressWarnings("deprecation") // API 35-ben elavult, de minSdk 29-től ez működik új függőség nélkül
    private static void setRemoteAdapterCompat(RemoteViews rv, Intent service) {
        rv.setRemoteAdapter(R.id.widgetList, service);
    }

    static RemoteViews buildRow(Context ctx, WidgetState.Row row) {
        RemoteViews item = new RemoteViews(ctx.getPackageName(), R.layout.widget_list_item);
        item.setTextViewText(R.id.widgetItemName, row.name);
        if (row.done) {
            item.setTextViewText(R.id.widgetItemCheck, ctx.getString(R.string.widget_check_done));
            item.setInt(R.id.widgetItemCheck, "setBackgroundResource", R.drawable.widget_check_done);
            item.setContentDescription(R.id.widgetItemCheck, ctx.getString(R.string.habit_done_today));
            // kész szokás: nincs kattintás (a widgetről nem lehet újra jutalmat szerezni)
        } else {
            item.setTextViewText(R.id.widgetItemCheck, ctx.getString(R.string.widget_check_open));
            item.setInt(R.id.widgetItemCheck, "setBackgroundResource", R.drawable.widget_check_open);
            item.setContentDescription(R.id.widgetItemCheck,
                    ctx.getString(R.string.widget_complete_action, row.name));
            Intent fillIn = new Intent();
            fillIn.putExtra(EXTRA_HABIT_ID, row.habitId);
            item.setOnClickFillInIntent(R.id.widgetItemCheck, fillIn);
        }
        return item;
    }

    // -------------------------
    // PendingIntentek
    // -------------------------
    private static PendingIntent openAppIntent(Context ctx) {
        return NotificationHelper.openHabitsIntent(ctx, 7001);
    }

    /** Sablon a lista elemeihez (a fill-in intent miatt MUTABLE; explicit komponensre mutat). */
    private static PendingIntent completeTemplate(Context ctx, int appWidgetId) {
        Intent intent = new Intent(ctx, HabitListWidgetProvider.class);
        intent.setAction(ACTION_COMPLETE);
        return PendingIntent.getBroadcast(ctx, appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    // -------------------------
    // Avatar (lekicsinyítve, gyorsítótárazva)
    // -------------------------
    @Nullable
    private static synchronized Bitmap avatarBitmap(Context ctx, int resId) {
        if (resId == 0) return null;
        Bitmap cached = AVATAR_CACHE.get(resId);
        if (cached != null) return cached;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(ctx.getResources(), resId, bounds);

            int sample = 1;
            int largest = Math.max(bounds.outWidth, bounds.outHeight);
            while (largest / (sample * 2) >= AVATAR_TARGET_PX) sample *= 2;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeResource(ctx.getResources(), resId, opts);
            if (bmp != null) AVATAR_CACHE.put(resId, bmp);
            return bmp;
        } catch (RuntimeException | OutOfMemoryError e) {
            Log.w(TAG, "Avatar betöltése sikertelen", e);
            return null;
        }
    }
}
