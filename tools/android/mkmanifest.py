#!/usr/bin/env python3
"""Convert WistfulHopes/RB2's pre-0.2 rb2_config.toml into an SDK 0.10 codegen manifest.

  mkmanifest.py <rb2_config.toml> <out manifest.toml>
"""
import re
import sys

src, dst = sys.argv[1], sys.argv[2]
# --no-file-crt: leave the game's own file I/O recompiled (it then goes through the emulated kernel's
# NtCreateFile/NtReadFile) instead of the rexcrt native file layer.
NO_FILE_CRT = "--no-file-crt" in sys.argv[3:]
# --no-crt: no rexcrt replacements at all (bisecting).
NO_CRT = "--no-crt" in sys.argv[3:]
funcs = []
crt = {}
# The guest CRT routines the SDK's rexcrt layer replaces natively. Names on the right are the
# rexcrt keys; addresses come from the original project's config / hooks.cpp.
CRT_FROM_NAMES = {
    "rex_CloseHandle": "CloseHandle", "rex_CreateFileA": "CreateFileA", "rex_WriteFile": "WriteFile",
    "rex_ReadFile": "ReadFile", "rex_RtlSizeHeap": "RtlSizeHeap", "rex_RtlAllocateHeap": "RtlAllocateHeap",
    "rex_RtlFreeHeap": "RtlFreeHeap", "rex_RtlReAllocateHeap": "RtlReAllocateHeap",
    "rex_SetFilePointer": "SetFilePointer", "rex_SetEndOfFile": "SetEndOfFile", "rex_SetFileTime": "SetFileTime",
    "rex_FlushFileBuffers": "FlushFileBuffers", "rex_MoveFileA": "MoveFileA", "rex_FindFirstFileA": "FindFirstFileA",
    "rex_GetFileAttributesA": "GetFileAttributesA", "rex_GetFileSize": "GetFileSize",
    "rex_FindNextFileA": "FindNextFileA", "rex_DeleteFileA": "DeleteFileA", "rex_GetFileSizeEx": "GetFileSizeEx",
}
# From the original src/hooks.cpp (GUEST_FUNCTION_HOOK sub_xxx -> native). Only the ones rexcrt
# implements with the same semantics; the printf family is left recompiled (guest va_lists).
CRT_FROM_HOOKS = {
    "memset": 0x8244E7D0, "memcpy": 0x8244E2A0, "memmove": 0x8244EEB0,
    "strncmp": 0x8244CEA0, "strncpy": 0x8244D5F8, "strcpy_s": 0x8244FB88, "wcslen": 0x8244FC40,
}
# Code the original list does not cover and SDK 0.10's discovery only reaches as a tail-jump chunk
# (unresolved inner branches -> REX_FATAL). Declared as functions of their own: start -> end.
EXTRA_FUNCS = {
    0x822289C8: 0x82228C78,  # switch on +4140 (5 cases), tail-jumped from sub_82228350
}
pat = re.compile(r'^(0x[0-9A-Fa-f]+)\s*=\s*\{\s*name\s*=\s*"([^"]+)"\s*,\s*size\s*=\s*(0x[0-9A-Fa-f]+)\s*\}')
for line in open(src, encoding="utf-8"):
    m = pat.match(line.strip())
    if not m:
        continue
    addr, name, size = int(m.group(1), 16), m.group(2), int(m.group(3), 16)
    if name in CRT_FROM_NAMES and not (NO_FILE_CRT and "Heap" not in name):
        crt[CRT_FROM_NAMES[name]] = addr
    funcs.append((addr, size))
crt.update(CRT_FROM_HOOKS)
if NO_CRT:
    crt = {}

with open(dst, "w", newline="\n") as o:
    o.write("""# ReXGlue codegen manifest for Dragon Ball: Raging Blast 2 (Xbox 360, USA Rev 1).
#
# Converted from WistfulHopes/RB2's rb2_config.toml (pre-0.2 SDK format) by
# tools/android/mkmanifest.py: same function boundaries, the CRT routines it
# hooked by hand now map to the SDK's rexcrt layer.
#
# TRAP (from THP8): every codegen table is read from [entrypoint]: write
# [entrypoint.functions] / [entrypoint.rexcrt]; a top-level table is ignored.

[project]
name = "rb2"
sdk_version = "0.10.0"
game_root = "REPLACE_ME"

[entrypoint]
file_path = "REPLACE_ME/default.xex"
out_directory_path = "../generated/default"
includes = []

""")
    if crt:
        o.write("[entrypoint.rexcrt]\n")
    for k in sorted(crt):
        o.write(f"{k} = 0x{crt[k]:08X}\n")
    o.write("\n[entrypoint.functions]\n")
    for addr, size in funcs:
        o.write(f"0x{addr:08X} = {{ size = 0x{size:X} }}\n")
    for addr, end in EXTRA_FUNCS.items():
        o.write(f"0x{addr:08X} = {{ end = 0x{end:08X} }}\n")
print(f"{len(funcs)} functions, {len(crt)} rexcrt entries -> {dst}")
