package com.yzb.tv.ui;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.yzb.tv.R;
import com.yzb.tv.util.CookieStore;

/** 关于页 */
public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        TextView tvAbout = findViewById(R.id.tvAbout);
        String loginText = CookieStore.isLogin()
                ? "已登录: " + CookieStore.uname + " (UID " + CookieStore.mid + ")"
                : "未登录";
        tvAbout.setText("云之彼端小电视 v1.0\n\n"
                + "对标云视听小电视框架的第三方客户端\n"
                + "B站官方接口 + 免费动漫源 + 非凡影视稳定源\n\n"
                + loginText + "\n\n"
                + "声明: 视频数据来自公开接口, 仅供学习交流");
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }
}
