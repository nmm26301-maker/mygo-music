package com.mygo.music;
import android.app.*;
import android.content.*;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Original public SoundGo source; compare identical HLS resources with only the local root URI suffix changed. */
final class SoundGoDiagnostic {
 final android.app.Instrumentation test;final BilibiliTransportDiagnostic shared;final JSONArray rows=new JSONArray();
 SoundGoDiagnostic(android.app.Instrumentation test){this.test=test;shared=new BilibiliTransportDiagnostic(test);}
 void save()throws Exception{File dir=new File(test.getTargetContext().getExternalFilesDir(null),"qa/soundgo");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,"comparison.json"))){out.write(new JSONObject().put("scope","anonymous original pinned SoundGo source; read-only public playback; same HLS resources per comparison; no login, credentials or source URLs exported").put("rows",rows).toString(2).getBytes("UTF-8"));}}
 JSONObject manifest(String address)throws Exception{JSONObject result=new JSONObject();try(InputStream in=new URL(address).openStream()){String text=shared.read(in);double duration=0;int segments=0;JSONArray codecs=new JSONArray();for(String line:text.split("\\r?\\n")){if(line.startsWith("#EXTINF:")){segments++;try{duration+=Double.parseDouble(line.substring(8).split(",",2)[0]);}catch(Exception ignored){}}if(line.startsWith("#EXT-X-STREAM-INF:"))codecs.put(line);}return result.put("header",text.startsWith("#EXTM3U")).put("endList",text.contains("#EXT-X-ENDLIST")).put("hasInitMap",text.contains("#EXT-X-MAP")).put("hasByteRanges",text.contains("#EXT-X-BYTERANGE")).put("mediaSegments",segments).put("durationSeconds",duration).put("variantTags",codecs);}}
 void run()throws Exception{
  MainActivity activity=null;PluginManager manager=null;
  try{JSONArray pins=new JSONArray(shared.read(test.getContext().getAssets().open("market-audit/index.json")));JSONObject pin=null;for(int i=0;i<pins.length();i++)if(pins.getJSONObject(i).getString("repo").equals("satyawikananda/soundgo"))pin=pins.getJSONObject(i);if(pin==null)throw new IOException("original pinned SoundGo missing");activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));manager=new PluginManager(activity);CountDownLatch installed=new CountDownLatch(1);boolean[] ok={false};manager.installZipStream(test.getContext().getAssets().open("market-audit/"+pin.getString("archive")),pin.getString("repo"),(yes,raw)->{ok[0]=yes;installed.countDown();});if(!installed.await(40,TimeUnit.SECONDS)||!ok[0])throw new IOException("source install failed");String id=pin.getString("declaredId");test.getTargetContext().getSharedPreferences("library",0).edit().putBoolean("plugin_network_"+id,false).commit();CountDownLatch consent=new CountDownLatch(1);manager.authorizeNetwork(id,(yes,raw)->{ok[0]=yes;consent.countDown();});SourcePlaylistUiChecks.click(test,"允许并继续");if(!consent.await(10,TimeUnit.SECONDS)||!ok[0])throw new IOException("source network consent failed");
   LinkedHashSet<String> tracks=new LinkedHashSet<>();tracks.add("2099120265");BilibiliTransportDiagnostic.Answer search=shared.call(manager,id,"search",new JSONObject().put("query","love").put("kind","tracks").put("offset",0));if(search.ok){JSONArray found=new JSONObject(search.value).optJSONArray("tracks");if(found!=null)for(int i=0;i<Math.min(2,found.length())&&tracks.size()<2;i++)tracks.add(found.getJSONObject(i).getString("id"));}
   for(String track:tracks){JSONObject row=new JSONObject().put("trackId",track);rows.put(row);save();BilibiliTransportDiagnostic.Answer answer=shared.call(manager,id,"stream",new JSONObject().put("id",track).put("quality","high"));if(!answer.ok){row.put("sourceError",answer.value);save();continue;}JSONObject stream=new JSONObject(answer.value);String original=stream.getString("url");row.put("reportedType",stream.optString("type")).put("remoteHost",new URL(original).getHost()).put("remoteEndsM3u8",new URL(original).getPath().toLowerCase(Locale.ROOT).endsWith(".m3u8"));Map<String,String> headers=manager.mediaHeaders(id,original,"audio",stream.optJSONObject("headers"));
    try(HlsPlayback proxy=new HlsPlayback(manager,id,original,headers)){String normal=proxy.address.toString();String forced=normal.substring(0,normal.lastIndexOf('/')+1)+"playlist.m3u8";row.put("normalLocalSuffix",Uri.parse(normal).getLastPathSegment()).put("manifest",manifest(normal)).put("nativeNormal",shared.nativeAudio(normal,new HashMap<>(),false)).put("nativePlaylistSuffix",shared.nativeAudio(forced,new HashMap<>(),false)).put("transportFailure",proxy.failure());}catch(Exception e){row.put("transportError",e.getMessage());}save();
   }
  }finally{PluginManager m=manager;MainActivity a=activity;test.runOnMainSync(()->{if(m!=null)m.close();if(a!=null)a.finish();});}
 }
}
