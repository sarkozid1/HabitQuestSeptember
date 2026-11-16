package com.example.habitquest.viewmodel;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.example.habitquest.data.HabitRepository;
import com.example.habitquest.model.Achievement;
import com.example.habitquest.model.Habit;
import com.example.habitquest.model.LevelUpCelebration;
import com.example.habitquest.model.Schedule;
import com.example.habitquest.model.ShopItem;
import com.example.habitquest.model.UserCharacter;
import com.example.habitquest.model.WorldZone;
import com.example.habitquest.notifications.ReminderScheduler;
import com.example.habitquest.utils.BackupManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * MainViewModel – a Fragmentek közös ViewModelje.
 *
 * Az adatok és a játéklogika (jutalom, streak, achievementek, mentés, emlékeztetők) a
 * {@link HabitRepository}-ban vannak; ugyanazt használja a widget és a háttérfeladatok is.
 * A ViewModel tükrözi a repository LiveData-it, és ebből számolja a megjelenítéshez szükséges
 * származtatott állapotot (mai szokáslista, világzóna, stat-számlálók).
 */
public class MainViewModel extends ViewModel {

    private HabitRepository repository = HabitRepository.inMemory();
    private boolean storageInitialized = false;
    /** Teszt által beállított ütemező – a valódi repositoryra is átkerül. */
    private ReminderScheduler customScheduler = null;

    // Tükrözött / származtatott LiveData-k
    private final MutableLiveData<UserCharacter> character = new MutableLiveData<>(new UserCharacter());
    private final MutableLiveData<List<Habit>> habits = new MutableLiveData<>(new ArrayList<>());
    /** A szokáslista a mai napra kiszámolt állapottal, rendezve (esedékesek elöl). */
    private final MutableLiveData<List<HabitListItem>> habitItems = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Achievement>> achievements = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> waterReminderEnabled = new MutableLiveData<>(true);
    private final MutableLiveData<WorldZone> currentZone = new MutableLiveData<>();
    private final MutableLiveData<List<ShopItem>> shopItems = new MutableLiveData<>(buildShopCatalog());
    /** Még nem nyugtázott szintlépés-ünneplés (null = nincs). */
    private final MutableLiveData<LevelUpCelebration> pendingCelebration = new MutableLiveData<>(null);

    private final MutableLiveData<Integer> hp = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> energy = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> mood = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> coins = new MutableLiveData<>(0);

    // A repository figyelése (onCleared-ben leiratkozunk)
    private final Observer<UserCharacter> characterObserver = this::onCharacterChanged;
    private final Observer<List<Habit>> habitsObserver = this::onHabitsChanged;
    private final Observer<List<Achievement>> achievementsObserver = achievements::setValue;
    private final Observer<Boolean> waterObserver = waterReminderEnabled::setValue;
    private final Observer<LevelUpCelebration> celebrationObserver = pendingCelebration::setValue;

    public MainViewModel() {
        attach(repository);
    }

    // -------------------------
    // GETTEREK
    // -------------------------
    public LiveData<Integer> getHp() { return hp; }
    public LiveData<Integer> getEnergy() { return energy; }
    public LiveData<Integer> getMood() { return mood; }
    public LiveData<Integer> getCoins() { return coins; }

    public LiveData<UserCharacter> getCharacter() { return character; }
    public LiveData<List<Habit>> getHabits() { return habits; }
    public LiveData<List<HabitListItem>> getHabitItems() { return habitItems; }
    public LiveData<List<Achievement>> getAchievements() { return achievements; }
    public LiveData<WorldZone> getCurrentZone() { return currentZone; }
    public LiveData<List<ShopItem>> getShopItems() { return shopItems; }
    public LiveData<Boolean> getWaterReminderEnabled() { return waterReminderEnabled; }
    public LiveData<LevelUpCelebration> getPendingCelebration() { return pendingCelebration; }

    /** A szintlépés-ünneplés nyugtázása ("Tovább"): elmentve, nem jön vissza. */
    public void acknowledgeCelebration() {
        repository.acknowledgeCelebration();
    }

    public boolean isStorageInitialized() { return storageInitialized; }

    /** A közös adatréteg (pl. statisztika képernyőhöz). */
    public HabitRepository getRepository() { return repository; }

