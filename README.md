> **Android port fork.** [**Download the Android APK**](https://github.com/moddedrounds/RB2-Android/releases/latest/download/RB2-Android.apk), then follow
> **[ANDROID.md](ANDROID.md#players-start-here)**: pick the ISO of your own disc in the app and play.
> No game data is included; you need your own legally obtained copy of the game.

# rb2 - Dragon Ball Raging Blast 2 Recompiled

Recompiled using the [ReXGlue SDK](https://github.com/rexglue/rexglue-sdk)

## Setup

1. Ensure REXSDK is set properly in your environment - see [ReXGlue SDK](https://github.com/rexglue/rexglue-sdk) for more info
1. Extract to `assets`
1. Run cmake configure
1. Run re2_codegen utility target from cmake
1. Run cmake build for re2
1. Launch with first argument pointing to the assets folder

## Credits

- [ReXGlue](https://github.com/rexglue/rexglue-sdk) for the recompilation tools.
- [dlmalloc](https://gee.cs.oswego.edu/dl/html/malloc.html) for the heap allocator.
- [REBlue](https://github.com/rexglue/reblue) for the dlmalloc setup.
## Legal

This repository contains no game data: no disc image, `default.xex`, extracted files or recompiled
code. You need your own legally obtained copy of Dragon Ball: Raging Blast 2 for Xbox 360.
