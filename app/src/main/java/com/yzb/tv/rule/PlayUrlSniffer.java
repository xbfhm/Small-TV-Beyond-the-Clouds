package com.yzb.tv.rule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 从番剧"播放页" HTML 里嗅探真实视频地址 (m3u8/mp4 优先)。
 * 策略:
 *  1. 页面里直接出现的 m3u8 / mp4 直链 (正则)
 *  2. iframe 的 src -> 跟一层, 提取其中的 m3u8/mp4
 *  3. 常见播放器配置 JSON (url / video / src 字段)
 * 找不到返回 null, 由上层回退到网页播放(WebView)。
 */
public class PlayUrlSniffer {

    private static final Pattern URL_PAT = Pattern.compile(
            "(https?://[^\\s\"'<>()\\\\]+?)\\.(m3u8|mp4|flv|ts|mkv|webm)(\\?[^\\s\"'<>()\\\\]*)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IFRAME_PAT = Pattern.compile(
            "<iframe[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    // JS 里赋值的 json 字符串: "url":"...m3u8", url: '...m3u8'
    private static final Pattern JSON_URL_PAT = Pattern.compile(
            "[\"']?(?:url|src|video|file|m3u8)[\"']?\\s*[:=]\\s*[\"'](https?://[^\"']+)",
            Pattern.CASE_INSENSITIVE);

    private final OkHttpClient client;
    private final String referer;
    private final String userAgent;

    public PlayUrlSniffer(OkHttpClient client, String referer, String userAgent) {
        this.client = client;
        this.referer = referer == null ? "" : referer;
        this.userAgent = (userAgent == null || userAgent.isEmpty())
                ? "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
                : userAgent;
    }

    /** 嗅探播放页 -> 视频直链 (失败返回 null) */
    public String sniff(String pageUrl) {
        try {
            String html = get(pageUrl, 0);
            if (html == null || html.isEmpty()) return null;

            // 1. 页面直接出现的视频直链
            String u = firstVideo(html);
            if (u != null) return u;

            // 2. JSON 配置里的 url
            u = firstJsonVideo(html);
            if (u != null) return u;

            // 3. iframe 跟一层
            Set<String> iframes = iframes(html);
            for (String f : iframes) {
                if (f == null) continue;
                String fu = resolve(pageUrl, f);
                String sub = get(fu, 1);
                if (sub == null) continue;
                String v = firstVideo(sub);
                if (v != null) return v;
                v = firstJsonVideo(sub);
                if (v != null) return v;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String firstVideo(String html) {
        Matcher m = URL_PAT.matcher(html);
        // 优先 m3u8, 其次 mp4
        String mp4 = null;
        while (m.find()) {
            String u = m.group(0);
            if (isAd(u)) continue;
            String ext = m.group(2).toLowerCase();
            if (ext.equals("m3u8")) return u;
            if (mp4 == null) mp4 = u;
        }
        return mp4;
    }

    private String firstJsonVideo(String html) {
        Matcher m = JSON_URL_PAT.matcher(html);
        while (m.find()) {
            String u = m.group(1);
            if (isAd(u)) continue;
            if (u.contains(".m3u8") || u.contains(".mp4")) return u;
        }
        return null;
    }

    private Set<String> iframes(String html) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = IFRAME_PAT.matcher(html);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    private boolean isAd(String u) {
        String l = u.toLowerCase();
        return l.contains("adposter") || l.contains("advert") || l.contains("/ad/")
                || l.contains("doubleclick") || l.contains("googlesyndication");
    }

    private String get(String url, int depth) {
        try {
            Request.Builder b = new Request.Builder().url(url)
                    .header("User-Agent", userAgent)
                    .header("Accept", "*/*")
                    .header("Accept-Language", "zh-CN,zh;q=0.9");
            if (!referer.isEmpty()) b.header("Referer", referer);
            try (Response resp = client.newCall(b.build()).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return null;
                return resp.body().string();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** 相对地址转绝对地址 */
    static String resolve(String base, String href) {
        if (href == null) return null;
        try {
            java.net.URI baseUri = java.net.URI.create(base);
            java.net.URI u = baseUri.resolve(href);
            return u.toString();
        } catch (Exception e) {
            return href;
        }
    }

    /** 规整番剧集/详情地址 (相对->绝对) */
    static String normalizeUrl(String baseUrl, String href) {
        if (href == null || href.isEmpty()) return "";
        href = href.trim();
        if (href.startsWith("http://") || href.startsWith("https://")) return href;
        String base = baseUrl == null ? "" : baseUrl;
        if (href.startsWith("//")) {
            int idx = base.indexOf("://");
            String scheme = idx >= 0 ? base.substring(0, idx + 3) : "https://";
            return scheme + href.substring(2);
        }
        // 相对
        try {
            java.net.URI u = java.net.URI.create(base).resolve(href);
            return u.toString();
        } catch (Exception e) {
            if (href.startsWith("/")) {
                // 拼 base 的 origin
                int idx = base.indexOf("://");
                if (idx >= 0) {
                    int slash = base.indexOf('/', idx + 3);
                    String origin = slash >= 0 ? base.substring(0, slash) : base;
                    return origin + href;
                }
            }
            return base + href;
        }
    }
}
