import pathlib,hashlib,urllib.request,subprocess
root=pathlib.Path(__file__).resolve().parents[1];target=root/'app/src/androidTest/assets/provider-fixes';target.mkdir(parents=True,exist_ok=True)
repo='fcmfcm1999/mygo-music-kugou';sha='d1ed5a25e5bc0303bb7b06dc210fb4dc0e40a9aa'
with urllib.request.urlopen(f'https://raw.githubusercontent.com/{repo}/{sha}/index.js',timeout=45) as response:data=response.read()
assert hashlib.sha256(data).hexdigest()=='4c463558265aa8a7a41606ad2ada7a50512d68ed791f462ed8de0ce515d14e24'
(target/'kugou-original.js').write_bytes(data)
with urllib.request.urlopen(f'https://raw.githubusercontent.com/bitbyte404/mygo-music-kugou-lite/b964ac03274163d3bf64bb530a452332be244635/LICENSE',timeout=45) as response:license=response.read()
assert b'MIT License' in license
bundled=root/'app/src/main/assets/third-party/Kugou-Device-MIT.txt'
assert bundled.read_bytes()==license,'Corresponding Kugou device license changed'
subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','lavfi','-i','sine=frequency=622:duration=8','-c:a','flac',str(target/'member.flac')],check=True)
print('Verified original Kugou source, corresponding MIT license and lossless playback fixture')

for name,repo,commit,digest in [
 ('qishui-original.js','DaviRain-Su/mygo-music-qishui','68ff332fe60eaa869f4a9afc55cc2144e151e312','d004a53dc1e6a63546919cbb92a662121dd6691b5cfe296d54bd6f7ab8cbe1c5'),
 ('bilibili-original.js','GentleM4dman/mygo-music-bilibili','43d15cca5f2eb3616bb7718a752b8e240b6c5cc5','d2f013c26929710d2185975a078a5844b9c41f9c704df6995951127c270a8f28'),
 ('ximalaya-original.js','specialhua/mygo-ximalaya','5084fa6ed17ff2b698fb7ccd554d028c6630bc1c','6a09ce6438ebc3787213f4c3319b7ee933576d104853878edf836db78e631abb')]:
 with urllib.request.urlopen(f'https://raw.githubusercontent.com/{repo}/{commit}/index.js',timeout=45) as response:data=response.read()
 assert hashlib.sha256(data).hexdigest()==digest
 (target/name).write_bytes(data)
print('Verified original Bilibili and licensed Ximalaya login fixtures')

# The current Bilibili release is checked against every blob of its immutable Git tree.
# Keep the older market archive as an independent compatibility target.
import io,json,zipfile
pin=json.loads((root/'tests/bilibili-current-pin.json').read_text())
with urllib.request.urlopen(pin['url'],timeout=45) as response:archive=response.read(32*1024*1024+1)
assert len(archive)<=32*1024*1024,'Current Bilibili fixture archive too large'
prefix='mygo-music-bilibili-'+pin['commit']+'/'
with zipfile.ZipFile(io.BytesIO(archive)) as package:
 assert all(name.endswith('/') or name.startswith(prefix) for name in package.namelist()),'Unexpected archive root'
 files={name[len(prefix):]:package.read(name) for name in package.namelist() if not name.endswith('/') and name.startswith(prefix)}
 assert set(files)==set(pin['gitBlobs']),'Current Bilibili archive tree differs from the pinned commit'
 for name,data in files.items():
  assert hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()==pin['gitBlobs'][name],name
 assert hashlib.sha256(files['index.js']).hexdigest()==pin['entrySha256']
 assert files['LICENSE']==(root/'app/src/main/assets/third-party/Bilibili-MIT.txt').read_bytes()
 manifest=json.loads(files['plugin.json']);assert manifest['id']=='bilibili' and manifest['version']=='0.4.2'
 (target/'bilibili-current.js').write_bytes(files['index.js'])
(target/'bilibili-current.zip').write_bytes(archive)
(target/'bilibili-current-proof.json').write_text(json.dumps({'commit':pin['commit'],'sourceSha256':pin['entrySha256'],'archiveSha256':hashlib.sha256(archive).hexdigest(),'verifiedGitBlobs':len(files)},indent=2))
print('Verified current Bilibili 0.4.2 and all',len(files),'pinned Git blobs')
