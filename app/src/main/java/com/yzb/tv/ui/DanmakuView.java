package com.yzb.tv.ui;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * 弹幕渲染层 —— 轨道池 + 平移动画 (轻量自绘, 兼容 Android 5.x)
 */
public class DanmakuView extends FrameLayout {

    private static final int TRACK_COUNT = 9;
    private final long[] trackFreeAt = new long[TRACK_COUNT];
    private float textSizeSp = 16f;

    public DanmakuView(Context context) {
        super(context);
        init();
    }

    public DanmakuView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DanmakuView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClipChildren(true);
        setClipToPadding(true);
    }

    public void setTextSizeSp(float sp) {
        textSizeSp = sp;
    }

    /** 加一条滚动弹幕 */
    public void add(String text, int color) {
        if (text == null || text.isEmpty()) return;

        long now = System.currentTimeMillis();
        int track = -1;
        for (int i = 0; i < TRACK_COUNT; i++) {
            if (trackFreeAt[i] <= now) {
                track = i;
                break;
            }
        }
        if (track < 0) return;  // 轨道全忙, 丢弃 (弹幕太密时)
        trackFreeAt[track] = now + 1100;

        final TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(textSizeSp);
        tv.setTextColor(color == 0 ? Color.WHITE : color);
        tv.setShadowLayer(3f, 1f, 1f, 0xCC000000);
        tv.setSingleLine(true);
        tv.setMaxLines(1);

        int w = getWidth();
        if (w <= 0) w = 1280;
        final int startX = w;
        int h = getHeight();
        if (h <= 0) h = 720;
        int trackH = Math.max(30, h / TRACK_COUNT);

        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = track * trackH + (trackH / 4);
        tv.setLayoutParams(lp);
        addView(tv);

        tv.post(() -> {
            int textW = tv.getWidth();
            tv.setX(startX);
            long dur = 8500;  // 8.5 秒横穿
            tv.animate()
                    .translationX(-(textW + 40))
                    .setDuration(dur)
                    .withEndAction(() -> {
                        try {
                            removeView(tv);
                        } catch (Exception ignored) {
                        }
                    })
                    .start();
        });
    }

    public void clearAll() {
        removeAllViews();
        for (int i = 0; i < TRACK_COUNT; i++) trackFreeAt[i] = 0;
    }
}
