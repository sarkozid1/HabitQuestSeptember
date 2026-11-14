package com.example.habitquest.notifications;

import com.example.habitquest.model.Habit;

import java.util.List;

/**
 * Emlékeztetők ütemezése. A ViewModel ezen az interfészen keresztül éri el,
 * így unit tesztben WorkManager nélkül (NO_OP vagy saját fake) futtatható.
 */
public interface ReminderScheduler {

    /** A szokás következő emlékeztetőjének (újra)ütemezése; ha nincs beállítva, törli. */
    void schedule(Habit habit);

    /** A szokás emlékeztetőjének törlése (pl. a szokás törlésekor). */
    void cancel(String habitId);

    /** Minden szokás emlékeztetőjének újraütemezése (induláskor, import után). */
    void rescheduleAll(List<Habit> habits);

    /** A 30 perces vízivás-emlékeztető be-/kikapcsolása. */
    void setWaterReminderEnabled(boolean enabled);

    ReminderScheduler NO_OP = new ReminderScheduler() {
        @Override public void schedule(Habit habit) { }
        @Override public void cancel(String habitId) { }
        @Override public void rescheduleAll(List<Habit> habits) { }
        @Override public void setWaterReminderEnabled(boolean enabled) { }
    };
}
