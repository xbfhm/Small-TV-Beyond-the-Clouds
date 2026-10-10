package com.yzb.tv.util;

import android.content.Context;
import android.content.SharedPreferences;

/** 界面模式: 电视端 / 手机端 (首次启动选择, 存本地) */
public class UIMode {
    public static final String TV = "tv";
    public static final String PHONE = "phone";
    private static final String PREFS = "ui_mode";
    private static final String KEY = "mode";

    public static boolean isTV(Context ctx) {
        return TV.equals(get(ctx));
    }

    public static String get(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
    }

    public static void set(Context ctx, String mode) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, mode).apply();
    }

    /** 是否已选择过 (首次启动为 false, 需要弹选择框) */
    public static boolean chosen(Context ctx) {
        return !get(ctx).isEmpty();
    }
}
