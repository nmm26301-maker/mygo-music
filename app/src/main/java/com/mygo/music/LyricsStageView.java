package com.mygo.music;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.text.*;
import android.view.*;
import java.util.*;

/** Full-screen native lyric renderer. Highlight width is continuous even inside one character. */
final class LyricsStageView extends View {
    interface Clock {long position();boolean playing();void seek(long position);}
    private final Clock clock;
    private final TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
    private final LyricsBackdrop backdrop=new LyricsBackdrop();
    private final LyricsStageEffects stageEffects=new LyricsStageEffects();
    private LyricsAppearance appearance=new LyricsAppearance();
    private static final int[] THEME_COLORS={Color.WHITE,Color.WHITE,0xffa3ffe0,0xffffdf91,0xffffb7d7,0xffb9c5ff};
    private Shader highlightShader;
    private final Map<Integer,StaticLayout> translations=new HashMap<>();
    private final LinkedHashMap<Integer,StaticLayout> layouts=new LinkedHashMap<>();
    private final Map<Integer,List<Glyph>> glyphs=new HashMap<>();
    private final Map<Integer,Float> drawnCenters=new HashMap<>();
    private LyricsTimeline timeline=LyricsTimeline.empty();
    private int anchor=-1,effect=0;
    private float offset,downY,lastY;
    private boolean dragging,following=true,paintBackdrop=true;
    private long frameTime,lastPosition=-1;
    private float textSize,lineGap;
    private String message="正在获取歌词";
    LyricsStageView(Context context,Clock clock){super(context);this.clock=clock;setLayerType(View.LAYER_TYPE_SOFTWARE,null);setContentDescription("全屏歌词，滑动浏览，点按歌词跳转播放");}
    private float dp(float value){return value*getResources().getDisplayMetrics().density;}
    void setBackdrop(boolean enabled){paintBackdrop=enabled;invalidate();}
    void drawBackground(Canvas canvas,int width,int height){backdrop.draw(canvas,clock.position(),appearance,width,height);}
    void setArtwork(Bitmap bitmap){backdrop.artwork(bitmap);highlightShader=null;invalidate();if(getParent() instanceof View)((View)getParent()).invalidate();}
    void release(){backdrop.release();}
    LyricsAppearance appearance(){return appearance.copy();}
    void setAppearance(LyricsAppearance value){appearance=value.copy();effect=(value.style==0||value.style>=6)?0:(value.style==2||value.style==4?2:1);clearLayouts();metrics();anchor=-1;offset=0;following=true;invalidate();if(getParent() instanceof View)((View)getParent()).invalidate();}
    private void clearLayouts(){layouts.clear();glyphs.clear();translations.clear();highlightShader=null;}
    private boolean expressive(){return appearance.style>=6;}
    private boolean compactStage(){return appearance.style==2||appearance.style==3||appearance.style==13;}
    private float focusY(){return compactStage()?getHeight()*.33f:getHeight()*((appearance.style==1||expressive())?.43f:.48f);}
    private int accent(){return appearance.color==0?backdrop.accent:THEME_COLORS[appearance.color];}
    private void lit(int width){paint.setColor(accent());if((appearance.style==5||appearance.style==7)&&highlightShader==null)highlightShader=new LinearGradient(0,0,Math.max(1,width),0,new int[]{accent(),0xffffffff,0xffb0cfff},null,Shader.TileMode.CLAMP);paint.setShader((appearance.style==5||appearance.style==7)?highlightShader:null);}
    private void metrics(){float factor=expressive()?1.08f:appearance.style==3?1.24f:appearance.style==4?.79f:appearance.style==2?1.08f:1;float w=getWidth(),h=getHeight();textSize=Math.max(dp(25),Math.min(dp(48),Math.min(w*.075f,h*.20f)))*appearance.size/100f*factor;lineGap=Math.max(dp(28),h*.18f)*appearance.spacing/100f*(appearance.style==4?.67f:1);}

