package com.mygo.music;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Actual Canvas pixels and real menu actions for the two added motion presets. */
final class LyricsMotionChecks {
    final Instrumentation test;final Context context;
    LyricsMotionChecks(Instrumentation test){this.test=test;context=test.getTargetContext();}
    void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static final class Clock implements LyricsStageView.Clock {long position;public long position(){return position;}public boolean playing(){return false;}public void seek(long value){position=value;}}
    static String fixture()throws Exception{
        JSONArray lines=new JSONArray();String[] phrases={"让旋律慢慢流动","在心里轻轻回响","每个字都有温度","跟着声音向前走"};
        for(int i=0;i<phrases.length;i++){long start=i*3200;lines.put(new JSONObject().put("start",start).put("end",start+2800).put("translation",i==0?"Let every note come alive":"Let the words follow the song").put("words",new JSONArray().put(new JSONObject().put("text",phrases[i].substring(0,1)).put("start",start+100).put("end",start+2200)).put(new JSONObject().put("text",phrases[i].substring(1)).put("start",start+2200).put("end",start+2800))));}
        return new JSONObject().put("lines",lines).toString();
    }
    Bitmap draw(View view){Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));return bitmap;}
    int difference(Bitmap a,Bitmap b){int n=0;for(int y=0;y<a.getHeight();y+=2)for(int x=0;x<a.getWidth();x+=2)if(a.getPixel(x,y)!=b.getPixel(x,y))n++;return n;}
    @SuppressWarnings("unchecked") Map<Integer,Float> centers(LyricsStageView view)throws Exception{return new HashMap<>((Map<Integer,Float>)FullPortChecks.field(view,"drawnCenters"));}
    void save(Bitmap image,File target)throws Exception{try(FileOutputStream out=new FileOutputStream(target)){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
    void run()throws Exception{
        try(InputStream in=test.getContext().getAssets().open("lyric-motion-policy.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);JSONArray cases=new JSONArray(out.toString("UTF-8"));for(int i=0;i<cases.length();i++){JSONObject expected=cases.getJSONObject(i);long interval=expected.getLong("interval");check(Math.abs(expected.getDouble("stiffness")-LyricsExpressiveMotion.stiffness(interval))<.00001,"native stiffness differs from pinned original AMLL policy");check(Math.abs(expected.getDouble("damping")-LyricsExpressiveMotion.damping(interval))<.00001,"native damping differs from pinned original AMLL policy");}}
        check(LyricsExpressiveMotion.remaining(0,800)==1&&LyricsExpressiveMotion.remaining(1500,800)==0,"spring does not start and settle");check(LyricsExpressiveMotion.wave(100,0,900,0)==0&&LyricsExpressiveMotion.wave(500,0,2000,0)>0,"word duration threshold changed");
        test.runOnMainSync(()->{try{pixels();}catch(Exception failure){throw new RuntimeException(failure);}});controls();
        System.out.println("MYGO_LYRIC_MOTION_OK: pinned original AMLL policy matches native values; distinct spring row cascade and duration wave pixels, paused/rewind equality, real timing, quiet mode, plain lyrics, actual menus and persisted choices");
    }
    void pixels()throws Exception{
        Clock clock=new Clock();LyricsStageView view=new LyricsStageView(context,clock);view.layout(0,0,960,720);view.setBackdrop(false);view.setTimeline(LyricsTimeline.parse(fixture()));LyricsAppearance options=new LyricsAppearance();options.style=6;options.color=1;options.translation=false;view.setAppearance(options);
        clock.position=3420;Bitmap first=draw(view);Map<Integer,Float> early=centers(view);Bitmap paused=draw(view);check(first.sameAs(paused),"spring rows move while paused");paused.recycle();
        clock.position=3800;Bitmap later=draw(view);check(centers(view).get(1)<early.get(1)-10,"new active line never springs toward focus");later.recycle();clock.position=5000;Bitmap settled=draw(view);Map<Integer,Float> rest=centers(view);settled.recycle();
        check(early.containsKey(2)&&rest.containsKey(2)&&early.get(2)-early.get(1)>rest.get(2)-rest.get(1)+1,"following rows moved as one rigid block instead of a delayed cascade");
        clock.position=3420;Bitmap rewind=draw(view);check(first.sameAs(rewind),"spring rewind retains stale wall-clock motion");rewind.recycle();first.recycle();
        clock.position=1200;Bitmap spring=draw(view);options.style=7;view.setAppearance(options);Bitmap wave=draw(view);check(difference(spring,wave)>80,"long-note wave is only a renamed spring preset");spring.recycle();paused=draw(view);check(wave.sameAs(paused),"duration wave advances while paused");paused.recycle();clock.position=2000;later=draw(view);later.recycle();clock.position=1200;rewind=draw(view);check(wave.sameAs(rewind),"duration wave does not replay deterministically after rewind");rewind.recycle();wave.recycle();
        options.style=6;options.reduceMotion=true;view.setAppearance(options);clock.position=3420;first=draw(view);Map<Integer,Float> quiet=centers(view);first.recycle();clock.position=5000;first=draw(view);check(Math.abs(quiet.get(1)-centers(view).get(1))<.01,"reduced motion kept spring translation");first.recycle();
        for(int style=6;style<=7;style++){options=new LyricsAppearance();options.style=style;options.color=1;view.setAppearance(options);view.setTimeline(LyricsTimeline.parse("没有时间轴的歌词\n保持文字可阅读"));clock.position=1000;first=draw(view);clock.position=2000;later=draw(view);check(first.sameAs(later),"plain lyrics fabricate a motion timing source");first.recycle();later.recycle();}
        view.setTimeline(LyricsTimeline.parse(fixture()));view.layout(0,0,640,360);view.setBackdrop(true);File root=new File(context.getExternalFilesDir(null),"qa/lyric-motion");root.mkdirs();
        for(int style=6;style<=7;style++){options=new LyricsAppearance();options.style=style;options.color=1;options.background=2;options.translation=true;view.setAppearance(options);File frames=new File(root,"style-"+style);frames.mkdirs();for(int i=0;i<90;i++){clock.position=50+i*55;Bitmap image=draw(view);Canvas canvas=new Canvas(image);Paint label=new Paint(Paint.ANTI_ALIAS_FLAG);label.setTextSize(17);label.setColor(0xffb9c5df);canvas.drawText(LyricsAppearance.STYLES[style],18,26,label);save(image,new File(frames,String.format(Locale.US,"frame-%03d.png",i)));}}
        view.release();
    }
    View text(View view,String label){if(view instanceof TextView&&label.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=text(group.getChildAt(i),label);if(found!=null)return found;}}return null;}
    List<AccessibilityNodeInfo> roots(){List<AccessibilityNodeInfo> out=new ArrayList<>();AccessibilityNodeInfo active=test.getUiAutomation().getRootInActiveWindow();if(active!=null)out.add(active);for(AccessibilityWindowInfo window:test.getUiAutomation().getWindows()){AccessibilityNodeInfo root=window.getRoot();if(root!=null)out.add(root);}return out;}
    void clickMenu(String label)throws Exception{long end=SystemClock.elapsedRealtime()+7000;while(SystemClock.elapsedRealtime()<end){List<AccessibilityNodeInfo> roots=roots();for(AccessibilityNodeInfo root:roots)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(label))if(label.contentEquals(node.getText())&&click(node))return;for(AccessibilityNodeInfo root:roots)scroll(root);SystemClock.sleep(180);}File dir=new File(context.getExternalFilesDir(null),"qa/lyric-motion");dir.mkdirs();Bitmap image=test.getUiAutomation().takeScreenshot();if(image!=null)save(image,new File(dir,"menu-failure.png"));StringBuilder tree=new StringBuilder();for(AccessibilityNodeInfo root:roots())describe(root,tree,0);try(FileOutputStream out=new FileOutputStream(new File(dir,"menu-failure.txt"))){out.write(tree.toString().getBytes("UTF-8"));}throw new AssertionError("actual motion menu missing: "+label+"; windows="+test.getUiAutomation().getWindows().size());}
    void describe(AccessibilityNodeInfo node,StringBuilder tree,int depth){if(node==null||depth>25)return;tree.append(node.getClassName()).append(" text=").append(node.getText()).append(" clickable=").append(node.isClickable()).append('\n');for(int i=0;i<node.getChildCount();i++)describe(node.getChild(i),tree,depth+1);}
    boolean click(AccessibilityNodeInfo node){for(int i=0;node!=null&&i<6;i++,node=node.getParent())if(node.isClickable()&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;return false;}
    boolean scroll(AccessibilityNodeInfo node){if(node==null)return false;if(node.isScrollable()&&node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))return true;for(int i=0;i<node.getChildCount();i++)if(scroll(node.getChild(i)))return true;return false;}
    void controls()throws Exception{
        SharedPreferences prefs=context.getSharedPreferences("library",0);LyricsAppearance original=LyricsAppearance.read(prefs);android.accessibilityservice.AccessibilityServiceInfo info=test.getUiAutomation().getServiceInfo();int originalFlags=info.flags;info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS|android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;test.getUiAutomation().setServiceInfo(info);Activity activity=test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));LyricsStageDialog[] dialog={null};int[] refreshed={0};Clock clock=new Clock();clock.position=1200;
        try{
            test.runOnMainSync(()->{try{dialog[0]=new LyricsStageDialog(activity,new LyricsStageDialog.Host(){public long duration(){return 12800;}public long position(){return clock.position;}public boolean playing(){return false;}public void seek(long value){clock.position=value;}public void toggle(){}public void previous(){}public void next(){}public void refresh(){refreshed[0]++;}public void importLyrics(){}});dialog[0].setTrack("lyric-motion-fixture","开源动效实测");dialog[0].setTimeline(LyricsTimeline.parse(fixture()));dialog[0].show();}catch(Exception error){throw new RuntimeException(error);}});test.waitForIdleSync();
            for(int style=6;style<=7;style++){test.runOnMainSync(()->text(dialog[0].getWindow().getDecorView(),"动效").performClick());clickMenu(LyricsAppearance.STYLES[style]);test.waitForIdleSync();int chosen=style;test.runOnMainSync(()->check(dialog[0].stage.appearance().style==chosen,"real popup did not apply new motion style"));check(LyricsAppearance.read(prefs).style==style,"new motion choice did not persist");}
            test.runOnMainSync(()->text(dialog[0].getWindow().getDecorView(),"动效").performClick());clickMenu("重新获取歌词");test.waitForIdleSync();check(refreshed[0]==1&&LyricsAppearance.read(prefs).style==7,"new style IDs intercepted the existing refresh action");
            test.runOnMainSync(()->{dialog[0].dismiss();check(LyricsAppearance.read(prefs).style==7,"closing lyrics discarded new preset");});
        }finally{test.runOnMainSync(()->{if(dialog[0]!=null)dialog[0].dismiss();activity.finish();});original.save(prefs);info.flags=originalFlags;test.getUiAutomation().setServiceInfo(info);}
    }
}
