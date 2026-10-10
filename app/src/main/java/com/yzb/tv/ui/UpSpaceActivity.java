package com.yzb.tv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yzb.tv.R;
import com.yzb.tv.api.BiliApi;
import com.yzb.tv.model.Video;
import com.yzb.tv.util.CookieStore;
import com.yzb.tv.util.Img;
import com.yzb.tv.util.UIMode;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UP主主页 (新功能) —— 头像/签名/数据 + 关注按钮 + 投稿列表(WBI)
 */
public class UpSpaceActivity extends AppCompatActivity {

    private long mid;
    private int relation = 0;   // 0未关注 2已关注 6互关

    private ImageView ivUpFace;
    private TextView tvUpName, tvUpSign, tvUpStat;
    private Button btnFollow;
    private RecyclerView rvUpVideos;
    private ProgressBar pbUp;

    private VideoAdapter adapter;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_up_space);

        mid = getIntent().getLongExtra("mid", 0);
        if (mid <= 0) {
            finish();
            return;
        }

        ivUpFace = findViewById(R.id.ivUpFace);
        tvUpName = findViewById(R.id.tvUpName);
        tvUpSign = findViewById(R.id.tvUpSign);
        tvUpStat = findViewById(R.id.tvUpStat);
        btnFollow = findViewById(R.id.btnFollow);
        rvUpVideos = findViewById(R.id.rvUpVideos);
        pbUp = findViewById(R.id.pbUp);

        adapter = new VideoAdapter(this::openVideo);
        int span = UIMode.isTV(this) ? 4 : 2;
        rvUpVideos.setLayoutManager(new GridLayoutManager(this, span));
        rvUpVideos.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnFollow.setOnClickListener(v -> toggleFollow());

        loadInfo();
        loadVideos();
    }

    private void loadInfo() {
        pool.execute(() -> {
            try {
                JSONObject d = BiliApi.spaceInfo(mid);
                int attr = BiliApi.relation(mid);
                relation = attr;
                ui.post(() -> {
                    tvUpName.setText(d.optString("name"));
                    tvUpSign.setText(d.optString("sign", ""));
                    tvUpStat.setText("关注 " + Video.countText(d.optLong("attention", 0))
                            + " · 粉丝 " + Video.countText(d.optLong("fans", 0))
                            + " · 获赞 " + Video.countText(d.optLong("likes", 0))
                            + " · Lv" + d.optInt("level", 0));
                    Img.load(ivUpFace, d.optString("face"));
                    updateFollowBtn();
                });
            } catch (Exception e) {
                ui.post(() -> {
                    tvUpName.setText("UP主 " + mid);
                    tvUpSign.setText("信息加载失败: " + e.getMessage());
                });
            }
        });
    }

    private void loadVideos() {
        pbUp.setVisibility(View.VISIBLE);
        pool.execute(() -> {
            try {
                JSONObject d = BiliApi.spaceVideos(mid, 1);
                JSONObject vlist = d.optJSONObject("list");
                JSONArray arr = vlist != null ? vlist.optJSONArray("vlist") : null;
                List<Video> list = new ArrayList<>();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o == null) continue;
                        Video v = Video.fromBili(o);
                        if (v != null && !v.bvid.isEmpty()) list.add(v);
                    }
                }
                ui.post(() -> {
                    pbUp.setVisibility(View.GONE);
                    adapter.setData(list);
                });
            } catch (Exception e) {
                ui.post(() -> {
                    pbUp.setVisibility(View.GONE);
                    Toast.makeText(this, "投稿加载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void updateFollowBtn() {
        if (relation == 6) btnFollow.setText("已互关");
        else if (relation == 2) btnFollow.setText("已关注");
        else if (relation == 128) btnFollow.setText("已拉黑");
        else btnFollow.setText("+ 关注");
    }

    private void toggleFollow() {
        if (!CookieStore.isLogin()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        if (relation == 128) return;
        final int act = (relation == 2 || relation == 6) ? 2 : 1;
        pool.execute(() -> {
            try {
                BiliApi.modifyRelation(mid, act);
                relation = act == 1 ? 2 : 0;
                ui.post(() -> {
                    updateFollowBtn();
                    Toast.makeText(this, act == 1 ? "已关注" : "已取消关注", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                ui.post(() -> Toast.makeText(this, "操作失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void openVideo(Video v) {
        Intent it = new Intent(this, PlayerActivity.class);
        it.putExtra("video", v);
        startActivity(it);
    }
}
