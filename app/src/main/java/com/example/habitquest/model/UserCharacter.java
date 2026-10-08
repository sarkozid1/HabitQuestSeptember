package com.example.habitquest.model;

import com.example.habitquest.R;

import java.util.HashMap;
import java.util.Map;

/**
 * Tamagotchi-szerű RPG karakter:
 * - XP, level, class, coins, avatar + képek
 * - Statok: HP + Energy + Mood (romlás/jutalom később WorkManagerrel)
 */
public class UserCharacter {

    // --- Karakterkép mapping kasztonként ---
    private static final Map<String, int[]> CLASS_IMAGES = new HashMap<>();
    static {
        CLASS_IMAGES.put("warrior", new int[]{
                R.drawable.lv1, R.drawable.lv2, R.drawable.lv3, R.drawable.lv4,
                R.drawable.lv5, R.drawable.lv6, R.drawable.lv7, R.drawable.lv8
        });
        CLASS_IMAGES.put("mage", new int[]{
                R.drawable.mage_lv1, R.drawable.mage_lv2,
                R.drawable.mage_lv3, R.drawable.mage_lv4
        });
        CLASS_IMAGES.put("rogue", new int[]{
                R.drawable.rogue_lv1, R.drawable.rogue_lv2, R.drawable.rogue_lv3,
                R.drawable.rogue_lv4, R.drawable.rogue_lv5
        });
    }

    // --- Alap statok / progression ---
    private int level;
    private int experience;

    private int health;
    private int healthMax;

    private int energy;
    private int energyMax;

    private int mood;

    private long updatedAt;

    private String characterClass;

    private int coins;

    private String avatarName;
    private int characterImageResId;

    public UserCharacter() {
        this.level = 1;
        this.experience = 0;
        this.healthMax = 100;
        this.health = 100;
        this.energyMax = 100;
        this.energy = 70;
        this.mood = 70;
        this.coins = 0;
        this.avatarName = "Hős";
        this.characterClass = "none";
        this.characterImageResId = R.drawable.lv1;
        this.updatedAt = System.currentTimeMillis();
    }

    // -------------------------
    // XP és szintkezelés
    // -------------------------
    public void addExperience(int amount) {
        if (amount <= 0) return;
        this.experience += amount;

        while (this.experience >= getXpToNextLevel()) {
            this.experience -= getXpToNextLevel();
            levelUp();
        }
    }

    private void levelUp() {
        this.level++;
        this.healthMax += 5;
        this.energyMax += 5;
        this.health = this.healthMax;
        this.energy = this.energyMax;
        this.mood = clamp(this.mood + 10, 0, 100);
        switch (characterClass) {
            case "warrior":
                addCoins(100 + (level * 20));
                addExperience(5);
                break;
            case "mage":
                addCoins(150);
                addExperience(10);
                break;
            case "rogue":
                addCoins(80);
                break;
            default:
                addCoins(100);
                break;
        }
        updateCharacterImage();
        touch();
    }

    // -------------------------
    // Tamagotchi core: jutalom / romlás
    // -------------------------
    public void applyReward(int hpDelta, int energyDelta, int moodDelta, int xpDelta, int coinDelta) {
        setHealth(this.health + hpDelta);
        setEnergy(this.energy + energyDelta);
        setMood(this.mood + moodDelta);
        if (xpDelta > 0) addExperience(xpDelta);
        if (coinDelta > 0) addCoins(coinDelta);
        touch();
    }

    public void applyDecay(int hpDelta, int energyDelta, int moodDelta) {
        setHealth(this.health + hpDelta);
        setEnergy(this.energy + energyDelta);
        setMood(this.mood + moodDelta);
        touch();
    }

    private void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // -------------------------
    // Kép frissítés — Map alapú, bővíthető megoldás
    // -------------------------
    private void updateCharacterImage() {
        int[] images = CLASS_IMAGES.getOrDefault(characterClass,
                new int[]{ R.drawable.lv1 });
        int index = Math.min(level - 1, images.length - 1);
        characterImageResId = images[index];
    }

    // -------------------------
    // Getters / Setters
    // -------------------------
    public String getCharacterClass() { return characterClass; }
    public void setCharacterClass(String characterClass) {
        this.characterClass = characterClass;
        updateCharacterImage();
        touch();
    }

    public int getXpToNextLevel() { return level * 100; }

    public void addCoins(int amount) { if (amount > 0) coins += amount; }
    public void spendCoins(int amount) { if (amount > 0 && coins >= amount) coins -= amount; }

    public int getLevel() { return level; }
    public void setLevel(int level) {
        this.level = Math.max(1, level);
        updateCharacterImage();
        touch();
    }

    public int getExperience() { return experience; }
    public void setExperience(int experience) {
        this.experience = Math.max(0, experience);
        touch();
    }

    public void setHealthMax(int healthMax) {
        this.healthMax = Math.max(1, healthMax);
        if (this.health > this.healthMax) this.health = this.healthMax;
    }

    public void setEnergyMax(int energyMax) {
        this.energyMax = Math.max(1, energyMax);
        if (this.energy > this.energyMax) this.energy = this.energyMax;
    }

    public int getHealth() { return health; }
    public int getHealthMax() { return healthMax; }
    public void setHealth(int health) { this.health = clamp(health, 0, healthMax); }
    public void setEnergy(int energy) { this.energy = clamp(energy, 0, energyMax); }
    public void setMood(int mood) { this.mood = clamp(mood, 0, 100); }

    public int getEnergy() { return energy; }
    public int getEnergyMax() { return energyMax; }

    public int getMood() { return mood; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public int getCoins() { return coins; }
    public void setCoins(int coins) { this.coins = Math.max(0, coins); }

    public String getAvatarName() { return avatarName; }
    public void setAvatarName(String avatarName) { this.avatarName = avatarName; }

    public int getCharacterImageResId() { return characterImageResId; }
    public void setCharacterImageResId(int characterImageResId) { this.characterImageResId = characterImageResId; }
}