package com.mygo.music;
import android.app.AlertDialog;
import android.content.*;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Native drag/drop and keyboard/context alternatives all edit the existing service queue. */
final class ThemeQueueView extends LinearLayout {
 private final PlayerScreenHost host;
 private final Runnable selected;
 private final LinearLayout heading;
 private final ListView list;
 private final BaseAdapter adapter;
 private final TextView count;
 private final Button locate;
 private JSONArray data=new JSONArray();
 private long revision=Long.MIN_VALUE;
 private int index=-2;
 private boolean locateOnOpen;

 private static final class Binding {
  final int position;final long revision;final String item;
  Binding(int position,long revision,JSONObject item){this.position=position;this.revision=revision;this.item=item==null?"":item.toString();}
 }
 private static final class Drag {final ThemeQueueView owner;final int from;final long revision;final String fingerprint;Drag(ThemeQueueView owner,int from,long revision,String fingerprint){this.owner=owner;this.from=from;this.revision=revision;this.fingerprint=fingerprint;}}

 ThemeQueueView(Context context,PlayerScreenHost host,Runnable selected){
  super(context);
  this.host=host;
  this.selected=selected;
  setOrientation(VERTICAL);
  heading=new LinearLayout(context);
  heading.setGravity(Gravity.CENTER_VERTICAL);
  count=new TextView(context);
  count.setTextColor(0xffc6dae2);
  count.setTextSize(12);
  count.setMaxLines(2);
  count.setEllipsize(TextUtils.TruncateAt.END);
  count.setGravity(Gravity.CENTER_VERTICAL);
  heading.addView(count,new LayoutParams(0,dp(48),1));
  locate=headerButton("定位当前");
  locate.setContentDescription("定位到当前歌曲");
  locate.setOnClickListener(v->locateCurrent());
  Button add=headerButton("添加歌曲");
  add.setOnClickListener(v->host.queueAdd());
  addView(heading,new LayoutParams(-1,dp(48)));

  list=new ListView(context);
  list.setDivider(null);
  list.setBackgroundColor(Color.TRANSPARENT);
  adapter=new BaseAdapter(){
   @Override public int getCount(){return data.length();}
   @Override public Object getItem(int position){return data.optJSONObject(position);}
   @Override public long getItemId(int position){return position;}
   @Override public View getView(int position,View old,ViewGroup parent){return createRow(position);}
  };
  list.setAdapter(adapter);
  list.setOnItemClickListener((a,v,p,id)->{if(v.getTag() instanceof Binding)play((Binding)v.getTag());});
  list.setOnDragListener(this::drag);
  addView(list,new LayoutParams(-1,0,1));
  refresh(true);
 }

 private Button headerButton(String label){
  Button button=new Button(getContext());
  button.setText(label);
  button.setTextSize(12);
  button.setAllCaps(false);
  button.setMinimumWidth(0);
  button.setMinWidth(0);
  button.setPadding(0,0,0,0);
  heading.addView(button,new LayoutParams(dp(82),dp(48)));
  return button;
 }

 private View createRow(int position){
  Context context=getContext();
  JSONObject item=data.optJSONObject(position);
  Binding binding=new Binding(position,revision,item);
  LinearLayout row=new LinearLayout(context);
  row.setTag(binding);
  row.setGravity(Gravity.CENTER_VERTICAL);
  TextView handle=new TextView(context);
  handle.setText("≡");
  handle.setTextColor(0xffa2c0ca);
  handle.setTextSize(23);
  handle.setGravity(Gravity.CENTER);
  handle.setContentDescription("拖动第 "+(position+1)+" 首排序");
  row.addView(handle,new LayoutParams(dp(48),dp(48)));
  TextView name=new TextView(context);
  String title=item==null?"歌曲":item.optString("title","歌曲");
  name.setText(title);
  name.setTextSize(13);
  name.setSingleLine(true);
  name.setEllipsize(TextUtils.TruncateAt.END);
  if(position==index){
   row.setBackgroundColor(0x3065cabe);
   name.setTextColor(0xffa8eddf);
   name.setContentDescription("当前歌曲："+title);
  }else name.setTextColor(Color.WHITE);
  row.addView(name,new LayoutParams(0,dp(48),1));
  Button remove=new Button(context);
  remove.setText("×");
  remove.setTextColor(0xffc3d6dd);
  remove.setTextSize(21);
  remove.setContentDescription("移除第 "+(position+1)+" 首");
  remove.setPadding(0,0,0,0);
  remove.setBackgroundColor(Color.TRANSPARENT);
  row.addView(remove,new LayoutParams(dp(48),dp(48)));

  name.setOnClickListener(v->play(binding));
  remove.setOnClickListener(v->{if(valid(binding)){host.queueRemove(binding.position);refresh(true);}});
  handle.setOnLongClickListener(v->{
   if(valid(binding)){
    Drag value=new Drag(this,binding.position,binding.revision,host.queueItems().toString());
    ClipData clip=ClipData.newPlainText("mygo-queue","local");
    View.DragShadowBuilder shadow=new View.DragShadowBuilder(row);
    if(android.os.Build.VERSION.SDK_INT>=24)v.startDragAndDrop(clip,shadow,value,0);
    else v.startDrag(clip,shadow,value,0);
   }
   return true;
  });
  row.setOnLongClickListener(v->{menu(binding);return true;});
  return row;
 }

