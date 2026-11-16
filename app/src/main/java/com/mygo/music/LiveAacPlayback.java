package com.mygo.music;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Bounded, lossless ADTS-to-packed-audio HLS transport for ICY AAC live streams (RFC 8216 section 3.4). */
final class LiveAacPlayback implements AutoCloseable {
    static final int MAX_BYTES=8*1024*1024,MAX_SEGMENTS=30,WINDOW=6;
    private static final int[] RATES={96000,88200,64000,48000,44100,32000,24000,22050,16000,12000,11025,8000,7350};
    private final ServerSocket server;
    private final String token=UUID.randomUUID().toString();
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,4,20,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16));
    private final Set<Socket> clients=ConcurrentHashMap.newKeySet();
    private final LinkedHashMap<Long,Segment> segments=new LinkedHashMap<>();
    private volatile boolean closed,ended;private volatile String error="";
    private volatile PluginMedia.Connection connection;
    private long sequence,totalSamples;private int retainedBytes,sampleRate,channels;
    static final class Segment {final long id;final byte[] bytes;final double seconds;Segment(long id,byte[] bytes,double seconds){this.id=id;this.bytes=bytes;this.seconds=seconds;}}
    static boolean matches(HttpURLConnection http){String type=http.getContentType();type=type==null?"":type.toLowerCase(Locale.ROOT).split(";",2)[0].trim();return http.getContentLengthLong()<0&&(type.equals("audio/aac")||type.equals("audio/aacp"))&&(http.getHeaderField("icy-br")!=null||http.getHeaderField("icy-name")!=null||http.getHeaderField("icy-metaint")!=null);}
    LiveAacPlayback(String original,Map<String,String> sourceHeaders)throws IOException{
        server=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));
        Thread accept=new Thread(()->{while(!closed)try{Socket client=server.accept();clients.add(client);try{workers.execute(()->serve(client));}catch(RejectedExecutionException full){clients.remove(client);client.close();}}catch(IOException ignored){}},"mygo-live-aac-http");accept.setDaemon(true);accept.start();
        Map<String,String> headers=new HashMap<>(sourceHeaders);headers.keySet().removeIf(key->key.equalsIgnoreCase("Range")||key.equalsIgnoreCase("Accept-Encoding")||key.equalsIgnoreCase("Icy-MetaData"));headers.put("Accept-Encoding","identity");headers.put("Icy-MetaData","0");
        Thread reader=new Thread(()->produce(original,headers),"mygo-live-aac-input");reader.setDaemon(true);reader.start();
    }
    String address(){return "http://127.0.0.1:"+server.getLocalPort()+"/"+token+"/live.m3u8";}
    String failure(){return error;}
    synchronized int retainedBytes(){return retainedBytes;}
    synchronized long publishedSegments(){return sequence;}
    synchronized int sampleRate(){return sampleRate;}
    synchronized int channels(){return channels;}
    private void produce(String original,Map<String,String> headers){
        try(PluginMedia.Connection active=PluginMedia.open(original,headers,false)){
            connection=active;if(closed)return;active.http.setReadTimeout(15000);int metadata=0;try{metadata=Integer.parseInt(active.http.getHeaderField("icy-metaint"));}catch(Exception ignored){}
            try(InputStream raw=active.http.getInputStream();InputStream input=new BufferedInputStream(new IcyInput(raw,metadata),32768)){
                ByteArrayOutputStream body=new ByteArrayOutputStream();long first=0;int skipped=0;
                while(!closed){int a=input.read();if(a<0)break;int b=input.read();if(b<0)break;
                    if(a!=255||(b&246)!=240){if(++skipped>8192)throw new IOException("直播音频不是有效的 ADTS AAC");continue;}
                    byte[] header=new byte[7];header[0]=(byte)a;header[1]=(byte)b;readFully(input,header,2,5);int rateIndex=(header[2]>>2)&15;if(rateIndex>=RATES.length||(header[6]&3)!=0)throw new IOException("直播 AAC 帧参数不受支持");int rate=RATES[rateIndex],count=((header[2]&1)<<2)|((header[3]>>6)&3);int length=((header[3]&3)<<11)|((header[4]&255)<<3)|((header[5]&255)>>5);if(length<((b&1)!=0?7:9)||length>8191)throw new IOException("直播 AAC 帧长度无效");
                    synchronized(this){if(sampleRate==0){sampleRate=rate;channels=count;}else if(sampleRate!=rate||channels!=count)throw new IOException("直播音频格式已变化，请重新播放");}
                    if(body.size()==0)first=totalSamples;body.write(header);byte[] payload=new byte[length-7];readFully(input,payload,0,payload.length);body.write(payload);totalSamples+=1024;skipped=0;
                    if((totalSamples-first)/(double)rate>=2){publish(body.toByteArray(),first,totalSamples-first,rate);body.reset();}
                }
                if(!closed&&body.size()>0)publish(body.toByteArray(),first,totalSamples-first,sampleRate);
                synchronized(this){ended=true;notifyAll();}
            }
        }catch(Exception problem){if(!closed)error=problem.getMessage()==null?"直播音频读取失败":problem.getMessage();synchronized(this){ended=true;notifyAll();}}finally{connection=null;}
    }
    private static void readFully(InputStream input,byte[] bytes,int offset,int length)throws IOException{int end=offset+length;while(offset<end){int n=input.read(bytes,offset,end-offset);if(n<0)throw new EOFException("直播 AAC 帧不完整");offset+=n;}}
    private static void syncSafe(ByteArrayOutputStream out,int value){out.write((value>>21)&127);out.write((value>>14)&127);out.write((value>>7)&127);out.write(value&127);}
    static byte[] packed(byte[] audio,long timestamp)throws IOException{
        byte[] owner="com.apple.streaming.transportStreamTimestamp".getBytes(StandardCharsets.US_ASCII);ByteArrayOutputStream frame=new ByteArrayOutputStream();frame.write("PRIV".getBytes(StandardCharsets.US_ASCII));syncSafe(frame,owner.length+9);frame.write(0);frame.write(0);frame.write(owner);frame.write(0);long value=timestamp&0x1ffffffffL;for(int shift=56;shift>=0;shift-=8)frame.write((int)(value>>shift)&255);byte[] f=frame.toByteArray();ByteArrayOutputStream out=new ByteArrayOutputStream(f.length+10+audio.length);out.write(new byte[]{'I','D','3',4,0,0});syncSafe(out,f.length);out.write(f);out.write(audio);return out.toByteArray();
    }
    private synchronized void publish(byte[] audio,long first,long count,int rate)throws IOException{
        if(closed)return;byte[] bytes=packed(audio,first*90000/rate);Segment item=new Segment(sequence++,bytes,count/(double)rate);segments.put(item.id,item);retainedBytes+=bytes.length;
        while(segments.size()>MAX_SEGMENTS||retainedBytes>MAX_BYTES&&segments.size()>WINDOW){Long firstId=segments.keySet().iterator().next();retainedBytes-=segments.remove(firstId).bytes.length;}notifyAll();
    }
    private synchronized byte[] playlist()throws Exception{
        long until=System.currentTimeMillis()+20000;while(!closed&&!ended&&sequence<3&&System.currentTimeMillis()<until)wait(300);
        if(closed||!error.isEmpty()||segments.isEmpty())throw new IOException(error.isEmpty()?"直播音频尚未就绪":error);
        ArrayList<Segment> live=new ArrayList<>(segments.values());int start=Math.max(0,live.size()-WINDOW);StringBuilder text=new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:2\n#EXT-X-MEDIA-SEQUENCE:").append(live.get(start).id).append('\n');
        for(int i=start;i<live.size();i++){Segment s=live.get(i);text.append(String.format(Locale.ROOT,"#EXTINF:%.6f,\n%d.aac\n",s.seconds,s.id));}if(ended)text.append("#EXT-X-ENDLIST\n");return text.toString().getBytes(StandardCharsets.UTF_8);
    }
    private void serve(Socket client){try(Socket socket=client){socket.setSoTimeout(12000);BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));String first=in.readLine();if(first==null||first.length()>8192)return;String[] parts=first.split(" ");if(parts.length<2||(!parts[0].equals("GET")&&!parts[0].equals("HEAD")))return;String prefix="/"+token+"/";if(!parts[1].startsWith(prefix))return;String path=parts[1].substring(prefix.length()).split("\\?",2)[0],range=null,line;int count=0;while((line=in.readLine())!=null&&!line.isEmpty()){if(++count>40||line.length()>8192)return;if(line.toLowerCase(Locale.ROOT).startsWith("range:"))range=line.substring(6).trim();}
        byte[] data;String type;int status=200,start=0,end;
        if(path.equals("live.m3u8")){data=playlist();type="application/vnd.apple.mpegurl";}else if(path.matches("[0-9]{1,12}\\.aac")){long id=Long.parseLong(path.substring(0,path.length()-4));Segment s;synchronized(this){s=segments.get(id);}if(s==null){reply(socket,410,"text/plain",new byte[0],0,-1,false,false);return;}data=s.bytes;type="audio/aac";}else return;
        end=data.length-1;if(range!=null&&range.matches("bytes=[0-9]+-[0-9]*")&&!path.equals("live.m3u8")){String[] values=range.substring(6).split("-",-1);long from=Long.parseLong(values[0]),to=values[1].isEmpty()?end:Long.parseLong(values[1]);if(from>to||from>=data.length){reply(socket,416,type,new byte[0],0,-1,false,false);return;}start=(int)from;end=(int)Math.min(end,to);status=206;}
        reply(socket,status,type,data,start,end,parts[0].equals("GET"),status==206);
    }catch(Exception ignored){}finally{clients.remove(client);}}
    private static void reply(Socket socket,int status,String type,byte[] data,int start,int end,boolean body,boolean range)throws IOException{OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 "+status+" Audio\r\nContent-Type: "+type+"\r\nContent-Length: "+Math.max(0,end-start+1)+"\r\nCache-Control: no-store\r\nConnection: close\r\n"+(range?"Content-Range: bytes "+start+"-"+end+"/"+data.length+"\r\n":"")+"\r\n").getBytes(StandardCharsets.US_ASCII));if(body&&end>=start)out.write(data,start,end-start+1);out.flush();}
    public void close(){closed=true;synchronized(this){segments.clear();retainedBytes=0;notifyAll();}try{server.close();}catch(IOException ignored){}for(Socket client:clients)try{client.close();}catch(IOException ignored){}workers.shutdownNow();PluginMedia.Connection active=connection;if(active!=null){Thread cancel=new Thread(active::close,"mygo-live-aac-close");cancel.setDaemon(true);cancel.start();}}
    private static final class IcyInput extends InputStream {
        final InputStream input;final int interval;int remaining;
        IcyInput(InputStream input,int interval){this.input=input;this.interval=Math.max(0,interval);remaining=this.interval;}
        public int read()throws IOException{if(interval>0&&remaining==0){int size=input.read();if(size<0)return -1;int bytes=size*16;while(bytes>0){long n=input.skip(bytes);if(n<=0){if(input.read()<0)throw new EOFException("直播元数据不完整");n=1;}bytes-=n;}remaining=interval;}int value=input.read();if(value>=0&&interval>0)remaining--;return value;}
        public int read(byte[] bytes,int offset,int length)throws IOException{if(length==0)return 0;if(interval==0)return input.read(bytes,offset,length);int first=read();if(first<0)return -1;bytes[offset]=(byte)first;int take=Math.min(length-1,remaining);int n=take>0?input.read(bytes,offset+1,take):0;if(n>0){remaining-=n;return n+1;}return 1;}
    }
}
