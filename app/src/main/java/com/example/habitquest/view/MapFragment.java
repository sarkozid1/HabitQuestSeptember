package com.example.habitquest.view;

import android.animation.ValueAnimator;
import android.graphics.PathMeasure;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.habitquest.R;
import com.example.habitquest.model.WorldZone;
import com.example.habitquest.viewmodel.MainViewModel;

/**
 * A térkép: a hős a szintje szerinti állomáson áll, szintlépéskor végigsétál az ösvényen.
 * Járás közben a haladás irányába fordul, kis lépés-ugrásokkal halad és enyhén billeg
 * (csak merev mozgás – a rajz nem torzul). Az új zóna felfedezését a szintlépés-ünneplés
 * jelzi (a régi felugró ablak megszűnt).
 */
public class MapFragment extends Fragment {

    /** Egy lépés-ugrás hossza (dp) és ideje (ms). */
    private static final float STEP_DP = 46f;
    private static final long HOP_MS = 300;
    /** Az ugrás magassága (dp). */
    private static final float HOP_DP = 9f;

    private ImageView characterIcon;
    private View characterShadow;
    private MapPathView mapPathView;
    private TextView zoneNameText;
    private TextView zoneRangeText;
    private TextView progressChip;
    private MainViewModel viewModel;

    private WorldZone currentZone = null;
    private int lastIndex = -1;
    /** Ahol a hős szintje szerint állnia kell (rejtett fülnél a megjelenéskor sétál oda). */
    private int pendingLevel = -1;
    private float facing = 1f;
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
        characterShadow = view.findViewById(R.id.characterShadowMap);
        mapPathView = view.findViewById(R.id.mapPathView);
        zoneNameText = view.findViewById(R.id.mapZoneName);
        zoneRangeText = view.findViewById(R.id.mapZoneRange);
        progressChip = view.findViewById(R.id.mapProgressChip);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        UIHelper.bindBackgroundToZone(this, viewModel);

