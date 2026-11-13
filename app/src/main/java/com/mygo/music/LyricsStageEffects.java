package com.mygo.music;

import android.graphics.*;

/** Bounded native decorations sampled solely from song timestamps. No random particles or beat guesses. */
final class LyricsStageEffects {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Camera camera=new Camera();
    private final Matrix matrix=new Matrix();
    private Shader halo,tail;private int shaderColor;private boolean shadersReady;
    private void colors(int color){
        if(shadersReady&&shaderColor==color)return;shaderColor=color;shadersReady=true;
        int transparent=color&0x00ffffff;
        halo=new RadialGradient(0,0,1,new int[]{color,(color&0x00ffffff)|0x50000000,transparent},new float[]{0,.45f,1},Shader.TileMode.CLAMP);
        tail=new LinearGradient(-1,0,0,0,new int[]{transparent,(color&0x00ffffff)|0x70000000,color},new float[]{0,.70f,1},Shader.TileMode.CLAMP);
    }
    private void glow(Canvas canvas,float x,float y,float radius,int alpha){
        if(radius<=0||alpha<=0)return;canvas.save();canvas.translate(x,y);canvas.scale(radius,radius);paint.setStyle(Paint.Style.FILL);paint.setShader(halo);paint.setAlpha(alpha);canvas.drawCircle(0,0,1,paint);canvas.restore();paint.setShader(null);paint.setAlpha(255);
    }
    static float edgeFade(float center,float block,float height){
        float margin=block*.30f,band=Math.max(1,Math.min(height*.14f,block*.8f));float p=Math.max(0,Math.min(1,Math.min(center+margin,height-center+margin)/band));return p*p*(3-2*p);
    }
    static float unit(long position,long start,long end){return end<=start?1:Math.max(0,Math.min(1,(position-start)/(float)(end-start)));}
    static float entrance(long position,long start,long end){
        long duration=Math.min(700,Math.max(260,(end-start)/3));float p=unit(position,start,start+duration);return (1-p)*(1-p)*(1-p);
    }
    static float handoff(long position,long start,long next,float strength){
        long duration=Math.round(Math.min(700,Math.max(200,(next-start)/4))*Math.max(.2f,Math.min(1.4f,strength)));float p=unit(position,next-duration,next);return p*p*(3-2*p);
    }
    void page(Canvas canvas,float width,float height,float amount,float density){
        if(amount<=0)return;
        camera.save();camera.setLocation(0,0,-8*Math.max(1,density));camera.rotateX(-24*amount);camera.getMatrix(matrix);camera.restore();
        matrix.preTranslate(-width/2,-height/2);matrix.postTranslate(width/2,height/2);
        canvas.translate(0,10*density*amount);canvas.concat(matrix);
    }
    void meteor(Canvas canvas,float x,float baseline,float size,int color,float strength){
        colors(color);float y=baseline+size*.21f,radius=size*.047f*strength,length=size*.95f*strength,width=Math.max(1,size*.033f*strength);
        glow(canvas,x,y,size*.20f*strength,85);
        canvas.save();canvas.translate(x,y);canvas.scale(Math.max(1,length),1);paint.setStyle(Paint.Style.FILL);paint.setShader(tail);paint.setAlpha(200);canvas.drawRect(-1,-width/2,0,width/2,paint);canvas.restore();
        paint.setShader(null);paint.setColor(color);paint.setAlpha(240);canvas.drawCircle(x,y,Math.max(.6f,radius),paint);paint.setAlpha(255);
    }
    void ripple(Canvas canvas,float x,float baseline,float size,int color,float progress,float strength){
        if(progress<=0||progress>=1)return;colors(color);
        float envelope=Math.min(1,progress/.15f)*(float)Math.pow(1-progress,1.5);
        glow(canvas,x,baseline-size*.35f,size*.50f*strength,(int)(30*envelope));
        paint.setStyle(Paint.Style.STROKE);paint.setColor(color);paint.setStrokeWidth(Math.max(.8f,size*.018f));
        for(int i=0;i<2;i++){
            float p=i==0?progress:(progress-.18f)/.82f;if(p<=0||p>=1)continue;
            float eased=1-(1-p)*(1-p),radius=size*(.08f+.85f*eased)*strength;
            float fade=Math.min(1,p/.15f)*(float)Math.pow(1-p,1.5);paint.setAlpha((int)(145*fade));canvas.drawCircle(x,baseline-size*.35f,radius,paint);
        }
        paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);
    }
}
