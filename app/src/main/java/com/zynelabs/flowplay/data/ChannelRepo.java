package com.zynelabs.flowplay.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/** Loads all channels for an account off the UI thread. Shared by the home screens. */
public class ChannelRepo {

    public interface Callback {
        void onResult(List<Channel> live, List<Channel> vod, List<Channel> series, String err);
    }

    /** Fresh-load progress phases, posted on the main thread. May be null. */
    public interface Progress {
        void onProgress(String phase);
    }

    public static void load(final Context ctx, final PlAccount acc, final Callback cb) {
        load(ctx, acc, null, cb);
    }

    public static void load(final Context ctx, final PlAccount acc,
                            final Progress prog, final Callback cb) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override public void run() {
                List<Channel> live = new ArrayList<>();
                List<Channel> vod = new ArrayList<>();
                List<Channel> series = new ArrayList<>();
                String err = null;
                // Catch Throwable, not just Exception: an OutOfMemoryError
                // while parsing a giant playlist used to kill this thread
                // silently, leaving the UI stuck on "Loading…" forever.
                try {
                    if ("m3u_url".equals(acc.type)) {
                        // Streaming path: download to a temp file, then parse
                        // line-by-line. The playlist is NEVER held in memory
                        // as one giant String (that was the OOM).
                        phase("Downloading playlist…");
                        final java.io.File tmp = java.io.File.createTempFile(
                                "pl", ".m3u", ctx.getCacheDir());
                        try {
                            XtreamClient.httpDownload(acc.url, tmp);
                            phase("Parsing channels…");
                            java.io.BufferedReader br = new java.io.BufferedReader(
                                    new java.io.InputStreamReader(
                                            new java.io.FileInputStream(tmp), "UTF-8"));
                            try {
                                splitM3u(M3uParser.parse(br, countListener()),
                                        live, vod, series);
                            } finally {
                                try { br.close(); } catch (Exception ignored) {}
                            }
                        } finally {
                            tmp.delete();
                        }
                    } else if ("m3u_file".equals(acc.type)) {
                        // Stream straight from the stored file — no
                        // read-it-all-into-a-String step.
                        phase("Reading playlist file…");
                        java.io.BufferedReader br = new java.io.BufferedReader(
                                new java.io.InputStreamReader(
                                        ctx.openFileInput(acc.file), "UTF-8"));
                        try {
                            phase("Parsing channels…");
                            splitM3u(M3uParser.parse(br, countListener()),
                                    live, vod, series);
                        } finally {
                            try { br.close(); } catch (Exception ignored) {}
                        }
                    } else if ("stalker".equals(acc.type)) {
                        StringBuilder portalErrors = new StringBuilder();
                        // A manual reload must not reuse a token/session that may
                        // have expired or belonged to a previous portal response.
                        StalkerClient.invalidate(acc.url, acc.mac);
                        phase("Loading portal channels…");
                        try { live = StalkerClient.channels(acc.url, acc.mac); }
                        catch (Exception e) { appendError(portalErrors, "Live: " + message(e)); }
                        phase("Loading portal movies…");
                        try { vod = StalkerClient.catalogFolders(acc.url, acc.mac, Channel.VOD); }
                        catch (Exception e) { appendError(portalErrors, "Movies: " + message(e)); }
                        phase("Loading portal series…");
                        try { series = StalkerClient.catalogFolders(acc.url, acc.mac, Channel.SERIES); }
                        catch (Exception e) { appendError(portalErrors, "Series: " + message(e)); }
                        try {
                            long exp = StalkerClient.subscriptionExpiry(acc.url, acc.mac);
                            if (exp > 0) {
                                acc.expDate = exp;
                                new Store(ctx).updateAccount(acc);
                            }
                        } catch (Exception ignored) {}
                        if (portalErrors.length() > 0) err = portalErrors.toString();
                        if (live.isEmpty() && vod.isEmpty() && series.isEmpty()) {
                            if (err == null) err =
                                    "Portal returned no accessible channels, movies or series.";
                        }
                    } else {
                        phase("Loading live channels…");
                        live = XtreamClient.live(acc.server, acc.user, acc.pass);
                        phase("Loading movies…");
                        vod = XtreamClient.vod(acc.server, acc.user, acc.pass);
                        phase("Loading series…");
                        series = XtreamClient.series(acc.server, acc.user, acc.pass);
                        // Refresh the subscription expiry on every background
                        // load so it stays current (not just at login time).
                        // user_info is one cheap call; login() returns null on
                        // auth failure, in which case we keep the old value.
                        try {
                            JSONObject ui = XtreamClient.login(
                                    acc.server, acc.user, acc.pass);
                            long exp = XtreamClient.parseExpDate(ui);
                            if (exp > 0) acc.expDate = exp;
                        } catch (Exception ignored) {}
                    }
                } catch (Throwable t) {
                    String m = t.getMessage();
                    err = (m == null || m.isEmpty()) ? String.valueOf(t) : m;
                }
                // Tag smart categories here, off the UI thread: 97k channels of
                // keyword matching on the main thread causes an ANR.
                if (err == null || !live.isEmpty() || !vod.isEmpty() || !series.isEmpty()) {
                    phase("Sorting categories…");
                    try {
                        // A provider may omit artwork in an otherwise successful reload.
                        // Keep its last known URL for the exact same item/account; do not
                        // clear image caches, restore deleted rows, or replace fresh art.
                        PlaylistCache.Data previous = PlaylistCache.load(ctx.getFilesDir(), acc.id);
                        if (previous != null) {
                            PlaylistArtwork.preserveMissing(live, previous.live);
                            PlaylistArtwork.preserveMissing(vod, previous.vod);
                            PlaylistArtwork.preserveMissing(series, previous.series);
                        }
                        Cats.tagAll(live);
                        Cats.tagAll(vod);
                        Cats.tagAll(series);
                    } catch (Throwable t) {
                        String m = t.getMessage();
                        err = (m == null || m.isEmpty()) ? String.valueOf(t) : m;
                    }
                }
                final List<Channel> fl = live, fv = vod, fs = series;
                final String ferr = err;
                main.post(new Runnable() {
                    @Override public void run() { cb.onResult(fl, fv, fs, ferr); }
                });
            }

            private void phase(final String p) {
                if (prog == null) return;
                main.post(new Runnable() {
                    @Override public void run() { prog.onProgress(p); }
                });
            }

            private String message(Throwable t) {
                String m = t.getMessage();
                return m == null || m.isEmpty() ? t.getClass().getSimpleName() : m;
            }

            private void appendError(StringBuilder all, String text) {
                if (all.length() > 0) all.append("; ");
                all.append(text);
            }

            private M3uParser.CountListener countListener() {
                return new M3uParser.CountListener() {
                    @Override public void onCount(final int n) {
                        phase("Parsing " + n + " channels…");
                    }
                };
            }
        }).start();
    }

    /** M3U playlists mix live channels and movie files: split by detected kind. */
    private static void splitM3u(List<Channel> all, List<Channel> live,
                                 List<Channel> vod, List<Channel> series) {
        for (Channel c : all) {
            if (c.kind == Channel.VOD) vod.add(c);
            else if (c.kind == Channel.SERIES) series.add(c);
            else live.add(c);
        }
    }
}
