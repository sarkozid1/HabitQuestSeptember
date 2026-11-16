package com.example.habitquest.view;

import android.app.TimePickerDialog;
import android.content.Context;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.google.android.material.materialswitch.MaterialSwitch;

import androidx.annotation.Nullable;

import com.example.habitquest.R;
import com.example.habitquest.model.Schedule;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * A view_schedule_picker.xml (ütemezés választó) kezelése a felvétel és a szerkesztés
 * dialógusban: kitöltés meglévő ütemezésből, illetve beolvasás Schedule objektummá.
 * Csak UI-réteg – az ütemezés logikája a model.Schedule / model.Habit osztályban van.
 */
class HabitFormBinder {

    private final RadioGroup radioGroup;
    private final View weekdaysRow;
    private final EditText timesInput;
    private final Map<DayOfWeek, ToggleButton> dayButtons = new EnumMap<>(DayOfWeek.class);
    private final MaterialSwitch reminderSwitch;
    private final TextView reminderTimeText;
    /** Hívódik, amikor a felhasználó BEKAPCSOLJA az emlékeztetőt (pl. engedélykéréshez). */
    private Runnable onReminderEnabled;
    /** A kiválasztott emlékeztető időpont (éjfél óta eltelt perc); alapból 8:00. */
    private int reminderMinutes = 8 * 60;

