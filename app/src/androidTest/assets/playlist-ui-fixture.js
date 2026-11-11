function lists(){return host.storage.get('lists') || [{id:'owned',name:'Owned',mine:true,tracks:[{id:'a',name:'Track A'},{id:'b',name:'Track B'}]},{id:'fixed',name:'Fixed',mine:true,fixed:true,tracks:[]}];}
function save(all,method,args){host.storage.set('lists',all);host.storage.set('lastCall',{method,args});}
export function playlists(){return lists();}
export function playlist({id}){return lists().find(item=>item.id===id);}
export function createPlaylist(args){const all=lists();const created={id:'created',name:args.name,mine:true,tracks:[]};all.push(created);save(all,'createPlaylist',args);return created;}
export function renamePlaylist(args){const all=lists();all.find(item=>item.id===args.id).name=args.name;save(all,'renamePlaylist',args);}
export function deletePlaylist(args){save(lists().filter(item=>item.id!==args.id),'deletePlaylist',args);}
export function removeFromPlaylist(args){const all=lists();const item=all.find(item=>item.id===args.id);item.tracks=item.tracks.filter(track=>!args.trackIds.includes(track.id));save(all,'removeFromPlaylist',args);}
export function movePlaylistTrack(args){const all=lists();const item=all.find(item=>item.id===args.id);const [track]=item.tracks.splice(args.from,1);item.tracks.splice(args.to,0,track);save(all,'movePlaylistTrack',args);}
export function addToPlaylist(args){save(lists(),'addToPlaylist',args);}