        viewModel.getCharacter().observe(getViewLifecycleOwner(), character -> {
            if (character == null) return;
            characterIcon.setImageResource(character.getCharacterImageResId());
            onLevel(character.getLevel());
        });
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        // rejtett fülként nem animálunk; megjelenéskor sétál az új helyére
        if (!hidden && pendingLevel > 0 && mapPathView != null) {
            mapPathView.post(() -> moveCharacterToLevel(pendingLevel));
        }
    }

    @Override
    public void onDestroyView() {
        if (walkAnimator != null) walkAnimator.cancel();
        super.onDestroyView();
    }

    private void onLevel(int level) {
        WorldZone newZone = WorldZone.forLevel(level);
        if (currentZone != newZone) {
            currentZone = newZone;
            lastIndex = -1; // új zónában elölről indul az ösvény
        }
        zoneNameText.setText(ZoneNames.of(requireContext(), currentZone));
        zoneRangeText.setText(getString(R.string.map_zone_range,
                currentZone.getMinLevel(), Math.min(currentZone.getMaxLevel(), 99)));
        mapPathView.setZone(currentZone.getName(), currentZone.getMinLevel(), currentZone.getMaxLevel());

        pendingLevel = level;
        if (!isHidden()) {
            // a nézet méretére várunk, különben a checkpointok még nincsenek kiszámolva
            mapPathView.post(() -> moveCharacterToLevel(level));
        }
    }

    private void moveCharacterToLevel(int level) {
        if (!isAdded() || getView() == null) return;
        float[][] checkpoints = mapPathView.getCheckpoints();
        if (checkpoints.length == 0) return;

        int index = Math.max(0, Math.min(level - currentZone.getMinLevel(), checkpoints.length - 1));
        mapPathView.setCurrentIndex(index);
        progressChip.setText(getString(R.string.map_progress, index + 1, checkpoints.length));

        if (index == lastIndex) return;

        if (lastIndex < 0 || !ValueAnimator.areAnimatorsEnabled()) {
            placeCharacterAt(checkpoints[index][0], checkpoints[index][1], 0f, 0f);
        } else {
            walkAlongTrail(lastIndex, index);
        }
        lastIndex = index;
    }

    /** A hős lépésenként végigsétál az ösvény ívén a két checkpoint között. */
    private void walkAlongTrail(int fromIndex, int toIndex) {
        if (walkAnimator != null) walkAnimator.cancel();

        PathMeasure measure = new PathMeasure(mapPathView.getTrailPath(), false);
        float startDistance = mapPathView.distanceToCheckpoint(fromIndex);
        float endDistance = mapPathView.distanceToCheckpoint(toIndex);
        float density = getResources().getDisplayMetrics().density;
        int hops = MapMotion.hopCount(endDistance - startDistance, STEP_DP * density);
        float hopPx = HOP_DP * density;
        float direction = Math.signum(endDistance - startDistance); // visszafelé is helyesen fordul
        float[] position = new float[2];
        float[] tangent = new float[2];
        AccelerateDecelerateInterpolator ease = new AccelerateDecelerateInterpolator();

        walkAnimator = ValueAnimator.ofFloat(0f, 1f);
        walkAnimator.setDuration(hops * HOP_MS);
        walkAnimator.setInterpolator(new LinearInterpolator()); // egyenletes lépésritmus
        walkAnimator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            float distance = startDistance + (endDistance - startDistance) * ease.getInterpolation(t);
            if (measure.getPosTan(distance, position, tangent)) {
                facing = MapMotion.facing(tangent[0] * direction, facing);
                placeCharacterAt(position[0], position[1],
                        MapMotion.hopHeight(t, hops) * hopPx, MapMotion.tilt(t, hops));
            }
        });
        walkAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                land();
            }
        });
        walkAnimator.start();
    }

    /** Megérkezés: egy kis zökkenő a talajon. */
    private void land() {
        if (getView() == null) return;
        characterIcon.setRotation(0f);
        float baseY = characterIcon.getTranslationY();
        float bump = 5f * getResources().getDisplayMetrics().density;
        characterIcon.setTranslationY(baseY - bump);
        characterIcon.animate().translationY(baseY).setDuration(260)
                .setInterpolator(new BounceInterpolator()).start();
    }

    /**
     * A karakter (és talajon maradó árnyéka) elhelyezése az ösvény egy pontján.
     * @param liftPx ugrás magassága; @param tiltDeg billenés
     */
    private void placeCharacterAt(float x, float y, float liftPx, float tiltDeg) {
        float offsetX = mapPathView.getLeft() - characterIcon.getLeft();
        float offsetY = mapPathView.getTop() - characterIcon.getTop();
        characterIcon.setTranslationX(offsetX + x - characterIcon.getWidth() / 2f);
        characterIcon.setTranslationY(offsetY + y - characterIcon.getHeight() * 0.85f - liftPx);
        characterIcon.setScaleX(facing);
        characterIcon.setRotation(tiltDeg * facing);

        if (characterShadow != null) {
            float shadowOffsetX = mapPathView.getLeft() - characterShadow.getLeft();
            float shadowOffsetY = mapPathView.getTop() - characterShadow.getTop();
            characterShadow.setTranslationX(shadowOffsetX + x - characterShadow.getWidth() / 2f);
            characterShadow.setTranslationY(shadowOffsetY + y + characterIcon.getHeight() * 0.12f
                    - characterShadow.getHeight() / 2f);
            // ugrás közben az árnyék kisebb és halványabb
            float k = 1f - 0.35f * (liftPx / Math.max(1f, HOP_DP * getResources().getDisplayMetrics().density));
            characterShadow.setScaleX(k);
            characterShadow.setScaleY(k);
            characterShadow.setAlpha(0.35f * k);
        }
    }
}
