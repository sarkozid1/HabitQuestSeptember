package com.example.habitquest.widget;

import com.example.habitquest.model.Habit;
import com.example.habitquest.model.UserCharacter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A widgetek által mutatott állapot egy adott napra – tiszta logika, unit tesztelhető.
 * (A RemoteViews-t a {@link HabitWidgets} építi belőle.)
 */
public final class WidgetState {

    /** Egy sor a közepes widget listájában. */
    public static final class Row {
        public final String habitId;
        public final String name;
        public final boolean done;

        Row(String habitId, String name, boolean done) {
            this.habitId = habitId;
            this.name = name;
            this.done = done;
        }
    }

    public final int level;
    public final int xp;
    public final int xpToNext;
    public final int characterImageResId;
    /** Ma esedékes szokások: a még nem teljesítettek elöl, a kész szokások a végén. */
    public final List<Row> rows;
    public final int doneToday;
    public final int dueToday;

    private WidgetState(int level, int xp, int xpToNext, int imageResId, List<Row> rows,
                        int doneToday, int dueToday) {
        this.level = level;
        this.xp = xp;
        this.xpToNext = xpToNext;
        this.characterImageResId = imageResId;
        this.rows = Collections.unmodifiableList(rows);
        this.doneToday = doneToday;
        this.dueToday = dueToday;
    }

    public static WidgetState from(UserCharacter character, List<Habit> habits, LocalDate today) {
        List<Row> open = new ArrayList<>();
        List<Row> done = new ArrayList<>();
        if (habits != null) {
            for (Habit h : habits) {
                if (!h.isDueOn(today)) continue;
                boolean isDone = h.isCompletedOn(today);
                Row row = new Row(h.getId(), h.getName() != null ? h.getName() : "", isDone);
                (isDone ? done : open).add(row);
            }
        }
        int doneCount = done.size();
        int dueCount = open.size() + done.size();
        open.addAll(done);

        UserCharacter c = character != null ? character : new UserCharacter();
        return new WidgetState(c.getLevel(), c.getExperience(), c.getXpToNextLevel(),
                c.getCharacterImageResId(), open, doneCount, dueCount);
    }
}
