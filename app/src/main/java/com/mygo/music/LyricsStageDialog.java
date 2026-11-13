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
        long duration();void toggle();void previous();void next();void refresh();void importLyrics();default void quality(){}
    }
    private final Host host;
    final LyricsTiming timing;
    private final Handler handler=new Handler(Looper.getMainLooper());
    final LyricsStageView stage;
    final SeekBar progress;
    private final TextView title,badge,time;
    private final Button play;
    private boolean touching;
    private LyricsAppearance appearance;
    private AlertDialog appearanceDialog;
    private AlertDialog timingDialog;
    String trackUri="";
    private final Runnable tick=new Runnable(){public void run(){if(!isShowing())return;long duration=host.duration(),position=host.position();if(!touching)progress.setProgress(duration>0?(int)Math.min(10000,position*10000/duration):0);time.setText(format(position)+" / "+format(duration));play.setText(host.playing()?"暂停":"播放");if(host.playing())getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);stage.invalidate();handler.postDelayed(this,100);}};
    LyricsStageDialog(Activity activity,Host host){
        super(activity,android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);this.host=host;appearance=LyricsAppearance.read(activity.getSharedPreferences("library",0));try{appearance.systemReduceMotion=android.provider.Settings.Global.getFloat(activity.getContentResolver(),android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0;}catch(Exception ignored){}timing=new LyricsTiming(host,activity.getSharedPreferences("library",0));stage=new LyricsStageView(activity,timing);stage.setBackdrop(false);
        LinearLayout root=new LinearLayout(activity);root.setOrientation(1);root.setPadding(dp(16),dp(5),dp(16),dp(4));
        root.setBackground(new android.graphics.drawable.Drawable(){public void draw(Canvas canvas){stage.drawBackground(canvas,getBounds().width(),getBounds().height());}public void setAlpha(int value){}public void setColorFilter(ColorFilter filter){}public int getOpacity(){return PixelFormat.OPAQUE;}});
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);
        Button back=button("返回");back.setContentDescription("返回播放器");header.addView(back,new LinearLayout.LayoutParams(dp(58),dp(40)));back.setOnClickListener(v->dismiss());
        LinearLayout info=new LinearLayout(activity);info.setOrientation(1);info.setPadding(dp(14),0,0,0);title=label("",15);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);badge=label("正在获取歌词",11);badge.setTextColor(0xffa8b7d8);info.addView(title);info.addView(badge);header.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        Button quality=button("音质");quality.setContentDescription("歌词页播放音质");header.addView(quality,new LinearLayout.LayoutParams(dp(50),dp(40)));quality.setOnClickListener(v->host.quality());
        Button follow=button("跟随");header.addView(follow,new LinearLayout.LayoutParams(dp(58),dp(40)));follow.setOnClickListener(v->stage.follow());
        Button more=button("动效");more.setContentDescription("歌词动效与设置");header.addView(more,new LinearLayout.LayoutParams(dp(58),dp(40)));more.setOnClickListener(v->showSettings(more));root.addView(header,new LinearLayout.LayoutParams(-1,dp(44)));
        root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        progress=new SeekBar(activity);progress.setContentDescription("歌词播放进度");progress.setMax(10000);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xffd7def2));progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x446c7593));progress.setThumbTintList(android.content.res.ColorStateList.valueOf(0xffe9edfb));root.addView(progress,new LinearLayout.LayoutParams(-1,dp(22)));
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int value,boolean user){if(user){host.seek(host.duration()*value/10000);stage.follow();stage.invalidate();}}public void onStartTrackingTouch(SeekBar bar){touching=true;}public void onStopTrackingTouch(SeekBar bar){touching=false;}});
        LinearLayout controls=new LinearLayout(activity);controls.setGravity(Gravity.CENTER_VERTICAL);time=label("00:00 / 00:00",12);controls.addView(time,new LinearLayout.LayoutParams(0,dp(40),1));
        Button previous=button("上一首");controls.addView(previous,new LinearLayout.LayoutParams(dp(76),dp(40)));previous.setOnClickListener(v->host.previous());play=button("播放");controls.addView(play,new LinearLayout.LayoutParams(dp(72),dp(40)));play.setOnClickListener(v->{host.toggle();stage.invalidate();});Button next=button("下一首");controls.addView(next,new LinearLayout.LayoutParams(dp(76),dp(40)));next.setOnClickListener(v->host.next());root.addView(controls,new LinearLayout.LayoutParams(-1,dp(40)));
        setContentView(root);setOnShowListener(d->{getWindow().setLayout(-1,-1);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);stage.setAppearance(appearance);handler.post(tick);});
    }
    private int dp(float value){return (int)(value*getContext().getResources().getDisplayMetrics().density+.5f);}
    private TextView label(String text,int size){TextView view=new TextView(getContext());view.setText(text);view.setTextSize(size);view.setTextColor(Color.WHITE);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    private Button button(String text){Button button=new Button(getContext());button.setText(text);button.setTextSize(13);button.setTextColor(0xffe5eafa);button.setAllCaps(false);button.setPadding(0,0,0,0);button.setMinHeight(0);button.setMinimumHeight(0);button.setMinWidth(0);button.setMinimumWidth(0);button.setBackgroundColor(Color.TRANSPARENT);return button;}
    void setTrack(String uri,String name){if(!uri.equals(trackUri)&&timingDialog!=null)timingDialog.dismiss();if(!uri.equals(trackUri))stage.setArtwork(null);trackUri=uri;timing.select(uri);title.setText(name);setTimeline(LyricsTimeline.empty());setMessage("正在获取歌词");}
    void setTimeline(LyricsTimeline value){stage.setTimeline(value);badge.setText(value.lines.isEmpty()?"暂无歌词":value.wordTimed?(value.estimatedWordTiming?"逐字歌词 · 部分时间按行推算":"逐字歌词 · 点击歌词可跳转"):value.timed?"逐行歌词 · 此来源未提供逐字时间":"文本歌词 · 此来源未提供时间轴");}
    void setMessage(String message){stage.setMessage(message);}
    void setArtwork(Bitmap bitmap){stage.setArtwork(bitmap);}
    void applyAppearance(LyricsAppearance value){appearance=value.copy();appearance.save(getContext().getSharedPreferences("library",0));stage.setAppearance(appearance);}
    private void showSettings(View anchor){
        PopupMenu menu=new PopupMenu(getContext(),anchor);
        java.util.ArrayList<Integer> order=new java.util.ArrayList<>();for(int recent:new int[]{13,10,11,12})if(recent<LyricsAppearance.STYLES.length)order.add(recent);for(int i=0;i<LyricsAppearance.STYLES.length;i++)if(!order.contains(i))order.add(i);
        for(int rank=0;rank<order.size();rank++){int i=order.get(rank);menu.getMenu().add(0,i,rank,LyricsAppearance.STYLES[i]).setCheckable(true).setChecked(appearance.style==i);}
        String[] extra={"歌词外观与动效","重新获取歌词","导入歌词文件","回到正在演唱的歌词","调整歌词同步"};for(int i=0;i<extra.length;i++)menu.getMenu().add(1,i,100+i,extra[i]);
        menu.setOnMenuItemClickListener(item->{int id=item.getItemId();if(item.getGroupId()==0){LyricsAppearance next=appearance.copy();next.style=id;applyAppearance(next);}else if(id==0)showAppearance();else if(id==1)host.refresh();else if(id==2)host.importLyrics();else if(id==3)stage.follow();else showTiming();return true;});menu.show();
    }
    private interface NumberChange {void set(int value);}
    private void slider(LinearLayout box,String name,int value,int low,int high,NumberChange change){
        TextView caption=label(name+"  "+value+"%",14);caption.setPadding(0,dp(10),0,0);box.addView(caption);SeekBar bar=new SeekBar(getContext());bar.setContentDescription(name);bar.setMax(high-low);bar.setProgress(value-low);box.addView(bar,new LinearLayout.LayoutParams(-1,dp(36)));bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar view,int progress,boolean user){if(user){int next=low+progress;caption.setText(name+"  "+next+"%");change.set(next);applyAppearance(appearance);}}public void onStartTrackingTouch(SeekBar view){}public void onStopTrackingTouch(SeekBar view){}});
    }
    private void choices(LinearLayout box,String title,String[] values,int selected,NumberChange change){
        TextView label=label(title,14);label.setPadding(0,dp(10),0,0);box.addView(label);Spinner spinner=new Spinner(getContext());spinner.setContentDescription(title);spinner.setAdapter(new ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,values));spinner.setSelection(selected);box.addView(spinner,new LinearLayout.LayoutParams(-1,dp(42)));spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,View view,int index,long id){change.set(index);applyAppearance(appearance);}public void onNothingSelected(AdapterView<?> parent){}});
    }
    private interface ToggleChange {void set(boolean value);}
    private void option(LinearLayout box,String text,boolean checked,ToggleChange change){Switch control=new Switch(getContext());control.setText(text);control.setTextColor(Color.WHITE);control.setChecked(checked);control.setPadding(0,dp(8),0,dp(8));box.addView(control);control.setOnCheckedChangeListener((button,value)->{change.set(value);applyAppearance(appearance);});}
    private void showAppearance(){
        LinearLayout box=new LinearLayout(getContext());box.setOrientation(1);box.setPadding(dp(20),dp(8),dp(20),dp(12));TextView hint=label("修改即时生效并保存。逐字时间以音乐源为准；译文使用歌曲已有内容。",13);box.addView(hint);
        choices(box,"显示样式",LyricsAppearance.STYLES,appearance.style,value->appearance.style=value);
        choices(box,"背景",LyricsAppearance.BACKGROUNDS,appearance.background,value->appearance.background=value);
        choices(box,"高亮配色",LyricsAppearance.COLORS,appearance.color,value->appearance.color=value);
        slider(box,"歌词字号",appearance.size,80,135,value->appearance.size=value);slider(box,"行间距",appearance.spacing,75,150,value->appearance.spacing=value);slider(box,"动效强度",appearance.strength,0,140,value->appearance.strength=value);
        option(box,"歌词居中排列",appearance.centered,value->appearance.centered=value);option(box,"显示已有译文",appearance.translation,value->appearance.translation=value);option(box,"减少动态效果",appearance.reduceMotion,value->appearance.reduceMotion=value);
        TextView note=label("流星拖尾跟随演唱位置，翻页入场带轻微透视，涟漪扩散环绕当前字，双行交接显示当前和下一行。长音波浪按来源字词时长起伏，不分析鼓点。双色卡拉OK和大字专注使用居中布局。减少动态效果保留逐字进度，关闭弹动和背景流动，也会跟随系统动画设置。",12);box.addView(note);
        ScrollView scroll=new ScrollView(getContext());scroll.addView(box);AlertDialog dialog=new AlertDialog.Builder(getContext()).setTitle("歌词外观与动效").setView(scroll).setPositiveButton("完成",null).setNeutralButton("恢复默认",(ignored,which)->{LyricsAppearance defaults=new LyricsAppearance();defaults.systemReduceMotion=appearance.systemReduceMotion;applyAppearance(defaults);}).create();appearanceDialog=dialog;dialog.show();
    }
    private String timingLabel(){int value=timing.offset();return value==0?"当前未调整":"歌词"+(value<0?"提前 ":"延后 ")+String.format(java.util.Locale.CHINA,"%.1f 秒",Math.abs(value)/1000.0);}
    private void showTiming(){
        final String selected=trackUri;
        LinearLayout box=new LinearLayout(getContext());box.setOrientation(1);box.setPadding(dp(20),dp(8),dp(20),dp(8));
        TextView value=label(timingLabel(),17);box.addView(value);
        TextView hint=label("每次调整 0.2 秒，最多前后 10 秒。自动记住这首歌的设置。",13);hint.setPadding(0,dp(8),0,dp(8));box.addView(hint);
        LinearLayout buttons=new LinearLayout(getContext());
        Button earlier=button("提前 0.2 秒"),later=button("延后 0.2 秒"),reset=button("恢复同步");
        for(Button button:new Button[]{earlier,later,reset})buttons.addView(button,new LinearLayout.LayoutParams(0,dp(48),1));box.addView(buttons);
        AlertDialog dialog=new AlertDialog.Builder(getContext()).setTitle("调整歌词同步").setView(box).setPositiveButton("完成",null).create();timingDialog=dialog;
        View.OnClickListener adjust=view->{if(!selected.equals(trackUri)){dialog.dismiss();return;}if(view==reset)timing.set(0);else timing.adjust(view==earlier?-200:200);stage.follow();value.setText(timingLabel());};
        earlier.setOnClickListener(adjust);later.setOnClickListener(adjust);reset.setOnClickListener(adjust);dialog.show();
    }
    private static String format(long time){return String.format(java.util.Locale.CHINA,"%02d:%02d",Math.max(0,time)/60000,Math.max(0,time)/1000%60);}
    @Override public void dismiss(){handler.removeCallbacksAndMessages(null);if(timingDialog!=null)timingDialog.dismiss();if(appearanceDialog!=null)appearanceDialog.dismiss();stage.release();super.dismiss();}
    @Override protected void onStop(){handler.removeCallbacksAndMessages(null);super.onStop();}
}
