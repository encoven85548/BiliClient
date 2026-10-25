package com.RobinNotBad.BiliClient.activity.video.local;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity;
import com.RobinNotBad.BiliClient.adapter.video.PageChooseAdapter;
import com.RobinNotBad.BiliClient.api.PlayerApi;
import com.RobinNotBad.BiliClient.model.PlayerData;
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.FileUtil;
import com.RobinNotBad.BiliClient.util.LocalProgressUtil;
import com.RobinNotBad.BiliClient.util.MsgUtil;

import java.io.File;
import java.util.ArrayList;

//分页视频选集
//2023-07-17

public class LocalPageChooseActivity extends BaseActivity {

    private int longClickPosition = -1;
    private long longClickTimestamp;
    private boolean deleted = false;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_list);
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        findViewById(R.id.top).setOnClickListener(view -> finish());

        TextView textView = findViewById(R.id.pageName);
        textView.setText("请选择分页");

        Intent intent = getIntent();
        String title = intent.getStringExtra("title");
        ArrayList<String> pageList = intent.getStringArrayListExtra("pageList");
        ArrayList<String> videoFileList = intent.getStringArrayListExtra("videoFileList");
        ArrayList<String> danmakuFileList = intent.getStringArrayListExtra("danmakuFileList");

        // 三个列表缺一个就没法玩：getStringArrayListExtra 在没这个 extra 时返回 null，
        // 后面 size()/get() 会直接 NPE，所以先兜住
        if (pageList == null || videoFileList == null || danmakuFileList == null) {
            MsgUtil.showMsg("分页信息不完整，请重新进入");
            finish();
            return;
        }

        PageChooseAdapter adapter = new PageChooseAdapter(this, pageList);
        adapter.setOnItemClickListener(position -> {
            // 防御：三个列表必须同时有这一项，否则下面 get() 会越界
            if (position < 0 || position >= pageList.size()
                    || position >= videoFileList.size() || position >= danmakuFileList.size())
                return;

            PlayerData playerData = new PlayerData(PlayerData.TYPE_LOCAL);
            String mediaPath = videoFileList.get(position);
            playerData.videoUrl = mediaPath;
            playerData.danmakuUrl = danmakuFileList.get(position);
            playerData.title = pageList.get(position);
            // 缓存视频的进度存在本地（毫秒），jumpToPlayer 的 progress 单位是秒
            playerData.progress = (int) (LocalProgressUtil.get(mediaPath) / 1000);
            try {
                Intent player = PlayerApi.jumpToPlayer(playerData);
                // 如果是仅音频文件，标记为音频模式
                if (mediaPath.endsWith("audio.m4a")) {
                    player.putExtra("audio_only", true);
                }
                startActivity(player);
            } catch (ActivityNotFoundException e) {
                MsgUtil.showMsg("没有找到播放器，请检查是否安装");
            } catch (Exception e) {
                MsgUtil.err(e);
            }
        });
        adapter.setOnItemLongClickListener(position -> {
            // 同上：删过一项之后如果还拿到旧下标，这里 pageList.get(position) 会直接崩
            if (position < 0 || position >= pageList.size()
                    || position >= videoFileList.size() || position >= danmakuFileList.size())
                return;

            long timestamp = System.currentTimeMillis();
            if (longClickPosition == position && timestamp - longClickTimestamp < 4000) {
                final String pageName = pageList.get(position);
                final boolean lastPage = pageList.size() == 1;

                // 磁盘操作放后台线程，列表的改动与 notify 全部留在主线程。
                // 以前是在子线程里 pageList.remove()，同时主线程这边 notifyItemRemoved，
                // 两边抢同一个 List，RecyclerView 会读到"数量对不上"的中间状态而重新布局，
                // 表现就是删一次卡一下（偶尔还会报 Adapter 错误）。
                CenterThreadPool.run(() -> {
                    File videoPath = new File(FileUtil.getVideoDownloadPath(), title);
                    File pagePath = new File(videoPath, pageName);
                    FileUtil.deleteFolder(pagePath);
                    LocalProgressUtil.removeTree(pagePath.getPath()); // 顺手清掉这一集的播放进度
                    if (lastPage) {
                        FileUtil.deleteFolder(videoPath); // 最后一集删掉之后把整个文件夹也清掉
                        LocalProgressUtil.removeTree(videoPath.getPath());
                    }
                });

                pageList.remove(position);
                videoFileList.remove(position);
                danmakuFileList.remove(position);
                // 只发 notifyItemRemoved：它会挪动后面行的位置但不会重新绑定它们，
                // 所以 PageChooseAdapter 里的点击/长按已经改成现取 getAdapterPosition()。
                adapter.notifyItemRemoved(position);

                MsgUtil.showMsg("删除成功");
                longClickPosition = -1;

                deleted = true;
            } else {
                longClickPosition = position;
                longClickTimestamp = timestamp;
                MsgUtil.showMsg("再次长按删除");
            }
        });

        recyclerView.setLayoutManager(new CustomLinearManager(this));
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onDestroy() {
        InstanceActivity instance = BiliTerminal.getInstanceActivityOnTop();
        if (deleted && instance instanceof LocalListActivity && !instance.isDestroyed())
            ((LocalListActivity) (instance)).refresh();
        super.onDestroy();
    }
}