package com.yzb.tv.api;

import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * B站 WBI 签名算法 (2023 起 web 接口风控要求)
 * w_rid = md5(排序后的query + mixinKey)
 */
public class WbiSign {

    private static final int[] MIXIN_TAB = {
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
            22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    };

    /** 从 nav 接口的 img_url/sub_url 计算 mixinKey (32位) */
    public static String mixinKey(String imgUrl, String subUrl) {
        String img = basename(imgUrl);
        String sub = basename(subUrl);
        String raw = img + sub;
        StringBuilder sb = new StringBuilder();
        for (int idx : MIXIN_TAB) {
            if (idx < raw.length()) sb.append(raw.charAt(idx));
        }
        return sb.length() >= 32 ? sb.substring(0, 32) : sb.toString();
    }

    private static String basename(String url) {
        if (url == null) return "";
        String s = url;
        int i = s.lastIndexOf('/');
        if (i >= 0) s = s.substring(i + 1);
        int j = s.indexOf('.');
        if (j >= 0) s = s.substring(0, j);
        return s;
    }

    /**
     * 对参数签名。会修改传入 map (补 wts / w_rid), 返回最终 query 串
     */
    public static String signQuery(Map<String, String> params, String mixinKey) {
        params.put("wts", String.valueOf(System.currentTimeMillis() / 1000));
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder query = new StringBuilder();
        for (String k : keys) {
            String v = params.get(k);
            if (v == null) v = "";
            v = v.replaceAll("[!'()*]", "");
            if (query.length() > 0) query.append('&');
            query.append(k).append('=').append(urlEncode(v));
        }
        String wRid = md5(query + mixinKey);
        query.append("&w_rid=").append(wRid);
        return query.toString();
    }

    public static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    public static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
