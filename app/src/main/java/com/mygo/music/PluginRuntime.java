package com.mygo.music;

import android.app.Activity;
import android.content.Context;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.os.Handler;
import android.os.Looper;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/** Small WebView based bridge for MyGo plugin exports. */
public final class PluginRuntime {
    public interface Callback { void done(boolean ok,String value); }
    private final Context context;
    private final File directory;
    private final Handler main=new Handler(Looper.getMainLooper());
    private WebView web;
    private boolean loaded;
    private final HashMap<String,String> storage=new HashMap<>();

    public PluginRuntime(Context c,File dir){context=c;directory=dir;}

    public void call(String function,JSONObject args,Callback callback){main.post(()->{ensureLoaded((ok,message)->{if(!ok){callback.done(false,message==null||message.isEmpty()?"插件脚本加载失败":message);return;}String source="window.__call("+JSONObject.quote(function)+","+args.toString()+").then(function(x){return JSON.stringify({ok:true,value:x});}).catch(function(e){return JSON.stringify({ok:false,error:String(e&&e.message||e)});})";web.evaluateJavascript(source,value->{try{Object decoded=new JSONTokener(value==null?"null":value).nextValue();if(!(decoded instanceof String)){callback.done(false,"插件返回值解析失败");return;}JSONObject envelope=new JSONObject((String)decoded);if(!envelope.optBoolean("ok",false)){callback.done(false,envelope.optString("error","插件接口调用失败"));return;}callback.done(true,envelope.optString("value","null"));}catch(Exception e){callback.done(false,"插件返回值解析失败："+e.getMessage());}});});});}

    private void ensureLoaded(Callback cb){if(loaded){cb.done(true,"");return;}try{String manifest=read(new File(directory,"plugin.json"));JSONObject m=new JSONObject(manifest);String mainFile=m.optString("main","index.js");String source=read(new File(directory,mainFile));String script=transform(source);web=new WebView(context);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccessFromFileURLs(true);s.setAllowUniversalAccessFromFileURLs(true);s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String url){if(loaded)return;loaded=true;cb.done(true,"");}});web.addJavascriptInterface(new NativeHost(),"nativeHost");String html="<html><body><script>"+script+"</script></body></html>";web.loadDataWithBaseURL("file://"+directory.getAbsolutePath()+"/",html,"text/html","UTF-8",null);}catch(Exception e){cb.done(false,e.getMessage()==null?"load error":e.getMessage());}}

    private String transform(String source){
        StringBuilder exports=new StringBuilder();
        Matcher f=Pattern.compile("export\\s+(?:async\\s+)?function\\s+([A-Za-z_$][\\w$]*)").matcher(source);
        while(f.find())exports.append("window.__exports[").append(JSONObject.quote(f.group(1))).append("]=").append(f.group(1)).append(";\\n");
        String body=source.replaceAll("export\\s+(async\\s+)?function","$1function");
        Matcher a=Pattern.compile("export\\s*\\{([^}]*)\\}\\s*;?").matcher(body);StringBuffer b=new StringBuffer();
        while(a.find()){String[] parts=a.group(1).split(",");StringBuilder repl=new StringBuilder();for(String p:parts){String[] x=p.trim().split("\\s+as\\s+");if(x.length==2)repl.append("window.__exports[").append(JSONObject.quote(x[1].trim())).append("]=").append(x[0].trim()).append(";\\n");else if(x.length==1&&!x[0].trim().isEmpty())repl.append("window.__exports[").append(JSONObject.quote(x[0].trim())).append("]=").append(x[0].trim()).append(";\\n");}a.appendReplacement(b,Matcher.quoteReplacement(repl.toString()));}a.appendTail(b);body=b.toString();
        String bridge="if(window.Headers&&!Headers.prototype.getSetCookie){Headers.prototype.getSetCookie=function(){return [];};}\\nif(window.fetch){var __mygoFetch=window.fetch.bind(window);window.fetch=function(input,init){return __mygoFetch(input,init).then(function(r){if(r.headers&&!r.headers.getSetCookie)r.headers.getSetCookie=function(){return [];};return r;});};}\\n";
        String host="var host={platform:\"android\",arch:\"arm64\",dir:"+JSONObject.quote(directory.getAbsolutePath())+",dataDir:"+JSONObject.quote(new File(directory,\"data\").getAbsolutePath())+",storage:{get:function(k){var v=nativeHost.storageGet(k);return v?JSON.parse(v):undefined;},set:function(k,v){nativeHost.storageSet(k,JSON.stringify(v));},remove:function(k){nativeHost.storageRemove(k);}},crypto:{md5:function(x){return nativeHost.md5(String(x));},aesEcbEncrypt:function(k,x){return nativeHost.aesEcbEncrypt(String(k),String(x));}},setting:function(k){return nativeHost.setting(k);},language:function(){return nativeHost.language();},t:function(x){return nativeHost.translate(x);},openURL:function(u){return nativeHost.openURL(u);}};\\n";
        return bridge+host+"window.__exports={};\\n"+body+"\\n"+exports+"\\nwindow.__call=async function(n,a){var f=window.__exports[n];if(!f)throw new Error('插件未导出 '+n);var r=await f(a||{});return JSON.stringify(r===undefined?null:r);};\\n";
    }
    private final class NativeHost {
        @JavascriptInterface public String storageGet(String key){return storage.get(key);}
        @JavascriptInterface public void storageSet(String key,String value){storage.put(key,value);}
        @JavascriptInterface public void storageRemove(String key){storage.remove(key);}
        @JavascriptInterface public String setting(String key){return "";}
        @JavascriptInterface public String language(){return "zh-CN";}
        @JavascriptInterface public String translate(String text){return text;}
        @JavascriptInterface public void openURL(String url){try{context.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(url)));}catch(Exception ignored){}}
        @JavascriptInterface public String md5(String text){try{byte[] d=MessageDigest.getInstance("MD5").digest((text==null?"":text).getBytes("UTF-8"));StringBuilder b=new StringBuilder();for(byte x:d)b.append(String.format("%02x",x&255));return b.toString();}catch(Exception e){return "";}}
        @JavascriptInterface public String aesEcbEncrypt(String key,String text){try{Cipher c=Cipher.getInstance("AES/ECB/PKCS5Padding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec((key==null?"":key).getBytes("UTF-8"),"AES"));byte[] out=c.doFinal((text==null?"":text).getBytes("UTF-8"));StringBuilder b=new StringBuilder();for(byte x:out)b.append(String.format("%02X",x&255));return b.toString();}catch(Exception e){return "";}}
    }
    private String read(File f)throws Exception{InputStream in=new FileInputStream(f);try{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[8192];int n;while((n=in.read(x))>0)b.write(x,0,n);return new String(b.toByteArray(),"UTF-8");}finally{in.close();}}
}
