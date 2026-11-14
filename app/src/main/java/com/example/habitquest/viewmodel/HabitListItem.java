package com.example.habitquest.viewmodel;

import com.example.habitquest.model.Habit;

/**
 * Egy szokás sora a listán, a mai napra kiszámolt állapottal.
 * A ViewModel állítja elő, a Fragment/adapter csak megjeleníti.
 */
public class HabitListItem {
    private final Habit habit;
    private final boolean dueToday;
    private final boolean completedToday;
    private final int completionsToday;
    private final int effectiveStreak;
    /** Heti X alkalomnál: hány napon volt már teljesítve ezen a héten. */
    private final int completionDaysThisWeek;

    public HabitListItem(Habit habit, boolean dueToday, boolean completedToday,
                         int completionsToday, int effectiveStreak, int completionDaysThisWeek) {
        this.habit = habit;
        this.dueToday = dueToday;
        this.completedToday = completedToday;
        this.completionsToday = completionsToday;
        this.effectiveStreak = effectiveStreak;
        this.completionDaysThisWeek = completionDaysThisWeek;
    }

    public Habit getHabit() { return habit; }
    public boolean isDueToday() { return dueToday; }
    public boolean isCompletedToday() { return completedToday; }
    public int getCompletionsToday() { return completionsToday; }
    public int getEffectiveStreak() { return effectiveStreak; }
    public int getCompletionDaysThisWeek() { return completionDaysThisWeek; }
}
