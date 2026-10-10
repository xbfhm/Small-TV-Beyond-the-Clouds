package com.yzb.tv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yzb.tv.R;
import com.yzb.tv.api.AgeApi;
import com.yzb.tv.api.BiliApi;
import com.yzb.tv.model.Anime;
import com.yzb.tv.model.Video;
import com.yzb.tv.util.CookieStore;
import com.yzb.tv.util.UIMode;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 主界面 —— 对标云视听小电视框架:
 * 顶部导航 (首页/番剧/排行/免费动漫) + 搜索 + 消息 + 我的
 * 内容区: 封面网格
 */
public class MainActivity extends AppCompatActivity {

    private EditText etSearch;
    private Button btnHome, btnBangumi, btnRank, btnHistory, btnFree, btnMsg, btnUser;
    private RecyclerView rvList;
    private ProgressBar pbLoading;
    private TextView tvStatus, tvBottom;

    private VideoAdapter adapter;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler ui = new Handler(Looper.getMainLooper());

    private String currentTab = "home";
    private int freshIdx = 1;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        com.yzb.tv.App.bootLog("MAIN-0 MainActivity.onCreate");
        try {
            setContentView(R.layout.activity_main);
        } catch (Throwable t) {
            com.yzb.tv.App.bootLog("MAIN-FAIL setContentView: " + t);
            throw new RuntimeException(t);
        }
        com.yzb.tv.App.bootLog("MAIN-1 layout ok");

        etSearch = findViewById(R.id.etSearch);
        btnHome = findViewById(R.id.btnHome);
        btnBangumi = findViewById(R.id.btnBangumi);
        btnRank = findViewById(R.id.btnRank);
        btnHistory = findViewById(R.id.btnHistory);
        btnFree = findViewById(R.id.btnFree);
        btnMsg = findViewById(R.id.btnMsg);
        btnUser = findViewById(R.id.btnUser);
        rvList = findViewById(R.id.rvList);
        pbLoading = findViewById(R.id.pbLoading);
        tvStatus = findViewById(R.id.tvStatus);
        tvBottom = findViewById(R.id.tvBottom);

        adapter = new VideoAdapter(this::openVideo);
        int span = UIMode.isTV(this) ? 5 : 2;
        rvList.setLayoutManager(new GridLayoutManager(this, span));
        rvList.setAdapter(adapter);

        btnHome.setOnClickListener(v -> switchTab("home"));
        btnBangumi.setOnClickListener(v -> switchTab("bangumi"));
        btnRank.setOnClickListener(v -> switchTab("rank"));
        btnHistory.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        btnFree.setOnClickListener(v -> switchTab("free"));
        btnMsg.setOnClickListener(v -> {
            if (!CookieStore.isLogin()) {
                toast("请先扫码登录");
                startActivity(new Intent(this, LoginActivity.class));
                return;
            }
            startActivity(new Intent(this, MessageActivity.class));
        });
        btnUser.setOnClickListener(v -> {
            if (CookieStore.isLogin()) {
                new AlertDialog.Builder(this)
                        .setTitle(CookieStore.uname)
                        .setMessage("UID: " + CookieStore.mid + "\n\n已登录 B站账号")
                        .setPositiveButton("账号页", (d, w) -> {
                            Intent it = new Intent(this, LoginActivity.class);
                            it.putExtra("logout_mode", true);
                            startActivity(it);
                        })
                        .setNegativeButton("关闭", null)
                        .show();
            } else {
                startActivity(new Intent(this, LoginActivity.class));
            }
        });

