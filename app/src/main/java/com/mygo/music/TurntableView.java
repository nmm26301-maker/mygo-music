package com.mygo.music;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Owns only its downsampled cover; the platter stops on pause and respects reduced motion. */
final class TurntableView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private Bitmap artwork;private BitmapShader shader;private final Matrix matrix=new Matrix();
    private boolean playing,active=true,motion=true;private long frame;private float angle;
    TurntableView(Context context){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    void setArtwork(Bitmap source){releaseArtwork();if(source!=null&&!source.isRecycled()){float f=Math.min(1,640f/Math.max(source.getWidth(),source.getHeight()));Bitmap scaled=Bitmap.createScaledBitmap(source,Math.max(1,Math.round(source.getWidth()*f)),Math.max(1,Math.round(source.getHeight()*f)),true);artwork=scaled==source?source.copy(Bitmap.Config.ARGB_8888,false):scaled;shader=new BitmapShader(artwork,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);}invalidate();}
    private void advance(){long now=SystemClock.uptimeMillis();if(frame>0&&playing&&active&&motion)angle=(angle+Math.min(80,now-frame)*.012f)%360;frame=now;}
    void setPlaying(boolean value){if(playing==value)return;advance();playing=value;invalidate();}
    void setActive(boolean value){if(active==value)return;advance();active=value;frame=SystemClock.uptimeMillis();invalidate();}
    void setMotion(boolean value){if(motion==value)return;advance();motion=value;invalidate();}
    float angle(){advance();return angle;}
    @Override protected void onDraw(Canvas canvas){
        advance();float side=Math.min(getWidth(),getHeight()),cx=getWidth()*.44f,cy=getHeight()*.53f,r=side*.405f;
        paint.setShader(null);paint.setColor(0xbb080b10);paint.setShadowLayer(side*.025f,0,side*.018f,0x99000000);canvas.drawCircle(cx,cy,r,paint);paint.clearShadowLayer();
        paint.setShader(new LinearGradient(cx-r,cy-r,cx+r,cy+r,new int[]{0xff595b5c,0xff1a1b20,0xff373b3c,0xff10141a},null,Shader.TileMode.CLAMP));canvas.drawCircle(cx,cy,r,paint);paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,side*.002f));for(int i=0;i<8;i++){paint.setColor(i%2==0?0x775c6264:0x77090c11);canvas.drawCircle(cx,cy,r*(.87f+i*.016f),paint);}paint.setStyle(Paint.Style.FILL);
        float face=r*.855f;canvas.save();canvas.rotate(angle,cx,cy);if(shader!=null){float scale=Math.max(face*2/artwork.getWidth(),face*2/artwork.getHeight());matrix.setScale(scale,scale);matrix.postTranslate(cx-artwork.getWidth()*scale/2,cy-artwork.getHeight()*scale/2);shader.setLocalMatrix(matrix);paint.setShader(shader);canvas.drawCircle(cx,cy,face,paint);paint.setShader(null);}else{paint.setShader(new LinearGradient(cx-face,cy-face,cx+face,cy+face,0xff347f83,0xff27364f,Shader.TileMode.CLAMP));canvas.drawCircle(cx,cy,face,paint);paint.setShader(null);paint.setColor(0x448cdddd);canvas.drawCircle(cx+face*.35f,cy-face*.3f,face*.34f,paint);}canvas.restore();
        paint.setColor(0xbbd7dfdf);canvas.drawCircle(cx,cy,r*.026f,paint);paint.setColor(0xff1b2229);canvas.drawCircle(cx,cy,r*.009f,paint);
        float px=getWidth()*.89f,py=getHeight()*.115f;canvas.save();if(!playing)canvas.rotate(-12,px,py);Path arm=new Path();arm.moveTo(px,py);arm.lineTo(px-side*.075f,py+side*.35f);arm.quadTo(px-side*.086f,py+side*.40f,px-side*.135f,py+side*.43f);arm.lineTo(px-side*.19f,py+side*.49f);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(side*.022f);paint.setColor(0x66101419);canvas.save();canvas.translate(side*.012f,side*.012f);canvas.drawPath(arm,paint);canvas.restore();paint.setStrokeWidth(side*.014f);paint.setShader(new LinearGradient(px-side*.22f,py,px,py,0xff3d4147,0xffd4d9db,Shader.TileMode.CLAMP));canvas.drawPath(arm,paint);paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(0xff272c34);canvas.save();canvas.rotate(40,px-side*.19f,py+side*.49f);canvas.drawRoundRect(px-side*.205f,py+side*.46f,px-side*.175f,py+side*.53f,side*.006f,side*.006f,paint);canvas.restore();canvas.restore();
        paint.setShader(new RadialGradient(px-side*.012f,py-side*.016f,side*.055f,new int[]{0xffe0e4e3,0xff747b7c,0xff242a30},null,Shader.TileMode.CLAMP));canvas.drawCircle(px,py,side*.047f,paint);paint.setShader(null);paint.setColor(0xffd2d8d8);canvas.drawCircle(px,py,side*.008f,paint);
        if(playing&&active&&motion&&isShown())postInvalidateOnAnimation();
    }
    private void releaseArtwork(){paint.setShader(null);shader=null;if(artwork!=null){artwork.recycle();artwork=null;}}
    void release(){active=false;playing=false;releaseArtwork();}
}
