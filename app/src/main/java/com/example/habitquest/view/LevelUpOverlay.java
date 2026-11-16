package com.example.habitquest.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.habitquest.R;
import com.example.habitquest.model.LevelUpCelebration;

/**
 * A szintlépés-ünneplés réteg megjelenítése / elrejtése (csak UI; az állapot a repositoryban).
 * A karakterkép csak merev mozgást kap (méretezés), a rajz nem torzul.
 * Ha a rendszerben ki vannak kapcsolva az animációk, animáció nélkül jelenik meg.
 */
public final class LevelUpOverlay {

    /** Hány max HP / energia jár szintenként (UserCharacter.levelUp). */
    private static final int MAX_STAT_PER_LEVEL = 5;

    private final View root;
    private final View card;
    private final LevelUpRaysView rays;
    private final ImageView avatar;
    private final TextView level, rewards, zone;
    private AnimatorSet showAnimation;
    private boolean showing = false;

    public LevelUpOverlay(View root, Runnable onContinue) {
        this.root = root;
        this.card = root.findViewById(R.id.levelUpCard);
        this.rays = root.findViewById(R.id.levelUpRays);
        this.avatar = root.findViewById(R.id.levelUpAvatar);
        this.level = root.findViewById(R.id.levelUpLevel);
        this.rewards = root.findViewById(R.id.levelUpRewards);
        this.zone = root.findViewById(R.id.levelUpZone);
        root.findViewById(R.id.levelUpContinue).setOnClickListener(v -> onContinue.run());
    }

    public boolean isShowing() {
        return showing;
    }

    /** Megjelenítés (ha már látszik, csak a tartalmat frissíti, nem animál újra). */
    public void show(LevelUpCelebration c, int characterImageResId) {
        Context ctx = root.getContext();
        avatar.setImageResource(characterImageResId);
        level.setText(ctx.getString(R.string.level_up_level, c.toLevel));
        rewards.setText(ctx.getString(R.string.level_up_rewards,
                MAX_STAT_PER_LEVEL * c.levelsGained(), c.coinsGained));
        if (c.newZone != null) {
            zone.setText(ctx.getString(R.string.level_up_zone, ZoneNames.of(ctx, c.newZone)));
            zone.setVisibility(View.VISIBLE);
        } else {
            zone.setVisibility(View.GONE);
        }
        if (showing) return;
        showing = true;

        root.setVisibility(View.VISIBLE);
        root.announceForAccessibility(ctx.getString(R.string.level_up_announcement, c.toLevel));

        if (!ValueAnimator.areAnimatorsEnabled()) {
            root.setAlpha(1f);
            card.setScaleX(1f);
            card.setScaleY(1f);
            return;
        }

        rays.start();
        root.setAlpha(0f);
        card.setScaleX(0.6f);
        card.setScaleY(0.6f);
        avatar.setScaleX(0.85f);
        avatar.setScaleY(0.85f);

        ObjectAnimator fade = ObjectAnimator.ofFloat(root, View.ALPHA, 0f, 1f).setDuration(220);
        ObjectAnimator pop = ObjectAnimator.ofPropertyValuesHolder(card,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.6f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.6f, 1f)).setDuration(520);
        pop.setInterpolator(new OvershootInterpolator(2.2f));
        // a hős kis "ugrással" mutatkozik be (merev méretezés)
        ObjectAnimator hero = ObjectAnimator.ofPropertyValuesHolder(avatar,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.85f, 1.08f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.85f, 1.08f, 1f)).setDuration(650);
        hero.setStartDelay(260);
        hero.setInterpolator(new DecelerateInterpolator());

        showAnimation = new AnimatorSet();
        showAnimation.playTogether(fade, pop, hero);
        showAnimation.start();
    }

    /** Elrejtés rövid elhalványulással. */
    public void hide() {
        if (!showing) return;
        showing = false;
        if (showAnimation != null) showAnimation.cancel();

        if (!ValueAnimator.areAnimatorsEnabled()) {
            finishHide();
            return;
        }
        root.animate().alpha(0f).setDuration(180).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                root.animate().setListener(null);
                finishHide();
            }
        });
    }

    private void finishHide() {
        rays.stop();
        root.setVisibility(View.GONE);
        root.setAlpha(1f);
    }
}
