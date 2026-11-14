package com.example.habitquest.model;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.junit.Assert.*;

/** Streak-számítás kihagyott és nem esedékes napokkal. 2026-11-16 hétfő. */
public class HabitStreakTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 16);

    private Habit habit(Schedule s) {
        Habit h = new Habit("Teszt", "", 10);
        h.setSchedule(s);
        return h;
    }

    // --- minden nap ---

    @Test
    public void daily_consecutiveDays_growStreak() {
        Habit h = habit(Schedule.daily());
        for (int i = 0; i < 5; i++) h.complete(MON.plusDays(i));
        assertEquals(5, h.getCurrentStreak());
        assertEquals(5, h.getLongestStreak());
    }

    @Test
    public void daily_missedDay_resetsStreak_keepsRecord() {
        Habit h = habit(Schedule.daily());
        h.complete(MON);
        h.complete(MON.plusDays(1));
        h.complete(MON.plusDays(2));
        h.complete(MON.plusDays(4)); // csütörtök kimaradt
        assertEquals(1, h.getCurrentStreak());
        assertEquals(3, h.getLongestStreak());
    }

    @Test
    public void daily_effectiveStreak_isZeroAfterMissedDay_beforeNextCompletion() {
        Habit h = habit(Schedule.daily());
        h.complete(MON);
        h.complete(MON.plusDays(1));
        assertEquals("ma még teljesíthető → nem szakadt meg", 2, h.getEffectiveStreak(MON.plusDays(2)));
        assertEquals("egy teljes nap kimaradt", 0, h.getEffectiveStreak(MON.plusDays(3)));
    }

    // --- a hét adott napjai ---

    @Test
    public void weekdays_skippingNonDueDays_keepsStreak() {
        // H, Sze, P: a kedd/csütörtök/hétvége kihagyása nem töri meg
        Habit h = habit(Schedule.weekdays(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
        h.complete(MON);
        h.complete(MON.plusDays(2));  // szerda
        h.complete(MON.plusDays(4));  // péntek
        h.complete(MON.plusDays(7));  // következő hétfő (hétvége kimaradt, de nem esedékes)
        assertEquals(4, h.getCurrentStreak());
    }

    @Test
    public void weekdays_missingDueDay_resetsStreak() {
        Habit h = habit(Schedule.weekdays(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
        h.complete(MON);
        h.complete(MON.plusDays(2)); // szerda
        h.complete(MON.plusDays(7)); // péntek kimaradt → újrakezdődik
        assertEquals(1, h.getCurrentStreak());
        assertEquals(2, h.getLongestStreak());
    }

    @Test
    public void weekdays_effectiveStreak_survivesNonDueDays() {
        Habit h = habit(Schedule.weekdays(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
        h.complete(MON);
        // kedd–péntek: a köztes napok nem esedékesek, péntek még teljesíthető
        assertEquals(1, h.getEffectiveStreak(MON.plusDays(4)));
        // szombaton már kimaradt a péntek
        assertEquals(0, h.getEffectiveStreak(MON.plusDays(5)));
    }

    @Test
    public void weekdays_bonusCompletionOnNonDueDay_doesNotBreakStreak() {
        Habit h = habit(Schedule.weekdays(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
        h.complete(MON);
        h.complete(MON.plusDays(1)); // kedd: nem esedékes, de teljesítette
        h.complete(MON.plusDays(2)); // szerda
        assertEquals(3, h.getCurrentStreak());
    }

    @Test
    public void weekdays_longGap_resetsStreak() {
        Habit h = habit(Schedule.weekdays(DayOfWeek.SUNDAY));
        h.complete(MON.plusDays(6));          // vasárnap
        h.complete(MON.plusDays(6 + 21));     // 3 héttel később vasárnap
        assertEquals(1, h.getCurrentStreak());
    }

    // --- heti X alkalom ---

    @Test
    public void timesPerWeek_targetMetEachWeek_keepsStreak() {
        Habit h = habit(Schedule.timesPerWeek(2));
        h.complete(MON);
        h.complete(MON.plusDays(3));           // 1. hét: 2/2
        h.complete(MON.plusDays(8));           // 2. hét kedd
        assertEquals(3, h.getCurrentStreak());
    }

    @Test
    public void timesPerWeek_targetMissed_resetsStreakInNewWeek() {
        Habit h = habit(Schedule.timesPerWeek(3));
        h.complete(MON);
        h.complete(MON.plusDays(1));           // 1. hét: csak 2/3
        h.complete(MON.plusDays(7));           // 2. hét
        assertEquals(1, h.getCurrentStreak());
        assertEquals(2, h.getLongestStreak());
    }

    @Test
    public void timesPerWeek_gapsWithinWeek_doNotBreakStreak() {
        Habit h = habit(Schedule.timesPerWeek(2));
        h.complete(MON);
        h.complete(MON.plusDays(5)); // szombat – 4 nap kihagyás a héten belül
        assertEquals(2, h.getCurrentStreak());
        assertEquals(2, h.getEffectiveStreak(MON.plusDays(6)));
    }

    @Test
    public void timesPerWeek_wholeWeekSkipped_resetsStreak() {
        Habit h = habit(Schedule.timesPerWeek(1));
        h.complete(MON);
        h.complete(MON.plusWeeks(2)); // a 2. hét teljesen kimaradt
        assertEquals(1, h.getCurrentStreak());
    }

    @Test
    public void timesPerWeek_effectiveStreak_zeroWhenLastWeekFellShort() {
        Habit h = habit(Schedule.timesPerWeek(2));
        h.complete(MON);
        assertEquals("a hét még tart", 1, h.getEffectiveStreak(MON.plusDays(6)));
        assertEquals("a hét lezárult 1/2-vel", 0, h.getEffectiveStreak(MON.plusDays(7)));
    }

    @Test
    public void timesPerWeek_acrossYearBoundary() {
        // 2026-12-28 hétfő, 2027-01-04 hétfő
        Habit h = habit(Schedule.timesPerWeek(1));
        h.complete(LocalDate.of(2026, 12, 31));
        h.complete(LocalDate.of(2027, 1, 5));
        assertEquals(2, h.getCurrentStreak());
    }

    // --- régi mentés kompatibilitás ---

    @Test
    public void legacyDailyHabit_continuesStreakFromStoredValues() {
        Habit h = habit(Schedule.daily());
        h.setLastCompletionDate("2026-11-15");
        h.setCurrentStreak(9);
        h.setLongestStreak(9);
        h.complete(MON); // 11-16
        assertEquals(10, h.getCurrentStreak());
    }

    @Test
    public void corruptLastCompletionDate_doesNotCrash() {
        Habit h = habit(Schedule.daily());
        h.setLastCompletionDate("nem-datum");
        h.complete(MON);
        assertEquals(1, h.getCurrentStreak());
        assertEquals(0, habit(Schedule.daily()).getEffectiveStreak(MON));
    }
}
