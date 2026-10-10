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

import java.util.ArrayList;
import java.util.List;

/** 消息流适配器 (回复我的 / @我 / 收到的赞) */
public class MsgFeedAdapter extends RecyclerView.Adapter<MsgFeedAdapter.VH> {

    public interface OnClick {
        void onClick(JSONObject item);
    }

    private final List<JSONObject> data = new ArrayList<>();
    private final OnClick onClick;

    public MsgFeedAdapter(OnClick onClick) {
        this.onClick = onClick;
    }

    public void setData(List<JSONObject> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivFace;
        TextView tvName, tvTitle, tvContent, tvTime;

        VH(View v) {
            super(v);
            ivFace = v.findViewById(R.id.ivFace);
            tvName = v.findViewById(R.id.tvName);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvContent = v.findViewById(R.id.tvContent);
            tvTime = v.findViewById(R.id.tvTime);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_msgfeed, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        JSONObject o = data.get(pos);
        JSONObject user = o.optJSONObject("user");
        if (user != null) {
            h.tvName.setText(user.optString("nickname", ""));
            Img.load(h.ivFace, user.optString("face", ""));
        }
        JSONObject item = o.optJSONObject("item");
        if (item != null) {
            String title = item.optString("title", "");
            if (title.isEmpty()) {
                JSONObject source = item.optJSONObject("source");
                if (source != null) title = source.optString("title", "");
            }
            h.tvTitle.setText(title);
            String content = item.optString("desc", item.optString("source_content", ""));
            if (content.isEmpty() && "at".equals(item.optString("business"))) {
                content = "@了你";
            }
            h.tvContent.setText(content);
        }
        h.tvTime.setText(ReplyAdapter.formatTime(o.optLong("reply_time", 0)));
        h.itemView.setOnClickListener(v -> {
            if (onClick != null) onClick.onClick(o);
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }
}
