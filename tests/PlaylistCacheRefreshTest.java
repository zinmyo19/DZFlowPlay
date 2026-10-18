package com.zynelabs.flowplay.data;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/** Plain-JVM regression test; run with Channel.java, Cats.java, PlaylistCache.java. */
public final class PlaylistCacheRefreshTest {
    private static Channel channel(String key, String name) {
        Channel c = new Channel();
        c.key = key; c.name = name; c.group = "Movies"; c.smartCat = "stale"; c.country = "stale";
        return c;
    }
    private static List<Channel> one(String key, String name) {
        List<Channel> l = new ArrayList<>(); l.add(channel(key, name)); return l;
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static void writeSchema1(File dir, String id) throws Exception {
        File f = new File(dir, "plcache_" + id + ".bin");
        DataOutputStream o = new DataOutputStream(new FileOutputStream(f));
        o.writeInt(0x5A504C43); o.writeInt(1); o.writeLong(1L);
        o.writeInt(1); o.writeInt(0); o.writeInt(0);
        o.writeInt(Channel.LIVE);
        o.writeUTF("old"); o.writeUTF("old name");
        for (int i = 0; i < 6; i++) o.writeUTF("");
        o.writeInt(0); o.writeUTF(""); o.writeBoolean(false);
        o.writeUTF(""); o.writeUTF(""); o.writeUTF(""); o.writeUTF("stale"); o.writeUTF("stale");
        o.close();
    }
    public static void main(String[] args) throws Exception {
        File dir = File.createTempFile("playlist-cache-test", "");
        check(dir.delete() && dir.mkdir(), "create test directory");
        try {
            writeSchema1(dir, "legacy");
            check(PlaylistCache.load(dir, "legacy") != null, "schema 1 should load");
            check(PlaylistCache.needsRefresh(dir, "legacy"), "schema 1 should refresh");
            check(PlaylistCache.saveChecked(dir, "legacy", one("new", "new name"), new ArrayList<Channel>(), new ArrayList<Channel>()), "save");
            check(!PlaylistCache.needsRefresh(dir, "legacy"), "schema 2 should be current");
            check("new".equals(PlaylistCache.load(dir, "legacy").live.get(0).key), "replacement visible");
            java.io.RandomAccessFile truncated = new java.io.RandomAccessFile(new File(dir, "plcache_legacy.bin"), "rw");
            truncated.setLength(28); truncated.close();
            check(PlaylistCache.load(dir, "legacy") == null, "truncated payload rejected");
            check(PlaylistCache.needsRefresh(dir, "legacy"), "corrupt payload must refresh");
            check(PlaylistCache.saveChecked(dir, "legacy", one("new", "new name"), new ArrayList<Channel>(), new ArrayList<Channel>()), "repair corrupted cache");
            Channel derived = PlaylistCache.load(dir, "legacy").live.get(0);
            check(derived.smartCat == null && derived.country == null, "derived labels must not persist");

            check(PlaylistCache.saveChecked(dir, "old", one("keep", "keep"), new ArrayList<Channel>(), new ArrayList<Channel>()), "initial save");
            Channel huge = channel("bad", new String(new char[70000]).replace('\0', 'x'));
            check(!PlaylistCache.saveChecked(dir, "old", one("bad", huge.name), new ArrayList<Channel>(), new ArrayList<Channel>()), "oversized write fails");
            check("keep".equals(PlaylistCache.load(dir, "old").live.get(0).key), "failed write kept old cache");

            check(PlaylistCache.saveChecked(dir, "catalog", one("old-live", "old list"), new ArrayList<Channel>(), new ArrayList<Channel>()), "legacy live-only snapshot");
            List<Channel> freshVod = one("movie-folder", "Movie folder");
            freshVod.get(0).kind = Channel.VOD;
            List<Channel> freshSeries = one("series-folder", "Series folder");
            freshSeries.get(0).kind = Channel.SERIES;
            check(PlaylistCache.saveChecked(dir, "catalog", one("fresh-live", "fresh list"), freshVod, freshSeries), "refresh complete catalog without clearing cache");
            PlaylistCache.Data repaired = PlaylistCache.load(dir, "catalog");
            check("fresh-live".equals(repaired.live.get(0).key) && repaired.vod.size() == 1 && repaired.series.size() == 1, "full catalog replaces stale snapshot");
            check(!PlaylistCache.needsRefresh(dir, "catalog"), "repaired snapshot current");

            CountDownLatch start = new CountDownLatch(1);
            Thread a = writer(dir, "a", "A", start), b = writer(dir, "b", "B", start);
            a.start(); b.start(); start.countDown(); a.join(); b.join();
            check("A".equals(PlaylistCache.load(dir, "a").live.get(0).key), "concurrent account A");
            check("B".equals(PlaylistCache.load(dir, "b").live.get(0).key), "concurrent account B");
            for (File f : dir.listFiles()) check(!f.getName().endsWith(".tmp"), "temporary file leaked");
        } finally {
            for (File f : dir.listFiles()) f.delete();
            dir.delete();
        }
        System.out.println("PlaylistCacheRefreshTest OK");
    }
    private static Thread writer(File d, String id, String key, CountDownLatch start) {
        return new Thread(() -> { try { start.await(); check(PlaylistCache.saveChecked(d, id, one(key, key), new ArrayList<Channel>(), new ArrayList<Channel>()), id); } catch (Exception e) { throw new RuntimeException(e); } });
    }
}
