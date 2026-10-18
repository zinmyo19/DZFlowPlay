package com.zynelabs.flowplay.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.util.List;

/** Cache-first loader: render saved data, then apply the provider result directly. */
public class PlaylistLoader {
    public interface Listener {
        void onCached(List<Channel> live, List<Channel> vod, List<Channel> series, long savedAt);
        void onProgress(String phase);
        void onFresh(List<Channel> live, List<Channel> vod, List<Channel> series);
        void onError(String err);
    }

    public static void start(final Context ctx, final PlAccount acc, final Listener l) {
        final Handler main = new Handler(Looper.getMainLooper());
        final File dir = ctx.getFilesDir();
        final String accId = acc.id;
        new Thread(new Runnable() {
            @Override public void run() {
                final PlaylistCache.Data cached = PlaylistCache.load(dir, accId);
                if (cached != null) main.post(new Runnable() {
                    @Override public void run() {
                        l.onCached(cached.live, cached.vod, cached.series, cached.savedAt);
                    }
                });
                ChannelRepo.load(ctx, acc, new ChannelRepo.Progress() {
                    @Override public void onProgress(final String phase) {
                        main.post(new Runnable() {
                            @Override public void run() { l.onProgress(phase); }
                        });
                    }
                }, new ChannelRepo.Callback() {
                    @Override public void onResult(final List<Channel> live,
                                                   final List<Channel> vod,
                                                   final List<Channel> series,
                                                   final String err) {
                        new Thread(new Runnable() {
                            @Override public void run() {
                                final boolean hasFresh = !live.isEmpty() || !vod.isEmpty() || !series.isEmpty();
                                if (err != null && !hasFresh) {
                                    main.post(new Runnable() {
                                        @Override public void run() { l.onError(err); }
                                    });
                                    return;
                                }
                                final List<Channel> freshLive = err != null && live.isEmpty() && cached != null ? cached.live : live;
                                final List<Channel> freshVod = err != null && vod.isEmpty() && cached != null ? cached.vod : vod;
                                final List<Channel> freshSeries = err != null && series.isEmpty() && cached != null ? cached.series : series;
                                final boolean saved = PlaylistCache.saveChecked(dir, accId, freshLive, freshVod, freshSeries);
                                main.post(new Runnable() {
                                    @Override public void run() {
                                        l.onFresh(freshLive, freshVod, freshSeries);
                                        if (err != null) l.onError(err);
                                        else if (!saved) l.onError("Latest data loaded, but offline cache could not be saved");
                                    }
                                });
                            }
                        }).start();
                    }
                });
            }
        }).start();
    }
}
