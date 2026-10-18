package com.zynelabs.flowplay.data;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Stalker / Ministra (MAC portal) client.
 *
 * Fixes compared with the original build:
 *  1. Portal URL is normalised (accepts .../c/, .../c/index.html, .../portal.php, .../stalker_portal/c ...).
 *  2. Handshake is followed by get_profile (many portals return empty lists without it).
 *  3. Redirects (http -> https, /c -> /portal.php) are followed and HTTP errors are reported.
 *  4. Channels: get_all_channels first, get_ordered_list paging as fallback, real error messages.
 *  5. Relative channel logos are turned into absolute URLs (they were broken before).
 *  6. create_link is retried with a fresh token when the cached token expired.
 *  7. playUrl(Channel) resolves the tokenised stream link at PLAY time. The original player
 *     read Channel.url directly, which is always empty for Stalker channels.
 */
public class StalkerClient {
    private static final String UA =
            "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) "
                    + "MAG250 stbapp ver: 4 rev: 1812 Safari/533.3";
    private static final String XUA = "Model: MAG250; Link: WiFi";

    private static final String[] API_PATHS = {
            "/portal.php",
            "/server/load.php",
            "/stalker_portal/server/load.php",
            "/c/server/load.php"
    };
    private static final String[] TAILS = {
            "/c/index.html", "/index.html", "/portal.php", "/server/load.php", "/c"
    };

    private static final Map<String, Session> cache = new HashMap<String, Session>();

    /** Last human readable error, so the UI can show something better than a generic failure. */
    public static volatile String lastError = "";

    public static class Session {
        public String apiBase;
        public String mac;
        public String token;
        public String logoBase = "";
        private final Map<String,String> categoryArtwork = new java.util.concurrent.ConcurrentHashMap<String,String>();
    }

    // ------------------------------------------------------------------ helpers

    public static String normPortal(String str) {
        String t = str == null ? "" : str.trim();
        String lowScheme = t.toLowerCase(Locale.ROOT);
        if (!lowScheme.startsWith("http://") && !lowScheme.startsWith("https://")) {
            t = "http://" + t;
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            while (t.endsWith("/")) {
                t = t.substring(0, t.length() - 1);
                changed = true;
            }
            String low = t.toLowerCase(Locale.ROOT);
            for (String tail : TAILS) {
                if (low.endsWith(tail)) {
                    t = t.substring(0, t.length() - tail.length());
                    changed = true;
                    break;
                }
            }
        }
        return t;
    }

    public static boolean validMac(String str) {
        return str != null && str.trim().toUpperCase(Locale.ROOT).matches("^([0-9A-F]{2}:){5}[0-9A-F]{2}$");
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private static String msg(Throwable e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }

    private static String enc(String str) {
        try {
            return URLEncoder.encode(str, "UTF-8");
        } catch (Exception e) {
            return str;
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format(Locale.ROOT, "%02x", x));
        }
        return sb.toString();
    }

    private static String digest(String algo, String in) {
        try {
            return hex(MessageDigest.getInstance(algo).digest(in.getBytes("UTF-8")));
        } catch (Exception e) {
            return "0000000000000000000000000000000000000000000000000000000000000000";
        }
    }

    // ------------------------------------------------------------------ session

    public static synchronized Session session(String portal, String mac) throws Exception {
        String p = normPortal(portal);
        String m = mac == null ? "" : mac.trim().toUpperCase(Locale.ROOT);
        if (!validMac(m)) {
            throw new Exception("Bad MAC format (00:1A:79:xx:xx:xx)");
        }
        String key = p + "|" + m;
        Session s = cache.get(key);
        if (s != null) {
            return s;
        }
        s = connect(p, m);
        cache.put(key, s);
        return s;
    }

    public static synchronized void invalidate(String portal, String mac) {
        if (portal == null || mac == null) {
            return;
        }
        cache.remove(normPortal(portal) + "|" + mac.trim().toUpperCase(Locale.ROOT));
    }

