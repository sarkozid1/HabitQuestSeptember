package com.example.habitquest.viewmodel;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.example.habitquest.model.Achievement;
import com.example.habitquest.model.Habit;
import com.example.habitquest.model.UserCharacter;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import static org.junit.Assert.*;

// Robolectric: a ViewModel android.util.Log-ot hív, ami sima JUnit alatt nincs mockolva
@RunWith(RobolectricTestRunner.class)
public class MainViewModelTest {

    // LiveData szinkron kiértékeléshez szükséges
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private MainViewModel viewModel;

    @Before
    public void setUp() {
        viewModel = new MainViewModel();
        viewModel.loadInitialData();
    }

    private String firstId() {
        return viewModel.getHabits().getValue().get(0).getId();
    }

    // --- completeHabit ---

    @Test
    public void completeHabit_increasesXP() {
        int xpBefore = viewModel.getCharacter().getValue().getExperience();
        viewModel.completeHabit(firstId());
        int xpAfter = viewModel.getCharacter().getValue().getExperience();
        assertTrue(xpAfter > xpBefore);
    }

    @Test
    public void completeHabit_increasesCompletionCount() {
        viewModel.completeHabit(firstId());
        Habit habit = viewModel.getHabits().getValue().get(0);
        assertEquals(1, habit.getCompletionCount());
    }

    @Test
    public void completeHabit_unknownId_doesNotCrash() {
        viewModel.completeHabit("nem-letezo-id"); // ismeretlen id
        assertNotNull(viewModel.getCharacter().getValue());
    }

    @Test
    public void completeHabit_updatesHpLiveData() {
        viewModel.completeHabit(firstId());
        assertNotNull(viewModel.getHp().getValue());
        assertTrue(viewModel.getHp().getValue() > 0);
    }

    @Test
    public void deleteHabit_removesTheRightHabit() {
        Habit second = viewModel.getHabits().getValue().get(1);
        viewModel.deleteHabit(second.getId());
        assertNull(viewModel.findHabitById(second.getId()));
        assertNotNull(viewModel.findHabitById(firstId()));
    }

