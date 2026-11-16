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
import com.example.habitquest.model.Schedule;
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
    private IdleFloatAnimator idleFloat;

    /** Értesítési engedély kérése az első emlékeztető bekapcsolásakor (Android 13+). */
    private final androidx.activity.result.ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                    granted -> { });

    private void requestNotificationPermissionIfNeeded() {
        if (com.example.habitquest.notifications.NotificationHelper.needsPermissionRequest(requireContext())) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        }
    }

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
        adapter = new HabitAdapter(habit -> viewModel.completeHabit(habit.getId()));
        adapter.setOnHabitLongClickListener(this::showHabitOptionsDialog);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        // Szokások figyelése (a ViewModel által rendezett, mai állapottal kiegészített lista)
        viewModel.getHabitItems().observe(getViewLifecycleOwner(), adapter::setItems);

        // Karakter figyelése
        viewModel.getCharacter().observe(getViewLifecycleOwner(), character -> {
            if (character == null) return;

            int level = character.getLevel();
            int xp = character.getExperience();

            characterStats.setText(getString(R.string.habit_header_stats, level, xp));
            xpBar.setMax(level * 100);
            xpBar.setProgress(xp);

            int levelTitle = R.string.level_title_novice;
            if (level >= 5) levelTitle = R.string.level_title_warrior;
            if (level >= 10) levelTitle = R.string.level_title_master;
            levelName.setText(levelTitle);

            // frissítjük a karakter képét
            characterImage.setImageResource(character.getCharacterImageResId());
        });


        //Új szokás hozzáadása
        addHabitButton.setOnClickListener(v -> showAddHabitDialog());

        UIHelper.bindBackgroundToZone(this, viewModel);

        // az avatar finoman lebeg (csak látható képernyőn)
        idleFloat = IdleFloatAnimator.attach(getViewLifecycleOwner(),
                characterImage, view.findViewById(R.id.characterShadow), 4f);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (idleFloat != null) idleFloat.setActive(!hidden); // rejtett fülön nem animál
        // fülre visszatérve (show/hide-nál nincs onResume) a mai állapot újraszámolása
        if (!hidden && viewModel != null) viewModel.refreshHabitItems();
    }

    @Override
    public void onResume() {
        super.onResume();
        // napváltás után (pl. éjfélkor háttérben maradt app) újraszámoljuk a mai esedékességet
        viewModel.refreshHabitItems();
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
        final HabitFormBinder form = new HabitFormBinder(dialogView);
        form.setOnReminderEnabled(this::requestNotificationPermissionIfNeeded);

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
                Schedule schedule = form.readSchedule();
                if (schedule == null) return; // hibás ütemezés → a dialógus nyitva marad
                Habit newHabit = new Habit(habitName, habitDesc, xp);
                newHabit.setSchedule(schedule);
                newHabit.setReminderMinutes(form.readReminder());
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
                        achDesc.isEmpty() ? getString(R.string.habit_ach_default_desc) : achDesc,
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
    private void showHabitOptionsDialog(Habit habit) {
        if (habit == null) return;

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(habit.getName())
                .setItems(new CharSequence[]{getString(R.string.action_edit), getString(R.string.action_delete)}, (d, which) -> {
                    if (which == 0) {
                        showEditHabitDialog(habit);
                    } else {
                        confirmDeleteHabit(habit);
                    }
                })
                .setNegativeButton(R.string.action_cancel, (d, which) -> d.cancel())
                .create();

        dialog.setOnShowListener(d -> styleDialogButtons(dialog));
        dialog.show();
    }

    private void confirmDeleteHabit(Habit habit) {
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.habit_delete_title)
                .setMessage(getString(R.string.habit_delete_message, habit.getName()))
                .setPositiveButton(R.string.action_delete, (d, which) -> viewModel.deleteHabit(habit.getId()))
                .setNegativeButton(R.string.action_cancel, (d, which) -> d.cancel())
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

    private void showEditHabitDialog(Habit habit) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_habit, null);

        final EditText nameInput = dialogView.findViewById(R.id.editHabitNameInput);
        final EditText descInput = dialogView.findViewById(R.id.editHabitDescInput);
        final EditText xpInput = dialogView.findViewById(R.id.editHabitXpInput);
        Button cancelButton = dialogView.findViewById(R.id.editCancelButton);
        Button confirmButton = dialogView.findViewById(R.id.editConfirmButton);
        final HabitFormBinder form = new HabitFormBinder(dialogView);
        form.setOnReminderEnabled(this::requestNotificationPermissionIfNeeded);
        form.setSchedule(habit.getSchedule());
        form.setReminder(habit.getReminderMinutes());

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

            Schedule schedule = form.readSchedule();
            if (schedule == null) return; // hibás ütemezés → a dialógus nyitva marad

            viewModel.updateHabit(habit.getId(), habitName, habitDesc, xp, schedule, form.readReminder());
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
