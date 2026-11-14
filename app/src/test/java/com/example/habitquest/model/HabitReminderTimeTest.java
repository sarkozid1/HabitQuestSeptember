package com.example.habitquest.model;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.Assert.*;

/** Szokásonkénti emlékeztető időpontja. 2026-11-16 hétfő. */
public class HabitReminderTimeTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 16);
    private static final int AT_8_30 = 8 * 60 + 30;

    private Habit habit(Schedule s, Integer reminder) {
        Habit h = new Habit("Teszt", "", 10);
        h.setSchedule(s);
        h.setReminderMinutes(reminder);
        return h;
    }

    @Test
    public void noReminder_returnsNull() {
        Habit h = habit(Schedule.daily(), null);
        assertFalse(h.hasReminder());
        assertNull(h.nextReminderAt(MON.atTime(7, 0)));
        assertFalse(h.shouldRemindOn(MON));
    }

    @Test
    public void invalidReminderValue_isTreatedAsNone() {
        assertFalse(habit(Schedule.daily(), 24 * 60).hasReminder());
        assertFalse(habit(Schedule.daily(), -5).hasReminder());
    }

    @Test
    public void daily_beforeTime_remindsToday() {
        Habit h = habit(Schedule.daily(), AT_8_30);
        assertEquals(MON.atTime(8, 30), h.nextReminderAt(MON.atTime(7, 0)));
    }

    @Test
    public void daily_afterTime_remindsTomorrow() {
        Habit h = habit(Schedule.daily(), AT_8_30);
        assertEquals(MON.plusDays(1).atTime(8, 30), h.nextReminderAt(MON.atTime(9, 0)));
    }

    @Test
    public void exactlyAtTime_remindsTomorrow() {
        // a worker elsülésekor "most" = a mai időpont → a következőt már holnapra kell tenni
        Habit h = habit(Schedule.daily(), AT_8_30);
        assertEquals(MON.plusDays(1).atTime(8, 30), h.nextReminderAt(MON.atTime(8, 30)));
    }

    @Test
    public void completedToday_skipsTodaysReminder() {
        Habit h = habit(Schedule.daily(), AT_8_30);
        h.complete(MON);
        assertFalse(h.shouldRemindOn(MON));
        assertEquals(MON.plusDays(1).atTime(8, 30), h.nextReminderAt(MON.atTime(7, 0)));
    }

    @Test
    public void weekdays_jumpsToNextDueDay() {
        Habit h = habit(Schedule.weekdays(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), AT_8_30);
        // hétfő 9:00 után → csütörtök
        assertEquals(MON.plusDays(3).atTime(8, 30), h.nextReminderAt(MON.atTime(9, 0)));
        // csütörtök után → következő hétfő
        assertEquals(MON.plusDays(7).atTime(8, 30), h.nextReminderAt(MON.plusDays(3).atTime(10, 0)));
        assertFalse(h.shouldRemindOn(MON.plusDays(1)));
    }

    @Test
    public void timesPerWeek_targetReached_waitsForNextWeek() {
        Habit h = habit(Schedule.timesPerWeek(2), AT_8_30);
        h.complete(MON);
        h.complete(MON.plusDays(1));
        // a heti cél megvan → a hét hátralevő részén nincs emlékeztető
        assertFalse(h.shouldRemindOn(MON.plusDays(2)));
        assertEquals(MON.plusDays(7).atTime(8, 30), h.nextReminderAt(MON.plusDays(1).atTime(12, 0)));
    }

    @Test
    public void timesPerWeek_targetNotReached_remindsNextDay() {
        Habit h = habit(Schedule.timesPerWeek(3), AT_8_30);
        h.complete(MON);
        assertEquals(MON.plusDays(1).atTime(8, 30), h.nextReminderAt(MON.atTime(12, 0)));
    }

    @Test
    public void nextReminder_isAlwaysInTheFuture() {
        Habit h = habit(Schedule.weekdays(DayOfWeek.SUNDAY), 0); // éjfél
        LocalDateTime now = MON.plusDays(6).atStartOfDay(); // vasárnap 0:00
        LocalDateTime next = h.nextReminderAt(now);
        assertTrue(next.isAfter(now));
        assertEquals(MON.plusDays(13).atStartOfDay(), next);
    }
}
