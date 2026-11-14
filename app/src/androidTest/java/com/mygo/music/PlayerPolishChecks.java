package com.mygo.music;

import android.app.*;
import android.content.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;

/** Exercise real seek gestures: preview, one commit, cancellation, keyboard and track changes. */
final class PlayerPolishChecks {
    final Instrumentation test;
    PlayerPolishChecks(Instrumentation test){this.test=test;}
    void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static final class Host implements LyricsStageDialog.Host {
        long position=1000;int seeks,timers;String sleep="定时 14:59";
        public long duration(){return 10000;}public long position(){return position;}public boolean playing(){return false;}
        public void seek(long value){position=value;seeks++;}public void toggle(){}public void previous(){}public void next(){}public void refresh(){}public void importLyrics(){}public void sleep(){timers++;}public String sleepStatus(){return sleep;}
    }
    void event(SeekBar bar,int action,float fraction){long now=SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(now,now,action,bar.getWidth()*fraction,bar.getHeight()/2f,0);bar.dispatchTouchEvent(event);event.recycle();}
    void run()throws Exception{
        Context context=test.getTargetContext();Activity activity=test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Host host=new Host();LyricsStageDialog[] page={null};
        try{
            test.runOnMainSync(()->{page[0]=new LyricsStageDialog(activity,host);page[0].setTrack("seek-a","拖动进度检查");page[0].show();});test.waitForIdleSync();
            test.runOnMainSync(()->{try{
                SeekBar bar=page[0].progress;event(bar,MotionEvent.ACTION_DOWN,.20f);event(bar,MotionEvent.ACTION_MOVE,.45f);event(bar,MotionEvent.ACTION_MOVE,.70f);check(host.seeks==0&&host.position==1000,"drag repeatedly sought before release");TextView time=(TextView)FullPortChecks.field(page[0],"time");check(time.getText().toString().contains("00:07")&&time.getText().toString().contains(host.sleep),"drag preview or sleep status is missing");event(bar,MotionEvent.ACTION_UP,.70f);check(host.seeks==1&&Math.abs(host.position-7000)<400,"release did not commit exactly one seek");
                event(bar,MotionEvent.ACTION_DOWN,.30f);event(bar,MotionEvent.ACTION_MOVE,.55f);event(bar,MotionEvent.ACTION_CANCEL,.55f);check(host.seeks==1,"cancelled gesture sought audio");
                event(bar,MotionEvent.ACTION_DOWN,.20f);event(bar,MotionEvent.ACTION_MOVE,.40f);page[0].setTrack("seek-b","新的歌曲");event(bar,MotionEvent.ACTION_UP,.40f);check(host.seeks==1,"old gesture sought the replacement track");
                bar.requestFocus();bar.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));bar.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT));check(host.seeks>1,"keyboard user seek was lost");time.performClick();check(host.timers==1,"visible timer status cannot open timer settings");
                PlaybackService.Snapshot timer=new PlaybackService.Snapshot();timer.sleepRemaining=1001;check(PlaybackService.sleepLabel(timer).equals("定时 00:02"),"timer truncates remaining seconds");timer.sleepAfterTrack=true;check(PlaybackService.sleepLabel(timer).equals("本曲结束暂停"),"end-track label missing");timer.sleepAfterTrack=false;timer.sleepRemaining=0;check(PlaybackService.sleepLabel(timer).isEmpty(),"cleared timer retains a stale label");
            }catch(Exception error){throw new RuntimeException(error);}});
            System.out.println("MYGO_PLAYER_POLISH_OK: actual multi-move preview and single seek commit, cancelled/stale-track gestures ignored, keyboard seek retained, visible timer opens settings and countdown labels clear correctly");
        }finally{test.runOnMainSync(()->{if(page[0]!=null)page[0].dismiss();activity.finish();});}
    }
}
