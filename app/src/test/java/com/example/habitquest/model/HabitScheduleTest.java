package com.example.habitquest.model;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.junit.Assert.*;

/** Ütemezés szerinti esedékesség. 2026-11-16 hétfő. */
public class HabitScheduleTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 16);
    private static final LocalDate TUE = MON.plusDays(1);
    private static final LocalDate WED = MON.plusDays(2);
    private static final LocalDate FRI = MON.plusDays(4);
    private static final LocalDate SUN = MON.plusDays(6);
    private static final LocalDate NEXT_MON = MON.plusWeeks(1);

    private Habit habit(Schedule s) {
        Habit h = new Habit("Teszt", "", 10);
        h.setSchedule(s);
        return h;
    }

    // --- minden nap ---

    @Test
    public void newHabit_defaultsToDaily() {
        assertEquals(Schedule.Type.DAILY, new Habit("x", "", 1).getSchedule().getType());
    }

    @Test
    public void daily_isDueEveryDay() {
        Habit h = habit(Schedule.daily());
        for (int i = 0; i < 7; i++) assertTrue(h.isDueOn(MON.plusDays(i)));
    }

    @Test
    public void setSchedule_null_fallsBackToDaily() {
        Habit h = habit(null);
        assertEquals(Schedule.Type.DAILY, h.getSchedule().getType());
    }

    // --- a hét adott napjai ---

    @Test
    public void weekdays_dueOnlyOnSelectedDays() {
        Habit h = habit(Schedule.weekdays(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
        assertTrue(h.isDueOn(MON));
        assertFalse(h.isDueOn(TUE));
        assertTrue(h.isDueOn(WED));
        assertTrue(h.isDueOn(FRI));
        assertFalse(h.isDueOn(SUN));
        assertTrue(h.isDueOn(NEXT_MON));
    }

    @Test
    public void weekdays_empty_isTreatedAsDaily() {
        Schedule s = Schedule.weekdays(new java.util.ArrayList<>());
        assertEquals(Schedule.Type.DAILY, s.getType());
        assertTrue(habit(s).isDueOn(TUE));
    }

    // --- heti X alkalom ---

    @Test
    public void timesPerWeek_dueUntilTargetReached() {
        Habit h = habit(Schedule.timesPerWeek(2));
        assertTrue(h.isDueOn(MON));

        h.complete(MON);
        assertTrue("aznap teljesítve → ma esedékes (és kész)", h.isDueOn(MON));
        assertTrue("1/2 → kedden még esedékes", h.isDueOn(TUE));

        h.complete(TUE);
        assertFalse("2/2 → a hét hátralevő részén nem esedékes", h.isDueOn(WED));
        assertFalse(h.isDueOn(SUN));
    }

    @Test
    public void timesPerWeek_multipleCompletionsSameDay_countAsOneDay() {
        Habit h = habit(Schedule.timesPerWeek(2));
        h.complete(MON);
        h.complete(MON);
        h.complete(MON);
        assertEquals(1, h.getCompletionDaysInWeekOf(MON));
        assertTrue(h.isDueOn(TUE));
    }

    @Test
    public void timesPerWeek_newWeek_isDueAgain() {
        Habit h = habit(Schedule.timesPerWeek(1));
        h.complete(MON);
        assertFalse(h.isDueOn(SUN));
        assertTrue(h.isDueOn(NEXT_MON));
        assertEquals(0, h.getCompletionDaysInWeekOf(NEXT_MON));
    }

    @Test
    public void timesPerWeek_isClampedTo1to7() {
        assertEquals(1, Schedule.timesPerWeek(0).getTimesPerWeek());
        assertEquals(7, Schedule.timesPerWeek(12).getTimesPerWeek());
    }

    // --- napi számláló ---

    @Test
    public void completionsOn_doesNotLeakIntoNextDay() {
        Habit h = habit(Schedule.daily());
        h.complete(MON);
        h.complete(MON);
        assertEquals(2, h.getCompletionsOn(MON));
        assertEquals(0, h.getCompletionsOn(TUE));
        assertTrue(h.isCompletedOn(MON));
        assertFalse(h.isCompletedOn(TUE));
    }
}
