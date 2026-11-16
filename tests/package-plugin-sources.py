"""Append exact native runtime sources, build provenance and complete notices."""
import json,pathlib,re,zipfile
root=pathlib.Path(__file__).resolve().parents[1]
version=re.search(r"versionName '([^']+)'",(root/'app/build.gradle').read_text()).group(1)
archive=root/f'app/build/outputs/release/mygo-music-v{version}-sources.zip'
pins=json.loads((root/'tests/native-tools-pins.json').read_text())
# Publish rebuildable sources without the legacy checkout's signing passwords.
clean=archive.with_suffix('.clean.zip')
with zipfile.ZipFile(archive) as source,zipfile.ZipFile(clean,'w',zipfile.ZIP_DEFLATED) as target:
 for item in source.infolist():
  data=source.read(item.filename)
  if item.filename=='mygo-music/app/build.gradle':
   text=data.decode();text=re.sub(r"storePassword '[^']*'","storePassword System.getenv('MYGO_STORE_PASSWORD')",text);text=re.sub(r"keyPassword '[^']*'","keyPassword System.getenv('MYGO_KEY_PASSWORD')",text);data=text.encode()
  target.writestr(item,data)
 clean_note='Release signing material is excluded. Supply your own keystore at signing/mygo-release.keystore and set MYGO_STORE_PASSWORD and MYGO_KEY_PASSWORD before a release rebuild. No original signing passwords are included. Debug builds do not need the release key.\n'
 target.writestr('SIGNING-REBUILD.txt',clean_note)
clean.replace(archive)
with zipfile.ZipFile(archive,'a',zipfile.ZIP_DEFLATED) as z:
 names=set(z.namelist())
 def add(path,name):
  if name not in names:z.write(path,name);names.add(name)
 for path in (root/'app/src/main/assets/third-party').iterdir():
  if path.is_file():add(path,'mygo-music/app/src/main/assets/third-party/'+path.name)
 add(root/'app/src/main/assets/native-tools.json','mygo-music/app/src/main/assets/native-tools.json')
 add(root/'app/src/main/assets/plugin-audit.json','mygo-music/app/src/main/assets/plugin-audit.json')
 add(root/'app/src/main/assets/vendor/yt-dlp','mygo-music/app/src/main/assets/vendor/yt-dlp')
 for key in ['flet','qjs','python','yt-source','ejs']:
  path=root/'.plugin-tools-cache'/pins[key]['file'];add(path,'third-party/plugin-tools/'+path.name)
 z.writestr('third-party/plugin-tools/REBUILD.txt','Run tests/build-plugin-tools.py with Python 3.12, Android NDK 27.2.12479018 and a C compiler. The pinned Flet build recipe and CPython source are included. arm64, armv7 and x86_64 runtime assets use the pinned upstream Flet release; legacy x86 is rebuilt from the same CPython sources and checksum-pinned dependency archives. QuickJS and the CLI launchers are built for all four ABIs. All upstream downloads are SHA-256 checked. See tests/native-tools-pins.json and the asset notices. No maxbeau plugin source is redistributed.\n')
print('Packaged native runtime sources, licenses and build provenance')
