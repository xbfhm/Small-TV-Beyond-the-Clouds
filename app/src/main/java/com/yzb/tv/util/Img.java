package com.yzb.tv.util;

import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;

/** 图片加载 (B站图床需带 Referer) */
public class Img {

    public static void load(ImageView iv, String url) {
        if (url == null || url.isEmpty() || iv == null) return;
        if (url.startsWith("//")) url = "https:" + url;
        try {
            GlideUrl gu = new GlideUrl(url, new LazyHeaders.Builder()
                    .addHeader("Referer", "https://www.bilibili.com/")
                    .addHeader("User-Agent", Http.UA)
                    .build());
            Glide.with(iv.getContext()).load(gu).into(iv);
        } catch (Exception ignored) {
        }
    }

    public static void loadPlain(ImageView iv, String url) {
        if (url == null || url.isEmpty() || iv == null) return;
        try {
            Glide.with(iv.getContext()).load(url).into(iv);
        } catch (Exception ignored) {
        }
    }
}
