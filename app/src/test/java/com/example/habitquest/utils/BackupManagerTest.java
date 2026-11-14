package com.example.habitquest.utils;

import com.example.habitquest.model.Achievement;
import com.example.habitquest.model.Habit;
import com.example.habitquest.model.Schedule;
import com.example.habitquest.model.UserCharacter;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

// Robolectric: a karakter-migráció R.drawable azonosítókat használ
@RunWith(RobolectricTestRunner.class)
public class BackupManagerTest {

    private UserCharacter character;
    private List<Habit> habits;
    private List<Achievement> achievements;

    @Before
    public void setUp() {
        character = new UserCharacter();
        character.setCharacterClass("mage");
        character.addExperience(350); // szintlépések
        character.addCoins(123);
        character.setMood(55);

        Habit water = new Habit("Víz", "Igyál vizet", 10);
        water.complete(LocalDate.of(2026, 11, 13));
        water.complete(LocalDate.of(2026, 11, 14));
        water.setReminderMinutes(9 * 60 + 15);

        Habit gym = new Habit("Edzés", "Konditerem", 30);
        gym.setSchedule(Schedule.weekdays(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));

        Habit read = new Habit("Olvasás", "", 15);
        read.setSchedule(Schedule.timesPerWeek(3));
        read.complete(LocalDate.of(2026, 11, 10));

        habits = new ArrayList<>(Arrays.asList(water, gym, read));
        achievements = new ArrayList<>(Arrays.asList(
                new Achievement("first_xp", "Első lépés", "Szerezz 10 XP-t.", true),
                new Achievement("drink_10", "Vízivó", "10× víz", false, 10, "Víz")));
    }

    private static final Gson GSON = new Gson();

    // --- oda-vissza ---

    @Test
    public void exportThenImport_returnsSameData() {
        String json = BackupManager.toJson(character, habits, achievements, false);

        BackupManager.ImportResult result = BackupManager.parse(json);

        assertTrue(result.isSuccess());
        BackupManager.BackupData data = result.getData();
        // mezőről mezőre azonos (Gson-szerializálással összehasonlítva)
        assertEquals(GSON.toJson(character), GSON.toJson(data.getCharacter()));
        assertEquals(GSON.toJson(habits), GSON.toJson(data.getHabits()));
        assertEquals(GSON.toJson(achievements), GSON.toJson(data.getAchievements()));
        assertEquals(Boolean.FALSE, data.getWaterReminderEnabled());
    }

    @Test
    public void exportThenImport_viaStreams_returnsSameData() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BackupManager.write(out, BackupManager.toJson(character, habits, achievements, true));

        BackupManager.ImportResult result =
                BackupManager.read(new ByteArrayInputStream(out.toByteArray()));

