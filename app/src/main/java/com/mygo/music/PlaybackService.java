package com.mygo.music;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.os.Build;
import android.view.*;

public class PlaybackService extends Service {
    public static final String ACTION_PLAY="com.mygo.music.PLAY";
    public static final String ACTION_PAUSE="com.mygo.music.PAUSE";
    public static final String ACTION_RESUME="com.mygo.music.RESUME";
    private MediaPlayer player;
    private final IBinder binder=new Binder();
    public class Binder extends android.os.Binder { PlaybackService getService(){return PlaybackService.this;} }
    @Override public IBinder onBind(Intent i){return binder;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null)return START_STICKY; String a=intent.getAction();
        try{
            if(ACTION_PLAY.equals(a)){String u=intent.getStringExtra("uri");String title=intent.getStringExtra("title");int pos=intent.getIntExtra("position",0);startPlayback(Uri.parse(u),title,pos);}
            else if(ACTION_PAUSE.equals(a)&&player!=null&&player.isPlaying())player.pause();
            else if(ACTION_RESUME.equals(a)&&player!=null&&!player.isPlaying())player.start();
        }catch(Exception ignored){} return START_STICKY;
    }
    private void startPlayback(Uri uri,String title,int pos)throws Exception{
        if(player!=null){player.release();player=null;} player=new MediaPlayer(); player.setDataSource(this,uri); player.setOnPreparedListener(p->{try{p.seekTo(Math.max(0,pos));}catch(Exception ignored){} p.start();}); player.setOnCompletionListener(p->stopSelf()); player.prepareAsync(); startForeground(7,notification(title));
    }
    private Notification notification(String title){String channel="mygo-playback"; if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(channel,"MyGo Music 播放",NotificationManager.IMPORTANCE_LOW);c.setDescription("正在播放");getSystemService(NotificationManager.class).createNotificationChannel(c);} Intent open=new Intent(this,MainActivity.class); PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT); return new Notification.Builder(this,channel).setContentTitle(title==null?"MyGo Music":title).setContentText("正在播放").setSmallIcon(com.mygo.music.R.drawable.ic_launcher).setColor(Color.rgb(147,88,190)).setContentIntent(pi).setOngoing(true).build(); }
    @Override public void onDestroy(){if(player!=null){player.release();player=null;}super.onDestroy();}
}