    void setTimeline(LyricsTimeline timeline){this.timeline=timeline;clearLayouts();anchor=-1;offset=0;following=true;lastPosition=-1;invalidate();}
    LyricsTimeline timeline(){return timeline;}
    int activeLine(){return timeline.active(clock.position());}
    void setMessage(String value){message=value;invalidate();}
    void setEffect(int value){if(effect!=value){effect=value;clearLayouts();anchor=-1;offset=0;}invalidate();}
    int effect(){return effect;}
    void follow(){following=true;anchor=-1;offset=0;invalidate();}
    boolean following(){return following;}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){clearLayouts();metrics();backdrop.resize(w,h);anchor=-1;offset=0;}
    private StaticLayout layout(int index){
        StaticLayout value=layouts.get(index);if(value!=null)return value;
        paint.setTextSize(textSize);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        value=StaticLayout.Builder.obtain(timeline.lines.get(index).text,0,timeline.lines.get(index).text.length(),paint,Math.max(1,(int)(getWidth()*.84f)))
            .setAlignment((appearance.centered||compactStage())?Layout.Alignment.ALIGN_CENTER:Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(dp(effect==0?12:5),1).setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY).build();
        layouts.put(index,value);return value;
    }
    private StaticLayout translation(int index){
        if(!appearance.translation||timeline.lines.get(index).translation.isEmpty())return null;StaticLayout value=translations.get(index);if(value!=null)return value;
        int width=layout(index).getWidth();paint.setTextSize(Math.max(dp(13),textSize*.40f));paint.setTypeface(Typeface.DEFAULT);String text=timeline.lines.get(index).translation;
        value=StaticLayout.Builder.obtain(text,0,text.length(),paint,width).setAlignment((appearance.centered||compactStage())?Layout.Alignment.ALIGN_CENTER:Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build();translations.put(index,value);return value;
    }
    private float blockHeight(int index){StaticLayout text=layout(index),translation=translation(index);return text.getHeight()+(translation==null?0:dp(10)+translation.getHeight());}
    private float distance(int from,int to){float result=0;if(from==to)return 0;int lo=Math.min(from,to),hi=Math.max(from,to);for(int i=lo;i<hi;i++)result+=(blockHeight(i)+blockHeight(i+1))/2f+lineGap;return from<to?result:-result;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(paintBackdrop)drawBackground(canvas,getWidth(),getHeight());
        long position=clock.position(),now=SystemClock.uptimeMillis();int active=timeline.active(position);
        if(timeline.lines.isEmpty()){
            paint.setShader(null);paint.setTextSize(dp(20));paint.setTypeface(Typeface.DEFAULT);paint.setColor(0xffc8cddd);paint.clearShadowLayer();paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(message,getWidth()/2f,getHeight()/2f,paint);paint.setTextAlign(Paint.Align.LEFT);return;
        }
        int target=Math.max(0,compactStage()&&!following&&anchor>=0?anchor:active);
        if(anchor<0)anchor=target;
        if(following&&target!=anchor){if(!expressive()&&Math.abs(target-anchor)<=3&&lastPosition>=0&&Math.abs(position-lastPosition)<1800)offset+=distance(anchor,target);else offset=0;anchor=target;}
        if(lastPosition>=0&&Math.abs(position-lastPosition)>1800&&following)offset=0;
        long elapsed=frameTime==0?16:Math.min(64,now-frameTime);frameTime=now;lastPosition=position;
        if(!dragging){offset=appearance.quiet()?0:(float)(offset*Math.exp(-elapsed/(appearance.style==1?200.0:130.0)));if(Math.abs(offset)<.3f)offset=0;}
        drawnCenters.clear();int lo=compactStage()?target:Math.max(0,anchor-5),hi=Math.min(timeline.lines.size()-1,compactStage()?target+1:anchor+5);
        for(int i=lo;i<=hi;i++){
            StaticLayout text=layout(i);float block=blockHeight(i);float center=compactStage()?(i==target?focusY():getHeight()*.77f):focusY()+offset+distance(anchor,i);if(expressive()&&appearance.style!=11&&appearance.style!=13&&following&&active>0&&!appearance.quiet()){
                LyricsTimeline.Line entered=timeline.lines.get(active);long interval=entered.start-timeline.lines.get(active-1).start;
                center+=distance(active-1,active)*LyricsExpressiveMotion.cascade(position-entered.start,interval,i-active);
            }
            float handoff=appearance.style==13&&following&&target==active&&timeline.timed&&active>=0&&active+1<timeline.lines.size()&&!appearance.quiet()?LyricsStageEffects.handoff(position,timeline.lines.get(active).start,timeline.lines.get(active+1).start,appearance.strength/100f):0;
            if(appearance.style==13)center-=(getHeight()*.77f-focusY())*handoff;
            float top=center-block/2f;
            if(top>getHeight()+dp(60)||top+text.getHeight()< -dp(60))continue;
            drawnCenters.put(i,center);LyricsTimeline.Line line=timeline.lines.get(i);boolean current=timeline.timed?i==active:i==target;
            float focus=1-Math.min(1,Math.abs(center-focusY())/Math.max(1,getHeight()*.7f));
            int rowSave=canvas.save();canvas.translate(getWidth()*.08f,top);
            if(handoff>0)canvas.saveLayerAlpha(-dp(40),-dp(40),text.getWidth()+dp(40),block+dp(40),(int)(current?255*(1-handoff):220+35*handoff));
            float scale=appearance.style==4?1:current?(expressive()?1.04f:1):compactStage()?.70f:expressive()?.80f+.09f*focus:.86f+.06f*focus;
            if(appearance.style==13)scale=current?1.04f-.10f*handoff:.70f+.34f*handoff;
            if(compactStage())scale=Math.min(scale,Math.max(dp(45),getHeight()*(i==target?.57f:appearance.style==13?.30f+.27f*handoff:.30f))/Math.max(1,block));
            float pivot=(appearance.centered||compactStage())?text.getWidth()/2f:0;canvas.scale(scale,scale,pivot,block/2f);
            if(current&&appearance.style==11&&timeline.timed&&!appearance.quiet())stageEffects.page(canvas,text.getWidth(),block,LyricsStageEffects.entrance(position,line.start,line.end)*appearance.strength/100f,getResources().getDisplayMetrics().density);
            paint.setShader(null);paint.clearShadowLayer();paint.setTextSize(textSize);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
            if(current&&effect==0&&!line.words.isEmpty()&&text.getParagraphDirection(0)>0&&!hasRtl(text,line.text))drawCharacters(canvas,text,line,i,position);
            else{
                paint.setColor(current?(appearance.style==2?0xff647085:0xff777f97):Color.argb((int)(appearance.style==4?135+60*focus:appearance.style==1?35+70*focus:50+65*focus),210,217,235));text.draw(canvas);
                if(current){if(line.words.isEmpty()){lit(text.getWidth());if(effect==0&&!appearance.quiet())paint.setShadowLayer(dp(5),0,0,0x80ffffff);text.draw(canvas);paint.setShader(null);paint.clearShadowLayer();}
                    else for(LyricsTimeline.Word word:line.words){float fraction=word.fraction(position);if(fraction>0)highlight(canvas,text,line,word,fraction,position>=word.start&&position<word.end);}}
            }
            StaticLayout translated=translation(i);
            if(translated!=null){
                paint.setShader(null);paint.clearShadowLayer();paint.setTextSize(Math.max(dp(13),textSize*.40f));paint.setTypeface(Typeface.DEFAULT);paint.setColor(current?0xffc2c9df:0x668d97b1);
                canvas.save();canvas.translate(0,text.getHeight()+dp(10));translated.draw(canvas);canvas.restore();
            }
            if(current&&appearance.style==5){paint.setShader(null);paint.setColor(accent());paint.setAlpha(150);float fraction=line.end<=line.start?0:Math.max(0,Math.min(1,(position-line.start)/(float)(line.end-line.start)));canvas.drawRoundRect(0,block+dp(12),Math.max(dp(3),text.getWidth()*fraction),block+dp(14),dp(2),dp(2),paint);paint.setAlpha(255);}
            canvas.restoreToCount(rowSave);
        }
        Iterator<Integer> keys=layouts.keySet().iterator();while(keys.hasNext()){int i=keys.next();if(i<lo-2||i>hi+2){keys.remove();glyphs.remove(i);translations.remove(i);}}
        interlude(canvas,position,active);
        paint.setShader(null);paint.clearShadowLayer();
        if(isShown()&&(clock.playing()||Math.abs(offset)>.3f)){if(appearance.quiet())postInvalidateDelayed(40);else postInvalidateOnAnimation();if(!paintBackdrop&&getParent() instanceof View){if(appearance.quiet())((View)getParent()).postInvalidateDelayed(40);else ((View)getParent()).postInvalidateOnAnimation();}}
    }
    private void interlude(Canvas canvas,long position,int active){
        if(!timeline.timed||timeline.lines.isEmpty())return;long from=0,to=timeline.lines.get(0).start;
        if(active>=0){if(active+1>=timeline.lines.size())return;LyricsTimeline.Line line=timeline.lines.get(active);if(line.estimated||line.words.isEmpty())return;from=line.end;if(!line.words.isEmpty())from=Math.min(from,line.words.get(line.words.size()-1).end);to=timeline.lines.get(active+1).start;}
        if(to-from<2800||position<from||position>=to)return;float progress=Math.max(0,Math.min(1,(position-from)/(float)(to-from)));
        paint.setShader(null);paint.clearShadowLayer();for(int i=0;i<3;i++){float pulse=appearance.quiet()?0:(float)(.5+.5*Math.sin(progress*Math.PI*4-i*.9));paint.setColor(progress>i/3f?accent():0xff6e7587);canvas.drawCircle(getWidth()/2f+dp((i-1)*20),getHeight()-dp(18),dp(3.5f+pulse),paint);}
    }
    private static boolean hasRtl(StaticLayout layout,String text){for(int i=0;i<text.length();i++)if(layout.isRtlCharAt(i))return true;return false;}
    private static final class Glyph {
        int from,to,row;
        long sourceStart,sourceEnd;float phase;boolean emphasize;
        float x,width;
        long start,end;
        Glyph(int from,int to,int row,float x,float width){this.from=from;this.to=to;this.row=row;this.x=x;this.width=width;}
    }
    private List<Glyph> characters(StaticLayout layout,LyricsTimeline.Line line,int index){
        List<Glyph> cached=glyphs.get(index);if(cached!=null)return cached;
        List<Glyph> result=new ArrayList<>();
        for(LyricsTimeline.Word word:line.words){
            List<Glyph> parts=new ArrayList<>();float total=0;
            for(int[] cluster:LyricsCharacterMotion.clusters(word.text)){
                int from=word.from+cluster[0],to=word.from+cluster[1],row=layout.getLineForOffset(from);
                if(line.text.substring(from,to).trim().isEmpty())continue;
                float x=layout.getPrimaryHorizontal(from),right=to>=layout.getLineEnd(row)?layout.getLineRight(row):layout.getPrimaryHorizontal(to);
                float width=Math.abs(right-x);if(width<=0)continue;
                Glyph glyph=new Glyph(from,to,row,Math.min(x,right),width);parts.add(glyph);total+=width;
            }
            float advance=0;
            for(Glyph glyph:parts){glyph.sourceStart=word.start;glyph.sourceEnd=word.end;glyph.phase=advance/Math.max(1,total);glyph.emphasize=!line.estimated&&word.end-word.start>=1000&&parts.size()<=7;glyph.start=word.start+(long)((word.end-word.start)*(advance/Math.max(1,total)));advance+=glyph.width;glyph.end=word.start+(long)((word.end-word.start)*(advance/Math.max(1,total)));result.add(glyph);}
        }
        glyphs.put(index,result);return result;
    }
    private void drawCharacters(Canvas canvas,StaticLayout text,LyricsTimeline.Line line,int index,long position){
        for(Glyph glyph:characters(text,line,index)){
            float lift=appearance.quiet()?0:LyricsCharacterMotion.lift(position,glyph.start,glyph.end)*appearance.strength/100f;
            if(appearance.style==7&&!appearance.quiet()&&glyph.emphasize&&position>=glyph.start)lift+=.85f*LyricsExpressiveMotion.wave(position,glyph.sourceStart,glyph.sourceEnd,glyph.phase)*appearance.strength/100f;
            float breath=appearance.style==9&&!appearance.quiet()?LyricsExpressiveMotion.breathe(position,line.start,line.end)*appearance.strength/100f:0;
            if(appearance.style==9||appearance.style>=10)lift=0;
            float baseline=text.getLineBaseline(glyph.row),pivot=glyph.x+glyph.width/2;
            float progress=LyricsStageEffects.unit(position,glyph.start,glyph.end);
            if(!appearance.quiet()&&appearance.style==12)stageEffects.ripple(canvas,pivot,baseline,textSize,accent(),LyricsStageEffects.unit(position,glyph.start,glyph.end+220),appearance.strength/100f);
            canvas.save();canvas.translate(0,-Math.min(dp(9),textSize*.20f)*lift);float scale=1+.14f*lift+.035f*breath;canvas.scale(scale,scale,pivot,baseline);if(appearance.style==8&&!appearance.quiet())canvas.rotate(LyricsExpressiveMotion.tilt(position,glyph.start,glyph.end)*(1-2*glyph.phase)*appearance.strength/100f,pivot,baseline-textSize*.45f);
            paint.setShader(null);paint.clearShadowLayer();paint.setColor(0xff777f97);
            drawCharacter(canvas,line.text,text,glyph,baseline);
            float fraction=position<glyph.start?0:glyph.end<=glyph.start?1:Math.max(0,Math.min(1,(position-glyph.start)/(float)(glyph.end-glyph.start)));
            if(fraction>0){
                canvas.save();canvas.clipRect(glyph.x-dp(1),text.getLineTop(glyph.row)-dp(16),glyph.x+glyph.width*fraction+dp(1),text.getLineBottom(glyph.row)+dp(8));
                lit(text.getWidth());if(lift>.02f||breath>.02f)paint.setShadowLayer(dp(5)*Math.min(1,lift)+dp(7)*Math.min(1,breath),0,0,0xaaffffff);
                drawCharacter(canvas,line.text,text,glyph,baseline);paint.setShader(null);paint.clearShadowLayer();canvas.restore();
            }
            canvas.restore();
            if(!appearance.quiet()&&appearance.style==10&&position>=glyph.start&&position<glyph.end)stageEffects.meteor(canvas,glyph.x+glyph.width*progress,baseline,textSize,accent(),progress,appearance.strength/100f);
        }
    }
    private void drawCharacter(Canvas canvas,String text,StaticLayout layout,Glyph glyph,float baseline){
        canvas.drawTextRun(text,glyph.from,glyph.to,layout.getLineStart(glyph.row),layout.getLineEnd(glyph.row),glyph.x,baseline,false,paint);
    }
    private void highlight(Canvas canvas,StaticLayout text,LyricsTimeline.Line line,LyricsTimeline.Word word,float fraction,boolean singing){
        float total=0;ArrayList<RectF> bounds=new ArrayList<>();ArrayList<int[]> ranges=new ArrayList<>();
        int fromRow=text.getLineForOffset(word.from),toRow=text.getLineForOffset(Math.max(word.from,word.to-1));
        for(int row=fromRow;row<=toRow;row++){
            int start=Math.max(word.from,text.getLineStart(row)),end=Math.min(word.to,text.getLineEnd(row));if(start>=end)continue;
            Path selection=new Path();text.getSelectionPath(start,end,selection);RectF rect=new RectF();selection.computeBounds(rect,true);
            if(rect.width()<=0)continue;bounds.add(rect);ranges.add(new int[]{start,end,row});total+=rect.width();
        }
        float left=total*fraction;
        for(int i=0;i<bounds.size();i++){
            RectF rect=bounds.get(i);int[] range=ranges.get(i);float lit=Math.max(0,Math.min(rect.width(),left));left-=rect.width();if(lit<=0)continue;
            boolean rtl=text.isRtlCharAt(range[0]);float edge=rtl?rect.right-lit:rect.left+lit;
            canvas.save();Path selection=new Path();text.getSelectionPath(range[0],range[1],selection);canvas.clipPath(selection);
            canvas.clipRect(rtl?edge:rect.left,rect.top,rtl?rect.right:edge,rect.bottom);
            paint.clearShadowLayer();lit(text.getWidth());text.draw(canvas);paint.setShader(null);canvas.restore();
            if(singing&&effect!=2&&!rtl&&!appearance.quiet()){
                canvas.save();canvas.clipRect(rect.left-dp(12),rect.top-dp(20),edge,rect.bottom+dp(20));
                lit(text.getWidth());paint.setShadowLayer(dp(effect==0?12:5),0,0,effect==0?0xf0ffffff:0x90ffffff);
                canvas.drawText(line.text,range[0],range[1],text.getPrimaryHorizontal(range[0]),text.getLineBaseline(range[2]),paint);
                paint.setShader(null);paint.clearShadowLayer();canvas.restore();
            }
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:downY=lastY=event.getY();dragging=false;return true;
            case MotionEvent.ACTION_MOVE:
                if(Math.abs(event.getY()-downY)>dp(8)){dragging=true;following=false;}
                if(dragging&&anchor>=0){offset+=event.getY()-lastY;
                    while(anchor+1<timeline.lines.size()&&offset< -distance(anchor,anchor+1)/2){float d=distance(anchor,anchor+1);anchor++;offset+=d;}
                    while(anchor>0&&offset>distance(anchor-1,anchor)/2){float d=distance(anchor-1,anchor);anchor--;offset-=d;}
                    offset=Math.max(-getHeight(),Math.min(getHeight(),offset));invalidate();}
                lastY=event.getY();return true;
            case MotionEvent.ACTION_UP:
                if(!dragging){performClick();int nearest=-1;float delta=Float.MAX_VALUE;for(Map.Entry<Integer,Float> entry:drawnCenters.entrySet()){float d=Math.abs(entry.getValue()-event.getY());if(d<delta){nearest=entry.getKey();delta=d;}}if(nearest>=0&&delta<dp(90)&&timeline.timed){clock.seek(timeline.lines.get(nearest).start);follow();}}
                dragging=false;invalidate();return true;
            case MotionEvent.ACTION_CANCEL:dragging=false;invalidate();return true;
        }
        return super.onTouchEvent(event);
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
