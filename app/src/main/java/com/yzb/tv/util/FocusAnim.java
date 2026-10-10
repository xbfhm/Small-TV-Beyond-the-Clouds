package com.yzb.tv.util;

import android.view.View;

/**
 * 电视焦点缩放动画: 聚焦时轻微放大 + 浮起, 遥控器"选中感"更强
 * 用于列表项/按钮/集数块, 老电视 GPU 也能流畅 (属性动画, 极短时长)
 */
public class FocusAnim {
    private static final float SCALE = 1.06f;
    private static final int DURATION = 130;
    private static float ELEV = -1f;   // 聚焦浮起高度 (px, 懒初始化)

    /** 静态缩放方法: 可在已有焦点监听里调用, 不覆盖原监听 */
    public static void scale(View v, boolean has) {
        if (v == null) return;
        if (ELEV < 0) ELEV = 10f * v.getResources().getDisplayMetrics().density;
        v.animate().scaleX(has ? SCALE : 1f).scaleY(has ? SCALE : 1f)
                .setDuration(DURATION).start();
        v.setElevation(has ? ELEV : 0f);
    }

    /** 直接绑定一个 View: 自动设置焦点缩放监听 (仅当该 View 无其他焦点监听时用) */
    public static void bind(final View v) {
        if (v == null) return;
        v.setOnFocusChangeListener((view, has) -> scale(view, has));
    }
}
