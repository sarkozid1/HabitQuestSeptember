package com.example.habitquest.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Statisztika-számítás a napi teljesítési előzményből. Tiszta logika, a "mai" napot
 * paraméterként kapja, így unit tesztelhető.
 *
 * Korlát: a sikerességi arány a szokás JELENLEGI ütemezésével számol (a korábbi
 * ütemezéseket nem tároljuk).
 */
public final class HabitStats {

    /** A hőtérkép ennyi hetet mutat (kb. 3 hónap). */
    public static final int HEATMAP_WEEKS = 13;
    /** Intenzitás-fokozatok: 0 = semmi, 1..4 = egyre több. */
    public static final int MAX_LEVEL = 4;

    private HabitStats() { }

    // -------------------------
    // Szokásonkénti összesítő
    // -------------------------

    public static final class Summary {
        public final int currentStreak;
        public final int longestStreak;
        public final int totalCompletions;
        /** Teljesített esedékes alkalmak (heti X-nél: max X / hét). */
        public final int completedDue;
        /** Esedékes alkalmak száma a vizsgált időszakban. */
        public final int due;

        Summary(int currentStreak, int longestStreak, int totalCompletions, int completedDue, int due) {
            this.currentStreak = currentStreak;
            this.longestStreak = longestStreak;
            this.totalCompletions = totalCompletions;
            this.completedDue = completedDue;
            this.due = due;
        }

        /** Sikerességi arány 0..1, vagy -1, ha még nem volt esedékes alkalom. */
        public double successRate() {
            return due == 0 ? -1 : (double) completedDue / due;
        }

        /** Arány százalékban (kerekítve), vagy -1. */
        public int successPercent() {
            return due == 0 ? -1 : (int) Math.round(100.0 * completedDue / due);
        }
    }

    public static Summary summarize(Habit habit, LocalDate today) {
        int[] rate = successCounts(habit, today);
        return new Summary(
                habit.getEffectiveStreak(today),
                habit.getLongestStreak(),
                habit.getCompletionCount(),
                rate[0], rate[1]);
    }

