package com.mygo.music;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Same-handset routing, real published image bytes, and pending login during an app switch. */
final class PhoneQrChecks {
    private final Instrumentation test;private final Context context;
    PhoneQrChecks(Instrumentation test){this.test=test;context=test.getTargetContext();}
    interface Ready{boolean get()throws Exception;}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private void await(Ready condition,String message)throws Exception{long end=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<end){boolean[] ready={false};Throwable[] error={null};test.runOnMainSync(()->{try{ready[0]=condition.get();}catch(Throwable failure){error[0]=failure;}});if(error[0]!=null)throw new AssertionError(message,error[0]);if(ready[0])return;SystemClock.sleep(40);}throw new AssertionError(message);}
    private static Object field(Object target,String name)throws Exception{java.lang.reflect.Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}
    private static View text(View view,String value){if(view instanceof TextView&&value.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=text(group.getChildAt(i),value);if(found!=null)return found;}}return null;}
    void run()throws Exception{routing();samePhone();}
    private void routing()throws Exception{
        String[][] cases={{"https://qrlogin.qq.com/fixture","","com.tencent.mobileqq"},{"http://weixin.qq.com/x/fixture","","com.tencent.mm"},{"https://y.qq.com/fixture","请使用手机 QQ 扫码登录","com.tencent.mobileqq"},{"https://music.163.com/login?codekey=fixture","","com.netease.cloudmusic"},{"https://h5.kugou.com/apps/loginQRCode/html/index.html?qrcode=fixture","","com.kugou.android"},{"https://passport.bilibili.com/qrcode/h5/login?oauthKey=fixture","","tv.danmaku.bili"}};
        for(String[] item:cases){List<PhoneQrLogin.App> apps=PhoneQrLogin.apps(item[0],item[1]);check(!apps.isEmpty()&&apps.get(0).packageName.equals(item[2]),"QR routed to the wrong scanner: "+item[2]);Bitmap image=PluginQrImage.create(item[0]);check(PluginQrImage.payload(image).equals(item[0]),"phone scan payload was changed");image.recycle();check(PhoneQrLogin.instructions(item[0],item[1]).contains("相册"),"same-phone instructions missing album selection");}
        check(PhoneQrLogin.apps("https://music.163.com.attacker.invalid/login","").isEmpty(),"scanner identity accepted a misleading hostname");check(PhoneQrLogin.apps("https://unknown.invalid/qr","").isEmpty(),"unknown QR scanner guessed");check(PhoneQrLogin.apps("https://h5.kugou.com/qr","").size()==2,"Kugou Concept option missing");
    }
    private void samePhone()throws Exception{
        String id="same-phone-qr";File directory=new File(context.getCacheDir(),id);directory.mkdirs();context.getSharedPreferences("plugin_data_"+id,0).edit().clear().commit();
        try(FileOutputStream out=new FileOutputStream(new File(directory,"plugin.json"))){out.write(("{\"id\":\""+id+"\",\"main\":\"index.js\"}").getBytes("UTF-8"));}
        try(FileOutputStream out=new FileOutputStream(new File(directory,"index.js"))){out.write("export async function signIn(){host.signInPrompt({qr:'https://music.163.com/login?codekey=same-phone-fixture',text:'请使用网易云音乐 App 扫码登录'});while(!host.storage.get('confirmed'))await new Promise(r=>setTimeout(r,100));const me={id:'same-phone-account'};host.storage.set('account',me);return me;}export function account(){return host.storage.get('account')||null;}".getBytes("UTF-8"));}
        Activity activity=test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));PluginRuntime runtime=new PluginRuntime(activity,directory);CountDownLatch complete=new CountDownLatch(1);boolean[] success={false};String[] result={""};AlertDialog[] login={null};
        try{
            runtime.call("signIn",new JSONObject(),(ok,raw)->{success[0]=ok;result[0]=raw;complete.countDown();});await(()->{login[0]=(AlertDialog)field(runtime,"loginDialog");return login[0]!=null&&login[0].isShowing()&&field(runtime,"loginBitmap")!=null;},"same-phone QR prompt missing");
            test.runOnMainSync(()->{View save=text(login[0].getWindow().getDecorView(),"保存并打开网易云音乐");check(save!=null,"save-and-open correct scanner button missing");save.performClick();});await(()->field(runtime,"phoneDialog")!=null,"missing scanner app did not provide an actionable same-phone path");
            test.runOnMainSync(()->{try{AlertDialog prompt=(AlertDialog)field(runtime,"phoneDialog");check(prompt.isShowing()&&text(prompt.getWindow().getDecorView(),"二维码已保存到相册")!=null,"gallery save did not complete before scanner handoff");check(login[0].isShowing()&&!runtime.isClosed()&&complete.getCount()==1,"handoff cancelled or falsely completed login");prompt.getButton(AlertDialog.BUTTON_POSITIVE).performClick();}catch(Exception failure){throw new RuntimeException(failure);}});
            ArrayList<String> lookedUp=new ArrayList<>();boolean[] launched={false};PhoneQrLogin.Launcher launcher=new PhoneQrLogin.Launcher(){public Intent find(String name){lookedUp.add(name);return new Intent().setComponent(new ComponentName(test.getContext().getPackageName(),PhoneScannerFixtureActivity.class.getName()));}public void start(Intent intent){launched[0]=true;activity.startActivity(intent);}};
            test.runOnMainSync(()->PhoneQrLogin.open(activity,PhoneQrLogin.apps("https://music.163.com/login?codekey=fixture",""),()->true,launcher));await(()->{android.view.accessibility.AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();android.graphics.Rect bounds=new android.graphics.Rect();if(root!=null)root.getBoundsInScreen(bounds);return launched[0]&&root!=null&&test.getContext().getPackageName().contentEquals(root.getPackageName())&&bounds.height()>bounds.width();},"scanner launch did not switch away from the player");check(lookedUp.equals(Collections.singletonList("com.netease.cloudmusic")),"handoff queried unrelated apps");check(!activity.isDestroyed()&&!runtime.isClosed()&&complete.getCount()==1,"portrait scanner switch recreated player or ended login polling");
            context.getSharedPreferences("plugin_data_"+id,0).edit().putString("confirmed","true").commit();check(complete.await(15,TimeUnit.SECONDS)&&success[0]&&new JSONObject(result[0]).getString("id").equals("same-phone-account"),"authorization did not complete while switched to another app: "+result[0]);
            test.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);await(()->activity.hasWindowFocus()&&activity.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE,"return from portrait scanner did not preserve landscape player Activity");test.runOnMainSync(()->check(!login[0].isShowing(),"confirmed same-phone login left QR prompt open"));check(new JSONObject(context.getSharedPreferences("plugin_data_"+id,0).getString("account","{}")).getString("id").equals("same-phone-account"),"same-phone account was not persisted");
            boolean[] staleLaunch={false};test.runOnMainSync(()->PhoneQrLogin.open(activity,PhoneQrLogin.apps("https://qrlogin.qq.com/fixture",""),()->false,new PhoneQrLogin.Launcher(){public Intent find(String name){throw new AssertionError("stale QR queried installed apps");}public void start(Intent intent){staleLaunch[0]=true;}}));check(!staleLaunch[0],"completed QR reopened scanner");
        }finally{runtime.dispose();test.runOnMainSync(activity::finish);PluginArchive.delete(directory);context.getSharedPreferences("plugin_data_"+id,0).edit().clear().commit();}
    }
}
