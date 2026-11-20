package com.mygo.music;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.widget.GridView;
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
            });
            actions.helper.await(() -> activity.hasWindowFocus() && local.grid.isShown() && local.grid.getHeight() > 0
                && local.grid.getChildCount() > 0, "local library never completed its initial layout");
            scrollToLastSong(actions, local.grid, 84);
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
            actions.ui(() -> {
                List<Uri> tracks = (List<Uri>)FullPortChecks.field(activity, "tracks");
                List<String> names = (List<String>)FullPortChecks.field(activity, "names");
                tracks.subList(1, tracks.size()).clear();
                names.subList(1, names.size()).clear();
                View stale = local.grid.getAdapter().getView(84, null, local.grid);
                OfflinePlaybackChecks.check(stale != null, "pending layout could not render the old view safely");
                local.grid.performItemClick(stale, 84, local.grid.getItemIdAtPosition(84));
                OfflinePlaybackChecks.check(playback.snapshot().uri.equals(expected.toString()),
                    "a click on a removed local track switched playback");
                local.refresh();
                OfflinePlaybackChecks.check(local.grid.getAdapter().getCount() == 1, "grid did not update after the library shrank");
                local.grid.performItemClick(stale, 0, local.grid.getItemIdAtPosition(0));
                OfflinePlaybackChecks.check(playback.snapshot().uri.equals(expected.toString()),
                    "an outdated card played the track at its new adapter position");
            });
            System.out.println("MYGO_LOCAL_GRID_OK: long-list scroll, artist/title filtering, native playback and stable rows during library shrink");
        } finally {
            actions.ui(() -> {
                playback.clearQueue();
                activity.finish();
            });
            new TurntableLyricsChecks(test).restore(prefs,before);
            wave.delete();
        }
    }

    private void scrollToLastSong(OfflinePlaybackChecks actions, GridView grid, int position) throws Exception {
        int[] touch = new int[3];
        boolean[] visible = {false};
        String[] viewport = {""};
        for (int attempt = 0; attempt <= 64; attempt++) {
            actions.ui(() -> {
                int first = grid.getFirstVisiblePosition();
                View card = grid.getChildAt(position - first);
                visible[0] = card != null && card.getTop() >= 0 && card.getBottom() <= grid.getHeight();
                int[] origin = new int[2];
                grid.getLocationOnScreen(origin);
                touch[0] = origin[0] + grid.getWidth() / 2;
                touch[1] = origin[1] + grid.getHeight() - 5;
                touch[2] = origin[1] + 5;
                viewport[0] = grid.getWidth() + "x" + grid.getHeight() + ", first=" + first
                    + ", last=" + grid.getLastVisiblePosition() + ", focus=" + grid.hasWindowFocus();
            });
            if (visible[0]) return;
            if (attempt == 64) break;
            long down = SystemClock.uptimeMillis();
            pointer(down, MotionEvent.ACTION_DOWN, touch[0], touch[1]);
            for (int step = 1; step <= 4; step++) {
                SystemClock.sleep(20);
                float y = touch[1] + (touch[2] - touch[1]) * step / 4f;
                pointer(down, MotionEvent.ACTION_MOVE, touch[0], y);
            }
            pointer(down, MotionEvent.ACTION_UP, touch[0], touch[2]);
            test.waitForIdleSync();
        }
        throw new AssertionError("last library song could not be swiped fully into view: " + viewport[0]);
    }

    private void pointer(long down, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        try {
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            test.sendPointerSync(event);
        } finally {
            event.recycle();
        }
    }
}
