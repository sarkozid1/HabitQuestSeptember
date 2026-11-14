package com.example.habitquest.model;

import java.time.DayOfWeek;
import java.time.LocalDate; // API 26+
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

/**
  Egy szokás, amit a felhasználó rendszeresen teljesíthet.
  Minden teljesítés XP-t ad, és számolja a napi / összes teljesítést.

  Ütemezés (Schedule): minden nap / a hét adott napjain / heti X alkalom.
  A streak a teljesített napokat számolja, és csak akkor szakad meg, ha egy
  ESEDÉKES alkalom kimaradt – a nem esedékes napok kihagyása nem töri meg.

  A dátumfüggő metódusok LocalDate paramétert kapnak, hogy tesztelhetők legyenek.
 */
public class Habit {
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    /** Stabil azonosító (UUID). Régi mentésekben hiányzik → StorageHelper migrálja. */
    private String id;
    private String name;
    private String description;
    private int experienceReward;

    // napi / összes számláló
    private int completionsToday = 0;
    private String lastCompletionDate = "";
    private int completionCount = 0;

    // streak mezők
    private int currentStreak = 0;
    private int longestStreak = 0;

    // ütemezés (régi mentésekben null → "minden nap")
    private Schedule schedule = Schedule.daily();

    // heti nyilvántartás a "heti X alkalom" ütemezéshez
    /** Az aktuálisan számolt hét hétfője (ISO dátum), "" ha még nincs. */
    private String weekStartDate = "";
    /** Hány KÜLÖNBÖZŐ napon volt teljesítve a weekStartDate hetében. */
    private int daysDoneThisWeek = 0;

    /** Emlékeztető időpontja éjfél óta eltelt percekben (pl. 8:30 = 510); null = nincs. */
    private Integer reminderMinutes = null;

    /** Gson-nak: így a mezők kezdőértékei régi JSON betöltésekor is lefutnak. */
    @SuppressWarnings("unused")
    private Habit() { }

