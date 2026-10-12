package org.rb2recomp.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;

/**
 * Transparent multitouch Xbox 360 pad drawn over SDL's SurfaceView.
 * Touch coordinates are never sent as mouse/touch events to the emulated title: each frame of
 * input is delivered to the SDL3 virtual gamepad owned by native/touch_input.cpp.
 */
final class TouchControls extends View {
    interface Listener {
        void onState(int buttons, float lx, float ly, float rx, float ry, float lt, float rt);
    }

    // These numbers intentionally match SDL_GamepadButton (SDL3), not Android KeyEvent codes.
    private static final int A = 0, B = 1, X = 2, Y = 3;
    private static final int BACK = 4, START = 6, L3 = 7, R3 = 8;
    private static final int LB = 9, RB = 10, UP = 11, DOWN = 12, LEFT = 13, RIGHT = 14;
    private static final int L_STICK = -1, R_STICK = -2, DPAD = -3, LT = -4, RT = -5;
    private static final float DEADZONE = 0.12f;

    private static final class Control {
        final int code;
        final String label;
        final float x, y, radius;
        Control(int code, String label, float x, float y, float radius) {
            this.code = code;
            this.label = label;
            this.x = x;
            this.y = y;
            this.radius = radius;
        }
    }

    private static final class Finger {
        final Control control;
        float x, y;
        Finger(Control control, float x, float y) {
            this.control = control;
            this.x = x;
            this.y = y;
        }
    }

    private final Listener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<Control> controls = new ArrayList<>();
    private final SparseArray<Finger> fingers = new SparseArray<>();
    private int pressed;
    private float lx, ly, rx, ry, lt, rt;
    private int opacityPercent = 75;
    private int sizePercent = 100;

    TouchControls(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setFocusable(false);
        setWillNotDraw(false);
        updateStyle();
    }

    void updateStyle() {
        int opacity = getContext().getSharedPreferences("touch_controls", Context.MODE_PRIVATE)
                .getInt("opacity", 75);
        int size = getContext().getSharedPreferences("touch_controls", Context.MODE_PRIVATE)
                .getInt("size", 100);
        opacity = Math.max(30, Math.min(100, opacity));
        size = Math.max(75, Math.min(125, size));
        boolean changed = size != sizePercent;
        opacityPercent = opacity;
        sizePercent = size;
        if (changed) layoutControls(getWidth(), getHeight());
        invalidate();
    }

    private int alpha(int base) {
        return Math.round(base * opacityPercent / 100f);
    }

    private void add(int code, String label, float x, float y, float radius) {
        controls.add(new Control(code, label, x, y, radius));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutControls(w, h);
    }

    private void layoutControls(int w, int h) {
        releaseAll();
        controls.clear();
        if (w <= 0 || h <= 0) return;
        final float scale = sizePercent / 100f;
        final float s = Math.min(h, w / 1.7f);
        final float stick = s * .118f * scale;
        // GameHub-inspired arrangement: outlined shoulder pills at both edges, open middle
        // for gameplay, concentric thumbsticks and minimal translucent face buttons.
        add(L_STICK, "L", w * .135f, h * .76f, stick);
        add(DPAD, "+", w * .345f, h * .77f, s * .115f * scale);
        add(R_STICK, "R", w * .655f, h * .76f, stick);

        float fx = w * .875f, fy = h * .725f;
        float space = s * .115f * scale, face = s * .057f * scale;
        add(A, "A", fx, fy + space, face);
        add(B, "B", fx + space, fy, face);
        add(X, "X", fx - space, fy, face);
        add(Y, "Y", fx, fy - space, face);

        float shoulder = s * .055f * scale;
        add(LT, "LT", w * .115f, h * .18f, shoulder);
        add(LB, "LB", w * .115f, h * .32f, shoulder);
        add(RT, "RT", w * .885f, h * .18f, shoulder);
        add(RB, "RB", w * .885f, h * .32f, shoulder);

        add(BACK, "Back", w * .445f, h * .18f, s * .042f * scale);
        add(START, "Start", w * .555f, h * .18f, s * .042f * scale);
        add(L3, "L3", w * .12f, h * .51f, s * .044f * scale);
        add(R3, "R3", w * .88f, h * .51f, s * .044f * scale);
    }

