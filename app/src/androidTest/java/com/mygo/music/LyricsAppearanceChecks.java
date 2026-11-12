package com.mygo.music;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Pixel checks and real controls for all six native layouts, not screenshot-only menu assertions. */
final class LyricsAppearanceChecks {
    private final Instrumentation test;private final Context context;
    LyricsAppearanceChecks(Instrumentation test){this.test=test;context=test.getTargetContext();}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static final class Clock implements LyricsStageView.Clock {long position;public long position(){return position;}public boolean playing(){return false;}public void seek(long value){position=value;}}
    private static String fixture()throws Exception{return new JSONObject().put("lines",new JSONArray()
        .put(new JSONObject().put("start",1000).put("end",5500).put("translation","Every note comes alive, every word follows the song").put("words",new JSONArray().put(new JSONObject().put("text","让每个音符").put("start",1000).put("end",3300)).put(new JSONObject().put("text","都看得见").put("start",3300).put("end",5500))))
        .put(new JSONObject().put("start",9500).put("end",15000).put("translation","Let the words flow softly with the melody").put("words",new JSONArray().put(new JSONObject().put("text","歌词随着旋律温柔流动").put("start",9500).put("end",14500))))
        .put(new JSONObject().put("start",18000).put("end",23000).put("words",new JSONArray().put(new JSONObject().put("text","الموسيقى تجمعنا Hello 世界").put("start",18000).put("end",22500))))).toString();}
    private static Bitmap draw(View view){Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));return bitmap;}
    private static int difference(Bitmap a,Bitmap b){int count=0;for(int y=0;y<a.getHeight();y+=2)for(int x=0;x<a.getWidth();x+=2)if(a.getPixel(x,y)!=b.getPixel(x,y))count++;return count;}
    private static void write(Bitmap bitmap,File file)throws Exception{try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    void run()throws Exception{test.runOnMainSync(()->{try{pixels();}catch(Exception failure){throw new RuntimeException(failure);}});controls();}
    private void pixels()throws Exception{
        Clock clock=new Clock();LyricsStageView view=new LyricsStageView(context,clock);view.layout(0,0,1280,640);view.setTimeline(LyricsTimeline.parse(fixture()));File folder=new File(context.getExternalFilesDir(null),"qa/lyric-styles");folder.mkdirs();
        Set<Integer> fingerprints=new HashSet<>();
        for(int style=0;style<6;style++){
            LyricsAppearance options=new LyricsAppearance();options.style=style;options.background=2;options.color=1;view.setAppearance(options);clock.position=2400;Bitmap image=draw(view),paused=draw(view);check(image.sameAs(paused),"paused preset continued animating: "+style);paused.recycle();int[] pixels=new int[1280*640];image.getPixels(pixels,0,1280,0,0,1280,640);fingerprints.add(Arrays.hashCode(pixels));
            clock.position=4200;Bitmap later=draw(view);check(difference(image,later)>100,"preset lost word progress: "+style);later.recycle();clock.position=2400;Bitmap rewind=draw(view);check(image.sameAs(rewind),"preset retained stale state after rewind: "+style);rewind.recycle();
            Canvas canvas=new Canvas(image);Paint label=new Paint(Paint.ANTI_ALIAS_FLAG);label.setTextSize(24);label.setColor(0xffaab6d5);canvas.drawText(LyricsAppearance.STYLES[style],30,40,label);write(image,new File(folder,"style-"+style+".png"));
            view.layout(0,0,280,360);clock.position=19500;Bitmap narrow=draw(view);check(narrow.getWidth()==280,"narrow RTL layout failed");narrow.recycle();view.layout(0,0,1280,640);
        }
        check(fingerprints.size()==6,"style names did not produce six different rendered layouts");
        LyricsAppearance options=new LyricsAppearance();options.style=2;options.background=2;view.setAppearance(options);clock.position=2400;Bitmap frame=draw(view);frame.recycle();long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,640,640*.77f,0),up=MotionEvent.obtain(now,now+40,MotionEvent.ACTION_UP,640,640*.77f,0);view.onTouchEvent(down);view.onTouchEvent(up);down.recycle();up.recycle();check(clock.position==9500,"dual-line tap did not seek the next source timestamp");
        options.style=1;options.size=80;options.translation=true;view.setAppearance(options);clock.position=2400;Bitmap translated=draw(view);Field size=LyricsStageView.class.getDeclaredField("textSize");size.setAccessible(true);float small=size.getFloat(view);options.size=135;options.translation=false;view.setAppearance(options);Bitmap large=draw(view);check(size.getFloat(view)>small*1.6f&&difference(translated,large)>500,"font or translation controls did not affect actual rendering");translated.recycle();large.recycle();
        Bitmap artwork=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);Canvas art=new Canvas(artwork);Paint paint=new Paint();paint.setShader(new LinearGradient(0,0,16,16,0xffc43b85,0xff2969a0,Shader.TileMode.CLAMP));art.drawRect(0,0,16,16,paint);
        LyricsBackdrop backdrop=new LyricsBackdrop();backdrop.artwork(artwork);check(!artwork.isRecycled(),"background recycled the caller's album art");options=new LyricsAppearance();options.background=0;Bitmap moving=background(backdrop,options,0),shifted=background(backdrop,options,15000);check(!moving.sameAs(shifted),"album fluid background never moved");moving.recycle();shifted.recycle();options.background=1;Bitmap still=background(backdrop,options,0),same=background(backdrop,options,15000);check(still.sameAs(same),"static album background moved");still.recycle();same.recycle();options.background=0;options.reduceMotion=true;still=background(backdrop,options,0);same=background(backdrop,options,15000);check(still.sameAs(same),"reduced motion kept animating the background");still.recycle();same.recycle();backdrop.release();check(!artwork.isRecycled(),"background cleanup destroyed the caller's cover");
        File frames=new File(folder,"frames");frames.mkdirs();view.layout(0,0,640,360);view.setArtwork(artwork);for(int style=0;style<6;style++){options=new LyricsAppearance();options.style=style;options.color=style==5?5:1;view.setAppearance(options);for(int i=0;i<24;i++){clock.position=800+i*420;Bitmap image=draw(view);Canvas canvas=new Canvas(image);Paint label=new Paint(Paint.ANTI_ALIAS_FLAG);label.setTextSize(16);label.setColor(0xffc6cfe5);canvas.drawText(LyricsAppearance.STYLES[style],20,26,label);write(image,new File(frames,String.format(Locale.US,"frame-%03d.png",style*24+i)));}}view.release();artwork.recycle();
    }
    private static Bitmap background(LyricsBackdrop backdrop,LyricsAppearance options,long time){Bitmap image=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);backdrop.draw(new Canvas(image),time,options,320,180);return image;}
    private static View description(View root,String description){if(root.getContentDescription()!=null&&description.contentEquals(root.getContentDescription()))return root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=description(group.getChildAt(i),description);if(found!=null)return found;}}return null;}
    private static View text(View root,String value){if(root instanceof TextView&&value.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=text(group.getChildAt(i),value);if(found!=null)return found;}}return null;}
    private void controls()throws Exception{
        SharedPreferences prefs=context.getSharedPreferences("library",0);LyricsAppearance original=LyricsAppearance.read(prefs);Activity activity=test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));LyricsStageDialog[] page={null};AlertDialog[] settings={null};Clock clock=new Clock();clock.position=2400;
        try{
            test.runOnMainSync(()->{try{page[0]=new LyricsStageDialog(activity,new LyricsStageDialog.Host(){public long duration(){return 23000;}public long position(){return clock.position;}public boolean playing(){return false;}public void seek(long position){clock.position=position;}public void toggle(){}public void previous(){}public void next(){}public void refresh(){}public void importLyrics(){}});page[0].setTrack("appearance-fixture","歌词样式检查");page[0].setTimeline(LyricsTimeline.parse(fixture()));page[0].show();}catch(Exception failure){throw new RuntimeException(failure);}});test.waitForIdleSync();
            test.runOnMainSync(()->{try{Method show=LyricsStageDialog.class.getDeclaredMethod("showAppearance");show.setAccessible(true);show.invoke(page[0]);Field field=LyricsStageDialog.class.getDeclaredField("appearanceDialog");field.setAccessible(true);settings[0]=(AlertDialog)field.get(page[0]);}catch(Exception failure){throw new RuntimeException(failure);}});test.waitForIdleSync();
            test.runOnMainSync(()->((Spinner)description(settings[0].getWindow().getDecorView(),"显示样式")).setSelection(2));test.waitForIdleSync();
            test.runOnMainSync(()->{check(page[0].stage.appearance().style==2,"style spinner did not apply to the real lyric view");View translation=text(settings[0].getWindow().getDecorView(),"显示已有译文");check(translation!=null,"translation switch missing");translation.performClick();settings[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick();});test.waitForIdleSync();
            check(LyricsAppearance.read(prefs).style==2,"chosen style was not saved");boolean translated=page[0].stage.appearance().translation;check(LyricsAppearance.read(prefs).translation==translated,"translation switch was not saved");test.runOnMainSync(page[0]::dismiss);
            test.runOnMainSync(()->{LyricsStageDialog restored=new LyricsStageDialog(activity,new LyricsStageDialog.Host(){public long duration(){return 10000;}public long position(){return 2400;}public boolean playing(){return false;}public void seek(long position){}public void toggle(){}public void previous(){}public void next(){}public void refresh(){}public void importLyrics(){}});page[0]=restored;restored.show();});test.waitForIdleSync();test.runOnMainSync(()->check(page[0].stage.appearance().style==2&&page[0].stage.appearance().translation==translated,"reopening lyrics lost display choices"));
        }finally{test.runOnMainSync(()->{if(settings[0]!=null)settings[0].dismiss();if(page[0]!=null)page[0].dismiss();activity.finish();});original.save(prefs);}
    }
}
