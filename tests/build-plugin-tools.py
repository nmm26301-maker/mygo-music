"""Checksum-pinned native runtimes. Reproducible recipes and licenses ship with sources."""
import hashlib,json,os,pathlib,shutil,subprocess,tarfile,tempfile,urllib.request,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
CACHE=ROOT/'.plugin-tools-cache';CACHE.mkdir(exist_ok=True)
PINS=json.loads((ROOT/'tests/native-tools-pins.json').read_text())
NDK=pathlib.Path(os.environ.get('ANDROID_HOME','/usr/local/lib/android/sdk'))/'ndk/27.2.12479018'
BIN=NDK/'toolchains/llvm/prebuilt/linux-x86_64/bin'
LICENSES=ROOT/'app/src/main/assets/third-party';LICENSES.mkdir(parents=True,exist_ok=True)
FILES={}
for key,item in PINS.items():
 f=CACHE/item['file']
 if not f.is_file():
  print('Downloading pinned native tool:',key,flush=True)
  with urllib.request.urlopen(item['url'],timeout=90) as response:data=response.read(80*1024*1024)
  assert hashlib.sha256(data).hexdigest()==item['sha256'],key+' checksum mismatch'
  f.write_bytes(data)
 assert hashlib.sha256(f.read_bytes()).hexdigest()==item['sha256'],key+' checksum mismatch'
 FILES[key]=f
def unpack(archive,dest):
 dest.mkdir(parents=True,exist_ok=True)
 with tarfile.open(archive) as z:z.extractall(dest,filter='data')
def run(args,cwd,env=None):
 log=CACHE/'build.log'
 with log.open('a') as out:r=subprocess.run(args,cwd=cwd,env=env,stdout=out,stderr=subprocess.STDOUT)
 if r.returncode:
  print(log.read_text(errors='replace')[-20000:],flush=True)
  raise RuntimeError('Native runtime build failed: '+str(args[:3]))