        assertTrue(result.isSuccess());
        assertEquals(GSON.toJson(habits), GSON.toJson(result.getData().getHabits()));
        assertEquals(Boolean.TRUE, result.getData().getWaterReminderEnabled());
    }

    @Test
    public void exportThenImport_keepsIdsScheduleStreakAndReminder() {
        BackupManager.BackupData data =
                BackupManager.parse(BackupManager.toJson(character, habits, achievements, true)).getData();

        Habit water = data.getHabits().get(0);
        assertEquals(habits.get(0).getId(), water.getId());
        assertEquals(2, water.getCurrentStreak());
        assertEquals(Integer.valueOf(9 * 60 + 15), water.getReminderMinutes());
        assertEquals(habits.get(1).getSchedule(), data.getHabits().get(1).getSchedule());
        assertEquals(Schedule.timesPerWeek(3), data.getHabits().get(2).getSchedule());
        assertEquals(character.getLevel(), data.getCharacter().getLevel());
        assertEquals(character.getCoins(), data.getCharacter().getCoins());
    }

    @Test
    public void export_containsSchemaVersion() {
        JsonObject root = JsonParser.parseString(
                BackupManager.toJson(character, habits, achievements, true)).getAsJsonObject();
        assertEquals(BackupManager.SCHEMA_VERSION, root.get("schemaVersion").getAsInt());
        assertEquals("HabitQuest", root.get("app").getAsString());
        assertTrue(root.has("exportedAt"));
    }

    // --- hibás fájlok: barátságos hiba, nincs kivétel ---

    @Test
    public void import_garbage_isNotJson() {
        assertEquals(BackupManager.Error.NOT_JSON, BackupManager.parse("ez nem json {{{").getError());
        assertEquals(BackupManager.Error.NOT_JSON, BackupManager.parse("").getError());
        assertEquals(BackupManager.Error.NOT_JSON, BackupManager.parse(null).getError());
        assertEquals(BackupManager.Error.NOT_JSON, BackupManager.parse("[1,2,3]").getError());
    }

    @Test
    public void import_binaryStream_isNotJson() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0, 1, 2, 3};
        assertEquals(BackupManager.Error.NOT_JSON,
                BackupManager.read(new ByteArrayInputStream(png)).getError());
    }

    @Test
    public void import_failingStream_isNotJson() {
        InputStream broken = new InputStream() {
            @Override public int read() throws java.io.IOException {
                throw new java.io.IOException("lemez hiba");
            }
        };
        assertEquals(BackupManager.Error.NOT_JSON, BackupManager.read(broken).getError());
        assertEquals(BackupManager.Error.NOT_JSON, BackupManager.read(null).getError());
    }

    @Test
    public void import_otherJson_isNotHabitQuest() {
        assertEquals(BackupManager.Error.NOT_HABITQUEST,
                BackupManager.parse("{\"name\":\"valami más\"}").getError());
        assertEquals(BackupManager.Error.NOT_HABITQUEST,
                BackupManager.parse("{\"schemaVersion\":\"abc\"}").getError());
        assertEquals(BackupManager.Error.NOT_HABITQUEST,
                BackupManager.parse("{\"schemaVersion\":0}").getError());
    }

    @Test
    public void import_newerVersion_isRejected() {
        String json = BackupManager.toJson(character, habits, achievements, true)
                .replace("\"schemaVersion\": 1", "\"schemaVersion\": 99");
        assertEquals(BackupManager.Error.NEWER_VERSION, BackupManager.parse(json).getError());
    }

    @Test
    public void import_missingCharacterOrHabits_isInvalid() {
        assertEquals(BackupManager.Error.INVALID_DATA,
                BackupManager.parse("{\"schemaVersion\":1,\"habits\":[]}").getError());
        assertEquals(BackupManager.Error.INVALID_DATA,
                BackupManager.parse("{\"schemaVersion\":1,\"character\":{\"level\":3}}").getError());
    }

    @Test
    public void import_wrongTypes_isInvalid() {
        assertEquals(BackupManager.Error.INVALID_DATA, BackupManager.parse(
                "{\"schemaVersion\":1,\"character\":{\"level\":3},\"habits\":\"nem lista\"}").getError());
        assertEquals(BackupManager.Error.INVALID_DATA, BackupManager.parse(
                "{\"schemaVersion\":1,\"character\":{\"level\":0},\"habits\":[]}").getError());
    }

    @Test
    public void import_habitWithoutName_isInvalid() {
        assertEquals(BackupManager.Error.INVALID_DATA, BackupManager.parse(
                "{\"schemaVersion\":1,\"character\":{\"level\":2},\"habits\":[{\"experienceReward\":5}]}")
                .getError());
    }

    // --- tűrőképesség / migráció ---

    @Test
    public void import_minimalFile_fillsDefaults() {
        BackupManager.ImportResult result = BackupManager.parse(
                "{\"schemaVersion\":1,\"character\":{\"level\":2},"
                        + "\"habits\":[{\"name\":\"Séta\",\"experienceReward\":15}, null]}");

        assertTrue(result.isSuccess());
        BackupManager.BackupData data = result.getData();
        assertEquals(1, data.getHabits().size());
        Habit h = data.getHabits().get(0);
        assertNotNull("hiányzó id generálva", h.getId());
        assertEquals(Schedule.Type.DAILY, h.getSchedule().getType());
        assertTrue(data.getAchievements().isEmpty());
        assertNull(data.getWaterReminderEnabled());
        assertTrue("karakter migrálva", data.getCharacter().getHealthMax() > 0);
        assertNotEquals(0, data.getCharacter().getCharacterImageResId());
    }

    @Test
    public void import_duplicateHabitIds_keepsFirstOnly() {
        String json = "{\"schemaVersion\":1,\"character\":{\"level\":1},\"habits\":["
                + "{\"id\":\"a\",\"name\":\"Egy\"},{\"id\":\"a\",\"name\":\"Kettő\"}]}";
        BackupManager.BackupData data = BackupManager.parse(json).getData();
        assertEquals(1, data.getHabits().size());
        assertEquals("Egy", data.getHabits().get(0).getName());
    }

    @Test
    public void import_utf8BomAndAccents_work() throws Exception {
        String json = "﻿" + BackupManager.toJson(character, habits, achievements, true);
        BackupManager.ImportResult result = BackupManager.read(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        assertTrue(result.isSuccess());
        assertEquals("Víz", result.getData().getHabits().get(0).getName());
    }

    @Test
    public void import_tooLargeFile_isRejected() {
        byte[] big = new byte[BackupManager.MAX_FILE_BYTES + 10];
        Arrays.fill(big, (byte) ' ');
        assertEquals(BackupManager.Error.NOT_JSON,
                BackupManager.read(new ByteArrayInputStream(big)).getError());
    }

    @Test
    public void suggestedFileName_containsDate() {
        assertEquals("habitquest-mentes-2026-11-14.json",
                BackupManager.suggestedFileName(LocalDate.of(2026, 11, 14)));
    }
}
