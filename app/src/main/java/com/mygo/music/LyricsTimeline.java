package com.mygo.music;

import org.json.*;
import java.util.*;
import java.util.regex.*;

/** Source timestamps are absolute milliseconds, exactly as in the desktop plugin API. */
final class LyricsTimeline {
    static final int MAX_INPUT = 2 * 1024 * 1024;
    private static final long MAX_TIME = 7L * 24 * 60 * 60 * 1000;
    static final class Word {
        final String text;
        final int from, to;
        final long start, end;
        Word(String text, int from, long start, long end) {
            this.text=text;this.from=from;this.to=from+text.length();this.start=start;this.end=end;
        }
        float fraction(long position) {
            if(position<start)return 0;
            if(end<=start)return 1;
            return (float)Math.max(0,Math.min(1,(position-start)/(double)(end-start)));
        }
    }
    static final class Line {
        final long start;
        long end;
        final String text;
        String translation;
        List<Word> words;
        boolean estimated;
        Line(long start,long end,String text,String translation,List<Word> words) {
            this.start=start;this.end=end;this.text=text;this.translation=translation;
            this.words=Collections.unmodifiableList(words);
        }
    }
    final List<Line> lines;
    final boolean timed, wordTimed, estimatedWordTiming;
    private LyricsTimeline(List<Line> data,boolean timed) {
        if(timed)Collections.sort(data,(a,b)->Long.compare(a.start,b.start));
        boolean words=false,estimated=false;
        for(int i=0;i<data.size();i++) {
            Line line=data.get(i);
            if(line.end<=line.start)line.end=i+1<data.size()?Math.max(line.start+1,Math.min(line.start+6000,data.get(i+1).start)):line.start+6000;
            if(timed&&line.words.isEmpty()){
                line.words=spread(line.text,line.start,line.end);line.estimated=true;estimated=true;
            }
            words|=!line.words.isEmpty();
        }
        lines=Collections.unmodifiableList(data);this.timed=timed;wordTimed=words;estimatedWordTiming=estimated;
    }
    static LyricsTimeline empty(){return new LyricsTimeline(new ArrayList<>(),false);}
    int active(long position) {
        if(!timed||lines.isEmpty())return -1;
        int low=0,high=lines.size()-1,result=-1;
        while(low<=high){int mid=(low+high)>>>1;if(lines.get(mid).start<=position){result=mid;low=mid+1;}else high=mid-1;}
        return result;
    }
    static LyricsTimeline parse(String raw)throws JSONException {
        if(raw==null||raw.trim().isEmpty()||raw.trim().equals("null"))return empty();
        if(raw.length()>MAX_INPUT)throw new JSONException("歌词文件超过 2 MB");
        raw=raw.replace("\uFEFF","").trim();
        // Plugin results are JSON-encoded, including string LRC and the empty string.
        if(raw.startsWith("\""))try{Object decoded=new JSONTokener(raw).nextValue();if(decoded instanceof String)raw=((String)decoded).trim();}catch(JSONException ignored){}
        if(raw.isEmpty()||raw.equals("null"))return empty();
        if(raw.startsWith("{")) {
            JSONObject root=new JSONObject(raw);JSONArray items=root.optJSONArray("lines");
            if(items!=null&&items.length()>0) {
                List<Line> lines=new ArrayList<>();int count=0;
                for(int i=0;i<items.length()&&lines.size()<5000;i++) {
                    JSONObject line=items.optJSONObject(i);if(line==null)continue;
                    long start=time(line.optLong("start")),end=time(line.optLong("end"));
                    JSONArray words=line.optJSONArray("words");List<Word> parsed=new ArrayList<>();StringBuilder text=new StringBuilder();
                    if(words!=null)for(int j=0;j<words.length()&&count<100000;j++) {
                        JSONObject word=words.optJSONObject(j);if(word==null)continue;
                        String value=word.optString("text","");if(value.isEmpty())continue;
                        long from=time(word.optLong("start",start));long to=time(word.optLong("end",from));
                        if(to<from)to=from;
                        parsed.add(new Word(value,text.length(),from,to));text.append(value);end=Math.max(end,to);count++;
                    }
                    String value=parsed.isEmpty()?line.optString("text",""):text.toString();
                    if(!value.trim().isEmpty())lines.add(new Line(start,end,value,line.optString("translation",""),parsed));
                }
                if(!lines.isEmpty()){LyricsTimeline result=new LyricsTimeline(lines,true);attachTranslation(result,root.optString("translation",""));return result;}
            }
            String lrc=root.optString("lrc","");if(lrc.isEmpty())lrc=root.optString("syncedLyrics","");
            if(lrc.isEmpty())lrc=root.optString("plainLyrics","");
            LyricsTimeline result=parseLrc(lrc);attachTranslation(result,root.optString("translation",""));return result;
        }
        return parseLrc(raw);
    }
    private static long time(long value){return Math.max(0,Math.min(MAX_TIME,value));}
    /** Same fallback as upstream internal/lyrics: CJK characters, other words by length, 85% singing. */
    private static List<Word> spread(String text,long start,long end){
        List<String> parts=new ArrayList<>();StringBuilder pending=new StringBuilder();
        for(int i=0;i<text.length();){int cp=text.codePointAt(i);i+=Character.charCount(cp);
            Character.UnicodeScript script=Character.UnicodeScript.of(cp);
            boolean cjk=script==Character.UnicodeScript.HAN||script==Character.UnicodeScript.HIRAGANA||script==Character.UnicodeScript.KATAKANA||script==Character.UnicodeScript.HANGUL;
            if(cjk){if(pending.length()>0){parts.add(pending.toString());pending.setLength(0);}parts.add(new String(Character.toChars(cp)));}
            else if(Character.isWhitespace(cp)){pending.appendCodePoint(cp);parts.add(pending.toString());pending.setLength(0);}
            else pending.appendCodePoint(cp);
        }
        if(pending.length()>0)parts.add(pending.toString());
        int weight=0;for(String part:parts)weight+=part.trim().codePointCount(0,part.trim().length());
        List<Word> words=new ArrayList<>();long span=(long)((end-start)*.85),at=start;int charAt=0;
        for(String part:parts){String trimmed=part.trim();long length=span*trimmed.codePointCount(0,trimmed.length())/Math.max(1,weight);words.add(new Word(part,charAt,at,at+length));at+=length;charAt+=part.length();}
        return Collections.unmodifiableList(words);
    }
    private static final Pattern TAG=Pattern.compile("\\[(\\d{1,5}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern WORD=Pattern.compile("<(\\d{1,5}):(\\d{2})(?:[.:](\\d{1,3}))?>");
    private static long stamp(Matcher m){String part=m.group(3);long fraction=part==null?0:Long.parseLong(part)*(part.length()==1?100:part.length()==2?10:1);return time((Long.parseLong(m.group(1))*60+Long.parseLong(m.group(2)))*1000+fraction);}
    private static LyricsTimeline parseLrc(String input) {
        List<Line> lines=new ArrayList<>();List<String> plain=new ArrayList<>();long offset=0;
        Matcher offsetTag=Pattern.compile("\\[offset:([+-]?\\d{1,9})\\]",Pattern.CASE_INSENSITIVE).matcher(input);
        if(offsetTag.find())offset=Long.parseLong(offsetTag.group(1));
        for(String row:input.replace("\r","").split("\n")) {
            if(lines.size()>=5000)break;
            Matcher tags=TAG.matcher(row);List<Long> starts=new ArrayList<>();int bodyAt=0;
            while(tags.find()){starts.add(stamp(tags));bodyAt=tags.end();}
            if(starts.isEmpty()){if(!row.trim().isEmpty()&&!row.trim().matches("\\[[a-zA-Z]+:.*\\]"))plain.add(row.trim());continue;}
            String body=row.substring(bodyAt);Matcher marks=WORD.matcher(body);
            List<Long> wordStarts=new ArrayList<>();List<String> wordText=new ArrayList<>();int last=0;long lastStart=starts.get(0);StringBuilder full=new StringBuilder();
            while(marks.find()) {
                if(marks.start()>last){String segment=body.substring(last,marks.start());wordStarts.add(lastStart);wordText.add(segment);full.append(segment);}
                lastStart=stamp(marks);last=marks.end();
            }
            if(last<body.length()){String segment=body.substring(last);wordStarts.add(lastStart);wordText.add(segment);full.append(segment);}
            boolean enhanced=last>0;String value=enhanced?full.toString():body;
            if(value.trim().isEmpty())continue;
            for(long start:starts) {
                long shift=start-starts.get(0)+offset;List<Word> words=new ArrayList<>();int charAt=0;
                if(enhanced)for(int j=0;j<wordText.size();j++) {
                    long from=time(wordStarts.get(j)+shift);
                    // A final marker is a real end timestamp. An unclosed final word is instantaneous.
                    long to=j+1<wordStarts.size()?time(wordStarts.get(j+1)+shift):time(lastStart+shift);
                    words.add(new Word(wordText.get(j),charAt,from,Math.max(from,to)));charAt+=wordText.get(j).length();
                }
                long end=words.isEmpty()?0:words.get(words.size()-1).end;
                lines.add(new Line(time(start+offset),end,value,"",words));
            }
        }
        if(!lines.isEmpty())return new LyricsTimeline(lines,true);
        for(String row:plain){if(lines.size()==5000)break;lines.add(new Line(0,0,row,"",new ArrayList<>()));}
        return new LyricsTimeline(lines,false);
    }
    private static void attachTranslation(LyricsTimeline result,String raw) {
        if(raw.isEmpty())return;
        LyricsTimeline translated=parseLrc(raw);
        for(int i=0;i<result.lines.size();i++) {
            Line line=result.lines.get(i);if(!line.translation.isEmpty())continue;
            int at=translated.timed?translated.active(line.start):i;
            if(at>=0&&at<translated.lines.size()) {
                Line candidate=translated.lines.get(at);
                if(!translated.timed||Math.abs(line.start-candidate.start)<80)line.translation=candidate.text;
            }
        }
    }
}
