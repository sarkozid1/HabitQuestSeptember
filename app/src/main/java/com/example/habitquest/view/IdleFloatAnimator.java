package com.example.habitquest.view;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

/**
 * Az avatar "idle" lebegése: a kép lassan fel-le mozog, az árnyéka közben kicsit
 * összehúzódik és halványul. Csak merev mozgás (eltolás) – a rajz nem torzul.
 *
 * Életciklushoz kötött: onResume-kor indul, onPause-kor áll; rejtett fülnél
 * {@link #setActive(boolean)}-dal leállítható. Kikapcsolt rendszeranimációknál nem fut.
 */
public final class IdleFloatAnimator implements DefaultLifecycleObserver {

    /** Fél ciklus ideje (fel VAGY le), ms. */
    static final long HALF_CYCLE_MS = 1200;

    private final View image;
    @Nullable private final View shadow;
    private final float liftPx;
    private final float shadowAlpha;
    private AnimatorSet animation;
    private boolean resumed = false;
    private boolean active = true;

    private IdleFloatAnimator(View image, @Nullable View shadow, float liftDp) {
        this.image = image;
        this.shadow = shadow;
        this.liftPx = liftDp * image.getResources().getDisplayMetrics().density;
        this.shadowAlpha = shadow != null ? shadow.getAlpha() : 1f;
    }

    /**
     * Lebegés a nézet életciklusához kötve.
     * @param liftDp a lebegés magassága (dp)
     */
    public static IdleFloatAnimator attach(LifecycleOwner owner, View image, @Nullable View shadow, float liftDp) {
        IdleFloatAnimator a = new IdleFloatAnimator(image, shadow, liftDp);
        owner.getLifecycle().addObserver(a);
        return a;
    }

    /** Rejtett fülnél false (pl. Fragment.onHiddenChanged). */
    public void setActive(boolean active) {
        this.active = active;
        update();
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        resumed = true;
        update();
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        resumed = false;
        update();
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        stop();
        owner.getLifecycle().removeObserver(this);
    }

    private void update() {
        if (resumed && active && ValueAnimator.areAnimatorsEnabled()) start();
        else stop();
    }

    private void start() {
        if (animation != null) return;
        ObjectAnimator lift = ObjectAnimator.ofFloat(image, View.TRANSLATION_Y, 0f, -liftPx);
        configure(lift);
        animation = new AnimatorSet();
        if (shadow != null) {
            ObjectAnimator shrink = ObjectAnimator.ofPropertyValuesHolder(shadow,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.82f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.82f),
                    PropertyValuesHolder.ofFloat(View.ALPHA, shadowAlpha, shadowAlpha * 0.6f));
            configure(shrink);
            animation.playTogether(lift, shrink);
        } else {
            animation.play(lift);
        }
        animation.start();
    }

    private static void configure(ObjectAnimator a) {
        a.setDuration(HALF_CYCLE_MS);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.setInterpolator(new AccelerateDecelerateInterpolator());
    }

    private void stop() {
        if (animation == null) return;
        animation.cancel();
        animation = null;
        // nyugalmi helyzet
        image.setTranslationY(0f);
        if (shadow != null) {
            shadow.setScaleX(1f);
            shadow.setScaleY(1f);
            shadow.setAlpha(shadowAlpha);
        }
    }
}
