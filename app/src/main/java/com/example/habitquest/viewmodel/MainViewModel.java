package com.example.habitquest.viewmodel;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.habitquest.model.Achievement;
import com.example.habitquest.model.Habit;
import com.example.habitquest.model.Schedule;
import com.example.habitquest.model.ShopItem;
import com.example.habitquest.model.UserCharacter;
import com.example.habitquest.model.WorldZone;
import com.example.habitquest.notifications.ReminderScheduler;
import com.example.habitquest.notifications.WorkManagerReminderScheduler;
import com.example.habitquest.utils.BackupManager;
import com.example.habitquest.utils.StorageHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * MainViewModel
 * Kezeli a karakter, szokások, achievementek és világzóna állapotát.
 * Tamagotchi-szerű stat jutalom (HP/Energy/Mood) habit teljesítéskor.
 */
public class MainViewModel extends ViewModel {

    private StorageHelper storage;
    private boolean storageInitialized = false;

    // LiveData objektumok
    private final MutableLiveData<UserCharacter> character = new MutableLiveData<>(new UserCharacter());
    private final MutableLiveData<List<Habit>> habits = new MutableLiveData<>(new ArrayList<>());
    /** A szokáslista a mai napra kiszámolt állapottal, rendezve (esedékesek elöl). */
    private final MutableLiveData<List<HabitListItem>> habitItems = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Achievement>> achievements = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<WorldZone> currentZone = new MutableLiveData<>();
    private final MutableLiveData<List<ShopItem>> shopItems = new MutableLiveData<>(buildShopCatalog());

    private final MutableLiveData<String> lastDiscoveredZone = new MutableLiveData<>(null);

    private final MutableLiveData<Integer> hp = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> energy = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> mood = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> coins = new MutableLiveData<>(0);

    public LiveData<Integer> getHp() { return hp; }
    public LiveData<Integer> getEnergy() { return energy; }
    public LiveData<Integer> getMood() { return mood; }
    public LiveData<Integer> getCoins() { return coins; }

    // GETTEREK
    public LiveData<UserCharacter> getCharacter() { return character; }
    public LiveData<List<Habit>> getHabits() { return habits; }
    public LiveData<List<HabitListItem>> getHabitItems() { return habitItems; }
    public LiveData<List<Achievement>> getAchievements() { return achievements; }
    public LiveData<WorldZone> getCurrentZone() { return currentZone; }
    public LiveData<List<ShopItem>> getShopItems() { return shopItems; }
    public LiveData<String> getLastDiscoveredZone() { return lastDiscoveredZone; }

    public void setLastDiscoveredZone(String zoneName) { lastDiscoveredZone.setValue(zoneName); }
    public boolean isStorageInitialized() { return storageInitialized; }

    // -------------------------
    // Emlékeztetők (tesztekben NO_OP vagy fake)
    // -------------------------
    private ReminderScheduler reminderScheduler = ReminderScheduler.NO_OP;
    private final MutableLiveData<Boolean> waterReminderEnabled = new MutableLiveData<>(true);

    public void setReminderScheduler(ReminderScheduler scheduler) {
        this.reminderScheduler = scheduler != null ? scheduler : ReminderScheduler.NO_OP;
    }

    public LiveData<Boolean> getWaterReminderEnabled() { return waterReminderEnabled; }

    public void setWaterReminderEnabled(boolean enabled) {
        waterReminderEnabled.setValue(enabled);
        if (storage != null) storage.setWaterReminderEnabled(enabled);
        reminderScheduler.setWaterReminderEnabled(enabled);
    }

    // -------------------------
    // Óra (tesztekben beinjektálható)
    // -------------------------
    private Clock clock = Clock.systemDefaultZone();

    public void setClock(Clock clock) {
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
        refreshHabitItems();
    }

    private LocalDate today() { return LocalDate.now(clock); }

