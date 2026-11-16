package com.example.habitquest.model;

import com.google.gson.Gson;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/** Napi teljesítési előzmény: frissítés, vágás, migráció. */
public class HabitHistoryTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 16);

    @Test
    public void complete_recordsDailyCounts() {
        Habit h = new Habit("Víz", "", 10);
        h.complete(MON);
        h.complete(MON);
        h.complete(MON.plusDays(2));

        assertEquals(2, h.getHistoryCountOn(MON));
        assertEquals(0, h.getHistoryCountOn(MON.plusDays(1)));
        assertEquals(1, h.getHistoryCountOn(MON.plusDays(2)));
        assertEquals(2, h.getHistory().size());
        assertEquals(3, h.getCompletionCount()); // az összesített számláló továbbra is megy
    }

    @Test
    public void history_isPrunedToLimit() {
        Habit h = new Habit("Víz", "", 10);
        LocalDate start = MON.minusDays(Habit.HISTORY_DAYS + 50);
        for (int i = 0; i <= Habit.HISTORY_DAYS + 50; i++) h.complete(start.plusDays(i));

        assertEquals(Habit.HISTORY_DAYS, h.getHistory().size());
        assertEquals(0, h.getHistoryCountOn(MON.minusDays(Habit.HISTORY_DAYS)));
        assertEquals(1, h.getHistoryCountOn(MON.minusDays(Habit.HISTORY_DAYS - 1)));
        assertEquals(1, h.getHistoryCountOn(MON));
    }

    @Test
    public void history_isReadOnlyView() {
        Habit h = new Habit("Víz", "", 10);
        h.complete(MON);
        try {
            h.getHistory().put("2000-01-01", 5);
            fail("az előzmény kívülről nem módosítható");
        } catch (UnsupportedOperationException expected) { }
    }

    @Test
    public void migrate_legacyJson_emptyHistory_keepsCounters_setsCreatedDate() {
        String legacy = "{\"id\":\"x\",\"name\":\"Víz\",\"experienceReward\":10,"
                + "\"completionsToday\":2,\"lastCompletionDate\":\"2026-11-15\",\"completionCount\":40,"
                + "\"currentStreak\":3,\"longestStreak\":7}";
        Habit h = new Gson().fromJson(legacy, Habit.class);

        assertTrue(h.migrate(MON));

        assertTrue(h.getHistory().isEmpty());
        assertEquals(MON, h.getCreatedLocalDate());
        assertEquals(40, h.getCompletionCount());
        assertEquals(3, h.getCurrentStreak());
        assertEquals(7, h.getLongestStreak());
        assertFalse("másodszor már nincs változás", h.migrate(MON.plusDays(1)));
        assertEquals(MON, h.getCreatedLocalDate());
    }

    @Test
    public void migrate_legacy_thenComplete_continuesStreakAndStartsHistory() {
        String legacy = "{\"id\":\"x\",\"name\":\"Víz\",\"experienceReward\":10,"
                + "\"lastCompletionDate\":\"2026-11-15\",\"completionCount\":40,\"currentStreak\":3,\"longestStreak\":7}";
        Habit h = new Gson().fromJson(legacy, Habit.class);
        h.migrate(MON);

        h.complete(MON);

        assertEquals(4, h.getCurrentStreak());
        assertEquals(41, h.getCompletionCount());
        assertEquals(1, h.getHistoryCountOn(MON));
    }

    @Test
    public void explicitNullHistory_isMigrated() {
        Habit h = new Gson().fromJson("{\"id\":\"x\",\"name\":\"A\",\"history\":null}", Habit.class);
        assertTrue(h.migrate(MON));
        h.complete(MON);
        assertEquals(1, h.getHistoryCountOn(MON));
    }

    @Test
    public void history_survivesGsonRoundTrip() {
        Habit h = new Habit("Víz", "", 10);
        h.setCreatedDate(MON.minusDays(10));
        h.complete(MON.minusDays(1));
        h.complete(MON);
        h.complete(MON);

        Habit copy = new Gson().fromJson(new Gson().toJson(h), Habit.class);

        assertEquals(1, copy.getHistoryCountOn(MON.minusDays(1)));
        assertEquals(2, copy.getHistoryCountOn(MON));
        assertEquals(MON.minusDays(10), copy.getCreatedLocalDate());
        copy.complete(MON.plusDays(1)); // a betöltött map is írható
        assertEquals(1, copy.getHistoryCountOn(MON.plusDays(1)));
    }

    @Test
    public void storageMigration_appliesToWholeList() {
        Gson gson = new Gson();
        List<Habit> list = new ArrayList<>(Arrays.asList(
                gson.fromJson("{\"name\":\"A\"}", Habit.class),
                gson.fromJson("{\"name\":\"B\"}", Habit.class)));

        assertTrue(com.example.habitquest.utils.StorageHelper.migrateHabits(list, MON));

        for (Habit h : list) {
            assertNotNull(h.getId());
            assertEquals(MON, h.getCreatedLocalDate());
            assertTrue(h.getHistory().isEmpty());
        }
    }
}
