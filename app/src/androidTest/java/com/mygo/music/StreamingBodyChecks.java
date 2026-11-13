package com.mygo.music;

import android.app.Instrumentation;
import android.content.Context;
import android.os.SystemClock;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.security.MessageDigest;

/** The actual native socket must yield a prefix before EOF and close when the reader cancels. */
final class StreamingBodyChecks {
    final Instrumentation test;final Context context;final CompletionChecks helper;
    StreamingBodyChecks(Instrumentation test){this.test=test;context=test.getTargetContext();helper=new CompletionChecks(test);}
    void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    String asset(String name)throws Exception{try(InputStream in=test.getContext().getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
    PluginRuntime fixture(String id,String source)throws Exception{File dir=new File(context.getCacheDir(),id);dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,"plugin.json"))){out.write(new JSONObject().put("id",id).put("main","index.js").put("permissions",new JSONObject().put("network",new JSONArray().put("127.0.0.1"))).toString().getBytes("UTF-8"));}try(FileOutputStream out=new FileOutputStream(new File(dir,"index.js"))){out.write(source.getBytes("UTF-8"));}try(FileOutputStream out=new FileOutputStream(new File(dir,"stream-local.json"))){out.write("local-body".getBytes("UTF-8"));}context.getSharedPreferences("library",0).edit().putBoolean("plugin_network_"+id,true).commit();return new PluginRuntime(context,dir);}
    String call(PluginRuntime runtime,String fn,JSONObject args)throws Exception{CountDownLatch done=new CountDownLatch(1);boolean[] success={false};String[] value={""};runtime.call(fn,args,(ok,raw)->{success[0]=ok;value[0]=raw;done.countDown();});check(done.await(20,TimeUnit.SECONDS)&&success[0],"native stream "+fn+": "+value[0]);return value[0];}
    void cleanup(String id){PluginArchive.delete(new File(context.getCacheDir(),id));context.getSharedPreferences("library",0).edit().remove("plugin_network_"+id).commit();context.getSharedPreferences("plugin_data_"+id,0).edit().clear().commit();}
    void run()throws Exception{
        String id="stream-body-fixture",originalId="stream-original-qishui";cleanup(id);cleanup(originalId);PluginRuntime runtime=fixture(id,asset("stream-body-fixture.js")),original=null;
        try(Server server=new Server()){
            JSONObject args=new JSONObject().put("url",server.base());JSONObject prefix=new JSONObject(call(runtime,"prefix",args));check(prefix.getInt("bytes")==3&&prefix.getInt("time")<2000&&prefix.getBoolean("used"),"prefix waited for the whole stalled body");
            CountDownLatch utf8Done=new CountDownLatch(1);boolean[] success={false};String[] result={""};runtime.call("utf8",args,(ok,raw)->{success[0]=ok;result[0]=raw;utf8Done.countDown();});helper.await(()->context.getSharedPreferences("plugin_data_"+id,0).getString("first-part","").equals("true"),"first UTF-8 chunk never reached the actual plugin before EOF");server.releaseUtf8.countDown();check(utf8Done.await(10,TimeUnit.SECONDS)&&success[0],"UTF-8 stream failed: "+result[0]);JSONObject utf8=new JSONObject(result[0]);check(utf8.getInt("chunks")>=2&&utf8.getJSONObject("value").getString("text").equals("逐字歌词"),"multibyte UTF-8 was corrupted across native chunks");
            check(new JSONObject(call(runtime,"unread",args)).getBoolean("used"),"cancel did not disturb the unread body");CountDownLatch pending=new CountDownLatch(1);runtime.call("pending",args,(ok,raw)->{success[0]=ok;result[0]=raw;pending.countDown();});helper.await(()->context.getSharedPreferences("plugin_data_"+id,0).getString("stream-waiting","").equals("true"),"pending stream read did not start");call(runtime,"abortStream",new JSONObject());check(pending.await(5,TimeUnit.SECONDS)&&success[0]&&result[0].contains("AbortError"),"abort did not reject an active native stream read");
            JSONObject values=new JSONObject(call(runtime,"primitives",args));check(values.getJSONObject("value").getInt("ok")==42&&values.getBoolean("twice")&&values.getBoolean("head")&&values.getString("cookie").startsWith("fixture=")&&values.getString("local").equals("local-body")&&values.getString("url").endsWith("/json"),"native Response metadata, clone, local stream or consumption semantics changed");
            String source=asset("provider-fixes/qishui-original.js");StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(source.getBytes("UTF-8")))hash.append(String.format(Locale.ROOT,"%02x",b&255));check(hash.toString().equals("d004a53dc1e6a63546919cbb92a662121dd6691b5cfe296d54bd6f7ab8cbe1c5"),"unverified Qishui original source");
            source+="\nexport async function __nativeCatalogProbe({url}){return fetchJSON(url+'/catalog',{},fetch);}export async function __nativeVerificationProbe({url}){try{await fetchJSON(url+'/verify',{},fetch);return 'unexpected';}catch(error){return error.kind;}}\n";original=fixture(originalId,source);check(new JSONObject(call(original,"__nativeCatalogProbe",args)).getJSONObject("data").getString("title").equals("原版目录"),"original Qishui consumer did not decode a native streaming response");check(call(original,"__nativeVerificationProbe",args).contains("verify"),"provider verification was converted into successful login");
            PluginHttp http=(PluginHttp)FullPortChecks.field(runtime,"http");helper.await(()->http.activeCount()==0,"consumed/cancelled native streams leaked connections");call(runtime,"heldStream",args);check(http.activeCount()==1,"held stream was consumed eagerly");runtime.dispose();check(server.disconnected.await(6,TimeUnit.SECONDS),"stream cancel, abort or dispose did not close actual sockets: "+server.closedPaths);helper.await(()->http.activeCount()==0,"disposed stream retained native state");
            System.out.println("MYGO_STREAM_BODY_OK: first prefix before EOF, pull backpressure, incremental UTF-8, actual socket cancel/abort/dispose, local file stream, native Response clone/cookie/url and body ownership, exact original Qishui JSON consumer and unchanged verification errors");
        }finally{runtime.dispose();if(original!=null)original.dispose();cleanup(id);cleanup(originalId);}
    }
    final class Server implements AutoCloseable {
        final ServerSocket server=new ServerSocket(0);final CountDownLatch releaseUtf8=new CountDownLatch(1),disconnected=new CountDownLatch(4);final Set<String> closedPaths=ConcurrentHashMap.newKeySet();volatile boolean closed;
        Server()throws Exception{Thread thread=new Thread(()->{while(!closed)try{Socket socket=server.accept();Thread worker=new Thread(()->serve(socket));worker.setDaemon(true);worker.start();}catch(Exception ignored){}});thread.setDaemon(true);thread.start();}
        String base(){return "http://127.0.0.1:"+server.getLocalPort();}
        void serve(Socket socket){try(Socket client=socket){client.setSoTimeout(12000);BufferedReader in=new BufferedReader(new InputStreamReader(client.getInputStream(),"UTF-8"));String first=in.readLine(),line;if(first==null)return;while((line=in.readLine())!=null&&!line.isEmpty()){}String path=new URI(first.split(" ")[1]).getPath();boolean stalled=Arrays.asList("/prefix","/unread","/pending","/held-stream").contains(path);String json=path.equals("/catalog")?"{\"status_code\":0,\"data\":{\"title\":\"原版目录\"}}":path.equals("/verify")?"{\"status_code\":2046}":"{\"text\":\"逐字歌词\",\"ok\":42}";byte[] bytes=json.getBytes("UTF-8");int length=stalled?64000000:bytes.length;OutputStream out=client.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nSet-Cookie: fixture=stream-test; HttpOnly\r\nContent-Length: "+length+"\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));out.flush();if(first.startsWith("HEAD "))return;
            if(stalled){if(path.equals("/prefix")||path.equals("/held-stream")){out.write(new byte[]{1,2,3});out.flush();}try{if(client.getInputStream().read()==-1){closedPaths.add(path);disconnected.countDown();}}catch(SocketException error){closedPaths.add(path);disconnected.countDown();}}
            else if(path.equals("/utf8")){out.write(bytes,0,10);out.flush();if(releaseUtf8.await(8,TimeUnit.SECONDS))out.write(bytes,10,bytes.length-10);}
            else out.write(bytes);out.flush();
        }catch(Exception ignored){}}
        public void close(){closed=true;releaseUtf8.countDown();try{server.close();}catch(IOException ignored){}}
    }
}
