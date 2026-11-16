package com.mygo.music;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.*;
import java.nio.ByteBuffer;

/** Copies clear AAC access units from a complete init+fragment into timestamped packed audio. No codec conversion. */
final class Fmp4Aac {
 static final int MAX_INIT=1024*1024,MAX_FRAGMENT=8*1024*1024;
 static byte[] pack(File directory,byte[] init,byte[] fragment,NativeAudio.Check check)throws Exception{return pack(directory,init,fragment,check,0);}
 static byte[] pack(File directory,byte[] init,byte[] fragment,NativeAudio.Check check,long startUs)throws Exception{
  if(startUs<0)throw new IOException("分段播放时间无效");
  if(init.length>MAX_INIT||fragment.length>MAX_FRAGMENT)throw new IOException("分段音频超过处理大小限制");
  File input=File.createTempFile("mygo-fmp4-",".mp4",directory);MediaExtractor extractor=new MediaExtractor();
  try{
   try(OutputStream out=new FileOutputStream(input)){out.write(init);out.write(fragment);}check.run();extractor.setDataSource(input.getAbsolutePath());
   if(extractor.getPsshInfo()!=null&&!extractor.getPsshInfo().isEmpty())throw new IOException("此分段音频需要平台 DRM 授权，当前暂不支持");
   if(extractor.getTrackCount()!=1)throw new IOException("此分段封装不是单轨 AAC 音频");MediaFormat format=extractor.getTrackFormat(0);
   if(!"audio/mp4a-latm".equals(format.getString(MediaFormat.KEY_MIME)))throw new IOException("此分段编码不是可直接封装的 AAC");
   ByteBuffer config=format.getByteBuffer("csd-0");if(config==null||config.remaining()<2)throw new IOException("分段 AAC 缺少编码参数");int a=config.get(config.position())&255,b=config.get(config.position()+1)&255,object=a>>3,rate=((a&7)<<1)|(b>>7),channels=(b>>3)&15;
   if(object<1||object>4||rate>12||channels<1||channels>7)throw new IOException("此 AAC 编码参数暂不支持直接封装");
   extractor.selectTrack(0);ByteBuffer sample=ByteBuffer.allocate(8192);ByteArrayOutputStream audio=new ByteArrayOutputStream();long first=-1,last=-1;int frames=0;
   while(true){check.run();sample.clear();int n=extractor.readSampleData(sample,0);if(n<0)break;if((extractor.getSampleFlags()&MediaExtractor.SAMPLE_FLAG_ENCRYPTED)!=0)throw new IOException("此分段音频需要平台 DRM 授权，当前暂不支持");long time=extractor.getSampleTime();if(time<0||time<last)throw new IOException("分段 AAC 时间戳无效");if(first<0)first=time;last=time;
    int length=n+7;if(n<=0||length>8191||audio.size()+length>MAX_FRAGMENT)throw new IOException("分段 AAC 帧超过处理大小限制");
    audio.write(255);audio.write(241);audio.write(((object-1)<<6)|(rate<<2)|(channels>>2));audio.write(((channels&3)<<6)|(length>>11));audio.write((length>>3)&255);audio.write(((length&7)<<5)|31);audio.write(252);audio.write(sample.array(),0,n);frames++;extractor.advance();
   }
   if(frames==0)throw new IOException("分段 AAC 音频为空");check.run();return LiveAacPlayback.packed(audio.toByteArray(),startUs*9/100);
  }finally{try{extractor.release();}finally{input.delete();}}
 }
}
