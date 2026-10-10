package com.yzb.tv;

import android.app.Application;

import com.yzb.tv.api.AgeApi;
import com.yzb.tv.rule.RuleRepo;
import com.yzb.tv.util.CookieStore;
import com.yzb.tv.util.Http;

/**
 * 云之彼端小电视 - Application
 * 对标云视听小电视框架: B站官方接口 + 免费动漫源 + 非凡影视稳定源 + 评论/私信增强
 */
public class App extends Application {

    public static App app;

    @Override
    public void onCreate() {
        super.onCreate();
        app = this;
        CookieStore.init(this);
        Http.init(this);
        AgeApi.init(this);       // 免费动漫源 (AGE) 磁盘缓存
        RuleRepo.init(this);     // Kazumi 规则引擎 (25 条备用免费源)
        // 后台预热 AGE 主站 cookie (规避首页间歇性 403)
        new Thread(AgeApi::warmUp, "age-warmup").start();
    }
}
