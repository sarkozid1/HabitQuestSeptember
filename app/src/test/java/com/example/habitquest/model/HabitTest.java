package com.example.habitquest.model;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class HabitTest {

    private Habit habit;

    @Before
    public void setUp() {
        habit = new Habit("Víz", "Igyál vizet", 10);
    }

    @Test
    public void complete_increasesCompletionCount() {
        habit.complete();
        assertEquals(1, habit.getCompletionCount());
    }

    @Test
    public void complete_increasesCompletionsToday() {
        habit.complete();
        habit.complete();
        assertEquals(2, habit.getCompletionsToday());
    }

    @Test
    public void complete_multipleTimesToday() {
        habit.complete();
        habit.complete();
        habit.complete();
        assertEquals(3, habit.getCompletionsToday());
        assertEquals(3, habit.getCompletionCount());
    }

    @Test
    public void complete_resetsCompletions() {
        // Szimuláljuk hogy tegnap teljesítettük
        habit.setLastCompletionDate("2000-01-01");
        habit.setCompletionsToday(5);

        // Ma teljesítjük
        habit.complete();

        // Napi számláló nullázódott és újra 1
        assertEquals(1, habit.getCompletionsToday());
    }

    @Test
    public void complete_totalCountNotResetOnNewDay() {
        habit.setLastCompletionDate("2000-01-01");
        habit.setCompletionCount(10);
        habit.complete();
        assertEquals(11, habit.getCompletionCount());
    }

    @Test
    public void initialState_isZero() {
        assertEquals(0, habit.getCompletionsToday());
        assertEquals(0, habit.getCompletionCount());
        assertEquals(0, habit.getCurrentStreak());
        assertEquals(0, habit.getLongestStreak());
    }

    @Test
    public void complete_firstTime_startsStreakAtOne() {
        habit.complete();
        assertEquals(1, habit.getCurrentStreak());
        assertEquals(1, habit.getLongestStreak());
    }

    @Test
    public void complete_sameDayTwice_streakStaysOne() {
        habit.complete();
        habit.complete();
        assertEquals(1, habit.getCurrentStreak());
    }

    @Test
    public void complete_consecutiveDay_increasesStreak() {
        java.time.LocalDate yesterday = java.time.LocalDate.now().minusDays(1);
        habit.setLastCompletionDate(yesterday.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE));
        habit.setCurrentStreak(1);
        habit.setLongestStreak(1);

        habit.complete();

        assertEquals(2, habit.getCurrentStreak());
        assertEquals(2, habit.getLongestStreak());
    }

    @Test
    public void complete_missedDay_resetsStreakToOne() {
        habit.setLastCompletionDate("2000-01-01");
        habit.setCurrentStreak(5);
        habit.setLongestStreak(5);

        habit.complete();

        assertEquals(1, habit.getCurrentStreak());
        assertEquals(5, habit.getLongestStreak()); // a rekord megmarad
    }

    @Test
    public void setNameDescriptionXp_updatesFields() {
        habit.setName("Új név");
        habit.setDescription("Új leírás");
        habit.setExperienceReward(50);

        assertEquals("Új név", habit.getName());
        assertEquals("Új leírás", habit.getDescription());
        assertEquals(50, habit.getExperienceReward());
    }
}
