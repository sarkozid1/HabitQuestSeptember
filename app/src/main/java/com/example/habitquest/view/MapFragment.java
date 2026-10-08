package com.example.habitquest.view;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.graphics.PathMeasure;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.habitquest.R;
import com.example.habitquest.model.WorldZone;
import com.example.habitquest.viewmodel.MainViewModel;

/**
 * MapFragment a karakter pozícióját mutatja a szint alapján.
 * A hős az ösvény ívén haladva mozog a checkpointok között.
 */
public class MapFragment extends Fragment {

    private ImageView characterIcon;
    private MapPathView mapPathView;
    private TextView zoneNameText;
    private TextView zoneRangeText;
    private TextView progressChip;
    private MainViewModel viewModel;

    private WorldZone currentZone = null;
    private int lastIndex = -1;
    private ValueAnimator walkAnimator;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        characterIcon = view.findViewById(R.id.characterIcon);
        mapPathView = view.findViewById(R.id.mapPathView);
        zoneNameText = view.findViewById(R.id.mapZoneName);
        zoneRangeText = view.findViewById(R.id.mapZoneRange);
        progressChip = view.findViewById(R.id.mapProgressChip);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        viewModel.getCharacter().observe(getViewLifecycleOwner(), character -> {
            if (character != null) {
                characterIcon.setImageResource(character.getCharacterImageResId());
            }
        });

        UIHelper.bindBackgroundToZone(this, viewModel);

        observeCharacterLevel();
    }

    @Override
    public void onDestroyView() {
        if (walkAnimator != null) walkAnimator.cancel();
        super.onDestroyView();
    }

    private void observeCharacterLevel() {
        viewModel.getCharacter().observe(getViewLifecycleOwner(), character -> {
            if (character == null) return;

            int level = character.getLevel();
            WorldZone newZone = zoneForLevel(level);

            if (currentZone != newZone) {
                announceZoneIfNew(newZone);
                currentZone = newZone;
                lastIndex = -1; // új zónában elölről indul az ösvény
            }

            zoneNameText.setText(currentZone.getName());
            zoneRangeText.setText("Szint " + currentZone.getMinLevel() + "–" + currentZone.getMaxLevel());
            mapPathView.setZone(currentZone.getName(), currentZone.getMinLevel(), currentZone.getMaxLevel());

            // a nézet méretére várunk, különben a checkpointok még nincsenek kiszámolva
            mapPathView.post(() -> moveCharacterToLevel(level));
        });
    }

    private WorldZone zoneForLevel(int level) {
        if (WorldZone.FOREST.containsLevel(level)) return WorldZone.FOREST;
        if (WorldZone.MOUNTAIN.containsLevel(level)) return WorldZone.MOUNTAIN;
        return WorldZone.CITY;
    }

    private void moveCharacterToLevel(int level) {
        float[][] checkpoints = mapPathView.getCheckpoints();
        if (checkpoints.length == 0) return;

        int index = Math.max(0, Math.min(level - currentZone.getMinLevel(), checkpoints.length - 1));
        mapPathView.setCurrentIndex(index);
        progressChip.setText("🚩 " + (index + 1) + " / " + checkpoints.length);

        if (index == lastIndex) return;

        if (lastIndex < 0) {
            placeCharacterAt(checkpoints[index][0], checkpoints[index][1]);
        } else {
            walkAlongTrail(lastIndex, index);
        }
        lastIndex = index;
    }

    /** A hős végigsétál az ösvény ívén a két checkpoint között. */
    private void walkAlongTrail(int fromIndex, int toIndex) {
        if (walkAnimator != null) walkAnimator.cancel();

        PathMeasure measure = new PathMeasure(mapPathView.getTrailPath(), false);
        float startDistance = mapPathView.distanceToCheckpoint(fromIndex);
        float endDistance = mapPathView.distanceToCheckpoint(toIndex);
        float[] position = new float[2];

        walkAnimator = ValueAnimator.ofFloat(startDistance, endDistance);
        walkAnimator.setDuration(900);
        walkAnimator.setInterpolator(new DecelerateInterpolator(1.5f));
        walkAnimator.addUpdateListener(animation -> {
            float distance = (float) animation.getAnimatedValue();
            if (measure.getPosTan(distance, position, null)) {
                placeCharacterAt(position[0], position[1]);
            }
        });
        walkAnimator.start();
    }

    /** Az ösvény koordinátáit a karakter ikon saját layout-pozíciójához igazítja. */
    private void placeCharacterAt(float x, float y) {
        float offsetX = mapPathView.getLeft() - characterIcon.getLeft();
        float offsetY = mapPathView.getTop() - characterIcon.getTop();
        characterIcon.setTranslationX(offsetX + x - characterIcon.getWidth() / 2f);
        characterIcon.setTranslationY(offsetY + y - characterIcon.getHeight() * 0.85f);
    }

    private void announceZoneIfNew(WorldZone newZone) {
        String lastZone = viewModel.getLastDiscoveredZone().getValue();
        if (lastZone != null && lastZone.equals(newZone.getName())) return;

        viewModel.setLastDiscoveredZone(newZone.getName());
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Új terület felfedezve!")
                .setMessage("Gratulálok, elérted a " + newZone.getName() + " zónát!\n")
                .setPositiveButton("Tovább", (d, which) -> d.dismiss())
                .create();

        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positive != null) {
                positive.setTextColor(ContextCompat.getColor(requireContext(), R.color.gold_accent));
            }
        });
        dialog.show();
    }
}
