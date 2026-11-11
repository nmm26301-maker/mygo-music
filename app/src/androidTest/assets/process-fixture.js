export function denied({command = '/system/bin/sh'}) {
  try { const process = host.spawn(command, []); process.kill(); return 'unexpected'; }
  catch(error) { return error.message; }
}
export async function run() {
  const process = host.spawn('/system/bin/sh', ['-c', 'read line; printf "%s\\n" "$line"; printf "diagnostic\\n" >&2; exit 7']);
  process.write('bridge-value\n'); process.closeStdin();
  const line = await process.readLine(), eof = await process.readLine(), code = await process.wait();
  return {pid:process.pid, line, eof, code, error:process.errorOutput()};
}
export function hold() {
  return host.spawn('/system/bin/sh', ['-c', 'while read line; do :; done']).pid;
}
export async function local({url}) { return (await fetch(url)).text(); }
export async function remoteDenied() { try { await fetch('https://example.com/'); return 'unexpected'; } catch(error) { return error.message; } }
export function remove({name}) { try { host.removeDataDir(name); return true; } catch(error) { return error.message; } }