stamp=hashlib.sha256(pathlib.Path(__file__).read_bytes()+(ROOT/'tests/native-tools-pins.json').read_bytes()).hexdigest()
hashes=[]
with tempfile.TemporaryDirectory(prefix='mygo-tools-') as tmp:
 work=pathlib.Path(tmp);sources={}
 for key in ['flet','qjs','python','yt-source','ejs']:
  unpack(FILES[key],work/key);sources[key]=next((work/key).iterdir())
 for key,name in [('flet','Flet-Python-build-MIT.txt'),('qjs','QuickJS-MIT.txt'),('python','Python-PSF.txt'),('yt-source','yt-dlp-Unlicense.txt'),('ejs','yt-dlp-EJS-MIT.txt')]:shutil.copyfile(sources[key]/'LICENSE',LICENSES/name)
 for key,path in FILES.items():
  if key.startswith('license-'):shutil.copyfile(path,LICENSES/path.name)
 for abi,target in [('arm64-v8a','aarch64-linux-android'),('armeabi-v7a','armv7a-linux-androideabi'),('x86_64','x86_64-linux-android'),('x86','i686-linux-android')]:
  cached=CACHE/abi;cached.mkdir(exist_ok=True)
  if not (cached/'stamp').is_file() or (cached/'stamp').read_text()!=stamp:
   runtime=work/(abi+'-runtime');runtime.mkdir()
   if abi!='x86':unpack(FILES[abi],runtime)
   else:
    android=sources['flet']/'android';mapping=android/'abi-to-host.sh';mapping.write_text(mapping.read_text().replace('    *)','    x86) HOST=i686-linux-android ;;\n    *)'))
    script=android/'build.sh';s=script.read_text().replace('wget -c https://www.python.org/ftp/python/$version_no_pre/$src_filename',': # source verified by parent recipe').replace('curl -Lf "$url" -o $lib_file','cp "$downloads/pinned-$name_ver.tar.gz" "$lib_file"').replace('make -j $CPU_COUNT','make -j 2');script.write_text(s)
    downloads=android/'downloads';downloads.mkdir(exist_ok=True)
    for key,path in FILES.items():
     if key.startswith('dep-'):shutil.copyfile(path,downloads/('pinned-'+key[4:]+'.tar.gz'))
    version=android/'build/3.12.16';version.mkdir(parents=True,exist_ok=True);shutil.copyfile(FILES['python'],version/'Python-3.12.16.tgz')
    env=os.environ.copy();env['NDK_HOME']=str(NDK);run(['bash',str(script),'3.12.16','x86'],android,env)
    installed=android/'install/android/x86/python-3.12.16'
    for name in ['libpython3.12.so','libssl_python.so','libcrypto_python.so','libsqlite3_python.so']:shutil.copyfile(installed/'lib'/name,runtime/name)
    stdlib=installed/'lib/python3.12'
    with zipfile.ZipFile(runtime/'libpythonbundle.so','w',zipfile.ZIP_DEFLATED) as z:
     for path in stdlib.rglob('*'):
      if not path.is_file() or any(p in ['test','tests','site-packages','__pycache__','idlelib','tkinter','ensurepip','config-3.12'] for p in path.parts):continue
      rel=path.relative_to(stdlib)
      if rel.parts[0]=='lib-dynload':
       if path.suffix=='.so':z.write(path,'modules/'+path.name)
      elif path.suffix in ['.py','.pyc']:z.write(path,'stdlib/'+str(rel))
   for path in runtime.glob('*.so'):shutil.copyfile(path,cached/path.name)
   qbuild=work/(abi+'-qjs');shutil.copytree(sources['qjs'],qbuild)
   run(['make','-j2','CONFIG_TEMPORAL_INTL=','qjsc'],qbuild)
   shutil.copyfile(qbuild/'qjsc',qbuild/'mygo-host-qjsc');(qbuild/'mygo-host-qjsc').chmod(0o755);shutil.rmtree(qbuild/'.obj')
   run(['make','-j2','CONFIG_TEMPORAL_INTL=','QJSC=./mygo-host-qjsc','LIBS=-lm -ldl','CC='+str(BIN/(target+'24-clang')),'AR='+str(BIN/'llvm-ar'),'LDFLAGS=-pie -Wl,-z,relro,-z,now,-z,max-page-size=16384','qjs'],qbuild)
   shutil.copyfile(qbuild/'qjs',cached/'libmygo-qjs.so')
   run([str(BIN/(target+'24-clang')),'-O2','-fPIE','-pie','-Wl,-z,relro,-z,now,-z,max-page-size=16384',str(ROOT/'app/src/main/cpp/plugin-python.c'),'-ldl','-o',str(cached/'libmygo-python.so')],ROOT)
   for path in cached.glob('*.so'):
    if path.name!='libpythonbundle.so':run([str(BIN/'llvm-strip'),str(path)],ROOT)
   (cached/'stamp').write_text(stamp)
  dest=ROOT/'app/src/main/jniLibs'/abi;dest.mkdir(parents=True,exist_ok=True)
  for path in cached.glob('*.so'):shutil.copyfile(path,dest/path.name)
  hashes.append(hashlib.sha256((cached/'libpythonbundle.so').read_bytes()).hexdigest());print('Verified native tools:',abi,flush=True)
vendor=ROOT/'app/src/main/assets/vendor';vendor.mkdir(exist_ok=True);shutil.copyfile(FILES['yt-dlp'],vendor/'yt-dlp')
info={'python':'3.12.16','ytDlp':'2026.10.26','quickjsCommit':'e585640ffa2cf88ce7c2d587583c6fe15928ec2f','ytDlpSha256':PINS['yt-dlp']['sha256'],'bundleHashes':hashes,'minApi':24,'pins':PINS}
(ROOT/'app/src/main/assets/native-tools.json').write_text(json.dumps(info,indent=2)+'\n')
