package com.mygo.music;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Saved tone settings apply to the existing player without a seek, reload or queue change. */
final class AudioExperienceDialog {
    interface Host {PlaybackService service();}
    private final Activity activity;private final Host host;private final SharedPreferences prefs;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private PlaybackTone.Settings settings;private boolean updating;
    final Switch enabled;final Spinner presets;final SeekBar[] bands=new SeekBar[5];
    private final TextView[] values=new TextView[5];private final TextView status;
    final AlertDialog dialog;
    AudioExperienceDialog(Activity activity,Host host){
        this.activity=activity;this.host=host;prefs=activity.getSharedPreferences("library",0);settings=PlaybackTone.Settings.read(prefs);
        LinearLayout box=new LinearLayout(activity);box.setOrientation(1);box.setPadding(dp(20),dp(8),dp(20),dp(8));
        status=label("",12);box.addView(status);TextView hint=label("均衡器调整听感。无损优先在播放音质中选择，实际清晰度由音源决定。提升频段时会预留音量余量，可随时关闭对比原声。",12);hint.setPadding(0,dp(8),0,dp(8));box.addView(hint);
        enabled=new Switch(activity);enabled.setText("启用均衡器");enabled.setTextColor(Color.WHITE);enabled.setChecked(settings.enabled);box.addView(enabled,new LinearLayout.LayoutParams(-1,dp(44)));
        presets=new Spinner(activity);presets.setContentDescription("均衡器预设");presets.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,PlaybackTone.PRESETS));presets.setSelection(settings.preset());box.addView(presets,new LinearLayout.LayoutParams(-1,dp(44)));
        for(int i=0;i<5;i++){final int band=i;values[i]=label("",13);box.addView(values[i]);SeekBar slider=new SeekBar(activity);slider.setMax(1200);slider.setProgress(settings.levels[i]+600);slider.setContentDescription("均衡器 "+frequency(i));bands[i]=slider;box.addView(slider,new LinearLayout.LayoutParams(-1,dp(38)));slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar view,int value,boolean user){if(user&&!updating){settings.levels[band]=value-600;commit();sync();}}public void onStartTrackingTouch(SeekBar view){}public void onStopTrackingTouch(SeekBar view){}});}
        enabled.setOnCheckedChangeListener((view,on)->{if(!updating){settings.enabled=on;commit();}});
        presets.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,View view,int choice,long id){if(updating||choice>=PlaybackTone.CURVES.length||settings.preset()==choice)return;settings.levels=PlaybackTone.CURVES[choice].clone();commit();sync();}public void onNothingSelected(AdapterView<?> parent){}});
        ScrollView scroll=new ScrollView(activity);scroll.addView(box);dialog=new AlertDialog.Builder(activity).setTitle("音效与均衡器").setView(scroll).setPositiveButton("完成",null).setNeutralButton("恢复原声",(d,w)->{settings=new PlaybackTone.Settings();commit();}).create();dialog.setOnDismissListener(d->handler.removeCallbacksAndMessages(null));sync();
    }
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private TextView label(String text,int size){TextView view=new TextView(activity);view.setText(text);view.setTextSize(size);view.setTextColor(0xffd9dfed);return view;}
    private String frequency(int i){return PlaybackTone.FREQUENCIES[i]>=1000?String.format(Locale.CHINA,"%.1f kHz",PlaybackTone.FREQUENCIES[i]/1000f):PlaybackTone.FREQUENCIES[i]+" Hz";}
    private void commit(){settings.save(prefs);PlaybackService service=host.service();if(service!=null)service.refreshVolume();updateStatus();}
    private void sync(){updating=true;enabled.setChecked(settings.enabled);presets.setSelection(settings.preset());for(int i=0;i<5;i++){bands[i].setProgress(settings.levels[i]+600);values[i].setText(frequency(i)+"     "+String.format(Locale.CHINA,"%+.1f dB",settings.levels[i]/100f));}updating=false;}
    private void updateStatus(){PlaybackService service=host.service();String state=service==null?"设置已保存，播放本机音频时生效":service.snapshot().toneInfo;if(!state.contentEquals(status.getText()))status.setText(state);}
    void show(){dialog.show();updateStatus();handler.postDelayed(new Runnable(){public void run(){if(!dialog.isShowing())return;updateStatus();handler.postDelayed(this,500);}},500);}
}
