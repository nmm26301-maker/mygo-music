package com.mygo.music;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Tests an explicitly selected song using the same player as normal playback. */
final class PluginCheckDialog {
    interface Player { void play(JSONObject track); }
    interface PlayerState { PlaybackService.Snapshot get(); }

    private final Activity activity;
    private final PluginManager manager;
    private final PluginManager.PluginInfo source;
    private final Runnable account;
    private final Runnable settings;
    private final Player player;
    private final PlayerState playback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<JSONObject> tracks = new ArrayList<>();
    private AlertDialog dialog;
    private TextView status;
    private EditText query;
    private Spinner songs;
    private Button listen;
    private long generation;
    private boolean closed;

    PluginCheckDialog(Activity activity, PluginManager manager, PluginManager.PluginInfo source, Runnable account,
            Runnable settings, Player player, PlayerState playback) {
        this.activity = activity;
        this.manager = manager;
        this.source = source;
        this.account = account;
        this.settings = settings;
        this.player = player;
        this.playback = playback;
    }

    void show() {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(16 * activity.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);
        query = new EditText(activity);
        query.setSingleLine(true);
        query.setHint("输入想检查的歌曲");
        box.addView(query);

        status = new TextView(activity);
        status.setTextColor(Color.WHITE);
        status.setTextSize(15);
        status.setPadding(0, pad, 0, pad);
        status.setText("使用当前插件账号和播放音质。搜索后选择歌曲，点按试听会切换到该曲。");
        box.addView(status);

        songs = new Spinner(activity);
        songs.setContentDescription("选择测试歌曲");
        songs.setEnabled(false);
        box.addView(songs);

        LinearLayout actions = new LinearLayout(activity);
        Button login = new Button(activity);
        login.setText("账号登录");
        login.setEnabled(source.supports("account") || source.supports("signIn"));
        login.setOnClickListener(view -> account.run());
        actions.addView(login, new LinearLayout.LayoutParams(0, -2, 1));

        Button configure = new Button(activity);
        configure.setText("权限与设置");
        configure.setOnClickListener(view -> settings.run());
        actions.addView(configure, new LinearLayout.LayoutParams(0, -2, 1));

        listen = new Button(activity);
        listen.setText("试听结果");
        listen.setEnabled(false);
        listen.setOnClickListener(view -> listen());
        actions.addView(listen, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(actions);

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(box);
        dialog = new AlertDialog.Builder(activity).setTitle(source.name + " · 可用性检查")
            .setView(scroll).setPositiveButton("开始检查", null).setNegativeButton("关闭", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> search()));
        dialog.setOnDismissListener(ignored -> {
            closed = true;
            handler.removeCallbacksAndMessages(null);
        });
        dialog.show();
    }

    private boolean current(long ticket) {
        return !closed && !activity.isDestroyed() && ticket == generation;
    }

    private void message(long ticket, String value, boolean finished) {
        if (!current(ticket)) return;
        status.setText(value);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(finished);
        listen.setEnabled(finished && !tracks.isEmpty());
        songs.setEnabled(finished && !tracks.isEmpty());
    }

    private static String reason(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "原因未知";
        int newline = raw.indexOf('\n');
        return raw.startsWith("@mygo:") && newline >= 0 ? raw.substring(newline + 1) : raw;
    }

    private void search() {
        String keyword = query.getText().toString().trim();
        if (keyword.isEmpty()) {
            query.setError("请输入歌曲名称");
            return;
        }
        long ticket = ++generation;
        tracks.clear();
        songs.setAdapter(null);
        message(ticket, "正在检查权限并搜索…", false);
        manager.authorizeSource(source.id, (allowed, permission) -> {
            if (!current(ticket)) return;
            if (!allowed) {
                message(ticket, reason(permission), true);
                return;
            }
            if (!source.supports("search")) {
                message(ticket, "此插件未提供歌曲搜索，请从其主页浏览。", true);
                return;
            }
            manager.searchSource(source.id, keyword, (ok, raw) -> searchResult(ticket, ok, raw));
        });
    }

    private void searchResult(long ticket, boolean ok, String raw) {
        if (!current(ticket)) return;
        if (!ok) {
            message(ticket, "搜索未通过\n" + reason(raw), true);
            return;
        }
        try {
            JSONArray result = new JSONObject(raw).optJSONArray("tracks");
            ArrayList<String> labels = new ArrayList<>();
            if (result != null) {
                for (int i = 0; i < result.length() && tracks.size() < 50; i++) {
                    JSONObject item = result.optJSONObject(i);
                    if (item == null || item.optString("id").isEmpty() || item.optBoolean("unavailable")) continue;
                    tracks.add(item);
                    labels.add(SourceBrowser.trackName(item));
                }
            }
            ArrayAdapter<String> rows = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, labels);
            rows.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            songs.setAdapter(rows);
            message(ticket, tracks.isEmpty() ? "没有可试听的搜索结果，请更换关键词。"
                : "找到 " + tracks.size() + " 首候选歌曲，选择一首试听。", true);
        } catch (JSONException failure) {
            message(ticket, "插件返回的歌曲列表格式无法使用。", true);
        }
    }

    private void listen() {
        int index = songs.getSelectedItemPosition();
        if (index < 0 || index >= tracks.size()) return;
        JSONObject selected = tracks.get(index);
        long ticket = ++generation;
        String title = SourceBrowser.trackName(selected);
        String uri = new Uri.Builder().scheme("plugin").authority(source.id)
            .appendQueryParameter("id", selected.optString("id")).build().toString();
        long baseline = playback.get().generation;
        message(ticket, "正在试听 " + title + "…", false);
        player.play(selected);
        handler.post(() -> observe(ticket, title, uri, baseline, 0, -1, SystemClock.elapsedRealtime() + 40_000));
    }

    private void observe(long ticket, String title, String uri, long baseline, long observed, int position, long deadline) {
        if (!current(ticket)) return;
        PlaybackService.Snapshot state = playback.get();
        if (state.generation != baseline && uri.equals(state.uri)) {
            if (!state.error.isEmpty()) {
                message(ticket, title + " 未能播放\n" + reason(state.error), true);
                return;
            }
            if (observed != state.generation) position = -1;
            observed = state.generation;
            if (state.ready && state.playing && !state.buffering) {
                if (position < 0 || state.position < position) position = state.position;
                if (state.position - position >= 350) {
                    String result = state.cacheHit ? "已从本机缓存开始播放" : state.remote
                        ? "远程设备开始播放，进度已推进" : "已开始播放，进度已推进";
                    message(ticket, title + "\n" + result + "\n" + state.qualityInfo, true);
                    return;
                }
            } else {
                position = -1;
            }
        } else if (observed != 0) {
            message(ticket, "已切换歌曲，本次试听结束。", true);
            return;
        }
        if (SystemClock.elapsedRealtime() >= deadline) {
            message(ticket, title + " 尚未确认播放进度，请检查账号、网络或播放设备后重试。", true);
            return;
        }
        final long nextGeneration = observed;
        final int nextPosition = position;
        handler.postDelayed(() -> observe(ticket, title, uri, baseline, nextGeneration, nextPosition, deadline), 200);
    }
}
