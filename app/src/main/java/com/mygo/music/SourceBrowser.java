package com.mygo.music;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** A source's real pages in the main landscape pane. All actions call the source's exports. */
final class SourceBrowser {
    interface Host {
        void error(PluginManager.PluginInfo source,String message,Runnable retry);
        void play(PluginManager.PluginInfo source,ArrayList<JSONObject> tracks,int index);
        void playlist(PluginManager.PluginInfo source,JSONObject playlist);
        void account(PluginManager.PluginInfo source);
        void image(PluginManager.PluginInfo source,ImageView image,String url);
    }
    private static final String[] KINDS={"tracks","albums","artists","playlists","shows","stations","links","episodes"};
    private final Activity activity;
    private final PluginManager manager;
    private final LinearLayout container;
    private final Host host;
    private final ArrayDeque<Page> history=new ArrayDeque<>();
    private PluginManager.PluginInfo source;
    private Page page;
    private long request;
    private boolean closed,loading;
    private static final class Page {
        final String method,title;
        final JSONObject args;
        JSONObject result,summary;
        int consumed,scroll;
        final IdentityHashMap<JSONArray,Integer> visible=new IdentityHashMap<>();
        Page(String method,JSONObject args,String title){this.method=method;this.args=args;this.title=title;}
    }
    SourceBrowser(Activity activity,PluginManager manager,LinearLayout container,Host host){this.activity=activity;this.manager=manager;this.container=container;this.host=host;}
    boolean active(){return !closed&&source!=null;}
    String sourceId(){return source==null?"":source.id;}
    void clear(){request++;source=null;page=null;history.clear();container.removeAllViews();loading=false;scrollTo(0);}
    void close(){clear();closed=true;}
    void select(PluginManager.PluginInfo next){clear();source=next;if(next.supports("home"))open("home",new JSONObject(),next.name);else{page=new Page("landing",new JSONObject(),next.name);page.result=new JSONObject();render();}}
    void open(PluginManager.PluginInfo next,String method,JSONObject args,String title){if(source==null||!source.id.equals(next.id)){clear();source=next;}open(method,args,title);}
    void showExternal(PluginManager.PluginInfo next,JSONObject result){if(source==null||!source.id.equals(next.id)){clear();source=next;}remember();page=new Page("external",new JSONObject(),result.optString("title",next.name));page.result=result;request++;loading=false;render();}
    private void remember(){if(page!=null&&page.result!=null){if(container.getParent() instanceof ScrollView)page.scroll=((ScrollView)container.getParent()).getScrollY();history.addLast(page);if(history.size()>20)history.removeFirst();}}
    private void open(String method,JSONObject args,String title){if(!active())return;remember();page=new Page(method,args,title);load(false);}
    void search(String query,String kind){if(!active())return;JSONObject args=new JSONObject();try{args.put("query",query).put("kind",kind).put("offset",0);}catch(JSONException ignored){}open("search",args,"搜索结果 · "+query);}
    boolean back(){if(history.isEmpty()||!active())return false;request++;loading=false;page=history.removeLast();render();scrollTo(page.scroll);return true;}
    void reload(){if(page!=null&&!page.method.equals("landing")&&!page.method.equals("external"))load(false);}
    private boolean current(PluginManager.PluginInfo selected,Page target,long ticket){if(closed||activity.isFinishing()||activity.isDestroyed()||source!=selected||page!=target||request!=ticket)return false;for(PluginManager.PluginInfo installed:manager.list())if(installed.enabled&&installed.id.equals(selected.id))return true;return false;}
    private void load(boolean append){
        if(!active()||page==null)return;
        final PluginManager.PluginInfo selected=source;final Page target=page;final long ticket=++request;
        loading=true;if(!append){target.result=null;target.consumed=0;target.visible.clear();}render();if(!append)scrollTo(0);
        JSONObject args;try{args=new JSONObject(target.args.toString());if(append)args.put("offset",target.consumed);}catch(JSONException error){return;}
        manager.export(selected.id,target.method,args,(ok,raw)->activity.runOnUiThread(()->{
            if(!current(selected,target,ticket))return;loading=false;
            if(!ok){render();host.error(selected,raw,()->{if(current(selected,target,ticket))load(append);});return;}
            try{
                JSONObject result=normalize(target.method,raw);decorate(target,result);
                int count=countRows(result);
                if(append&&target.result!=null){boolean added=false;for(String key:KINDS){JSONArray incoming=result.optJSONArray(key);if(incoming==null)continue;JSONArray previous=target.result.optJSONArray(key);if(previous==null)previous=new JSONArray();Set<String> seen=new HashSet<>();for(int i=0;i<previous.length();i++){JSONObject item=previous.optJSONObject(i);if(item!=null)seen.add(itemKey(key,item));}for(int i=0;i<incoming.length();i++){JSONObject item=incoming.optJSONObject(i);if(item!=null&&seen.add(itemKey(key,item))){previous.put(item);added=true;}}target.result.put(key,previous);}target.result.put("more",result.optBoolean("more")&&count>0&&added);}
                else{target.result=result;if(count==0)target.result.put("more",false);}
                target.consumed+=count;render();if(!append)scrollTo(0);
            }catch(Exception error){render();host.error(selected,"插件页面格式无效："+error.getMessage(),()->{if(current(selected,target,ticket))load(append);});}
        }));
    }
    static JSONObject normalize(String method,String raw)throws JSONException{
        if(raw==null||raw.length()>8*1024*1024)throw new JSONException("页面超过大小限制");
        String value=raw.trim();if(value.equals("null")||value.isEmpty())return new JSONObject();
        if(value.startsWith("[")){String key=method.equals("stationNext")||method.equals("recent")?"tracks":method.equals("shows")?"shows":method.equals("stations")?"stations":"playlists";return new JSONObject().put(key,new JSONArray(value));}
        return new JSONObject(value);
    }
    private static void decorate(Page target,JSONObject result)throws JSONException{
        if(target.method.equals("playlist")){if(!result.has("id"))result.put("id",target.args.optString("id"));if(target.summary!=null)for(String key:new String[]{"name","mine","fixed","saved"})if(!result.has(key)&&target.summary.has(key))result.put(key,target.summary.get(key));}
        for(String key:new String[]{"album","artist","show"})if(target.method.equals(key)&&target.summary!=null){JSONObject entity=result.optJSONObject(key);if(entity==null){entity=new JSONObject();result.put(key,entity);}for(Iterator<String> it=target.summary.keys();it.hasNext();){String name=it.next();if(!entity.has(name))entity.put(name,target.summary.get(name));}}
    }
    private static int countRows(JSONObject result){int count=0;for(String key:KINDS){JSONArray items=result.optJSONArray(key);if(items!=null)count+=items.length();}return count;}
    private static String itemKey(String kind,JSONObject item){return kind+":"+item.optString("id",item.optString("page",item.optString("name",item.optString("title"))));}
    private int dp(float value){return (int)(value*activity.getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int size,int color){TextView view=new TextView(activity);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setGravity(Gravity.CENTER_VERTICAL);view.setPadding(dp(5),dp(3),dp(5),dp(3));return view;}
    private Button button(String value,Runnable action){Button button=new Button(activity);button.setText(value);button.setAllCaps(false);button.setTextColor(0xffe4d9f4);button.setTextSize(13);button.setMinHeight(0);button.setMinimumHeight(0);button.setMinWidth(0);button.setMinimumWidth(0);button.setPadding(dp(8),0,dp(8),0);button.setBackgroundColor(Color.TRANSPARENT);button.setOnClickListener(view->action.run());return button;}
    private boolean dense(){return activity.getResources().getDisplayMetrics().heightPixels/activity.getResources().getDisplayMetrics().density<350;}
    private boolean compact(){return activity.getResources().getDisplayMetrics().heightPixels/activity.getResources().getDisplayMetrics().density<420;}
    private void heading(String value){if(!value.isEmpty()){TextView view=text(value,dense()?15:17,Color.WHITE);view.setTypeface(null,Typeface.BOLD);container.addView(view,new LinearLayout.LayoutParams(-1,dp(dense()?24:compact()?28:34)));}}
    private void render(){
        if(!active()||page==null)return;container.removeAllViews();
        final Page displayed=page;
        LinearLayout toolbar=new LinearLayout(activity);toolbar.setGravity(Gravity.CENTER_VERTICAL);
        Button back=button("返回",this::back);back.setEnabled(!history.isEmpty());toolbar.addView(back,new LinearLayout.LayoutParams(dp(compact()?44:60),dp(36)));
        TextView title=text(displayed.result==null?displayed.title:displayed.result.optString("title",displayed.title),16,Color.WHITE);title.setMaxLines(compact()?1:2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);toolbar.addView(title,new LinearLayout.LayoutParams(0,dp(compact()?36:44),1));
        if(!displayed.method.equals("external")&&!displayed.method.equals("landing"))toolbar.addView(button("刷新",this::reload),new LinearLayout.LayoutParams(dp(55),dp(36)));
        if(source.supports("account")||source.supports("signIn")){PluginManager.PluginInfo accountSource=source;toolbar.addView(button("账号",()->host.account(accountSource)),new LinearLayout.LayoutParams(dp(55),dp(36)));}
        container.addView(toolbar);
        LinearLayout navigation=new LinearLayout(activity);Set<String> entries=new HashSet<>();
        for(Map.Entry<String,String> entry:source.navigationPages.entrySet())addNavigation(navigation,entries,entry.getKey(),entry.getValue());
        String[][] builtin={{"首页","home"},{"我喜欢","liked"},{"收藏专辑","savedAlbums"},{"歌单","playlists"},{"播客","shows"},{"电台","stations"},{"最近播放","recent"}};
        for(String[] entry:builtin)if(source.supports(entry[1]))addNavigation(navigation,entries,entry[0],entry[1]);
        if(navigation.getChildCount()>0){if(compact()){Button categories=button("分类",()->{});categories.setOnClickListener(view->{PopupMenu popup=new PopupMenu(activity,categories);for(int i=0;i<navigation.getChildCount();i++){Button item=(Button)navigation.getChildAt(i);popup.getMenu().add(0,i,i,item.getText());}popup.setOnMenuItemClickListener(item->{((Button)navigation.getChildAt(item.getItemId())).performClick();return true;});popup.show();});toolbar.addView(categories,2,new LinearLayout.LayoutParams(dp(52),dp(36)));}else{HorizontalScrollView strip=new HorizontalScrollView(activity);strip.setHorizontalScrollBarEnabled(false);strip.addView(navigation);container.addView(strip,new LinearLayout.LayoutParams(-1,dp(36)));}}
        if(displayed.result==null){container.addView(text(loading?"正在加载音乐源内容":"加载失败，可点击刷新重试",14,0xffb8adca));return;}
        JSONObject result=displayed.result;
        String subtitle=result.optString("subtitle"),description=result.optString("description");if(!subtitle.isEmpty())container.addView(text(subtitle,13,0xffb8adca));if(!description.isEmpty())container.addView(text(description,13,0xffb8adca));
        for(String key:new String[]{"album","artist","show"}){JSONObject entity=result.optJSONObject(key);if(entity!=null)entityHeader(key,entity);}
        if(displayed.method.equals("playlist"))entityHeader("playlist",result);
        JSONArray sections=result.optJSONArray("sections");
        if(sections!=null){for(int i=0;i<sections.length()&&i<100;i++){JSONObject section=sections.optJSONObject(i);if(section==null)continue;heading(section.optString("title"));renderItems(section);String more=section.optString("morePage");if(!more.isEmpty()){container.addView(button("查看更多 · "+section.optString("title"),()->open("browse",arg("page",more),section.optString("title","更多内容"))),new LinearLayout.LayoutParams(-1,dp(38)));}if(countRows(section)==0&&!section.optString("empty").isEmpty())container.addView(text(section.optString("empty"),13,0xffb8adca));}}
        else renderItems(result);
        if(sections==null&&countRows(result)==0)container.addView(text(displayed.method.equals("landing")?"输入关键词，搜索此音乐源":"暂无内容",14,0xffb8adca));
        if(result.optBoolean("more")&&canPaginate(displayed.method)){Button more=button(loading?"正在加载更多":"加载更多",()->{if(!loading)load(true);});more.setEnabled(!loading);container.addView(more,new LinearLayout.LayoutParams(-1,dp(42)));}
    }
    private void scrollTo(int y){Page expected=page;container.post(()->{if(closed||page!=expected)return;if(container.getParent() instanceof ScrollView)((ScrollView)container.getParent()).scrollTo(0,y);});}
    private static boolean canPaginate(String method){return method.equals("search")||method.equals("liked")||method.equals("savedAlbums")||method.equals("show");}
    private void addNavigation(LinearLayout navigation,Set<String> seen,String label,String target){
        if(target.isEmpty()||!seen.add(target))return;
        String method=target;boolean custom=!Arrays.asList("home","liked","savedAlbums","playlists","shows","stations","recent").contains(target);
        if(!custom&&!source.supports(method))return;
        navigation.addView(button(label,()->open(custom?"browse":method,custom?arg("page",target):arg("offset",0),label)),new LinearLayout.LayoutParams(-2,dp(34)));
    }
    private void entityHeader(String kind,JSONObject entity){
        String name=entity.optString("name",page.title);heading(name);
        String summary=entity.optString("description",entity.optString("released"));if(summary.isEmpty()&&entity.optString("publisherPage").isEmpty())summary=entity.optString("publisher");if(!summary.isEmpty())container.addView(text(summary,13,0xffb8adca));
        if(kind.equals("show")&&!entity.optString("publisherPage").isEmpty()){String publisher=entity.optString("publisher","发布者");container.addView(button(publisher,()->open("browse",arg("page",entity.optString("publisherPage")),publisher)));}
        if(kind.equals("playlist")&&!entity.optString("id").isEmpty()){PluginManager.PluginInfo selected=source;container.addView(button("歌单管理",()->host.playlist(selected,entity)),new LinearLayout.LayoutParams(-1,dp(38)));}
        String method=kind.equals("album")?"saveAlbum":kind.equals("artist")?"followArtist":kind.equals("show")?"subscribeShow":"savePlaylist";
        if(!source.supports(method)||entity.optString("id").isEmpty()||kind.equals("playlist")&&entity.optBoolean("mine"))return;
        String key=kind.equals("artist")?"following":kind.equals("show")?"subscribed":"saved";
        String label=kind.equals("artist")?"关注此歌手":kind.equals("show")?"订阅此播客":kind.equals("album")?"收藏此专辑":"收藏此歌单";Button save=button((entity.optBoolean(key)?"取消":"")+label,()->{});save.setOnClickListener(v->{boolean desired=!entity.optBoolean(key);JSONObject args=arg("id",entity.optString("id"));try{args.put("on",desired);}catch(JSONException ignored){}save.setEnabled(false);PluginManager.PluginInfo selected=source;Page target=page;long ticket=request;manager.export(selected.id,method,args,(ok,raw)->activity.runOnUiThread(()->{if(!current(selected,target,request))return;if(ok){try{entity.put(key,desired);}catch(JSONException ignored){}render();}else{save.setEnabled(true);host.error(selected,raw,()->{if(current(selected,target,ticket))save.performClick();});}}));});container.addView(save,new LinearLayout.LayoutParams(-1,dp(38)));
    }
    private void renderItems(JSONObject result){for(String kind:KINDS){JSONArray items=result.optJSONArray(kind);if(items==null||items.length()==0)continue;if(kind.equals("tracks")||kind.equals("episodes"))tracks(items);else cards(kind,items);}}
    private void tracks(JSONArray items){
        ArrayList<JSONObject> tracks=new ArrayList<>();for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item!=null&&!item.optString("id").isEmpty())tracks.add(item);}
        if(tracks.isEmpty())return;PluginManager.PluginInfo selected=source;
        container.addView(button((tracks.size()>500?"播放前 500 首 · 共 ":"播放全部 · ")+tracks.size()+" 首",()->host.play(selected,tracks,0)),new LinearLayout.LayoutParams(-1,dp(36)));
        int visible=visibleCount(items,tracks.size());for(int i=0;i<visible;i++){JSONObject item=tracks.get(i);final int at=i;Button row=button(trackName(item),()->host.play(selected,tracks,at));row.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);row.setMaxLines(2);row.setEllipsize(android.text.TextUtils.TruncateAt.END);if(item.optBoolean("unavailable")){row.setEnabled(false);row.setTextColor(0xff777080);row.setText(trackName(item)+" · 暂不可播放");}container.addView(row,new LinearLayout.LayoutParams(-1,dp(50)));}showMoreLoaded(items,visible,tracks.size());
    }
    private void cards(String kind,JSONArray items){
        int columns=activity.getResources().getDisplayMetrics().widthPixels/activity.getResources().getDisplayMetrics().density>1000?4:3;int cardHeight=dense()?76:136;LinearLayout row=null;int count=0;
        int visible=visibleCount(items,items.length());for(int i=0;i<visible;i++){JSONObject item=items.optJSONObject(i);if(item==null)continue;if(count%columns==0){row=new LinearLayout(activity);container.addView(row,new LinearLayout.LayoutParams(-1,-2));}
            LinearLayout card=new LinearLayout(activity);card.setOrientation(1);card.setPadding(dp(6),dp(6),dp(6),dp(6));GradientDrawable background=new GradientDrawable();background.setColor(0xff25212a);background.setCornerRadius(dp(10));card.setBackground(background);
            ImageView image=new ImageView(activity);image.setImageResource(R.drawable.ic_launcher);image.setScaleType(ImageView.ScaleType.CENTER_CROP);card.addView(image,new LinearLayout.LayoutParams(-1,dp(dense()?40:82)));host.image(source,image,item.optString("imageUrl"));TextView label=text(item.optString("name",item.optString("title","内容")),13,Color.WHITE);label.setMaxLines(dense()?1:2);label.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(label,new LinearLayout.LayoutParams(-1,dp(dense()?24:42)));card.setOnClickListener(v->openItem(kind,item));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(cardHeight),1);params.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(card,params);count++;
        }
        if(row!=null)while(count%columns!=0){row.addView(new View(activity),new LinearLayout.LayoutParams(0,dp(cardHeight),1));count++;}showMoreLoaded(items,visible,items.length());
    }
    private int visibleCount(JSONArray items,int count){Integer limit=page.visible.get(items);return Math.min(count,limit==null?60:limit);}
    private void showMoreLoaded(JSONArray items,int visible,int count){if(visible>=count)return;Page displayed=page;container.addView(button("显示更多已加载内容 · "+visible+" / "+count,()->{if(page!=displayed)return;displayed.visible.put(items,visible+60);render();}),new LinearLayout.LayoutParams(-1,dp(42)));}
    private void openItem(String kind,JSONObject item){String id=item.optString("id"),title=item.optString("name",item.optString("title","内容"));if(kind.equals("links")){open("browse",arg("page",item.optString("page")),title);return;}if(id.isEmpty()){host.error(source,"内容缺少来源标识",()->{});return;}String method=kind.equals("albums")?"album":kind.equals("artists")?"artist":kind.equals("playlists")?"playlist":kind.equals("shows")?"show":"stationNext";if(!source.supports(method)){host.error(source,"此音乐源未提供这项内容接口",()->{});return;}JSONObject args=arg("id",id);if(method.equals("show"))try{args.put("offset",0);}catch(JSONException ignored){}open(method,args,title);if(page!=null)page.summary=item;}
    static JSONObject arg(String key,Object value){JSONObject args=new JSONObject();try{args.put(key,value);}catch(JSONException ignored){}return args;}
    static String trackName(JSONObject track){String name=track.optString("name",track.optString("title","歌曲")),artist=track.optString("artist");JSONArray artists=track.optJSONArray("artists");if(artist.isEmpty()&&artists!=null){ArrayList<String> names=new ArrayList<>();for(int i=0;i<artists.length();i++){JSONObject person=artists.optJSONObject(i);if(person!=null&&!person.optString("name").isEmpty())names.add(person.optString("name"));}artist=android.text.TextUtils.join("、",names);}return name+(artist.isEmpty()?"":"  ·  "+artist);}
    static String artwork(JSONObject track){String url=track.optString("imageUrl");JSONObject album=track.optJSONObject("album");return !url.isEmpty()||album==null?url:album.optString("imageUrl");}
}
