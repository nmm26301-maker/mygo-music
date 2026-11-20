package com.mygo.music;

import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real native transport proves shuffle history, queue edits and completion share one order. */
final class ShufflePlaybackChecks {
    private final OfflinePlaybackChecks actions;
    ShufflePlaybackChecks(android.app.Instrumentation test) { actions = new OfflinePlaybackChecks(test); }
    private static void check(boolean value, String message) { OfflinePlaybackChecks.check(value, message); }
    private String playing(PlaybackService service) throws Exception {
        actions.helper.await(() -> service.snapshot().playing && service.snapshot().position > 30,
            "shuffle selection did not reach the native audio clock");
        String[] uri = {null};
        actions.ui(() -> uri[0] = service.snapshot().uri);
        return uri[0];
    }
    void run() throws Exception {
        SharedPreferences prefs = actions.context.getSharedPreferences("library", 0);
        boolean oldShuffle = prefs.getBoolean("shuffle", false), oldRepeat = prefs.getBoolean("repeat", false);
        MainActivity activity = actions.launch();
        PlaybackService service = actions.service(activity);
        ArrayList<File> files = new ArrayList<>();
        JSONArray queue = new JSONArray();
        try {
            for (int i = 0; i < 5; i++) {
                File file = new File(actions.context.getCacheDir(), "shuffle-" + i + ".wav");
                files.add(file);
                try (FileOutputStream out = new FileOutputStream(file)) { out.write(BackgroundPlaybackChecks.wav(12000)); }
                queue.put(new JSONObject().put("uri", Uri.fromFile(file).toString()).put("title", "Shuffle " + i));
            }
            actions.ui(() -> PlaybackService.startQueue(activity, queue, 2, 0, 65, true, false, "high"));
            String first = playing(service);
            Set<String> heard = new HashSet<>();
            heard.add(first);
            ArrayList<String> history = new ArrayList<>();
            history.add(first);
            for (int i = 1; i < queue.length(); i++) {
                actions.ui(service::next);
                String selected = playing(service);
                check(heard.add(selected), "native shuffle repeated before finishing the queue");
                history.add(selected);
            }
            actions.ui(service::previous);
            check(playing(service).equals(history.get(3)), "previous ignored actual shuffle history");
            actions.ui(service::previous);
            check(playing(service).equals(history.get(2)), "second previous lost shuffle history");
            actions.ui(service::next);
            check(playing(service).equals(history.get(3)), "next after previous lost the forward entry");
            actions.ui(() -> {
                MediaPlayer player = service.snapshot().player;
                int from = service.snapshot().index;
                service.moveQueue(from, 0);
                check(service.snapshot().player == player, "queue reorder restarted current audio");
            });
            actions.ui(service::previous);
            check(playing(service).equals(history.get(2)), "queue reorder broke history identity");
            actions.ui(service::next);
            check(playing(service).equals(history.get(3)), "reordered forward history selected another entry");
            String deleted = history.get(2);
            actions.ui(() -> {
                JSONArray values = service.queueSnapshot();
                for (int i = 0; i < values.length(); i++) if (deleted.equals(values.getJSONObject(i).getString("uri"))) {
                    service.removeQueue(i);
                    return;
                }
            });
            actions.ui(service::previous);
            check(playing(service).equals(history.get(1)), "deleted song remained in shuffle history");
            actions.ui(service::next);
            check(playing(service).equals(history.get(3)), "deleting an old entry corrupted forward history");
            actions.ui(() -> service.seek(11700));
            actions.helper.await(() -> service.snapshot().playing && service.snapshot().uri.equals(history.get(4)),
                "natural completion ignored shuffle forward history");
            actions.ui(() -> service.setModes(false, false));
            int[] expected = {0};
            actions.ui(() -> { expected[0] = (service.snapshot().index + 1) % service.queueSnapshot().length(); service.next(); });
            playing(service);
            actions.ui(() -> check(service.snapshot().index == expected[0], "turning off shuffle broke sequential playback"));
            System.out.println("MYGO_SHUFFLE_PLAYBACK_OK: real native cycles, previous/forward history, reorder without restart, deletion, natural completion and sequential mode");
        } finally {
            actions.ui(() -> { service.stopPlayback(); service.setModes(oldShuffle, oldRepeat); activity.finish(); });
            for (File file : files) file.delete();
        }
    }
}