    @Test
    public void initialHabits_haveUniqueIds() {
        List<Habit> list = viewModel.getHabits().getValue();
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Habit h : list) {
            assertNotNull(h.getId());
            ids.add(h.getId());
        }
        assertEquals(list.size(), ids.size());
    }

    // --- ütemezés / rendezett lista ---

    /** 2026-11-17 kedd, helyi idő szerint dél. */
    private static final java.time.Clock TUESDAY_CLOCK = java.time.Clock.fixed(
            java.time.LocalDate.of(2026, 11, 17).atTime(12, 0)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant(),
            java.time.ZoneId.systemDefault());

    @Test
    public void habitItems_notDueHabitsGoLast() {
        viewModel.setClock(TUESDAY_CLOCK);
        Habit mondayOnly = new Habit("Csak hétfő", "", 5);
        mondayOnly.setSchedule(com.example.habitquest.model.Schedule.weekdays(java.time.DayOfWeek.MONDAY));
        // az elejére szúrjuk be, hogy látszódjon az átrendezés
        viewModel.getHabits().getValue().add(0, mondayOnly);
        viewModel.addHabit(new Habit("Napi", "", 5));

        List<HabitListItem> items = viewModel.getHabitItems().getValue();
        HabitListItem last = items.get(items.size() - 1);
        assertEquals(mondayOnly.getId(), last.getHabit().getId());
        assertFalse(last.isDueToday());
        for (int i = 0; i < items.size() - 1; i++) assertTrue(items.get(i).isDueToday());
    }

    @Test
    public void habitItems_keepOriginalOrderWithinGroup() {
        List<Habit> stored = viewModel.getHabits().getValue();
        List<HabitListItem> items = viewModel.getHabitItems().getValue();
        assertEquals(stored.size(), items.size());
        for (int i = 0; i < stored.size(); i++) {
            assertEquals(stored.get(i).getId(), items.get(i).getHabit().getId());
        }
    }

    @Test
    public void completeHabit_usesInjectedClock() {
        viewModel.setClock(TUESDAY_CLOCK);
        viewModel.completeHabit(firstId());
        assertEquals("2026-11-17", viewModel.getHabits().getValue().get(0).getLastCompletionDate());
        assertTrue(viewModel.getHabitItems().getValue().get(0).isCompletedToday());
    }

    @Test
    public void updateHabit_changesSchedule() {
        com.example.habitquest.model.Schedule s = com.example.habitquest.model.Schedule.timesPerWeek(3);
        viewModel.updateHabit(firstId(), "N", "D", 5, s, null);
        assertEquals(s, viewModel.findHabitById(firstId()).getSchedule());
    }

    // --- unlockAchievement ---

    @Test
    public void unlockAchievement_setsUnlockedTrue() {
        viewModel.addAchievement(
                new Achievement("test_ach", "Teszt", "Leírás", false)
        );
        viewModel.unlockAchievement("test_ach");

        List<Achievement> achievements = viewModel.getAchievements().getValue();
        boolean found = false;
        for (Achievement a : achievements) {
            if (a.getId().equals("test_ach")) {
                assertTrue(a.isUnlocked());
                found = true;
            }
        }
        assertTrue(found);
    }

    @Test
    public void unlockAchievement_unknownId_doesNotCrash() {
        viewModel.unlockAchievement("nem_letezik");
        assertNotNull(viewModel.getAchievements().getValue());
    }

    // --- addHabit ---

    @Test
    public void addHabit_increasesListSize() {
        int before = viewModel.getHabits().getValue().size();
        viewModel.addHabit(new Habit("Új szokás", "Leírás", 20));
        int after = viewModel.getHabits().getValue().size();
        assertEquals(before + 1, after);
    }

    // --- setCharacterClass ---

    @Test
    public void setCharacterClass_setsClass() {
        viewModel.setCharacterClass("mage");
        assertEquals("mage", viewModel.getCharacter().getValue().getCharacterClass());
    }

    @Test
    public void setCharacterClass_resetsLevel() {
        viewModel.setCharacterClass("warrior");
        assertEquals(1, viewModel.getCharacter().getValue().getLevel());
    }

    // --- updateHabit ---

    @Test
    public void updateHabit_changesFields() {
        viewModel.updateHabit(firstId(), "Módosított név", "Módosított leírás", 99);
        Habit habit = viewModel.getHabits().getValue().get(0);

        assertEquals("Módosított név", habit.getName());
        assertEquals("Módosított leírás", habit.getDescription());
        assertEquals(99, habit.getExperienceReward());
    }

    @Test
    public void updateHabit_unknownId_doesNotCrash() {
        viewModel.updateHabit("nem-letezo-id", "X", "Y", 1);
        assertNotNull(viewModel.getHabits().getValue());
    }

    // --- deleteHabit ---

    @Test
    public void deleteHabit_removesFromList() {
        int before = viewModel.getHabits().getValue().size();
        viewModel.deleteHabit(firstId());
        int after = viewModel.getHabits().getValue().size();
        assertEquals(before - 1, after);
    }

    @Test
    public void deleteHabit_unknownId_doesNotCrash() {
        int before = viewModel.getHabits().getValue().size();
        viewModel.deleteHabit("nem-letezo-id");
        assertEquals(before, viewModel.getHabits().getValue().size());
    }

    // --- shop / buyItem ---

    @Test
    public void shopItems_areLoadedByDefault() {
        assertNotNull(viewModel.getShopItems().getValue());
        assertFalse(viewModel.getShopItems().getValue().isEmpty());
    }

    @Test
    public void buyItem_notEnoughCoins_returnsFalse() {
        // friss karakternek nincs coinja
        boolean result = viewModel.buyItem("full_restore");
        assertFalse(result);
    }

    @Test
    public void buyItem_unknownId_returnsFalse() {
        boolean result = viewModel.buyItem("nem_letezik");
        assertFalse(result);
    }

    @Test
    public void buyItem_enoughCoins_spendsCoinsAndReturnsTrue() {
        UserCharacter user = viewModel.getCharacter().getValue();
        user.addCoins(1000);

        int coinsBefore = viewModel.getCharacter().getValue().getCoins();
        boolean result = viewModel.buyItem("cake"); // ár: 35

        assertTrue(result);
        assertEquals(coinsBefore - 35, viewModel.getCharacter().getValue().getCoins());
    }
}
