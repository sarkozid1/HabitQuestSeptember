package com.example.habitquest.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.habitquest.R;
import com.example.habitquest.model.Habit;
import com.example.habitquest.model.HabitStats;
import com.example.habitquest.viewmodel.MainViewModel;
import com.example.habitquest.viewmodel.StatsSnapshot;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;

/**
 * Statisztika képernyő (a Profilból nyílik). Csak megjelenít: minden számítás a
 * HabitStats-ban (modell) történik, a ViewModel adja a kész StatsSnapshot-ot.
 */
public class StatsFragment extends Fragment {

    private MainViewModel viewModel;
    private HeatmapView heatmap;
    private TextView cellInfo, weekThis, weekLast, weekDiff, emptyText;
    private LinearLayout habitList;
    private Spinner filter;

    /** A szűrő elemeihez tartozó szokás-id-k (0. = összes → null). */
    private final List<String> filterIds = new ArrayList<>();
    private String selectedHabitId = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        heatmap = view.findViewById(R.id.statsHeatmap);
        cellInfo = view.findViewById(R.id.statsCellInfo);
        weekThis = view.findViewById(R.id.statsWeekThis);
        weekLast = view.findViewById(R.id.statsWeekLast);
        weekDiff = view.findViewById(R.id.statsWeekDiff);
        habitList = view.findViewById(R.id.statsHabitList);
        emptyText = view.findViewById(R.id.statsEmpty);
        filter = view.findViewById(R.id.statsFilter);

        heatmap.setOnCellClickListener(this::showCellInfo);
        filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                String newId = position < filterIds.size() ? filterIds.get(position) : null;
                if (newId == null ? selectedHabitId != null : !newId.equals(selectedHabitId)) {
                    selectedHabitId = newId;
                    render();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        viewModel.getHabits().observe(getViewLifecycleOwner(), habits -> {
            bindFilter(habits);
            render();
        });

        UIHelper.bindBackgroundToZone(this, viewModel);
    }

    private void bindFilter(List<Habit> habits) {
        filterIds.clear();
        List<String> labels = new ArrayList<>();
        filterIds.add(null);
        labels.add(getString(R.string.stats_filter_all));
        int selection = 0;
        if (habits != null) {
            for (Habit h : habits) {
                filterIds.add(h.getId());
                labels.add(h.getName());
                if (h.getId().equals(selectedHabitId)) selection = filterIds.size() - 1;
            }
        }
        if (selection == 0) selectedHabitId = null; // a szűrt szokást közben törölték

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filter.setAdapter(adapter);
        filter.setSelection(selection, false);
    }

    private void render() {
        StatsSnapshot stats = viewModel.buildStats(selectedHabitId);

        heatmap.setData(stats.heatmap, stats.today);
        cellInfo.setText(R.string.stats_tap_hint);

        weekThis.setText(String.valueOf(stats.week.thisWeek));
        weekLast.setText(String.valueOf(stats.week.lastWeek));
        int diff = stats.week.difference();
        if (diff > 0) weekDiff.setText(getString(R.string.stats_week_better, diff));
        else if (diff < 0) weekDiff.setText(getString(R.string.stats_week_worse, -diff));
        else weekDiff.setText(R.string.stats_week_same);

        habitList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (StatsSnapshot.HabitRow row : stats.rows) {
            View item = inflater.inflate(R.layout.item_stats_habit, habitList, false);
            HabitStats.Summary s = row.summary;
            ((TextView) item.findViewById(R.id.statsHabitName)).setText(row.habit.getName());
            ((TextView) item.findViewById(R.id.statsHabitSchedule)).setText(
                    HabitFormBinder.describe(requireContext(), row.habit.getSchedule(),
                            row.habit.getCompletionDaysInWeekOf(stats.today)));
            ((TextView) item.findViewById(R.id.statsHabitStreak)).setText(
                    getString(R.string.stats_streak, s.currentStreak, s.longestStreak));
            int pct = s.successPercent();
            ((TextView) item.findViewById(R.id.statsHabitRate)).setText(
                    pct < 0 ? getString(R.string.stats_rate_none) : getString(R.string.stats_rate, pct));
            ((TextView) item.findViewById(R.id.statsHabitTotal)).setText(
                    getString(R.string.stats_total, s.totalCompletions));
            habitList.addView(item);
        }
        emptyText.setVisibility(stats.rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showCellInfo(java.time.LocalDate date, int value, boolean notDue) {
        String day = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM));
        if (selectedHabitId == null) {
            cellInfo.setText(getString(R.string.stats_cell_all, day, value));
        } else if (value > 0) {
            cellInfo.setText(getString(R.string.stats_cell_habit_done, day, value));
        } else if (notDue) {
            cellInfo.setText(getString(R.string.stats_cell_not_due, day));
        } else {
            cellInfo.setText(getString(R.string.stats_cell_habit_missed, day));
        }
    }
}
