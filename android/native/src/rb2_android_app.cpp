// Registers the title's app class for the Android build.
//
// The game hooks are included here (once): each defines REX_HOOK_RAW() overrides of generated
// functions and their cvars.

#include "rb2_init.h"

#include "rb2_hooks.h"
#include "rb2_app.h"

REX_DEFINE_APP(rb2, Rb2App::Create)
