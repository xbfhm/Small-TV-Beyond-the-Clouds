package com.yzb.tv.rule;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.wanghaomiao.xpath.model.JXDocument;
import cn.wanghaomiao.xpath.model.JXNode;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Kazumi 规则引擎: 用规则执行 搜索 / 章节(剧集) 解析。
 *  - XPath 模式: Jsoup 解析 HTML + JsoupXpath 求值
 *  - API 模式:   JSONPath 求值 + @变量模板
 */
public class KazumiEngine {

    /** 搜索结果 */
    public static class SearchItem {
        public String name;
        public String url;     // 详情页 URL (XPath) 或 源 ID (API 模式)
        public SearchItem(String n, String u) { name = n; url = u; }
    }

    /** 一集 */
    public static class Ep {
        public String name;
        public String url;     // 播放页 URL
        public Ep(String n, String u) { name = n; url = u; }
    }

    /** 一条线路 (播放分组) */
    public static class Road {
        public String name = "";
        public final List<Ep> eps = new ArrayList<>();
    }

    private final OkHttpClient client;

    public KazumiEngine(OkHttpClient client) {
        this.client = client;
    }

    // ================= 搜索 =================
    public List<SearchItem> search(KazumiRule r, String keyword) throws Exception {
        if (r.isApiSearch()) return apiSearch(r, keyword);
        return xpathSearch(r, keyword);
    }

    private List<SearchItem> xpathSearch(KazumiRule r, String keyword) throws Exception {
        List<SearchItem> out = new ArrayList<>();
        String url = r.searchUrl.replace("@keyword", urlEncode(keyword));
        String html = get(url, r);
        if (html == null) return out;
        JXDocument jx = new JXDocument(Jsoup.parse(html));
        List<JXNode> items = jx.selN(r.searchList);
        for (JXNode it : items) {
            try {
                String name = nodeText(first(it.sel(r.searchName)));
                String href = nodeHref(first(it.sel(r.searchResult)));
                if (name.isEmpty() || href.isEmpty()) continue;
                String detail = PlayUrlSniffer.normalizeUrl(r.baseUrl, href);
                out.add(new SearchItem(name, detail));
            } catch (Exception ignored) {}
        }
        return out;
    }

    private List<SearchItem> apiSearch(KazumiRule r, String keyword) throws Exception {
        List<SearchItem> out = new ArrayList<>();
        Map<String, Object> vars = new HashMap<>();
        vars.put("keyword", keyword);
        String body = apiRequest(r.searchRequest, vars, r);
        if (body == null) return out;
        Object doc = new org.json.JSONTokener(body).nextValue();
        if (doc == null) return out;
        List<Object> nodes = JsonPath.read(doc, r.searchApi.listPath);
        for (Object node : nodes) {
            String name = JsonPath.asString(JsonPath.readFirst(node, r.searchApi.namePath));
            String src = JsonPath.asString(JsonPath.readFirst(node, r.searchApi.sourcePath));
            if (name.isEmpty() || src.isEmpty()) continue;
            out.add(new SearchItem(name, src));
        }
        return out;
    }

    // ================= 章节 (剧集) =================
    public List<Road> chapters(KazumiRule r, String detailUrl) throws Exception {
        if (r.isApiChapter()) return apiChapters(r, detailUrl);
        return xpathChapters(r, detailUrl);
    }

    private List<Road> xpathChapters(KazumiRule r, String detailUrl) throws Exception {
        List<Road> out = new ArrayList<>();
        String html = get(detailUrl, r);
        if (html == null) return out;
        JXDocument jx = new JXDocument(Jsoup.parse(html));
        List<JXNode> roads = jx.selN(r.chapterRoads);
        for (JXNode roadNode : roads) {
            try {
                List<JXNode> epNodes = roadNode.sel(r.chapterResult);
                Road road = new Road();
                for (int i = 0; i < epNodes.size(); i++) {
                    JXNode en = epNodes.get(i);
                    String href = nodeHref(en);
                    if (href.isEmpty()) continue;
                    String name = nodeText(en).replaceAll("\\s+", "");
                    if (name.isEmpty()) name = "第" + (i + 1) + "集";
                    road.eps.add(new Ep(name, PlayUrlSniffer.normalizeUrl(r.baseUrl, href)));
                }
                if (!road.eps.isEmpty()) {
                    road.name = "线路" + (out.size() + 1);
                    out.add(road);
                }
            } catch (Exception ignored) {}
        }
        return out;
    }

