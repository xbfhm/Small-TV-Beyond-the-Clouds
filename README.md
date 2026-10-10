# 云之彼端小电视 (YzbTV)

对标**云视听小电视**（B站官方 TV 端）框架的第三方追番/视频 App，在其功能基础上新增：

| 功能 | 说明 |
|---|---|
| 🎬 B站官方接口 | 首页推荐 / 排行榜 / 番剧索引 / 搜索(WBI) / 播放 / 弹幕 / 历史 |
| 💬 评论区 (新) | 查看评论、楼中楼、**发表评论**、点赞 |
| 👤 UP主主页 (新) | 头像/签名/粉丝数据 + 关注/取关 + 投稿列表 |
| 📧 私信 (新) | 会话列表、聊天记录、**发私信**、消息中心(回复/@我/赞) |
| 📺 播放增强 | 弹幕渲染、清晰度切换、倍速、集数连播、进度上报 |
| 🆓 免费动漫源 | AGE 动漫 + Kazumi 25 条规则源（搜 B站没版权的番） |
| 📀 非凡影视源 (新) | 苹果CMS 稳定源（用户网站看剧页同款，98497 部，当天更新） |

## 架构（对标云视听）

```
原生 Kotlin/Java (横屏 TV + 手机双适配)
├── 网络: OkHttp (Android 5.x TLS 兼容) + WBI 风控签名
├── 播放: ExoPlayer/Media3 (HLS/DASH/渐进式) + 自绘弹幕层
├── 图片: Glide (带 B站 Referer)
├── 登录: 扫码登录 (二维码轮询 + Set-Cookie 抓凭证)
├── 免费源: AGE 官方API + Kazumi 规则引擎 (jsoup+XPath)
└── 影视源: 苹果CMS 标准接口 (可换源)
```

## 构建

GitHub Actions 云端构建（无电脑也可）：
- push 到 main 自动构建
- APK 发布到 Release `latest`: https://github.com/xbfhm/-/releases/download/latest/app-debug.apk

本地构建: Android Studio / `gradle assembleDebug`

## 免责声明

视频数据来自公开接口（B站官方 / AGE / 非凡资源），仅供学习交流。
