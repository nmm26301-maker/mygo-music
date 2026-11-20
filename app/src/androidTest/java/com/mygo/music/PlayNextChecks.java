package com.mygo.music;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.session.MediaController;
import android.net.Uri;
import android.service.notification.StatusBarNotification;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import org.json.JSONArray;
import org.json.JSONObject;

/** Explicit play-next requests must reach real native audio before shuffle or forward history. */
final class PlayNextChecks {
    private final OfflinePlaybackChecks actions;
    private final ArrayList<File> files = new ArrayList<>();
    private PlaybackService service;
    private MainActivity activity;

    PlayNextChecks(android.app.Instrumentation test) { actions = new OfflinePlaybackChecks(test); }
    private static void check(boolean value, String message) { OfflinePlaybackChecks.check(value, message); }
    private JSONObject item(int at, String title) throws Exception {
        return new JSONObject().put("uri", Uri.fromFile(files.get(at)).toString()).put("title", title);
    }
    private void start(boolean shuffle) throws Exception {
        JSONArray queue = new JSONArray().put(item(0, "Base A")).put(item(1, "Base B")).put(item(2, "Base C"));
        actions.ui(() -> PlaybackService.startQueue(activity, queue, 0, 0, 68, shuffle, false, "high"));
        playing("Base A");
    }
    private void playing(String title) throws Exception {
        actions.helper.await(() -> service.snapshot().playing && title.equals(service.snapshot().title)
            && service.snapshot().position > 30, "native play-next selection missing: " + title);
    }
    private String playingTitle() throws Exception {
        actions.helper.await(() -> service.snapshot().playing && service.snapshot().position > 30,
            "native shuffle did not continue after the priority request");
        String[] title = {null};
        actions.ui(() -> title[0] = service.snapshot().title);
        return title[0];
    }
    private int at(String title) throws Exception {
        JSONArray queue = service.queueSnapshot();
        for (int i = 0; i < queue.length(); i++) if (title.equals(queue.getJSONObject(i).getString("title"))) return i;
        throw new AssertionError("requested queue row disappeared: " + title);
    }
    private void notificationNext() throws Exception {
        actions.ui(() -> {
            NotificationManager manager = actions.context.getSystemService(NotificationManager.class);
            for (StatusBarNotification active : manager.getActiveNotifications()) {
                Notification note = active.getNotification();
                if (service.token().equals(note.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION))) {
                    check(note.actions != null && note.actions.length > 2, "notification has no next control");
                    note.actions[2].actionIntent.send();
                    return;
                }
            }
            throw new AssertionError("current playback notification missing");
        });
    }
    void run() throws Exception {
        SharedPreferences prefs = actions.context.getSharedPreferences("library", 0);
        boolean oldShuffle = prefs.getBoolean("shuffle", false), oldRepeat = prefs.getBoolean("repeat", false);
        String oldQuality = prefs.getString("play_quality", "high");
        activity = actions.launch();
        service = actions.service(activity);
        AudioCache cache = AudioCache.shared(actions.context);
        String cached = "plugin://play-next-offline?id=one";
        try {
            for (int i = 0; i < 6; i++) {
                File file = new File(actions.context.getCacheDir(), "play-next-" + i + ".wav");
                files.add(file);
                try (FileOutputStream out = new FileOutputStream(file)) { out.write(BackgroundPlaybackChecks.wav(12000)); }
            }
            start(true);
            actions.ui(() -> {
                MediaPlayer original = service.snapshot().player;
                service.enqueue(item(3, "First request"), true);
                service.enqueue(item(4, "Latest request"), true);
                check(service.snapshot().player == original && service.snapshot().title.equals("Base A"),
                    "enqueue interrupted the current audio");
                service.pause();
                service.resume();
            });
            notificationNext();
            playing("Latest request");
            new MediaController(actions.context, service.token()).getTransportControls().skipToNext();
            playing("First request");
            actions.ui(service::previous);
            playing("Latest request");
            actions.ui(() -> { service.enqueue(item(5, "Override forward history"), true); service.next(); });
            playing("Override forward history");
            actions.ui(service::previous);
            playing("Latest request");
            actions.ui(service::next);
            playing("Override forward history");
            HashSet<String> remaining = new HashSet<>();
            for (int i = 0; i < 2; i++) {
                actions.ui(service::next);
                String title = playingTitle();
                check((title.equals("Base B") || title.equals("Base C")) && remaining.add(title),
                    "priority requests reset or repeated the remaining shuffle cycle: " + title);
            }
            actions.ui(() -> {
                service.enqueue(item(3, "Mode switch request"), true);
                service.setModes(false, false);
                service.setModes(true, false);
                service.moveQueue(at("Mode switch request"), service.queueSnapshot().length() - 1);
                service.next();
            });
            playing("Mode switch request");
            actions.ui(() -> {
                service.enqueue(item(4, "Manual request"), true);
                service.playQueueIndex(at("Manual request"));
            });
            playing("Manual request");
            actions.ui(service::next);
            check(!playingTitle().equals("Manual request"), "manually selected priority entry played twice");
            actions.ui(() -> {
                MediaPlayer original = service.snapshot().player;
                service.enqueue(item(5, "Deleted request"), true);
                service.removeQueue(at("Deleted request"));
                check(service.snapshot().player == original, "removing another entry interrupted current audio");
                service.next();
            });
            check(!playingTitle().equals("Deleted request"), "removed priority entry returned");
            actions.ui(() -> service.enqueue(item(3, "Natural completion request"), true));
            actions.ui(() -> service.seek(11700));
            playing("Natural completion request");

            start(false);
            actions.ui(() -> {
                service.enqueue(item(5, "Ordinary append"), false);
                service.enqueue(item(3, "Sequential first"), true);
                service.enqueue(item(4, "Sequential latest"), true);
                service.next();
            });
            playing("Sequential latest");
            actions.ui(service::next);
            playing("Sequential first");
            actions.ui(service::next);
            playing("Base B");
            actions.ui(() -> service.enqueue(item(3, "Old queue request"), true));
            start(true);
            actions.ui(service::next);
            check(!playingTitle().equals("Old queue request"), "replacement queue inherited a priority request");
            actions.ui(() -> {
                service.enqueue(item(3, "Cleared request"), true);
                service.clearQueue();
                service.enqueue(item(2, "New single entry"), false);
                service.resume();
            });
            playing("New single entry");
            actions.ui(service::next);
            playing("New single entry");

            cache.importFile(cached, "Offline high", "high", new JSONObject(), files.get(0), cache.epoch());
            cache.importFile(cached, "Offline lossless", "lossless", new JSONObject(), files.get(1), cache.epoch());
            JSONObject high = new JSONObject().put("uri", cached).put("title", "Offline high")
                .put("cacheOnly", true).put("cachedQuality", "high");
            JSONObject lossless = new JSONObject(high.toString()).put("title", "Offline priority")
                .put("cachedQuality", "lossless");
            actions.ui(() -> PlaybackService.startQueue(activity, new JSONArray().put(high), 0, 0, 68, true, false, "high"));
            playing("Offline high");
            actions.ui(() -> { service.enqueue(lossless, true); service.next(); });
            playing("Offline priority");
            actions.ui(() -> check(service.snapshot().offlineOnly && service.snapshot().requestedQuality.equals("lossless")
                && service.snapshot().stream.equals(Uri.fromFile(cache.find(cached, "lossless")).toString()),
                "offline priority selected another cached quality or resolved online"));
            System.out.println("MYGO_PLAY_NEXT_OK: actual notification and media-controller next, native audio clocks, LIFO priority, uninterrupted enqueue, pause/resume, forward-history override, remaining shuffle cycle, mode toggles, reorder, manual consumption, deletion, natural completion, sequential mode, replacement, clear and same-URI offline quality identity");
        } finally {
            actions.ui(() -> { service.stopPlayback(); service.setModes(oldShuffle, oldRepeat); activity.finish(); });
            prefs.edit().putString("play_quality", oldQuality).commit();
            cache.remove(cached, "high");
            cache.remove(cached, "lossless");
            for (File file : files) file.delete();
        }
    }
}
