package com.yzb.tv.ui;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import com.yzb.tv.R;
import com.yzb.tv.api.AgeApi;
import com.yzb.tv.api.BiliApi;
import com.yzb.tv.api.DanmakuParser;
import com.yzb.tv.api.MaccmsApi;
import com.yzb.tv.model.AnimeDetail;
import com.yzb.tv.model.Episode;
import com.yzb.tv.model.Video;
import com.yzb.tv.util.CookieStore;
import com.yzb.tv.util.Http;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 统一播放器 —— 对标云视听小电视播放页
 * 支持: B站视频(durl直链) / B站番剧 / AGE免费动漫 / 非凡影视(m3u8稳定源)
 * 功能: 弹幕(B站) / 评论入口 / 清晰度循环 / 集数切换 / 进度上报 / 遥控器控制
 */
public class PlayerActivity extends AppCompatActivity {

    private PlayerView playerView;
    private DanmakuView danmakuView;
    private ProgressBar pbPlayer;
    private TextView tvCenterPlay, tvNowPlaying, tvProgress, tvTotal;
    private SeekBar seekBar;
    private View topBar, controlBar;
    private Button btnPause, btnPrevEp, btnNextEp, btnQuality, btnSpeed, btnDmSwitch, btnComment;

    private ExoPlayer player;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Handler ticker = new Handler(Looper.getMainLooper());

    // ---------- 播放数据 ----------
    private Video video;
    private String currentUrl = "";
    private boolean controlVisible = true;
    private int qnIndex = 0;
    private static final int[] QN = {64, 32, 16, 80, 112};
    private float speed = 1.0f;
    private static final float[] SPEEDS = {1.0f, 1.25f, 1.5f, 2.0f, 0.75f, 0.5f};
    private int speedIndex = 0;

    // ---------- B站 ----------
    private JSONObject viewInfo;      // view 接口数据 (aid/cid/owner)
    private JSONArray pgcEpisodes;    // 番剧集列表
    private int pgcIndex = 0;
    private long currentAid = 0, currentCid = 0;

    // ---------- AGE ----------
    private AnimeDetail ageDetail;
    private List<Episode> ageEps = new ArrayList<>();
    private int ageIndex = 0;
    private String ageLine = "";

    // ---------- 非凡 ----------
    private MaccmsApi.Vod macVod;
    private int macLine = 0, macEp = 0;

    // ---------- 弹幕 ----------
    private final List<DanmakuParser.Item> dmList = new ArrayList<>();
    private int dmIndex = 0;
    private boolean dmOn = true;

    // ---------- 进度上报 ----------
    private long lastReportMs = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        video = (Video) getIntent().getSerializableExtra("video");
        if (video == null) {
            finish();
            return;
        }

