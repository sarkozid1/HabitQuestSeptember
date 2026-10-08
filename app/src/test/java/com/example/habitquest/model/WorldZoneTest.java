package com.example.habitquest.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class WorldZoneTest {

    @Test
    public void forest_containsLevel1() {
        assertTrue(WorldZone.FOREST.containsLevel(1));
    }

    @Test
    public void forest_containsLevel5() {
        assertTrue(WorldZone.FOREST.containsLevel(5));
    }

    @Test
    public void forest_doesNotContainLevel6() {
        assertFalse(WorldZone.FOREST.containsLevel(6));
    }

    @Test
    public void mountain_containsLevel6() {
        assertTrue(WorldZone.MOUNTAIN.containsLevel(6));
    }

    @Test
    public void mountain_containsLevel10() {
        assertTrue(WorldZone.MOUNTAIN.containsLevel(10));
    }

    @Test
    public void mountain_doesNotContainLevel5() {
        assertFalse(WorldZone.MOUNTAIN.containsLevel(5));
    }

    @Test
    public void city_containsLevel11() {
        assertTrue(WorldZone.CITY.containsLevel(11));
    }

    @Test
    public void city_containsHighLevel() {
        assertTrue(WorldZone.CITY.containsLevel(99));
    }

    @Test
    public void city_doesNotContainLevel10() {
        assertFalse(WorldZone.CITY.containsLevel(10));
    }

    @Test
    public void noZoneContainsSameLevel() {
        // Egy szint csak egy zónához tartozhat
        int level = 6;
        int matchCount = 0;
        if (WorldZone.FOREST.containsLevel(level)) matchCount++;
        if (WorldZone.MOUNTAIN.containsLevel(level)) matchCount++;
        if (WorldZone.CITY.containsLevel(level)) matchCount++;
        assertEquals(1, matchCount);
    }
}
