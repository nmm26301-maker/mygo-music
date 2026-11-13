// Android transport repair, based on the public MIT Kugou protocol documentation.
// Credentials stay inside this source's own storage and application profile.
import {registerConceptDevice} from './kugou-device.mjs';
const API='https://gateway.kugou.com', SALT='LnT6xpN3khm36zse0QzvmgTZ3waWdRSA';
const UA='Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi';
const failure=(code,message)=>Object.assign(new Error(message),{code});
const ladder={standard:['128'],high:['320','128'],lossless:['high','flac','320','128']};
const utf=v=>String(v==null?'':v);
export function rememberKugouTrack(raw,id,album={}){
  if(!/^[a-f0-9]{32}$/i.test(id))return;
  const cache=host.storage.get('_android_kugou_tracks')||{};
  delete cache[id.toUpperCase()];cache[id.toUpperCase()]={hash:id,mix:raw.album_audio_id||raw.MixSongID||raw.mixsongid||0,album:raw.album_id||raw.AlbumID||raw.albumid||album.album_id||0};
  for(const key of Object.keys(cache).slice(0,-600))delete cache[key];
  host.storage.set('_android_kugou_tracks',cache);
}
function playable(data,requested){
  if(!data||typeof data!=='object')return null;
  const values=[data.url,data.backupUrl,data.backup_url].flatMap(v=>Array.isArray(v)?v:typeof v==='string'?[v]:[]);
  const url=values.find(v=>typeof v==='string'&&/^https?:\/\//i.test(v)&&!/[\r\n]/.test(v));if(!url)return null;
  const path=new URL(url).pathname;let ext=utf(data.extName||data.extname||data.ext||path.split('.').pop()).toLowerCase();
  if(/\.(mgg|mflac|qmc\w*|kgm\w*)$/i.test(path)||!['mp3','flac','ogg','aac','m4a','mp4'].includes(ext))return null;
  if(requested==='high'&&ext!=='flac')return null;
  if(data.is_free_part===1||data.IsFreePart===1||/\/p_\d+_\d+\//.test(path))return null;
  let kbps=Number(data.bitRate||data.bitrate||0);if(kbps>10000)kbps=Math.round(kbps/1000);
  const result={url,type:ext,quality:ext==='flac'?'lossless':kbps>=300?'high':'standard',bitRate:kbps||undefined,bitRateUnit:kbps?'kbps':undefined,size:Number(data.fileSize||data.filesize||0)||undefined};
  // Preserve the original sources: do not infer ReplayGain units from vendor volume fields.
  return result;
}
export function createKugouPlayback(config){return async args=>{
  const account=host.storage.get('auth')||{};
  if(config.skip?.(args.id))return config.legacy(args);
  if(!account.token||!account.userid){try{return await config.legacy(args);}catch(error){if(error.code==='unavailable')throw failure('signIn','当前酷狗来源尚未保存有效登录凭据，请在播放器中登录此来源；官方 App 中的会员登录不会自动传入插件');throw error;}}
  // A stored badge is insufficient: all requests below carry the actual source token.
  const started=Date.now(),until=started+35000;let stage='设备',lastCode='';
  const session={token:utf(account.token),userid:utf(account.userid)};let bits=config.device();
  const sign=(params,body='')=>host.crypto.md5(SALT+Object.keys(params).sort().map(k=>k+'='+params[k]).join('')+body+SALT);
  async function transport(address,init={}){
    const left=until-Date.now();if(left<=0)throw failure('unavailable','酷狗音频请求超时，阶段：'+stage);
    const abort=new AbortController(),timer=setTimeout(()=>abort.abort(),Math.min(left,7000));
    try{const response=await fetch(address,{...init,signal:abort.signal,redirect:'error',cache:'no-store'});const read=method=>async()=>{try{return await response[method]();}finally{clearTimeout(timer);}};return {ok:response.ok,status:response.status,headers:response.headers,json:read('json'),arrayBuffer:read('arrayBuffer'),discard:()=>{clearTimeout(timer);abort.abort();}};}catch(error){clearTimeout(timer);throw error;}
  }
  async function request(path,extra={},router='',key=false){
    stage=path;bits=config.device();const params={dfid:bits.dfid||'-',mid:utf(bits.mid),uuid:'-',appid:3116,clientver:11440,clienttime:Math.floor(Date.now()/1000),...session,...extra};
    if(key)params.key=host.crypto.md5(params.hash+'185672dd44712f60bb1736df5a377e82'+params.appid+params.mid+params.userid);
    params.signature=sign(params);
    const response=await transport(API+path+'?'+new URLSearchParams(Object.entries(params).map(([k,v])=>[k,utf(v)])),{headers:{'User-Agent':UA,dfid:utf(params.dfid),mid:utf(params.mid),clienttime:utf(params.clienttime),'kg-rc':'1','kg-thash':'5d816a0','kg-rec':'1','kg-rf':'B9EDA08A64250DEFFBCADDEE00F8F25F',...(router?{'x-router':router}:{})}});
    if(!response.ok){response.discard();throw failure('unavailable','酷狗请求失败：HTTP '+response.status+'，接口 '+path);}
    const body=await response.json();lastCode=utf(body.error_code||body.errcode||body.err_code||0);
    host.storage.set('_android_playback_status',{at:Date.now(),stage:path,loggedIn:true,code:lastCode});
    if(['20018','20017','34175'].includes(lastCode))throw failure('signIn','酷狗接口明确返回登录态失效，请重新登录当前来源；错误码 '+lastCode);
    if(['20028','20006'].includes(lastCode))throw failure('setup','酷狗接口要求完成设备验证；已有会员不代表设备验证已完成。错误码 '+lastCode+'，接口 '+path);
    if(Number(body.status)===0||lastCode!=='0'&&lastCode!=='200')throw failure('unavailable','酷狗请求未成功，接口 '+path+'，状态 '+utf(body.status)+'，错误码 '+lastCode);
    return body.data??body;
  }
  try{
    if(!bits.dfid||bits.dfid==='-'){
      stage='设备注册';const value=await registerConceptDevice({auth:()=>session,deviceGuid:()=>config.guid(),mid:()=>utf(config.device().mid),fetch:transport});config.saveDfid(value);bits=config.device();
    }
    const parsed=config.parse(args.id),hash=utf(parsed.hash).toLowerCase();if(!/^[a-f0-9]{32}$/.test(hash))throw failure('unavailable','酷狗歌曲缺少有效音频标识，请从当前来源重新搜索后播放');
    const base={hash,album_id:Number(parsed.album)||0,album_audio_id:Number(parsed.mix)||0,area_code:1,ssa_flag:'is_fromtrack',version:11430,page_id:967177915,behavior:'play',pid:411,cmd:26,pidversion:3001,IsFreePart:0,ppage_id:'356753938,823673182,967485191',cdnBackup:1,module:'',clientver:11430};
    const qualities=ladder[args.quality]||ladder.high;
    let userGrant,songGrant,encrypted=false;
    // Try the normal, registered-device route first. Only the same account is used.
    let direct;try{direct=await request('/v5/url',{...base,quality:qualities[0]},'trackercdn.kugou.com',true);}catch(e){if(e.code==='signIn'||e.code==='setup')throw e;}
    let result=playable(direct,qualities[0]);
    if(!result){
      userGrant=await request('/v1/user_verify',{module_id:51,clientver:11561},'trackercdngz.kugou.com');
      if(typeof userGrant.auth!=='string'||!userGrant.auth)throw failure('unavailable','酷狗账号校验未返回取流授权，不能据此判断会员状态');
      songGrant=await request('/v1/authorization',{hash,album_audio_id:base.album_audio_id,module_id:51,clientver:11561,authorization:userGrant.auth},'trackercdngz.kugou.com');
      if(!songGrant.auth||!Number.isFinite(Number(songGrant.open_time)))throw failure('unavailable','酷狗歌曲授权未返回完整凭据，不能据此判断会员状态');
      for(const quality of qualities){
        const data=await request('/tracker/v5/url',{...base,quality,clientver:11561,auth:songGrant.auth,open_time:songGrant.open_time,need_m:0,need_ogg:1,ptype:0,mtype:0,module_id:51},'',true);
        result=playable(data,quality);if(result)break;encrypted=encrypted||/mgg|mflac|qmc|kgm/i.test(utf(data.extName||data.extname||data.ext));
      }
    }
    if(!result)throw failure('unavailable',encrypted?'酷狗仅返回当前播放器不支持的加密音频，未将它标为可播放':'当前来源已携带登录凭据，但授权接口仍未返回完整音频地址；不能据此判断你没有会员');
    host.storage.set('_android_playback_status',{at:Date.now(),stage,loggedIn:true,hasUrl:true,quality:result.quality});return result;
  }catch(error){host.storage.set('_android_playback_status',{at:Date.now(),stage,loggedIn:true,code:error.code||'network',hasUrl:false});throw error;}
};}
