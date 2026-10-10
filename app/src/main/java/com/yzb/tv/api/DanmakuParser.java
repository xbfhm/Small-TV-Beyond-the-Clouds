package com.yzb.tv.api;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 极简 protobuf 解析器 —— 解析 B站弹幕 seg.so
 * (DmSegMobileReply { repeated DanmakuElem elems = 1; }
 *  DanmakuElem { id=1; progress=2(ms); mode=3; fontsize=4; color=5;
 *                midHash=6; content=7; ctime=8; ... })
 * 不引第三方 protobuf 库, 手写 varint/LEN 解析, 兼容 Android 5.x
 */
public class DanmakuParser {

    public static class Item {
        public long id;
        public int progressMs;   // 出现时间(毫秒)
        public int mode;         // 1/2/3=滚动 4=底部 5=顶部 6=逆向 7=高级 8=代码
        public int fontSize;
        public int color;        // rgb int
        public String content;
    }

    public static List<Item> parse(byte[] data) {
        List<Item> list = new ArrayList<>();
        if (data == null || data.length == 0) return list;
        try {
            int[] pos = {0};
            while (pos[0] < data.length) {
                int tag = readVarint(data, pos);
                int fieldNo = tag >>> 3;
                int wire = tag & 7;
                if (wire != 2) {  // 顶层只有 LEN 字段
                    skipField(data, pos, wire);
                    continue;
                }
                int len = readVarint(data, pos);
                if (fieldNo == 1 && len > 0 && pos[0] + len <= data.length) {
                    Item it = parseElem(data, pos[0], len);
                    if (it != null && it.content != null && !it.content.isEmpty()) {
                        list.add(it);
                    }
                }
                pos[0] += len;
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    /** 解析单个 DanmakuElem（从 start 开始, 长度为 len） */
    private static Item parseElem(byte[] d, int start, int len) {
        Item it = new Item();
        int[] pos = {start};
        int end = start + len;
        while (pos[0] < end) {
            int tag = readVarint(d, pos);
            int fieldNo = tag >>> 3;
            int wire = tag & 7;
            switch (wire) {
                case 0: { // varint
                    long v = readVarint64(d, pos);
                    if (fieldNo == 1) it.id = v;
                    else if (fieldNo == 2) it.progressMs = (int) v;
                    else if (fieldNo == 3) it.mode = (int) v;
                    else if (fieldNo == 4) it.fontSize = (int) v;
                    else if (fieldNo == 5) it.color = (int) v;
                    break;
                }
                case 2: { // length-delimited
                    int l = readVarint(d, pos);
                    if (fieldNo == 7 && l > 0 && pos[0] + l <= end) {
                        it.content = new String(d, pos[0], l, StandardCharsets.UTF_8);
                    }
                    pos[0] += l;
                    break;
                }
                case 5: pos[0] += 4; break;  // 32bit
                case 1: pos[0] += 8; break;  // 64bit
                default: return it;          // 未知 wire, 放弃本元素剩余
            }
        }
        return it;
    }

    private static int readVarint(byte[] d, int[] pos) {
        return (int) readVarint64(d, pos);
    }

    private static long readVarint64(byte[] d, int[] pos) {
        long result = 0;
        int shift = 0;
        while (pos[0] < d.length) {
            byte b = d[pos[0]++];
            result |= (long) (b & 0x7f) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
            if (shift > 63) break;
        }
        return result;
    }

    private static void skipField(byte[] d, int[] pos, int wire) {
        switch (wire) {
            case 0: readVarint64(d, pos); break;
            case 1: pos[0] += 8; break;
            case 2: { int l = readVarint(d, pos); pos[0] += l; break; }
            case 5: pos[0] += 4; break;
            default: pos[0] = d.length; break;
        }
    }
}
