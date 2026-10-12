#!/usr/bin/env bash
# Compile the GameHub-style virtual controller as an isolated Android JNI library.
# Shares the *same* SDL3 symbols/state as an already compiled ReXGlue runtime;
# never statically links a second SDL runtime.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LIBS="${1:-$ROOT/android/app/src/main/jniLibs/arm64-v8a}"
SDL_INCLUDE="${2:?usage: build_touch_bridge.sh <jniLibs/arm64-v8a dir> <SDL3/include dir>}"
NDK="${ANDROID_NDK_HOME:-${ANDROID_HOME:-$HOME/android-sdk}/ndk/28.2.13676358}"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
CXX="$TOOLCHAIN/aarch64-linux-android29-clang++"
READELF="$TOOLCHAIN/llvm-readelf"

test -x "$CXX" || { echo "Missing NDK cross-compiler: $CXX"; exit 1; }
test -f "$LIBS/librexruntimerd.so" || { echo "No pinned upstream librexruntimerd.so"; exit 1; }
test -f "$SDL_INCLUDE/SDL3/SDL_joystick.h" || { echo "Missing pinned SDL3 headers"; exit 1; }
test -f "$LIBS/libmain.so" || { echo "Missing pinned upstream libmain.so"; exit 1; }

# Fail the build instead of accidentally creating a second SDL universe in this .so.
# The checked-in touch_input.cpp has only JNI exports and references SDL via the .so.
"$CXX" -std=c++23 -O2 -fPIC -shared -Wall -Wextra -Werror \
    -ffunction-sections -fdata-sections -Wl,--gc-sections \
    -Wl,--no-undefined -Wl,-soname,libtouchbridge.so \
    -I "$SDL_INCLUDE" \
    "$ROOT/android/native/src/touch_input.cpp" \
    "$LIBS/librexruntimerd.so" -llog \
    -o "$LIBS/libtouchbridge.so"

echo "JNI bridge dependencies:"
"$READELF" -d "$LIBS/libtouchbridge.so" | grep NEEDED
echo "JNI methods:"
"$TOOLCHAIN/llvm-nm" -D --defined-only "$LIBS/libtouchbridge.so" | grep 'Java_org_rb2recomp_android_GameActivity_nativeTouch'
echo "[ok] compiled JNI bridge using the pinned upstream SDL3 runtime"