        bindViews();
        initPlayer();
        playCurrent();
    }

    private void bindViews() {
        playerView = findViewById(R.id.playerView);
        danmakuView = findViewById(R.id.danmakuView);
        pbPlayer = findViewById(R.id.pbPlayer);
        tvCenterPlay = findViewById(R.id.tvCenterPlay);
        tvNowPlaying = findViewById(R.id.tvNowPlaying);
        tvProgress = findViewById(R.id.tvProgress);
        tvTotal = findViewById(R.id.tvTotal);
        seekBar = findViewById(R.id.seekBar);
        topBar = findViewById(R.id.topBar);
        controlBar = findViewById(R.id.controlBar);
        btnPause = findViewById(R.id.btnPause);
        btnPrevEp = findViewById(R.id.btnPrevEp);
        btnNextEp = findViewById(R.id.btnNextEp);
        btnQuality = findViewById(R.id.btnQuality);
        btnSpeed = findViewById(R.id.btnSpeed);
        btnDmSwitch = findViewById(R.id.btnDmSwitch);
        btnComment = findViewById(R.id.btnComment);

        findViewById(R.id.btnTopBack).setOnClickListener(v -> finish());
        btnPause.setOnClickListener(v -> togglePlay());
        btnPrevEp.setOnClickListener(v -> switchEp(-1));
        btnNextEp.setOnClickListener(v -> switchEp(1));
        btnQuality.setOnClickListener(v -> cycleQuality());
        btnSpeed.setOnClickListener(v -> cycleSpeed());
        btnDmSwitch.setOnClickListener(v -> {
            dmOn = !dmOn;
            btnDmSwitch.setText(dmOn ? "弹幕开" : "弹幕关");
            if (!dmOn) danmakuView.clearAll();
        });
        btnComment.setOnClickListener(v -> openReply());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            boolean touching;
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser && player != null) {
                    long dur = player.getDuration();
                    player.seekTo(dur * p / 1000L);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { touching = true; }
            @Override public void onStopTrackingTouch(SeekBar sb) { touching = false; }
        });

        playerView.setOnClickListener(v -> toggleControl());
    }

    private void initPlayer() {
        player = new ExoPlayer.Builder(this).build();
        player.setPlayWhenReady(true);
        playerView.setPlayer(player);
        playerView.setUseController(false);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    pbPlayer.setVisibility(View.GONE);
                    long dur = player.getDuration();
                    tvTotal.setText(fmt(dur));
                    ui.post(() -> tvCenterPlay.setVisibility(View.GONE));
                } else if (state == Player.STATE_BUFFERING) {
                    pbPlayer.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                ui.post(() -> {
                    pbPlayer.setVisibility(View.GONE);
                    Toast.makeText(PlayerActivity.this, "播放失败: " + error.getErrorCodeName(), Toast.LENGTH_LONG).show();
                    switchEp(1);  // 失败自动跳下一集
                });
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                ui.post(() -> {
                    btnPause.setText(isPlaying ? "暂停" : "播放");
                    tvCenterPlay.setVisibility(isPlaying ? View.GONE : View.VISIBLE);
                });
            }
        });

        // 定时刷新进度 + 发射弹幕
        ticker.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (player != null && !isFinishing()) {
                    long pos = player.getCurrentPosition();
                    long dur = player.getDuration();
                    if (dur > 0) {
                        seekBar.setProgress((int) (pos * 1000L / dur));
                        tvProgress.setText(fmt(pos));
                    }
                    // 发射弹幕
                    if (dmOn && player.isPlaying()) {
                        while (dmIndex < dmList.size() && dmList.get(dmIndex).progressMs <= pos) {
                            DanmakuParser.Item it = dmList.get(dmIndex++);
                            danmakuView.add(it.content, it.color);
                        }
                    }
                    // 进度上报 (30秒一次)
                    if (CookieStore.isLogin() && currentAid > 0 && pos - lastReportMs > 30000) {
                        lastReportMs = pos;
                        pool.execute(() -> BiliApi.historyReport(currentAid, currentCid, pos));
                    }
                }
                ticker.postDelayed(this, 250);
            }
        }, 250);
    }

    // ==================== 播放调度 ====================

    private void playCurrent() {
        tvNowPlaying.setText(video.title + "  加载中...");
        pbPlayer.setVisibility(View.VISIBLE);
        danmakuView.clearAll();
        dmList.clear();
        dmIndex = 0;
        pool.execute(() -> {
            try {
                switch (video.type) {
                    case Video.TYPE_PGC:
                        resolvePgc();
                        break;
                    case Video.TYPE_FREE:
                        if ("mac".equals(video.source)) resolveMac();
                        else resolveAge();
                        break;
                    default:
                        resolveBiliUgc();
                }
            } catch (Exception e) {
                ui.post(() -> {
                    pbPlayer.setVisibility(View.GONE);
                    Toast.makeText(this, "解析失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ---------- B站 UGC ----------
    private void resolveBiliUgc() throws Exception {
        viewInfo = BiliApi.videoView(video.bvid);
        currentAid = viewInfo.optLong("aid");
        currentCid = viewInfo.optLong("cid");
        String url = pickDurl(BiliApi.playUrl(video.bvid, currentCid, QN[qnIndex], false));
        loadDanmaku(currentCid);
        ui.post(() -> startPlay(url, "B站"));
    }

    // ---------- B站番剧 ----------
    private void resolvePgc() throws Exception {
        JSONObject season = BiliApi.pgcSeason(video.seasonId, video.epId > 0 ? video.epId : 0);
        pgcEpisodes = season.optJSONArray("episodes");
        if (pgcEpisodes == null || pgcEpisodes.length() == 0) throw new Exception("没有集数数据");
        loadPgcEp(pgcIndex);
    }

    private void loadPgcEp(int i) throws Exception {
        if (pgcEpisodes == null || i < 0 || i >= pgcEpisodes.length()) return;
        pgcIndex = i;
        JSONObject ep = pgcEpisodes.optJSONObject(i);
        long epId = ep.optLong("id");
        long cid = ep.optLong("cid");
        currentCid = cid;
        currentAid = ep.optLong("aid", 0);
        String url = pickDurl(BiliApi.pgcPlayUrl(epId, cid, QN[qnIndex], false));
        loadDanmaku(cid);
        String title = ep.optString("long_title", ep.optString("title", video.title));
        ui.post(() -> {
            tvNowPlaying.setText(video.title + " " + title + "  (第" + (i + 1) + "集/" + pgcEpisodes.length() + ")");
            startPlay(url, "番剧");
        });
    }

    private String pickDurl(JSONObject d) throws Exception {
        JSONArray durl = d.optJSONArray("durl");
        if (durl != null && durl.length() > 0) {
            JSONObject first = durl.optJSONObject(0);
            String u = first.optString("url");
            if (!u.isEmpty()) return u;
        }
        // 无 durl (需登录/会员) → 试 dash
        JSONObject dash = d.optJSONObject("dash");
        if (dash != null) {
            JSONArray videoArr = dash.optJSONArray("video");
            if (videoArr != null && videoArr.length() > 0) {
                return videoArr.optJSONObject(0).optString("baseUrl");
            }
        }
        throw new Exception("无可用清晰度(需大会员?)");
    }

    private void loadDanmaku(long cid) {
        pool.execute(() -> {
            try {
                List<DanmakuParser.Item> list = BiliApi.danmaku(cid);
                synchronized (dmList) {
                    dmList.clear();
                    dmList.addAll(list);
                    dmList.sort((a, b) -> Integer.compare(a.progressMs, b.progressMs));
                }
                dmIndex = 0;
            } catch (Exception ignored) {
            }
        });
    }

    // ---------- AGE ----------
    private void resolveAge() throws Exception {
        int animeId = Integer.parseInt(video.extra);
        ageDetail = AgeApi.detail(animeId);
        ageEps = new ArrayList<>(ageDetail.episodes);
        if (ageEps.isEmpty()) throw new Exception("该动漫暂无可用集数");
        loadAgeEp(ageIndex);
    }

    private void loadAgeEp(int i) {
        if (i < 0 || i >= ageEps.size()) return;
        ageIndex = i;
        Episode target = ageEps.get(i);
        ui.post(() -> tvNowPlaying.setText(ageDetail.name + " " + target.title + "  解析中..."));
        pool.execute(() -> {
            try {
                String u = AgeApi.resolve(target.enc, target.vip, ageDetail.jxZJ, ageDetail.jxVIP);
                if (u == null || u.isEmpty()) throw new Exception("解析为空");
                ui.post(() -> {
                    tvNowPlaying.setText(ageDetail.name + " " + target.title + " [" + target.lineLabel + "]");
                    startPlay(u, "免费");
                });
            } catch (Exception e) {
                ui.post(() -> Toast.makeText(this, "该集解析失败: " + e.getMessage() + "，试下一集", Toast.LENGTH_SHORT).show());
            }
        });
    }

    // ---------- 非凡影视 ----------
    private void resolveMac() throws Exception {
        macVod = MaccmsApi.detail(0, Long.parseLong(video.extra));
        if (macVod.lineCount() == 0) throw new Exception("暂无播放线路");
        // 优先 ffm3u8 (直链 m3u8)
        for (int i = 0; i < macVod.lineNames.length; i++) {
            if (macVod.lineNames[i].toLowerCase().contains("ffm3u8")) {
                macLine = i;
                break;
            }
        }
        loadMacEp(macEp);
    }

    private void loadMacEp(int i) {
        String[] r = MaccmsApi.pickUrl(macVod, macLine, i);
        if (r == null) {
            Toast.makeText(this, "无播放地址", Toast.LENGTH_SHORT).show();
            return;
        }
        macEp = i;
        ui.post(() -> {
            tvNowPlaying.setText(macVod.name + " " + r[1] + " [" + r[0] + "]");
            startPlay(r[2], "影视");
        });
    }

    // ==================== 播放器控制 ====================

    private void startPlay(String url, String sourceLabel) {
        if (player == null) return;
        currentUrl = url;
        // 构造带 UA/Referer 的数据源
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Http.UA);
        if ("B站".equals(sourceLabel) || "番剧".equals(sourceLabel)) {
            headers.put("Referer", "https://www.bilibili.com/");
        }
        DefaultHttpDataSource.Factory httpF = new DefaultHttpDataSource.Factory()
                .setUserAgent(Http.UA)
                .setDefaultRequestProperties(headers)
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(30000);
        DefaultDataSource.Factory dsF = new DefaultDataSource.Factory(this, httpF);
        DefaultMediaSourceFactory msF = new DefaultMediaSourceFactory(dsF);
        try {
            player.setMediaSource(msF.createMediaSource(MediaItem.fromUri(url)));
            player.setPlayWhenReady(true);
            player.prepare();
        } catch (Exception e) {
            Toast.makeText(this, "播放器错误: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        tvCenterPlay.setVisibility(View.GONE);
    }

    private void togglePlay() {
        if (player == null) return;
        if (player.isPlaying()) player.pause();
        else player.play();
    }

    private void seekBy(long sec) {
        if (player == null) return;
        long pos = player.getCurrentPosition() + sec * 1000;
        pos = Math.max(0, Math.min(pos, player.getDuration()));
        player.seekTo(pos);
        tvProgress.setText(fmt(pos));
    }

    private void cycleQuality() {
        qnIndex = (qnIndex + 1) % QN.length;
        Toast.makeText(this, "切换到清晰度档位 #" + (qnIndex + 1), Toast.LENGTH_SHORT).show();
        if (video != null && (video.type == Video.TYPE_UGC || video.type == Video.TYPE_PGC)) {
            playCurrent();
        }
    }

    private void cycleSpeed() {
        speedIndex = (speedIndex + 1) % SPEEDS.length;
        speed = SPEEDS[speedIndex];
        if (player != null) player.setPlaybackSpeed(speed);
        btnSpeed.setText("倍速 " + speed + "x");
    }

    private void switchEp(int dir) {
        switch (video.type) {
            case Video.TYPE_PGC:
                if (pgcEpisodes != null) {
                    int n = pgcEpisodes.length();
                    if (pgcIndex + dir < 0 || pgcIndex + dir >= n) {
                        Toast.makeText(this, dir > 0 ? "已经是最后一集" : "已经是第一集", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    pool.execute(() -> {
                        try {
                            loadPgcEp(pgcIndex + dir);
                        } catch (Exception e) {
                            ui.post(() -> Toast.makeText(this, "切集失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        }
                    });
                }
                break;
            case Video.TYPE_FREE:
                if ("mac".equals(video.source)) {
                    if (macVod == null) return;
                    if (macEp + dir < 0 || macEp + dir >= macVod.epCount(macLine)) {
                        Toast.makeText(this, dir > 0 ? "已经是最后一集" : "已经是第一集", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    loadMacEp(macEp + dir);
                } else {
                    if (ageEps.isEmpty()) return;
                    if (ageIndex + dir < 0 || ageIndex + dir >= ageEps.size()) {
                        Toast.makeText(this, dir > 0 ? "已经是最后一集" : "已经是第一集", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    loadAgeEp(ageIndex + dir);
                }
                break;
            default:
                Toast.makeText(this, "单视频无集数", Toast.LENGTH_SHORT).show();
        }
    }

    private void openReply() {
        if (currentAid <= 0 && viewInfo != null) currentAid = viewInfo.optLong("aid");
        if (currentAid <= 0) {
            Toast.makeText(this, "免费源暂无评论区", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent it = new Intent(this, ReplyActivity.class);
        it.putExtra("aid", currentAid);
        it.putExtra("bvid", video.bvid);
        it.putExtra("title", video.title);
        startActivity(it);
    }

    // ==================== 遥控器 ====================

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_SPACE:
                togglePlay();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                seekBy(-10);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                seekBy(10);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                adjustVolume(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                adjustVolume(-1);
                return true;
            case KeyEvent.KEYCODE_MENU:
                toggleControl();
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (controlVisible) {
                    showControl(false);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                togglePlay();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (player != null) player.play();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (player != null) player.pause();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void adjustVolume(int dir) {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(0, Math.min(max, cur + dir)), 0);
        Toast.makeText(this, "音量 " + (cur + dir) + "/" + max, Toast.LENGTH_SHORT).show();
    }

    private void toggleControl() {
        showControl(!controlVisible);
    }

    private void showControl(boolean show) {
        controlVisible = show;
        topBar.setVisibility(show ? View.VISIBLE : View.GONE);
        controlBar.setVisibility(show ? View.VISIBLE : View.GONE);
        // 全屏沉浸
        getWindow().getDecorView().setSystemUiVisibility(show ? 0
                : View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private static String fmt(long ms) {
        long s = ms / 1000;
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        ticker.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
