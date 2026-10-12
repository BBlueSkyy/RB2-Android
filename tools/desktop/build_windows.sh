#!/usr/bin/env bash
# Cross-build the Windows desktop reference (rb2.exe) with the desktop SDK prepared for
# Project8Recomp (~/thp8/rexglue-sdk, installed prefixes out/install/{linux-amd64,win-amd64-rd}) and
# its clang-cl + xwin toolchain (the maintainer's layout: ~/thp8/... - adjust the paths below). Output staged to C:\rb2-work\win.
#
#   tools/desktop/build_windows.sh [game dir with default.xex]
set -euo pipefail
export PATH="/usr/lib/llvm-20/bin:$HOME/.cargo/bin:$PATH"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
GAME="${1:-/mnt/c/rb2-work/game}"
SDK=~/thp8/rexglue-sdk/out/install
CLI="$SDK/linux-amd64/bin/rexglue"
TOOLCHAIN=~/thp8/Project8Recomp/tools/cross/windows-msvc.cmake
D="$REPO/desktop"
OUT=/mnt/c/rb2-work/win

mkdir -p "$D/config"
sed -e "s#REPLACE_ME#$GAME#g" "$REPO/config/rb2_manifest.toml" > "$D/config/rb2_manifest.local.toml"
cp ~/thp8/Project8Recomp/src/game/src/win_math_compat.cpp "$D/game/"
(cd "$D/config" && LD_LIBRARY_PATH="$SDK/linux-amd64/lib:${LD_LIBRARY_PATH:-}" "$CLI" codegen rb2_manifest.local.toml > /tmp/rb2_desk_cg.log 2>&1) \
  || { tail -20 /tmp/rb2_desk_cg.log; exit 1; }
# rexglue_setup_target expects generated/ next to the game sources (as in Project8Recomp src/).
ln -sfn ../generated "$D/game/generated"
cmake -S "$D/game" -B "$D/build-win" -G Ninja -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
  -DCMAKE_PREFIX_PATH="$SDK/win-amd64-rd" -DRB2_REXGLUE_HOST_EXECUTABLE="$CLI" \
  -DCMAKE_BUILD_TYPE=RelWithDebInfo > /tmp/rb2_desk_cfg.log 2>&1 || { tail -30 /tmp/rb2_desk_cfg.log; exit 1; }
cmake --build "$D/build-win" --parallel "$(nproc)" > /tmp/rb2_desk_build.log 2>&1 \
  || { grep -m8 -B2 -A6 "error" /tmp/rb2_desk_build.log; tail -5 /tmp/rb2_desk_build.log; exit 1; }
mkdir -p "$OUT"
cp "$D/build-win/rb2.exe" "$OUT/"
cp ~/thp8/Project8Recomp/build/game-win-rd/*.dll "$OUT/" 2>/dev/null || true
ls -la "$OUT"