        etSearch.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String kw = etSearch.getText().toString().trim();
                if (!kw.isEmpty()) {
                    Intent it = new Intent(this, SearchActivity.class);
                    it.putExtra("keyword", kw);
                    startActivity(it);
                }
                return true;
            }
            return false;
        });

        com.yzb.tv.App.bootLog("MAIN-2 views/adapter ok");
        // 默认加载首页推荐
        switchTab("home");
        com.yzb.tv.App.bootLog("MAIN-3 switchTab done");
    }

    @Override
    protected void onResume() {
        super.onResume();
        btnUser.setText(CookieStore.isLogin() ? CookieStore.uname : "未登录");
    }

    private void switchTab(String tab) {
        currentTab = tab;
        tvBottom.setText("当前: " + tabName(tab));
        showLoading(true);
        pool.execute(() -> {
            try {
                List<Video> list;
                switch (tab) {
                    case "bangumi":
                        list = loadBangumi();
                        break;
                    case "rank":
                        list = loadRanking();
                        break;
                    case "free":
                        list = loadFree();
                        break;
                    default:
                        list = loadRecommend();
                }
                com.yzb.tv.App.bootLog("MAIN-4 tab=" + tab + " 数据加载成功 n=" + list.size());
                ui.post(() -> {
                    adapter.setData(list);
                    showLoading(false);
                    tvStatus.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    tvStatus.setText("暂无内容，试试搜索");
                    tvBottom.setText(tabName(tab) + " · 共 " + list.size() + " 项");
                });
            } catch (Exception ex) {
                com.yzb.tv.App.bootLog("MAIN-FAIL tab=" + tab + ": " + ex);
                ui.post(() -> {
                    showLoading(false);
                    tvStatus.setVisibility(View.VISIBLE);
                    tvStatus.setText("加载失败: " + ex.getMessage());
                });
            }
        });
    }

    // ---------- B站: 首页推荐 ----------
    private List<Video> loadRecommend() throws Exception {
        JSONArray arr = BiliApi.recommend(freshIdx++);
        List<Video> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Video v = Video.fromBili(o);
            if (v != null && !v.bvid.isEmpty()) list.add(v);
        }
        return list;
    }

    // ---------- B站: 番剧 ----------
    private List<Video> loadBangumi() throws Exception {
        JSONObject d = BiliApi.pgcIndex(1, 1);
        JSONArray arr = d.optJSONArray("list");
        List<Video> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Video v = new Video();
            v.type = Video.TYPE_PGC;
            v.seasonId = o.optLong("season_id", 0);
            v.title = o.optString("title", "");
            v.cover = o.optString("cover", "");
            v.play = Video.parseCount(o.optString("order", "0").replace("播放", "").trim());
            v.upName = o.optString("index_show", "番剧");
            JSONObject stat = o.optJSONObject("stat");
            if (stat != null) {
                v.play = stat.optLong("views", v.play);
                v.danmaku = stat.optLong("danmakus", 0);
            }
            if (v.seasonId > 0) list.add(v);
        }
        return list;
    }

    // ---------- B站: 排行榜 ----------
    private List<Video> loadRanking() throws Exception {
        JSONArray arr = BiliApi.ranking("0");
        List<Video> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Video v = Video.fromBili(o);
            if (v != null && !v.bvid.isEmpty()) list.add(v);
        }
        return list;
    }

    // ---------- 免费动漫源 (AGE) ----------
    private List<Video> loadFree() throws Exception {
        List<Anime> animes = AgeApi.home();
        List<Video> list = new ArrayList<>();
        for (Anime a : animes) {
            Video v = new Video();
            v.type = Video.TYPE_FREE;
            v.source = "age";
            v.title = a.name;
            v.cover = a.cover;
            v.upName = a.uptodate.isEmpty() ? "免费动漫源" : a.uptodate;
            v.extra = String.valueOf(a.id);
            list.add(v);
        }
        return list;
    }

    // ---------- 点击视频 ----------
    private void openVideo(Video v) {
        Intent it = new Intent(this, PlayerActivity.class);
        it.putExtra("video", v);
        startActivity(it);
    }

    private void showLoading(boolean show) {
        loading = show;
        pbLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) tvStatus.setVisibility(View.GONE);
    }

    private String tabName(String tab) {
        switch (tab) {
            case "bangumi": return "番剧";
            case "rank": return "排行榜";
            case "free": return "免费动漫";
            default: return "首页推荐";
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
