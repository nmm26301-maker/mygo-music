package com.mygo.music;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Ordered setting writes, native types, explicit credential save and source acknowledgement. */
final class SourceSettings {
    private final Activity activity;private final PluginManager manager;private final PluginManager.PluginInfo source;private final SharedPreferences settings,permissions;
    private final LinkedHashMap<String,PluginSettingField> fields=new LinkedHashMap<>();private final Set<String> pending=new HashSet<>();private final Runnable onSaved;private boolean closing,closed,submitting;private AlertDialog dialog;
    SourceSettings(Activity activity,PluginManager manager,PluginManager.PluginInfo source){this(activity,manager,source,null);}
    SourceSettings(Activity activity,PluginManager manager,PluginManager.PluginInfo source,Runnable onSaved){this.activity=activity;this.manager=manager;this.source=source;this.onSaved=onSaved;settings=activity.getSharedPreferences("plugin_settings_"+source.id,0);permissions=activity.getSharedPreferences("library",0);}
    private TextView text(String value,int size){TextView text=new TextView(activity);text.setText(value);text.setTextSize(size);text.setTextColor(Color.WHITE);return text;}
    void show(){
        int pad=(int)(16*activity.getResources().getDisplayMetrics().density);LinearLayout root=new LinearLayout(activity);root.setOrientation(1);root.setPadding(pad,pad/2,pad,pad/2);
        permission(root,"允许网络访问\n"+source.network,"plugin_network_");if(source.deviceNetwork)permission(root,"允许酷狗设备注册\nuserservice.kugou.com","plugin_device_network_");if(source.programs.length()>0)permission(root,"允许启动辅助程序\n"+source.programs+"\n仅支持兼容 Android 的程序；程序可访问此应用能读取的数据。","plugin_process_");
        if(source.functions.contains("diagnostics")){Button diagnose=new Button(activity);diagnose.setText("检查此插件运行环境");diagnose.setOnClickListener(view->{diagnose.setEnabled(false);diagnose.setText("正在检查");manager.export(source.id,"diagnostics",new JSONObject(),(ok,raw)->activity.runOnUiThread(()->{if(closed||activity.isDestroyed())return;diagnose.setEnabled(true);diagnose.setText("检查此插件运行环境");String result=raw;if(ok)try{JSONArray checks=new JSONArray(raw);StringBuilder message=new StringBuilder();for(int i=0;i<checks.length();i++){JSONObject item=checks.getJSONObject(i);if(i>0)message.append("\n\n");message.append(item.optString("name")).append(item.optBoolean("ok")?" · 正常\n":" · 需处理\n").append(item.optString("value"));}result=message.toString();}catch(Exception ignored){}new AlertDialog.Builder(activity).setTitle(source.name+" · 运行检查").setMessage(result).setPositiveButton("关闭",null).show();}));});root.addView(diagnose);}
        for(int i=0;i<source.settings.length();i++){JSONObject spec=source.settings.optJSONObject(i);if(spec==null)continue;String key=spec.optString("key");if(key.isEmpty())continue;root.addView(text(spec.optString("label",key),15));PluginSettingField field=new PluginSettingField(activity,spec,settings.getString(key,""));fields.put(key,field);root.addView(field.view);if(!spec.optString("help").isEmpty())root.addView(text(spec.optString("help"),12));String link=spec.optString("link");if(link.startsWith("https://")||link.startsWith("http://")){Button open=new Button(activity);open.setText("打开说明页面");open.setOnClickListener(view->activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(link))));root.addView(open);}field.commits(value->save(key,value));}
        root.addView(text("普通文本在完成输入或移开焦点后保存；开关和选项即时保存。密码请点击保存，失败时可重试或恢复原值。",12));ScrollView scroll=new ScrollView(activity);scroll.addView(root);dialog=new AlertDialog.Builder(activity).setTitle(source.name+" · 权限与设置").setView(scroll).setPositiveButton(onSaved==null?"保存":"保存并重试",null).setNegativeButton("关闭",null).create();dialog.setOnDismissListener(value->closed=true);dialog.setOnShowListener(value->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->{closing=true;submitting=true;try{for(Map.Entry<String,PluginSettingField> entry:fields.entrySet())save(entry.getKey(),entry.getValue().value());}finally{submitting=false;}finishSave();}));dialog.show();
    }
    private void finishSave(){if(!closing||closed||submitting||!pending.isEmpty())return;closing=false;dialog.dismiss();if(onSaved!=null&&!activity.isFinishing()&&!activity.isDestroyed())onSaved.run();}
    private void permission(LinearLayout root,String title,String prefix){Switch control=new Switch(activity);control.setText(title);control.setTextColor(Color.WHITE);control.setChecked(permissions.getBoolean(prefix+source.id,false));root.addView(control);control.setOnCheckedChangeListener((button,value)->{permissions.edit().putBoolean(prefix+source.id,value).apply();manager.restart(source.id);});}
    private void save(String key,String value){
        PluginSettingField field=fields.get(key);if(pending.contains(key)||!field.changed())return;pending.add(key);field.view.setEnabled(false);
        SourceSettingQueue.submit(activity,source.id,finished->{
            boolean existed=settings.contains(key);String previous=settings.getString(key,"");settings.edit().putString(key,value).apply();boolean[] replied={false};
            PluginRuntime.Callback result=(ok,raw)->{if(replied[0])return;replied[0]=true;pending.remove(key);field.view.setEnabled(true);
                if(ok)field.saved(value);else{SharedPreferences.Editor restore=settings.edit();if(existed)restore.putString(key,previous);else restore.remove(key);restore.apply();manager.restart(source.id);closing=false;if(!closed&&!activity.isDestroyed())new AlertDialog.Builder(activity).setTitle("设置保存失败").setMessage(raw).setPositiveButton("重试",(dialog,which)->save(key,value)).setNegativeButton("恢复原值",(dialog,which)->field.restore(previous)).show();}
                finished.run();finishSave();
            };
            if(previous.equals(value)){result.done(true,"");return;}
            if(source.supports("setSetting")){JSONObject args=SourceBrowser.arg("key",key);try{args.put("value",value);}catch(JSONException ignored){}manager.setSetting(source.id,args,result);}else{manager.restart(source.id);result.done(true,"");}
        });
    }
}
