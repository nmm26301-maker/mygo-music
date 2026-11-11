package com.mygo.music;

import android.app.AlertDialog;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;

/** ES modules, asynchronous native I/O, per-plugin storage and network permissions. */
public final class PluginRuntime {
    public interface Callback { void done(boolean ok,String value); }
    private final Context context;
    private final File directory;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService network=Executors.newFixedThreadPool(3);
    private final HashMap<String,Callback> pending=new HashMap<>();
    private final ArrayList<Callback> loading=new ArrayList<>();
    private SharedPreferences storage, permissions;
    private JSONArray hosts=new JSONArray();
    private WebView web;
    private boolean loaded, starting, closed;
    private String id, loadError="";
    private int sequence;
    private AlertDialog loginDialog;
    private TextView loginText;
    private ImageView loginQr;
    private String lastQr="";
    private static PluginRuntime browserOwner;
    private AlertDialog browserDialog;
    private WebView browserView;
    private String browserRequest="", cookiesOf="";
    private JSONArray requiredCookies;
    private Runnable browserPoll;


    public PluginRuntime(Context c,File dir){context=c;directory=dir;}
    public void validate(Callback cb){main.post(()->ensureLoaded(cb));}
    public void call(String function,JSONObject args,Callback callback){main.post(()->ensureLoaded((ok,message)->{
        if(!ok){callback.done(false,message);return;}
        String request=String.valueOf(++sequence);pending.put(request,callback);
        main.postDelayed(()->{Callback timeout=pending.remove(request);if(timeout!=null)timeout.done(false,"插件接口超时："+function);},"signIn".equals(function)?200000:45000);
        web.evaluateJavascript("window.__invoke("+JSONObject.quote(request)+","+JSONObject.quote(function)+","+(args==null?"{}":args.toString())+");",null);
    }));}
    public void dispose(){main.post(()->{closed=true;if(!browserRequest.isEmpty())finishBrowserLogin(false,"插件已关闭");for(Callback cb:new ArrayList<>(pending.values()))cb.done(false,"插件已关闭");pending.clear();if(web!=null){web.removeJavascriptInterface("nativeHost");web.destroy();web=null;}if(loginDialog!=null)loginDialog.dismiss();network.shutdownNow();});}
    private void ensureLoaded(Callback cb){
        if(closed){cb.done(false,"插件已关闭");return;}if(loaded){cb.done(true,"");return;}if(!loadError.isEmpty()){cb.done(false,loadError);return;}
        loading.add(cb);if(starting)return;starting=true;
        try{
            JSONObject manifest=new JSONObject(read(new FileInputStream(new File(directory,"plugin.json"))));
            id=manifest.getString("id");JSONObject p=manifest.optJSONObject("permissions");if(p!=null&&p.optJSONArray("network")!=null)hosts=p.getJSONArray("network");
            storage=context.getSharedPreferences("plugin_data_"+id,Context.MODE_PRIVATE);permissions=context.getSharedPreferences("library",Context.MODE_PRIVATE);
            File entry=new File(directory,manifest.optString("main","index.js"));
            if(!entry.getCanonicalPath().startsWith(directory.getCanonicalPath()+File.separator))throw new IOException("无效插件入口");
            if(entry.getName().endsWith(".ts"))throw new IOException("此插件入口为 TypeScript，需要已编译的 JavaScript 版本");
            String module="data:text/javascript;base64,"+android.util.Base64.encodeToString(read(new FileInputStream(entry)).getBytes("UTF-8"),android.util.Base64.NO_WRAP);
            JSONObject locales=new JSONObject();File localeFile=new File(directory,"locales/zh-CN.json");if(localeFile.isFile())locales=new JSONObject(read(new FileInputStream(localeFile)));
            JSONObject config=new JSONObject().put("translations",locales).put("module",module).put("arch",Build.SUPPORTED_ABIS[0]).put("dir",directory.getAbsolutePath());
            String bridge=read(context.getAssets().open("plugin-host.js"));
            web=new WebView(context);WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
            web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}});
            web.addJavascriptInterface(new NativeHost(),"nativeHost");
            String html="<!doctype html><meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; script-src 'unsafe-inline' data:; connect-src 'none'\"><script>window.__pluginConfig="+config+";</script><script>"+bridge.replace("</script","<\\/script")+"</script>";
            web.loadDataWithBaseURL("https://plugin.invalid/",html,"text/html","UTF-8",null);
            main.postDelayed(()->{if(!loaded&&loadError.isEmpty())finishLoad(false,"插件加载超时，请更新 Android System WebView");},15000);
        }catch(Exception e){finishLoad(false,e.getMessage());}
    }
    private void finishLoad(boolean ok,String message){if(closed)return;loaded=ok;loadError=ok?"":message;ArrayList<Callback> waiters=new ArrayList<>(loading);loading.clear();for(Callback cb:waiters)cb.done(ok,message);}
    static boolean hostMatches(String host,String rule){host=host.toLowerCase(Locale.US);rule=rule.toLowerCase(Locale.US);return "*".equals(rule)||host.equals(rule)||(rule.startsWith("*.")&&host.endsWith(rule.substring(1))&&host.length()>rule.length()-1);}
    private void requireNetwork(URL url)throws Exception{
        if(!"https".equals(url.getProtocol())&&!"http".equals(url.getProtocol()))throw new IOException("仅支持 HTTP/HTTPS");
        if(!permissions.getBoolean("plugin_network_"+id,false))throw new IOException("请在插件网络权限中允许访问");
        for(int i=0;i<hosts.length();i++)if(hostMatches(url.getHost(),hosts.optString(i)))return;
        throw new IOException("插件未声明该网络域名："+url.getHost());
    }
    private JSONObject requestHttp(String address,JSONObject init)throws Exception{
        String method=init.optString("method","GET").toUpperCase(Locale.US),body=init.optString("body","");JSONObject headers=init.optJSONObject("headers");URL url=new URL(address);
        for(int redirects=0;redirects<=5;redirects++){
            requireNetwork(url);HttpURLConnection c=(HttpURLConnection)url.openConnection();
            try{
                c.setConnectTimeout(12000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(false);c.setRequestMethod(method);c.setRequestProperty("Accept-Encoding","identity");
                if(headers!=null){Iterator<String> it=headers.keys();while(it.hasNext()){String key=it.next();c.setRequestProperty(key,headers.optString(key));}}
                if(!body.isEmpty()&&!"GET".equals(method)&&!"HEAD".equals(method)){c.setDoOutput(true);try(OutputStream out=c.getOutputStream()){out.write(body.getBytes("UTF-8"));}}
                int status=c.getResponseCode();
                if(status>=300&&status<400&&c.getHeaderField("Location")!=null){
                    if("error".equals(init.optString("redirect")))throw new IOException("插件禁止重定向");
                    if(!"manual".equals(init.optString("redirect"))){URL next=new URL(url,c.getHeaderField("Location"));if(!next.getHost().equalsIgnoreCase(url.getHost()))headers=new JSONObject();url=next;if(status==303||((status==301||status==302)&&"POST".equals(method))){method="GET";body="";}continue;}
                }
                JSONObject responseHeaders=new JSONObject();JSONArray cookies=new JSONArray();for(Map.Entry<String,List<String>> entry:c.getHeaderFields().entrySet())if(entry.getKey()!=null){if("set-cookie".equalsIgnoreCase(entry.getKey()))for(String value:entry.getValue())cookies.put(value);else responseHeaders.put(entry.getKey(),String.join(", ",entry.getValue()));}
                byte[] bytes=new byte[0];if(!"HEAD".equals(method)){InputStream in=status>=400?c.getErrorStream():c.getInputStream();if(in!=null)try(InputStream input=in){bytes=readBytes(input);}}
                return new JSONObject().put("status",status).put("url",url.toString()).put("headers",responseHeaders).put("cookies",cookies).put("body",new String(bytes,"UTF-8")).put("base64",android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP));
            }finally{c.disconnect();}
        }throw new IOException("重定向次数过多");
    }
    private void hostResult(String request,boolean ok,String value){if(!closed&&web!=null)web.evaluateJavascript("window.__hostResult("+JSONObject.quote(request)+","+ok+","+JSONObject.quote(value)+");",null);}
    private void finishBrowserLogin(boolean ok,String value){
        if(browserRequest.isEmpty())return;String request=browserRequest;browserRequest="";if(browserPoll!=null)main.removeCallbacks(browserPoll);if(browserDialog!=null){browserDialog.setOnDismissListener(null);browserDialog.dismiss();browserDialog=null;}if(browserView!=null){browserView.stopLoading();browserView.destroy();browserView=null;}
        WebStorage.getInstance().deleteAllData();CookieManager.getInstance().removeAllCookies(removed->{if(browserOwner==this)browserOwner=null;hostResult(request,ok,value);});
    }
    private boolean secureLoginURL(URL address){return "https".equals(address.getProtocol())||((context.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0&&"http".equals(address.getProtocol())&&"127.0.0.1".equals(address.getHost()));}
    private void showBrowserLogin(String request,String raw){
        try{JSONObject options=new JSONObject(raw);URL address=new URL(options.getString("url")),cookieAddress=new URL(options.getString("cookiesOf"));if(!secureLoginURL(address)||!secureLoginURL(cookieAddress))throw new IOException("登录页面和 Cookie 地址必须使用 HTTPS");requireNetwork(cookieAddress);JSONArray names=options.optJSONArray("until");if(names==null||names.length()==0)throw new IOException("插件未指定登录成功的 Cookie");if(!(context instanceof android.app.Activity)||((android.app.Activity)context).isFinishing())throw new IOException("请在播放器界面中登录");if(browserOwner!=null)throw new IOException("已有登录窗口，请先完成或关闭");browserOwner=this;browserRequest=request;cookiesOf=cookieAddress.toString();requiredCookies=names;
            WebStorage.getInstance().deleteAllData();CookieManager cookies=CookieManager.getInstance();cookies.setAcceptCookie(true);cookies.removeAllCookies(removed->{if(closed){finishBrowserLogin(false,"插件已关闭");return;}browserView=new WebView(context);WebSettings settings=browserView.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setCacheMode(WebSettings.LOAD_NO_CACHE);String userAgent=options.optString("userAgent","");if(!userAgent.isEmpty())settings.setUserAgentString(userAgent);CookieManager.getInstance().setAcceptThirdPartyCookies(browserView,false);browserView.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){String scheme=req.getUrl().getScheme();return !"http".equals(scheme)&&!"https".equals(scheme);}});
                browserDialog=new AlertDialog.Builder(context).setTitle("插件网页登录").setView(browserView).setNegativeButton("取消",(d,w)->finishBrowserLogin(false,"已取消登录")).create();browserDialog.setOnCancelListener(d->finishBrowserLogin(false,"已取消登录"));browserDialog.show();browserDialog.getWindow().setLayout((int)(context.getResources().getDisplayMetrics().widthPixels*0.92),(int)(context.getResources().getDisplayMetrics().heightPixels*0.90));browserView.loadUrl(address.toString());final long deadline=android.os.SystemClock.elapsedRealtime()+180000;
                browserPoll=()->{if(browserRequest.isEmpty())return;if(android.os.SystemClock.elapsedRealtime()>deadline){finishBrowserLogin(false,"登录超时");return;}String cookie=CookieManager.getInstance().getCookie(cookiesOf);HashSet<String> present=new HashSet<>();if(cookie!=null)for(String pair:cookie.split(";")){int equals=pair.indexOf('=');if(equals>0)present.add(pair.substring(0,equals).trim());}boolean complete=true;for(int i=0;i<requiredCookies.length();i++)if(!present.contains(requiredCookies.optString(i)))complete=false;if(complete){try{finishBrowserLogin(true,new JSONObject().put("cookie",cookie).toString());}catch(Exception e){finishBrowserLogin(false,"无法读取登录结果");}}else main.postDelayed(browserPoll,500);};main.postDelayed(browserPoll,500);
            });
        }catch(Exception e){hostResult(request,false,e.getMessage()==null?"登录失败":e.getMessage());}
    }
    private final class NativeHost {
        @JavascriptInterface public void webSignIn(String request,String raw){main.post(()->showBrowserLogin(request,raw));}
        @JavascriptInterface public void ready(boolean ok,String value){main.post(()->finishLoad(ok,value));}
        @JavascriptInterface public void complete(String request,boolean ok,String value){main.post(()->{Callback cb=pending.remove(request);if(cb!=null)cb.done(ok,value);});}
        @JavascriptInterface public String storageGet(String key){return storage.getString(key,"");}
        @JavascriptInterface public void storageSet(String key,String value){storage.edit().putString(key,value).commit();}
        @JavascriptInterface public void storageRemove(String key){storage.edit().remove(key).commit();}
        @JavascriptInterface public String setting(String key){return context.getSharedPreferences("plugin_settings_"+id,Context.MODE_PRIVATE).getString(key,"");}
        @JavascriptInterface public String md5(String text){try{return hex(MessageDigest.getInstance("MD5").digest(text.getBytes("UTF-8")));}catch(Exception e){throw new IllegalStateException(e);}}
        @JavascriptInterface public String aesEcbEncrypt(String key,String text){try{Cipher c=Cipher.getInstance("AES/ECB/PKCS5Padding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key.getBytes("UTF-8"),"AES"));return hex(c.doFinal(text.getBytes("UTF-8")));}catch(Exception e){throw new IllegalStateException(e);}}
        @JavascriptInterface public void openURL(String address){main.post(()->{Uri uri=Uri.parse(address);if("https".equals(uri.getScheme())||"http".equals(uri.getScheme()))context.startActivity(new Intent(Intent.ACTION_VIEW,uri));});}
        @JavascriptInterface public void request(String request,String address,String init){network.execute(()->{JSONObject result;try{result=requestHttp(address,new JSONObject(init));}catch(Exception e){result=new JSONObject();try{result.put("error",e.getMessage()==null?"网络失败":e.getMessage());}catch(Exception ignored){}}String raw=result.toString();main.post(()->{if(!closed&&web!=null)web.evaluateJavascript("window.__networkResult("+JSONObject.quote(request)+","+JSONObject.quote(raw)+");",null);});});}
        @JavascriptInterface public void signInPrompt(String raw){main.post(()->{
            try{JSONObject prompt=new JSONObject(raw);String qr=prompt.optString("qr","");String text=prompt.optString("text","");
                if(loginDialog==null||!loginDialog.isShowing()){LinearLayout box=new LinearLayout(context);box.setOrientation(1);box.setPadding(24,16,24,16);loginText=new TextView(context);box.addView(loginText);loginQr=new ImageView(context);int qrSize=Math.max(128,Math.min(400,(int)(context.getResources().getDisplayMetrics().heightPixels*0.5)));box.addView(loginQr,new LinearLayout.LayoutParams(qrSize,qrSize));ScrollView loginScroll=new ScrollView(context);loginScroll.addView(box);loginDialog=new AlertDialog.Builder(context).setTitle("插件登录").setView(loginScroll).setNegativeButton("关闭",null).create();lastQr="";loginDialog.show();}
                loginText.setText(text);if(!qr.isEmpty()&&!qr.equals(lastQr)){BitMatrix matrix=new MultiFormatWriter().encode(qr,BarcodeFormat.QR_CODE,400,400);Bitmap b=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888);for(int y=0;y<400;y++)for(int x=0;x<400;x++)b.setPixel(x,y,matrix.get(x,y)?0xff000000:0xffffffff);loginQr.setImageBitmap(b);lastQr=qr;}
            }catch(Exception e){Toast.makeText(context,"登录提示失败："+e.getMessage(),Toast.LENGTH_LONG).show();}
        });}
    }
    private static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte x:bytes)b.append(String.format(Locale.US,"%02x",x&255));return b.toString();}
    private static byte[] readBytes(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>8*1024*1024)throw new IOException("插件响应超过 8 MB");out.write(buffer,0,n);}return out.toByteArray();}
    private static String read(InputStream in)throws IOException{try(InputStream input=in){return new String(readBytes(input),java.nio.charset.StandardCharsets.UTF_8);}}
}
