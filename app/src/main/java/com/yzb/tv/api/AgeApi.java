package com.yzb.tv.api;

import com.yzb.tv.util.DiskCache;
import com.yzb.tv.model.Anime;
import com.yzb.tv.model.AnimeDetail;
import com.yzb.tv.model.Episode;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.ConnectionSpec;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * AGE动漫 官方 API 客户端
 * 接口链路(2026-08 实测): search -> detail -> jx 解析 -> m3u8 直链
 * 兼容 Android 5.x: 全信任证书(老系统证书库旧) + TLS1.2
 */
public class AgeApi {
        private static final String API_BASE = "https://api.agedm.io/v2/";
    private static final String JX_BASE = "https://jx.wuzhoupai.com:8443/m3u8/?url=";
    private static final String JX_BASE_VIP = "https://jx.wuzhoupai.com:8443/vip/?url=";
    private static final String UA = "Mozilla/5.0 (Linux; Android 7.0; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0 Mobile Safari/537.36";

    private static final OkHttpClient CLIENT = buildClient(15, 20);
    // 解析专用: 短超时, 坏线路快速失败 (加载提速)
    private static final OkHttpClient RESOLVE_CLIENT = buildClient(5, 10);

    private static DiskCache CACHE = null;

    /** 初始化磁盘缓存 (在 Application.onCreate 调用) */
    public static void init(android.content.Context ctx) {
        try {
            CACHE = new DiskCache(ctx);
            CACHE.resetDaily();   // 每天7点后自动清缓存
        } catch (Exception ignored) {}
    }

    /** 共享的全信任 OkHttpClient (供播放器数据源复用) */
    public static OkHttpClient getClient() { return CLIENT; }

