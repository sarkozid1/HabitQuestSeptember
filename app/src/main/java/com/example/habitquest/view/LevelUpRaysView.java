package com.example.habitquest.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.habitquest.R;

/**
 * Szintlépés háttér-effekt: lassan forgó arany fénysugarak és pislákoló szikrák.
 * Egyetlen animátor hajtja, csak amíg a nézet ki van rakva (start/stop).
 */
public class LevelUpRaysView extends View {

    private static final int RAY_COUNT = 12;
    private static final int SPARKLE_COUNT = 10;
    /** Egy teljes körbefordulás ideje (ms). */
    private static final long TURN_MS = 14000;

    private final Paint rayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sparklePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path rayPath = new Path();
    private final float[][] sparkles = new float[SPARKLE_COUNT][3]; // szög, távolság-arány, fázis
    private final int gold;

    private ValueAnimator animator;
    /** 0..1: a forgás és a pislákolás közös fázisa. */
    private float phase = 0f;

    public LevelUpRaysView(Context context) { this(context, null); }

    public LevelUpRaysView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        gold = ContextCompat.getColor(context, R.color.gold_bright);
        sparklePaint.setColor(Color.WHITE);
        // determinisztikus "véletlen" elrendezés (minden ünneplésnél ugyanúgy néz ki)
        for (int i = 0; i < SPARKLE_COUNT; i++) {
            sparkles[i][0] = (float) (i * 2 * Math.PI / SPARKLE_COUNT + (i % 3) * 0.35);
            sparkles[i][1] = 0.45f + 0.45f * ((i * 37) % 10) / 10f;
            sparkles[i][2] = (i * 0.137f) % 1f;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float r = Math.min(w, h) / 2f;
        int transparentGold = Color.argb(0, Color.red(gold), Color.green(gold), Color.blue(gold));
        int softGold = Color.argb(150, Color.red(gold), Color.green(gold), Color.blue(gold));
        rayPaint.setShader(new RadialGradient(w / 2f, h / 2f, r,
                new int[]{softGold, transparentGold}, new float[]{0.15f, 1f}, Shader.TileMode.CLAMP));
    }

    /** Az animáció indítása (ha a rendszerben engedélyezettek az animációk). */
    public void start() {
        stop();
        if (!ValueAnimator.areAnimatorsEnabled()) return; // "animációk kikapcsolva" beállítás
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(TURN_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) / 2f;
        if (r <= 0) return;

        // fénysugarak (keskeny ékek), lassan forognak
        canvas.save();
        canvas.rotate(phase * 360f, cx, cy);
        double half = Math.PI / RAY_COUNT * 0.45;
        for (int i = 0; i < RAY_COUNT; i++) {
            double a = i * 2 * Math.PI / RAY_COUNT;
            rayPath.reset();
            rayPath.moveTo(cx, cy);
            rayPath.lineTo(cx + (float) (Math.cos(a - half) * r), cy + (float) (Math.sin(a - half) * r));
            rayPath.lineTo(cx + (float) (Math.cos(a + half) * r), cy + (float) (Math.sin(a + half) * r));
            rayPath.close();
            canvas.drawPath(rayPath, rayPaint);
        }
        canvas.restore();

        // szikrák: lassan körbe vándorolnak és pislákolnak
        float density = getResources().getDisplayMetrics().density;
        for (float[] s : sparkles) {
            double angle = s[0] + phase * 2 * Math.PI * 0.5;
            float dist = s[1] * r * 0.92f;
            float twinkle = (float) (0.5 + 0.5 * Math.sin((phase * 6 + s[2]) * 2 * Math.PI));
            sparklePaint.setAlpha((int) (60 + 195 * twinkle));
            float size = (1.5f + 2.5f * twinkle) * density;
            canvas.drawCircle(cx + (float) Math.cos(angle) * dist, cy + (float) Math.sin(angle) * dist,
                    size, sparklePaint);
        }
    }
}
