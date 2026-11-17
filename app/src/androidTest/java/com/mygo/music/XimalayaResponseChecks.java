package com.mygo.music;

import android.app.*;
import android.content.*;
import org.json.*;
import java.io.*;

/** Exact upstream response handler in the Android runtime; no private login or media request. */
final class XimalayaResponseChecks {
 final Instrumentation test;final Context context;final CompletionChecks helper;
 XimalayaResponseChecks(Instrumentation test){this.test=test;context=test.getTargetContext();helper=new CompletionChecks(test);}
 static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
 void run()throws Exception{
  String original=new LoginRepairChecks(test).asset("provider-fixes/ximalaya-original.js");File root=new File(context.getCacheDir(),"xm-response-adapter");root.mkdirs();File entry=new File(root,"index.js");String adapted;
  try{try(OutputStream out=new FileOutputStream(new File(root,"plugin.json"))){out.write("{\"id\":\"ximalaya\",\"main\":\"index.js\"}".getBytes("UTF-8"));}try(OutputStream out=new FileOutputStream(entry)){out.write(original.getBytes("UTF-8"));}adapted=new BilibiliTransportDiagnostic(test).read(new PluginAdapters(root).open(entry));check(adapted.contains("__androidXimalayaResponseError")&&PluginIdentity.read(entry).equals(original),"Ximalaya exact adapter did not preserve the installed source");try(OutputStream out=new FileOutputStream(entry)){out.write((original+"\n").getBytes("UTF-8"));}check(!new BilibiliTransportDiagnostic(test).read(new PluginAdapters(root).open(entry)).contains("__androidXimalayaResponseError"),"unknown Ximalaya revision was adapted");}finally{PluginArchive.delete(root);}
  String id="xm-response-controlled";Activity activity=test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));PluginManager manager=new PluginManager(activity);
  try{String script="let __reply={ret:0};globalThis.fetch=async()=>new Response(JSON.stringify(__reply),{headers:{'Content-Type':'application/json'}});\n"+adapted+"\nexport async function checkResponse({reply}){__reply=reply;return request('/mobile-playpage/track/v3/baseInfo/1',{device:'web',trackId:'47740352',trackQualityLevel:1});}";helper.install(manager,id,"stream",new JSONArray(),script);BilibiliMirrorChecks calls=new BilibiliMirrorChecks(test);
   BilibiliTransportDiagnostic.Answer result=calls.call(manager,id,"checkResponse",new JSONObject().put("reply",new JSONObject().put("ret",927).put("msg","很抱歉，由于版权方要求，您所在的地区/国家暂时无法使用该资源 https://fixture.invalid/?token=private")));check(!result.ok&&result.value.contains("地区版权限制")&&!result.value.contains("private")&&!result.value.contains("会员"),"observed Ximalaya restriction was misreported or leaked raw data: "+result.value);
   result=calls.call(manager,id,"checkResponse",new JSONObject().put("reply",new JSONObject().put("ret",927).put("msg","unknown")));check(!result.ok&&result.value.contains("927")&&!result.value.contains("地区版权限制"),"unknown 927 was guessed as a regional restriction");
   result=calls.call(manager,id,"checkResponse",new JSONObject().put("reply",new JSONObject().put("ret",403)));check(!result.ok&&result.value.contains("验证")&&!result.value.contains("地区版权限制"),"upstream challenge handling changed");
   result=calls.call(manager,id,"checkResponse",new JSONObject().put("reply",new JSONObject().put("ret",0).put("data",new JSONObject().put("unchanged",true))));check(result.ok&&new JSONObject(result.value).getJSONObject("data").getBoolean("unchanged"),"successful Ximalaya response changed");
   File folder=new File(context.getExternalFilesDir(null),"qa/ximalaya-response");folder.mkdirs();try(OutputStream out=new FileOutputStream(new File(folder,"controlled.json"))){out.write(new JSONObject().put("scope","Exact pinned upstream response handler with controlled public response shapes; no private login or authorized music playback claim").put("fingerprintGuard",true).put("installedSourceUnchanged",true).put("regionalCopyrightExplained",true).put("rawResponseNotExposed",true).put("unknown927Retained",true).put("verificationRetained",true).put("successUnchanged",true).toString(2).getBytes("UTF-8"));}
  }finally{test.runOnMainSync(()->{manager.close();activity.finish();});helper.cleanup(id);}
 }
}
