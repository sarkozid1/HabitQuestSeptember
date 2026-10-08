package com.example.habitquest.view;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.habitquest.R;
import com.example.habitquest.model.Achievement;
import com.example.habitquest.model.Habit;
import com.example.habitquest.model.WorldZone;
import com.example.habitquest.viewmodel.MainViewModel;

import java.util.ArrayList;
import java.util.List;

public class HabitFragment extends Fragment {

    private MainViewModel viewModel;
    private HabitAdapter adapter;
    private TextView characterStats;
    private TextView levelName;
    private ImageView characterImage;
    private ProgressBar xpBar;
    private RecyclerView recyclerView;
    private Button addHabitButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_habit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ViewModel és Storage
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        // UI elemek
        characterStats = view.findViewById(R.id.characterStats);
        levelName = view.findViewById(R.id.levelName);
        characterImage = view.findViewById(R.id.characterImage);
        xpBar = view.findViewById(R.id.xpProgressBar);
        recyclerView = view.findViewById(R.id.habitRecyclerView);
        addHabitButton = view.findViewById(R.id.addHabitButton);

        // Adapter
        adapter = new HabitAdapter(new ArrayList<>(), position -> {
            viewModel.completeHabit(position);
        });
        adapter.setOnHabitLongClickListener(this::showHabitOptionsDialog);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        // Szokások figyelése
        viewModel.getHabits().observe(getViewLifecycleOwner(), habits -> {
            adapter.habitList = habits;
            adapter.notifyDataSetChanged();
        });

        // Karakter figyelése
        viewModel.getCharacter().observe(getViewLifecycleOwner(), character -> {
            if (character == null) return;

            int level = character.getLevel();
            int xp = character.getExperience();

            characterStats.setText("Szint: " + level + " | XP: " + xp);
            xpBar.setMax(level * 100);
            xpBar.setProgress(xp);

            String levelTitle = "Tanonc";
            if (level >= 5) levelTitle = "Harcos";
            if (level >= 10) levelTitle = "Mester";
            levelName.setText(levelTitle);

            // frissítjük a karakter képét
            characterImage.setImageResource(character.getCharacterImageResId());
        });


        //Új szokás hozzáadása
        addHabitButton.setOnClickListener(v -> showAddHabitDialog());

        //  Csak ha nincs adat akkor töltse be az alapértelmezett szokásokat
        List<Habit> existingHabits = viewModel.getHabits().getValue();
        if (existingHabits == null || existingHabits.isEmpty()) {
            viewModel.loadInitialData();
        }

