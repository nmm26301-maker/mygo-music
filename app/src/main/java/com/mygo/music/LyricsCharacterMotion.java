package com.mygo.music;

import android.os.Build;
import java.util.*;

/**
 * Smooth rise / held lift / damped settle adapted from KaraokeText's MIT-licensed
 * KaraokeRenderer.swift, Copyright (c) 2026 WillSuo. See third-party/KaraokeText-MIT.txt.
 * Native Android geometry and playback-clock integration are implemented here.
 */
final class LyricsCharacterMotion {
    static float lift(long position,long start,long end){
        if(position<start)return 0;
        double riseMs=Math.min(140,Math.max(45,(end-start)*.45));
        double t=Math.max(0,Math.min(1,(position-start)/riseMs));
        double rise=t*t*(3-2*t);
        long fallAt=Math.max(start+(long)riseMs,end)+90;
        if(position<=fallAt)return (float)rise;
        double elapsed=(position-fallAt)/1000.0;
        if(elapsed>.75)return 0;
        return (float)(rise*Math.exp(-9*elapsed)*Math.cos(13*elapsed));
    }
    /** Native grapheme boundaries keep surrogate pairs, combining marks and emoji together. */
    static List<int[]> clusters(String text){
        List<int[]> out=new ArrayList<>();
        if(Build.VERSION.SDK_INT>=24){android.icu.text.BreakIterator iterator=android.icu.text.BreakIterator.getCharacterInstance(Locale.ROOT);iterator.setText(text);int from=iterator.first();for(int to=iterator.next();to!=android.icu.text.BreakIterator.DONE;from=to,to=iterator.next())out.add(new int[]{from,to});}
        else{java.text.BreakIterator iterator=java.text.BreakIterator.getCharacterInstance(Locale.ROOT);iterator.setText(text);int from=iterator.first();for(int to=iterator.next();to!=java.text.BreakIterator.DONE;from=to,to=iterator.next())out.add(new int[]{from,to});}
        return out;
    }
}
