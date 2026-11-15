package com.mygo.music;
interface PlayerLyricsTarget {String trackUri();boolean isShowing();void setTimeline(LyricsTimeline timeline);void setLyricMessage(String message);}
