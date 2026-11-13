// First-party QQ Music website login. Never treats p_skey or a saved QR as a music session.
const musicKeys=['qqmusic_key','qm_keyst','music_key','wxskey'];
const ids=['qqmusic_uin','musicuin','uin','wxuin'];
const invalid=v=>!v||/^(null|undefined|deleted|0)$/i.test(v);
export function qqWebSession(cookie,loginType=2){
 if(![1,2].includes(loginType)||typeof cookie!=='string'||/[\r\n]/.test(cookie))throw new Error('QQ音乐网页登录返回的数据无效');
 const values=new Map();for(const pair of cookie.split(';')){const at=pair.indexOf('=');if(at>0)values.set(pair.slice(0,at).trim(),pair.slice(at+1).trim());}
 const key=musicKeys.map(k=>values.get(k)).find(v=>!invalid(v));
 const order=loginType===1?['qqmusic_uin','musicuin','wxuin','uin']:ids;const raw=order.map(k=>values.get(k)).find(v=>/^o?\d+$/i.test(v||'')&&!/^o?0+$/i.test(v));
 if(!key||!raw||/[;\s]/.test(key))throw new Error('尚未取得QQ音乐播放凭据；普通QQ网页登录状态不能代替音乐授权');
 const musicid=raw.replace(/^o/i,'').replace(/^0+/,'');if(!musicid||musicid.length>24)throw new Error('QQ音乐网页登录未返回有效账号');
 if(loginType===2&&/^W_/.test(key)||loginType===1&&/^Q_/.test(key))throw new Error('网页登录账号类型与选择的QQ或微信方式不一致，请重新选择登录方式');
 return {musicid,musickey:key,loginType,web:true};
}
export async function qqWebsiteLogin({finish,loginType=2}){
 const result=await host.webSignIn({url:'https://y.qq.com/portal/player.html',cookiesOf:'https://y.qq.com/',
  untilAny:[ids,musicKeys],imageScanning:false,desktop:true,title:'QQ音乐官方网页登录',
  message:'请使用官网提供的账号密码等方式登录。若页面仅允许摄像头扫码，本机相册不能完成该授权。',
  userAgent:'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36'});
 // The original provider validates this candidate before persisting it.
 return finish(qqWebSession(result?.cookie,loginType));
}
