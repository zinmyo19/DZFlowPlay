package com.zynelabs.flowplay.data;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Plain-JVM, credential-free artwork refresh regression tests. */
public final class PlaylistArtworkTest {
    private static int checks;
    private static Channel row(String logo) {
        Channel c = new Channel();
        c.kind = Channel.VOD; c.key = "vod_42"; c.name = "Fixture Movie";
        c.url = "https://example.test/movie/42"; c.logo = logo;
        return c;
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void preserve(Channel fresh, Channel old) {
        PlaylistArtwork.preserveMissing(Arrays.asList(fresh), Arrays.asList(old));
    }
    public static void main(String[] args) throws Exception {
        String poster = "https://example.test/provider-poster.jpg";
        for (String missing : new String[]{"", null, "  ", "null", "NULL"}) {
            Channel fresh = row(missing), old = row(poster);
            preserve(fresh, old);
            check(poster.equals(fresh.logo), "Missing provider artwork should survive reload");
            check(poster.equals(old.logo), "Previous row must remain unchanged");
        }
        Channel fresh = row("https://example.test/new.jpg");
        preserve(fresh, row(poster));
        check(fresh.logo.endsWith("/new.jpg"), "New provider artwork must win");
        for (int field = 0; field < 10; field++) {
            Channel next = row("");
            switch (field) {
                case 0: next.key = "vod_43"; break;
                case 1: next.kind = Channel.SERIES; break;
                case 2: next.name = "Different item"; break;
                case 3: next.url = "https://other.test/movie/42"; break;
                case 4: next.stalkerPortal = "https://other.test/"; break;
                case 5: next.stalkerMac = "fixture-other-device"; break;
                case 6: next.server = "https://other.test/"; break;
                case 7: next.user = "fixture-other-user"; break;
                case 8: next.container = "FLOWPLAY_CATEGORY"; break;
                case 9: next.key = ""; break;
            }
            preserve(next, row(poster));
            check(next.logo.isEmpty(), "Different identity must not inherit artwork: " + field);
        }
        for (String invalid : new String[]{"", "null", "file:///private/image", "stalker://fixture/image", "local-fallback"}) {
            Channel next = row(""); preserve(next, row(invalid));
            check(next.logo.isEmpty(), "Only real HTTP(S) provider URLs may be inherited");
        }
        List<Channel> empty = new ArrayList<>();
        PlaylistArtwork.preserveMissing(empty, Arrays.asList(row(poster)));
        check(empty.isEmpty(), "Removed items must stay removed");
        Channel folderOld = row(poster), folderNew = row("");
        folderOld.container = folderNew.container = "FLOWPLAY_CATEGORY";
        folderOld.stalkerPortal = folderNew.stalkerPortal = "https://portal.example.test/";
        folderOld.stalkerMac = folderNew.stalkerMac = "fixture-device";
        preserve(folderNew, folderOld);
        check(poster.equals(folderNew.logo), "Category provider artwork must survive");
        File dir = Files.createTempDirectory("flowplay-artwork-test").toFile();
        List<Channel> none = Collections.emptyList();
        check(PlaylistCache.saveChecked(dir, "account-a", none, Arrays.asList(row(poster)), none), "Seed cache");
        Channel next = row("");
        PlaylistCache.Data previous = PlaylistCache.load(dir, "account-a");
        PlaylistArtwork.preserveMissing(Arrays.asList(next), previous.vod);
        check(PlaylistCache.saveChecked(dir, "account-a", none, Arrays.asList(next), none), "Save refresh");
        check(poster.equals(PlaylistCache.load(dir, "account-a").vod.get(0).logo), "Preserved URL must persist across restart");
        check(PlaylistCache.load(dir, "account-b") == null, "Separate account cannot access prior metadata");
        PlaylistArtwork.preserveMissing(null, null);
        PlaylistArtwork.preserveMissing(Arrays.asList((Channel) null), Arrays.asList((Channel) null));
        System.out.println("PlaylistArtworkTest OK: " + checks + " checks");
    }
}