    private Control hit(float x, float y) {
        Control best = null;
        float score = Float.POSITIVE_INFINITY;
        for (Control c : controls) {
            float dx = x - c.x, dy = y - c.y;
            boolean pill = isPill(c.code);
            float rx = c.radius * (pill ? 1.65f : 1.28f);
            float ry = c.radius * (pill ? .94f : 1.28f);
            float distance = (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry);
            if (distance <= 1f && distance < score) {
                best = c;
                score = distance;
            }
        }
        return best;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL) {
            releaseAll();
            return true;
        }
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int index = event.getActionIndex();
            Control c = hit(event.getX(index), event.getY(index));
            if (c != null) {
                fingers.put(event.getPointerId(index),
                        new Finger(c, event.getX(index), event.getY(index)));
            }
        }
        for (int i = 0; i < event.getPointerCount(); ++i) {
            Finger finger = fingers.get(event.getPointerId(i));
            if (finger != null) {
                finger.x = event.getX(i);
                finger.y = event.getY(i);
            }
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            fingers.remove(event.getPointerId(event.getActionIndex()));
        }
        publish();
        return true;
    }

    private static float axis(float displacement, float radius) {
        float value = Math.max(-1f, Math.min(1f, displacement / radius));
        return Math.abs(value) < DEADZONE ? 0f : value;
    }

    private void publish() {
        int buttons = 0;
        float leftX = 0, leftY = 0, rightX = 0, rightY = 0;
        float leftTrigger = 0, rightTrigger = 0;
        boolean leftClaimed = false, rightClaimed = false;
        for (int i = 0; i < fingers.size(); ++i) {
            Finger finger = fingers.valueAt(i);
            Control c = finger.control;
            if (c.code >= 0) {
                buttons |= 1 << c.code;
            } else if (c.code == LT) {
                leftTrigger = 1f;
            } else if (c.code == RT) {
                rightTrigger = 1f;
            } else if (c.code == L_STICK && !leftClaimed) {
                leftX = axis(finger.x - c.x, c.radius);
                leftY = axis(finger.y - c.y, c.radius);
                // Preserve a circular limit on diagonal movement.
                float length = (float) Math.hypot(leftX, leftY);
                if (length > 1f) { leftX /= length; leftY /= length; }
                leftClaimed = true;
            } else if (c.code == R_STICK && !rightClaimed) {
                rightX = axis(finger.x - c.x, c.radius);
                rightY = axis(finger.y - c.y, c.radius);
                float length = (float) Math.hypot(rightX, rightY);
                if (length > 1f) { rightX /= length; rightY /= length; }
                rightClaimed = true;
            } else if (c.code == DPAD) {
                float dx = axis(finger.x - c.x, c.radius * .7f);
                float dy = axis(finger.y - c.y, c.radius * .7f);
                if (dx < -.28f) buttons |= 1 << LEFT;
                if (dx > .28f) buttons |= 1 << RIGHT;
                if (dy < -.28f) buttons |= 1 << UP;
                if (dy > .28f) buttons |= 1 << DOWN;
            }
        }
        pressed = buttons;
        lx = leftX; ly = leftY; rx = rightX; ry = rightY; lt = leftTrigger; rt = rightTrigger;
        listener.onState(buttons, leftX, leftY, rightX, rightY, leftTrigger, rightTrigger);
        invalidate();
    }

    /** Called on menu open, app pause, rotation, loss of focus, or pad takeover. */
    void releaseAll() {
        fingers.clear();
        publish();
    }

    private boolean active(Control c) {
        if (c.code >= 0) return (pressed & (1 << c.code)) != 0;
        if (c.code == LT) return lt > 0f;
        if (c.code == RT) return rt > 0f;
        if (c.code == L_STICK) return lx != 0f || ly != 0f;
        if (c.code == R_STICK) return rx != 0f || ry != 0f;
        return (pressed & ((1 << UP) | (1 << DOWN) | (1 << LEFT) | (1 << RIGHT))) != 0;
    }

    private static boolean isPill(int code) {
        return code == LT || code == RT || code == LB || code == RB
                || code == BACK || code == START;
    }

    private void fill(int color, int opacity) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(alpha(opacity), Color.red(color),
                Color.green(color), Color.blue(color)));
    }

    private void outline(int opacity, float width) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setColor(Color.argb(alpha(opacity), 239, 246, 255));
    }

    private void label(Canvas canvas, String label, float x, float y, float textSize,
                       int color, int opacity) {
        fill(color, opacity);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        paint.setTextSize(textSize);
        canvas.drawText(label, x, y - (paint.ascent() + paint.descent()) / 2f, paint);
    }

    private void drawRoundControl(Canvas canvas, Control c, boolean down) {
        float r = c.radius;
        fill(0x101923, down ? 119 : 53);
        canvas.drawCircle(c.x, c.y, r, paint);
        outline(down ? 234 : 170, Math.max(1.4f, r * .032f));
        canvas.drawCircle(c.x, c.y, r, paint);
        if (c.code == L_STICK || c.code == R_STICK) {
            float dx = (c.code == L_STICK ? lx : rx) * r * .55f;
            float dy = (c.code == L_STICK ? ly : ry) * r * .55f;
            fill(0xB0BCC8, down ? 145 : 75);
            canvas.drawCircle(c.x + dx, c.y + dy, r * .51f, paint);
            outline(down ? 231 : 130, Math.max(1.1f, r * .021f));
            canvas.drawCircle(c.x + dx, c.y + dy, r * .51f, paint);
            // The neutral pad stays legible even without covering the action in the center.
            label(canvas, c.label, c.x + dx, c.y + dy, r * .32f, Color.WHITE, 165);
        } else if (c.code == DPAD) {
            drawDpad(canvas, c);
        } else {
            int color = c.code == A ? 0x93E6A6 : c.code == B ? 0xF6A19B
                    : c.code == X ? 0x94C7F2 : c.code == Y ? 0xF8D58B : Color.WHITE;
            label(canvas, c.label, c.x, c.y, r * (c.code == L3 || c.code == R3 ? .58f : .88f),
                    color, down ? 255 : 230);
        }
    }

    private void drawDpad(Canvas canvas, Control c) {
        float unit = c.radius * .42f;
        int[] codes = { UP, DOWN, LEFT, RIGHT };
        float[] offsetsX = {0f, 0f, -1f, 1f};
        float[] offsetsY = {-1f, 1f, 0f, 0f};
        for (int i = 0; i < 4; i++) {
            boolean down = (pressed & (1 << codes[i])) != 0;
            float cx = c.x + offsetsX[i] * c.radius * .48f;
            float cy = c.y + offsetsY[i] * c.radius * .48f;
            RectF rect = new RectF(cx - unit * .47f, cy - unit * .47f,
                    cx + unit * .47f, cy + unit * .47f);
            fill(0xD5E1ED, down ? 110 : 19);
            canvas.drawRoundRect(rect, unit * .22f, unit * .22f, paint);
            outline(down ? 232 : 171, Math.max(1.2f, unit * .055f));
            canvas.drawRoundRect(rect, unit * .22f, unit * .22f, paint);
        }
    }

    private void drawPill(Canvas canvas, Control c, boolean down) {
        float width = c.radius * (c.code == START || c.code == BACK ? 2.7f : 3.0f);
        float height = c.radius * (c.code == START || c.code == BACK ? 1.28f : 1.35f);
        float corner = c.radius * .26f;
        RectF rect = new RectF(c.x - width / 2, c.y - height / 2,
                c.x + width / 2, c.y + height / 2);
        fill(0x0C1722, down ? 133 : 52);
        canvas.drawRoundRect(rect, corner, corner, paint);
        outline(down ? 238 : 171, Math.max(1.4f, c.radius * .038f));
        canvas.drawRoundRect(rect, corner, corner, paint);
        label(canvas, c.label, c.x, c.y,
                c.radius * (c.code == START || c.code == BACK ? .45f : .71f),
                Color.WHITE, down ? 255 : 214);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Control c : controls) {
            boolean down = active(c);
            if (isPill(c.code)) drawPill(canvas, c, down);
            else drawRoundControl(canvas, c, down);
        }
    }
}