    HabitFormBinder(View dialogRoot) {
        radioGroup = dialogRoot.findViewById(R.id.scheduleRadioGroup);
        weekdaysRow = dialogRoot.findViewById(R.id.weekdaysRow);
        timesInput = dialogRoot.findViewById(R.id.timesPerWeekInput);

        dayButtons.put(DayOfWeek.MONDAY, dialogRoot.findViewById(R.id.dayMon));
        dayButtons.put(DayOfWeek.TUESDAY, dialogRoot.findViewById(R.id.dayTue));
        dayButtons.put(DayOfWeek.WEDNESDAY, dialogRoot.findViewById(R.id.dayWed));
        dayButtons.put(DayOfWeek.THURSDAY, dialogRoot.findViewById(R.id.dayThu));
        dayButtons.put(DayOfWeek.FRIDAY, dialogRoot.findViewById(R.id.dayFri));
        dayButtons.put(DayOfWeek.SATURDAY, dialogRoot.findViewById(R.id.daySat));
        dayButtons.put(DayOfWeek.SUNDAY, dialogRoot.findViewById(R.id.daySun));

        reminderSwitch = dialogRoot.findViewById(R.id.reminderSwitch);
        reminderTimeText = dialogRoot.findViewById(R.id.reminderTimeText);
        reminderSwitch.setOnCheckedChangeListener((b, checked) -> {
            updateVisibility();
            if (checked && b.isPressed() && onReminderEnabled != null) onReminderEnabled.run();
        });
        reminderTimeText.setOnClickListener(v -> showTimePicker());

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> updateVisibility());
        updateReminderText();
        updateVisibility();
    }

    void setOnReminderEnabled(Runnable callback) {
        this.onReminderEnabled = callback;
    }

    private void showTimePicker() {
        Context ctx = reminderTimeText.getContext();
        new TimePickerDialog(ctx, (view, hour, minute) -> {
            reminderMinutes = hour * 60 + minute;
            updateReminderText();
        }, reminderMinutes / 60, reminderMinutes % 60, DateFormat.is24HourFormat(ctx)).show();
    }

    private void updateReminderText() {
        reminderTimeText.setText(formatTime(reminderMinutes));
    }

    /** Időpont a készülék nyelvi beállítása szerint (pl. "8:30"). */
    static String formatTime(int minutesOfDay) {
        return LocalTime.of(minutesOfDay / 60, minutesOfDay % 60)
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT));
    }

    /** Emlékeztető kitöltése (null = kikapcsolva). */
    void setReminder(Integer minutes) {
        reminderSwitch.setChecked(minutes != null);
        if (minutes != null) reminderMinutes = minutes;
        updateReminderText();
        updateVisibility();
    }

    /** A beállított emlékeztető (éjfél óta eltelt perc), vagy null, ha ki van kapcsolva. */
    @Nullable
    Integer readReminder() {
        return reminderSwitch.isChecked() ? reminderMinutes : null;
    }

    private void updateVisibility() {
        int checked = radioGroup.getCheckedRadioButtonId();
        weekdaysRow.setVisibility(checked == R.id.scheduleWeekdays ? View.VISIBLE : View.GONE);
        timesInput.setVisibility(checked == R.id.scheduleTimesPerWeek ? View.VISIBLE : View.GONE);
        if (reminderSwitch != null) {
            reminderTimeText.setVisibility(reminderSwitch.isChecked() ? View.VISIBLE : View.GONE);
        }
    }

    /** A választó kitöltése egy meglévő ütemezéssel (szerkesztés). */
    void setSchedule(Schedule schedule) {
        if (schedule == null) schedule = Schedule.daily();
        for (Map.Entry<DayOfWeek, ToggleButton> e : dayButtons.entrySet()) {
            e.getValue().setChecked(schedule.getDays().contains(e.getKey()));
        }
        timesInput.setText(schedule.getType() == Schedule.Type.TIMES_PER_WEEK
                ? String.valueOf(schedule.getTimesPerWeek()) : "");

        switch (schedule.getType()) {
            case WEEKDAYS: radioGroup.check(R.id.scheduleWeekdays); break;
            case TIMES_PER_WEEK: radioGroup.check(R.id.scheduleTimesPerWeek); break;
            default: radioGroup.check(R.id.scheduleDaily); break;
        }
        updateVisibility();
    }

    /**
     * A kiválasztott ütemezés. Hibás bevitelnél null-t ad, és a megfelelő mezőn
     * hibaüzenetet jelenít meg (a dialógus ilyenkor nyitva marad).
     */
    @Nullable
    Schedule readSchedule() {
        Context ctx = radioGroup.getContext();
        int checked = radioGroup.getCheckedRadioButtonId();

        if (checked == R.id.scheduleWeekdays) {
            Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
            for (Map.Entry<DayOfWeek, ToggleButton> e : dayButtons.entrySet()) {
                if (e.getValue().isChecked()) days.add(e.getKey());
            }
            if (days.isEmpty()) {
                android.widget.Toast.makeText(ctx, R.string.schedule_error_no_days,
                        android.widget.Toast.LENGTH_SHORT).show();
                return null;
            }
            return Schedule.weekdays(days);
        }

        if (checked == R.id.scheduleTimesPerWeek) {
            int times;
            try {
                times = Integer.parseInt(timesInput.getText().toString().trim());
            } catch (NumberFormatException e) {
                times = -1;
            }
            if (times < Schedule.MIN_TIMES_PER_WEEK || times > Schedule.MAX_TIMES_PER_WEEK) {
                timesInput.setError(ctx.getString(R.string.schedule_error_times));
                return null;
            }
            return Schedule.timesPerWeek(times);
        }

        return Schedule.daily();
    }

    /** Rövid, olvasható összefoglaló a listára, pl. "H, Sze, P" vagy "Heti 3× · ezen a héten 1/3". */
    static String describe(Context ctx, Schedule schedule, int completionDaysThisWeek) {
        switch (schedule.getType()) {
            case WEEKDAYS: {
                StringBuilder sb = new StringBuilder();
                for (DayOfWeek d : DayOfWeek.values()) {
                    if (!schedule.getDays().contains(d)) continue;
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(ctx.getString(shortDayRes(d)));
                }
                return sb.toString();
            }
            case TIMES_PER_WEEK:
                return ctx.getString(R.string.schedule_summary_times,
                        schedule.getTimesPerWeek(), completionDaysThisWeek);
            default:
                return ctx.getString(R.string.schedule_summary_daily);
        }
    }

    static int shortDayRes(DayOfWeek d) {
        switch (d) {
            case MONDAY: return R.string.day_short_mon;
            case TUESDAY: return R.string.day_short_tue;
            case WEDNESDAY: return R.string.day_short_wed;
            case THURSDAY: return R.string.day_short_thu;
            case FRIDAY: return R.string.day_short_fri;
            case SATURDAY: return R.string.day_short_sat;
            default: return R.string.day_short_sun;
        }
    }
}
