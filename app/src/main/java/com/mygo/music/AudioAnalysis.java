package com.mygo.music;

/** Measured output only; unavailable audio never produces synthetic bars or duplicated stereo. */
final class AudioAnalysis {
    static final class Frame {
        final float[] left,right,bands;final int rate;final boolean stereo;final long time;
        final float rmsLeft,rmsRight,peakLeft,peakRight;
        Frame(float[] l,float[] r,float[] b,int rate,boolean stereo,long time,float rl,float rr,float pl,float pr){left=l;right=r;bands=b;this.rate=rate;this.stereo=stereo;this.time=time;rmsLeft=rl;rmsRight=rr;peakLeft=pl;peakRight=pr;}
    }
    static final Frame EMPTY=new Frame(new float[256],new float[0],new float[48],48000,false,0,0,0,0,0);
    static volatile Frame measured=EMPTY;static volatile String status="尚未开启音频分析";
    static volatile boolean visible,playing,remote,authorized;
    static Frame frame(){Frame f=measured;return f.time>0&&System.nanoTime()-f.time<700000000L?f:EMPTY;}
    static void clear(String message){measured=EMPTY;status=message;}
    static void demand(boolean foreground,boolean audible,boolean device){visible=foreground;playing=audible;remote=device;PlayerAudioCaptureService.changed();}
}
