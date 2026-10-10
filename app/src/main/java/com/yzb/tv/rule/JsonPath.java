package com.yzb.tv.rule;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 极简 JSONPath + @变量 模板引擎 (仅覆盖 Kazumi API 规则用到的子集):
 *   $.data.records[*]  /  $.title  /  $.episodes[*]  /  $.a.b
 *   [*] 数组通配, [n] 数组下标, ['key'] 引号键名
 * 模板: @keyword / @source / @episodeUrl / @episodeNumber / @roadIndex ...
 */
public class JsonPath {

    /** 读取 JSONPath 结果 (返回多个值) */
    public static List<Object> read(Object doc, String expr) {
        List<Object> out = new ArrayList<>();
        if (expr == null || expr.isEmpty()) return out;
        if (!expr.startsWith("$")) return out;
        List<Object> cur = new ArrayList<>();
        cur.add(doc);
        List<Object> next = new ArrayList<>();

        int i = 1;
        int n = expr.length();
        while (i < n) {
            char c = expr.charAt(i);
            if (c == '.') {
                // .key 或 .key 后续
                i++;
                int s = i;
                while (i < n && isKeyChar(expr.charAt(i))) i++;
                String key = expr.substring(s, i);
                next.clear();
                for (Object o : cur) {
                    Object v = getKey(o, key);
                    if (v != null) next.add(v);
                }
                cur = new ArrayList<>(next);
            } else if (c == '[') {
                int e = findBracket(expr, i);
                String content = expr.substring(i + 1, e).trim();
                i = e + 1;
                next.clear();
                if (content.equals("*")) {
                    for (Object o : cur) addAll(next, o);
                } else if (content.matches("\\d+")) {
                    int idx = Integer.parseInt(content);
                    for (Object o : cur) {
                        Object v = getIndex(o, idx);
                        if (v != null) next.add(v);
                    }
                } else {
                    // ['key'] 或 ["key"]
                    String key = content;
                    if (key.length() >= 2 && ((key.startsWith("'") && key.endsWith("'"))
                            || (key.startsWith("\"") && key.endsWith("\"")))) {
                        key = key.substring(1, key.length() - 1);
                    }
                    for (Object o : cur) {
                        Object v = getKey(o, key);
                        if (v != null) next.add(v);
                    }
                }
                cur = new ArrayList<>(next);
            } else {
                // 不支持
                return out;
            }
        }
        return cur;
    }

    /** 读取第一个值 (无则 null) */
    public static Object readFirst(Object doc, String expr) {
        List<Object> l = read(doc, expr);
        return l.isEmpty() ? null : l.get(0);
    }

    /** 把值转字符串 (null->"", 非字符串 toString) */
    public static String asString(Object v) {
        if (v == null) return "";
        if (v instanceof String) return ((String) v).trim();
        return String.valueOf(v);
    }

    /** 渲染 @变量 模板。encode=true 时对变量值 URL 编码 */
    public static String render(String template, java.util.Map<String, Object> vars, boolean encode) {
        if (template == null) return "";
        Matcher m = Pattern.compile("(?<![A-Za-z0-9_])@([A-Za-z_][A-Za-z0-9_]*)").matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String name = m.group(1);
            Object v = vars.get(name);
            String val = v == null ? "" : String.valueOf(v);
            if (encode) {
                try { val = java.net.URLEncoder.encode(val, "UTF-8"); } catch (Exception ignored) {}
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ---- 内部 ----
    private static boolean isKeyChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '_' || c == '$' || c == '-';
    }

    private static int findBracket(String expr, int start) {
        boolean inQ = false;
        char q = 0;
        for (int i = start + 1; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (inQ) {
                if (c == q) inQ = false;
                continue;
            }
            if (c == '\'' || c == '"') { inQ = true; q = c; continue; }
            if (c == ']') return i;
        }
        return expr.length();
    }

    private static Object getKey(Object o, String key) {
        if (o instanceof JSONObject) return ((JSONObject) o).opt(key);
        return null;
    }

    private static Object getIndex(Object o, int idx) {
        if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            if (idx >= 0 && idx < a.length()) return a.opt(idx);
        }
        return null;
    }

    private static void addAll(List<Object> out, Object o) {
        if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            for (int i = 0; i < a.length(); i++) out.add(a.opt(i));
        } else if (o != null) {
            out.add(o);
        }
    }
}
