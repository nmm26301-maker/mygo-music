package com.mygo.music;

import android.app.AlertDialog;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.webkit.*;
import android.webkit.CookieManager;
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
    private PluginHttp http;
    private final HashMap<String,Callback> pending=new HashMap<>();
    private final ArrayList<Callback> loading=new ArrayList<>();
    private SharedPreferences storage, permissions;
    private JSONArray hosts=new JSONArray();
    private WebView web;
    private PluginResources resources;
    private boolean loaded, starting;
    private volatile boolean closed;
    private PluginProcesses processes;
    private final Set<PluginRedirect> redirects=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor ioWorkers=new ThreadPoolExecutor(2,6,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64));
    private String id, loadError="";
    private int sequence;
    private AlertDialog loginDialog;
    private TextView loginText;
    private ImageView loginQr;
    private String lastQr="", signInRequest="";
    private Bitmap loginBitmap;
    private static PluginRuntime browserOwner;
    private AlertDialog browserDialog;
    private WebView browserView;
    private String browserRequest="", cookiesOf="";
    private JSONArray requiredCookies;
    private Runnable browserPoll;


    public PluginRuntime(Context c,File dir){context=c;directory=dir;}
    public boolean isClosed(){return closed;}
    public void validate(Callback cb){main.post(()->ensureLoaded(cb));}
    public void call(String function,JSONObject args,Callback callback){main.post(()->ensureLoaded((ok,message)->{
        if(!ok){callback.done(false,message);return;}
        String request=String.valueOf(++sequence);pending.put(request,callback);if("signIn".equals(function))signInRequest=request;
        main.postDelayed(()->{Callback timeout=pending.remove(request);if(timeout!=null)timeout.done(false,"插件接口超时："+function);},"signIn".equals(function)?300000:45000);
        web.evaluateJavascript("window.__invoke("+JSONObject.quote(request)+","+JSONObject.quote(function)+","+(args==null?"{}":args.toString())+");",null);
    }));}
    public void dispose(){main.post(()->{closed=true;synchronized(redirects){for(PluginRedirect redirect:redirects)redirect.close();redirects.clear();}if(processes!=null)processes.close();ioWorkers.shutdownNow();if(!browserRequest.isEmpty())finishBrowserLogin(false,"插件已关闭");for(Callback cb:new ArrayList<>(pending.values()))cb.done(false,"插件已关闭");pending.clear();if(web!=null){web.removeJavascriptInterface("nativeHost");web.destroy();web=null;}if(loginDialog!=null)loginDialog.dismiss();if(http!=null)http.close();});}
    private void ensureLoaded(Callback cb){
        if(closed){cb.done(false,"插件已关闭");return;}if(loaded){cb.done(true,"");return;}if(!loadError.isEmpty()){cb.done(false,loadError);return;}
        loading.add(cb);if(starting)return;starting=true;
        try{
            JSONObject manifest=new JSONObject(read(new FileInputStream(new File(directory,"plugin.json"))));
            id=manifest.getString("id");JSONObject p=manifest.optJSONObject("permissions");if(p!=null&&p.optJSONArray("network")!=null)hosts=p.getJSONArray("network");
            storage=context.getSharedPreferences("plugin_data_"+id,Context.MODE_PRIVATE);permissions=context.getSharedPreferences("library",Context.MODE_PRIVATE);processes=new PluginProcesses(context,directory,id,p==null?null:p.opt("process"));
            File entry=new File(directory,manifest.optString("main","index.js"));
            if(!entry.getCanonicalPath().startsWith(directory.getCanonicalPath()+File.separator))throw new IOException("无效插件入口");
            if(entry.getName().endsWith(".ts"))throw new IOException("此插件入口为 TypeScript，需要已编译的 JavaScript 版本");
            resources=new PluginResources(directory);http=new PluginHttp(this::requireNetwork,(request,phase,result)->main.post(()->{if(!closed&&web!=null)web.evaluateJavascript("window.__networkResult("+JSONObject.quote(request)+","+JSONObject.quote(phase)+","+JSONObject.quote(result.toString())+");",null);}),resources);String module=resources.moduleURL(manifest.optString("main","index.js"));
            JSONObject locales=new JSONObject();File localeFile=new File(directory,"locales/zh-CN.json");if(localeFile.isFile())locales=new JSONObject(read(new FileInputStream(localeFile)));
            JSONObject config=new JSONObject().put("translations",locales).put("module",module).put("arch",architecture()).put("dir",directory.getAbsolutePath());
            String bridge=read(context.getAssets().open("plugin-host.js"));
            web=new WebView(context);WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
            web.setWebChromeClient(new WebChromeClient(){@Override public boolean onConsoleMessage(ConsoleMessage message){if((context.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)android.util.Log.i("MyGoPlugin",id+": "+message.message()+" @"+message.lineNumber());return true;}});
            web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}@Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){return resources.intercept(request.getUrl());}});
            web.addJavascriptInterface(new NativeHost(),"nativeHost");
            String html="<!doctype html><meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; script-src 'self' 'unsafe-inline'; connect-src 'none'\"><script>window.__pluginConfig="+config+";</script><script>"+bridge.replace("</script","<\\/script")+"</script>";
            resources.document(html);web.loadUrl(PluginResources.ORIGIN+"/");
            main.postDelayed(()->{if(!loaded&&loadError.isEmpty())finishLoad(false,"插件初始化超时，请检查脚本和依赖文件");},15000);
        }catch(Exception e){finishLoad(false,e.getMessage()==null?e.toString():e.getMessage());}
    }
    private void finishLoad(boolean ok,String message){if(closed)return;loaded=ok;loadError=ok?"":message;ArrayList<Callback> waiters=new ArrayList<>(loading);loading.clear();for(Callback cb:waiters)cb.done(ok,message);}
    static boolean hostMatches(String host,String rule){host=host.toLowerCase(Locale.US);rule=rule.toLowerCase(Locale.US);return "*".equals(rule)||host.equals(rule)||(rule.startsWith("*.")&&host.endsWith(rule.substring(1))&&host.length()>rule.length()-1);}
    private void requireNetwork(URL url)throws Exception{
        if(!"https".equals(url.getProtocol())&&!"http".equals(url.getProtocol()))throw new IOException("仅支持 HTTP/HTTPS");
        String host=url.getHost();if(processes!=null&&processes.declared()&&permissions.getBoolean("plugin_process_"+id,false)&&(host.equals("127.0.0.1")||host.equals("localhost")||host.equals("::1")||host.equals("[::1]")))return;
        if(!permissions.getBoolean("plugin_network_"+id,false))throw new IOException("请在插件网络权限中允许访问");
        for(int i=0;i<hosts.length();i++)if(hostMatches(url.getHost(),hosts.optString(i)))return;
        throw new IOException("插件未声明该网络域名："+url.getHost());
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
                browserDialog=new AlertDialog.Builder(context).setTitle("插件网页登录").setView(browserView).setNeutralButton("保存二维码",null).setNegativeButton("取消",(d,w)->finishBrowserLogin(false,"已取消登录")).create();browserDialog.setOnCancelListener(d->finishBrowserLogin(false,"已取消登录"));browserDialog.show();browserDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->saveBrowserQr());browserDialog.getWindow().setLayout((int)(context.getResources().getDisplayMetrics().widthPixels*0.92),(int)(context.getResources().getDisplayMetrics().heightPixels*0.90));browserView.loadUrl(address.toString());final long deadline=android.os.SystemClock.elapsedRealtime()+180000;
                browserPoll=()->{if(browserRequest.isEmpty())return;if(android.os.SystemClock.elapsedRealtime()>deadline){finishBrowserLogin(false,"登录超时");return;}String cookie=CookieManager.getInstance().getCookie(cookiesOf);HashSet<String> present=new HashSet<>();if(cookie!=null)for(String pair:cookie.split(";")){int equals=pair.indexOf('=');if(equals>0)present.add(pair.substring(0,equals).trim());}boolean complete=true;for(int i=0;i<requiredCookies.length();i++)if(!present.contains(requiredCookies.optString(i)))complete=false;if(complete){try{finishBrowserLogin(true,new JSONObject().put("cookie",cookie).toString());}catch(Exception e){finishBrowserLogin(false,"无法读取登录结果");}}else main.postDelayed(browserPoll,500);};main.postDelayed(browserPoll,500);
            });
        }catch(Exception e){hostResult(request,false,e.getMessage()==null?"登录失败":e.getMessage());}
    }
    private void saveBrowserQr(){
        if(browserView==null||browserView.getWidth()<=0||browserView.getHeight()<=0)return;
        float scale=Math.min(1,(float)Math.sqrt(2000000.0/(browserView.getWidth()*(double)browserView.getHeight())));
        Bitmap visible=Bitmap.createBitmap(Math.max(1,(int)(browserView.getWidth()*scale)),Math.max(1,(int)(browserView.getHeight()*scale)),Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(visible);canvas.scale(scale,scale);browserView.draw(canvas);
        Button button=browserDialog.getButton(AlertDialog.BUTTON_NEUTRAL);button.setEnabled(false);
        if(!submitHost("",()->{
            try{
                Bitmap qr=PluginQrImage.extract(visible);
                if(Build.VERSION.SDK_INT<29&&context instanceof MainActivity)main.post(()->((MainActivity)context).saveLoginQrDocument(qr));
                else{try{PluginQrImage.save(context,qr);}finally{qr.recycle();}main.post(()->Toast.makeText(context,"二维码已保存到相册，请到对应 App 扫一扫中选择，再返回等待确认",Toast.LENGTH_LONG).show());}
            }catch(Exception error){String message=error instanceof com.google.zxing.ReaderException?"未识别到二维码，请让登录二维码完整显示后再保存":"二维码保存失败："+error.getMessage();main.post(()->Toast.makeText(context,message,Toast.LENGTH_LONG).show());}
            finally{visible.recycle();main.post(()->button.setEnabled(true));}
        })){visible.recycle();button.setEnabled(true);}
    }
    private String result(boolean ok,Object value){try{return new JSONObject().put("ok",ok).put(ok?"value":"error",value==null?JSONObject.NULL:value).toString();}catch(Exception e){return "{\"ok\":false,\"error\":\"辅助程序返回数据无效\"}";}}
    private void hostReply(String request,boolean ok,Object value){try{String raw;if(ok){String array=new JSONArray().put(value==null?JSONObject.NULL:value).toString();if(array==null)throw new IllegalArgumentException("invalid host value");raw=array.substring(1,array.length()-1);}else raw=String.valueOf(value);main.post(()->hostResult(request,ok,raw));}catch(Exception error){main.post(()->hostResult(request,false,"辅助程序返回数据无效"));}}

    private boolean submitHost(String request,Runnable action){try{ioWorkers.execute(action);return true;}catch(RejectedExecutionException error){hostReply(request,false,"插件任务已关闭或队列已满");return false;}}
    private final class NativeHost {
        @JavascriptInterface public String processSpawn(String command,String args){try{return result(true,processes.spawn(command,new JSONArray(args)));}catch(Exception e){return result(false,e.getMessage()==null?e.toString():e.getMessage());}}
        @JavascriptInterface public String processSync(long pid,String method,String value){try{if(method.equals("write"))processes.write(pid,value);else if(method.equals("closeStdin"))processes.closeStdin(pid);else if(method.equals("kill"))processes.kill(pid);else if(method.equals("errorOutput"))return result(true,processes.errors(pid));else throw new IOException("未知辅助程序操作");return result(true,null);}catch(Exception e){return result(false,e.getMessage()==null?e.toString():e.getMessage());}}
        @JavascriptInterface public void processTask(String request,long pid,String method){submitHost(request,()->{try{Object value;if(method.equals("readLine"))value=processes.readLine(pid);else if(method.equals("wait"))value=processes.waitFor(pid);else throw new IOException("未知辅助程序任务");hostReply(request,true,value);}catch(Exception e){hostReply(request,false,e.getMessage()==null?e.toString():e.getMessage());}});}
        @JavascriptInterface public void waitForRedirect(String request,String raw){synchronized(redirects){try{if(closed)throw new IOException("插件已关闭");if(hosts.length()==0||!permissions.getBoolean("plugin_network_"+id,false))throw new IOException("登录回调需要已允许的插件网络权限");if(!redirects.isEmpty())throw new IOException("已有登录回调正在等待");PluginRedirect redirect=new PluginRedirect(new JSONObject(raw));redirects.add(redirect);if(!submitHost(request,()->{Object value;boolean success;try{value=redirect.await();success=true;}catch(Exception e){value=e.getMessage()==null?e.toString():e.getMessage();success=false;}finally{redirect.close();redirects.remove(redirect);}hostReply(request,success,value);})){redirect.close();redirects.remove(redirect);}}catch(Exception e){hostReply(request,false,e.getMessage()==null?e.toString():e.getMessage());}}}

        @JavascriptInterface public String removeDataDir(String name){try{File root=new File(directory,"data"),target=new File(root,name);if(name.isEmpty()||name.contains("\\")||!root.getCanonicalPath().startsWith(directory.getCanonicalPath()+File.separator)||!target.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator))throw new IOException("插件数据目录路径越界");PluginArchive.delete(target);return result(true,null);}catch(Exception e){return result(false,e.getMessage()==null?e.toString():e.getMessage());}}
        @JavascriptInterface public void webSignIn(String request,String raw){main.post(()->showBrowserLogin(request,raw));}
        @JavascriptInterface public void ready(boolean ok,String value){main.post(()->finishLoad(ok,value));}
        @JavascriptInterface public void complete(String request,boolean ok,String value){main.post(()->{if(request.equals(signInRequest)){signInRequest="";if(loginDialog!=null)loginDialog.dismiss();loginBitmap=null;}Callback cb=pending.remove(request);if(cb!=null)cb.done(ok,value);});}
        @JavascriptInterface public String storageGet(String key){return storage.getString(key,"");}
        @JavascriptInterface public void storageSet(String key,String value){storage.edit().putString(key,value).commit();}
        @JavascriptInterface public void storageRemove(String key){storage.edit().remove(key).commit();}
        @JavascriptInterface public String setting(String key){return context.getSharedPreferences("plugin_settings_"+id,Context.MODE_PRIVATE).getString(key,"");}
        @JavascriptInterface public String md5(String text){try{return hex(MessageDigest.getInstance("MD5").digest(text.getBytes("UTF-8")));}catch(Exception e){throw new IllegalStateException(e);}}
        @JavascriptInterface public String aesEcbEncrypt(String key,String text){try{Cipher c=Cipher.getInstance("AES/ECB/PKCS5Padding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key.getBytes("UTF-8"),"AES"));return hex(c.doFinal(text.getBytes("UTF-8")));}catch(Exception e){throw new IllegalStateException(e);}}
        @JavascriptInterface public void openURL(String address){main.post(()->{if(closed)return;Uri uri=Uri.parse(address);if("https".equals(uri.getScheme())||"http".equals(uri.getScheme()))context.startActivity(new Intent(Intent.ACTION_VIEW,uri).addFlags(context instanceof android.app.Activity?0:Intent.FLAG_ACTIVITY_NEW_TASK));});}
        @JavascriptInterface public void request(String request,String address,String init){try{http.open(request,address,new JSONObject(init));}catch(Exception e){main.post(()->{if(!closed&&web!=null)web.evaluateJavascript("window.__networkResult("+JSONObject.quote(request)+",\"error\","+JSONObject.quote("{\"error\":\"无效网络请求\"}")+");",null);});}}
        @JavascriptInterface public void readResponse(String request){if(http!=null)http.read(request);}
        @JavascriptInterface public void cancelResponse(String request){if(http!=null)http.cancel(request);}
        @JavascriptInterface public void signInPrompt(String raw){main.post(()->{
            if(closed)return;try{JSONObject prompt=new JSONObject(raw);String qr=prompt.optString("qr","");String text=prompt.optString("text","");
                if(loginDialog==null||!loginDialog.isShowing()){LinearLayout box=new LinearLayout(context);box.setOrientation(1);box.setPadding(24,16,24,16);loginText=new TextView(context);box.addView(loginText);TextView phoneHint=new TextView(context);phoneHint.setText("同一部手机登录：保存二维码后，到对应音乐应用的扫一扫中从相册选择，再返回这里等待确认。");box.addView(phoneHint);Button saveQr=new Button(context);saveQr.setText("保存二维码");saveQr.setOnClickListener(view->{Bitmap bitmap=loginBitmap;if(bitmap==null){Toast.makeText(context,"二维码尚未生成",Toast.LENGTH_SHORT).show();return;}if(Build.VERSION.SDK_INT<29&&context instanceof MainActivity){((MainActivity)context).saveLoginQrDocument(bitmap);return;}submitHost("",()->{try{PluginQrImage.save(context,bitmap);main.post(()->Toast.makeText(context,"二维码已保存到相册，请到对应应用扫一扫中选择",Toast.LENGTH_LONG).show());}catch(Exception error){main.post(()->Toast.makeText(context,"保存失败："+error.getMessage(),Toast.LENGTH_LONG).show());}});});box.addView(saveQr);loginQr=new ImageView(context);int qrSize=Math.max(128,Math.min(400,(int)(context.getResources().getDisplayMetrics().heightPixels*0.5)));box.addView(loginQr,new LinearLayout.LayoutParams(qrSize,qrSize));ScrollView loginScroll=new ScrollView(context);loginScroll.addView(box);loginDialog=new AlertDialog.Builder(context).setTitle("插件登录").setView(loginScroll).setNegativeButton("关闭",(dialog,which)->dispose()).create();loginDialog.setOnCancelListener(dialog->dispose());lastQr="";loginDialog.show();}
                loginText.setText(text);if(!qr.isEmpty()&&!qr.equals(lastQr)){loginBitmap=PluginQrImage.create(qr);loginQr.setImageBitmap(loginBitmap);lastQr=qr;}
            }catch(Exception e){Toast.makeText(context,"登录提示失败："+e.getMessage(),Toast.LENGTH_LONG).show();}
        });}
    }
    private static String architecture(){String abi=Build.SUPPORTED_ABIS[0];if(abi.equals("arm64-v8a"))return "arm64";if(abi.equals("x86_64"))return "amd64";if(abi.startsWith("armeabi"))return "arm";if(abi.equals("x86"))return "386";return abi;}
    private static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte x:bytes)b.append(String.format(Locale.US,"%02x",x&255));return b.toString();}
    private static byte[] readBytes(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>8*1024*1024)throw new IOException("插件响应超过 8 MB");out.write(buffer,0,n);}return out.toByteArray();}
    private static String read(InputStream in)throws IOException{try(InputStream input=in){return new String(readBytes(input),java.nio.charset.StandardCharsets.UTF_8);}}
}
