package com.yzb.tv.model;

/** 番剧条目 (搜索/列表结果) */
public class Anime {
    public int id;
    public String name = "";
    public String premiere = "";
    public String status = "";
    public String uptodate = "";
    public String type = "";
    public String cover = "";    // 封面图 URL
    public String display = "";   // 列表展示文本
    public boolean isResume = false;   // 首页"继续观看"占位条目
    public String url = "";      // kazumi 详情页 URL (仅 kazumi 源)
    public String source = "AGE"; // 来源: "AGE" 或 kazumi 规则名
    public String baseUrl = "";   // kazumi baseURL

    public Anime() {}

    public Anime(int id, String name, String premiere, String status, String uptodate) {
        this.id = id;
        this.name = name;
        this.premiere = premiere;
        this.status = status;
        this.uptodate = uptodate;
        this.display = name + "  (" + premiere + " " + status + ")";
    }
}
