package com.yzb.tv.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yzb.tv.R;
import com.yzb.tv.util.Img;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 私信会话列表适配器 */
public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.VH> {

    public interface OnClick {
        void onClick(JSONObject session);
    }

    private final List<JSONObject> data = new ArrayList<>();
    public Map<Long, JSONObject> cards = new HashMap<>();  // mid -> user card
    private final OnClick onClick;

    public SessionAdapter(OnClick onClick) {
        this.onClick = onClick;
    }

    public void setCards(Map<Long, JSONObject> cards) {
        this.cards = cards;
        notifyDataSetChanged();
    }

    public void setData(List<JSONObject> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivFace;
        TextView tvName, tvLast, tvTime, tvUnread;

        VH(View v) {
            super(v);
            ivFace = v.findViewById(R.id.ivFace);
            tvName = v.findViewById(R.id.tvName);
            tvLast = v.findViewById(R.id.tvLast);
            tvTime = v.findViewById(R.id.tvTime);
            tvUnread = v.findViewById(R.id.tvUnread);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_session, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        JSONObject o = data.get(pos);
        long talker = o.optLong("talker_id", 0);
        JSONObject card = cards.get(talker);
        String name = card != null ? card.optString("name", "用户" + talker) : "用户" + talker;
        String face = card != null ? card.optString("face", "") : "";
        h.tvName.setText(name);
        Img.load(h.ivFace, face);

        // 最后一条消息
        JSONObject lastMsg = o.optJSONObject("last_msg");
        String lastText = "";
        if (lastMsg != null) {
            int msgType = lastMsg.optInt("msg_type", -1);
            String content = lastMsg.optString("content", "");
            if (msgType == 1) lastText = content;  // 文本
            else if (msgType == 2) lastText = "[图片]";
            else if (msgType == 7) lastText = "[表情]";
            else if (msgType == 6) lastText = "[小程序]";
            else if (msgType == 9) lastText = "[视频]";
            else lastText = "[消息]";
            long sender = lastMsg.optLong("sender_uid", 0);
            if (sender == com.yzb.tv.util.CookieStore.mid) lastText = "我: " + lastText;
        }
        h.tvLast.setText(lastText);

        long ts = o.optLong("session_ts", 0) / 1000 / 1000;
        if (ts <= 0) ts = o.optLong("ack_ts", 0) / 1000 / 1000;
        h.tvTime.setText(ts > 0 ? new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(ts)) : "");

        long unread = o.optLong("unread_count", 0);
        h.tvUnread.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
        h.tvUnread.setText(String.valueOf(unread));

        h.itemView.setOnClickListener(v -> {
            if (onClick != null) onClick.onClick(o);
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }
}
