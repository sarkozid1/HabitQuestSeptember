package com.example.habitquest.widget;

import android.content.Context;
import android.content.Intent;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;

import com.example.habitquest.data.HabitRepository;
import com.example.habitquest.model.Habit;
import com.example.habitquest.model.Schedule;
import com.example.habitquest.model.UserCharacter;
import com.example.habitquest.utils.StorageHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class WidgetTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static final LocalDate TUE = LocalDate.of(2026, 11, 17);

    @Before
    public void setUp() {
        new StorageHelper(ApplicationProvider.getApplicationContext()).clearAllData();
        HabitRepository.resetInstanceForTests();
    }

    /** A singleton repository WorkManager nélkül (unit tesztben nincs inicializálva). */
    private static HabitRepository repo(Context ctx) {
        HabitRepository r = HabitRepository.getInstance(ctx);
        r.setReminderScheduler(com.example.habitquest.notifications.ReminderScheduler.NO_OP);
        if (r.getHabitsSnapshot().isEmpty()) r.resetToDefaultHabits(); // új tárolóban nincs alaplista
        return r;
    }

    @After
    public void tearDown() {
        HabitRepository.resetInstanceForTests();
    }

    // --- WidgetState (tiszta logika) ---

    @Test
    public void state_listsOnlyDueHabits_openFirst_andCountsProgress() {
        Habit water = new Habit("Víz", "", 10);
        Habit walk = new Habit("Séta", "", 10);
        Habit mondayOnly = new Habit("Hétfői", "", 10);
        mondayOnly.setSchedule(Schedule.weekdays(DayOfWeek.MONDAY));
        water.complete(TUE);

        WidgetState s = WidgetState.from(new UserCharacter(), Arrays.asList(water, walk, mondayOnly), TUE);

        assertEquals(2, s.dueToday);
        assertEquals(1, s.doneToday);
        assertEquals(2, s.rows.size());
        assertEquals("Séta", s.rows.get(0).name);   // nyitott elöl
        assertFalse(s.rows.get(0).done);
        assertEquals("Víz", s.rows.get(1).name);    // kész a végén, kipipálva
        assertTrue(s.rows.get(1).done);
    }

    @Test
    public void state_newDay_resetsDailyProgress() {
        Habit water = new Habit("Víz", "", 10);
        water.complete(TUE);
        WidgetState tomorrow = WidgetState.from(new UserCharacter(), Arrays.asList(water), TUE.plusDays(1));
        assertEquals(0, tomorrow.doneToday);
        assertFalse(tomorrow.rows.get(0).done);
    }

    @Test
    public void state_emptyAndNullInputs_doNotCrash() {
        WidgetState s = WidgetState.from(null, null, TUE);
        assertEquals(0, s.dueToday);
        assertTrue(s.rows.isEmpty());
        assertEquals(1, s.level);
    }

    @Test
    public void midnightDelay_pointsJustAfterNextMidnight() {
        long ms = WidgetMidnightWorker.delayUntilNextMidnightMillis(TUE.atTime(23, 59, 0));
        assertEquals(90_000, ms); // 1 perc + 30 mp ráhagyás
        long fromMorning = WidgetMidnightWorker.delayUntilNextMidnightMillis(LocalDateTime.of(TUE, java.time.LocalTime.of(8, 0)));
        assertEquals((16 * 3600 + 30) * 1000L, fromMorning);
    }

    // --- teljesítés a widgetről ---

    @Test
    public void completeFromWidget_completesOnce_noDoubleReward() {
        Context ctx = ApplicationProvider.getApplicationContext();
        HabitRepository repo = repo(ctx);
        Habit first = repo.getHabitsSnapshot().get(0);

        Intent tap = new Intent(ctx, HabitListWidgetProvider.class)
                .setAction(HabitWidgets.ACTION_COMPLETE)
                .putExtra(HabitWidgets.EXTRA_HABIT_ID, first.getId());
        new HabitListWidgetProvider().onReceive(ctx, tap);
        int coinsAfterFirst = repo.getCharacterSnapshot().getCoins();
        new HabitListWidgetProvider().onReceive(ctx, tap); // második koppintás ugyanazon a napon

        assertEquals(1, first.getCompletionCount());
        assertEquals(coinsAfterFirst, repo.getCharacterSnapshot().getCoins());
        assertTrue(coinsAfterFirst > 0);

        // a mentésben is benne van (zárt app esete: új példány a tárolóból)
        HabitRepository.resetInstanceForTests();
        List<Habit> reloaded = repo(ctx).getHabitsSnapshot();
        assertEquals(1, reloaded.get(0).getCompletionCount());
    }

    @Test
    public void completeFromWidget_unknownOrMissingId_isIgnored() {
        Context ctx = ApplicationProvider.getApplicationContext();
        repo(ctx);
        new SmallHabitWidgetProvider().onReceive(ctx,
                new Intent(HabitWidgets.ACTION_COMPLETE).putExtra(HabitWidgets.EXTRA_HABIT_ID, "nincs"));
        new SmallHabitWidgetProvider().onReceive(ctx, new Intent(HabitWidgets.ACTION_COMPLETE));
        assertEquals(0, repo(ctx).getCharacterSnapshot().getCoins());
    }

    @Test
    public void buildViews_doNotCrash() {
        Context ctx = ApplicationProvider.getApplicationContext();
        HabitRepository repo = repo(ctx);
        WidgetState state = WidgetState.from(repo.getCharacterSnapshot(), repo.getHabitsSnapshot(), repo.today());
        assertNotNull(HabitWidgets.buildSmall(ctx, state));
        assertNotNull(HabitWidgets.buildList(ctx, state, 42));
        for (WidgetState.Row row : state.rows) assertNotNull(HabitWidgets.buildRow(ctx, row));
    }
}
