package com.mygo.music;

import android.app.*;
import android.content.*;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/** A source may return valid MP4 audio while native preparation stalls; decode the complete original safely. */
final class MediaFallbackChecks {
    final Instrumentation test;final Context context;final CompletionChecks helper;
    MediaFallbackChecks(Instrumentation test){this.test=test;context=test.getTargetContext();helper=new CompletionChecks(test);}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    final class Server implements AutoCloseable {
        final ServerSocket server=new ServerSocket(0);final ExecutorService workers=Executors.newCachedThreadPool();final byte[] audio=new NativeAudioChecks(test).asset("quality-aac.m4a");final java.util.concurrent.atomic.AtomicInteger requests=new java.util.concurrent.atomic.AtomicInteger();final CountDownLatch nativeHeld=new CountDownLatch(1),release=new CountDownLatch(1);
        Server()throws Exception{Thread thread=new Thread(()->{while(!server.isClosed())try{Socket socket=server.accept();workers.execute(()->serve(socket));}catch(Exception ignored){}});thread.setDaemon(true);thread.start();}
        String url(){return "http://127.0.0.1:"+server.getLocalPort()+"/audio.mp4";}
        void serve(Socket socket){try(Socket client=socket){client.setSoTimeout(5000);BufferedReader in=new BufferedReader(new InputStreamReader(client.getInputStream(),"US-ASCII"));String first=in.readLine(),line,range="";if(first==null)return;while((line=in.readLine())!=null&&!line.isEmpty())if(line.toLowerCase(java.util.Locale.ROOT).startsWith("range:"))range=line.substring(6).trim();int request=requests.incrementAndGet(),start=0,end=audio.length-1,status=200;if(range.matches("bytes=[0-9]+-[0-9]*")){String[] values=range.substring(6).split("-",-1);start=Integer.parseInt(values[0]);if(!values[1].isEmpty())end=Math.min(end,Integer.parseInt(values[1]));status=206;}if(start>=audio.length)return;OutputStream out=client.getOutputStream();out.write(("HTTP/1.1 "+status+" OK\r\nContent-Type: audio/mp4\r\nContent-Length: "+(end-start+1)+"\r\nAccept-Ranges: bytes\r\n"+(status==206?"Content-Range: bytes "+start+"-"+end+"/"+audio.length+"\r\n":"")+"Connection: close\r\n\r\n").getBytes("US-ASCII"));if(request==2){out.write(audio,0,Math.min(32,audio.length));out.flush();nativeHeld.countDown();release.await(12,TimeUnit.SECONDS);}else{out.write(audio,start,end-start+1);out.flush();}}catch(Exception ignored){}}
        public void close(){release.countDown();try{server.close();}catch(Exception ignored){}workers.shutdownNow();}
    }
    void run()throws Exception{
        String id="mp4-prepare-fallback",uri="plugin://"+id+"?id=one";helper.cleanup(id);SharedPreferences prefs=context.getSharedPreferences("library",0);boolean cache=prefs.getBoolean("auto_cache",true);String selected=prefs.getString("selected_source","");prefs.edit().remove("selected_source").putBoolean("auto_cache",false).commit();MainActivity activity=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));PlaybackService[] service={null};helper.await(()->{service[0]=(PlaybackService)FullPortChecks.field(activity,"playbackService");return service[0]!=null;},"MP4 fallback service missing");PluginManager manager=new PluginManager(activity);
        try(Server server=new Server()){
            helper.install(manager,id,"stream",new JSONArray(),"export function stream(){return {url:'"+server.url()+"',type:'mp4',sourceCodec:'aac',quality:'high'};}");JSONArray queue=new JSONArray().put(new JSONObject().put("uri",uri).put("title","Valid MP4 fallback"));test.runOnMainSync(()->{try{service[0].clearQueue();PlaybackService.startQueue(context,queue,0,0,80,false,false,"high");}catch(Exception error){throw new RuntimeException(error);}});check(server.nativeHeld.await(12,TimeUnit.SECONDS),"native MP4 preparation did not reach held bytes");helper.await(()->service[0].snapshot().loading&&FullPortChecks.field(service[0],"prepareTimeout")!=null,"native MP4 preparation was not pending");Runnable timeout=(Runnable)FullPortChecks.field(service[0],"prepareTimeout");test.runOnMainSync(timeout);helper.await(()->service[0].snapshot().uri.equals(uri)&&service[0].snapshot().playing&&service[0].snapshot().position>150,"complete MP4 fallback did not reach native audio: "+service[0].snapshot().error);File converted=(File)FullPortChecks.field(service[0],"decodedFile");check(converted!=null&&converted.isFile()&&server.requests.get()>=3,"MP4 fallback was not a complete local conversion");check(service[0].snapshot().qualityInfo.contains("AAC")&&!service[0].snapshot().qualityInfo.contains("无损编码"),"compatible FLAC changed original lossy provenance");test.runOnMainSync(()->{service[0].pause();service[0].seek(3000);});helper.await(()->Math.abs(service[0].snapshot().position-3000)<250,"MP4 converted seek failed");test.runOnMainSync(service[0]::resume);helper.await(()->service[0].snapshot().position>3200,"MP4 converted resume clock did not advance");long generation=service[0].snapshot().generation;test.runOnMainSync(()->{service[0].stopPlayback();timeout.run();});check(!converted.exists()&&service[0].snapshot().player==null&&service[0].snapshot().generation>generation,"stale timeout restarted stopped audio or retained decoded file");
            System.out.println("MYGO_MP4_FALLBACK_OK: real stalled native preparation, bounded complete MP4-to-FLAC compatibility conversion, advancing native audio, original AAC provenance, pause/seek/resume and stopped-session cleanup/stale timeout guard");
        }finally{test.runOnMainSync(()->{service[0].clearQueue();manager.close();activity.finish();});helper.cleanup(id);prefs.edit().putBoolean("auto_cache",cache).putString("selected_source",selected).commit();}
    }
}
