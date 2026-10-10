package com.yzb.tv.rule;

import org.json.JSONObject;

/**
 * 一条 Kazumi 规则 (插件)。
 * 支持两种模式:
 *  - xpath: 抓取 HTML, 用 XPath 提取搜索/剧集
 *  - api  : 请求 JSON API, 用 JSONPath 提取 (sorani 等)
 */
public class KazumiRule {
    public String name = "";
    public String baseUrl = "";

    // ---- XPath 模式字段 ----
    public String searchUrl = "";
    public String searchList = "";
    public String searchName = "";
    public String searchResult = "";
    public String chapterRoads = "";
    public String chapterResult = "";
    public boolean usePost = false;

    // ---- 通用 ----
    public String userAgent = "";
    public String referer = "";
    public boolean useWebview = true;
    public boolean useNativePlayer = true;
    public boolean adBlocker = false;
    public boolean useLegacyParser = false;
    public boolean multiSources = true;

    // ---- API 模式字段 ----
    public String searchMode = "xpath";    // "xpath" / "api"
    public String chapterMode = "xpath";
    public ApiRequest searchRequest;       // api 搜索请求
    public ApiSearchCfg searchApi;         // api 搜索路径
    public ApiRequest chapterRequest;      // api 章节请求
    public ApiChapterCfg chapterApi;       // api 章节路径

    public boolean isApiSearch() { return "api".equalsIgnoreCase(searchMode); }
    public boolean isApiChapter() { return "api".equalsIgnoreCase(chapterMode); }

    public static KazumiRule fromJson(JSONObject o) {
        KazumiRule r = new KazumiRule();
        r.name = o.optString("name");
        r.baseUrl = o.optString("baseURL", o.optString("baseUrl", ""));
        r.searchUrl = o.optString("searchURL", "");
        r.searchList = o.optString("searchList", "");
        r.searchName = o.optString("searchName", "");
        r.searchResult = o.optString("searchResult", "");
        r.chapterRoads = o.optString("chapterRoads", "");
        r.chapterResult = o.optString("chapterResult", "");
        r.usePost = o.optBoolean("usePost", false);
        r.userAgent = o.optString("userAgent", "");
        r.referer = o.optString("referer", "");
        r.useWebview = o.optBoolean("useWebview", true);
        r.useNativePlayer = o.optBoolean("useNativePlayer", true);
        r.adBlocker = o.optBoolean("adBlocker", false);
        r.useLegacyParser = o.optBoolean("useLegacyParser", false);
        r.multiSources = o.optBoolean("muliSources", o.optBoolean("multiSources", true));

        r.searchMode = o.optString("searchMode", "xpath");
        r.chapterMode = o.optString("chapterMode", "xpath");

        JSONObject sapi = o.optJSONObject("searchApiConfig");
        if (sapi != null) {
            r.searchMode = "api";
            r.searchApi = ApiSearchCfg.fromJson(sapi);
            r.searchRequest = ApiRequest.fromJson(sapi.optJSONObject("request"));
        }
        JSONObject capi = o.optJSONObject("chapterApiConfig");
        if (capi != null) {
            r.chapterMode = "api";
            r.chapterApi = ApiChapterCfg.fromJson(capi);
            r.chapterRequest = ApiRequest.fromJson(capi.optJSONObject("request"));
        }
        return r;
    }

    /** API 请求定义 */
    public static class ApiRequest {
        public String method = "GET";
        public String url = "";
        public JSONObject headers = new JSONObject();
        public JSONObject query = new JSONObject();
        public String bodyType = "none";   // none/json/form
        public Object body = null;

        static ApiRequest fromJson(JSONObject o) {
            ApiRequest a = new ApiRequest();
            if (o == null) return a;
            a.method = o.optString("method", "GET").toUpperCase();
            a.url = o.optString("url", "");
            if (o.has("headers")) a.headers = o.optJSONObject("headers");
            if (o.has("query")) a.query = o.optJSONObject("query");
            a.bodyType = o.optString("bodyType", "none");
            a.body = o.opt("body");
            return a;
        }
    }

    /** API 搜索路径配置 */
    public static class ApiSearchCfg {
        public String listPath = "$.data[*]";
        public String namePath = "$.name";
        public String sourcePath = "$.url";

        static ApiSearchCfg fromJson(JSONObject o) {
            ApiSearchCfg c = new ApiSearchCfg();
            c.listPath = o.optString("listPath", c.listPath);
            c.namePath = o.optString("namePath", c.namePath);
            c.sourcePath = o.optString("sourcePath", c.sourcePath);
            return c;
        }
    }

    /** API 章节路径配置 */
    public static class ApiChapterCfg {
        public String format = "nested";   // nested / delimited
        public String roadsPath = "";
        public String roadNamePath = "";
        public String episodesPath = "$.episodes[*]";
        public String episodeNamePath = "$.name";
        public String episodeUrlPath = "$.url";
        // 播放页模板 (有则用模板拼接, 替代 episodeUrlPath 的原始值)
        public String episodePageUrl = "";
        public JSONObject episodePageQuery = new JSONObject();
        // 章节响应变量: 变量名 -> JSONPath (从章节响应整体提取, 如 slug: $.data.slug)
        public java.util.Map<String, String> variables = new java.util.HashMap<>();

        static ApiChapterCfg fromJson(JSONObject o) {
            ApiChapterCfg c = new ApiChapterCfg();
            c.format = o.optString("format", "nested");
            c.roadsPath = o.optString("roadsPath", "");
            c.roadNamePath = o.optString("roadNamePath", "");
            c.episodesPath = o.optString("episodesPath", c.episodesPath);
            c.episodeNamePath = o.optString("episodeNamePath", c.episodeNamePath);
            c.episodeUrlPath = o.optString("episodeUrlPath", c.episodeUrlPath);
            JSONObject page = o.optJSONObject("episodePage");
            if (page != null) {
                c.episodePageUrl = page.optString("url", "");
                JSONObject q = page.optJSONObject("query");
                if (q != null) c.episodePageQuery = q;
            }
            JSONObject vars = o.optJSONObject("variables");
            if (vars != null) {
                java.util.Iterator<String> it = vars.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    c.variables.put(k, String.valueOf(vars.opt(k)));
                }
            }
            return c;
        }
    }
}
