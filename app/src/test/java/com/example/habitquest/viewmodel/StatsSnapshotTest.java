package com.example.habitquest.viewmodel;

import com.example.habitquest.model.Habit;
import com.example.habitquest.model.HabitStats;
import com.example.habitquest.model.Schedule;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class StatsSnapshotTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 16);

    @Test
    public void filter_switchesHeatmapAndWeek_listAlwaysHasAllHabits() {
        Habit a = new Habit("A", "", 5);
        Habit b = new Habit("B", "", 5);
        b.setSchedule(Schedule.weekdays(DayOfWeek.TUESDAY));
        a.complete(MON);
        a.complete(MON);
        b.complete(MON);
        List<Habit> all = Arrays.asList(a, b);

        StatsSnapshot total = StatsSnapshot.build(all, null, MON);
        StatsSnapshot onlyB = StatsSnapshot.build(all, b.getId(), MON);

        assertEquals(3, total.week.thisWeek);
        assertEquals(1, onlyB.week.thisWeek);
        assertEquals(2, total.rows.size());
        assertEquals(2, onlyB.rows.size());
        int today = total.heatmap.cellCount() - 7;
        assertEquals(2, total.heatmap.values[today]);           // 2 különböző szokás
        assertTrue(onlyB.heatmap.notDue[today]);                // B kedden esedékes
        assertEquals(HabitStats.MAX_LEVEL, onlyB.heatmap.levels[today]);
    }

    @Test
    public void unknownFilter_fallsBackToAll_emptyListSafe() {
        StatsSnapshot s = StatsSnapshot.build(null, "nincs", MON);
        assertTrue(s.rows.isEmpty());
        assertEquals(0, s.week.thisWeek);
        assertNotNull(s.heatmap);
    }
}
