package com.example.habitquest.utils;

import com.example.habitquest.model.Achievement;
import com.example.habitquest.model.Habit;
import com.example.habitquest.model.UserCharacter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mentés és visszaállítás egyetlen JSON fájlba (telefonváltáshoz).
 *
 * Formátum (schemaVersion: 1):
 * {
 *   "app": "HabitQuest", "schemaVersion": 1, "exportedAt": "2026-11-14T10:00:00Z",
 *   "character": {...}, "habits": [...], "achievements": [...],
 *   "settings": { "waterReminderEnabled": true }
 * }
 *
 * Android-független (csak Gson), így sima unit tesztből is tesztelhető.
 * Importnál ugyanaz a migráció fut, mint a SharedPreferences betöltésekor.
 */
public final class BackupManager {

    public static final int SCHEMA_VERSION = 1;
    public static final String APP_ID = "HabitQuest";
    public static final String MIME_TYPE = "application/json";
    /** Ennél nagyobb fájlt nem olvasunk be (védelem véletlenül kiválasztott óriásfájl ellen). */
    static final int MAX_FILE_BYTES = 5 * 1024 * 1024;

    /** Miért nem sikerült az import – a UI ebből választ barátságos üzenetet. */
    public enum Error {
        /** Nem olvasható / üres / nem JSON / túl nagy. */
        NOT_JSON,
        /** JSON, de nem HabitQuest mentés (nincs schemaVersion). */
        NOT_HABITQUEST,
        /** Újabb app-verzióval készült mentés. */
        NEWER_VERSION,
        /** Hiányzó vagy érvénytelen adat (pl. nincs karakter vagy szokáslista). */
        INVALID_DATA
    }

    /** A teljes mentés tartalma. */
    public static final class BackupData {
        String app = APP_ID;
        int schemaVersion;
        String exportedAt;
        UserCharacter character;
        List<Habit> habits;
        List<Achievement> achievements;
        Settings settings;

        public UserCharacter getCharacter() { return character; }
        public List<Habit> getHabits() { return habits; }
        public List<Achievement> getAchievements() { return achievements; }
        public int getSchemaVersion() { return schemaVersion; }
        public String getExportedAt() { return exportedAt; }
        /** null, ha a mentésben nem szerepelt. */
        public Boolean getWaterReminderEnabled() {
            return settings != null ? settings.waterReminderEnabled : null;
        }
    }

    static final class Settings {
        Boolean waterReminderEnabled;
    }

    /** Az import eredménye: vagy érvényes adat, vagy egy hibakód. */
    public static final class ImportResult {
        private final BackupData data;
        private final Error error;

        private ImportResult(BackupData data, Error error) {
            this.data = data;
            this.error = error;
        }

        static ImportResult ok(BackupData data) { return new ImportResult(data, null); }
        static ImportResult fail(Error error) { return new ImportResult(null, error); }

        public boolean isSuccess() { return data != null; }
        public BackupData getData() { return data; }
        public Error getError() { return error; }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BackupManager() { }

    // -------------------------
    // EXPORT
    // -------------------------

    public static String toJson(UserCharacter character, List<Habit> habits,
                                List<Achievement> achievements, boolean waterReminderEnabled) {
        BackupData data = new BackupData();
        data.schemaVersion = SCHEMA_VERSION;
        data.exportedAt = Instant.now().toString();
        data.character = character;
        data.habits = habits != null ? habits : new ArrayList<>();
        data.achievements = achievements != null ? achievements : new ArrayList<>();
        data.settings = new Settings();
        data.settings.waterReminderEnabled = waterReminderEnabled;
        return GSON.toJson(data);
    }

    public static void write(OutputStream out, String json) throws IOException {
        out.write(json.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    // -------------------------
    // IMPORT
    // -------------------------

    /** Beolvasás streamből; soha nem dob kivételt, hibánál ImportResult.fail-t ad. */
    public static ImportResult read(InputStream in) {
        if (in == null) return ImportResult.fail(Error.NOT_JSON);
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, n);
                if (buffer.size() > MAX_FILE_BYTES) return ImportResult.fail(Error.NOT_JSON);
            }
            return parse(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return ImportResult.fail(Error.NOT_JSON);
        }
    }

    /** JSON szöveg validálása és betöltése; soha nem dob kivételt. */
    public static ImportResult parse(String json) {
        if (json == null || json.trim().isEmpty()) return ImportResult.fail(Error.NOT_JSON);

        // 1) érvényes JSON objektum-e
        JsonObject root;
        try {
            JsonElement el = JsonParser.parseString(stripBom(json));
            if (!el.isJsonObject()) return ImportResult.fail(Error.NOT_JSON);
            root = el.getAsJsonObject();
        } catch (RuntimeException e) {
            return ImportResult.fail(Error.NOT_JSON);
        }

        // 2) HabitQuest mentés-e, és kezeljük-e a verzióját
        int version;
        try {
            if (!root.has("schemaVersion")) return ImportResult.fail(Error.NOT_HABITQUEST);
            version = root.get("schemaVersion").getAsInt();
        } catch (RuntimeException e) {
            return ImportResult.fail(Error.NOT_HABITQUEST);
        }
        if (version < 1) return ImportResult.fail(Error.NOT_HABITQUEST);
        if (version > SCHEMA_VERSION) return ImportResult.fail(Error.NEWER_VERSION);

        // 3) tartalom
        BackupData data;
        try {
            data = GSON.fromJson(root, BackupData.class);
        } catch (RuntimeException e) {
            return ImportResult.fail(Error.INVALID_DATA);
        }
        if (data == null || data.character == null || data.habits == null) {
            return ImportResult.fail(Error.INVALID_DATA);
        }
        if (data.character.getLevel() < 1) return ImportResult.fail(Error.INVALID_DATA);

        // szokások: null elemek ki, hiányzó mezők pótlása, név kötelező, id-k egyediek
        StorageHelper.migrateHabits(data.habits);
        Set<String> seenIds = new HashSet<>();
        List<Habit> uniqueHabits = new ArrayList<>();
        for (Habit h : data.habits) {
            if (h.getName() == null) return ImportResult.fail(Error.INVALID_DATA);
            if (!seenIds.add(h.getId())) continue; // duplikált id → a másodpéldányt kihagyjuk
            uniqueHabits.add(h);
        }
        data.habits = uniqueHabits;

        // achievementek: opcionális; id nélküli elemek kihagyva
        List<Achievement> achievements = new ArrayList<>();
        if (data.achievements != null) {
            for (Achievement a : data.achievements) {
                if (a != null && a.getId() != null) achievements.add(a);
            }
        }
        data.achievements = achievements;

        StorageHelper.migrateCharacter(data.character);
        return ImportResult.ok(data);
    }

    private static String stripBom(String s) {
        return s.startsWith("﻿") ? s.substring(1) : s;
    }

    /** Javasolt fájlnév, pl. "habitquest-mentes-2026-11-14.json". */
    public static String suggestedFileName(java.time.LocalDate date) {
        return "habitquest-mentes-" + date + ".json";
    }
}
