package com.zynelabs.flowplay.data;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * On-disk cache of a fully parsed+tagged playlist, per account.
 *
 * <p>The user's #1 complaint: leaving the app re-scans the whole ~97k-entry
 * playlist from scratch every time. After a successful load we persist the
 * channel lists here; the home screen renders from cache instantly and then
 * refreshes quietly in the background.</p>
 *
 * <p>Custom compact binary format (fast to read, no reflection):</p>
 * <pre>
 *   "ZPLC" magic, int schema, long savedAt,
 *   int liveCount, int vodCount, int seriesCount,
 *   then each channel: kind, key, name, logo, group, url,
 *     server, user, pass, streamId, container, archive,
 *     stalkerPortal, stalkerMac, stalkerCmd, smartCat, country
 * </pre>
 * Pure java.io — no Android dependency, so the round-trip is unit-testable
 * on a plain JVM.
 */
public class PlaylistCache {

    /** Current on-disk format; schema 1 remains readable for seamless upgrades. */
    public static final int SCHEMA = 2;
    private static final int OLD_SCHEMA = 1;
    private static final int MAX_LIST_ENTRIES = 1_000_000;
    private static final long MAX_TOTAL_ENTRIES = 2_000_000L;
    private static final Object SAVE_LOCK = new Object();

    private static final int MAGIC = 0x5A504C43; // "ZPLC"

    public static class Data {
        public final List<Channel> live;
        public final List<Channel> vod;
        public final List<Channel> series;
        public final long savedAt;
        public Data(List<Channel> l, List<Channel> v, List<Channel> s, long t) {
            live = l; vod = v; series = s; savedAt = t;
        }
    }

    public static class Counts {
        public final int live;
        public final int vod;
        public final int series;
        public final long savedAt;
        public Counts(int l, int v, int s, long t) {
            live = l; vod = v; series = s; savedAt = t;
        }
    }

    private static File fileFor(File dir, String accountId) {
        String safe = accountId == null ? "x"
                : accountId.replaceAll("[^A-Za-z0-9]", "_");
        return new File(dir, "plcache_" + safe + ".bin");
    }

    /** Header-only read: cheap counts for the provider list. Null if none. */
    public static Counts readCounts(File dir, String accountId) {
        File f = fileFor(dir, accountId);
        if (!f.exists()) return null;
        DataInputStream in = null;
        try {
            in = new DataInputStream(new BufferedInputStream(
                    new FileInputStream(f)));
            if (in.readInt() != MAGIC) return null;
            int schema = in.readInt();
            if (schema != OLD_SCHEMA && schema != SCHEMA) return null;
            long savedAt = in.readLong();
            int l = in.readInt(), v = in.readInt(), s = in.readInt();
            if (!validCounts(l, v, s)) return null;
            return new Counts(l, v, s, savedAt);
        } catch (IOException e) {
            return null;
        } finally {
            closeQuiet(in);
        }
    }

    /** Full read. Returns null when missing, corrupt, or wrong schema. */
    public static Data load(File dir, String accountId) {
        File f = fileFor(dir, accountId);
        if (!f.exists()) return null;
        DataInputStream in = null;
        try {
            in = new DataInputStream(new BufferedInputStream(
                    new FileInputStream(f), 65536));
            if (in.readInt() != MAGIC) return null;
            int schema = in.readInt();
            if (schema != OLD_SCHEMA && schema != SCHEMA) return null;
            long savedAt = in.readLong();
            int nl = in.readInt(), nv = in.readInt(), ns = in.readInt();
            if (!validCounts(nl, nv, ns)) return null;
            List<Channel> live = readList(in, nl);
            List<Channel> vod = readList(in, nv);
            List<Channel> series = readList(in, ns);
            return new Data(live, vod, series, savedAt);
        } catch (IOException | RuntimeException e) {
            return null;
        } finally {
            closeQuiet(in);
        }
    }

