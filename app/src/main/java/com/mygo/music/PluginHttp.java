package com.mygo.music;

import android.os.*;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Fetch resolves at headers; bodies are bounded and consumed only when requested. */
final class PluginHttp {
    interface Access {void check(URL url)throws Exception;}
    interface Reply {void send(String id,String phase,JSONObject result);}
    private final Access access;
    private final Reply reply;
    private final PluginResources resources;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(3,3,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(48));
    private final Map<String,Transfer> active=new ConcurrentHashMap<>();
    private volatile boolean closed;
    private static final int LIMIT=8*1024*1024;
    private static final class Transfer {
        volatile HttpURLConnection connection;
        volatile boolean cancelled;
        boolean reading;
        String method;
        File local;
        Runnable expiry;
        // Disconnect cancels the socket. The reading worker alone owns InputStream.close():
        // Android's buffered stream cannot be closed concurrently with its read operation.
        void close(){cancelled=true;HttpURLConnection c=connection;if(c!=null)try{c.disconnect();}catch(RuntimeException ignored){}}
    }
    PluginHttp(Access access,Reply reply,PluginResources resources){this.access=access;this.reply=reply;this.resources=resources;}
    void open(String id,String address,JSONObject init){
        if(closed){error(id,"插件已关闭");return;}
        synchronized(active){if(active.size()>=24){error(id,"插件并发请求过多，请稍后重试");return;}active.put(id,new Transfer());}
        Transfer transfer=active.get(id);transfer.expiry=()->{if(active.containsKey(id)){error(id,"插件响应未及时读取，连接已关闭");cancel(id);}};main.postDelayed(transfer.expiry,30000);
        submit(id,()->{try{JSONObject result=open(transfer,address,init);if(!transfer.cancelled)reply.send(id,"headers",result);}catch(Exception e){fail(id,transfer,e);}});
    }
    private void submit(String id,Runnable action){try{workers.execute(action);}catch(RejectedExecutionException e){error(id,"插件网络任务已关闭或队列已满");cancel(id);}}
    private JSONObject open(Transfer transfer,String address,JSONObject init)throws Exception {
        transfer.method=init.optString("method","GET").toUpperCase(Locale.US);
        android.net.Uri uri=android.net.Uri.parse(address);
        if(resources.owns(uri)){
            if(!transfer.method.equals("GET")&&!transfer.method.equals("HEAD"))throw new IOException("本地插件资源只允许读取");
            transfer.local=resources.file(uri);return envelope(200,address,new JSONObject().put("Content-Type",resources.mime(transfer.local)),new JSONArray());
        }
        String method=transfer.method,body=init.optString("body","");JSONObject headers=init.optJSONObject("headers");URL url=new URL(address);
        for(int redirects=0;redirects<=5;redirects++){
            if(transfer.cancelled||closed)throw new IOException("请求已取消");access.check(url);
            HttpURLConnection c=(HttpURLConnection)url.openConnection();transfer.connection=c;
            if(transfer.cancelled){c.disconnect();throw new IOException("请求已取消");}
            c.setUseCaches(false);c.setConnectTimeout(12000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(false);c.setRequestMethod(method);c.setRequestProperty("Accept-Encoding","identity");
            if(headers!=null){Iterator<String> keys=headers.keys();while(keys.hasNext()){String key=keys.next();c.setRequestProperty(key,headers.optString(key));}}
            if(!body.isEmpty()&&!method.equals("GET")&&!method.equals("HEAD")){c.setDoOutput(true);try(OutputStream out=c.getOutputStream()){out.write(body.getBytes("UTF-8"));}}
            int status=c.getResponseCode();String location=c.getHeaderField("Location");
            if(status>=300&&status<400&&location!=null){
                if("error".equals(init.optString("redirect")))throw new IOException("插件禁止重定向");
                if(!"manual".equals(init.optString("redirect"))){URL next=new URL(url,location);if(!sameOrigin(url,next))headers=new JSONObject();url=next;if(status==303||((status==301||status==302)&&method.equals("POST"))){method="GET";body="";}c.disconnect();continue;}
            }
            JSONObject responseHeaders=new JSONObject();JSONArray cookies=new JSONArray();
            for(Map.Entry<String,List<String>> entry:c.getHeaderFields().entrySet())if(entry.getKey()!=null){if("set-cookie".equalsIgnoreCase(entry.getKey()))for(String value:entry.getValue())cookies.put(value);else responseHeaders.put(entry.getKey(),String.join(", ",entry.getValue()));}
            transfer.method=method;return envelope(status,url.toString(),responseHeaders,cookies);
        }
        throw new IOException("重定向次数过多");
    }
    private static boolean sameOrigin(URL a,URL b){return a.getProtocol().equalsIgnoreCase(b.getProtocol())&&a.getHost().equalsIgnoreCase(b.getHost())&&a.getPort()==b.getPort();}
    private static JSONObject envelope(int status,String url,JSONObject headers,JSONArray cookies)throws JSONException{return new JSONObject().put("status",status).put("url",url).put("headers",headers).put("cookies",cookies);}
    void read(String id){Transfer transfer=active.get(id);if(transfer==null){error(id,"响应已关闭或请求已取消");return;}synchronized(transfer){if(transfer.reading){error(id,"响应内容已读取");return;}transfer.reading=true;}
        submit(id,()->{try{
            byte[] bytes=new byte[0];
            if(!transfer.method.equals("HEAD")){
                HttpURLConnection c=transfer.connection;InputStream input=transfer.local!=null?new FileInputStream(transfer.local):c.getResponseCode()>=400?c.getErrorStream():c.getInputStream();
                if(input!=null)try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(transfer.cancelled||closed)throw new IOException("请求已取消");if(out.size()+n>LIMIT)throw new IOException("插件响应超过 8 MB");out.write(buffer,0,n);}bytes=out.toByteArray();
                }
            }
            if(!transfer.cancelled)reply.send(id,"body",new JSONObject().put("body",new String(bytes,"UTF-8")).put("base64",Base64.encodeToString(bytes,Base64.NO_WRAP)));
            cancel(id);
        }catch(Exception e){fail(id,transfer,e);}});
    }
    private void error(String id,String message){try{reply.send(id,"error",new JSONObject().put("error",message));}catch(JSONException ignored){}}
    private void fail(String id,Transfer transfer,Exception error){if(!transfer.cancelled&&!closed)error(id,error.getMessage()==null?"插件网络请求失败":error.getMessage());cancel(id);}
    void cancel(String id){Transfer transfer=active.remove(id);if(transfer!=null){main.removeCallbacks(transfer.expiry);transfer.close();}}
    int activeCount(){return active.size();}
    void close(){closed=true;for(String id:new ArrayList<>(active.keySet()))cancel(id);workers.shutdownNow();}
}
