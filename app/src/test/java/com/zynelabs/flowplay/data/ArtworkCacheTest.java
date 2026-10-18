package com.zynelabs.flowplay.data;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class ArtworkCacheTest {
    @Test public void hashesUrlsAndRoundTripsBytes() throws Exception {
        File dir = Files.createTempDirectory("art-cache").toFile();
        ArtworkCache cache = new ArtworkCache(dir, 1024);
        String url = "https://example.test/art work.png";
        byte[] bytes = new byte[] {1, 2, 3, 4};
        assertTrue(cache.put(url, bytes));
        assertArrayEquals(bytes, cache.get(url));
        assertTrue(cache.fileFor(url).getName().matches("[0-9a-f]{64}\\.img"));
        assertFalse(cache.fileFor(url).getName().contains("example"));
    }

    @Test public void rejectsCorruptAndOversizeEntries() throws Exception {
        File dir = Files.createTempDirectory("art-cache").toFile();
        ArtworkCache cache = new ArtworkCache(dir, 1024);
        String url = "https://example.test/corrupt";
        File file = cache.fileFor(url);
        dir.mkdirs();
        Files.write(file.toPath(), new byte[ArtworkCache.MAX_ENTRY_BYTES + 1]);
        assertNull(cache.get(url));
        assertFalse(file.exists());
        assertFalse(cache.put(url, new byte[ArtworkCache.MAX_ENTRY_BYTES + 1]));
    }

    @Test public void trimsOldestEntriesToConfiguredLimit() throws Exception {
        File dir = Files.createTempDirectory("art-cache").toFile();
        ArtworkCache cache = new ArtworkCache(dir, 6);
        assertTrue(cache.put("https://example.test/one", new byte[] {1, 1, 1, 1}));
        Thread.sleep(5);
        assertTrue(cache.put("https://example.test/two", new byte[] {2, 2, 2, 2}));
        assertNull(cache.get("https://example.test/one"));
        assertArrayEquals(new byte[] {2, 2, 2, 2}, cache.get("https://example.test/two"));
    }

    @Test public void clearRemovesOnlyCacheFiles() throws Exception {
        File dir = Files.createTempDirectory("art-cache").toFile();
        ArtworkCache cache = new ArtworkCache(dir, 1024);
        assertTrue(cache.put("https://example.test/one", new byte[] {1}));
        File unrelated = new File(dir, "keep.txt");
        Files.write(unrelated.toPath(), new byte[] {9});
        cache.clear();
        assertNull(cache.get("https://example.test/one"));
        assertTrue(unrelated.exists());
    }
}
