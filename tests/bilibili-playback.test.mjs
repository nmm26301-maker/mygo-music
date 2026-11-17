import test from 'node:test';
import assert from 'node:assert/strict';
import {selectBilibiliMirror,androidBilibiliHeAac} from '../app/src/main/assets/bilibili-playback.mjs';
const a='https://one.bilivideo.com/audio.m4a?signature=first';
const b='https://two.bilivideo.cn/audio.m4a?signature=second';
const media=Buffer.from([0,0,0,24,...Buffer.from('ftypM4A '),0,0,0,0]);

test('HE-AAC profiles use original readable media without claiming lossless or inventing sample precision',async()=>{
 for(const [codec,expected] of [['mp4a.40.5','he-aac'],['mp4a.40.29','he-aac-v2']]){
  const headers={Referer:'https://www.bilibili.com/','User-Agent':'original-agent'};
  const result=await androidBilibiliHeAac({as:'he',codec,urls:[a,b],bandwidth:64000},'lossless',headers,{fetch:async u=>u===a?new Response('',{status:403}):response()});
  assert.equal(result.url,b);assert.equal(result.type,'m4a');assert.equal(result.sourceCodec,expected);assert.equal(result.quality,'high');assert.equal(result.sourceQuality,'high');assert.equal(result.sourceBitRateUnit,'bps');assert.equal(result.bitRate,64000);assert.equal(result.headers,headers);assert.equal(result.sampleRate,undefined);assert.equal(result.bitDepth,undefined);
 }
});
test('unknown codecs and existing AAC-LC or FLAC branches retain the original source behavior',async()=>{
 for(const audio of [{as:'he',codec:'opus'},{as:'he',codec:'mp4a.40.42'},{as:'he',codec:''},{as:'m4a',codec:'mp4a.40.2'},{as:'flac',codec:'flac'}])assert.equal(await androidBilibiliHeAac(audio,'high',{}, {fetch:()=>{throw Error('unexpected request');}}),null);
});
test('standard HE-AAC quality is retained and an unreadable source is not converted into a desktop URL',async()=>{
 assert.equal((await androidBilibiliHeAac({as:'he',codec:'mp4a.40.5',urls:[a]},'standard',{}, {fetch:async()=>response()})).quality,'standard');
 await assert.rejects(androidBilibiliHeAac({as:'he',codec:'mp4a.40.29',urls:[a]},'high',{}, {fetch:async()=>new Response('',{status:403})}),e=>e.code==='unavailable');
});
function response(data=media,{status=206,headers={}}={}){return new Response(data,{status,headers:{'Content-Type':'video/mp4','Content-Length':String(data.length),...(status===206?{'Content-Range':`bytes 0-${data.length-1}/${data.length}`}:{ }),...headers}});}

test('one-byte-success mirror is rejected by the real playback range and signed backup is retained',async()=>{
 const calls=[];const fetch=async(url,init)=>{calls.push({url,init});return url===a?new Response('denied',{status:403}):response();};
 assert.equal(await selectBilibiliMirror([a,b],{Referer:'https://www.bilibili.com/','User-Agent':'source-agent'},{fetch}),b);
 assert.deepEqual(calls.map(x=>x.url),[a,b]);for(const call of calls){assert.equal(call.init.headers.Range,'bytes=0-');assert.equal(call.init.headers.Referer,'https://www.bilibili.com/');assert.equal(call.init.headers['User-Agent'],'source-agent');assert.equal(call.init.signal.aborted,true);}
});
test('a single permitted backup is checked before an unprobeable first CDN',async()=>{
 const calls=[];const fetch=async u=>{calls.push(u);return response();};
 assert.equal(await selectBilibiliMirror(['https://media.akamaized.net/file?original=1',b],{}, {fetch}),b);assert.deepEqual(calls,[b]);
});
test('non-media and one-byte partial responses never count as playable',async()=>{
 for(const bad of [response(Buffer.from('x')),response(Buffer.from('{"error":1}'),{status:200,headers:{'Content-Type':'application/json'}}),response(Buffer.from('not an audio response'),{status:200}),response(media,{headers:{'Content-Range':'bytes 100-115/1000'}})]){
  const calls=[];assert.equal(await selectBilibiliMirror([a,b],{}, {fetch:async u=>{calls.push(u);return u===a?bad:response();}}),b);assert.equal(calls.length,2);
 }
});
test('partial network chunks are assembled only to the signature and body is cancelled',async()=>{
 let reads=0,cancelled=0;const body=new ReadableStream({pull(c){reads++;c.enqueue(media.subarray(reads===1?0:5,reads===1?5:16));},cancel(){cancelled++;}});
 const r=new Response(body,{headers:{'Content-Type':'application/octet-stream'}});assert.equal(await selectBilibiliMirror([a],{}, {fetch:async()=>r}),a);assert.equal(cancelled,1);assert.ok(reads<=3);
});
test('ignored Range is cancelled after prefix instead of downloading a complete file',async()=>{
 let cancelled=0;const body=new ReadableStream({pull(c){c.enqueue(media);},cancel(){cancelled++;}});assert.equal(await selectBilibiliMirror([a],{}, {fetch:async()=>new Response(body)}),a);assert.equal(cancelled,1);
});
test('a stalled body is aborted before trying the backup',async()=>{
 let aborted=false;const fetch=async(url,{signal})=>url===a?new Response(new ReadableStream({start(c){signal.addEventListener('abort',()=>{aborted=true;c.error(new DOMException('aborted','AbortError'));});}})):response();
 assert.equal(await selectBilibiliMirror([a,b],{}, {fetch,timeoutMs:15}),b);assert.equal(aborted,true);
});
test('all failures are reported and no known-bad first URL is returned',async()=>{
 await assert.rejects(selectBilibiliMirror([a,b],{}, {fetch:async()=>new Response('denied',{status:403})}),e=>e.code==='unavailable'&&/镜像/.test(e.message));
});
test('signed query variants remain intact, candidates are bounded and permissions are not broadened',async()=>{
 const calls=[];await assert.rejects(selectBilibiliMirror([a,a,a+'&variant=2',b,'https://third.bilivideo.com/f'],{}, {fetch:async u=>{calls.push(u);return new Response('',{status:403});}}));assert.deepEqual(calls,[a,a+'&variant=2',b]);
 const unknown='https://outside.example/media';assert.equal(await selectBilibiliMirror([unknown],{}, {fetch:async()=>{throw Error('unexpected network');}}),unknown);
});