    /**
     * [teljesített esedékes, esedékes] a felvétel napjától (vagy az előzmény elejétől) máig.
     * - Minden nap / adott napok: esedékes napok, ezekből hány volt teljesítve. A mai nap csak
     *   akkor számít, ha már kész (a még folyamatban lévő napot nem büntetjük).
     * - Heti X: hetente min(teljesített napok, X) / X; az első (csonka) hétnél a cél legfeljebb
     *   a hátralévő napok száma; a folyamatban lévő hét csak akkor számít, ha a cél már megvan.
     */
    static int[] successCounts(Habit habit, LocalDate today) {
        LocalDate start = statsStart(habit, today);
        if (start.isAfter(today)) return new int[]{0, 0};
        Schedule s = habit.getSchedule();

        int done = 0, due = 0;
        if (s.getType() == Schedule.Type.TIMES_PER_WEEK) {
            int target = s.getTimesPerWeek();
            LocalDate week = weekStart(start);
            LocalDate thisWeek = weekStart(today);
            while (!week.isAfter(thisWeek)) {
                LocalDate from = week.isBefore(start) ? start : week;
                LocalDate to = week.plusDays(6).isAfter(today) ? today : week.plusDays(6);
                int doneDays = 0;
                for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                    if (habit.getHistoryCountOn(d) > 0) doneDays++;
                }
                int availableDays = (int) ChronoUnit.DAYS.between(from, week.plusDays(7));
                int weekTarget = Math.min(target, availableDays);
                boolean ongoing = week.equals(thisWeek);
                if (!ongoing || doneDays >= weekTarget) {
                    done += Math.min(doneDays, weekTarget);
                    due += weekTarget;
                }
                week = week.plusWeeks(1);
            }
        } else {
            for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
                if (!s.isScheduledDay(d)) continue;
                boolean isDone = habit.getHistoryCountOn(d) > 0;
                if (d.equals(today) && !isDone) continue; // a mai nap még teljesíthető
                due++;
                if (isDone) done++;
            }
        }
        return new int[]{done, due};
    }

    /** A statisztika kezdőnapja: a felvétel napja, de legfeljebb az előzmény elejéig visszamenőleg. */
    static LocalDate statsStart(Habit habit, LocalDate today) {
        LocalDate earliest = today.minusDays(Habit.HISTORY_DAYS - 1);
        LocalDate created = habit.getCreatedLocalDate();
        if (created == null || created.isBefore(earliest)) return earliest;
        return created;
    }

    // -------------------------
    // Heti összesítő
    // -------------------------

    public static final class WeekComparison {
        public final int thisWeek;
        public final int lastWeek;

        WeekComparison(int thisWeek, int lastWeek) {
            this.thisWeek = thisWeek;
            this.lastWeek = lastWeek;
        }

        public int difference() { return thisWeek - lastWeek; }
    }

    /** Teljesítések száma ezen a héten (hétfőtől máig) és az egész előző héten. */
    public static WeekComparison weekComparison(List<Habit> habits, LocalDate today) {
        LocalDate monday = weekStart(today);
        return new WeekComparison(
                completionsBetween(habits, monday, today),
                completionsBetween(habits, monday.minusWeeks(1), monday.minusDays(1)));
    }

    /** Összes teljesítés két nap között (mindkettő beleértve). */
    public static int completionsBetween(List<Habit> habits, LocalDate from, LocalDate to) {
        int total = 0;
        if (habits == null) return 0;
        for (Habit h : habits) {
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                total += h.getHistoryCountOn(d);
            }
        }
        return total;
    }

    // -------------------------
    // Hőtérkép
    // -------------------------

    /** A hőtérkép adatai: hétfőtől kezdődő hetek, oszloponként 7 nap. */
    public static final class Heatmap {
        /** Az első (bal felső) cella napja – mindig hétfő. */
        public final LocalDate start;
        /** Cellánkénti intenzitás 0..{@link #MAX_LEVEL}; -1 = üres cella (jövő, ill. a szokás felvétele előtt). */
        public final int[] levels;
        /** Cellánként: nem esedékes nap (csak egy szokás szűrésénél lehet true). */
        public final boolean[] notDue;
        /** Cellánként: a nyers érték (teljesítések / teljesített szokások száma). */
        public final int[] values;

        Heatmap(LocalDate start, int[] levels, boolean[] notDue, int[] values) {
            this.start = start;
            this.levels = levels;
            this.notDue = notDue;
            this.values = values;
        }

        public int cellCount() { return levels.length; }
        public LocalDate dateOf(int index) { return start.plusDays(index); }
    }

    /**
     * Összesített hőtérkép: naponta hány KÜLÖNBÖZŐ szokás lett teljesítve; az intenzitás a
     * legjobb nap értékéhez viszonyít (1..4).
     */
    public static Heatmap heatmapAll(List<Habit> habits, LocalDate today) {
        LocalDate start = heatmapStart(today);
        int cells = HEATMAP_WEEKS * 7;
        int[] values = new int[cells];
        int[] levels = new int[cells];
        boolean[] notDue = new boolean[cells];

        int max = 0;
        for (int i = 0; i < cells; i++) {
            LocalDate d = start.plusDays(i);
            if (d.isAfter(today)) { levels[i] = -1; continue; }
            int count = 0;
            if (habits != null) {
                for (Habit h : habits) if (h.getHistoryCountOn(d) > 0) count++;
            }
            values[i] = count;
            max = Math.max(max, count);
        }
        for (int i = 0; i < cells; i++) {
            if (levels[i] == -1) continue;
            levels[i] = scale(values[i], max);
        }
        return new Heatmap(start, levels, notDue, values);
    }

    /** Egy szokás hőtérképe: teljesítve = teljes intenzitás; a nem esedékes napok jelölve. */
    public static Heatmap heatmapForHabit(Habit habit, LocalDate today) {
        LocalDate start = heatmapStart(today);
        int cells = HEATMAP_WEEKS * 7;
        int[] values = new int[cells];
        int[] levels = new int[cells];
        boolean[] notDue = new boolean[cells];
        Schedule s = habit.getSchedule();
        LocalDate created = statsStart(habit, today);

        for (int i = 0; i < cells; i++) {
            LocalDate d = start.plusDays(i);
            // a jövő és a felvétel előtti napok üresek (ott még nem volt mit teljesíteni)
            if (d.isAfter(today) || (d.isBefore(created) && habit.getHistoryCountOn(d) == 0)) {
                levels[i] = -1;
                continue;
            }
            values[i] = habit.getHistoryCountOn(d);
            levels[i] = values[i] > 0 ? MAX_LEVEL : 0;
            notDue[i] = !s.isScheduledDay(d);
        }
        return new Heatmap(start, levels, notDue, values);
    }

    /** A hőtérkép első napja: a mai héttel együtt 13 hét, hétfővel kezdve. */
    public static LocalDate heatmapStart(LocalDate today) {
        return weekStart(today).minusWeeks(HEATMAP_WEEKS - 1);
    }

    /** 0 → 0; egyébként 1..MAX_LEVEL a maximumhoz viszonyítva. */
    static int scale(int value, int max) {
        if (value <= 0 || max <= 0) return 0;
        return Math.max(1, (int) Math.ceil((double) value * MAX_LEVEL / max));
    }

    // -------------------------
    // Segédek
    // -------------------------
    static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Összesítők az összes szokásra (a statisztika képernyő listájához). */
    public static List<Summary> summarizeAll(List<Habit> habits, LocalDate today) {
        List<Summary> list = new ArrayList<>();
        if (habits != null) for (Habit h : habits) list.add(summarize(h, today));
        return list;
    }
}
