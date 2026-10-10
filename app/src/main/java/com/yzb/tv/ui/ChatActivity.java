package com.yzb.tv.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

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
 * 私聊页 (新功能) —— 聊天记录 + 发消息 + 定时刷新
 */
public class ChatActivity extends AppCompatActivity {

    private long talkerId;
    private String talkerName = "", talkerFace = "";

    private RecyclerView rvChat;
    private EditText etChat;
    private TextView tvChatName;

    private ChatAdapter adapter;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Handler refresher = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        talkerId = getIntent().getLongExtra("talker_id", 0);
        talkerName = getIntent().getStringExtra("name");
        talkerFace = getIntent().getStringExtra("face");
        if (talkerId <= 0) {
            finish();
            return;
        }

        rvChat = findViewById(R.id.rvChat);
        etChat = findViewById(R.id.etChat);
        tvChatName = findViewById(R.id.tvChatName);

        tvChatName.setText(talkerName == null || talkerName.isEmpty() ? "聊天" : talkerName);

        adapter = new ChatAdapter();
        adapter.setFaces(CookieStore.face, talkerFace);
        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSendChat).setOnClickListener(v -> sendMessage());
        etChat.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        loadMessages();
        // 每 8 秒刷新一次
        refresher.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) {
                    loadMessages();
                    refresher.postDelayed(this, 8000);
                }
            }
        }, 8000);
    }

    private void loadMessages() {
        pool.execute(() -> {
            try {
                JSONArray arr = BiliApi.chatMessages(talkerId, 30);
                List<JSONObject> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o != null) list.add(o);
                }
                ui.post(() -> {
                    adapter.setData(list);
                    rvChat.scrollToPosition(list.size() - 1);
                });
            } catch (Exception ignored) {
            }
        });
    }

    private void sendMessage() {
        String text = etChat.getText().toString().trim();
        if (text.isEmpty()) return;
        etChat.setText("");
        pool.execute(() -> {
            try {
                BiliApi.sendMessage(talkerId, text);
                ui.post(loadMessagesOnUi());
            } catch (Exception e) {
                ui.post(() -> Toast.makeText(this, "发送失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private Runnable loadMessagesOnUi() {
        return this::loadMessages;
    }

    @Override
    protected void onDestroy() {
        refresher.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