        UIHelper.bindBackgroundToZone(this, viewModel);


    }


    /**
     Új szokás ablak (fantasy "küldetés tekercs" stílus)
     */
    private void showAddHabitDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_habit, null);

        final EditText nameInput = dialogView.findViewById(R.id.habitNameInput);
        final EditText descInput = dialogView.findViewById(R.id.habitDescInput);
        final EditText xpInput = dialogView.findViewById(R.id.habitXpInput);
        final EditText achNameInput = dialogView.findViewById(R.id.achNameInput);
        final EditText achDescInput = dialogView.findViewById(R.id.achDescInput);
        final EditText achTargetInput = dialogView.findViewById(R.id.achTargetInput);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button confirmButton = dialogView.findViewById(R.id.confirmButton);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        confirmButton.setOnClickListener(v -> {
            String habitName = nameInput.getText().toString();
            String habitDesc = descInput.getText().toString();
            int xp = 10;
            try {
                xp = Integer.parseInt(xpInput.getText().toString());
            } catch (NumberFormatException ignored) {}

            if (!habitName.isEmpty()) {
                Habit newHabit = new Habit(habitName, habitDesc, xp);
                viewModel.addHabit(newHabit);
            }

            // Ha achievement mező kitöltött
            String achName = achNameInput.getText().toString();
            String achDesc = achDescInput.getText().toString();
            String achTargetStr = achTargetInput.getText().toString();
            int achTarget = 0;
            try { achTarget = Integer.parseInt(achTargetStr); } catch (NumberFormatException ignored) {}

            if (!achName.isEmpty()) {
                Achievement newAch = new Achievement(
                        achName.toLowerCase().replace(" ", "_"),
                        achName,
                        achDesc.isEmpty() ? "Új achievement" : achDesc,
                        false,
                        achTarget,
                        habitName  // összekapcsoljuk a szokással
                );
                viewModel.addAchievement(newAch);
            }

            dialog.dismiss();
        });

        dialog.show();
    }

    /**
     Szokás hosszú nyomásra megjelenő opciói: szerkesztés / törlés
     */
    private void showHabitOptionsDialog(int position) {
        List<Habit> habitList = viewModel.getHabits().getValue();
        if (habitList == null || position < 0 || position >= habitList.size()) return;
        Habit habit = habitList.get(position);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(habit.getName())
                .setItems(new CharSequence[]{"Szerkesztés", "Törlés"}, (d, which) -> {
                    if (which == 0) {
                        showEditHabitDialog(position, habit);
                    } else {
                        confirmDeleteHabit(position, habit);
                    }
                })
                .setNegativeButton("Mégse", (d, which) -> d.cancel())
                .create();

        dialog.setOnShowListener(d -> styleDialogButtons(dialog));
        dialog.show();
    }

    private void confirmDeleteHabit(int position, Habit habit) {
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Szokás törlése")
                .setMessage("Biztosan törlöd: \"" + habit.getName() + "\"?")
                .setPositiveButton("Törlés", (d, which) -> viewModel.deleteHabit(position))
                .setNegativeButton("Mégse", (d, which) -> d.cancel())
                .create();

        dialog.setOnShowListener(d -> styleDialogButtons(dialog));
        dialog.show();
    }

    /** Alapértelmezett (nem egyedi layoutú) dialógusok gombjait arany fantasy színre állítja a Material3 lila helyett. */
    private void styleDialogButtons(AlertDialog dialog) {
        int color = ContextCompat.getColor(requireContext(), R.color.gold_accent);
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (positive != null) positive.setTextColor(color);
        if (negative != null) negative.setTextColor(color);
    }

    private void showEditHabitDialog(int position, Habit habit) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_habit, null);

        final EditText nameInput = dialogView.findViewById(R.id.editHabitNameInput);
        final EditText descInput = dialogView.findViewById(R.id.editHabitDescInput);
        final EditText xpInput = dialogView.findViewById(R.id.editHabitXpInput);
        Button cancelButton = dialogView.findViewById(R.id.editCancelButton);
        Button confirmButton = dialogView.findViewById(R.id.editConfirmButton);

        nameInput.setText(habit.getName());
        descInput.setText(habit.getDescription());
        xpInput.setText(String.valueOf(habit.getExperienceReward()));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        confirmButton.setOnClickListener(v -> {
            String habitName = nameInput.getText().toString();
            if (habitName.isEmpty()) return;

            String habitDesc = descInput.getText().toString();
            int xp = habit.getExperienceReward();
            try {
                xp = Integer.parseInt(xpInput.getText().toString());
            } catch (NumberFormatException ignored) {}

            viewModel.updateHabit(position, habitName, habitDesc, xp);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void animateBackgroundTransition(WorldZone newZone) {
        ImageView globalBackground = requireActivity().findViewById(R.id.globalBackground);
        if (globalBackground == null) return;

        Drawable oldBg = globalBackground.getDrawable();
        Drawable newBg = ContextCompat.getDrawable(requireContext(), newZone.getBackgroundResId());

        if (oldBg != null && newBg != null) {
            TransitionDrawable transition = new TransitionDrawable(new Drawable[]{oldBg, newBg});
            globalBackground.setImageDrawable(transition);
            transition.startTransition(800); // 0.8s fade
        } else {
            globalBackground.setImageDrawable(newBg);
        }
    }

}
