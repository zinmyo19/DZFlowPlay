package com.zynelabs.flowplay.data;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Credential-free JVM integration tests against a local portal fixture. */
public final class StalkerClientRegressionTest {
    static final AtomicInteger requests = new AtomicInteger();
    static boolean badCategories = false;
    static boolean wrappedCategories = false;
    static boolean emptyCategories = false;
    static boolean withCategoryArtwork = false;
    static final Map<String,Integer> links = new HashMap<>();
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    static Map<String,String> query(String raw) throws Exception {
        Map<String,String> out = new HashMap<>();
        if (raw != null) for (String part : raw.split("&")) {
            String[] kv = part.split("=",2);
            out.put(URLDecoder.decode(kv[0],"UTF-8"), kv.length == 2 ? URLDecoder.decode(kv[1],"UTF-8") : "");
        }
        return out;
    }
    static String response(Map<String,String> q) {
        requests.incrementAndGet();
        String a=q.get("action"), t=q.get("type");
        if ("handshake".equals(a)) return "{\"js\":{\"token\":\"local-fixture-token\"}}";
        if ("get_profile".equals(a)) return "{\"js\":{\"id\":1}}";
        if ("get_genres".equals(a)) return "{\"js\":[{\"id\":\"1\",\"title\":\"News\"},{\"id\":\"2\",\"title\":\"FOR ADULTS\"}]}";
        if ("get_all_channels".equals(a)) return "{\"js\":{\"data\":[{\"id\":\"11\",\"name\":\"Fixture News\",\"tv_genre_id\":\"1\",\"cmd\":\"ffmpeg http://localhost/ch/11\"}]}}";
        if ("get_categories".equals(a)) {
            if (badCategories) return "{\"js\":{\"error\":\"Access denied\"}}";
            if (emptyCategories) return "{\"js\":[]}";
            if (wrappedCategories) return "{\"js\":{\"categories\":[{\"category_id\":\"10\",\"category_name\":\"EN Library\"}]}}";
            if (withCategoryArtwork) return "{\"js\":[{\"id\":\"10\",\"title\":\"EN Library\",\"cover\":\"/covers/library.jpg\"}]}";
            return "{\"js\":[{\"id\":\"10\",\"title\":\"EN Library\"}]}";
        }
        if ("get_ordered_list".equals(a)) {
            if ("itv".equals(t)) {
                if (!"2".equals(q.get("genre"))) return "{\"js\":{\"data\":[]}}";
                return "{\"js\":{\"data\":[{\"id\":\"12\",\"name\":\"Fixture Restricted\",\"tv_genre_id\":\"2\",\"cmd\":\"ffmpeg http://localhost/ch/12\"}],\"total_items\":1}}";
            }
            String filter=q.get("category");
            if (filter == null) return "{\"js\":{\"data\":[]}}";
            if ("vod".equals(t)) {
                if ("2".equals(q.get("p"))) return "{\"js\":{\"data\":[{\"id\":\"22\",\"name\":\"Movie B\",\"category_id\":\"10\",\"poster\":\"//cdn.example.test/movie-b.jpg\",\"cmd\":\"ffmpeg http://localhost/movie/22\"}],\"total_items\":2}}";
                if (!"1".equals(q.get("p"))) return "{\"js\":{\"data\":[]}}";
                return "{\"js\":{\"data\":[{\"id\":\"21\",\"name\":\"Movie A\",\"category_id\":\"10\",\"cover\":\"images/movie-a.jpg\",\"cmd\":\"ffmpeg http://localhost/movie/21\"}],\"total_items\":2}}";
            }
            // Deliberately repeats its only page without total_items: must terminate.
            return "{\"js\":{\"data\":[{\"id\":\"31\",\"name\":\"Fixture Series\",\"category_id\":\"10\",\"img\":\"/covers/series31.jpg\",\"cmd\":\"series31\"}]}}";
        }
        if ("get_series".equals(a)) return "{\"js\":{\"episodes\":[{\"id\":\"41\",\"title\":\"Episode One\",\"season_num\":1,\"episode_num\":1,\"cmd\":\"ffmpeg http://localhost/episode/41\"}]}}";
        if ("create_link".equals(a)) {
            links.put(t,links.getOrDefault(t,0)+1);
            return "{\"js\":{\"cmd\":\"ffmpeg https://cdn.example.test/fixture.m3u8\"}}";
        }
        return "{\"js\":{\"data\":[]}}";
    }
    public static void main(String[] args) throws Exception {
        check("http://host".equals(StalkerClient.normPortal("http://host/c/index.html")), "portal normalization");
        check("https://cdn.example/movie.m3u8".equals(StalkerClient.urlFromCmd("ffmpeg://https://cdn.example/movie.m3u8")), "command extraction");
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/portal.php", ex -> {
            try {
                byte[] bytes=response(query(ex.getRequestURI().getRawQuery())).getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type","application/json");
                ex.sendResponseHeaders(200,bytes.length);
                ex.getResponseBody().write(bytes);
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { ex.close(); }
        });
        server.start();
        String portal="http://127.0.0.1:"+server.getAddress().getPort()+"/c/";
        String mac="00:1A:79:00:00:01";
        try {
            List<Channel> live=StalkerClient.channels(portal,mac);
            check(live.size()==2,"bulk plus missing explicit adult genre");
            int folderBefore=requests.get();
            List<Channel> folders=StalkerClient.catalogFolders(portal,mac,Channel.VOD);
            check(requests.get()==folderBefore+1,"category discovery must not fetch catalog pages");
            check(folders.size()==1 && "FLOWPLAY_CATEGORY".equals(folders.get(0).container),"folder marker");
            check(folders.get(0).logo.isEmpty(),"category folders must not use category ID as artwork");
            withCategoryArtwork = true;
            List<Channel> artFolders = StalkerClient.catalogFolders(portal, mac, Channel.VOD);
            check(("http://127.0.0.1:"+server.getAddress().getPort()+"/covers/library.jpg").equals(artFolders.get(0).logo), "genuine category artwork must be retained");
            withCategoryArtwork = false;

            int pageBefore=requests.get();
            StalkerClient.CatalogPage first=StalkerClient.catalogPage(folders.get(0),1,"");
            check(requests.get()==pageBefore+1,"catalogPage must issue exactly one request");
            check(first.items.size()==1 && first.total==2,"first lazy page and provider total");
            check(("http://127.0.0.1:"+server.getAddress().getPort()+"/images/movie-a.jpg").equals(first.items.get(0).logo),"relative catalog cover normalization");
            check(first.hasMore,"first page has additional titles");
            List<Channel> vod=StalkerClient.vod(portal,mac);
            check(vod.size()==2,"category-filtered VOD pagination expected 2, got "+vod.size());
            check("http://cdn.example.test/movie-b.jpg".equals(vod.get(1).logo), "protocol-relative poster normalization");
            check("EN Library".equals(vod.get(0).group),"provider category retained");
            List<Channel> series=StalkerClient.series(portal,mac);
            check(series.size()==1,"repeated page deduplication");
            check(("http://127.0.0.1:"+server.getAddress().getPort()+"/covers/series31.jpg").equals(series.get(0).logo),"absolute-path series artwork normalization");
            int before=links.values().stream().mapToInt(Integer::intValue).sum();
            List<Channel> episodes=StalkerClient.episodeChannels(series.get(0));
            check(episodes.size()==1,"episode parsing");
            check(before==links.values().stream().mapToInt(Integer::intValue).sum(),"episode list must not eagerly create playback links");
            check(StalkerClient.playUrl(live.get(0)).startsWith("https://cdn.example.test/"),"live link resolution");
            check(StalkerClient.playUrl(vod.get(0)).startsWith("https://cdn.example.test/"),"VOD link resolution");
            check(StalkerClient.playUrl(episodes.get(0)).startsWith("https://cdn.example.test/"),"episode link resolution");
            check(links.getOrDefault("itv",0)==1,"itv create_link type");
            check(links.getOrDefault("vod",0)>=1,"vod create_link type");
            wrappedCategories=true;
            check(StalkerClient.catalogFolders(portal,mac,Channel.VOD).size()==1,"wrapped categories parsed");
            wrappedCategories=false;
            emptyCategories=true;
            check("All".equals(StalkerClient.catalogFolders(portal,mac,Channel.VOD).get(0).name),"valid empty categories permit wildcard");
            emptyCategories=false;
            badCategories=true;
            boolean failed=false;
            try { StalkerClient.catalogFolders(portal,mac,Channel.VOD); }
            catch (Exception expected) { failed=true; }
            check(failed,"access error must not become a fake successful All folder");
            badCategories=false;
            check(StalkerClient.expiryFromInfo(new org.json.JSONObject().put("exp_date","1798761600"))==1798761600L,"epoch expiry");
            check(StalkerClient.expiryFromInfo(new org.json.JSONObject().put("exp_date","1798761600000"))==1798761600L,"millisecond expiry");
            long date=StalkerClient.expiryFromInfo(new org.json.JSONObject().put("expire_billing_date","Sep 28, 2027"));
            check(date>0 && "2027-09-28".equals(new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date(date*1000))),"text expiry date");
            check(StalkerClient.expiryFromInfo(new org.json.JSONObject().put("expire_billing_date","not a date"))==0,"unknown expiry not fabricated");
            check(requests.get()<45,"bounded request count: "+requests.get());
            System.out.println("StalkerClientRegressionTest OK: live/categories, VOD pagination, repeated pages, lazy episodes, playback link types; requests="+requests.get());
        } finally { StalkerClient.invalidate(portal,mac); server.stop(0); }
    }
}
