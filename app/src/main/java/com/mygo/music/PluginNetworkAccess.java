package com.mygo.music;

import android.app.*;
import android.content.SharedPreferences;
import android.os.*;
import java.util.*;


/** Foreground consent is serialized across managers; a saved denial is recoverable on the next use. */
final class PluginNetworkAccess {
    interface Valid {boolean get();}
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final HashMap<Activity,Session> SESSIONS=new HashMap<>();
    private static final class Waiter {
        final Object owner;final Valid valid;final PluginRuntime.Callback callback;
        Waiter(Object owner,Valid valid,PluginRuntime.Callback callback){this.owner=owner;this.valid=valid;this.callback=callback;}
    }
    private static final class Entry {
        final String id,name,domains;final SharedPreferences prefs;final ArrayList<Waiter> waiters=new ArrayList<>();final long deadline=SystemClock.elapsedRealtime()+120000;
        Entry(String id,String name,String domains,SharedPreferences prefs){this.id=id;this.name=name;this.domains=domains;this.prefs=prefs;}
    }
    private static final class Session {
        final Activity activity;final ArrayDeque<Entry> entries=new ArrayDeque<>();AlertDialog dialog;Entry active;
        Session(Activity activity){this.activity=activity;}
        void next(){
            if(active!=null)return;
            if(activity.isFinishing()||activity.isDestroyed()){cancelAll("授权页面已关闭");return;}
            if(entries.isEmpty()){SESSIONS.remove(activity);return;}
            active=entries.peekFirst();Entry entry=active;
            if(!prune(entry)){finish(entry,false,"插件状态已变化，请重新操作",false);return;}
            if(entry.prefs.getBoolean("plugin_network_"+entry.id,false)){finish(entry,true,"",false);return;}
            dialog=new AlertDialog.Builder(activity).setTitle(entry.name+" · 网络访问")
                .setMessage("此插件需要访问以下域名，才能加载内容、搜索、登录和播放。\n\n"+entry.domains+"\n\n允许后会继续刚才的操作；可在插件权限设置中撤回。")
                .setPositiveButton("允许并继续",(d,w)->finish(entry,true,"",true))
                .setNegativeButton("暂不允许",(d,w)->finish(entry,false,"已取消网络授权",true))
                .setOnCancelListener(d->finish(entry,false,"已取消网络授权",false)).create();
            dialog.show();MAIN.postDelayed(watch,250);
        }
        boolean prune(Entry entry){Iterator<Waiter> iterator=entry.waiters.iterator();ArrayList<Waiter> invalid=new ArrayList<>();while(iterator.hasNext()){Waiter item=iterator.next();if(!item.valid.get()){iterator.remove();invalid.add(item);}}for(Waiter item:invalid)item.callback.done(false,"插件状态已变化，请重新操作");return !entry.waiters.isEmpty();}
        final Runnable watch=new Runnable(){public void run(){Entry entry=active;if(entry==null)return;if(activity.isFinishing()||activity.isDestroyed()){cancelAll("授权页面已关闭");return;}if(!prune(entry)){finish(entry,false,"插件状态已变化，请重新操作",false);return;}if(SystemClock.elapsedRealtime()>entry.deadline){finish(entry,false,"网络授权已超时，请重新操作",false);return;}MAIN.postDelayed(this,250);}};
        void finish(Entry entry,boolean allowed,String message,boolean persist){
            if(active!=entry)return;MAIN.removeCallbacks(watch);boolean usable=prune(entry);
            if(persist&&usable)entry.prefs.edit().putBoolean("plugin_network_"+entry.id,allowed).apply();
            if(dialog!=null){dialog.setOnCancelListener(null);dialog.dismiss();dialog=null;}
            entries.remove(entry);active=null;ArrayList<Waiter> done=new ArrayList<>(entry.waiters);entry.waiters.clear();
            for(Waiter waiter:done)waiter.callback.done(allowed&&usable,allowed&&usable?"":message);
            MAIN.post(this::next);
        }
        void cancelAll(String message){MAIN.removeCallbacks(watch);if(dialog!=null){dialog.setOnCancelListener(null);dialog.dismiss();dialog=null;}ArrayList<Entry> all=new ArrayList<>(entries);entries.clear();active=null;SESSIONS.remove(activity);for(Entry entry:all)for(Waiter waiter:new ArrayList<>(entry.waiters))waiter.callback.done(false,message);}
        void cancelOwner(Object owner){for(Entry entry:new ArrayList<>(entries)){ArrayList<Waiter> removed=new ArrayList<>();Iterator<Waiter> iterator=entry.waiters.iterator();while(iterator.hasNext()){Waiter waiter=iterator.next();if(waiter.owner==owner){iterator.remove();removed.add(waiter);}}for(Waiter waiter:removed)waiter.callback.done(false,"插件运行环境已关闭");if(entry.waiters.isEmpty()){if(active==entry)finish(entry,false,"插件运行环境已关闭",false);else entries.remove(entry);}}if(active==null)next();}
    }
    static void request(Activity activity,Object owner,String id,String name,String domains,SharedPreferences prefs,Valid valid,PluginRuntime.Callback callback){
        if(Looper.myLooper()!=Looper.getMainLooper()){MAIN.post(()->request(activity,owner,id,name,domains,prefs,valid,callback));return;}
        if(activity.isFinishing()||activity.isDestroyed()||!valid.get()){callback.done(false,"授权页面已关闭");return;}
        Session session=SESSIONS.get(activity);if(session==null){session=new Session(activity);SESSIONS.put(activity,session);}
        Entry chosen=null;for(Entry entry:session.entries)if(entry.id.equals(id)&&entry.domains.equals(domains)){chosen=entry;break;}
        if(chosen==null){chosen=new Entry(id,name,domains,prefs);session.entries.addLast(chosen);}chosen.waiters.add(new Waiter(owner,valid,callback));session.next();
    }
    static void cancel(Object owner){if(Looper.myLooper()!=Looper.getMainLooper()){MAIN.post(()->cancel(owner));return;}for(Session session:new ArrayList<>(SESSIONS.values()))session.cancelOwner(owner);}
}
