package com.mygo.music;

import android.content.Context;
import android.os.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Test-only diagnostics. It records stalls without changing timing assertions or app behavior. */
final class MainThreadDiagnostics implements AutoCloseable {
 private final Context context;private final Handler main=new Handler(Looper.getMainLooper());
 private volatile boolean closed;private volatile long heartbeat=SystemClock.elapsedRealtime();private volatile String stage="startup";
 private final Runnable beat=new Runnable(){public void run(){if(closed)return;heartbeat=SystemClock.elapsedRealtime();main.postDelayed(this,300);}};
 MainThreadDiagnostics(Context context){this.context=context;}
 void stage(String name){stage=name;}
 void start(){main.post(beat);Thread thread=new Thread(()->{long saved=0;int number=0;while(!closed){try{Thread.sleep(500);long now=SystemClock.elapsedRealtime(),lag=now-heartbeat;if(lag<2500||now-saved<3000)continue;saved=now;JSONObject report=new JSONObject().put("stage",stage).put("mainLagMs",lag).put("elapsedMs",now);JSONArray stacks=new JSONArray();for(Map.Entry<Thread,StackTraceElement[]> entry:Thread.getAllStackTraces().entrySet()){Thread current=entry.getKey();JSONArray frames=new JSONArray();for(StackTraceElement frame:entry.getValue())frames.put(frame.toString());stacks.put(new JSONObject().put("name",current.getName()).put("state",current.getState().name()).put("stack",frames));}report.put("threads",stacks);File folder=new File(context.getExternalFilesDir(null),"qa/main-stalls");folder.mkdirs();byte[] bytes=report.toString(2).getBytes("UTF-8");try(OutputStream out=new FileOutputStream(new File(folder,"latest.json"))){out.write(bytes);}if(number<24)try(OutputStream out=new FileOutputStream(new File(folder,String.format(Locale.ROOT,"stall-%02d.json",number++)))){out.write(bytes);}}catch(InterruptedException ended){return;}catch(Exception ignored){}}},"mygo-qa-main-watchdog");thread.setDaemon(true);thread.start();}
 public void close(){closed=true;main.removeCallbacks(beat);}
}
