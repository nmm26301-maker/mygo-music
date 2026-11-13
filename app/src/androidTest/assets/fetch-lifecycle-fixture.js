export async function probe({url}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 1800);
  try {
    const response = await fetch(url + '/probe', {headers:{Range:'bytes=0-0'}, signal:controller.signal});
    controller.abort();
    let aborted = false, abortName = "", abortMessage = "";
    try { await response.text(); } catch (error) { abortName = error.name; abortMessage = error.message; aborted = error.name === 'AbortError'; }
    return {status:response.status, length:response.headers.get('content-length'), aborted, abortName, abortMessage};
  } finally { clearTimeout(timer); }
}
let pendingAbort;
export function abort() { pendingAbort?.abort(); }
export async function cancel({url, path}) {
  const controller = new AbortController();
  pendingAbort = controller;
  try {
    const response = await fetch(url + path, {signal:controller.signal});
    const body = response.text();
    host.storage.set('body-started', true);
    await body;
    return 'not cancelled';
  } catch (error) { return error.name; }
  finally { pendingAbort = undefined; }
}
export async function body({url}) {
  const response = await fetch(url + '/json');
  const before = response.bodyUsed;
  const value = await response.json();
  let twice = false;
  try { await response.text(); } catch (error) { twice = error.name === 'TypeError'; }
  const binary = await fetch(url + '/binary');
  return {before, after:response.bodyUsed, twice, value, binary:[...new Uint8Array(await binary.arrayBuffer())]};
}
export async function oversized({url}) {
  try { await (await fetch(url + '/oversized')).text(); return false; }
  catch (error) { return error.message; }
}
export async function held({url}) { await fetch(url + '/held'); return true; }
export function search({query}) {
  const count = (host.storage.get(query) || 0) + 1;
  host.storage.set(query, count);
  if (query !== 'recover' || count < 3) throw new Error('QQ Music DoSearchForQQMusicDesktop: 0/2001');
  return {tracks:[{id:'recovered', name:'recovered'}], count};
}
export function like() {
  host.storage.set('writes', (host.storage.get('writes') || 0) + 1);
  throw new Error('QQ Music DoSearchForQQMusicDesktop: 0/2001');
}
export function counts() { return {permanent:host.storage.get('permanent'), writes:host.storage.get('writes')}; }
