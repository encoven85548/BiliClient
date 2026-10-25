package com.RobinNotBad.BiliClient.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.RobinNotBad.BiliClient.BiliTerminal;

import java.util.Map;

/**
 * 缓存（本地）视频的播放进度。
 * <p>
 * 在线视频的进度是上报到 B 站历史接口（靠 aid/cid），而缓存视频没有 aid，
 * 那条路根本走不通 —— 所以本地文件单独存一份：
 * key = 媒体文件路径（video.mp4 / audio.m4a），value = 播放位置（毫秒）。
 * <p>
 * 用一份独立的 SharedPreferences（local_play_progress），不混进设置里，
 * 免得被"设置备份"一起导出、也不会污染设置项列表。
 */
public class LocalProgressUtil {

    private static final String PREF_NAME = "local_play_progress";
    /** 小于这个位置（毫秒）不记录：刚点开就退出没必要续播 */
    private static final long MIN_KEEP_MS = 5000;
    /** 离结尾小于这个距离（毫秒）就当作看完了，清掉记录，免得下次一打开就播完 */
    private static final long TAIL_MS = 5000;

    private LocalProgressUtil() {
    }

    private static SharedPreferences prefs() {
        return BiliTerminal.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 预热：第一次 getSharedPreferences 会把整个文件读出来并解析 XML。
     * 在懒加载写法下这个开销会落在"播放器退出的 onPause/finish"或"点缓存条目"这种
     * 恰好不该卡的地方（低端手表上能看出掉帧）。所以在 Application 启动时丢到后台线程先读一次，
     * 之后所有调用都只是内存操作。
     */
    public static void warmUp() {
        try {
            prefs();
        } catch (Exception ignored) {
        }
    }

    /**
     * 读取某个本地视频的播放进度。
     *
     * @param mediaPath 媒体文件路径（video.mp4 / audio.m4a）
     * @return 播放位置，单位毫秒；没有记录返回 0
     */
    public static long get(String mediaPath) {
        if (mediaPath == null || mediaPath.isEmpty()) return 0;
        try {
            return prefs().getLong(mediaPath, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 保存播放进度。
     *
     * @param mediaPath  媒体文件路径
     * @param positionMs 当前播放位置（毫秒）
     * @param durationMs 视频总时长（毫秒），未知时传 0 或负数
     */
    public static void save(String mediaPath, long positionMs, long durationMs) {
        if (mediaPath == null || mediaPath.isEmpty()) return;
        try {
            if (positionMs < MIN_KEEP_MS
                    || (durationMs > 0 && positionMs >= durationMs - TAIL_MS)) {
                prefs().edit().remove(mediaPath).apply();
                return;
            }
            prefs().edit().putLong(mediaPath, positionMs).apply();
        } catch (Exception ignored) {
        }
    }

    /** 删除某个文件（或其所在文件夹下所有文件）的进度记录 */
    public static void removeTree(String path) {
        if (path == null || path.isEmpty()) return;
        try {
            String prefix = path.endsWith("/") ? path : path + "/";
            SharedPreferences.Editor editor = prefs().edit();
            boolean changed = false;
            for (Map.Entry<String, ?> entry : prefs().getAll().entrySet()) {
                String key = entry.getKey();
                if (key.equals(path) || key.startsWith(prefix)) {
                    editor.remove(key);
                    changed = true;
                }
            }
            if (changed) editor.apply();
        } catch (Exception ignored) {
        }
    }
}
