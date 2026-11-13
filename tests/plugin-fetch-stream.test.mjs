import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import fs from 'node:fs/promises';
import {createHash} from 'node:crypto';
const source=await fs.readFile(new URL('../app/src/main/assets/plugin-host.js',import.meta.url),'utf8');
const network=source.slice(source.indexOf('  const requests = new Map();'),source.indexOf('  const hostTasks = new Map();'));
function environment(routes={},legacy=false,ResponseType=Response){
 const active=new Map(),cancelled=[],reads=[];let ctx;
 const emit=(id,phase,value)=>ctx.__networkResult(id,phase,JSON.stringify(value));
 const nativeHost={request(id,url,raw){const route=routes[url]||{};active.set(id,{route,chunks:[...(route.chunks||[])]});if(!route.holdHeaders)queueMicrotask(()=>emit(id,'headers',{status:route.status||200,url:route.finalURL||url,redirected:!!route.finalURL,statusText:'OK',headers:{'content-type':'application/json'},cookies:['session=fixture; HttpOnly']}));},
  readResponseChunk(id){reads.push(id);const state=active.get(id);if(!state||state.route.holdBody)return;queueMicrotask(()=>{if(!active.has(id))return;if(state.chunks.length)emit(id,'chunk',{done:false,base64:Buffer.from(state.chunks.shift()).toString('base64')});else{active.delete(id);emit(id,'chunk',{done:true,base64:''});}});},
  readResponse(id){const state=active.get(id);if(!state)return;const all=Buffer.concat(state.chunks.map(v=>Buffer.from(v)));active.delete(id);queueMicrotask(()=>emit(id,'body',{body:all.toString(),base64:all.toString('base64')}));},
  cancelResponse(id){cancelled.push(id);active.delete(id);}};
 ctx=vm.createContext({Response:legacy?undefined:ResponseType,ReadableStream:legacy?undefined:ReadableStream,Headers,DOMException,Uint8Array,atob,Promise,nativeHost});ctx.window=ctx;vm.runInContext(`(()=>{${network}})()`,ctx);
 return {fetch:ctx.fetch,active,cancelled,reads};
}
test('headers stay lazy; standard reader delivers byte chunks, locks and consumes once',async()=>{
 const env=environment({'https://fixture/json':{chunks:[Buffer.from('{"文'),Buffer.from('字":42}') ]}});const r=await env.fetch('https://fixture/json');
 assert.equal(env.reads.length,0);assert.equal(r.bodyUsed,false);const reader=r.body.getReader();assert.equal(r.bodyUsed,false);await assert.rejects(r.text(),/Body|body/);
 const first=await reader.read();assert.equal(r.bodyUsed,true);assert.equal(Buffer.from(first.value).toString(),'{"文');const second=await reader.read();assert.equal(Buffer.from(second.value).toString(),'字":42}');assert.equal((await reader.read()).done,true);reader.releaseLock();await assert.rejects(r.json(),/Body|body/);assert.equal(env.active.size,0);
});
test('text, JSON, binary and clone share the one native connection while metadata survives',async()=>{
 const bytes=Buffer.from('{"text":"逐字歌词"}');const env=environment({'https://fixture/data':{chunks:[bytes.subarray(0,11),bytes.subarray(11)],finalURL:'https://fixture/final'}});
 const r=await env.fetch('https://fixture/data');const copy=r.clone();const [json,binary]=await Promise.all([r.json(),copy.arrayBuffer()]);assert.equal(json.text,'逐字歌词');assert.deepEqual(Buffer.from(binary),bytes);assert.equal(copy.url,'https://fixture/final');assert.equal(copy.redirected,true);assert.deepEqual(Array.from(copy.headers.getSetCookie()),['session=fixture; HttpOnly']);assert.equal(env.reads.length,3);assert.equal(env.active.size,0);
});
test('reader cancel stops a pending read and unread body cancel never pulls',async()=>{
 const env=environment({'https://fixture/held':{holdBody:true}});const r=await env.fetch('https://fixture/held');await r.body.cancel();assert.equal(env.reads.length,0);assert.equal(env.active.size,0);
 const next=await env.fetch('https://fixture/held');const reader=next.body.getReader();const pending=reader.read();await Promise.resolve();await reader.cancel();assert.equal((await pending).done,true);assert.equal(env.active.size,0);
});
test('abort rejects waiting headers and active reader with AbortError',async()=>{
 const env=environment({'https://fixture/headers':{holdHeaders:true},'https://fixture/body':{holdBody:true}});const first=new AbortController();const waiting=env.fetch('https://fixture/headers',{signal:first.signal});first.abort();await assert.rejects(waiting,{name:'AbortError'});
 const second=new AbortController();const r=await env.fetch('https://fixture/body',{signal:second.signal});const reader=r.body.getReader();const pending=reader.read();second.abort();await assert.rejects(pending,{name:'AbortError'});assert.equal(env.active.size,0);
});
test('HEAD and 204 have null bodies and release the native connection',async()=>{
 const env=environment({'https://fixture/empty':{status:204}});const head=await env.fetch('https://fixture/head',{method:'HEAD'});const empty=await env.fetch('https://fixture/empty');assert.equal(head.body,null);assert.equal(empty.body,null);assert.equal(await empty.text(),'');assert.equal(env.active.size,0);
});
test('body consumers keep abort reasons on older WebViews while locked and consumed bodies retain TypeError',async()=>{
 class OldResponse extends Response {text(){return super.text().catch(()=>{throw new TypeError('Failed to fetch');});}}
 const env=environment({'https://fixture/held':{holdBody:true}},false,OldResponse);
 const first=new AbortController(),r=await env.fetch('https://fixture/held',{signal:first.signal});first.abort();await assert.rejects(r.text(),{name:'AbortError'});await assert.rejects(r.text(),{name:'TypeError'});
 const second=new AbortController(),pendingResponse=await env.fetch('https://fixture/held',{signal:second.signal}),pending=pendingResponse.text();second.abort();await assert.rejects(pending,{name:'AbortError'});
 const third=new AbortController(),locked=await env.fetch('https://fixture/held',{signal:third.signal}),reader=locked.body.getReader();third.abort();await assert.rejects(locked.text(),{name:'TypeError'});reader.releaseLock();await assert.rejects(locked.text(),{name:'AbortError'});assert.equal(env.active.size,0);
});

