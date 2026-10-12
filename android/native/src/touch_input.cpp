// Android touchscreen -> SDL3 virtual Xbox-style gamepad.
// SDL's existing input driver consumes this through normal GAMEPAD_ADDED/AXIS/BUTTON events.
#include "touch_input.h"

#include <SDL3/SDL_error.h>
#include <SDL3/SDL_gamepad.h>
#include <SDL3/SDL_init.h>
#include <SDL3/SDL_joystick.h>
#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <mutex>

namespace thps::touch {
namespace {
std::mutex mutex;
bool ready = false;
bool enabled = false;
SDL_JoystickID id = 0;
SDL_Joystick* joystick = nullptr;
uint32_t last_buttons = 0;
std::array<Sint16, SDL_GAMEPAD_AXIS_COUNT> last_axes{};

void NeutralLocked() {
  if (!joystick) return;
  for (int b = 0; b < SDL_GAMEPAD_BUTTON_COUNT; ++b) {
    SDL_SetJoystickVirtualButton(joystick, b, false);
  }
  for (int a = 0; a < SDL_GAMEPAD_AXIS_COUNT; ++a) {
    // Raw virtual trigger axes rest at minimum. SDL maps them to gamepad trigger zero.
    SDL_SetJoystickVirtualAxis(joystick, a, a >= SDL_GAMEPAD_AXIS_LEFT_TRIGGER
                                              ? SDL_JOYSTICK_AXIS_MIN : 0);
  }
  last_buttons = 0;
  last_axes.fill(0);
  last_axes[SDL_GAMEPAD_AXIS_LEFT_TRIGGER] = SDL_JOYSTICK_AXIS_MIN;
  last_axes[SDL_GAMEPAD_AXIS_RIGHT_TRIGGER] = SDL_JOYSTICK_AXIS_MIN;
}

void SyncAttachmentLocked() {
  // SDLActivity can call JNI before the ReXGlue driver initializes SDL_INIT_GAMEPAD.
  // Retry on the first touch rather than silently leaving the touchscreen disconnected.
  if (!ready) ready = SDL_WasInit(SDL_INIT_GAMEPAD) != 0;
  if (!ready) return;
  if (enabled && !joystick) {
    SDL_VirtualJoystickDesc desc;
    SDL_INIT_INTERFACE(&desc);
    desc.type = SDL_JOYSTICK_TYPE_GAMEPAD;
    desc.naxes = SDL_GAMEPAD_AXIS_COUNT;
    desc.nbuttons = SDL_GAMEPAD_BUTTON_COUNT;
    id = SDL_AttachVirtualJoystick(&desc);
    if (id != 0) {
      joystick = SDL_OpenJoystick(id);
    }
    if (!joystick) {
      if (id) SDL_DetachVirtualJoystick(id);
      id = 0;
      __android_log_print(ANDROID_LOG_WARN, "rb2_touch",
                          "virtual gamepad unavailable: %s", SDL_GetError());
    } else {
      NeutralLocked();
    }
  } else if (!enabled && joystick) {
    NeutralLocked();
    SDL_CloseJoystick(joystick);
    joystick = nullptr;
    SDL_DetachVirtualJoystick(id);
    id = 0;
  }
}

Sint16 Stick(float value) {
  if (!std::isfinite(value)) return 0;
  return static_cast<Sint16>(std::lround(std::clamp(value, -1.0f, 1.0f) * 32767.0f));
}

Sint16 Trigger(float value) {
  if (!std::isfinite(value)) return SDL_JOYSTICK_AXIS_MIN;
  return static_cast<Sint16>(std::lround(
      std::clamp(value, 0.0f, 1.0f) * 65535.0f - 32768.0f));
}

void SetStateLocked(uint32_t buttons, const std::array<Sint16, SDL_GAMEPAD_AXIS_COUNT>& axes) {
  if (!ready || !enabled || !joystick) return;
  for (int b = 0; b < SDL_GAMEPAD_BUTTON_COUNT; ++b) {
    const uint32_t bit = uint32_t{1} << b;
    if ((last_buttons & bit) != (buttons & bit)) {
      SDL_SetJoystickVirtualButton(joystick, b, (buttons & bit) != 0);
    }
  }
  last_buttons = buttons;
  for (int a = 0; a < SDL_GAMEPAD_AXIS_COUNT; ++a) {
    if (last_axes[a] != axes[a]) {
      SDL_SetJoystickVirtualAxis(joystick, a, axes[a]);
      last_axes[a] = axes[a];
    }
  }
}
}  // namespace

void Initialize() {
  std::lock_guard guard(mutex);
  // The ReXGlue SDLInputDriver sets SDL_INIT_GAMEPAD when the game window opens.
  ready = SDL_WasInit(SDL_INIT_GAMEPAD) != 0;
  if (ready) SyncAttachmentLocked();
}

void Shutdown() {
  std::lock_guard guard(mutex);
  if (joystick && SDL_WasInit(SDL_INIT_GAMEPAD)) {
    NeutralLocked();
    SDL_CloseJoystick(joystick);
    SDL_DetachVirtualJoystick(id);
  }
  joystick = nullptr;
  id = 0;
  ready = false;
  enabled = false;
}

void Enable(bool on) {
  std::lock_guard guard(mutex);
  enabled = on;
  SyncAttachmentLocked();
}

void State(uint32_t buttons, float lx, float ly, float rx, float ry, float lt, float rt) {
  std::lock_guard guard(mutex);
  if (enabled && !joystick) SyncAttachmentLocked();
  const std::array<Sint16, SDL_GAMEPAD_AXIS_COUNT> axes = {
      Stick(lx), Stick(ly), Stick(rx), Stick(ry), Trigger(lt), Trigger(rt)};
  SetStateLocked(buttons, axes);
}

void Reset() {
  State(0, 0, 0, 0, 0, 0, 0);
}

}  // namespace thps::touch

extern "C" JNIEXPORT void JNICALL
Java_org_rb2recomp_android_GameActivity_nativeTouchEnabled(JNIEnv*, jclass, jboolean enabled) {
  thps::touch::Enable(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_rb2recomp_android_GameActivity_nativeTouchState(
    JNIEnv*, jclass, jint buttons, jfloat lx, jfloat ly, jfloat rx, jfloat ry,
    jfloat lt, jfloat rt) {
  thps::touch::State(static_cast<uint32_t>(buttons), lx, ly, rx, ry, lt, rt);
}

extern "C" JNIEXPORT void JNICALL
Java_org_rb2recomp_android_GameActivity_nativeTouchReset(JNIEnv*, jclass) {
  thps::touch::Reset();
}
