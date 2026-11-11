package com.mygo.music;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;

/** Android side of the MyGo Music plugin directory protocol. */
public final class PluginManager {
    public static final class PluginInfo {
        public String id, name, version, main, playback;
        public String directory;
        public boolean enabled;
        public final ArrayList<String> navigation = new ArrayList<>();
        public final LinkedHashMap<String,String> navigationPages = new LinkedHashMap<>();
        public JSONArray network=new JSONArray(), settings=new JSONArray();
        public String summary(){return name+(version==null||version.isEmpty()?"":"  ·  "+version);}
    }

    public static final class PluginTrack {
        public String pluginId="", id="", name="", artist="", url="", pic="";
    }

    public interface SearchCallback { void done(ArrayList<PluginTrack> tracks); }

    public interface Callback { void done(boolean ok, String message); }
    private final Context context;
    private final File root;
    private final android.content.SharedPreferences prefs;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final HashMap<String,PluginRuntime> runtimes=new HashMap<>();

    public PluginManager(Context c){context=c;root=new File(context.getFilesDir(),"plugins");if(!root.exists())root.mkdirs();prefs=context.getSharedPreferences("library",Context.MODE_PRIVATE);}

    public ArrayList<PluginInfo> list(){ArrayList<PluginInfo> out=new ArrayList<>();File[] dirs=root.listFiles();if(dirs==null)return out;for(File d:dirs){if(!d.isDirectory()||d.getName().startsWith("."))continue;try{PluginInfo p=parse(new File(d,"plugin.json"));if(p!=null){p.directory=d.getAbsolutePath();p.enabled=prefs.getBoolean("plugin_enabled_"+p.id,true);out.add(p);}}catch(Exception ignored){}}Collections.sort(out,(a,b)->a.name.compareToIgnoreCase(b.name));return out;}

    private PluginInfo parse(File manifest)throws Exception{if(!manifest.isFile())return null;JSONObject o=new JSONObject(read(manifest));PluginInfo p=new PluginInfo();p.id=o.optString("id",manifest.getParentFile().getName());p.name=o.optString("name",p.id);p.version=o.optString("version","");p.main=o.optString("main","index.js");p.playback=o.optString("playback","stream");JSONObject permissions=o.optJSONObject("permissions");if(permissions!=null&&permissions.optJSONArray("network")!=null)p.network=permissions.getJSONArray("network");if(o.optJSONArray("settings")!=null)p.settings=o.getJSONArray("settings");JSONArray nav=o.optJSONArray("navigation");if(nav!=null)for(int i=0;i<nav.length();i++){JSONObject n=nav.optJSONObject(i);if(n!=null){String label=n.optString("title",n.optString("builtin",""));String target=n.optString("page",n.optString("builtin",""));if(label.isEmpty())label=target;if(!label.isEmpty()){p.navigation.add(label);p.navigationPages.put(label,target);}}}return p;}

    public void setEnabled(PluginInfo p,boolean enabled){if(p==null)return;p.enabled=enabled;prefs.edit().putBoolean("plugin_enabled_"+p.id,enabled).apply();}
    public void remove(PluginInfo p){if(p==null||p.directory==null)return;PluginRuntime rt=runtimes.remove(p.id);if(rt!=null)rt.dispose();delete(new File(p.directory));prefs.edit().remove("plugin_enabled_"+p.id).apply();}

