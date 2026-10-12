// rb2 - Dragon Ball: Raging Blast 2, recompiled with ReXGlue (SDK 0.10).
//
// The original project (WistfulHopes/RB2) predates the SDK's 0.2 overhaul and replaced the game's
// heap and file I/O by hand (src/bdengine). SDK 0.10 ships the same thing as its rexcrt layer, so the
// codegen manifest (config/rb2_manifest.toml) maps the game's CRT routines to it instead.

#pragma once

#include <rex/cvar.h>
#include <rex/logging.h>
#include <rex/perf/counter.h>
#include <rex/rex_app.h>

class Rb2App : public rex::ReXApp {
 public:
  using rex::ReXApp::ReXApp;

  static std::unique_ptr<rex::ui::WindowedApp> Create(rex::ui::WindowedAppContext& ctx) {
    return std::unique_ptr<Rb2App>(new Rb2App(ctx, "rb2", PPCImageConfig));
  }

  // The runtime compiles the per-frame perf-counter CSV writer in, but nothing in the SDK opens
  // the --perf_log_csv file: wire it once logging is up (same as Project8Recomp).
  void OnPostSetup() override {
    std::string csv_path = REXCVAR_QUERY(std::string, perf_log_csv);
    if (!csv_path.empty()) {
      rex::perf::SetCsvLogPath(csv_path);
      REXLOG_INFO("perf: csv -> {}", csv_path);
    }
  }
};
