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
            boolean changed = false;

            if (loaded.getHealthMax() <= 0) {
                int hpMax = 100 + Math.max(0, loaded.getLevel() - 1) * 5;
                loaded.setHealthMax(hpMax);
                changed = true;
            }

            if (loaded.getEnergyMax() <= 0) {
                int enMax = 100 + Math.max(0, loaded.getLevel() - 1) * 5;
                loaded.setEnergyMax(enMax);
                changed = true;
            }

            if (loaded.getHealth() <= 0) {
                loaded.setHealth(loaded.getHealthMax());
                changed = true;
            }

            if (loaded.getEnergy() <= 0) {
                loaded.setEnergy(70);
                changed = true;
            }

            if (loaded.getMood() <= 0) {
                loaded.setMood(70);
                changed = true;
            }

            if (loaded.getUpdatedAt() <= 0) {
                loaded.setUpdatedAt(System.currentTimeMillis());
                changed = true;
            }

            if (loaded.getCharacterImageResId() == 0) {
                loaded.setCharacterImageResId(R.drawable.lv1);
                changed = true;
            }

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
            Log.d("StorageHelper", "Szokások betöltve: " + list.size());
            return list;
        } catch (Exception e) {
            Log.e("StorageHelper", "Hiba a szokások betöltésekor: " + e.getMessage());
            return new ArrayList<>();
        }
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
    // RESET / DEBUG
    // -------------------------
    public void clearAllData() {
        prefs.edit().clear().apply();
        Log.d("StorageHelper", "Minden adat törölve a HabitQuest-ből.");
    }
}
