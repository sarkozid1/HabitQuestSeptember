package com.example.habitquest.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
  Kanyargó ösvény a zóna checkpointjaival, a habit kártyák pergamen/arany stílusában.
  A checkpointok a nézet méretéhez igazodva, arányosan kerülnek kiszámításra.
 */
public class MapPathView extends View {

    private static final int INK = 0xFF3A2C1A;
    private static final int PARCHMENT = 0xFFF3E4C1;
    private static final int PARCHMENT_DIM = 0x99F3E4C1;
    private static final int GOLD = 0xFFC9A227;

    private final Paint trailBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailDashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodeFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodeStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path trailPath = new Path();
    private float[][] checkpoints = new float[0][];
    private int[] checkpointLevels = new int[0];
    private int currentIndex = 0;

    private int nodeCount = 5;
    private int startLevel = 1;

    /** Az állomások vízszintes helye a szélesség arányában – váltakozó, de nem szimmetrikus. */
    private static final float[] X_FRACTIONS = {0.30f, 0.68f, 0.38f, 0.72f, 0.34f, 0.64f};

    public MapPathView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        trailBasePaint.setStyle(Paint.Style.STROKE);
        trailBasePaint.setStrokeCap(Paint.Cap.ROUND);
        trailBasePaint.setStrokeJoin(Paint.Join.ROUND);
        trailBasePaint.setColor(PARCHMENT_DIM);

        trailDashPaint.setStyle(Paint.Style.STROKE);
        trailDashPaint.setStrokeCap(Paint.Cap.ROUND);
        trailDashPaint.setColor(GOLD);

        nodeStrokePaint.setStyle(Paint.Style.STROKE);

        labelPaint.setColor(INK);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setFakeBoldText(true);
    }

    /** A zóna nevéből meghatározza a checkpointok számát és a kezdő szintet. */
    public void setZone(String zoneName, int minLevel, int maxLevel) {
        this.startLevel = minLevel;
        this.nodeCount = Math.max(2, Math.min(maxLevel - minLevel + 1, 6));
        buildCheckpoints();
        invalidate();
    }

    /** Melyik checkpointnál áll a hős (0-alapú). */
    public void setCurrentIndex(int index) {
        this.currentIndex = index;
        invalidate();
    }

    public float[][] getCheckpoints() {
        return checkpoints;
    }

    /** Az ösvény útvonala, hogy a karakter végig tudjon haladni rajta. */
    public Path getTrailPath() {
        return trailPath;
    }

    /** Az adott checkpointhoz tartozó távolság az ösvény mentén. */
    public float distanceToCheckpoint(int index) {
        if (checkpoints.length < 2) return 0f;
        PathMeasure measure = new PathMeasure(trailPath, false);
        float total = measure.getLength();
        int clamped = Math.max(0, Math.min(index, checkpoints.length - 1));
        return total * clamped / (checkpoints.length - 1);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        buildCheckpoints();
    }

    /** Kanyargó ösvény alulról felfelé; a vízszintes kitérés szándékosan mérsékelt és változó. */
    private void buildCheckpoints() {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float bottom = h * 0.88f;
        float top = h * 0.14f;
        float stepY = (bottom - top) / (nodeCount - 1);

        checkpoints = new float[nodeCount][2];
        checkpointLevels = new int[nodeCount];

        for (int i = 0; i < nodeCount; i++) {
            checkpoints[i][0] = w * X_FRACTIONS[i % X_FRACTIONS.length];
            checkpoints[i][1] = bottom - stepY * i;
            checkpointLevels[i] = startLevel + i;
        }

        buildTrailPath();

        float radius = nodeRadius();
        trailBasePaint.setStrokeWidth(radius * 0.5f);
        trailDashPaint.setStrokeWidth(radius * 0.22f);
        trailDashPaint.setPathEffect(new DashPathEffect(
                new float[]{radius * 0.35f, radius * 0.45f}, 0));
        labelPaint.setTextSize(radius * 0.85f);
    }

    /**
      Ösvény: egyenes szakaszok az állomások között, a kanyarokban lekerekítve.
      Így valódi hegyi csapásra hasonlít, nem képernyőt átívelő nagy görbékre.
     */
    private void buildTrailPath() {
        trailPath.reset();
        if (checkpoints.length < 2) return;

        trailPath.moveTo(checkpoints[0][0], checkpoints[0][1]);

        for (int i = 1; i < checkpoints.length - 1; i++) {
            float cornerX = checkpoints[i][0];
            float cornerY = checkpoints[i][1];

            // a kanyar előtti és utáni pont, a szakaszhossz arányában behúzva
            float[] before = pointTowards(cornerX, cornerY, checkpoints[i - 1][0], checkpoints[i - 1][1]);
            float[] after = pointTowards(cornerX, cornerY, checkpoints[i + 1][0], checkpoints[i + 1][1]);

            trailPath.lineTo(before[0], before[1]);
            trailPath.quadTo(cornerX, cornerY, after[0], after[1]);
        }

        float[] last = checkpoints[checkpoints.length - 1];
        trailPath.lineTo(last[0], last[1]);
    }

    /** A kanyarponttól a szomszéd felé mutató, kerekítéshez használt pont. */
    private float[] pointTowards(float fromX, float fromY, float towardX, float towardY) {
        float dx = towardX - fromX;
        float dy = towardY - fromY;
        float length = (float) Math.hypot(dx, dy);
        if (length == 0f) return new float[]{fromX, fromY};

        float cornerRadius = Math.min(length * 0.45f, nodeRadius() * 2.2f);
        return new float[]{
                fromX + dx / length * cornerRadius,
                fromY + dy / length * cornerRadius
        };
    }

    private float nodeRadius() {
        return Math.min(getWidth(), getHeight()) * 0.055f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (checkpoints.length == 0) return;

        // 1) ÖSVÉNY
        canvas.drawPath(trailPath, trailBasePaint);
        canvas.drawPath(trailPath, trailDashPaint);

        // 2) CHECKPOINTOK
        float radius = nodeRadius();
        for (int i = 0; i < checkpoints.length; i++) {
            float cx = checkpoints[i][0];
            float cy = checkpoints[i][1];
            boolean reached = i <= currentIndex;
            boolean isCurrent = i == currentIndex;

            nodeFillPaint.setColor(reached ? GOLD : PARCHMENT_DIM);
            canvas.drawCircle(cx, cy, radius, nodeFillPaint);

            nodeStrokePaint.setColor(reached ? INK : 0x663A2C1A);
            nodeStrokePaint.setStrokeWidth(radius * 0.12f);
            nodeStrokePaint.setPathEffect(reached ? null
                    : new DashPathEffect(new float[]{radius * 0.3f, radius * 0.25f}, 0));
            canvas.drawCircle(cx, cy, radius, nodeStrokePaint);

            // az aktuális állomást külső arany gyűrű emeli ki
            if (isCurrent) {
                nodeStrokePaint.setPathEffect(null);
                nodeStrokePaint.setColor(GOLD);
                nodeStrokePaint.setStrokeWidth(radius * 0.1f);
                canvas.drawCircle(cx, cy, radius * 1.35f, nodeStrokePaint);
            }

            labelPaint.setColor(reached ? INK : 0x993A2C1A);
            float textY = cy - (labelPaint.descent() + labelPaint.ascent()) / 2f;
            canvas.drawText(String.valueOf(checkpointLevels[i]), cx, textY, labelPaint);
        }
    }
}
