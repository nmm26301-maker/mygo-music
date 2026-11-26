package com.mygo.music;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

import java.util.*;
import java.util.regex.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Android implementation of the upstream requestHeaders protocol, internal/plugin/headers.go.
 * Protocol and validation reference: egoist/mygo-music, AGPL-3.0. No extra JS dependencies. */
final class PluginMedia {
    private static final Pattern PLACEHOLDER=Pattern.compile("\\{(setting|storage):([^{}]+)\\}");
    static boolean contains(Map<String,String> values,String key){for(String name:values.keySet())if(name.equalsIgnoreCase(key))return true;return false;}
    private static boolean reaches(JSONArray network,String host){for(int i=0;i<network.length();i++)if(PluginRuntime.hostMatches(host,network.optString(i)))return true;return false;}
    private static JSONArray rules(JSONObject manifest)throws Exception{
        JSONArray merged=new JSONArray();JSONObject permissions=manifest.optJSONObject("permissions");
        for(JSONObject owner:new JSONObject[]{manifest,permissions}){if(owner==null||!owner.has("requestHeaders"))continue;JSONArray values=owner.optJSONArray("requestHeaders");if(values==null)throw new IOException("资源请求头规则必须是数组");for(int i=0;i<values.length();i++)merged.put(values.getJSONObject(i));}return merged;
    }
    static void validate(JSONObject manifest)throws Exception{
        JSONArray rules=rules(manifest);if(rules.length()==0)return;
        JSONObject permissions=manifest.optJSONObject("permissions");JSONArray network=permissions==null?new JSONArray():permissions.optJSONArray("network");if(network==null)network=new JSONArray();
        for(int i=0;i<rules.length();i++){
            JSONObject rule=rules.getJSONObject(i);JSONArray hosts=rule.getJSONArray("hosts");JSONObject headers=rule.getJSONObject("headers");
            if(hosts.length()==0||headers.length()==0)throw new IOException("资源请求头规则缺少域名或请求头");
            JSONArray kinds=rule.optJSONArray("kinds");if(kinds!=null)for(int j=0;j<kinds.length();j++)if(!Arrays.asList("image","audio").contains(kinds.getString(j)))throw new IOException("资源请求头类型无效");
            for(int j=0;j<hosts.length();j++){
                String host=hosts.getString(j).toLowerCase(Locale.ROOT);
                if(host.matches("\\{setting:[^{}]+\\}"))continue;
                if(host.isEmpty()||host.equals("*")||host.matches(".*[{}\\/:].*"))throw new IOException("资源请求头域名无效");
                boolean covered=false;for(int k=0;k<network.length();k++){
                    String allowed=network.optString(k).toLowerCase(Locale.ROOT);
                    if(allowed.equals("*")||allowed.equals(host)||(allowed.startsWith("*.")&&host.replaceFirst("^\\*","").endsWith(allowed.substring(1))))covered=true;
                }
                if(!covered)throw new IOException("资源请求头域名超出网络权限："+host);
            }
            for(Iterator<String> keys=headers.keys();keys.hasNext();)if(!validName(keys.next()))throw new IOException("资源请求头名称无效");
        }
    }
    private static boolean validName(String name){return name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")&&!name.equalsIgnoreCase("Host")&&!name.equalsIgnoreCase("Content-Length")&&!name.equalsIgnoreCase("android-allow-cross-domain-redirect");}
    static HashMap<String,String> headers(Context context,PluginManager.PluginInfo source,String address,String kind,JSONObject explicit)throws Exception{
        HashMap<String,String> result=new HashMap<>();URL url=new URL(address);
        if(!url.getProtocol().equals("https")&&!url.getProtocol().equals("http"))throw new IOException("资源地址必须使用 HTTP/HTTPS");
        if(source!=null){
            JSONObject manifest=new JSONObject(PluginIdentity.read(new File(source.directory,"plugin.json")));validate(manifest);
            JSONArray rules=rules(manifest);SharedPreferences settings=context.getSharedPreferences("plugin_settings_"+source.id,0),storage=context.getSharedPreferences("plugin_data_"+source.id,0);
            if(rules!=null&&context.getSharedPreferences("library",0).getBoolean("plugin_network_"+source.id,false))for(int i=0;i<rules.length();i++){
                JSONObject rule=rules.getJSONObject(i);JSONArray kinds=rule.optJSONArray("kinds");if(kinds!=null&&kinds.length()>0){boolean matches=false;for(int j=0;j<kinds.length();j++)if(kind.equals(kinds.optString(j)))matches=true;if(!matches)continue;}
                boolean matches=false;JSONArray hosts=rule.getJSONArray("hosts");for(int j=0;j<hosts.length();j++){
                    String host=hosts.getString(j);
                    if(host.startsWith("{setting:")){String server=settings.getString(host.substring(9,host.length()-1),"").trim();try{URL configured=new URL(server.contains("://")?server:"http://"+server);if(configured.getHost().equalsIgnoreCase(url.getHost())&&reaches(source.network,url.getHost()))matches=true;}catch(Exception ignored){}}
                    else if(PluginRuntime.hostMatches(url.getHost(),host))matches=true;
                }
                if(!matches)continue;JSONObject values=rule.getJSONObject("headers");for(Iterator<String> keys=values.keys();keys.hasNext();){String key=keys.next(),value=fill(values.getString(key),settings,storage);if(value!=null&&!contains(result,key))result.put(key,value);}
            }
        }
        if(explicit!=null)for(Iterator<String> keys=explicit.keys();keys.hasNext();){String key=keys.next(),value=explicit.getString(key);if(!validName(key)||value.indexOf('\r')>=0||value.indexOf('\n')>=0)throw new IOException("音频请求头无效");result.keySet().removeIf(name->name.equalsIgnoreCase(key));result.put(key,value);}
        return result;
    }
    private static String fill(String template,SharedPreferences settings,SharedPreferences storage)throws Exception{
        Matcher matcher=PLACEHOLDER.matcher(template);StringBuffer out=new StringBuffer();while(matcher.find()){
            String value;
            if(matcher.group(1).equals("setting"))value=settings.getString(matcher.group(2),"");
            else{String raw=storage.getString(matcher.group(2),"");if(raw.isEmpty()||raw.equals("null"))return null;Object decoded=new JSONTokener(raw).nextValue();value=decoded instanceof String?(String)decoded:String.valueOf(decoded);}
            if(value.isEmpty())return null;matcher.appendReplacement(out,Matcher.quoteReplacement(value));
        }matcher.appendTail(out);String value=out.toString();return value.indexOf('\r')>=0||value.indexOf('\n')>=0?null:value;
    }
    static final class Connection implements AutoCloseable{
        final HttpURLConnection http;final HashMap<String,String> headers;
        Connection(HttpURLConnection http,HashMap<String,String> headers){this.http=http;this.headers=headers;}
        public void close(){http.disconnect();}
    }
    /** A progressive audio server may accept ranges while rejecting an unbounded GET. */
    static HashMap<String,String> progressiveHeaders(Map<String,String> values){
        HashMap<String,String> headers=new HashMap<>(values);
        if(!contains(headers,"Range"))headers.put("Range","bytes=0-");
        return headers;
    }
    /** Download the complete representation, never a source's partial byte window. */
    static Connection openFullAudio(String address,Map<String,String> values)throws Exception{
        Connection result=open(address,values,false,true);
        try{fullAudioLength(result.http);return result;}catch(Exception error){result.close();throw error;}
    }
    static long fullAudioLength(HttpURLConnection http)throws Exception{
        if(http.getResponseCode()==200)return http.getContentLengthLong();
        if(http.getResponseCode()!=206)throw new IOException("完整音频返回 HTTP "+http.getResponseCode());
        String range=http.getHeaderField("Content-Range");Matcher match=Pattern.compile("(?i)bytes 0-([0-9]+)/([0-9]+)").matcher(range==null?"":range.trim());
        if(!match.matches())throw new IOException("音频分段未覆盖完整文件");
        long end=Long.parseLong(match.group(1)),total=Long.parseLong(match.group(2)),length=http.getContentLengthLong();
        if(total<=0||end!=total-1||length>=0&&length!=total)throw new IOException("音频分段未覆盖完整文件");
        return total;
    }
    private static final ExecutorService probeCleanup=Executors.newFixedThreadPool(2, action->{
        Thread thread=new Thread(action,"mygo-audio-probe-cleanup");
        thread.setDaemon(true);
        return thread;
    });

    /** Tracks the disposable header request for one playback selection. */
    static final class PlaybackProbe {
        private volatile boolean cancelled;
        private volatile HttpURLConnection connection;

        Connection open(String address,Map<String,String> headers)throws Exception{
            return PluginMedia.open(address,headers,true,false,this);
        }

        void cancel(){
            cancelled=true;
            HttpURLConnection active=connection;
            if(active!=null)probeCleanup.execute(()->{
                try{active.disconnect();}catch(RuntimeException ignored){}
            });
        }

        private void check()throws IOException{
            if(cancelled)throw new IOException("音频探测已取消");
        }
    }

    /** Follow redirects ourselves: a token cannot follow a change of host, port or scheme. */
    static Connection open(String address,Map<String,String> values,boolean probe)throws Exception{
        return open(address,values,probe,false);
    }
    private static Connection open(String address,Map<String,String> values,boolean probe,boolean fullAudio)throws Exception{
        return open(address,values,probe,fullAudio,null);
    }

    private static Connection open(String address,Map<String,String> values,boolean probe,boolean fullAudio,PlaybackProbe playback)throws Exception{
        URL current=new URL(address);HashMap<String,String> headers=new HashMap<>(values);
        for(int redirects=0;redirects<=8;redirects++){
            if(playback!=null)playback.check();
            if(!Arrays.asList("https","http").contains(current.getProtocol()))throw new IOException("资源重定向协议无效");
            HttpURLConnection connection=(HttpURLConnection)current.openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(fullAudio?12000:10000);connection.setReadTimeout(fullAudio?20000:12000);
            try{
                for(Map.Entry<String,String> entry:headers.entrySet())connection.setRequestProperty(entry.getKey(),entry.getValue());
                if(probe)connection.setRequestProperty("Range","bytes=0-0");
                else if(fullAudio){connection.setRequestProperty("Range","bytes=0-");connection.setRequestProperty("Accept-Encoding","identity");}
                if(playback!=null){playback.connection=connection;playback.check();}
                int status=connection.getResponseCode();
                if(playback!=null)playback.check();
                if(Arrays.asList(301,302,303,307,308).contains(status)){
                    String location=connection.getHeaderField("Location");if(location==null)throw new IOException("资源重定向缺少地址");URL next=new URL(current,location);
                    if(!sameOrigin(current,next))headers.clear();current=next;connection.disconnect();continue;
                }
                if(status<200||status>=300)throw new IOException("资源服务器返回 "+status);
                return new Connection(connection,headers);
            }catch(Exception error){connection.disconnect();throw error;}
        }throw new IOException("资源重定向次数过多");
    }
    static boolean sameOrigin(URL a,URL b){return a.getProtocol().equalsIgnoreCase(b.getProtocol())&&a.getHost().equalsIgnoreCase(b.getHost())&&(a.getPort()<0?a.getDefaultPort():a.getPort())==(b.getPort()<0?b.getDefaultPort():b.getPort());}
}
