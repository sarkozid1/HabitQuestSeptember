package com.example.habitquest.model;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/** Statisztika: esedékesség szerinti arány, heti összesítő, hőtérkép. 2026-11-16 hétfő. */
public class HabitStatsTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 16);

    private Habit habit(Schedule s, LocalDate created) {
        Habit h = new Habit("Teszt", "", 10);
        h.setSchedule(s);
        h.setCreatedDate(created);
        return h;
    }

    // --- sikerességi arány ---

    @Test
    public void emptyHistory_newHabit_hasNoRateYet() {
        Habit h = habit(Schedule.daily(), MON);
        HabitStats.Summary s = HabitStats.summarize(h, MON);
        assertEquals(0, s.due);          // a mai, még nem teljesített nap nem számít
        assertEquals(-1, s.successPercent());
        assertEquals(0, s.totalCompletions);
    }

    @Test
    public void emptyHistory_olderHabit_countsMissedDays() {
        Habit h = habit(Schedule.daily(), MON.minusDays(4));
        HabitStats.Summary s = HabitStats.summarize(h, MON);
        assertEquals(4, s.due);          // 4 elmúlt nap, a mai nem
        assertEquals(0, s.completedDue);
        assertEquals(0, s.successPercent());
    }

    @Test
    public void daily_rateCountsTodayOnlyWhenDone() {
        Habit h = habit(Schedule.daily(), MON.minusDays(3));
        h.complete(MON.minusDays(3));
        h.complete(MON.minusDays(1));
        assertEquals(2, HabitStats.summarize(h, MON).completedDue);
        assertEquals(3, HabitStats.summarize(h, MON).due);          // ma még nyitott
        h.complete(MON);
        assertEquals(3, HabitStats.summarize(h, MON).completedDue);
        assertEquals(4, HabitStats.summarize(h, MON).due);
        assertEquals(75, HabitStats.summarize(h, MON).successPercent());
    }

    @Test
    public void weekdays_onlyDueDaysCount_bonusDaysIgnored() {
        // H, Sze, P – két hét
        Habit h = habit(Schedule.weekdays(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                MON.minusWeeks(2));
        h.complete(MON.minusWeeks(2));              // H  ✔
        h.complete(MON.minusWeeks(2).plusDays(1));  // K  (nem esedékes – bónusz)
        h.complete(MON.minusWeeks(2).plusDays(4));  // P  ✔
        h.complete(MON.minusWeeks(1).plusDays(2));  // Sze ✔
        // esedékes: 2 hét × 3 = 6 (a mai hétfő nyitott → nem számít)
        HabitStats.Summary s = HabitStats.summarize(h, MON);
        assertEquals(6, s.due);
        assertEquals(3, s.completedDue);
        assertEquals(50, s.successPercent());
    }

    @Test
    public void timesPerWeek_cappedAtTarget_ongoingWeekOnlyWhenReached() {
        Habit h = habit(Schedule.timesPerWeek(2), MON.minusWeeks(2));
        // 1. hét: 3 nap (de max 2 számít)
        h.complete(MON.minusWeeks(2));
        h.complete(MON.minusWeeks(2).plusDays(1));
        h.complete(MON.minusWeeks(2).plusDays(2));
        // 2. hét: 1 nap
        h.complete(MON.minusWeeks(1).plusDays(3));
        HabitStats.Summary s = HabitStats.summarize(h, MON.plusDays(1)); // kedd, a hét folyamatban
        assertEquals(4, s.due);           // 2 lezárt hét × 2, a folyó hét még nem
        assertEquals(3, s.completedDue);  // 2 + 1

        h.complete(MON);
        h.complete(MON.plusDays(1));      // a folyó héten megvan a 2
        HabitStats.Summary s2 = HabitStats.summarize(h, MON.plusDays(1));
        assertEquals(6, s2.due);
        assertEquals(5, s2.completedDue);
    }

    @Test
    public void timesPerWeek_partialFirstWeek_targetLimitedToRemainingDays() {
        // vasárnap vették fel, heti 3: az első héten csak 1 nap volt hátra
        Habit h = habit(Schedule.timesPerWeek(3), MON.minusDays(1));
        h.complete(MON.minusDays(1));
        HabitStats.Summary s = HabitStats.summarize(h, MON);
        assertEquals(1, s.due);
        assertEquals(1, s.completedDue);
        assertEquals(100, s.successPercent());
    }

    @Test
    public void rate_acrossMonthAndYearBoundary() {
        LocalDate dec30 = LocalDate.of(2026, 12, 30);
        Habit h = habit(Schedule.daily(), dec30);
        h.complete(dec30);
        h.complete(dec30.plusDays(1));        // dec 31
        h.complete(dec30.plusDays(3));        // jan 2 (jan 1 kimaradt)
        HabitStats.Summary s = HabitStats.summarize(h, LocalDate.of(2027, 1, 2));
        assertEquals(4, s.due);
        assertEquals(3, s.completedDue);
    }

    @Test
    public void unknownCreatedDate_isLimitedToHistoryWindow() {
        Habit h = habit(Schedule.daily(), null);
        h.setCreatedDate(null);
        assertEquals(Habit.HISTORY_DAYS - 1, HabitStats.summarize(h, MON).due);
    }

    // --- heti összesítő ---

    @Test
    public void weekComparison_thisWeekVsLastWeek() {
        Habit a = habit(Schedule.daily(), MON.minusWeeks(2));
        Habit b = habit(Schedule.daily(), MON.minusWeeks(2));
        a.complete(MON.minusDays(7));   // előző hét hétfő
        a.complete(MON.minusDays(1));   // előző hét vasárnap
        b.complete(MON.minusDays(3));
        b.complete(MON.minusDays(3));   // ugyanazon a napon kétszer = 2 teljesítés
        a.complete(MON);
        a.complete(MON.plusDays(2));
        HabitStats.WeekComparison w = HabitStats.weekComparison(Arrays.asList(a, b), MON.plusDays(2));
        assertEquals(2, w.thisWeek);
        assertEquals(4, w.lastWeek);
        assertEquals(-2, w.difference());
    }

    @Test
    public void weekComparison_emptyInput() {
        HabitStats.WeekComparison w = HabitStats.weekComparison(Collections.emptyList(), MON);
        assertEquals(0, w.thisWeek);
        assertEquals(0, w.lastWeek);
        assertEquals(0, HabitStats.completionsBetween(null, MON, MON));
    }

    // --- hőtérkép ---

    @Test
    public void heatmap_startsOnMonday_13Weeks_futureCellsBlank() {
        LocalDate wed = MON.plusDays(2);
        HabitStats.Heatmap hm = HabitStats.heatmapAll(Collections.emptyList(), wed);
        assertEquals(DayOfWeek.MONDAY, hm.start.getDayOfWeek());
        assertEquals(HabitStats.HEATMAP_WEEKS * 7, hm.cellCount());
        assertEquals(MON, hm.dateOf(hm.cellCount() - 7));
        assertEquals(0, hm.levels[hm.cellCount() - 5]);    // szerda = ma
        assertEquals(-1, hm.levels[hm.cellCount() - 4]);   // csütörtök = jövő
    }

    @Test
    public void heatmapAll_countsDistinctHabitsPerDay_scaledToBestDay() {
        Habit a = habit(Schedule.daily(), MON.minusWeeks(3));
        Habit b = habit(Schedule.daily(), MON.minusWeeks(3));
        a.complete(MON.minusDays(1));
        b.complete(MON.minusDays(1));
        a.complete(MON);
        a.complete(MON); // ugyanaz a szokás kétszer → 1 szokás
        HabitStats.Heatmap hm = HabitStats.heatmapAll(Arrays.asList(a, b), MON);
        int today = hm.cellCount() - 7;
        assertEquals(2, hm.values[today - 1]);
        assertEquals(HabitStats.MAX_LEVEL, hm.levels[today - 1]);
        assertEquals(1, hm.values[today]);
        assertEquals(2, hm.levels[today]);
        assertEquals(0, hm.levels[today - 2]);
    }

    @Test
    public void heatmapForHabit_marksNotDueDays() {
        Habit h = habit(Schedule.weekdays(DayOfWeek.MONDAY), MON.minusWeeks(3));
        h.complete(MON);
        HabitStats.Heatmap hm = HabitStats.heatmapForHabit(h, MON);
        int today = hm.cellCount() - 7;
        assertEquals(HabitStats.MAX_LEVEL, hm.levels[today]);
        assertFalse(hm.notDue[today]);
        assertTrue(hm.notDue[today - 1]);    // vasárnap
        assertFalse(hm.notDue[today - 7]);   // előző hétfő
    }

    @Test
    public void heatmapForHabit_daysBeforeCreation_areBlank() {
        Habit h = habit(Schedule.daily(), MON);
        HabitStats.Heatmap hm = HabitStats.heatmapForHabit(h, MON.plusDays(1));
        int today = hm.cellCount() - 6;          // kedd
        assertEquals(0, hm.levels[today]);       // ma: még nincs kész, de esedékes
        assertEquals(0, hm.levels[today - 1]);   // a felvétel napja
        assertEquals(-1, hm.levels[today - 2]);  // a felvétel előtt: üres
    }

    @Test
    public void scale_mapsToLevels() {
        assertEquals(0, HabitStats.scale(0, 5));
        assertEquals(1, HabitStats.scale(1, 5));
        assertEquals(4, HabitStats.scale(5, 5));
        assertEquals(0, HabitStats.scale(3, 0));
    }
}
