package com.zynelabs.flowplay.data;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/** Minimal M3U / M3U8 playlist parser (EXTINF). */
public class M3uParser {

    private static String attr(String line, String name) {
        // indexOf-based: Pattern.compile per line was a major slowdown
        // (100k+ compiles on giant playlists) and churned memory.
        String key = name + "=\"";
        int i = line.indexOf(key);
        if (i < 0) return "";
        int s = i + key.length();
        int e = line.indexOf('"', s);
        return e < 0 ? "" : line.substring(s, e);
    }

    /** Explicit movie path or a conventional downloadable movie extension. */
    public static boolean isVodUrl(String url) {
        if (url == null) return false;
        int path = pathKind(url);
        if (path == Channel.SERIES) return false;
        if (path == Channel.VOD) return true;
        String u = url.toLowerCase(java.util.Locale.ROOT).split("[?#]", 2)[0];
        for (String ext : new String[]{".mp4", ".mkv", ".avi", ".mov", ".wmv",
                ".flv", ".webm", ".m4v", ".mpg", ".mpeg", ".3gp", ".tsv"}) {
            if (u.endsWith(ext)) return true;
        }
        return false;
    }

    /** Explicit Xtream-style URL path metadata; do not infer VOD from names/groups. */
    private static int pathKind(String url) {
        if (url == null) return Channel.LIVE;
        String u = url.toLowerCase();
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        int h = u.indexOf('#');
        if (h >= 0) u = u.substring(0, h);
        if (u.contains("/movie/")) return Channel.VOD;
        if (u.contains("/series/")) return Channel.SERIES;
        return Channel.LIVE;
    }

    /** Explicit provider group metadata only.  Generic "Movies" groups remain live. */
    private static int groupKind(String group) {
        if (group == null) return Channel.LIVE;
        String g = group.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
        String[] words = g.isEmpty() ? new String[0] : g.split(" ");
        for (String word : words) {
            if ("series".equals(word) || "srs".equals(word)) return Channel.SERIES;
        }
        for (String word : words) if ("vod".equals(word)) return Channel.VOD;
        return Channel.LIVE;
    }

    private static int kindFor(String url, String group) {
        int path = pathKind(url);
        if (path != Channel.LIVE) return path;
        int metadata = groupKind(group);
        if (metadata != Channel.LIVE) return metadata;
        return isVodUrl(url) ? Channel.VOD : Channel.LIVE;
    }

    public static List<Channel> parse(String text) {
        return parse(text, null);
    }

    /** Parses from a String (kept for small inputs); giant playlists should
     * use parse(BufferedReader, …) so the whole file is never one String. */
    public static List<Channel> parse(String text, CountListener cl) {
        BufferedReader br = new BufferedReader(new StringReader(text));
        try {
            List<Channel> out = parse(br, cl);
            br.close();
            return out;
        } catch (Exception e) {
            return new ArrayList<Channel>();
        }
    }

    /**
     * Streaming parse: reads line-by-line from the reader, so a 100k-entry
     * playlist never sits in memory as one giant String. Callers must close
     * the reader.
     */
    /** Progress callback for huge playlists (called every 2000 channels). */
    public interface CountListener {
        void onCount(int n);
    }

    public static List<Channel> parse(BufferedReader br, CountListener cl) {
        List<Channel> out = new ArrayList<>();
        try {
            String line;
            String pendingName = null, pendingLogo = "", pendingGroup = "";
            String lastExtGrp = "";
            int n = 0;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#EXTINF")) {
                    pendingLogo = attr(line, "tvg-logo");
                    pendingGroup = attr(line, "group-title");
                    if (pendingGroup.isEmpty()) pendingGroup = lastExtGrp;
                    int comma = line.lastIndexOf(',');
                    pendingName = comma >= 0 && comma + 1 < line.length()
                            ? line.substring(comma + 1).trim() : ("Channel " + (++n));
                } else if (line.startsWith("#EXTGRP:")) {
                    String extgrp = line.substring(8).trim();
                    lastExtGrp = extgrp;
                    if (pendingName != null && pendingGroup.isEmpty()) pendingGroup = extgrp;
                } else if (line.startsWith("#")) {
                    // skip other tags (EXTVLCOPT, ...)
                } else if (pendingName != null && (line.startsWith("http://") || line.startsWith("https://") || line.startsWith("rtsp://") || line.startsWith("rtmp://"))) {
                    Channel c = new Channel();
                    c.kind = kindFor(line, pendingGroup);
                    c.key = "m3u_" + out.size();
                    c.name = pendingName;
                    c.logo = pendingLogo;
                    c.group = pendingGroup;
                    c.url = line;
                    out.add(c);
                    pendingName = null;
                    if (cl != null && out.size() % 2000 == 0) cl.onCount(out.size());
                }
            }
            br.close();
        } catch (Exception ignored) {}
        return out;
    }
}
