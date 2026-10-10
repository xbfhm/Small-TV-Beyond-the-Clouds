package com.yzb.tv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yzb.tv.R;
import com.yzb.tv.api.BiliApi;
import com.yzb.tv.util.CookieStore;
import com.yzb.tv.util.Img;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 消息中心 (新功能) —— 私信会话 + 回复我的 + @我 + 收到的赞
 */
public class MessageActivity extends AppCompatActivity {

    private RecyclerView rvMessage;
    private ProgressBar pbMsg;
    private TextView tvMsgEmpty, tvMsgTitle;
    private Button btnTabSession, btnTabReply, btnTabAt, btnTabLike;

    private SessionAdapter sessionAdapter;
    private MsgFeedAdapter feedAdapter;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler ui = new Handler(Looper.getMainLooper());

    private String tab = "session";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message);

        rvMessage = findViewById(R.id.rvMessage);
        pbMsg = findViewById(R.id.pbMsg);
        tvMsgEmpty = findViewById(R.id.tvMsgEmpty);
        tvMsgTitle = findViewById(R.id.tvMsgTitle);
        btnTabSession = findViewById(R.id.btnTabSession);
        btnTabReply = findViewById(R.id.btnTabReply);
        btnTabAt = findViewById(R.id.btnTabAt);
        btnTabLike = findViewById(R.id.btnTabLike);

        sessionAdapter = new SessionAdapter(this::openChat);
        feedAdapter = new MsgFeedAdapter(this::openFeedItem);
        rvMessage.setLayoutManager(new LinearLayoutManager(this));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnTabSession.setOnClickListener(v -> switchTab("session"));
        btnTabReply.setOnClickListener(v -> switchTab("reply"));
        btnTabAt.setOnClickListener(v -> switchTab("at"));
        btnTabLike.setOnClickListener(v -> switchTab("like"));

        switchTab("session");
    }

    private void switchTab(String t) {
        tab = t;
        updateTabUi();
        rvMessage.setAdapter("session".equals(t) ? sessionAdapter : feedAdapter);
        load();
    }

    private void updateTabUi() {
        btnTabSession.setText("session".equals(tab) ? "【私信】" : "私信");
        btnTabReply.setText("reply".equals(tab) ? "【回复我的】" : "回复我的");
        btnTabAt.setText("at".equals(tab) ? "【@我】" : "@我");
        btnTabLike.setText("like".equals(tab) ? "【收到的赞】" : "收到的赞");
    }

    private void load() {
        if (!CookieStore.isLogin()) {
            tvMsgEmpty.setText("请先登录 (主页 → 未登录)");
            tvMsgEmpty.setVisibility(View.VISIBLE);
            return;
        }
        pbMsg.setVisibility(View.VISIBLE);
        tvMsgEmpty.setVisibility(View.GONE);
        pool.execute(() -> {
            try {
                switch (tab) {
                    case "reply":
                        loadFeed("reply");
                        break;
                    case "at":
                        loadFeed("at");
                        break;
                    case "like":
                        loadFeed("like");
                        break;
                    default:
                        loadSessions();
                }
            } catch (Exception e) {
                ui.post(() -> {
                    pbMsg.setVisibility(View.GONE);
                    tvMsgEmpty.setText("加载失败: " + e.getMessage());
                    tvMsgEmpty.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void loadSessions() throws Exception {
        JSONArray arr = BiliApi.sessions();
        List<JSONObject> list = new ArrayList<>();
        StringBuilder uids = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            list.add(o);
            long talker = o.optLong("talker_id", 0);
            if (talker > 0) {
                if (uids.length() > 0) uids.append(",");
                uids.append(talker);
            }
        }
        Map<Long, JSONObject> cards = new HashMap<>();
        if (uids.length() > 0) {
            JSONArray cardArr = BiliApi.userCards(uids.toString());
            for (int i = 0; i < cardArr.length(); i++) {
                JSONObject c = cardArr.optJSONObject(i);
                if (c != null) cards.put(c.optLong("mid", 0), c);
            }
        }
        ui.post(() -> {
            pbMsg.setVisibility(View.GONE);
            sessionAdapter.setCards(cards);
            sessionAdapter.setData(list);
            if (list.isEmpty()) {
                tvMsgEmpty.setText("暂无私信会话");
                tvMsgEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void loadFeed(String type) throws Exception {
        JSONObject d = BiliApi.msgFeed(type);
        JSONObject items = d.optJSONObject("items");
        List<JSONObject> list = new ArrayList<>();
        if (items != null) {
            JSONArray arr = items.optJSONArray("items");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o != null) list.add(o);
                }
            }
        }
        ui.post(() -> {
            pbMsg.setVisibility(View.GONE);
            feedAdapter.setData(list);
            if (list.isEmpty()) {
                tvMsgEmpty.setText("暂无消息");
                tvMsgEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void openChat(JSONObject session) {
        long talker = session.optLong("talker_id", 0);
        JSONObject card = sessionAdapter.cards != null ? sessionAdapter.cards.get(talker) : null;
        Intent it = new Intent(this, ChatActivity.class);
        it.putExtra("talker_id", talker);
        it.putExtra("name", card != null ? card.optString("name", "聊天") : "聊天");
        it.putExtra("face", card != null ? card.optString("face", "") : "");
        startActivity(it);
    }

    private void openFeedItem(JSONObject item) {
        // 回复/@我/赞 → 打开对应 UP 主页或视频
        JSONObject user = item.optJSONObject("user");
        if (user != null) {
            long mid = user.optLong("mid", 0);
            if (mid > 0) {
                Intent it = new Intent(this, UpSpaceActivity.class);
                it.putExtra("mid", mid);
                startActivity(it);
            }
        }
    }
}
