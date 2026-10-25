package com.RobinNotBad.BiliClient.util;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

/**
 * 被 luern0313 创建于 2020/5/4.
 * #以下代码部分来源于腕上哔哩的开源项目，有修改。感谢开源者做出的贡献！
 */
public class SharedPreferencesUtil {
    public static final String LINK_ENABLE = "link_enable";
    public static final String RCMD_API_NEW_PARAM = "rcmd_api_new_param";
    public static final String MENU_SORT = "menu_sort";
    public static final String ASYNC_INFLATE_ENABLE = "async_inflate_enable";
    public static final String LOAD_TRANSITION = "load_transition";
    public static final String SNACKBAR_ENABLE = "snackbar_enable";
    public static final String STRICT_URL_MATCH = "strict_url_match";
    public static final String NO_VIP_COLOR = "no_vip_color";
    public static final String NO_MEDAL = "no_medal";
    public static final String REPLY_MARQUEE_NAME = "reply_marquee_name";
    public static final String NEW_DANMAKU_API = "new_danmaku_api";
    public static final String DYNAMIC_UPDATE_CHECK_ENABLE = "dynamic_update_check_enable";
    public static final String DYNAMIC_UPDATE_NUM = "dynamic_update_num";
    public static final String MESSAGE_UPDATE_CHECK_ENABLE = "message_update_check_enable";
    public static final String MESSAGE_UPDATE_NUM = "message_update_num";
    public static final String PRIVATE_MSG_UNREAD_BADGE_ENABLE = "private_msg_unread_badge_enable";
    public static final String PRIVATE_MSG_AUTO_READ_ENABLE = "private_msg_auto_read_enable";
    public static final String FOLLOW_GROUP_MODE = "follow_group_mode";
    public static final String NIGHT_REMINDER_ENABLE = "night_reminder_enable";
    public static final String SEARCH_DEFAULT_CONTENT_ENABLE = "search_default_content_enable";
    public static final String PLAYER_MEDIA_SESSION_ENABLE = "player_media_session_enable";
    public static final String AUTO_CHECK_UPDATE_ENABLE = "auto_check_update_enable";
    public static final String API_RETRY_MAX_TIMES = "api_retry_max_times";
    public static final String API_RETRY_INTERVAL_SECONDS = "api_retry_interval_seconds";

    public static String cookies = "cookies";
    public static String mid = "mid";
    public static String csrf = "csrf";
    public static String access_key = "access_key";
    public static String refresh_token = "refresh_token";
    public static String setup = "setup";
    public static String last_version = "last_version";
    public static String player = "player";
    public static String padding_horizontal = "padding_horizontal";
    public static String padding_vertical = "padding_vertical";
    public static String cookie_refresh = "cookie_refresh";
    public static String search_history = "search_history";
    public static String cover_play_enabled = "cover_play_enabled";
    public static String tutorial_version = "tutorial_version";

    public static SharedPreferences sharedPreferences;

    /**
     * 「自动备份」的防抖：开关（auto_backup_enable）打开后，
     * 每次改设置都延迟 2 秒重写一次备份文件；连续改一堆设置只会在停下来之后写一次，
     * 不会因为播放器/列表频繁写 pref 而疯狂刷磁盘。文件 I/O 放到线程池里做。
     */
    private static final Handler AUTO_BACKUP_HANDLER = new Handler(Looper.getMainLooper());
    private static final Runnable AUTO_BACKUP_TASK = () -> CenterThreadPool.run(() -> {
        try {
            BackupUtil.backupOnly();
        } catch (Exception e) {
            e.printStackTrace();
        }
    });

    public static SharedPreferences getSharedPreferences() {
        return sharedPreferences;
    }

    public static String getString(String key, String def) {
        return sharedPreferences.getString(key, def);
    }

    public static void putString(String key, String value) {
        sharedPreferences.edit().putString(key, value).apply();
        notifySettingChanged(key);
    }

    public static int getInt(String key, int def) {
        return sharedPreferences.getInt(key, def);
    }

    public static void putInt(String key, int value) {
        sharedPreferences.edit().putInt(key, value).apply();
        notifySettingChanged(key);
    }

    public static long getLong(String key, long def) {
        return sharedPreferences.getLong(key, def);
    }

    public static void putLong(String key, long value) {
        sharedPreferences.edit().putLong(key, value).apply();
        notifySettingChanged(key);
    }

    public static boolean getBoolean(String key, boolean def) {
        return sharedPreferences.getBoolean(key, def);
    }

    public static void putBoolean(String key, boolean value) {
        sharedPreferences.edit().putBoolean(key, value).apply();
        notifySettingChanged(key);
    }

    public static void putFloat(String key, float value) {
        sharedPreferences.edit().putFloat(key, value).apply();
        notifySettingChanged(key);
    }

    public static float getFloat(String key, float def) {
        return sharedPreferences.getFloat(key, def);
    }

    public static void removeValue(String key) {
        sharedPreferences.edit().remove(key).apply();
        notifySettingChanged(key);
    }

    /**
     * 当前是否已登录（mid 和 cookies 都非空）。
     * <p>
     * 专门给"自动备份登录信息"用：没登录时绝不能拿空数据去覆盖 login.txt / LoginData.txt，
     * 否则原来能用的登录备份就被冲掉了。
     */
    public static boolean isLoggedIn() {
        try {
            if (sharedPreferences == null) return false;
            return sharedPreferences.getLong(mid, 0) != 0
                    && !TextUtils.isEmpty(sharedPreferences.getString(cookies, ""));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 设置被修改后调用：若「自动备份」开着，就把备份文件延迟重写一次。
     * 只写备份文件，不做任何"加载"；加载备份仍然只能在实验室里手动点「加载」。
     *
     * @param key 被修改的键。运行态/登录态的数据（未读数、版本号、cookie 等，
     *            也就是 BackupUtil 里 EXCLUDE_KEYS 那些）不算"设置改动"，不触发备份，
     *            否则光启动时写几个未读数就会把备份文件重写一遍。
     */
    public static void notifySettingChanged(String key) {
        try {
            if (sharedPreferences == null) return;
            // 注意：这里用的是新键 auto_backup_enable，不是旧的 backup_restore_enable。
            // 旧键的语义是"启动时自动恢复"，沿用它会让升级上来的用户（旧开关是开的）
            // 在第一次启动时就把自己原来的备份文件覆盖掉，所以必须换成新键、默认关闭。
            if (!sharedPreferences.getBoolean("auto_backup_enable", false)) return;
            if (!BackupUtil.isBackedUpKey(key)) return;
            AUTO_BACKUP_HANDLER.removeCallbacks(AUTO_BACKUP_TASK);
            AUTO_BACKUP_HANDLER.postDelayed(AUTO_BACKUP_TASK, 2000);
        } catch (Exception ignored) {
        }
    }
}
