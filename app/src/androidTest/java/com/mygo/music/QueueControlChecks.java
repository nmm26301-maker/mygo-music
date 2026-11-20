package com.mygo.music;

import android.app.AlertDialog;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.SystemClock;
import android.graphics.Rect;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/** Retained native rows and gestures must not act on a later queue occupying the same positions. */
final class QueueControlChecks {
    private final OfflinePlaybackChecks actions;
    private MainActivity activity;
    private PlaybackService service;
    private ThemeQueueView view;
    private ListView list;
    private AlertDialog dialog;
    private final ArrayList<File> files = new ArrayList<>();
    private final int[] selections = {0};
    private JSONArray base;

    QueueControlChecks(android.app.Instrumentation test) { actions = new OfflinePlaybackChecks(test); }
    private static void check(boolean value, String message) { OfflinePlaybackChecks.check(value, message); }
    private void playing(String title) throws Exception {
        actions.helper.await(() -> service.snapshot().playing && title.equals(service.snapshot().title)
            && service.snapshot().position > 30, "queue control did not reach actual native audio: " + title);
    }
    private void start(JSONArray queue) throws Exception {
        long previous = service.snapshot().generation;
        actions.ui(() -> PlaybackService.startQueue(activity, queue, 0, 0, 62, false, false, "high"));
        actions.helper.await(() -> service.snapshot().generation != previous && service.snapshot().playing
            && service.snapshot().position > 30, "queue replacement did not start native audio");
    }
    private void frame() throws Exception {
        boolean[] drawn = {false};
        actions.ui(() -> {
            view.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    view.getViewTreeObserver().removeOnPreDrawListener(this);
                    drawn[0] = true;
                    return true;
                }
            });
            view.refresh(true);
            list.setSelection(0);
            view.invalidate();
        });
        actions.helper.await(() -> drawn[0], "queue did not draw its new adapter rows");
    }
    private ViewGroup row(int at) throws Exception {
        ViewGroup[] found = {null};
        actions.helper.await(() -> {
            int child = at - list.getFirstVisiblePosition();
            if (child >= 0 && child < list.getChildCount()) found[0] = (ViewGroup) list.getChildAt(child);
            return found[0] != null;
        }, "queue row was not visible: " + at);
        return found[0];
    }
    private void unchanged(String expected, MediaPlayer player, long generation, int callbacks) throws Exception {
        actions.ui(() -> {
            check(service.queueSnapshot().toString().equals(expected), "stale queue control changed another entry");
            check(service.snapshot().player == player && service.snapshot().generation == generation,
                "stale queue control restarted or replaced current audio");
            check(selections[0] == callbacks, "stale queue control ran the selection callback");
        });
    }
    private void input(int action, float x, float y, long down) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        actions.test.getUiAutomation().injectInputEvent(event, true);
        event.recycle();
    }
    private void menuClick(String label) throws Exception {
        android.view.accessibility.AccessibilityNodeInfo[] item = {null};
        Rect bounds = new Rect();
        try {
            actions.helper.await(() -> {
                item[0] = SourcePlaylistUiChecks.node(actions.test, label);
                if (item[0] == null || !item[0].isVisibleToUser()) return false;
                item[0].getBoundsInScreen(bounds);
                return bounds.width() > 0 && bounds.height() > 0;
            }, "queue menu item did not become visible: " + label);
        } catch (AssertionError error) {
            throw new AssertionError("queue menu did not settle: " + label + "; UI="
                + SourcePlaylistUiChecks.uiText(actions.test.getUiAutomation().getRootInActiveWindow())
                + "; queue=" + service.queueSnapshot(), error);
        }
        long down = SystemClock.uptimeMillis();
        input(MotionEvent.ACTION_DOWN, bounds.centerX(), bounds.centerY(), down);
        SystemClock.sleep(60);
        input(MotionEvent.ACTION_UP, bounds.centerX(), bounds.centerY(), down);
        actions.test.waitForIdleSync();
    }
    private void drag(boolean replaceDuringDrag) throws Exception {
        frame();
        ViewGroup first = row(0), second = row(1);
        Rect from = new Rect(), to = new Rect();
        actions.ui(() -> {
            check(first.getChildAt(0).getGlobalVisibleRect(from), "drag source handle missing");
            check(second.getChildAt(0).getGlobalVisibleRect(to), "drag destination handle missing");
        });
        long down = SystemClock.uptimeMillis();
        input(MotionEvent.ACTION_DOWN, from.centerX(), from.centerY(), down);
        SystemClock.sleep(700);
        for (int i = 1; i <= 3; i++) {
            input(MotionEvent.ACTION_MOVE, from.centerX(), from.centerY() + (to.centerY() - from.centerY()) * i / 4f, down);
            SystemClock.sleep(50);
        }
        if (replaceDuringDrag) start(base);
        input(MotionEvent.ACTION_MOVE, to.centerX(), to.centerY(), down);
        input(MotionEvent.ACTION_UP, to.centerX(), to.centerY(), down);
        SystemClock.sleep(400);
        actions.test.waitForIdleSync();
    }
    void run() throws Exception {
        android.content.SharedPreferences prefs = actions.context.getSharedPreferences("library", 0);
        boolean oldShuffle = prefs.getBoolean("shuffle", false), oldRepeat = prefs.getBoolean("repeat", false);
        String oldQuality = prefs.getString("play_quality", "high");
        activity = actions.launch();
        service = actions.service(activity);
        AudioCache cache = AudioCache.shared(actions.context);
        String cached = "plugin://queue-control-offline?id=same";
        try {
            base = new JSONArray();
            for (int i = 0; i < 3; i++) {
                File file = new File(actions.context.getCacheDir(), "queue-control-" + i + ".wav");
                files.add(file);
                try (FileOutputStream out = new FileOutputStream(file)) { out.write(BackgroundPlaybackChecks.wav(60000)); }
                base.put(new JSONObject().put("uri", Uri.fromFile(file).toString()).put("title", "Queue " + (char)('A' + i)));
            }
            start(base);
            actions.ui(() -> {
                PlayerScreenHost host = (PlayerScreenHost) NowPlayingChecks.invoke(activity, "playerHost");
                view = new ThemeQueueView(activity, host, () -> selections[0]++);
                list = (ListView) FullPortChecks.field(view, "list");
                dialog = new AlertDialog.Builder(activity).setView(view).setNegativeButton("关闭", null).create();
                dialog.show();
                android.util.DisplayMetrics size = activity.getResources().getDisplayMetrics();
                dialog.getWindow().setLayout(size.widthPixels - 16, size.heightPixels - 16);
            });
            frame();
            ViewGroup stale = row(0);
            MediaPlayer original = service.snapshot().player;
            long generation = service.snapshot().generation;
            actions.ui(() -> {
                service.moveQueue(1, 0);
                String expected = service.queueSnapshot().toString();
                view.refresh(true);
                stale.getChildAt(2).performClick();
                stale.getChildAt(1).performClick();
                list.performItemClick(stale, 0, 0);
                check(service.queueSnapshot().toString().equals(expected), "refreshed adapter let an old row target its new position");
            });
            unchanged(service.queueSnapshot().toString(), original, generation, 0);
            frame();
            ViewGroup live = row(0);
            actions.ui(() -> live.getChildAt(1).performClick());
            playing("Queue B");
            check(selections[0] == 1, "fresh row could no longer select a song");
            actions.ui(service::next);
            playing("Queue A");
            MediaPlayer current = service.snapshot().player;
            actions.ui(() -> live.getChildAt(2).performClick());
            actions.ui(() -> check(service.queueSnapshot().length() == 2 && service.snapshot().player == current
                && service.snapshot().title.equals("Queue A"), "index-only playback change invalidated the correct bound row"));

            start(base);
            frame();
            ViewGroup menu = row(0);
            actions.ui(menu::performLongClick);
            actions.helper.await(() -> SourcePlaylistUiChecks.node(actions.test, "从队列移除") != null,
                "queue context menu did not open");
            start(base); // Identical JSON, different queue lifetime.
            MediaPlayer replacement = service.snapshot().player;
            long replacementGeneration = service.snapshot().generation;
            int callbacks = selections[0];
            menuClick("从队列移除");
            actions.helper.await(() -> dialog.getWindow().getDecorView().hasWindowFocus(), "stale menu did not close");
            unchanged(base.toString(), replacement, replacementGeneration, callbacks);
            frame();
            ViewGroup freshMenu = row(0);
            actions.ui(freshMenu::performLongClick);
            menuClick("下移一首");
            actions.helper.await(() -> service.queueSnapshot().getJSONObject(0).getString("title").equals("Queue B"),
                "fresh context menu did not move the selected entry");
            actions.ui(() -> check(service.snapshot().player == replacement, "valid menu move restarted current audio"));

            cache.importFile(cached, "Same song", "high", new JSONObject(), files.get(0), cache.epoch());
            cache.importFile(cached, "Same song", "lossless", new JSONObject(), files.get(1), cache.epoch());
            JSONObject high = new JSONObject().put("uri", cached).put("title", "Same song")
                .put("cacheOnly", true).put("cachedQuality", "high");
            JSONObject lossless = new JSONObject(high.toString()).put("cachedQuality", "lossless");
            start(new JSONArray().put(high).put(lossless));
            frame();
            ViewGroup sameUri = row(0);
            MediaPlayer highPlayer = service.snapshot().player;
            long highGeneration = service.snapshot().generation;
            int highCallbacks = selections[0];
            actions.ui(() -> {
                service.moveQueue(1, 0);
                String expected = service.queueSnapshot().toString();
                view.refresh(true);
                sameUri.getChildAt(1).performClick();
                sameUri.getChildAt(2).performClick();
                check(service.queueSnapshot().toString().equals(expected), "same-URI cached qualities confused stale row actions");
            });
            unchanged(service.queueSnapshot().toString(), highPlayer, highGeneration, highCallbacks);
            frame();
            ViewGroup losslessRow = row(0);
            actions.ui(() -> losslessRow.getChildAt(1).performClick());
            actions.helper.await(() -> service.snapshot().playing && service.snapshot().offlineOnly
                && service.snapshot().requestedQuality.equals("lossless") && service.snapshot().position > 30,
                "fresh same-URI row did not play its exact cached quality");

            start(base);
            frame();
            ViewGroup removed = row(2);
            actions.ui(() -> {
                service.clearQueue();
                view.refresh(true);
                removed.getChildAt(1).performClick();
                removed.getChildAt(2).performClick();
                check(service.queueSnapshot().length() == 0 && service.snapshot().player == null,
                    "old row action revived or mutated a cleared queue");
            });
            start(base);
            drag(true);
            actions.ui(() -> check(service.queueSnapshot().toString().equals(base.toString()),
                "drag from the prior queue moved a new identical queue"));
            MediaPlayer dragging = service.snapshot().player;
            long draggingGeneration = service.snapshot().generation;
            drag(false);
            actions.helper.await(() -> service.queueSnapshot().getJSONObject(0).getString("title").equals("Queue B"),
                "fresh native drag stopped working after rejecting an obsolete drag");
            actions.ui(() -> check(service.snapshot().player == dragging && service.snapshot().generation == draggingGeneration,
                "native drag replaced the shared player"));
            System.out.println("MYGO_QUEUE_CONTROLS_OK: actual retained row play/remove/list click rejection after adapter refresh, fresh native selection, index-only update, identical-JSON menu lifetime, normal menu move, same-URI offline quality identity, cleared rows, obsolete native drag rejection and fresh native drag without player restart");
        } finally {
            actions.ui(() -> { if (dialog != null) dialog.dismiss(); service.stopPlayback(); service.setModes(oldShuffle, oldRepeat); activity.finish(); });
            prefs.edit().putString("play_quality", oldQuality).commit();
            cache.remove(cached, "high");
            cache.remove(cached, "lossless");
            for (File file : files) file.delete();
        }
    }
}
