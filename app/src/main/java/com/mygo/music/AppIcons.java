package com.mygo.music;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

/** Local vector controls: independent of emoji fonts, network images and device glyph coverage. */
final class AppIcons extends Drawable {
    final String kind;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final boolean filled;
    AppIcons(String kind,int color,boolean filled){this.kind=kind;this.filled=filled;paint.setColor(color);paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);}
    static void apply(TextView view,String kind){apply(view,kind,false);}
    static void apply(TextView view,String kind,boolean filled){int size=(int)(20*view.getResources().getDisplayMetrics().density+.5f);AppIcons icon=new AppIcons(kind,view.getCurrentTextColor(),filled);icon.setBounds(0,0,size,size);view.setCompoundDrawablePadding((int)(6*view.getResources().getDisplayMetrics().density));view.setCompoundDrawables(icon,null,null,null);}
    void line(Canvas c,float a,float b,float x,float y){c.drawLine(a,b,x,y,paint);}
    @Override public void draw(Canvas canvas){int saved=canvas.save();Rect b=getBounds();canvas.translate(b.left,b.top);canvas.scale(b.width()/24f,b.height()/24f);paint.setStyle(Paint.Style.STROKE);Path p=new Path();
        switch(kind){
        case "home":p.moveTo(3,10);p.lineTo(12,3);p.lineTo(21,10);p.moveTo(5,9);p.lineTo(5,21);p.lineTo(10,21);p.lineTo(10,14);p.lineTo(14,14);p.lineTo(14,21);p.lineTo(19,21);p.lineTo(19,9);canvas.drawPath(p,paint);break;
        case "heart":p.moveTo(12,21);p.cubicTo(6,17,2,13,3,8);p.cubicTo(4,3,9,3,12,7);p.cubicTo(15,3,20,3,21,8);p.cubicTo(22,13,18,17,12,21);paint.setStyle(filled?Paint.Style.FILL:Paint.Style.STROKE);canvas.drawPath(p,paint);break;
        case "user":canvas.drawCircle(12,7,4,paint);p.moveTo(4,21);p.cubicTo(4,12,20,12,20,21);canvas.drawPath(p,paint);break;
        case "download":line(canvas,12,3,12,15);line(canvas,7,10,12,15);line(canvas,12,15,17,10);p.moveTo(4,16);p.lineTo(4,21);p.lineTo(20,21);p.lineTo(20,16);canvas.drawPath(p,paint);break;
        case "plug":canvas.drawRoundRect(6,7,18,17,3,3,paint);line(canvas,9,3,9,7);line(canvas,15,3,15,7);line(canvas,12,17,12,22);break;
        case "add":line(canvas,12,4,12,20);line(canvas,4,12,20,12);break;
        case "previous":case "next":boolean previous=kind.equals("previous");if(previous){canvas.translate(24,0);canvas.scale(-1,1);}line(canvas,19,5,19,19);p.moveTo(5,5);p.lineTo(15,12);p.lineTo(5,19);p.close();canvas.drawPath(p,paint);break;
        case "search":canvas.drawCircle(10,10,6,paint);line(canvas,15,15,21,21);break;
        case "music":case "album":line(canvas,10,5,10,17);line(canvas,10,5,20,3);line(canvas,20,3,20,15);canvas.drawOval(4,15,10,20,paint);canvas.drawOval(14,13,20,18,paint);break;
        case "queue":case "playlist":case "chart":for(int i=0;i<3;i++){canvas.drawCircle(4,6+i*6,.6f,paint);line(canvas,8,6+i*6,20,6+i*6);}break;
        default:canvas.drawRoundRect(4,4,20,20,3,3,paint);line(canvas,8,9,16,9);line(canvas,8,14,16,14);break;
        }canvas.restoreToCount(saved);
    }
    @Override public void setAlpha(int value){paint.setAlpha(value);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
