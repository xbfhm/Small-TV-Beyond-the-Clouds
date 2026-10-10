package com.yzb.tv.api;

import com.yzb.tv.util.Http;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 苹果CMS (maccms) 标准采集接口客户端 —— 「稳定播放源」
 * 当前默认源: 非凡资源 (用户网站看剧页同款, 实测 98497 部/当天更新)
 *   - 列表:  ?ac=list&pg=1&t=类型id
 *   - 搜索:  ?ac=detail&wd=关键词
 *   - 详情:  ?ac=detail&ids=vod_id
 *   - 播放:  vod_play_url 解析出 m3u8 直链 (ffm3u8 线路直接可播)
 * 国内网络直连可用 (海外 IP 会被 CDN 拒绝)
 */
public class MaccmsApi {

    /** 源列表 (可扩展备用源, 全部 maccms 标准格式) */
    public static final String[][] SOURCES = {
            {"ffzy", "非凡资源", "https://api.ffzyapi.com/api.php/provide/vod/"},
            // {"备用源id", "备用源名", "https://xxx/api.php/provide/vod/"},
    };

    /** 分类: 名称 -> type_id (与网站看剧页一致) */
    public static final String[][] CATEGORIES = {
            {"电影", ""},
            {"国产剧", "13"},
            {"美剧", "16"},
            {"日剧", "22"},
            {"韩剧", "15"},
            {"港台剧", "14"},
            {"泰国剧", "24"},
            {"日本动漫", "30"},
            {"国产动漫", "29"},
            {"欧美动漫", "31"},
            {"综艺", "25"},
    };

    public static class Vod {
        public long vodId;
        public String name = "";
        public String cover = "";
        public String remarks = "";   // 更新状态
        public String blurb = "";     // 简介
        public String[] lineNames = {};   // 线路名 (feifan, ffm3u8...)
        public String[][] lines = {};     // [线路][集] = 播放地址
        public String[] epNames = {};     // [集] = "第01集"

        public int lineCount() { return lines.length; }
        public int epCount(int line) { return line >= 0 && line < lines.length ? lines[line].length : 0; }
    }

    public static String api(int sourceIdx) {
        int i = sourceIdx >= 0 && sourceIdx < SOURCES.length ? sourceIdx : 0;
        return SOURCES[i][2];
    }

    // ---------- 基础请求 ----------

    private static JSONObject getJson(String url) throws Exception {
        return new JSONObject(Http.get(url));
    }

    /** 列表 */
    public static JSONArray list(int sourceIdx, String typeId, int page) throws Exception {
        StringBuilder url = new StringBuilder(api(sourceIdx))
                .append("?ac=list&pg=").append(page);
        if (typeId != null && !typeId.isEmpty()) url.append("&t=").append(typeId);
        JSONObject d = getJson(url.toString());
        if (d.optInt("code", -1) != 1) throw new Exception(d.optString("msg", "列表请求失败"));
        JSONArray arr = d.optJSONArray("list");
        return arr != null ? arr : new JSONArray();
    }

    /** 搜索 (maccms 用 ac=detail&wd=) */
    public static JSONArray search(int sourceIdx, String keyword, int page) throws Exception {
        String url = api(sourceIdx) + "?ac=detail&wd=" + WbiSign.urlEncode(keyword) + "&pg=" + page;
        JSONObject d = getJson(url);
        if (d.optInt("code", -1) != 1) throw new Exception(d.optString("msg", "搜索失败"));
        JSONArray arr = d.optJSONArray("list");
        return arr != null ? arr : new JSONArray();
    }

    /** 详情 */
    public static Vod detail(int sourceIdx, long vodId) throws Exception {
        String url = api(sourceIdx) + "?ac=detail&ids=" + vodId;
        JSONObject d = getJson(url);
        JSONArray arr = d.optJSONArray("list");
        if (arr == null || arr.length() == 0) throw new Exception("视频不存在");
        return parseVod(arr.optJSONObject(0));
    }

    /** 解析 maccms vod 对象 (列表/搜索返回的也是这个结构) */
    public static Vod parseVod(JSONObject o) {
        Vod v = new Vod();
        if (o == null) return v;
        v.vodId = o.optLong("vod_id", 0);
        v.name = o.optString("vod_name", "");
        v.cover = o.optString("vod_pic", "");
        v.remarks = o.optString("vod_remarks", "");
        v.blurb = o.optString("vod_blurb", "");

        String from = o.optString("vod_play_from", "");
        String url = o.optString("vod_play_url", "");
        if (!url.isEmpty()) {
            String[] lineBlocks = url.split("\\$\\$\\$");
            String[] fromNames = from.isEmpty() ? new String[0] : from.split("\\$\\$\\$");
            int n = lineBlocks.length;
            v.lineNames = new String[n];
            v.lines = new String[n][];
            for (int i = 0; i < n; i++) {
                v.lineNames[i] = i < fromNames.length ? fromNames[i] : ("线路" + (i + 1));
                String[] eps = lineBlocks[i].split("#");
                List<String> urls = new ArrayList<>();
                List<String> names = new ArrayList<>();
                for (String ep : eps) {
                    int dollar = ep.indexOf('$');
                    if (dollar < 0) continue;
                    String epName = ep.substring(0, dollar);
                    String epUrl = ep.substring(dollar + 1).trim();
                    if (epUrl.isEmpty()) continue;
                    urls.add(epUrl);
                    names.add(epName);
                }
                v.lines[i] = urls.toArray(new String[0]);
                if (i == 0) v.epNames = names.toArray(new String[0]);
            }
        }
        return v;
    }

    /** 取播放地址: 优先 ffm3u8 线路 (直链 m3u8), 返回 [线路名, 集名, 地址] */
    public static String[] pickUrl(Vod v, int line, int ep) {
        if (v == null || v.lines.length == 0) return null;
        // 线路修正
        int li = line;
        if (li < 0 || li >= v.lines.length) li = 0;
        int ei = ep;
        if (ei < 0 || ei >= v.lines[li].length) ei = 0;
        String epName = ei < v.epNames.length ? v.epNames[ei] : ("第" + (ei + 1) + "集");
        return new String[]{v.lineNames[li], epName, v.lines[li][ei]};
    }
}
