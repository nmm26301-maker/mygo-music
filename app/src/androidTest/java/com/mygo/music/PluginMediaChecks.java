package com.mygo.music;

import android.app.Instrumentation;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.os.SystemClock;
import android.widget.ImageView;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** Regression checks use the actual installer, account storage, image loader and background player. */
final class PluginMediaChecks {
    private final Instrumentation test;private final Context context;
    PluginMediaChecks(Instrumentation test){this.test=test;context=test.getTargetContext();}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private interface Condition{boolean ready()throws Exception;}
    private void await(Condition condition,String message)throws Exception{long until=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<until){boolean[] ready={false};test.runOnMainSync(()->{try{ready[0]=condition.ready();}catch(Exception failure){throw new RuntimeException(failure);}});if(ready[0])return;SystemClock.sleep(50);}throw new AssertionError(message);}
    private byte[] zip(JSONObject manifest,String script)throws Exception{ByteArrayOutputStream data=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(data)){for(String name:new String[]{"plugin.json","index.js"}){zip.putNextEntry(new ZipEntry(name));zip.write((name.equals("plugin.json")?manifest.toString():script).getBytes("UTF-8"));zip.closeEntry();}}return data.toByteArray();}
    private void install(PluginManager manager,String repo,JSONObject manifest,String script,boolean expected)throws Exception{CountDownLatch done=new CountDownLatch(1);boolean[] ok={false};String[] message={""};manager.installZipStream(new ByteArrayInputStream(zip(manifest,script)),repo,(success,raw)->{ok[0]=success;message[0]=raw;done.countDown();});check(done.await(35,TimeUnit.SECONDS)&&ok[0]==expected,"repository install: "+message[0]);}
    private PluginManager.PluginInfo info(PluginManager manager,String repo){for(PluginManager.PluginInfo item:manager.list())if(item.repository.equals(repo))return item;throw new AssertionError("source not found: "+repo);}
    private String call(PluginManager manager,String id,String fn,JSONObject args)throws Exception{CountDownLatch done=new CountDownLatch(1);boolean[] ok={false};String[] raw={""};manager.export(id,fn,args,(success,value)->{ok[0]=success;raw[0]=value;done.countDown();});check(done.await(15,TimeUnit.SECONDS)&&ok[0],"fixture export: "+raw[0]);return raw[0];}
    void run()throws Exception{
        String declared="repository-media-fixture",firstRepo="fixture-a/media",secondRepo="fixture-b/media";PluginManager manager=new PluginManager(context);MainActivity activity=null;PlaybackService service=null;
        JSONObject manifest=new JSONObject().put("id",declared).put("main","index.js").put("permissions",new JSONObject().put("network",new JSONArray().put("127.0.0.1")));
        JSONArray rules=new JSONArray().put(new JSONObject().put("hosts",new JSONArray().put("{setting:server}")).put("kinds",new JSONArray().put("image").put("audio")).put("headers",new JSONObject().put("Cookie","token={storage:token}").put("X-Rule","manifest").put("User-Agent","fixture-manifest")));
        manifest.put("requestHeaders",rules);
        try(Server server=new Server();Server redirected=new Server()){
            String script="export function search(){return {tracks:[{id:'protected',name:'Protected audio'}]};}export function save({value}){host.storage.set('token',value);}export function read(){return host.storage.get('token');}export function stream(){return {url:'"+server.base()+"/audio',headers:{'user-agent':'fixture-explicit'}};}";
            install(manager,firstRepo,manifest,script,true);PluginManager.PluginInfo a=info(manager,firstRepo);
            context.getSharedPreferences("library",0).edit().putBoolean("plugin_network_"+a.id,true).commit();context.getSharedPreferences("plugin_settings_"+a.id,0).edit().putString("server",server.base()).commit();
            call(manager,a.id,"save",new JSONObject().put("value","alpha"));
            install(manager,secondRepo,manifest,script,true);PluginManager.PluginInfo b=info(manager,secondRepo);check(!a.id.equals(b.id),"same-id repositories replaced one another");
            context.getSharedPreferences("library",0).edit().putBoolean("plugin_network_"+b.id,true).commit();call(manager,b.id,"save",new JSONObject().put("value","beta"));check(call(manager,a.id,"read",new JSONObject()).equals("\"alpha\"")&&call(manager,b.id,"read",new JSONObject()).equals("\"beta\""),"accounts leaked between repositories");
            install(manager,firstRepo,manifest,script,true);check(info(manager,firstRepo).id.equals(a.id)&&info(manager,secondRepo).id.equals(b.id),"repository update changed source identity");check(call(manager,a.id,"read",new JSONObject()).equals("\"alpha\""),"update discarded saved account");
            HashMap<String,String> headers=manager.mediaHeaders(a.id,server.base()+"/audio","audio",new JSONObject().put("user-agent","fixture-explicit"));check(headers.get("Cookie").equals("token=alpha")&&headers.get("user-agent").equals("fixture-explicit")&&!headers.containsKey("User-Agent"),"requestHeaders merge precedence was not case insensitive");
            call(manager,a.id,"save",new JSONObject().put("value","renewed"));check(manager.mediaHeaders(a.id,server.base()+"/cover","image",null).get("Cookie").equals("token=renewed"),"renewed token was not used immediately");call(manager,a.id,"save",new JSONObject().put("value",""));check(!manager.mediaHeaders(a.id,server.base()+"/cover","image",null).containsKey("Cookie"),"empty token created a credential header");call(manager,a.id,"save",new JSONObject().put("value","alpha"));
            check(manager.mediaHeaders(a.id,"http://localhost/cover","image",null).isEmpty(),"dynamic host escaped network declaration");
            JSONObject invalid=new JSONObject(manifest.toString());invalid.getJSONArray("requestHeaders").getJSONObject(0).getJSONObject("headers").put("Host","forbidden");install(manager,firstRepo,invalid,script,false);check(call(manager,a.id,"read",new JSONObject()).equals("\"alpha\""),"rejected update damaged original source");
            invalid=new JSONObject(manifest.toString());invalid.getJSONArray("requestHeaders").getJSONObject(0).put("hosts",new JSONArray().put("evil.example"));boolean rejected=false;try{PluginMedia.validate(invalid);}catch(Exception expected){rejected=true;}check(rejected,"header rule outside network declaration accepted");
            server.redirect=redirected.base()+"/destination";try(PluginMedia.Connection connection=PluginMedia.open(server.base()+"/redirect",headers,false)){check(connection.headers.isEmpty(),"redirect retained account headers on another port");try(InputStream input=connection.http.getInputStream()){input.read();}}check(!redirected.destinationAuthenticated,"account header leaked to a redirected origin");
            activity=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));MainActivity current=activity;java.lang.reflect.Field field=MainActivity.class.getDeclaredField("playbackService");field.setAccessible(true);PlaybackService[] bound={null};await(()->{bound[0]=(PlaybackService)field.get(current);return bound[0]!=null;},"media test could not bind playback service");service=bound[0];PlaybackService player=service;
            test.runOnMainSync(()->{try{PlaybackService.startQueue(current,new JSONArray().put(new JSONObject().put("uri","plugin://"+a.id+"?id=test").put("title","Protected audio")),0,0,80,false,false,"high");}catch(Exception failure){throw new RuntimeException(failure);}});await(()->player.snapshot().playing,"manifest audio headers did not reach native background playback");check(server.audioAuthenticated&&server.explicitAgent,"protected audio lost token or explicit user-agent");
            ImageView[] view={null};test.runOnMainSync(()->{try{view[0]=new ImageView(current);java.lang.reflect.Method load=MainActivity.class.getDeclaredMethod("loadGridArtwork",ImageView.class,String.class,String.class);load.setAccessible(true);load.invoke(current,view[0],server.base()+"/cover",a.id);}catch(Exception failure){throw new RuntimeException(failure);}});await(()->view[0].getDrawable() instanceof BitmapDrawable,"protected cover did not load through the real artwork loader");check(server.coverAuthenticated,"cover request lost its saved account");
            PluginCheckDialog[] diagnostic={null};CountDownLatch selected=new CountDownLatch(1);test.runOnMainSync(()->{diagnostic[0]=new PluginCheckDialog(current,manager,a,()->{},()->{},song->{if(song.optString("id").equals("protected"))selected.countDown();});diagnostic[0].show();});test.waitForIdleSync();test.runOnMainSync(()->{try{java.lang.reflect.Field dialogField=PluginCheckDialog.class.getDeclaredField("dialog");dialogField.setAccessible(true);((android.app.AlertDialog)dialogField.get(diagnostic[0])).getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();}catch(Exception failure){throw new RuntimeException(failure);}});
            java.lang.reflect.Field listenField=PluginCheckDialog.class.getDeclaredField("listen");listenField.setAccessible(true);await(()->((android.widget.Button)listenField.get(diagnostic[0])).isEnabled(),"installed-source diagnostic did not verify authenticated search and audio");test.runOnMainSync(()->{try{((android.widget.Button)listenField.get(diagnostic[0])).performClick();}catch(Exception failure){throw new RuntimeException(failure);}});check(selected.await(2,TimeUnit.SECONDS),"diagnostic listen button lost the verified source track");
        }finally{
            MainActivity last=activity;PlaybackService player=service;test.runOnMainSync(()->{if(player!=null)player.stopPlayback();if(last!=null)last.finish();manager.close();});
            for(PluginManager.PluginInfo item:manager.list())if(item.repository.equals(firstRepo)||item.repository.equals(secondRepo)){PluginArchive.delete(new File(item.directory));context.getSharedPreferences("library",0).edit().remove("plugin_network_"+item.id).remove("plugin_enabled_"+item.id).commit();context.getSharedPreferences("plugin_data_"+item.id,0).edit().clear().commit();context.getSharedPreferences("plugin_settings_"+item.id,0).edit().clear().commit();}
        }
    }
    private static final class Server implements AutoCloseable{
        final ServerSocket socket=new ServerSocket(0);volatile String redirect="";volatile boolean audioAuthenticated,coverAuthenticated,explicitAgent,destinationAuthenticated;
        Server()throws Exception{Thread worker=new Thread(()->{while(!socket.isClosed())try(Socket client=socket.accept()){
            client.setSoTimeout(5000);BufferedReader input=new BufferedReader(new InputStreamReader(client.getInputStream(),"US-ASCII"));String request=input.readLine(),line;boolean token=false,rule=false,agent=false;
            while((line=input.readLine())!=null&&!line.isEmpty()){if(line.equalsIgnoreCase("Cookie: token=alpha"))token=true;if(line.equalsIgnoreCase("X-Rule: manifest"))rule=true;if(line.equalsIgnoreCase("User-Agent: fixture-explicit"))agent=true;}
            if(request.contains(" /redirect ")){client.getOutputStream().write(("HTTP/1.1 302 Found\r\nLocation: "+redirect+"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));continue;}
            boolean cover=request.contains(" /cover "),destination=request.contains(" /destination ");if(destination)destinationAuthenticated=token||rule;
            if(cover)coverAuthenticated=token&&rule;else if(!destination){audioAuthenticated=token&&rule;explicitAgent=agent;}
            byte[] body;String type;if(cover){Bitmap bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.RED);ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);body=bytes.toByteArray();type="image/png";}else{body=BackgroundPlaybackChecks.wav(15000);type="audio/wav";}
            boolean allowed=destination||token&&rule;client.getOutputStream().write(("HTTP/1.1 "+(allowed?"200 OK":"401 Unauthorized")+"\r\nContent-Type: "+type+"\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));client.getOutputStream().write(body);
        }catch(Exception ignored){}});worker.setDaemon(true);worker.start();}
        String base(){return "http://127.0.0.1:"+socket.getLocalPort();}
        public void close()throws Exception{socket.close();}
    }
}
