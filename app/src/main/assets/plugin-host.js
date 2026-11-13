// Android implementation of MyGo's documented host interface.
(() => {
  const requests = new Map(); let seq = 0;
  const NativeResponse = window.Response, NativeStream = window.ReadableStream;
  const streamBodies = (() => { try { const probe = new NativeStream({start(c){c.close();}}, {highWaterMark:0}); return new NativeResponse(probe).body === probe; } catch (_) { return false; } })();
  const bytes = value => Uint8Array.from(atob(value || ''), c => c.charCodeAt(0));
  const failTask = (id, task, error) => {
    task.error = error; requests.delete(id); task.cleanup();
    if (!task.resolved) task.reject(error);
    else { try { task.controller?.error(error); } catch (_) {} task.pullReject?.(error); task.bodyReject?.(error); }
    task.pullResolve = task.pullReject = null;
    try { nativeHost.cancelResponse(id); } catch (_) {}
  };
  window.__networkResult = (id, phase, raw) => {
    const task = requests.get(id); if (!task) return;
    try {
      const result = JSON.parse(raw);
      if (phase === 'error') throw new Error(result.error || 'Network request failed');
      if (phase === 'headers') {
        task.headersDone = true;
        const headers = new Headers(result.headers || {});
        headers.getSetCookie = () => result.cookies || [];
        if (streamBodies) {
          const noBody = task.method === 'HEAD' || [204, 205, 304].includes(result.status);
          const stream = noBody ? null : new NativeStream({
            start(controller) { task.controller = controller; },
            pull() {
              if (task.error) return Promise.reject(task.error);
              return new Promise((resolve, reject) => {
                task.pullResolve = resolve; task.pullReject = reject;
                try { nativeHost.readResponseChunk(id); } catch (error) { failTask(id, task, error); }
              });
            },
            cancel() {
              requests.delete(id); task.cleanup(); task.pullResolve?.();
              task.pullResolve = task.pullReject = null;
              try { nativeHost.cancelResponse(id); } catch (_) {}
            }
          }, {highWaterMark: 0});
          const response = new NativeResponse(stream, {status:result.status, statusText:result.statusText || '', headers});
          const decorate = value => {
            Object.defineProperty(value, 'url', {value:result.url});
            Object.defineProperty(value, 'redirected', {value:!!result.redirected});
            value.headers.getSetCookie = () => result.cookies || [];
            const clone = value.clone.bind(value); value.clone = () => decorate(clone());
            return value;
          };
          const resultResponse = decorate(response); task.resolved = true; task.resolve(resultResponse);
          if (noBody) { requests.delete(id); task.cleanup(); nativeHost.cancelResponse(id); }
        } else {
          // Preserve the bounded text/binary path on old system WebViews without stream primitives.
          const consume = async () => {
            if (task.bodyUsed) throw new TypeError('Body has already been consumed');
            task.bodyUsed = true; if (task.error) throw task.error;
            return new Promise((resolve, reject) => { task.bodyResolve = resolve; task.bodyReject = reject; nativeHost.readResponse(id); });
          };
          task.resolved = true;
          task.resolve({ok:result.status >= 200 && result.status < 300, status:result.status, url:result.url, headers,
            get bodyUsed(){return !!task.bodyUsed;}, text:async () => (await consume()).body || '',
            json:async () => JSON.parse((await consume()).body || ''), arrayBuffer:async () => bytes((await consume()).base64).buffer});
        }
      } else if (phase === 'chunk') {
        const resolve = task.pullResolve; task.pullResolve = task.pullReject = null;
        if (result.done) { task.controller.close(); requests.delete(id); task.cleanup(); }
        else task.controller.enqueue(bytes(result.base64));
        resolve?.();
      } else if (phase === 'body') { requests.delete(id); task.cleanup(); task.bodyResolve?.(result); }
    } catch (error) { failTask(id, task, error); }
  };
  window.fetch = (input, init = {}) => new Promise((resolve, reject) => {
    const id = String(++seq), signal = init.signal;
    const method = String(init.method || 'GET').toUpperCase();
    const task = {resolve, reject, method, cleanup:() => signal?.removeEventListener('abort', abort)};
    const abort = () => failTask(id, task, new DOMException('Aborted', 'AbortError'));
    if (signal?.aborted) { abort(); return; }
    const headers = {}; new Headers(init.headers || {}).forEach((v, k) => headers[k] = v);
    requests.set(id, task); signal?.addEventListener('abort', abort, {once:true});
    try { nativeHost.request(id, String(input.url || input), JSON.stringify({method, headers, body:init.body == null ? '' : String(init.body), redirect:init.redirect || 'follow'})); }
    catch (error) { failTask(id, task, error); }
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
  window.__compilePluginModule = name => {
    try {
      if (!window.Babel) throw new Error('TypeScript 编译器未加载');
      const output = Babel.transform(nativeHost.typescriptSource(name), {filename:name, presets:[['typescript',{allowDeclareFields:true}]], babelrc:false, configFile:false, sourceMaps:false}).code;
      nativeHost.typescriptComplete(name, output, '');
    } catch (error) { nativeHost.typescriptComplete(name, '', String(error?.message || error)); }
  };
  import(__pluginConfig.module).then(module => {
    window.__plugin = module;
    nativeHost.ready(true, JSON.stringify(Object.keys(module).filter(k=>typeof module[k]==='function')));
  }).catch(e => nativeHost.ready(false, '插件加载失败：' + String(e.message || e)));
})();
