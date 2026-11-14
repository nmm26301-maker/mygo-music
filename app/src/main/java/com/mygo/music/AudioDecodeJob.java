package com.mygo.music;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.net.*;
import java.util.*;

/** Decode unsupported ALAC/M4A to a complete lossless FLAC, retaining native player controls. */
final class AudioDecodeJob {
    static boolean iso(File file){try(InputStream in=new FileInputStream(file)){byte[] bytes=new byte[64];int count=in.read(bytes);return count>=12&&bytes[4]=='f'&&bytes[5]=='t'&&bytes[6]=='y'&&bytes[7]=='p';}catch(IOException ignored){return false;}}
    static boolean candidate(Uri address,String kind,int error){if(error==-1004)return false;if(HlsPlayback.matches(address,kind)&&(error==-1007||error==-1010))return true;if("content".equals(address.getScheme())&&error==-1010)return true;if(kind.equalsIgnoreCase("alac")||kind.equalsIgnoreCase("m4a")||kind.equalsIgnoreCase("mp4"))return true;if("file".equals(address.getScheme()))return iso(new File(address.getPath()));String path=address.getPath();return path!=null&&path.toLowerCase(Locale.ROOT).endsWith(".m4a");}
    static File decodeHls(Context context,PluginManager plugins,String source,Uri address,Map<String,String> headers,NativeAudio.Check check)throws Exception{
        File output=File.createTempFile("mygo-hls-decoded-",".flac",context.getCacheDir());boolean ok=false;
        try(HlsPlayback proxy=new HlsPlayback(plugins,source==null?"":source,address.toString(),headers,false)){
            try{NativeAudio.convert(context,proxy.address.toString(),output,AudioCache.MAX_TRACK,check);}catch(Exception error){if(!proxy.failure().isEmpty())throw new IOException(proxy.failure());throw error;}
            check.run();ok=true;return output;
        }finally{if(!ok)output.delete();}
    }
    static File decode(Context context,Uri address,Map<String,String> headers,NativeAudio.Check check)throws Exception{
        File downloaded=null,output=File.createTempFile("mygo-decoded-",".flac",context.getCacheDir());boolean ok=false;
        try{File input;
            if("file".equals(address.getScheme()))input=new File(address.getPath());
            else{downloaded=File.createTempFile("mygo-alac-input-",".part",context.getCacheDir());input=downloaded;PluginMedia.Connection source="content".equals(address.getScheme())?null:PluginMedia.open(address.toString(),headers,false);try(InputStream in=source==null?context.getContentResolver().openInputStream(address):source.http.getInputStream();OutputStream out=new FileOutputStream(input)){long expected=source==null?-1:source.http.getContentLengthLong(),count=0;if(expected>AudioCache.MAX_TRACK)throw new IOException("解码输入超过单曲大小限制");byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1){check.run();if((count+=n)>AudioCache.MAX_TRACK)throw new IOException("解码输入超过单曲大小限制");out.write(buffer,0,n);}if(count<=0||expected>=0&&count!=expected)throw new IOException("解码音频未完整下载");}finally{if(source!=null)source.close();}}
            if(!iso(input))throw new IOException("音频不是可解码的 ALAC/M4A 容器");check.run();NativeAudio.convert(context,input.getAbsolutePath(),output,AudioCache.MAX_TRACK,check);check.run();ok=true;return output;
        }finally{if(downloaded!=null)downloaded.delete();if(!ok)output.delete();}
    }
}
