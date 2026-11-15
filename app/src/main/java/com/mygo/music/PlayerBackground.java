package com.mygo.music;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.util.concurrent.*;

/** App-private custom photo, bounded decoding and owned textures, independent from source cover identity. */
final class PlayerBackground extends Drawable {
    static final String MODE="player_background_mode",BLUR="player_background_blur",DIM="player_background_dim";
    static File photo(Context c){return new File(c.getFilesDir(),"player-background.jpg");}
    static Bitmap decode(File file)throws IOException{
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),o);if(o.outWidth<=0||o.outHeight<=0)throw new IOException("请选择有效的图片文件");o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>1600)o.inSampleSize*=2;o.inJustDecodeBounds=false;o.inPreferredConfig=Bitmap.Config.ARGB_8888;Bitmap b=BitmapFactory.decodeFile(file.getPath(),o);if(b==null)throw new IOException("无法读取这张图片");
        try{int orientation=new android.media.ExifInterface(file.getPath()).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);Matrix m=new Matrix();switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(270);m.postScale(-1,1);break;case 8:m.setRotate(270);break;}if(!m.isIdentity()){Bitmap rotated=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);if(rotated!=b)b.recycle();b=rotated;}}catch(IOException ignored){}return b;
    }
    static synchronized void importPhoto(Context context,Uri uri)throws IOException{
        File incoming=File.createTempFile("player-photo-",".input",context.getCacheDir()),ready=File.createTempFile("player-photo-",".jpg",context.getFilesDir());Bitmap bitmap=null;
        try{try(InputStream in=context.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(incoming)){if(in==null)throw new IOException("无法打开选中的图片");byte[] bytes=new byte[16384];int n;long count=0;while((n=in.read(bytes))!=-1){count+=n;if(count>32L*1024*1024)throw new IOException("请选择小于 32 MB 的图片");out.write(bytes,0,n);}}bitmap=decode(incoming);try(FileOutputStream out=new FileOutputStream(ready)){if(!bitmap.compress(Bitmap.CompressFormat.JPEG,94,out))throw new IOException("图片保存失败");out.getFD().sync();}if(!ready.renameTo(photo(context)))throw new IOException("无法保存播放背景");context.getSharedPreferences("library",0).edit().putInt(MODE,1).commit();}finally{incoming.delete();ready.delete();if(bitmap!=null)bitmap.recycle();}
    }
    private final Context context;private final SharedPreferences prefs;private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService worker=Executors.newSingleThreadExecutor();private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);private final RectF target=new RectF();
    private Bitmap original,texture;private boolean closed;private int token,mode=-1,blur=-1,dim;private Bitmap follow;private String message="";
    PlayerBackground(Context c){context=c.getApplicationContext();prefs=c.getSharedPreferences("library",0);}
    boolean ready(){return texture!=null&&!texture.isRecycled();}
    int mode(){return mode;}String message(){return message;}
    void update(Bitmap art,boolean force){int next=LyricsAppearance.clamp(prefs.getInt(MODE,0),0,2);dim=LyricsAppearance.clamp(prefs.getInt(DIM,44),15,85);int nextBlur=LyricsAppearance.clamp(prefs.getInt(BLUR,18),0,100);boolean reload=force||mode!=next||(next==0&&follow!=art);follow=art;mode=next;if(reload){int request=++token;releaseSource();message="";if(mode==0&&art!=null&&!art.isRecycled()){float f=Math.min(1,1024f/Math.max(art.getWidth(),art.getHeight()));Bitmap scaled=Bitmap.createScaledBitmap(art,Math.max(1,Math.round(art.getWidth()*f)),Math.max(1,Math.round(art.getHeight()*f)),true);original=scaled==art?art.copy(Bitmap.Config.ARGB_8888,false):scaled;}else if(mode==1){worker.execute(()->{Bitmap loaded=null;String error="";try{loaded=decode(photo(context));}catch(Exception failure){error="自定义图片不可用，请重新选择";}Bitmap result=loaded;String failure=error;main.post(()->{if(closed||request!=token){if(result!=null)result.recycle();return;}releaseSource();original=result;message=failure;makeTexture();invalidateSelf();});});}}
        if(reload||nextBlur!=blur){blur=nextBlur;makeTexture();}invalidateSelf();}
    private void makeTexture(){if(texture!=null&&texture!=original)texture.recycle();texture=null;if(original==null)return;if(blur==0){texture=original;return;}float limit=Math.max(16,1024f/(1+blur*.34f)),f=Math.min(1,limit/Math.max(original.getWidth(),original.getHeight()));texture=Bitmap.createScaledBitmap(original,Math.max(1,Math.round(original.getWidth()*f)),Math.max(1,Math.round(original.getHeight()*f)),true);}
    @Override public void draw(Canvas canvas){Rect b=getBounds();paint.setShader(null);paint.setAlpha(255);canvas.drawColor(0xff17252d);if(mode!=2&&texture!=null&&!texture.isRecycled()){float scale=Math.max(b.width()/(float)texture.getWidth(),b.height()/(float)texture.getHeight());float w=texture.getWidth()*scale,h=texture.getHeight()*scale;target.set(b.centerX()-w/2,b.centerY()-h/2,b.centerX()+w/2,b.centerY()+h/2);canvas.drawBitmap(texture,null,target,paint);}else{paint.setShader(new LinearGradient(0,0,b.width(),b.height(),0xff294b56,0xff131b2c,Shader.TileMode.CLAMP));canvas.drawRect(b,paint);paint.setShader(null);}paint.setColor(Color.argb(dim*255/100,5,12,20));canvas.drawRect(b,paint);paint.setShader(new LinearGradient(0,0,0,b.height(),new int[]{0x44101822,0x00101822,0x77101822},null,Shader.TileMode.CLAMP));canvas.drawRect(b,paint);paint.setShader(null);}
    private void releaseSource(){if(texture!=null&&texture!=original)texture.recycle();texture=null;if(original!=null)original.recycle();original=null;}
    void release(){closed=true;token++;worker.shutdownNow();releaseSource();follow=null;}
    @Override public void setAlpha(int alpha){}@Override public void setColorFilter(ColorFilter filter){}@Override public int getOpacity(){return PixelFormat.OPAQUE;}
}
