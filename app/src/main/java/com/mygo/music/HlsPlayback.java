package com.mygo.music;

import android.net.Uri;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URL;
import java.util.*;

/** One player's loopback transport. Only origin-matched explicit headers follow HLS resources. */
final class HlsPlayback implements AutoCloseable {
    final Uri address;
    private final HlsAudioProxy proxy;
    private volatile boolean closed;
    static boolean matches(Uri uri,String kind){String path=uri.getPath();return "hls".equalsIgnoreCase(kind)||"application/vnd.apple.mpegurl".equalsIgnoreCase(kind)||"application/x-mpegurl".equalsIgnoreCase(kind)||path!=null&&path.toLowerCase(Locale.ROOT).endsWith(".m3u8");}
    HlsPlayback(PluginManager plugins,String source,String original,Map<String,String> headers)throws Exception{
        URL origin=new URL(original);HashMap<String,String> explicit=new HashMap<>(headers);
        proxy=new HlsAudioProxy(child->{JSONObject values=new JSONObject();if(PluginMedia.sameOrigin(origin,new URL(child)))for(Map.Entry<String,String> item:explicit.entrySet())values.put(item.getKey(),item.getValue());return plugins.mediaHeaders(source,child,"audio",values);},()->{if(closed||Thread.currentThread().isInterrupted())throw new IOException("分段播放已取消");},Long.MAX_VALUE,true);
        Uri local;try{local=Uri.parse(proxy.address(original));}catch(Exception error){proxy.close();throw error;}address=local;
    }
    String failure(){return proxy.failure();}
    @Override public synchronized void close(){if(closed)return;closed=true;Thread cleanup=new Thread(proxy::close,"mygo-hls-close");cleanup.setDaemon(true);cleanup.start();}
}
