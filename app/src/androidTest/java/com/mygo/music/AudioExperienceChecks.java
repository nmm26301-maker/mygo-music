package com.mygo.music;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.media.*;
import android.media.audiofx.Equalizer;
import android.net.Uri;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;

/** Native effect parameters, real service clocks and actual UI settings, without claiming listening scores. */
final class AudioExperienceChecks {
    final Instrumentation test;final Context context;final CompletionChecks helper;
    AudioExperienceChecks(Instrumentation test){this.test=test;context=test.getTargetContext();helper=new CompletionChecks(test);}
    void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    void run()throws Exception{
        SharedPreferences prefs=context.getSharedPreferences("library",0);PlaybackTone.Settings original=PlaybackTone.Settings.read(prefs);boolean normalization=prefs.getBoolean("normalize",true);String quality=prefs.getString("play_quality",AudioQuality.DEFAULT);
        File first=new File(context.getCacheDir(),"tone-first.wav"),second=new File(context.getCacheDir(),"tone-second.wav");for(File file:new File[]{first,second})try(FileOutputStream out=new FileOutputStream(file)){out.write(BackgroundPlaybackChecks.wav(30000));}
        MainActivity activity=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));PlaybackService[] service={null};AudioExperienceDialog[] ui={null};Equalizer[] rival={null};helper.await(()->{service[0]=(PlaybackService)FullPortChecks.field(activity,"playbackService");return service[0]!=null;},"audio experience service missing");
        try{
            PlaybackTone.Settings initial=new PlaybackTone.Settings();initial.save(prefs);prefs.edit().putBoolean("normalize",false).commit();JSONArray queue=new JSONArray().put(new JSONObject().put("uri",Uri.fromFile(first).toString()).put("title","均衡器实测")).put(new JSONObject().put("uri",Uri.fromFile(second).toString()).put("title","下一首实测"));
            test.runOnMainSync(()->{try{PlaybackService.startQueue(context,queue,0,0,80,false,true,"high");}catch(Exception error){throw new RuntimeException(error);}});helper.await(()->service[0].snapshot().playing&&service[0].snapshot().position>200,"tone fixture never started");
            test.runOnMainSync(()->{ui[0]=new AudioExperienceDialog(activity,()->service[0]);ui[0].show();});test.waitForIdleSync();
            MediaPlayer before=service[0].snapshot().player;long generation=service[0].snapshot().generation;
            test.runOnMainSync(()->{ui[0].enabled.performClick();ui[0].presets.setSelection(1);});test.waitForIdleSync();
            helper.await(()->service[0].snapshot().toneInfo.contains("已生效"),"native equalizer did not enable");
            test.runOnMainSync(()->{try{
                PlaybackService.Snapshot state=service[0].snapshot();check(state.player==before&&state.generation==generation&&state.playing,"tone setting restarted playback");check(PlaybackTone.Settings.read(prefs).preset()==1,"actual preset choice was not saved");
                PlaybackTone tone=(PlaybackTone)FullPortChecks.field(service[0],"tone");Equalizer eq=(Equalizer)FullPortChecks.field(tone,"equalizer");check(eq.getEnabled()&&eq.hasControl(),"native effect is only a UI label");short[] range=eq.getBandLevelRange();int boost=0;for(short i=0;i<eq.getNumberOfBands();i++){int expected=Math.max(range[0],Math.min(range[1],PlaybackTone.at(PlaybackTone.CURVES[1],eq.getCenterFreq(i)/1000)));check(Math.abs(eq.getBandLevel(i)-expected)<3,"native frequency curve differs from selected preset");boost=Math.max(boost,expected);}PlaybackLoudness loudness=(PlaybackLoudness)FullPortChecks.field(service[0],"loudness");check(Math.abs(loudness.appliedVolume-.8*Math.pow(10,-(boost+100)/2000.0))<.003,"EQ boost has no gain headroom");
                rival[0]=new Equalizer(100,before.getAudioSessionId());rival[0].setEnabled(false);service[0].refreshVolume();check(!tone.active()&&service[0].snapshot().toneInfo.contains("无法")&&Math.abs(loudness.appliedVolume-.8)<.001,"lost effect control kept attenuation or reported success");rival[0].release();rival[0]=null;service[0].refreshVolume();check(tone.active(),"effect did not recover after control returned");
            }catch(Exception error){throw new RuntimeException(error);}});
            Bitmap image=test.getUiAutomation().takeScreenshot();if(image!=null){File file=new File(context.getExternalFilesDir(null),"qa/audio-experience.png");try(FileOutputStream out=new FileOutputStream(file)){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
            test.runOnMainSync(()->ui[0].dialog.dismiss());test.runOnMainSync(()->{ui[0]=new AudioExperienceDialog(activity,()->service[0]);ui[0].show();});test.waitForIdleSync();test.runOnMainSync(()->check(ui[0].enabled.isChecked()&&ui[0].presets.getSelectedItemPosition()==1,"reopened tone panel lost settings"));
            test.runOnMainSync(()->{ui[0].dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();});helper.await(()->!PlaybackTone.Settings.read(prefs).enabled&&service[0].snapshot().toneInfo.contains("未启用"),"restore original sound did not disable effect");
            test.runOnMainSync(()->{service[0].setSleepTimer(3500,false);service[0].next();});helper.await(()->service[0].snapshot().ready&&service[0].snapshot().playing&&service[0].snapshot().index==1,"countdown switch fixture did not start");helper.await(()->!service[0].snapshot().playing&&service[0].snapshot().sleepRemaining==0,"countdown did not pause after a track switch");
            int pausedAt=service[0].snapshot().position;SystemClock.sleep(250);check(Math.abs(service[0].snapshot().position-pausedAt)<60,"sleep timer left the native audio clock running");
            test.runOnMainSync(()->{service[0].resume();service[0].setSleepTimer(700,false);service[0].setSleepTimer(0,false);});SystemClock.sleep(1000);check(service[0].snapshot().playing,"cancelled sleep timer still paused audio");
            test.runOnMainSync(()->{service[0].setSleepTimer(0,true);service[0].seek(29200);});helper.await(()->!service[0].snapshot().playing&&!service[0].snapshot().sleepAfterTrack,"end-of-track timer ignored repeat mode");check(service[0].snapshot().index==1,"end-of-track timer advanced the queue");
            test.runOnMainSync(()->{service[0].resume();service[0].setSleepTimer(0,true);service[0].previous();});helper.await(()->service[0].snapshot().playing&&service[0].snapshot().index==0&&!service[0].snapshot().sleepAfterTrack,"manual skip kept an old end-of-track timer");
            test.runOnMainSync(()->{service[0].setSleepTimer(10000,false);service[0].stopPlayback();check(service[0].snapshot().sleepRemaining==0,"stop retained a stale timer");});
            System.out.println("MYGO_AUDIO_EXPERIENCE_OK: actual preset UI/save/reopen/reset, native equalizer curves and per-session headroom, loss-of-control fallback/recovery without restarting audio, countdown across track switch, cancellation, real paused clock and end-of-track before repeat, stale end timer cleared on manual skip and stop");
        }finally{test.runOnMainSync(()->{if(rival[0]!=null)rival[0].release();if(ui[0]!=null)ui[0].dialog.dismiss();service[0].stopPlayback();activity.finish();});original.save(prefs);prefs.edit().putBoolean("normalize",normalization).putString("play_quality",quality).commit();first.delete();second.delete();}
    }
}
