package com.mygo.music;

import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** One OAuth callback listener, bound only to IPv4 loopback and closed after use. */
final class PluginRedirect implements Closeable {
    private final ServerSocket server=new ServerSocket();
    private volatile Socket activeSocket;
    private final String path,page;
    private final long timeout;
    PluginRedirect(JSONObject options)throws Exception {
        int port=options.optInt("port");if(port<1||port>65535)throw new IOException("登录回调需要有效端口");path=options.optString("path","");if(!path.isEmpty()&&!path.startsWith("/"))throw new IOException("登录回调路径必须以 / 开头");page=options.optString("page","登录完成，可以返回播放器。");if(page.length()>16384)throw new IOException("登录完成提示过长");timeout=Math.min(300000,Math.max(1,options.optLong("timeout",180000)));server.setReuseAddress(true);try{server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),port));}catch(IOException error){server.close();throw error;}
    }
    JSONObject await()throws Exception {
        long deadline=android.os.SystemClock.elapsedRealtime()+timeout;
        try{while(!server.isClosed()){
            long left=deadline-android.os.SystemClock.elapsedRealtime();if(left<=0)throw new SocketTimeoutException("登录回调等待超时");server.setSoTimeout((int)left);
            try(Socket socket=server.accept()){activeSocket=socket;if(server.isClosed())throw new IOException("登录回调已取消");socket.setSoTimeout((int)Math.min(5000,left));BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),java.nio.charset.StandardCharsets.UTF_8));String first=line(input,8192);String[] parts=first.split(" ",3);int size=0;String header;while(!(header=line(input,8192)).isEmpty()){size+=header.length();if(size>32768)throw new IOException("登录回调请求头过长");}if(parts.length!=3||!parts[0].equals("GET")){respond(socket,405,"仅支持 GET");continue;}URI uri=new URI(parts[1]);if(!path.isEmpty()&&!path.equals(uri.getPath())){respond(socket,404,"回调路径不匹配");continue;}

                JSONObject values=new JSONObject();String query=uri.getRawQuery();if(query!=null)for(String item:query.split("&")){String[] pair=item.split("=",2);String key=URLDecoder.decode(pair[0],"UTF-8");if(!values.has(key))values.put(key,pair.length==2?URLDecoder.decode(pair[1],"UTF-8"):"");}respond(socket,200,page);return values;
            }catch(SocketTimeoutException e){if(android.os.SystemClock.elapsedRealtime()>=deadline)throw new SocketTimeoutException("登录回调等待超时");}catch(IOException e){if(server.isClosed())throw new IOException("登录回调已取消");}catch(IllegalArgumentException|URISyntaxException e){/* Ignore malformed local probes until the valid callback arrives. */}
        }throw new IOException("登录回调已取消");}finally{close();}
    }
    private static String line(Reader input,int max)throws IOException {StringBuilder text=new StringBuilder();int c;while((c=input.read())!=-1&&c!='\n'){if(text.length()>=max)throw new IOException("登录回调请求行过长");if(c!='\r')text.append((char)c);}return text.toString();}
    private static void respond(Socket socket,int status,String message)throws IOException {byte[] body=message.getBytes(java.nio.charset.StandardCharsets.UTF_8);OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 "+status+" "+(status==200?"OK":status==404?"Not Found":"Method Not Allowed")+"\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: "+body.length+"\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));out.write(body);out.flush();}
    @Override public void close(){try{server.close();}catch(IOException ignored){}Socket socket=activeSocket;if(socket!=null)try{socket.close();}catch(IOException ignored){}}
}
