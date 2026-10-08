package com.example.habitquest.model;

import java.time.LocalDate; // API 26+
import java.time.format.DateTimeFormatter;

/**
  Egy szokás, amit a felhasználó rendszeresen teljesíthet.
  Minden teljesítés XP-t ad, és számolja a napi / összes teljesítést.
 */
public class Habit {
    private String name;
    private String description;
    private int experienceReward;

    // új mezők
    private int completionsToday = 0;
    private String lastCompletionDate = "";
    private int completionCount = 0;

    // streak mezők
    private int currentStreak = 0;
    private int longestStreak = 0;

    public Habit(String name, String description, int experienceReward) {
        this.name = name;
        this.description = description;
        this.experienceReward = experienceReward;
    }

    /**
      Szokás teljesítése
      - ha uj nap kezdodik: napi szamlalo reset, streak frissítés
      - napi es osszesitett szamlalo noveles
     */
    public void complete() {
        LocalDate today = LocalDate.now();
        String todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE);

        if (!todayStr.equals(lastCompletionDate)) {
            completionsToday = 0;

            if (lastCompletionDate != null && !lastCompletionDate.isEmpty()) {
                LocalDate last = LocalDate.parse(lastCompletionDate, DateTimeFormatter.ISO_LOCAL_DATE);
                if (last.plusDays(1).equals(today)) {
                    currentStreak++; // tegnap is teljesítve -> folytatódik a sorozat
                } else {
                    currentStreak = 1; // kihagyott nap -> újrakezdődik
                }
            } else {
                currentStreak = 1; // első teljesítés
            }

            if (currentStreak > longestStreak) longestStreak = currentStreak;
            lastCompletionDate = todayStr;
        }
        completionsToday++;
        completionCount++; // összes teljesítés növelése
    }

    //  GETTEREK / SETTEREK
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getExperienceReward() { return experienceReward; }
    public void setExperienceReward(int experienceReward) { this.experienceReward = experienceReward; }

    public int getCurrentStreak() { return currentStreak; }
    public void setCurrentStreak(int currentStreak) { this.currentStreak = currentStreak; }

    public int getLongestStreak() { return longestStreak; }
    public void setLongestStreak(int longestStreak) { this.longestStreak = longestStreak; }

    public int getCompletionsToday() { return completionsToday; }
    public void setCompletionsToday(int completionsToday) { this.completionsToday = completionsToday; }

    public String getLastCompletionDate() { return lastCompletionDate; }
    public void setLastCompletionDate(String lastCompletionDate) { this.lastCompletionDate = lastCompletionDate; }

    public int getCompletionCount() { return completionCount; }
    public void setCompletionCount(int completionCount) { this.completionCount = completionCount; }
}
