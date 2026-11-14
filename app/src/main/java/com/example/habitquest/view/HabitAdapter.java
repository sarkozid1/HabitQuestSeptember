package com.example.habitquest.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.habitquest.R;
import com.example.habitquest.model.Habit;
import com.example.habitquest.model.Schedule;
import com.example.habitquest.viewmodel.HabitListItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Szokáslista. A sorrendet és a mai állapotot (esedékes / teljesítve / streak)
 * a ViewModel számolja ki (HabitListItem), az adapter csak megjeleníti.
 * A listenerek a Habit objektumot adják vissza (nem pozíciót), mert a megjelenített
 * sorrend eltér a tárolttól.
 */
public class HabitAdapter extends RecyclerView.Adapter<HabitAdapter.HabitViewHolder> {

    private static final float NOT_DUE_ALPHA = 0.5f;

    // Listener interfész a kattintás kezeléséhez
    public interface OnHabitCompleteListener {
        void onHabitComplete(Habit habit);
    }

    // Listener interfész a hosszú nyomás (szerkesztés/törlés) kezeléséhez
    public interface OnHabitLongClickListener {
        void onHabitLongClick(Habit habit);
    }

    private List<HabitListItem> items = new ArrayList<>();
    private final OnHabitCompleteListener listener;
    private OnHabitLongClickListener longClickListener;

    public HabitAdapter(OnHabitCompleteListener listener) {
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setItems(List<HabitListItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setOnHabitLongClickListener(OnHabitLongClickListener longClickListener) {
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public HabitViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_habit, parent, false);
        return new HabitViewHolder(view);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull HabitViewHolder holder, int position) {
        HabitListItem item = items.get(position);
        Habit habit = item.getHabit();
        Context ctx = holder.itemView.getContext();

        holder.nameText.setText(habit.getName() != null ? habit.getName() : "");
        String desc = habit.getDescription() != null ? habit.getDescription().trim() : "";
        holder.descriptionText.setText(desc);
        holder.descriptionText.setVisibility(desc.isEmpty() ? View.GONE : View.VISIBLE);
        holder.completionsTodayText.setText(String.valueOf(item.getCompletionsToday()));

        // Ütemezés + mai állapot
        String scheduleLine = HabitFormBinder.describe(ctx, habit.getSchedule(), item.getCompletionDaysThisWeek());
        if (habit.hasReminder()) {
            scheduleLine += " · " + ctx.getString(R.string.reminder_summary,
                    HabitFormBinder.formatTime(habit.getReminderMinutes()));
        }
        if (item.isCompletedToday()) {
            scheduleLine += " · " + ctx.getString(R.string.habit_done_today);
        } else if (!item.isDueToday()) {
            scheduleLine += " · " + ctx.getString(R.string.habit_not_due_today);
        }
        holder.scheduleText.setText(scheduleLine);

        // A ma nem esedékes szokások halványítva (a lista végén vannak)
        holder.itemView.setAlpha(item.isDueToday() ? 1f : NOT_DUE_ALPHA);

        int streak = item.getEffectiveStreak();
        if (streak > 0) {
            holder.streakText.setVisibility(View.VISIBLE);
            int res = habit.getSchedule().getType() == Schedule.Type.DAILY
                    ? R.string.streak_days : R.string.streak_times;
            holder.streakText.setText(ctx.getString(res, streak));
        } else {
            holder.streakText.setVisibility(View.GONE);
        }

        // 🔹 az egész kártya kattintható
        holder.itemView.setOnClickListener(v -> listener.onHabitComplete(habit));
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) longClickListener.onHabitLongClick(habit);
            return true;
        });
        holder.itemView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(100).start(); // kicsit összenyomódik
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start(); // visszaáll
                    break;
            }
            return false; // hogy a kattintás is működjön
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HabitViewHolder extends RecyclerView.ViewHolder {
        TextView nameText, descriptionText, completionsTodayText, streakText, scheduleText;

        HabitViewHolder(View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.habitName);
            descriptionText = itemView.findViewById(R.id.habitDescription);
            completionsTodayText = itemView.findViewById(R.id.completionsTodayText);
            streakText = itemView.findViewById(R.id.habitStreakText);
            scheduleText = itemView.findViewById(R.id.habitScheduleText);
        }
    }
}
