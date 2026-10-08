package com.example.habitquest.model;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class UserCharacterTest {

    private UserCharacter character;

    @Before
    public void setUp() {
        character = new UserCharacter();
    }

    // --- XP és szintlépés ---

    @Test
    public void addExperience_increasesXP() {
        character.addExperience(50);
        assertEquals(50, character.getExperience());
    }

    @Test
    public void addExperience_triggersLevelUp() {
        character.addExperience(100); // 1. szint küszöbe: 1 * 100
        assertEquals(2, character.getLevel());
    }

    @Test
    public void addExperience_xpResetsAfterLevelUp() {
        character.addExperience(110); // 100 = szintlépés, 10 marad
        assertEquals(10, character.getExperience());
    }

    @Test
    public void addExperience_multipleLeveUps() {
        character.addExperience(300); // elég 3 szintlépéshez
        assertTrue(character.getLevel() > 2);
    }

    @Test
    public void addExperience_ignoresNegativeValue() {
        character.addExperience(-10);
        assertEquals(0, character.getExperience());
    }

    // --- Clamp ---

    @Test
    public void setHealth_clampsToMax() {
        character.setHealth(9999);
        assertEquals(character.getHealthMax(), character.getHealth());
    }

    @Test
    public void setHealth_clampsToZero() {
        character.setHealth(-50);
        assertEquals(0, character.getHealth());
    }

    @Test
    public void setMood_clampsTo100() {
        character.setMood(200);
        assertEquals(100, character.getMood());
    }

    // --- Szintlépés jutalmak ---

    @Test
    public void levelUp_increasesHealthMax() {
        int before = character.getHealthMax();
        character.addExperience(100);
        assertEquals(before + 5, character.getHealthMax());
    }

    @Test
    public void levelUp_fillsHealthToMax() {
        character.setHealth(10);
        character.addExperience(100);
        assertEquals(character.getHealthMax(), character.getHealth());
    }

    @Test
    public void levelUp_increasesMood() {
        character.setMood(50);
        character.addExperience(100);
        assertEquals(60, character.getMood());
    }
}
