#!/usr/bin/env bash
# Manifest -> codegen -> native libraries -> APK, copied to out/rb2-<tag>.apk.
#
#   tools/android/rebuild.sh <tag> [mkmanifest flags, e.g. --no-file-crt]
#
# Game files: RB2_GAME = the folder holding YOUR extracted disc (default.xex, data/, nxeart).
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
TAG="${1:?usage: rebuild.sh <tag> [mkmanifest flags]}"; shift
GAME="${RB2_GAME:?set RB2_GAME to the folder with your extracted disc (default.xex)}"
cd "$REPO"
python3 tools/android/mkmanifest.py rb2_config.toml config/rb2_manifest.toml "$@"
tools/android/codegen.sh "$GAME" > /tmp/rb2_codegen.log 2>&1 || { tail -20 /tmp/rb2_codegen.log; exit 1; }
echo "unresolved branches: $(grep -c Unresolved /tmp/rb2_codegen.log || true)"
tools/android/build_native.sh > /tmp/rb2_native.log 2>&1 || { grep -m5 -B2 -A8 "error:" /tmp/rb2_native.log; tail -5 /tmp/rb2_native.log; exit 1; }
tools/android/build_apk.sh > /tmp/rb2_apk.log 2>&1 || { tail -30 /tmp/rb2_apk.log; exit 1; }
mkdir -p out
cp android/app/build/outputs/apk/release/app-release.apk "out/rb2-$TAG.apk"
echo "built out/rb2-$TAG.apk"
