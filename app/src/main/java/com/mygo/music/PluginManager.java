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

/** Android side of the MyGo Music plugin directory protocol. */
public final class PluginManager {
    public static final class PluginInfo {
        public String id, name, version, main, playback;
        public String directory;
        public boolean enabled;
        public final ArrayList<String> navigation = new ArrayList<>();
        public String summary(){return name+(version==null||version.isEmpty()?"":"  ·  "+version);}
    }

    public interface Callback { void done(boolean ok, String message); }
    private final Context context;
    private final File root;
    private final android.content.SharedPreferences prefs;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();

    public PluginManager(Context c){context=c.getApplicationContext();root=new File(context.getFilesDir(),"plugins");if(!root.exists())root.mkdirs();prefs=context.getSharedPreferences("library",Context.MODE_PRIVATE);}

    public ArrayList<PluginInfo> list(){ArrayList<PluginInfo> out=new ArrayList<>();File[] dirs=root.listFiles();if(dirs==null)return out;for(File d:dirs){if(!d.isDirectory())continue;try{PluginInfo p=parse(new File(d,"plugin.json"));if(p!=null){p.directory=d.getAbsolutePath();p.enabled=prefs.getBoolean("plugin_enabled_"+p.id,true);out.add(p);}}catch(Exception ignored){}}Collections.sort(out,(a,b)->a.name.compareToIgnoreCase(b.name));return out;}

    private PluginInfo parse(File manifest)throws Exception{if(!manifest.isFile())return null;JSONObject o=new JSONObject(read(manifest));PluginInfo p=new PluginInfo();p.id=o.optString("id",manifest.getParentFile().getName());p.name=o.optString("name",p.id);p.version=o.optString("version","");p.main=o.optString("main","index.js");p.playback=o.optString("playback","stream");JSONArray nav=o.optJSONArray("navigation");if(nav!=null)for(int i=0;i<nav.length();i++){JSONObject n=nav.optJSONObject(i);if(n!=null)p.navigation.add(n.optString("title",n.optString("builtin","")));}return p;}

    public void setEnabled(PluginInfo p,boolean enabled){if(p==null)return;p.enabled=enabled;prefs.edit().putBoolean("plugin_enabled_"+p.id,enabled).apply();}
    public void remove(PluginInfo p){if(p==null||p.directory==null)return;delete(new File(p.directory));prefs.edit().remove("plugin_enabled_"+p.id).apply();}

    public void installFromGitHub(String input,Callback cb){final String repo=normalizeRepo(input);if(repo==null){cb.done(false,"请输入 owner/repo 或 GitHub 仓库地址");return;}executor.execute(()->{try{String base="https://api.github.com/repos/"+repo+"/contents/";JSONObject mf=new JSONObject(get(base+"plugin.json"));String id=mf.optString("id");if(id.isEmpty())throw new IOException("plugin.json 缺少 id");if(!id.matches("[A-Za-z0-9][A-Za-z0-9_.-]*"))throw new IOException("插件 id 不合法");String main=mf.optString("main","index.js");JSONObject mainFile=new JSONObject(get(base+main));String manifestText=mf.toString(2);String mainText=decodeContent(mainFile.optString("content"));if(mainText.isEmpty()){String download=mainFile.optString("download_url");if(download.isEmpty())throw new IOException("无法读取插件入口脚本");mainText=get(download);}File target=new File(root,id);if(target.exists())delete(target);if(!target.mkdirs())throw new IOException("无法创建插件目录");write(new File(target,"plugin.json"),manifestText);write(new File(target,main),mainText);String repoText=new JSONObject().put("repo",repo).put("installedAt",System.currentTimeMillis()).toString();write(new File(target,".origin.json"),repoText);cb.done(true,"插件已安装："+mf.optString("name",id));}catch(Exception e){cb.done(false,"插件安装失败："+e.getMessage());}});}

    private String normalizeRepo(String s){if(s==null)return null;s=s.trim();s=s.replace("https://github.com/","").replace("http://github.com/","").replace("www.github.com/","");int q=s.indexOf('?');if(q>=0)s=s.substring(0,q);int h=s.indexOf('#');if(h>=0)s=s.substring(0,h);s=s.replaceAll("/tree/.*$","/").replaceAll("/blob/.*$","/");s=s.replaceAll("/+$","");if(s.endsWith(".git"))s=s.substring(0,s.length()-4);if(!s.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))return null;return s;}
    private String get(String address)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","MyGoMusic-Android");int code=c.getResponseCode();InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();String s=read(in);c.disconnect();if(code<200||code>=300)throw new IOException("GitHub 返回 "+code);return s;}
    private String decodeContent(String value){if(value==null)return "";try{return new String(android.util.Base64.decode(value.replaceAll("\\s",""),android.util.Base64.DEFAULT),"UTF-8");}catch(Exception e){return "";}}
    private static String read(File f)throws Exception{return read(new FileInputStream(f));}
    private static String read(InputStream in)throws Exception{if(in==null)return "";try{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)b.write(buf,0,n);return new String(b.toByteArray(),"UTF-8");}finally{in.close();}}
    private static void write(File f,String s)throws Exception{File parent=f.getParentFile();if(parent!=null&&!parent.exists())parent.mkdirs();FileOutputStream out=new FileOutputStream(f);try{out.write(s.getBytes("UTF-8"));}finally{out.close();}}
    private static void delete(File f){if(f.isDirectory()){File[] cs=f.listFiles();if(cs!=null)for(File c:cs)delete(c);}f.delete();}
}
