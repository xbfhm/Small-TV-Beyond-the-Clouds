package com.yzb.tv;

import android.app.Application;
import android.widget.Toast;

import com.yzb.tv.api.AgeApi;
import com.yzb.tv.rule.RuleRepo;
import com.yzb.tv.util.CookieStore;
import com.yzb.tv.util.Http;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * 云之彼端小电视 - Application
 * 对标云视听小电视框架: B站官方接口 + 免费动漫源 + 非凡影视稳定源 + 评论/私信增强
 * 内置崩溃捕获: 闪退自动上报 https://xbfhm.xyz/api/crash
 */
public class App extends Application {

    public static App app;

    @Override
    public void onCreate() {
        super.onCreate();
        app = this;
        installCrashHandler();

        // 每个初始化独立 try/catch, 单个失败不影响启动
        safe(() -> CookieStore.init(this), "CookieStore");
        safe(() -> Http.init(this), "Http");
        safe(() -> AgeApi.init(this), "AgeApi");
        safe(() -> RuleRepo.init(this), "RuleRepo");
        try {
            new Thread(AgeApi::warmUp, "age-warmup").start();
        } catch (Throwable ignored) {
        }

        uploadPendingCrash();
    }

    private void safe(Runnable r, String name) {
        try {
            r.run();
        } catch (Throwable t) {
            reportCrash("init " + name + " 失败: " + stackOf(t));
        }
    }

    private static String stackOf(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    // ==================== 崩溃捕获 ====================

    private void installCrashHandler() {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
                String text = "=== 云之彼端小电视 崩溃 ===\ntime=" + time
                        + "\nthread=" + thread.getName()
                        + "\nversion=1.0.1\n"
                        + stackOf(throwable);
                // 1. 写本地
                writeLocal(text);
                // 2. 尽力立即上报 (进程即将被杀)
                try {
                    reportCrash(text);
                } catch (Exception ignored) {
                }
            } catch (Exception ignored) {
            }
            if (prev != null) {
                prev.uncaughtException(thread, throwable);
            }
        });
    }

    private void writeLocal(String text) {
        try {
            for (File dir : new File[]{getFilesDir(), getCacheDir()}) {
                if (dir == null) continue;
                try {
                    File f = new File(dir, "crash.log");
                    try (FileWriter fw = new FileWriter(f, false)) {
                        fw.write(text);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        // Android 10+ : 通过 MediaStore 把崩溃日志写到 Download 目录 (无需权限, 方便读取)
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                        "yzb-crash-" + System.currentTimeMillis() + ".txt");
                cv.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                cv.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOWNLOADS);
                android.content.ContentResolver cr = getContentResolver();
                android.net.Uri uri = cr.insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri != null) {
                    try (java.io.OutputStream os = cr.openOutputStream(uri)) {
                        os.write(text.getBytes("UTF-8"));
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** 上报崩溃到服务器 (同步, 短超时) */
    private void reportCrash(String text) {
        try {
            OkHttpClient c = new OkHttpClient.Builder()
                    .connectTimeout(6, TimeUnit.SECONDS)
                    .readTimeout(6, TimeUnit.SECONDS)
                    .build();
            Request req = new Request.Builder()
                    .url("https://xbfhm.xyz/api/crash")
                    .post(RequestBody.create(text, MediaType.parse("text/plain; charset=utf-8")))
                    .build();
            try (okhttp3.Response resp = c.newCall(req).execute()) {
                resp.close();
            }
        } catch (Exception ignored) {
        }
    }

    /** 下次启动补报上次崩溃 (进程死太快没报成功的情况) */
    private void uploadPendingCrash() {
        new Thread(() -> {
            try {
                File f = new File(getFilesDir(), "crash.log");
                if (f.exists() && f.length() > 0 && f.length() < 65536) {
                    byte[] b = new byte[(int) f.length()];
                    try (FileInputStream in = new FileInputStream(f)) {
                        int n = 0;
                        while (n < b.length) {
                            int r = in.read(b, n, b.length - n);
                            if (r < 0) break;
                            n += r;
                        }
                    }
                    String text = new String(b, "UTF-8");
                    reportCrash("[补报]" + text);
                    // 展示给用户看 (方便截图反馈)
                    final String firstLine = firstLineOf(text);
                    try {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                                Toast.makeText(this, "上次崩溃: " + firstLine, Toast.LENGTH_LONG).show());
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        }, "crash-upload").start();
    }

    private static String firstLineOf(String text) {
        int i = text.indexOf('\n');
        String head = i > 0 ? text.substring(0, i) : text;
        // 取第一行真正的异常类型
        for (String line : text.split("\n")) {
            if (line.contains("Exception") || line.contains("Error")) {
                return line.trim().length() > 120 ? line.trim().substring(0, 120) : line.trim();
            }
        }
        return head.length() > 120 ? head.substring(0, 120) : head;
    }
}
