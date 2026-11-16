package com.mygo.music;

import android.app.*;
import android.content.*;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.FrameLayout;
import org.json.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real output capture across transport changes and native waveform scheduling. */
final class AudioAnalysisChecks {
    final Instrumentation test;final Context context;final NowPlayingChecks actions;final CompletionChecks helper;
    AudioAnalysisChecks(Instrumentation test){this.test=test;context=test.getTargetContext();actions=new NowPlayingChecks(test);helper=new CompletionChecks(test);}
    void ui(NowPlayingChecks.Action action)throws Exception{actions.ui(action);}
    void check(boolean yes,String message){NowPlayingChecks.check(yes,message);}
    boolean peaks(float left,float right){AudioAnalysis.Frame f=AudioAnalysis.frame();return f.stereo&&f.rmsLeft>.03f&&Math.abs(f.peakLeft-left)<45&&Math.abs(f.peakRight-right)<45;}
    File wave(String name,int left,int right,boolean change)throws Exception{
        int frames=48000*40;ByteBuffer bytes=ByteBuffer.allocate(44+frames*4).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put("RIFF".getBytes("US-ASCII")).putInt(36+frames*4).put("WAVEfmt ".getBytes("US-ASCII")).putInt(16).putShort((short)1).putShort((short)2).putInt(48000).putInt(192000).putShort((short)4).putShort((short)16).put("data".getBytes("US-ASCII")).putInt(frames*4);
        for(int n=0;n<frames;n++){int l=change&&n>=480000?1320:left,r=change&&n>=480000?1760:right;bytes.putShort((short)(12000*Math.sin(n*2*Math.PI*l/48000)));bytes.putShort((short)(8000*Math.sin(n*2*Math.PI*r/48000)));}
        File file=new File(context.getCacheDir(),name);try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes.array());}return file;
    }
    File directory(){File file=new File(context.getExternalFilesDir(null),"qa/audio-analysis");file.mkdirs();return file;}
    void shot(String name)throws Exception{test.waitForIdleSync();SystemClock.sleep(250);android.graphics.Bitmap image=test.getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(directory(),name+".png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
    int windowDraws(AtomicInteger draws,long millis){draws.set(0);SystemClock.sleep(millis);return draws.get();}
    void budget(MainActivity activity,PlaybackService service)throws Exception{
        Dialog[] window={null};ThemeSeekBar[] bar={null};FrameLayout[] root={null};AtomicInteger draws=new AtomicInteger();
        try{
            ui(()->{window[0]=new Dialog(activity);root[0]=new FrameLayout(activity);root[0].setBackgroundColor(0xff111c24);bar[0]=new ThemeSeekBar(activity);bar[0].style=2;bar[0].active=true;bar[0].rendering(false,false);root[0].addView(bar[0],new FrameLayout.LayoutParams(280,48,Gravity.CENTER));root[0].getViewTreeObserver().addOnDrawListener(()->draws.incrementAndGet());window[0].setContentView(root[0]);window[0].show();window[0].getWindow().setLayout(320,160);});
            helper.await(()->root[0].hasWindowFocus(),"waveform budget window did not gain focus");ui(()->AudioAnalysis.demand(true,service.snapshot().playing,false));helper.await(()->AudioAnalysis.frame().time>0,"real PCM did not reach waveform window");SystemClock.sleep(200);
            int normal=windowDraws(draws,1000);ui(()->bar[0].rendering(false,true));SystemClock.sleep(200);int low=windowDraws(draws,1000);
            check(normal>18&&low>2&&low<=20&&low<normal*.8f,"waveform draw budget ineffective normal="+normal+" low="+low);
            ui(()->service.pause());helper.await(()->AudioAnalysis.frame().time==0,"unfocused theme retained PCM after native pause");SystemClock.sleep(200);int paused=windowDraws(draws,600);check(paused<=2,"paused waveform kept scheduling draws "+paused);
            JSONObject result=new JSONObject().put("normalWindowDrawsPer1000ms",normal).put("lowPerformanceWindowDrawsPer1000ms",low).put("pausedWindowDrawsPer600ms",paused).put("source","actual same-UID native audio capture").put("scope","emulator draw scheduling, not physical phone frame rate or power");
            try(FileOutputStream out=new FileOutputStream(new File(directory(),"render-budget.json"))){out.write(result.toString(2).getBytes("UTF-8"));}
            System.out.println("MYGO_WAVEFORM_BUDGET_OK realPcm=true normalDraws="+normal+" lowDraws="+low+" pausedDraws="+paused);
        }finally{ui(()->{if(window[0]!=null)window[0].dismiss();});}
    }
    void run()throws Exception{
        SharedPreferences prefs=context.getSharedPreferences("library",0);Map<String,?> original=new HashMap<>(prefs.getAll());prefs.edit().remove("selected_source").putBoolean("auto_cache",false).putString("player_theme","spectrum").putBoolean("player_remember_theme",true).putFloat("play_speed",1).commit();
        MainActivity activity=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));PlaybackService[] connected={null};helper.await(()->{connected[0]=(PlaybackService)FullPortChecks.field(activity,"playbackService");return connected[0]!=null;},"analysis service unavailable");PlaybackService service=connected[0];ThemedPlayerDialog[] page={null};File first=wave("analysis-discontinuity.wav",440,880,true),second=wave("analysis-next.wav",660,990,false);
        try{
            helper.await(()->!AudioAnalysis.authorized,"prior capture service was not stopped");
            ui(()->context.startForegroundService(new Intent(context,PlayerAudioCaptureService.class).putExtra("result",Activity.RESULT_CANCELED)));
            helper.await(()->AudioAnalysis.status.startsWith("音频分析不可用："),"capture initialization failure reason missing");SystemClock.sleep(250);check(!AudioAnalysis.authorized&&AudioAnalysis.status.startsWith("音频分析不可用："),"capture teardown replaced the initialization error");
            JSONArray queue=new JSONArray().put(new JSONObject().put("uri",Uri.fromFile(first).toString()).put("title","频谱时间轴检查")).put(new JSONObject().put("uri",Uri.fromFile(second).toString()).put("title","下一首真实音频"));
            ui(()->PlaybackService.startQueue(context,queue,0,0,80,false,false,"high"));helper.await(()->service.snapshot().playing&&service.snapshot().position>300,"analysis test source did not play");
            ui(()->{NowPlayingChecks.invoke(activity,"showNowPlaying");page[0]=(ThemedPlayerDialog)FullPortChecks.field(activity,"themedPlayer");PlayerAudioPermissions.request(activity);});SourcePlaylistUiChecks.click(test,"Start now");helper.await(()->AudioAnalysis.authorized&&peaks(440,880),"analysis capture did not recover after a rejected grant: "+AudioAnalysis.status);
            MediaPlayer core=service.snapshot().player;long generation=service.snapshot().generation;AudioAnalysis.Frame old=AudioAnalysis.frame();long oldRevision=AudioAnalysis.revision;
            ui(()->{service.seek(12000);check(AudioAnalysis.frame().time==0,"seek retained previous spectrum");check(!AudioAnalysis.publish(old,oldRevision),"old PCM callback crossed seek boundary");});helper.await(()->peaks(1320,1760),"seek did not capture the second real tone region");check(service.snapshot().player==core&&service.snapshot().generation==generation,"analysis seek replaced the audio core");shot("after-seek");
            ui(()->{for(int i=0;i<12;i++){service.pause();check(!AudioAnalysis.playing&&AudioAnalysis.frame().time==0,"native pause did not invalidate capture");service.resume();service.setVolume(0);check(!AudioAnalysis.playing&&AudioAnalysis.frame().time==0,"native mute did not invalidate capture");service.setVolume(80);}});helper.await(()->AudioAnalysis.authorized&&peaks(1320,1760),"rapid pause/mute/resume stopped output capture: "+AudioAnalysis.status);
            ui(()->{service.next();check(AudioAnalysis.frame().time==0,"track replacement retained previous spectrum");});helper.await(()->service.snapshot().playing&&service.snapshot().uri.equals(Uri.fromFile(second).toString())&&peaks(660,990),"new audio session did not replace captured frequencies");shot("after-next");budget(activity,service);
            ui(()->service.resume());helper.await(()->AudioAnalysis.authorized&&peaks(660,990),"capture did not return after budget window");MediaPlayer last=service.snapshot().player;ui(()->context.startService(new Intent(context,PlayerAudioCaptureService.class).setAction("stop")));helper.await(()->!AudioAnalysis.authorized&&AudioAnalysis.frame().time==0&&AudioAnalysis.status.equals("音频分析已关闭"),"explicit capture stop did not clear output");check(service.snapshot().player==last&&service.snapshot().playing,"stopping capture stopped music");
            System.out.println("MYGO_AUDIO_ANALYSIS_OK nativeGrantFailurePreserved=true seekTone=1320/1760Hz nextTone=660/990Hz stalePcmRejected=true rapidPauseMuteResume=12 backgroundTransport=true captureStopKeepsMusic=true");
        }finally{ui(()->{context.stopService(new Intent(context,PlayerAudioCaptureService.class));if(page[0]!=null)page[0].dismiss();service.clearQueue();activity.finish();});first.delete();second.delete();new TurntableLyricsChecks(test).restore(prefs,original);}
    }
}
