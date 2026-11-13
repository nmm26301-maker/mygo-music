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
    private PluginResources resources;private PluginTypeScript typescript;
    private boolean loaded, starting;
    private volatile boolean closed;
    private PluginProcesses processes;
    private final Set<PluginRedirect> redirects=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor ioWorkers=new ThreadPoolExecutor(2,6,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64));
    private String id, loadError="";
    private int sequence;
    private AlertDialog loginDialog;
    private TextView loginText,loginPhoneHint;private Button loginSaveButton,loginOpenButton;private AlertDialog phoneDialog;private boolean loginQrSaving;private String loginPromptText="";
    private ImageView loginQr;
    private String lastQr="", signInRequest="";
    private Bitmap loginBitmap;
    interface LoginRefresh {void restart(boolean save,boolean open);}
    private LoginRefresh loginRefresh;private boolean saveFreshQr,openFreshQr;private long qrCreated;private Runnable qrAge;
    void refreshLogin(LoginRefresh listener,boolean save,boolean open){loginRefresh=listener;saveFreshQr=save;openFreshQr=open;}
    private void requestFreshQr(boolean save,boolean open){if(loginRefresh!=null){loginRefresh.restart(save,open);return;}if(save)saveNativeQr(open);}

    private static PluginRuntime browserOwner;
    private AlertDialog browserDialog;
    private WebView browserView;
    private String browserRequest="", cookiesOf="";
    private BrowserCookieGate browserGate;private android.app.Activity browserActivity;private int browserOrientation;
    private Runnable browserPoll;private long browserDeadline;private QrZoom qrZoom;private volatile int browserGeneration;


    public PluginRuntime(Context c,File dir){context=c;directory=dir;}
    public boolean isClosed(){return closed;}
    public void validate(Callback cb){main.post(()->ensureLoaded(cb));}
    public void call(String function,JSONObject args,Callback callback){main.post(()->ensureLoaded((ok,message)->{
        if(!ok){callback.done(false,message);return;}
        String request=String.valueOf(++sequence);pending.put(request,callback);if("signIn".equals(function))signInRequest=request;
        main.postDelayed(()->{Callback timeout=pending.remove(request);if(timeout!=null){finishSignInPrompt(request);timeout.done(false,"插件接口超时："+function);}},"signIn".equals(function)?300000:45000);
        web.evaluateJavascript("window.__invoke("+JSONObject.quote(request)+","+JSONObject.quote(function)+","+(args==null?"{}":args.toString())+");",null);
    }));}
    public void dispose(){closed=true;main.post(()->{if(qrAge!=null)main.removeCallbacks(qrAge);closePhoneDialog();closeQrZoom();synchronized(redirects){for(PluginRedirect redirect:redirects)redirect.close();redirects.clear();}if(processes!=null)processes.close();ioWorkers.shutdownNow();if(!browserRequest.isEmpty())finishBrowserLogin(false,"插件已关闭");for(Callback cb:new ArrayList<>(pending.values()))cb.done(false,"插件已关闭");pending.clear();if(web!=null){web.removeJavascriptInterface("nativeHost");web.destroy();web=null;}if(loginDialog!=null)loginDialog.dismiss();if(http!=null)http.close();if(typescript!=null)typescript.close();});}
    private void ensureLoaded(Callback cb){
        if(closed){cb.done(false,"插件已关闭");return;}if(loaded){cb.done(true,"");return;}if(!loadError.isEmpty()){cb.done(false,loadError);return;}
        loading.add(cb);if(starting)return;starting=true;
        try{
            JSONObject manifest=new JSONObject(read(new FileInputStream(new File(directory,"plugin.json"))));
            id=PluginIdentity.instance(directory,manifest);PluginProtocol.validate(manifest);PluginMedia.validate(manifest);JSONObject p=manifest.optJSONObject("permissions");if(p!=null&&p.optJSONArray("network")!=null)hosts=p.getJSONArray("network");
            storage=context.getSharedPreferences("plugin_data_"+id,Context.MODE_PRIVATE);permissions=context.getSharedPreferences("library",Context.MODE_PRIVATE);processes=new PluginProcesses(context,directory,id,p==null?null:p.opt("process"));
            File entry=new File(directory,manifest.optString("main","index.js"));
            if(!entry.getCanonicalPath().startsWith(directory.getCanonicalPath()+File.separator))throw new IOException("无效插件入口");
            typescript=new PluginTypeScript(context,directory);
            resources=new PluginResources(directory,context,typescript);http=new PluginHttp(this::requireNetwork,(request,phase,result)->main.post(()->{if(!closed&&web!=null)web.evaluateJavascript("window.__networkResult("+JSONObject.quote(request)+","+JSONObject.quote(phase)+","+JSONObject.quote(result.toString())+");",null);}),resources);String module=resources.moduleURL(manifest.optString("main","index.js"));
            JSONObject locales=new JSONObject();File localeFile=new File(directory,"locales/zh-CN.json");if(localeFile.isFile())locales=new JSONObject(read(new FileInputStream(localeFile)));
            JSONObject config=new JSONObject().put("typescript",typescript.needed()).put("translations",locales).put("module",module).put("arch",architecture()).put("dir",directory.getAbsolutePath());
            String bridge=read(context.getAssets().open("plugin-host.js"));
            web=new WebView(context);WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);if(Build.VERSION.SDK_INT>=26)web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT,false);
            web.setWebChromeClient(new WebChromeClient(){@Override public boolean onConsoleMessage(ConsoleMessage message){if((context.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)android.util.Log.i("MyGoPlugin",id+": "+message.message()+" @"+message.lineNumber());return true;}});
            web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}@Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){return resources.intercept(request.getUrl());}});
            web.addJavascriptInterface(new NativeHost(),"nativeHost");typescript.compiler(name->main.post(()->{if(closed||web==null){typescript.complete(name,"","插件已关闭");return;}web.evaluateJavascript("window.__compilePluginModule("+JSONObject.quote(name)+");",null);}));
            String html="<!doctype html><meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; script-src 'self' 'unsafe-inline'; connect-src 'none'\"><script>window.__pluginConfig="+config+";</script>"+(typescript.needed()?"<script src=\""+PluginResources.ORIGIN+"/.host/babel.js\"></script>":"")+"<script>"+bridge.replace("</script","<\\/script")+"</script>";
            resources.document(html);web.loadUrl(PluginResources.ORIGIN+"/");
            main.postDelayed(()->{if(!loaded&&loadError.isEmpty())finishLoad(false,"插件初始化超时，请检查脚本和依赖文件");},typescript.needed()?45000:15000);
        }catch(Exception e){finishLoad(false,e.getMessage()==null?e.toString():e.getMessage());}
    }
    private void finishLoad(boolean ok,String message){if(closed)return;loaded=ok;loadError=ok?"":message;ArrayList<Callback> waiters=new ArrayList<>(loading);loading.clear();for(Callback cb:waiters)cb.done(ok,message);}
    static boolean hostMatches(String host,String rule){host=host.toLowerCase(Locale.US);rule=rule.toLowerCase(Locale.US);return "*".equals(rule)||host.equals(rule)||(rule.startsWith("*.")&&host.endsWith(rule.substring(1))&&host.length()>rule.length()-1);}
    private void requireNetwork(URL url)throws Exception{
        if(!"https".equals(url.getProtocol())&&!"http".equals(url.getProtocol()))throw new IOException("仅支持 HTTP/HTTPS");
        String host=url.getHost();if(processes!=null&&processes.declared()&&permissions.getBoolean("plugin_process_"+id,false)&&(host.equals("127.0.0.1")||host.equals("localhost")||host.equals("::1")||host.equals("[::1]")))return;
        if(!permissions.getBoolean("plugin_network_"+id,false))throw new IOException("请在插件网络权限中允许访问");
        if(host.equals("userservice.kugou.com")&&resources!=null&&resources.deviceNetwork()&&permissions.getBoolean("plugin_device_network_"+id,false))return;
        for(int i=0;i<hosts.length();i++)if(hostMatches(url.getHost(),hosts.optString(i)))return;
        throw new IOException("插件未声明该网络域名："+url.getHost());
    }
    private void hostResult(String request,boolean ok,String value){if(!closed&&web!=null)web.evaluateJavascript("window.__hostResult("+JSONObject.quote(request)+","+ok+","+JSONObject.quote(value)+");",null);}
    private void finishBrowserLogin(boolean ok,String value){
        if(browserRequest.isEmpty())return;String request=browserRequest;browserRequest="";browserGeneration++;if(browserActivity!=null){browserActivity.setRequestedOrientation(browserOrientation);browserActivity=null;}closePhoneDialog();closeQrZoom();if(browserPoll!=null)main.removeCallbacks(browserPoll);if(browserDialog!=null){browserDialog.setOnDismissListener(null);browserDialog.dismiss();browserDialog=null;}if(browserView!=null){browserView.stopLoading();browserView.destroy();browserView=null;}
        WebStorage.getInstance().deleteAllData();CookieManager.getInstance().removeAllCookies(removed->{if(browserOwner==this)browserOwner=null;hostResult(request,ok,value);});
    }
    private boolean secureLoginURL(URL address){return "https".equals(address.getProtocol())||((context.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0&&"http".equals(address.getProtocol())&&"127.0.0.1".equals(address.getHost()));}
    private void showBrowserLogin(String request,String raw){
        try{JSONObject options=new JSONObject(raw);URL address=new URL(options.getString("url")),cookieAddress=new URL(options.getString("cookiesOf"));if(!secureLoginURL(address)||!secureLoginURL(cookieAddress))throw new IOException("登录页面和 Cookie 地址必须使用 HTTPS");requireNetwork(address);requireNetwork(cookieAddress);BrowserCookieGate gate=new BrowserCookieGate(options);if(!(context instanceof android.app.Activity)||((android.app.Activity)context).isFinishing())throw new IOException("请在播放器界面中登录");if(browserOwner!=null)throw new IOException("已有登录窗口，请先完成或关闭");browserOwner=this;browserRequest=request;cookiesOf=cookieAddress.toString();browserGate=gate;final boolean imageScanning=options.optBoolean("imageScanning",true),desktop=options.optBoolean("desktop",true);
            WebStorage.getInstance().deleteAllData();CookieManager cookies=CookieManager.getInstance();cookies.setAcceptCookie(true);cookies.removeAllCookies(removed->{if(closed){finishBrowserLogin(false,"插件已关闭");return;}browserView=new WebView(context);WebSettings settings=browserView.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setCacheMode(WebSettings.LOAD_NO_CACHE);String userAgent=options.optString("userAgent","");if(address.getHost().equals("passport.ximalaya.com")&&address.getPath().equals("/page/web/login"))userAgent="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36";if(!userAgent.isEmpty())settings.setUserAgentString(userAgent);if(desktop){settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);settings.setBuiltInZoomControls(true);settings.setDisplayZoomControls(false);browserActivity=(android.app.Activity)context;browserOrientation=browserActivity.getRequestedOrientation();browserActivity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);}browserView.setMinimumHeight((int)(context.getResources().getDisplayMetrics().heightPixels*.60));CookieManager.getInstance().setAcceptThirdPartyCookies(browserView,false);
                browserView.setWebViewClient(new WebViewClient(){@Override public void onPageStarted(WebView view,String url,Bitmap favicon){browserGeneration++;closeQrZoom();}@Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){String scheme=req.getUrl().getScheme();boolean blocked=!"http".equals(scheme)&&!"https".equals(scheme);if(blocked&&!imageScanning)Toast.makeText(context,"此快捷登录入口暂不支持，请在官网页面选择其他登录方式",Toast.LENGTH_LONG).show();return blocked;}});
                AlertDialog.Builder builder=new AlertDialog.Builder(context).setTitle(options.optString("title",imageScanning?"网页登录与本机相册扫码":"官方网站登录")).setView(browserView).setNegativeButton("取消",(dialog,which)->finishBrowserLogin(false,"已取消登录"));
                if(imageScanning)builder.setPositiveButton("保存并打开扫码 App",null).setNeutralButton("保存二维码",null);else builder.setPositiveButton("刷新官网页面",null);
                if(desktop||!imageScanning){LinearLayout page=new LinearLayout(context);page.setOrientation(LinearLayout.VERTICAL);TextView explanation=new TextView(context);explanation.setText(address.getProtocol()+"://"+address.getHost()+"\n"+options.optString("message",imageScanning?"可在官网选择登录方式；二维码可放大或保存后打开对应 App。":"请在官方网站完成登录，平台要求的验证必须由本人完成。"));int padding=(int)(12*context.getResources().getDisplayMetrics().density);explanation.setPadding(padding,padding,padding,padding/2);page.addView(explanation,new LinearLayout.LayoutParams(-1,-2));if(imageScanning){LinearLayout tools=new LinearLayout(context);Button zoom=new Button(context);zoom.setText("放大二维码");zoom.setOnClickListener(view->readBrowserQr(false,true));tools.addView(zoom,new LinearLayout.LayoutParams(0,-2,1));Button refresh=new Button(context);refresh.setText("刷新二维码");refresh.setOnClickListener(view->{browserGeneration++;closeQrZoom();browserDeadline=SystemClock.elapsedRealtime()+180000;if(browserView!=null)browserView.loadUrl(address.toString());});tools.addView(refresh,new LinearLayout.LayoutParams(0,-2,1));page.addView(tools);}page.addView(browserView,new LinearLayout.LayoutParams(-1,0,1));page.setMinimumHeight((int)(context.getResources().getDisplayMetrics().heightPixels*.80));builder.setView(page);}

                browserDialog=builder.create();browserDialog.setOnCancelListener(dialog->finishBrowserLogin(false,"已取消登录"));browserDialog.show();if(imageScanning){browserDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view->saveBrowserQr(false));browserDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->saveBrowserQr(true));}else browserDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->{if(browserView!=null)browserView.reload();});
                if(desktop){WebView loginView=browserView;browserDialog.getWindow().getDecorView().addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{if(browserView!=loginView)return;int minimum=(int)((bottom-top)*.60);if(minimum>0&&loginView.getMinimumHeight()!=minimum)loginView.setMinimumHeight(minimum);if(loginView.getParent() instanceof LinearLayout){LinearLayout page=(LinearLayout)loginView.getParent();int pageHeight=(int)((bottom-top)*.80);if(pageHeight>0&&page.getMinimumHeight()!=pageHeight)page.setMinimumHeight(pageHeight);}});}browserDialog.getWindow().setLayout(desktop?android.view.ViewGroup.LayoutParams.MATCH_PARENT:(int)(context.getResources().getDisplayMetrics().widthPixels*.92),desktop?android.view.ViewGroup.LayoutParams.MATCH_PARENT:(int)(context.getResources().getDisplayMetrics().heightPixels*.90));browserView.loadUrl(address.toString());browserDeadline=SystemClock.elapsedRealtime()+(imageScanning?180000:270000);
                browserPoll=()->{if(browserRequest.isEmpty())return;if(SystemClock.elapsedRealtime()>browserDeadline){finishBrowserLogin(false,"登录超时");return;}String cookie=CookieManager.getInstance().getCookie(cookiesOf);if(browserGate.ready(cookie)){try{finishBrowserLogin(true,new JSONObject().put("cookie",cookie).toString());}catch(Exception error){finishBrowserLogin(false,"无法读取登录结果");}}else main.postDelayed(browserPoll,500);};main.postDelayed(browserPoll,500);
            });
        }catch(Exception error){if(!browserRequest.isEmpty())finishBrowserLogin(false,error.getMessage()==null?"登录失败":error.getMessage());else hostResult(request,false,error.getMessage()==null?"登录失败":error.getMessage());}
    }
    private void saveBrowserQr(boolean openApp){readBrowserQr(openApp,false);}
    private void readBrowserQr(boolean openApp,boolean zoom){
        if(browserView==null||browserView.getWidth()<=0||browserView.getHeight()<=0){Toast.makeText(context,"登录页面正在加载，请稍后保存二维码",Toast.LENGTH_LONG).show();return;}
        WebView target=browserView;
        Button button=browserDialog.getButton(AlertDialog.BUTTON_NEUTRAL);browserQrEnabled(false);String request=browserRequest;final int page=browserGeneration;
        target.postVisualStateCallback(0,new WebView.VisualStateCallback(){@Override public void onComplete(long frame){if(closed||browserView!=target||page!=browserGeneration){browserQrEnabled(true);return;}captureBrowserQr(target,button,openApp,request,zoom,page);}});
    }
    private void captureBrowserQr(WebView target,Button button,boolean openApp,String request,boolean zoom,int page){
        float scale=Math.min(1,(float)Math.sqrt(2000000.0/(target.getWidth()*(double)target.getHeight())));
        Bitmap visible=Bitmap.createBitmap(Math.max(1,(int)(target.getWidth()*scale)),Math.max(1,(int)(target.getHeight()*scale)),Bitmap.Config.ARGB_8888);
        if(Build.VERSION.SDK_INT>=26){
            target.invalidate();target.postOnAnimation(()->target.postOnAnimation(()->{
                if(closed||browserView!=target||browserDialog==null||page!=browserGeneration){visible.recycle();browserQrEnabled(true);return;}
                int[] location=new int[2];target.getLocationInWindow(location);android.graphics.Rect bounds=new android.graphics.Rect(location[0],location[1],location[0]+target.getWidth(),location[1]+target.getHeight());
                try{android.view.PixelCopy.request(browserDialog.getWindow(),bounds,visible,status->{if(status==android.view.PixelCopy.SUCCESS)saveBrowserQrImage(visible,button,openApp,request,zoom,page);else{visible.recycle();browserQrEnabled(true);Toast.makeText(context,"页面暂未绘制完成，请稍后再保存二维码",Toast.LENGTH_LONG).show();}},main);}
                catch(IllegalArgumentException error){visible.recycle();browserQrEnabled(true);Toast.makeText(context,"页面暂未绘制完成，请稍后再保存二维码",Toast.LENGTH_LONG).show();}
            }));return;
        }
        android.graphics.Canvas canvas=new android.graphics.Canvas(visible);canvas.drawColor(android.graphics.Color.WHITE);canvas.scale(scale,scale);target.draw(canvas);
        saveBrowserQrImage(visible,button,openApp,request,zoom,page);
    }
    private void saveBrowserQrImage(Bitmap visible,Button button,boolean openApp,String request,boolean zoom,int page){
        if(!submitHost("",()->{
            try{
                if(closed||page!=browserGeneration)return;String payload=PluginQrImage.payload(visible);if(zoom){main.post(()->{if(!closed&&page==browserGeneration&&request.equals(browserRequest))showQrZoom(payload);});return;}Bitmap qr=PluginQrImage.create(payload);
                if(Build.VERSION.SDK_INT<29&&context instanceof MainActivity)main.post(()->{((MainActivity)context).saveLoginQrDocument(qr,()->browserQrSaved(payload,openApp,request));qr.recycle();});
                else{try{PluginQrImage.save(context,qr);}finally{qr.recycle();}main.post(()->browserQrSaved(payload,openApp,request));}
            }catch(Exception error){if((context.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)android.util.Log.i("MyGoPlugin",id+": QR save failed: "+error.getClass().getSimpleName());String message=error instanceof com.google.zxing.ReaderException?"未识别到二维码，请让登录二维码完整显示后再保存":"二维码保存失败："+error.getMessage();main.post(()->Toast.makeText(context,message,Toast.LENGTH_LONG).show());}
            finally{visible.recycle();main.post(()->browserQrEnabled(true));}
        })){visible.recycle();browserQrEnabled(true);}
    }
    private void closeQrZoom(){if(qrZoom!=null){qrZoom.close();qrZoom=null;}}
    private void showQrZoom(String payload){if(payload==null||payload.isEmpty())return;try{closeQrZoom();qrZoom=new QrZoom(context,payload);}catch(Exception error){Toast.makeText(context,"二维码尚未准备好，请刷新后重试",Toast.LENGTH_LONG).show();}}
    private void closePhoneDialog(){if(phoneDialog!=null){phoneDialog.dismiss();phoneDialog=null;}}
    private void finishSignInPrompt(String request){if(!request.equals(signInRequest))return;signInRequest="";if(qrAge!=null)main.removeCallbacks(qrAge);closePhoneDialog();closeQrZoom();if(loginDialog!=null)loginDialog.dismiss();loginBitmap=null;}
    private void browserQrEnabled(boolean enabled){if(browserDialog==null)return;for(int which:new int[]{AlertDialog.BUTTON_NEUTRAL,AlertDialog.BUTTON_POSITIVE}){Button button=browserDialog.getButton(which);if(button!=null)button.setEnabled(enabled);}}
    private void browserQrSaved(String payload,boolean openApp,String request){
        if(closed||browserRequest.isEmpty()||!browserRequest.equals(request))return;
        if(browserDialog!=null)browserDialog.setTitle("已保存，使用本机相册扫码后返回");
        Toast.makeText(context,PhoneQrLogin.cameraAuthorization(payload,"")?"二维码已保存；此授权需摄像头扫描，不能从相册授权":"二维码已保存到相册，登录等待仍在继续",Toast.LENGTH_LONG).show();
        if(openApp){closePhoneDialog();phoneDialog=PhoneQrLogin.open(context,payload,"",()->!closed&&request.equals(browserRequest));}
    }
    private void updateLoginPrompt(String raw){
        if(closed)return;try{JSONObject prompt=new JSONObject(raw);String qr=prompt.optString("qr",""),text=prompt.optString("text","");
            if(loginDialog==null||!loginDialog.isShowing()){
                LinearLayout box=new LinearLayout(context);box.setOrientation(1);int padding=(int)(12*context.getResources().getDisplayMetrics().density);box.setPadding(padding,padding,padding,padding);loginText=new TextView(context);loginText.setMaxLines(2);loginText.setEllipsize(android.text.TextUtils.TruncateAt.END);loginPhoneHint=new TextView(context);
                loginOpenButton=new Button(context);loginOpenButton.setOnClickListener(view->{if(PhoneQrLogin.cameraAuthorization(lastQr,loginPromptText)&&resources!=null&&resources.qqWebLogin()&&loginRefresh!=null){context.getSharedPreferences("plugin_settings_"+id,0).edit().putString("loginMethod","web").apply();requestFreshQr(false,false);}else requestFreshQr(true,true);});loginOpenButton.setTextSize(13);loginOpenButton.setPadding(padding/2,0,padding/2,0);box.addView(loginOpenButton,new LinearLayout.LayoutParams(-1,padding*4));
                loginSaveButton=new Button(context);loginSaveButton.setText("保存二维码");loginSaveButton.setOnClickListener(view->requestFreshQr(true,false));loginSaveButton.setTextSize(13);loginSaveButton.setPadding(padding/2,0,padding/2,0);Button enlarge=new Button(context);enlarge.setText("全屏放大");enlarge.setTextSize(13);enlarge.setPadding(padding/2,0,padding/2,0);enlarge.setOnClickListener(view->showQrZoom(lastQr));LinearLayout actions=new LinearLayout(context);actions.addView(loginSaveButton,new LinearLayout.LayoutParams(0,padding*4,1));actions.addView(enlarge,new LinearLayout.LayoutParams(0,padding*4,1));box.addView(actions);box.addView(loginText);box.addView(loginPhoneHint);
                loginQr=new ImageView(context);loginQr.setContentDescription("登录二维码，点击全屏放大");loginQr.setScaleType(ImageView.ScaleType.FIT_CENTER);loginQr.setBackgroundColor(android.graphics.Color.WHITE);loginQr.setOnClickListener(view->showQrZoom(lastQr));ScrollView scroll=new ScrollView(context);scroll.addView(box);LinearLayout panel=new LinearLayout(context);panel.setGravity(android.view.Gravity.CENTER_VERTICAL);boolean horizontal=context.getResources().getDisplayMetrics().widthPixels>context.getResources().getDisplayMetrics().heightPixels;panel.setOrientation(horizontal?0:1);int square=(int)(Math.min(context.getResources().getDisplayMetrics().heightPixels*.68,context.getResources().getDisplayMetrics().widthPixels*.44));if(horizontal){panel.addView(loginQr,new LinearLayout.LayoutParams(square,-1));panel.addView(scroll,new LinearLayout.LayoutParams(0,-1,1));}else{panel.addView(loginQr,new LinearLayout.LayoutParams(square*2,square*2));panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));}panel.setMinimumHeight((int)(context.getResources().getDisplayMetrics().heightPixels*.55));
                loginDialog=new AlertDialog.Builder(context).setTitle("扫码登录").setView(panel).setPositiveButton("刷新二维码",null).setNegativeButton("取消",(dialog,which)->dispose()).create();loginDialog.setOnCancelListener(dialog->dispose());loginDialog.setCanceledOnTouchOutside(false);lastQr="";loginDialog.show();loginDialog.getWindow().setLayout((int)(context.getResources().getDisplayMetrics().widthPixels*.96),(int)(context.getResources().getDisplayMetrics().heightPixels*.94));loginDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(loginRefresh!=null);loginDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->requestFreshQr(false,false));
            }
            loginPromptText=text;loginText.setText(text);
            if(!qr.isEmpty()&&!qr.equals(lastQr)){qrCreated=SystemClock.elapsedRealtime();loginBitmap=PluginQrImage.create(qr);android.graphics.drawable.BitmapDrawable display=new android.graphics.drawable.BitmapDrawable(context.getResources(),loginBitmap);display.setFilterBitmap(false);loginQr.setImageDrawable(display);if(qrZoom!=null)qrZoom.update(qr);lastQr=qr;closePhoneDialog();}
            boolean camera=PhoneQrLogin.cameraAuthorization(lastQr,text);loginDialog.setTitle(camera?"QQ摄像头扫码授权":"本机相册扫码登录");loginSaveButton.setText(camera?"保存到另一屏幕":loginRefresh==null?"保存二维码":"刷新并保存二维码");loginOpenButton.setText(camera?resources!=null&&resources.qqWebLogin()&&loginRefresh!=null?"改用官网网页登录":"本次授权需要摄像头扫描":(loginRefresh==null?"":"刷新后")+PhoneQrLogin.button(lastQr,text));nativeQrEnabled(!loginQrSaving);
            if(qrAge!=null)main.removeCallbacks(qrAge);qrAge=()->{if(closed||loginDialog==null||!loginDialog.isShowing()||lastQr.isEmpty())return;long age=(SystemClock.elapsedRealtime()-qrCreated)/1000;loginPhoneHint.setText("当前二维码已生成 "+age+" 秒。\n"+(age>=90?"如平台提示失效，返回本页刷新，不要继续扫旧图。\n":"")+PhoneQrLogin.instructions(lastQr,loginPromptText));main.postDelayed(qrAge,1000);};qrAge.run();
            if(saveFreshQr&&!qr.isEmpty()){saveFreshQr=false;main.post(()->saveNativeQr(openFreshQr));}
        }catch(Exception error){Toast.makeText(context,"登录提示失败："+error.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void nativeQrEnabled(boolean enabled){if(loginSaveButton!=null)loginSaveButton.setEnabled(enabled&&loginBitmap!=null);if(loginOpenButton!=null)loginOpenButton.setEnabled(enabled&&loginBitmap!=null&&(!PhoneQrLogin.cameraAuthorization(lastQr,loginPromptText)||resources!=null&&resources.qqWebLogin()&&loginRefresh!=null));}
    private void nativeQrSaved(String payload,String prompt,String request,boolean openApp){
        if(closed||!request.equals(signInRequest)||loginDialog==null||!loginDialog.isShowing())return;
        if(!payload.equals(lastQr)){Toast.makeText(context,"二维码已更新，请保存当前二维码",Toast.LENGTH_LONG).show();return;}
        loginPhoneHint.setText((PhoneQrLogin.cameraAuthorization(payload,prompt)?"图片仅供另一块屏幕展示。\n":"二维码已保存到相册。\n")+PhoneQrLogin.instructions(payload,prompt));
        Toast.makeText(context,PhoneQrLogin.cameraAuthorization(payload,prompt)?"二维码已保存；此授权需摄像头扫描，不能从相册授权":"二维码已保存到相册，登录等待仍在继续",Toast.LENGTH_LONG).show();
        if(openApp){closePhoneDialog();phoneDialog=PhoneQrLogin.open(context,payload,prompt,()->!closed&&request.equals(signInRequest)&&payload.equals(lastQr));}
    }
    private void saveNativeQr(boolean openApp){
        if(loginQrSaving)return;Bitmap current=loginBitmap;if(current==null){Toast.makeText(context,"二维码尚未生成",Toast.LENGTH_SHORT).show();return;}
        String payload=lastQr,prompt=loginPromptText,request=signInRequest;Bitmap copy=current.copy(Bitmap.Config.ARGB_8888,false);if(copy==null)return;
        if(Build.VERSION.SDK_INT<29&&context instanceof MainActivity){((MainActivity)context).saveLoginQrDocument(copy,()->nativeQrSaved(payload,prompt,request,openApp));copy.recycle();return;}
        loginQrSaving=true;nativeQrEnabled(false);if(!submitHost("",()->{
            try{PluginQrImage.save(context,copy);main.post(()->nativeQrSaved(payload,prompt,request,openApp));}catch(Exception error){main.post(()->{if(!closed)Toast.makeText(context,"二维码保存失败："+error.getMessage(),Toast.LENGTH_LONG).show();});}
            finally{copy.recycle();main.post(()->{loginQrSaving=false;nativeQrEnabled(true);});}
        })){copy.recycle();loginQrSaving=false;nativeQrEnabled(true);}
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
        @JavascriptInterface public void complete(String request,boolean ok,String value){main.post(()->{finishSignInPrompt(request);Callback cb=pending.remove(request);if(cb!=null)cb.done(ok,value);});}
        @JavascriptInterface public String typescriptSource(String name){try{return typescript.source(name);}catch(Exception error){throw new IllegalArgumentException(error.getMessage());}}
        @JavascriptInterface public void typescriptComplete(String name,String code,String error){if(typescript!=null)typescript.complete(name,code,error);}
        @JavascriptInterface public String storageGet(String key){return closed?"":storage.getString(key,"");}
        @JavascriptInterface public void storageSet(String key,String value){if(closed)return;storage.edit().putString(key,value).commit();}
        @JavascriptInterface public void storageRemove(String key){if(closed)return;storage.edit().remove(key).commit();}
        @JavascriptInterface public String setting(String key){return context.getSharedPreferences("plugin_settings_"+id,Context.MODE_PRIVATE).getString(key,"");}
        @JavascriptInterface public String md5(String text){try{return hex(MessageDigest.getInstance("MD5").digest(text.getBytes("UTF-8")));}catch(Exception e){throw new IllegalStateException(e);}}
        @JavascriptInterface public String aesEcbEncrypt(String key,String text){try{Cipher c=Cipher.getInstance("AES/ECB/PKCS5Padding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key.getBytes("UTF-8"),"AES"));return hex(c.doFinal(text.getBytes("UTF-8")));}catch(Exception e){throw new IllegalStateException(e);}}
        @JavascriptInterface public void openURL(String address){main.post(()->{if(closed)return;Uri uri=Uri.parse(address);if("https".equals(uri.getScheme())||"http".equals(uri.getScheme()))context.startActivity(new Intent(Intent.ACTION_VIEW,uri).addFlags(context instanceof android.app.Activity?0:Intent.FLAG_ACTIVITY_NEW_TASK));});}
        @JavascriptInterface public void request(String request,String address,String init){try{http.open(request,address,new JSONObject(init));}catch(Exception e){main.post(()->{if(!closed&&web!=null)web.evaluateJavascript("window.__networkResult("+JSONObject.quote(request)+",\"error\","+JSONObject.quote("{\"error\":\"无效网络请求\"}")+");",null);});}}
        @JavascriptInterface public void readResponseChunk(String request){if(http!=null)http.readChunk(request);}
        @JavascriptInterface public void readResponse(String request){if(http!=null)http.read(request);}
        @JavascriptInterface public void cancelResponse(String request){if(http!=null)http.cancel(request);}
        @JavascriptInterface public void signInPrompt(String raw){main.post(()->updateLoginPrompt(raw));}
    }
    private static String architecture(){String abi=Build.SUPPORTED_ABIS[0];if(abi.equals("arm64-v8a"))return "arm64";if(abi.equals("x86_64"))return "amd64";if(abi.startsWith("armeabi"))return "arm";if(abi.equals("x86"))return "386";return abi;}
    private static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte x:bytes)b.append(String.format(Locale.US,"%02x",x&255));return b.toString();}
    private static byte[] readBytes(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>8*1024*1024)throw new IOException("插件响应超过 8 MB");out.write(buffer,0,n);}return out.toByteArray();}
    private static String read(InputStream in)throws IOException{try(InputStream input=in){return new String(readBytes(input),java.nio.charset.StandardCharsets.UTF_8);}}
}
