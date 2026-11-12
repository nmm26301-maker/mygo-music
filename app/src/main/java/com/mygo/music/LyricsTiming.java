package com.mygo.music;

import android.content.SharedPreferences;
import org.json.JSONObject;

/** A positive offset delays lyrics. Audio transport always keeps its original clock. */
final class LyricsTiming implements LyricsStageView.Clock {
    private final LyricsStageView.Clock audio;
    private final SharedPreferences preferences;
    private String track="";
    private int offset;
    LyricsTiming(LyricsStageView.Clock audio,SharedPreferences preferences){this.audio=audio;this.preferences=preferences;}
    void select(String uri){
        track=uri;offset=0;
        try{offset=clamp(new JSONObject(preferences.getString("lyrics_offsets","{}")).optInt(uri,0));}catch(Exception ignored){}
    }
    int offset(){return offset;}
    private static int clamp(long value){return (int)Math.max(-10000,Math.min(10000,value));}
    void adjust(int delta){set(clamp((long)offset+delta));}
    void set(int value){
        offset=clamp(value);if(track.isEmpty())return;
        JSONObject values;try{values=new JSONObject(preferences.getString("lyrics_offsets","{}"));}catch(Exception ignored){values=new JSONObject();}
        try{if(offset==0)values.remove(track);else values.put(track,offset);preferences.edit().putString("lyrics_offsets",values.toString()).apply();}catch(Exception ignored){}
    }
    public long position(){return audio.position()-offset;}
    public boolean playing(){return audio.playing();}
    public void seek(long lyricPosition){audio.seek(Math.max(0,lyricPosition+offset));}
}
