package com.example.habitquest.model;

public class Achievement {
    private final String id;                 // Egyedi azonosító (pl. "drink_water_10")
    private final String title;              // Cím
    private final String description;        // Leírás
    private boolean unlocked;          // Fel van-e oldva

    private int targetCount;           // Hányszor kell teljesíteni a szokást
    private String linkedHabitName;    // Melyik szokáshoz tartozik

    // Alap konstruktor (régi achievementekhez)
    public Achievement(String id, String title, String description, boolean unlocked) {
        this(id, title, description, unlocked, 0, null);
    }

    // Új konstruktor (feltételes achievementhez)
    public Achievement(String id, String title, String description, boolean unlocked, int targetCount, String linkedHabitName) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.unlocked = unlocked;
        this.targetCount = targetCount;
        this.linkedHabitName = linkedHabitName;
    }

    // Getterek / Setterek
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public boolean isUnlocked() { return unlocked; }
    public void setUnlocked(boolean unlocked) { this.unlocked = unlocked; }

    public int getTargetCount() { return targetCount; }
    public void setTargetCount(int targetCount) { this.targetCount = targetCount; }

    public String getLinkedHabitName() { return linkedHabitName; }
    public void setLinkedHabitName(String linkedHabitName) { this.linkedHabitName = linkedHabitName; }
}