test('older WebViews retain lazy bounded-body behavior without native stream primitives',async()=>{
 const env=environment({'https://fixture/legacy':{chunks:['{"ok":true}']}},true);const response=await env.fetch('https://fixture/legacy');assert.equal(env.active.size,1);assert.equal(response.bodyUsed,false);assert.equal((await response.json()).ok,true);assert.equal(response.bodyUsed,true);await assert.rejects(response.text(),/Body/);assert.equal(env.active.size,0);
});
test('exact original Qishui JSON consumer accepts chunked UTF-8 and retains account/verification errors',async()=>{
 const original=await fs.readFile(new URL('../app/src/androidTest/assets/provider-fixes/qishui-original.js',import.meta.url));assert.equal(createHash('sha256').update(original).digest('hex'),'d004a53dc1e6a63546919cbb92a662121dd6691b5cfe296d54bd6f7ab8cbe1c5');
 const saved=globalThis.host;globalThis.host={platform:'android',setting:()=>'',storage:{get:()=>undefined,set(){},remove(){}},crypto:{md5:value=>createHash('md5').update(value).digest('hex')}};
 try{const plugin=await import('data:text/javascript;base64,'+Buffer.concat([original,Buffer.from('\nexport const __originalFetchJSON=fetchJSON;\n')]).toString('base64'));
  const data=Buffer.from('{"status_code":0,"data":{"title":"原版目录"}}');const env=environment({'https://fixture/catalog':{chunks:[data.subarray(0,34),data.subarray(34)]},'https://fixture/login':{status:401,chunks:['{}']},'https://fixture/verify':{chunks:['{"status_code":2046}']}});
  const body=await plugin.__originalFetchJSON('https://fixture/catalog',{},env.fetch);assert.equal(body.data.title,'原版目录');await assert.rejects(plugin.__originalFetchJSON('https://fixture/login',{},env.fetch),error=>error.kind==='session');await assert.rejects(plugin.__originalFetchJSON('https://fixture/verify',{},env.fetch),error=>error.kind==='verify');await assert.rejects(plugin.__originalFetchJSON('https://fixture/catalog',{},env.fetch,8),error=>error.kind==='response');assert.equal(env.active.size,0);
 }finally{globalThis.host=saved;}
});
