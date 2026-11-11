package com.mygo.music;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Controls use the same service clock and transport as the notification and native player. */
final class LyricsStageDialog extends Dialog {
    interface Host extends LyricsStageView.Clock {
        long duration();void toggle();void previous();void next();void refresh();void importLyrics();
    }
    private final Host host;
    private final Handler handler=new Handler(Looper.getMainLooper());
    final LyricsStageView stage;
    final SeekBar progress;
    private final TextView title,badge,time;
    private final Button play;
    private boolean touching;
    String trackUri="";
    private final Runnable tick=new Runnable(){public void run(){if(!isShowing())return;long duration=host.duration(),position=host.position();if(!touching)progress.setProgress(duration>0?(int)Math.min(10000,position*10000/duration):0);time.setText(format(position)+" / "+format(duration));play.setText(host.playing()?"暂停":"播放");if(host.playing())getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);stage.invalidate();handler.postDelayed(this,100);}};
    LyricsStageDialog(Activity activity,Host host){
        super(activity,android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);this.host=host;stage=new LyricsStageView(activity,host);
        LinearLayout root=new LinearLayout(activity);root.setOrientation(1);root.setPadding(dp(16),dp(5),dp(16),dp(4));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff2c406a,0xff202744,0xff151627}));
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);
        Button back=button("返回");back.setContentDescription("返回播放器");header.addView(back,new LinearLayout.LayoutParams(dp(58),dp(40)));back.setOnClickListener(v->dismiss());
        LinearLayout info=new LinearLayout(activity);info.setOrientation(1);info.setPadding(dp(14),0,0,0);title=label("",15);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);badge=label("正在获取歌词",11);badge.setTextColor(0xffa8b7d8);info.addView(title);info.addView(badge);header.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        Button follow=button("跟随");header.addView(follow,new LinearLayout.LayoutParams(dp(58),dp(40)));follow.setOnClickListener(v->stage.follow());
        Button more=button("设置");header.addView(more,new LinearLayout.LayoutParams(dp(58),dp(40)));more.setOnClickListener(v->showSettings(more));root.addView(header,new LinearLayout.LayoutParams(-1,dp(44)));
        root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        progress=new SeekBar(activity);progress.setContentDescription("歌词播放进度");progress.setMax(10000);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xffd7def2));progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x446c7593));progress.setThumbTintList(android.content.res.ColorStateList.valueOf(0xffe9edfb));root.addView(progress,new LinearLayout.LayoutParams(-1,dp(22)));
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int value,boolean user){if(user){host.seek(host.duration()*value/10000);stage.follow();stage.invalidate();}}public void onStartTrackingTouch(SeekBar bar){touching=true;}public void onStopTrackingTouch(SeekBar bar){touching=false;}});
        LinearLayout controls=new LinearLayout(activity);controls.setGravity(Gravity.CENTER_VERTICAL);time=label("00:00 / 00:00",12);controls.addView(time,new LinearLayout.LayoutParams(0,dp(40),1));
        Button previous=button("上一首");controls.addView(previous,new LinearLayout.LayoutParams(dp(76),dp(40)));previous.setOnClickListener(v->host.previous());play=button("播放");controls.addView(play,new LinearLayout.LayoutParams(dp(72),dp(40)));play.setOnClickListener(v->{host.toggle();stage.invalidate();});Button next=button("下一首");controls.addView(next,new LinearLayout.LayoutParams(dp(76),dp(40)));next.setOnClickListener(v->host.next());root.addView(controls,new LinearLayout.LayoutParams(-1,dp(40)));
        setContentView(root);setOnShowListener(d->{getWindow().setLayout(-1,-1);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);applyEffect(activity.getSharedPreferences("library",0).getInt("lyrics_effect",0));handler.post(tick);});
    }
    private int dp(float value){return (int)(value*getContext().getResources().getDisplayMetrics().density+.5f);}
    private TextView label(String text,int size){TextView view=new TextView(getContext());view.setText(text);view.setTextSize(size);view.setTextColor(Color.WHITE);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    private Button button(String text){Button button=new Button(getContext());button.setText(text);button.setTextSize(13);button.setTextColor(0xffe5eafa);button.setAllCaps(false);button.setPadding(0,0,0,0);button.setMinHeight(0);button.setMinimumHeight(0);button.setMinWidth(0);button.setMinimumWidth(0);button.setBackgroundColor(Color.TRANSPARENT);return button;}
    void setTrack(String uri,String name){trackUri=uri;title.setText(name);setTimeline(LyricsTimeline.empty());setMessage("正在获取歌词");}
    void setTimeline(LyricsTimeline value){stage.setTimeline(value);badge.setText(value.lines.isEmpty()?"暂无歌词":value.wordTimed?(value.estimatedWordTiming?"逐字歌词 · 部分时间按行推算":"逐字歌词 · 点击歌词可跳转"):value.timed?"逐行歌词 · 此来源未提供逐字时间":"文本歌词 · 此来源未提供时间轴");}
    void setMessage(String message){stage.setMessage(message);}
    private void applyEffect(int effect){stage.setEffect(effect);}
    private void showSettings(View anchor){
        PopupMenu menu=new PopupMenu(getContext(),anchor);String[] labels={"发光律动","柔和高亮","简洁高亮","重新获取歌词","导入歌词文件","回到正在演唱的歌词"};
        for(int i=0;i<labels.length;i++)menu.getMenu().add(0,i,i,labels[i]);menu.setOnMenuItemClickListener(item->{int id=item.getItemId();if(id<3){getContext().getSharedPreferences("library",0).edit().putInt("lyrics_effect",id).apply();applyEffect(id);}else if(id==3)host.refresh();else if(id==4)host.importLyrics();else stage.follow();return true;});menu.show();
    }
    private static String format(long time){return String.format(java.util.Locale.CHINA,"%02d:%02d",Math.max(0,time)/60000,Math.max(0,time)/1000%60);}
    @Override public void dismiss(){handler.removeCallbacksAndMessages(null);super.dismiss();}
    @Override protected void onStop(){handler.removeCallbacksAndMessages(null);super.onStop();}
}
