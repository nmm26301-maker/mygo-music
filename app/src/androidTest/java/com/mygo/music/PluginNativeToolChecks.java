package com.mygo.music;
import android.app.Instrumentation;
import android.content.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Genuine native CLIs, stdin, JSON extraction and default Android trust roots. */
final class PluginNativeToolChecks {
 final Instrumentation test;final Context context;final CompletionChecks helper;
 PluginNativeToolChecks(Instrumentation t){test=t;context=t.getTargetContext();helper=new CompletionChecks(t);}
 void check(boolean ok,String error){if(!ok)throw new AssertionError(error);}
 void run()throws Exception{
  String id="native-tools-fixture";helper.cleanup(id);SharedPreferences prefs=context.getSharedPreferences("library",0);PluginManager manager=new PluginManager(context);
  try{
   helper.install(manager,id,"stream",new JSONArray(),"export async function version(){const p=host.spawn('yt-dlp',['--version']);p.closeStdin();let lines=[],line;while((line=await p.readLine())!==null)lines.push(line);return {code:await p.wait(),lines,error:p.errorOutput()};}");
   File manifestFile=new File(context.getFilesDir(),"plugins/"+id+"/plugin.json");StringBuilder text=new StringBuilder();try(Reader r=new FileReader(manifestFile)){char[] b=new char[2048];int n;while((n=r.read(b))!=-1)text.append(b,0,n);}JSONObject manifest=new JSONObject(text.toString()).put("permissions",new JSONObject().put("process",new JSONArray().put("yt-dlp").put("mygo-qjs")));
   try(Writer w=new FileWriter(manifestFile)){w.write(manifest.toString());}manager.restart(id);prefs.edit().putBoolean("plugin_process_"+id,true).putBoolean("plugin_network_"+id,false).commit();
   CountDownLatch done=new CountDownLatch(1);boolean[] ok={false};String[] value={""};manager.export(id,"version",new JSONObject(),(success,raw)->{ok[0]=success;value[0]=raw;done.countDown();});check(done.await(45,TimeUnit.SECONDS)&&ok[0],"native WebView spawn failed: "+value[0]);JSONObject version=new JSONObject(value[0]);check(version.getInt("code")==0&&version.getJSONArray("lines").toString().contains("2026.10.26"),"native yt-dlp failed: "+version);
   PluginProcesses processes=new PluginProcesses(context,context.getCacheDir(),id,new JSONArray().put("yt-dlp").put("mygo-qjs"));try{
    long qjs=processes.spawn("mygo-qjs",new JSONArray().put("-e").put("console.log(JSON.stringify({answer:6*7,big:String(2n**80n)}))")).getLong("pid");processes.closeStdin(qjs);JSONObject js=new JSONObject(processes.readLine(qjs));check(processes.waitFor(qjs)==0&&js.getInt("answer")==42&&js.getString("big").equals("1208925819614629174706176"),"QuickJS actual JS/BigInt failed");
    try{processes.spawn("yt-dlp",new JSONArray().put("--dump-json").put("https://example.com/audio"));throw new AssertionError("yt-dlp ignored network denial");}catch(IOException expected){check(expected.getMessage().contains("网络权限"),"yt-dlp network gate incorrect");}
    prefs.edit().putBoolean("plugin_network_"+id,true).commit();NativeAudioChecks audio=new NativeAudioChecks(test);try(NativeAudioChecks.Server server=audio.new Server(false)){
     long pid=processes.spawn("yt-dlp",new JSONArray().put("--config-locations").put("-").put("--no-warnings").put("--no-playlist").put("--skip-download").put("--dump-json").put(server.base()+"/quality-aac.m4a")).getLong("pid");processes.write(pid,"--add-headers \"X-MyGo-Test:native-stdin\"\n");processes.closeStdin(pid);String line=processes.readLine(pid);check(line!=null,"yt-dlp JSON missing: "+processes.errors(pid));JSONObject track=new JSONObject(line);while(processes.readLine(pid)!=null){}check(processes.waitFor(pid)==0&&track.getString("url").startsWith(server.base())&&track.getJSONObject("http_headers").toString().contains("native-stdin"),"yt-dlp JSON/stdin failed: "+processes.errors(pid));
    }
    PluginNativeTools.Plan plan=PluginNativeTools.prepare(context,id,Arrays.asList("--version"));ProcessBuilder builder=new ProcessBuilder(plan.binary.getAbsolutePath(),"-c","import ssl,hashlib,ctypes,json;print(json.dumps({'roots':len(ssl.create_default_context().get_ca_certs())}))");builder.environment().putAll(plan.env);Process python=builder.start();String line;try(BufferedReader r=new BufferedReader(new InputStreamReader(python.getInputStream()))){line=r.readLine();}check(python.waitFor()==0&&line!=null&&new JSONObject(line).getInt("roots")>0,"Python SSL/Android trust failed");
    long waiting=processes.spawn("mygo-qjs",new JSONArray().put("-e").put("while(true){} ")).getLong("pid");processes.kill(waiting);check(processes.waitFor(waiting)!=0,"native process cancellation failed");
    System.out.println("MYGO_NATIVE_TOOLS_OK webViewSpawn=true ytDlp=true quickjsBigInt=true stdin=true actualAudioJSON=true networkGate=true sslAndroidRoots=true cancellation=true");
   }finally{processes.close();}
  }finally{manager.close();helper.cleanup(id);prefs.edit().remove("plugin_process_"+id).remove("plugin_network_"+id).commit();}
 }
}
