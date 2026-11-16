package com.example.habitquest.view;

/**
 * A térképes járás mozgásgörbéi – tiszta függvények, unit tesztelhetők.
 * Csak merev mozgás: eltolás, tükrözés, enyhe billenés (a rajz nem torzul).
 */
final class MapMotion {

    /** Legfeljebb ennyi fokot billen a hős lépés közben. */
    static final float MAX_TILT_DEG = 5f;

    private MapMotion() { }

    /** Hány "lépés-ugrás" legyen egy adott hosszú úton (minimum 2). */
    static int hopCount(float distancePx, float stepPx) {
        if (stepPx <= 0) return 2;
        return Math.max(2, Math.round(Math.abs(distancePx) / stepPx));
    }

    /**
     * Ugrás magassága 0..1 a haladás (0..1) függvényében: minden lépésnél egy kis ív,
     * a lépések között a talajon (0). Az út elején és végén 0.
     */
    static float hopHeight(float progress, int hops) {
        float p = clamp01(progress);
        return (float) Math.abs(Math.sin(p * hops * Math.PI));
    }

    /**
     * Billegés fokban: felfelé ívelve az egyik, lefelé a másik irányba dől;
     * a talajon és az ív csúcsán 0, így minden lépés egyenesen ér földet.
     */
    static float tilt(float progress, int hops) {
        float p = clamp01(progress);
        return (float) (Math.sin(p * hops * 2 * Math.PI) * MAX_TILT_DEG);
    }

    /**
     * Merre nézzen a hős: +1 jobbra, -1 balra (scaleX). Kis vízszintes mozgásnál
     * (főleg függőleges szakasz) megtartja az előző irányt, hogy ne "pörögjön".
     */
    static float facing(float tangentX, float previousFacing) {
        if (tangentX > 0.15f) return 1f;
        if (tangentX < -0.15f) return -1f;
        return previousFacing == 0 ? 1f : previousFacing;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
