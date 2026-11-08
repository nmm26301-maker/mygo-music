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
    private android.media.AudioManager audioManager;
    private final android.media.AudioManager.OnAudioFocusChangeListener focusListener=focus->{if(focus<=0&&player!=null&&player.isPlaying())player.pause();else if(focus==android.media.AudioManager.AUDIOFOCUS_GAIN&&player!=null&&!player.isPlaying())player.start();notifyPlayback();};
    private String currentTitle="MyGo Music";
    private final IBinder binder=new Binder();
    public class Binder extends android.os.Binder { PlaybackService getService(){return PlaybackService.this;} }
    @Override public IBinder onBind(Intent i){return binder;}
    @Override public void onCreate(){super.onCreate();audioManager=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null)return START_STICKY; String a=intent.getAction();
        try{
            if(ACTION_PLAY.equals(a)){String u=intent.getStringExtra("uri");String title=intent.getStringExtra("title");int pos=intent.getIntExtra("position",0);startPlayback(Uri.parse(u),title,pos);}
            else if(ACTION_PAUSE.equals(a)&&player!=null&&player.isPlaying()){player.pause();notifyPlayback();}
            else if(ACTION_RESUME.equals(a)&&player!=null&&!player.isPlaying()){player.start();notifyPlayback();}
            else if("STOP".equals(a)){stopSelf();}
        }catch(Exception ignored){} return START_STICKY;
    }
    private void startPlayback(Uri uri,String title,int pos)throws Exception{
        if(player!=null){player.release();player=null;} currentTitle=title==null?"MyGo Music":title; player=new MediaPlayer();if(audioManager!=null)audioManager.requestAudioFocus(focusListener,android.media.AudioManager.STREAM_MUSIC,android.media.AudioManager.AUDIOFOCUS_GAIN); if(uri.toString().startsWith("http")){java.util.HashMap<String,String> h=new java.util.HashMap<>();h.put("User-Agent","Mozilla/5.0");h.put("Referer","https://music.163.com/");player.setDataSource(this,uri,h);}else player.setDataSource(this,uri); player.setOnPreparedListener(p->{try{p.seekTo(Math.max(0,pos));}catch(Exception ignored){} p.start();}); player.setOnCompletionListener(p->stopSelf()); player.prepareAsync(); startForeground(7,notification(currentTitle));
    }
    private Notification notification(String title){String channel="mygo-playback"; if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(channel,"MyGo Music 播放",NotificationManager.IMPORTANCE_LOW);c.setDescription("正在播放");getSystemService(NotificationManager.class).createNotificationChannel(c);} Intent open=new Intent(this,MainActivity.class); PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT); Notification.Builder b=new Notification.Builder(this,channel).setContentTitle(title==null?"MyGo Music":title).setContentText(player!=null&&player.isPlaying()?"正在播放":"已暂停").setSmallIcon(com.mygo.music.R.drawable.ic_launcher).setColor(Color.rgb(147,88,190)).setContentIntent(pi).setOngoing(true); Intent pause=new Intent(this,PlaybackService.class).setAction(ACTION_PAUSE); Intent resume=new Intent(this,PlaybackService.class).setAction(ACTION_RESUME); Intent stop=new Intent(this,PlaybackService.class).setAction("STOP"); PendingIntent pp=PendingIntent.getService(this,11,pause,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT); PendingIntent pr=PendingIntent.getService(this,12,resume,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT); PendingIntent ps=PendingIntent.getService(this,13,stop,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT); if(player!=null&&player.isPlaying())b.addAction(new Notification.Action.Builder(0,"暂停",pp).build()); else b.addAction(new Notification.Action.Builder(0,"继续",pr).build()); b.addAction(new Notification.Action.Builder(0,"停止",ps).build()); return b.build(); }
    private void notifyPlayback(){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(7,notification(currentTitle));}
    @Override public void onDestroy(){if(audioManager!=null)audioManager.abandonAudioFocus(focusListener);if(player!=null){player.release();player=null;}super.onDestroy();}
}
