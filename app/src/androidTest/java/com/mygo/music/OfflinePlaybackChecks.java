package com.mygo.music;

import android.app.*;
import android.content.*;
import android.media.MediaPlayer;
import android.os.SystemClock;
import android.view.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Exercise the actual cache picker, mixed-quality native queue, cold resume and saved lyric page. */
final class OfflinePlaybackChecks {
    final Instrumentation test;final Context context;final CompletionChecks helper;PlaybackService service;
    OfflinePlaybackChecks(Instrumentation test){this.test=test;context=test.getTargetContext();helper=new CompletionChecks(test);}
    interface Action{void run()throws Exception;}
    void ui(Action action)throws Exception{Throwable[] failure={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable error){failure[0]=error;}});if(failure[0]!=null)throw new AssertionError("Offline action failed",failure[0]);}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static Object field(Object value,String name)throws Exception{return FullPortChecks.field(value,name);}
    static Object invoke(Object target,String name,Class<?>[] types,Object...args)throws Exception{Method method=target.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(target,args);}
    static View described(View root,String description){if(description.equals(root.getContentDescription()))return root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View match=described(group.getChildAt(i),description);if(match!=null)return match;}}return null;}
    MainActivity launch(){return (MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    PlaybackService service(MainActivity activity)throws Exception{PlaybackService[] result={null};helper.await(()->{result[0]=(PlaybackService)field(activity,"playbackService");return result[0]!=null;},"offline service missing");return result[0];}
    void store(AudioCache cache,String uri,String title,String quality,String url)throws Exception{CountDownLatch done=new CountDownLatch(1);boolean[] success={false};cache.store(uri,title,quality,url,new HashMap<>(),(ok,message)->{success[0]=ok;done.countDown();});check(done.await(15,TimeUnit.SECONDS)&&success[0],"offline fixture transfer failed");}
    void selectRemoval(AlertDialog picker,AudioCache.Entry entry)throws Exception{
        ui(()->{
            View remove=described(picker.getWindow().getDecorView(),"删除离线缓存："+entry.title+" · "+AudioQuality.label(entry.quality));
            check(remove!=null&&remove.performClick(),"cache delete control was missing");
        });
        long until=SystemClock.elapsedRealtime()+7000;
        while(SystemClock.elapsedRealtime()<until){
            if(SourcePlaylistUiChecks.node(test,"删除这份离线缓存？")!=null)return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("cache deletion confirmation was missing");
    }
    void verifyCacheRemoval(MainActivity activity,AudioCache cache,String a,String b)throws Exception{
        AudioCache.Entry playing=cache.list().stream().filter(entry->entry.track.equals(a)&&entry.quality.equals("high")).findFirst().get();
        AudioCache.Entry removed=cache.list().stream().filter(entry->entry.track.equals(a)&&entry.quality.equals("lossless")).findFirst().get();
        AudioCache.Entry missing=cache.list().stream().filter(entry->entry.track.equals(b)).findFirst().get();
        PlaybackService.Snapshot before=service.snapshot();
        check(before.uri.equals(a)&&before.requestedQuality.equals("high"),"wrong quality for removal regression");
        AlertDialog[] picker={null};
        ui(()->picker[0]=(AlertDialog)invoke(activity,"showAudioCache",new Class<?>[]{}));
        selectRemoval(picker[0],removed);
        SourcePlaylistUiChecks.click(test,"取消");
        helper.await(()->picker[0].getWindow().getDecorView().hasWindowFocus(),"cancel did not return to cache list");
        check(removed.file.isFile()&&cache.list().size()==3,"cancel deleted a cache entry");
        selectRemoval(picker[0],playing);
        SourcePlaylistUiChecks.click(test,"删除");
        helper.await(()->picker[0].getWindow().getDecorView().hasWindowFocus(),"playing cache confirmation did not close");
        check(playing.file.isFile(),"deletion removed an in-use cache");
        selectRemoval(picker[0],removed);
        SourcePlaylistUiChecks.click(test,"删除");
        helper.await(()->!picker[0].isShowing()&&service.queueSnapshot().length()==2,"removed cache remained in offline queue");
        PlaybackService.Snapshot after=service.snapshot();
        check(after.player==before.player&&after.generation==before.generation&&after.playing,"deleting another quality restarted playback");
        check(!removed.file.exists()&&playing.file.isFile()&&missing.file.isFile(),"delete did not target only the selected quality");
        try(AudioCache reloaded=new AudioCache(context)){
            check(reloaded.list().size()==2&&reloaded.find(a,"lossless")==null,"deleted cache returned after reloading index");
        }
        check(missing.file.delete(),"could not simulate a missing cache file");
        check(cache.list().size()==1&&cache.bytes()==playing.bytes,"expired file remained in list or capacity total");
        test.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
        helper.await(activity::hasWindowFocus,"close refreshed cache list");
        ui(()->{
            AlertDialog refreshed=(AlertDialog)invoke(activity,"showAudioCache",new Class<?>[]{});
            check(described(refreshed.getWindow().getDecorView(),"播放离线缓存："+missing.key)==null,"missing file still showed a playable row");
            refreshed.dismiss();
        });
        System.out.println("MYGO_CACHE_REMOVAL_OK: cancel, in-use protection, exact quality deletion, live queue, same native player, index reload, missing file and capacity");
    }
    void run()throws Exception{
        String id="offline-workflow",a="plugin://"+id+"?id=a",b="plugin://"+id+"?id=b",raw="{\"lines\":[{\"start\":0,\"end\":9000,\"translation\":\"Saved translation\",\"words\":[{\"text\":\"离线歌词仍然亮起\",\"start\":1000,\"end\":8000}]}]}";
        check(LyricsTimeline.parse(JSONObject.quote("[00:01.00]插件字符串歌词")).timed,"JSON-encoded source LRC lost timing");check(LyricsTimeline.parse(JSONObject.quote(raw)).wordTimed,"JSON-encoded source word timings lost");check(LyricsTimeline.parse(JSONObject.quote("")).lines.isEmpty(),"encoded empty lyric became visible quote characters");
        SharedPreferences prefs=context.getSharedPreferences("library",0),data=context.getSharedPreferences("plugin_data_"+id,0);Map<String,?> original=new HashMap<>(prefs.getAll());prefs.edit().remove("selected_source").putBoolean("auto_cache",false).putString("play_quality","lossless").commit();
        MainActivity[] activity={launch()};service=service(activity[0]);AudioCache cache=AudioCache.shared(context);ui(()->{service.clearQueue();service.setModes(false,false);});cache.clear();
        try{
            helper.install(new PluginManager(context),id,"stream",new JSONArray(),"export function stream(){host.storage.set('streamCalls',(host.storage.get('streamCalls')||0)+1);throw Error('network stream must never run offline');}export function lyrics(){host.storage.set('lyricCalls',(host.storage.get('lyricCalls')||0)+1);if(host.storage.get('empty'))return '';throw Error('fixture lyric update failure');}export function played(){host.storage.set('playedCalls',(host.storage.get('playedCalls')||0)+1);}");
            try(Server server=new Server()){store(cache,a,"Offline A","high",server.url());store(cache,a,"Offline A","lossless",server.url());store(cache,b,"Offline B","standard",server.url());}
            ArrayList<AudioCache.Entry> entries=cache.list();String key="";for(AudioCache.Entry entry:entries)if(entry.track.equals(a)&&entry.quality.equals("high"))key=entry.key;final String selectedKey=key;
            AlertDialog[] picker={null};ui(()->{picker[0]=(AlertDialog)invoke(activity[0],"showAudioCache",new Class<?>[]{});View row=described(picker[0].getWindow().getDecorView(),"播放离线缓存："+selectedKey);check(row!=null&&row.performClick(),"actual offline cache row did not start playback");});
            helper.await(()->service.snapshot().playing&&service.snapshot().position>150,"cached audio did not play after server shutdown");
            ui(()->{PlaybackService.Snapshot state=service.snapshot();check(!picker[0].isShowing()&&state.offlineOnly&&state.requestedQuality.equals("high"),"cache picker lost exact quality or queue policy");check(prefs.getString("play_quality","").equals("lossless"),"offline picker overwrote online quality preference");check(service.queueSnapshot().length()==3,"offline picker reused accumulated music library");MediaPlayer player=state.player;long generation=state.generation;check(!service.changeQuality("standard"),"offline quality change started network replacement");check(service.snapshot().player==player&&service.snapshot().generation==generation&&service.snapshot().requestedQuality.equals("high")&&prefs.getString("play_quality","").equals("standard"),"online quality preference changed cached playback");});
            for(int i=0;i<3;i++){final int at=i;JSONObject item=service.queueSnapshot().getJSONObject(i);ui(()->service.playQueueIndex(at));helper.await(()->service.snapshot().playing&&service.snapshot().index==at&&service.snapshot().requestedQuality.equals(item.optString("cachedQuality")),"mixed-quality cache queue selected wrong audio");}
            long generation=service.snapshot().generation;int next=(service.snapshot().index+1)%3;ui(()->service.seek(service.snapshot().duration-350));helper.await(()->service.snapshot().playing&&service.snapshot().index==next&&service.snapshot().generation>generation,"actual completion escaped or stopped offline queue");
            ui(()->{service.setModes(true,false);service.next();});helper.await(()->service.snapshot().playing&&service.snapshot().offlineOnly,"shuffle escaped cached queue");
            long beforeRepeat=service.snapshot().generation;int repeated=service.snapshot().index;ui(()->{service.setModes(false,true);service.seek(service.snapshot().duration-350);});helper.await(()->service.snapshot().playing&&service.snapshot().generation>beforeRepeat&&service.snapshot().index==repeated,"offline repeat did not restart actual cached audio");
            ui(()->{service.setModes(false,false);HashMap<String,String> saved=(HashMap<String,String>)field(activity[0],"localLyrics");saved.put(a,raw);saved.put(b,raw);invoke(activity[0],"saveLocalLyrics",new Class<?>[]{});HashMap<String,String> refs=(HashMap<String,String>)field(activity[0],"pluginTrackRefs");refs.put(a,id+"\na");refs.put(b,id+"\nb");invoke(activity[0],"showLyrics",new Class<?>[]{});});
            helper.await(()->((LyricsStageDialog)field(activity[0],"lyricsStage")).stage.timeline().wordTimed,"offline source timings were not restored into actual lyric stage");
            ui(()->invoke(activity[0],"loadStageLyrics",new Class<?>[]{boolean.class},true));helper.await(()->((LyricsStageDialog)field(activity[0],"lyricsStage")).stage.timeline().wordTimed,"offline refresh discarded saved lyrics");
            check(data.getString("lyricCalls","0").equals("0"),"offline refresh called source lyrics");
            ui(()->{String uri=service.snapshot().uri;((HashMap<String,String>)field(activity[0],"localLyrics")).remove(uri);invoke(activity[0],"loadStageLyrics",new Class<?>[]{boolean.class},true);LyricsStageDialog stage=(LyricsStageDialog)field(activity[0],"lyricsStage");check(stage.stage.timeline().lines.isEmpty()&&((String)field(stage.stage,"message")).contains("尚未保存"),"missing offline lyric did not explain how to get lyrics");((HashMap<String,String>)field(activity[0],"localLyrics")).put(uri,raw);invoke(activity[0],"saveLocalLyrics",new Class<?>[]{});stage.dismiss();service.pause();service.seek(3200);});
            helper.await(()->Math.abs(service.snapshot().position-3200)<200,"offline seek failed");String resumeUri=service.snapshot().uri,resumeQuality=service.snapshot().requestedQuality;
            ui(()->{service.clearQueue();activity[0].finish();});test.waitForIdleSync();activity[0]=launch();service=service(activity[0]);ui(()->invoke(activity[0],"toggle",new Class<?>[]{}));helper.await(()->service.snapshot().playing&&service.snapshot().uri.equals(resumeUri)&&service.snapshot().requestedQuality.equals(resumeQuality)&&service.snapshot().position>=3000,"empty-service restore lost offline mode, cached tier or position");
            ui(()->{check(service.snapshot().offlineOnly&&service.queueSnapshot().length()==3,"cold resume rebuilt global library queue");try{service.enqueue(new JSONObject().put("uri","https://example.com/uncached"),false);throw new AssertionError("online row entered offline queue");}catch(IOException expected){} });
            check(data.getString("streamCalls","0").equals("0")&&data.getString("playedCalls","0").equals("0")&&data.getString("lyricCalls","0").equals("0"),"offline workflow invoked source exports");
            // Online refresh failure and empty response must retain the last usable timed lyric.
            JSONArray online=new JSONArray().put(new JSONObject().put("uri",resumeUri).put("title","Online cached source"));ui(()->PlaybackService.startQueue(context,online,0,0,80,false,false,resumeQuality));helper.await(()->service.snapshot().playing&&!service.snapshot().offlineOnly,"online cached fixture did not start");
            ui(()->{((HashMap<String,String>)field(activity[0],"pluginTrackRefs")).put(resumeUri,id+"\n"+android.net.Uri.parse(resumeUri).getQueryParameter("id"));invoke(activity[0],"showLyrics",new Class<?>[]{});invoke(activity[0],"loadStageLyrics",new Class<?>[]{boolean.class},true);});helper.await(()->data.getString("lyricCalls","0").equals("1")&&((LyricsStageDialog)field(activity[0],"lyricsStage")).stage.timeline().wordTimed,"failed refresh did not restore saved lyrics");
            data.edit().putString("empty","true").commit();ui(()->invoke(activity[0],"loadStageLyrics",new Class<?>[]{boolean.class},true));helper.await(()->data.getString("lyricCalls","0").equals("2")&&((LyricsStageDialog)field(activity[0],"lyricsStage")).stage.timeline().wordTimed,"empty refresh wiped saved timed lyrics");
            ui(()->{((LyricsStageDialog)field(activity[0],"lyricsStage")).dismiss();invoke(activity[0],"startOfflineQueue",new Class<?>[]{ArrayList.class,String.class,int.class},entries,selectedKey,0);});helper.await(()->service.snapshot().playing&&service.snapshot().offlineOnly,"offline queue did not reopen");
            verifyCacheRemoval(activity[0],cache,a,b);
            ui(()->{cache.clear();service.next();});helper.await(()->!service.snapshot().loading&&!service.snapshot().error.isEmpty(),"expired offline cache silently fell back to network");check(data.getString("streamCalls","0").equals("0"),"missing cached file called provider stream");
            System.out.println("MYGO_OFFLINE_WORKFLOW_OK: actual cache picker; stopped server; separate offline queue; same track in two cached qualities; per-entry next/shuffle/completion/repeat; no stream/played/lyric source calls; unchanged online quality preference; native seek and empty-service resume; cached source word timings; safe failed/empty refresh; missing file never falls back online");
        }finally{ui(()->{service.clearQueue();if(activity[0]!=null)activity[0].finish();});cache.clear();helper.cleanup(id);SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> entry:original.entrySet()){Object value=entry.getValue();String key=entry.getKey();if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof Set)edit.putStringSet(key,(Set<String>)value);}edit.commit();}
    }
    static final class Server implements AutoCloseable {
        final ServerSocket socket=new ServerSocket(0);final byte[] audio=BackgroundPlaybackChecks.wav(18000);
        Server()throws Exception{Thread thread=new Thread(()->{while(!socket.isClosed())try(Socket client=socket.accept()){BufferedReader in=new BufferedReader(new InputStreamReader(client.getInputStream(),"US-ASCII"));String line;while((line=in.readLine())!=null&&!line.isEmpty()){}OutputStream out=client.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\nContent-Length: "+audio.length+"\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));out.write(audio);out.flush();}catch(Exception ignored){}});thread.setDaemon(true);thread.start();}
        String url(){return "http://127.0.0.1:"+socket.getLocalPort()+"/song";}
        public void close()throws Exception{socket.close();}
    }
}
