package com.mygo.music;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.io.*;

/** Real responsive layout, clock-safe browsing and retained user display choices. */
final class LyricsPlayerChecks {
    final Instrumentation test;final Context context;
    LyricsPlayerChecks(Instrumentation test){this.test=test;context=test.getTargetContext();}
    void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static final class Clock implements LyricsStageView.Clock {long position=1200;public long position(){return position;}public boolean playing(){return false;}public void seek(long value){position=value;}}
    Bitmap draw(View view){Bitmap image=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));return image;}
    void save(Bitmap image,String name)throws Exception{File dir=new File(context.getExternalFilesDir(null),"qa/lyric-player");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
    void layout(View view,int width,int height){view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);}
    void drag(LyricsStageView stage){long now=SystemClock.uptimeMillis();float x=stage.getWidth()/2f,y=stage.getHeight()*.7f;for(int i=0;i<3;i++){int action=i==0?MotionEvent.ACTION_DOWN:i==1?MotionEvent.ACTION_MOVE:MotionEvent.ACTION_UP;MotionEvent e=MotionEvent.obtain(now,now+i*50,action,x,i==0?y:y-stage.getHeight()*.65f,0);stage.dispatchTouchEvent(e);e.recycle();}}
    Bitmap artwork(){Bitmap art=Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(art);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setShader(new LinearGradient(0,0,320,320,new int[]{0xff172951,0xff5b4f8b,0xffd08d8a},null,Shader.TileMode.CLAMP));canvas.drawRect(0,0,320,320,paint);paint.setShader(null);paint.setColor(0x447fb8e2);canvas.drawCircle(220,90,110,paint);paint.setColor(0x33495b9e);canvas.drawCircle(90,250,150,paint);return art;}
    void run()throws Exception{
        SharedPreferences prefs=context.getSharedPreferences("library",0);LyricsAppearance original=LyricsAppearance.read(prefs);String offsets=prefs.getString("lyrics_offsets","{}");Clock clock=new Clock();Activity activity=test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));LyricsStageDialog[] page={null};Bitmap art=artwork();
        try{
            test.runOnMainSync(()->{try{LyricsAppearance options=original.copy();options.album=true;options.style=13;options.systemReduceMotion=false;options.reduceMotion=false;options.strength=100;options.save(prefs);page[0]=new LyricsStageDialog(activity,new LyricsStageDialog.Host(){public long duration(){return 12800;}public long position(){return clock.position;}public boolean playing(){return false;}public void seek(long value){clock.position=value;}public void toggle(){}public void previous(){}public void next(){}public void refresh(){}public void importLyrics(){}});page[0].setTrack("market-layout-fixture","旋律与光影");page[0].setTimeline(LyricsTimeline.parse(LyricsMotionChecks.fixture()));page[0].setArtwork(art);page[0].show();}catch(Exception error){throw new RuntimeException(error);}});test.waitForIdleSync();
            test.runOnMainSync(()->{try{
                LyricsStageDialog p=page[0];check(p.body.split(),"wide real lyric page did not show cover beside lyrics");check(p.body.album.getRight()<p.stage.getLeft(),"album overlaps lyric reading area");check(!art.isRecycled(),"player stole caller artwork ownership");save(draw(p.getWindow().getDecorView()),"player-page.png");
                View toggle=p.getWindow().getDecorView().findViewWithTag("lyrics-layout-toggle");check(toggle!=null,"layout control missing");toggle.performClick();check(!LyricsAppearance.read(prefs).album,"pure lyric choice was not saved");
            }catch(Exception error){throw new RuntimeException(error);}});test.waitForIdleSync();
            test.runOnMainSync(()->{try{LyricsStageDialog p=page[0];check(!p.body.split()&&p.stage.getLeft()==0,"pure lyrics kept hidden album width");save(draw(p.getWindow().getDecorView()),"pure-page.png");p.getWindow().getDecorView().findViewWithTag("lyrics-layout-toggle").performClick();}catch(Exception error){throw new RuntimeException(error);}});test.waitForIdleSync();
            test.runOnMainSync(()->{try{LyricsStageDialog p=page[0];p.timing.set(600);p.stage.follow();draw(p.stage).recycle();long before=clock.position;drag(p.stage);check(clock.position==before&&!p.stage.following(),"browsing unexpectedly sought audio");check(p.body.seekLine.getVisibility()==View.VISIBLE&&p.body.returnCurrent.getVisibility()==View.VISIBLE,"browse time and return controls missing");long selected=p.stage.browseStart();check(selected>=0,"browse selection lacks source timestamp");save(draw(p.getWindow().getDecorView()),"browsing-page.png");p.body.seekLine.performClick();check(clock.position==selected+600&&p.stage.following(),"confirmed browse seek ignored song synchronization");check(p.body.seekLine.getVisibility()==View.GONE,"seek left stale browse controls");draw(p.stage).recycle();before=clock.position;drag(p.stage);p.body.returnCurrent.performClick();check(clock.position==before&&p.stage.following(),"return to current altered the audio clock");p.setTimeline(LyricsTimeline.parse("没有时间轴\n只阅读不跳转"));draw(p.stage).recycle();drag(p.stage);check(p.body.seekLine.getVisibility()==View.GONE,"plain lyrics invented a seek timestamp");p.setTimeline(LyricsTimeline.parse(LyricsMotionChecks.fixture()));check(p.body.returnCurrent.getVisibility()==View.GONE,"track timeline replacement retained browsing state");p.timing.set(0);
                p.dismiss();check(!art.isRecycled(),"closing lyrics recycled caller cover");
            }catch(Exception error){throw new RuntimeException(error);}});
            test.runOnMainSync(()->{try{
                LyricsStageView stage=new LyricsStageView(context,clock);stage.setBackdrop(false);LyricsAppearance options=new LyricsAppearance();options.style=13;options.systemReduceMotion=false;options.color=1;stage.setAppearance(options);stage.setTimeline(LyricsTimeline.parse(LyricsMotionChecks.fixture()));LyricsPlayerBody body=new LyricsPlayerBody(context,stage);body.setTitle("旋律与光影");body.setArtwork(art);
                float density=context.getResources().getDisplayMetrics().density;int wide=Math.round(960*density),height=Math.round(400*density);layout(body,wide,height);check(body.split(),"responsive body missing wide composition");int withAlbum=stage.getWidth();body.setAlbumEnabled(false);layout(body,wide,height);check(stage.getWidth()>withAlbum,"pure mode did not restore lyric width");body.setAlbumEnabled(true);layout(body,Math.round(360*density),Math.round(500*density));check(!body.split()&&stage.getLeft()==0,"narrow player squeezed lyrics beside an album");save(draw(body),"narrow.png");
                layout(body,wide,height);for(int i=0;i<90;i++){clock.position=50+i*55;save(draw(body),String.format(java.util.Locale.US,"frame-%03d.png",i));}
                body.setArtwork(null);save(draw(body),"placeholder.png");body.release();stage.release();check(!art.isRecycled(),"body cleanup recycled caller art");
                LyricsAppearance saved=LyricsAppearance.read(prefs);saved.album=false;saved.save(prefs);LyricsStageDialog restored=new LyricsStageDialog(activity,new LyricsStageDialog.Host(){public long duration(){return 0;}public long position(){return 0;}public boolean playing(){return false;}public void seek(long value){}public void toggle(){}public void previous(){}public void next(){}public void refresh(){}public void importLyrics(){}});restored.show();page[0]=restored;
            }catch(Exception error){throw new RuntimeException(error);}});test.waitForIdleSync();test.runOnMainSync(()->check(!page[0].body.split()&&!page[0].stage.appearance().album,"reopened page lost pure lyric preference"));
            System.out.println("MYGO_LYRIC_PLAYER_OK: responsive actual album/lyrics split and pure mode, saved/reopened choices, narrow fallback, caller art ownership, browsing does not seek, confirmed source-time seek with offset, return without seeking, plain-text and replacement reset");
        }finally{test.runOnMainSync(()->{if(page[0]!=null)page[0].dismiss();activity.finish();});original.save(prefs);prefs.edit().putString("lyrics_offsets",offsets).commit();art.recycle();}
    }
}
