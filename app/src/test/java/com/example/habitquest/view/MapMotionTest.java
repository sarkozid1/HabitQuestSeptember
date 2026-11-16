package com.example.habitquest.view;

import org.junit.Test;

import static org.junit.Assert.*;

/** A térképes járás mozgásgörbéi. */
public class MapMotionTest {

    private static final float EPS = 1e-4f;

    @Test
    public void hopCount_proportionalToDistance_minimumTwo() {
        assertEquals(2, MapMotion.hopCount(10f, 100f));
        assertEquals(5, MapMotion.hopCount(500f, 100f));
        assertEquals(5, MapMotion.hopCount(-500f, 100f)); // visszafelé is
        assertEquals(2, MapMotion.hopCount(500f, 0f));
    }

    @Test
    public void hopHeight_groundedAtStartEndAndBetweenSteps() {
        int hops = 4;
        assertEquals(0f, MapMotion.hopHeight(0f, hops), EPS);
        assertEquals(0f, MapMotion.hopHeight(1f, hops), EPS);
        assertEquals(0f, MapMotion.hopHeight(0.25f, hops), EPS);   // két lépés között
        assertEquals(1f, MapMotion.hopHeight(0.125f, hops), EPS);  // egy lépés csúcsán
        for (float t = 0; t <= 1f; t += 0.01f) {
            float h = MapMotion.hopHeight(t, hops);
            assertTrue(h >= 0f && h <= 1f);
        }
    }

    @Test
    public void hopHeight_clampsOutOfRangeProgress() {
        assertEquals(0f, MapMotion.hopHeight(-0.5f, 3), EPS);
        assertEquals(0f, MapMotion.hopHeight(1.5f, 3), EPS);
    }

    @Test
    public void tilt_zeroOnGroundAndAtApex_boundedOtherwise() {
        int hops = 3;
        assertEquals(0f, MapMotion.tilt(0f, hops), EPS);
        assertEquals(0f, MapMotion.tilt(1f / hops, hops), EPS);       // talaj
        assertEquals(0f, MapMotion.tilt(0.5f / hops, hops), EPS);     // csúcs
        for (float t = 0; t <= 1f; t += 0.01f) {
            assertTrue(Math.abs(MapMotion.tilt(t, hops)) <= MapMotion.MAX_TILT_DEG + EPS);
        }
    }

    @Test
    public void facing_followsHorizontalDirection_keepsItOnVerticalSegments() {
        assertEquals(1f, MapMotion.facing(0.9f, -1f), EPS);
        assertEquals(-1f, MapMotion.facing(-0.9f, 1f), EPS);
        assertEquals(-1f, MapMotion.facing(0.05f, -1f), EPS);  // szinte függőleges: marad
        assertEquals(1f, MapMotion.facing(0f, 0f), EPS);       // alapból jobbra
    }
}
