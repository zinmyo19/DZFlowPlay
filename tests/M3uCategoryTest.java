import com.zynelabs.flowplay.data.Cats;
import com.zynelabs.flowplay.data.Channel;
import com.zynelabs.flowplay.data.M3uParser;

import java.util.List;

/** Standalone regression test: javac then java -ea (no Android/JUnit dependency). */
public final class M3uCategoryTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static Channel one(String m3u) {
        List<Channel> channels = M3uParser.parse(m3u);
        check(channels.size() == 1, "expected one channel, got " + channels.size());
        return channels.get(0);
    }

    public static void main(String[] args) {
        Channel moviePath = one("#EXTM3U\n#EXTINF:-1,Film\nhttp://host/movie/u/p/42.ts\n");
        check(moviePath.kind == Channel.VOD, "/movie/ must be VOD");

        Channel seriesPath = one("#EXTM3U\n#EXTINF:-1,Episode\nhttp://host/series/u/p/77.ts\n");
        check(seriesPath.kind == Channel.SERIES, "/series/ must be SERIES");
        check(seriesPath.url.endsWith("77.ts"), "series episode URL must be retained");

        Channel vodGroup = one("#EXTM3U\n#EXTINF:-1 group-title=\"VOD\",Film\nhttp://host/live/index.m3u8\n");
        check(vodGroup.kind == Channel.VOD, "explicit VOD group must be VOD");

        Channel srsGroup = one("#EXTM3U\n#EXTINF:-1 group-title=\"SRS Episodes\",Episode\nhttp://host/live/index.m3u8\n");
        check(srsGroup.kind == Channel.SERIES, "explicit SRS group must be SERIES");

        Channel plainMovie = one("#EXTM3U\n#EXTINF:-1,Film\nhttp://host/file.mp4?token=demo\n");
        check(plainMovie.kind == Channel.VOD, "generic movie extensions remain supported");
        Channel plainLive = one("#EXTM3U\n#EXTINF:-1,TV\nhttp://host/live/u/p/44.ts\n");
        check(plainLive.kind == Channel.LIVE, "live TS remains live");

        Channel movieChannel = one("#EXTM3U\n#EXTINF:-1 group-title=\"SKY CINEMA\",Sky Cinema\nhttp://host/live/index.m3u8\n");
        check(movieChannel.kind == Channel.LIVE, "movie channel group must remain LIVE");

        Channel extgrp = one("#EXTM3U\n#EXTINF:-1,Episode\n#EXTGRP:Series\nhttp://host/live/index.m3u8\n");
        check(extgrp.kind == Channel.SERIES && "Series".equals(extgrp.group), "EXTGRP fallback");

        Channel adultSwim = new Channel();
        adultSwim.group = "Entertainment";
        adultSwim.name = "Adult Swim";
        check(!Cats.ADULT.equals(Cats.of(adultSwim)), "Adult Swim must not be adult");

        for (String name : new String[]{"Stingray Private", "Wicked Music", "Sexy Dance Radio",
                "USA - STINGRAY POP ADULT", "USA - STINGRAY POP ADULT 1",
                "USA - MC MUSIC ADULT ALTERNATIVE", "CA - ADULT SWIM HD", "Adult Hits"}) {
            Channel music = new Channel();
            music.group = "Music";
            music.name = name;
            check(!Cats.ADULT.equals(Cats.of(music)), name + " must not be adult");
        }

        Channel explicit = new Channel();
        explicit.group = "FOR ADULTS";
        explicit.name = "Channel";
        check(Cats.ADULT.equals(Cats.of(explicit)), "FOR ADULTS must be adult");
        explicit.group = "18+";
        check(Cats.ADULT.equals(Cats.of(explicit)), "18+ must be adult");
        explicit.group = "XXX";
        check(Cats.ADULT.equals(Cats.of(explicit)), "XXX must be adult");
        explicit.group = "Adult";
        check(Cats.ADULT.equals(Cats.of(explicit)), "plain Adult group stays adult");
        explicit.name = "Adult Swim";
        check(Cats.ADULT.equals(Cats.of(explicit)), "Adult Swim must not mask explicit group");

        System.out.println("M3uCategoryTest OK");
    }
}
