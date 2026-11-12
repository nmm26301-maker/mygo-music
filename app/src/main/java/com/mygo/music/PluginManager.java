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
        public String id, declaredId, name, version, main, playback;
        public String directory;
        public String repository="";
        public boolean enabled;
        public final ArrayList<String> navigation = new ArrayList<>();
        public final LinkedHashMap<String,String> navigationPages = new LinkedHashMap<>();
        public final HashSet<String> functions=new HashSet<>();
        public boolean supports(String name){return functions.isEmpty()||functions.contains(name);}
        public JSONArray network=new JSONArray(), settings=new JSONArray(), programs=new JSONArray();
        public String summary(){return name+(version==null||version.isEmpty()?"":"  ·  "+version);}
    }

    public static final class PluginTrack {
        public String pluginId="", id="", name="", artist="", url="", pic="";
    }

    public interface SearchCallback { void done(ArrayList<PluginTrack> tracks); }

    public interface Callback { void done(boolean ok, String message); }
    private volatile boolean closed;
    private final Context context;
    private final File root;
    private final android.content.SharedPreferences prefs;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final HashMap<String,PluginRuntime> runtimes=new HashMap<>();
    private final HashMap<String,Long> runtimeRevisions=new HashMap<>();
    private long revision(String id){return prefs.getLong("plugin_revision_"+id,0);}
    private long bumpRevision(String id){long value=Math.max(System.nanoTime(),revision(id)+1);prefs.edit().putLong("plugin_revision_"+id,value).apply();return value;}

    public PluginManager(Context c){context=c;root=new File(context.getFilesDir(),"plugins");if(!root.exists())root.mkdirs();prefs=context.getSharedPreferences("library",Context.MODE_PRIVATE);}

    public ArrayList<PluginInfo> list(){ArrayList<PluginInfo> out=new ArrayList<>();File[] dirs=root.listFiles();if(dirs==null)return out;for(File d:dirs){if(!d.isDirectory()||d.getName().startsWith("."))continue;try{PluginInfo p=parse(new File(d,"plugin.json"));if(p!=null){p.directory=d.getAbsolutePath();File origin=new File(d,".origin.json");if(origin.isFile())try{p.repository=new JSONObject(read(origin)).optString("repo","");}catch(Exception ignored){}File caps=new File(d,".runtime.json");if(caps.isFile())try{JSONArray names=new JSONObject(read(caps)).getJSONArray("exports");for(int i=0;i<names.length();i++)p.functions.add(names.getString(i));}catch(Exception ignored){}p.enabled=prefs.getBoolean("plugin_enabled_"+p.id,true);out.add(p);}}catch(Exception ignored){}}Collections.sort(out,(a,b)->a.name.compareToIgnoreCase(b.name));return out;}

    private PluginInfo parse(File manifest)throws Exception{if(!manifest.isFile())return null;JSONObject o=new JSONObject(read(manifest));JSONObject translations=new JSONObject();File locale=new File(manifest.getParentFile(),"locales/zh-CN.json");if(locale.isFile())try{translations=new JSONObject(read(locale));}catch(Exception ignored){}PluginInfo p=new PluginInfo();p.declaredId=o.optString("id",manifest.getParentFile().getName());p.id=PluginIdentity.instance(manifest.getParentFile(),o);p.name=o.optString("name",p.id);p.name=translations.optString(p.name,p.name);p.version=o.optString("version","");p.main=o.optString("main","index.js");p.playback=o.optString("playback","stream");JSONObject permissions=o.optJSONObject("permissions");if(permissions!=null&&permissions.optJSONArray("network")!=null)p.network=permissions.getJSONArray("network");if(permissions!=null){Object process=permissions.opt("process");if(Boolean.TRUE.equals(process))p.programs.put("*");else if(process instanceof JSONArray)p.programs=(JSONArray)process;}if(o.optJSONArray("settings")!=null){p.settings=o.getJSONArray("settings");for(int index=0;index<p.settings.length();index++){JSONObject setting=p.settings.optJSONObject(index);if(setting!=null)for(String key:new String[]{"label","help"})if(setting.has(key))setting.put(key,translations.optString(setting.optString(key),setting.optString(key)));}}JSONArray nav=o.optJSONArray("navigation");if(nav!=null)for(int i=0;i<nav.length();i++){JSONObject n=nav.optJSONObject(i);if(n!=null){String label=n.optString("title",n.optString("builtin",""));String target=n.optString("page",n.optString("builtin",""));if(label.isEmpty())label=target;label=translations.optString(label,label);if(!label.isEmpty()){p.navigation.add(label);p.navigationPages.put(label,target);}}}return p;}

    public void restart(String id){bumpRevision(id);PluginRuntime runtime=runtimes.remove(id);runtimeRevisions.remove(id);if(runtime!=null)runtime.dispose();}
    public void setEnabled(PluginInfo p,boolean enabled){if(p==null)return;if(!enabled)restart(p.id);p.enabled=enabled;prefs.edit().putBoolean("plugin_enabled_"+p.id,enabled).apply();}
    public void remove(PluginInfo p){if(p==null||p.directory==null)return;bumpRevision(p.id);prefs.edit().putBoolean("plugin_network_"+p.id,false).putBoolean("plugin_process_"+p.id,false).apply();runtimeRevisions.remove(p.id);PluginRuntime rt=runtimes.remove(p.id);if(rt!=null)rt.dispose();delete(new File(p.directory));prefs.edit().remove("plugin_enabled_"+p.id).apply();}

    public void search(String query,SearchCallback callback){new Handler(Looper.getMainLooper()).post(()->{ArrayList<PluginInfo> all=list();ArrayList<PluginInfo> enabled=new ArrayList<>();for(PluginInfo p:all)if(p.enabled)enabled.add(p);searchPlugin(enabled,0,query,new ArrayList<>(),callback);});}
    public void searchOne(String pluginId,String query,SearchCallback callback){new Handler(Looper.getMainLooper()).post(()->{ArrayList<PluginInfo> one=new ArrayList<>();for(PluginInfo p:list())if(p.enabled&&p.id.equals(pluginId)){one.add(p);break;}searchPlugin(one,0,query,new ArrayList<>(),callback);});}
    public void lyrics(String pluginId,String trackId,PluginRuntime.Callback cb){callTrackExport(pluginId,"lyrics",new JSONObjectBuilder().put("id",trackId).value(),cb);}
    public void page(String pluginId,String function,String page,PluginRuntime.Callback cb){callTrackExport(pluginId,function,new JSONObjectBuilder().put("page",page).value(),cb);}
    public void comments(String pluginId,String trackId,PluginRuntime.Callback cb){callTrackExport(pluginId,"comments",new JSONObjectBuilder().put("trackId",trackId).put("offset",0).value(),cb);}
    public void postComment(String pluginId,String trackId,String body,PluginRuntime.Callback cb){callTrackExport(pluginId,"postComment",new JSONObjectBuilder().put("trackId",trackId).put("text",body).value(),cb);}
    public void export(String pluginId,String function,JSONObject args,PluginRuntime.Callback cb){callTrackExport(pluginId,function,args,cb);}
    public void searchSource(String pluginId,String query,PluginRuntime.Callback cb){callTrackExport(pluginId,"search",new JSONObjectBuilder().put("query",query).put("kind","tracks").put("offset",0).value(),cb);}
    public void authorizeSource(String pluginId,PluginRuntime.Callback callback){new Handler(Looper.getMainLooper()).post(()->{PluginInfo source=null;for(PluginInfo item:list())if(item.id.equals(pluginId)&&item.enabled){source=item;break;}if(source==null){callback.done(false,"插件未启用");return;}String permission="plugin_network_"+pluginId;if(source.network.length()==0||prefs.getBoolean(permission,false)){callback.done(true,"");return;}if(prefs.contains(permission)||!(context instanceof android.app.Activity)){callback.done(false,"@mygo:setup:"+pluginId+"\n请在插件设置中允许网络访问");return;}new android.app.AlertDialog.Builder(context).setTitle("允许插件访问网络").setMessage(source.name+" 申请访问：\n"+source.network.toString()).setPositiveButton("允许",(dialog,which)->{prefs.edit().putBoolean(permission,true).apply();callback.done(true,"");}).setNegativeButton("拒绝",(dialog,which)->{prefs.edit().putBoolean(permission,false).apply();callback.done(false,"插件网络访问已拒绝");}).setOnCancelListener(dialog->callback.done(false,"已取消授权")).show();});}
    private void callTrackExport(String pluginId,String function,JSONObject args,PluginRuntime.Callback callback){new Handler(Looper.getMainLooper()).post(()->{
        if(closed){callback.done(false,"插件运行环境已关闭");return;}
        PluginInfo found=null;for(PluginInfo p:list())if(p.id.equals(pluginId)&&p.enabled){found=p;break;}
        if(found==null){callback.done(false,"插件未启用");return;}final PluginInfo target=found;
        Runnable run=()->{PluginRuntime rt=runtimes.get(pluginId);if(rt==null||rt.isClosed()||!Objects.equals(runtimeRevisions.get(pluginId),revision(pluginId))){if(rt!=null)rt.dispose();rt=new PluginRuntime(context,new File(target.directory));runtimes.put(pluginId,rt);runtimeRevisions.put(pluginId,revision(pluginId));}rt.call(function,args,(ok,raw)->{if(ok&&(function.equals("signIn")||function.equals("signOut")||function.equals("setSetting")))runtimeRevisions.put(pluginId,bumpRevision(pluginId));if(!closed)callback.done(ok,ok?raw:failure(pluginId,raw));});};
        String permission="plugin_network_"+pluginId;
        if(target.network.length()>0&&!prefs.contains(permission)){
            if(!(context instanceof android.app.Activity)){callback.done(false,"@mygo:setup:"+pluginId+"\n请先在前台允许此插件访问网络");return;}
            new android.app.AlertDialog.Builder(context).setTitle("允许插件访问网络").setMessage(target.name+" 申请访问：\n"+target.network.toString()+"\n可在插件网络权限中撤回。")
                .setPositiveButton("允许",(d,w)->{prefs.edit().putBoolean(permission,true).apply();run.run();})
                .setNegativeButton("拒绝",(d,w)->{prefs.edit().putBoolean(permission,false).apply();callback.done(false,"插件网络访问已拒绝");}).setOnCancelListener(d->callback.done(false,"已取消授权")).show();
        }else run.run();
    });}
    static String failure(String plugin,String raw){try{JSONObject error=new JSONObject(raw);if(error.optBoolean("__mygoError")){String code=error.optString("code");String message=error.optString("message","插件调用失败");if(code.equals("signIn")||code.equals("setup"))return "@mygo:"+code+":"+plugin+"\n"+message;return message;}}catch(Exception ignored){}return raw;}
    public void close(){closed=true;executor.shutdownNow();for(PluginRuntime rt:runtimes.values())rt.dispose();runtimes.clear();}
    private static final class JSONObjectBuilder{private final JSONObject o=new JSONObject();JSONObjectBuilder put(String k,Object v){try{o.put(k,v);}catch(Exception ignored){}return this;}JSONObject value(){return o;}}
    private void searchPlugin(ArrayList<PluginInfo> plugins,int index,String query,ArrayList<PluginTrack> out,SearchCallback cb){if(index>=plugins.size()){cb.done(out);return;}PluginInfo p=plugins.get(index);searchSource(p.id,query,(ok,value)->{if(ok)for(PluginTrack t:parseTracks(value)){t.pluginId=p.id;t.url=new android.net.Uri.Builder().scheme("plugin").authority(p.id).appendQueryParameter("id",t.id).build().toString();out.add(t);}searchPlugin(plugins,index+1,query,out,cb);});}
    private ArrayList<PluginTrack> parseTracks(String raw){ArrayList<PluginTrack> out=new ArrayList<>();try{JSONObject root=new JSONObject(raw);JSONArray a=root.optJSONArray("tracks");if(a==null)return out;for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;PluginTrack t=new PluginTrack();t.id=o.optString("id","");t.name=o.optString("name",o.optString("title",""));t.url=o.optString("url","");t.pic=o.optString("imageUrl",o.optString("pic",""));JSONArray artists=o.optJSONArray("artists");if(artists!=null&&artists.length()>0&&artists.optJSONObject(0)!=null)t.artist=artists.optJSONObject(0).optString("name","");if(t.artist.isEmpty())t.artist=o.optString("artist","");if(!t.name.isEmpty())out.add(t);}}catch(Exception ignored){}return out;}

    private final HashMap<String,JSONObject> marketChecks=new HashMap<>();private boolean marketChecksLoaded;
    public JSONObject marketCheck(String repository){if(!marketChecksLoaded){marketChecksLoaded=true;try{JSONArray rows=new JSONObject(read(context.getAssets().open("plugin-audit.json"))).getJSONArray("results");for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);marketChecks.put(row.getString("repo").toLowerCase(Locale.ROOT),row);}}catch(Exception ignored){}}return marketChecks.get(repository.toLowerCase(Locale.ROOT));}
    public static String checkSummary(JSONObject check){
        if(check==null)return "尚无安卓实测记录";
        if(!check.optString("install").equals("passed"))return "安卓安装或加载未通过，查看检查结果";
        if(check.optString("androidDecoder").equals("prepared"))return "匿名歌曲样本通过，账号功能待验证";
        if(check.optString("stream").equals("requires_signed_in_remote_device"))return "搜索通过，播放需登录并配置远程设备";
        if(check.optString("stream").equals("http_passed"))return "音频连接通过，实际播放待验证";
        if(check.optString("search").equals("passed"))return "搜索通过，播放条件请查看检查结果";
        if(check.optString("search").equals("not_exported"))return "已加载，请从插件导航使用专有功能";
        return "已加载，搜索未通过，请查看检查结果";
    }
    public JSONArray marketSnapshot(){try{String cached=prefs.getString("plugin_market","");return new JSONArray(cached.isEmpty()?read(context.getAssets().open("plugin-market.json")):cached);}catch(Exception e){return new JSONArray();}}
    public void refreshMarket(Callback callback){executor.execute(()->{try{JSONArray out=new JSONArray();for(int page=1;page<=10;page++){JSONObject answer=new JSONObject(get("https://api.github.com/search/repositories?q=topic%3Amygo-music-plugin%20archived%3Afalse&sort=stars&per_page=100&page="+page));JSONArray items=answer.getJSONArray("items");for(int i=0;i<items.length();i++){JSONObject repo=items.getJSONObject(i);out.put(new JSONObject().put("repo",repo.getString("full_name")).put("name",repo.getString("name")).put("description",repo.optString("description","")).put("stars",repo.optInt("stargazers_count")));}if(items.length()<100)break;}if(out.length()==0)throw new IOException("插件目录为空");prefs.edit().putString("plugin_market",out.toString()).apply();callback.done(true,"已刷新 "+out.length()+" 个插件");}catch(Exception e){callback.done(false,"刷新失败，已保留本机目录："+e.getMessage());}});}
    public void installFromGitHub(String input,Callback cb){
        final String repo=normalizeRepo(input);if(repo==null){cb.done(false,"请输入 owner/repo 或 GitHub 仓库地址");return;}
        executor.execute(()->{File temp=null;try{
            temp=File.createTempFile("plugin-","",context.getCacheDir());temp.delete();temp.mkdirs();File candidate=null;String failure="";
            // Platform-specific desktop releases cannot be used as Android executables.
            try{JSONObject release=new JSONObject(get("https://api.github.com/repos/"+repo+"/releases/latest"));JSONArray assets=release.optJSONArray("assets");if(assets!=null)for(int i=0;i<assets.length();i++){JSONObject asset=assets.getJSONObject(i);String name=asset.optString("name").toLowerCase(Locale.ROOT);boolean android=name.contains("android"),neutral=!name.matches(".*(darwin|macos|windows|win32|linux|android).*" );if(!name.endsWith(".zip")||(!android&&!neutral))continue;if(android&&!androidAssetMatches(name))continue;try{candidate=downloadArchive(asset.getString("browser_download_url"),new File(temp,"release-"+i));break;}catch(Exception e){failure=e.getMessage();}}}catch(Exception ignored){}
            // Author-built packages are essential when source archives omit the entry script.
            // A verified fallback also works when GitHub's anonymous API is rate limited.
            if(candidate==null)try{JSONArray verified=new JSONArray(read(context.getAssets().open("plugin-packages.json")));for(int i=0;i<verified.length();i++){JSONObject item=verified.getJSONObject(i);if(item.getString("repo").equalsIgnoreCase(repo)){candidate=downloadArchive(item.getString("url"),new File(temp,"verified-release"),item.getString("sha256"));break;}}}catch(Exception error){failure=error.getMessage();}
            if(candidate==null){ArrayList<String> refs=new ArrayList<>(Arrays.asList("main","master"));try{String branch=new JSONObject(get("https://api.github.com/repos/"+repo)).optString("default_branch");if(!branch.isEmpty()&&!refs.contains(branch))refs.add(0,branch);}catch(Exception ignored){}for(int i=0;i<refs.size();i++)try{candidate=downloadArchive("https://codeload.github.com/"+repo+"/zip/refs/heads/"+refs.get(i),new File(temp,"source-"+i));break;}catch(Exception e){failure=e.getMessage();}}
            if(candidate==null)throw new IOException("无法下载完整插件包："+failure);
            publish(candidate,repo,cb);
        }catch(Exception e){if(!closed)cb.done(false,"插件安装失败："+message(e));}finally{PluginArchive.delete(temp);}});
    }
    private boolean androidAssetMatches(String name){String arch=android.os.Build.SUPPORTED_ABIS[0];if(name.contains("arm64")||name.contains("aarch64"))return arch.equals("arm64-v8a");if(name.contains("amd64")||name.contains("x86_64")||name.contains("x64"))return arch.equals("x86_64");if(name.contains("armeabi")||name.contains("armv7"))return arch.startsWith("armeabi");return true;}
    public boolean networkAllowed(String id){if(prefs.getBoolean("plugin_network_"+id,false))return true;try{PluginInfo info=parse(new File(new File(root,id),"plugin.json"));return info!=null&&info.network.length()==0;}catch(Exception error){return false;}}
    public HashMap<String,String> mediaHeaders(String pluginId,String address,String kind,JSONObject explicit)throws Exception{
        PluginInfo source=null;
        if(!pluginId.isEmpty()){
            if(!pluginId.matches("[A-Za-z0-9][A-Za-z0-9_.-]*"))throw new IOException("资源来源标识无效");
            File directory=new File(root,pluginId);source=parse(new File(directory,"plugin.json"));
            if(source==null||!source.id.equals(pluginId)||!prefs.getBoolean("plugin_enabled_"+pluginId,true))throw new IOException("资源来源插件未启用");source.directory=directory.getAbsolutePath();
        }
        return PluginMedia.headers(context,source,address,kind,explicit);
    }
    private File downloadArchive(String address,File destination)throws Exception{return downloadArchive(address,destination,"");}
    private File downloadArchive(String address,File destination,String expectedHash)throws Exception{
        HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();connection.setConnectTimeout(15000);connection.setReadTimeout(20000);connection.setRequestProperty("User-Agent","MyGoMusic-Android");try{int status=connection.getResponseCode();if(status<200||status>=300)throw new IOException("GitHub 下载返回 "+status);if(connection.getContentLength()>PluginArchive.MAX_DOWNLOAD)throw new IOException("插件压缩包超过 32 MB");File archive=File.createTempFile("plugin-download-",".zip",context.getCacheDir());try{try(InputStream in=connection.getInputStream();OutputStream out=new FileOutputStream(archive)){byte[] buffer=new byte[8192];long total=0;int n;while((n=in.read(buffer))!=-1){total+=n;if(total>PluginArchive.MAX_DOWNLOAD)throw new IOException("插件压缩包超过 32 MB");out.write(buffer,0,n);}}if(!expectedHash.isEmpty()){java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");try(InputStream bytes=new FileInputStream(archive)){byte[] part=new byte[8192];int count;while((count=bytes.read(part))!=-1)digest.update(part,0,count);}StringBuilder actual=new StringBuilder();for(byte value:digest.digest())actual.append(String.format(Locale.ROOT,"%02x",value&255));if(!actual.toString().equalsIgnoreCase(expectedHash))throw new IOException("插件发布包校验失败");}return PluginArchive.extract(new FileInputStream(archive),destination);}finally{archive.delete();}}finally{connection.disconnect();}
    }
    public void installZip(android.net.Uri uri,Callback cb){executor.execute(()->{File temp=null;try{temp=File.createTempFile("local-plugin-","",context.getCacheDir());temp.delete();temp.mkdirs();InputStream input=context.getContentResolver().openInputStream(uri);if(input==null)throw new IOException("无法打开插件 ZIP");File candidate=PluginArchive.extract(input,temp);publish(candidate,"local",cb);}catch(Exception e){if(!closed)cb.done(false,"插件安装失败："+message(e));}finally{PluginArchive.delete(temp);}});}
    void installZipStream(InputStream input,Callback cb){installZipStream(input,"local",cb);}
    void installZipStream(InputStream input,String repository,Callback cb){executor.execute(()->{File temp=null;try{temp=File.createTempFile("test-plugin-","",context.getCacheDir());temp.delete();temp.mkdirs();publish(PluginArchive.extract(input,temp),repository,cb);}catch(Exception e){if(!closed)cb.done(false,"插件安装失败："+message(e));}finally{PluginArchive.delete(temp);}});}
    private void publish(File candidate,String repo,Callback cb)throws Exception{
        JSONObject manifest=new JSONObject(read(new File(candidate,"plugin.json")));PluginProtocol.validate(manifest);PluginMedia.validate(manifest);String declaredId=manifest.optString("id");if(!declaredId.matches("[A-Za-z0-9][A-Za-z0-9_.-]*"))throw new IOException("插件 id 不合法");
        String chosen=declaredId;boolean sameRepository=false;
        for(PluginInfo installed:list())if(installed.declaredId.equals(declaredId)&&installed.repository.equalsIgnoreCase(repo)){chosen=installed.id;sameRepository=true;break;}
        if(!sameRepository&&new File(root,chosen).exists())chosen=PluginIdentity.separate(declaredId,repo);
        final String id=chosen;
        String entry=manifest.optString("main","index.js");new PluginResources(candidate).file(android.net.Uri.parse(PluginResources.ORIGIN+PluginResources.PREFIX+entry));
        File staging=new File(root,".install-"+id);PluginArchive.delete(staging);try{
            PluginArchive.copy(candidate,staging);write(new File(staging,".origin.json"),new JSONObject().put("repo",repo).put("instanceId",id).put("installedAt",System.currentTimeMillis()).toString());
            java.util.concurrent.CountDownLatch loaded=new java.util.concurrent.CountDownLatch(1);boolean[] valid={false};String[] names={"插件加载超时"};PluginRuntime validation=new PluginRuntime(context,staging);validation.validate((ok,value)->{valid[0]=ok;names[0]=value;loaded.countDown();});boolean finished;try{finished=loaded.await(60,java.util.concurrent.TimeUnit.SECONDS);}finally{validation.dispose();}if(!finished||!valid[0])throw new IOException("插件脚本无法运行："+names[0]);JSONArray exports=new JSONArray(names[0]);if(exports.length()==0)throw new IOException("插件未提供可调用接口");if("remote".equals(manifest.optString("playback"))){HashSet<String> available=new HashSet<>();for(int i=0;i<exports.length();i++)available.add(exports.getString(i));if(!available.contains("remotePlay")||!available.contains("remoteState"))throw new IOException("远程播放插件缺少 remotePlay 或 remoteState 接口");}write(new File(staging,".runtime.json"),new JSONObject().put("exports",exports).toString());
            File target=new File(root,id),backup=new File(root,".backup-"+id);boolean changed=false;if(target.exists())try{JSONObject previous=new JSONObject(read(new File(target,"plugin.json")));changed=!String.valueOf(previous.opt("permissions")).equals(String.valueOf(manifest.opt("permissions")));}catch(Exception ignored){}
            PluginArchive.delete(backup);if(target.exists()&&!target.renameTo(backup))throw new IOException("无法备份原插件");if(!staging.renameTo(target)){backup.renameTo(target);throw new IOException("无法保存插件，已恢复原版本");}PluginArchive.delete(backup);if(changed)prefs.edit().remove("plugin_network_"+id).remove("plugin_process_"+id).apply();bumpRevision(id);prefs.edit().putBoolean("plugin_enabled_"+id,true).apply();new Handler(Looper.getMainLooper()).post(()->{PluginRuntime old=runtimes.remove(id);if(old!=null)old.dispose();if(!closed)cb.done(true,"插件已安装："+manifest.optString("name",id));});
        }finally{PluginArchive.delete(staging);}
    }
    private static String message(Exception e){return e.getMessage()==null?e.toString():e.getMessage();}
    private String normalizeRepo(String s){if(s==null)return null;s=s.trim();s=s.replace("https://github.com/","").replace("http://github.com/","").replace("www.github.com/","");int q=s.indexOf('?');if(q>=0)s=s.substring(0,q);int h=s.indexOf('#');if(h>=0)s=s.substring(0,h);s=s.replaceAll("/tree/.*$","/").replaceAll("/blob/.*$","/");s=s.replaceAll("/+$","");if(s.endsWith(".git"))s=s.substring(0,s.length()-4);if(!s.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))return null;return s;}
    private String getRaw(String address)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","MyGoMusic-Android");int code=c.getResponseCode();InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();String s=read(in);c.disconnect();if(code<200||code>=300)throw new IOException("GitHub 原始文件返回 "+code);return s;}
    private String get(String address)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","MyGoMusic-Android");c.setRequestProperty("Accept","application/vnd.github+json");int code=c.getResponseCode();InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();String s=read(in);c.disconnect();if(code<200||code>=300)throw new IOException("GitHub 返回 "+code);return s;}
    private String decodeContent(String value){if(value==null)return "";try{return new String(android.util.Base64.decode(value.replaceAll("\\s",""),android.util.Base64.DEFAULT),"UTF-8");}catch(Exception e){return "";}}
    private static String read(File f)throws Exception{return read(new FileInputStream(f));}
    private static String read(InputStream in)throws Exception{if(in==null)return "";try{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)b.write(buf,0,n);return new String(b.toByteArray(),"UTF-8");}finally{in.close();}}
    private static void write(File f,String s)throws Exception{File parent=f.getParentFile();if(parent!=null&&!parent.exists())parent.mkdirs();FileOutputStream out=new FileOutputStream(f);try{out.write(s.getBytes("UTF-8"));}finally{out.close();}}
    private static void delete(File f){if(f.isDirectory()){File[] cs=f.listFiles();if(cs!=null)for(File c:cs)delete(c);}f.delete();}
}
