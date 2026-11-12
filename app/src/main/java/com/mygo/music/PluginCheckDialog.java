package com.mygo.music;

import android.app.*;
import android.graphics.Color;
import android.widget.*;
import org.json.*;
import java.util.HashMap;
import java.util.concurrent.*;

/** Check the user's actual installed source and saved session without changing their music library. */
final class PluginCheckDialog {
    interface Player {void play(JSONObject track);}
    private final Activity activity;private final PluginManager manager;private final PluginManager.PluginInfo source;private final Runnable account,settings;private final Player player;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private AlertDialog dialog;private TextView status;private EditText query;private Button listen;private JSONObject track;private long generation;private boolean closed;
    PluginCheckDialog(Activity activity,PluginManager manager,PluginManager.PluginInfo source,Runnable account,Runnable settings,Player player){this.activity=activity;this.manager=manager;this.source=source;this.account=account;this.settings=settings;this.player=player;}
    void show(){
        LinearLayout box=new LinearLayout(activity);box.setOrientation(1);int pad=(int)(16*activity.getResources().getDisplayMetrics().density);box.setPadding(pad,pad,pad,pad);
        query=new EditText(activity);query.setSingleLine(true);query.setHint("用于检查的歌曲名称");query.setText("周杰伦");box.addView(query);
        status=new TextView(activity);status.setTextColor(Color.WHITE);status.setTextSize(15);status.setPadding(0,pad,0,pad);status.setText("使用此插件当前保存的账号与设置，检查加载、搜索和音频连接。\n检查不会修改歌单或收藏；通过后可试听搜索到的第一首歌曲。");box.addView(status);
        LinearLayout actions=new LinearLayout(activity);Button login=new Button(activity);login.setText("账号登录");login.setEnabled(source.supports("account")||source.supports("signIn"));login.setOnClickListener(view->account.run());actions.addView(login,new LinearLayout.LayoutParams(0,-2,1));Button configure=new Button(activity);configure.setText("权限与设置");configure.setOnClickListener(view->settings.run());actions.addView(configure,new LinearLayout.LayoutParams(0,-2,1));listen=new Button(activity);listen.setText("试听结果");listen.setEnabled(false);listen.setOnClickListener(view->{if(track!=null){JSONObject selected=track;dialog.dismiss();player.play(selected);}});actions.addView(listen,new LinearLayout.LayoutParams(0,-2,1));box.addView(actions);
        ScrollView scroll=new ScrollView(activity);scroll.addView(box);dialog=new AlertDialog.Builder(activity).setTitle(source.name+" · 可用性检查").setView(scroll).setPositiveButton("开始检查",null).setNegativeButton("关闭",null).create();dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->start()));dialog.setOnDismissListener(ignored->{closed=true;generation++;worker.shutdownNow();});dialog.show();
    }
    private boolean current(long ticket){return !closed&&!activity.isDestroyed()&&ticket==generation;}
    private void message(long ticket,String value,boolean finished){if(!current(ticket))return;status.setText(value);if(finished)dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);}
    private String reason(String raw){if(raw==null)return "未知原因";int newline=raw.indexOf('\n');return raw.startsWith("@mygo:")&&newline>=0?raw.substring(newline+1):raw;}
    private void start(){
        long ticket=++generation;String keyword=query.getText().toString().trim();if(keyword.isEmpty()){query.setError("请输入歌曲名称");return;}track=null;listen.setEnabled(false);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);message(ticket,"正在检查网络权限与插件加载…",false);
        manager.authorizeSource(source.id,(allowed,permission)->{if(!current(ticket))return;if(!allowed){message(ticket,reason(permission),true);return;}
            if(!source.supports("search")){message(ticket,"此插件未提供歌曲搜索接口。请从插件导航使用它声明的功能。",true);return;}
            try{manager.searchSource(source.id,keyword,(ok,raw)->{if(!current(ticket))return;if(!ok){message(ticket,"搜索未通过：\n"+reason(raw)+"\n可先登录账号或检查服务器设置，再重新检查。",true);return;}
                try{JSONArray tracks=new JSONObject(raw).optJSONArray("tracks");int count=tracks==null?0:tracks.length();if(count==0){message(ticket,"插件已响应搜索，但未返回歌曲。请更换关键词或检查此来源的登录条件。",true);return;}track=tracks.getJSONObject(0);if("remote".equals(source.playback)){listen.setEnabled(true);message(ticket,"搜索通过，共 "+count+" 首。此来源使用远程播放，请登录并选择播放设备后试听。",true);return;}if(!source.supports("stream")){message(ticket,"搜索通过，共 "+count+" 首；此插件未提供直接音频接口。",true);return;}
                    message(ticket,"搜索通过，共 "+count+" 首，正在解析第一首音频…",false);manager.export(source.id,"stream",new JSONObject().put("id",track.getString("id")).put("quality",activity.getSharedPreferences("library",0).getString("play_quality","high")),(resolved,value)->{if(!current(ticket))return;if(!resolved){message(ticket,"搜索通过，音频解析未通过：\n"+reason(value),true);return;}
                        worker.execute(()->{String result;boolean ready=false;try{JSONObject stream=new JSONObject(value);String address=stream.getString("url");HashMap<String,String> headers=manager.mediaHeaders(source.id,address,"audio",stream.optJSONObject("headers"));try(PluginMedia.Connection connection=PluginMedia.open(address,headers,true)){result="搜索与音频连接通过。点击试听结果可检查实际播放。\n这次结果只对应当前歌曲、账号和音质。";ready=true;}}catch(Exception failure){result="搜索通过，音频连接未通过：\n"+reason(failure.getMessage());}String report=result;boolean passed=ready;activity.runOnUiThread(()->{if(!current(ticket))return;listen.setEnabled(passed);message(ticket,report,true);});});
                    });
                }catch(Exception failure){message(ticket,"插件返回格式无法使用："+reason(failure.getMessage()),true);}
            });}catch(Exception failure){message(ticket,reason(failure.getMessage()),true);}
        });
    }
}
