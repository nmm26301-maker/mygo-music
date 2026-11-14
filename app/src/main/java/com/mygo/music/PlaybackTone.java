package com.mygo.music;

import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.audiofx.*;
import java.util.*;

/** Per-player tone control, never the global audio mix. Original source quality remains separate. */
final class PlaybackTone implements AutoCloseable {
    static final int[] FREQUENCIES={60,230,910,3600,14000};
    static final String[] PRESETS={"原声平直","人声清晰","温暖耐听","低频饱满","明亮通透","自定义"};
    static final int[][] CURVES={{0,0,0,0,0},{-200,-100,200,300,0},{200,100,0,-100,-200},{300,200,-100,0,0},{-100,0,0,200,300}};
    static final class Settings {
        boolean enabled;int[] levels=new int[5];
        static Settings read(SharedPreferences prefs){Settings value=new Settings();value.enabled=prefs.getBoolean("tone_enabled",false);for(int i=0;i<5;i++)value.levels[i]=Math.max(-600,Math.min(600,prefs.getInt("tone_band_"+i,0)));return value;}
        void save(SharedPreferences prefs){SharedPreferences.Editor edit=prefs.edit().putBoolean("tone_enabled",enabled);for(int i=0;i<5;i++)edit.putInt("tone_band_"+i,levels[i]);edit.apply();}
        int preset(){for(int i=0;i<CURVES.length;i++)if(Arrays.equals(levels,CURVES[i]))return i;return CURVES.length;}
    }
    private final Runnable controlChanged;
    PlaybackTone(){this(null);}
    PlaybackTone(Runnable controlChanged){this.controlChanged=controlChanged;}
    private Equalizer equalizer;private int session=-1;private int[] applied;
    private boolean active;private float headroom=1;String status="均衡器未启用";
    static int at(int[] levels,int hz){
        if(hz<=FREQUENCIES[0])return levels[0];for(int i=1;i<FREQUENCIES.length;i++)if(hz<=FREQUENCIES[i]){double t=Math.log(hz/(double)FREQUENCIES[i-1])/Math.log(FREQUENCIES[i]/(double)FREQUENCIES[i-1]);return (int)Math.round(levels[i-1]+t*(levels[i]-levels[i-1]));}return levels[4];
    }
    float apply(MediaPlayer player,SharedPreferences prefs){
        Settings settings=Settings.read(prefs);if(!settings.enabled){close();status="均衡器未启用 · 原声输出";return 1;}
        try{
            int id=player.getAudioSessionId();if(id<=0)throw new IllegalStateException();if(session!=id){close();session=id;equalizer=new Equalizer(0,id);equalizer.setControlStatusListener((effect,control)->{if(effect==equalizer&&controlChanged!=null)controlChanged.run();});}
            if(equalizer==null||!equalizer.hasControl())throw new IllegalStateException("no control");
            if(!Arrays.equals(applied,settings.levels)){
                short[] range=equalizer.getBandLevelRange();short bands=equalizer.getNumberOfBands();if(bands<=0||range.length<2)throw new IllegalStateException();int boost=0;
                for(short i=0;i<bands;i++){int level=Math.max(range[0],Math.min(range[1],at(settings.levels,Math.max(1,equalizer.getCenterFreq(i)/1000))));equalizer.setBandLevel(i,(short)level);boost=Math.max(boost,level);}
                headroom=boost>0?(float)Math.pow(10,-(boost+100)/2000.0):1;applied=settings.levels.clone();
            }
            if(equalizer.setEnabled(true)!=AudioEffect.SUCCESS||!equalizer.getEnabled())throw new IllegalStateException();active=true;status="均衡器已生效 · "+PRESETS[settings.preset()]+(headroom<1?" · 已预留增益余量":"");return headroom;
        }catch(RuntimeException failure){close();status="此设备暂无法应用本应用均衡器，音频继续播放";return 1;}
    }
    boolean active(){return active;}
    @Override public void close(){active=false;headroom=1;applied=null;session=-1;Equalizer old=equalizer;equalizer=null;if(old!=null){try{if(old.hasControl())old.setEnabled(false);}catch(RuntimeException ignored){}try{old.release();}catch(RuntimeException ignored){}}}
}
