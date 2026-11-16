package com.mygo.music;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.*;
import java.io.*;
import java.util.*;

/** Actual settings save, source acknowledgements, native helper and advancing audio. */
final class SetupRetryChecks {
    final Instrumentation test;final Context context;final CompletionChecks helper;final PlaybackRetryChecks playback;
    SetupRetryChecks(Instrumentation test){this.test=test;context=test.getTargetContext();helper=new CompletionChecks(test);playback=new PlaybackRetryChecks(test);}
    void ui(PlaybackRetryChecks.Action action)throws Exception{playback.ui(action);}
    void check(boolean ok,String message){PlaybackRetryChecks.check(ok,message);}
    void open()throws Exception{SourcePlaylistUiChecks.click(test,"打开设置");helper.await(()->SourcePlaylistUiChecks.node(test,"保存并重试")!=null,"setup settings did not offer continuation");}
    void type(String value)throws Exception{SourcePlaylistUiChecks.type(test,value);}
    void save()throws Exception{SourcePlaylistUiChecks.click(test,"保存并重试");}
    boolean settingsWritesFinished()throws Exception{java.lang.reflect.Field field=SourceSettingQueue.class.getDeclaredField("queues");field.setAccessible(true);return ((Map<?,?>)field.get(null)).isEmpty();}

