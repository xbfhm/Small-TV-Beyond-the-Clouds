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

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yzb.tv.R;
import com.yzb.tv.api.AgeApi;
import com.yzb.tv.api.BiliApi;
import com.yzb.tv.model.Anime;
import com.yzb.tv.model.Video;
import com.yzb.tv.util.UIMode;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 搜索页 —— 三通道:
 * 1. B站视频 (WBI 搜索)
 * 2. B站番剧
 * 3. 免费源 (AGE动漫 / Kazumi 规则) → 可搜 B站没版权的动漫, 免费播放
 */
public class SearchActivity extends AppCompatActivity {

    private EditText etKeyword;
    private Button btnBack, btnSearch, btnTabBili, btnTabBangumi, btnTabFree;
    private RecyclerView rvResult;
    private ProgressBar pbSearch;
    private TextView tvSearchStatus;

    private VideoAdapter adapter;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler ui = new Handler(Looper.getMainLooper());

    private String tab = "bili";
    private String lastKeyword = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        etKeyword = findViewById(R.id.etKeyword);
        btnBack = findViewById(R.id.btnBack);
        btnSearch = findViewById(R.id.btnSearch);
        btnTabBili = findViewById(R.id.btnTabBili);
        btnTabBangumi = findViewById(R.id.btnTabBangumi);
        btnTabFree = findViewById(R.id.btnTabFree);
        rvResult = findViewById(R.id.rvResult);
        pbSearch = findViewById(R.id.pbSearch);
        tvSearchStatus = findViewById(R.id.tvSearchStatus);

        adapter = new VideoAdapter(this::openVideo);
        int span = UIMode.isTV(this) ? 5 : 2;
        rvResult.setLayoutManager(new GridLayoutManager(this, span));
        rvResult.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        btnSearch.setOnClickListener(v -> doSearch(currentKeyword()));
        btnTabBili.setOnClickListener(v -> switchTab("bili"));
        btnTabBangumi.setOnClickListener(v -> switchTab("bangumi"));
        btnTabFree.setOnClickListener(v -> switchTab("free"));

        etKeyword.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                doSearch(currentKeyword());
                return true;
            }
            return false;
        });

        String kw = getIntent().getStringExtra("keyword");
        if (kw != null && !kw.isEmpty()) {
            etKeyword.setText(kw);
            etKeyword.setSelection(kw.length());
            doSearch(kw);
        }
        updateTabUi();
    }

    private String currentKeyword() {
        return etKeyword.getText().toString().trim();
    }

    private void switchTab(String t) {
        tab = t;
        updateTabUi();
        String kw = currentKeyword();
        if (!kw.isEmpty()) doSearch(kw);
    }

    private void updateTabUi() {
        btnTabBili.setText("bili".equals(tab) ? "【B站视频】" : "B站视频");
        btnTabBangumi.setText("bangumi".equals(tab) ? "【番剧】" : "番剧");
        btnTabFree.setText("free".equals(tab) ? "【免费源】" : "免费源");
        if ("free".equals(tab)) {
            tvSearchStatus.setText("免费动漫源: 可搜 B站没有版权的动漫，免费播放");
        } else {
            tvSearchStatus.setText("");
        }
    }

    private void doSearch(String kw) {
        if (kw.isEmpty()) {
            tvSearchStatus.setText("请输入关键词");
            return;
        }
        lastKeyword = kw;
        pbSearch.setVisibility(View.VISIBLE);
        tvSearchStatus.setText("搜索中: " + kw + " ...");
        adapter.setData(new ArrayList<>());

        pool.execute(() -> {
            try {
                List<Video> list;
                switch (tab) {
                    case "bangumi":
                        list = searchBangumi(kw);
                        break;
                    case "free":
                        list = searchFree(kw);
                        break;
                    default:
                        list = searchBili(kw);
                }
                ui.post(() -> {
                    pbSearch.setVisibility(View.GONE);
                    adapter.setData(list);
                    tvSearchStatus.setText(tabLabel() + ": " + kw + " · 共 " + list.size() + " 个结果"
                            + (list.isEmpty() ? "（换个关键词或切『免费源』试试）" : ""));
                });
            } catch (Exception e) {
                ui.post(() -> {
                    pbSearch.setVisibility(View.GONE);
                    tvSearchStatus.setText("搜索失败: " + e.getMessage() + "（可重试）");
                });
            }
        });
    }

    private String tabLabel() {
        switch (tab) {
            case "bangumi": return "番剧";
            case "free": return "免费源";
            default: return "B站视频";
        }
    }

    private List<Video> searchBili(String kw) throws Exception {
        JSONObject d = BiliApi.searchVideo(kw, 1);
        JSONArray arr = d.optJSONArray("result");
        List<Video> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            if (!"video".equals(o.optString("type"))) continue;
            Video v = Video.fromBili(o);
            if (v != null && !v.bvid.isEmpty()) {
                v.danmaku = o.optLong("video_review", v.danmaku);
                list.add(v);
            }
        }
        return list;
    }

    private List<Video> searchBangumi(String kw) throws Exception {
        JSONObject d = BiliApi.searchBangumi(kw, 1);
        JSONArray arr = d.optJSONArray("result");
        List<Video> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Video v = new Video();
            v.type = Video.TYPE_PGC;
            v.seasonId = o.optLong("season_id", 0);
            v.title = Video.stripEm(o.optString("title", ""));
            v.cover = o.optString("cover", "");
            if (v.cover.startsWith("//")) v.cover = "https:" + v.cover;
            v.upName = o.optString("index_show", "番剧");
            if (v.seasonId > 0) list.add(v);
        }
        return list;
    }

    private List<Video> searchFree(String kw) throws Exception {
        List<Anime> animes = AgeApi.search(kw);
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

    private void openVideo(Video v) {
        Intent it = new Intent(this, PlayerActivity.class);
        it.putExtra("video", v);
        startActivity(it);
    }
}
