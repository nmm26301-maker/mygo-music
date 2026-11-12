package com.mygo.music;

import android.content.Context;
import android.os.*;
import java.util.*;

/** One initiation-order queue per source, shared across dialogs and manager instances. */
final class SourceSettingQueue {
    interface Write {void run(Runnable finished);}
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final Map<String,State> queues=new HashMap<>();
    private static final class State {final ArrayDeque<Write> writes=new ArrayDeque<>();boolean running;}
    static void submit(Context context,String source,Write write){Runnable task=()->{String key=context.getFilesDir().getAbsolutePath()+"/"+source;State state=queues.get(key);if(state==null){state=new State();queues.put(key,state);}state.writes.addLast(write);drain(key,state);};if(Looper.myLooper()==Looper.getMainLooper())task.run();else main.post(task);}
    private static void drain(String key,State state){if(state.running||state.writes.isEmpty())return;state.running=true;boolean[] completed={false};state.writes.peekFirst().run(()->main.post(()->{if(completed[0])return;completed[0]=true;state.writes.removeFirst();state.running=false;if(state.writes.isEmpty())queues.remove(key);else drain(key,state);}));}
}
