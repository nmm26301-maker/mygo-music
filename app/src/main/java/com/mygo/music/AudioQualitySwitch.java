package com.mygo.music;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;

/** Prepare a replacement without releasing or changing the currently audible player. */
final class AudioQualitySwitch implements AutoCloseable {
    interface Listener {void prepared(AudioQualitySwitch change);void failed(AudioQualitySwitch change,String message);}
    final String quality;
    final long generation;
    final Context context;
    final PluginManager plugins;
    final AudioCache cache;
    final ExecutorService workers;
    final Listener listener;
    final Handler main=new Handler(Looper.getMainLooper());
    volatile boolean closed;
    MediaPlayer player;HlsPlayback hlsPlayback;
    Uri address;
    HashMap<String,String> headers=new HashMap<>();
    JSONObject info=new JSONObject();
    String kind="",track="",title="";
    boolean cached,sourceResolved,decoded;
    File temporary;
    final Runnable timeout=()->fail("音质准备超时，继续播放原音频");
    AudioQualitySwitch(Context context,PluginManager plugins,AudioCache cache,ExecutorService workers,String quality,long generation,Listener listener){
        this.context=context.getApplicationContext();this.plugins=plugins;this.cache=cache;this.workers=workers;this.quality=quality;this.generation=generation;this.listener=listener;
    }
    void start(String track,String title){
        this.track=track;this.title=title;main.postDelayed(timeout,180000);
        try{
            File file=cache.find(track,quality);
            if(file!=null){cached=true;info=cache.metadata(track,quality);cache.pinSwitch(file);prepare(Uri.fromFile(file),new HashMap<>());return;}
            Uri uri=Uri.parse(track);PluginManager.PluginInfo source=null;
            for(PluginManager.PluginInfo item:plugins.list())if(item.enabled&&item.id.equals(uri.getAuthority())){source=item;break;}
            if(source==null)throw new IOException("插件未启用，继续播放原音频");
            final String id=source.id;
            plugins.export(id,"stream",new JSONObject().put("id",uri.getQueryParameter("id")).put("quality",quality),(ok,raw)->{
                if(closed)return;if(!ok){fail(raw);return;}
                try{
                    JSONObject result=new JSONObject(raw);info=AudioQuality.info(result);kind=result.optString("type");if(kind.isEmpty())kind=result.optString("codec");sourceResolved=true;
                    String url=result.optString("url");if(!url.startsWith("http://")&&!url.startsWith("https://"))throw new IOException("插件未返回音频地址");
                    HashMap<String,String> requestHeaders=plugins.mediaHeaders(id,url,"audio",result.optJSONObject("headers"));
                    workers.execute(()->{try(PluginMedia.Connection connection=PluginMedia.open(url,requestHeaders,true)){
                        Uri resolved=Uri.parse(connection.http.getURL().toString());HashMap<String,String> values=new HashMap<>(connection.headers);
                        main.post(()->{if(closed)return;if(kind.equalsIgnoreCase("alac"))decode(resolved,values);else prepare(resolved,values);});
                    }catch(Exception error){main.post(()->fail(error.getMessage()));}});
                }catch(Exception error){fail(error.getMessage());}
            });
        }catch(Exception error){fail(error.getMessage());}
    }
    private void prepare(Uri uri,HashMap<String,String> values){
        if(closed)return;
        try{
            address=uri;headers=new HashMap<>(values);if(!PluginMedia.contains(headers,"User-Agent"))headers.put("User-Agent","MyGoMusic/5.22");
            MediaPlayer created=new MediaPlayer();player=created;created.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());created.setWakeMode(context,PowerManager.PARTIAL_WAKE_LOCK);
            if("http".equals(uri.getScheme())||"https".equals(uri.getScheme())){HashMap<String,String> restricted=new HashMap<>(headers);restricted.put("android-allow-cross-domain-redirect","0");Uri playable=uri;if(!cached&&HlsPlayback.matches(uri,kind)){String source=Uri.parse(track).getAuthority();hlsPlayback=new HlsPlayback(plugins,source==null?"":source,uri.toString(),headers);playable=hlsPlayback.address;restricted.clear();}created.setDataSource(context,playable,restricted);}else created.setDataSource(context,uri);
            created.setOnPreparedListener(mp->{if(!closed&&player==mp)listener.prepared(this);});
            created.setOnErrorListener((mp,what,extra)->{if(closed||player!=mp)return true;if(!decoded&&AudioDecodeJob.candidate(uri,kind,extra))decode(uri,values);else fail(hlsPlayback!=null&&!hlsPlayback.failure().isEmpty()?"新音质分段音频无法播放："+hlsPlayback.failure():"新音质音频无法播放："+what+" / "+extra);return true;});
            created.prepareAsync();
        }catch(Exception error){fail(error.getMessage());}
    }
    private void decode(Uri uri,HashMap<String,String> values){
        if(closed)return;decoded=true;releasePlayer();
        long epoch=cache.epoch();
        try{workers.execute(()->{
            File result=null;
            try{
                result=AudioDecodeJob.decode(context,uri,values,()->{if(closed||Thread.currentThread().isInterrupted())throw new IOException("音质切换已取消");});
                File stored=null;
                if(!closed&&context.getSharedPreferences("library",0).getBoolean("auto_cache",true))try{stored=cache.importFile(track,title,quality,info,result,epoch);}catch(Exception ignored){}
                final File output=result,saved=stored;
                main.post(()->{if(closed){output.delete();return;}if(saved!=null&&saved.isFile()){output.delete();cached=true;cache.pinSwitch(saved);prepare(Uri.fromFile(saved),new HashMap<>());}else{cached=false;temporary=output;prepare(Uri.fromFile(output),new HashMap<>());}});
            }catch(Exception error){if(result!=null)result.delete();main.post(()->fail(error.getMessage()));}
        });}catch(Exception error){fail(error.getMessage());}
    }
    private void fail(String message){if(!closed)listener.failed(this,message==null||message.isEmpty()?"新音质准备失败":message);}
    void transferred(){player=null;temporary=null;close();}
    private void releasePlayer(){if(hlsPlayback!=null){hlsPlayback.close();hlsPlayback=null;}if(player!=null){player.setOnPreparedListener(null);player.setOnErrorListener(null);player.setOnSeekCompleteListener(null);player.release();player=null;}}
    @Override public void close(){if(closed)return;closed=true;main.removeCallbacksAndMessages(null);releasePlayer();if(temporary!=null){temporary.delete();temporary=null;}cache.pinSwitch(null);}
}
