package com.yzb.tv.model;

import java.io.Serializable;

/** 一集 (含线路信息) */
public class Episode implements Serializable {
    private static final long serialVersionUID = 1L;
    public String line;        // 线路 key, 如 ffm3u8
    public String lineLabel;   // 线路显示名, 如 非凡
    public String title;       // 集标题
    public String enc;         // age_xxx 加密串 (仅 AGE 源)
    public String url;         // kazumi 播放页 URL (仅 kazumi 源)
    public boolean vip;        // 是否 VIP 线路
    public String source;      // 来源: "AGE" 或 kazumi 规则名
    public String baseUrl;     // kazumi baseURL (嗅探 Referer 用)

    public Episode() {}

    public Episode(String line, String lineLabel, String title, String enc, boolean vip) {
        this.line = line;
        this.lineLabel = lineLabel;
        this.title = title;
        this.enc = enc;
        this.vip = vip;
    }
}
