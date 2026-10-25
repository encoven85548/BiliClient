package com.RobinNotBad.BiliClient.adapter.video;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.video.local.DownloadListActivity;
import com.RobinNotBad.BiliClient.activity.video.local.LocalPageChooseActivity;
import com.RobinNotBad.BiliClient.api.PlayerApi;
import com.RobinNotBad.BiliClient.listener.OnItemLongClickListener;
import com.RobinNotBad.BiliClient.model.LocalVideo;
import com.RobinNotBad.BiliClient.model.PlayerData;
import com.RobinNotBad.BiliClient.util.GlideUtil;
import com.RobinNotBad.BiliClient.util.LocalProgressUtil;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.ToolsUtil;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.Locale;

//本地视频Adapter
//2023-10-04

public class LocalVideoAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    final Context context;
    final ArrayList<LocalVideo> localVideoList;
    OnItemLongClickListener longClickListener;

    public LocalVideoAdapter(Context context, ArrayList<LocalVideo> localVideoList) {
        this.context = context;
        this.localVideoList = localVideoList;
    }

    public void setOnLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == 0) {
            View view = LayoutInflater.from(this.context).inflate(R.layout.cell_video_local, parent, false);
            return new LocalVideoHolder(view);
        } else {
            View view = LayoutInflater.from(this.context).inflate(R.layout.cell_goto, parent, false);
            return new GotoHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (position != 0) {
            int realPosition = position - 1;
            if (realPosition < 0 || realPosition >= localVideoList.size())
                return;
            LocalVideo localVideo = localVideoList.get(realPosition);
            if (localVideo == null)
                return;

            ((LocalVideoHolder) holder).showLocalVideo(localVideo, context);

            // 【注意】位置绝对不能在 bind 时捕获后长期使用：
            // notifyItemRemoved 之后，RecyclerView 只会把"已经绑定过"的 View 的 adapter 位置往前挪，
            // 并不会重新调用 onBindViewHolder（监听器还是原来那个 lambda），
            // 于是捕获下来的 position 就变成旧值：
            //   · 删掉 b 之后再长按 c，实际删掉的是 d（差一位）
            //   · 连续删过之后再删最后一项，旧下标超出列表长度 → IndexOutOfBoundsException
            // 所以点/长按时要重新向 holder 问一次"你现在是第几项"。
            holder.itemView.setOnClickListener(view -> {
                int index = holder.getAdapterPosition() - 1;
                if (index < 0 || index >= localVideoList.size())
                    return;
                LocalVideo video = localVideoList.get(index);
                if (video == null)
                    return;

                if (video.videoFileList != null && video.videoFileList.size() == 1) {
                    PlayerData playerData = new PlayerData(PlayerData.TYPE_LOCAL);
                    String mediaPath = video.videoFileList.get(0);
                    playerData.videoUrl = mediaPath;
                    if (video.danmakuFileList != null && !video.danmakuFileList.isEmpty()) {
                        playerData.danmakuUrl = video.danmakuFileList.get(0);
                    }
                    playerData.title = video.title;
                    // 缓存视频没有服务端历史，读本地存的播放进度（毫秒 → 秒，
                    // 因为 jumpToPlayer 传出去的 progress 单位是秒）
                    playerData.progress = (int) (LocalProgressUtil.get(mediaPath) / 1000);

                    try {
                        Intent player = PlayerApi.jumpToPlayer(playerData);
                        // 如果是仅音频文件，标记为音频模式
                        if (mediaPath.endsWith("audio.m4a")) {
                            player.putExtra("audio_only", true);
                        }
                        context.startActivity(player);
                    } catch (ActivityNotFoundException e) {
                        MsgUtil.showMsg("跳转失败");
                        e.printStackTrace();
                    }
                } else if (video.videoFileList != null && video.videoFileList.size() > 1) {
                    Intent intent = new Intent();
                    intent.setClass(context, LocalPageChooseActivity.class);
                    intent.putExtra("pageList", video.pageList);
                    intent.putExtra("videoFileList", video.videoFileList);
                    intent.putExtra("danmakuFileList", video.danmakuFileList);
                    intent.putExtra("title", video.title);
                    context.startActivity(intent);
                }
            });

            holder.itemView.setOnLongClickListener(view -> {
                if (longClickListener == null)
                    return false;
                // 同样必须现取位置：列表被删过之后，绑定时的下标已经不可信了
                int index = holder.getAdapterPosition() - 1;
                if (index < 0 || index >= localVideoList.size())
                    return true; // 位置已失效（正好被删/列表已刷新），忽略这次长按，别再拿旧下标去删
                longClickListener.onItemLongClick(index);
                return true;
            });
        } else
            ((GotoHolder) holder).show(context);
    }

    @Override
    public int getItemCount() {
        return localVideoList != null ? localVideoList.size() + 1 : 1;
    }

    public static class GotoHolder extends RecyclerView.ViewHolder {

        TextView textView;

        public GotoHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.text);
        }

        public void show(Context context) {
            textView.setText("下载列表");
            itemView.setOnClickListener(v -> context.startActivity(new Intent(context, DownloadListActivity.class)));
        }
    }

    public static class LocalVideoHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView extra;
        final ImageView cover;

        public LocalVideoHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.text_title);
            extra = itemView.findViewById(R.id.text_extra);
            cover = itemView.findViewById(R.id.img_cover);
        }

        @SuppressLint("SetTextI18n")
        public void showLocalVideo(LocalVideo videoCard, Context context) {
            title.setText(videoCard.title);
            String size = String.format(Locale.CHINA, "%.2f", videoCard.size / 1000000f) + "MB";
            extra.setText(size);

            try {
                Glide.with(BiliTerminal.context).asDrawable().load(videoCard.cover)
                        .transition(GlideUtil.getTransitionOptions())
                        .apply(RequestOptions.bitmapTransform(new RoundedCorners(ToolsUtil.dp2px(5))))
                        // 封面按 RGB_565 解码：内存占用减半，手表上少一点 GC 抖动
                        .format(DecodeFormat.PREFER_RGB_565)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(cover);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? 1 : 0;
    }
}
