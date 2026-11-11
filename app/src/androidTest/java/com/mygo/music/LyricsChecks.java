package com.mygo.music;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Checks pixels inside a word, then the full page against actual paused/seeked native audio. */
final class LyricsChecks {
    private final Instrumentation test;
    private final Context context;
    LyricsChecks(Instrumentation test){this.test=test;context=test.getTargetContext();}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private interface Condition {boolean valid()throws Exception;}
    private void await(Condition condition,String message)throws Exception{long deadline=SystemClock.elapsedRealtime()+12000;while(SystemClock.elapsedRealtime()<deadline){boolean[] result={false};test.runOnMainSync(()->{try{result[0]=condition.valid();}catch(Exception ignored){}});if(result[0])return;SystemClock.sleep(50);}throw new AssertionError(message);}
    private static Object field(Object object,String name)throws Exception{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static void invoke(Object object,String name)throws Exception{Method method=object.getClass().getDeclaredMethod(name);method.setAccessible(true);method.invoke(object);}
    private static String fixture()throws Exception{return new JSONObject().put("lines",new JSONArray()
        .put(new JSONObject().put("start",0).put("end",6000).put("translation","A source-provided timed phrase").put("words",new JSONArray().put(new JSONObject().put("text","让音乐慢慢亮起").put("start",1000).put("end",5000))))
        .put(new JSONObject().put("start",7000).put("end",14000).put("words",new JSONArray().put(new JSONObject().put("text","跟着声音向前").put("start",7000).put("end",12000))))).toString();}
    void run()throws Exception{parser();pixels();servicePage();}
    private void parser()throws Exception{
        LyricsTimeline parsed=LyricsTimeline.parse(fixture());check(parsed.wordTimed&&!parsed.estimatedWordTiming,"source word times were replaced with estimates");check(parsed.active(-1)==-1&&parsed.active(0)==0&&parsed.active(6999)==0&&parsed.active(7000)==1,"line boundary/intro lookup failed");
        LyricsTimeline.Word word=parsed.lines.get(0).words.get(0);check(word.fraction(999)==0&&word.fraction(1000)==0&&word.fraction(3000)==.5f&&word.fraction(5000)==1&&word.fraction(2000)==.25f,"absolute word clock/seek fraction failed");
        parsed=LyricsTimeline.parse("[offset:+250]\n[00:01.00][00:09.00]<00:01.00>轻<00:02.00>唱<00:03.00>");
        check(parsed.lines.size()==2&&parsed.lines.get(0).start==1250&&parsed.lines.get(1).start==9250,"offset or repeated LRC timestamp lost");check(parsed.lines.get(1).words.get(1).start==10250&&parsed.lines.get(1).words.get(1).end==11250,"enhanced LRC real word timestamps lost");
        parsed=LyricsTimeline.parse("[00:00]一起听音乐\n[00:04]下一行");check(parsed.wordTimed&&parsed.estimatedWordTiming&&parsed.lines.get(0).words.size()==5,"upstream CJK LRC fallback missing");check(parsed.lines.get(0).words.get(4).end==3400,"upstream 85 percent singing span changed");
        parsed=LyricsTimeline.parse(new JSONObject().put("lrc","[00:01]原文\n[00:03]下一行").put("translation","[00:01]译文\n[00:03]第二句译文").toString());check(parsed.lines.get(0).translation.equals("译文")&&parsed.lines.get(1).translation.equals("第二句译文"),"translation timestamps lost");
        String supplementary=new String(Character.toChars(0x20000));parsed=LyricsTimeline.parse("[00:00]"+supplementary+"好\n[00:02]结束");check(parsed.lines.get(0).words.get(0).to==2&&parsed.lines.get(0).words.get(1).from==2,"UTF16 glyph boundary split a supplementary character");
        check(!LyricsTimeline.parse("没有时间轴\n文本歌词").timed&&LyricsTimeline.parse("null").lines.isEmpty(),"plain/missing lyrics mislabeled as timed");
        boolean rejected=false;try{LyricsTimeline.parse(new String(new char[LyricsTimeline.MAX_INPUT+1]).replace('\0','字'));}catch(JSONException expected){rejected=true;}check(rejected,"unbounded lyric input accepted");
    }
    private static final class Clock implements LyricsStageView.Clock {long position;public long position(){return position;}public boolean playing(){return false;}public void seek(long value){position=value;}}
    private static Bitmap draw(View view){Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));return bitmap;}
    private static int bright(Bitmap bitmap){int count=0;int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());for(int pixel:pixels)if(Color.red(pixel)>230&&Color.green(pixel)>230&&Color.blue(pixel)>230)count++;return count;}
    private void pixels()throws Exception{
        String data=fixture();test.runOnMainSync(()->{try{Clock clock=new Clock();LyricsStageView view=new LyricsStageView(context,clock);view.setEffect(2);view.setTimeline(LyricsTimeline.parse(data));view.layout(0,0,1200,650);
            clock.position=1000;Bitmap before=draw(view);clock.position=1500;Bitmap early=draw(view);clock.position=4500;Bitmap late=draw(view);clock.position=4500;Bitmap paused=draw(view);
            check(bright(early)>bright(before)+50,"a partly sung multi-character word remained fully dark");check(bright(late)>bright(early)*2,"word highlight did not move continuously through glyph width");check(late.sameAs(paused),"paused word pixels kept animating");clock.position=1500;Bitmap back=draw(view);check(early.sameAs(back),"seek backwards left stale highlighted pixels");
            before.recycle();early.recycle();late.recycle();paused.recycle();back.recycle();
            String longText="让声音陪着我们慢慢向前走在安静的路上每一个字都随着时间亮起";String wrapped=new JSONObject().put("lines",new JSONArray().put(new JSONObject().put("start",0).put("end",8000).put("words",new JSONArray().put(new JSONObject().put("text",longText).put("start",1000).put("end",7000))))).toString();view.setTimeline(LyricsTimeline.parse(wrapped));view.layout(0,0,500,650);clock.position=2200;early=draw(view);clock.position=5800;late=draw(view);check(bright(late)>bright(early)*2,"wrapped word clipping stopped at the first visual row");early.recycle();late.recycle();
        }catch(Exception error){throw new RuntimeException(error);}});
    }
    private void sourceRefresh(MainActivity activity,LyricsStageDialog page,String uri)throws Exception{
        String id="lyrics-export-fixture";PluginManager manager=(PluginManager)field(activity,"pluginManager");
        ByteArrayOutputStream data=new ByteArrayOutputStream();
        String source="export function lyrics(){return "+fixture()+";}";
        try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(data)){
            zip.putNextEntry(new java.util.zip.ZipEntry("plugin.json"));zip.write(("{\"id\":\""+id+"\",\"main\":\"index.js\"}").getBytes("UTF-8"));zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("index.js"));zip.write(source.getBytes("UTF-8"));zip.closeEntry();
        }
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);boolean[] installed={false};manager.installZipStream(new ByteArrayInputStream(data.toByteArray()),(ok,raw)->{installed[0]=ok;done.countDown();});
        check(done.await(30,java.util.concurrent.TimeUnit.SECONDS)&&installed[0],"lyric export fixture failed to install");
        try{
            test.runOnMainSync(()->{try{
                ((Map<String,String>)field(activity,"pluginTrackRefs")).put(uri,id+"\nfixture");
                ((Map<String,String>)field(activity,"localLyrics")).put(uri,"[00:00]旧版逐行缓存\n[00:05]第二行");
                Method load=MainActivity.class.getDeclaredMethod("loadStageLyrics",boolean.class);load.setAccessible(true);load.invoke(activity,false);
            }catch(Exception error){throw new RuntimeException(error);}});
            await(()->page.stage.timeline().estimatedWordTiming,"cached LRC did not use the original approximate word fallback");
            test.runOnMainSync(()->{try{Method refresh=MainActivity.class.getDeclaredMethod("loadStageLyrics",boolean.class);refresh.setAccessible(true);refresh.invoke(activity,true);}catch(Exception error){throw new RuntimeException(error);}});
            await(()->page.stage.timeline().wordTimed&&!page.stage.timeline().estimatedWordTiming&&page.stage.timeline().lines.get(0).text.equals("让音乐慢慢亮起"),"real plugin lyric export did not replace cached line-only lyrics");
            test.runOnMainSync(()->{try{String saved=((Map<String,String>)field(activity,"localLyrics")).get(uri);check(new JSONObject(saved).getJSONArray("lines").getJSONObject(0).getJSONArray("words").getJSONObject(0).getLong("end")==5000,"plugin word timing was flattened when saving lyrics");}catch(Exception error){throw new RuntimeException(error);}});
        }finally{PluginArchive.delete(new File(context.getFilesDir(),"plugins/"+id));}
    }
    @SuppressWarnings("unchecked") private void servicePage()throws Exception{
        File first=new File(context.getCacheDir(),"lyrics-stage-a.wav"),second=new File(context.getCacheDir(),"lyrics-stage-b.wav");try(FileOutputStream output=new FileOutputStream(first)){output.write(BackgroundPlaybackChecks.wav(20000));}try(FileOutputStream output=new FileOutputStream(second)){output.write(BackgroundPlaybackChecks.wav(20000));}
        MainActivity activity=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));PlaybackService[] service={null};await(()->{service[0]=(PlaybackService)field(activity,"playbackService");return service[0]!=null;},"lyrics page did not bind to audio service");
        String firstUri=Uri.fromFile(first).toString(),secondUri=Uri.fromFile(second).toString();String original=context.getSharedPreferences("library",0).getString("local_lyrics","{}");
        try{
            String raw=fixture();test.runOnMainSync(()->{try{HashMap<String,String> saved=(HashMap<String,String>)field(activity,"localLyrics");saved.put(firstUri,raw);saved.put(secondUri,"[00:00]新的歌曲歌词\n[00:03]切歌后不要显示旧词");JSONArray queue=new JSONArray().put(new JSONObject().put("uri",firstUri).put("title","逐字歌词实测")).put(new JSONObject().put("uri",secondUri).put("title","切歌实测"));PlaybackService.startQueue(activity,queue,0,0,60,false,false,"high");}catch(Exception error){throw new RuntimeException(error);}});
            await(()->service[0].snapshot().playing,"native audio fixture never played");test.runOnMainSync(()->{try{service[0].pause();service[0].seek(3000);invoke(activity,"showLyrics");}catch(Exception error){throw new RuntimeException(error);}});
            LyricsStageDialog[] page={null};await(()->{page[0]=(LyricsStageDialog)field(activity,"lyricsStage");return page[0]!=null&&page[0].stage.timeline().wordTimed&&page[0].stage.getWidth()>0;},"full-screen word stage did not display cached structured lyrics");
            test.runOnMainSync(()->{check(page[0].getWindow().getAttributes().width==-1&&page[0].getWindow().getAttributes().height==-1,"lyrics are still in a small alert dialog");check(page[0].stage.activeLine()==0,"stage is not using the native player position");check(Math.abs(page[0].stage.timeline().lines.get(0).words.get(0).fraction(service[0].snapshot().position)-.5f)<.08,"actual native seek did not update partial word progress");});
            int[] pausedAt={0};test.runOnMainSync(()->pausedAt[0]=service[0].snapshot().position);SystemClock.sleep(350);test.runOnMainSync(()->check(Math.abs(service[0].snapshot().position-pausedAt[0])<100,"stage clock advances while audio is paused"));test.runOnMainSync(service[0]::resume);await(()->service[0].snapshot().position>pausedAt[0]+300,"resume did not resume lyric playback clock");
            test.runOnMainSync(()->{service[0].pause();android.widget.SeekBar bar=page[0].progress;long now=SystemClock.uptimeMillis();float x=bar.getWidth()*.40f,y=bar.getHeight()/2f;MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(now,now+40,MotionEvent.ACTION_UP,x,y,0);bar.dispatchTouchEvent(down);bar.dispatchTouchEvent(up);down.recycle();up.recycle();});
            await(()->Math.abs(service[0].snapshot().position-8000)<600&&page[0].stage.activeLine()==1,"full-screen lyric progress control did not seek actual audio");
            test.runOnMainSync(()->{service[0].seek(3000);page[0].stage.follow();});await(()->page[0].stage.activeLine()==0,"backwards seek left the next line active");
            SystemClock.sleep(350);test.runOnMainSync(()->{try{File qa=new File(context.getExternalFilesDir(null),"qa");qa.mkdirs();Bitmap image=draw(page[0].getWindow().getDecorView());try(FileOutputStream out=new FileOutputStream(new File(qa,"lyrics-stage.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}catch(Exception error){throw new RuntimeException(error);}});
            sourceRefresh(activity,page[0],firstUri);
            test.runOnMainSync(service[0]::next);await(()->page[0].trackUri.equals(secondUri)&&page[0].stage.timeline().lines.size()==2&&page[0].stage.timeline().lines.get(0).text.equals("新的歌曲歌词"),"track change left the previous song's lyric timeline visible");
            await(()->service[0].snapshot().playing&&service[0].snapshot().uri.equals(secondUri),"new audio did not become ready before closing lyrics");
            test.runOnMainSync(()->{page[0].dismiss();check(service[0].snapshot().playing,"closing the lyric page paused the song");});
        }finally{test.runOnMainSync(()->{try{LyricsStageDialog page=(LyricsStageDialog)field(activity,"lyricsStage");if(page!=null)page.dismiss();}catch(Exception ignored){}service[0].stopPlayback();activity.finish();});context.getSharedPreferences("library",0).edit().putString("local_lyrics",original).commit();first.delete();second.delete();}
    }
}
