package com.yzb.tv.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yzb.tv.R;
import com.yzb.tv.api.BiliApi;
import com.yzb.tv.util.CookieStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 评论区 (新功能) —— 查看评论 + 发表评论 + 楼中楼
 */
public class ReplyActivity extends AppCompatActivity {

    private long aid;
    private String bvid = "", videoTitle = "";
    private int sort = 2;   // 2=热度 0=时间
    private int page = 1;
    private boolean hasMore = true;

    private RecyclerView rvReply;
    private ProgressBar pbReply;
    private TextView tvReplyEmpty, tvReplyTitle;
    private EditText etReply;
    private Button btnSortHot, btnSortTime;

    private ReplyAdapter adapter;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reply);

        aid = getIntent().getLongExtra("aid", 0);
        bvid = getIntent().getStringExtra("bvid");
        videoTitle = getIntent().getStringExtra("title");

        rvReply = findViewById(R.id.rvReply);
        pbReply = findViewById(R.id.pbReply);
        tvReplyEmpty = findViewById(R.id.tvReplyEmpty);
        tvReplyTitle = findViewById(R.id.tvReplyTitle);
        etReply = findViewById(R.id.etReply);
        btnSortHot = findViewById(R.id.btnSortHot);
        btnSortTime = findViewById(R.id.btnSortTime);

        tvReplyTitle.setText("评论 · " + (videoTitle == null ? "" : videoTitle));
        adapter = new ReplyAdapter(this::showSubReply);
        rvReply.setLayoutManager(new LinearLayoutManager(this));
        rvReply.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnSortHot.setOnClickListener(v -> {
            sort = 2; page = 1;
            updateSortUi();
            load();
        });
        btnSortTime.setOnClickListener(v -> {
            sort = 0; page = 1;
            updateSortUi();
            load();
        });
        findViewById(R.id.btnSendReply).setOnClickListener(v -> sendReply());

        etReply.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                sendReply();
                return true;
            }
            return false;
        });

        updateSortUi();
        load();
    }

    private void updateSortUi() {
        btnSortHot.setText(sort == 2 ? "【热度】" : "热度");
        btnSortTime.setText(sort == 0 ? "【时间】" : "时间");
    }

    private void load() {
        if (aid <= 0) {
            tvReplyEmpty.setText("无评论区 (免费源视频)");
            tvReplyEmpty.setVisibility(View.VISIBLE);
            return;
        }
        pbReply.setVisibility(View.VISIBLE);
        tvReplyEmpty.setVisibility(View.GONE);
        pool.execute(() -> {
            try {
                JSONObject d = BiliApi.replies(aid, page, sort);
                JSONArray arr = d.optJSONArray("replies");
                JSONObject pager = d.optJSONObject("page");
                List<JSONObject> list = new ArrayList<>();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o != null) list.add(o);
                    }
                }
                int total = pager != null ? pager.optInt("count", 0) : 0;
                hasMore = list.size() >= 20;
                ui.post(() -> {
                    pbReply.setVisibility(View.GONE);
                    adapter.setData(list);
                    if (list.isEmpty()) {
                        tvReplyEmpty.setText("还没有评论，快来抢沙发~");
                        tvReplyEmpty.setVisibility(View.VISIBLE);
                    }
                    Toast.makeText(this, "共 " + total + " 条评论", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                ui.post(() -> {
                    pbReply.setVisibility(View.GONE);
                    tvReplyEmpty.setText("评论加载失败: " + e.getMessage());
                    tvReplyEmpty.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void showSubReply(JSONObject reply) {
        // 楼中楼弹窗
        long rpid = reply.optLong("rpid");
        pool.execute(() -> {
            try {
                JSONObject d = BiliApi.repliesOfReply(aid, rpid, 1);
                JSONArray arr = d.optJSONArray("replies");
                StringBuilder sb = new StringBuilder();
                JSONObject root = d.optJSONObject("root");
                if (root != null) {
                    JSONObject c = root.optJSONObject("content");
                    JSONObject m = root.optJSONObject("member");
                    sb.append("楼主 ").append(m != null ? m.optString("uname") : "")
                            .append(": ").append(c != null ? c.optString("message") : "").append("\n\n");
                }
                if (arr != null) {
                    for (int i = 0; i < Math.min(arr.length(), 30); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        JSONObject c = o.optJSONObject("content");
                        JSONObject m = o.optJSONObject("member");
                        sb.append(m != null ? m.optString("uname") : "").append(": ")
                                .append(c != null ? c.optString("message") : "").append("\n");
                    }
                }
                ui.post(() -> new AlertDialog.Builder(this)
                        .setTitle("楼中楼回复")
                        .setMessage(sb.length() > 0 ? sb.toString() : "暂无回复")
                        .setPositiveButton("关闭", null)
                        .show());
            } catch (Exception e) {
                ui.post(() -> Toast.makeText(this, "回复加载失败", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void sendReply() {
        String text = etReply.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "评论内容不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!CookieStore.isLogin()) {
            Toast.makeText(this, "请先登录 (主页 → 未登录)", Toast.LENGTH_LONG).show();
            return;
        }
        etReply.setEnabled(false);
        pool.execute(() -> {
            try {
                BiliApi.addReply(aid, text);
                ui.post(() -> {
                    etReply.setText("");
                    etReply.setEnabled(true);
                    Toast.makeText(this, "评论成功！", Toast.LENGTH_SHORT).show();
                    load();
                });
            } catch (Exception e) {
                ui.post(() -> {
                    etReply.setEnabled(true);
                    Toast.makeText(this, "评论失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
