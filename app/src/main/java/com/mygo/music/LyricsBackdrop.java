package com.mygo.music;

import android.graphics.*;

/** Native album-color background. Small owned artwork texture and cached gradients, no WebGL runtime. */
final class LyricsBackdrop {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final int[] colors={0xff4c427b,0xff204c63,0xff693e61};
    private final Shader[] gradients=new Shader[3];
    private Bitmap texture;private int width,height;int accent=Color.WHITE;
    void artwork(Bitmap artwork){
        if(texture!=null){texture.recycle();texture=null;}accent=Color.WHITE;
        colors[0]=0xff4c427b;colors[1]=0xff204c63;colors[2]=0xff693e61;
        if(artwork!=null&&!artwork.isRecycled()){
            Bitmap sampled=Bitmap.createScaledBitmap(artwork,16,16,true);if(sampled==artwork)sampled=sampled.copy(Bitmap.Config.ARGB_8888,false);texture=Bitmap.createScaledBitmap(sampled,8,8,true);if(texture==sampled)texture=sampled.copy(Bitmap.Config.ARGB_8888,false);
            float[][] bins=new float[12][4];float[] hsv=new float[3];
            for(int y=0;y<16;y++)for(int x=0;x<16;x++){int color=sampled.getPixel(x,y);Color.colorToHSV(color,hsv);if(Color.alpha(color)<128||hsv[2]<.12f)continue;int bin=Math.min(11,(int)(hsv[0]/30));float weight=.15f+hsv[1];bins[bin][0]+=weight;bins[bin][1]+=hsv[0]*weight;bins[bin][2]+=hsv[1]*weight;bins[bin][3]+=hsv[2]*weight;}
            for(int i=0;i<3;i++){int best=-1;for(int j=0;j<12;j++)if(bins[j][0]>0&&(best<0||bins[j][0]>bins[best][0]))best=j;if(best<0)break;float weight=bins[best][0],hue=bins[best][1]/weight,saturation=bins[best][2]/weight;colors[i]=Color.HSVToColor(new float[]{hue,Math.max(.28f,Math.min(.65f,saturation)),.42f});if(i==0)accent=Color.HSVToColor(new float[]{hue,Math.max(.12f,Math.min(.32f,saturation)),1f});bins[best][0]=0;bins[(best+1)%12][0]*=.1f;bins[(best+11)%12][0]*=.1f;}
            sampled.recycle();
        }
        resize(width,height);
    }
    void resize(int width,int height){this.width=width;this.height=height;float radius=Math.max(1,Math.max(width,height)*.83f);for(int i=0;i<3;i++)gradients[i]=new RadialGradient(0,0,radius,new int[]{colors[i],colors[i]&0x00ffffff},null,Shader.TileMode.CLAMP);}
    void draw(Canvas canvas,long position,LyricsAppearance options,int width,int height){
        if(width!=this.width||height!=this.height)resize(width,height);
        canvas.drawColor(0xff11131b);if(options.background==2)return;
        if(texture!=null){paint.setShader(null);paint.setAlpha(80);canvas.drawBitmap(texture,null,new Rect(0,0,width,height),paint);paint.setAlpha(255);}
        float phase=options.background==0&&!options.quiet()?Math.max(0,position)/18000f:0;
        for(int i=0;i<3;i++){float angle=phase+i*2.094f;float x=width*(.5f+.34f*(float)Math.cos(angle)),y=height*(.5f+.42f*(float)Math.sin(angle*.83f+i));canvas.save();canvas.translate(x,y);paint.setShader(gradients[i]);paint.setAlpha(185);canvas.drawRect(-x,-y,width-x,height-y,paint);canvas.restore();}
        paint.setShader(null);paint.setColor(0x66101420);paint.setAlpha(102);canvas.drawRect(0,0,width,height,paint);paint.setAlpha(255);
    }
    void release(){if(texture!=null){texture.recycle();texture=null;}}
}
