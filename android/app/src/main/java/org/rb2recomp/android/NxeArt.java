package org.rb2recomp.android;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Launcher background from the player's own installed game: the disc's dashboard art package
 * (game/nxeart, an STFS "CON"/"LIVE"/"PIRS" package) holds the title's background image (nxebg.jpg).
 * It is read from the installed copy on the device; the APK ships no game art. Port of
 * tools/android/nxeart.py (same STFS block layout rules as Xenia).
 */
final class NxeArt {
    private NxeArt() {}

    static File backgroundFile(Context context) {
        return new File(context.getFilesDir(), "art/launcher_bg.jpg");
    }

    /** Extracts the background if the game is installed and it is not there yet. Returns true when present. */
    static boolean ensureBackground(Context context) {
        File out = backgroundFile(context);
        if (out.isFile() && out.length() > 0) return true;
        File nxeart = new File(LaunchConfig.gameDataRoot(context), "nxeart");
        if (!nxeart.isFile()) return false;
        try (RandomAccessFile f = new RandomAccessFile(nxeart, "r")) {
            byte[] image = findBackground(f);
            if (image == null) return false;
            //noinspection ResultOfMethodCallIgnored
            out.getParentFile().mkdirs();
            File tmp = new File(out.getPath() + ".tmp");
            try (FileOutputStream os = new FileOutputStream(tmp)) {
                os.write(image);
            }
            return tmp.renameTo(out);
        } catch (Exception e) {
            android.util.Log.w("rb2", "nxeart background", e);
            return false;
        }
    }

    /** Forget the extracted art (a reinstall from another image re-extracts it). */
    static void clear(Context context) {
        //noinspection ResultOfMethodCallIgnored
        backgroundFile(context).delete();
    }

    private static byte[] read(RandomAccessFile f, long off, int len) throws IOException {
        byte[] b = new byte[len];
        f.seek(off);
        f.readFully(b);
        return b;
    }

    private static int be32(byte[] b, int o) {
        return ((b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }

    private static int le24(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8) | ((b[o + 2] & 0xFF) << 16);
    }

    private static byte[] findBackground(RandomAccessFile f) throws IOException {
        byte[] head = read(f, 0, 0x400);
        String magic = new String(head, 0, 4, "ISO-8859-1");
        if (!magic.equals("CON ") && !magic.equals("LIVE") && !magic.equals("PIRS")) return null;
        final boolean con = magic.equals("CON ");
        int headerSize = be32(head, 0x340);
        byte[] vd = new byte[0x24];
        System.arraycopy(head, 0x379, vd, 0, 0x24);
        int blockSep = vd[2] & 0xFF;
        int ftBlocks = (vd[3] & 0xFF) | ((vd[4] & 0xFF) << 8);
        int ftStart = le24(vd, 5);
        int shift = (con && (blockSep & 1) == 0) ? 1 : 0;
        if (((headerSize + 0xFFF) & 0xF000) == 0xB000) shift = con ? 1 : 0;
        final long dataBase = (headerSize + 0xFFF) & 0xFFFFF000L;
        final int sh = shift;

        java.util.function.IntToLongFunction blockOffset = block -> {
            long base = (block + 0xAA) / 0xAA;
            if (con) base <<= sh;
            long off = base + block;
            if (block >= 0xAA) {
                base = (block + 0x70E4) / 0x70E4;
                if (con) base <<= sh;
                off += base;
                if (block >= 0x70E4) {
                    base = (block + 0x4AF768) / 0x4AF768;
                    if (con) base <<= sh;
                    off += base;
                }
            }
            return dataBase + (off << 12);
        };

        byte[] table = new byte[ftBlocks * 0x1000];
        for (int i = 0; i < ftBlocks; i++) {
            System.arraycopy(read(f, blockOffset.applyAsLong(ftStart + i), 0x1000), 0, table, i * 0x1000, 0x1000);
        }
        byte[] best = null;
        for (int i = 0; i + 0x40 <= table.length; i += 0x40) {
            int flags = table[i + 0x28] & 0xFF;
            int nlen = flags & 0x3F;
            if (nlen == 0 || (flags & 0x80) != 0) continue;
            String name = new String(table, i, nlen, "ISO-8859-1").toLowerCase(java.util.Locale.US);
            int nblocks = le24(table, i + 0x29);
            int start = le24(table, i + 0x2F);
            int size = be32(table, i + 0x34);
            if (size <= 0 || size > (8 << 20) || !(name.endsWith(".jpg") || name.endsWith(".png"))) continue;
            byte[] buf = new byte[nblocks * 0x1000];
            for (int k = 0; k < nblocks; k++) {
                System.arraycopy(read(f, blockOffset.applyAsLong(start + k), 0x1000), 0, buf, k * 0x1000, 0x1000);
            }
            byte[] blob = java.util.Arrays.copyOf(buf, size);
            if (name.contains("bg")) return blob;          // nxebg.jpg: the dashboard background
            if (best == null || blob.length > best.length) best = blob;
        }
        return best;
    }
}