    public Habit(String name, String description, int experienceReward) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.description = description;
        this.experienceReward = experienceReward;
    }

    /** Szokás teljesítése a mai napon. */
    public void complete() {
        complete(LocalDate.now());
    }

    /**
      Szokás teljesítése adott napon
      - ha új nap kezdődik: napi számláló reset, streak frissítés, heti számláló
      - napi és összesített számláló növelése
     */
    public void complete(LocalDate today) {
        String todayStr = today.format(ISO);

        if (!todayStr.equals(lastCompletionDate)) {
            completionsToday = 0;

            LocalDate last = getLastCompletionLocalDate();
            if (last == null || last.isAfter(today)) {
                currentStreak = 1; // első teljesítés (vagy visszaállított óra)
            } else if (isStreakBrokenBetween(last, today)) {
                currentStreak = 1; // kimaradt egy esedékes alkalom → újrakezdődik
            } else {
                currentStreak++;   // nem maradt ki esedékes alkalom → folytatódik
            }
            if (currentStreak > longestStreak) longestStreak = currentStreak;

            // heti nyilvántartás (minden ütemezésnél vezetjük, hogy váltáskor is pontos legyen)
            String monday = weekStart(today).format(ISO);
            if (!monday.equals(weekStartDate)) {
                weekStartDate = monday;
                daysDoneThisWeek = 0;
            }
            daysDoneThisWeek++;

            lastCompletionDate = todayStr;
        }
        completionsToday++;
        completionCount++; // összes teljesítés növelése
    }

    // -------------------------
    // Ütemezés szerinti lekérdezések
    // -------------------------

    /** Teljesítve volt-e az adott napon. */
    public boolean isCompletedOn(LocalDate date) {
        return date.format(ISO).equals(lastCompletionDate);
    }

    /** Az adott napi teljesítések száma (régi nap számlálója nem "lóg át"). */
    public int getCompletionsOn(LocalDate date) {
        return isCompletedOn(date) ? completionsToday : 0;
    }

    /** Hány különböző napon volt teljesítve a dátum hetében. */
    public int getCompletionDaysInWeekOf(LocalDate date) {
        return weekStart(date).format(ISO).equals(weekStartDate) ? daysDoneThisWeek : 0;
    }

    /**
     * Esedékes-e a szokás az adott napon.
     * Heti X alkalomnál akkor, ha a heti cél még nincs meg, vagy aznap már teljesítve lett.
     */
    public boolean isDueOn(LocalDate date) {
        Schedule s = getSchedule();
        switch (s.getType()) {
            case WEEKDAYS:
                return s.isScheduledDay(date);
            case TIMES_PER_WEEK:
                return isCompletedOn(date) || getCompletionDaysInWeekOf(date) < s.getTimesPerWeek();
            default:
                return true;
        }
    }

    /**
     * A ténylegesen érvényes sorozat az adott napon: 0, ha azóta kimaradt egy esedékes alkalom
     * (a tárolt currentStreak csak a következő teljesítéskor nullázódna).
     */
    public int getEffectiveStreak(LocalDate today) {
        LocalDate last = getLastCompletionLocalDate();
        if (last == null) return 0;
        if (last.isAfter(today)) return currentStreak;
        return isStreakBrokenBetween(last, today) ? 0 : currentStreak;
    }

    /**
     * Megszakad-e a sorozat az utolsó teljesítés (last) és a mai nap (today) között.
     * A mai nap nem számít kihagyásnak, hiszen még teljesíthető.
     */
    boolean isStreakBrokenBetween(LocalDate last, LocalDate today) {
        if (!today.isAfter(last)) return false;
        Schedule s = getSchedule();

        if (s.getType() == Schedule.Type.TIMES_PER_WEEK) {
            LocalDate lastWeek = weekStart(last);
            LocalDate thisWeek = weekStart(today);
            if (lastWeek.equals(thisWeek)) return false;          // ugyanaz a hét, még nem zárult le
            if (!lastWeek.plusWeeks(1).equals(thisWeek)) return true; // kimaradt egy teljes hét
            // az előző hét lezárult: elérte-e a heti célt?
            int doneLastWeek = lastWeek.format(ISO).equals(weekStartDate)
                    ? daysDoneThisWeek
                    : 1; // nincs heti adat (régi mentés) → legalább az utolsó teljesítés megvolt
            return doneLastWeek < s.getTimesPerWeek();
        }

        // DAILY / WEEKDAYS: volt-e esedékes nap last és today között (mindkettő kizárva)?
        long gapDays = ChronoUnit.DAYS.between(last, today) - 1;
        // 7 egymást követő nap minden hétnapot lefed, ezért elég legfeljebb 7 napot megnézni
        long toCheck = Math.min(gapDays, 7);
        for (long i = 1; i <= toCheck; i++) {
            if (s.isScheduledDay(last.plusDays(i))) return true;
        }
        return false;
    }

    // -------------------------
    // Emlékeztető
    // -------------------------

    public boolean hasReminder() {
        return reminderMinutes != null && reminderMinutes >= 0 && reminderMinutes < 24 * 60;
    }

    /** Kell-e emlékeztetőt küldeni az adott napon: esedékes és még nincs teljesítve. */
    public boolean shouldRemindOn(LocalDate date) {
        return hasReminder() && isDueOn(date) && !isCompletedOn(date);
    }

    /**
     * A következő emlékeztető időpontja "now" után (szigorúan később), vagy null, ha nincs
     * beállítva. Csak olyan napot ad, amikor a szokás – a jelenlegi állapot szerint – esedékes,
     * és még nincs teljesítve. Elsüléskor a worker újra ellenőrzi a feltételt.
     */
    public LocalDateTime nextReminderAt(LocalDateTime now) {
        if (!hasReminder()) return null;
        int hour = reminderMinutes / 60;
        int minute = reminderMinutes % 60;
        // 8 nap: a mai (ha még nem múlt el) + egy teljes hét minden napja
        for (int i = 0; i <= 7; i++) {
            LocalDate day = now.toLocalDate().plusDays(i);
            LocalDateTime candidate = day.atTime(hour, minute);
            if (candidate.isAfter(now) && shouldRemindOn(day)) return candidate;
        }
        // heti X alkalomnál a cél teljesülése után a következő hét elejéig nincs esedékes nap
        LocalDate nextMonday = weekStart(now.toLocalDate()).plusWeeks(1);
        return nextMonday.atTime(hour, minute);
    }

    private LocalDate getLastCompletionLocalDate() {
        if (lastCompletionDate == null || lastCompletionDate.isEmpty()) return null;
        try {
            return LocalDate.parse(lastCompletionDate, ISO);
        } catch (Exception e) {
            return null; // sérült mentett dátum → mintha még nem lett volna teljesítve
        }
    }

    private static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    // -------------------------
    // Migráció
    // -------------------------

    /**
     * Ha hiányzik az id (régi mentés), generál egyet.
     * @return true, ha az objektum megváltozott (vissza kell menteni)
     */
    public boolean ensureId() {
        if (id != null && !id.isEmpty()) return false;
        id = UUID.randomUUID().toString();
        return true;
    }

    /**
     * Régi mentések hiányzó mezőinek pótlása (id, ütemezés, null stringek).
     * @return true, ha bármi változott
     */
    public boolean migrate() {
        boolean changed = ensureId();
        if (schedule == null) { schedule = Schedule.daily(); changed = true; }
        if (lastCompletionDate == null) { lastCompletionDate = ""; changed = true; }
        if (weekStartDate == null) { weekStartDate = ""; changed = true; }
        return changed;
    }

    //  GETTEREK / SETTEREK
    public String getId() { return id; }

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

    public Schedule getSchedule() { return schedule != null ? schedule : Schedule.daily(); }
    public void setSchedule(Schedule schedule) { this.schedule = schedule != null ? schedule : Schedule.daily(); }

    public Integer getReminderMinutes() { return hasReminder() ? reminderMinutes : null; }
    public void setReminderMinutes(Integer reminderMinutes) { this.reminderMinutes = reminderMinutes; }

    public String getWeekStartDate() { return weekStartDate; }
    public void setWeekStartDate(String weekStartDate) { this.weekStartDate = weekStartDate; }

    public int getDaysDoneThisWeek() { return daysDoneThisWeek; }
    public void setDaysDoneThisWeek(int daysDoneThisWeek) { this.daysDoneThisWeek = daysDoneThisWeek; }
}
