package com.mygo.music;

import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Narrow, source-hash-checked platform adaptations. Original installed files and licenses are retained. */
final class PluginAdapters {
    static final String SOUNDGO_SHA="bd256059a8be720df5b79db6a8da90d7a0aba0d660fd4057afba8dac97c0196e";
    private File entry;private byte[] adapted;boolean deviceNetwork,qqWebLogin;
    PluginAdapters(File root){try{
        JSONObject manifest=new JSONObject(PluginIdentity.read(new File(root,"plugin.json")));String identity=manifest.optString("id");if(!identity.equals("soundgo")&&!identity.equals("kugou")&&!identity.equals("kugou-lite")&&!identity.equals("qqmusic"))return;
        File file=new File(root,identity.equals("qqmusic")?manifest.optString("main","index.js"):"index.js");if(!file.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator)||!file.isFile())return;String source=PluginIdentity.read(file);StringBuilder hash=new StringBuilder();for(byte value:MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)))hash.append(String.format(Locale.ROOT,"%02x",value&255));String digest=hash.toString();
        if(identity.equals("qqmusic")&&digest.equals("cf6fdcb57ac32da865e6cc4d6d34bfecdfa2970e33da8659dba5a40de384f8bc")){
            String marker="async function signIn() {",comm="...session3.device ?";if(source.indexOf(marker)<0||source.indexOf(marker)!=source.lastIndexOf(marker)||source.indexOf(comm)<0||source.indexOf(comm)!=source.lastIndexOf(comm))return;
            source="import {qqWebsiteLogin as __androidQQWebLogin} from 'https://plugin.invalid/.host/qq-web-login.mjs';\n"+source.replace(marker,"async function __androidQQOriginalSignIn() {").replace(comm,"...session3.web ? {ct:24,cv:0,uin:session3.musicid,platform:'yqq.json'} : session3.device ?");
            source=source.replace("if (session3) param.ctx = 0;","if (session3 && !session3.web) param.ctx = 0;").replace("rpc(session3 ? \"music.vkey.GetVkey\" : \"vkey.GetVkeyServer\", session3 ? \"UrlGetVkey\" : \"CgiGetVkey\", param)","rpc(session3 && !session3.web ? \"music.vkey.GetVkey\" : \"vkey.GetVkeyServer\", session3 && !session3.web ? \"UrlGetVkey\" : \"CgiGetVkey\", param)");
            source=source.replace("\"User-Agent\": \"QQMusic 14090008(android 10)\"","\"User-Agent\": session3 && session3.web ? \"Mozilla/5.0\" : \"QQMusic 14090008(android 10)\"");
            source+="\nasync function signIn(){const mode=(host.setting('loginMethod')||'').trim().toLowerCase();if(mode==='wechat'||mode==='微信')return __androidQQOriginalSignIn();if(mode==='qq-camera')return qqFinishSignIn(await qqSignIn({fetch,decodeQR,prompt:value=>host.signInPrompt(value),sleep:ms=>new Promise(resolve=>setTimeout(resolve,ms))}));if(!mode||mode==='qq'||mode==='web'||mode==='web-wechat')return __androidQQWebLogin({finish:qqFinishSignIn,loginType:mode==='web-wechat'?1:2});throw new Error('请选择官方网页登录或明确的摄像头扫码方式');}\n";
            adapted=source.getBytes(StandardCharsets.UTF_8);entry=file.getCanonicalFile();qqWebLogin=true;return;
        }
        if(identity.equals("kugou")&&digest.equals("4c463558265aa8a7a41606ad2ada7a50512d68ed791f462ed8de0ce515d14e24")){
            String marker="export async function stream({ id, quality })";String point="  const duration = Number(track.duration";
            if(source.indexOf(marker)<0||source.indexOf(point)<0)return;
            source=source.replace(marker,"async function __androidLegacyStream({ id, quality })").replace(point,"  __androidRemember(track,id,albumInfo);\n"+point);
            source="import {createKugouPlayback as __androidKugou,rememberKugouTrack as __androidRemember} from 'https://plugin.invalid/.host/kugou-playback.mjs';\n"+source;
            source+="\nexport const stream=__androidKugou({legacy:__androidLegacyStream,device:()=>({mid:deviceID(),dfid:host.storage.get('dfid')||'-'}),guid:()=>host.crypto.md5(deviceID()),saveDfid:value=>host.storage.set('dfid',value),parse:id=>(host.storage.get('_android_kugou_tracks')||{})[String(id).toUpperCase()]||{hash:id,mix:0,album:0}});\n";
            adapted=source.getBytes(StandardCharsets.UTF_8);entry=file.getCanonicalFile();deviceNetwork=true;return;
        }
        if(identity.equals("kugou-lite")&&digest.equals("38f94c0ca21a4e289cdeb7f4066e62377f0c01c944ca0484e1074f77f53f32ce")){
            String marker="export async function stream({ id, quality })";if(source.indexOf(marker)<0)return;
            source="import {createKugouPlayback as __androidKugou} from 'https://plugin.invalid/.host/kugou-playback.mjs';\n"+source.replace(marker,"async function __androidLegacyStream({ id, quality })");
            source+="\nexport const stream=__androidKugou({legacy:__androidLegacyStream,device:()=>({mid:mid(),dfid:dfid()}),guid:deviceGuid,saveDfid:value=>store.set('dfid',value),parse:parseId,skip:id=>String(id).endsWith(CLOUD_TAG)});\n";
            adapted=source.getBytes(StandardCharsets.UTF_8);entry=file.getCanonicalFile();return;
        }
        if(!identity.equals("soundgo")||!digest.equals(SOUNDGO_SHA))return;
        String marker="  const kbps = quality === \"standard\" ? 128 : 192;";if(source.indexOf(marker)<0||source.indexOf(marker)!=source.lastIndexOf(marker))return;
        String branch="  if (host.platform === 'android') return {url:media, type:'hls', quality:quality || 'high'};\n";
        String login="  const pasted = readToken(host.setting(\"oauthToken\"));";
        if(source.indexOf(login)<0||source.indexOf(login)!=source.lastIndexOf(login))return;
        String browser="  let pasted = readToken(host.setting('oauthToken'));\n  if (!pasted && host.platform === 'android') { const session = await host.webSignIn({url:'https://soundcloud.com/signin',cookiesOf:'https://soundcloud.com',until:['oauth_token']}); pasted = readToken(session.cookie); }";
        adapted=source.replace(marker,branch+marker).replace(login,browser).getBytes(StandardCharsets.UTF_8);entry=file.getCanonicalFile();
    }catch(Exception ignored){}}
    InputStream open(File file)throws IOException{return entry!=null&&entry.equals(file.getCanonicalFile())?new ByteArrayInputStream(adapted):new FileInputStream(file);}
}
