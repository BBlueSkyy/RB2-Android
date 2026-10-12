package org.rb2recomp.android;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * A native-library-free test of the *same* TouchControls View shown over SDL during gameplay.
 * Lets players verify layout, diagonals, triggers, buttons and multitouch on their own phone,
 * even when they do not yet have an accepted Xbox 360 disc image installed.
 */
public final class TouchPreviewActivity extends Activity {
    private static final int[] SIZES = {75, 100, 125};
    private static final int[] OPACITIES = {40, 60, 75, 100};
    private static final String[] BUTTON_NAMES = {
            "A", "B", "X", "Y", "Back", "Guide", "Start", "L3", "R3",
            "LB", "RB", "Up", "Down", "Left", "Right"
    };
    private TouchControls touch;
    private TextView readout;
    private Button sizeButton, opacityButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(14, 18, 28));

        // Intentionally below the touch surface: labels cannot consume gamepad touches.
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER);
        TextView heading = new TextView(this);
        heading.setText("RB2 • Touch controls preview");
        heading.setTextSize(17f);
        heading.setTextColor(Color.WHITE);
        heading.setGravity(Gravity.CENTER);
        info.addView(heading);

        TextView hint = new TextView(this);
        hint.setText("Touch multiple buttons and move both sticks. This is a UI test, not the game.");
        hint.setTextColor(Color.rgb(178, 192, 208));
        hint.setTextSize(12f);
        hint.setGravity(Gravity.CENTER);
        info.addView(hint);

        readout = new TextView(this);
        readout.setTextColor(Color.rgb(130, 227, 201));
        readout.setTypeface(android.graphics.Typeface.MONOSPACE);
        readout.setTextSize(14f);
        readout.setGravity(Gravity.CENTER);
        info.addView(readout);

        FrameLayout.LayoutParams infoLayout = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        infoLayout.leftMargin = dp(30);
        infoLayout.rightMargin = dp(30);
        root.addView(info, infoLayout);

        touch = new TouchControls(this, (buttons, lx, ly, rx, ry, lt, rt) -> {
            StringBuilder pressed = new StringBuilder();
            for (int i = 0; i < BUTTON_NAMES.length; i++) {
                if ((buttons & (1 << i)) != 0) {
                    if (pressed.length() > 0) pressed.append(" + ");
                    pressed.append(BUTTON_NAMES[i]);
                }
            }
            readout.setText(String.format(Locale.US,
                    "Buttons: %s\nL (%.2f, %.2f)   R (%.2f, %.2f)\nLT %.2f   RT %.2f",
                    pressed.length() > 0 ? pressed.toString() : "none",
                    lx, ly, rx, ry, lt, rt));
        });
        root.addView(touch, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // These buttons are on top; only the top-center strip is reserved from the pad.
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER);
        toolbar.setPadding(dp(8), 0, dp(8), 0);
        Button exit = smallButton("Close", v -> finish());
        sizeButton = smallButton("", v -> cycleStyle("size", SIZES));
        opacityButton = smallButton("", v -> cycleStyle("opacity", OPACITIES));
        toolbar.addView(exit);
        toolbar.addView(sizeButton);
        toolbar.addView(opacityButton);
        FrameLayout.LayoutParams toolbarLayout = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        root.addView(toolbar, toolbarLayout);
        setContentView(root);
        updateLabels();
    }

    private Button smallButton(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12f);
        b.setOnClickListener(listener);
        return b;
    }

    private void cycleStyle(String key, int[] values) {
        int fallback = key.equals("size") ? 100 : 75;
        int old = getSharedPreferences("touch_controls", MODE_PRIVATE).getInt(key, fallback);
        int next = values[0];
        for (int i = 0; i < values.length; i++) {
            if (values[i] == old) {
                next = values[(i + 1) % values.length];
                break;
            }
        }
        touch.releaseAll();
        getSharedPreferences("touch_controls", MODE_PRIVATE).edit().putInt(key, next).apply();
        touch.updateStyle();
        updateLabels();
    }

    private void updateLabels() {
        android.content.SharedPreferences p = getSharedPreferences("touch_controls", MODE_PRIVATE);
        sizeButton.setText("Size " + p.getInt("size", 100) + "%");
        opacityButton.setText("Opacity " + p.getInt("opacity", 75) + "%");
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onPause() {
        if (touch != null) touch.releaseAll();
        super.onPause();
    }
}
