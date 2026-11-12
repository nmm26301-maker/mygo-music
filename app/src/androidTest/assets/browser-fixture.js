const base = '__BASE__', label = '__LABEL__';
function record(method, args = {}) { host.storage.set('lastCall', {method, args}); }
function track(id) { return {id, name: label + ' ' + id, artists: [{id:'artist', name:'Browser artist'}], album:{id:'album', name:label+' album', imageUrl:base+'/cover.png'}}; }
function albumCard() { return {id:'album', name:label+' album', imageUrl:base+'/cover.png', saved:!!host.storage.get('saved')}; }
function artistCard() { return {id:'artist', name:label+' artist', imageUrl:base+'/cover.png', following:!!host.storage.get('following')}; }
function showItem() { return {id:'show', name:label+' podcast', imageUrl:base+'/cover.png', publisher:'Browser publisher', publisherPage:'publisher', subscribed:!!host.storage.get('subscribed')}; }
export function home() { record('home'); return {title:label+' source home', sections:[{title:'Albums', albums:[albumCard()]},{title:'Artists', artists:[artistCard()]},{title:'Podcasts', shows:[showItem()]},{title:'Stations', stations:[{id:'station', name:label+' station', imageUrl:base+'/cover.png'}]},{title:'Links', links:[{title:'Open custom page',page:'custom'}], morePage:'more'}]}; }
export function browse(args) { record('browse',args);return {title:label+' '+args.page,sections:[{title:'Custom music',tracks:[track('a'),track('b')]}]}; }
export async function search(args) {
  record('search', args);
  if(args.query==='slow') { await new Promise(r=>setTimeout(r,1200));return {tracks:[track('stale slow')],more:false}; }
  if(args.kind==='albums')return {albums:[albumCard()],more:false};
  if(args.kind==='artists')return {artists:[artistCard()],more:false};
  if(args.kind==='playlists')return {playlists:[{id:'list',name:label+' playlist',mine:true}],more:false};
  if(args.kind==='shows')return {shows:[showItem()],more:false};
  return {tracks:args.offset===0?[track('a'),track('b')]:args.offset===2?[track('b'),track('c')]:[],more:true};
}
export function album(args) { record('album',args);return {album:albumInfo(),tracks:[track('a'),track('b')]}; }
function albumInfo(){return {id:'album',name:label+' album',saved:!!host.storage.get('saved')};}
export function artist(args) {record('artist',args);return {artist:{id:'artist',name:label+' artist',following:!!host.storage.get('following')},tracks:[track('a')],albums:[albumInfo()]};}
export function saveAlbum(args) {record('saveAlbum',args);host.storage.set('saved',args.on);}
export function followArtist(args) {record('followArtist',args);host.storage.set('following',args.on);}
export function subscribeShow(args) {record('subscribeShow',args);host.storage.set('subscribed',args.on);}
export function savedAlbums(args) {record('savedAlbums',args);return {albums:host.storage.get('saved')?[albumInfo()]:[],more:false};}
export function liked(args) {record('liked',args);return {tracks:[track('a')],more:false};}
export function recent() {return [track('b')];}
export function playlists() {return [{id:'list',name:label+' playlist',mine:true}];}
export function playlist(args) {record('playlist',args);return {tracks:[track('a'),track('b')]};}
export function shows() {return [showItem()];}
export function show(args) {record('show',args);return {show:showItem(),episodes:[track(args.offset===0?'episode one':'episode two')],more:args.offset===0};}
export function stations() {return [{id:'station',name:label+' station'}];}
export function stationNext(args) {record('stationNext',args);return [track('a'),track('b')];}
export function stream(args) {record('stream',args);return {url:base+'/'+args.id,headers:{'X-Source-Token':'browser-audio'}};}
