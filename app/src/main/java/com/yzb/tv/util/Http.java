package com.yzb.tv.util;

import android.content.Context;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * OkHttp 统一封装: 桌面 UA + B站 Referer + Cookie 注入 + 超时
 * (Android 5.x 老系统走 OkHttp 自带 TLS, 绕开系统 CA 库过旧问题)
 */
public class Http {

    public static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    public static final String REFERER = "https://www.bilibili.com/";

    public static OkHttpClient client;

    /** 播放器专用: 长超时 */
    public static OkHttpClient playerClient;

    public static void init(Context c) {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build();
        playerClient = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public static Request.Builder base(String url) {
        return new Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Referer", REFERER)
                .header("Cookie", CookieStore.cookieHeader());
    }

    /** GET, 返回响应体字符串 (utf-8) */
    public static String get(String url) throws IOException {
        return get(url, null);
    }

    public static String get(String url, Map<String, String> extraHeaders) throws IOException {
        Request.Builder b = base(url);
        if (extraHeaders != null) {
            for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
                b.header(e.getKey(), e.getValue());
            }
        }
        try (Response resp = client.newCall(b.build()).execute()) {
            ResponseBody body = resp.body();
            return body == null ? "" : body.string();
        }
    }

    /** GET 返回 Response 本体 (扫码登录需要读 Set-Cookie 头) */
    public static Response getResponse(String url) throws IOException {
        return client.newCall(base(url).build()).execute();
    }

    /** GET, 返回原始字节 (弹幕 protobuf) */
    public static byte[] getBytes(String url) throws IOException {
        try (Response resp = client.newCall(base(url).build()).execute()) {
            ResponseBody body = resp.body();
            return body == null ? new byte[0] : body.bytes();
        }
    }

    /** POST form 表单 */
    public static String postForm(String url, Map<String, String> params) throws IOException {
        FormBody.Builder fb = new FormBody.Builder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            fb.add(e.getKey(), e.getValue() == null ? "" : e.getValue());
        }
        Request req = base(url)
                .post(fb.build())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build();
        try (Response resp = client.newCall(req).execute()) {
            ResponseBody body = resp.body();
            return body == null ? "" : body.string();
        }
    }

    /** POST JSON body */
    public static String postJson(String url, String json) throws IOException {
        Request req = base(url)
                .post(okhttp3.RequestBody.create(json,
                        okhttp3.MediaType.parse("application/json; charset=utf-8")))
                .build();
        try (Response resp = client.newCall(req).execute()) {
            ResponseBody body = resp.body();
            return body == null ? "" : body.string();
        }
    }
}
