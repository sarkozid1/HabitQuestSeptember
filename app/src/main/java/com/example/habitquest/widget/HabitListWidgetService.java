package com.example.habitquest.widget;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.example.habitquest.data.HabitRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * A közepes widget listájának adatforrása Android 10–11-en (minSdk 29).
 * Android 12+ alatt a HabitWidgets natív RemoteCollectionItems-t használ, ez a szolgáltatás
 * ott nem fut. (A visszaportolt RemoteViewsCompat külön könyvtár lenne: androidx.core:core-remoteviews.)
 */
public class HabitListWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext());
    }

    static final class Factory implements RemoteViewsFactory {
        private final Context context;
        private List<WidgetState.Row> rows = new ArrayList<>();

        Factory(Context context) {
            this.context = context;
        }

        @Override public void onCreate() { }

        /** Binder szálon fut: friss állapot a közös repositoryból. */
        @Override
        public void onDataSetChanged() {
            HabitRepository repo = HabitRepository.getInstance(context);
            rows = new ArrayList<>(WidgetState.from(
                    repo.getCharacterSnapshot(), repo.getHabitsSnapshot(), repo.today()).rows);
        }

        @Override public void onDestroy() { rows.clear(); }

        @Override public int getCount() { return rows.size(); }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= rows.size()) return null;
            return HabitWidgets.buildRow(context, rows.get(position));
        }

        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 1; }
        @Override public long getItemId(int position) { return position; }
        @Override public boolean hasStableIds() { return false; }
    }
}
