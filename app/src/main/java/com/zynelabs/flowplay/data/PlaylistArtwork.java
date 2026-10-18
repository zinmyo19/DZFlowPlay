package com.zynelabs.flowplay.data;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Retains provider artwork when a refresh omits it, without retaining removed items. */
public final class PlaylistArtwork {
    private PlaylistArtwork() {}

    /** Only changes missing logos on fresh rows. Fresh provider artwork always wins. */
    public static void preserveMissing(List<Channel> fresh, List<Channel> previous) {
        if (fresh == null || previous == null || previous.isEmpty()) return;
        Map<String, Channel> byKey = new HashMap<>();
        for (Channel old : previous) {
            if (old != null && old.key != null && !old.key.isEmpty() && ordinaryUrl(old.logo)) {
                byKey.put(old.kind + "|" + old.key, old);
            }
        }
        for (Channel item : fresh) {
            if (item == null || !missing(item.logo) || item.key == null || item.key.isEmpty()) continue;
            Channel old = byKey.get(item.kind + "|" + item.key);
            // IDs may be reused or an existing account may be edited to another portal.
            // M3U keys are positional, so require the same playback URL as well as title.
            if (old != null && Objects.equals(item.name, old.name)
                    && Objects.equals(item.url, old.url)
                    && Objects.equals(item.stalkerPortal, old.stalkerPortal)
                    && Objects.equals(item.stalkerMac, old.stalkerMac)
                    && Objects.equals(item.server, old.server)
                    && Objects.equals(item.user, old.user)
                    && Objects.equals(item.container, old.container)) {
                item.logo = old.logo;
            }
        }
    }

    private static boolean missing(String value) {
        return value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim());
    }

    private static boolean ordinaryUrl(String value) {
        if (missing(value)) return false;
        try {
            URL url = new URL(value);
            return !url.getHost().isEmpty() && ("http".equalsIgnoreCase(url.getProtocol())
                    || "https".equalsIgnoreCase(url.getProtocol()));
        } catch (Exception ignored) { return false; }
    }
}
