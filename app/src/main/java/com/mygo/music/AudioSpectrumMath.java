package com.mygo.music;

/** Windowed radix-2 FFT of genuine interleaved PCM; no playback clock or random source enters it. */
final class AudioSpectrumMath {
    static final int N=2048;private final double[] real=new double[N],imag=new double[N];
    private final float[] left=new float[N],right=new float[N];private int used;
    AudioAnalysis.Frame append(short[] input,int count,int rate){AudioAnalysis.Frame result=null;for(int i=0;i+1<count;i+=2){left[used]=input[i]/32768f;right[used]=input[i+1]/32768f;if(++used==N){used=0;result=analyze(rate);}}return result;}
    void reset(){used=0;}
    private AudioAnalysis.Frame analyze(int rate){float[] l=new float[256],r=new float[256];double a=0,b=0;for(int i=0;i<N;i++){a+=left[i]*left[i];b+=right[i]*right[i];if(i%8==0){l[i/8]=left[i];r[i/8]=right[i];}}float[] lm=fft(left),rm=fft(right),bands=new float[48];for(int i=0;i<48;i++){double low=25*Math.pow(800,i/48.0),high=25*Math.pow(800,(i+1)/48.0);int from=Math.max(1,(int)(low*N/rate)),to=Math.min(N/2-1,Math.max(from,(int)(high*N/rate)));float peak=0;for(int k=from;k<=to;k++)peak=Math.max(peak,Math.max(lm[k],rm[k]));bands[i]=(float)Math.max(0,Math.min(1,(20*Math.log10(Math.max(1e-8,peak))+75)/65));}return new AudioAnalysis.Frame(l,r,bands,rate,true,System.nanoTime(),(float)Math.sqrt(a/N),(float)Math.sqrt(b/N),peak(lm,rate),peak(rm,rate));}
    private float peak(float[] data,int rate){int best=1;for(int i=2;i<data.length;i++)if(data[i]>data[best])best=i;return data[best]<.0001f?0:best*rate/(float)N;}
    private float[] fft(float[] input){for(int i=0;i<N;i++){real[i]=input[i]*(.5-.5*Math.cos(2*Math.PI*i/(N-1)));imag[i]=0;}for(int i=1,j=0;i<N;i++){int bit=N>>1;for(;(j&bit)!=0;bit>>=1)j^=bit;j^=bit;if(i<j){double t=real[i];real[i]=real[j];real[j]=t;}}for(int size=2;size<=N;size<<=1)for(int start=0;start<N;start+=size)for(int i=0;i<size/2;i++){double angle=-2*Math.PI*i/size,c=Math.cos(angle),s=Math.sin(angle),r=real[start+i+size/2]*c-imag[start+i+size/2]*s,m=real[start+i+size/2]*s+imag[start+i+size/2]*c;real[start+i+size/2]=real[start+i]-r;imag[start+i+size/2]=imag[start+i]-m;real[start+i]+=r;imag[start+i]+=m;}float[] result=new float[N/2];for(int i=0;i<result.length;i++)result[i]=(float)(Math.hypot(real[i],imag[i])*4/N);return result;}
}
