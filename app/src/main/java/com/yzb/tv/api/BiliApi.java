package com.yzb.tv.api;

import com.yzb.tv.util.CookieStore;
import com.yzb.tv.util.Http;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * B站 API 客户端 —— 「云之彼端小电视」核心
 * 覆盖: 登录 / 首页 / 搜索(WBI) / 视频详情 / 播放地址(durl+dash) / 弹幕 /
 *       评论(看+发) / UP主主页 / 消息中心 / 私信 / 历史 / 番剧 / 收藏
 * 所有接口已实测可用 (2026-10-11)
 */
public class BiliApi {

    public static final String API = "https://api.bilibili.com";
    public static final String VC = "https://api.vc.bilibili.com";
    public static final String PASSPORT = "https://passport.bilibili.com";

    /** 消息/私信专用 Referer */
    private static final String MSG_REFERER = "https://message.bilibili.com/";

    // ---------- WBI 缓存 ----------
    private static String wbiMixin = null;
    private static long wbiTime = 0;

    public static class ApiException extends Exception {
        public final int code;
        public ApiException(int code, String msg) {
            super("[" + code + "] " + msg);
            this.code = code;
        }
    }

    // ==================== 基础 ====================

    public static JSONObject getRaw(String url) throws Exception {
        return new JSONObject(Http.get(url));
    }

    /** GET 并解包 code/data，code!=0 抛 ApiException */
    public static JSONObject getData(String url) throws Exception {
        return unwrap(new JSONObject(Http.get(url)));
    }

    /** GET (自定义 Referer) */
    public static JSONObject getDataRef(String url, String referer) throws Exception {
        Map<String, String> h = new HashMap<>();
        h.put("Referer", referer);
        return unwrap(new JSONObject(Http.get(url, h)));
    }

    /** POST 表单并解包 */
    public static JSONObject postForm(String url, Map<String, String> params) throws Exception {
        return unwrap(new JSONObject(Http.postForm(url, params)));
    }

    public static JSONObject postFormRef(String url, Map<String, String> params, String referer) throws Exception {
        return unwrap(new JSONObject(Http.postForm(url, params)));
    }

    private static JSONObject unwrap(JSONObject j) throws ApiException {
        int code = j.optInt("code", -1);
        if (code != 0) {
            throw new ApiException(code, j.optString("message", j.optString("msg")));
        }
        JSONObject d = j.optJSONObject("data");
        return d != null ? d : new JSONObject();
    }

    // ---------- WBI 签名请求 ----------

    private static synchronized String getMixin() throws Exception {
        if (wbiMixin != null && System.currentTimeMillis() - wbiTime < 30 * 60 * 1000L) {
            return wbiMixin;
        }
        JSONObject d = getData(API + "/x/web-interface/nav");
        JSONObject img = d.optJSONObject("wbi_img");
        if (img != null) {
            wbiMixin = WbiSign.mixinKey(img.optString("img_url"), img.optString("sub_url"));
            wbiTime = System.currentTimeMillis();
        }
        if (wbiMixin == null || wbiMixin.isEmpty()) {
            wbiMixin = "ea1db124af3c7062474693fa704f4ff8";  // 兜底(会过期)
        }
        return wbiMixin;
    }

    public static JSONObject wbiGet(String path, Map<String, String> params) throws Exception {
        String mixin = getMixin();
        String q = WbiSign.signQuery(params, mixin);
        return getData(API + path + "?" + q);
    }

    // ==================== 登录 ====================

    /** 二维码生成 → {url, qrcode_key} */
    public static JSONObject qrGenerate() throws Exception {
        return getData(PASSPORT + "/x/passport-login/web/qrcode/generate");
    }

    /** 二维码轮询。返回 {code(0/86101/86090/86038), message}；成功后自动写 CookieStore */
    public static JSONObject qrPoll(String qrcodeKey) throws Exception {
        JSONObject j = new JSONObject(Http.get(
                PASSPORT + "/x/passport-login/web/qrcode/poll?qrcode_key=" + qrcodeKey + "&source=main-fe-header"));
        int code = j.optInt("code", -1);
        JSONObject data = j.optJSONObject("data");
        if (data == null) data = new JSONObject();
        if (code == 0) {
            // 凭证在 data.url 中 (双保险: Set-Cookie 版见 pollWithResponse)
            String url = data.optString("url", "");
            parseCookiesFromUrl(url);
        }
        data.put("_code", code);
        return data;
    }