    public void search(String query,SearchCallback callback){new Handler(Looper.getMainLooper()).post(()->{ArrayList<PluginInfo> all=list();ArrayList<PluginInfo> enabled=new ArrayList<>();for(PluginInfo p:all)if(p.enabled)enabled.add(p);searchPlugin(enabled,0,query,new ArrayList<>(),callback);});}
    public void searchOne(String pluginId,String query,SearchCallback callback){new Handler(Looper.getMainLooper()).post(()->{ArrayList<PluginInfo> one=new ArrayList<>();for(PluginInfo p:list())if(p.enabled&&p.id.equals(pluginId)){one.add(p);break;}searchPlugin(one,0,query,new ArrayList<>(),callback);});}
    public void lyrics(String pluginId,String trackId,PluginRuntime.Callback cb){callTrackExport(pluginId,"lyrics",new JSONObjectBuilder().put("id",trackId).value(),cb);}
    public void page(String pluginId,String function,String page,PluginRuntime.Callback cb){callTrackExport(pluginId,function,new JSONObjectBuilder().put("page",page).value(),cb);}
    public void comments(String pluginId,String trackId,PluginRuntime.Callback cb){callTrackExport(pluginId,"comments",new JSONObjectBuilder().put("trackId",trackId).put("offset",0).value(),cb);}
    public void postComment(String pluginId,String trackId,String body,PluginRuntime.Callback cb){callTrackExport(pluginId,"postComment",new JSONObjectBuilder().put("trackId",trackId).put("text",body).value(),cb);}
    public void export(String pluginId,String function,JSONObject args,PluginRuntime.Callback cb){callTrackExport(pluginId,function,args,cb);}
    public void searchSource(String pluginId,String query,PluginRuntime.Callback cb){callTrackExport(pluginId,"search",new JSONObjectBuilder().put("query",query).put("kind","tracks").put("offset",0).value(),cb);}
    private void callTrackExport(String pluginId,String function,JSONObject args,PluginRuntime.Callback callback){new Handler(Looper.getMainLooper()).post(()->{
        PluginInfo found=null;for(PluginInfo p:list())if(p.id.equals(pluginId)&&p.enabled){found=p;break;}
        if(found==null){callback.done(false,"插件未启用");return;}final PluginInfo target=found;
        Runnable run=()->{PluginRuntime rt=runtimes.get(pluginId);if(rt==null){rt=new PluginRuntime(context,new File(target.directory));runtimes.put(pluginId,rt);}rt.call(function,args,callback);};
        String permission="plugin_network_"+pluginId;
        if(target.network.length()>0&&!prefs.contains(permission)){
            new android.app.AlertDialog.Builder(context).setTitle("允许插件访问网络").setMessage(target.name+" 申请访问：\n"+target.network.toString()+"\n可在插件网络权限中撤回。")
                .setPositiveButton("允许",(d,w)->{prefs.edit().putBoolean(permission,true).apply();run.run();})
                .setNegativeButton("拒绝",(d,w)->{prefs.edit().putBoolean(permission,false).apply();callback.done(false,"插件网络访问已拒绝");}).setOnCancelListener(d->callback.done(false,"已取消授权")).show();
        }else run.run();
    });}
    public void close(){for(PluginRuntime rt:runtimes.values())rt.dispose();runtimes.clear();}
    private static final class JSONObjectBuilder{private final JSONObject o=new JSONObject();JSONObjectBuilder put(String k,Object v){try{o.put(k,v);}catch(Exception ignored){}return this;}JSONObject value(){return o;}}
    private void searchPlugin(ArrayList<PluginInfo> plugins,int index,String query,ArrayList<PluginTrack> out,SearchCallback cb){if(index>=plugins.size()){cb.done(out);return;}PluginInfo p=plugins.get(index);searchSource(p.id,query,(ok,value)->{if(ok)for(PluginTrack t:parseTracks(value)){t.pluginId=p.id;t.url=new android.net.Uri.Builder().scheme("plugin").authority(p.id).appendQueryParameter("id",t.id).build().toString();out.add(t);}searchPlugin(plugins,index+1,query,out,cb);});}
    private ArrayList<PluginTrack> parseTracks(String raw){ArrayList<PluginTrack> out=new ArrayList<>();try{JSONObject root=new JSONObject(raw);JSONArray a=root.optJSONArray("tracks");if(a==null)return out;for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;PluginTrack t=new PluginTrack();t.id=o.optString("id","");t.name=o.optString("name",o.optString("title",""));t.url=o.optString("url","");t.pic=o.optString("imageUrl",o.optString("pic",""));JSONArray artists=o.optJSONArray("artists");if(artists!=null&&artists.length()>0&&artists.optJSONObject(0)!=null)t.artist=artists.optJSONObject(0).optString("name","");if(t.artist.isEmpty())t.artist=o.optString("artist","");if(!t.name.isEmpty())out.add(t);}}catch(Exception ignored){}return out;}

    public void installFromGitHub(String input,Callback cb){
        final String repo=normalizeRepo(input);
        if(repo==null){cb.done(false,"请输入 owner/repo 或 GitHub 仓库地址");return;}
        executor.execute(()->{try{
            String manifestText="",branch="";
            for(String candidate:new String[]{"main","master"}){try{manifestText=getRaw("https://raw.githubusercontent.com/"+repo+"/"+candidate+"/plugin.json");branch=candidate;break;}catch(Exception ignored){}}
            if(manifestText.isEmpty()){
                JSONObject mfFile=new JSONObject(get("https://api.github.com/repos/"+repo+"/contents/plugin.json"));manifestText=decodeContent(mfFile.optString("content"));
            }
            if(manifestText.isEmpty())throw new IOException("无法读取 plugin.json，请确认仓库公开且文件在根目录");
            JSONObject mf=new JSONObject(manifestText);String id=mf.optString("id");if(id.isEmpty())throw new IOException("plugin.json 缺少 id");if(!id.matches("[A-Za-z0-9][A-Za-z0-9_.-]*"))throw new IOException("插件 id 不合法");
            String main=mf.optString("main","index.js"),mainText="";if(main.startsWith("/")||main.contains("..")||main.contains("\\"))throw new IOException("无效入口路径");if(main.endsWith(".ts"))throw new IOException("此插件需要 JavaScript 编译版，当前不支持 TypeScript 入口");
            if(!branch.isEmpty())try{mainText=getRaw("https://raw.githubusercontent.com/"+repo+"/"+branch+"/"+main);}catch(Exception ignored){}
            if(mainText.isEmpty()){JSONObject mainFile=new JSONObject(get("https://api.github.com/repos/"+repo+"/contents/"+main));mainText=decodeContent(mainFile.optString("content"));if(mainText.isEmpty()){String download=mainFile.optString("download_url");if(!download.isEmpty())mainText=get(download);}}
            if(mainText.isEmpty())throw new IOException("无法读取插件入口脚本："+main);
            File staging=new File(root,".install-"+id);if(staging.exists())delete(staging);if(!staging.mkdirs())throw new IOException("无法创建插件目录");write(new File(staging,"plugin.json"),manifestText);write(new File(staging,main),mainText);String repoText=new JSONObject().put("repo",repo).put("installedAt",System.currentTimeMillis()).toString();write(new File(staging,".origin.json"),repoText);File target=new File(root,id),backup=new File(root,".backup-"+id);if(backup.exists())delete(backup);if(target.exists()&&!target.renameTo(backup))throw new IOException("无法备份已安装插件");if(!staging.renameTo(target)){backup.renameTo(target);throw new IOException("无法保存插件");}delete(backup);new Handler(Looper.getMainLooper()).post(()->{PluginRuntime rt=runtimes.remove(id);if(rt!=null)rt.dispose();});prefs.edit().putBoolean("plugin_enabled_"+id,true).apply();cb.done(true,"插件已安装："+mf.optString("name",id));
        }catch(Exception e){cb.done(false,"插件安装失败："+e.getMessage());}});
    }
    private String normalizeRepo(String s){if(s==null)return null;s=s.trim();s=s.replace("https://github.com/","").replace("http://github.com/","").replace("www.github.com/","");int q=s.indexOf('?');if(q>=0)s=s.substring(0,q);int h=s.indexOf('#');if(h>=0)s=s.substring(0,h);s=s.replaceAll("/tree/.*$","/").replaceAll("/blob/.*$","/");s=s.replaceAll("/+$","");if(s.endsWith(".git"))s=s.substring(0,s.length()-4);if(!s.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))return null;return s;}
    private String getRaw(String address)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","MyGoMusic-Android");int code=c.getResponseCode();InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();String s=read(in);c.disconnect();if(code<200||code>=300)throw new IOException("GitHub 原始文件返回 "+code);return s;}
    private String get(String address)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","MyGoMusic-Android");c.setRequestProperty("Accept","application/vnd.github+json");int code=c.getResponseCode();InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();String s=read(in);c.disconnect();if(code<200||code>=300)throw new IOException("GitHub 返回 "+code);return s;}
    private String decodeContent(String value){if(value==null)return "";try{return new String(android.util.Base64.decode(value.replaceAll("\\s",""),android.util.Base64.DEFAULT),"UTF-8");}catch(Exception e){return "";}}
    private static String read(File f)throws Exception{return read(new FileInputStream(f));}
    private static String read(InputStream in)throws Exception{if(in==null)return "";try{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)b.write(buf,0,n);return new String(b.toByteArray(),"UTF-8");}finally{in.close();}}
    private static void write(File f,String s)throws Exception{File parent=f.getParentFile();if(parent!=null&&!parent.exists())parent.mkdirs();FileOutputStream out=new FileOutputStream(f);try{out.write(s.getBytes("UTF-8"));}finally{out.close();}}
    private static void delete(File f){if(f.isDirectory()){File[] cs=f.listFiles();if(cs!=null)for(File c:cs)delete(c);}f.delete();}
}
