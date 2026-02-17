package com.example.mobile_robot_android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Custom dual-axis joystick with neobrutalism styling.
 * <p>
 * Renders a large circular pad with thick borders and hard shadow,
 * a draggable knob, crosshair guides, and direction labels.
 * Outputs normalised X/Y in [-1, +1] (Y inverted: up = positive).
 * <p>
 * Zero allocations in onDraw / onTouchEvent for GC-free 60 fps.
 */
public class JoystickView extends View {

    /** Callback delivered on the UI thread. */
    public interface JoystickListener {
        void onJoystickMoved(float x, float y);
        void onJoystickReleased();
    }

    // ── Neobrutalism colours ────────────────────────────────────
    private static final int COL_BASE       = 0xFFF0F4FF;
    private static final int COL_BORDER     = 0xFF000000;
    private static final int COL_SHADOW     = 0xFF000000;
    private static final int COL_GRID       = 0x22000000;
    private static final int COL_KNOB       = 0xFF4A7DFF;
    private static final int COL_LABEL      = 0x44000000;
    private static final int COL_RING       = 0x224A7DFF;

    private static final float DEAD_ZONE = 0.05f;

    // ── Pre-allocated paints (no alloc in onDraw) ───────────────
    private final Paint pBaseFill   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pShadow     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGrid       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pKnobFill   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pKnobBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pKnobShad   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pRing       = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ── Layout metrics ──────────────────────────────────────────
    private float cx, cy;           // centre of the view
    private float baseR;            // base circle radius
    private float knobR;            // knob radius
    private float knobX, knobY;    // current knob pixel position
    private float bw;               // border width
    private float sd;               // shadow distance

    // ── State ───────────────────────────────────────────────────
    private boolean touching;
    private float outX, outY;       // normalised output
    private JoystickListener listener;

    // ── Constructors ────────────────────────────────────────────

    public JoystickView(Context c)                            { super(c);       init(); }
    public JoystickView(Context c, AttributeSet a)            { super(c, a);    init(); }
    public JoystickView(Context c, AttributeSet a, int d)     { super(c, a, d); init(); }

    private void init() {
        float dp = getResources().getDisplayMetrics().density;
        bw = 3f * dp;
        sd = 5f * dp;

        pBaseFill.setStyle(Paint.Style.FILL);
        pBaseFill.setColor(COL_BASE);

        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(bw);
        pBorder.setColor(COL_BORDER);

        pShadow.setStyle(Paint.Style.FILL);
        pShadow.setColor(COL_SHADOW);

        pGrid.setStyle(Paint.Style.STROKE);
        pGrid.setStrokeWidth(1.5f * dp);
        pGrid.setColor(COL_GRID);

        pKnobFill.setStyle(Paint.Style.FILL);
        pKnobFill.setColor(COL_KNOB);

        pKnobBorder.setStyle(Paint.Style.STROKE);
        pKnobBorder.setStrokeWidth(bw);
        pKnobBorder.setColor(COL_BORDER);

        pKnobShad.setStyle(Paint.Style.FILL);
        pKnobShad.setColor(COL_SHADOW);

        pLabel.setTextSize(13f * dp);
        pLabel.setColor(COL_LABEL);
        pLabel.setTextAlign(Paint.Align.CENTER);
        pLabel.setFakeBoldText(true);

        pRing.setStyle(Paint.Style.STROKE);
        pRing.setStrokeWidth(2f * dp);
        pRing.setColor(COL_RING);
    }

    public void setJoystickListener(JoystickListener l) { listener = l; }

    // ── Measure / layout ────────────────────────────────────────

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        cx = w / 2f;
        cy = h / 2f;
        baseR = Math.min(w, h) / 2f - sd - bw;
        knobR = baseR * 0.22f;
        knobX = cx;
        knobY = cy;
    }

    // ── Drawing ─────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas c) {
        // 1 — base shadow
        c.drawCircle(cx + sd, cy + sd, baseR, pShadow);

        // 2 — base fill + border
        c.drawCircle(cx, cy, baseR, pBaseFill);
        c.drawCircle(cx, cy, baseR, pBorder);

        // 3 — crosshair guides
        float g = baseR * 0.85f;
        c.drawLine(cx - g, cy, cx + g, cy, pGrid);
        c.drawLine(cx, cy - g, cx, cy + g, pGrid);

        // 4 — concentric guide rings
        c.drawCircle(cx, cy, baseR * 0.33f, pGrid);
        c.drawCircle(cx, cy, baseR * 0.66f, pGrid);

        // 5 — direction labels
        float lo = baseR * 0.88f;
        float ty = pLabel.getTextSize() * 0.35f;
        c.drawText("FWD", cx, cy - lo + ty, pLabel);
        c.drawText("REV", cx, cy + lo + ty, pLabel);
        c.drawText("L",   cx - lo, cy + ty, pLabel);
        c.drawText("R",   cx + lo, cy + ty, pLabel);

        // 6 — active displacement ring
        if (touching) {
            float d = dist(knobX, knobY, cx, cy);
            if (d > baseR * 0.08f) {
                c.drawCircle(cx, cy, d, pRing);
            }
        }

        // 7 — knob shadow (smaller when pressed for "push-in" feel)
        float so = touching ? sd * 0.2f : sd * 0.6f;
        c.drawCircle(knobX + so, knobY + so, knobR, pKnobShad);

        // 8 — knob fill + border
        c.drawCircle(knobX, knobY, knobR, pKnobFill);
        c.drawCircle(knobX, knobY, knobR, pKnobBorder);
    }

    // ── Touch handling ──────────────────────────────────────────

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                touching = true;
                processTouch(e.getX(), e.getY());
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touching = false;
                knobX = cx;
                knobY = cy;
                outX = outY = 0f;
                invalidate();
                if (listener != null) listener.onJoystickReleased();
                break;
        }
        return true;
    }

    private void processTouch(float tx, float ty) {
        float dx = tx - cx;
        float dy = ty - cy;
        float d  = (float) Math.sqrt(dx * dx + dy * dy);
        float maxD = baseR - knobR;

        // clamp to base boundary
        if (d > maxD) {
            float r = maxD / d;
            dx *= r;
            dy *= r;
            d = maxD;
        }

        knobX = cx + dx;
        knobY = cy + dy;

        float norm = d / maxD;
        if (norm < DEAD_ZONE) {
            outX = outY = 0f;
        } else {
            float angle = (float) Math.atan2(dy, dx);
            float adj   = (norm - DEAD_ZONE) / (1f - DEAD_ZONE);
            outX = clamp((float) Math.cos(angle) * adj);
            outY = clamp(-(float) Math.sin(angle) * adj);   // Y inverted
        }

        invalidate();
        if (listener != null) listener.onJoystickMoved(outX, outY);
    }

    // ── Helpers ─────────────────────────────────────────────────

    private static float clamp(float v) { return Math.max(-1f, Math.min(1f, v)); }

    private static float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public float getOutputX() { return outX; }
    public float getOutputY() { return outY; }
}
