package com.mygo.music;

import android.os.Handler;
import android.os.HandlerThread;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/** Detached lyric views render and save their test frames without blocking app input. */
final class OffscreenLyricsChecks {
    private OffscreenLyricsChecks() {}

    static void run(Callable<Void> render) throws Exception {
        HandlerThread thread = new HandlerThread("mygo-qa-lyrics-render");
        thread.start();
        FutureTask<Void> renderTask = new FutureTask<>(render);
        try {
            new Handler(thread.getLooper()).post(renderTask);
            renderTask.get();
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof Exception) throw (Exception) cause;
            throw new RuntimeException(cause);
        } finally {
            thread.quitSafely();
        }
    }
}
