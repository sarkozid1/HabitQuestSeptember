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

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
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
