package com.example.habitquest.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.habitquest.R;
import com.example.habitquest.model.HabitStats;

import java.time.LocalDate;

/**
 * GitHub-stílusú naptár-hőtérkép (a MapPathView mintájára saját rajzolással, külső könyvtár nélkül).
 * Oszlopok = hetek (hétfőtől), sorok = napok; felül hónapnevek, balra H / Sze / P.
 * Csak megjelenít: az adatot ({@link HabitStats.Heatmap}) a modell számolja.
 */
public class HeatmapView extends View {

    /** Cellára koppintáskor (dátum + nyers érték). */
    public interface OnCellClickListener {
        void onCellClick(LocalDate date, int value, boolean notDue);
    }

    private static final int ROWS = 7;

    private HabitStats.Heatmap data;
    private LocalDate today;
    private OnCellClickListener cellClickListener;

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint todayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private final int emptyColor;
    private final int notDueColor;
    private final int[] levelColors = new int[HabitStats.MAX_LEVEL + 1];
    private String[] monthNames;
    private String[] dayLabels;

    // elrendezés (onSizeChanged / onDraw számolja)
    private float cell, gap, labelWidth, labelHeight;

    public HeatmapView(Context context) { this(context, null); }

    public HeatmapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        int gold = ContextCompat.getColor(context, R.color.gold_accent);
        emptyColor = Color.parseColor("#FFFBF0");
        notDueColor = ContextCompat.getColor(context, R.color.glass_parchment_dark);
        levelColors[0] = emptyColor;
        float[] alphas = {0f, 0.30f, 0.52f, 0.76f, 1f};
        for (int i = 1; i <= HabitStats.MAX_LEVEL; i++) {
            levelColors[i] = blend(emptyColor, gold, alphas[i]);
        }

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(ContextCompat.getColor(context, R.color.glass_border));
        todayPaint.setStyle(Paint.Style.STROKE);
        todayPaint.setColor(ContextCompat.getColor(context, R.color.ink_primary));
        labelPaint.setColor(ContextCompat.getColor(context, R.color.ink_secondary));

        monthNames = context.getResources().getStringArray(R.array.month_short);
        dayLabels = new String[]{
                context.getString(R.string.day_short_mon), "",
                context.getString(R.string.day_short_wed), "",
                context.getString(R.string.day_short_fri), "", ""};
    }

    public void setData(HabitStats.Heatmap data, LocalDate today) {
        this.data = data;
        this.today = today;
        setContentDescription(buildDescription());
        invalidate();
    }

    public void setOnCellClickListener(OnCellClickListener listener) {
        this.cellClickListener = listener;
        setClickable(listener != null);
    }

    private int columns() {
        return data == null ? HabitStats.HEATMAP_WEEKS : data.cellCount() / ROWS;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        float density = getResources().getDisplayMetrics().density;
        labelWidth = 26 * density;
        labelHeight = 16 * density;
        int cols = columns();
        // cella + rés: a rés a cella ~18%-a
        cell = (width - getPaddingLeft() - getPaddingRight() - labelWidth) / (cols + (cols - 1) * 0.18f);
        gap = cell * 0.18f;
        int height = (int) (getPaddingTop() + labelHeight + ROWS * cell + (ROWS - 1) * gap + getPaddingBottom());
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (data == null) return;
        float density = getResources().getDisplayMetrics().density;
        float radius = cell * 0.22f;
        labelPaint.setTextSize(10.5f * density);
        borderPaint.setStrokeWidth(Math.max(1f, density * 0.8f));
        todayPaint.setStrokeWidth(2f * density);

        float left0 = getPaddingLeft() + labelWidth;
        float top0 = getPaddingTop() + labelHeight;

        // napnevek (H, Sze, P)
        for (int r = 0; r < ROWS; r++) {
            if (dayLabels[r].isEmpty()) continue;
            float y = top0 + r * (cell + gap) + cell * 0.75f;
            canvas.drawText(dayLabels[r], getPaddingLeft(), y, labelPaint);
        }

        int cols = columns();
        int lastMonth = -1;
        int lastLabelCol = -10;
        for (int c = 0; c < cols; c++) {
            float x = left0 + c * (cell + gap);

            // hónapnév ott, ahol új hónap kezdődik (az oszlop hétfője alapján); ne fedjék egymást
            int month = data.dateOf(c * ROWS).getMonthValue();
            if (month != lastMonth) {
                if (c - lastLabelCol >= 3) {
                    canvas.drawText(monthNames[month - 1], x, getPaddingTop() + labelHeight * 0.7f, labelPaint);
                    lastLabelCol = c;
                }
                lastMonth = month;
            }

            for (int r = 0; r < ROWS; r++) {
                int i = c * ROWS + r;
                int level = data.levels[i];
                if (level < 0) continue; // jövőbeli nap
                float y = top0 + r * (cell + gap);
                rect.set(x, y, x + cell, y + cell);

                if (data.notDue[i] && level == 0) {
                    cellPaint.setColor(notDueColor);
                    float inset = cell * 0.28f;
                    rect.inset(inset, inset);
                    canvas.drawRoundRect(rect, radius, radius, cellPaint);
                    continue;
                }
                cellPaint.setColor(levelColors[Math.min(level, HabitStats.MAX_LEVEL)]);
                canvas.drawRoundRect(rect, radius, radius, cellPaint);
                canvas.drawRoundRect(rect, radius, radius, borderPaint);

                if (data.dateOf(i).equals(today)) {
                    canvas.drawRoundRect(rect, radius, radius, todayPaint);
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (cellClickListener == null || data == null) return super.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float x = event.getX() - getPaddingLeft() - labelWidth;
            float y = event.getY() - getPaddingTop() - labelHeight;
            int c = (int) (x / (cell + gap));
            int r = (int) (y / (cell + gap));
            if (x >= 0 && y >= 0 && c < columns() && r < ROWS) {
                int i = c * ROWS + r;
                if (data.levels[i] >= 0) {
                    cellClickListener.onCellClick(data.dateOf(i), data.values[i], data.notDue[i]);
                    performClick();
                }
            }
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private String buildDescription() {
        int active = 0, total = 0;
        for (int i = 0; i < data.cellCount(); i++) {
            if (data.levels[i] < 0) continue;
            total++;
            if (data.values[i] > 0) active++;
        }
        return getContext().getString(R.string.stats_heatmap_description, active, total);
    }

    private static int blend(int from, int to, float t) {
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.rgb(r, g, b);
    }
}
