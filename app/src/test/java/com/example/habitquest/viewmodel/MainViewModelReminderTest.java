package com.example.habitquest.viewmodel;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.example.habitquest.model.Habit;
import com.example.habitquest.model.Schedule;
import com.example.habitquest.notifications.ReminderScheduler;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** A ViewModel a megfelelő pillanatokban ütemezi / törli az emlékeztetőket. */
@RunWith(RobolectricTestRunner.class)
public class MainViewModelReminderTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    /** Feljegyzi a hívásokat WorkManager helyett. */
    static class FakeScheduler implements ReminderScheduler {
        final List<String> scheduled = new ArrayList<>();
        final List<String> cancelled = new ArrayList<>();
        Boolean water = null;
        List<Habit> rescheduled = null;

        @Override public void schedule(Habit habit) { scheduled.add(habit.getId()); }
        @Override public void cancel(String habitId) { cancelled.add(habitId); }
        @Override public void rescheduleAll(List<Habit> habits) { rescheduled = habits; }
        @Override public void setWaterReminderEnabled(boolean enabled) { water = enabled; }
    }

    private MainViewModel viewModel;
    private FakeScheduler scheduler;

    @Before
    public void setUp() {
        viewModel = new MainViewModel();
        scheduler = new FakeScheduler();
        viewModel.setReminderScheduler(scheduler);
        viewModel.loadInitialData();
    }

    @Test
    public void addHabit_schedulesReminder() {
        Habit h = new Habit("Edzés", "", 20);
        h.setReminderMinutes(7 * 60);
        viewModel.addHabit(h);
        assertTrue(scheduler.scheduled.contains(h.getId()));
    }

    @Test
    public void updateHabit_setsReminderAndReschedules() {
        Habit first = viewModel.getHabits().getValue().get(0);
        viewModel.updateHabit(first.getId(), "N", "D", 5, Schedule.daily(), 20 * 60);
        assertEquals(Integer.valueOf(20 * 60), first.getReminderMinutes());
        assertTrue(scheduler.scheduled.contains(first.getId()));
    }

    @Test
    public void updateHabit_removingReminder_stillNotifiesScheduler() {
        Habit first = viewModel.getHabits().getValue().get(0);
        first.setReminderMinutes(9 * 60);
        viewModel.updateHabit(first.getId(), "N", "D", 5, Schedule.daily(), null);
        assertNull(first.getReminderMinutes());
        // a scheduler.schedule() a reminder nélküli szokásnál törli a munkát
        assertTrue(scheduler.scheduled.contains(first.getId()));
    }

    @Test
    public void updateHabit_withoutReminderArg_keepsExistingReminder() {
        Habit first = viewModel.getHabits().getValue().get(0);
        first.setReminderMinutes(9 * 60);
        viewModel.updateHabit(first.getId(), "N", "D", 5);
        assertEquals(Integer.valueOf(9 * 60), first.getReminderMinutes());
    }

    @Test
    public void deleteHabit_cancelsReminder() {
        String id = viewModel.getHabits().getValue().get(0).getId();
        viewModel.deleteHabit(id);
        assertTrue(scheduler.cancelled.contains(id));
    }

    @Test
    public void completeHabit_reschedulesReminder() {
        String id = viewModel.getHabits().getValue().get(0).getId();
        viewModel.completeHabit(id);
        assertTrue(scheduler.scheduled.contains(id));
    }

    @Test
    public void waterReminderToggle_updatesSchedulerAndLiveData() {
        viewModel.setWaterReminderEnabled(false);
        assertEquals(Boolean.FALSE, scheduler.water);
        assertEquals(Boolean.FALSE, viewModel.getWaterReminderEnabled().getValue());

        viewModel.setWaterReminderEnabled(true);
        assertEquals(Boolean.TRUE, scheduler.water);
    }

    // --- export → import a ViewModelen keresztül ---

    @Test
    public void exportThenApplyImport_restoresStateAndReschedules() {
        Habit first = viewModel.getHabits().getValue().get(0);
        viewModel.completeHabit(first.getId());
        viewModel.setWaterReminderEnabled(false);
        String json = viewModel.buildExportJson();
        int habitCount = viewModel.getHabits().getValue().size();
        int xp = viewModel.getCharacter().getValue().getExperience();

        // "új telefon": friss ViewModel
        MainViewModel fresh = new MainViewModel();
        FakeScheduler freshScheduler = new FakeScheduler();
        fresh.setReminderScheduler(freshScheduler);
        fresh.loadInitialData();
        fresh.deleteHabit(fresh.getHabits().getValue().get(0).getId());

        com.example.habitquest.utils.BackupManager.ImportResult result =
                com.example.habitquest.utils.BackupManager.parse(json);
        assertTrue(result.isSuccess());
        fresh.applyImport(result.getData());

        assertEquals(habitCount, fresh.getHabits().getValue().size());
        assertEquals(habitCount, fresh.getHabitItems().getValue().size());
        assertNotNull(fresh.findHabitById(first.getId()));
        assertEquals(1, fresh.findHabitById(first.getId()).getCompletionCount());
        assertEquals(xp, fresh.getCharacter().getValue().getExperience());
        assertEquals(Boolean.FALSE, fresh.getWaterReminderEnabled().getValue());
        assertEquals(Boolean.FALSE, freshScheduler.water);
        assertEquals(habitCount, freshScheduler.rescheduled.size());
    }

    // --- induláskori betöltés (regresszió: a mentett karakter elveszett) ---

    @Test
    public void initStorageHelper_publishesSavedCharacterAndMigratedHabits() {
        android.content.Context ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        com.example.habitquest.data.HabitRepository.resetInstanceForTests();
        com.example.habitquest.utils.StorageHelper storage = new com.example.habitquest.utils.StorageHelper(ctx);
        storage.clearAllData();
        com.example.habitquest.model.UserCharacter saved = new com.example.habitquest.model.UserCharacter();
        saved.setCharacterClass("mage");
        saved.setLevel(4);
        saved.addCoins(77);
        storage.saveCharacter(saved);
        ctx.getSharedPreferences("habitquest_prefs", android.content.Context.MODE_PRIVATE).edit()
                .putString("habits_data", "[{\"name\":\"Régi\",\"experienceReward\":5}]").commit();

        MainViewModel vm = new MainViewModel();
        FakeScheduler fake = new FakeScheduler();
        vm.setReminderScheduler(fake);
        vm.initStorageHelper(ctx);

        assertEquals(4, vm.getCharacter().getValue().getLevel());
        assertEquals("mage", vm.getCharacter().getValue().getCharacterClass());
        assertEquals(Integer.valueOf(77), vm.getCoins().getValue());
        assertEquals("Régi", vm.getHabits().getValue().get(0).getName());
        assertNotNull(vm.getHabits().getValue().get(0).getId());
        assertNotNull("induláskor újraütemez", fake.rescheduled);
        assertEquals(Boolean.TRUE, fake.water);
    }
}
