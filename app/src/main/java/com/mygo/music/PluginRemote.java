package com.mygo.music;

import android.os.*;
import org.json.*;

/** Controls the source's actual player; audio stays on its selected device. */
final class PluginRemote {
    interface Listener { void state(JSONObject state);void error(String message); }
    private final PluginManager manager;private final Listener listener;private final Handler main=new Handler(Looper.getMainLooper());
    private String source="";private int failures;private long generation;private boolean polling,ready,closed;private JSONObject state=new JSONObject();
    PluginRemote(PluginManager manager,Listener listener){this.manager=manager;this.listener=listener;}
    String source(){return source;}boolean active(){return !source.isEmpty();}boolean ready(){return ready;}
    int position(){return (int)Math.min(Integer.MAX_VALUE,Math.max(0,state.optLong("position")));}
    int duration(){return (int)Math.min(Integer.MAX_VALUE,Math.max(0,state.optLong("duration")));}
    boolean playing(){return state.optBoolean("playing");}
    void play(String id,JSONArray ids,int start,int position,PluginRuntime.Callback callback){
        clear(!source.equals(id));source=id;final long token=generation;
        try{manager.export(id,"remotePlay",new JSONObject().put("ids",ids).put("start",start).put("position",Math.max(0,position)),(ok,raw)->{if(closed||token!=generation)return;ready=ok;callback.done(ok,raw);if(ok)poll(token);else source="";});}catch(Exception error){source="";callback.done(false,error.getMessage());}
    }
    void toggle(){if(ready)command(playing()?"remotePause":"remoteResume",new JSONObject());}
    void seek(int position){try{command("remoteSeek",new JSONObject().put("position",Math.max(0,position)));}catch(JSONException ignored){}}
    void volume(int percent){try{command("remoteVolume",new JSONObject().put("percent",Math.max(0,Math.min(100,percent))));}catch(JSONException ignored){}}
    void use(String device){try{command("remoteUse",new JSONObject().put("deviceId",device));}catch(JSONException ignored){}}
    private void command(String method,JSONObject args){if(!active()||closed)return;final long token=generation;manager.export(source,method,args,(ok,raw)->{if(token!=generation||closed)return;if(!ok)listener.error(raw);else poll(token);});}
    private void poll(long token){if(closed||token!=generation||!active()||polling)return;polling=true;manager.export(source,"remoteState",new JSONObject(),(ok,raw)->{if(closed||token!=generation)return;polling=false;if(ok)try{failures=0;ready=true;state="null".equals(raw)?new JSONObject():new JSONObject(raw);listener.state(state);}catch(JSONException error){listener.error("远程播放器状态格式无效");}else{if(++failures<3){main.removeCallbacksAndMessages(null);main.postDelayed(()->poll(token),1000L*failures);return;}ready=false;listener.error(raw);return;}main.removeCallbacksAndMessages(null);main.postDelayed(()->poll(token),1000);});}
    void clear(boolean pause){String previous=source;boolean wasReady=ready;++generation;main.removeCallbacksAndMessages(null);source="";ready=false;polling=false;failures=0;state=new JSONObject();if(pause&&wasReady&&!previous.isEmpty())manager.export(previous,"remotePause",new JSONObject(),(ok,raw)->{});}
    void close(){clear(false);closed=true;}
}
