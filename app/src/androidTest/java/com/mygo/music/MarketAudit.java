package com.mygo.music;

import android.app.Instrumentation;
import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Read-only, anonymous checks of every pinned market package, using the real installer/WebView. */
final class MarketAudit {
    private final Instrumentation test;private final Context context;private final JSONArray results=new JSONArray();
    MarketAudit(Instrumentation test){this.test=test;context=test.getTargetContext();}
    private static String read(InputStream input)throws Exception{try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);return out.toString("UTF-8");}}
    void run()throws Exception{
        JSONArray pins=new JSONArray(read(test.getContext().getAssets().open("market-audit/index.json")));if(pins.length()!=64)throw new AssertionError("market coverage changed without updating the expected count");
        PluginManager installer=new PluginManager(context);ArrayList<JSONObject> installed=new ArrayList<>();
        try{
            for(int i=0;i<pins.length();i++){
                JSONObject pin=pins.getJSONObject(i);JSONObject row=new JSONObject().put("repo",pin.getString("repo")).put("commit",pin.getString("commit")).put("name",pin.getString("name")).put("licenseFiles",pin.getJSONArray("licenseFiles"));results.put(row);
                if(pin.has("downloadError")){row.put("install","download_unverified").put("detail",pin.getString("downloadError"));save();continue;}
                CountDownLatch done=new CountDownLatch(1);boolean[] ok={false};String[] detail={"安装超时"};
                installer.installZipStream(test.getContext().getAssets().open("market-audit/"+pin.getString("archive")),pin.getString("repo"),(success,value)->{ok[0]=success;detail[0]=value;done.countDown();});
                if(!done.await(40,TimeUnit.SECONDS)||!ok[0])row.put("install","failed").put("detail",detail[0]);
                else{
                    PluginManager.PluginInfo source=null;for(PluginManager.PluginInfo item:installer.list())if(item.repository.equals(pin.getString("repo"))){source=item;break;}
                    if(source==null)throw new AssertionError("installed repository lost its identity: "+pin.getString("repo"));
                    row.put("install","passed").put("version",source.version).put("instanceId",source.id).put("exports",new JSONArray(source.functions)).put("programs",source.programs).put("playback",source.playback);
                    context.getSharedPreferences("library",0).edit().putBoolean("plugin_network_"+source.id,true).putBoolean("plugin_process_"+source.id,false).commit();installed.add(row);
                }
                android.util.Log.i("MyGoMarket",(i+1)+"/"+pins.length()+" "+pin.getString("repo")+" install="+row.getString("install"));save();
            }
        }finally{installer.close();}
        ExecutorService workers=Executors.newFixedThreadPool(3);ArrayList<Future<?>> checks=new ArrayList<>();
        for(JSONObject row:installed)checks.add(workers.submit(()->{try{probe(row);}catch(Exception failure){try{row.put("auditError",failure.toString());}catch(JSONException ignored){}}finally{try{save();}catch(Exception ignored){}}}));
        workers.shutdown();for(Future<?> check:checks)check.get(8,TimeUnit.MINUTES);save();
    }
    private static final class Answer {boolean ok;String value="timeout";}
    private Answer call(PluginManager manager,String id,String method,JSONObject args)throws Exception{
        Answer answer=new Answer();CountDownLatch done=new CountDownLatch(1);manager.export(id,method,args,(ok,raw)->{answer.ok=ok;answer.value=raw;done.countDown();});
        if(!done.await(35,TimeUnit.SECONDS))answer.value="read-only audit deadline exceeded";return answer;
    }
    private static final class DecoderProbe {boolean ready,noAudio;int error;}
    private DecoderProbe prepare(Uri address,Map<String,String> headers)throws Exception{
        DecoderProbe result=new DecoderProbe();CountDownLatch done=new CountDownLatch(1);MediaPlayer[] player={null};
        try{test.runOnMainSync(()->{try{player[0]=new MediaPlayer();player[0].setDataSource(context,address,headers);player[0].setOnPreparedListener(mp->{result.ready=AudioDecodeJob.hasAudio(mp);result.noAudio=!result.ready;done.countDown();});player[0].setOnErrorListener((mp,what,extra)->{result.error=extra;done.countDown();return true;});player[0].prepareAsync();}catch(Exception failure){done.countDown();}});done.await(15,TimeUnit.SECONDS);return result;}
        finally{test.runOnMainSync(()->{if(player[0]!=null)player[0].release();});}
    }
    private void probe(JSONObject row)throws Exception{
        PluginManager manager=new PluginManager(context);String id=row.getString("instanceId");
        try{
            Set<String> functions=new HashSet<>();JSONArray exports=row.getJSONArray("exports");for(int i=0;i<exports.length();i++)functions.add(exports.getString(i));
            if(!functions.contains("search")){row.put("search","not_exported");return;}
            Answer search=call(manager,id,"search",new JSONObject().put("query","love").put("kind","tracks").put("offset",0));
            if(!search.ok){row.put("search","failed_or_requires_setup").put("searchDetail",trim(search.value));return;}
            JSONObject result=new JSONObject(search.value);JSONArray tracks=result.optJSONArray("tracks");row.put("search","passed").put("tracks",tracks==null?0:tracks.length());
            if("remote".equals(row.getString("playback"))){row.put("stream","requires_signed_in_remote_device");return;}
            if(!functions.contains("stream")){row.put("stream","not_exported");return;}
            if(tracks==null||tracks.length()==0){row.put("stream","unverified_no_search_track");return;}
            Answer stream=call(manager,id,"stream",new JSONObject().put("id",tracks.getJSONObject(0).getString("id")).put("quality","high"));
            if(!stream.ok){row.put("stream","failed_or_requires_setup").put("streamDetail",trim(stream.value));return;}
            JSONObject value=new JSONObject(stream.value);String address=value.optString("url");if(!address.startsWith("http://")&&!address.startsWith("https://")){row.put("stream","unverified_no_http_url");return;}
            HashMap<String,String> headers=manager.mediaHeaders(id,address,"audio",value.optJSONObject("headers"));
            try(PluginMedia.Connection connection=PluginMedia.open(address,headers,true)){
                row.put("stream","http_passed").put("httpStatus",connection.http.getResponseCode());
                String resolved=connection.http.getURL().toString();HashMap<String,String> playerHeaders=new HashMap<>(connection.headers);playerHeaders.put("android-allow-cross-domain-redirect","0");
                HlsPlayback transport=null;String kind=value.optString("type",value.optString("codec"));Uri playable=Uri.parse(resolved);Map<String,String> nativeHeaders=playerHeaders;
                if(HlsPlayback.matches(playable,kind)){transport=new HlsPlayback(manager,id,resolved,new HashMap<>(connection.headers));playable=transport.address;nativeHeaders=new HashMap<>();row.put("androidTransport","app per-resource HLS");}else row.put("androidTransport","native direct");
                try{DecoderProbe decoder=prepare(playable,nativeHeaders);
                    if(!decoder.ready&&transport!=null&&transport.failure().isEmpty()&&(decoder.error==0||AudioDecodeJob.candidate(Uri.parse(resolved),kind,decoder.error))){
                        transport.close();transport=null;File converted=null;long deadline=android.os.SystemClock.elapsedRealtime()+45000;
                        try{converted=AudioDecodeJob.decodeHls(context,manager,id,Uri.parse(resolved),new HashMap<>(connection.headers),()->{if(Thread.currentThread().isInterrupted()||android.os.SystemClock.elapsedRealtime()>deadline)throw new IOException("完整分段音频解码检查超时");});decoder=prepare(Uri.fromFile(converted),new HashMap<>());row.put("androidDecoderFallback","complete FLAC");}
                        catch(Exception failure){row.put("decoderDetail",trim(failure.getMessage()));}finally{if(converted!=null)converted.delete();}
                    }
                    row.put("androidDecoder",decoder.ready?"prepared":"unverified_or_failed");if(!decoder.ready){row.put("decoderError",decoder.error);if(decoder.noAudio)row.put("decoderDetail","原生准备完成但未识别到可播放音轨");if(transport!=null&&!transport.failure().isEmpty())row.put("decoderDetail",trim(transport.failure()));}
                }finally{if(transport!=null)transport.close();}
            }catch(Exception failure){row.put("stream","http_failed").put("streamDetail",trim(failure.toString()));}
        }finally{manager.close();android.util.Log.i("MyGoMarket",row.getString("repo")+" search="+row.optString("search")+" stream="+row.optString("stream")+" decoder="+row.optString("androidDecoder"));}
    }
    private String trim(String text){return text==null?"":text.substring(0,Math.min(800,text.length()));}
    private synchronized void save()throws Exception{
        File dir=new File(context.getExternalFilesDir(null),"qa");dir.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(dir,"market-audit.json"))){out.write(new JSONObject().put("mode","anonymous read-only; no account login, no mutations, no desktop helper execution").put("results",results).toString(2).getBytes("UTF-8"));}
    }
}
