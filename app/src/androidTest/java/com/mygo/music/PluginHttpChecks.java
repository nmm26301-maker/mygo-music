package com.mygo.music;

import android.app.Instrumentation;
import android.content.Context;
import android.os.SystemClock;
import org.json.*;
import java.io.*;
import java.net.*;
import java.lang.reflect.Field;
import java.util.concurrent.*;

/** Actual WebView fetch against stalled and oversized native HTTP responses. */
final class PluginHttpChecks {
    private final Instrumentation test;
    private final Context context;
    private final CountDownLatch disconnected=new CountDownLatch(4);
    private final ConcurrentMap<String,CountDownLatch> served=new ConcurrentHashMap<>();
    private final java.util.Set<String> closedPaths=ConcurrentHashMap.newKeySet();
    PluginHttpChecks(Instrumentation test){this.test=test;context=test.getTargetContext();}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private String call(PluginRuntime runtime,String name,JSONObject args,boolean expected)throws Exception{
        CountDownLatch done=new CountDownLatch(1);boolean[] ok={false};String[] value={""};runtime.call(name,args,(success,raw)->{ok[0]=success;value[0]=raw;done.countDown();});
        check(done.await(15,TimeUnit.SECONDS),"fetch lifecycle callback missing: "+name);check(ok[0]==expected,"fetch lifecycle "+name+": "+value[0]);return value[0];
    }
    private void connection(Socket socket){
        try(Socket c=socket){c.setSoTimeout(6000);BufferedReader reader=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));String request=reader.readLine(),line;while((line=reader.readLine())!=null&&!line.isEmpty()){}
            String path=request.split(" ")[1];OutputStream out=c.getOutputStream();
            if(path.equals("/before")){served.computeIfAbsent(path,k->new CountDownLatch(1)).countDown();try{if(c.getInputStream().read()==-1){closedPaths.add(path);disconnected.countDown();}}catch(SocketException e){closedPaths.add(path);disconnected.countDown();}return;}
            byte[] body=path.equals("/binary")?new byte[]{0,1,(byte)255,127}:"{\"ok\":42}".getBytes("UTF-8");
            boolean stalled=path.equals("/probe")||path.equals("/slow")||path.equals("/held");
            int length=stalled?64000000:path.equals("/oversized")?9*1024*1024:body.length;
            out.write(("HTTP/1.1 200 OK\r\nContent-Length: "+length+"\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));out.flush();
            if(stalled){if(path.equals("/slow")){out.write('{');out.flush();}served.computeIfAbsent(path,k->new CountDownLatch(1)).countDown();try{if(c.getInputStream().read()==-1){closedPaths.add(path);disconnected.countDown();}}catch(SocketException e){closedPaths.add(path);disconnected.countDown();}}
            else if(path.equals("/oversized")){byte[] block=new byte[8192];for(int i=0;i<length/block.length;i++)out.write(block);}
            else out.write(body);
        }catch(Exception ignored){}
    }
    void run()throws Exception{
        ServerSocket server=new ServerSocket(0);Thread accept=new Thread(()->{while(!server.isClosed())try{Socket socket=server.accept();Thread worker=new Thread(()->connection(socket));worker.setDaemon(true);worker.start();}catch(IOException ignored){}});accept.setDaemon(true);accept.start();
        String id="fetch-lifecycle-fixture";File dir=new File(context.getCacheDir(),id);dir.mkdirs();
        try(InputStream in=test.getContext().getAssets().open("fetch-lifecycle-fixture.js");FileOutputStream out=new FileOutputStream(new File(dir,"index.js"))){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        try(FileOutputStream out=new FileOutputStream(new File(dir,"plugin.json"))){out.write(("{\"id\":\""+id+"\",\"main\":\"index.js\",\"permissions\":{\"network\":[\"127.0.0.1\"]}}").getBytes("UTF-8"));}
        context.getSharedPreferences("library",0).edit().putBoolean("plugin_network_"+id,true).commit();context.getSharedPreferences("plugin_data_"+id,0).edit().clear().commit();
        PluginRuntime runtime=new PluginRuntime(context,dir);JSONObject args=new JSONObject().put("url","http://127.0.0.1:"+server.getLocalPort());
        try{
            JSONObject probe=new JSONObject(call(runtime,"probe",args,true));check(probe.getInt("status")==200&&probe.getString("length").equals("64000000")&&probe.getBoolean("aborted"),"fetch waited for an ignored Range body or lost abort after headers: "+probe);
            cancelConnected(runtime,args,"/before",false);
            cancelConnected(runtime,args,"/slow",true);
            JSONObject body=new JSONObject(call(runtime,"body",args,true));check(!body.getBoolean("before")&&body.getBoolean("after")&&body.getBoolean("twice")&&body.getJSONObject("value").getInt("ok")==42&&body.getJSONArray("binary").getInt(2)==255,"lazy JSON/binary or single body consumption failed");
            check(call(runtime,"oversized",args,true).contains("8 MB"),"body limit was removed by lazy reading");
            check(new JSONObject(call(runtime,"search",new JSONObject().put("query","recover"),true)).getInt("count")==3,"QQ transient search did not recover");
            call(runtime,"search",new JSONObject().put("query","permanent"),false);call(runtime,"like",new JSONObject(),false);
            JSONObject counts=new JSONObject(call(runtime,"counts",new JSONObject(),true));check(counts.getInt("permanent")==3&&counts.getInt("writes")==1,"search retried without a bound or repeated a library mutation");
            Field field=PluginRuntime.class.getDeclaredField("http");field.setAccessible(true);PluginHttp http=(PluginHttp)field.get(runtime);
            long deadline=SystemClock.elapsedRealtime()+2000;while(http.activeCount()>0&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(20);check(http.activeCount()==0,"consumed or aborted response leaked a native connection");
            call(runtime,"held",args,true);check(http.activeCount()==1,"unread response was eagerly consumed");runtime.dispose();
            check(disconnected.await(5,TimeUnit.SECONDS),"abort/dispose did not close the actual stalled HTTP sockets; closed="+closedPaths+", served="+served.keySet());
            deadline=SystemClock.elapsedRealtime()+2000;while(http.activeCount()>0&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(20);check(http.activeCount()==0,"disposing a plugin kept its unread network response");
        }finally{runtime.dispose();server.close();context.getSharedPreferences("library",0).edit().remove("plugin_network_"+id).commit();PluginArchive.delete(dir);}
    }
    private void cancelConnected(PluginRuntime runtime,JSONObject args,String path,boolean body)throws Exception{
        args.put("path",path);context.getSharedPreferences("plugin_data_fetch-lifecycle-fixture",0).edit().remove("body-started").commit();
        CountDownLatch done=new CountDownLatch(1);String[] value={""};boolean[] ok={false};runtime.call("cancel",args,(success,raw)->{ok[0]=success;value[0]=raw;done.countDown();});
        check(served.computeIfAbsent(path,k->new CountDownLatch(1)).await(5,TimeUnit.SECONDS),"cancellation target was never connected: "+path);
        if(body){long deadline=SystemClock.elapsedRealtime()+5000;while(!context.getSharedPreferences("plugin_data_fetch-lifecycle-fixture",0).getString("body-started","").equals("true")&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(10);check(context.getSharedPreferences("plugin_data_fetch-lifecycle-fixture",0).getString("body-started","").equals("true"),"response body was never requested before cancelling");}
        call(runtime,"abort",new JSONObject(),true);check(done.await(5,TimeUnit.SECONDS)&&ok[0]&&value[0].contains("AbortError"),"connected fetch did not cancel: "+path+" "+value[0]);
    }
}