    /** True when missing, corrupt, or still in the legacy format. */
    public static boolean needsRefresh(File dir, String accountId) {
        if (dir == null || !dir.isDirectory()) return true;
        File f = fileFor(dir, accountId);
        if (!f.exists()) return true;
        DataInputStream in = null;
        try {
            in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)));
            if (in.readInt() != MAGIC) return true;
            int schema = in.readInt();
            if (schema == OLD_SCHEMA || schema != SCHEMA) return true;
            in.readLong();
            if (!validCounts(in.readInt(), in.readInt(), in.readInt())) return true;
            return load(dir, accountId) == null;
        } catch (IOException | RuntimeException e) { return true; }
        finally { closeQuiet(in); }
    }

    private static List<Channel> readList(DataInputStream in, int n)
            throws IOException {
        if (n < 0 || n > MAX_LIST_ENTRIES) throw new IOException("invalid cache count");
        List<Channel> out = new ArrayList<>(Math.max(n, 16));
        for (int i = 0; i < n; i++) out.add(readChannel(in));
        return out;
    }

    private static boolean validCounts(int l, int v, int s) {
        return l >= 0 && v >= 0 && s >= 0
                && l <= MAX_LIST_ENTRIES && v <= MAX_LIST_ENTRIES
                && s <= MAX_LIST_ENTRIES
                && (long) l + v + s <= MAX_TOTAL_ENTRIES;
    }

    private static Channel readChannel(DataInputStream in) throws IOException {
        Channel c = new Channel();
        c.kind = in.readInt();
        c.key = in.readUTF();
        c.name = in.readUTF();
        c.logo = in.readUTF();
        c.group = in.readUTF();
        c.url = in.readUTF();
        c.server = in.readUTF();
        c.user = in.readUTF();
        c.pass = in.readUTF();
        c.streamId = in.readInt();
        c.container = in.readUTF();
        c.archive = in.readBoolean();
        c.stalkerPortal = in.readUTF();
        c.stalkerMac = in.readUTF();
        c.stalkerCmd = in.readUTF();
        // Do not restore derived labels.  The keyword tables can change between
        // app versions (and old caches otherwise keep misclassified adult items
        // forever); getSmartCat()/getCountry() lazily recompute them below.
        in.readUTF();
        c.smartCat = null;
        in.readUTF();
        c.country = null;
        return c;
    }

    /** Compatibility wrapper for callers that do not need the outcome. */
    public static void save(File dir, String accountId,
                            List<Channel> live, List<Channel> vod,
                            List<Channel> series) {
        saveChecked(dir, accountId, live, vod, series);
    }

    /** Writes, syncs, and atomically renames a unique sibling temporary file. */
    public static boolean saveChecked(File dir, String accountId,
                                      List<Channel> live, List<Channel> vod,
                                      List<Channel> series) {
        if (dir == null || !dir.isDirectory() || !dir.canWrite()
                || live == null || vod == null || series == null
                || !validCounts(live.size(), vod.size(), series.size())) return false;
        synchronized (SAVE_LOCK) {
            File dst = fileFor(dir, accountId), tmp = null;
            FileOutputStream fos = null;
            DataOutputStream out = null;
            try {
                tmp = uniqueTemp(dir);
                fos = new FileOutputStream(tmp);
                out = new DataOutputStream(new BufferedOutputStream(fos, 65536));
                out.writeInt(MAGIC);
                out.writeInt(SCHEMA);
                out.writeLong(System.currentTimeMillis());
                out.writeInt(live.size()); out.writeInt(vod.size()); out.writeInt(series.size());
                writeList(out, live); writeList(out, vod); writeList(out, series);
                out.flush();
                fos.getFD().sync();
                out.close(); out = null; fos = null;
                if (!tmp.renameTo(dst)) return false;
                tmp = null;
                return true;
            } catch (IOException | RuntimeException ignored) {
                return false;
            } finally {
                closeQuiet(out); closeQuiet(fos);
                if (tmp != null) try { tmp.delete(); } catch (Exception ignored) {}
            }
        }
    }

    private static File uniqueTemp(File dir) throws IOException {
        String prefix = "plcache_" + Long.toHexString(System.nanoTime()) + "_"
                + Thread.currentThread().getId() + "_";
        for (int i = 0; i < 100; i++) {
            File f = new File(dir, prefix + i + ".tmp");
            if (f.createNewFile()) return f;
        }
        throw new IOException("cannot create unique cache temporary");
    }

    private static void writeList(DataOutputStream out, List<Channel> l)
            throws IOException {
        for (Channel c : l) writeChannel(out, c);
    }

    private static void writeChannel(DataOutputStream out, Channel c)
            throws IOException {
        out.writeInt(c.kind);
        out.writeUTF(nn(c.key));
        out.writeUTF(nn(c.name));
        out.writeUTF(nn(c.logo));
        out.writeUTF(nn(c.group));
        out.writeUTF(nn(c.url));
        out.writeUTF(nn(c.server));
        out.writeUTF(nn(c.user));
        out.writeUTF(nn(c.pass));
        out.writeInt(c.streamId);
        out.writeUTF(nn(c.container));
        out.writeBoolean(c.archive);
        out.writeUTF(nn(c.stalkerPortal));
        out.writeUTF(nn(c.stalkerMac));
        out.writeUTF(nn(c.stalkerCmd));
        out.writeUTF(c.smartCat == null ? "" : c.smartCat);
        out.writeUTF(c.country == null ? "" : c.country);
    }

    private static String nn(String s) { return s == null ? "" : s; }

    public static void clear(File dir, String accountId) {
        try { fileFor(dir, accountId).delete(); } catch (Exception ignored) {}
    }

    private static void closeQuiet(java.io.Closeable c) {
        try { if (c != null) c.close(); } catch (Exception ignored) {}
    }

    /** Human "updated … ago" label for the home screen. */
    public static String ago(long savedAt) {
        long d = System.currentTimeMillis() - savedAt;
        if (d < 60_000) return "just now";
        long m = d / 60_000;
        if (m < 60) return m + "m ago";
        long h = m / 60;
        if (h < 24) return h + "h ago";
        return (h / 24) + "d ago";
    }

    // ---------------- JVM smoke test ----------------
    // javac: Channel.java Cats.java PlaylistCache.java ; java com.zynelabs.flowplay.data.PlaylistCache
    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0]
                : System.getProperty("java.io.tmpdir"));
        List<Channel> live = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            Channel c = new Channel();
            c.kind = Channel.LIVE;
            c.key = "m3u_" + i;
            c.name = "Test Channel " + i + " ⚽ Sports HD";
            c.logo = i % 3 == 0 ? "http://example.com/logo" + i + ".png" : "";
            c.group = i % 2 == 0 ? "UK | SPORTS" : "Premium Movies";
            c.url = "http://example.com/stream" + i;
            c.smartCat = "⚽ Sports";
            live.add(c);
        }
        List<Channel> vod = new ArrayList<>();
        Channel m = new Channel();
        m.kind = Channel.VOD; m.key = "vod_1"; m.name = "Movie (2024)";
        m.url = "http://example.com/movie.mp4"; m.container = "mp4";
        vod.add(m);
        long t0 = System.currentTimeMillis();
        save(dir, "test-acc", live, vod, new ArrayList<Channel>());
        long t1 = System.currentTimeMillis();
        Counts co = readCounts(dir, "test-acc");
        Data d = load(dir, "test-acc");
        long t2 = System.currentTimeMillis();
        if (co == null || d == null) throw new RuntimeException("cache miss");
        if (co.live != 500 || co.vod != 1) throw new RuntimeException("counts wrong");
        if (d.live.size() != 500 || d.vod.size() != 1) throw new RuntimeException("size wrong");
        Channel r = d.live.get(499);
        if (!"Test Channel 499 ⚽ Sports HD".equals(r.name)
                || !"Premium Movies".equals(r.group) // 499 is odd
                || r.smartCat != null
                || r.kind != Channel.LIVE) throw new RuntimeException("field mismatch");
        Channel r2 = d.live.get(498);
        if (!"UK | SPORTS".equals(r2.group)) throw new RuntimeException("group mismatch");
        if (!"mp4".equals(d.vod.get(0).container)) throw new RuntimeException("vod field mismatch");
        System.out.println("PlaylistCache OK: save " + (t1 - t0) + "ms, load "
                + (t2 - t1) + "ms, 501 channels, counts=" + co.live + "/"
                + co.vod + "/" + co.series);
        clear(dir, "test-acc");
    }
}
