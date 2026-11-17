package com.mygo.music;

import android.content.Context;
import android.os.SystemClock;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

/** Bounded app-owned decoder job. Plugins cannot invoke this executable by declaring a desktop helper. */
final class NativeAudio {
    interface Check {void run()throws IOException;}
    static File binary(Context context)throws IOException{File file=new File(context.getApplicationInfo().nativeLibraryDir,"libmygo-ffmpeg.so");if(!file.isFile())throw new IOException("安装包缺少 Android 音频解码组件");return file;}
    static final class CopyUnsupported extends IOException {CopyUnsupported(){super("此分段编码需要兼容转换");}}
    static void convert(Context context,String input,File output,long maximum,Check check)throws Exception{
        ArrayList<String> args=new ArrayList<>(Arrays.asList(binary(context).getAbsolutePath(),"-nostdin","-hide_banner","-loglevel","error","-y","-protocol_whitelist",input.startsWith("http://127.0.0.1:")?"http,tcp,crypto":"file","-i",input,"-map","0:a:0","-vn","-sn","-dn","-threads","1","-c:a","flac","-f","flac",output.getAbsolutePath()));
        if(input.startsWith("http://127.0.0.1:")){int at=args.indexOf("-i");args.addAll(at,Arrays.asList("-allowed_extensions","ALL","-extension_picky","0"));}
        run(args,output,maximum,check,false);
    }
    static JSONObject copyM4a(Context context,String input,File output,long maximum,Check check)throws Exception{
        ArrayList<String> args=new ArrayList<>(Arrays.asList(binary(context).getAbsolutePath(),"-nostdin","-hide_banner","-loglevel","error","-y","-protocol_whitelist","http,tcp,crypto","-allowed_extensions","ALL","-extension_picky","0","-i",input,"-map","0:a:0","-vn","-sn","-dn","-threads","1","-c:a","copy","-movflags","+faststart","-f","ipod",output.getAbsolutePath()));
        run(args,output,maximum,check,true);check.run();MediaExtractor extractor=new MediaExtractor();
        try{extractor.setDataSource(output.getAbsolutePath());if(extractor.getTrackCount()!=1)throw new CopyUnsupported();MediaFormat format=extractor.getTrackFormat(0);String mime=format.getString(MediaFormat.KEY_MIME);String codec="audio/mp4a-latm".equals(mime)?"aac":"audio/alac".equals(mime)?"alac":"";if(codec.isEmpty())throw new CopyUnsupported();JSONObject info=new JSONObject().put("sourceCodec",codec);if(format.containsKey(MediaFormat.KEY_SAMPLE_RATE))info.put("sourceSampleRate",format.getInteger(MediaFormat.KEY_SAMPLE_RATE));return info;}finally{extractor.release();}
    }
    private static void run(List<String> args,File output,long maximum,Check check,boolean copy)throws Exception{
        Process process=new ProcessBuilder(args).redirectErrorStream(true).start();StringBuilder errors=new StringBuilder();Thread drain=new Thread(()->{try(Reader reader=new InputStreamReader(process.getInputStream(),"UTF-8")){char[] buffer=new char[1024];int count;while((count=reader.read(buffer))!=-1)synchronized(errors){if(errors.length()<12000)errors.append(buffer,0,Math.min(count,12000-errors.length()));}}catch(IOException ignored){}});drain.setDaemon(true);drain.start();long end=SystemClock.elapsedRealtime()+180000;
        try{while(true){check.run();if(output.length()>maximum)throw new IOException("转换后的音频超过缓存大小限制");if(SystemClock.elapsedRealtime()>end)throw new IOException("音频处理超时");try{int status=process.exitValue();drain.join(1500);if(status!=0){String message; synchronized(errors){message=errors.toString();}if(copy&&message.contains("codec not currently supported in container"))throw new CopyUnsupported();if(message.length()>500)message=message.substring(message.length()-500);throw new IOException("音频解码失败："+message);}check.run();if(output.length()>maximum)throw new IOException("转换后的音频超过缓存大小限制");if(output.length()<=0)throw new IOException("音频解码结果为空");return;}catch(IllegalThreadStateException running){SystemClock.sleep(50);}}}finally{process.destroy();try{process.getInputStream().close();}catch(IOException ignored){}}
    }
}
