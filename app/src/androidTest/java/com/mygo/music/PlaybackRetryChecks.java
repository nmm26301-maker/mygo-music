package com.mygo.music;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.SystemClock;
import org.json.*;
import java.io.*;
import java.util.*;

/** Real failure dialogs, plugin authorization and native audio must retain the service queue. */
final class PlaybackRetryChecks {
    final Instrumentation test;final Context context;final CompletionChecks helper;
    PlaybackRetryChecks(Instrumentation test){this.test=test;context=test.getTargetContext();helper=new CompletionChecks(test);}
    interface Action{void run()throws Exception;}
    void ui(Action action)throws Exception{Throwable[] error={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable failure){error[0]=failure;}});if(error[0]!=null)throw new AssertionError("Retry action failed",error[0]);}
    static Object invoke(Object target,String name,Class<?>[] types,Object...args)throws Exception{java.lang.reflect.Method method=target.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(target,args);}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    void playing(PlaybackService service,String uri,long previous)throws Exception{helper.await(()->{PlaybackService.Snapshot state=service.snapshot();return state.uri.equals(uri)&&state.generation>previous&&state.playing&&state.position>200;},"retry never reached native audio for the new request");int at=service.snapshot().position;helper.await(()->service.snapshot().position>at+150,"retry native clock stalled");}
    void unchanged(PlaybackService service,PlaybackService.Snapshot before)throws Exception{ui(()->{PlaybackService.Snapshot after=service.snapshot();check(after.generation==before.generation&&after.player==before.player&&after.uri.equals(before.uri)&&after.playing,"stale retry replaced the current player");});}
    void queue(PlaybackService service,JSONArray expected)throws Exception{ui(()->{PlaybackService.Snapshot state=service.snapshot();check(service.queueSnapshot().toString().equals(expected.toString()),"retry rebuilt or changed selected queue metadata");check(state.index==1&&state.shuffle&&state.repeat&&state.volume==37&&state.requestedQuality.equals("lossless"),"retry lost selected index, modes, volume or quality");});}
    void run()throws Exception{
        String id="playback-retry",a="plugin://"+id+"?id=a",b="plugin://"+id+"?id=b";
        SharedPreferences prefs=context.getSharedPreferences("library",0),data=context.getSharedPreferences("plugin_data_"+id,0);Map<String,?> original=new HashMap<>(prefs.getAll());
        prefs.edit().remove("selected_source").putBoolean("auto_cache",false).putString("play_quality","lossless").putBoolean("plugin_network_"+id,true).commit();helper.cleanup(id);
        MainActivity activity=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));PluginManager manager=new PluginManager(activity);PlaybackService[] selected={null};helper.await(()->{selected[0]=(PlaybackService)FullPortChecks.field(activity,"playbackService");return selected[0]!=null;},"retry service not connected");PlaybackService service=selected[0];
        File outsider=new File(context.getCacheDir(),"retry-outside-playlist.wav");try(FileOutputStream out=new FileOutputStream(outsider)){out.write(BackgroundPlaybackChecks.wav(18000));}String outside=Uri.fromFile(outsider).toString();
        try(OfflinePlaybackChecks.Server server=new OfflinePlaybackChecks.Server()){
            helper.install(manager,id,"stream",new JSONArray(),"export function stream({id,quality}){host.storage.set('calls',(host.storage.get('calls')||0)+1);if(!host.storage.get('allowed'))throw Error('fixture authorization required');return {url:"+JSONObject.quote(server.url())+",type:'wav',quality};}export async function signIn(){host.storage.set('signIns',(host.storage.get('signIns')||0)+1);if(host.storage.get('delayLogin')){host.storage.set('loginPending',true);await new Promise(r=>setTimeout(r,1400));}host.storage.set('allowed',true);return {id:'fixture-account'};}");
            ui(()->{service.clearQueue();invoke(activity,"addNamed",new Class<?>[]{Uri.class,String.class},Uri.parse(outside),"Outside selected playlist");});
            JSONArray originalQueue=new JSONArray().put(new JSONObject().put("uri",a).put("title","Retry A").put("track",new JSONObject().put("id","a").put("name","Retry A"))).put(new JSONObject().put("uri",b).put("title","Retry B").put("track",new JSONObject().put("id","b").put("name","Retry B")).put("fixtureScope","selected-playlist"));
            ui(()->PlaybackService.startQueue(context,originalQueue,1,2300,37,true,true,"lossless"));helper.await(()->service.snapshot().uri.equals(b)&&!service.snapshot().error.isEmpty(),"retry fixture did not fail");PlaybackService.Snapshot failed=service.snapshot();data.edit().putString("allowed","true").commit();
            SourcePlaylistUiChecks.click(test,"重试");playing(service,b,failed.generation);queue(service,originalQueue);ui(()->check(service.snapshot().position>=2300,"retry lost selected resume position"));
            PlaybackService.Snapshot current=service.snapshot();ui(()->check(!service.retryFailed(failed),"consumed failure could be retried twice"));unchanged(service,current);

            // The old dialog must not replace even a new session for the same URI.
            data.edit().putString("allowed","false").commit();ui(()->PlaybackService.startQueue(context,originalQueue,1,0,37,true,true,"lossless"));helper.await(()->service.snapshot().generation>current.generation&&!service.snapshot().error.isEmpty(),"second retry fixture did not fail");PlaybackService.Snapshot stale=service.snapshot();
            data.edit().putString("allowed","true").commit();JSONArray replacement=new JSONArray().put(new JSONObject().put("uri",b).put("title","New same-song session"));ui(()->PlaybackService.startQueue(context,replacement,0,0,80,false,false,"high"));playing(service,b,stale.generation);PlaybackService.Snapshot sameSong=service.snapshot();SourcePlaylistUiChecks.click(test,"重试");unchanged(service,sameSong);check(service.queueSnapshot().length()==1,"old retry replaced new same-song queue");

            // Login uses the identical retry route and preserves the original scoped queue.
            data.edit().putString("allowed","false").commit();ui(()->PlaybackService.startQueue(context,originalQueue,1,2300,37,true,true,"lossless"));helper.await(()->service.snapshot().generation>sameSong.generation&&!service.snapshot().error.isEmpty(),"login retry did not fail");PlaybackService.Snapshot loginFailure=service.snapshot();SourcePlaylistUiChecks.click(test,"登录后重试");playing(service,b,loginFailure.generation);queue(service,originalQueue);check(data.getString("signIns","0").equals("1"),"login retry did not call source signIn");

            data.edit().putString("allowed","false").putString("delayLogin","true").commit();long previous=service.snapshot().generation;ui(()->PlaybackService.startQueue(context,originalQueue,1,0,37,true,true,"lossless"));helper.await(()->service.snapshot().generation>previous&&!service.snapshot().error.isEmpty(),"delayed login retry did not fail");PlaybackService.Snapshot delayed=service.snapshot();SourcePlaylistUiChecks.click(test,"登录后重试");helper.await(()->data.getString("loginPending","false").equals("true"),"delayed login never began");JSONArray local=new JSONArray().put(new JSONObject().put("uri",outside).put("title","New local session"));ui(()->PlaybackService.startQueue(context,local,0,0,80,false,false,"high"));playing(service,outside,delayed.generation);PlaybackService.Snapshot switched=service.snapshot();helper.await(()->data.getString("allowed","false").equals("true"),"delayed login never completed");SystemClock.sleep(350);test.waitForIdleSync();unchanged(service,switched);ui(()->{service.stopPlayback();check(!service.retryFailed(delayed),"stopped session accepted a late retry");});
            System.out.println("MYGO_PLAYBACK_RETRY_OK: actual failure dialog and sign-in retry reach advancing native audio; original scoped queue, index, metadata, position, shuffle, repeat, volume and quality retained; consumed failure, same-song replacement, delayed login after track change and stopped-session stale retries rejected");
        }finally{
            ui(()->{service.clearQueue();manager.close();ArrayList<Uri> tracks=(ArrayList<Uri>)FullPortChecks.field(activity,"tracks");ArrayList<String> names=(ArrayList<String>)FullPortChecks.field(activity,"names");for(String uri:new String[]{a,b,outside}){int at=tracks.indexOf(Uri.parse(uri));if(at>=0){tracks.remove(at);names.remove(at);}}invoke(activity,"saveLibrary",new Class<?>[]{});activity.finish();});helper.cleanup(id);outsider.delete();
            SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> entry:original.entrySet()){Object value=entry.getValue();String key=entry.getKey();if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof Set)edit.putStringSet(key,(Set<String>)value);}edit.commit();
        }
    }
}