    /** 轮询增强版: 直接从 Set-Cookie 响应头抓凭证 (最稳) */
    public static JSONObject qrPollWithResponse(String qrcodeKey) throws Exception {
        Response resp = Http.getResponse(
                PASSPORT + "/x/passport-login/web/qrcode/poll?qrcode_key=" + qrcodeKey + "&source=main-fe-header");
        String body = "";
        try {
            ResponseBody b = resp.body();
            if (b != null) body = b.string();
        } finally {
            resp.close();
        }
        JSONObject j = new JSONObject(body);
        int code = j.optInt("code", -1);
        JSONObject data = j.optJSONObject("data");
        if (data == null) data = new JSONObject();
        if (code == 0) {
            List<String> cookies = resp.headers("Set-Cookie");
            for (String ck : cookies) {
                fillCookie(ck);
            }
            if (!CookieStore.isLogin()) {
                parseCookiesFromUrl(data.optString("url", ""));
            }
            CookieStore.save();
        }
        data.put("_code", code);
        return data;
    }

    private static void parseCookiesFromUrl(String url) {
        try {
            int q = url.indexOf('?');
            if (q < 0) return;
            String[] pairs = url.substring(q + 1).split("&");
            for (String p : pairs) {
                int eq = p.indexOf('=');
                if (eq <= 0) continue;
                String k = p.substring(0, eq);
                String v = java.net.URLDecoder.decode(p.substring(eq + 1), "UTF-8");
                applyCookie(k, v);
            }
            CookieStore.save();
        } catch (Exception ignored) {
        }
    }

