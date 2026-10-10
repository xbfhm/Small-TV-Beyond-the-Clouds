package com.yzb.tv.model;

import java.util.ArrayList;
import java.util.List;

/** 番剧详情 */
public class AnimeDetail {
    public int id;
    public String name = "";
    public String nameOther = "";
    public String cover = "";
    public String intro = "";
    public String premiere = "";
    public String status = "";
    public String uptodate = "";
    public String type = "";
    public List<String> tags = new ArrayList<>();
    public List<Episode> episodes = new ArrayList<>();   // 已扁平化的集列表
    public String jxZJ = "https://jx.wuzhoupai.com:8443/m3u8/?url=";
    public String jxVIP = "https://jx.wuzhoupai.com:8443/vip/?url=";
    public String currentLine = "";   // 当前选中线路
    public boolean vipOnly = false;    // 是否仅有VIP源(版权番)
    public String source = "AGE";     // 来源
    public String baseUrl = "";       // kazumi baseURL
    public String detailUrl = "";     // kazumi 详情页 URL
}
