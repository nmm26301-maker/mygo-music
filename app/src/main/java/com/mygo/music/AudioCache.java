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
    static final long DEFAULT_LIMIT=512L*1024*1024,MAX_TRACK=128L*1024*1024;
    interface Callback {void done(boolean ok,String message);}
    static final class Entry {
        final String key,track,title,quality;final File file;final long bytes;long used;
        Entry(String key,String track,String title,String quality,File file,long bytes,long used){this.key=key;this.track=track;this.title=title;this.quality=quality;this.file=file;this.bytes=bytes;this.used=used;}
    }
    private final File root;private final HashMap<String,Entry> entries=new HashMap<>();private final HashSet<String> pending=new HashSet<>();
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16));
    private String pinned="";
    private long limit,generation;private volatile boolean closed;
    AudioCache(Context context){this(new File(context.getNoBackupFilesDir(),"audio-cache"),DEFAULT_LIMIT);}
    AudioCache(File root,long limit){this.root=root;this.limit=Math.max(1,limit);root.mkdirs();load();}
    private void load(){File[] files=root.listFiles();if(files==null)return;for(File metadata:files){String name=metadata.getName();if(name.endsWith(".part")){metadata.delete();continue;}if(!name.matches("[a-f0-9]{64}\\.json"))continue;String key=name.substring(0,64);File audio=new File(root,key+".audio");try{JSONObject data;try(InputStream in=new FileInputStream(metadata)){byte[] bytes=new byte[(int)Math.min(metadata.length(),32768)];int n=in.read(bytes);data=new JSONObject(new String(bytes,0,Math.max(0,n),java.nio.charset.StandardCharsets.UTF_8));}String track=data.getString("track"),quality=data.getString("quality");long size=data.getLong("bytes");if(!key.equals(key(track,quality))||!audio.isFile()||size<=0||audio.length()!=size)throw new IOException("invalid audio cache");entries.put(key,new Entry(key,track,data.optString("title",track),quality,audio,size,data.optLong("used",System.currentTimeMillis())));}catch(Exception error){metadata.delete();audio.delete();}}for(File file:files)if(file.getName().matches("[a-f0-9]{64}\\.audio")&&!entries.containsKey(file.getName().substring(0,64)))file.delete();trim(null);}
    private static String key(String track,String quality){try{byte[] digest=MessageDigest.getInstance("SHA-256").digest((track+"\n"+quality).getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder value=new StringBuilder();for(byte b:digest)value.append(String.format(Locale.ROOT,"%02x",b&255));return value.toString();}catch(Exception error){throw new IllegalStateException(error);}}
    synchronized File find(String track,String quality){Entry entry=entries.get(key(track,quality));if(entry==null)return null;if(!entry.file.isFile()||entry.file.length()!=entry.bytes){removeEntry(entry);return null;}entry.used=System.currentTimeMillis();try{writeMetadata(entry);}catch(Exception ignored){}return entry.file;}
    synchronized ArrayList<Entry> list(){ArrayList<Entry> result=new ArrayList<>(entries.values());Collections.sort(result,(a,b)->Long.compare(b.used,a.used));return result;}
    synchronized long bytes(){long total=0;for(Entry entry:entries.values())total+=entry.bytes;return total;}
    synchronized void setLimit(long bytes){limit=Math.max(1,bytes);trim(null);}
    synchronized boolean busy(){return !pending.isEmpty();}
    synchronized boolean owns(File file){if(file==null)return false;for(Entry entry:entries.values())if(entry.file.equals(file))return true;return false;}
    synchronized void pin(File file){pinned="";if(file!=null)for(Entry entry:entries.values())if(entry.file.equals(file)){pinned=entry.key;break;}trim(null);}
    void store(String track,String title,String quality,String address,Map<String,String> headers,Callback callback){
        String key=key(track,quality);final long epoch;final Map<String,String> requestHeaders=new HashMap<>(headers);
        synchronized(this){if(closed){callback.done(false,"缓存已关闭");return;}if(entries.containsKey(key)){callback.done(true,"已有缓存");return;}if(!pending.add(key)){callback.done(false,"正在缓存");return;}epoch=generation;}
        try{worker.execute(()->download(key,track,title,quality,address,requestHeaders,epoch,callback));}catch(RejectedExecutionException error){synchronized(this){pending.remove(key);}callback.done(false,"缓存任务已满，请稍后重试");}
    }
    private void checkEpoch(long epoch)throws IOException{synchronized(this){if(closed||generation!=epoch||Thread.currentThread().isInterrupted())throw new IOException("缓存任务已取消");}}
    private void download(String key,String track,String title,String quality,String address,Map<String,String> headers,long epoch,Callback callback){File temporary=new File(root,key+"-"+epoch+".part");HttpURLConnection connection=null;boolean ok=false;String message="";
        try{URL url=new URL(address);for(int redirect=0;;redirect++){
                checkEpoch(epoch);if(!url.getProtocol().equals("http")&&!url.getProtocol().equals("https"))throw new IOException("缓存只支持 HTTP 音频");connection=(HttpURLConnection)url.openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(12000);connection.setReadTimeout(20000);connection.setRequestProperty("Accept-Encoding","identity");for(Map.Entry<String,String> header:headers.entrySet())if(!header.getKey().equalsIgnoreCase("Range"))connection.setRequestProperty(header.getKey(),header.getValue());int status=connection.getResponseCode();if(status>=300&&status<400){String location=connection.getHeaderField("Location");if(location==null||redirect>=5)throw new IOException("音频重定向失败");URL next=new URL(url,location);if(!next.getHost().equalsIgnoreCase(url.getHost())||next.getPort()!=url.getPort()||!next.getProtocol().equals(url.getProtocol()))headers.clear();connection.disconnect();connection=null;url=next;continue;}if(status!=200)throw new IOException("缓存音频返回 HTTP "+status);break;
            }
            String type=connection.getContentType();if(type!=null&&(type.contains("text/")||type.contains("json")||type.contains("html")))throw new IOException("来源返回的不是音频");long expected=connection.getContentLengthLong();long max;synchronized(this){max=Math.min(limit,MAX_TRACK);}if(expected>max)throw new IOException("单曲超过缓存大小限制");long total=0;
            try(InputStream in=connection.getInputStream();FileOutputStream out=new FileOutputStream(temporary)){byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1){checkEpoch(epoch);total+=n;if(total>max)throw new IOException("单曲超过缓存大小限制");out.write(buffer,0,n);}out.getFD().sync();}
            if(total<=0||(expected>=0&&expected!=total))throw new IOException("音频未完整下载");
            MediaMetadataRetriever metadata=new MediaMetadataRetriever();try{metadata.setDataSource(temporary.getAbsolutePath());String duration=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);if(duration==null||Long.parseLong(duration)<=0||!"yes".equalsIgnoreCase(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)))throw new IOException("下载内容没有可识别的音频");}finally{metadata.release();}
            synchronized(this){checkEpoch(epoch);File target=new File(root,key+".audio");if(!temporary.renameTo(target))throw new IOException("无法保存完整音频");Entry entry=new Entry(key,track,title,quality,target,total,System.currentTimeMillis());try{writeMetadata(entry);}catch(Exception error){target.delete();throw error;}entries.put(key,entry);trim(key);ok=true;message="已缓存，可离线播放";}
        }catch(Exception error){message=error.getMessage()==null?"缓存失败":error.getMessage();}finally{if(connection!=null)connection.disconnect();temporary.delete();synchronized(this){pending.remove(key);}callback.done(ok,message);}
    }
    private void writeMetadata(Entry entry)throws Exception{JSONObject data=new JSONObject().put("track",entry.track).put("title",entry.title).put("quality",entry.quality).put("bytes",entry.bytes).put("used",entry.used);File temporary=new File(root,entry.key+"-meta.part"),target=new File(root,entry.key+".json");try(FileOutputStream out=new FileOutputStream(temporary)){out.write(data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));out.getFD().sync();}if(!temporary.renameTo(target)){temporary.delete();throw new IOException("无法保存缓存索引");}}
    private void removeEntry(Entry entry){entries.remove(entry.key);entry.file.delete();new File(root,entry.key+".json").delete();}
    private void trim(String keep){ArrayList<Entry> sorted=new ArrayList<>(entries.values());Collections.sort(sorted,(a,b)->Long.compare(a.used,b.used));for(Entry entry:sorted){if(bytes()<=limit&&entries.size()<=512)break;if(!entry.key.equals(keep)&&!entry.key.equals(pinned))removeEntry(entry);}}
    synchronized void remove(String track,String quality){Entry entry=entries.get(key(track,quality));if(entry!=null)removeEntry(entry);}
    synchronized void clear(){generation++;pinned="";entries.clear();File[] files=root.listFiles();if(files!=null)for(File file:files)file.delete();}
    @Override public synchronized void close(){closed=true;generation++;worker.shutdownNow();}
}
