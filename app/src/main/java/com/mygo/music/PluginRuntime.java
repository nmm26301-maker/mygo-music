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

    public void call(String function,JSONObject args,Callback callback){main.post(()->{ensureLoaded(ok->{if(!ok){callback.done(false,"插件脚本加载失败");return;}String source="window.__call("+JSONObject.quote(function)+","+args.toString()+")";web.evaluateJavascript(source,value->{try{Object parsed=new JSONTokener(value==null?"null":value).nextValue();callback.done(true,parsed==null?"null":String.valueOf(parsed));}catch(Exception e){callback.done(false,"插件返回值解析失败");}});});});}

    private void ensureLoaded(Callback cb){if(loaded){cb.done(true,"");return;}try{String manifest=read(new File(directory,"plugin.json"));JSONObject m=new JSONObject(manifest);String mainFile=m.optString("main","index.js");String source=read(new File(directory,mainFile));String script=transform(source);web=new WebView(context);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String url){if(loaded)return;loaded=true;cb.done(true,"");}});web.addJavascriptInterface(new NativeHost(),"nativeHost");String html="<html><body><script>"+script+"</script></body></html>";web.loadDataWithBaseURL("file://"+directory.getAbsolutePath()+"/",html,"text/html","UTF-8",null);}catch(Exception e){cb.done(false,e.getMessage()==null?"load error":e.getMessage());}}

    private String transform(String source){StringBuilder exports=new StringBuilder();Matcher f=Pattern.compile("export\\s+(?:async\\s+)?function\\s+([A-Za-z_$][\\w$]*)").matcher(source);while(f.find())exports.append("window.__exports[").append(JSONObject.quote(f.group(1))).append("]=").append(f.group(1)).append(";\\n");String body=source.replaceAll("export\\s+(async\\s+)?function","$1function");Matcher a=Pattern.compile("export\\s*\\{([^}]*)\\}\\s*;?").matcher(body);StringBuffer b=new StringBuffer();while(a.find()){String[] parts=a.group(1).split(",");StringBuilder repl=new StringBuilder();for(String p:parts){String[] x=p.trim().split("\\s+as\\s+");if(x.length==2)repl.append("window.__exports[").append(JSONObject.quote(x[1].trim())).append("]=").append(x[0].trim()).append(";\\n");else if(x.length==1&&!x[0].trim().isEmpty())repl.append("window.__exports[").append(JSONObject.quote(x[0].trim())).append("]=").append(x[0].trim()).append(";\\n");}a.appendReplacement(b,Matcher.quoteReplacement(repl.toString()));}a.appendTail(b);body=b.toString();String host="var host={storage:{get:function(k){var v=nativeHost.storageGet(k);return v?JSON.parse(v):undefined;},set:function(k,v){nativeHost.storageSet(k,JSON.stringify(v));},remove:function(k){nativeHost.storageRemove(k);}},setting:function(k){return nativeHost.setting(k);},language:function(){return nativeHost.language();},t:function(x){return nativeHost.translate(x);},openURL:function(u){return nativeHost.openURL(u);}};\\n";return host+"window.__exports={};\\n"+body+"\\n"+exports+"\\nwindow.__call=async function(n,a){var f=window.__exports[n];if(!f)throw new Error('插件未导出 '+n);var r=await f(a||{});return JSON.stringify(r===undefined?null:r);};\\n";}

    private final class NativeHost {
        @JavascriptInterface public String storageGet(String key){return storage.get(key);}
        @JavascriptInterface public void storageSet(String key,String value){storage.put(key,value);}
        @JavascriptInterface public void storageRemove(String key){storage.remove(key);}
        @JavascriptInterface public String setting(String key){return "";}
        @JavascriptInterface public String language(){return "zh-CN";}
        @JavascriptInterface public String translate(String text){return text;}
        @JavascriptInterface public void openURL(String url){try{context.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(url)));}catch(Exception ignored){}}
    }
    private String read(File f)throws Exception{InputStream in=new FileInputStream(f);try{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[8192];int n;while((n=in.read(x))>0)b.write(x,0,n);return new String(b.toByteArray(),"UTF-8");}finally{in.close();}}
}