    private static Session connect(String portal, String mac) throws Exception {
        String lastErr = "no response";
        for (String path : API_PATHS) {
            String api = portal + path;
            try {
                Session s = new Session();
                s.apiBase = api;
                s.mac = mac;
                String body = http(api + "?type=stb&action=handshake&token=&prehash=&JsHttpRequest=1-xml", s);
                JSONObject js = js(body);
                String token = js == null ? "" : js.optString("token", "");
                if (isEmpty(token) || "null".equals(token)) {
                    lastErr = path + ": no token in handshake reply";
                    continue;
                }
                s.token = token;
                String prefix = path.startsWith("/stalker_portal") ? "/stalker_portal" : "";
                s.logoBase = portal + prefix + "/misc/logos/320/";
                profile(s);
                return s;
            } catch (Exception e) {
                lastErr = path + ": " + msg(e);
            }
        }
        lastError = "Portal handshake failed (" + lastErr + ")";
        throw new Exception(lastError);
    }

    /** Activates the session. Several portals refuse channel requests until this was called. */
    private static void profile(Session s) {
        try {
            String sn = digest("MD5", s.mac).substring(0, 13).toUpperCase(Locale.ROOT);
            String did = digest("SHA-256", s.mac).toUpperCase(Locale.ROOT);
            String ver = "ImageDescription: 0.2.18-r14-pub-250; ImageDate: Fri Jan 15 15:20:44 EET 2016; "
                    + "PORTAL version: 5.6.1; API Version: JS API version: 328; STB API version: 134; "
                    + "Player Engine version: 0x566";
            String metrics = "{\"mac\":\"" + s.mac + "\",\"model\":\"MAG250\",\"type\":\"STB\","
                    + "\"uid\":\"\",\"device\":\"\",\"random\":\"\"}";
            String url = s.apiBase + "?type=stb&action=get_profile&hd=1&ver=" + enc(ver)
                    + "&num_banks=2&sn=" + sn + "&stb_type=MAG250&client_type=STB&image_version=218"
                    + "&video_out=hdmi&device_id=" + did + "&device_id2=" + did
                    + "&signature=&auth_second_step=1&hw_version=1.7-BD-00&not_valid_token=0"
                    + "&metrics=" + enc(metrics) + "&api_signature=262&prehash=&JsHttpRequest=1-xml";
            http(url, s);
        } catch (Exception ignored) {
            // some portals do not implement get_profile; the session can still work
        }
    }

    // ------------------------------------------------------------------ http

