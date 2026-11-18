package com.mygo.music;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** Complete, validated audio files only; never reuses an expiring stream URL offline. */
final class AudioCache implements Closeable {
    private static AudioCache shared;
    static synchronized AudioCache shared(Context context){if(shared==null||shared.closed)shared=new AudioCache(context.getApplicationContext());return shared;}
    static final long DEFAULT_LIMIT=512L*1024*1024,MAX_TRACK=128L*1024*1024;
    interface Callback {void done(boolean ok,String message);}
    static final class Entry {
        final String key,track,title,quality;final File file;final long bytes;long used;JSONObject info=new JSONObject();
        Entry(String key,String track,String title,String quality,File file,long bytes,long used){this.key=key;this.track=track;this.title=title;this.quality=quality;this.file=file;this.bytes=bytes;this.used=used;}
    }
    private Context context;private final File root;private final HashMap<String,Entry> entries=new HashMap<>();private final HashMap<String,Long> pending=new HashMap<>();
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16));
    private String pinned="",switchPinned="";
    private long limit,generation;private volatile boolean closed;
    AudioCache(Context context){this(new File(context.getNoBackupFilesDir(),"audio-cache"),DEFAULT_LIMIT);this.context=context.getApplicationContext();}
    AudioCache(File root,long limit){this.root=root;this.limit=Math.max(1,limit);worker.allowCoreThreadTimeOut(true);root.mkdirs();load();}
    private void load(){File[] files=root.listFiles();if(files==null)return;for(File metadata:files){String name=metadata.getName();if(name.endsWith(".part")){metadata.delete();continue;}if(!name.matches("[a-f0-9]{64}\\.json"))continue;String key=name.substring(0,64);File audio=new File(root,key+".audio");try{JSONObject data;try(InputStream in=new FileInputStream(metadata)){byte[] bytes=new byte[(int)Math.min(metadata.length(),32768)];int n=in.read(bytes);data=new JSONObject(new String(bytes,0,Math.max(0,n),java.nio.charset.StandardCharsets.UTF_8));}String track=data.getString("track"),quality=data.getString("quality");long size=data.getLong("bytes");if(!key.equals(key(track,quality))||!audio.isFile()||size<=0||audio.length()!=size)throw new IOException("invalid audio cache");Entry entry=new Entry(key,track,data.optString("title",track),quality,audio,size,data.optLong("used",System.currentTimeMillis()));entry.info=AudioQuality.info(data.optJSONObject("audioInfo"));entries.put(key,entry);}catch(Exception error){metadata.delete();audio.delete();}}for(File file:files)if(file.getName().matches("[a-f0-9]{64}\\.audio")&&!entries.containsKey(file.getName().substring(0,64)))file.delete();trim(null);}
    private static String key(String track,String quality){try{byte[] digest=MessageDigest.getInstance("SHA-256").digest((track+"\n"+quality).getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder value=new StringBuilder();for(byte b:digest)value.append(String.format(Locale.ROOT,"%02x",b&255));return value.toString();}catch(Exception error){throw new IllegalStateException(error);}}
    private Entry existing(String key){Entry entry=entries.get(key);if(entry!=null&&(!entry.file.isFile()||entry.file.length()!=entry.bytes)){removeEntry(entry);return null;}return entry;}
    synchronized File find(String track,String quality){Entry entry=existing(key(track,quality));if(entry==null)return null;entry.used=System.currentTimeMillis();try{writeMetadata(entry);}catch(Exception ignored){}return entry.file;}
    synchronized ArrayList<Entry> list(){
        ArrayList<Entry> result=new ArrayList<>();
        for(String key:new ArrayList<>(entries.keySet())){
            Entry entry=existing(key);
            if(entry!=null)result.add(entry);
        }
        result.sort((a,b)->Long.compare(b.used,a.used));
        return result;
    }
    synchronized long bytes(){long total=0;for(Entry entry:entries.values())total+=entry.bytes;return total;}
    synchronized void setLimit(long bytes){limit=Math.max(1,bytes);trim(null);}
    synchronized boolean busy(){return !pending.isEmpty();}
    synchronized boolean owns(File file){if(file==null)return false;for(Entry entry:entries.values())if(entry.file.equals(file))return true;return false;}
    synchronized void pin(File file){pinned="";if(file!=null)for(Entry entry:entries.values())if(entry.file.equals(file)){pinned=entry.key;break;}trim(null);}
    synchronized void pinSwitch(File file){switchPinned="";if(file!=null)for(Entry entry:entries.values())if(entry.file.equals(file)){switchPinned=entry.key;break;}trim(null);}
    void store(String track,String title,String quality,String address,Map<String,String> headers,Callback callback){
        store(track,title,quality,address,headers,new JSONObject(),callback);
    }
    synchronized JSONObject metadata(String track,String quality){Entry entry=existing(key(track,quality));return entry==null?new JSONObject():AudioQuality.info(entry.info);}
    void store(String track,String title,String quality,String address,Map<String,String> headers,JSONObject info,Callback callback){
        String key=key(track,quality);final long epoch;final Map<String,String> requestHeaders=new HashMap<>(headers);final JSONObject values=AudioQuality.info(info);
        synchronized(this){if(closed){callback.done(false,"缓存已关闭");return;}if(existing(key)!=null){callback.done(true,"已有缓存");return;}if(pending.containsKey(key)){callback.done(false,"正在缓存");return;}epoch=generation;pending.put(key,epoch);}
        try{worker.execute(()->download(key,track,title,quality,address,requestHeaders,values,epoch,callback));}catch(RejectedExecutionException error){finish(key,epoch);callback.done(false,"缓存任务已满，请稍后重试");}
    }
    private synchronized void finish(String key,long epoch){Long active=pending.get(key);if(active!=null&&active==epoch)pending.remove(key);}
    synchronized long epoch(){return generation;}
    private long trackLimit(){synchronized(this){return Math.min(limit,MAX_TRACK);}}
    private void validate(File file)throws Exception{MediaMetadataRetriever metadata=new MediaMetadataRetriever();try{metadata.setDataSource(file.getAbsolutePath());String duration=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);if(duration==null||Long.parseLong(duration)<=0||!"yes".equalsIgnoreCase(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)))throw new IOException("缓存内容没有完整可识别的音频");}finally{metadata.release();}}
    private synchronized File publishFile(String key,String track,String title,String quality,JSONObject info,File temporary,long epoch)throws Exception{
        checkEpoch(epoch);long bytes=temporary.length();if(bytes<=0||bytes>trackLimit())throw new IOException("单曲超过缓存大小限制");File target=new File(root,key+".audio");if(!temporary.renameTo(target))throw new IOException("无法保存完整音频");Entry entry=new Entry(key,track,title,quality,target,bytes,System.currentTimeMillis());entry.info=AudioQuality.info(info);try{writeMetadata(entry);}catch(Exception error){target.delete();throw error;}entries.put(key,entry);trim(key);return target;
    }
    File importFile(String track,String title,String quality,JSONObject info,File input,long epoch)throws Exception{
        String key=key(track,quality);File temporary=File.createTempFile(key+"-import-",".part",root);try{long count=0;try(InputStream in=new FileInputStream(input);FileOutputStream out=new FileOutputStream(temporary)){byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1){checkEpoch(epoch);if((count+=n)>trackLimit())throw new IOException("单曲超过缓存大小限制");out.write(buffer,0,n);}out.getFD().sync();}validate(temporary);return publishFile(key,track,title,quality,info,temporary,epoch);}finally{temporary.delete();}
    }
    void storeHls(String track,String title,String quality,String address,Map<String,String> headers,JSONObject info,Callback callback){
        String key=key(track,quality);final long epoch;Map<String,String> originalHeaders=new HashMap<>(headers);JSONObject metadata=AudioQuality.info(info);
        synchronized(this){if(closed){callback.done(false,"缓存已关闭");return;}if(existing(key)!=null){callback.done(true,"已有缓存");return;}if(pending.containsKey(key)){callback.done(false,"正在缓存");return;}epoch=generation;pending.put(key,epoch);}
        try{worker.execute(()->{File temporary=new File(root,key+"-hls-"+epoch+".part");boolean ok=false;String message="";
            try{if(context==null)throw new IOException("缺少 Android 音频解码环境");URL origin=new URL(address);long max=trackLimit();
                PluginManager media=new PluginManager(context);try{
                    try{JSONObject copied=writeHls(media,track,origin,address,originalHeaders,temporary,max,epoch,true);for(Iterator<String> keys=copied.keys();keys.hasNext();){String field=keys.next();metadata.put(field,copied.get(field));}}
                    catch(NativeAudio.CopyUnsupported unsupported){checkEpoch(epoch);temporary.delete();writeHls(media,track,origin,address,originalHeaders,temporary,max,epoch,false);}
                }finally{media.close();}
                validate(temporary);publishFile(key,track,title,quality,metadata,temporary,epoch);ok=true;message="分段音频已完整缓存，可离线播放";
            }catch(Exception error){message=error.getMessage()==null?"分段缓存失败":error.getMessage();}finally{temporary.delete();finish(key,epoch);callback.done(ok,message);}
        });}catch(RejectedExecutionException error){finish(key,epoch);callback.done(false,"缓存任务已满");}
    }
    private JSONObject writeHls(PluginManager media,String track,URL origin,String address,Map<String,String> originalHeaders,File temporary,long max,long epoch,boolean copy)throws Exception{
        try(HlsAudioProxy proxy=new HlsAudioProxy(child->{JSONObject explicit=new JSONObject();if(PluginMedia.sameOrigin(origin,new URL(child)))for(Map.Entry<String,String> entry:originalHeaders.entrySet())explicit.put(entry.getKey(),entry.getValue());android.net.Uri source=android.net.Uri.parse(track);String id="plugin".equals(source.getScheme())?source.getAuthority():"";return media.mediaHeaders(id==null?"":id,child,"audio",explicit);},()->checkEpoch(epoch),Math.max(4096,max*2))){
            try{JSONObject result;if(copy)result=NativeAudio.copyM4a(context,proxy.address(address),temporary,max,()->checkEpoch(epoch));else{NativeAudio.convert(context,proxy.address(address),temporary,max,()->checkEpoch(epoch));result=new JSONObject();}if(!proxy.failure().isEmpty())throw new IOException(proxy.failure());return result;}catch(Exception error){if(!proxy.failure().isEmpty())throw new IOException(proxy.failure());throw error;}
        }
    }
    private void checkEpoch(long epoch)throws IOException{synchronized(this){if(closed||generation!=epoch||Thread.currentThread().isInterrupted())throw new IOException("缓存任务已取消");}}
    private void download(String key,String track,String title,String quality,String address,Map<String,String> headers,JSONObject info,long epoch,Callback callback){File temporary=new File(root,key+"-"+epoch+".part");PluginMedia.Connection source=null;HttpURLConnection connection=null;boolean ok=false;String message="";
        try{checkEpoch(epoch);source=PluginMedia.openFullAudio(address,headers);connection=source.http;if(LiveAacPlayback.matches(connection))throw new IOException("直播音频没有完整时长，无法作为离线歌曲缓存");
            String type=connection.getContentType();if(type!=null)type=type.toLowerCase(Locale.ROOT);if(type!=null&&(type.contains("text/")||type.contains("json")||type.contains("html")||type.contains("mpegurl")||type.contains("dash+xml")))throw new IOException("来源返回的不是音频");long expected=PluginMedia.fullAudioLength(connection);long max;synchronized(this){max=Math.min(limit,MAX_TRACK);}if(expected>max)throw new IOException("单曲超过缓存大小限制");long total=0;
            try(InputStream in=connection.getInputStream();FileOutputStream out=new FileOutputStream(temporary)){byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1){checkEpoch(epoch);total+=n;if(total>max)throw new IOException("单曲超过缓存大小限制");out.write(buffer,0,n);}out.getFD().sync();}
            if(total<=0||(expected>=0&&expected!=total))throw new IOException("音频未完整下载");
            try(InputStream in=new FileInputStream(temporary)){byte[] prefix=new byte[256];int read=in.read(prefix);String text=new String(prefix,0,Math.max(0,read),java.nio.charset.StandardCharsets.UTF_8).trim();if(text.startsWith("#EXTM3U")||text.startsWith("<?xml")||text.startsWith("<MPD"))throw new IOException("分段播放清单不能作为完整音频缓存");}MediaMetadataRetriever metadata=new MediaMetadataRetriever();try{metadata.setDataSource(temporary.getAbsolutePath());String duration=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);if(duration==null||Long.parseLong(duration)<=0||!"yes".equalsIgnoreCase(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)))throw new IOException("下载内容没有可识别的音频");}finally{metadata.release();}
            publishFile(key,track,title,quality,info,temporary,epoch);ok=true;message="已缓存，可离线播放";
        }catch(Exception error){message=error.getMessage()==null?"缓存失败":error.getMessage();}finally{if(source!=null)source.close();temporary.delete();finish(key,epoch);callback.done(ok,message);}
    }
    private void writeMetadata(Entry entry)throws Exception{JSONObject data=new JSONObject().put("track",entry.track).put("title",entry.title).put("quality",entry.quality).put("bytes",entry.bytes).put("used",entry.used).put("audioInfo",entry.info);File temporary=new File(root,entry.key+"-meta.part"),target=new File(root,entry.key+".json");try(FileOutputStream out=new FileOutputStream(temporary)){out.write(data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));out.getFD().sync();}if(!temporary.renameTo(target)){temporary.delete();throw new IOException("无法保存缓存索引");}}
    private void removeEntry(Entry entry){entries.remove(entry.key);entry.file.delete();new File(root,entry.key+".json").delete();}
    private void trim(String keep){ArrayList<Entry> sorted=new ArrayList<>(entries.values());Collections.sort(sorted,(a,b)->Long.compare(a.used,b.used));for(Entry entry:sorted){if(bytes()<=limit&&entries.size()<=512)break;if(!entry.key.equals(keep)&&!entry.key.equals(pinned)&&!entry.key.equals(switchPinned))removeEntry(entry);}}
    synchronized void remove(String track,String quality){Entry entry=entries.get(key(track,quality));if(entry!=null)removeEntry(entry);}
    synchronized void removeSaved(Entry entry)throws IOException{
        if(existing(entry.key)!=entry)throw new IOException("这份缓存已变化，请刷新后再试");
        if(entry.key.equals(pinned)||entry.key.equals(switchPinned))throw new IOException("这份缓存正在使用，请切换歌曲或音质后再删除");
        if(!entry.file.delete()&&entry.file.exists())throw new IOException("无法删除这份缓存，请稍后再试");
        entries.remove(entry.key);
        new File(root,entry.key+".json").delete();
    }
    synchronized void clear(){generation++;pending.clear();pinned="";switchPinned="";entries.clear();File[] files=root.listFiles();if(files!=null)for(File file:files)file.delete();}
    @Override public synchronized void close(){closed=true;generation++;worker.shutdownNow();}
}
