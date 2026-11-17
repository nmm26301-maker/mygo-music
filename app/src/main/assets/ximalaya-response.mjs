// Deliberately narrow: a platform-declared regional copyright rejection, not
// an inferred account failure. Never expose raw response text or signed URLs.
export function ximalayaResponseError(body) {
  const message = typeof body?.msg === 'string' ? body.msg : typeof body?.message === 'string' ? body.message : '';
  if (Number(body?.ret)!==927 || !/版权/.test(message) || !/地区|国家/.test(message)) return null;
  return Object.assign(new Error('喜马拉雅提示该资源受所在地区版权限制，请在官方应用确认当前地区是否可播放'), {code:'unavailable'});
}
