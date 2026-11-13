package com.mygo.music;

/**
 * Normal-playback stiffness/damping policy ported from AMLL's getPosYSpringPolicy,
 * commit 832bad5f16ec2a7f96d06707ef927c9a4d3713d3, AGPL-3.0-only.
 * See third-party/AMLL-NOTICE.txt and the unchanged TypeScript in third-party/amll.
 * The closed-form native sampler and word envelope are Android-specific code.
 */
final class LyricsExpressiveMotion {
    static double stiffness(long interval){
        double clamped=Math.max(100,Math.min(800,interval));
        double ratio=Math.pow(1-(clamped-100)/700,.2);
        return 170+ratio*50;
    }
    static double damping(long interval){return Math.sqrt(stiffness(interval))*2.2;}
    /** Rest-to-rest damped oscillator sampled from song time, never wall-clock frames. */
    static float remaining(long elapsed,long interval){
        if(elapsed<=0)return 1;if(elapsed>=1400)return 0;
        double a=damping(interval)/2,root=Math.sqrt(a*a-stiffness(interval));
        double r1=-a+root,r2=-a-root,t=elapsed/1000.0;
        return (float)Math.max(0,Math.min(1,(-r2*Math.exp(r1*t)+r1*Math.exp(r2*t))/(r1-r2)));
    }
    static float cascade(long elapsed,long interval,int row){return remaining(elapsed-Math.min(4,Math.max(0,row))*42L,interval);}
    static float tilt(long position,long start,long end){if(position<=start||position>=end+240)return 0;double p=(position-start)/(double)Math.max(1,end-start+240);return (float)(3.5*Math.sin(Math.PI*p));}
    static float breathe(long position,long start,long end){if(end<=start||position<=start||position>=end)return 0;double p=(position-start)/(double)(end-start);return (float)Math.pow(Math.sin(Math.PI*p),2);}
    /** Duration emphasis, not a pitch/beat detector. Apply only to source-timed short words. */
    static float wave(long position,long start,long end,float characterPhase){
        if(end-start<1000||position<=start||position>=end)return 0;
        double p=(position-start)/(double)(end-start);
        return (float)(Math.sin(Math.PI*p)*(.75+.25*Math.sin(Math.PI*2*p-characterPhase*Math.PI*1.2)));
    }
}