    // -------------------------
    // Repository kötés
    // -------------------------

    /** Az app-szintű (mentett) repository bekötése – a MainActivity hívja induláskor. */
    public void initStorageHelper(Context context) {
        if (storageInitialized) {
            Log.d("MainViewModel", "Már inicializálva, kihagyva.");
            return;
        }
        HabitRepository repo = HabitRepository.getInstance(context);
        if (customScheduler != null) repo.setReminderScheduler(customScheduler);
        setRepository(repo);
        repo.rescheduleAllReminders();
        storageInitialized = true;
    }

    /** Repository csere (tesztekhez is). */
    public void setRepository(HabitRepository repo) {
        detach(repository);
        repository = repo;
        attach(repository);
    }

    private void attach(HabitRepository repo) {
        repo.getCharacter().observeForever(characterObserver);
        repo.getHabits().observeForever(habitsObserver);
        repo.getAchievements().observeForever(achievementsObserver);
        repo.getWaterReminderEnabled().observeForever(waterObserver);
        repo.getPendingCelebration().observeForever(celebrationObserver);
    }

    private void detach(HabitRepository repo) {
        repo.getCharacter().removeObserver(characterObserver);
        repo.getHabits().removeObserver(habitsObserver);
        repo.getAchievements().removeObserver(achievementsObserver);
        repo.getWaterReminderEnabled().removeObserver(waterObserver);
        repo.getPendingCelebration().removeObserver(celebrationObserver);
    }

    @Override
    protected void onCleared() {
        detach(repository);
        super.onCleared();
    }

    private void onCharacterChanged(UserCharacter u) {
        if (u == null) return;
        character.setValue(u);
        hp.setValue(u.getHealth());
        energy.setValue(u.getEnergy());
        mood.setValue(u.getMood());
        coins.setValue(u.getCoins());
        updateCurrentZone(u);
    }

    private void onHabitsChanged(List<Habit> list) {
        habits.setValue(list);
        habitItems.setValue(buildHabitItems(list, today()));
    }

    // -------------------------
    // Emlékeztetők, óra (tesztekben beinjektálható)
    // -------------------------
    public void setReminderScheduler(ReminderScheduler scheduler) {
        customScheduler = scheduler;
        repository.setReminderScheduler(scheduler);
    }

    public void setWaterReminderEnabled(boolean enabled) {
        repository.setWaterReminderEnabled(enabled);
    }

    public void setClock(Clock clock) {
        repository.setClock(clock);
        refreshHabitItems();
    }

    private LocalDate today() { return repository.today(); }

    /** A szokáslista frissítése (pl. napváltás után, amikor a Fragment újra előtérbe kerül). */
    public void refreshHabitItems() {
        habitItems.setValue(buildHabitItems(habits.getValue(), today()));
    }

    /**
     * Megjelenítési lista: a ma esedékes szokások elöl, a nem esedékesek a végén
     * (az eredeti sorrend csoporton belül megmarad).
     */
    public static List<HabitListItem> buildHabitItems(List<Habit> habitList, LocalDate today) {
        List<HabitListItem> due = new ArrayList<>();
        List<HabitListItem> notDue = new ArrayList<>();
        if (habitList != null) {
            for (Habit h : habitList) {
                HabitListItem item = new HabitListItem(
                        h,
                        h.isDueOn(today),
                        h.isCompletedOn(today),
                        h.getCompletionsOn(today),
                        h.getEffectiveStreak(today),
                        h.getCompletionDaysInWeekOf(today));
                (item.isDueToday() ? due : notDue).add(item);
            }
        }
        due.addAll(notDue);
        return due;
    }

    // -------------------------
    // Karakter
    // -------------------------
    public void setCharacterClass(String characterClass) {
        repository.setCharacterClass(characterClass);
    }

    public void persistCharacter() {
        repository.persistCharacter();
    }

    // -------------------------
    // Achievementek
    // -------------------------
    public void unlockAchievement(String id) {
        repository.unlockAchievement(id);
    }

    public void addAchievement(Achievement achievement) {
        repository.addAchievement(achievement);
    }