    private static String http(String url, Session s) throws Exception {
        String cur = url;
        for (int hop = 0; hop < 4; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(cur).openConnection();
            try {
                c.setInstanceFollowRedirects(false);
                c.setUseCaches(false);
                c.setRequestProperty("Cache-Control", "no-cache");
                c.setRequestProperty("Pragma", "no-cache");
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.setRequestProperty("User-Agent", UA);
                c.setRequestProperty("X-User-Agent", XUA);
                c.setRequestProperty("Accept", "*/*");
                c.setRequestProperty("Cookie",
                        "mac=" + s.mac + "; stb_lang=en; timezone=" + TimeZone.getDefault().getID());
                if (!isEmpty(s.token)) {
                    c.setRequestProperty("Authorization", "Bearer " + s.token);
                }
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = c.getHeaderField("Location");
                    if (loc == null) {
                        throw new Exception("HTTP " + code);
                    }
                    cur = new URL(new URL(cur), loc).toString();
                    continue;
                }
                if (code >= 400) {
                    throw new Exception("HTTP " + code);
                }
                InputStream in = c.getInputStream();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    bos.write(buf, 0, n);
                }
                in.close();
                return new String(bos.toByteArray(), "UTF-8");
            } finally {
                c.disconnect();
            }
        }
        throw new Exception("Too many redirects");
    }

    private static JSONObject root(String body) throws Exception {
        if (body == null) {
            throw new Exception("Empty reply");
        }
        int i = body.indexOf('{');
        if (i < 0) {
            String snip = body.trim();
            if (snip.length() > 60) {
                snip = snip.substring(0, 60);
            }
            throw new Exception("Not a portal reply: " + snip);
        }
        return new JSONObject(body.substring(i));
    }

    private static JSONObject js(String body) throws Exception {
        return root(body).optJSONObject("js");
    }

    // ------------------------------------------------------------------ channels

    private static Map<String, String> genres(Session s) {
        Map<String, String> map = new HashMap<String, String>();
        try {
            Object o = root(http(s.apiBase + "?type=itv&action=get_genres&JsHttpRequest=1-xml", s)).opt("js");
            JSONArray arr = null;
            if (o instanceof JSONArray) {
                arr = (JSONArray) o;
            } else if (o instanceof JSONObject) {
                arr = ((JSONObject) o).optJSONArray("data");
            }
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject g = arr.optJSONObject(i);
                    if (g != null) {
                        map.put(g.optString("id"), g.optString("title", ""));
                    }
                }
            }
        } catch (Exception ignored) {
            // groups are optional
        }
        return map;
    }

    private static String absLogo(String logo, Session s) {
        if (isEmpty(logo) || "null".equals(logo)) {
            return "";
        }
        if (logo.startsWith("http://") || logo.startsWith("https://")) {
            return logo;
        }
        if (logo.startsWith("//")) {
            return "http:" + logo;
        }
        if (logo.startsWith("/")) {
            try {
                URL u = new URL(s.logoBase);
                return u.getProtocol() + "://" + u.getAuthority() + logo;
            } catch (Exception e) {
                return logo;
            }
        }
        return s.logoBase + logo;
    }

    /** Catalog artwork resolves against the portal origin, not the live-logo directory. */
    private static String absArtwork(String artwork, Session s) {
        if (isEmpty(artwork) || "null".equalsIgnoreCase(artwork.trim())) return "";
        String value = artwork.trim();
        try {
            URL base = new URL(s.apiBase);
            if (value.startsWith("//")) return base.getProtocol() + ":" + value;
            if (value.startsWith("http://") || value.startsWith("https://")) return value;
            URL origin = new URL(base.getProtocol(), base.getHost(), base.getPort(), "/");
            return new URL(origin, value).toString();
        } catch (Exception ignored) {
            return value;
        }
    }

    private static String catalogArtwork(JSONObject o, Session s) {
        for (String field : new String[]{"screenshot_uri", "cover", "poster", "movie_image", "img", "logo"}) {
            String value = o.optString(field, "");
            if (!isEmpty(value) && !"null".equalsIgnoreCase(value.trim())) return absArtwork(value, s);
        }
        return "";
    }

    private static void addChannels(List<Channel> out, Set<String> seen, JSONArray data,
                                    Map<String, String> genres, Session s, String portal) {
        if (data == null) {
            return;
        }
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String id = o.optString("id", "");
            if (isEmpty(id) || "null".equals(id)) {
                continue;
            }
            String key = "stalker_" + id;
            if (!seen.add(key)) {
                continue;
            }
            int idNum = 0;
            try {
                idNum = Integer.parseInt(id.trim());
            } catch (Exception ignored) {
                // keep 0, key stays unique
            }
            Channel ch = new Channel();
            ch.kind = 0;
            ch.key = key;
            ch.name = o.optString("name", "Channel " + id);
            ch.logo = absLogo(o.optString("logo", ""), s);
            String g = genres.get(o.optString("tv_genre_id", ""));
            ch.group = g == null ? "" : g;
            ch.stalkerPortal = portal;
            ch.stalkerMac = s.mac;
            ch.stalkerCmd = o.optString("cmd", "");
            // Stable, unique, NOT playable: the app uses Channel.url as the list/recents identity.
            // The real tokenised link is created by playUrl() at play time.
            ch.url = "stalker://" + s.mac.replace(":", "") + "/" + id;
            ch.streamId = idNum;
            out.add(ch);
        }
    }

    private static JSONArray allData(String body) throws Exception {
        Object o = root(body).opt("js");
        if (o instanceof JSONArray) {
            return (JSONArray) o;
        }
        if (o instanceof JSONObject) {
            return ((JSONObject) o).optJSONArray("data");
        }
        return null;
    }

    public static List<Channel> channels(String portal, String mac) throws Exception {
        Session s = session(portal, mac);
        String normalised = normPortal(portal);
        Map<String, String> genres = genres(s);
        List<Channel> out = new ArrayList<Channel>();
        Set<String> seen = new HashSet<String>();
        Set<String> genreSeen = new HashSet<String>();
        try {
            String body = http(s.apiBase + "?type=itv&action=get_all_channels&JsHttpRequest=1-xml", s);
            addChannels(out, seen, allData(body), genres, s, normalised);
            JSONArray a = allData(body);
            if (a != null) for (int i=0;i<a.length();i++) { JSONObject o=a.optJSONObject(i); if(o!=null) genreSeen.add(o.optString("tv_genre_id", "")); }
        } catch (Exception e) { lastError = msg(e); }
        // Bulk is preferred.  Only fall back when empty, or supplement categories
        // whose titles look restricted; the portal still decides entitlement/PIN.
        if (out.isEmpty()) pageLiveGenre(s, "*", out, seen, genres, normalised);
        for (Map.Entry<String,String> e : genres.entrySet()) {
            String title=e.getValue()==null?"":e.getValue().toLowerCase(Locale.ROOT);
            if (!genreSeen.contains(e.getKey()) && (out.isEmpty() || title.contains("adult") || title.contains("xxx") || title.contains("porn") || title.contains("18") || title.contains("erotic")))
                pageLiveGenre(s, e.getKey(), out, seen, genres, normalised);
        }
        if (out.isEmpty()) {
            invalidate(portal, mac);
            lastError = "Portal returned no channels (" + (isEmpty(lastError)?"empty list":lastError) + "). Check that the MAC is active.";
            throw new Exception(lastError);
        }
        return out;
    }

    private static void pageLiveGenre(Session s, String genre, List<Channel> out, Set<String> seen, Map<String,String> genres, String portal) {
        String previous=null;
        for (int p=1;p<=10000;p++) try {
            JSONObject j=js(http(s.apiBase+"?type=itv&action=get_ordered_list&genre="+enc(genre)+"&force_ch_link_check=&fav=0&sortby=number&hd=0&p="+p+"&JsHttpRequest=1-xml",s));
            JSONArray a=j==null?null:j.optJSONArray("data"); if(a==null||a.length()==0) break;
            String fp=a.toString(); if(fp.equals(previous)) break; previous=fp; addChannels(out,seen,a,genres,s,portal);
            int total=j.optInt("total_items",-1); if(total>0 && p*a.length()>=total) break;
        } catch(Exception e) { lastError=msg(e); break; }
    }

    private static JSONArray catalogCategoryData(Object raw) throws Exception {
        if (raw instanceof JSONArray) return (JSONArray) raw;
        if (raw instanceof JSONObject) {
            JSONObject object = (JSONObject) raw;
            for (String field : new String[]{"data", "categories"}) {
                JSONArray rows = object.optJSONArray(field);
                if (rows != null) return rows;
            }
        }
        throw new Exception("Portal category response is not a list");
    }

    private static Map<String,String> catalogCategories(Session s,String type) throws Exception {
        Map<String,String> out = new LinkedHashMap<String,String>();
        JSONArray rows = catalogCategoryData(root(http(s.apiBase + "?type=" + type
                + "&action=get_categories&JsHttpRequest=1-xml", s)).opt("js"));
        for (int i = 0; i < rows.length(); i++) {
            JSONObject o = rows.optJSONObject(i);
            if (o == null) continue;
            String id = o.optString("id", o.optString("category_id", ""));
            if (!isEmpty(id) && !"null".equals(id)) {
                out.put(id, o.optString("title", o.optString("category_name", "")));
                String art = catalogArtwork(o, s);
                if (isEmpty(art)) s.categoryArtwork.remove(type + "|" + id);
                else s.categoryArtwork.put(type + "|" + id, art);
            }
        }
        return out;
    }

    /** Account metadata only; never changes portal entitlements or parental controls. */
    public static long subscriptionExpiry(String portal, String mac) {
        try {
            Session s = session(portal, mac);
            JSONObject info = js(http(s.apiBase
                    + "?type=account_info&action=get_main_info&JsHttpRequest=1-xml", s));
            return expiryFromInfo(info);
        } catch (Exception ignored) { return 0; }
    }

    static long expiryFromInfo(JSONObject info) {
        if (info == null) return 0;
        for (String field : new String[]{"expire_billing_date", "end_date", "exp_date", "expire_date", "expiration_date"}) {
            String value = info.optString(field, "").trim();
            if (value.isEmpty() || "null".equalsIgnoreCase(value)) continue;
            try {
                long stamp = Long.parseLong(value);
                if (stamp > 0) return stamp > 100000000000L ? stamp / 1000 : stamp;
            } catch (NumberFormatException ignored) { }
            for (String pattern : new String[]{"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "MMM d, yyyy", "MMMM d, yyyy", "dd.MM.yyyy"}) {
                try {
                    java.text.SimpleDateFormat format = new java.text.SimpleDateFormat(pattern, Locale.US);
                    format.setLenient(false);
                    java.text.ParsePosition pos = new java.text.ParsePosition(0);
                    java.util.Date date = format.parse(value, pos);
                    if (date != null && pos.getIndex() == value.length()) return date.getTime() / 1000;
                } catch (Exception ignored) { }
            }
        }
        return 0;
    }

    /** Lightweight VOD/series category folders; deliberately does not enumerate catalog rows. */
    public static List<Channel> catalogFolders(String portal, String mac, int kind) throws Exception {
        List<Channel> out = new ArrayList<Channel>();
        if (kind != Channel.VOD && kind != Channel.SERIES) {
            throw new Exception("Invalid catalog kind");
        }
        try {
            Session s = session(portal, mac);
            String normalised = normPortal(portal);
            String type = kind == Channel.VOD ? "vod" : "series";
            Map<String, String> cats = catalogCategories(s, type);
            boolean hasReal = false;
            for (String id : cats.keySet()) {
                if (!"*".equals(id) && !"0".equals(id)) { hasReal = true; break; }
            }
            if (!hasReal) {
                cats = new LinkedHashMap<String, String>();
                cats.put("*", "All");
            }
            for (Map.Entry<String, String> e : cats.entrySet()) {
                String id = e.getKey();
                if (hasReal && ("*".equals(id) || "0".equals(id))) continue;
                Channel folder = new Channel();
                folder.kind = kind;
                folder.key = "stalker_category_" + kind + "_" + id;
                folder.name = e.getValue() == null || e.getValue().isEmpty() ? id : e.getValue();
                folder.group = folder.name;
                folder.container = "FLOWPLAY_CATEGORY";
                String art = s.categoryArtwork.get(type + "|" + id);
                folder.logo = art == null ? "" : art;
                folder.streamId = safeInt(id);
                folder.stalkerCmd = id;
                folder.stalkerPortal = normalised;
                folder.stalkerMac = s.mac;
                folder.url = "stalker://category/" + kind + "/" + enc(id);
                out.add(folder);
            }
        } catch (Exception e) {
            lastError = (kind == Channel.VOD ? "Movies: " : "Series: ") + msg(e);
            throw new Exception(lastError, e);
        }
        return out;
    }

    public static class CatalogPage {
        public List<Channel> items;
        public int total;
        public boolean hasMore;
    }

    /** Fetches exactly one ordered-list page for a category (never walks all pages). */
    public static CatalogPage catalogPage(Channel folder, int page, String search) throws Exception {
        CatalogPage result = new CatalogPage();
        result.items = new ArrayList<Channel>();
        result.total = 0;
        result.hasMore = false;
        if (folder == null || !"FLOWPLAY_CATEGORY".equals(folder.container)
                || (folder.kind != Channel.VOD && folder.kind != Channel.SERIES)
                || isEmpty(folder.stalkerPortal) || isEmpty(folder.stalkerMac)) {
            throw new Exception("Invalid Stalker catalog folder");
        }
        Session s = session(folder.stalkerPortal, folder.stalkerMac);
        String type = folder.kind == Channel.VOD ? "vod" : "series";
        int p = page < 1 ? 1 : page;
        String q = search == null ? "" : search.trim();
        String url = s.apiBase + "?type=" + type + "&action=get_ordered_list&category="
                + enc(folder.stalkerCmd) + "&p=" + p;
        if (!q.isEmpty()) url += "&search=" + enc(q);
        url += "&JsHttpRequest=1-xml";
        JSONObject j = js(http(url, s));
        JSONArray data = j == null ? null : j.optJSONArray("data");
        if (data == null) throw new Exception("Portal catalog response is not a list; reload provider to reconnect");
        Map<String, String> cats = new HashMap<String, String>();
        cats.put(folder.stalkerCmd, folder.group);
        Set<String> seen = new HashSet<String>();
        addCatalog(result.items, seen, data, cats, s, normPortal(folder.stalkerPortal), folder.kind, folder.group);
        result.total = j == null ? 0 : j.optInt("total_items", 0);
        int pageSize = j == null ? 0 : j.optInt("max_page_items", 0);
        if (pageSize > 0 && result.total > 0) result.hasMore = p * pageSize < result.total;
        else result.hasMore = !result.items.isEmpty();
        return result;
    }

    private static List<Channel> catalog(String portal,String mac,String type,int kind) throws Exception {
        Session s=session(portal,mac); String normalised=normPortal(portal); Map<String,String> cats=catalogCategories(s,type);
        List<Channel> out=new ArrayList<Channel>(); Set<String> seen=new HashSet<String>();
        // Stalker catalog APIs filter by category, not the ITV genre parameter.  A
        // wildcard response is authoritative on portals that support it; only walk
        // categories when wildcard is unavailable or returned no usable rows.
        List<String> groups=new ArrayList<String>(); groups.add("*");
        for(String category:groups){String previous=null; for(int p=1;p<=10000;p++) try {
            JSONObject j=js(http(s.apiBase+"?type="+type+"&action=get_ordered_list&category="+enc(category)+"&force_ch_link_check=&fav=0&sortby=added&p="+p+"&JsHttpRequest=1-xml",s)); JSONArray a=j==null?null:j.optJSONArray("data"); if(a==null||a.length()==0)break;
            String fp=a.toString(); if(fp.equals(previous))break; previous=fp; addCatalog(out,seen,a,cats,s,normalised,kind);
            int total=j.optInt("total_items",-1); if(total>0&&p*a.length()>=total)break;
        }catch(Exception e){lastError=msg(e);break;}}
        if(out.isEmpty()) for(String category:cats.keySet()){String previous=null; for(int p=1;p<=10000;p++) try {
            JSONObject j=js(http(s.apiBase+"?type="+type+"&action=get_ordered_list&category="+enc(category)+"&force_ch_link_check=&fav=0&sortby=added&p="+p+"&JsHttpRequest=1-xml",s)); JSONArray a=j==null?null:j.optJSONArray("data"); if(a==null||a.length()==0)break;
            String fp=a.toString(); if(fp.equals(previous))break; previous=fp; addCatalog(out,seen,a,cats,s,normalised,kind);
            int total=j.optInt("total_items",-1); if(total>0&&p*a.length()>=total)break;
        }catch(Exception e){lastError=msg(e);break;}}
        return out;
    }
    private static void addCatalog(List<Channel> out,Set<String> seen,JSONArray a,Map<String,String> cats,Session s,String portal,int kind){ addCatalog(out,seen,a,cats,s,portal,kind,""); }
    private static void addCatalog(List<Channel> out,Set<String> seen,JSONArray a,Map<String,String> cats,Session s,String portal,int kind,String fallbackGroup){
        if(a==null)return; for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;String id=o.optString("id",o.optString(kind==Channel.SERIES?"series_id":"movie_id",""));if(isEmpty(id))continue;String key=(kind==Channel.VOD?"vod_":"series_")+id;if(!seen.add(key))continue;Channel c=new Channel();c.kind=kind;c.key=key;c.name=o.optString("name",kind==Channel.VOD?"Video "+id:"Series "+id);c.logo=catalogArtwork(o,s);String gid=o.optString("category_id",o.optString("genre_id",""));c.group=cats.containsKey(gid)?cats.get(gid):fallbackGroup;c.stalkerPortal=portal;c.stalkerMac=s.mac;c.streamId=safeInt(id);c.stalkerCmd=o.optString("cmd",id);c.url="stalker://"+s.mac.replace(":","")+"/"+kind+"/"+id;out.add(c);}
    }
    private static int safeInt(String s){try{return Integer.parseInt(s);}catch(Exception e){return 0;}}
    public static List<Channel> vod(String portal,String mac) {
        try { return catalog(portal,mac,"vod",Channel.VOD); } catch (Exception e) { lastError=msg(e); return new ArrayList<Channel>(); }
    }
    public static List<Channel> series(String portal,String mac) {
        try { return catalog(portal,mac,"series",Channel.SERIES); } catch (Exception e) { lastError=msg(e); return new ArrayList<Channel>(); }
    }

    /** Lazy episode items.  The returned VOD-shaped Channels retain an explicit
     * FLOWPLAY_EP command (movie/season/episode), avoiding an episode-wide scan. */
    public static List<Channel> episodeChannels(Channel series) {
        List<Channel> out=new ArrayList<Channel>();
        if(series==null||series.kind!=Channel.SERIES||isEmpty(series.stalkerPortal)) { lastError="Invalid Stalker series"; return out; }
        try { Session s=session(series.stalkerPortal,series.stalkerMac); String id=series.streamId==0?series.stalkerCmd:String.valueOf(series.streamId);
            JSONObject j=js(http(s.apiBase+"?type=series&action=get_series&movie_id="+enc(id)+"&JsHttpRequest=1-xml",s));
            JSONArray direct=j==null?null:j.optJSONArray("episodes");
            if(direct==null&&j!=null){Object raw=j.opt("episodes"); if(raw instanceof JSONObject)direct=((JSONObject)raw).optJSONArray("data");}
            if(direct==null&&j!=null){Object raw=j.opt("data"); if(raw instanceof JSONArray)direct=(JSONArray)raw;}
            // Some Stalker variants return the episode array directly from get_series.
            if(direct!=null){for(int i=0;i<direct.length();i++){JSONObject e=direct.optJSONObject(i);if(e==null)continue;String cmd=e.optString("cmd",e.optString("id",""));if(isEmpty(cmd))continue;Channel c=new Channel();c.kind=Channel.VOD;c.key="episode_"+series.streamId+"_"+i;c.name=e.optString("name",e.optString("title","Episode "+(i+1)));c.group=series.group;c.logo=series.logo;c.stalkerPortal=series.stalkerPortal;c.stalkerMac=series.stalkerMac;c.stalkerCmd=episodePrefix(id,e.optString("season_id",e.optString("season","0")),cmd);c.url="stalker://"+s.mac.replace(":","")+"/episode/"+series.streamId+"/"+i;c.container=e.optString("container_extension","");out.add(c);} if(out.isEmpty())lastError="Series has no entitled episodes"; return out;}
            JSONArray seasons=j==null?null:j.optJSONArray("seasons"); if(seasons==null&&j!=null){Object raw=j.opt("data"); if(raw instanceof JSONArray)seasons=(JSONArray)raw;}
            if(seasons==null) seasons=new JSONArray().put(new JSONObject().put("id","0").put("season_num","1"));
            int ix=0;
            for(int si=0;si<seasons.length();si++){JSONObject season=seasons.optJSONObject(si); if(season==null)continue; String sid=season.optString("id",season.optString("season_id","0"));
                JSONObject ej=js(http(s.apiBase+"?type=series&action=get_ordered_list&movie_id="+enc(id)+"&season_id="+enc(sid)+"&p=1&JsHttpRequest=1-xml",s)); JSONArray a=ej==null?null:ej.optJSONArray("data"); if(a==null)continue;
                for(int i=0;i<a.length();i++){JSONObject e=a.optJSONObject(i);if(e==null)continue;String cmd=e.optString("cmd",e.optString("id",""));if(isEmpty(cmd))continue;Channel c=new Channel();c.kind=Channel.VOD;c.key="episode_"+series.streamId+"_"+ix;c.name=e.optString("name",e.optString("title","Episode "+(i+1)));c.group=series.group;c.logo=series.logo;c.stalkerPortal=series.stalkerPortal;c.stalkerMac=series.stalkerMac;c.stalkerCmd=episodePrefix(id,sid,cmd);c.url="stalker://"+s.mac.replace(":","")+"/episode/"+series.streamId+"/"+ix;c.container=e.optString("container_extension","");out.add(c);ix++;}
            }
            if(out.isEmpty())lastError="Series has no entitled episodes";
        } catch(Exception e){lastError=msg(e);} return out;
    }

    public static List<XtreamClient.Episode> episodes(Channel series){
        List<XtreamClient.Episode> out=new ArrayList<XtreamClient.Episode>(); if(series==null||series.kind!=Channel.SERIES||isEmpty(series.stalkerPortal))return out;
        try{Session s=session(series.stalkerPortal,series.stalkerMac);String id=series.streamId==0?series.stalkerCmd:String.valueOf(series.streamId);JSONObject j=js(http(s.apiBase+"?type=series&action=get_series&movie_id="+enc(id)+"&JsHttpRequest=1-xml",s));Object raw=j==null?null:j.opt("episodes");JSONArray a=raw instanceof JSONArray?(JSONArray)raw:(raw instanceof JSONObject?((JSONObject)raw).optJSONArray("data"):null);if(a==null&&j!=null)a=j.optJSONArray("data");if(a==null)return out;
            for(int i=0;i<a.length();i++){JSONObject e=a.optJSONObject(i);if(e==null)continue;String cmd=e.optString("cmd",e.optString("id",""));if(isEmpty(cmd))continue;XtreamClient.Episode ep=new XtreamClient.Episode();String sn=e.optString("season_num",e.optString("season","1"));String en=e.optString("episode_num",String.valueOf(i+1));ep.label="S"+sn+" E"+en+" — "+e.optString("title","Episode "+en);ep.url=createLink(s,Channel.SERIES,cmd);if(!isEmpty(ep.url))out.add(ep);}
        }catch(Exception e){lastError=msg(e);} return out;
    }

    private static String episodePrefix(String movie, String season, String cmd) { return "FLOWPLAY_EP|"+movie+"|"+season+"|"+cmd; }
    private static String commandForLink(String cmd) {
        if(cmd!=null && cmd.startsWith("FLOWPLAY_EP|")){ int a=cmd.indexOf('|',12), b=a<0?-1:cmd.indexOf('|',a+1); if(b>=0)return cmd.substring(b+1); }
        return cmd;
    }
    private static String createLink(Session s, int kind, String cmd) throws Exception {
        cmd=commandForLink(cmd);
        String type = kind == Channel.VOD ? "vod" : (kind == Channel.SERIES ? "series" : "itv");
        String body=http(s.apiBase+"?type="+type+"&action=create_link&cmd="+enc(cmd)
                +"&series=&forced_storage=&disable_ad=0&download=0&force_ch_link_check=0&JsHttpRequest=1-xml",s);
        JSONObject r=root(body), js=r.optJSONObject("js"); String u=js==null?null:urlFromCmd(js.optString("cmd",""));
        if(u==null)throw new Exception("Portal refused the link"+(js==null?"":" ("+js.optString("error","")+")")); return u;
    }

    // ------------------------------------------------------------------ playback link

    public static String urlFromCmd(String cmd) {
        if (cmd == null) {
            return null;
        }
        for (String tok : cmd.trim().split("\\s+")) {
            if (tok.contains("://")) {
                String t = tok.replaceFirst("^(ffrt\\d*|ffmpeg|auto)://(?=https?://)", "");
                return t.replaceAll("[\"',]+$", "");
            }
        }
        return null;
    }

    /** Creates a fresh tokenised stream link. Network call - never run on the UI thread. */
    public static String resolve(Channel ch) throws Exception {
        if (ch == null || isEmpty(ch.stalkerPortal) || isEmpty(ch.stalkerCmd)) {
            throw new Exception("No portal link");
        }
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            Session s = session(ch.stalkerPortal, ch.stalkerMac);
            try {
                String url = createLink(s, ch.kind, ch.stalkerCmd);
                return url;
            } catch (Exception e) {
                last = e;
            }
            // token probably expired: drop the cached session and try once more
            invalidate(ch.stalkerPortal, ch.stalkerMac);
        }
        String direct = urlFromCmd(ch.stalkerCmd);
        if (direct != null && !direct.contains("localhost")) {
            return direct;
        }
        throw last != null ? last : new Exception("Bad stream link");
    }

    /**
     * Single entry point for the player: use this wherever the app used to read Channel.url.
     * Non-Stalker channels return their normal url. Stalker channels are resolved on a worker
     * thread, so it is safe to call from the main thread (it blocks up to 45 s).
     */
    public static String playUrl(final Channel c) {
        if (c == null) {
            return "";
        }
        if (isEmpty(c.stalkerPortal) || isEmpty(c.stalkerCmd)) {
            return c.url == null ? "" : c.url;
        }
        final String[] out = new String[1];
        final Exception[] err = new Exception[1];
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    out[0] = resolve(c);
                } catch (Exception e) {
                    err[0] = e;
                }
            }
        });
        t.start();
        try {
            t.join(45000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        if (out[0] != null) {
            lastError = "";
            return out[0];
        }
        if (err[0] != null) {
            lastError = msg(err[0]);
        } else if (isEmpty(lastError)) {
            lastError = "Portal did not answer in time";
        }
        return ""; // never hand the stalker:// placeholder to the player
    }
}
