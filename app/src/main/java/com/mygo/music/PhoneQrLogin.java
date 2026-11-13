package com.mygo.music;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.widget.Toast;
import java.util.*;

/** Identify the scanning account app, rather than the music source's display name. */
final class PhoneQrLogin {
    static final class App {
        final String name,packageName;
        App(String name,String packageName){this.name=name;this.packageName=packageName;}
    }
    private static final App QQ=new App("手机 QQ","com.tencent.mobileqq"),WECHAT=new App("微信","com.tencent.mm"),NETEASE=new App("网易云音乐","com.netease.cloudmusic"),KUGOU=new App("酷狗音乐","com.kugou.android"),KUGOU_LITE=new App("酷狗概念版","com.kugou.android.lite"),QQMUSIC=new App("QQ 音乐","com.tencent.qqmusic"),BILI=new App("哔哩哔哩","tv.danmaku.bili");
    private static boolean domain(String host,String domain){return host.equals(domain)||host.endsWith("."+domain);}
    static List<App> apps(String payload,String prompt){
        Uri uri=Uri.parse(payload==null?"":payload);String host=uri.getHost()==null?"":uri.getHost().toLowerCase(Locale.ROOT),scheme=uri.getScheme()==null?"":uri.getScheme().toLowerCase(Locale.ROOT);String text=prompt==null?"":prompt.toLowerCase(Locale.ROOT);
        if(domain(host,"weixin.qq.com")||domain(host,"wechat.com")||scheme.equals("weixin"))return Collections.singletonList(WECHAT);
        if(domain(host,"ptlogin2.qq.com")||domain(host,"qrlogin.qq.com")||domain(host,"qm.qq.com")||domain(host,"qu.qq.com")||scheme.equals("mqq")||scheme.equals("mqqapi"))return Collections.singletonList(QQ);
        if(domain(host,"music.163.com")||scheme.equals("orpheus"))return Collections.singletonList(NETEASE);
        if(domain(host,"kugou.com")||scheme.equals("kugou"))return Arrays.asList(KUGOU,KUGOU_LITE);
        if(domain(host,"bilibili.com")||domain(host,"b23.tv")||scheme.equals("bilibili"))return Collections.singletonList(BILI);
        if(text.contains("微信")||text.contains("wechat"))return Collections.singletonList(WECHAT);
        if(text.contains("手机 qq")||text.contains("qq 扫")||text.contains("qq扫码")||text.contains("mobile qq"))return Collections.singletonList(QQ);
        if(domain(host,"y.qq.com")||scheme.equals("qqmusic"))return Collections.singletonList(QQMUSIC);
        return Collections.emptyList();
    }
    static String names(List<App> apps){StringJoiner names=new StringJoiner("或");for(App app:apps)names.add(app.name);return names.toString();}
    static String button(String payload,String prompt){List<App> apps=apps(payload,prompt);return apps.isEmpty()?"保存并查看本机扫码步骤":"保存并打开"+names(apps);}
    static boolean cameraAuthorization(String payload,String prompt){List<App> candidates=apps(payload,prompt);return candidates.size()==1&&candidates.get(0).packageName.equals(QQ.packageName);}
    static String cameraInstructions(){return "QQ授权可能被平台限制为摄像头扫码。相册识别或长按识别被拒绝后，保存图片和刷新二维码都不能解除该限制。请改用官网提供的其他登录方式；若继续扫码，需要另一块屏幕展示二维码。";}
    static String instructions(String payload,String prompt){if(cameraAuthorization(payload,prompt))return cameraInstructions();List<App> apps=apps(payload,prompt);String target=apps.isEmpty()?"二维码要求的 App":names(apps);return "在这一部手机完成登录\n1. 保存当前二维码到相册。\n2. 打开"+target+"，进入扫一扫，在相册中选择刚保存的图片。\n3. 在该应用确认登录，再返回 MyGo Music，本页会自动更新。";}
    interface Current {boolean valid();}
    interface Launcher {Intent find(String packageName);void start(Intent intent);}
    static Launcher launcher(Context context){return new Launcher(){public Intent find(String name){return context.getPackageManager().getLaunchIntentForPackage(name);}public void start(Intent intent){context.startActivity(intent.addFlags(context instanceof Activity?0:Intent.FLAG_ACTIVITY_NEW_TASK));}};}
    static AlertDialog open(Context context,String payload,String prompt,Current current){if(!current.valid())return null;if(cameraAuthorization(payload,prompt)){if(context instanceof Activity)return new AlertDialog.Builder(context).setTitle("本次授权不能依靠相册扫码").setMessage(cameraInstructions()).setPositiveButton("知道了",null).show();Toast.makeText(context,cameraInstructions(),Toast.LENGTH_LONG).show();return null;}return open(context,apps(payload,prompt),current,launcher(context));}
    static AlertDialog open(Context context,List<App> requested,Current current,Launcher launcher){
        if(!current.valid())return null;ArrayList<App> available=new ArrayList<>();ArrayList<Intent> intents=new ArrayList<>();
        for(App app:requested){Intent intent=launcher.find(app.packageName);if(intent!=null){available.add(app);intents.add(intent);}}
        if(available.size()==1){launch(context,intents.get(0),available.get(0).name,current,launcher);return null;}
        if(!(context instanceof Activity)){Toast.makeText(context,"二维码已保存，请在本机扫码应用的扫一扫中从相册选择",Toast.LENGTH_LONG).show();return null;}
        if(available.isEmpty())return new AlertDialog.Builder(context).setTitle("二维码已保存到相册").setMessage(requested.isEmpty()?"请切换到二维码要求的应用，在扫一扫中从相册选择刚保存的二维码，确认后返回播放器。":"本机未找到"+names(requested)+"。请打开或安装对应应用，在扫一扫中从相册选择刚保存的二维码。登录等待仍在继续。").setPositiveButton("继续等待登录",null).show();
        String[] names=new String[available.size()];for(int i=0;i<names.length;i++)names[i]=available.get(i).name;
        return new AlertDialog.Builder(context).setTitle("选择本机扫码应用").setItems(names,(dialog,index)->launch(context,intents.get(index),available.get(index).name,current,launcher)).setNegativeButton("继续等待登录",null).show();
    }
    private static void launch(Context context,Intent intent,String name,Current current,Launcher launcher){
        if(!current.valid())return;try{launcher.start(intent);Toast.makeText(context,"在"+name+"的扫一扫中选择相册里的二维码，确认后返回 MyGo Music",Toast.LENGTH_LONG).show();}catch(ActivityNotFoundException|SecurityException error){Toast.makeText(context,"二维码已保存，无法打开"+name+"，请手动打开并从相册扫码",Toast.LENGTH_LONG).show();}
    }
}
