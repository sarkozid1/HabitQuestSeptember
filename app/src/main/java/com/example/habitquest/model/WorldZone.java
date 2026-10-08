package com.example.habitquest.model;

import com.example.habitquest.R;

/**
 * Map leírása: háttér, path stílus, szinttartomány.
 *
 * JAVÍTÁSOK:
 * - #6: Zóna nevek egyeztetése a MapPathView switch-ével
 *       ("Erdő" → "Forest", "Hegység" → "Mountain", "Város" → "City")
 * - #12: CITY maxLevel 15 → 99
 */
public class WorldZone {

    private final String name;
    private final int minLevel;
    private final int maxLevel;
    private final int backgroundResId;

    public WorldZone(String name, int minLevel, int maxLevel, int backgroundResId) {
        this.name = name;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.backgroundResId = backgroundResId;
    }

    public String getName() { return name; }
    public int getMinLevel() { return minLevel; }
    public int getMaxLevel() { return maxLevel; }
    public int getBackgroundResId() { return backgroundResId; }

    public boolean containsLevel(int level) {
        return level >= minLevel && level <= maxLevel;
    }



    public static final WorldZone FOREST =
            new WorldZone("Forest", 1, 5, R.drawable.map_forest);
    public static final WorldZone MOUNTAIN =
            new WorldZone("Mountain", 6, 10, R.drawable.map_background);
    public static final WorldZone CITY =
            new WorldZone("City", 11, 99, R.drawable.map_city);
}