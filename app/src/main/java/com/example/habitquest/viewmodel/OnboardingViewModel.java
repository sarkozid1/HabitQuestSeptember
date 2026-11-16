package com.example.habitquest.viewmodel;

import androidx.lifecycle.ViewModel;

import com.example.habitquest.data.HabitRepository;
import com.example.habitquest.model.Habit;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Az onboarding választásai (kaszt, kezdő szokások) – képernyőforgatáskor is megmaradnak.
 * A lezárás logikája a HabitRepository-ban van.
 */
public class OnboardingViewModel extends ViewModel {

    private String selectedClass = null;
    /** A kiválasztott kezdő szokások indexei; alapból a víz és a séta. */
    private final Set<Integer> selectedStarters = new TreeSet<>();
    private boolean defaultsApplied = false;

    public String getSelectedClass() { return selectedClass; }
    public void selectClass(String characterClass) { selectedClass = characterClass; }
    public boolean hasClass() { return selectedClass != null; }

    /** Első megnyitáskor a javasolt alapértelmezések (víz, séta) bejelölése. */
    public void applyDefaultStarters(int... indexes) {
        if (defaultsApplied) return;
        for (int i : indexes) selectedStarters.add(i);
        defaultsApplied = true;
    }

    public boolean isStarterSelected(int index) { return selectedStarters.contains(index); }

    public void toggleStarter(int index) {
        if (!selectedStarters.remove(index)) selectedStarters.add(index);
    }

    public Set<Integer> getSelectedStarters() { return new TreeSet<>(selectedStarters); }

    /**
     * Lezárás: kaszt + a kiválasztott sablonokból készült szokások mentése.
     * @param starterHabits a kiválasztott indexekhez tartozó, már legyártott szokások
     */
    public void finish(HabitRepository repository, List<Habit> starterHabits) {
        repository.completeOnboarding(selectedClass,
                starterHabits != null ? starterHabits : new ArrayList<>());
    }
}
