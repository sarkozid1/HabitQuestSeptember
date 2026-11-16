package com.example.habitquest.viewmodel;

import com.example.habitquest.model.Habit;
import com.example.habitquest.model.HabitStats;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A statisztika képernyő teljes, kiszámolt állapota (a Fragment csak megjeleníti).
 */
public final class StatsSnapshot {

    /** Egy szokás sora az összesítő listában. */
    public static final class HabitRow {
        public final Habit habit;
        public final HabitStats.Summary summary;

        HabitRow(Habit habit, HabitStats.Summary summary) {
            this.habit = habit;
            this.summary = summary;
        }
    }

    public final LocalDate today;
    public final HabitStats.Heatmap heatmap;
    public final HabitStats.WeekComparison week;
    public final List<HabitRow> rows;

    private StatsSnapshot(LocalDate today, HabitStats.Heatmap heatmap,
                          HabitStats.WeekComparison week, List<HabitRow> rows) {
        this.today = today;
        this.heatmap = heatmap;
        this.week = week;
        this.rows = Collections.unmodifiableList(rows);
    }

    /**
     * @param filterHabitId null = összes szokás; egyébként a hőtérkép és a heti összesítő csak
     *                      erre a szokásra szűr (a lista mindig minden szokást mutat)
     */
    public static StatsSnapshot build(List<Habit> habits, String filterHabitId, LocalDate today) {
        List<Habit> all = habits != null ? habits : new ArrayList<>();
        Habit filtered = null;
        if (filterHabitId != null) {
            for (Habit h : all) if (filterHabitId.equals(h.getId())) filtered = h;
        }

        HabitStats.Heatmap heatmap = filtered != null
                ? HabitStats.heatmapForHabit(filtered, today)
                : HabitStats.heatmapAll(all, today);
        HabitStats.WeekComparison week = HabitStats.weekComparison(
                filtered != null ? Collections.singletonList(filtered) : all, today);

        List<HabitRow> rows = new ArrayList<>();
        for (Habit h : all) rows.add(new HabitRow(h, HabitStats.summarize(h, today)));
        return new StatsSnapshot(today, heatmap, week, rows);
    }
}
