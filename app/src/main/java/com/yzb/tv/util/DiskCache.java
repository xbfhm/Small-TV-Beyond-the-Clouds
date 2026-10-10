package com.yzb.tv.util;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;

/**
 * 简单磁盘缓存 (API JSON): 首页/详情/搜索秒开
 * - 按 URL hash 存文件, 带时间戳
 * - 超过有效期自动失效; 总容量超 500MB 时清理最旧文件
 */
public class DiskCache {
    private static final long MAX_SIZE = 500L * 1024 * 1024;   // 500MB
    private final File dir;

    public DiskCache(Context ctx) {
        dir = new File(ctx.getCacheDir(), "api_cache");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    /** 读缓存, 超过 maxAgeMs 视为失效返回 null */
    public String get(String key, long maxAgeMs) {
        try {
            File f = new File(dir, key.hashCode() + ".json");
            if (!f.exists()) return null;
            long age = System.currentTimeMillis() - f.lastModified();
            if (age > maxAgeMs || age < 0) return null;
            byte[] buf = new byte[(int) f.length()];
            try (FileInputStream in = new FileInputStream(f)) {
                int n = 0;
                while (n < buf.length) {
                    int r = in.read(buf, n, buf.length - n);
                    if (r < 0) break;
                    n += r;
                }
            }
            return new String(buf, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    public void put(String key, String content) {
        try {
            File f = new File(dir, key.hashCode() + ".json");
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(content.getBytes("UTF-8"));
            }
            trim();
        } catch (Exception ignored) {}
    }

    /** 每天 7:00 后首次访问时清空整个缓存目录 */
    public void resetDaily() {
        try {
            File marker = new File(dir.getParentFile(), "last_clear");
            long last = 0;
            if (marker.exists()) {
                try (FileInputStream in = new FileInputStream(marker)) {
                    byte[] b = new byte[32];
                    int n = in.read(b);
                    if (n > 0) last = Long.parseLong(new String(b, 0, n).trim());
                } catch (Exception ignored) {}
            }
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.set(java.util.Calendar.HOUR_OF_DAY, 7);
            c.set(java.util.Calendar.MINUTE, 0);
            c.set(java.util.Calendar.SECOND, 0);
            c.set(java.util.Calendar.MILLISECOND, 0);
            long today7 = c.getTimeInMillis();
            long now = System.currentTimeMillis();
            if (now >= today7 && last < today7) {
                File root = dir.getParentFile();
                File[] all = root != null ? root.listFiles() : null;
                if (all != null) for (File f : all) deleteRecursive(f);
                try (FileOutputStream out = new FileOutputStream(marker)) {
                    out.write(String.valueOf(now).getBytes());
                }
            }
        } catch (Exception ignored) {}
    }

    private void deleteRecursive(File f) {
        if (f == null) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    /** 容量管理: 超过上限删除最旧文件 */
    private void trim() {
        try {
            File[] files = dir.listFiles();
            if (files == null) return;
            long total = 0;
            for (File f : files) total += f.length();
            if (total <= MAX_SIZE) return;
            // 按修改时间排序, 从最旧开始删
            java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            for (File f : files) {
                if (total <= MAX_SIZE) break;
                total -= f.length();
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        } catch (Exception ignored) {}
    }
}
