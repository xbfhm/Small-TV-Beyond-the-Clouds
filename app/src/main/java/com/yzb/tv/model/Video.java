package com.yzb.tv.model;

import org.json.JSONObject;

import java.io.Serializable;

/**
 * 统一视频卡片模型 —— 兼容三种来源:
 * 1. B站推荐流 / 搜索结果 / UP主投稿 (UGC)
 * 2. B站番剧 (PGC, season_id/ep_id)
 * 3. 免费动漫源 (AGE / Kazumi 规则, source 字段区分)
 */
public class Video implements Serializable {

    public String bvid = "";
    public long aid = 0;
    public long cid = 0;
    public long seasonId = 0;
    public long epId = 0;
    public String title = "";
    public String cover = "";
    public String upName = "";
    public long upMid = 0;
    public String upFace = "";
    public long play = 0;
    public long danmaku = 0;
    public long durationSec = 0;   // 秒
    public long pubdate = 0;
    public int type = TYPE_UGC;    // 见常量
    public String source = "bili"; // bili / age / 规则名
    public String extra = "";      // 免费源详情页 url / 规则来源标记

    public static final int TYPE_UGC = 1;
    public static final int TYPE_PGC = 2;
    public static final int TYPE_FREE = 3;

    public boolean isPgc() { return type == TYPE_PGC; }

    /** B站接口 json → Video (兼容 rcmd item / 搜索结果 / view 接口) */
    public static Video fromBili(JSONObject o) {
        if (o == null) return null;
        Video v = new Video();
        v.bvid = o.optString("bvid", "");
        v.aid = o.optLong("id", o.optLong("aid", 0));
        v.cid = o.optLong("cid", 0);
        v.title = stripEm(o.optString("title", ""));
        v.cover = o.optString("pic", o.optString("cover", ""));
        if (v.cover.startsWith("//")) v.cover = "https:" + v.cover;

        JSONObject owner = o.optJSONObject("owner");
        if (owner != null) {
            v.upName = owner.optString("name", "");
            v.upMid = owner.optLong("mid", 0);
            v.upFace = owner.optString("face", "");
        } else {
            v.upName = o.optString("author", o.optString("name", ""));
            v.upMid = o.optLong("mid", o.optLong("uid", 0));
        }

        JSONObject stat = o.optJSONObject("stat");
        if (stat != null) {
            v.play = stat.optLong("view", 0);
            v.danmaku = stat.optLong("danmaku", 0);
        } else {
            v.play = parseCount(o.optString("play", "0"));
            v.danmaku = parseCount(o.optString("danmaku", "0"));
        }
        v.durationSec = o.optLong("duration", 0);
        Object dur = o.opt("duration");
        if (dur instanceof String) {
            v.durationSec = parseDuration((String) dur);
        }
        v.pubdate = o.optLong("pubdate", 0);

        // 搜索结果 play 是字符串 "12.3万"
        if (v.play == 0 && o.has("play")) v.play = parseCount(String.valueOf(o.opt("play")));

        if (o.has("season_id") || o.has("ep_id") || "pgc".equals(o.optString("goto"))) {
            v.type = TYPE_PGC;
            v.seasonId = o.optLong("season_id", 0);
            v.epId = o.optLong("ep_id", 0);
        }
        return v;
    }

    public String durationText() {
        if (durationSec <= 0) return "";
        long m = durationSec / 60, s = durationSec % 60;
        return m + ":" + (s < 10 ? "0" + s : s);
    }

    public String playText() {
        return countText(play);
    }

    public String danmakuText() {
        return countText(danmaku) + "弹幕";
    }

    public static String countText(long n) {
        if (n >= 100000000) return String.format("%.1f亿", n / 100000000.0);
        if (n >= 10000) return String.format("%.1f万", n / 10000.0);
        return String.valueOf(n);
    }

    /** "12.3万" / "1234" → 数字 */
    public static long parseCount(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            if (s.endsWith("万")) return (long) (Double.parseDouble(s.substring(0, s.length() - 1)) * 10000);
            if (s.endsWith("亿")) return (long) (Double.parseDouble(s.substring(0, s.length() - 1)) * 100000000);
            return Long.parseLong(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    /** "12:34" / "1:02:33" → 秒 */
    public static long parseDuration(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            String[] parts = s.split(":");
            long sec = 0;
            for (String p : parts) {
                sec = sec * 60 + Long.parseLong(p.trim());
            }
            return sec;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 去掉搜索结果里的 <em class="keyword"> 高亮标签 */
    public static String stripEm(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]+>", "");
    }
}
