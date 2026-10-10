package com.yzb.tv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yzb.tv.R;
import com.yzb.tv.api.BiliApi;
import com.yzb.tv.model.Video;
import com.yzb.tv.util.UIMode;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 观看历史 (登录后云端同步) */
public class HistoryActivity extends AppCompatActivity {

    private RecyclerView rvHistory;
    private ProgressBar pbHistory;
    private TextView tvEmpty;

    private VideoAdapter adapter;
    private final ExecutorService pool = Executors.newFixedThreadPool(1);
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        rvHistory = findViewById(R.id.rvHistory);
        pbHistory = findViewById(R.id.pbHistory);
        tvEmpty = findViewById(R.id.tvEmpty);

        adapter = new VideoAdapter(this::openVideo);
        int span = UIMode.isTV(this) ? 5 : 2;
        rvHistory.setLayoutManager(new GridLayoutManager(this, span));
        rvHistory.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        load();
    }

    private void load() {
        pbHistory.setVisibility(View.VISIBLE);
        pool.execute(() -> {
            try {
                JSONObject d = BiliApi.history(0, 0);
                JSONArray arr = d.optJSONArray("list");
                List<Video> list = new ArrayList<>();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o == null) continue;
                        JSONObject meta = o.optJSONObject("videos");
                        Video v = null;
                        if (meta != null) v = Video.fromBili(meta);
                        if (v != null) {
                            v.title = o.optString("title", v.title);
                            v.cover = o.optString("cover", v.cover);
                            list.add(v);
                        }
                    }
                }
                ui.post(() -> {
                    pbHistory.setVisibility(View.GONE);
                    adapter.setData(list);
                    if (list.isEmpty()) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText("暂无观看历史");
                    }
                });
            } catch (Exception e) {
                ui.post(() -> {
                    pbHistory.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText("加载失败: " + e.getMessage());
                });
            }
        });
    }

    private void openVideo(Video v) {
        Intent it = new Intent(this, PlayerActivity.class);
        it.putExtra("video", v);
        startActivity(it);
    }
}
