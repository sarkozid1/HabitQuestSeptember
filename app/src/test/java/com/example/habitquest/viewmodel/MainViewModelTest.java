package com.example.habitquest.viewmodel;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.example.habitquest.model.Achievement;
import com.example.habitquest.model.Habit;
import com.example.habitquest.model.UserCharacter;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

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

    // --- completeHabit ---

    @Test
    public void completeHabit_increasesXP() {
        int xpBefore = viewModel.getCharacter().getValue().getExperience();
        viewModel.completeHabit(0);
        int xpAfter = viewModel.getCharacter().getValue().getExperience();
        assertTrue(xpAfter > xpBefore);
    }

    @Test
    public void completeHabit_increasesCompletionCount() {
        viewModel.completeHabit(0);
        Habit habit = viewModel.getHabits().getValue().get(0);
        assertEquals(1, habit.getCompletionCount());
    }

    @Test
    public void completeHabit_invalidIndex_doesNotCrash() {
        viewModel.completeHabit(999); // érvénytelen index
        assertNotNull(viewModel.getCharacter().getValue());
    }

    @Test
    public void completeHabit_updatesHpLiveData() {
        viewModel.completeHabit(0);
        assertNotNull(viewModel.getHp().getValue());
        assertTrue(viewModel.getHp().getValue() > 0);
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
        viewModel.updateHabit(0, "Módosított név", "Módosított leírás", 99);
        Habit habit = viewModel.getHabits().getValue().get(0);

        assertEquals("Módosított név", habit.getName());
        assertEquals("Módosított leírás", habit.getDescription());
        assertEquals(99, habit.getExperienceReward());
    }

    @Test
    public void updateHabit_invalidIndex_doesNotCrash() {
        viewModel.updateHabit(999, "X", "Y", 1);
        assertNotNull(viewModel.getHabits().getValue());
    }

    // --- deleteHabit ---

    @Test
    public void deleteHabit_removesFromList() {
        int before = viewModel.getHabits().getValue().size();
        viewModel.deleteHabit(0);
        int after = viewModel.getHabits().getValue().size();
        assertEquals(before - 1, after);
    }

    @Test
    public void deleteHabit_invalidIndex_doesNotCrash() {
        int before = viewModel.getHabits().getValue().size();
        viewModel.deleteHabit(999);
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
