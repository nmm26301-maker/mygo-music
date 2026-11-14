package com.mygo.music;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;

/** Responsive album and lyric composition. Browsing never changes the audio clock until confirmed. */
final class LyricsPlayerBody extends ViewGroup {
    final LyricsStageView stage;
    final LinearLayout album;
    final Cover cover;
    final Button seekLine,returnCurrent;
    private final TextView albumTitle,state;
    private boolean albumEnabled=true,split;
    private int lyricLeft;
    LyricsPlayerBody(Context context,LyricsStageView stage){
        super(context);this.stage=stage;setClipChildren(true);
        album=new LinearLayout(context);album.setOrientation(LinearLayout.VERTICAL);album.setGravity(Gravity.CENTER);
        cover=new Cover(context);album.addView(cover,new LinearLayout.LayoutParams(1,1));
        albumTitle=label(18);albumTitle.setGravity(Gravity.CENTER);albumTitle.setMaxLines(2);albumTitle.setEllipsize(TextUtils.TruncateAt.END);albumTitle.setPadding(dp(4),dp(12),dp(4),0);album.addView(albumTitle,new LinearLayout.LayoutParams(-1,-2));
        state=label(11);state.setTextColor(0xffaeb8cf);state.setGravity(Gravity.CENTER);state.setPadding(0,dp(5),0,0);album.addView(state,new LinearLayout.LayoutParams(-1,-2));
        addView(album);addView(stage);
        seekLine=chip("播放这句");returnCurrent=chip("回到当前");addView(seekLine);addView(returnCurrent);
        seekLine.setOnClickListener(v->stage.seekBrowse());returnCurrent.setOnClickListener(v->stage.follow());
        stage.setBrowseListener(this::updateBrowse);setPlaying(false);
    }
    private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    private TextView label(int size){TextView view=new TextView(getContext());view.setTextColor(0xfff2f4fc);view.setTextSize(size);return view;}
    static void surface(Button button,int color,float radius){GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(radius);button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x448fa9dc),shape,null));}
    private Button chip(String text){Button button=new Button(getContext());button.setText(text);button.setTextSize(12);button.setTextColor(0xffedf2ff);button.setAllCaps(false);button.setMinHeight(0);button.setMinimumHeight(0);button.setMinimumWidth(0);button.setMinWidth(0);button.setPadding(dp(12),0,dp(12),0);surface(button,0xee242b3d,dp(22));return button;}
    void setAlbumEnabled(boolean enabled){if(albumEnabled!=enabled){albumEnabled=enabled;requestLayout();}}
    boolean split(){return split;}
    void setTitle(String title){albumTitle.setText(title);cover.setContentDescription(title.isEmpty()?"专辑封面":title+"的专辑封面");}
    void setPlaying(boolean playing){String text=playing?"正在播放 · 封面与歌词":"暂停中 · 封面与歌词";if(!text.contentEquals(state.getText()))state.setText(text);}
    void setArtwork(Bitmap bitmap){cover.setArtwork(bitmap);}
    void release(){cover.release();}
    void updateBrowse(){
        long start=stage.browseStart();boolean visible=start>=0;
        int visibility=visible?VISIBLE:GONE;if(seekLine.getVisibility()!=visibility)seekLine.setVisibility(visibility);if(returnCurrent.getVisibility()!=visibility)returnCurrent.setVisibility(visibility);
        if(visible){String time=String.format(java.util.Locale.CHINA,"%02d:%02d",start/60000,start/1000%60);seekLine.setText(time+"  播放这句");seekLine.setContentDescription("从歌词时间 "+time+" 播放");}
    }
    private void exact(View child,int width,int height){child.measure(MeasureSpec.makeMeasureSpec(Math.max(1,width),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(1,height),MeasureSpec.EXACTLY));}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int w=MeasureSpec.getSize(widthSpec),h=MeasureSpec.getSize(heightSpec);setMeasuredDimension(w,h);
        split=albumEnabled&&w>=dp(560)&&w>=h*1.35f&&h>=dp(170);
        int albumWidth=split?Math.min(dp(300),(int)(w*.31f)):0;lyricLeft=split?albumWidth+dp(20):0;
        album.setVisibility(split?VISIBLE:GONE);
        if(split){int side=Math.max(dp(80),Math.min(albumWidth-dp(16),h-dp(86)));LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)cover.getLayoutParams();if(params.width!=side||params.height!=side){params.width=side;params.height=side;cover.setLayoutParams(params);}exact(album,albumWidth,h);}
        exact(stage,w-lyricLeft,h);int available=w-lyricLeft-dp(16);
        exact(seekLine,Math.min(dp(164),available/2-dp(4)),dp(40));exact(returnCurrent,Math.min(dp(112),available/2-dp(4)),dp(40));
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        int w=right-left,h=bottom-top;if(split)album.layout(0,0,album.getMeasuredWidth(),h);stage.layout(lyricLeft,0,w,h);
        seekLine.layout(lyricLeft+dp(8),h-dp(44),lyricLeft+dp(8)+seekLine.getMeasuredWidth(),h-dp(4));returnCurrent.layout(w-dp(8)-returnCurrent.getMeasuredWidth(),h-dp(44),w-dp(8),h-dp(4));
    }
    static final class Cover extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final RectF bounds=new RectF();private final Matrix matrix=new Matrix();
        private Bitmap bitmap;private BitmapShader shader;
        Cover(Context context){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void setArtwork(Bitmap source){
            release();if(source!=null&&!source.isRecycled()){float factor=Math.min(1,384f/Math.max(source.getWidth(),source.getHeight()));Bitmap scaled=Bitmap.createScaledBitmap(source,Math.max(1,Math.round(source.getWidth()*factor)),Math.max(1,Math.round(source.getHeight()*factor)),true);bitmap=scaled==source?source.copy(Bitmap.Config.ARGB_8888,false):scaled;shader=new BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);fit();}invalidate();
        }
        private void fit(){if(bitmap==null)return;float scale=Math.max(getWidth()/(float)bitmap.getWidth(),getHeight()/(float)bitmap.getHeight());matrix.setScale(scale,scale);matrix.postTranslate((getWidth()-bitmap.getWidth()*scale)/2,(getHeight()-bitmap.getHeight()*scale)/2);shader.setLocalMatrix(matrix);}
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){bounds.set(0,0,w,h);fit();}
        @Override protected void onDraw(Canvas canvas){float radius=12*getResources().getDisplayMetrics().density;paint.setShader(shader);paint.setColor(0xff303b56);canvas.drawRoundRect(bounds,radius,radius,paint);paint.setShader(null);if(bitmap==null){float x=getWidth()/2f,y=getHeight()/2f;paint.setColor(0xff465572);canvas.drawCircle(x,y,getWidth()*.31f,paint);paint.setColor(0xff263147);canvas.drawCircle(x,y,getWidth()*.22f,paint);paint.setColor(0xff9aaccd);canvas.drawCircle(x,y,getWidth()*.042f,paint);}}
        void release(){paint.setShader(null);shader=null;if(bitmap!=null){bitmap.recycle();bitmap=null;}}
    }
}
