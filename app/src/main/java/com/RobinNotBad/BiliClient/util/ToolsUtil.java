package com.RobinNotBad.BiliClient.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.BuildConfig;
import com.RobinNotBad.BiliClient.R;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

//2023-07-25

public class ToolsUtil {

    public static int dp2px(float dpValue) {
        final float scale = BiliTerminal.context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    public static int sp2px(float spValue) {
        final float fontScale = BiliTerminal.context.getResources()
                .getDisplayMetrics().scaledDensity;
        return (int) (spValue * fontScale + 0.5f);
    }

    public static String md5(String plainText) {
        byte[] secretBytes;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(plainText.getBytes());
            secretBytes = md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("没有md5这个算法！");
        }
        StringBuilder md5code = new StringBuilder(new BigInteger(1, secretBytes).toString(16));
        for (int i = 0; i < 32 - md5code.length(); i++) {
            md5code.insert(0, "0");
        }
        return md5code.toString();
    }


    public static String getUpdateLog(Context context) {
        StringBuilder str = new StringBuilder();
        String[] logItems = context.getResources().getStringArray(R.array.update_log_items);
        for (int i = 0; i < logItems.length; i++)
            str.append("\n").append((i + 1)).append(".").append(logItems[i]);
        return str.toString();
    }

    public static boolean isDebugBuild() {
        return BuildConfig.BETA;
    }

    public static int getRgb888(int color) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append((color >> 16) & 0xff);
        stringBuilder.append((color >> 8) & 0xff);
        stringBuilder.append((color) & 0xff);
        Log.e("颜色", stringBuilder.toString());
        return Integer.parseInt(stringBuilder.toString());
    }

    /**
     * 是不是低内存设备（Android 4.4 / API 19 才有这个 API，更低版本按"不是"处理）。
     * 用来关掉一些"拿内存换渲染速度"的开关，比如弹幕绘制缓存。
     */
    public static boolean isLowRamDevice() {
        try {
            if (android.os.Build.VERSION.SDK_INT < 19) return false;
            android.app.ActivityManager manager = (android.app.ActivityManager)
                    BiliTerminal.context.getSystemService(Context.ACTIVITY_SERVICE);
            return manager != null && manager.isLowRamDevice();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 当前有没有可用网络。
     * <p>
     * 用来区分两件不同的事：<b>没网</b>（那就安静地走离线流程，别弹"网络错误"）
     * 和 <b>有网但请求失败</b>（这才值得提示）。
     * <p>
     * 判断不出来时返回 true（宁可当作有网，也不要因为拿不到网络状态而误伤正常流程）。
     */
    public static boolean isNetworkAvailable() {
        try {
            Context context = BiliTerminal.context;
            if (context == null) return true;
            ConnectivityManager manager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return true;
            NetworkInfo info = manager.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) {
            return true;
        }
    }

}
