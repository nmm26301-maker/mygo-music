package com.mygo.music;

import android.app.*;
import android.content.*;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Reproduce a range-only CDN with real native playback, switching, decode and complete offline bytes. */
final class AudioRangeChecks {
    final Instrumentation test;final CompletionChecks helper;final Context context;
    AudioRangeChecks(Instrumentation test){this.test=test;helper=new CompletionChecks(test);context=test.getTargetContext();}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    final class Server implements AutoCloseable {
        final ServerSocket socket=new ServerSocket(0,12,InetAddress.getByName("127.0.0.1"));final ExecutorService workers=Executors.newCachedThreadPool();
        final byte[] wav=BackgroundPlaybackChecks.wav(30000),alac=new NativeAudioChecks(test).asset("alac-24.m4a");String redirect="";volatile boolean leaked;volatile int ranges;
        Server()throws Exception{Thread loop=new Thread(()->{while(!socket.isClosed())try{Socket client=socket.accept();workers.execute(()->serve(client));}catch(Exception ignored){}});loop.setDaemon(true);loop.start();}
        String base(){return "http://127.0.0.1:"+socket.getLocalPort();}
        void serve(Socket client){try(Socket active=client){active.setSoTimeout(5000);BufferedReader in=new BufferedReader(new InputStreamReader(active.getInputStream(),"US-ASCII"));String first=in.readLine(),line,range="";boolean auth=false;if(first==null)return;while((line=in.readLine())!=null&&!line.isEmpty()){if(line.toLowerCase(Locale.ROOT).startsWith("range:"))range=line.substring(6).trim();if(line.equalsIgnoreCase("X-Audio-Key: fixture"))auth=true;}String path=first.split(" ")[1];OutputStream out=active.getOutputStream();
            if(path.equals("/redirect")){out.write(("HTTP/1.1 302 Found\r\nLocation: "+redirect+"/public\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));return;}
            if(path.equals("/public"))leaked|=auth;
            if((!auth&&!path.equals("/public"))||(!path.equals("/ignored")&&!range.matches("bytes=[0-9]+-[0-9]*"))){out.write("HTTP/1.1 567 Test\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes("US-ASCII"));return;}
            byte[] body=path.endsWith(".m4a")?alac:wav;int start=0,end=body.length-1,status=200;if(!path.equals("/ignored")){String[] parts=range.substring(6).split("-",-1);start=Integer.parseInt(parts[0]);if(!parts[1].isEmpty())end=Math.min(end,Integer.parseInt(parts[1]));status=206;ranges++;}if(path.equals("/partial"))end=body.length/2;if(start>=body.length||end<start)return;
            out.write(("HTTP/1.1 "+status+" Test\r\nContent-Type: "+(path.endsWith(".m4a")?"audio/mp4":"audio/wav")+"\r\n"+(!path.equals("/unknown-length")?"Content-Length: "+(end-start+1)+"\r\n":"")+"Accept-Ranges: bytes\r\n"+(status==206&&!path.equals("/missing-range")?"Content-Range: bytes "+start+"-"+end+"/"+body.length+"\r\n":"")+"Connection: close\r\n\r\n").getBytes("US-ASCII"));out.write(body,start,path.equals("/truncated")?(end-start+1)/2:end-start+1);out.flush();
        }catch(Exception ignored){}}
        public void close(){try{socket.close();}catch(Exception ignored){}workers.shutdownNow();}
    }
    void run()throws Exception{
        String id="audio-range-fixture";helper.cleanup(id);Map<String,String> headers=new HashMap<>();headers.put("X-Audio-Key","fixture");File directory=new File(context.getCacheDir(),"audio-range-cache");PluginArchive.delete(directory);
        try(Server server=new Server();Server other=new Server()){
            boolean rejected=false;try(PluginMedia.Connection ignored=PluginMedia.open(server.base()+"/audio",headers,false)){}catch(IOException expected){rejected=expected.getMessage().contains("567");}check(rejected,"range-only reproduction unexpectedly accepted a normal GET");
            try(PluginMedia.Connection probe=PluginMedia.open(server.base()+"/audio",headers,true)){check(probe.http.getContentLengthLong()==1,"original one-byte preflight did not succeed");}
            server.redirect=other.base();try(PluginMedia.Connection full=PluginMedia.openFullAudio(server.base()+"/redirect",headers)){check(full.http.getResponseCode()==206&&!other.leaked,"range redirect leaked origin credentials or lost range support");}check(headers.size()==1,"full transfer mutated source headers");
            Map<String,String> custom=new HashMap<>(headers);custom.put("range","bytes=4-9");check(PluginMedia.progressiveHeaders(custom).get("range").equals("bytes=4-9")&&PluginMedia.progressiveHeaders(custom).size()==2,"native playback overwrote a source range");
            AudioCache cache=new AudioCache(directory,4*1024*1024);try{
                for(String path:new String[]{"audio","ignored","unknown-length"}){check(AudioCacheChecks.store(cache,path,server.base()+"/"+path),"complete range audio cache failed: "+path);File file=cache.find(path,"high");check(Arrays.equals(java.nio.file.Files.readAllBytes(file.toPath()),server.wav),"range cache omitted or duplicated bytes: "+path);}
                for(String path:new String[]{"partial","missing-range","truncated"})check(!AudioCacheChecks.store(cache,path,server.base()+"/"+path),"incomplete 206 response published as a complete cache: "+path);
                check(cache.list().size()==3,"failed range transfers polluted offline index");
            }finally{cache.close();PluginArchive.delete(directory);}
            File converted=AudioDecodeJob.decode(context,Uri.parse(server.base()+"/alac.m4a"),headers,()->{});try(FileInputStream in=new FileInputStream(converted)){byte[] magic=new byte[4];check(in.read(magic)==4&&new String(magic,"US-ASCII").equals("fLaC"),"range-only compatibility decode failed");}finally{converted.delete();}
            SharedPreferences prefs=context.getSharedPreferences("library",0);boolean oldCache=prefs.getBoolean("auto_cache",true);String oldQuality=prefs.getString("play_quality","high");prefs.edit().putBoolean("auto_cache",false).commit();MainActivity activity=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));PlaybackService[] service={null};helper.await(()->{service[0]=(PlaybackService)FullPortChecks.field(activity,"playbackService");return service[0]!=null;},"range test service missing");PluginManager manager=new PluginManager(activity);
            try{
                helper.install(manager,id,"stream",new JSONArray(),"export function stream({quality}){return {url:'"+server.base()+"/audio',type:'wav',quality,headers:{'X-Audio-Key':'fixture'}};}");JSONArray queue=new JSONArray().put(new JSONObject().put("uri","plugin://"+id+"?id=one").put("title","Range-only audio"));
                test.runOnMainSync(()->{try{PlaybackService.startQueue(context,queue,0,0,70,false,false,"high");}catch(Exception error){throw new RuntimeException(error);}});helper.await(()->service[0].snapshot().playing&&service[0].snapshot().position>200,"range-only source did not advance native playback");test.runOnMainSync(()->{service[0].pause();service[0].seek(4200);});helper.await(()->!service[0].snapshot().playing&&Math.abs(service[0].snapshot().position-4200)<300,"range-only native pause and seek failed");test.runOnMainSync(()->service[0].changeQuality("lossless"));helper.await(()->!service[0].snapshot().qualitySwitching&&service[0].snapshot().qualityIssue.isEmpty()&&service[0].snapshot().requestedQuality.equals("lossless"),"range-only quality replacement failed");test.runOnMainSync(service[0]::resume);helper.await(()->service[0].snapshot().playing&&service[0].snapshot().position>4500,"range-only replacement did not resume real audio");check(server.ranges>5,"range fixture missed actual audio transport paths");
            }finally{test.runOnMainSync(()->{service[0].stopPlayback();manager.close();activity.finish();});prefs.edit().putBoolean("auto_cache",oldCache).putString("play_quality",oldQuality).commit();helper.cleanup(id);}
        }
        System.out.println("MYGO_AUDIO_RANGE_OK: range-only native start/pause/seek/resume and quality replacement; complete/ignored/chunked 206 cache bytes; rejected partial/missing/truncated ranges; actual ALAC decode; redirect credential isolation");
    }
}
