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
import android.os.StrictMode;
import org.json.*;
import java.io.*;
import java.net.*;
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
        String bridge="var __mygoFetch=function(input,init){init=init||{};var u=typeof input==='string'?input:input.url;var method=init.method||'GET';var body=init.body||'';var headers=init.headers||{};if(window.Headers&&headers instanceof Headers){var h={};headers.forEach(function(v,k){h[k]=v;});headers=h;}var raw=nativeHost.httpRequest(u,method,String(body),JSON.stringify(headers));var x=JSON.parse(raw);if(x.error){return Promise.reject(new Error(x.error));}var hs=new Headers(x.headers||{});hs.getSetCookie=function(){return x.cookies||[];};return Promise.resolve(new Response(x.body||'',{status:x.status||200,headers:hs}));};window.fetch=__mygoFetch;\\n";
        String host="var host={platform:\"android\",arch:\"arm64\",dir:"+JSONObject.quote(directory.getAbsolutePath())+",dataDir:"+JSONObject.quote(new File(directory,"data").getAbsolutePath())+",storage:{get:function(k){var v=nativeHost.storageGet(k);return v?JSON.parse(v):undefined;},set:function(k,v){nativeHost.storageSet(k,JSON.stringify(v));},remove:function(k){nativeHost.storageRemove(k);}},crypto:{md5:function(x){return nativeHost.md5(String(x));},aesEcbEncrypt:function(k,x){return nativeHost.aesEcbEncrypt(String(k),String(x));}},setting:function(k){return nativeHost.setting(k);},language:function(){return nativeHost.language();},t:function(x){return nativeHost.translate(x);},openURL:function(u){return nativeHost.openURL(u);}};\\n";
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
        @JavascriptInterface public String httpRequest(String address,String method,String body,String headersJson){StrictMode.ThreadPolicy old=StrictMode.getThreadPolicy();StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder(old).permitNetwork().build());HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(address).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(false);String verb=method==null||method.isEmpty()?"GET":method.toUpperCase(Locale.US);c.setRequestMethod(verb);c.setRequestProperty("Accept-Encoding","identity");if(headersJson!=null&&!headersJson.isEmpty()){JSONObject h=new JSONObject(headersJson);java.util.Iterator<String> it=h.keys();while(it.hasNext()){String k=it.next();c.setRequestProperty(k,h.optString(k,""));}}if(body!=null&&!body.isEmpty()&&!"GET".equals(verb)&&!"HEAD".equals(verb)){c.setDoOutput(true);OutputStream out=c.getOutputStream();out.write(body.getBytes("UTF-8"));out.close();}int status=c.getResponseCode();InputStream in=status>=200&&status<400?c.getInputStream():c.getErrorStream();String text="";if(in!=null){ByteArrayOutputStream buf=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))>0)buf.write(b,0,n);in.close();text=new String(buf.toByteArray(),"UTF-8");}JSONObject result=new JSONObject().put("status",status).put("body",text);JSONObject hs=new JSONObject();java.util.Map<String,java.util.List<String>> fields=c.getHeaderFields();if(fields!=null)for(java.util.Map.Entry<String,java.util.List<String>> e:fields.entrySet())if(e.getKey()!=null&&e.getValue()!=null&&!e.getValue().isEmpty())hs.put(e.getKey(),e.getValue().get(0));result.put("headers",hs);JSONArray cookies=new JSONArray();java.util.List<String> set=fields==null?null:fields.get("Set-Cookie");if(set!=null)for(String line:set)cookies.put(line);result.put("cookies",cookies);return result.toString();}catch(Exception e){try{return new JSONObject().put("error",e.getMessage()==null?"网络请求失败":e.getMessage()).toString();}catch(Exception ignored){return "{\"error\":\"network error\"}";}}finally{if(c!=null)c.disconnect();StrictMode.setThreadPolicy(old);}}
    }
    private String read(File f)throws Exception{InputStream in=new FileInputStream(f);try{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[8192];int n;while((n=in.read(x))>0)b.write(x,0,n);return new String(b.toByteArray(),"UTF-8");}finally{in.close();}}
}
