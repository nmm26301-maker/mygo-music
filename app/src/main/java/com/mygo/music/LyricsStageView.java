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
    private final Paint backdrop=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final LinkedHashMap<Integer,StaticLayout> layouts=new LinkedHashMap<>();
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
    void setTimeline(LyricsTimeline timeline){this.timeline=timeline;layouts.clear();anchor=-1;offset=0;following=true;lastPosition=-1;invalidate();}
    LyricsTimeline timeline(){return timeline;}
    int activeLine(){return timeline.active(clock.position());}
    void setMessage(String value){message=value;invalidate();}
    void setEffect(int value){effect=value;invalidate();}
    void follow(){following=true;anchor=-1;offset=0;invalidate();}
    boolean following(){return following;}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){layouts.clear();textSize=Math.max(dp(25),Math.min(dp(48),Math.min(w*.075f,h*.20f)));lineGap=Math.max(dp(28),h*.18f);backdrop.setShader(new LinearGradient(0,0,w,h,new int[]{Color.rgb(44,64,106),Color.rgb(32,39,68),Color.rgb(21,22,39)},null,Shader.TileMode.CLAMP));anchor=-1;offset=0;}
    private StaticLayout layout(int index){
        StaticLayout value=layouts.get(index);if(value!=null)return value;
        paint.setTextSize(textSize);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        value=StaticLayout.Builder.obtain(timeline.lines.get(index).text,0,timeline.lines.get(index).text.length(),paint,Math.max(1,(int)(getWidth()*.84f)))
            .setIncludePad(false).setLineSpacing(dp(5),1).setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY).build();
        layouts.put(index,value);return value;
    }
    private float distance(int from,int to){float result=0;if(from==to)return 0;int lo=Math.min(from,to),hi=Math.max(from,to);for(int i=lo;i<hi;i++)result+=(layout(i).getHeight()+layout(i+1).getHeight())/2f+lineGap;return from<to?result:-result;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(paintBackdrop)canvas.drawRect(0,0,getWidth(),getHeight(),backdrop);
        long position=clock.position(),now=SystemClock.uptimeMillis();int active=timeline.active(position);
        if(timeline.lines.isEmpty()){
            paint.setTextSize(dp(20));paint.setTypeface(Typeface.DEFAULT);paint.setColor(0xffc8cddd);paint.clearShadowLayer();paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(message,getWidth()/2f,getHeight()/2f,paint);paint.setTextAlign(Paint.Align.LEFT);return;
        }
        int target=Math.max(0,active);
        if(anchor<0)anchor=target;
        if(following&&target!=anchor){if(Math.abs(target-anchor)<=3&&lastPosition>=0&&Math.abs(position-lastPosition)<1800)offset+=distance(anchor,target);else offset=0;anchor=target;}
        if(lastPosition>=0&&Math.abs(position-lastPosition)>1800&&following)offset=0;
        long elapsed=frameTime==0?16:Math.min(64,now-frameTime);frameTime=now;lastPosition=position;
        if(!dragging){offset*=Math.exp(-elapsed/130.0);if(Math.abs(offset)<.3f)offset=0;}
        drawnCenters.clear();int lo=Math.max(0,anchor-5),hi=Math.min(timeline.lines.size()-1,anchor+5);
        for(int i=lo;i<=hi;i++){
            StaticLayout text=layout(i);float center=getHeight()*.48f+offset+distance(anchor,i);float top=center-text.getHeight()/2f;
            if(top>getHeight()+dp(60)||top+text.getHeight()< -dp(60))continue;
            drawnCenters.put(i,center);LyricsTimeline.Line line=timeline.lines.get(i);boolean current=timeline.timed&&i==active;
            float focus=1-Math.min(1,Math.abs(center-getHeight()*.48f)/Math.max(1,getHeight()*.7f));
            canvas.save();canvas.translate(getWidth()*.08f,top);
            float scale=current?1:.86f+.06f*focus;canvas.scale(scale,scale,0,text.getHeight()/2f);
            paint.clearShadowLayer();paint.setTextSize(textSize);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
            paint.setColor(current?0xff777f97:Color.argb((int)(50+65*focus),210,217,235));text.draw(canvas);
            if(current){if(line.words.isEmpty()){paint.setColor(Color.WHITE);if(effect==0)paint.setShadowLayer(dp(5),0,0,0x80ffffff);text.draw(canvas);paint.clearShadowLayer();}
                else for(LyricsTimeline.Word word:line.words){float fraction=word.fraction(position);if(fraction>0)highlight(canvas,text,line,word,fraction,position>=word.start&&position<word.end);}}
            if(!line.translation.isEmpty()){
                paint.clearShadowLayer();paint.setTextSize(Math.max(dp(13),textSize*.40f));paint.setTypeface(Typeface.DEFAULT);paint.setColor(current?0xffc2c9df:0x668d97b1);
                StaticLayout translation=StaticLayout.Builder.obtain(line.translation,0,line.translation.length(),paint,text.getWidth()).setIncludePad(false).build();canvas.save();canvas.translate(0,text.getHeight()+dp(10));translation.draw(canvas);canvas.restore();
            }
            canvas.restore();
        }
        Iterator<Integer> keys=layouts.keySet().iterator();while(keys.hasNext()){int i=keys.next();if(i<lo-2||i>hi+2)keys.remove();}
        if(isShown()&&(clock.playing()||Math.abs(offset)>.3f))postInvalidateOnAnimation();
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
            paint.clearShadowLayer();paint.setColor(Color.WHITE);text.draw(canvas);canvas.restore();
            if(singing&&effect!=2&&!rtl){
                canvas.save();canvas.clipRect(rect.left-dp(12),rect.top-dp(20),edge,rect.bottom+dp(20));
                paint.setColor(Color.WHITE);paint.setShadowLayer(dp(effect==0?12:5),0,0,effect==0?0xf0ffffff:0x90ffffff);
                canvas.drawText(line.text,range[0],range[1],text.getPrimaryHorizontal(range[0]),text.getLineBaseline(range[2]),paint);
                paint.clearShadowLayer();canvas.restore();
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
