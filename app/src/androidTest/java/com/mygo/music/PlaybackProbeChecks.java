package com.mygo.music;

import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** A skipped song must release a socket blocked waiting for streaming response headers. */
final class PlaybackProbeChecks {
    private final OfflinePlaybackChecks actions;

    PlaybackProbeChecks(Instrumentation test){actions=new OfflinePlaybackChecks(test);}

    @SuppressWarnings("unchecked")
    void run()throws Exception{
        String source="probe-switch-fixture";
        SharedPreferences prefs=actions.context.getSharedPreferences("library",0);
        boolean autoCache=prefs.getBoolean("auto_cache",true);
        String quality=prefs.getString("play_quality","high");
        prefs.edit().putBoolean("auto_cache",false).commit();
        actions.helper.cleanup(source);
        MainActivity activity=actions.launch();
        PlaybackService service=actions.service(activity);
        PluginManager manager=new PluginManager(activity);
        try(HeldHeaders held=new HeldHeaders();OfflinePlaybackChecks.Server audio=new OfflinePlaybackChecks.Server()){
            actions.helper.install(manager,source,"stream",new JSONArray(),
                "export function stream({id}){return {type:'wav',url:id==='fast'?"+
                JSONObject.quote(audio.url())+":"+JSONObject.quote(held.url())+"+id};}");
            JSONArray queue=new JSONArray();
            for(String id:new String[]{"1","2","fast","3"}){
                queue.put(new JSONObject().put("uri","plugin://"+source+"?id="+id).put("title","Probe "+id));
            }
            actions.ui(()->PlaybackService.startQueue(activity,queue,0,0,65,false,false,"high"));
            check(held.started[0].await(8,TimeUnit.SECONDS),"first song did not open a probe socket");
            actions.ui(service::next);
            check(held.started[1].await(8,TimeUnit.SECONDS),"second song did not open a probe socket");
            actions.ui(service::next);
            check(held.disconnected[0].await(6,TimeUnit.SECONDS)&&held.disconnected[1].await(6,TimeUnit.SECONDS),
                "skipping did not disconnect both actual stalled playback probes");
            actions.helper.await(()->{
                PlaybackService.Snapshot state=service.snapshot();
                return state.index==2&&state.playing&&state.position>150&&state.error.isEmpty();
            },"previous probes blocked actual audio for the newest song");

            actions.ui(service::next);
            check(held.started[2].await(8,TimeUnit.SECONDS),"last probe did not open before Stop");
            actions.ui(service::stopPlayback);
            check(held.disconnected[2].await(6,TimeUnit.SECONDS),"Stop left the pending media probe connected");
            actions.ui(()->{
                PlaybackService.Snapshot state=service.snapshot();
                check(!state.loading&&!state.playing&&state.error.isEmpty(),
                    "cancelling a probe published an obsolete playback failure");
            });
            System.out.println("MYGO_PLAYBACK_PROBE_OK: rapid skips and Stop closed held sockets; newest song played actual audio");
        }finally{
            actions.ui(()->{
                service.clearQueue();
                manager.close();
                List<Uri> tracks=(List<Uri>)FullPortChecks.field(activity,"tracks");
                List<String> names=(List<String>)FullPortChecks.field(activity,"names");
                for(int index=tracks.size()-1;index>=0;index--){
                    if(source.equals(tracks.get(index).getAuthority())){tracks.remove(index);names.remove(index);}
                }
                NowPlayingChecks.invoke(activity,"saveLibrary");
                activity.finish();
            });
            prefs.edit().putBoolean("auto_cache",autoCache).putString("play_quality",quality).commit();
            actions.helper.cleanup(source);
        }
    }

    private static void check(boolean value,String message){OfflinePlaybackChecks.check(value,message);}

    private static final class HeldHeaders implements AutoCloseable {
        private final ServerSocket server=new ServerSocket(0);
        final CountDownLatch[] started={new CountDownLatch(1),new CountDownLatch(1),new CountDownLatch(1)};
        final CountDownLatch[] disconnected={new CountDownLatch(1),new CountDownLatch(1),new CountDownLatch(1)};

        HeldHeaders()throws IOException{
            Thread accepting=new Thread(()->{
                while(!server.isClosed()){
                    try{
                        Socket socket=server.accept();
                        Thread worker=new Thread(()->awaitDisconnect(socket),"mygo-test-probe");
                        worker.setDaemon(true);
                        worker.start();
                    }catch(IOException failure){
                        return;
                    }
                }
            },"mygo-test-probe-server");
            accepting.setDaemon(true);
            accepting.start();
        }

        String url(){return "http://127.0.0.1:"+server.getLocalPort()+"/";}
        private void awaitDisconnect(Socket socket){
            try(Socket client=socket){
                client.setSoTimeout(20000);
                BufferedReader input=new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.US_ASCII));
                String request=input.readLine();
                if(request==null)return;
                int index=Integer.parseInt(request.split(" ")[1].substring(1))-1;
                String line;
                while((line=input.readLine())!=null&&!line.isEmpty()){}
                started[index].countDown();
                try{
                    if(input.read()==-1)disconnected[index].countDown();
                }catch(SocketException closed){
                    disconnected[index].countDown();
                }
            }catch(IOException|RuntimeException ignored){}
        }

        @Override public void close()throws IOException{server.close();}
    }
}
