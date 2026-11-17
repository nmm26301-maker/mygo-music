// Android media mirror check. Uses only addresses returned by the original source.
// The original Bilibili plugin and its MIT notice remain installed unchanged.
const allowed = /\.bilivideo\.(com|cn)$/i;
const unavailable = () => Object.assign(new Error('B站音频镜像暂时无法读取，请稍后重试或切换音源'), {code:'unavailable'});

// Android decodes these MPEG-4 profiles in the shared native player. Other
// codecs retain the original plugin path; a container label is not a codec.
export async function androidBilibiliHeAac(audio, tier, headers, options) {
  const profile = /^mp4a\.40\.(5|29)$/i.exec(audio?.codec || '');
  if (audio?.as !== 'he' || !profile) return null;
  const quality = tier === 'standard' ? 'standard' : 'high';
  return {url:await selectBilibiliMirror(audio.urls,headers,options),type:'m4a',
    quality,sourceQuality:quality,sourceCodec:profile[1]==='29'?'he-aac-v2':'he-aac',
    bitRate:audio.bandwidth,sourceBitRateUnit:'bps',headers};
}

function candidate(value) {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && !url.username && !url.password && (!url.port || url.port === '443') && allowed.test(url.hostname);
  } catch { return false; }
}

async function playable(response) {
  if (response.status !== 200 && response.status !== 206) return false;
  if (/text\/|json|html/i.test(response.headers.get('Content-Type') || '')) return false;
  const length = response.headers.get('Content-Length');
  if (length !== null && Number(length) >= 0 && Number(length) < 12) return false;
  if (response.status === 206) {
    const range = /^bytes\s+0-(\d+)\/(\d+)$/i.exec((response.headers.get('Content-Range') || '').trim());
    if (!range || Number(range[1]) < 11 || Number(range[2]) <= Number(range[1])) return false;
  }
  if (!response.body || typeof response.body.getReader !== 'function') return false;
  const reader = response.body.getReader();
  try {
    const prefix = new Uint8Array(12);let count = 0;
    while (count < prefix.length) {
      const part = await reader.read();if (part.done) return false;
      if (!part.value || !part.value.length) continue;
      const take = Math.min(part.value.length, prefix.length - count);
      prefix.set(part.value.subarray(0,take),count);count += take;
    }
    return (prefix[4]===102 && prefix[5]===116 && prefix[6]===121 && prefix[7]===112) ||
      (prefix[0]===102 && prefix[1]===76 && prefix[2]===97 && prefix[3]===67);
  } finally {
    // Cancel the actual native response, including a server that ignored Range.
    try { await reader.cancel(); } catch {}
    try { reader.releaseLock(); } catch {}
  }
}

export async function selectBilibiliMirror(urls, headers, {fetch: request=globalThis.fetch, timeoutMs=3000}={}) {
  if (!Array.isArray(urls) || !urls.length) throw unavailable();
  const candidates = [...new Set(urls.filter(candidate))].slice(0,3);
  // Undeclared CDN hosts retain the original native-media path; no extra plugin permission is granted.
  if (!candidates.length) return urls[0];
  const mediaHeaders = Object.fromEntries(Object.entries(headers || {}).filter(([key])=>key.toLowerCase()!=='range'));
  for (const url of candidates) {
    const controller = new AbortController();
    const timer = setTimeout(()=>controller.abort(),timeoutMs);
    let response;
    try {
      response = await request(url,{headers:{...mediaHeaders,Range:'bytes=0-'},signal:controller.signal});
      if (await playable(response)) return url;
    } catch {} finally {
      clearTimeout(timer);controller.abort();
      if (response?.body && !response.body.locked) try { await response.body.cancel(); } catch {}
    }
  }
  throw unavailable();
}
