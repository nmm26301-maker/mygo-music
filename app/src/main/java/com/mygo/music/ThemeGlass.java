package com.mygo.music;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** Cheap frosted background-only texture, never a blur of controls or text. */
final class ThemeGlass extends Drawable {
 private Bitmap image;private final Paint paint=new Paint(3);private final RectF rect=new RectF();private final float radius;
 ThemeGlass(Drawable background,int width,int height,float radius){this.radius=radius;int w=Math.max(1,Math.min(160,width/4)),h=Math.max(1,Math.min(100,height/4));image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(image);c.scale(w/(float)Math.max(1,width),h/(float)Math.max(1,height));background.draw(c);}
 @Override public void draw(Canvas c){rect.set(getBounds());Path clip=new Path();clip.addRoundRect(rect,radius,radius,Path.Direction.CW);c.save();c.clipPath(clip);if(image!=null)c.drawBitmap(image,null,rect,paint);paint.setColor(0xc82a3e49);c.drawRect(rect,paint);paint.setShader(new LinearGradient(rect.left,rect.top,rect.right,rect.bottom,0x33ffffff,0x052d7a8a,Shader.TileMode.CLAMP));c.drawRect(rect,paint);paint.setShader(null);c.restore();paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(0x66d4eef5);c.drawRoundRect(rect,radius,radius,paint);paint.setStyle(Paint.Style.FILL);}
 void release(){if(image!=null){image.recycle();image=null;}}@Override public void setAlpha(int a){}@Override public void setColorFilter(ColorFilter f){}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
