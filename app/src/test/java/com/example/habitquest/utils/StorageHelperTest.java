package com.example.habitquest.utils;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.example.habitquest.model.Achievement;
import com.example.habitquest.model.Habit;
import com.example.habitquest.model.UserCharacter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

// Robolectric szükséges a Context és SharedPreferences szimulálásához
@RunWith(RobolectricTestRunner.class)
public class StorageHelperTest {

    private StorageHelper storageHelper;
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        storageHelper = new StorageHelper(context);
        storageHelper.clearAllData();
    }

    // --- Karakter ---

    @Test
    public void saveAndLoadCharacter_preservesLevel() {
        UserCharacter character = new UserCharacter();
        character.setLevel(5);
        storageHelper.saveCharacter(character);

        UserCharacter loaded = storageHelper.loadCharacter();
        assertEquals(5, loaded.getLevel());
    }

    @Test
    public void saveAndLoadCharacter_preservesExperience() {
        UserCharacter character = new UserCharacter();
        character.setExperience(75);
        storageHelper.saveCharacter(character);

        UserCharacter loaded = storageHelper.loadCharacter();
        assertEquals(75, loaded.getExperience());
    }

    @Test
    public void loadCharacter_returnsDefaultWhenEmpty() {
        UserCharacter loaded = storageHelper.loadCharacter();
        assertNotNull(loaded);
        assertEquals(1, loaded.getLevel());
    }

    // --- Szokások ---

    @Test
    public void saveAndLoadHabits_preservesList() {
        List<Habit> habits = Arrays.asList(
                new Habit("Víz", "Igyál vizet", 10),
                new Habit("Séta", "Sétálj egyet", 15)
        );
        storageHelper.saveHabits(habits);

        List<Habit> loaded = storageHelper.loadHabits();
        assertEquals(2, loaded.size());
        assertEquals("Víz", loaded.get(0).getName());
    }

    @Test
    public void loadHabits_returnsEmptyListWhenEmpty() {
        List<Habit> loaded = storageHelper.loadHabits();
        assertNotNull(loaded);
        assertTrue(loaded.isEmpty());
    }

    // --- Migráció régi (id nélküli) JSON-ból ---

    /** Az 1.0-s verzió által mentett formátum: nincs id mező. */
    private static final String LEGACY_HABITS_JSON = "["
            + "{\"name\":\"Víz\",\"description\":\"Igyál\",\"experienceReward\":10,"
            + "\"completionsToday\":2,\"lastCompletionDate\":\"2026-01-05\",\"completionCount\":40,"
            + "\"currentStreak\":3,\"longestStreak\":7},"
            + "{\"name\":\"Séta\",\"description\":\"\",\"experienceReward\":15}"
            + "]";

    private void writeRawHabits(String json) {
        context.getSharedPreferences("habitquest_prefs", Context.MODE_PRIVATE)
                .edit().putString("habits_data", json).commit();
    }

    @Test
    public void loadHabits_legacyJson_generatesUniqueIds() {
        writeRawHabits(LEGACY_HABITS_JSON);

        List<Habit> loaded = storageHelper.loadHabits();

        assertEquals(2, loaded.size());
        assertNotNull(loaded.get(0).getId());
        assertNotNull(loaded.get(1).getId());
        assertFalse(loaded.get(0).getId().isEmpty());
        assertNotEquals(loaded.get(0).getId(), loaded.get(1).getId());
    }

    @Test
    public void loadHabits_legacyJson_keepsAllOldData() {
        writeRawHabits(LEGACY_HABITS_JSON);

        Habit h = storageHelper.loadHabits().get(0);

        assertEquals("Víz", h.getName());
        assertEquals(10, h.getExperienceReward());
        assertEquals(40, h.getCompletionCount());
        assertEquals(3, h.getCurrentStreak());
        assertEquals(7, h.getLongestStreak());
        assertEquals("2026-01-05", h.getLastCompletionDate());
    }

    @Test
    public void loadHabits_legacyJson_idsArePersistedAndStable() {
        writeRawHabits(LEGACY_HABITS_JSON);

        String firstLoadId = storageHelper.loadHabits().get(0).getId();
        // új példány = mintha újraindult volna az app
        String secondLoadId = new StorageHelper(context).loadHabits().get(0).getId();

        assertEquals(firstLoadId, secondLoadId);
    }

    @Test
    public void loadHabits_legacyJson_defaultsToDailySchedule() {
        writeRawHabits(LEGACY_HABITS_JSON);
        for (Habit h : storageHelper.loadHabits()) {
            assertEquals(com.example.habitquest.model.Schedule.Type.DAILY, h.getSchedule().getType());
        }
    }

    @Test
    public void saveAndLoadHabits_preservesSchedule() {
        Habit h = new Habit("Edzés", "", 20);
        h.setSchedule(com.example.habitquest.model.Schedule.weekdays(
                java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.FRIDAY));
        storageHelper.saveHabits(Arrays.asList(h));

        Habit loaded = storageHelper.loadHabits().get(0);
        assertEquals(h.getSchedule(), loaded.getSchedule());
    }

    @Test
    public void saveAndLoadHabits_preservesReminder() {
        Habit h = new Habit("Edzés", "", 20);
        h.setReminderMinutes(18 * 60 + 15);
        storageHelper.saveHabits(Arrays.asList(h));
        assertEquals(Integer.valueOf(18 * 60 + 15), storageHelper.loadHabits().get(0).getReminderMinutes());
    }

    @Test
    public void loadHabits_legacyJson_hasNoReminder() {
        writeRawHabits(LEGACY_HABITS_JSON);
        assertNull(storageHelper.loadHabits().get(0).getReminderMinutes());
    }

    // --- Beállítások ---

    @Test
    public void waterReminder_defaultsToEnabled_andPersists() {
        assertTrue(storageHelper.isWaterReminderEnabled());
        storageHelper.setWaterReminderEnabled(false);
        assertFalse(new StorageHelper(context).isWaterReminderEnabled());
    }

    @Test
    public void saveAndLoadHabits_keepsExistingId() {
        Habit h = new Habit("Víz", "Igyál vizet", 10);
        storageHelper.saveHabits(Arrays.asList(h));

        assertEquals(h.getId(), storageHelper.loadHabits().get(0).getId());
    }

    @Test
    public void loadHabits_corruptJson_returnsEmptyListWithoutCrash() {
        writeRawHabits("{ez nem lista");
        assertTrue(storageHelper.loadHabits().isEmpty());
    }

    // --- Achievementek ---

    @Test
    public void saveAndLoadAchievements_preservesUnlockedState() {
        Achievement ach = new Achievement("test_id", "Teszt", "Leírás", true);
        storageHelper.saveAchievements(Arrays.asList(ach));

        List<Achievement> loaded = storageHelper.loadAchievements();
        assertEquals(1, loaded.size());
        assertTrue(loaded.get(0).isUnlocked());
    }

    @Test
    public void clearAllData_removesEverything() {
        UserCharacter character = new UserCharacter();
        character.setLevel(10);
        storageHelper.saveCharacter(character);
        storageHelper.clearAllData();

        UserCharacter loaded = storageHelper.loadCharacter();
        assertEquals(1, loaded.getLevel()); // visszaáll alapértelmezettre
    }
}
