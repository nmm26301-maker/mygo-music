package com.mygo.music;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

final class LocalMusicGridChecks {
    private final Instrumentation test;

    LocalMusicGridChecks(Instrumentation test) { this.test = test; }

    @SuppressWarnings("unchecked")
    void run() throws Exception {
        Context context = test.getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences("library", 0);
        Map<String, ?> before = new HashMap<>(prefs.getAll());
        File wave = File.createTempFile("mygo-library-", ".wav", context.getCacheDir());
        try (FileOutputStream out = new FileOutputStream(wave)) {
            out.write(BackgroundPlaybackChecks.wav(15000));
        }
        OfflinePlaybackChecks actions = new OfflinePlaybackChecks(test);
        MainActivity activity = actions.launch();
        PlaybackService playback = actions.service(activity);
        LocalMusicGrid local = (LocalMusicGrid) FullPortChecks.field(activity, "localMusicGrid");
        Uri expected = Uri.fromFile(wave);
        try {
            actions.ui(() -> {
                playback.clearQueue();
                List<Uri> tracks = (List<Uri>)FullPortChecks.field(activity, "tracks");
                List<String> names = (List<String>)FullPortChecks.field(activity, "names");
                tracks.clear();
                names.clear();
                for (int i = 0; i < 84; i++) {
                    tracks.add(Uri.fromFile(new File(context.getCacheDir(), "library-missing-" + i)));
                    names.add("歌曲 " + i + "  ·  测试歌手");
                }
                tracks.add(expected);
                names.add("星海夜色  ·  测试女歌手");
                OfflinePlaybackChecks.invoke(activity, "showLocalLibrary", new Class<?>[]{});
                OfflinePlaybackChecks.check(local.grid.getAdapter().getCount() == 85, "library results were truncated");
                local.grid.setSelection(84);
            });
            actions.helper.await(() -> local.grid.getLastVisiblePosition() == 84,
                "last library song could not be brought into view");
            actions.ui(() -> {
                local.filter.setText("女歌手 星海");
                OfflinePlaybackChecks.check(local.grid.getAdapter().getCount() == 1,
                    "combined artist and title filter did not find the last song");
            });
            actions.helper.await(() -> local.grid.getChildAt(0) != null&&local.grid.getFirstVisiblePosition()==0,
                "filtered local song was not rendered");
            actions.ui(() -> {
                View card = local.grid.getChildAt(0);
                OfflinePlaybackChecks.check(local.grid.performItemClick(card, 0, local.grid.getItemIdAtPosition(0)),
                    "filtered local song could not be selected");
            });
            actions.helper.await(() -> playback.snapshot().playing&&playback.snapshot().uri.equals(expected.toString()),
                "filtered grid played a different song");
            actions.ui(() -> {
                local.filter.setText("no music matches");
                OfflinePlaybackChecks.check(local.grid.getAdapter().getCount() == 0, "no-match filter retained items");
                local.filter.setText("");
                OfflinePlaybackChecks.check(local.grid.getAdapter().getCount() == 85, "clearing filter lost library entries");
            });
            System.out.println("MYGO_LOCAL_GRID_OK: 85 songs, final song scroll, artist/title filtering and native playback");
        } finally {
            actions.ui(() -> {
                playback.clearQueue();
                activity.finish();
            });
            new TurntableLyricsChecks(test).restore(prefs,before);
            wave.delete();
        }
    }
}
