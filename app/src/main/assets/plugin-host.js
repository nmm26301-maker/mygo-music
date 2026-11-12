// Android implementation of MyGo's documented host interface.
(() => {
  const requests = new Map(); let seq = 0;
  window.__networkResult = (id, phase, raw) => {
    const task = requests.get(id); if (!task) return;
    try {
      const result = JSON.parse(raw);
      if (phase === 'error') throw new Error(result.error || 'Network request failed');
      if (phase === 'headers') {
        task.headersDone = true;
        const headers = new Headers(result.headers || {});
        headers.getSetCookie = () => result.cookies || [];
        const consume = async () => {
          if (task.bodyUsed) throw new TypeError('Body has already been consumed');
          task.bodyUsed = true;
          if (task.error) throw task.error;
          return new Promise((resolve, reject) => {
            task.bodyResolve = resolve; task.bodyReject = reject;
            nativeHost.readResponse(id);
          });
        };
        task.resolve({ok:result.status >= 200 && result.status < 300, status:result.status,
          url:result.url, headers, get bodyUsed(){return !!task.bodyUsed;},
          text:async () => (await consume()).body || '',
          json:async () => JSON.parse((await consume()).body || ''),
          arrayBuffer:async () => Uint8Array.from(atob((await consume()).base64 || ''), c => c.charCodeAt(0)).buffer});
      } else if (phase === 'body') {
        requests.delete(id); task.cleanup(); task.bodyResolve?.(result);
      }
    } catch (error) {
      task.error = error; requests.delete(id); task.cleanup();
      if (!task.headersDone) task.reject(error); else task.bodyReject?.(error);
    }
  };
  window.fetch = (input, init = {}) => new Promise((resolve, reject) => {
    const id = String(++seq), signal = init.signal;
    const task = {resolve, reject, cleanup:() => signal?.removeEventListener('abort', abort)};
    const abort = () => {
      const error = new DOMException('Aborted', 'AbortError'); task.error = error;
      requests.delete(id); task.cleanup();
      if (!task.headersDone) reject(error); else task.bodyReject?.(error);
      try { nativeHost.cancelResponse(id); } catch (_) {}
    };
    if (signal?.aborted) { abort(); return; }
    const headers = {}; new Headers(init.headers || {}).forEach((v, k) => headers[k] = v);
    requests.set(id, task);
    signal?.addEventListener('abort', abort, {once:true});
    nativeHost.request(id, String(input.url || input), JSON.stringify({method:init.method || 'GET',
      headers, body:init.body == null ? '' : String(init.body), redirect:init.redirect || 'follow'}));
  });
  const hostTasks = new Map(); let hostSequence = 0;
  window.__hostResult = (id, ok, raw) => {
    const task = hostTasks.get(id); if (!task) return; hostTasks.delete(id);
    if (ok) { try { task.resolve(JSON.parse(raw)); } catch(e) { task.reject(e); } }
    else task.reject(new Error(raw));
  };
  const hostTask = (method, ...args) => new Promise((resolve, reject) => {
    const id = String(++hostSequence); hostTasks.set(id, {resolve, reject});
    try { nativeHost[method](id, ...args); } catch(error) { hostTasks.delete(id); reject(error); }
  });
  const webSignIn = options => hostTask('webSignIn', JSON.stringify(options || {}));
  const nativeResult = raw => { const result = JSON.parse(raw); if (!result.ok) throw new Error(result.error); return result.value; };
  const spawn = (command, args = []) => {
    const info = nativeResult(nativeHost.processSpawn(String(command), JSON.stringify(args)));
    const sync = (method, value = '') => nativeResult(nativeHost.processSync(info.pid, method, String(value)));
    return {pid:info.pid, write:text=>sync('write', text), closeStdin:()=>sync('closeStdin'),
      readLine:()=>hostTask('processTask', info.pid, 'readLine'), wait:()=>hostTask('processTask', info.pid, 'wait'),
      kill:()=>sync('kill'), errorOutput:()=>sync('errorOutput')};
  };
  window.host = {
    platform:'android', arch:__pluginConfig.arch, dir:__pluginConfig.dir, dataDir:__pluginConfig.dir + '/data',
    storage:{get:k => {const v=nativeHost.storageGet(String(k)); return v === '' ? undefined : JSON.parse(v);},
      set:(k,v) => nativeHost.storageSet(String(k), JSON.stringify(v)), remove:k => nativeHost.storageRemove(String(k))},
    setting:k => nativeHost.setting(String(k)), language:()=>'zh-CN', t:x=>__pluginConfig.translations?.[x] || x,
    crypto:{md5:x=>nativeHost.md5(String(x)), aesEcbEncrypt:(k,x)=>nativeHost.aesEcbEncrypt(String(k),String(x))},
    openURL:u=>nativeHost.openURL(String(u)), webSignIn,
    spawn,
    waitForRedirect:options=>hostTask('waitForRedirect', JSON.stringify(options || {})),
    removeDataDir:name=>nativeResult(nativeHost.removeDataDir(String(name))), signInPrompt:x=>nativeHost.signInPrompt(JSON.stringify(x || {}))
  };
  window.__invoke = async (id, name, args) => {
    try {
      const fn = window.__plugin[name];
      if (typeof fn !== 'function') throw new Error('插件未提供接口：' + name);
      let value;
      for (let attempt = 0; ; attempt++) {
        try { value = await fn(args || {}); break; }
        catch (error) {
          // Retry only QQ's known transient read-only search error, never account/library writes.
          if (name !== 'search' || attempt >= 2 || !/^QQ Music DoSearchForQQMusicDesktop: 0\/2001$/.test(String(error?.message))) throw error;
          await new Promise(resolve => setTimeout(resolve, 300 * (attempt + 1)));
        }
      }
      nativeHost.complete(id, true, JSON.stringify(value === undefined ? null : value));
    } catch(e) { nativeHost.complete(id, false, JSON.stringify({__mygoError:true,code:String(e?.code || ''),message:String(e?.message || e)})); }
  };
  import(__pluginConfig.module).then(module => {
    window.__plugin = module;
    nativeHost.ready(true, JSON.stringify(Object.keys(module).filter(k=>typeof module[k]==='function')));
  }).catch(e => nativeHost.ready(false, '插件加载失败：' + String(e.message || e)));
})();
