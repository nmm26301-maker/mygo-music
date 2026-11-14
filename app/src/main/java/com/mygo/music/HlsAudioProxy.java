package com.mygo.music;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** FFmpeg parses the HLS grammar. This loopback transport retains Java TLS and per-origin headers. */
final class HlsAudioProxy implements AutoCloseable {
    interface Headers {Map<String,String> get(String address)throws Exception;}
    private final ServerSocket server;private final String token=UUID.randomUUID().toString();private final Headers headers;private final NativeAudio.Check check;private final long maximum;private final boolean allowLive;
    private final Map<String,Integer> ids=new LinkedHashMap<>();private final ArrayList<String> addresses=new ArrayList<>();
    private final Set<Socket> clients=ConcurrentHashMap.newKeySet();private final Set<PluginMedia.Connection> connections=ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,4,20,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32));private volatile boolean closed;private long bytes;private volatile String failure="";
    private static final Pattern URI=Pattern.compile("(?i)\\bURI\\s*=\\s*\"([^\"]*)\"");
    HlsAudioProxy(Headers headers,NativeAudio.Check check,long maximum)throws IOException{this(headers,check,maximum,false);}
    HlsAudioProxy(Headers headers,NativeAudio.Check check,long maximum,boolean allowLive)throws IOException{this.allowLive=allowLive;this.headers=headers;this.check=check;this.maximum=maximum;server=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));Thread accept=new Thread(()->{while(!closed)try{Socket client=server.accept();clients.add(client);try{workers.execute(()->serve(client));}catch(RejectedExecutionException error){clients.remove(client);client.close();}}catch(IOException ignored){}});accept.setDaemon(true);accept.start();}
    synchronized String address(String original)throws IOException{URL url=new URL(original);if(!url.getProtocol().equals("http")&&!url.getProtocol().equals("https"))throw new IOException("分段音频地址必须使用 HTTP 或 HTTPS");Integer id=ids.get(original);if(id==null){if(addresses.size()>=4096)throw new IOException("分段音频资源数量过多");id=addresses.size();ids.put(original,id);addresses.add(original);}String suffix=url.getPath().toLowerCase(Locale.ROOT).endsWith(".m3u8")?"playlist.m3u8":"media.ts";return "http://127.0.0.1:"+server.getLocalPort()+"/"+token+"/"+id+"/"+suffix;}
    String failure(){return failure;}
    private synchronized void addBytes(int count)throws IOException{bytes+=count;if(bytes>maximum)throw new IOException("分段音频超过下载大小限制");}
    private String rewrite(String text,URL base)throws Exception{
        text=text.replaceFirst("^\\uFEFF","");if(!text.trim().startsWith("#EXTM3U"))throw new IOException("分段音频清单无效");
        if(!allowLive&&text.contains("#EXTINF:")&&!text.contains("#EXT-X-ENDLIST"))throw new IOException("直播流不能保存为完整离线歌曲");
        if(text.contains("#EXT-X-DEFINE"))throw new IOException("此分段清单使用变量，暂不支持分段处理");
        StringBuilder result=new StringBuilder();for(String line:text.split("\\r?\\n")){
            String value=line.trim();if(value.startsWith("#EXT-X-KEY")||value.startsWith("#EXT-X-SESSION-KEY")){if(value.contains("SAMPLE-AES")||value.contains("KEYFORMAT=")&&!value.contains("KEYFORMAT=\"identity\""))throw new IOException("受 DRM 保护的分段音频不能转为离线缓存");}
            if(!value.isEmpty()&&!value.startsWith("#"))result.append(address(new URL(base,value).toString()));
            else{Matcher match=URI.matcher(line);StringBuffer replaced=new StringBuffer();int matches=0;while(match.find()){matches++;match.appendReplacement(replaced,Matcher.quoteReplacement("URI=\""+address(new URL(base,match.group(1)).toString())+"\""));}match.appendTail(replaced);if(line.toUpperCase(Locale.ROOT).contains("URI=")&&matches==0)throw new IOException("分段音频 URI 格式无效");result.append(replaced);}
            result.append('\n');
        }return result.toString();
    }
    private void serve(Socket client){PluginMedia.Connection source=null;try(Socket socket=client){
        socket.setSoTimeout(12000);BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));String first=reader.readLine();if(first==null||first.length()>8192)return;String[] request=first.split(" ");if(request.length<2||(!request[0].equals("GET")&&!request[0].equals("HEAD")))return;String prefix="/"+token+"/";if(!request[1].startsWith(prefix))return;String id=request[1].substring(prefix.length()).split("/",2)[0];if(!id.matches("[0-9]{1,5}"))return;String original;synchronized(this){int number=Integer.parseInt(id);if(number>=addresses.size())return;original=addresses.get(number);}
        String range=null,line;int count=0;while((line=reader.readLine())!=null&&!line.isEmpty()){if(++count>40||line.length()>8192)throw new IOException("分段请求头过大");if(line.toLowerCase(Locale.ROOT).startsWith("range:"))range=line.substring(6).trim();}check.run();Map<String,String> values=new HashMap<>(headers.get(original));values.keySet().removeIf(key->key.equalsIgnoreCase("Range")||key.equalsIgnoreCase("Accept-Encoding"));values.put("Accept-Encoding","identity");if(range!=null&&range.matches("bytes=[0-9]+-[0-9]*"))values.put("Range",range);
        source=PluginMedia.open(original,values,false);connections.add(source);HttpURLConnection http=source.http;BufferedInputStream input=new BufferedInputStream(http.getInputStream());input.mark(32);byte[] prefixBytes=new byte[16];int prefixCount=input.read(prefixBytes);input.reset();String start=new String(prefixBytes,0,Math.max(0,prefixCount),StandardCharsets.UTF_8).trim();String type=http.getContentType();boolean playlist=start.startsWith("#EXTM3U")||start.startsWith("\uFEFF#EXTM3U");OutputStream output=socket.getOutputStream();
        if(playlist){ByteArrayOutputStream body=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1){check.run();addBytes(n);if(body.size()+n>2*1024*1024)throw new IOException("分段音频清单过大");body.write(buffer,0,n);}byte[] text=rewrite(body.toString("UTF-8"),http.getURL()).getBytes(StandardCharsets.UTF_8);output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/vnd.apple.mpegurl\r\nContent-Length: "+text.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));if(request[0].equals("GET"))output.write(text);}
        else{if(type!=null&&(type.contains("text/html")||type.contains("application/json")))throw new IOException("分段服务器返回了错误页面");int status=http.getResponseCode();long length=http.getContentLengthLong();String contentRange=http.getHeaderField("Content-Range");String response="HTTP/1.1 "+status+" Audio\r\nContent-Type: "+(type==null?"application/octet-stream":type)+"\r\nConnection: close\r\n"+(length>=0?"Content-Length: "+length+"\r\n":"")+(contentRange!=null?"Content-Range: "+contentRange+"\r\n":"")+"\r\n";output.write(response.getBytes(StandardCharsets.US_ASCII));if(request[0].equals("GET")){byte[] buffer=new byte[16384];int n;while((n=input.read(buffer))!=-1){check.run();addBytes(n);output.write(buffer,0,n);}}}
        input.close();output.flush();
    }catch(Exception error){failure=error.getMessage()==null?"分段音频读取失败":error.getMessage();}finally{if(source!=null){connections.remove(source);source.close();}clients.remove(client);}}
    public void close(){closed=true;try{server.close();}catch(IOException ignored){}for(PluginMedia.Connection connection:connections)connection.close();for(Socket client:clients)try{client.close();}catch(IOException ignored){}workers.shutdownNow();}
}
