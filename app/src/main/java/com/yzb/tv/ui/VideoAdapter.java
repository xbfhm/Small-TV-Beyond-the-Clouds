package com.yzb.tv.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yzb.tv.R;
import com.yzb.tv.model.Video;
import com.yzb.tv.util.FocusAnim;
import com.yzb.tv.util.Img;

import java.util.ArrayList;
import java.util.List;

/** 视频卡片网格适配器 (首页/搜索/UP主页 复用) */
public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VH> {

    public interface OnClick {
        void onClick(Video v);
    }

    private final List<Video> data = new ArrayList<>();
    private final OnClick onClick;

    public VideoAdapter(OnClick onClick) {
        this.onClick = onClick;
    }

    public void setData(List<Video> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    public void addData(List<Video> list) {
        if (list == null || list.isEmpty()) return;
        int start = data.size();
        data.addAll(list);
        notifyItemRangeInserted(start, list.size());
    }

    public List<Video> getData() {
        return data;
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivCover;
        TextView tvDuration, tvTitle, tvUp, tvStat;

        VH(View v) {
            super(v);
            ivCover = v.findViewById(R.id.ivCover);
            tvDuration = v.findViewById(R.id.tvDuration);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvUp = v.findViewById(R.id.tvUp);
            tvStat = v.findViewById(R.id.tvStat);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Video v = data.get(pos);
        h.tvTitle.setText(v.title);
        h.tvUp.setText(v.upName.isEmpty() ? "免费源" : v.upName);
        h.tvStat.setText(v.play > 0 ? v.playText() : "");
        h.tvDuration.setText(v.durationText());
        Img.load(h.ivCover, v.cover);

        FocusAnim.bind(h.itemView);
        h.itemView.setOnClickListener(view -> {
            if (onClick != null) onClick.onClick(v);
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }
}