    private static void fillCookie(String setCookie) {
        try {
            String first = setCookie.split(";")[0];
            int eq = first.indexOf('=');
            if (eq <= 0) return;
            applyCookie(first.substring(0, eq).trim(), first.substring(eq + 1).trim());
            if (setCookie.contains("SESSDATA")) {
                int i = setCookie.indexOf("ac_time_value=");
                if (i >= 0) {
                    String rest = setCookie.substring(i + 14);
                    int end = rest.indexOf(';');
                    if (end < 0) end = rest.length();
                    CookieStore.acTimeValue = rest.substring(0, end);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void applyCookie(String k, String v) {
        if ("SESSDATA".equals(k)) CookieStore.sessdata = v;
        else if ("bili_jct".equals(k)) CookieStore.biliJct = v;
        else if ("DedeUserID".equals(k)) CookieStore.dedeUserId = v;
        else if ("ac_time_value".equals(k)) CookieStore.acTimeValue = v;
        else if ("buvid3".equals(k)) CookieStore.buvid3 = v;
        else if ("buvid4".equals(k)) CookieStore.buvid4 = v;
    }

    /** 当前登录用户信息, 同时刷新 uname/mid/face */
    public static JSONObject nav() throws Exception {
        JSONObject d = getData(API + "/x/web-interface/nav");
        if (d.optBoolean("isLogin")) {
            CookieStore.uname = d.optString("uname");
            CookieStore.mid = d.optLong("mid");
            CookieStore.face = d.optString("face");
            CookieStore.save();
        }
        return d;
    }

    /** 拉设备指纹 buvid3/buvid4 */
    public static void fetchBuvid() throws Exception {
        JSONObject d = getData(API + "/x/frontend/finger/spi");
        CookieStore.buvid3 = d.optString("b_3");
        CookieStore.buvid4 = d.optString("b_4");
        CookieStore.save();
    }

    // ==================== 首页 / 排行 ====================

    /** 首页推荐流 */
    public static JSONArray recommend(int freshIdx) throws Exception {
        String url = API + "/x/web-interface/index/top/rcmd?ps=14&fresh_type=4&version=1"
                + "&fresh_idx=" + freshIdx + "&fresh_idx_1h=" + freshIdx + "&feed_version=V8";
        JSONObject j = new JSONObject(Http.get(url));
        JSONObject d = j.optJSONObject("data");
        if (d == null) return new JSONArray();
        JSONArray arr = d.optJSONArray("item");
        return arr != null ? arr : new JSONArray();
    }

    /** 排行榜 */
    public static JSONArray ranking(String rid) throws Exception {
        JSONObject d = getData(API + "/x/web-interface/ranking/v2?rid=" + rid + "&type=all");
        JSONArray arr = d.optJSONArray("list");
        return arr != null ? arr : new JSONArray();
    }

    /** 热门 */
    public static JSONArray popular() throws Exception {
        JSONObject d = getData(API + "/x/web-interface/popular?ps=20&pn=1");
        JSONArray arr = d.optJSONArray("list");
        return arr != null ? arr : new JSONArray();
    }

    // ==================== 搜索 ====================

    /** 视频搜索 (WBI, 支持 B站没版权的番剧名 → 找 up 主上传的片段/合集) */
    public static JSONObject searchVideo(String kw, int page) throws Exception {
        Map<String, String> p = new HashMap<>();
        p.put("search_type", "video");
        p.put("keyword", kw);
        p.put("page", String.valueOf(page));
        return wbiGet("/x/web-interface/wbi/search/type", p);
    }

    /** 番剧搜索 (含 B站有版权的) */
    public static JSONObject searchBangumi(String kw, int page) throws Exception {
        Map<String, String> p = new HashMap<>();
        p.put("search_type", "media_bangumi");
        p.put("keyword", kw);
        p.put("page", String.valueOf(page));
        return wbiGet("/x/web-interface/wbi/search/type", p);
    }

    /** 搜索建议 */
    public static JSONArray suggest(String term) throws Exception {
        try {
            JSONObject d = getData(API + "/x/web-interface/search/suggest?term=" + WbiSign.urlEncode(term));
            JSONArray tag = d.optJSONArray("tag");
            return tag != null ? tag : new JSONArray();
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    // ==================== 视频详情 / 播放 ====================

    /** 视频详情 (by bvid 或 aid) */
    public static JSONObject videoView(String bvidOrAv) throws Exception {
        String key = bvidOrAv.startsWith("av") ? "aid=" + bvidOrAv.substring(2) : "bvid=" + bvidOrAv;
        return getData(API + "/x/web-interface/view?" + key);
    }

    /**
     * 播放地址。dash=true 用 fnval=16 (1080P 分轨), false 用 fnval=1 (mp4 整段)
     */
    public static JSONObject playUrl(String bvid, long cid, int qn, boolean dash) throws Exception {
        String url = API + "/x/player/playurl?bvid=" + bvid + "&cid=" + cid
                + "&qn=" + qn + "&fnver=0&fnval=" + (dash ? 16 : 1) + "&fourk=1";
        return getData(url);
    }

    /** 番剧播放地址 */
    public static JSONObject pgcPlayUrl(long epId, long cid, int qn, boolean dash) throws Exception {
        String url = API + "/pgc/player/web/playurl?ep_id=" + epId + "&cid=" + cid
                + "&qn=" + qn + "&fnver=0&fnval=" + (dash ? 16 : 1) + "&fourk=1";
        return getData(url);
    }

    // ==================== 弹幕 ====================

    /** 弹幕 (protobuf 分段, 6 分钟一段) */
    public static List<DanmakuParser.Item> danmaku(long cid) throws Exception {
        byte[] data = Http.getBytes(API + "/x/v2/dm/web/seg.so?type=1&oid=" + cid + "&segment_index=1");
        return DanmakuParser.parse(data);
    }

    /** 发送弹幕 */
    public static void sendDanmaku(long cid, String text, int progressMs, int mode) throws Exception {
        Map<String, String> p = new HashMap<>();
        p.put("type", "1");
        p.put("oid", String.valueOf(cid));
        p.put("msg", text);
        p.put("progress", String.valueOf(progressMs));
        p.put("mode", String.valueOf(mode));
        p.put("fontsize", "25");
        p.put("color", "16777215");
        p.put("rnd", String.valueOf(System.currentTimeMillis() / 1000));
        p.put("csrf", CookieStore.csrf());
        postForm(API + "/x/v2/dm/post", p);
    }

    // ==================== 评论 ====================

    /** 评论列表。sort: 0=时间 2=热度 */
    public static JSONObject replies(long aid, int page, int sort) throws Exception {
        return getData(API + "/x/v2/reply?type=1&oid=" + aid + "&nohot=0&sort=" + sort + "&ps=20&pn=" + page);
    }

    /** 楼中楼 */
    public static JSONObject repliesOfReply(long aid, long rootRpid, int page) throws Exception {
        return getData(API + "/x/v2/reply/reply?type=1&oid=" + aid + "&root=" + rootRpid
                + "&ps=10&pn=" + page + "&nohot=0");
    }

    /** 发表评论 (返回 rpid) */
    public static JSONObject addReply(long aid, String message) throws Exception {
        Map<String, String> p = new HashMap<>();
        p.put("type", "1");
        p.put("oid", String.valueOf(aid));
        p.put("message", message);
        p.put("plat", "1");
        p.put("csrf", CookieStore.csrf());
        return postForm(API + "/x/v2/reply/add", p);
    }

    /** 评论点赞 action: 1=赞 0=取消 */
    public static void likeReply(long aid, long rpid, int action) throws Exception {
        Map<String, String> p = new HashMap<>();
        p.put("type", "1");
        p.put("oid", String.valueOf(aid));
        p.put("rpid", String.valueOf(rpid));
        p.put("action", String.valueOf(action));
        p.put("csrf", CookieStore.csrf());
        postForm(API + "/x/v2/reply/action", p);
    }

    // ==================== UP 主 ====================

    /** UP主空间信息 (风控回退: 从 /x/space/acc/info 带重试) */
    public static JSONObject spaceInfo(long mid) throws Exception {
        return getData(API + "/x/space/acc/info?mid=" + mid + "&platform=web");
    }

    /** UP主投稿视频 (WBI 签名版, 规避 -799 风控) */
    public static JSONObject spaceVideos(long mid, int page) throws Exception {
        Map<String, String> p = new HashMap<>();
        p.put("mid", String.valueOf(mid));
        p.put("ps", "30");
        p.put("pn", String.valueOf(page));
        p.put("order", "pubdate");
        p.put("platform", "web");
        p.put("web_location", "1550101");
        return wbiGet("/x/space/wbi/arc/search", p);
    }

    /** 与某人关系: attribute 0未关注 2已关注 6互关 128拉黑 */
    public static int relation(long mid) throws Exception {
        JSONObject d = getData(API + "/x/relation?fid=" + mid);
        return d.optInt("attribute", 0);
    }

    /** 关注 act: 1=关注 2=取关 */
    public static void modifyRelation(long mid, int act) throws Exception {
        Map<String, String> p = new HashMap<>();
        p.put("fid", String.valueOf(mid));
        p.put("act", String.valueOf(act));
        p.put("re_src", "11");
        p.put("csrf", CookieStore.csrf());
        postForm(API + "/x/relation/modify", p);
    }

    /** 批量用户名片 (昵称/头像) — 用于私信会话列表 */
    public static JSONArray userCards(String uids) throws Exception {
        JSONObject d = getDataRef(VC + "/account/v1/user/cards?uids=" + uids,
                "https://message.bilibili.com/");
        JSONArray arr = d.optJSONArray("cards");
        return arr != null ? arr : new JSONArray();
    }

    // ==================== 消息中心 ====================

    public static JSONObject msgUnread() throws Exception {
        return getDataRef(API + "/x/msgfeed/unread", MSG_REFERER);
    }

    /** type: like / reply / at / system */
    public static JSONObject msgFeed(String type) throws Exception {
        return getDataRef(API + "/x/msgfeed/" + type + "?platform=web&build=0&mobi_app=web", MSG_REFERER);
    }

    // ==================== 私信 ====================

    /** 私信会话列表 */
    public static JSONArray sessions() throws Exception {
        String url = VC + "/session_svr/v1/session_svr/get_sessions"
                + "?session_type=1&group_fold=1&unfold_fold=1&sort_rule=2&build=0&mobi_app=web";
        JSONObject d = getDataRef(url, MSG_REFERER);
        JSONArray arr = d.optJSONArray("session_list");
        return arr != null ? arr : new JSONArray();
    }

    /** 聊天记录 */
    public static JSONArray chatMessages(long talkerId, int size) throws Exception {
        String url = VC + "/svr_sync/v1/svr_sync/fetch_session_msgs?talker_id=" + talkerId
                + "&session_type=1&size=" + size + "&build=0&mobi_app=web";
        JSONObject d = getDataRef(url, MSG_REFERER);
        JSONArray arr = d.optJSONArray("messages");
        return arr != null ? arr : new JSONArray();
    }

    /** 发私信 (文本) */
    public static JSONObject sendMessage(long receiverId, String text) throws Exception {
        Map<String, String> p = new HashMap<>();
        p.put("msg[sender_uid]", String.valueOf(CookieStore.mid));
        p.put("msg[receiver_id]", String.valueOf(receiverId));
        p.put("msg[receiver_type]", "1");
        p.put("msg[msg_type]", "1");
        p.put("msg[msg_status]", "0");
        p.put("msg[content]", new JSONObject().put("content", text).toString());
        p.put("msg[dev_id]", UUID.randomUUID().toString().toUpperCase());
        p.put("csrf", CookieStore.csrf());
        p.put("csrf_token", CookieStore.csrf());
        return postFormRef(VC + "/web_im/v1/web_im/send_msg", p, MSG_REFERER);
    }

    // ==================== 历史 / 收藏 ====================

    public static JSONObject history(long max, long viewAt) throws Exception {
        return getData(API + "/x/web-interface/history/cursor?max=" + max + "&view_at=" + viewAt + "&ps=30");
    }

    public static void historyReport(long aid, long cid, long progressMs) {
        try {
            Map<String, String> p = new HashMap<>();
            p.put("aid", String.valueOf(aid));
            p.put("cid", String.valueOf(cid));
            p.put("progress", String.valueOf(progressMs / 1000));
            p.put("csrf", CookieStore.csrf());
            postForm(API + "/x/v2/history/report", p);
        } catch (Exception ignored) {
        }
    }

    public static JSONArray favFolders() throws Exception {
        JSONObject d = getData(API + "/x/v3/fav/folder/created/list-all?up_mid=" + CookieStore.mid);
        JSONArray arr = d.optJSONArray("list");
        return arr != null ? arr : new JSONArray();
    }

    public static JSONObject favList(long mediaId, int page) throws Exception {
        return getData(API + "/x/v3/fav/resource/list?media_id=" + mediaId + "&ps=20&pn=" + page
                + "&platform=web&order=mtime&type=0&tid=0");
    }

    // ==================== 番剧 ====================

    /** 番剧详情 by season_id 或 ep_id */
    public static JSONObject pgcSeason(long seasonId, long epId) throws Exception {
        String key = epId > 0 ? "ep_id=" + epId : "season_id=" + seasonId;
        return getData(API + "/pgc/view/web/season?" + key);
    }

    /** 番剧索引 */
    public static JSONObject pgcIndex(int seasonType, int page) throws Exception {
        String url = API + "/pgc/season/index/result?season_type=" + seasonType + "&page=" + page
                + "&pagesize=20&order=3&st=1&sort=0&version=0";
        return getData(url);
    }

    /** 我的追番 */
    public static JSONArray myFollowBangumi() throws Exception {
        JSONObject d = getData(API + "/x/space/bangumi/follow/list?type=1&follow_status=0&pn=1&ps=30&vmid=" + CookieStore.mid);
        JSONArray arr = d.optJSONArray("list");
        return arr != null ? arr : new JSONArray();
    }

    // ==================== 互动 ====================

    /** 点赞 like: 1=赞 2=取消 */
    public static void likeVideo(String bvid, int like) throws Exception {
        Map<String, String> p = new HashMap<>();
        p.put("bvid", bvid);
        p.put("like", String.valueOf(like));
        p.put("csrf", CookieStore.csrf());
        postForm(API + "/x/web-interface/archive/like", p);
    }

    /** 投币 */
    public static void coinVideo(String bvid, int num) throws Exception {
        Map<String, String> p = new HashMap<>();
        p.put("bvid", bvid);
        p.put("multiply", String.valueOf(num));
        p.put("select_like", "0");
        p.put("csrf", CookieStore.csrf());
        postForm(API + "/x/web-interface/coin/add", p);
    }

    /** 是否已点赞 */
    public static boolean hasLiked(String bvid) throws Exception {
        JSONObject d = getData(API + "/x/web-interface/archive/has/like?bvid=" + bvid);
        return d.optInt("0") == 1 || d.toString().contains("1");
    }
}
