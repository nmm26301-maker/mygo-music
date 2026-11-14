package com.mygo.music;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** The real local playlist row must start its own queue and keep working while a track is selected. */
final class LocalPlaylistChecks {
    final Instrumentation test;final Context context;final CompletionChecks helper;
    LocalPlaylistChecks(Instrumentation test){this.test=test;context=test.getTargetContext();helper=new CompletionChecks(test);}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    interface Action{void run()throws Exception;}
    void ui(Action action)throws Exception{Throwable[] failure={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable error){failure[0]=error;}});if(failure[0]!=null)throw new AssertionError("Local playlist action failed",failure[0]);}
    Object invoke(Object target,String name,Class<?>[] types,Object...args)throws Exception{Method method=target.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(target,args);}
    static ListView list(View root){if(root instanceof ListView)return (ListView)root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){ListView found=list(group.getChildAt(i));if(found!=null)return found;}}return null;}
    void run()throws Exception{
        MainActivity activity=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));PlaybackService[] service={null};helper.await(()->{service[0]=(PlaybackService)FullPortChecks.field(activity,"playbackService");return service[0]!=null;},"local playlist service not connected");
        ArrayList<File> files=new ArrayList<>();ArrayList<Uri> songs=new ArrayList<>();String name="Local queue fixture";AlertDialog[] dialog={null};
        try{
            for(int i=0;i<3;i++){File file=new File(context.getCacheDir(),"local-playlist-"+i+".wav");try(FileOutputStream out=new FileOutputStream(file)){out.write(BackgroundPlaybackChecks.wav(12000));}files.add(file);songs.add(Uri.fromFile(file));}
            ui(()->{service[0].clearQueue();service[0].setModes(false,false);for(int i=0;i<3;i++)invoke(activity,"addNamed",new Class<?>[]{Uri.class,String.class},songs.get(i),"Local fixture "+i);HashMap<String,HashSet<String>> playlists=(HashMap<String,HashSet<String>>)FullPortChecks.field(activity,"playlists");playlists.put(name,new HashSet<>(Arrays.asList(songs.get(0).toString(),songs.get(1).toString())));invoke(activity,"savePlaylists",new Class<?>[]{});dialog[0]=(AlertDialog)invoke(activity,"showPlaylistSongs",new Class<?>[]{String.class},name);ListView rows=list(dialog[0].getWindow().getDecorView());check(rows!=null&&rows.getCount()==2,"local playlist could not open");rows.performItemClick(null,0,0);});
            helper.await(()->service[0].snapshot().playing&&service[0].snapshot().uri.equals(songs.get(0).toString()),"local playlist row did not reach actual native player");
            ui(()->{check(service[0].queueSnapshot().length()==2,"local playlist used entire accumulated music library");service[0].next();});helper.await(()->service[0].snapshot().playing&&service[0].snapshot().uri.equals(songs.get(1).toString()),"local playlist next escaped selected songs");
            ui(()->{dialog[0]=(AlertDialog)invoke(activity,"showPlaylistSongs",new Class<?>[]{String.class},name);ListView rows=list(dialog[0].getWindow().getDecorView());check(rows!=null&&rows.getCount()==2,"selected current track prevented opening local playlist");check(dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).getText().toString().equals("加入当前歌曲"),"playlist lost explicit add-current action");dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick();HashMap<String,HashSet<String>> playlists=(HashMap<String,HashSet<String>>)FullPortChecks.field(activity,"playlists");check(playlists.get(name).size()==2,"adding existing current track duplicated playlist entries");});
            System.out.println("MYGO_LOCAL_PLAYLIST_OK: actual native playlist row playback, scoped next queue, open while a current track is selected, explicit add-current action without duplicate songs");
        }finally{ui(()->{service[0].clearQueue();HashMap<String,HashSet<String>> playlists=(HashMap<String,HashSet<String>>)FullPortChecks.field(activity,"playlists");playlists.remove(name);invoke(activity,"savePlaylists",new Class<?>[]{});ArrayList<Uri> library=(ArrayList<Uri>)FullPortChecks.field(activity,"tracks");ArrayList<String> names=(ArrayList<String>)FullPortChecks.field(activity,"names");for(Uri uri:songs){int at=library.indexOf(uri);if(at>=0){library.remove(at);names.remove(at);}}invoke(activity,"saveLibrary",new Class<?>[]{});activity.finish();});test.waitForIdleSync();for(File file:files)file.delete();}
    }
}
