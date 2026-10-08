package com.example.habitquest.viewmodel;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.habitquest.model.Achievement;
import com.example.habitquest.model.Habit;
import com.example.habitquest.model.ShopItem;
import com.example.habitquest.model.UserCharacter;
import com.example.habitquest.model.WorldZone;
import com.example.habitquest.utils.StorageHelper;

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
    public LiveData<List<Achievement>> getAchievements() { return achievements; }
    public LiveData<WorldZone> getCurrentZone() { return currentZone; }
    public LiveData<List<ShopItem>> getShopItems() { return shopItems; }
    public LiveData<String> getLastDiscoveredZone() { return lastDiscoveredZone; }

    public void setLastDiscoveredZone(String zoneName) { lastDiscoveredZone.setValue(zoneName); }
    public boolean isStorageInitialized() { return storageInitialized; }

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
        habits.setValue(habitList);

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
    public void completeHabit(int index) {
        List<Habit> habitList = habits.getValue();
        if (habitList == null || habitList.isEmpty() || index < 0 || index >= habitList.size()) return;

        Habit habit = habitList.get(index);
        habit.complete();

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

    }

    public void addHabit(Habit habit) {
        List<Habit> habitList = habits.getValue();
        if (habitList == null) habitList = new ArrayList<>();
        habitList.add(habit);
        habits.setValue(habitList);
        if (storage != null) storage.saveHabits(habitList);
    }

    public void updateHabit(int index, String name, String description, int experienceReward) {
        List<Habit> habitList = habits.getValue();
        if (habitList == null || index < 0 || index >= habitList.size()) return;

        Habit habit = habitList.get(index);
        habit.setName(name);
        habit.setDescription(description);
        habit.setExperienceReward(experienceReward);

        habits.setValue(habitList);
        if (storage != null) storage.saveHabits(habitList);
    }

    public void deleteHabit(int index) {
        List<Habit> habitList = habits.getValue();
        if (habitList == null || index < 0 || index >= habitList.size()) return;

        habitList.remove(index);
        habits.setValue(habitList);
        if (storage != null) storage.saveHabits(habitList);
    }

    public void loadInitialData() {
        List<Habit> initialHabits = new ArrayList<>();
        initialHabits.add(new Habit("Víz számláló", "Legalább 30 percenként igyál meg egy pohár vizet!", 10));
        initialHabits.add(new Habit("10 perc séta", "Sétálj egyet a szabadban", 15));
        initialHabits.add(new Habit("Tanulás 30 perc", "Tanulj fókuszáltan legalább 30 percet", 25));
        initialHabits.add(new Habit("Teszt", "sok XP", 1125));
        habits.setValue(initialHabits);
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
    // Storage init
    // -------------------------
    public void initStorageHelper(Context context) {
        if (storageInitialized) {
            Log.d("StorageHelper", "Már inicializálva, kihagyva.");
            return;
        }

        storage = new StorageHelper(context);

        // --- Karakter betöltése ---
        UserCharacter loadedCharacter = storage.loadCharacter();
        if (loadedCharacter == null) loadedCharacter = new UserCharacter();
        publishStatsFromCharacter(loadedCharacter);

        // --- Szokások ---
        List<Habit> loadedHabits = storage.loadHabits();
        if (loadedHabits != null && !loadedHabits.isEmpty()) {
            habits.setValue(loadedHabits);
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

        storageInitialized = true;
        Log.d("StorageHelper", "Betöltés befejezve.");
    }
}
