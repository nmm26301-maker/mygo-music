// Device-registration protocol adapted from bitbyte404/mygo-music-kugou-lite,
// commit b964ac03274163d3bf64bb530a452332be244635, with MakcRe MIT attribution.
// Full MIT text is bundled in third-party/Kugou-Device-MIT.txt.
export async function registerConceptDevice(config){
const auth=config.auth,deviceGuid=config.deviceGuid,mid=config.mid,fetch=config.fetch;
const APP_ID=3116,CLIENT_VER=11440,UA='Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi';
const md5=s=>host.crypto.md5(String(s));const now=()=>Math.floor(Date.now()/1000);
const androidSign=(params,body)=>md5('LnT6xpN3khm36zse0QzvmgTZ3waWdRSA'+Object.keys(params).sort().map(k=>k+'='+params[k]).join('')+(body||'')+'LnT6xpN3khm36zse0QzvmgTZ3waWdRSA');
const SBOX = new Uint8Array(256);
const INV = new Uint8Array(256);
(() => {
  let p = 1, q = 1;
  do {
    p = p ^ ((p << 1) & 0xff) ^ (p & 0x80 ? 0x1b : 0);
    q ^= q << 1; q ^= q << 2; q ^= q << 4; q &= 0xff;
    if (q & 0x80) q ^= 0x09;
    const x = q ^ ((q << 1 | q >> 7) & 0xff) ^ ((q << 2 | q >> 6) & 0xff) ^ ((q << 3 | q >> 5) & 0xff) ^ ((q << 4 | q >> 4) & 0xff) ^ 0x63;
    SBOX[p] = x; INV[x] = p;
  } while (p !== 1);
  SBOX[0] = 0x63; INV[0x63] = 0;
})();
const xt = (a) => ((a << 1) ^ (a & 0x80 ? 0x1b : 0)) & 0xff;
const mul = (a, b) => { let r = 0; while (b) { if (b & 1) r ^= a; a = xt(a); b >>= 1; } return r; };

function expandKey(key) {
  const w = new Uint8Array(176);
  w.set(key);
  let rcon = 1;
  for (let i = 16; i < 176; i += 4) {
    let t = w.slice(i - 4, i);
    if (i % 16 === 0) {
      t = new Uint8Array([SBOX[t[1]] ^ rcon, SBOX[t[2]], SBOX[t[3]], SBOX[t[0]]]);
      rcon = xt(rcon);
    }
    for (let j = 0; j < 4; j++) w[i + j] = w[i - 16 + j] ^ t[j];
  }
  return w;
}

function encBlock(s, w) {
  for (let i = 0; i < 16; i++) s[i] ^= w[i];
  for (let r = 1; r <= 10; r++) {
    for (let i = 0; i < 16; i++) s[i] = SBOX[s[i]];
    const t = s.slice();
    for (let c = 0; c < 4; c++) for (let row = 0; row < 4; row++) s[c * 4 + row] = t[((c + row) % 4) * 4 + row];
    if (r < 10) for (let c = 0; c < 4; c++) {
      const [a0, a1, a2, a3] = s.slice(c * 4, c * 4 + 4);
      s[c * 4] = xt(a0) ^ xt(a1) ^ a1 ^ a2 ^ a3;
      s[c * 4 + 1] = a0 ^ xt(a1) ^ xt(a2) ^ a2 ^ a3;
      s[c * 4 + 2] = a0 ^ a1 ^ xt(a2) ^ xt(a3) ^ a3;
      s[c * 4 + 3] = xt(a0) ^ a0 ^ a1 ^ a2 ^ xt(a3);
    }
    for (let i = 0; i < 16; i++) s[i] ^= w[r * 16 + i];
  }
}

function decBlock(s, w) {
  for (let i = 0; i < 16; i++) s[i] ^= w[160 + i];
  for (let r = 9; r >= 0; r--) {
    const t = s.slice();
    for (let c = 0; c < 4; c++) for (let row = 0; row < 4; row++) s[((c + row) % 4) * 4 + row] = t[c * 4 + row];
    for (let i = 0; i < 16; i++) s[i] = INV[s[i]];
    for (let i = 0; i < 16; i++) s[i] ^= w[r * 16 + i];
    if (r > 0) for (let c = 0; c < 4; c++) {
      const [a0, a1, a2, a3] = s.slice(c * 4, c * 4 + 4);
      s[c * 4] = mul(a0, 14) ^ mul(a1, 11) ^ mul(a2, 13) ^ mul(a3, 9);
      s[c * 4 + 1] = mul(a0, 9) ^ mul(a1, 14) ^ mul(a2, 11) ^ mul(a3, 13);
      s[c * 4 + 2] = mul(a0, 13) ^ mul(a1, 9) ^ mul(a2, 14) ^ mul(a3, 11);
      s[c * 4 + 3] = mul(a0, 11) ^ mul(a1, 13) ^ mul(a2, 9) ^ mul(a3, 14);
    }
  }
}

const utf8 = (s) => new TextEncoder().encode(s);
function cbcEncrypt(data, key, iv) {
  const w = expandKey(key);
  const pad = 16 - (data.length % 16);
  const buf = new Uint8Array(data.length + pad);
  buf.set(data);
  buf.fill(pad, data.length);
  let prev = iv;
  for (let i = 0; i < buf.length; i += 16) {
    const s = buf.subarray(i, i + 16);
    for (let j = 0; j < 16; j++) s[j] ^= prev[j];
    encBlock(s, w);
    prev = s;
  }
  return buf;
}
function cbcDecrypt(data, key, iv) {
  const w = expandKey(key);
  const out = new Uint8Array(data.length);
  let prev = iv;
  for (let i = 0; i < data.length; i += 16) {
    const s = data.slice(i, i + 16);
    decBlock(s, w);
    for (let j = 0; j < 16; j++) out[i + j] = s[j] ^ prev[j];
    prev = data.subarray(i, i + 16);
  }
  return out.subarray(0, out.length - (out[out.length - 1] || 0));
}
const b64 = (bytes) => { let s = ""; for (const b of bytes) s += String.fromCharCode(b); return btoa(s); };

// The Concept app's RSA public key, as its modulus and exponent.
const RSA_N = BigInt("0xc40a2d0da76511f3bb1cc2bbd3afbd8bea83b4d6b05b6c13eb8920c53f1af7679b32ba0d0edb843240ef1b836efed3ee240734c14c1399fd6594d16af22f52525d14d72e0155c6dcc8638d4f7bb94f3a0b1f4c29f991972f2a160a25eb0a9e724336be7f69bbd319ffab1c6dd8470b021dc434f3faba89f4a2a01b33bdbdd08b");
function modpow(b, e, m) {
  let r = 1n;
  b %= m;
  while (e > 0n) { if (e & 1n) r = (r * b) % m; b = (b * b) % m; e >>= 1n; }
  return r;
}
// RSAES-PKCS1-v1_5, in hex.
function rsaPkcs1(text) {
  const m = utf8(text);if(m.length>117)throw new Error("酷狗设备注册凭据过长");
  const k = 128;
  const block = new Uint8Array(k);
  block[1] = 2;
  for (let i = 2; i < k - m.length - 1; i++) block[i] = 1 + Math.floor(Math.random() * 255);
  block.set(m, k - m.length);
  let x = 0n;
  for (const b of block) x = (x << 8n) | BigInt(b);
  return modpow(x, 65537n, RSA_N).toString(16).padStart(256, "0");
}

// Registers this device with Kugou, for the dfid it then plays to.
async function registerDevice() {
  const a = auth();
  const guid = deviceGuid();
  const info = {
    availableRamSize: 4983533568, availableRomSize: 48114719, availableSDSize: 48114717, basebandVer: "", batteryLevel: 100, batteryStatus: 3,
    brand: "Redmi", buildSerial: "unknown", device: "marble", imei: guid, imsi: "", manufacturer: "Xiaomi", uuid: guid,
    accelerometer: false, accelerometerValue: "", gravity: false, gravityValue: "", gyroscope: false, gyroscopeValue: "", light: false, lightValue: "",
    magnetic: false, magneticValue: "", orientation: false, orientationValue: "", pressure: false, pressureValue: "",
    step_counter: false, step_counterValue: "", temperature: false, temperatureValue: "",
  };
  const k6 = Array.from({ length: 6 }, () => "abcdefghijklmnopqrstuvwxyz0123456789"[Math.floor(Math.random() * 36)]).join("");
  const h = md5(k6);
  const key = utf8(h.slice(0, 16)), iv = utf8(h.slice(16, 32));
  const body = b64(cbcEncrypt(utf8(JSON.stringify(info)), key, iv));
  const p = rsaPkcs1(JSON.stringify({ aes: k6, uid: Number(a.userid || 0), token: a.token || "" }));
  const params = { dfid: "-", mid: mid(), uuid: "-", appid: APP_ID, clientver: CLIENT_VER, clienttime: now(), part: 1, platid: 1, p };
  if (a.token) params.token = a.token;
  if (a.userid) params.userid = a.userid;
  params.signature = androidSign(params, body);
  const qs = Object.keys(params).map((k) => `${k}=${encodeURIComponent(params[k])}`).join("&");
  const res = await fetch(`https://userservice.kugou.com/risk/v3/r_register_dev?${qs}`, {
    method: "POST",
    headers: { "User-Agent": UA, dfid: "-", mid: params.mid, clienttime: String(params.clienttime), "Content-Type": "application/json" },
    body,
  });
  const bytes = new Uint8Array(await res.arrayBuffer());
  const out = JSON.parse(new TextDecoder().decode(cbcDecrypt(bytes, key, iv)));
  if (Number(out.status) !== 1 || !out.data?.dfid) throw new Error(`register_dev: ${JSON.stringify(out).slice(0, 120)}`);
  return String(out.data.dfid);
}


return registerDevice();
}
