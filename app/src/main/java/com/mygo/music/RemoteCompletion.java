package com.mygo.music;

import org.json.JSONObject;

/** Completion needs an actual end position or repeated empty state after playing near the end. */
final class RemoteCompletion {
    private String track="";private long position,duration;private boolean playing,paused;private int empty;
    void reset(){track="";position=duration=0;playing=paused=false;empty=0;}
    void pause(){paused=true;empty=0;}
    void resume(){paused=false;playing=false;empty=0;}
    boolean update(JSONObject state){
        String next=state.optString("trackId");boolean active=state.optBoolean("playing");long at=Math.max(0,state.optLong("position")),length=Math.max(0,state.optLong("duration"));
        if(!next.isEmpty()&&!track.isEmpty()&&!next.equals(track)){position=duration=0;playing=false;empty=0;}
        boolean nearEnd=duration>0&&position>=Math.max(0,duration-1500),explicit=!active&&length>0&&at>=Math.max(0,length-80);
        if(!paused&&playing&&nearEnd&&next.isEmpty()&&length==0&&!active){if(++empty>=2){reset();return true;}return false;}
        boolean ended=!paused&&!active&&(state.optBoolean("ended")||playing&&explicit);
        if(!next.isEmpty())track=next;position=at;duration=length;playing=active;empty=0;if(ended)reset();return ended;
    }
}
