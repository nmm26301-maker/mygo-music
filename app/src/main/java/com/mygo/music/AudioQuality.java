package com.mygo.music;

import org.json.*;
import java.util.*;

/** Keep the requested tier separate from the source's returned description. */
final class AudioQuality {
    static final String DEFAULT="high";
    static final String[] VALUES={"standard","high","lossless"};
    static final String[] LABELS={"标准 · 节省流量","高品质","无损优先"};
    static String normalize(String value){return Arrays.asList(VALUES).contains(value)?value:DEFAULT;}
    static String label(String value){int index=Arrays.asList(VALUES).indexOf(value);return index<0?"未报告":LABELS[index];}
    static double numeric(JSONObject value,String key){double n=value==null?0:value.optDouble(key,0);return Double.isNaN(n)||Double.isInfinite(n)||n<0?0:n;}
    static JSONObject info(JSONObject input){
        JSONObject result=PlaybackLoudness.info(input);if(input==null)return result;
        try{
            String tier=input.optString("sourceQuality",input.optString("quality",""));if(Arrays.asList(VALUES).contains(tier))result.put("sourceQuality",tier);
            String codec=input.optString("sourceCodec",input.optString("type",input.optString("codec",""))).toLowerCase(Locale.ROOT);if(codec.matches("[a-z0-9+._-]{1,24}"))result.put("sourceCodec",codec);
            double rate=numeric(input,input.has("sourceBitRate")?"sourceBitRate":"bitRate");if(rate>0&&rate<=100000000)result.put("sourceBitRate",rate);
            String unit=input.optString("sourceBitRateUnit",input.optString("bitRateUnit",""));if(unit.equals("bps")||unit.equals("kbps"))result.put("sourceBitRateUnit",unit);
            double sample=numeric(input,input.has("sourceSampleRate")?"sourceSampleRate":"sampleRate");if(sample>=8000&&sample<=768000)result.put("sourceSampleRate",sample);
            double bits=numeric(input,input.has("sourceBitDepth")?"sourceBitDepth":input.has("bitDepth")?"bitDepth":"bitsPerSample");if(Arrays.asList(8.0,16.0,20.0,24.0,32.0,64.0).contains(bits))result.put("sourceBitDepth",bits);
        }catch(JSONException ignored){}return result;
    }
    static String describe(JSONObject raw){
        JSONObject info=info(raw);ArrayList<String> parts=new ArrayList<>();String quality=info.optString("sourceQuality"),codec=info.optString("sourceCodec");
        if(!quality.isEmpty())parts.add(quality.equals("lossless")?"无损":label(quality));if(!codec.isEmpty())parts.add(codec.toUpperCase(Locale.ROOT));
        double rate=numeric(info,"sourceBitRate");String unit=info.optString("sourceBitRateUnit");if(rate>0){if(unit.equals("bps"))parts.add(number(rate/1000)+" kbps");else if(unit.equals("kbps"))parts.add(number(rate)+" kbps");else parts.add("码率参数 "+number(rate)+"（单位未说明）");}
        double sample=numeric(info,"sourceSampleRate"),bits=numeric(info,"sourceBitDepth");if(bits>0)parts.add(number(bits)+" bit");if(sample>0)parts.add(number(sample/1000)+" kHz");
        return parts.isEmpty()?"音源未报告音质参数":"音源报告："+String.join(" · ",parts);
    }
    private static String number(double value){return value==Math.rint(value)?String.format(Locale.ROOT,"%.0f",value):String.format(Locale.ROOT,"%.2f",value).replaceAll("0+$","").replaceAll("\\.$","");}
}
