package com.zynelabs.flowplay.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Dependency-free asynchronous artwork loader with bounded memory and persistent disk caches. */
public class ImageLoader {
    private static final int MAX_NETWORK_BYTES = ArtworkCache.MAX_ENTRY_BYTES;
    private static final int TARGET_PX = 384;
    private static final LruCache<String, Bitmap> cache;
    private static final ExecutorService pool = Executors.newFixedThreadPool(4);
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final ConcurrentHashMap<String, ArrayList<Callback>> pending = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> failures = new ConcurrentHashMap<>();
    private static final long RETRY_DELAY_MS = 20000L;
    private static final ArrayList<ArtworkCache> knownCaches = new ArrayList<>();
    private static final java.util.concurrent.atomic.AtomicInteger generation = new java.util.concurrent.atomic.AtomicInteger();

    static {
        int memKb = (int) (Runtime.getRuntime().maxMemory() / 1024);
        cache = new LruCache<String, Bitmap>(Math.max(1024, memKb / 8)) {
            @Override protected int sizeOf(String k, Bitmap b) { return Math.max(1, b.getByteCount() / 1024); }
        };
    }

    private static final class Callback {
        final WeakReference<ImageView> view;
        final android.graphics.drawable.Drawable placeholder;
        Callback(ImageView view, android.graphics.drawable.Drawable placeholder) {
            this.view = new WeakReference<>(view); this.placeholder = placeholder;
        }
    }

    /** Clears both in-memory artwork and all disk caches used by this process. */
    public static void clear() {
        generation.incrementAndGet();
        failures.clear();
        cache.evictAll();
        synchronized (knownCaches) {
            for (ArtworkCache disk : knownCaches) disk.clear();
        }
    }

    public static void load(final String url, final ImageView iv, final int placeholder) {
        load(url, iv, placeholder == 0 ? null : iv.getResources().getDrawable(placeholder, null));
    }

    /** Same, with a Drawable placeholder (e.g. per-category fallback art). */
    public static void load(final String url, final ImageView iv,
                            final android.graphics.drawable.Drawable placeholder) {
        if (iv == null) return;
        if (url == null || url.length() == 0) {
            iv.setImageDrawable(placeholder); iv.setTag(null); return;
        }
        ensureDisk(iv.getContext());
        iv.setTag(url);
        Bitmap hit = cache.get(url);
        if (hit != null) { iv.setImageBitmap(hit); return; }
        iv.setImageDrawable(placeholder);
        Long failed = failures.get(url);
        if (failed != null && System.currentTimeMillis() - failed < RETRY_DELAY_MS) return;
        final Callback callback = new Callback(iv, placeholder);
        boolean launch = false;
        synchronized (pending) {
            ArrayList<Callback> waiters = pending.get(url);
            if (waiters == null) {
                waiters = new ArrayList<>(); pending.put(url, waiters); launch = true;
            }
            boolean duplicate = false;
            for (Callback existing : waiters) if (existing.view.get() == iv) { duplicate = true; break; }
            if (!duplicate) waiters.add(callback);
        }
        if (launch) start(url, generation.get());
    }

    private static void start(final String url, final int requestGeneration) {
        pool.execute(new Runnable() {
            @Override public void run() {
                final Result result = fetch(url, requestGeneration);
                if (result.bitmap == null) {
                    if (failures.size() > 512) failures.clear();
                    failures.put(url, System.currentTimeMillis());
                } else failures.remove(url);
                if (result.bitmap != null && generation.get() == requestGeneration) cache.put(url, result.bitmap);
                ArrayList<Callback> callbacks;
                synchronized (pending) { callbacks = pending.remove(url); }
                if (callbacks == null) return;
                for (final Callback callback : callbacks) {
                    final ImageView view = callback.view.get();
                    if (view == null) continue;
                    main.post(new Runnable() {
                        @Override public void run() {
                            if (url.equals(view.getTag())) {
                                if (result.bitmap != null) view.setImageBitmap(result.bitmap);
                                else view.setImageDrawable(callback.placeholder);
                            }
                        }
                    });
                }
            }
        });
    }

    private static final class Result {
        final Bitmap bitmap;
        Result(Bitmap bitmap) { this.bitmap = bitmap; }
    }

    private static Result fetch(String urlString, int requestGeneration) {
        if (!isOrdinaryUrl(urlString)) return new Result(null);
        ArtworkCache disk = null;
        try {
            // Context is obtained only from a live ImageView in load(); disk is initialized there lazily.
            // The worker receives the per-URL cache directory through the cache registry below.
            disk = cacheFor(urlString);
            byte[] bytes = disk == null ? null : disk.get(urlString);
            Bitmap bitmap = decode(bytes);
            if (bitmap != null) return new Result(bitmap);
            if (bytes != null && disk != null) disk.remove(urlString);
            bytes = download(urlString);
            if (bytes == null) return new Result(null);
            bitmap = decode(bytes);
            if (bitmap != null && disk != null && generation.get() == requestGeneration) disk.put(urlString, bytes);
            return new Result(bitmap);
        } catch (Exception ignored) { return new Result(null); }
    }

    private static ArtworkCache cacheFor(String ignored) { return currentCache; }
    private static volatile ArtworkCache currentCache;

    private static byte[] download(String urlString) {
        HttpURLConnection con = null;
        try {
            URL target = new URL(urlString.replace(" ", "%20"));
            for (int redirect = 0; redirect <= 5; redirect++) {
                if (!isOrdinaryUrl(target.toExternalForm())) return null;
                con = (HttpURLConnection) target.openConnection();
                con.setInstanceFollowRedirects(false); con.setConnectTimeout(10000); con.setReadTimeout(15000);
                con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) FlowPlay/1.1");
                con.setRequestProperty("Accept", "image/webp,image/png,image/jpeg,image/gif;q=0.8");
                int status = con.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = con.getHeaderField("Location");
                    if (location == null || redirect == 5) return null;
                    target = new URL(target, location.replace(" ", "%20"));
                    con.disconnect(); con = null; continue;
                }
                if (status < 200 || status >= 300 || con.getContentLengthLong() > MAX_NETWORK_BYTES) return null;
                String type = con.getContentType();
                if (type != null && type.toLowerCase(java.util.Locale.US).contains("text/html")) return null;
                try (InputStream in = con.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[8192]; int n, total = 0;
                    while ((n = in.read(buf)) != -1) {
                        total += n; if (total > MAX_NETWORK_BYTES) return null;
                        out.write(buf, 0, n);
                    }
                    return out.toByteArray();
                }
            }
            return null;
        } catch (Exception ignored) { return null; }
        finally { if (con != null) con.disconnect(); }
    }

    private static Bitmap decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= TARGET_PX || bounds.outHeight / (sample * 2) >= TARGET_PX) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private static boolean isOrdinaryUrl(String value) {
        try { String scheme = new URL(value).getProtocol(); return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme); }
        catch (Exception e) { return false; }
    }

    /** Called by the UI-facing code indirectly through the first load's ImageView context. */
    private static synchronized void ensureDisk(Context context) {
        if (context == null) return;
        java.io.File directory = new java.io.File(context.getCacheDir(), "artwork");
        if (currentCache != null && currentCache.fileFor("").getParentFile().equals(directory)) return;
        ArtworkCache disk = null;
        synchronized (knownCaches) {
            for (ArtworkCache existing : knownCaches) {
                if (existing.fileFor("").getParentFile().equals(directory)) { disk = existing; break; }
            }
            if (disk == null) { disk = new ArtworkCache(directory); knownCaches.add(disk); }
        }
        currentCache = disk;
    }
}
