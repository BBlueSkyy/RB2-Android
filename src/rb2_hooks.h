// Raging Blast 2 game hooks (one translation unit includes this: the app registration file).
//
// From WistfulHopes/RB2 src/hooks.cpp, ported to the SDK 0.10 hook API. The CRT replacements
// (memcpy/memset/strings, heap, file I/O) moved to the manifest's [entrypoint.rexcrt] table; the
// printf-family hooks were dropped (they passed guest va_lists to the host CRT).

#pragma once

#include <chrono>
#include <cstdint>
#include <thread>

#include <rex/cvar.h>
#include <rex/hook.h>

REXCVAR_DEFINE_BOOL(rb2_vsync_timer, true, "RB2",
                    "Replace SpGameServer::_vsyncWait with a sleep to the next 1/rb2_vsync_hz "
                    "boundary (the original project's frame-rate fix); false = the game's own wait");
REXCVAR_DEFINE_UINT32(rb2_vsync_hz, 60, "RB2", "Tick rate of the replaced vsync wait");

REXCVAR_DEFINE_BOOL(rb2_trace_loads, false, "RB2",
                    "Log the middleware's asynchronous file loads (path, destination, status)");

extern "C" REX_FUNC(__imp__sub_8249B680);
extern "C" REX_FUNC(__imp__sub_82469320);
extern "C" REX_FUNC(__imp__sub_82469150);

// Boot loader: sub_823E24F0 starts an asynchronous load (sub_82469320(loader, path, 0, dest)) and
// polls sub_82469150(loader) until it is not 2 (busy); 4 = error, which makes the game's init fail.
REX_HOOK_RAW(sub_82469320) {
  REX_FUNC_PROLOGUE();
  if (REXCVAR_GET(rb2_trace_loads)) {
    const char* path = ctx.r4.u32 ? reinterpret_cast<const char*>(base + ctx.r4.u32) : "(null)";
    REXLOG_INFO("rb2 load: loader={:08X} path='{}' r5={:08X} r6={:08X}", ctx.r3.u32, path,
                ctx.r5.u32, ctx.r6.u32);
  }
  __imp__sub_82469320(ctx, base);
}

REX_HOOK_RAW(sub_82469150) {
  REX_FUNC_PROLOGUE();
  const uint32_t loader = ctx.r3.u32;
  __imp__sub_82469150(ctx, base);
  if (REXCVAR_GET(rb2_trace_loads)) {
    static thread_local uint32_t last = 0xFFFFFFFFu;
    if (ctx.r3.u32 != last) {
      REXLOG_INFO("rb2 load: loader={:08X} status {}", loader, ctx.r3.u32);
      last = ctx.r3.u32;
    }
  }
}

// SpGameServer::_vsyncWait
REX_HOOK_RAW(sub_8249B680) {
  REX_FUNC_PROLOGUE();
  if (!REXCVAR_GET(rb2_vsync_timer)) {
    __imp__sub_8249B680(ctx, base);
    return;
  }
  const uint32_t hz = REXCVAR_GET(rb2_vsync_hz) ? REXCVAR_GET(rb2_vsync_hz) : 60;
  const auto interval = std::chrono::nanoseconds(1000000000LL / hz);
  const auto now = std::chrono::steady_clock::now();
  std::this_thread::sleep_until(now + (interval - now.time_since_epoch() % interval));
}

// --- Middleware error trace (rb2_trace_cri) --------------------------------------------------
// The CRI ADX / file-system libraries report problems by passing message strings to a handful of
// error sinks (found by mapping the "E2005..."-style strings to their callees). With the cvar on,
// every distinct message is logged (first 20 times each).
REXCVAR_DEFINE_BOOL(rb2_trace_cri, false, "RB2",
                    "Log the CRI ADX / file-system middleware's error messages");

#include <cstring>
#include <mutex>
#include <string>
#include <unordered_map>

namespace rb2 {
inline const char* GuestStr(uint8_t* base, uint32_t addr) {
  if (addr < 0x40000000u || addr >= 0xA0000000u) return nullptr;
  const char* s = reinterpret_cast<const char*>(base + addr);
  for (int i = 0; i < 4; ++i)
    if (static_cast<unsigned char>(s[i]) < 0x20 || static_cast<unsigned char>(s[i]) > 0x7E)
      return nullptr;
  return s;
}
inline void TraceCri(const char* sink, uint8_t* base, uint32_t a, uint32_t b, uint32_t c) {
  const char* s[3] = {GuestStr(base, a), GuestStr(base, b), GuestStr(base, c)};
  if (!s[0] && !s[1] && !s[2]) return;
  std::string msg;
  for (auto* p : s) {
    if (!p) continue;
    if (!msg.empty()) msg += " | ";
    msg.append(p, strnlen(p, 200));
  }
  static std::mutex m;
  static std::unordered_map<std::string, uint32_t> seen;
  uint32_t n;
  {
    std::lock_guard<std::mutex> lock(m);
    n = ++seen[msg];
  }
  if (n <= 20) REXLOG_WARN("rb2 cri {} #{}: {}", sink, n, msg);
}
}  // namespace rb2

#define RB2_CRI_SINK(addr)                                                      \
  extern "C" REX_FUNC(__imp__sub_##addr);                                       \
  REX_HOOK_RAW(sub_##addr) {                                                    \
    REX_FUNC_PROLOGUE();                                                        \
    if (REXCVAR_GET(rb2_trace_cri))                                             \
      rb2::TraceCri(#addr, base, ctx.r3.u32, ctx.r4.u32, ctx.r5.u32);           \
    __imp__sub_##addr(ctx, base);                                               \
  }

RB2_CRI_SINK(8246B350)  // criErr notify (151 messages)
RB2_CRI_SINK(82472CD8)  // ADXERR 1-string
RB2_CRI_SINK(82472D60)  // ADXERR 2-string
RB2_CRI_SINK(82476BA8)
RB2_CRI_SINK(8247B9C0)
RB2_CRI_SINK(82479BE0)
RB2_CRI_SINK(8246FB98)  // SVM
RB2_CRI_SINK(824775F0)  // "IXAudio2SourceVoice_SubmitSourceBuffer failed" (message in r5)
RB2_CRI_SINK(82477640)
RB2_CRI_SINK(8246FAD8)
RB2_CRI_SINK(82479B48)

#include "rb2_resolution.h"
