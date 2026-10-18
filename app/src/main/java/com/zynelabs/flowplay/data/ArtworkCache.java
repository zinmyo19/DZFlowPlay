package com.zynelabs.flowplay.data;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Small, process-safe-enough disk cache for already validated artwork bytes. */
public final class ArtworkCache {
    public static final long DEFAULT_MAX_BYTES = 96L * 1024L * 1024L;
    public static final int MAX_ENTRY_BYTES = 8 * 1024 * 1024;
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private final File directory;
    private final long maxBytes;

    public ArtworkCache(File directory) { this(directory, DEFAULT_MAX_BYTES); }
    public ArtworkCache(File directory, long maxBytes) {
        this.directory = directory;
        this.maxBytes = Math.max(0L, maxBytes);
    }

    public File fileFor(String url) { return new File(directory, sha256(url) + ".img"); }

    /** Returns bytes and refreshes recency, or null for missing/corrupt/oversize entries. */
    public synchronized byte[] get(String url) {
        if (url == null) return null;
        File file = fileFor(url);
        if (!file.isFile() || file.length() <= 0 || file.length() > MAX_ENTRY_BYTES) {
            if (file.exists()) file.delete();
            return null;
        }
        try {
            byte[] value = readFully(file, MAX_ENTRY_BYTES);
            if (value.length == 0) { file.delete(); return null; }
            file.setLastModified(System.currentTimeMillis());
            return value;
        } catch (IOException e) {
            file.delete();
            return null;
        }
    }

    /** Atomically stores bytes. Callers must validate that bytes are an image first. */
    public synchronized boolean put(String url, byte[] bytes) {
        if (url == null || bytes == null || bytes.length == 0 || bytes.length > MAX_ENTRY_BYTES
                || maxBytes == 0) return false;
        if (!directory.exists() && !directory.mkdirs()) return false;
        File target = fileFor(url);
        File tmp = null;
        try {
            tmp = File.createTempFile("artwork-", ".tmp", directory);
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(bytes); out.flush(); out.getFD().sync();
            }
            if (!tmp.renameTo(target)) { tmp.delete(); return false; }
            target.setLastModified(System.currentTimeMillis());
            trim();
            return target.isFile();
        } catch (IOException e) {
            return false;
        } finally {
            if (tmp != null && tmp.exists()) tmp.delete();
        }
    }

    public synchronized void remove(String url) {
        if (url != null) fileFor(url).delete();
    }

    public synchronized void clear() {
        File[] files = directory.listFiles();
        if (files != null) for (File f : files) if (f.isFile() && (f.getName().matches("[0-9a-f]{64}\\.img") ||
                (f.getName().startsWith("artwork-") && f.getName().endsWith(".tmp")))) f.delete();
    }

    public synchronized void trim() {
        File[] files = directory.listFiles();
        if (files == null) return;
        long total = 0;
        for (File f : files) if (f.isFile() && f.getName().endsWith(".img")) total += f.length();
        while (total > maxBytes) {
            File oldest = null;
            for (File f : files) {
                if (f.isFile() && f.getName().endsWith(".img") && (oldest == null || f.lastModified() < oldest.lastModified())) oldest = f;
            }
            if (oldest == null) break;
            long removed = oldest.length();
            if (!oldest.delete()) break;
            total -= removed;
            files = directory.listFiles();
            if (files == null) break;
        }
    }

    private static byte[] readFully(File file, int limit) throws IOException {
        InputStream in = new FileInputStream(file);
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(file.length(), 65536));
        byte[] buffer = new byte[8192]; int n; int total = 0;
        try {
            while ((n = in.read(buffer)) != -1) {
                total += n;
                if (total > limit) throw new IOException("entry too large");
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } finally { in.close(); }
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(UTF8));
            StringBuilder result = new StringBuilder(64);
            for (byte b : digest) result.append(String.format("%02x", b & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
