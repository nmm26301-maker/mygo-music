package com.mygo.music;

import android.graphics.*;

/** Bounded native decorations sampled solely from song timestamps. No random particles or beat guesses. */
final class LyricsStageEffects {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Camera camera=new Camera();
    private final Matrix matrix=new Matrix();
    static float unit(long position,long start,long end){return end<=start?1:Math.max(0,Math.min(1,(position-start)/(float)(end-start)));}
    static float entrance(long position,long start,long end){
        long duration=Math.min(700,Math.max(260,(end-start)/3));float p=unit(position,start,start+duration);return (1-p)*(1-p)*(1-p);
    }
    static float handoff(long position,long start,long next){
        long duration=Math.min(700,Math.max(200,(next-start)/4));float p=unit(position,next-duration,next);return p*p*(3-2*p);
    }
    void page(Canvas canvas,float width,float height,float amount,float density){
        if(amount<=0)return;
        camera.save();camera.rotateX(-32*amount);camera.getMatrix(matrix);camera.restore();
        matrix.preTranslate(-width/2,-height/2);matrix.postTranslate(width/2,height/2);
        canvas.translate(0,14*density*amount);canvas.concat(matrix);
    }
    void meteor(Canvas canvas,float x,float baseline,float size,int color,float progress,float strength){
        float y=baseline+size*.20f,radius=size*.045f*strength,length=size*.75f*strength;
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(Math.max(1,size*.025f));paint.setColor(color);
        for(int i=7;i>=0;i--){paint.setAlpha((int)(160*(1-i/8f)));float from=x-length*(i+1)/8,to=x-length*i/8;canvas.drawLine(from,y,to,y,paint);}
        paint.setStyle(Paint.Style.FILL);paint.setAlpha(210);canvas.drawCircle(x,y,Math.max(.5f,radius),paint);
        paint.setAlpha((int)(100*(1-progress)));canvas.drawCircle(x-size*.22f,y-size*.09f,size*.018f*strength,paint);paint.setAlpha(255);
    }
    void ripple(Canvas canvas,float x,float baseline,float size,int color,float progress,float strength){
        if(progress<=0||progress>=1)return;
        paint.setStyle(Paint.Style.STROKE);paint.setColor(color);paint.setStrokeWidth(Math.max(1,size*.022f));
        for(int i=0;i<2;i++){float p=progress-i*.18f;if(p<=0)continue;float radius=size*(.10f+.70f*p)*strength;paint.setAlpha((int)(115*(1-p)));canvas.drawCircle(x,baseline-size*.35f,radius,paint);}
        paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);
    }
}
