// Android implementation of MyGo's documented host interface.
(() => {
  const requests = new Map(); let seq = 0;
  window.__networkResult = (id, raw) => {
    const task = requests.get(id); if (!task) return;
    requests.delete(id); task.cleanup();
    try {
      const result = JSON.parse(raw);
      if (result.error) throw new Error(result.error);
      const headers = new Headers(result.headers || {});
      headers.getSetCookie = () => result.cookies || [];
      // Return the header object directly: Response copies headers and loses getSetCookie.
      task.resolve({ok: result.status >= 200 && result.status < 300, status: result.status,
        url: result.url, headers, text: async () => result.body || '',
        json: async () => JSON.parse(result.body || ''),
        arrayBuffer: async () => Uint8Array.from(atob(result.base64 || ''), c => c.charCodeAt(0)).buffer});
    } catch (e) { task.reject(e); }
  };
  window.fetch = (input, init = {}) => new Promise((resolve, reject) => {
    const id = String(++seq), signal = init.signal;
    const abort = () => { requests.delete(id); reject(new DOMException('Aborted', 'AbortError')); };
    if (signal?.aborted) { abort(); return; }
    const headers = {}; new Headers(init.headers || {}).forEach((v, k) => headers[k] = v);
    requests.set(id, {resolve, reject, cleanup: () => signal?.removeEventListener('abort', abort)});
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
      const value = await fn(args || {});
      nativeHost.complete(id, true, JSON.stringify(value === undefined ? null : value));
    } catch(e) { nativeHost.complete(id, false, JSON.stringify({__mygoError:true,code:String(e?.code || ''),message:String(e?.message || e)})); }
  };
  import(__pluginConfig.module).then(module => {
    window.__plugin = module;
    nativeHost.ready(true, JSON.stringify(Object.keys(module).filter(k=>typeof module[k]==='function')));
  }).catch(e => nativeHost.ready(false, '插件加载失败：' + String(e.message || e)));
})();
