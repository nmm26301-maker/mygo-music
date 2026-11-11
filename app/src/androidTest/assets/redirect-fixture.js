export async function login({port, timeout = 5000}) {
  const reply = await host.waitForRedirect({port, path:'/callback', timeout, page:'complete'});
  host.storage.set('oauth', reply); return reply;
}
export async function failure({port, timeout = 100}) {
  try { await login({port, timeout}); return 'unexpected'; }
  catch(error) { return error.message; }
}
export function saved() { return host.storage.get('oauth'); }