    void helperPermission()throws Exception{
        ArrayDeque<AccessibilityNodeInfo> nodes=new ArrayDeque<>();AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();if(root!=null)nodes.add(root);
        while(!nodes.isEmpty()){AccessibilityNodeInfo node=nodes.remove();CharSequence text=node.getText();if(text!=null&&text.toString().startsWith("允许启动辅助程序")){check(!node.isChecked()&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK),"native helper grant could not be selected");return;}for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null)nodes.add(child);}}
        throw new AssertionError("native helper permission control missing");
    }
    void startFailed(PlaybackService service,JSONArray queue,int position,String message)throws Exception{
        long[] before={0};String expected=queue.getJSONObject(1).getString("uri");
        ui(()->{before[0]=service.snapshot().generation;PlaybackService.startQueue(context,queue,1,position,37,true,true,"lossless");});
        helper.await(()->{PlaybackService.Snapshot state=service.snapshot();return state.generation>before[0]&&state.uri.equals(expected)&&!state.error.isEmpty();},message);
    }
    void run()throws Exception{
        String id="setup-retry",a="plugin://"+id+"?id=a",b="plugin://"+id+"?id=b";helper.cleanup(id);
        SharedPreferences prefs=context.getSharedPreferences("library",0),settings=context.getSharedPreferences("plugin_settings_"+id,0),data=context.getSharedPreferences("plugin_data_"+id,0);Map<String,?> original=new HashMap<>(prefs.getAll());
        prefs.edit().remove("selected_source").putBoolean("auto_cache",false).putString("play_quality","lossless").putBoolean("plugin_network_"+id,true).putBoolean("plugin_process_"+id,false).commit();
        MainActivity activity=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));PluginManager manager=new PluginManager(activity);PlaybackService[] selected={null};helper.await(()->{selected[0]=(PlaybackService)FullPortChecks.field(activity,"playbackService");return selected[0]!=null;},"setup retry service missing");PlaybackService service=selected[0];
        try(OfflinePlaybackChecks.Server server=new OfflinePlaybackChecks.Server()){
            String code="function setup(message){const e=Error(message);e.code='setup';throw e;}export async function setSetting({value}){host.storage.set('pending',true);await new Promise(r=>setTimeout(r,700));if(value==='reject'){host.storage.set('pending',false);throw Error('fixture refused configuration');}host.storage.set('ack',value);host.storage.set('pending',false);}export async function stream({quality}){host.storage.set('calls',(host.storage.get('calls')||0)+1);const token=host.setting('token');if(!token)setup('请填写配置值并允许辅助程序');if(host.storage.get('ack')!==token)throw Error('setting was not acknowledged');let p;try{p=host.spawn('yt-dlp',['--version']);}catch(e){setup('请允许启动辅助程序');}p.closeStdin();let first='',line;while((line=await p.readLine())!==null)if(!first)first=line;if(await p.wait()!==0)throw Error(p.errorOutput());host.storage.set('nativeVersion',first);return {url:"+JSONObject.quote(server.url())+",type:'wav',quality};}";
            helper.install(manager,id,"stream",new JSONArray().put(new JSONObject().put("key","token").put("label","配置值").put("type","password")),code);
            File manifest=new File(context.getFilesDir(),"plugins/"+id+"/plugin.json");StringBuilder raw=new StringBuilder();try(Reader reader=new FileReader(manifest)){char[] buf=new char[2048];int n;while((n=reader.read(buf))!=-1)raw.append(buf,0,n);}try(Writer writer=new FileWriter(manifest)){writer.write(new JSONObject(raw.toString()).put("permissions",new JSONObject().put("process",new JSONArray().put("yt-dlp"))).toString());}manager.restart(id);
            JSONArray queue=new JSONArray().put(new JSONObject().put("uri",a).put("title","Setup A")).put(new JSONObject().put("uri",b).put("title","Setup B").put("scope","selected-list"));
            ui(service::clearQueue);startFailed(service,queue,2300,"setup fixture did not fail");PlaybackService.Snapshot failed=service.snapshot();open();type("ready");helperPermission();save();
            helper.await(()->data.getString("pending","false").equals("true"),"configuration acknowledgement never started");check(service.snapshot().generation==failed.generation,"settings retried before source acknowledgement");playback.playing(service,b,failed.generation);playback.queue(service,queue);check(service.snapshot().position>=2300&&!data.getString("nativeVersion","").isEmpty(),"setup continuation lost position or skipped real helper");

            // Closing while a source write is pending must not resume playback later.
            settings.edit().clear().commit();startFailed(service,queue,0,"pending-close fixture did not fail");PlaybackService.Snapshot pending=service.snapshot();open();type("closed");save();helper.await(()->data.getString("pending","false").equals("true"),"pending-close write missing");SourcePlaylistUiChecks.click(test,"关闭");helper.await(()->data.getString("ack","").equals(JSONObject.quote("closed"))&&data.getString("pending","false").equals("false")&&settingsWritesFinished(),"closed settings transaction and callbacks did not finish");check(service.snapshot().generation==pending.generation&&!service.snapshot().playing,"closed settings resumed an abandoned request");

            // A rejected setting rolls back and keeps retry pending until a later successful save.
            settings.edit().clear().commit();startFailed(service,queue,0,"rejection fixture did not fail");PlaybackService.Snapshot rejected=service.snapshot();open();type("reject");save();helper.await(()->SourcePlaylistUiChecks.node(test,"设置保存失败")!=null,"configuration failure did not stay visible");ui(()->{check(settings.getString("token","").isEmpty(),"failed save persisted a rejected fixture setting");check(service.snapshot().generation==rejected.generation,"failed save retried playback: expected generation="+rejected.generation+", actual="+service.snapshot().generation);});SourcePlaylistUiChecks.click(test,"恢复原值");type("fixed");save();playback.playing(service,b,rejected.generation);

            // A same-song replacement consumes the old session without accepting its saved retry.
            settings.edit().clear().commit();startFailed(service,queue,0,"stale fixture did not fail");PlaybackService.Snapshot stale=service.snapshot();open();type("new-session");settings.edit().putString("token","new-session").commit();data.edit().putString("ack",JSONObject.quote("new-session")).commit();JSONArray replacement=new JSONArray().put(new JSONObject().put("uri",b).put("title","Replacement same song"));ui(()->PlaybackService.startQueue(context,replacement,0,0,80,false,false,"high"));playback.playing(service,b,stale.generation);PlaybackService.Snapshot current=service.snapshot();save();helper.await(()->data.getString("pending","false").equals("false"),"stale settings did not finish");SystemClock.sleep(200);playback.unchanged(service,current);check(service.queueSnapshot().toString().equals(replacement.toString()),"settings rebuilt a superseded queue");

            // Configuration retry also uses the guarded quality replacement route.
            ui(()->{service.pause();service.seek(1800);});helper.await(()->Math.abs(service.snapshot().position-1800)<250,"quality baseline seek failed");settings.edit().clear().commit();ui(()->service.changeQuality("lossless"));helper.await(()->!service.snapshot().qualityIssue.isEmpty(),"quality setup error missing");open();type("quality");save();helper.await(()->service.snapshot().requestedQuality.equals("lossless")&&!service.snapshot().qualitySwitching&&service.snapshot().qualityIssue.isEmpty(),"saved configuration did not retry quality");check(!service.snapshot().playing&&Math.abs(service.snapshot().position-1800)<300&&service.queueSnapshot().toString().equals(replacement.toString()),"quality setup retry lost pause, position or queue");
            System.out.println("MYGO_SETUP_RETRY_OK realHelper=true nativeClock=true acknowledgedSave=true closeWhilePending=true rollbackThenRetry=true sameSongStaleRejected=true pausedQualityPreserved=true");
        }finally{ui(()->{service.clearQueue();manager.close();ArrayList<Uri> tracks=(ArrayList<Uri>)FullPortChecks.field(activity,"tracks");ArrayList<String> names=(ArrayList<String>)FullPortChecks.field(activity,"names");for(String uri:new String[]{a,b}){int at=tracks.indexOf(Uri.parse(uri));if(at>=0){tracks.remove(at);names.remove(at);}}PlaybackRetryChecks.invoke(activity,"saveLibrary",new Class<?>[]{});activity.finish();});helper.cleanup(id);new TurntableLyricsChecks(test).restore(prefs,original);}
    }
}