    private List<Road> apiChapters(KazumiRule r, String detailUrl) throws Exception {
        List<Road> out = new ArrayList<>();
        Map<String, Object> vars = new HashMap<>();
        vars.put("source", detailUrl);   // API 模式下 detailUrl 实为源 ID
        String body = apiRequest(r.chapterRequest, vars, r);
        if (body == null) return out;
        Object doc = new org.json.JSONTokener(body).nextValue();
        KazumiRule.ApiChapterCfg cfg = r.chapterApi;

        // 提取章节响应整体变量 (如 slug: $.data.slug)
        for (Map.Entry<String, String> e : cfg.variables.entrySet()) {
            Object v = JsonPath.readFirst(doc, e.getValue());
            if (v != null) vars.put(e.getKey(), v);
        }

        List<Object> roadNodes = cfg.roadsPath.isEmpty()
                ? java.util.Collections.singletonList(doc)
                : JsonPath.read(doc, cfg.roadsPath);
        for (int ri = 0; ri < roadNodes.size(); ri++) {
            Object roadNode = roadNodes.get(ri);
            Road road = new Road();
            String roadName = cfg.roadNamePath.isEmpty() ? "" : JsonPath.asString(JsonPath.readFirst(roadNode, cfg.roadNamePath));
            List<Object> epNodes = JsonPath.read(roadNode, cfg.episodesPath);
            for (int ei = 0; ei < epNodes.size(); ei++) {
                Object epNode = epNodes.get(ei);
                String name = JsonPath.asString(JsonPath.readFirst(epNode, cfg.episodeNamePath));
                if (name.isEmpty()) name = "第" + (ei + 1) + "集";
                String rawUrl = cfg.episodeUrlPath.isEmpty() ? "" : JsonPath.asString(JsonPath.readFirst(epNode, cfg.episodeUrlPath));
                // 播放页 URL
                String playUrl;
                if (!cfg.episodePageUrl.isEmpty()) {
                    Map<String, Object> pv = new HashMap<>(vars);
                    pv.put("episodeUrl", rawUrl);
                    pv.put("episodeNumber", ei + 1);
                    pv.put("episodeIndex", ei);
                    pv.put("roadNumber", ri + 1);
                    pv.put("roadIndex", ri);
                    playUrl = JsonPath.render(cfg.episodePageUrl, pv, true);
                    // 合并 query 参数 (渲染变量)
                    if (cfg.episodePageQuery.length() > 0) {
                        StringBuilder qs = new StringBuilder();
                        org.json.JSONObject q = cfg.episodePageQuery;
                        java.util.Iterator<String> qk = q.keys();
                        while (qk.hasNext()) {
                            String key = qk.next();
                            String val = JsonPath.render(String.valueOf(q.opt(key)), pv, true);
                            qs.append(qs.length() == 0 && !playUrl.contains("?") ? "?" : "&");
                            qs.append(JsonPath.render(key, pv, true)).append("=").append(val);
                        }
                        playUrl += qs.toString();
                    }
                } else {
                    playUrl = PlayUrlSniffer.normalizeUrl(r.baseUrl, rawUrl);
                }
                if (playUrl.isEmpty()) continue;
                road.eps.add(new Ep(name, playUrl));
            }
            if (!road.eps.isEmpty()) {
                road.name = roadName.isEmpty() ? "线路" + (out.size() + 1) : roadName;
                out.add(road);
            }
        }
        return out;
    }

    // ================= HTTP =================
    private String get(String url, KazumiRule r) {
        try {
            Request.Builder b = new Request.Builder().url(url)
                    .header("User-Agent", ua(r))
                    .header("Accept", "text/html,application/xhtml+xml,*/*")
                    .header("Accept-Language", "zh-CN,zh;q=0.9");
            String ref = r.referer.isEmpty() ? (r.baseUrl.isEmpty() ? "" : r.baseUrl) : r.referer;
            if (!ref.isEmpty()) b.header("Referer", ref);
            try (Response resp = client.newCall(b.build()).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return null;
                return resp.body().string();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String apiRequest(KazumiRule.ApiRequest req, Map<String, Object> vars, KazumiRule r) {
        try {
            String url = JsonPath.render(req.url, vars, true);
            // 组装 query (渲染变量)
            java.util.Map<String, String> qs = new java.util.LinkedHashMap<>();
            if (req.query != null) {
                org.json.JSONObject q = req.query;
                java.util.Iterator<String> keys = q.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    Object v = q.opt(k);
                    String val = v == null ? "" : JsonPath.render(String.valueOf(v), vars, true);
                    qs.put(k, val);
                }
            }
            // 拼接 query
            if (!qs.isEmpty()) {
                StringBuilder sb = new StringBuilder(url);
                sb.append(url.contains("?") ? "&" : "?");
                boolean first = true;
                for (Map.Entry<String, String> e : qs.entrySet()) {
                    if (!first) sb.append("&");
                    sb.append(urlEncode(e.getKey())).append("=").append(e.getValue());
                    first = false;
                }
                url = sb.toString();
            }
            Request.Builder b = new Request.Builder().url(url)
                    .header("User-Agent", ua(r))
                    .header("Accept", "application/json,*/*");
            String ref = r.referer.isEmpty() ? (r.baseUrl.isEmpty() ? "" : r.baseUrl) : r.referer;
            if (!ref.isEmpty()) b.header("Referer", ref);
            try (Response resp = client.newCall(b.build()).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return null;
                return resp.body().string();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String ua(KazumiRule r) {
        return (r.userAgent == null || r.userAgent.isEmpty())
                ? "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
                : r.userAgent;
    }

    private static String urlEncode(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }

    // ================= JsoupXpath 节点辅助 =================
    private static JXNode first(List<JXNode> l) {
        return (l == null || l.isEmpty()) ? null : l.get(0);
    }

    private static String nodeText(JXNode n) {
        if (n == null) return "";
        if (n.isText()) return n.getTextVal();
        Element e = n.getElement();
        return e != null ? e.text() : "";
    }

    private static String nodeHref(JXNode n) {
        if (n == null) return "";
        Element e = n.getElement();
        return e != null ? e.attr("href") : "";
    }
}
