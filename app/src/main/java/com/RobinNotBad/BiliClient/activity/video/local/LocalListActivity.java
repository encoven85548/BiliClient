package com.RobinNotBad.BiliClient.activity.video.local;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity;
import com.RobinNotBad.BiliClient.adapter.video.LocalVideoAdapter;
import com.RobinNotBad.BiliClient.model.LocalVideo;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.FileUtil;
import com.RobinNotBad.BiliClient.util.LocalProgressUtil;
import com.RobinNotBad.BiliClient.util.MsgUtil;

import java.io.File;
import java.util.ArrayList;

//本地列表
//2023-08-07

public class LocalListActivity extends InstanceActivity {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private final ArrayList<LocalVideo> videoList = new ArrayList<>(10);
    private LocalVideoAdapter adapter;
    private TextView emptyTip;

    private int longClickPosition = -1;
    private long longClickTimestamp;

    private boolean started;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_main_refresh);
        setMenuClick();

        recyclerView = findViewById(R.id.recyclerView);

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(this::refresh);

        emptyTip = findViewById(R.id.emptyTip);

        TextView pageName = findViewById(R.id.pageName);
        pageName.setText("缓存");

        if (!FileUtil.checkStoragePermission()) {
            FileUtil.requestStoragePermission(this);
        }

        // 先把 adapter 挂上去：列表、下拉刷新立刻就能用（此时列表里只有"下载列表"入口），
        // 目录扫描放到后面异步做，扫完再把条目插进来。
        // 以前是等 scan() 扫完整个缓存目录才 setAdapter，慢速存储上打开"缓存"页要空等一两秒才有内容。
        adapter = new LocalVideoAdapter(this, videoList);

        adapter.setOnLongClickListener(position -> {
            // 防御：position 是 adapter 现取的，但这一瞬间列表也可能刚被别处刷新/清空过。
            // 之前这里不检查，配合 adapter 里"绑定时的旧下标"，连续删除后删最后一项会直接
            // IndexOutOfBoundsException（Index: N, Size: N）崩掉。
            if (position < 0 || position >= videoList.size()) return;

            long timestamp = System.currentTimeMillis();
            if (longClickPosition == position && timestamp - longClickTimestamp < 4000) {
                // 先把文件夹名取出来：下面 remove 之后 position 就可能越界了
                final String title = videoList.get(position).title;

                // 磁盘相关的操作全部丢到后台线程。
                // FileUtil.getVideoDownloadPath() 里有 nomedia 的 exists/createNewFile/delete，
                // 在手表这种慢速存储上每个都是几十毫秒级的系统调用，放在主线程就是"删一次卡一下"。
                // 顺便把这个视频的本地播放进度记录清掉，免得以后重新缓存同名视频时续播到旧位置。
                CenterThreadPool.run(() -> {
                    File folder = new File(FileUtil.getVideoDownloadPath(), title);
                    FileUtil.deleteFolder(folder);
                    LocalProgressUtil.removeTree(folder.getPath());
                });

                MsgUtil.showMsg("删除成功");
                videoList.remove(position);
                // 只发 notifyItemRemoved 就够：它会把后面的行重绘并挪动位置，
                // 但【不会重新绑定】这些行（监听器还是原来那个），
                // 所以 adapter 里的点击/长按必须现取 getAdapterPosition()，不能捕获 bind 时的下标；
                // 再补 notifyItemRangeChanged 只会多触发一批"变化动画"（每项多造一个 View），
                // 低端设备上正好就是那一下卡顿的来源。
                adapter.notifyItemRemoved(position + 1);
                longClickPosition = -1;
                checkEmpty();
            } else {
                longClickPosition = position;
                longClickTimestamp = timestamp;
                MsgUtil.showMsg("再次长按删除");
            }
        });

        recyclerView.setLayoutManager(getLayoutManager());
        recyclerView.setAdapter(adapter);
        // 等一次布局之后再开转圈，否则 SwipeRefreshLayout 可能算不出指示器的位置
        swipeRefreshLayout.post(() -> swipeRefreshLayout.setRefreshing(true));

        CenterThreadPool.run(() -> {
            scan(FileUtil.getVideoDownloadPath());
            runOnUiThread(() -> {
                // 刚打开时列表必定是空的，所以全部都是"新增"，用插入通知比 notifyDataSetChanged 便宜得多
                adapter.notifyItemRangeInserted(1, videoList.size());
                swipeRefreshLayout.setRefreshing(false);
                started = true;
            });
        });
    }

    private void scan(File folder) {
        File[] files = folder.listFiles();
        if (files == null)
            return;

        for (File video : files) {
            if (video.isDirectory()) {
                LocalVideo localVideo = new LocalVideo();
                localVideo.title = video.getName();

                localVideo.cover = (new File(video, "cover.png")).toString();

                localVideo.pageList = new ArrayList<>();
                localVideo.danmakuFileList = new ArrayList<>();
                localVideo.videoFileList = new ArrayList<>();
                localVideo.sizeList = new ArrayList<>();

                File videoFile = new File(video, "video.mp4");
                File audioFile = new File(video, "audio.m4a"); // 仅音频文件
                File danmakuFile = new File(video, "danmaku.xml");

                // 优先检查视频文件，其次检查音频文件
                File mediaFile = videoFile.exists() ? videoFile : (audioFile.exists() ? audioFile : null);

                if (mediaFile != null) {
                    File mark = new File(video, ".DOWNLOADING");
                    if (mark.exists())
                        continue;

                    localVideo.sizeList.add(mediaFile.length());
                    localVideo.videoFileList.add(mediaFile.toString());
                    localVideo.danmakuFileList.add(danmakuFile.toString()); // 单集视频

                    localVideo.calcTotalSize();
                    videoList.add(localVideo);
                } else {
                    File[] pages = video.listFiles(); // 分页视频
                    if (pages != null) {
                        for (File page : pages) {
                            if (page.isDirectory()) {
                                File mark = new File(page, ".DOWNLOADING");
                                if (mark.exists())
                                    continue;

                                File pageVideoFile = new File(page, "video.mp4");
                                File pageAudioFile = new File(page, "audio.m4a"); // 仅音频文件
                                File pageDanmakuFile = new File(page, "danmaku.xml");

                                // 优先检查视频文件，其次检查音频文件
                                File pageMediaFile = pageVideoFile.exists() ? pageVideoFile
                                        : (pageAudioFile.exists() ? pageAudioFile : null);

                                if (pageMediaFile != null) {
                                    localVideo.pageList.add(page.getName());
                                    localVideo.sizeList.add(pageMediaFile.length());
                                    localVideo.videoFileList.add(pageMediaFile.toString());
                                    localVideo.danmakuFileList.add(pageDanmakuFile.toString());
                                }
                            }
                        }
                        localVideo.calcTotalSize();
                        if (localVideo.videoFileList.size() > 0)
                            videoList.add(localVideo);
                    }
                }
            }
        }
        checkEmpty();
    }

    private void checkEmpty() {
        runOnUiThread(() -> {
            if (videoList.isEmpty() && emptyTip != null) {
                emptyTip.setVisibility(View.VISIBLE);
            } else {
                if (emptyTip != null) {
                    emptyTip.setVisibility(View.GONE);
                }
            }
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    public void refresh() {
        if (started)
            CenterThreadPool.run(() -> {
                runOnUiThread(() -> swipeRefreshLayout.setRefreshing(true));
                videoList.clear();
                scan(FileUtil.getVideoDownloadPath());
                runOnUiThread(() -> {
                    // 整个列表都重新扫过了、对象全是新的，必须整体刷新。
                    // 以前用的是 notifyItemRangeChanged(1, 旧长度)：长度对不上时位置会错乱，
                    // 而且每一项都会跑一次"变化动画"（每项要多复制一个 View、重新加载封面），
                    // 低性能设备上就是刷新一下卡一下。这里只重绑、不做变化动画。
                    adapter.notifyDataSetChanged();
                    swipeRefreshLayout.setRefreshing(false);
                });
            });
    }
}