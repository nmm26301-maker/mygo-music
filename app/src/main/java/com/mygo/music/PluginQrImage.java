package com.mygo.music;

import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.io.*;
import java.util.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;

final class PluginQrImage {
    static Bitmap create(String value)throws Exception {
        Map<EncodeHintType,Object> hints=new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET,"UTF-8");hints.put(EncodeHintType.MARGIN,4);
        BitMatrix matrix=new MultiFormatWriter().encode(value,BarcodeFormat.QR_CODE,640,640,hints);
        int[] pixels=new int[640*640];for(int y=0;y<640;y++)for(int x=0;x<640;x++)pixels[y*640+x]=matrix.get(x,y)?0xff000000:0xffffffff;
        return Bitmap.createBitmap(pixels,640,640,Bitmap.Config.ARGB_8888);
    }
    static Uri save(Context context,Bitmap bitmap)throws IOException {
        if(Build.VERSION.SDK_INT<29)throw new IOException("请使用文件选择器保存二维码");
        ContentResolver resolver=context.getContentResolver();ContentValues values=new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME,"MyGo-login-"+System.currentTimeMillis()+".png");values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/MyGo Music");values.put(MediaStore.Images.Media.IS_PENDING,1);
        Uri uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("无法创建二维码图片");
        try{try(OutputStream out=resolver.openOutputStream(uri)){if(out==null||!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("无法保存二维码图片");}
            ContentValues done=new ContentValues();done.put(MediaStore.Images.Media.IS_PENDING,0);if(resolver.update(uri,done,null,null)!=1)throw new IOException("无法发布二维码图片");return uri;
        }catch(Exception error){resolver.delete(uri,null,null);throw error instanceof IOException?(IOException)error:new IOException(error);}
    }
}
