package com.example.habitquest.viewmodel;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.example.habitquest.data.HabitRepository;
import com.example.habitquest.model.Habit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class OnboardingViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Test
    public void defaults_appliedOnlyOnce_andToggleWorks() {
        OnboardingViewModel vm = new OnboardingViewModel();
        vm.applyDefaultStarters(0, 1);
        vm.toggleStarter(1);              // a felhasználó kiveszi a sétát
        vm.applyDefaultStarters(0, 1);    // pl. forgatás után újra hívódik – nem írja felül
        assertTrue(vm.isStarterSelected(0));
        assertFalse(vm.isStarterSelected(1));
        vm.toggleStarter(4);
        assertEquals(2, vm.getSelectedStarters().size());
    }

    @Test
    public void classSelection_requiredState() {
        OnboardingViewModel vm = new OnboardingViewModel();
        assertFalse(vm.hasClass());
        vm.selectClass("mage");
        assertTrue(vm.hasClass());
        assertEquals("mage", vm.getSelectedClass());
    }

    @Test
    public void finish_writesThroughRepository() {
        OnboardingViewModel vm = new OnboardingViewModel();
        vm.selectClass("warrior");
        HabitRepository repo = HabitRepository.inMemory();
        vm.finish(repo, Arrays.asList(new Habit("Víz", "", 10)));
        assertEquals("warrior", repo.getCharacterSnapshot().getCharacterClass());
        assertEquals(1, repo.getHabitsSnapshot().size());
        assertFalse(repo.needsOnboarding());
    }
}
