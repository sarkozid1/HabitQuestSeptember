package com.example.habitquest.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

import com.example.habitquest.data.HabitRepository;
import com.example.habitquest.model.Habit;

/**
 * Közös widget provider: frissítés a rendszer kérésére, időváltáskor, és a
 * teljesítés-gomb kezelése (a HabitRepository-n keresztül – ugyanaz a jutalom-logika, mint az appban).
 */
public abstract class BaseHabitWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        HabitWidgets.updateAll(context);
    }

    @Override
    public void onEnabled(Context context) {
        HabitWidgets.updateAll(context);
    }

    @Override
    public void onDisabled(Context context) {
        // ha egy widget sem maradt, az éjféli frissítés is leáll
        HabitWidgets.updateAll(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (action == null) return;

        switch (action) {
            case HabitWidgets.ACTION_COMPLETE:
                completeFromWidget(context, intent.getStringExtra(HabitWidgets.EXTRA_HABIT_ID));
                break;
            case Intent.ACTION_TIME_CHANGED:
            case Intent.ACTION_TIMEZONE_CHANGED:
            case Intent.ACTION_DATE_CHANGED:
            case Intent.ACTION_MY_PACKAGE_REPLACED:
                HabitWidgets.updateAll(context);
                break;
            default:
                break;
        }
    }

    /**
     * Teljesítés a widgetről. A repository értesíti a widgeteket és – ha az app nyitva van –
     * a ViewModel LiveData-it is (ugyanaz a folyamaton belüli példány).
     */
    static void completeFromWidget(Context context, String habitId) {
        if (habitId == null) return;
        HabitRepository repo = HabitRepository.getInstance(context);
        Habit habit = repo.findHabit(habitId);
        // már kész vagy közben törölt szokás: nincs dupla jutalom
        if (habit != null && !habit.isCompletedOn(repo.today())) {
            repo.completeHabit(habitId);
        }
        HabitWidgets.updateAll(context);
    }
}