 private boolean drag(View view,DragEvent event){
  if(!(event.getLocalState() instanceof Drag))return false;
  Drag drag=(Drag)event.getLocalState();
  if(drag.owner!=this)return false;
  if(event.getAction()==DragEvent.ACTION_DRAG_LOCATION){
   if(event.getY()<dp(36))list.smoothScrollBy(-dp(48),80);
   else if(event.getY()>list.getHeight()-dp(36))list.smoothScrollBy(dp(48),80);
  }else if(event.getAction()==DragEvent.ACTION_DROP){
   int to=list.pointToPosition((int)event.getX(),(int)event.getY());
   if(to<0)to=data.length()-1;
   if(drag.revision==host.state().queueRevision&&drag.fingerprint.equals(host.queueItems().toString())&&to>=0){
    host.queueMove(drag.from,to);
    refresh(true);
   }else{
    refresh(true);
    Toast.makeText(getContext(),"队列已变化，请重新拖动",Toast.LENGTH_SHORT).show();
   }
  }
  return true;
 }
 private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
 private boolean valid(Binding binding){
  JSONArray now=host.queueItems();int at=binding.position;
  boolean current=binding.revision==host.state().queueRevision&&at>=0&&at<now.length()&&now.optJSONObject(at)!=null&&!binding.item.isEmpty()&&binding.item.equals(now.optJSONObject(at).toString());
  if(!current)changed();return current;
 }
 private void changed(){refresh(true);Toast.makeText(getContext(),"队列已更新，请重新选择",Toast.LENGTH_SHORT).show();}
 private void play(Binding binding){if(valid(binding)){host.queuePlay(binding.position);if(selected!=null)selected.run();}}
 private void menu(Binding binding){
  if(!valid(binding))return;int at=binding.position;String snapshot=host.queueItems().toString();
  ArrayList<String> names=new ArrayList<>();ArrayList<Integer> codes=new ArrayList<>();
  if(at>0){names.add("上移一首");codes.add(0);}if(at+1<data.length()){names.add("下移一首");codes.add(1);}names.add("从队列移除");codes.add(2);
  new AlertDialog.Builder(getContext()).setTitle("队列操作").setItems(names.toArray(new String[0]),(d,p)->{
   if(!valid(binding))return;if(!snapshot.equals(host.queueItems().toString())){changed();return;}
   if(codes.get(p)==2)host.queueRemove(at);else host.queueMove(at,codes.get(p)==0?at-1:at+1);refresh(true);
  }).show();
 }
 void refresh(boolean force){
  PlaybackService.Snapshot state=host.state();
  boolean changed=force||revision!=state.queueRevision;
  if(!changed&&index==state.index)return;
  if(changed){
   revision=state.queueRevision;
   data=host.queueItems();
   count.setText("播放队列 · "+data.length()+" 首");
  }
  index=state.index;
  locate.setEnabled(hasCurrent());
  adapter.notifyDataSetChanged();
 }

 private boolean hasCurrent(){return index>=0&&index<data.length();}
 private void locateCurrent(){
  refresh(false);
  if(hasCurrent())list.setSelectionFromTop(index,0);
 }

 @Override protected void onAttachedToWindow(){
  super.onAttachedToWindow();
  locateOnOpen=true;
 }

 @Override protected void onVisibilityChanged(View changedView,int visibility){
  super.onVisibilityChanged(changedView,visibility);
  if(changedView==this&&visibility==VISIBLE)locateOnOpen=true;
 }

 @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
  super.onLayout(changed,left,top,right,bottom);
  if(locateOnOpen&&isShown()&&list.getHeight()>0){
   locateOnOpen=false;
   post(()->{if(isShown())locateCurrent();});
  }
 }

 @Override protected void onSizeChanged(int w,int h,int ow,int oh){heading.setVisibility(h<dp(144)?GONE:VISIBLE);}
}
