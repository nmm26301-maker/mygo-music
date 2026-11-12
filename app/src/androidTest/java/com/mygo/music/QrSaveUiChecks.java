package com.mygo.music;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.*;
import android.widget.TextView;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;

/** Click the actual login button, then decode the published gallery image. */
final class QrSaveUiChecks {
    private final Instrumentation test;
    private final Context context;
    QrSaveUiChecks(Instrumentation test){this.test=test;context=test.getTargetContext();}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static View find(View view,String text){if(view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View match=find(group.getChildAt(i),text);if(match!=null)return match;}}return null;}
    private Set<Long> gallery(){Set<Long> ids=new HashSet<>();try(Cursor cursor=context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{MediaStore.Images.Media._ID},MediaStore.Images.Media.DISPLAY_NAME+" LIKE ? AND "+MediaStore.Images.Media.IS_PENDING+"=0",new String[]{"MyGo-login-%"},null)){if(cursor!=null)while(cursor.moveToNext())ids.add(cursor.getLong(0));}return ids;}
    void run()throws Exception{
        String id="qr-save-ui",payload="https://example.com/login?token=qr-save-ui";File directory=new File(context.getCacheDir(),id);directory.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(directory,"plugin.json"))){out.write(("{\"id\":\""+id+"\",\"main\":\"index.js\"}").getBytes("UTF-8"));}
        try(FileOutputStream out=new FileOutputStream(new File(directory,"index.js"))){out.write(("export async function signIn(){host.signInPrompt({text:'保存后从音乐 App 的相册扫码',qr:'"+payload+"'});await new Promise(()=>{});}").getBytes("UTF-8"));}
        Set<Long> before=gallery();Activity activity=test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));PluginRuntime runtime=new PluginRuntime(activity,directory);CountDownLatch completed=new CountDownLatch(1);Uri saved=null;
        try{
            runtime.call("signIn",new org.json.JSONObject(),(ok,raw)->completed.countDown());AlertDialog[] prompt={null};boolean[] clicked={false};long deadline=SystemClock.elapsedRealtime()+10000;
            while(!clicked[0]&&SystemClock.elapsedRealtime()<deadline){test.runOnMainSync(()->{try{prompt[0]=(AlertDialog)field(runtime,"loginDialog");if(prompt[0]!=null&&prompt[0].isShowing()&&field(runtime,"loginBitmap")!=null){View save=find(prompt[0].getWindow().getDecorView(),"保存二维码");check(save!=null,"save QR button missing from actual login UI");save.performClick();clicked[0]=true;}}catch(ReflectiveOperationException e){throw new RuntimeException(e);}catch(Exception e){throw new RuntimeException(e);}});SystemClock.sleep(30);}
            check(clicked[0],"actual login QR never appeared");deadline=SystemClock.elapsedRealtime()+7000;
            while(saved==null&&SystemClock.elapsedRealtime()<deadline){Set<Long> after=gallery();after.removeAll(before);if(!after.isEmpty())saved=ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,after.iterator().next());else SystemClock.sleep(40);}
            check(saved!=null,"save button did not publish a gallery image");
            try(InputStream in=context.getContentResolver().openInputStream(saved)){Bitmap bitmap=BitmapFactory.decodeStream(in);check(bitmap!=null,"saved QR is not a readable PNG");int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());Result result=new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(bitmap.getWidth(),bitmap.getHeight(),pixels))));check(payload.equals(result.getText()),"actual gallery image has a wrong or unscannable QR payload");bitmap.recycle();}
            test.runOnMainSync(()->check(prompt[0].isShowing()&&!runtime.isClosed()&&completed.getCount()==1,"saving QR cancelled the login confirmation wait"));
        }finally{runtime.dispose();if(saved!=null)context.getContentResolver().delete(saved,null,null);test.runOnMainSync(activity::finish);PluginArchive.delete(directory);}
    }
}
