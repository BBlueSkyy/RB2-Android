package org.rb2recomp.android;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        // The game puts its music and voices on the 5.1 center channel; a 6-channel stream
        // handed to Android lost them on the Thor's speakers. Fold to stereo ourselves.
        "--audio_force_stereo=true",
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

    /**
     * Starting resolution until the player picks one: handhelds with a fan (AYN Thor / Odin, Retroid,
     * Ayaneo, Anbernic) render at 100 %; phones throttle hard when hot (a Galaxy S25 Ultra fell to
     * 18 fps at locked 30 once warm), so high-end Snapdragons (Adreno 7xx / 8xx) start at 85 % and
     * everything else at 75 %. FSR 1 scales the picture back up.
     */
    static int defaultRenderScale(Context context) {
        String maker = (android.os.Build.MANUFACTURER + " " + android.os.Build.BRAND).toLowerCase(java.util.Locale.US);
        for (String handheld : new String[] {"ayn", "retroid", "ayaneo", "anbernic", "odin"}) {
            if (maker.contains(handheld)) return 100;
        }
        int generation = adrenoGeneration(context);
        return generation >= 7 ? 85 : 75;
    }

    static int renderScale(Context context) {
        return context.getSharedPreferences("options", Context.MODE_PRIVATE).getInt("render_scale",
                defaultRenderScale(context));
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

    // Raging Blast 2 runs its game logic once per frame: capped at 30 the whole game plays at half
    // speed. Only the 60 modes are offered; older saved 30 fps choices become 60.
    static int fpsMode(Context context) {
        int mode = context.getSharedPreferences("options", Context.MODE_PRIVATE).getInt("fps_mode", 2);
        return sixty(mode) ? mode : 2;
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
        return context.getSharedPreferences("options", Context.MODE_PRIVATE).getBoolean("msaa", false);
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
        // Same rounding as src/rb2_resolution.h (multiples of 16).
        int s = Math.min(100, scale);
        int w = Math.max(640, (1280 * s / 100 + 8) & ~15);
        int h = Math.max(360, (720 * s / 100 + 8) & ~15);
        return w + "x" + h;
    }

    /** Name part of "--name=value", used to let args.txt replace a default instead of repeating it. */
    private static String optionName(String arg) {
        int eq = arg.indexOf('=');
        return eq < 0 ? arg : arg.substring(0, eq);
    }

    private static final Pattern ADRENO = Pattern.compile("Adreno \\(TM\\) (\\d)\\d\\d");

    /** The Vulkan GPU name ("Adreno (TM) 740"), probed once and remembered. */
    static String gpuName(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE);
        String name = prefs.getString("gpu_name", null);
        if (name == null) {
            name = "";
            try {
                Matcher m = Pattern.compile("== Device 0: (.+) ==").matcher(VulkanProbe.run());
                if (m.find()) name = m.group(1).trim();
            } catch (Throwable ignored) {
            }
            prefs.edit().putString("gpu_name", name).apply();
        }
        return name;
    }

    /** 6, 7, 8 ... for an Adreno 6xx / 7xx / 8xx, 0 for any other GPU. */
    static int adrenoGeneration(Context context) {
        Matcher m = ADRENO.matcher(gpuName(context));
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    // Graphics driver (SharedPreferences "options" gpu_driver). Automatic picks per GPU: the bundled
    // Turnip on Adreno 6xx / 7xx, AUTO_8XX on Adreno 8xx, and the phone's own driver on everything
    // else (Mali, PowerVR, Xclipse, older Adreno). Before using a custom driver the game checks that
    // it finds the GPU, and falls back to the phone's driver when it does not (custom_driver.cpp).
    static final int DRIVER_AUTO = 0, DRIVER_SYSTEM = 1, DRIVER_TURNIP = 2, DRIVER_TURNIP8 = 3;
    static final String[] DRIVER_NAMES = {"Automatic", "Phone's own", "Turnip (Adreno 6xx/7xx)",
            "Turnip 8xx (experimental)"};
    // Folder under assets/drivers/ (tools/android/fetch_turnip.sh) and the library inside it.
    // Turnip 6xx/7xx falls back to the newer a8xx build (Mesa main) on Adreno 7xx variants R8 does not
    // know; the game uses the first folder whose driver finds the GPU (custom_driver.cpp).
    private static final String[] DRIVER_FOLDERS = {null, null, "turnip,turnip8", "turnip8"};
    private static final String[] DRIVER_LIBS = {null, null, "vulkan.ad07xx.so", "libvulkan_freedreno.so"};
    // The a8xx build draws with page faults through GMEM on the Adreno 830; sysmem rendering is clean.
    private static final String[] DRIVER_ENV = {null, null, null, "TU_DEBUG=sysmem"};
    // The stock driver on the Adreno 830 (Galaxy S25 Ultra) draws the same blue haze / flicker as
    // the Thor's stock driver; the a8xx Turnip renders correctly.
    static final int AUTO_8XX = DRIVER_TURNIP8;

    static int driverChoice(Context context) {
        int c = context.getSharedPreferences("options", Context.MODE_PRIVATE).getInt("gpu_driver", DRIVER_AUTO);
        return c >= 0 && c < DRIVER_NAMES.length ? c : DRIVER_AUTO;
    }

    /** Choosing a driver again also forgets that it failed to start before. */
    static void setDriverChoice(Context context, int choice) {
        context.getSharedPreferences("options", Context.MODE_PRIVATE).edit().putInt("gpu_driver", choice).apply();
        String folder = DRIVER_FOLDERS[choice == DRIVER_AUTO ? autoDriver(context) : choice];
        if (folder != null) {
            for (String f : folder.split(",")) {
                //noinspection ResultOfMethodCallIgnored
                new File(context.getFilesDir(), "drivers/.failed_" + f).delete();
            }
        }
    }

    static int autoDriver(Context context) {
        int generation = adrenoGeneration(context);
        if (generation == 6 || generation == 7) return DRIVER_TURNIP;
        if (generation == 8) return AUTO_8XX;
        return DRIVER_SYSTEM;
    }

    static int effectiveDriver(Context context) {
        int choice = driverChoice(context);
        return choice == DRIVER_AUTO ? autoDriver(context) : choice;
    }

    static String driverLabel(Context context) {
        int choice = driverChoice(context);
        return choice == DRIVER_AUTO ? "Automatic (" + DRIVER_NAMES[autoDriver(context)] + ")" : DRIVER_NAMES[choice];
    }

    /** What the last launch actually ran on, as written by the game (custom_driver.cpp). */
    static String lastDriver(Context context) {
        File f = new File(context.getFilesDir(), "drivers/last_driver.txt");
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            String line = reader.readLine();
            return line != null ? line.trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static void addDriverArguments(Context context, List<String> args) {
        int driver = effectiveDriver(context);
        if (DRIVER_FOLDERS[driver] == null) return;
        args.add("--android_vulkan_driver=" + DRIVER_FOLDERS[driver]);
        args.add("--android_vulkan_driver_lib=" + DRIVER_LIBS[driver]);
        if (DRIVER_ENV[driver] != null) args.add("--android_vulkan_driver_env=" + DRIVER_ENV[driver]);
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
        // Mali and PowerVR GPUs have no geometry shaders and no wireframe fill: the SDK has
        // fallbacks for both (primitive expansion in the vertex shader, solid fill).
        defaults.add("--vulkan_require_geometry_shader=false");
        defaults.add("--vulkan_require_fill_mode_non_solid=false");
        addDriverArguments(context, defaults);
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
        // Internal resolution (src/rb2_resolution.h); the presenter scales it to the screen.
        defaults.add("--rb2_render_scale=" + renderScale(context));
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
