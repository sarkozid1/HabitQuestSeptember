package com.example.habitquest.data;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;

import com.example.habitquest.model.Habit;
import com.example.habitquest.model.LevelUpCelebration;
import com.example.habitquest.model.UserCharacter;
import com.example.habitquest.model.WorldZone;
import com.example.habitquest.utils.BackupManager;
import com.example.habitquest.utils.StorageHelper;
import com.example.habitquest.viewmodel.MainViewModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;

import static org.junit.Assert.*;

/** Szintlépés-ünneplés: mentett, nyugtázásig függő állapot. */
@RunWith(RobolectricTestRunner.class)
public class LevelUpCelebrationTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private StorageHelper storage;

    @Before
    public void setUp() {
        Context ctx = ApplicationProvider.getApplicationContext();
        storage = new StorageHelper(ctx);
        storage.clearAllData();
        HabitRepository.resetInstanceForTests();
    }

    @After
    public void tearDown() {
        HabitRepository.resetInstanceForTests();
    }

    /** Mentett karakter adott szinten + egy szokás adott XP-vel; friss repository. */
    private HabitRepository repoWith(int level, String cls, Habit... habits) {
        UserCharacter c = new UserCharacter();
        c.setCharacterClass(cls);
        c.setLevel(level);
        storage.saveCharacter(c);
        storage.saveHabits(new ArrayList<>(java.util.Arrays.asList(habits)));
        HabitRepository repo = new HabitRepository(storage, null, null, null);
        repo.loadFromStorage();
        return repo;
    }

    private HabitRepository reload() {
        HabitRepository repo = new HabitRepository(storage, null, null, null);
        repo.loadFromStorage();
        return repo;
    }

    // --- tiszta logika ---

    @Test
    public void pending_onlyWhenLevelIsHigher() {
        assertNull(LevelUpCelebration.pending(3, 3, 50));
        assertNull(LevelUpCelebration.pending(4, 3, 50));
        LevelUpCelebration c = LevelUpCelebration.pending(3, 5, 120);
        assertNotNull(c);
        assertEquals(2, c.levelsGained());
        assertEquals(120, c.coinsGained);
        assertNull("ugyanaz a zóna", c.newZone);
    }

    @Test
    public void pending_crossingZoneBoundary_reportsNewZone() {
        assertSame(WorldZone.MOUNTAIN, LevelUpCelebration.pending(5, 6, 0).newZone);
        assertSame(WorldZone.CITY, LevelUpCelebration.pending(9, 12, 0).newZone);
    }

    // --- repository ---

    @Test
    public void existingUser_noRetroactiveCelebration() {
        HabitRepository repo = repoWith(7, "warrior");
        assertNull(repo.currentCelebration());
        assertEquals(7, storage.getCelebratedLevel());
    }

    @Test
    public void levelUp_createsCelebration_withCoins() {
        Habit big = new Habit("Nagy", "", 150);
        HabitRepository repo = repoWith(1, "mage", big);
        int coinsBefore = repo.getCharacterSnapshot().getCoins();

        repo.completeHabit(big.getId());

        LevelUpCelebration c = repo.currentCelebration();
        assertNotNull(c);
        assertEquals(1, c.fromLevel);
        assertEquals(2, c.toLevel);
        assertEquals(repo.getCharacterSnapshot().getCoins() - coinsBefore, c.coinsGained);
        assertNotNull(repo.getPendingCelebration().getValue());
    }

    @Test
    public void multipleLevels_areSummarizedInOneCelebration() {
        Habit huge = new Habit("Óriás", "", 2000);
        HabitRepository repo = repoWith(4, "rogue", huge);

        repo.completeHabit(huge.getId());

        LevelUpCelebration c = repo.currentCelebration();
        assertEquals(4, c.fromLevel);
        assertTrue(c.toLevel >= 6);
        assertSame("5 → 6+ zónahatár", WorldZone.forLevel(c.toLevel), c.newZone);
    }

    @Test
    public void celebration_survivesRestart_untilAcknowledged() {
        Habit big = new Habit("Nagy", "", 150);
        HabitRepository repo = repoWith(1, "mage", big);
        repo.completeHabit(big.getId());

        // pl. widgetről lépett szintet zárt appnál → a következő indításkor is ott van
        assertNotNull(reload().currentCelebration());

        reload().acknowledgeCelebration();

        HabitRepository afterAck = reload();
        assertNull(afterAck.currentCelebration());
        assertNull(afterAck.getPendingCelebration().getValue());
    }

    @Test
    public void acknowledge_clearsLiveData_andCoins() {
        Habit big = new Habit("Nagy", "", 150);
        HabitRepository repo = repoWith(1, "warrior", big);
        repo.completeHabit(big.getId());

        repo.acknowledgeCelebration();

        assertNull(repo.getPendingCelebration().getValue());
        assertEquals(0, storage.getPendingLevelCoins());
        assertEquals(2, storage.getCelebratedLevel());
    }

    @Test
    public void import_doesNotCelebrateImportedProgress() {
        HabitRepository repo = repoWith(1, "mage");
        UserCharacter advanced = new UserCharacter();
        advanced.setCharacterClass("mage");
        advanced.setLevel(9);
        BackupManager.BackupData data = BackupManager.parse(
                BackupManager.toJson(advanced, new ArrayList<>(), new ArrayList<>(), true)).getData();

        repo.applyImport(data);

        assertNull(repo.currentCelebration());
    }

    @Test
    public void choosingClass_resetsCelebration() {
        Habit big = new Habit("Nagy", "", 150);
        HabitRepository repo = repoWith(1, "mage", big);
        repo.completeHabit(big.getId());
        assertNotNull(repo.currentCelebration());

        repo.setCharacterClass("rogue"); // 1. szintről újraindul

        assertNull(repo.currentCelebration());
        assertNull(repo.getPendingCelebration().getValue());
    }

    @Test
    public void viewModel_mirrorsAndAcknowledges() {
        Habit big = new Habit("Nagy", "", 150);
        HabitRepository repo = repoWith(1, "mage", big);
        MainViewModel vm = new MainViewModel();
        vm.setRepository(repo);

        vm.completeHabit(big.getId());
        assertNotNull(vm.getPendingCelebration().getValue());

        vm.acknowledgeCelebration();
        assertNull(vm.getPendingCelebration().getValue());
    }
}