    /** A szokáslista frissítése (pl. napváltás után, amikor a Fragment újra előtérbe kerül). */
    public void refreshHabitItems() {
        habitItems.setValue(buildHabitItems(habits.getValue(), today()));
    }

    /** A szokások beállítása + a megjelenítési lista újraszámolása. */
    private void publishHabits(List<Habit> habitList) {
        habits.setValue(habitList);
        habitItems.setValue(buildHabitItems(habitList, today()));
    }

    /**
     * Megjelenítési lista: a ma esedékes szokások elöl, a nem esedékesek a végén
     * (az eredeti sorrend csoporton belül megmarad).
     */
    static List<HabitListItem> buildHabitItems(List<Habit> habitList, LocalDate today) {
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
    // Persist helper metódusok
    // -------------------------
    private void persistCharacterOnly(UserCharacter user) {
        publishStatsFromCharacter(user);
        character.setValue(user);
        if (storage != null) storage.saveCharacter(user);
    }

    private void persistAll(UserCharacter user, List<Habit> habitList) {
        publishStatsFromCharacter(user);
        character.setValue(user);
        publishHabits(habitList);

        if (storage != null) {
            storage.saveCharacter(user);
            storage.saveHabits(habitList);
        }
    }


    private void publishStatsFromCharacter(UserCharacter u) {
        if (u == null) return;
        hp.setValue(u.getHealth());
        energy.setValue(u.getEnergy());
        mood.setValue(u.getMood());
        coins.setValue(u.getCoins());
    }


    // -------------------------
    // Habit -> stat jutalom mapping
    // -------------------------
    private void applyHabitStatsReward(UserCharacter user, Habit habit) {
        if (user == null || habit == null) return;

        String title = habit.getName() == null ? "" : habit.getName().toLowerCase();


        // Default reward (minden szokás ad egy kicsit)
        int hp = +2;
        int energy = +2;
        int mood = +1;
        int xp = habit.getExperienceReward();
        int coins = 5;

        if (title.contains("víz") || title.contains("water")) {
            hp = +3;
            energy = +8;
            mood = +2;
            coins = 8;
        } else if (title.contains("séta") || title.contains("walk") || title.contains("mozg")) {
            hp = +5;
            energy = -3;   // fáraszt, de egészséges
            mood = +6;
            coins = 12;
        } else if (title.contains("tanul") || title.contains("study")) {
            hp = +1;
            energy = -6;
            mood = +3;
            coins = 15;
        }

        user.applyReward(hp, energy, mood, xp, coins);
    }

    // -------------------------
    // Kaszt beállítás
    // -------------------------
    public void setCharacterClass(String characterClass) {
        UserCharacter u = character.getValue();
        if (u == null) return;

        u.setCharacterClass(characterClass);

        // reset kasztválasztáskor
        u.setLevel(1);
        u.setExperience(0);

        // Tamagotchi reset: töltsük fel normális induló értékekre
        // (a maxokat a UserCharacter defaultja kezeli, ha kell)
        u.setHealth(u.getHealthMax());
        u.setEnergy(70);
        u.setMood(70);
        u.setUpdatedAt(System.currentTimeMillis());

        // zóna frissítés
        updateCurrentZone(u);

        publishStatsFromCharacter(u);
        persistCharacterOnly(u);

    }

    public void persistCharacter() {
        UserCharacter u = character.getValue();
        if (u != null) persistCharacterOnly(u);
    }

    // -------------------------
    // Achievement logika
    // -------------------------
    public void unlockAchievement(String id) {
        List<Achievement> current = achievements.getValue();
        if (current == null) return;

        for (Achievement a : current) {
            if (a.getId().equals(id)) {
                a.setUnlocked(true);
                Log.d("Achievement", "Feloldva: " + a.getTitle());
                break;
            }
        }

        achievements.setValue(current);
        if (storage != null) storage.saveAchievements(current);
    }

    private void loadInitialAchievements() {
        List<Achievement> initialAchievements = Arrays.asList(
                new Achievement("first_xp", "Első lépés", "Szerezz 10 XP-t.", false),
                new Achievement("level_up", "Szintlépés", "Érd el a 2. szintet.", false)
        );
        achievements.setValue(initialAchievements);
    }

    // -------------------------
    // Habit logika
    // -------------------------
    /** Szokás keresése stabil id alapján (null, ha nincs ilyen). */
    public Habit findHabitById(String id) {
        List<Habit> habitList = habits.getValue();
        if (habitList == null || id == null) return null;
        for (Habit h : habitList) {
            if (id.equals(h.getId())) return h;
        }
        return null;
    }

    public void completeHabit(String habitId) {
        List<Habit> habitList = habits.getValue();
        Habit habit = findHabitById(habitId);
        if (habitList == null || habit == null) return;

        habit.complete(today());

        UserCharacter user = character.getValue();
        if (user == null) return;

        int prevLevel = user.getLevel();

        // ✅ XP + HP/Energy/Mood + coin jutalom
        applyHabitStatsReward(user, habit);

        if (user.getLevel() > prevLevel) {
            Log.d("Character", "Szintlépés! Új szint: " + user.getLevel());
        }

        // zóna frissítés
        updateCurrentZone(user);

        // Achievementek (megjegyzés: experience nálad szint-hoz kötött maradék)
        if (user.getExperience() >= 10) unlockAchievement("first_xp");
        if (user.getLevel() >= 2) unlockAchievement("level_up");

        publishStatsFromCharacter(user);
        persistAll(user, habitList);
        // ha ma már teljesítve van, a mai emlékeztető kimarad → a következő esedékes napra ugrik
        reminderScheduler.schedule(habit);

    }

    public void addHabit(Habit habit) {
        List<Habit> habitList = habits.getValue();
        if (habitList == null) habitList = new ArrayList<>();
        habitList.add(habit);
        publishHabits(habitList);
        if (storage != null) storage.saveHabits(habitList);
        reminderScheduler.schedule(habit);
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
        List<Habit> habitList = habits.getValue();
        Habit habit = findHabitById(habitId);
        if (habitList == null || habit == null) return;

        habit.setName(name);
        habit.setDescription(description);
        habit.setExperienceReward(experienceReward);
        habit.setSchedule(schedule);
        habit.setReminderMinutes(reminderMinutes);

        publishHabits(habitList);
        if (storage != null) storage.saveHabits(habitList);
        reminderScheduler.schedule(habit); // frissíti vagy törli az emlékeztetőt
    }

    public void deleteHabit(String habitId) {
        List<Habit> habitList = habits.getValue();
        Habit habit = findHabitById(habitId);
        if (habitList == null || habit == null) return;

        habitList.remove(habit);
        reminderScheduler.cancel(habitId);
        publishHabits(habitList);
        if (storage != null) storage.saveHabits(habitList);
    }

    public void loadInitialData() {
        List<Habit> initialHabits = new ArrayList<>();
        initialHabits.add(new Habit("Víz számláló", "Legalább 30 percenként igyál meg egy pohár vizet!", 10));
        initialHabits.add(new Habit("10 perc séta", "Sétálj egyet a szabadban", 15));
        initialHabits.add(new Habit("Tanulás 30 perc", "Tanulj fókuszáltan legalább 30 percet", 25));
        initialHabits.add(new Habit("Teszt", "sok XP", 1125));
        publishHabits(initialHabits);
    }

    public void addAchievement(Achievement achievement) {
        List<Achievement> current = achievements.getValue();
        if (current == null) current = new ArrayList<>();
        current.add(achievement);
        achievements.setValue(current);
        if (storage != null) storage.saveAchievements(current);
    }

    // -------------------------
    // Bolt logika
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
        UserCharacter user = character.getValue();
        List<ShopItem> items = shopItems.getValue();
        if (user == null || items == null) return false;

        ShopItem item = null;
        for (ShopItem candidate : items) {
            if (candidate.getId().equals(itemId)) {
                item = candidate;
                break;
            }
        }
        if (item == null || user.getCoins() < item.getPrice()) return false;

        user.spendCoins(item.getPrice());
        user.applyReward(item.getHpBonus(), item.getEnergyBonus(), item.getMoodBonus(), 0, 0);

        persistCharacterOnly(user);
        return true;
    }

