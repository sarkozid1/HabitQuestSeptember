package com.example.habitquest.model;

/**
  Boltban megvásárolható tárgy. Megvásárláskor azonnal alkalmazódik
  a HP/Energy/Mood jutalma a karakterre (Tamagotchi-szerű "elfogyasztás").
 */
public class ShopItem {
    private final String id;
    private final String name;
    private final String description;
    private final int price;

    private final int hpBonus;
    private final int energyBonus;
    private final int moodBonus;

    private final String emoji;

    public ShopItem(String id, String name, String description, int price,
                     int hpBonus, int energyBonus, int moodBonus, String emoji) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.hpBonus = hpBonus;
        this.energyBonus = energyBonus;
        this.moodBonus = moodBonus;
        this.emoji = emoji;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getPrice() { return price; }
    public int getHpBonus() { return hpBonus; }
    public int getEnergyBonus() { return energyBonus; }
    public int getMoodBonus() { return moodBonus; }
    public String getEmoji() { return emoji; }
}
