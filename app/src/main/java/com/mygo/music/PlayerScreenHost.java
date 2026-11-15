package com.mygo.music;
import org.json.JSONArray;
interface PlayerScreenHost extends NowPlayingDialog.Host {
 void loadThemeLyrics(String uri,PlayerLyricsTarget target);void useTheme(String id);void themes();
 JSONArray queueItems();void queuePlay(int index);void queueMove(int from,int to);void queueRemove(int index);void queueAdd();
 void setVolume(int value);boolean setSpeed(float value);void requestAnalysis();
}