    // -------------------------
    // Szokások
    // -------------------------
    /** Szokás keresése stabil id alapján (null, ha nincs ilyen). */
    public Habit findHabitById(String id) {
        return repository.findHabit(id);
    }

    public void completeHabit(String habitId) {
        repository.completeHabit(habitId);
    }

    public void addHabit(Habit habit) {
        repository.addHabit(habit);
    }

    public void updateHabit(String habitId, String name, String description, int experienceReward) {
        Habit habit = findHabitById(habitId);
        if (habit == null) return;
        updateHabit(habitId, name, description, experienceReward, habit.getSchedule(),
                habit.getReminderMinutes());
    }

    /**
     * Szokás módosítása ütemezéssel és emlékeztetővel együtt.
     * @param reminderMinutes emlékeztető (éjfél óta eltelt perc), null = nincs
     */
    public void updateHabit(String habitId, String name, String description, int experienceReward,
                            Schedule schedule, Integer reminderMinutes) {
        repository.updateHabit(habitId, name, description, experienceReward, schedule, reminderMinutes);
    }

    public void deleteHabit(String habitId) {
        repository.deleteHabit(habitId);
    }

    /** Alapértelmezett kezdő szokások betöltése (tesztek és első indítás). */
    public void loadInitialData() {
        repository.resetToDefaultHabits();
    }

    // -------------------------
    // Bolt
    // -------------------------
    private static List<ShopItem> buildShopCatalog() {
        return Arrays.asList(
                new ShopItem("energy_potion", "Frissítő ital", "Azonnal +25 Energia", 40, 0, 25, 0, "🧪"),
                new ShopItem("health_potion", "Gyógyital", "Azonnal +30 HP", 50, 30, 0, 0, "❤️"),
                new ShopItem("cake", "Finom sütemény", "Azonnal +20 Hangulat", 35, 0, 0, 20, "🍰"),
                new ShopItem("full_restore", "Teljes felfrissülés", "+20 HP, +20 Energia, +20 Hangulat", 120, 20, 20, 20, "✨")
        );
    }

    /**
      Tárgy megvásárlása: levonja a coinokat és azonnal alkalmazza a jutalmat.
      @return true, ha sikerült a vásárlás (volt elég coin), false egyébként
     */
    public boolean buyItem(String itemId) {
        List<ShopItem> items = shopItems.getValue();
        if (items == null) return false;
        for (ShopItem candidate : items) {
            if (candidate.getId().equals(itemId)) return repository.buyItem(candidate);
        }
        return false;
    }

    // -------------------------
    // Világzóna (a karakter szintjéből származtatva)
    // -------------------------
    private void updateCurrentZone(UserCharacter user) {
        int level = user.getLevel();
        WorldZone zone;
        if (WorldZone.FOREST.containsLevel(level)) {
            zone = WorldZone.FOREST;
        } else if (WorldZone.MOUNTAIN.containsLevel(level)) {
            zone = WorldZone.MOUNTAIN;
        } else {
            zone = WorldZone.CITY;
        }
        if (currentZone.getValue() != zone) {
            currentZone.setValue(zone);
            Log.d("WorldZone", "Zóna frissítve: " + zone.getName());
        }
    }

    // -------------------------
    // Statisztika
    // -------------------------

    /** A statisztika képernyő kiszámolt állapota (null szűrő = összes szokás). */
    public StatsSnapshot buildStats(String filterHabitId) {
        return StatsSnapshot.build(habits.getValue(), filterHabitId, today());
    }

    // -------------------------
    // Mentés és visszaállítás (export / import)
    // -------------------------

    /** Az összes adat egyetlen JSON-ba (schemaVersion-nel). */
    public String buildExportJson() {
        return repository.buildExportJson();
    }

    public void exportTo(OutputStream out) throws IOException {
        BackupManager.write(out, buildExportJson());
    }

    /** Import fájl beolvasása és validálása – még NEM írja felül a jelenlegi adatokat. */
    public BackupManager.ImportResult readImport(InputStream in) {
        return BackupManager.read(in);
    }

    /** A (már validált és jóváhagyott) mentés betöltése: felülírja a jelenlegi adatokat. */
    public void applyImport(BackupManager.BackupData data) {
        repository.applyImport(data);
    }
}
