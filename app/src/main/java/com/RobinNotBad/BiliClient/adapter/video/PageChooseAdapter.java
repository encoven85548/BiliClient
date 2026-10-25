package com.RobinNotBad.BiliClient.adapter.video;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.listener.OnItemClickListener;
import com.RobinNotBad.BiliClient.listener.OnItemLongClickListener;

import java.util.ArrayList;

//分页视频选集
//2023-08-29

public class PageChooseAdapter extends RecyclerView.Adapter<PageChooseAdapter.Holder> {

    final Context context;
    final ArrayList<String> nameList;

    OnItemClickListener onItemClickListener;
    OnItemLongClickListener onItemLongClickListener;

    public PageChooseAdapter(Context context, ArrayList<String> nameList) {
        this.context = context;
        this.nameList = nameList;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.onItemLongClickListener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(this.context).inflate(R.layout.cell_choose, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        if (position < 0 || position >= nameList.size())
            return;

        holder.folder_name.setText(nameList.get(position));

        // 和 LocalVideoAdapter 一样：删掉一项之后 RecyclerView 只会挪动已绑定 View 的位置，
        // 并不会重新绑定（监听器还是原来那个），bind 时捕获的 position 就会变成旧值。
        // 结果就是"删 b 之后点 c 实际点到 d"，连续删后再删最后一项还会数组越界，
        // 所以这里必须现取 holder.getAdapterPosition()。
        holder.itemView.setOnClickListener(view -> {
            if (onItemClickListener == null) return;
            int index = holder.getAdapterPosition();
            if (index < 0 || index >= nameList.size()) return;
            onItemClickListener.onItemClick(index);
        });

        holder.itemView.setOnLongClickListener(view -> {
            if (onItemLongClickListener == null) return false;
            int index = holder.getAdapterPosition();
            if (index < 0 || index >= nameList.size()) return true; // 位置已失效，忽略这次长按
            onItemLongClickListener.onItemLongClick(index);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return nameList != null ? nameList.size() : 0;
    }

    public static class Holder extends RecyclerView.ViewHolder {
        final TextView folder_name;

        public Holder(@NonNull View itemView) {
            super(itemView);
            folder_name = itemView.findViewById(R.id.text);
        }
    }
}
