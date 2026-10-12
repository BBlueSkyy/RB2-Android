#!/usr/bin/env python3
"""Pull the dashboard art out of an Xbox 360 disc's `nxeart` package (STFS: CON/LIVE/PIRS).

  nxeart.py <nxeart> <out_dir>

Writes icon.png (the package's 64x64 title thumbnail) and the package's images (background,
banner, ...) as art_<n>.<ext>, by walking the STFS file table (so files that span a hash block are
reassembled correctly). Also prints each image's size.
"""
import io
import os
import struct
import sys

from PIL import Image

path, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
d = open(path, "rb").read()
magic = d[:4]
assert magic in (b"CON ", b"LIVE", b"PIRS"), magic

# Thumbnails in the header (metadata v1/v2): title thumbnail at 0x571A, package thumbnail at 0x171A.
for off, name in ((0x571A, "icon.png"), (0x171A, "package_thumb.png")):
    size = struct.unpack(">I", d[0x1716:0x171A] if off == 0x171A else d[0x1712:0x1716])[0]
    blob = d[off:off + 0x4000]
    end = blob.find(b"IEND")
    if blob.startswith(b"\x89PNG") and end > 0:
        open(os.path.join(out, name), "wb").write(blob[:end + 8])

header_size = struct.unpack(">I", d[0x340:0x344])[0]
vd = d[0x379:0x379 + 0x24]  # STFS volume descriptor
block_sep = vd[2]
ft_blocks = struct.unpack("<H", vd[3:5])[0]
ft_start = int.from_bytes(vd[5:8], "little")
# Xenia's rule: CON packages that are not read-only use 2 hash tables per level.
shift = 1 if (magic == b"CON " and not (block_sep & 1)) else 0
if ((header_size + 0xFFF) & 0xF000) == 0xB000:
    shift = 1 if magic == b"CON " else 0
data_base = (header_size + 0xFFF) & 0xFFFFF000


def block_offset(block):
    base = (block + 0xAA) // 0xAA
    if magic == b"CON ":
        base <<= shift
    off = base + block
    if block >= 0xAA:
        base = (block + 0x70E4) // 0x70E4
        if magic == b"CON ":
            base <<= shift
        off += base
        if block >= 0x70E4:
            base = (block + 0x4AF768) // 0x4AF768
            if magic == b"CON ":
                base <<= shift
            off += base
    return data_base + (off << 12)


def read_file(start, nblocks, size):
    buf = bytearray()
    for i in range(nblocks):
        o = block_offset(start + i)
        buf += d[o:o + 0x1000]
    return bytes(buf[:size])


table = read_file(ft_start, ft_blocks, ft_blocks * 0x1000)
n = 0
for i in range(0, len(table), 0x40):
    e = table[i:i + 0x40]
    flags = e[0x28]
    nlen = flags & 0x3F
    if nlen == 0:
        continue
    name = e[:nlen].decode("latin-1")
    is_dir = bool(flags & 0x80)
    nblocks = int.from_bytes(e[0x29:0x2C], "little")
    start = int.from_bytes(e[0x2F:0x32], "little")
    size = struct.unpack(">I", e[0x34:0x38])[0]
    if is_dir:
        continue
    blob = read_file(start, nblocks, size)
    try:
        im = Image.open(io.BytesIO(blob))
        im.load()
    except Exception:
        print(f"{name}: {size} bytes (not an image)")
        continue
    ext = (im.format or "bin").lower().replace("jpeg", "jpg")
    dst = os.path.join(out, f"art_{n}_{os.path.splitext(name)[0]}.{ext}")
    open(dst, "wb").write(blob)
    print(f"{name}: {im.size[0]}x{im.size[1]} {im.format} -> {dst}")
    n += 1
for f in ("icon.png", "package_thumb.png"):
    p = os.path.join(out, f)
    if os.path.exists(p):
        print(f, Image.open(p).size)