    private static OkHttpClient buildClient(final int connectSec, final int readSec) {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        public void checkClientTrusted(X509Certificate[] c, String a) {}
                        public void checkServerTrusted(X509Certificate[] c, String a) {}
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    }};
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());
            SSLSocketFactory sf = sc.getSocketFactory();
            return new OkHttpClient.Builder()
                    .connectTimeout(connectSec, TimeUnit.SECONDS)
                    .readTimeout(readSec, TimeUnit.SECONDS)
                    .writeTimeout(connectSec, TimeUnit.SECONDS)
                    .sslSocketFactory(sf, (X509TrustManager) trustAll[0])
                    .connectionSpecs(java.util.Arrays.asList(
                            ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT))
                    .protocols(java.util.Arrays.asList(okhttp3.Protocol.HTTP_1_1))
                    .cookieJar(new CookieJar() {
                        private final java.util.List<Cookie> jar = new java.util.ArrayList<>();
                        @Override public void saveFromResponse(HttpUrl u, java.util.List<Cookie> c) {
                            for (Cookie x : c) {
                                for (int i = jar.size() - 1; i >= 0; i--) {
                                    if (jar.get(i).name().equals(x.name())) jar.remove(i);
                                }
                                jar.add(x);
                            }
                        }
                        @Override public java.util.List<Cookie> loadForRequest(HttpUrl u) { return jar; }
                    })
                    .build();
        } catch (Exception e) {
            System.err.println("AgeApi TLS init failed: " + e);
            return new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build();
        }
    }

    /** GET 返回文本 (403 自动带 cookie 重试一次) */
    private static String get(String url) throws IOException { return get(url, CLIENT); }

    /** 解析专用: 短超时 (坏线路快速失败) */
    private static String getFast(String url) throws IOException { return get(url, RESOLVE_CLIENT); }

    private static String get(String url, OkHttpClient client) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) {
                try { Thread.sleep(300L * attempt); } catch (InterruptedException ignored) {}
            }
            Request req = new Request.Builder()
                    .url(url)
                    .header("User-Agent", UA)
                    .header("Referer", "https://www.agedm.io/")
                    .header("Accept", "application/json,text/html,*/*")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Accept-Encoding", "identity")
                    .build();
            try (Response resp = client.newCall(req).execute()) {
                if (resp.isSuccessful()) {
                    String body = resp.body() != null ? resp.body().string() : "";
                    return body;
                }
                int code = resp.code();
                last = new IOException("HTTP " + code + " " + url);
                // 403: 访问主站预热拿 cookie 后重试 (AGE 有轻度反爬)
                if (code == 403) {
                    warmUp();
                    continue;
                }
                if (code >= 500) continue;   // 5xx 服务端错误也重试
                throw last;
            }
        }
        throw last != null ? last : new IOException("HTTP fail " + url);
    }

    /** 预热: 访问 AGE 主站首页, 建立 cookie/session (规避间歇性 403) */
    public static void warmUp() {
        try {
            Request req = new Request.Builder()
                    .url("https://www.agedm.io/")
                    .header("User-Agent", UA)
                    .header("Accept", "text/html,application/xhtml+xml,*/*")
                    .build();
            try (Response r = CLIENT.newCall(req).execute()) {
                r.body();   // 触发读取, 让 CookieJar 保存 cookie
            }
        } catch (Exception ignored) {}
    }

    /** GET JSONObject (带磁盘缓存: 命中且未过期则秒回) */
    private static JSONObject getJson(String path, long maxAgeMs) throws Exception {
        if (CACHE != null) {
            String cached = CACHE.get(path, maxAgeMs);
            if (cached != null) {
                try { return new JSONObject(cached); } catch (Exception ignored) {}
            }
        }
        String text = get(API_BASE + path);
        if (CACHE != null && !text.isEmpty()) CACHE.put(path, text);
        return new JSONObject(text);
    }

    private static JSONObject getJson(String path) throws Exception {
        return getJson(path, 10 * 60 * 1000L);   // 默认 10 分钟
    }

    /** 从视频 JSON 里取封面 URL (兼容不同字段名, 取到即返回, 并解包百度代理为 CDN 直链) */
    private static String coverOf(JSONObject v) {
        for (String k : new String[]{"cover", "pic", "PicSmall", "pic_small", "picSmall", "img", "Pic"}) {
            String c = v.optString(k);
            if (!c.isEmpty() && !"null".equals(c)) return directCover(c);
        }
        return "";
    }

    /** 解包百度图片代理 (gimg*.baidu.com) -> AGE CDN 直链 (更快更稳) */
    private static String directCover(String url) {
        if (url == null || url.isEmpty()) return "";
        int idx = url.indexOf("src=");
        if (url.contains("gimg") && idx >= 0) {
            try {
                String src = java.net.URLDecoder.decode(url.substring(idx + 4), "UTF-8");
                if (src.startsWith("http")) return src;
            } catch (Exception ignored) {}
        }
        return url;
    }

    /** AGE CDN 封面直链 (按 id 拼接, 供 week_list 等无 cover 字段的条目使用) */
    private static String cdnCover(int id) {
        return id > 0 ? "https://cdn.aqdstatic.com:966/age/covers/" + id + ".jpg" : "";
    }

    /** 搜索番剧 */
    public static List<Anime> search(String keyword) throws Exception {
        List<Anime> out = new ArrayList<>();
        JSONObject j = getJson("search?query=" + java.net.URLEncoder.encode(keyword, "UTF-8"));
        JSONArray videos = j.optJSONObject("data").optJSONArray("videos");
        if (videos == null) return out;
        for (int i = 0; i < videos.length() && i < 30; i++) {
            JSONObject v = videos.getJSONObject(i);
            Anime a = new Anime(v.optInt("id"),
                    v.optString("name"),
                    v.optString("premiere"),
                    v.optString("status"),
                    v.optString("uptodate"));
            a.cover = coverOf(v);
            out.add(a);
        }
        return out;
    }

    /** 首页: 最新 + 本周新番 */
    public static List<Anime> home() throws Exception {
        List<Anime> out = new ArrayList<>();
        JSONObject j = getJson("home-list", 5 * 60 * 1000L);
        JSONArray latest = j.optJSONArray("latest");
        if (latest != null) {
            for (int i = 0; i < latest.length(); i++) {
                JSONObject v = latest.getJSONObject(i);
                Anime a = new Anime(v.optInt("AID"), v.optString("Title"), "", "", "");
                a.cover = coverOf(v);
                a.display = "🔥 " + a.name + "  " + v.optString("NewTitle");
                out.add(a);
            }
        }
        JSONObject week = j.optJSONObject("week_list");
        if (week != null) {
            java.util.Iterator<String> keys = week.keys();
            while (keys.hasNext()) {
                JSONArray list = week.optJSONArray(keys.next());
                if (list == null) continue;
                for (int i = 0; i < list.length(); i++) {
                    JSONObject v = list.getJSONObject(i);
                    Anime a = new Anime(v.optInt("id"), v.optString("name"), "", "", "");
                    a.cover = coverOf(v);   // 本周新番也加载封面
                    if (a.cover.isEmpty()) a.cover = cdnCover(v.optInt("id"));  // week_list 无 cover 字段, 用 id 拼 CDN 直链
                    a.display = (v.optInt("isnew") == 1 ? "NEW " : "") + a.name;
                    out.add(a);
                }
            }
        }
        return out;
    }

    /** 新番目录 */
    public static List<Anime> catalog(int year, int season) throws Exception {
        List<Anime> out = new ArrayList<>();
        JSONObject j = getJson("catalog?year=" + year + "&season=" + season + "&page=1", 60 * 60 * 1000L);
        JSONArray videos = j.optJSONArray("videos");
        if (videos == null) return out;
        for (int i = 0; i < videos.length() && i < 12; i++) {
            JSONObject v = videos.getJSONObject(i);
            Anime a = new Anime(v.optInt("id"), v.optString("name"),
                    v.optString("premiere"), v.optString("status"), v.optString("uptodate"));
            a.cover = coverOf(v);
            a.display = a.name;
            out.add(a);
        }
        return out;
    }

    /** 详情 + 集数(扁平化, 免费线路优先) */
    public static AnimeDetail detail(int aid) throws Exception {
        AnimeDetail d = new AnimeDetail();
        JSONObject j = getJson("detail/" + aid, 24 * 60 * 60 * 1000L);
        JSONObject video = j.optJSONObject("video");
        if (video == null) throw new IOException("detail 无 video");
        d.id = video.optInt("id");
        d.name = video.optString("name");
        d.nameOther = video.optString("name_other");
        d.cover = video.optString("cover");
        d.intro = video.optString("intro_clean");
        if (d.intro.isEmpty()) d.intro = video.optString("intro");
        d.premiere = video.optString("premiere");
        d.status = video.optString("status");
        d.uptodate = video.optString("uptodate");
        d.type = video.optString("type");
        JSONArray tags = video.optJSONArray("tags_arr");
        if (tags != null) for (int i = 0; i < tags.length(); i++) d.tags.add(tags.optString(i));

        JSONObject pjx = j.optJSONObject("player_jx");
        if (pjx != null) {
            if (!pjx.optString("zj").isEmpty()) d.jxZJ = pjx.optString("zj");
            if (!pjx.optString("vip").isEmpty()) d.jxVIP = pjx.optString("vip");
        }

        JSONObject playlists = video.optJSONObject("playlists");
        if (playlists == null) return d;
        // 遍历全部线路: 跳过 VIP 平台线(qiyi/qq/youku/mgtv/bilibili), 其余均视为免费线
        // (动态解析, 覆盖 xigua 等非硬编码线路, 避免误判"仅有VIP")
        java.util.Iterator<String> lineKeys = playlists.keys();
        while (lineKeys.hasNext()) {
            String line = lineKeys.next();
            if (isVipLine(line)) continue;
            JSONArray eps = playlists.optJSONArray(line);
            if (eps == null) continue;
            for (int i = 0; i < eps.length(); i++) {
                JSONArray pair = eps.optJSONArray(i);
                if (pair == null) continue;
                d.episodes.add(new Episode(line, labelOf(line), pair.optString(0), pair.optString(1), false));
            }
        }
        if (!d.episodes.isEmpty()) d.currentLine = d.episodes.get(0).line;
        // 无免费线路 = 仅有VIP源(版权番) 标记
        d.vipOnly = d.episodes.isEmpty();
        return d;
    }

    /** 加密串 -> m3u8 直链 */
    public static String resolve(String enc, boolean vip, String jxZj, String jxVip) throws IOException {
        if (enc == null || enc.isEmpty()) throw new IOException("无播放标识");
        String jx = vip ? jxVip : jxZj;
        String html = getFast(jx + enc);
        Matcher m = Pattern.compile("https?://[^\\s\"']+\\.m3u8[^\\s\"']*").matcher(html);
        if (m.find()) return m.group(0);
        Matcher m2 = Pattern.compile("https?://[^\\s\"']+\\.mp4[^\\s\"']*").matcher(html);
        if (m2.find()) {
            String u = m2.group(0);
            // 过滤广告片(会员引导): adposter/advert 等
            if (u.contains("adposter") || u.contains("advert") || u.contains("/ad/")) {
                throw new IOException("广告源(需会员)");
            }
            return u;
        }
        throw new IOException("解析页未找到播放地址");
    }

    /** 是否是 VIP 平台线路 (需视频会员才能看) */
    private static boolean isVipLine(String line) {
        return "qiyi".equals(line) || "qq".equals(line) || "youku".equals(line)
                || "mgtv".equals(line) || "bilibili".equals(line);
    }

    private static String labelOf(String line) {
        switch (line) {
            case "xigua": return "西瓜";
            case "ffm3u8": return "非凡";
            case "bfzym3u8": return "暴风";
            case "wjm3u8": return "无尽";
            case "hnm3u8": return "红牛";
            case "lzm3u8": return "计算云";
            case "wolong": return "凤雏云";
            case "sdm3u8": return "闪电";
            case "qiyi": return "爱奇艺";
            case "qq": return "腾讯";
            case "youku": return "优酷";
            case "mgtv": return "芒果";
            case "bilibili": return "B站";
            case "zjm3u8": return "自建云";
            case "99m3u8": return "九九";
            case "tkm3u8": return "天空";
            case "kbm3u8": return "快播";
            case "bjm3u8": return "八戒";
            case "xkm3u8": return "想看";
            case "tpm3u8": return "淘片";
            case "baba": return "巴巴云";
            case "qiqi": return "七七云";
            case "panda": return "熊猫";
            case "tt": return "TT云";
            case "my": return "蚂蚁云";
            case "wy": return "微软云";
            case "baidu": return "千度云";
            default: return line;
        }
    }
}
