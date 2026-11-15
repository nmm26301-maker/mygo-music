"""Append exact native runtime sources, build provenance and complete notices."""
import json,pathlib,re,zipfile
root=pathlib.Path(__file__).resolve().parents[1]
version=re.search(r"versionName '([^']+)'",(root/'app/build.gradle').read_text()).group(1)
archive=root/f'app/build/outputs/release/mygo-music-v{version}-sources.zip'
pins=json.loads((root/'tests/native-tools-pins.json').read_text())
with zipfile.ZipFile(archive,'a',zipfile.ZIP_DEFLATED) as z:
 names=set(z.namelist())
 def add(path,name):
  if name not in names:z.write(path,name);names.add(name)
 for path in (root/'app/src/main/assets/third-party').iterdir():
  if path.is_file():add(path,'mygo-music/app/src/main/assets/third-party/'+path.name)
 add(root/'app/src/main/assets/native-tools.json','mygo-music/app/src/main/assets/native-tools.json')
 add(root/'app/src/main/assets/vendor/yt-dlp','mygo-music/app/src/main/assets/vendor/yt-dlp')
 for key in ['flet','qjs','python','yt-source','ejs']:
  path=root/'.plugin-tools-cache'/pins[key]['file'];add(path,'third-party/plugin-tools/'+path.name)
 z.writestr('third-party/plugin-tools/REBUILD.txt','Run tests/build-plugin-tools.py with Python 3.12, Android NDK 27.2.12479018 and a C compiler. The pinned Flet build recipe and CPython source are included. arm64, armv7 and x86_64 runtime assets use the pinned upstream Flet release; legacy x86 is rebuilt from the same CPython sources and checksum-pinned dependency archives. QuickJS and the CLI launchers are built for all four ABIs. All upstream downloads are SHA-256 checked. See tests/native-tools-pins.json and the asset notices. No maxbeau plugin source is redistributed.\n')
print('Packaged native runtime sources, licenses and build provenance')
