package com.example.habitquest.data;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;

import com.example.habitquest.model.Habit;
import com.example.habitquest.model.UserCharacter;
import com.example.habitquest.utils.StorageHelper;
import com.example.habitquest.viewmodel.MainViewModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** A közös adatréteg: app, widget és worker ugyanazt a logikát és állapotot látja. */
@RunWith(RobolectricTestRunner.class)
public class HabitRepositoryTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static final Clock MONDAY = Clock.fixed(
            LocalDate.of(2026, 11, 16).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant(),
            ZoneId.systemDefault());

    private Context context;
    private StorageHelper storage;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        storage = new StorageHelper(context);
        storage.clearAllData();
        HabitRepository.resetInstanceForTests();
    }

    @After
    public void tearDown() {
        HabitRepository.resetInstanceForTests();
    }

    private HabitRepository newRepo(HabitRepository.ChangeListener listener) {
        HabitRepository repo = new HabitRepository(storage, null, MONDAY, null);
        repo.loadFromStorage();
        // új (üres) tárolónál nincs alapértelmezett lista → a tesztekhez feltöltjük
        if (repo.getHabitsSnapshot().isEmpty()) repo.resetToDefaultHabits();
        repo.setChangeListener(listener);
        return repo;
    }

    // --- időzóna-váltás (utazás) ---

    @Test
    public void today_followsTimeZoneChange_whileRepositoryLives() {
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        try {
            HabitRepository repo = new HabitRepository(storage, null, null, null); // rendszeróra
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Pacific/Kiritimati")); // UTC+14
            LocalDate east = repo.today();
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Etc/GMT+12"));        // UTC-12
            LocalDate west = repo.today();
            // 26 óra különbség: a nyugati dátum mindig korábbi (1 vagy 2 nappal)
            assertTrue(west.isBefore(east));
            assertTrue(!west.isBefore(east.minusDays(2)));
        } finally {
            java.util.TimeZone.setDefault(original);
        }
    }

    // --- onboarding ---

    @Test
    public void newUser_needsOnboarding_andStartsWithoutHabits() {
        HabitRepository repo = new HabitRepository(storage, null, MONDAY, null);
        repo.loadFromStorage();
        assertTrue(repo.needsOnboarding());
        assertTrue(repo.getHabitsSnapshot().isEmpty());
        assertFalse("az üres lista nem mentődik alapértékekkel", storage.hasSavedUserData());
    }

    @Test
    public void existingUser_skipsOnboarding_andFlagIsSaved() {
        UserCharacter saved = new UserCharacter();
        saved.setCharacterClass("mage");
        saved.setLevel(4);
        storage.saveCharacter(saved);

        HabitRepository repo = new HabitRepository(storage, null, MONDAY, null);
        repo.loadFromStorage();

        assertFalse(repo.needsOnboarding());
        assertFalse(repo.needsClassSelection());
        assertTrue(storage.isOnboardingDone());
    }

    @Test
    public void existingUserWithOnlyHabits_skipsOnboarding_butNeedsClass() {
        storage.saveHabits(java.util.Arrays.asList(new Habit("Régi", "", 5)));
        HabitRepository repo = new HabitRepository(storage, null, MONDAY, null);
        repo.loadFromStorage();
        assertFalse(repo.needsOnboarding());
        assertTrue(repo.needsClassSelection());
        assertEquals(1, repo.getHabitsSnapshot().size());
    }

    @Test
    public void completeOnboarding_setsClassAddsStartersAndPersists() {
        HabitRepository repo = new HabitRepository(storage, null, MONDAY, null);
        repo.loadFromStorage();
        Habit water = new Habit("Víz számláló", "", 10);
        Habit walk = new Habit("10 perc séta", "", 15);

        repo.completeOnboarding("rogue", java.util.Arrays.asList(water, walk));

        HabitRepository reloaded = new HabitRepository(storage, null, MONDAY, null);
        reloaded.loadFromStorage();
        assertFalse(reloaded.needsOnboarding());
        assertEquals("rogue", reloaded.getCharacterSnapshot().getCharacterClass());
        assertEquals(1, reloaded.getCharacterSnapshot().getLevel());
        assertEquals(2, reloaded.getHabitsSnapshot().size());
        assertEquals(LocalDate.of(2026, 11, 16), reloaded.getHabitsSnapshot().get(0).getCreatedLocalDate());
    }

    @Test
    public void deletingAllHabits_doesNotBringDefaultsBack() {
        HabitRepository repo = newRepo(null);
        for (Habit h : repo.getHabitsSnapshot()) repo.deleteHabit(h.getId());

        HabitRepository reloaded = new HabitRepository(storage, null, MONDAY, null);
        reloaded.loadFromStorage();
        assertTrue(reloaded.getHabitsSnapshot().isEmpty());
        assertFalse(reloaded.needsOnboarding());
    }

    @Test
    public void completeHabit_appliesRewardAndPersists() {
        HabitRepository repo = newRepo(null);
        Habit first = repo.getHabitsSnapshot().get(0);
        int coinsBefore = repo.getCharacterSnapshot().getCoins();

        assertTrue(repo.completeHabit(first.getId()));

        assertTrue(repo.getCharacterSnapshot().getCoins() > coinsBefore);
        assertEquals("2026-11-16", first.getLastCompletionDate());
        // új példány = újraindított app: a mentésből ugyanaz jön vissza
        HabitRepository reloaded = newRepo(null);
        assertEquals(1, reloaded.findHabit(first.getId()).getCompletionCount());
        assertEquals(repo.getCharacterSnapshot().getCoins(), reloaded.getCharacterSnapshot().getCoins());
    }

    @Test
    public void completeHabit_unknownId_returnsFalse() {
        assertFalse(newRepo(null).completeHabit("nincs-ilyen"));
    }

    @Test
    public void everyChange_notifiesListener() {
        AtomicInteger calls = new AtomicInteger();
        HabitRepository repo = newRepo(calls::incrementAndGet);
        String id = repo.getHabitsSnapshot().get(0).getId();

        repo.completeHabit(id);
        repo.addHabit(new Habit("Új", "", 5));
        repo.deleteHabit(id);

        assertEquals(3, calls.get());
    }

    @Test
    public void widgetCompletion_updatesOpenAppViewModel() {
        // az app nyitva: a ViewModel ugyanarra a repositoryra figyel
        HabitRepository repo = newRepo(null);
        MainViewModel vm = new MainViewModel();
        vm.setRepository(repo);
        Habit first = repo.getHabitsSnapshot().get(0);
        int xpBefore = vm.getCharacter().getValue().getExperience();

        // "widget" teljesít a repositoryn keresztül
        repo.completeHabit(first.getId());

        assertTrue(vm.getHabitItems().getValue().get(0).isCompletedToday());
        assertTrue(vm.getCharacter().getValue().getExperience() > xpBefore
                || vm.getCharacter().getValue().getLevel() > 1);
        assertEquals(repo.getCharacterSnapshot().getCoins(), (int) vm.getCoins().getValue());
    }

    @Test
    public void getInstance_isSharedAndLoadsSavedData() {
        UserCharacter saved = new UserCharacter();
        saved.setCharacterClass("rogue");
        saved.setLevel(6);
        storage.saveCharacter(saved);

        HabitRepository a = HabitRepository.getInstance(context);
        HabitRepository b = HabitRepository.getInstance(context);

        assertSame(a, b);
        assertEquals(6, a.getCharacterSnapshot().getLevel());
        assertEquals("rogue", a.getCharacterSnapshot().getCharacterClass());
    }

    @Test
    public void achievements_unlockOnlyOnce_andPersist() {
        HabitRepository repo = newRepo(null);
        Habit big = new Habit("Nagy", "", 500);
        repo.addHabit(big);
        repo.completeHabit(big.getId());

        HabitRepository reloaded = newRepo(null);
        long unlocked = reloaded.getAchievementsSnapshot().stream().filter(a -> a.isUnlocked()).count();
        assertEquals(2, unlocked); // first_xp + level_up
    }

    @Test
    public void completionFromBackgroundThread_isPostedToLiveData() throws Exception {
        HabitRepository repo = newRepo(null);
        String id = repo.getHabitsSnapshot().get(0).getId();

        Thread worker = new Thread(() -> repo.completeHabit(id));
        worker.start();
        worker.join();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();

        assertEquals(1, repo.getHabits().getValue().get(0).getCompletionCount());
    }
}
