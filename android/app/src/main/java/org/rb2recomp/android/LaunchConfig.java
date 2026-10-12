package org.rb2recomp.android;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where things live and which command-line settings the game starts with.
 *
 * Everything is under the app's external files folder (Android/data/&lt;package&gt;/files), which needs
 * no storage permission and is reachable over USB / adb:
 *
 *   game/    the extracted disc (contains default.xex), copied here by the user
 *   saves/   the game's user data
 *   cache/   shader and pipeline caches
 *   logs/    the game's log, the Vulkan probe report
 *   perf/    per-frame timing CSV
 *   args.txt optional, one setting per line (e.g. --vsync=false); overrides the defaults below
 */
final class LaunchConfig {
    private LaunchConfig() {}

    static File root(Context context) {
        File dir = context.getExternalFilesDir(null);
        return dir != null ? dir : context.getFilesDir();
    }

    static File dir(Context context, String name) {
        File dir = new File(root(context), name);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    static File gameDataRoot(Context context) {
        return new File(root(context), "game");
    }

    static boolean gameInstalled(Context context) {
        return new File(gameDataRoot(context), "default.xex").isFile();
    }

    // SDK-level settings proven on the Thor (Adreno 740) by the Project8Recomp port, which shares
    // this SDK tree and its patches. Its game-specific hooks (guest_*_wait, vertex unpack, the
    // display-mode / present-interval / frame-limiter hooks) do not apply to Raging Blast 2.
    private static final String[] PERFORMANCE = {
        "--gpu_cp_fastpath=true",
        "--primitive_processor_cache_min_indices=4096",
        "--gpu_sampler_set_reuse=true",
        "--gpu_texture_request_reuse=true",
        "--gpu_texture_last_view_cache=true",
        "--gpu_texture_descriptor_set_adjacent_reuse=true",
        "--timer_queue_blocking_wait=true",
        // Tiled-GPU settings: keep render passes open across uploads and texture loads.
        "--gpu_hoist_shared_memory_uploads=true",
        "--gpu_hoist_texture_loads=true",
        // Leaner translated pixel shaders: implicit-LOD sampling, texture sign / gamma / exponent
        // bias as pipeline-time constants.
        "--gpu_implicit_texture_lod=true",
        "--gpu_static_texture_signs=true",
        // Resolves of 8_8_8_8 / depth render targets in one compute pass.
        "--gpu_fused_resolve=true",
        // Mesa Turnip, bundled in the APK; falls back to the stock driver when missing or after two
        // failed starts. --android_vulkan_driver= (empty) in args.txt forces the stock driver.
        // The game puts its music and voices on the 5.1 center channel; a 6-channel stream
        // handed to Android lost them on the Thor's speakers. Fold to stereo ourselves.
        "--audio_force_stereo=true",
        "--android_vulkan_driver=turnip",
        "--kernel_wait_poll_backoff_us=100",
        "--android_thread_affinity=true",
        "--android_affinity_mode=5",
        // FIFO presentation only (frame generation pacing needs it).
        "--vulkan_allow_present_mode_mailbox=false",
        "--vulkan_allow_present_mode_immediate=false",
        "--vulkan_allow_present_mode_fifo_relaxed=false",
    };

    // Player options chosen in the launcher (SharedPreferences "options"); args.txt still wins.
    // Resolution / anti-aliasing were Project 8 display-mode hooks: kept for the launcher UI, not
    // applied to Raging Blast 2 yet.
    static final int[] RENDER_SCALES = {100, 85, 75, 60, 50};

    static int renderScale(Context context) {
        return context.getSharedPreferences("options", Context.MODE_PRIVATE).getInt("render_scale", 100);
    }

    // Frame rate modes: 0 = locked 30 shown at 60 with frame generation, 1 = locked 30,
    // 2 = 60 (the game's own rate; default), 3 = 60 + frame generation (120 shown on a 120 Hz screen).
    static final String[] FPS_MODES = {"30 + frame generation (60 shown)", "Locked 30", "60",
            "60 + frame generation (120 shown)"};

    static boolean sixty(int fpsMode) {
        return fpsMode == 2 || fpsMode == 3;
    }

    static boolean frameGeneration(int fpsMode) {
        return fpsMode == 0 || fpsMode == 3;
    }

    static int fpsMode(Context context) {
        return context.getSharedPreferences("options", Context.MODE_PRIVATE).getInt("fps_mode", 2);
    }

    static void setRenderScale(Context context, int scale) {
        context.getSharedPreferences("options", Context.MODE_PRIVATE).edit().putInt("render_scale", scale).apply();
    }

    // Upscaler from the internal resolution to the screen: bilinear, or AMD FSR 1 (EASU + RCAS).
    static boolean fsr(Context context) {
        return context.getSharedPreferences("options", Context.MODE_PRIVATE).getBoolean("fsr", true);
    }

    static void setFsr(Context context, boolean on) {
        context.getSharedPreferences("options", Context.MODE_PRIVATE).edit().putBoolean("fsr", on).apply();
    }

    static boolean msaa(Context context) {
        return context.getSharedPreferences("options", Context.MODE_PRIVATE).getBoolean("msaa", true);
    }

    static void setMsaa(Context context, boolean on) {
        context.getSharedPreferences("options", Context.MODE_PRIVATE).edit().putBoolean("msaa", on).apply();
    }

    // On-screen frame rate in the top right corner.
    static boolean showFps(Context context) {
        return context.getSharedPreferences("options", Context.MODE_PRIVATE).getBoolean("show_fps", false);
    }

    static void setShowFps(Context context, boolean on) {
        context.getSharedPreferences("options", Context.MODE_PRIVATE).edit().putBoolean("show_fps", on).apply();
    }

    static void setFpsMode(Context context, int mode) {
        context.getSharedPreferences("options", Context.MODE_PRIVATE).edit().putInt("fps_mode", mode).apply();
    }

    /** Internal resolution label at a scale (the game renders 1280x720). */
    static String renderSize(int scale) {
        return renderSize(scale, true);
    }

    static String renderSize(int scale, boolean msaa) {
        int w = (1280 * Math.min(100, scale) / 100 + 4) & ~7;
        int h = (720 * Math.min(100, scale) / 100 + 4) & ~7;
        return w + "x" + h;
    }

    /** Name part of "--name=value", used to let args.txt replace a default instead of repeating it. */
    private static String optionName(String arg) {
        int eq = arg.indexOf('=');
        return eq < 0 ? arg : arg.substring(0, eq);
    }

    static String[] buildArguments(Context context) {
        File root = root(context);
        // The CLI parser rejects a repeated non-boolean option, so later sources replace earlier ones.
        Map<String, String> args = new LinkedHashMap<>();
        List<String> defaults = new ArrayList<>();
        defaults.add("--game_data_root=" + gameDataRoot(context));
        defaults.add("--user_data_root=" + dir(context, "saves"));
        defaults.add("--cache_root=" + dir(context, "cache"));
        defaults.add("--log_file=" + new File(dir(context, "logs"), "rb2.log"));
        defaults.add("--log_level=info");
        defaults.add("--log_flush_interval=2");
        defaults.add("--perf_log_csv=" + new File(dir(context, "perf"), "frames.csv"));
        defaults.add("--gpu_plugin=xenos");
        defaults.add("--mnk_mode=true");
        // Without this the window is not fullscreen: the system bars stay on screen and the picture
        // is letterboxed inside the remaining area.
        defaults.add("--fullscreen=true");
        // Adreno has no sparse binding; the framebuffer-object render target path is the portable one.
        defaults.add("--vulkan_sparse_shared_memory=false");
        defaults.add("--render_target_path_vulkan=fbo");
        for (String flag : PERFORMANCE) {
            defaults.add(flag);
        }
        // No busy-waiting before vblanks / the frame cap.
        defaults.add("--vblank_spin_us=0");
        defaults.add("--gpu_swap_cap_spin_us=0");
        defaults.add("--gpu_regmem_yield_window_us=300");
        defaults.add("--gpu_regmem_max_sleep_us=1000");
        // The guest 60 Hz vblank follows the display's own vsync (AChoreographer).
        defaults.add("--gpu_vblank_display_lock=true");
        defaults.add("--present_effect=" + (fsr(context) ? "fsr" : "bilinear"));
        if (showFps(context)) {
            defaults.add("--show_debug_overlay=true");
        }
        // The in-game menu switches frame generation and the frame rate while the game runs, so the
        // generator always exists and presentation stays FIFO.
        defaults.add("--gpu_frame_generation_available=true");
        int fpsMode = fpsMode(context);
        defaults.add("--gpu_frame_generation=" + frameGeneration(fpsMode));
        if (sixty(fpsMode)) {
            defaults.add("--gpu_swap_fps_cap=60");
            // The GPU command processor alone on the prime core.
            defaults.add("--android_affinity_mode=2");
            if (!frameGeneration(fpsMode)) {
                // A frame still being emulated at a vblank holds that vblank until it is ready.
                defaults.add("--gpu_vblank_hold_us=15000");
            }
        } else {
            defaults.add("--gpu_swap_fps_cap=30");
            defaults.add("--rb2_vsync_hz=30");
        }
        for (String arg : defaults) {
            args.put(optionName(arg), arg);
        }

        File override = new File(root, "args.txt");
        if (override.isFile()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(override))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        args.put(optionName(line), line);
                    }
                }
            } catch (Exception e) {
                android.util.Log.w("rb2", "could not read args.txt", e);
            }
        }
        return args.values().toArray(new String[0]);
    }
}
