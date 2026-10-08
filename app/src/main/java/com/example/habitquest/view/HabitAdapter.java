package com.example.habitquest.view;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.habitquest.R;
import com.example.habitquest.model.Habit;

import java.util.List;

public class HabitAdapter extends RecyclerView.Adapter<HabitAdapter.HabitViewHolder> {

    // Listener interfész a kattintás kezeléséhez
    public interface OnHabitCompleteListener {
        void onHabitComplete(int position);
    }

    // Listener interfész a hosszú nyomás (szerkesztés/törlés) kezeléséhez
    public interface OnHabitLongClickListener {
        void onHabitLongClick(int position);
    }

    public List<Habit> habitList;
    private OnHabitCompleteListener listener;
    private OnHabitLongClickListener longClickListener;

    public HabitAdapter(List<Habit> habitList, OnHabitCompleteListener listener) {
        this.habitList = habitList;
        this.listener = listener;
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
        Habit habit = habitList.get(position);

        holder.nameText.setText(habit.getName());
        holder.descriptionText.setText(habit.getDescription());
        holder.nameText.setText(habit.getName() != null ? habit.getName() : "");
        holder.descriptionText.setText(habit.getDescription() != null ? habit.getDescription() : "");
        holder.completionsTodayText.setText(String.valueOf(habit.getCompletionsToday()));

        if (habit.getCurrentStreak() > 0) {
            holder.streakText.setVisibility(View.VISIBLE);
            holder.streakText.setText("🔥 " + habit.getCurrentStreak() + " napos sorozat");
        } else {
            holder.streakText.setVisibility(View.GONE);
        }

        // 🔹 az egész kártya kattintható
        holder.itemView.setOnClickListener(v -> listener.onHabitComplete(position));
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) longClickListener.onHabitLongClick(position);
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
        return habitList.size();
    }

    static class HabitViewHolder extends RecyclerView.ViewHolder {
        TextView nameText, descriptionText, completionsTodayText, streakText;

        HabitViewHolder(View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.habitName);
            descriptionText = itemView.findViewById(R.id.habitDescription);
            completionsTodayText = itemView.findViewById(R.id.completionsTodayText);
            streakText = itemView.findViewById(R.id.habitStreakText);
        }
    }

}
