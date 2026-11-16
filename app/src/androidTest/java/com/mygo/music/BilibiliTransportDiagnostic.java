package com.mygo.music;

import android.app.*;
import android.content.*;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Anonymous, read-only comparison of source-authorized HTTP transports. No credentials are exported. */
final class BilibiliTransportDiagnostic {
    final Instrumentation test;
    BilibiliTransportDiagnostic(Instrumentation test){this.test=test;}
    Context getTargetContext(){return test.getTargetContext();}
    Context getContext(){return test.getContext();}
    void runOnMainSync(Runnable task){test.runOnMainSync(task);}
    Activity startActivitySync(Intent intent){return test.startActivitySync(intent);}
    static final String REPO="GentleM4dman/mygo-music-bilibili";
    final JSONArray rows=new JSONArray();
    String read(InputStream input)throws Exception{try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
    static final class Answer {boolean ok;String value="timeout";}
    Answer call(PluginManager manager,String id,String method,JSONObject args)throws Exception{Answer a=new Answer();CountDownLatch done=new CountDownLatch(1);manager.export(id,method,args,(ok,raw)->{a.ok=ok;a.value=raw;done.countDown();});if(!done.await(40,TimeUnit.SECONDS))throw new IOException("source call deadline: "+method);return a;}
    JSONObject http(String address,Map<String,String> headers,String range)throws Exception{
        JSONObject result=new JSONObject().put("range",range);HashMap<String,String> values=new HashMap<>(headers);if(!range.equals("none"))values.put("Range",range);
        long start=SystemClock.elapsedRealtime();
        try(PluginMedia.Connection connection=PluginMedia.open(address,values,false)){
            result.put("status",connection.http.getResponseCode()).put("resolvedHost",connection.http.getURL().getHost()).put("contentType",connection.http.getContentType()).put("contentLength",connection.http.getContentLengthLong()).put("contentRange",connection.http.getHeaderField("Content-Range"));
            try(InputStream input=connection.http.getInputStream()){byte[] prefix=new byte[32];int n=input.read(prefix);StringBuilder hex=new StringBuilder();for(int i=0;i<Math.max(0,n);i++)hex.append(String.format(Locale.ROOT,"%02x",prefix[i]&255));result.put("prefixHex",hex.toString());}
        }catch(Exception error){result.put("error",error.getMessage());}
        return result.put("millis",SystemClock.elapsedRealtime()-start);
    }
    JSONObject nativeAudio(String address,Map<String,String> headers,boolean ranged)throws Exception{
        JSONObject result=new JSONObject().put("explicitInitialRange",ranged);CountDownLatch done=new CountDownLatch(1);MediaPlayer[] player={null};long start=SystemClock.elapsedRealtime();
        try{runOnMainSync(()->{try{HashMap<String,String> values=ranged?PluginMedia.progressiveHeaders(headers):new HashMap<>(headers);values.put("android-allow-cross-domain-redirect","0");player[0]=new MediaPlayer();MediaPlayer p=player[0];p.setDataSource(getTargetContext(),Uri.parse(address),values);p.setOnPreparedListener(mp->{mp.setVolume(0,0);mp.start();new Handler(Looper.getMainLooper()).postDelayed(()->{try{result.put("positionMs",mp.getCurrentPosition()).put("clockAdvanced",mp.getCurrentPosition()>150);}catch(Exception ignored){}done.countDown();},1500);});p.setOnErrorListener((mp,what,extra)->{try{result.put("what",what).put("extra",extra);}catch(Exception ignored){}done.countDown();return true;});p.prepareAsync();}catch(Exception e){try{result.put("error",e.getMessage());}catch(Exception ignored){}done.countDown();}});result.put("completed",done.await(18,TimeUnit.SECONDS));}
        finally{runOnMainSync(()->{if(player[0]!=null)player[0].release();});}
        return result.put("millis",SystemClock.elapsedRealtime()-start);
    }
    void probe(PluginManager manager,String id,String track,boolean rangeFirst)throws Exception{
        JSONObject row=new JSONObject().put("trackId",track).put("rangeFirst",rangeFirst);rows.put(row);save();
        Answer answer=call(manager,id,"stream",new JSONObject().put("id",track).put("quality","high"));if(!answer.ok){row.put("sourceError",answer.value);save();return;}
        JSONObject stream=new JSONObject(answer.value);String address=stream.getString("url");HashMap<String,String> headers=manager.mediaHeaders(id,address,"audio",stream.optJSONObject("headers"));row.put("host",new URL(address).getHost()).put("reportedType",stream.optString("type")).put("headerNames",new JSONArray(headers.keySet()));
        // Keep the exact signed URL and source headers identical within each paired comparison.
        if(rangeFirst){row.put("httpRange",http(address,headers,"bytes=0-")).put("httpNormal",http(address,headers,"none"));row.put("nativeRange",nativeAudio(address,headers,true)).put("nativeNormal",nativeAudio(address,headers,false));}
        else{row.put("httpNormal",http(address,headers,"none")).put("httpRange",http(address,headers,"bytes=0-"));row.put("nativeNormal",nativeAudio(address,headers,false)).put("nativeRange",nativeAudio(address,headers,true));}
        row.put("httpOneByte",http(address,headers,"bytes=0-0"));
        File directory=new File(getTargetContext().getCacheDir(),"bilibili-range-cache");AudioCache cache=new AudioCache(directory,32*1024*1024);try{CountDownLatch done=new CountDownLatch(1);boolean[] ok={false};String[] detail={""};cache.store(track,track,"high",address,headers,(yes,message)->{ok[0]=yes;detail[0]=message;done.countDown();});boolean completed=done.await(60,TimeUnit.SECONDS);row.put("fullCache",new JSONObject().put("completed",completed).put("ok",ok[0]).put("detail",detail[0]));File saved=cache.find(track,"high");if(saved!=null){row.getJSONObject("fullCache").put("bytes",saved.length());row.put("nativeCached",nativeAudio(saved.toURI().toString(),new HashMap<>(),false));}}finally{cache.close();PluginArchive.delete(directory);}save();
    }
    void save()throws Exception{File dir=new File(getTargetContext().getExternalFilesDir(null),"qa/bilibili-transport");dir.mkdirs();JSONObject report=new JSONObject().put("scope","anonymous read-only source and media requests; same signed URL and headers per pair; no account login, remote mutation or credential export").put("rows",rows);try(FileOutputStream out=new FileOutputStream(new File(dir,"comparison.json"))){out.write(report.toString(2).getBytes("UTF-8"));}}
    void run()throws Exception{MainActivity activity=null;PluginManager manager=null;
        try{
            JSONArray pins=new JSONArray(read(getContext().getAssets().open("market-audit/index.json")));JSONObject pin=null;for(int i=0;i<pins.length();i++)if(REPO.equals(pins.getJSONObject(i).getString("repo")))pin=pins.getJSONObject(i);if(pin==null)throw new IOException("pinned Bilibili source missing");
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));manager=new PluginManager(activity);CountDownLatch installed=new CountDownLatch(1);boolean[] ok={false};manager.installZipStream(getContext().getAssets().open("market-audit/"+pin.getString("archive")),REPO,(yes,raw)->{ok[0]=yes;installed.countDown();});if(!installed.await(35,TimeUnit.SECONDS)||!ok[0])throw new IOException("source install failed");String id=null;for(PluginManager.PluginInfo p:manager.list())if(REPO.equals(p.repository))id=p.id;if(id==null)throw new IOException("installed source unavailable");
            getTargetContext().getSharedPreferences("library",0).edit().putBoolean("plugin_network_"+id,false).putBoolean("plugin_process_"+id,false).commit();CountDownLatch allowed=new CountDownLatch(1);manager.authorizeNetwork(id,(yes,raw)->{ok[0]=yes;allowed.countDown();});SourcePlaylistUiChecks.click(test,"允许并继续");if(!allowed.await(10,TimeUnit.SECONDS)||!ok[0])throw new IOException("source network not authorized");
            LinkedHashSet<String> tracks=new LinkedHashSet<>();tracks.add("BV137KPezEe9");tracks.add("BV1o1AZzmEV6");
            int n=0;for(String track:tracks)probe(manager,id,track,n++%2!=0);
        }
        finally{PluginManager last=manager;MainActivity page=activity;runOnMainSync(()->{if(last!=null)last.close();if(page!=null)page.finish();});}
    }
}
