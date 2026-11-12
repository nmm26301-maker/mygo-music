package com.mygo.music;

import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Narrow, source-hash-checked platform adaptations. Original installed files and licenses are retained. */
final class PluginAdapters {
    static final String SOUNDGO_SHA="bd256059a8be720df5b79db6a8da90d7a0aba0d660fd4057afba8dac97c0196e";
    private File entry;private byte[] adapted;
    PluginAdapters(File root){try{
        JSONObject manifest=new JSONObject(PluginIdentity.read(new File(root,"plugin.json")));if(!manifest.optString("id").equals("soundgo"))return;
        File file=new File(root,"index.js");if(!file.isFile())return;String source=PluginIdentity.read(file);StringBuilder hash=new StringBuilder();for(byte value:MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)))hash.append(String.format(Locale.ROOT,"%02x",value&255));if(!hash.toString().equals(SOUNDGO_SHA))return;
        String marker="  const kbps = quality === \"standard\" ? 128 : 192;";if(source.indexOf(marker)<0||source.indexOf(marker)!=source.lastIndexOf(marker))return;
        String branch="  if (host.platform === 'android') return {url:media, type:'hls', quality:quality || 'high'};\n";
        String login="  const pasted = readToken(host.setting(\"oauthToken\"));";
        if(source.indexOf(login)<0||source.indexOf(login)!=source.lastIndexOf(login))return;
        String browser="  let pasted = readToken(host.setting('oauthToken'));\n  if (!pasted && host.platform === 'android') { const session = await host.webSignIn({url:'https://soundcloud.com/signin',cookiesOf:'https://soundcloud.com',until:['oauth_token']}); pasted = readToken(session.cookie); }";
        adapted=source.replace(marker,branch+marker).replace(login,browser).getBytes(StandardCharsets.UTF_8);entry=file.getCanonicalFile();
    }catch(Exception ignored){}}
    InputStream open(File file)throws IOException{return entry!=null&&entry.equals(file.getCanonicalFile())?new ByteArrayInputStream(adapted):new FileInputStream(file);}
}
