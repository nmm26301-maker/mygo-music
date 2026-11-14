package com.mygo.music;

import android.content.SharedPreferences;

/** Saved display choices are independent of the source's lyric timing. */
final class LyricsAppearance {
    static final String[] STYLES={"逐字弹起","沉浸流动","双色卡拉OK","大字专注","经典滚动","霓虹流光","弹簧聚焦","长音波浪","逐字轻旋","柔光呼吸","流星拖尾","翻页入场","涟漪扩散","双行交接"};
    static final String[] BACKGROUNDS={"封面流动","封面静态","纯净深色"};
    static final String[] COLORS={"跟随封面","清透白","薄荷绿","暖金色","樱花粉","冰蓝紫"};
    int style=0,size=100,spacing=100,strength=100,background=0,color=0;
    boolean centered,translation=true,album=true,reduceMotion,systemReduceMotion;
    static int clamp(int value,int low,int high){return Math.max(low,Math.min(high,value));}
    static LyricsAppearance read(SharedPreferences prefs){
        LyricsAppearance result=new LyricsAppearance();
        int legacy=prefs.getInt("lyrics_effect",0);result.style=clamp(prefs.getInt("lyrics_style",legacy==2?4:legacy==1?1:0),0,STYLES.length-1);
        result.size=clamp(prefs.getInt("lyrics_size",100),80,135);result.spacing=clamp(prefs.getInt("lyrics_spacing",100),75,150);result.strength=clamp(prefs.getInt("lyrics_strength",100),0,140);
        result.background=clamp(prefs.getInt("lyrics_background",0),0,2);result.color=clamp(prefs.getInt("lyrics_color",0),0,5);result.album=prefs.getBoolean("lyrics_album",true);result.centered=prefs.getBoolean("lyrics_centered",false);result.translation=prefs.getBoolean("lyrics_translation",true);result.reduceMotion=prefs.getBoolean("lyrics_reduce_motion",false);return result;
    }
    void save(SharedPreferences prefs){prefs.edit().putInt("lyrics_style",style).putInt("lyrics_size",size).putInt("lyrics_spacing",spacing).putInt("lyrics_strength",strength).putInt("lyrics_background",background).putInt("lyrics_color",color).putBoolean("lyrics_album",album).putBoolean("lyrics_centered",centered).putBoolean("lyrics_translation",translation).putBoolean("lyrics_reduce_motion",reduceMotion).apply();}
    boolean quiet(){return reduceMotion||systemReduceMotion||strength==0;}
    LyricsAppearance copy(){LyricsAppearance copy=new LyricsAppearance();copy.style=style;copy.size=size;copy.spacing=spacing;copy.strength=strength;copy.background=background;copy.color=color;copy.album=album;copy.centered=centered;copy.translation=translation;copy.reduceMotion=reduceMotion;copy.systemReduceMotion=systemReduceMotion;return copy;}
}
