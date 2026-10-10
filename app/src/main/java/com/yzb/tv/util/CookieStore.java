package com.yzb.tv.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 登录态持久化 (SESSDATA / bili_jct / DedeUserID / buvid 设备指纹)
 */
public class CookieStore {

    private static SharedPreferences sp;

    public static String sessdata = "";
    public static String biliJct = "";
    public static String dedeUserId = "";
    public static String buvid3 = "";
    public static String buvid4 = "";
    public static String acTimeValue = "";
    public static String uname = "";
    public static long mid = 0;
    public static String face = "";

    public static void init(Context c) {
        sp = c.getSharedPreferences("yzb_cookie", Context.MODE_PRIVATE);
        load();
    }

    public static void load() {
        sessdata = sp.getString("sessdata", "");
        biliJct = sp.getString("bili_jct", "");
        dedeUserId = sp.getString("dedeUserId", "");
        buvid3 = sp.getString("buvid3", "");
        buvid4 = sp.getString("buvid4", "");
        acTimeValue = sp.getString("acTimeValue", "");
        uname = sp.getString("uname", "");
        mid = sp.getLong("mid", 0);
        face = sp.getString("face", "");
    }

    public static void save() {
        sp.edit()
                .putString("sessdata", sessdata)
                .putString("bili_jct", biliJct)
                .putString("dedeUserId", dedeUserId)
                .putString("buvid3", buvid3)
                .putString("buvid4", buvid4)
                .putString("acTimeValue", acTimeValue)
                .putString("uname", uname)
                .putLong("mid", mid)
                .putString("face", face)
                .apply();
    }

    public static void logout() {
        sessdata = ""; biliJct = ""; dedeUserId = "";
        uname = ""; mid = 0; face = "";
        save();
    }

    public static boolean isLogin() {
        return sessdata != null && sessdata.length() > 10;
    }

    /** 发给 api.bilibili.com 的 Cookie 头 */
    public static String cookieHeader() {
        StringBuilder sb = new StringBuilder();
        if (!sessdata.isEmpty()) sb.append("SESSDATA=").append(sessdata).append("; ");
        if (!biliJct.isEmpty()) sb.append("bili_jct=").append(biliJct).append("; ");
        if (!dedeUserId.isEmpty()) sb.append("DedeUserID=").append(dedeUserId).append("; ");
        if (!buvid3.isEmpty()) sb.append("buvid3=").append(buvid3).append("; ");
        if (!buvid4.isEmpty()) sb.append("buvid4=").append(buvid4).append("; ");
        return sb.toString();
    }

    /** 发表评论/私信时的 CSRF token */
    public static String csrf() {
        return biliJct == null ? "" : biliJct;
    }
}
