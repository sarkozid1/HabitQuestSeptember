package com.example.habitquest.model;

import androidx.annotation.Nullable;

/**
 * Egy még nem nyugtázott szintlépés-ünneplés. Mentett állapotból számolódik
 * (az utoljára megünnepelt szint vs. a jelenlegi), így a widgetről vagy zárt appnál
 * történt szintlépést is megünnepli az app a következő megnyitáskor, több szintet
 * egyszerre összesítve, és forgatáskor sem indul újra.
 */
public final class LevelUpCelebration {

    public final int fromLevel;
    public final int toLevel;
    /** Az ünneplés óta (a szintlépő teljesítésekkel) szerzett érme. */
    public final int coinsGained;
    /** Új zóna, ha a szintlépéssel zónahatárt lépett át; különben null. */
    @Nullable public final WorldZone newZone;

    private LevelUpCelebration(int fromLevel, int toLevel, int coinsGained, @Nullable WorldZone newZone) {
        this.fromLevel = fromLevel;
        this.toLevel = toLevel;
        this.coinsGained = coinsGained;
        this.newZone = newZone;
    }

    /** Hány szintet lépett (≥ 1). */
    public int levelsGained() {
        return toLevel - fromLevel;
    }

    /**
     * @return ünneplés, ha a jelenlegi szint nagyobb az utoljára megünnepeltnél; különben null
     */
    @Nullable
    public static LevelUpCelebration pending(int celebratedLevel, int currentLevel, int coinsGained) {
        if (currentLevel <= celebratedLevel || celebratedLevel < 1) return null;
        WorldZone from = WorldZone.forLevel(celebratedLevel);
        WorldZone to = WorldZone.forLevel(currentLevel);
        return new LevelUpCelebration(celebratedLevel, currentLevel, Math.max(0, coinsGained),
                from == to ? null : to);
    }
}
