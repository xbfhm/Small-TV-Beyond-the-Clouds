package com.yzb.tv.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.yzb.tv.R;
import com.yzb.tv.api.BiliApi;
import com.yzb.tv.util.CookieStore;
import com.yzb.tv.util.ZxingUtil;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 扫码登录 B站账号 (云视听同款流程)
 * 手机 App 扫码 → 授权 → 自动保存 SESSDATA/bili_jct
 */
public class LoginActivity extends AppCompatActivity {

    private ImageView ivQR;
    private TextView tvLoginStatus;
    private Button btnRefresh, btnLogout;

    private final ExecutorService pool = Executors.newFixedThreadPool(1);
    private final Handler ui = new Handler(Looper.getMainLooper());

    private String qrcodeKey = "";
    private volatile boolean polling = false;
    private boolean loginDone = false;

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            if (!polling) return;
            pool.execute(() -> {
                try {
                    JSONObject d = BiliApi.qrPollWithResponse(qrcodeKey);
                    int code = d.optInt("_code", -1);
                    ui.post(() -> handlePoll(code, d));
                } catch (Exception e) {
                    ui.post(() -> tvLoginStatus.setText("网络异常，重试中..."));
                    if (polling) ui.postDelayed(pollTask, 2500);
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        ivQR = findViewById(R.id.ivQR);
        tvLoginStatus = findViewById(R.id.tvLoginStatus);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnLogout = findViewById(R.id.btnLogout);

        boolean logoutMode = getIntent().getBooleanExtra("logout_mode", false);

        if (CookieStore.isLogin()) {
            tvLoginStatus.setText("已登录: " + CookieStore.uname + "\nUID: " + CookieStore.mid);
            btnLogout.setVisibility(View.VISIBLE);
        }

        btnRefresh.setOnClickListener(v -> generateQR());
        btnLogout.setOnClickListener(v -> {
            CookieStore.logout();
            Toast.makeText(this, "已退出登录", Toast.LENGTH_SHORT).show();
            finish();
        });

        if (!logoutMode || !CookieStore.isLogin()) {
            generateQR();
        }
    }

    private void generateQR() {
        loginDone = false;
        tvLoginStatus.setText("正在生成二维码...");
        pool.execute(() -> {
            try {
                JSONObject d = BiliApi.qrGenerate();
                qrcodeKey = d.optString("qrcode_key");
                String url = d.optString("url");
                ui.post(() -> {
                    ivQR.setImageBitmap(ZxingUtil.makeQR(url, 600));
                    tvLoginStatus.setText("请用「哔哩哔哩」App 扫码");
                    polling = true;
                    ui.postDelayed(pollTask, 1500);
                });
            } catch (Exception e) {
                ui.post(() -> tvLoginStatus.setText("二维码生成失败: " + e.getMessage()));
            }
        });
    }

    private void handlePoll(int code, JSONObject d) {
        switch (code) {
            case 0:
                polling = false;
                loginDone = true;
                tvLoginStatus.setText("登录成功！");
                // 拉设备指纹 + 用户信息
                pool.execute(() -> {
                    try {
                        BiliApi.fetchBuvid();
                        BiliApi.nav();
                    } catch (Exception ignored) {
                    }
                    ui.post(() -> {
                        Toast.makeText(this, "欢迎，" + CookieStore.uname, Toast.LENGTH_LONG).show();
                        finish();
                    });
                });
                break;
            case 86090:
                tvLoginStatus.setText("已扫码，请在手机上确认登录");
                if (polling) ui.postDelayed(pollTask, 2000);
                break;
            case 86038:
                tvLoginStatus.setText("二维码已过期，请点『刷新二维码』");
                break;
            default:
                tvLoginStatus.setText("等待扫码...");
                if (polling) ui.postDelayed(pollTask, 2000);
        }
    }

    @Override
    protected void onDestroy() {
        polling = false;
        super.onDestroy();
    }
}
