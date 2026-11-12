package com.mygo.music;

import android.app.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** The provider owns comment ids, counts, pagination and mutation acknowledgements. */
final class SourceComments {
    interface Host {void error(String error,Runnable retry);void image(ImageView view,String address);}
    private final Activity activity;private final PluginManager manager;private final PluginManager.PluginInfo source;private final String track,title;private final Host host;
    private final LinkedHashMap<String,JSONObject> comments=new LinkedHashMap<>();private final ArrayList<JSONObject> rows=new ArrayList<>();
    private AlertDialog dialog,composer;private TextView status;private Button more;private BaseAdapter adapter;private int offset,total;private boolean loading,closed,hasMore;private long generation;
    SourceComments(Activity activity,PluginManager manager,PluginManager.PluginInfo source,String track,String title,Host host){this.activity=activity;this.manager=manager;this.source=source;this.track=track;this.title=title;this.host=host;}
    private int dp(int value){return (int)(value*activity.getResources().getDisplayMetrics().density+.5f);}
    private boolean current(long ticket){return !closed&&!activity.isDestroyed()&&generation==ticket;}
    private TextView text(String value,int size){TextView text=new TextView(activity);text.setText(value);text.setTextSize(size);text.setTextColor(Color.WHITE);return text;}
    void show(){
        LinearLayout root=new LinearLayout(activity);root.setOrientation(1);root.setPadding(dp(12),dp(6),dp(12),0);status=text("正在读取评论",13);root.addView(status);
        ListView list=new ListView(activity);adapter=new BaseAdapter(){public int getCount(){return rows.size();}public Object getItem(int index){return rows.get(index);}public long getItemId(int index){return index;}
            public View getView(int index,View recycled,ViewGroup parent){JSONObject item=rows.get(index);LinearLayout box=new LinearLayout(activity);box.setOrientation(1);box.setPadding(dp(4),dp(8),dp(4),dp(8));LinearLayout author=new LinearLayout(activity);ImageView avatar=new ImageView(activity);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);author.addView(avatar,new LinearLayout.LayoutParams(dp(28),dp(28)));host.image(avatar,item.optString("avatarUrl"));TextView name=text(item.optString("author","用户"),13);name.setPadding(dp(8),0,0,0);author.addView(name,new LinearLayout.LayoutParams(0,dp(30),1));long time=item.optLong("time");if(time>0)author.addView(text(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA).format(new Date(time)),11));box.addView(author);TextView body=text(item.optString("text",item.optString("content")),15);body.setPadding(0,dp(5),0,dp(5));box.addView(body);
                if(source.supports("likeComment")&&!item.optString("id").isEmpty()){Button like=new Button(activity);like.setAllCaps(false);like.setText((item.optBoolean("liked")?"取消赞":"点赞")+" · "+item.optInt("likes"));like.setTextSize(12);like.setOnClickListener(view->like(item,like));box.addView(like,new LinearLayout.LayoutParams(-2,dp(40)));}return box;}};list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,dp(180)));more=new Button(activity);more.setText("加载更多评论");more.setOnClickListener(view->load(true));root.addView(more);
        list.getLayoutParams().height=dp(Math.max(80,Math.min(180,activity.getResources().getConfiguration().screenHeightDp-190)));
        AlertDialog.Builder builder=new AlertDialog.Builder(activity).setTitle(title+" · 评论").setView(root).setNegativeButton("关闭",null).setNeutralButton("刷新",null);if(source.supports("postComment"))builder.setPositiveButton("写评论",null);dialog=builder.create();dialog.setOnDismissListener(ignored->{closed=true;generation++;if(composer!=null)composer.dismiss();});dialog.setOnShowListener(ignored->{dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view->load(false));if(source.supports("postComment"))dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->compose());load(false);});dialog.show();
    }
    private JSONObject args(String key,Object value){JSONObject args=SourceBrowser.arg("trackId",track);try{args.put(key,value);}catch(JSONException ignored){}return args;}
    private void render(){rows.clear();rows.addAll(comments.values());adapter.notifyDataSetChanged();status.setText(loading?"正在读取评论":rows.isEmpty()?"暂无评论":"已加载 "+rows.size()+" 条"+(total>0?"，共 "+total+" 条":""));more.setVisibility(hasMore?View.VISIBLE:View.GONE);more.setEnabled(!loading);}
    private void load(boolean append){
        if(loading||closed)return;if(!source.supports("comments")){status.setText("此音乐源没有评论接口");more.setVisibility(View.GONE);return;}if(!append){offset=0;comments.clear();hasMore=false;}long ticket=++generation;loading=true;render();
        manager.export(source.id,"comments",args("offset",offset),(ok,raw)->{if(!current(ticket))return;loading=false;
            if(!ok){render();status.setText("评论读取失败，可刷新重试");host.error(raw,()->load(append));return;}
            try{JSONObject result=new JSONObject(raw);JSONArray incoming=result.optJSONArray("comments");int count=incoming==null?0:incoming.length(),before=comments.size();for(int i=0;i<count;i++){JSONObject item=incoming.optJSONObject(i);if(item==null)continue;String id=item.optString("id");if(id.isEmpty())id=item.optString("author")+":"+item.optLong("time")+":"+item.optString("text");comments.put(id,item);}offset+=count;total=result.optInt("total",total);hasMore=result.optBoolean("more")&&count>0&&comments.size()>before;render();}catch(Exception error){status.setText("评论返回格式无效");host.error(error.getMessage(),()->load(append));}
        });
    }
    private void like(JSONObject item,Button button){
        boolean desired=!item.optBoolean("liked");long ticket=generation;button.setEnabled(false);JSONObject args=SourceBrowser.arg("trackId",track);try{args.put("commentId",item.getString("id")).put("on",desired);}catch(JSONException error){return;}
        manager.export(source.id,"likeComment",args,(ok,raw)->{if(!current(ticket))return;if(!ok){button.setEnabled(true);host.error(raw,button::performClick);return;}try{item.put("liked",desired).put("likes",Math.max(0,item.optInt("likes")+(desired?1:-1)));}catch(JSONException ignored){}render();});
    }
    private void compose(){
        EditText input=new EditText(activity);input.setHint("写下评论");input.setMaxLines(4);composer=new AlertDialog.Builder(activity).setTitle("发表评论").setView(input).setPositiveButton("发布",null).setNegativeButton("取消",null).create();composer.setOnShowListener(ignored->composer.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->{String body=input.getText().toString().trim();if(body.isEmpty()||body.length()>5000){input.setError(body.isEmpty()?"请输入评论":"评论过长");return;}Button send=composer.getButton(AlertDialog.BUTTON_POSITIVE);send.setEnabled(false);long ticket=generation;
            manager.export(source.id,"postComment",args("text",body),(ok,raw)->{if(!current(ticket)||!composer.isShowing())return;send.setEnabled(true);if(!ok){host.error(raw,send::performClick);return;}try{JSONObject result=new JSONObject(raw);if(result.optString("id").isEmpty())throw new JSONException("来源未返回评论标识");LinkedHashMap<String,JSONObject> updated=new LinkedHashMap<>();updated.put(result.getString("id"),result);updated.putAll(comments);comments.clear();comments.putAll(updated);if(total>0)total++;render();composer.dismiss();}catch(Exception error){host.error("评论已提交，重新读取服务器结果",()->load(false));composer.dismiss();load(false);}});
        }));composer.show();
    }
}
