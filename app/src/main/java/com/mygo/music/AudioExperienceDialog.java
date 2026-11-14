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
    private PlaybackTone.Settings settings;private boolean updating,pending;
    private final Runnable pendingApply=()->{pending=false;apply();};
    final Switch enabled;final Spinner presets;final SeekBar[] bands=new SeekBar[5];
    private final TextView[] values=new TextView[5];private final TextView status;
    final AlertDialog dialog;
    AudioExperienceDialog(Activity activity,Host host){
        this.activity=activity;this.host=host;prefs=activity.getSharedPreferences("library",0);settings=PlaybackTone.Settings.read(prefs);
        boolean wide=activity.getResources().getConfiguration().screenWidthDp>=560;
        LinearLayout box=new LinearLayout(activity);box.setOrientation(wide?0:1);box.setPadding(dp(14),dp(4),dp(14),dp(4));
        LinearLayout options=new LinearLayout(activity);options.setOrientation(1);options.setPadding(0,0,wide?dp(14):0,0);box.addView(options,wide?new LinearLayout.LayoutParams(dp(198),-2):new LinearLayout.LayoutParams(-1,-2));
        TextView title=label("音效与均衡器",17);options.addView(title,new LinearLayout.LayoutParams(-1,dp(26)));
        status=label("",12);status.setMaxLines(3);options.addView(status);TextView hint=label("关闭可对比原声，曲线会保留。提升频段时预留音量余量；音源品质仍由来源决定。",11);hint.setPadding(0,dp(5),0,dp(5));options.addView(hint);
        enabled=new Switch(activity);enabled.setText("启用均衡器");enabled.setTextSize(13);enabled.setTextColor(Color.WHITE);enabled.setChecked(settings.enabled);options.addView(enabled,new LinearLayout.LayoutParams(-1,dp(40)));
        presets=new Spinner(activity);presets.setContentDescription("均衡器预设");presets.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,PlaybackTone.PRESETS));presets.setSelection(settings.preset());options.addView(presets,new LinearLayout.LayoutParams(-1,dp(40)));
        LinearLayout curve=new LinearLayout(activity);curve.setOrientation(1);box.addView(curve,wide?new LinearLayout.LayoutParams(0,-2,1):new LinearLayout.LayoutParams(-1,-2));
        for(int i=0;i<5;i++){
            final int band=i;LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);TextView frequency=label(frequency(i),12);row.addView(frequency,new LinearLayout.LayoutParams(dp(57),-1));
            SeekBar slider=new SeekBar(activity);slider.setMax(1200);slider.setProgress(settings.levels[i]+600);slider.setContentDescription("均衡器 "+frequency(i));bands[i]=slider;row.addView(slider,new LinearLayout.LayoutParams(0,-1,1));values[i]=label("",12);values[i].setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);row.addView(values[i],new LinearLayout.LayoutParams(dp(57),-1));curve.addView(row,new LinearLayout.LayoutParams(-1,dp(wide?36:48)));
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar view,int value,boolean user){if(user&&!updating){settings.levels[band]=value-600;caption(band);updating=true;presets.setSelection(settings.preset());updating=false;schedule();}}public void onStartTrackingTouch(SeekBar view){}public void onStopTrackingTouch(SeekBar view){flush();}});
        }
        enabled.setOnCheckedChangeListener((view,on)->{if(!updating){settings.enabled=on;commit();}});
        presets.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,View view,int choice,long id){if(updating||choice!=presets.getSelectedItemPosition()||choice>=PlaybackTone.CURVES.length||settings.preset()==choice)return;settings.levels=PlaybackTone.CURVES[choice].clone();commit();sync();}public void onNothingSelected(AdapterView<?> parent){}});
        ScrollView scroll=new ScrollView(activity);scroll.addView(box);dialog=new AlertDialog.Builder(activity).setView(scroll).setPositiveButton("完成",null).setNeutralButton("恢复原声",(d,w)->{settings=new PlaybackTone.Settings();commit();}).create();dialog.setOnDismissListener(d->{flush();handler.removeCallbacksAndMessages(null);});sync();
    }
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private TextView label(String text,int size){TextView view=new TextView(activity);view.setText(text);view.setTextSize(size);view.setGravity(Gravity.CENTER_VERTICAL);view.setTextColor(0xffd9dfed);return view;}
    private String frequency(int i){return PlaybackTone.FREQUENCIES[i]>=1000?String.format(Locale.CHINA,"%.1f kHz",PlaybackTone.FREQUENCIES[i]/1000f):PlaybackTone.FREQUENCIES[i]+" Hz";}
    private void schedule(){if(!pending){pending=true;handler.postDelayed(pendingApply,35);}}
    private void flush(){if(pending){pending=false;handler.removeCallbacks(pendingApply);apply();}}
    private void commit(){pending=false;handler.removeCallbacks(pendingApply);apply();}
    private void apply(){settings.save(prefs);PlaybackService service=host.service();if(service!=null)service.refreshVolume();updateStatus();}
    private void caption(int i){values[i].setText(String.format(Locale.CHINA,"%+.1f dB",settings.levels[i]/100f));}
    private void sync(){updating=true;enabled.setChecked(settings.enabled);presets.setSelection(settings.preset());for(int i=0;i<5;i++){bands[i].setProgress(settings.levels[i]+600);caption(i);}updating=false;}
    private void updateStatus(){PlaybackService service=host.service();String state=service==null?"设置已保存，播放本机音频时生效":service.snapshot().toneInfo;if(!state.contentEquals(status.getText()))status.setText(state);}
    void show(){dialog.show();if(activity.getResources().getConfiguration().screenWidthDp>=560)dialog.getWindow().setLayout(-1,-2);updateStatus();handler.postDelayed(new Runnable(){public void run(){if(!dialog.isShowing())return;updateStatus();handler.postDelayed(this,500);}},500);}
}
