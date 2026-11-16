package com.example.habitquest.model;

import java.util.Locale;

/**
 * Szokás teljesítésének jutalma (XP, érme, HP / Energia / Hangulat).
 * Tiszta logika – az app, a widget és a háttérfeladatok ugyanezt használják
 * (a HabitRepository-n keresztül).
 */
public final class RewardRules {

    private RewardRules() { }

    /** Egy teljesítés jutalma. */
    public static final class Reward {
        public final int hp, energy, mood, xp, coins;

        Reward(int hp, int energy, int mood, int xp, int coins) {
            this.hp = hp;
            this.energy = energy;
            this.mood = mood;
            this.xp = xp;
            this.coins = coins;
        }
    }

    /** A szokás neve alapján (víz / séta / tanulás) eltérő stat-jutalom, egyébként alapjutalom. */
    public static Reward forHabit(Habit habit) {
        String title = habit.getName() == null ? "" : habit.getName().toLowerCase(Locale.ROOT);
        int xp = habit.getExperienceReward();

        if (title.contains("víz") || title.contains("water")) {
            return new Reward(3, 8, 2, xp, 8);
        } else if (title.contains("séta") || title.contains("walk") || title.contains("mozg")) {
            return new Reward(5, -3, 6, xp, 12);   // fáraszt, de egészséges
        } else if (title.contains("tanul") || title.contains("study")) {
            return new Reward(1, -6, 3, xp, 15);
        }
        return new Reward(2, 2, 1, xp, 5);          // minden szokás ad egy kicsit
    }

    /** A jutalom alkalmazása a karakterre. */
    public static void apply(UserCharacter user, Habit habit) {
        Reward r = forHabit(habit);
        user.applyReward(r.hp, r.energy, r.mood, r.xp, r.coins);
    }
}
