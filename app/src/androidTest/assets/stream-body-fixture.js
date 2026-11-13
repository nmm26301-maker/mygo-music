let controller, held;
export async function prefix({url}) {
 const response=await fetch(url+'/prefix');const reader=response.body.getReader();const began=Date.now();const part=await reader.read();await reader.cancel();reader.releaseLock();return {bytes:part.value.length,time:Date.now()-began,used:response.bodyUsed};
}
export async function utf8({url}) {
 const response=await fetch(url+'/utf8');const reader=response.body.getReader();const decoder=new TextDecoder();let body='',count=0;
 for(;;){const next=await reader.read();if(next.done)break;body+=decoder.decode(next.value,{stream:true});if(++count===1)host.storage.set('first-part',true);}body+=decoder.decode();reader.releaseLock();return {value:JSON.parse(body),chunks:count};
}
export async function unread({url}) {const response=await fetch(url+'/unread');await response.body.cancel();return {used:response.bodyUsed};}
export async function pending({url}) {controller=new AbortController();const response=await fetch(url+'/pending',{signal:controller.signal});const reader=response.body.getReader();const read=reader.read();host.storage.set('stream-waiting',true);try{await read;return 'unexpected';}catch(error){return error.name;}finally{reader.releaseLock();}}
export function abortStream(){controller.abort();}
export async function heldStream({url}) {held=await fetch(url+'/held-stream');const reader=held.body.getReader();await reader.read();reader.releaseLock();return held.bodyUsed;}
export async function primitives({url}) {
 const response=await fetch(url+'/json');const clone=response.clone();const [value,text]=await Promise.all([response.json(),clone.text()]);let twice=false;try{await response.text();}catch(error){twice=error instanceof TypeError;}
 const local=await fetch(new URL('./stream-local.json',import.meta.url));const reader=local.body.getReader();const first=await reader.read();await reader.cancel();reader.releaseLock();const head=await fetch(url+'/json',{method:'HEAD'});
 return {value,text,twice,cookie:clone.headers.getSetCookie()[0],url:clone.url,local:new TextDecoder().decode(first.value),head:head.body===null};
}

export async function cookies({url,cookie}){const res=await fetch(url+'/cookie',{headers:cookie?{Cookie:cookie}:{}});return res.json();}
