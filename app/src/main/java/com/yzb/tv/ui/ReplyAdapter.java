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
import java.util.List;
import java.util.Locale;

/** 评论区列表适配器 */
public class ReplyAdapter extends RecyclerView.Adapter<ReplyAdapter.VH> {

    public interface OnClick {
        void onClick(JSONObject reply);
    }

    private final List<JSONObject> data = new ArrayList<>();
    private final OnClick onClick;

    public ReplyAdapter(OnClick onClick) {
        this.onClick = onClick;
    }

    public void setData(List<JSONObject> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivFace;
        TextView tvName, tvContent, tvTime, tvLike, tvSubCount;

        VH(View v) {
            super(v);
            ivFace = v.findViewById(R.id.ivFace);
            tvName = v.findViewById(R.id.tvName);
            tvContent = v.findViewById(R.id.tvContent);
            tvTime = v.findViewById(R.id.tvTime);
            tvLike = v.findViewById(R.id.tvLike);
            tvSubCount = v.findViewById(R.id.tvSubCount);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reply, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        JSONObject o = data.get(pos);
        JSONObject member = o.optJSONObject("member");
        if (member != null) {
            h.tvName.setText(member.optString("uname"));
            JSONObject lv = member.optJSONObject("level_info");
            if (lv != null) h.tvName.append("  Lv" + lv.optInt("current_level", 0));
            Img.load(h.ivFace, member.optString("avatar"));
        }
        JSONObject content = o.optJSONObject("content");
        h.tvContent.setText(content != null ? content.optString("message", "") : "");
        h.tvLike.setText("👍 " + o.optInt("like", 0));
        int rcount = o.optInt("rcount", 0);
        h.tvSubCount.setText(rcount > 0 ? rcount + "条回复" : "");
        h.tvTime.setText(formatTime(o.optLong("ctime", 0)));

        h.itemView.setOnClickListener(v -> {
            if (onClick != null) onClick.onClick(o);
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public static String formatTime(long epochSec) {
        if (epochSec <= 0) return "";
        long now = System.currentTimeMillis() / 1000;
        long diff = now - epochSec;
        if (diff < 60) return "刚刚";
        if (diff < 3600) return (diff / 60) + "分钟前";
        if (diff < 86400) return (diff / 3600) + "小时前";
        if (diff < 7 * 86400L) return (diff / 86400) + "天前";
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(epochSec * 1000));
    }
}
