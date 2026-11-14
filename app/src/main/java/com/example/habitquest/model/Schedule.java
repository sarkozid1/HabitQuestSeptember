package com.example.habitquest.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Egy szokás ütemezése.
 * - DAILY: minden nap
 * - WEEKDAYS: a hét megadott napjain (pl. H, Sze, P)
 * - TIMES_PER_WEEK: heti X alkalom, bármelyik napon
 *
 * Gson-nal mentődik a Habit részeként. A getterek hibatűrők: hiányzó / érvénytelen
 * mentett érték esetén "minden nap" viselkedést adnak.
 */
public class Schedule {

    public enum Type { DAILY, WEEKDAYS, TIMES_PER_WEEK }

    public static final int MIN_TIMES_PER_WEEK = 1;
    public static final int MAX_TIMES_PER_WEEK = 7;

    private Type type = Type.DAILY;
    /** ISO napok: 1 = hétfő … 7 = vasárnap (Gson-barát, egyszerű egész lista). */
    private List<Integer> days = new ArrayList<>();
    private int timesPerWeek = 1;

    @SuppressWarnings("unused")
    private Schedule() { } // Gson

    private Schedule(Type type, List<Integer> days, int timesPerWeek) {
        this.type = type;
        this.days = days;
        this.timesPerWeek = timesPerWeek;
    }

    public static Schedule daily() {
        return new Schedule(Type.DAILY, new ArrayList<>(), 1);
    }

    /** Üres napkészlet esetén "minden nap" ütemezést ad. */
    public static Schedule weekdays(Collection<DayOfWeek> dayOfWeeks) {
        List<Integer> list = new ArrayList<>();
        if (dayOfWeeks != null) {
            for (DayOfWeek d : DayOfWeek.values()) { // rendezett, duplikátum nélkül
                if (dayOfWeeks.contains(d)) list.add(d.getValue());
            }
        }
        if (list.isEmpty()) return daily();
        return new Schedule(Type.WEEKDAYS, list, 1);
    }

    public static Schedule weekdays(DayOfWeek... dayOfWeeks) {
        List<DayOfWeek> list = new ArrayList<>();
        for (DayOfWeek d : dayOfWeeks) list.add(d);
        return weekdays(list);
    }

    public static Schedule timesPerWeek(int times) {
        return new Schedule(Type.TIMES_PER_WEEK, new ArrayList<>(), clampTimes(times));
    }

    private static int clampTimes(int times) {
        return Math.max(MIN_TIMES_PER_WEEK, Math.min(MAX_TIMES_PER_WEEK, times));
    }

    /** A ténylegesen érvényes típus (érvénytelen mentés → DAILY). */
    public Type getType() {
        if (type == null) return Type.DAILY;
        if (type == Type.WEEKDAYS && getDays().isEmpty()) return Type.DAILY;
        return type;
    }

    /** A kiválasztott napok (csak WEEKDAYS típusnál értelmes). */
    public Set<DayOfWeek> getDays() {
        Set<DayOfWeek> set = EnumSet.noneOf(DayOfWeek.class);
        if (days == null) return set;
        for (Integer i : days) {
            if (i != null && i >= 1 && i <= 7) set.add(DayOfWeek.of(i));
        }
        return set;
    }

    public int getTimesPerWeek() {
        return clampTimes(timesPerWeek);
    }

    /**
     * Ütemezés szerint "kijelölt" nap-e (naptár alapján, a teljesítésektől függetlenül).
     * Heti X alkalomnál minden nap szóba jöhet.
     */
    public boolean isScheduledDay(LocalDate date) {
        if (getType() == Type.WEEKDAYS) return getDays().contains(date.getDayOfWeek());
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Schedule)) return false;
        Schedule other = (Schedule) o;
        if (getType() != other.getType()) return false;
        switch (getType()) {
            case WEEKDAYS: return getDays().equals(other.getDays());
            case TIMES_PER_WEEK: return getTimesPerWeek() == other.getTimesPerWeek();
            default: return true;
        }
    }

    @Override
    public int hashCode() {
        return getType().hashCode() * 31 + getDays().hashCode() * 7 + getTimesPerWeek();
    }
}
