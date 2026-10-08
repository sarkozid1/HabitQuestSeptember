package com.example.habitquest.view;

import android.animation.ValueAnimator;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.example.habitquest.model.WorldZone;
import com.example.habitquest.viewmodel.MainViewModel;

public class ProfileFragment extends Fragment {

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
        MainViewModel viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        // Karakter figyelése
        // Karakter figyelése
        viewModel.getCharacter().observe(getViewLifecycleOwner(), character -> {
            if (character == null) return;

            avatar.setImageResource(character.getCharacterImageResId());
            usernameText.setText(character.getAvatarName());
            levelText.setText("Szint: " + character.getLevel());

            int xp = character.getExperience();
            int xpNext = character.getXpToNextLevel();
            xpText.setText("XP: " + xp + " / " + xpNext);
            xpBar.setMax(xpNext);
            xpBar.setProgress(xp);
        });


        // Achievementek figyelése
        viewModel.getAchievements().observe(getViewLifecycleOwner(), achievements -> {
            if (achievements != null) {
                achievementsRecycler.setAdapter(new AchievementAdapter(achievements));
            }
        });



    // Achievementek megfigyelése (Observer)
        viewModel.getAchievements().observe(getViewLifecycleOwner(), achievements -> {
            if (achievements != null) {
                // Adapter összekötése az adatokkal
                AchievementAdapter adapter = new AchievementAdapter(achievements);
                achievementsRecycler.setAdapter(adapter);
            }
        });

        UIHelper.bindBackgroundToZone(this, viewModel);

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
