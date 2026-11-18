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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

final class ManualCacheChecks {
    private final Instrumentation test;
    private final OfflinePlaybackChecks actions;
    private final Context context;
    private static final String SAVED_LINE="离线逐字歌词";

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
                "export function stream({id}){const path={hls:'fmp4.m3u8',opus:'opus.m3u8'}[id]||'bilibili-stereo.m4a';return {url:'"+server.base()+"/'+path,type:id==='direct'?'aac':'hls',sourceCodec:id==='opus'?'opus':'aac',quality:'high',headers:{'X-Audio':'native','Authorization':'Bearer native-fixture'}};}"+
                "export function lyrics({id}){return id==='direct'?{lines:[{start:0,end:6000,translation:'Saved translation',words:[{text:'"+SAVED_LINE+"',start:700,end:4300}]}]}:'';}");
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
                if(kind.equals("direct"))verifyOfflineLyrics(activity,service,cache,prefs,id,entry,picker);
            }
            AudioCache.Entry saved=verifyDecodedSave(activity,service,cache,prefs,id,server,picker);
            OfflinePlaybackChecks.check(!server.badHeader&&!prefs.getBoolean("auto_cache",true),"manual save lost authorized headers or changed automatic preference");
            actions.ui(()->{
                picker[0]=(AlertDialog)OfflinePlaybackChecks.invoke(activity,"showAudioCache",new Class<?>[]{});
                View row=OfflinePlaybackChecks.described(picker[0].getWindow().getDecorView(),"播放离线缓存："+saved.key);
                OfflinePlaybackChecks.check(row!=null&&row.performClick(),"saved row could not start offline playback");
            });
            actions.helper.await(()->service.snapshot().offlineOnly&&service.snapshot().playing&&service.snapshot().uri.equals(saved.track)&&service.snapshot().position>250,"saved track did not play without server or plugin");
            System.out.println("MYGO_DECODED_CACHE_OK: already-decoded bytes survive clear/retry, track switch, original file deletion and source shutdown; offline native playback and source codec preserved");
            System.out.println("MYGO_MANUAL_CACHE_OK: native dialog, auto disabled, direct bytes and HLS AAC packets, source headers and quality, live list, same native player, offline playback");
        }finally{
            actions.ui(()->{if(picker[0]!=null)picker[0].dismiss();service.clearQueue();manager.close();activity.finish();});
            cache.clear();
            actions.helper.cleanup(id);
            new TurntableLyricsChecks(test).restore(prefs,old);
        }
    }

    private void verifyOfflineLyrics(MainActivity activity,PlaybackService service,AudioCache cache,
            SharedPreferences prefs,String id,AudioCache.Entry entry,AlertDialog[] picker)throws Exception{
        actions.helper.await(()->cache.hasLyrics(entry),"manual cache never saved source lyrics");
        OfflinePlaybackChecks.check(!new JSONObject(prefs.getString("local_lyrics","{}")).has(entry.track),
            "fixture opened lyrics online before saving");
        prefs.edit().putBoolean("plugin_enabled_"+id,false).commit();
        LyricsStageDialog[] stage={null};
        try{
            actions.ui(()->{
                picker[0]=(AlertDialog)OfflinePlaybackChecks.invoke(activity,"showAudioCache",new Class<?>[]{});
                View row=OfflinePlaybackChecks.described(picker[0].getWindow().getDecorView(),"播放离线缓存："+entry.key);
                OfflinePlaybackChecks.check(row!=null&&row.performClick(),"saved song was missing from the actual cache list");
            });
            actions.helper.await(()->service.snapshot().offlineOnly&&service.snapshot().playing&&service.snapshot().uri.equals(entry.track),
                "cached song failed to start after disabling its source");
            actions.ui(()->{
                service.pause();
                OfflinePlaybackChecks.invoke(activity,"showLyrics",new Class<?>[]{});
                stage[0]=(LyricsStageDialog)OfflinePlaybackChecks.field(activity,"lyricsStage");
            });
            actions.helper.await(()->stage[0].stage.timeline().wordTimed,"offline stage did not load the saved timing");
            actions.ui(()->{
                LyricsTimeline timeline=stage[0].stage.timeline();
                LyricsTimeline.Line line=timeline.lines.get(0);
                OfflinePlaybackChecks.check(!timeline.estimatedWordTiming&&line.text.equals(SAVED_LINE)
                    &&line.words.get(0).start==700&&line.words.get(0).end==4300&&line.translation.equals("Saved translation"),
                    "offline lyrics lost the original word timestamps or translation");
                stage[0].dismiss();
            });
            verifyLibraryLyrics(activity,service,prefs,id,entry);
        }finally{prefs.edit().putBoolean("plugin_enabled_"+id,true).commit();}
        System.out.println("MYGO_MANUAL_CACHE_LYRICS_OK: cached audio and original word timestamps plus translation available in the actual offline lyric stage with the source disabled");
    }

    private void verifyLibraryLyrics(MainActivity activity,PlaybackService service,SharedPreferences prefs,
            String source,AudioCache.Entry entry)throws Exception{
        prefs.edit().putBoolean("player_remember_theme",true).putString("player_theme","immersive").commit();
        actions.ui(()->{
            @SuppressWarnings("unchecked")
            java.util.List<android.net.Uri> tracks=(java.util.List<android.net.Uri>)OfflinePlaybackChecks.field(activity,"tracks");
            @SuppressWarnings("unchecked")
            Map<String,String> refs=(Map<String,String>)OfflinePlaybackChecks.field(activity,"pluginTrackRefs");
            refs.put(entry.track,source+"\ndirect");
            int index=tracks.indexOf(android.net.Uri.parse(entry.track));
            OfflinePlaybackChecks.check(index>=0,"cached song did not appear in the normal library");
            OfflinePlaybackChecks.invoke(activity,"playIndex",new Class<?>[]{int.class},index);
        });
        actions.helper.await(()->!service.snapshot().offlineOnly&&service.snapshot().playing
            &&service.snapshot().uri.equals(entry.track)&&service.snapshot().stream.startsWith("file:"),
            "normal library did not start the cached audio with the source disabled");
        ThemedPlayerDialog[] player={null};
        LyricsStageDialog[] stage={null};
        actions.ui(()->{
            service.pause();
            OfflinePlaybackChecks.invoke(activity,"showNowPlaying",new Class<?>[]{});
            player[0]=(ThemedPlayerDialog)OfflinePlaybackChecks.field(activity,"themedPlayer");
        });
        actions.helper.await(()->player[0]!=null&&player[0].stage.timeline().wordTimed,
            "normal player did not load source timing from the cache");
        actions.ui(()->{
            player[0].dismiss();
            OfflinePlaybackChecks.invoke(activity,"showLyrics",new Class<?>[]{});
            stage[0]=(LyricsStageDialog)OfflinePlaybackChecks.field(activity,"lyricsStage");
        });
        actions.helper.await(()->stage[0].stage.timeline().wordTimed,"normal lyric stage did not load saved lyrics");
        actions.ui(()->OfflinePlaybackChecks.invoke(activity,"loadStageLyrics",new Class<?>[]{boolean.class},true));
        actions.helper.await(()->stage[0].stage.timeline().wordTimed
            &&stage[0].stage.timeline().lines.get(0).text.equals(SAVED_LINE),
            "a failed explicit lyric refresh discarded the saved lyrics");
        actions.ui(()->stage[0].dismiss());
        System.out.println("MYGO_LIBRARY_CACHE_LYRICS_OK: normal library playback, immersive theme and isolated lyric stage read cached lyrics; explicit refresh falls back after the disabled source fails");
    }

    private AudioCache.Entry verifyDecodedSave(MainActivity activity,PlaybackService service,AudioCache cache,
            SharedPreferences prefs,String id,HlsPlaybackChecks.Server server,AlertDialog[] picker)throws Exception{
        String opus="plugin://"+id+"?id=opus",direct="plugin://"+id+"?id=direct";
        JSONArray queue=new JSONArray().put(new JSONObject().put("uri",opus).put("title","Compatible Opus"))
                .put(new JSONObject().put("uri",direct).put("title","Next song"));
        actions.ui(()->PlaybackService.startQueue(context,queue,0,0,75,false,false,"standard"));
        actions.helper.await(()->service.snapshot().playing&&service.snapshot().uri.equals(opus)&&service.snapshot().stream.startsWith("file:"),"Opus did not use the compatible decoder");
        File temporary=(File)FullPortChecks.field(service,"decodedFile");
        OfflinePlaybackChecks.check(temporary!=null&&temporary.isFile(),"no actual compatibility output to save");
        byte[] expected=Files.readAllBytes(temporary.toPath());
        ThreadPoolExecutor worker=(ThreadPoolExecutor)FullPortChecks.field(cache,"worker");
        CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1),cancelled=new CountDownLatch(1);
        AtomicBoolean oldSucceeded=new AtomicBoolean();
        worker.execute(()->{held.countDown();try{release.await(20,TimeUnit.SECONDS);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}});
        try{
            OfflinePlaybackChecks.check(held.await(5,TimeUnit.SECONDS),"cache worker barrier did not start");
            actions.ui(()->service.cacheCurrentSong((ok,message)->{oldSucceeded.set(ok);cancelled.countDown();}));
            OfflinePlaybackChecks.check(cache.busy(),"first compatible save was not queued");
            actions.ui(cache::clear);
            OfflinePlaybackChecks.check(!cache.busy()&&cache.list().isEmpty(),"clear kept the first request");
            actions.ui(()->picker[0]=(AlertDialog)OfflinePlaybackChecks.invoke(activity,"showAudioCache",new Class<?>[]{}));
            SourcePlaylistUiChecks.click(test,"缓存当前歌曲");
            actions.helper.await(cache::busy,"manual retry after clear did not queue");
            actions.ui(service::next);
            actions.helper.await(()->service.snapshot().playing&&service.snapshot().uri.equals(direct),"next track did not play");
            OfflinePlaybackChecks.check(!temporary.exists(),"track switch retained the temporary audio path");
            server.close();
            prefs.edit().putBoolean("plugin_enabled_"+id,false).commit();
        }finally{release.countDown();}
        OfflinePlaybackChecks.check(cancelled.await(5,TimeUnit.SECONDS)&&!oldSucceeded.get(),"a cancelled save published audio");
        actions.helper.await(()->!cache.busy()&&prefs.getString("cache_status","").equals("兼容音频已缓存，可离线播放")&&cache.list().stream().anyMatch(entry->entry.track.equals(opus)&&entry.quality.equals("standard")),"queued save lost the compatible file after track switch: "+prefs.getString("cache_status",""));
        AudioCache.Entry saved=cache.list().stream().filter(entry->entry.track.equals(opus)).findFirst().get();
        OfflinePlaybackChecks.check(!cache.busy()&&cache.list().size()==1&&Arrays.equals(Files.readAllBytes(saved.file.toPath()),expected),"wrong file, duplicate entry or incomplete compatible audio cached");
        OfflinePlaybackChecks.check(saved.info.optString("sourceCodec").equals("opus"),"compatible FLAC hid the original source codec");
        actions.ui(()->picker[0].dismiss());
        return saved;
    }
}
