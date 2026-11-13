package com.mygo.music;

import android.app.*;
import android.content.*;
import android.media.MediaPlayer;
import android.os.SystemClock;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/** Exercise actual failed replacement decoders and source callbacks against a running player. */
final class AudioQualitySwitchChecks {
    final Instrumentation test;final Context context;final CompletionChecks helper;
    AudioQualitySwitchChecks(Instrumentation test){this.test=test;context=test.getTargetContext();helper=new CompletionChecks(test);}
    void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    final class Server implements AutoCloseable {
        final ServerSocket socket=new ServerSocket(0,12,InetAddress.getByName("127.0.0.1"));final ExecutorService workers=Executors.newCachedThreadPool();
        final byte[] audio=BackgroundPlaybackChecks.wav(30000);
        Server()throws Exception{Thread thread=new Thread(()->{while(!socket.isClosed())try{Socket client=socket.accept();workers.execute(()->serve(client));}catch(Exception ignored){}});thread.setDaemon(true);thread.start();}
        String base(){return "http://127.0.0.1:"+socket.getLocalPort();}
        void serve(Socket client){try(Socket active=client){BufferedReader reader=new BufferedReader(new InputStreamReader(active.getInputStream(),"US-ASCII"));String first=reader.readLine(),line,range="";if(first==null)return;while((line=reader.readLine())!=null&&!line.isEmpty())if(line.toLowerCase(java.util.Locale.ROOT).startsWith("range:"))range=line.substring(6).trim();String path=first.split(" ")[1];byte[] body=path.contains("bad")?new byte[512]:audio;int status=path.contains("missing")?403:200,start=0,end=body.length-1;if(status==200&&range.matches("bytes=[0-9]+-[0-9]*")){String[] parts=range.substring(6).split("-",-1);start=Integer.parseInt(parts[0]);if(!parts[1].isEmpty())end=Math.min(end,Integer.parseInt(parts[1]));status=206;}if(start>=body.length||end<start)return;OutputStream out=active.getOutputStream();out.write(("HTTP/1.1 "+status+" Test\r\nContent-Type: audio/wav\r\nContent-Length: "+(end-start+1)+"\r\nAccept-Ranges: bytes\r\n"+(status==206?"Content-Range: bytes "+start+"-"+end+"/"+body.length+"\r\n":"")+"Connection: close\r\n\r\n").getBytes("US-ASCII"));if(!first.startsWith("HEAD"))out.write(body,start,end-start+1);out.flush();}catch(Exception ignored){}}
        public void close(){try{socket.close();}catch(Exception ignored){}workers.shutdownNow();}
    }
    void run()throws Exception{
        String id="quality-switch-fixture";helper.cleanup(id);SharedPreferences prefs=context.getSharedPreferences("library",0),data=context.getSharedPreferences("plugin_data_"+id,0);boolean oldCache=prefs.getBoolean("auto_cache",true);String oldQuality=prefs.getString("play_quality","high");prefs.edit().putBoolean("auto_cache",false).commit();
        MainActivity activity=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));PlaybackService[] holder={null};helper.await(()->{holder[0]=(PlaybackService)FullPortChecks.field(activity,"playbackService");return holder[0]!=null;},"quality replacement service missing");PlaybackService service=holder[0];PluginManager manager=new PluginManager(activity);
        try(Server server=new Server()){
            String script="export async function stream({id,quality}){const mode=host.storage.get('mode')||'ok';host.storage.set('request',quality);if(quality!=='high')await new Promise(r=>setTimeout(r,900));if(quality==='lossless'&&mode==='auth'){const e=Error('fixture membership authorization failed');e.code='signIn';throw e;}const path=quality==='lossless'&&mode==='http'?'missing':quality==='lossless'&&mode==='decoder'?'bad':'song';return {url:'"+server.base()+"/'+path,type:'wav',quality,sampleRate:8000,bitDepth:16};}export function played(){host.storage.set('played',(host.storage.get('played')||0)+1);}export function signIn(){return {id:'fixture'};}";
            helper.install(manager,id,"stream",new JSONArray(),script);
            JSONArray queue=new JSONArray().put(new JSONObject().put("uri","plugin://"+id+"?id=one").put("title","Quality original")).put(new JSONObject().put("uri","plugin://"+id+"?id=two").put("title","Quality next"));
            test.runOnMainSync(()->{try{PlaybackService.startQueue(context,queue,0,0,80,false,false,"high");}catch(Exception error){throw new RuntimeException(error);}});
            helper.await(()->service.snapshot().playing&&service.snapshot().position>200,"quality source initial audio never played");helper.await(()->data.getString("played","").equals("1"),"initial played acknowledgement missing");
            MediaPlayer[] original={null};test.runOnMainSync(()->original[0]=service.snapshot().player);
            for(String mode:new String[]{"auth","http","decoder"}){
                data.edit().putString("mode","\""+mode+"\"").commit();long previous=service.snapshot().qualityIssueId;
                test.runOnMainSync(()->{service.resume();check(service.changeQuality("lossless"),"replacement did not start");check(service.snapshot().player==original[0]&&service.snapshot().playing&&service.snapshot().qualitySwitching,"preparing a new quality released the currently audible player");});
                if(mode.equals("auth"))test.runOnMainSync(service::pause);
                helper.await(()->!service.snapshot().qualitySwitching&&service.snapshot().qualityIssueId>previous,"failed "+mode+" quality never completed");
                test.runOnMainSync(()->{PlaybackService.Snapshot state=service.snapshot();check(state.ready&&state.player==original[0]&&state.error.isEmpty(),"failed "+mode+" quality destroyed the working player");check(state.requestedQuality.equals("high")&&prefs.getString("play_quality","").equals("high"),"failed quality became a false active setting");check(state.playing!=mode.equals("auth"),"failed replacement lost the latest play/pause state");if(mode.equals("auth"))check(state.qualityIssue.startsWith("@mygo:signIn:"),"membership error lost its login action");service.pause();});
                SourcePlaylistUiChecks.click(test,"关闭");
            }
            data.edit().putString("mode","\"ok\"").commit();
            test.runOnMainSync(()->{service.changeQuality("lossless");check(!service.changeQuality("high"),"returning to active quality reloaded the same audio");});SystemClock.sleep(1100);
            test.runOnMainSync(()->check(service.snapshot().player==original[0]&&!service.snapshot().qualitySwitching&&service.snapshot().qualityIssue.isEmpty(),"cancelled source callback replaced the current player"));
            test.runOnMainSync(()->{service.changeQuality("lossless");service.seek(4200);service.pause();});
            helper.await(()->{PlaybackService.Snapshot state=service.snapshot();return !state.qualitySwitching&&state.requestedQuality.equals("lossless")&&state.player!=original[0];},"valid replacement was not adopted");
            test.runOnMainSync(()->check(!service.snapshot().playing&&Math.abs(service.snapshot().position-4200)<250,"replacement ignored pause or seek during preparation"));
            test.runOnMainSync(()->{service.changeQuality("standard");service.changeQuality("high");service.changeQuality("standard");});
            helper.await(()->!service.snapshot().qualitySwitching&&service.snapshot().requestedQuality.equals("standard"),"latest rapid quality choice did not win");
            test.runOnMainSync(service::resume);helper.await(()->service.snapshot().playing&&service.snapshot().position>4500,"rapid paused changes did not resume actual audio");SystemClock.sleep(150);
            check(data.getString("played","").equals("1"),"rapid paused quality changes duplicated source played acknowledgements");
            test.runOnMainSync(()->{service.pause();service.changeQuality("lossless");service.next();});
            helper.await(()->service.snapshot().title.equals("Quality next")&&service.snapshot().ready&&!service.snapshot().qualitySwitching,"next track was blocked by an obsolete quality request");
            SystemClock.sleep(1100);test.runOnMainSync(()->check(service.snapshot().title.equals("Quality next"),"old quality callback resurrected the previous track"));
            test.runOnMainSync(()->{service.changeQuality("standard");service.stopPlayback();});SystemClock.sleep(1100);
            test.runOnMainSync(()->check(service.snapshot().player==null&&!service.snapshot().qualitySwitching&&!service.snapshot().playing,"stopped quality request restarted playback"));
            System.out.println("MYGO_QUALITY_SWITCH_OK: unchanged audible player on auth/HTTP/decoder failures; paused/live controls during preparation; cancelled and rapid request ordering; no duplicate played; next/stop discard obsolete results");
        }finally{test.runOnMainSync(()->{service.stopPlayback();manager.close();activity.finish();});prefs.edit().putBoolean("auto_cache",oldCache).putString("play_quality",oldQuality).commit();helper.cleanup(id);}
    }
}
