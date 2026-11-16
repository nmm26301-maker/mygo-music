package com.mygo.music;
import android.content.Context;
import android.graphics.*;
import android.widget.SeekBar;
/** All styles retain native seek/buffer behavior; the waveform uses measured samples only. */
final class ThemeSeekBar extends SeekBar {
 private final Paint paint=new Paint(3);int style,color=0xffa8ded9;boolean active;private int frameIntervalMs;
 ThemeSeekBar(Context c){super(c);setLayerType(LAYER_TYPE_SOFTWARE,null);}
 void rendering(boolean quiet,boolean lowPerformance){frameIntervalMs=lowPerformance?66:quiet?80:0;invalidate();}
 @Override protected void onDraw(Canvas c){if(style==2){AudioAnalysis.Frame f=AudioAnalysis.frame();if(f.time>0&&f.left.length>1){paint.setColor(color);paint.setAlpha(65);paint.setStrokeWidth(1);float width=getWidth()-getPaddingLeft()-getPaddingRight(),y=getHeight()/2f;Path p=new Path();for(int i=0;i<f.left.length;i++){float x=getPaddingLeft()+width*i/(f.left.length-1),py=y-f.left[i]*getHeight()*.42f;if(i==0)p.moveTo(x,py);else p.lineTo(x,py);}paint.setStyle(Paint.Style.STROKE);c.drawPath(p,paint);paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);}}super.onDraw(c);if(style==1){paint.setColor(color);paint.setAlpha(90);paint.setStrokeWidth(2);paint.setShadowLayer(6,0,0,color);float w=getWidth()-getPaddingLeft()-getPaddingRight();c.drawLine(getPaddingLeft(),getHeight()/2f,getPaddingLeft()+w*getProgress()/Math.max(1,getMax()),getHeight()/2f,paint);paint.clearShadowLayer();paint.setAlpha(255);}if(active&&style==2&&AudioAnalysis.frame().time>0){if(frameIntervalMs>0)postInvalidateDelayed(frameIntervalMs);else postInvalidateOnAnimation();}}
}