    // -------------------------
    // World Zone logika
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

        WorldZone current = currentZone.getValue();
        if (current != zone) {
            currentZone.setValue(zone);
            Log.d("WorldZone", "Zóna frissítve: " + zone.getName());
        }
    }

    // -------------------------
    // Mentés és visszaállítás (export / import)
    // -------------------------

    /** Az összes adat egyetlen JSON-ba (schemaVersion-nel). */
    public String buildExportJson() {
        Boolean water = waterReminderEnabled.getValue();
        return BackupManager.toJson(character.getValue(), habits.getValue(),
                achievements.getValue(), water == null || water);
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
        if (data == null || data.getCharacter() == null || data.getHabits() == null) return;

        UserCharacter user = data.getCharacter();
        List<Habit> habitList = new ArrayList<>(data.getHabits());
        List<Achievement> achievementList = new ArrayList<>(data.getAchievements());

        updateCurrentZone(user);
        persistAll(user, habitList);
        achievements.setValue(achievementList);
        if (storage != null) storage.saveAchievements(achievementList);

        Boolean water = data.getWaterReminderEnabled();
        if (water != null) setWaterReminderEnabled(water);

        // a régi szokások emlékeztetői törlődnek, az importáltak beütemeződnek
        reminderScheduler.rescheduleAll(habitList);
    }

    // -------------------------
    // Storage init
    // -------------------------
    public void initStorageHelper(Context context) {
        if (storageInitialized) {
            Log.d("StorageHelper", "Már inicializálva, kihagyva.");
            return;
        }

        storage = new StorageHelper(context);
        if (reminderScheduler == ReminderScheduler.NO_OP) {
            reminderScheduler = new WorkManagerReminderScheduler(context.getApplicationContext());
        }

        // --- Karakter betöltése ---
        UserCharacter loadedCharacter = storage.loadCharacter();
        if (loadedCharacter == null) loadedCharacter = new UserCharacter();
        // a betöltött karaktert a LiveData-ba is be kell tenni, különben induláskor egy friss
        // 1. szintű karakter látszana, és az első mentés felülírná a valódi haladást
        character.setValue(loadedCharacter);
        publishStatsFromCharacter(loadedCharacter);

        // --- Szokások ---
        List<Habit> loadedHabits = storage.loadHabits();
        if (loadedHabits != null && !loadedHabits.isEmpty()) {
            publishHabits(loadedHabits);
        } else {
            loadInitialData();
            if (storage != null) storage.saveHabits(habits.getValue());
        }

        // --- Achievementek ---
        List<Achievement> loadedAchievements = storage.loadAchievements();
        if (loadedAchievements != null && !loadedAchievements.isEmpty()) {
            achievements.setValue(loadedAchievements);
        } else {
            loadInitialAchievements();
            if (storage != null) storage.saveAchievements(achievements.getValue());
        }

        // --- Zóna beállítása ---
        updateCurrentZone(loadedCharacter);

        // --- Emlékeztetők (induláskor újraütemezve: időzóna-/óraváltás, Google-visszaállítás) ---
        reminderScheduler.rescheduleAll(habits.getValue());
        boolean water = storage.isWaterReminderEnabled();
        waterReminderEnabled.setValue(water);
        reminderScheduler.setWaterReminderEnabled(water);

        storageInitialized = true;
        Log.d("StorageHelper", "Betöltés befejezve.");
    }
}
