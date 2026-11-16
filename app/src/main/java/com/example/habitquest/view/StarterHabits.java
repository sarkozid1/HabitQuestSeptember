package com.example.habitquest.view;

import android.content.Context;

import com.example.habitquest.R;
import com.example.habitquest.model.Habit;
import com.example.habitquest.model.Schedule;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

/**
 * Az onboardingban felajánlott kezdő szokások. A nevek a strings.xml-ből jönnek (fordítható),
 * és illeszkednek a meglévő jutalom-kulcsszavakhoz (víz → energia, séta → hangulat, tanulás).
 */
final class StarterHabits {

    static final class Template {
        final int emojiRes, nameRes, descRes, xp;
        final Schedule schedule;

        Template(int emojiRes, int nameRes, int descRes, int xp, Schedule schedule) {
            this.emojiRes = emojiRes;
            this.nameRes = nameRes;
            this.descRes = descRes;
            this.xp = xp;
            this.schedule = schedule;
        }

        Habit toHabit(Context ctx) {
            Habit h = new Habit(ctx.getString(nameRes), ctx.getString(descRes), xp);
            h.setSchedule(schedule);
            return h;
        }
    }

    private StarterHabits() { }

    static List<Template> all() {
        List<Template> list = new ArrayList<>();
        list.add(new Template(R.string.starter_water_emoji, R.string.starter_water_name,
                R.string.starter_water_desc, 10, Schedule.daily()));
        list.add(new Template(R.string.starter_walk_emoji, R.string.starter_walk_name,
                R.string.starter_walk_desc, 15, Schedule.daily()));
        list.add(new Template(R.string.starter_read_emoji, R.string.starter_read_name,
                R.string.starter_read_desc, 15, Schedule.daily()));
        list.add(new Template(R.string.starter_study_emoji, R.string.starter_study_name,
                R.string.starter_study_desc, 25, Schedule.daily()));
        list.add(new Template(R.string.starter_workout_emoji, R.string.starter_workout_name,
                R.string.starter_workout_desc, 30,
                Schedule.weekdays(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)));
        list.add(new Template(R.string.starter_meditate_emoji, R.string.starter_meditate_name,
                R.string.starter_meditate_desc, 10, Schedule.daily()));
        return list;
    }
}
