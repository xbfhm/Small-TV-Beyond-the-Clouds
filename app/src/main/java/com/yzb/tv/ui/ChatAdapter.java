package com.yzb.tv.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yzb.tv.R;
import com.yzb.tv.util.CookieStore;
import com.yzb.tv.util.Img;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 聊天消息气泡适配器 */
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {

    private final List<JSONObject> data = new ArrayList<>();
    private String myFace = "";
    private String otherFace = "";

    public void setFaces(String my, String other) {
        myFace = my;
        otherFace = other;
    }

    public void setData(List<JSONObject> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    public void add(JSONObject msg) {
        data.add(msg);
        notifyItemInserted(data.size() - 1);
    }

    static class VH extends RecyclerView.ViewHolder {
        LinearLayout boxMe, boxOther;
        ImageView ivFaceMe, ivFaceOther;
        TextView tvMsgMe, tvMsgOther;

        VH(View v) {
            super(v);
            boxMe = v.findViewById(R.id.boxMe);
            boxOther = v.findViewById(R.id.boxOther);
            ivFaceMe = v.findViewById(R.id.ivFaceMe);
            ivFaceOther = v.findViewById(R.id.ivFaceOther);
            tvMsgMe = v.findViewById(R.id.tvMsgMe);
            tvMsgOther = v.findViewById(R.id.tvMsgOther);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_msg, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        JSONObject o = data.get(pos);
        boolean mine = o.optLong("sender_uid", 0) == CookieStore.mid;

        h.boxMe.setVisibility(mine ? View.VISIBLE : View.GONE);
        h.boxOther.setVisibility(mine ? View.GONE : View.VISIBLE);

        int msgType = o.optInt("msg_type", 1);
        String text;
        if (msgType == 1) text = o.optString("content", "");
        else if (msgType == 2) text = "[图片]";
        else if (msgType == 7) text = "[表情]";
        else if (msgType == 9) text = "[视频]";
        else if (msgType == 5) text = "[撤回了一条消息]";
        else text = "[消息]";

        if (mine) {
            h.tvMsgMe.setText(text);
            Img.load(h.ivFaceMe, myFace);
        } else {
            h.tvMsgOther.setText(text);
            Img.load(h.ivFaceOther, otherFace);
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }
}
