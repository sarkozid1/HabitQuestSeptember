package com.example.habitquest.view;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.net.Uri;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.habitquest.R;
import com.example.habitquest.model.WorldZone;
import com.example.habitquest.utils.BackupManager;
import com.example.habitquest.viewmodel.MainViewModel;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;

public class ProfileFragment extends Fragment {

    private MainViewModel viewModel;

    // Storage Access Framework: a felhasználó választja ki a helyet → nem kell tárhely-engedély
    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(BackupManager.MIME_TYPE), this::onExportTargetChosen);

    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::onImportFileChosen);

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // betölti a layout-ot (fragment_profile.xml)
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView hpText = view.findViewById(R.id.hpText);
        TextView energyText = view.findViewById(R.id.energyText);
        TextView moodText = view.findViewById(R.id.moodText);
        TextView coinsText = view.findViewById(R.id.coinsText);

        ImageView avatar = view.findViewById(R.id.avatarImage);
        TextView usernameText = view.findViewById(R.id.usernameText);
        TextView levelText = view.findViewById(R.id.levelText);
        TextView xpText = view.findViewById(R.id.xpText);
        ProgressBar xpBar = view.findViewById(R.id.progressBar);
        RecyclerView achievementsRecycler = view.findViewById(R.id.achievementsRecycler);
        achievementsRecycler.setLayoutManager(new LinearLayoutManager(getContext()));

        //ViewModel inicializálás
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        // Karakter figyelése
        // Karakter figyelése
        viewModel.getCharacter().observe(getViewLifecycleOwner(), character -> {
            if (character == null) return;

            avatar.setImageResource(character.getCharacterImageResId());
            usernameText.setText(character.getAvatarName());
            levelText.setText(getString(R.string.profile_level, character.getLevel()));

            int xp = character.getExperience();
            int xpNext = character.getXpToNextLevel();
            xpText.setText(getString(R.string.profile_xp, xp, xpNext));
            xpBar.setMax(xpNext);
            xpBar.setProgress(xp);
        });


        // Achievementek figyelése
        viewModel.getAchievements().observe(getViewLifecycleOwner(), achievements -> {
            if (achievements != null) {
                achievementsRecycler.setAdapter(new AchievementAdapter(achievements));
            }
        });



        UIHelper.bindBackgroundToZone(this, viewModel);

        // ⚙️ Beállítások: vízivás-emlékeztető kapcsoló
        MaterialSwitch waterSwitch = view.findViewById(R.id.waterReminderSwitch);
        viewModel.getWaterReminderEnabled().observe(getViewLifecycleOwner(), enabled -> {
            boolean on = enabled == null || enabled;
            if (waterSwitch.isChecked() != on) waterSwitch.setChecked(on);
        });
        waterSwitch.setOnCheckedChangeListener((button, checked) -> {
            Boolean current = viewModel.getWaterReminderEnabled().getValue();
            if (current == null || current != checked) viewModel.setWaterReminderEnabled(checked);
        });

        // 📤 / 📥 Mentés és visszaállítás
        view.findViewById(R.id.exportButton).setOnClickListener(v ->
                exportLauncher.launch(BackupManager.suggestedFileName(LocalDate.now())));
        view.findViewById(R.id.importButton).setOnClickListener(v ->
                // egyes fájlkezelők a .json-t octet-stream / text típusként jelölik
                importLauncher.launch(new String[]{
                        BackupManager.MIME_TYPE, "text/plain", "application/octet-stream"}));

        final int[] lastHp = {0};
        final int[] lastEnergy = {0};
        final int[] lastMood = {0};
        final int[] lastCoins = {0};

        viewModel.getHp().observe(getViewLifecycleOwner(), v -> {
            int max = 100;
            if (viewModel.getCharacter().getValue() != null) {
                max = viewModel.getCharacter().getValue().getHealthMax();
            }
            animateCounter(hpText, lastHp[0], v, 350, true, max);
            lastHp[0] = v;
        });

        viewModel.getEnergy().observe(getViewLifecycleOwner(), v -> {
            int max = 100;
            if (viewModel.getCharacter().getValue() != null) {
                max = viewModel.getCharacter().getValue().getEnergyMax();
            }
            animateCounter(energyText, lastEnergy[0], v, 350, true, max);
            lastEnergy[0] = v;
        });

        viewModel.getMood().observe(getViewLifecycleOwner(), v -> {
            animateCounter(moodText, lastMood[0], v, 350, true, 100);
            lastMood[0] = v;
        });

        viewModel.getCoins().observe(getViewLifecycleOwner(), v -> {
            animateCounter(coinsText, lastCoins[0], v, 350, false, 0);
            lastCoins[0] = v;
        });



    }
    // -------------------------
    // Export / import
    // -------------------------

    private void onExportTargetChosen(@Nullable Uri uri) {
        if (uri == null || !isAdded()) return; // a felhasználó visszalépett
        boolean ok;
        try (OutputStream out = requireContext().getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("null stream");
            viewModel.exportTo(out);
            ok = true;
        } catch (IOException | RuntimeException e) {
            ok = false;
        }
        Toast.makeText(requireContext(),
                ok ? R.string.backup_export_success : R.string.backup_export_failed,
                Toast.LENGTH_LONG).show();
    }

    private void onImportFileChosen(@Nullable Uri uri) {
        if (uri == null || !isAdded()) return;
        BackupManager.ImportResult result;
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
            result = viewModel.readImport(in);
        } catch (IOException | RuntimeException e) {
            result = null;
        }

        if (result == null || !result.isSuccess()) {
            showImportError(result == null ? BackupManager.Error.NOT_JSON : result.getError());
            return;
        }
        confirmImport(result.getData());
    }

    private void confirmImport(BackupManager.BackupData data) {
        String message = getString(R.string.backup_import_confirm_message,
                data.getCharacter().getLevel(), data.getHabits().size(), data.getAchievements().size());
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.backup_import_confirm_title)
                .setMessage(message)
                .setPositiveButton(R.string.backup_import_confirm_ok, (d, w) -> {
                    viewModel.applyImport(data);
                    Toast.makeText(requireContext(), R.string.backup_import_success, Toast.LENGTH_LONG).show();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        dialog.setOnShowListener(d -> styleDialogButtons(dialog));
        dialog.show();
    }

    private void showImportError(BackupManager.Error error) {
        int message;
        switch (error) {
            case NOT_HABITQUEST: message = R.string.backup_error_not_habitquest; break;
            case NEWER_VERSION: message = R.string.backup_error_newer_version; break;
            case INVALID_DATA: message = R.string.backup_error_invalid_data; break;
            default: message = R.string.backup_error_not_json; break;
        }
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.backup_import_error_title)
                .setMessage(message)
                .setPositiveButton(R.string.action_ok, null)
                .create();
        dialog.setOnShowListener(d -> styleDialogButtons(dialog));
        dialog.show();
    }

    /** Arany fantasy gombszín a Material3 alapértelmezett helyett (mint a Szokások képernyőn). */
    private void styleDialogButtons(AlertDialog dialog) {
        int color = ContextCompat.getColor(requireContext(), R.color.gold_accent);
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (positive != null) positive.setTextColor(color);
        if (negative != null) negative.setTextColor(color);
    }

    private void animateCounter(TextView tv, int from, int to, int durationMs, boolean showMax, int max) {
        ValueAnimator a = ValueAnimator.ofInt(from, to);
        a.setDuration(durationMs);
        a.addUpdateListener(v -> {
            int value = (int) v.getAnimatedValue();
            tv.setText(showMax ? (value + " / " + max) : String.valueOf(value));
        });
        a.start();
    }

}
