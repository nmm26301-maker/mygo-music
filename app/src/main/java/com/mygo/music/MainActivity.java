package com.mygo.music;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.text.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final int PICK_AUDIO = 1001;
    private final ArrayList<Uri> tracks = new ArrayList<>();
    private final ArrayList<String> names = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private MediaPlayer player;
    private TextView nowPlaying, timeLabel;
    private SeekBar progress, volume;
    private Button playButton, shuffleButton, repeatButton;
    private int current = -1;
    private boolean shuffle = false, repeat = false;
    private final Handler handler = new Handler();
    private final Random random = new Random();
    private SharedPreferences prefs;

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(String s, float size, int color) { TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); t.setGravity(Gravity.CENTER_VERTICAL); return t; }
    private GradientDrawable bg(int color, float radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private Button control(String label) { Button b = new Button(this); b.setText(label); b.setTextSize(16); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setBackground(bg(Color.rgb(48,38,57), 24)); return b; }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(14,11,18));
        prefs = getSharedPreferences("library", MODE_PRIVATE);
        loadLibrary();
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20),dp(14),dp(20),dp(12)); root.setBackgroundColor(Color.rgb(14,11,18));
        TextView title = text("MyGo Music",28,Color.WHITE); title.setTypeface(null,1); root.addView(title,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView subtitle = text("你的音乐，安静地播放",14,Color.rgb(180,170,190)); root.addView(subtitle,new LinearLayout.LayoutParams(-1,dp(28)));
        LinearLayout action = new LinearLayout(this); action.setGravity(Gravity.CENTER_VERTICAL); action.setPadding(dp(14),0,dp(8),0); action.setBackground(bg(Color.rgb(31,25,39),18));
        EditText search = new EditText(this); search.setSingleLine(true); search.setTextSize(15); search.setTextColor(Color.rgb(235,225,240)); search.setHintTextColor(Color.rgb(150,140,160)); search.setHint("搜索本地音乐"); search.setPadding(0,0,0,0); action.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));
        Button add = new Button(this); add.setText("＋ 添加"); add.setTextColor(Color.rgb(225,196,255)); add.setTextSize(14); add.setAllCaps(false); add.setBackgroundColor(Color.TRANSPARENT); action.addView(add,new LinearLayout.LayoutParams(dp(95),dp(48))); add.setOnClickListener(v->pickAudio()); root.addView(action,new LinearLayout.LayoutParams(-1,dp(52)));
        TextView heading = text("本地歌曲  ·  "+names.size(),18,Color.WHITE); heading.setTypeface(null,1); heading.setPadding(0,dp(18),0,dp(8)); root.addView(heading,new LinearLayout.LayoutParams(-1,dp(52)));
        ListView list = new ListView(this); list.setDivider(null); list.setPadding(0,0,0,dp(6)); list.setBackgroundColor(Color.TRANSPARENT);
        adapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,names){ @Override public View getView(int pos,View convert,ViewGroup parent){ TextView v=(TextView)super.getView(pos,convert,parent); v.setTextColor(Color.rgb(236,230,240)); v.setTextSize(16); v.setGravity(Gravity.CENTER_VERTICAL); v.setPadding(dp(16),0,dp(10),0); v.setBackground(bg(Color.rgb(28,23,35),16)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(54)); lp.setMargins(0,dp(4),0,dp(4)); v.setLayoutParams(lp); return v; }};
        list.setAdapter(adapter); search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){adapter.getFilter().filter(s);} public void afterTextChanged(Editable e){}}); root.addView(list,new LinearLayout.LayoutParams(-1,0,1)); list.setOnItemClickListener((p,v,pos,id)->{String n=adapter.getItem(pos); playIndex(names.indexOf(n));}); list.setOnItemLongClickListener((p,v,pos,id)->{String n=adapter.getItem(pos); removeTrack(names.indexOf(n)); return true;});
        LinearLayout now = new LinearLayout(this); now.setOrientation(LinearLayout.VERTICAL); now.setPadding(dp(16),dp(12),dp(16),dp(8)); now.setBackground(bg(Color.rgb(33,26,41),22));
        nowPlaying=text("尚未选择歌曲",16,Color.WHITE); now.addView(nowPlaying,new LinearLayout.LayoutParams(-1,dp(32))); timeLabel=text("00:00 / 00:00",12,Color.rgb(180,170,190)); now.addView(timeLabel,new LinearLayout.LayoutParams(-1,dp(22)));
        progress=new SeekBar(this); progress.setMax(1000); now.addView(progress,new LinearLayout.LayoutParams(-1,dp(28))); progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean from){if(from&&player!=null&&player.getDuration()>0)player.seekTo(player.getDuration()*p/1000);} public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}});
        LinearLayout controls=new LinearLayout(this); controls.setGravity(Gravity.CENTER_VERTICAL); Button prev=control("|<"); controls.addView(prev,new LinearLayout.LayoutParams(dp(48),dp(48))); prev.setOnClickListener(v->previous()); playButton=control("播放"); playButton.setBackground(bg(Color.rgb(147,88,190),30)); controls.addView(playButton,new LinearLayout.LayoutParams(dp(72),dp(48))); playButton.setOnClickListener(v->toggle()); Button next=control(">|"); controls.addView(next,new LinearLayout.LayoutParams(dp(48),dp(48))); next.setOnClickListener(v->next()); shuffleButton=control("随机"); controls.addView(shuffleButton,new LinearLayout.LayoutParams(dp(58),dp(48))); shuffleButton.setOnClickListener(v->{shuffle=!shuffle;updateModes();}); repeatButton=control("循环"); controls.addView(repeatButton,new LinearLayout.LayoutParams(dp(58),dp(48))); repeatButton.setOnClickListener(v->{repeat=!repeat;updateModes();}); now.addView(controls,new LinearLayout.LayoutParams(-1,dp(56)));
        volume=new SeekBar(this); volume.setMax(100); volume.setProgress(80); volume.setContentDescription("音量"); now.addView(volume,new LinearLayout.LayoutParams(-1,dp(28))); volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){if(player!=null)player.setVolume(p/100f,p/100f);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}}); root.addView(now,new LinearLayout.LayoutParams(-1,dp(210)));
        setContentView(root); updateModes(); updateProgress();
    }
    private void updateModes(){ if(shuffleButton!=null)shuffleButton.setText(shuffle?"随机开":"随机"); if(repeatButton!=null)repeatButton.setText(repeat?"循环开":"循环"); }
    private void pickAudio(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("audio/*"); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); i.addCategory(Intent.CATEGORY_OPENABLE); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION); startActivityForResult(i,PICK_AUDIO); }
    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r!=PICK_AUDIO||c!=RESULT_OK||d==null)return;try{if(d.getClipData()!=null){for(int i=0;i<d.getClipData().getItemCount();i++){Uri u=d.getClipData().getItemAt(i).getUri();persist(u);add(u);}}else if(d.getData()!=null){persist(d.getData());add(d.getData());}}catch(Exception ignored){}adapter.notifyDataSetChanged();}
    private void persist(Uri u){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}
    private void loadLibrary(){StringJoiner us=new StringJoiner("\n");String raw=prefs.getString("uris","");String ns=prefs.getString("names","");if(raw.isEmpty())return;String[] a=raw.split("\\n",-1),b=ns.split("\\n",-1);for(int i=0;i<a.length;i++)try{tracks.add(Uri.parse(a[i]));names.add(i<b.length?b[i]:a[i]);}catch(Exception ignored){}}
    private void saveLibrary(){StringJoiner u=new StringJoiner("\n"),n=new StringJoiner("\n");for(Uri x:tracks)u.add(x.toString());for(String x:names)n.add(x);prefs.edit().putString("uris",u.toString()).putString("names",n.toString()).apply();}
    private void removeTrack(int i){if(i<0||i>=tracks.size())return; if(current==i){if(player!=null){player.stop();player.release();player=null;}current=-1;nowPlaying.setText("尚未选择歌曲");playButton.setText("播放");} tracks.remove(i); names.remove(i); saveLibrary(); adapter.notifyDataSetChanged(); Toast.makeText(this,"已移除歌曲",Toast.LENGTH_SHORT).show();}
    private void add(Uri u){if(tracks.contains(u))return;tracks.add(u);String n=null;try{android.database.Cursor c=getContentResolver().query(u,null,null,null,null);if(c!=null&&c.moveToFirst()){int x=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(x>=0)n=c.getString(x);c.close();}}catch(Exception ignored){}names.add(n==null?u.getLastPathSegment():n);saveLibrary();}
    private void playIndex(int i){if(i<0||i>=tracks.size())return;try{if(player!=null)player.release();player=new MediaPlayer();player.setAudioStreamType(AudioManager.STREAM_MUSIC);player.setDataSource(this,tracks.get(i));player.setOnPreparedListener(mp->{mp.setVolume(volume.getProgress()/100f,volume.getProgress()/100f);mp.start();current=i;nowPlaying.setText(names.get(i));playButton.setText("暂停");});player.setOnCompletionListener(mp->{if(repeat)playIndex(current);else next();});player.prepareAsync();}catch(Exception e){Toast.makeText(this,"无法播放此文件",Toast.LENGTH_SHORT).show();}}
    private void toggle(){if(player==null){if(!tracks.isEmpty())playIndex(0);return;}if(player.isPlaying()){player.pause();playButton.setText("播放");}else{player.start();playButton.setText("暂停");}}
    private void next(){if(tracks.isEmpty())return;int i=shuffle?random.nextInt(tracks.size()):(current+1)%tracks.size();playIndex(i);}
    private void previous(){if(tracks.isEmpty())return;int i=current<=0?tracks.size()-1:current-1;playIndex(i);}
    private void updateProgress(){handler.postDelayed(()->{if(player!=null&&player.getDuration()>0){progress.setProgress(player.getCurrentPosition()*1000/player.getDuration());timeLabel.setText(fmt(player.getCurrentPosition())+" / "+fmt(player.getDuration()));}updateProgress();},500);}
    private String fmt(int ms){return String.format(Locale.getDefault(),"%02d:%02d",ms/60000,(ms/1000)%60);}
    @Override protected void onDestroy(){if(player!=null)player.release();super.onDestroy();}
}
