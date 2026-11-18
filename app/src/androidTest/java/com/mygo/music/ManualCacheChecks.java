package com.mygo.music;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

final class ManualCacheChecks {
    private final Instrumentation test;
    private final OfflinePlaybackChecks actions;
    private final Context context;

    ManualCacheChecks(Instrumentation test){
        this.test=test;
        actions=new OfflinePlaybackChecks(test);
        context=test.getTargetContext();
    }

    void run()throws Exception{
        String id="manual-cache";
        SharedPreferences prefs=context.getSharedPreferences("library",0);
        Map<String,?> old=new HashMap<>(prefs.getAll());
        prefs.edit().putBoolean("auto_cache",false).putString("play_quality","standard").commit();
        MainActivity activity=actions.launch();
        PlaybackService service=actions.service(activity);
        PluginManager manager=new PluginManager(activity);
        AudioCache cache=AudioCache.shared(context);
        HlsPlaybackChecks media=new HlsPlaybackChecks(test);
        AlertDialog[] picker={null};
        try(HlsPlaybackChecks.Server server=media.new Server(true)){
            actions.ui(service::clearQueue);
            cache.clear();
            actions.helper.install(manager,id,"stream",new JSONArray(),
                "export function stream({id}){return {url:'"+server.base()+"/'+(id==='hls'?'fmp4.m3u8':'bilibili-stereo.m4a'),type:id==='hls'?'hls':'aac',quality:'high',headers:{'X-Audio':'native','Authorization':'Bearer native-fixture'}};}");
            for(String kind:new String[]{"direct","hls"}){
                String uri="plugin://"+id+"?id="+kind;
                JSONArray queue=new JSONArray().put(new JSONObject().put("uri",uri).put("title","Manual cache "+kind));
                actions.ui(()->PlaybackService.startQueue(context,queue,0,0,75,false,false,"standard"));
                actions.helper.await(()->service.snapshot().playing&&service.snapshot().uri.equals(uri)&&service.snapshot().position>250,"manual cache fixture did not play: "+kind);
                PlaybackService.Snapshot before=service.snapshot();
                OfflinePlaybackChecks.check(!cache.busy()&&cache.list().stream().noneMatch(entry->entry.track.equals(uri)),"automatic caching was not disabled");
                actions.ui(()->picker[0]=(AlertDialog)OfflinePlaybackChecks.invoke(activity,"showAudioCache",new Class<?>[]{}));
                SourcePlaylistUiChecks.click(test,"缓存当前歌曲");
                String success=kind.equals("hls")?"分段音频已完整缓存，可离线播放":"已缓存，可离线播放";
                actions.helper.await(()->cache.list().stream().anyMatch(entry->entry.track.equals(uri)&&entry.quality.equals("standard")),"cache was not saved for "+kind+": "+prefs.getString("cache_status",""));
                AudioCache.Entry entry=cache.list().stream().filter(item->item.track.equals(uri)).findFirst().get();
                actions.helper.await(()->prefs.getString("cache_status","").equals(success)&&OfflinePlaybackChecks.described(picker[0].getWindow().getDecorView(),"播放离线缓存："+entry.key)!=null,"open list did not show the completed save");
                PlaybackService.Snapshot after=service.snapshot();
                OfflinePlaybackChecks.check(after.player==before.player&&after.generation==before.generation&&after.playing,"saving interrupted playback");
                OfflinePlaybackChecks.check(entry.info.optString("sourceCodec").equals("aac"),"saved codec did not match the source");
                if(kind.equals("direct")){
                    OfflinePlaybackChecks.check(Arrays.equals(Files.readAllBytes(entry.file.toPath()),media.asset("bilibili-stereo.m4a")),"direct save changed the source audio bytes");
                }else{
                    HlsCacheChecks hls=new HlsCacheChecks(test);
                    byte[] expected=new Fmp4AacChecks(test).strip(media.asset("fmp4-expected.aac"));
                    OfflinePlaybackChecks.check(Arrays.equals(hls.packets(entry.file),expected),"segmented save changed the source AAC packets");
                }
                actions.ui(()->picker[0].dismiss());
            }
            OfflinePlaybackChecks.check(!server.badHeader&&!prefs.getBoolean("auto_cache",true),"manual save lost authorized headers or changed automatic preference");
            server.close();
            prefs.edit().putBoolean("plugin_enabled_"+id,false).commit();
            AudioCache.Entry saved=cache.list().stream().filter(entry->entry.track.endsWith("id=hls")).findFirst().get();
            actions.ui(()->{
                picker[0]=(AlertDialog)OfflinePlaybackChecks.invoke(activity,"showAudioCache",new Class<?>[]{});
                View row=OfflinePlaybackChecks.described(picker[0].getWindow().getDecorView(),"播放离线缓存："+saved.key);
                OfflinePlaybackChecks.check(row!=null&&row.performClick(),"saved row could not start offline playback");
            });
            actions.helper.await(()->service.snapshot().offlineOnly&&service.snapshot().playing&&service.snapshot().position>250,"saved track did not play without server or plugin");
            System.out.println("MYGO_MANUAL_CACHE_OK: native dialog, auto disabled, direct bytes and HLS AAC packets, source headers and quality, live list, same native player, offline playback");
        }finally{
            actions.ui(()->{if(picker[0]!=null)picker[0].dismiss();service.clearQueue();manager.close();activity.finish();});
            cache.clear();
            actions.helper.cleanup(id);
            new TurntableLyricsChecks(test).restore(prefs,old);
        }
    }
}
