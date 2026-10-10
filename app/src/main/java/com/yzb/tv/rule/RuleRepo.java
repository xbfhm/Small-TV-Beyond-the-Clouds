package com.yzb.tv.rule;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 内置 Kazumi 规则仓库: 从 assets/rules.json 加载全部规则。
 * 来源: github.com/Predidit/KazumiRules (官方规则仓库)
 */
public class RuleRepo {

    private static final List<KazumiRule> RULES = new ArrayList<>();
    private static boolean loaded = false;

    public static synchronized void init(Context ctx) {
        if (loaded) return;
        loaded = true;
        try {
            java.io.InputStream is = ctx.getAssets().open("rules.json");
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) > 0) bos.write(buf, 0, r);
            is.close();
            String json = new String(bos.toByteArray(), "UTF-8");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) {
                    KazumiRule rule = KazumiRule.fromJson(o);
                    if (!rule.name.isEmpty()) RULES.add(rule);
                }
            }
            android.util.Log.i("RuleRepo", "加载 " + RULES.size() + " 条 Kazumi 规则");
        } catch (Exception e) {
            android.util.Log.i("RuleRepo", "RuleRepo 加载失败: " + e);
        }
    }

    public static List<KazumiRule> getRules() { return RULES; }

    public static KazumiRule get(String name) {
        for (KazumiRule r : RULES) if (r.name.equals(name)) return r;
        return null;
    }
}
