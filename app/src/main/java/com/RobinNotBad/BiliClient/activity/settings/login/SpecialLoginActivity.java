package com.RobinNotBad.BiliClient.activity.settings.login;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.BuildConfig;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.SplashActivity;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.util.FileUtil;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONException;
import org.json.JSONObject;

public class SpecialLoginActivity extends BaseActivity {

    private EditText textInput;

    @SuppressLint({"MissingInflatedId", "SetTextI18n"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_special);
        Logu.i("debug", "使用特殊登录方式");

        textInput = findViewById(R.id.loginInput);
        MaterialCardView confirm = findViewById(R.id.confirm);
        MaterialCardView refuse = findViewById(R.id.refuse);
        MaterialCardView copy = findViewById(R.id.copy);
        TextView desc = findViewById(R.id.desc);

        Intent intent = getIntent();

        if (intent.getBooleanExtra("login", true)) {
            refuse.setOnClickListener(v -> {
                if (getIntent().getBooleanExtra("from_setup", false))
                    startActivity(new Intent(this, SplashActivity.class));
                else finish();
            });

            confirm.setOnClickListener(view -> {
                String loginInfo = textInput.getText().toString();

                JSONObject jsonObject;
                String cookies;
                try {
                    jsonObject = new JSONObject(loginInfo);
                    cookies = jsonObject.optString("cookies", "");
                } catch (JSONException e) {
                    runOnUiThread(() -> MsgUtil.showMsg("请检查输入的内容，不要有多余空格或字符"));
                    return;
                }

                // 【先校验、再落盘】以前是先把 mid/cookies 写进 SharedPreferences、再写 LoginData.txt，
                // 于是粘进来一份 cookies 为空的 JSON 时：mid 变成 0（等于悄悄退出登录），
                // 同时还用空数据把 LoginData.txt 里原来能用的登录信息覆盖掉 —— 表现就是"登录信息丢了"。
                // 现在只要不是一份有效的登录（没有 cookies / 没有 SESSDATA / DedeUserID 无效）就直接取消，
                // 一个字段都不改，原来的登录状态和备份都保持原样。
                long uid;
                try {
                    uid = Long.parseLong(NetWorkUtil.getInfoFromCookie("DedeUserID", cookies));
                } catch (Exception e) {
                    uid = 0;
                }
                if (cookies.isEmpty() || !cookies.contains("SESSDATA") || uid <= 0) {
                    runOnUiThread(() -> MsgUtil.showMsg("登录信息无效：缺少 cookies / SESSDATA / DedeUserID\n已取消，原有登录状态没有被改动"));
                    return;
                }

                SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, uid);
                SharedPreferencesUtil.putString(SharedPreferencesUtil.csrf, NetWorkUtil.getInfoFromCookie("bili_jct", cookies));
                SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, cookies);
                SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token, jsonObject.optString("refresh_token", ""));
                SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.setup, true);
                runOnUiThread(() -> MsgUtil.showMsg("登录成功！"));

                // 测试版(非正式版)：把登录信息写入 LoginData.txt，方便更新版本后免登录。
                // 必须"确认已登录"才写（上面已校验过，这里再兜一层），绝不用空数据覆盖原来的备份。
                if (BuildConfig.BETA && SharedPreferencesUtil.isLoggedIn()) {
                    FileUtil.saveLoginData(loginInfo);
                }

                NetWorkUtil.refreshHeaders();

                Intent intent1 = new Intent();
                intent1.setClass(SpecialLoginActivity.this, SplashActivity.class);
                startActivity(intent1);
                finish();
            });
        } else {
            desc.setText(R.string.special_login_export);

            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("cookies", SharedPreferencesUtil.getString("cookies", ""));
                jsonObject.put("refresh_token", SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, ""));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            textInput.setText(jsonObject.toString());
            textInput.clearFocus();

            refuse.setVisibility(View.GONE);
            if (BiliTerminal.isDebugBuild()) {
                confirm.setOnClickListener(v -> {
                    try {
                        JSONObject input = new JSONObject(textInput.getText().toString());
                        String cookies = input.getString("cookies");
                        SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, cookies);
                        runOnUiThread(() -> MsgUtil.showMsg("导入cookies成功"));

                        NetWorkUtil.refreshHeaders();
                    } catch (JSONException e) {
                        runOnUiThread(() -> MsgUtil.showMsg("请检查输入的内容，不要有多余空格或字符"));
                    }
                });
            } else confirm.setVisibility(View.GONE);
            copy.setOnClickListener((view) -> {
                ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clipData = ClipData.newPlainText("label", textInput.getText());
                clipboardManager.setPrimaryClip(clipData);
                MsgUtil.showMsg("已复制");
            });
        }
    }

}