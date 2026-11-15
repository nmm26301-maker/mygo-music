package com.mygo.music;
import android.content.SharedPreferences;
/** Rendering identities, never playback engines. */
final class PlayerThemes {
 static final String[] IDS={"dynamic","professional","immersive","minimal","spectrum","glass","cassette","polaroid","halo","vinyl"};
 static final String[] NAMES={"动态封面","双栏专业","沉浸歌词","极简","实时频谱","液态玻璃","磁带","拍立得","光环","经典唱片"};
 static final String[] DETAILS={"封面主色与流动光影","歌词、队列与歌曲信息","真实逐字高亮与平滑跟随","留白与深浅主题","真实双声道 PCM 与频谱","磨砂面板与悬浮操作","随播放转动的磁带卷轴","封面照片与轻微摆动","真实频谱环绕封面","原生唱片与唱臂"};
 static int index(String id){for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return i;return 0;}
 static String valid(String id){return IDS[index(id)];}
 static String current(SharedPreferences p){String id=p.getString(p.getBoolean("player_remember_theme",true)?"player_theme":"player_default_theme",p.getString("player_default_theme","dynamic"));return id.equals("legacy")?id:valid(id);}
 static String key(String id,String key){return id.equals("legacy")?key:"theme_"+valid(id)+"_"+key;}
 static boolean audio(String id){return id.equals("spectrum")||id.equals("halo");}
 static void reset(SharedPreferences p,String id){String prefix="theme_"+valid(id)+"_";SharedPreferences.Editor edit=p.edit();for(String k:p.getAll().keySet())if(k.startsWith(prefix))edit.remove(k);edit.apply();}
}
