package com.mygo.music;

import android.media.MediaPlayer;
import android.media.audiofx.LoudnessEnhancer;
import org.json.*;

/** Upstream ReplayGain preamp/peak rule; Android effects provide the boost above unity. */
final class PlaybackLoudness implements AutoCloseable {
    private LoudnessEnhancer enhancer;private int session=-1;
    float appliedVolume=1;int appliedBoost;boolean boostSupported=true;
    static double number(JSONObject info,String key){double value=info==null?0:info.optDouble(key,0);return Double.isNaN(value)||Double.isInfinite(value)?0:value;}
    static double gain(JSONObject info){double gain=number(info,"gain"),peak=Math.max(0,number(info,"peak"));if(gain==0&&peak==0)return 1;double value=Math.pow(10,(Math.max(-120,Math.min(120,gain))+4)/20);return peak>0?Math.min(value,1/peak):value;}
    static JSONObject info(JSONObject source){JSONObject result=new JSONObject();try{for(String key:new String[]{"gain","peak"})if(source!=null&&source.has(key))result.put(key,number(source,key));}catch(JSONException ignored){}return result;}
    void apply(MediaPlayer player,int percent,boolean ducked,boolean normalize,JSONObject info){
        apply(player,percent,ducked,normalize,info,1);
    }
    void apply(MediaPlayer player,int percent,boolean ducked,boolean normalize,JSONObject info,float headroom){
        double target=Math.max(0,Math.min(100,percent))/100.0*Math.max(0,Math.min(1,headroom))*(ducked?.2:1)*(normalize?gain(info):1);appliedVolume=(float)Math.min(1,target);appliedBoost=target>1?(int)Math.min(2400,Math.round(2000*Math.log10(target))):0;player.setVolume(appliedVolume,appliedVolume);
        if(session!=player.getAudioSessionId()){close();session=player.getAudioSessionId();boostSupported=true;}
        try{if(appliedBoost>0&&enhancer==null)enhancer=new LoudnessEnhancer(session);if(enhancer!=null){enhancer.setTargetGain(appliedBoost);enhancer.setEnabled(appliedBoost>0);}}catch(RuntimeException failure){boostSupported=false;appliedBoost=0;if(enhancer!=null){enhancer.release();enhancer=null;}}
    }
    public void close(){if(enhancer!=null){enhancer.release();enhancer=null;}session=-1;}
}
