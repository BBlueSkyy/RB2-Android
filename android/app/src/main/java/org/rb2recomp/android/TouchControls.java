package org.rb2recomp.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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

    TouchControls(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setFocusable(false);
        setWillNotDraw(false);
    }

    private void add(int code, String label, float x, float y, float radius) {
        controls.add(new Control(code, label, x, y, radius));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        releaseAll();
        controls.clear();
        float s = h;
        float stick = s * 0.119f;
        add(L_STICK, "L", w * .135f, s * .765f, stick);
        add(DPAD, "+", w * .345f, s * .765f, s * .119f);
        add(R_STICK, "R", w * .655f, s * .765f, stick);

        float fx = w * .875f, fy = s * .723f, space = s * .116f, face = s * .058f;
        add(A, "A", fx, fy + space, face);
        add(B, "B", fx + space, fy, face);
        add(X, "X", fx - space, fy, face);
        add(Y, "Y", fx, fy - space, face);

        float top = s * .17f, shoulder = s * .065f;
        add(LB, "LB", w * .12f, top, shoulder);
        add(LT, "LT", w * .255f, top, shoulder);
        add(BACK, "Back", w * .43f, top, s * .060f);
        add(START, "Start", w * .57f, top, s * .060f);
        add(RB, "RB", w * .745f, top, shoulder);
        add(RT, "RT", w * .88f, top, shoulder);

        add(L3, "L3", w * .215f, s * .43f, s * .048f);
        add(R3, "R3", w * .785f, s * .43f, s * .048f);
    }

    private Control hit(float x, float y) {
        Control best = null;
        float score = Float.POSITIVE_INFINITY;
        for (Control c : controls) {
            float dx = x - c.x, dy = y - c.y;
            float radius = c.radius * (c.code < 0 && c.code != LT && c.code != RT ? 1.28f : 1.40f);
            float distance = (dx * dx + dy * dy) / (radius * radius);
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

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Control c : controls) {
            boolean down = active(c);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(down ? Color.argb(166, 80, 155, 229) : Color.argb(94, 16, 23, 35));
            canvas.drawCircle(c.x, c.y, c.radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2f, c.radius * .035f));
            paint.setColor(down ? Color.WHITE : Color.argb(185, 220, 225, 239));
            canvas.drawCircle(c.x, c.y, c.radius, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(220, 255, 255, 255));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(c.radius * (c.code == START || c.code == BACK ? .44f : .63f));
            if (c.code == L_STICK || c.code == R_STICK) {
                float x = c.code == L_STICK ? lx : rx;
                float y = c.code == L_STICK ? ly : ry;
                paint.setColor(Color.argb(150, 210, 225, 245));
                canvas.drawCircle(c.x + x * c.radius * .64f,
                        c.y + y * c.radius * .64f, c.radius * .43f, paint);
            } else if (c.code == DPAD) {
                paint.setTextSize(c.radius * .85f);
                canvas.drawText("✛", c.x, c.y - (paint.ascent() + paint.descent()) / 2f, paint);
            } else {
                canvas.drawText(c.label, c.x, c.y - (paint.ascent() + paint.descent()) / 2f, paint);
            }
        }
    }
}
