package com.example.habitquest.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.habitquest.R;
import com.example.habitquest.model.Achievement;
import com.example.habitquest.model.Habit;
import com.example.habitquest.model.UserCharacter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Adatok mentése és betöltése SharedPreferences segítségével.
 * - Karakter (JSON)
 * - Szokások (JSON lista)
 * - Achievementek (JSON lista)
 * + Migration régi mentésekhez (új stat mezők defaultolása)
 */
public class StorageHelper {

    private static final String PREFS_NAME = "habitquest_prefs";
    private static final String KEY_CHARACTER = "character_data";
    private static final String KEY_HABITS = "habits_data";
    private static final String KEY_ACHIEVEMENTS = "achievements_data";
    private static final String KEY_WATER_REMINDER = "water_reminder_enabled";
    private static final String KEY_ONBOARDING_DONE = "onboarding_done";
    private static final String KEY_CELEBRATED_LEVEL = "celebrated_level";
    private static final String KEY_PENDING_LEVEL_COINS = "pending_level_coins";

    private final SharedPreferences prefs;
    private final Gson gson;

    public StorageHelper(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    // -------------------------
    // KARAKTER
    // -------------------------
    public void saveCharacter(UserCharacter character) {
        if (character == null) return;
        String json = gson.toJson(character);
        prefs.edit().putString(KEY_CHARACTER, json).apply();
        Log.d("StorageHelper", "Karakter mentve (szint: " + character.getLevel() + ")");
    }

    public UserCharacter loadCharacter() {
        String json = prefs.getString(KEY_CHARACTER, null);
        if (json == null) {
            UserCharacter c = new UserCharacter();
            c.setLevel(1);
            c.setExperience(0);
            c.setCoins(0);
            c.setCharacterImageResId(R.drawable.lv1);
            Log.d("StorageHelper", "Új alapkarakter létrehozva.");
            return c;
        }

        try {
            UserCharacter loaded = gson.fromJson(json, UserCharacter.class);
            if (loaded == null) return new UserCharacter();

            // ✅ Migration (régi mentésekben az új mezők 0-k lehetnek)
            boolean changed = migrateCharacter(loaded);

            if (changed) {
                saveCharacter(loaded);
                Log.d("StorageHelper", "Karakter migrálva és újramentve.");
            }

            Log.d("StorageHelper", "Karakter betöltve (szint: " + loaded.getLevel() + ")");
            return loaded;

        } catch (Exception e) {
            Log.e("StorageHelper", "Hiba a karakter betöltésekor: " + e.getMessage());
            return new UserCharacter();
        }
    }

    /**
     * Karakter migráció: régi mentésekben az új mezők 0-k lehetnek. Importnál is ez fut.
     * A karakterképet mindig újraszámolja (a mentett R.drawable id app-frissítéskor változhat).
     * @return true, ha bármi változott
     */
    public static boolean migrateCharacter(UserCharacter c) {
        if (c == null) return false;
        boolean changed = false;

        if (c.getHealthMax() <= 0) {
            int hpMax = 100 + Math.max(0, c.getLevel() - 1) * 5;
            c.setHealthMax(hpMax);
            changed = true;
        }

        if (c.getEnergyMax() <= 0) {
            int enMax = 100 + Math.max(0, c.getLevel() - 1) * 5;
            c.setEnergyMax(enMax);
            changed = true;
        }

        if (c.getHealth() <= 0) {
            c.setHealth(c.getHealthMax());
            changed = true;
        }

        if (c.getEnergy() <= 0) {
            c.setEnergy(70);
            changed = true;
        }

        if (c.getMood() <= 0) {
            c.setMood(70);
            changed = true;
        }

        if (c.getUpdatedAt() <= 0) {
            c.setUpdatedAt(System.currentTimeMillis());
            changed = true;
        }

        if (c.getCharacterImageResId() == 0) {
            c.setCharacterImageResId(R.drawable.lv1);
            changed = true;
        }

        int imageBefore = c.getCharacterImageResId();
        c.refreshCharacterImage();
        if (c.getCharacterImageResId() != imageBefore) changed = true;

        return changed;
    }

    // -------------------------
    // SZOKÁSOK
    // -------------------------
    public void saveHabits(List<Habit> habits) {
        if (habits == null) return;
        String json = gson.toJson(habits);
        prefs.edit().putString(KEY_HABITS, json).apply();
        Log.d("StorageHelper", "Szokások mentve: " + habits.size());
    }

    public List<Habit> loadHabits() {
        String json = prefs.getString(KEY_HABITS, null);
        if (json == null) return new ArrayList<>();

        try {
            Type type = new TypeToken<List<Habit>>() {}.getType();
            List<Habit> list = gson.fromJson(json, type);
            if (list == null) list = new ArrayList<>();
            if (migrateHabits(list)) {
                saveHabits(list);
                Log.d("StorageHelper", "Szokások migrálva és újramentve.");
            }
            Log.d("StorageHelper", "Szokások betöltve: " + list.size());
            return list;
        } catch (Exception e) {
            Log.e("StorageHelper", "Hiba a szokások betöltésekor: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Régi mentések pótlása: hiányzó id-k generálása, ütemezés "minden nap"-ra állítása.
     * Kiszűri a null elemeket is. Importnál is ugyanez fut.
     * @return true, ha bármi változott (vissza kell menteni)
     */
    public static boolean migrateHabits(List<Habit> habits) {
        return migrateHabits(habits, java.time.LocalDate.now());
    }

    /** Migráció adott "mai" nappal (tesztelhető). */
    public static boolean migrateHabits(List<Habit> habits, java.time.LocalDate today) {
        if (habits == null) return false;
        boolean changed = habits.removeIf(h -> h == null);
        for (Habit h : habits) {
            if (h.migrate(today)) changed = true;
        }
        return changed;
    }

    // -------------------------
    // ACHIEVEMENTEK
    // -------------------------
    public void saveAchievements(List<Achievement> achievements) {
        if (achievements == null) return;
        String json = gson.toJson(achievements);
        prefs.edit().putString(KEY_ACHIEVEMENTS, json).apply();
        Log.d("StorageHelper", "Achievementek mentve: " + achievements.size());
    }

    public List<Achievement> loadAchievements() {
        String json = prefs.getString(KEY_ACHIEVEMENTS, null);
        if (json == null) return new ArrayList<>();

        try {
            Type type = new TypeToken<List<Achievement>>() {}.getType();
            List<Achievement> list = gson.fromJson(json, type);
            if (list == null) list = new ArrayList<>();
            Log.d("StorageHelper", "Achievementek betöltve: " + list.size());
            return list;
        } catch (Exception e) {
            Log.e("StorageHelper", "Hiba az achievementek betöltésekor: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // -------------------------
    // BEÁLLÍTÁSOK
    // -------------------------
    public boolean isWaterReminderEnabled() {
        return prefs.getBoolean(KEY_WATER_REMINDER, true);
    }

    public void setWaterReminderEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_WATER_REMINDER, enabled).apply();
    }

    /** Első indítás bevezetője lefutott-e (meglévő felhasználóknál a repository állítja be). */
    public boolean isOnboardingDone() {
        return prefs.getBoolean(KEY_ONBOARDING_DONE, false);
    }

    public void setOnboardingDone(boolean done) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply();
    }

    /** Az utoljára megünnepelt szint; 0, ha még nincs mentve (régi mentés). */
    public int getCelebratedLevel() {
        return prefs.getInt(KEY_CELEBRATED_LEVEL, 0);
    }

    /** A még nem nyugtázott szintlépés(ek) óta szerzett érme. */
    public int getPendingLevelCoins() {
        return prefs.getInt(KEY_PENDING_LEVEL_COINS, 0);
    }

    public void saveCelebrationState(int celebratedLevel, int pendingCoins) {
        prefs.edit()
                .putInt(KEY_CELEBRATED_LEVEL, celebratedLevel)
                .putInt(KEY_PENDING_LEVEL_COINS, pendingCoins)
                .apply();
    }

    /** Van-e már mentett karakter vagy szokáslista (= meglévő felhasználó). */
    public boolean hasSavedUserData() {
        return prefs.contains(KEY_CHARACTER) || prefs.contains(KEY_HABITS);
    }

    // -------------------------
    // RESET / DEBUG
    // -------------------------
    public void clearAllData() {
        prefs.edit().clear().apply();
        Log.d("StorageHelper", "Minden adat törölve a HabitQuest-ből.");
    }
}
