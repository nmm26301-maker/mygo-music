"""Build an LGPL-only, pinned FFmpeg audio helper for Android; no retired binary wrappers."""
import hashlib, json, os, pathlib, shutil, subprocess, tarfile, tempfile, urllib.request, zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
VERSION = '9.0.2'
SHA256 = '8c3850283eb25fa026482078a04051e0be17347b09ef81a0849bec15a96e002e'
CACHE = ROOT / '.native-audio-cache'
CACHE.mkdir(exist_ok=True)
archive = CACHE / f'ffmpeg-{VERSION}.tar.xz'
if not archive.exists():
    with urllib.request.urlopen(f'https://ffmpeg.org/releases/ffmpeg-{VERSION}.tar.xz', timeout=60) as response:
        archive.write_bytes(response.read(40 * 1024 * 1024))
assert hashlib.sha256(archive.read_bytes()).hexdigest() == SHA256, 'FFmpeg source checksum mismatch'
sdk = pathlib.Path(os.environ.get('ANDROID_HOME', '/usr/local/lib/android/sdk'))
toolchain = sdk / 'ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin'
assert (toolchain / 'llvm-ar').is_file(), 'Install Android NDK 27.2.12479018 first'
stamp = hashlib.sha256(pathlib.Path(__file__).read_bytes()).hexdigest()
licenses = ROOT / 'app/src/main/assets/third-party'
licenses.mkdir(parents=True, exist_ok=True)
configurations = {}
with tempfile.TemporaryDirectory(prefix='mygo-ffmpeg-') as workspace:
    workspace = pathlib.Path(workspace)
    with tarfile.open(archive) as package:
        package.extractall(workspace, filter='data')
    source = workspace / f'ffmpeg-{VERSION}'
    shutil.copyfile(source / 'COPYING.LGPLv2.1', licenses / 'FFmpeg-LGPL-2.1.txt')
    shutil.copyfile(source / 'CREDITS', licenses / 'FFmpeg-CREDITS.txt')
    platforms = [('arm64-v8a', 'aarch64-linux-android', 'aarch64'), ('armeabi-v7a', 'armv7a-linux-androideabi', 'arm'), ('x86_64', 'x86_64-linux-android', 'x86_64'), ('x86', 'i686-linux-android', 'x86')]
    for abi, target, arch in platforms:
        output = CACHE / abi
        output.mkdir(exist_ok=True)
        binary = output / 'ffmpeg'
        flags = [str(source / 'configure'), '--target-os=android', '--arch=' + arch,
            '--enable-cross-compile', '--cc=' + str(toolchain / (target + '23-clang')),
            '--cxx=' + str(toolchain / (target + '23-clang++')), '--ld=' + str(toolchain / (target + '23-clang')),
            '--ar=' + str(toolchain / 'llvm-ar'), '--ranlib=' + str(toolchain / 'llvm-ranlib'),
            '--nm=' + str(toolchain / 'llvm-nm'), '--strip=' + str(toolchain / 'llvm-strip'),
            '--disable-autodetect', '--disable-everything', '--disable-doc', '--disable-debug',
            '--disable-programs', '--enable-ffmpeg', '--disable-avdevice', '--disable-swscale',
            '--disable-gpl', '--disable-nonfree', '--disable-version3', '--disable-shared', '--enable-static',
            '--disable-asm', '--enable-small', '--enable-pic', '--enable-network',
            '--enable-protocol=file,pipe,http,tcp,crypto',
            '--enable-demuxer=hls,mov,mpegts,aac,mp3,flac,ogg,wav',
            '--enable-decoder=alac,aac,mp3,flac,opus,vorbis,pcm_s16le,pcm_s24le,pcm_s32le,pcm_f32le',
            '--enable-parser=aac,aac_latm,mpegaudio,flac,opus,vorbis',
            '--enable-encoder=flac,pcm_s16le', '--enable-muxer=flac,wav',
            '--enable-filter=abuffer,abuffersink,aformat,anull,aresample',
            '--extra-cflags=-O2 -fPIE', '--extra-ldflags=-pie -latomic -Wl,-z,relro,-z,now,-z,max-page-size=16384']
        configurations[abi] = [flag.replace(str(source), 'FFMPEG_SOURCE').replace(str(toolchain), 'NDK_TOOLCHAIN') for flag in flags]
        if not binary.is_file() or not (output / 'stamp').is_file() or (output / 'stamp').read_text() != stamp:
            build = workspace / abi
            build.mkdir()
            log_path = CACHE / (abi + '.log')
            try:
                with log_path.open('w') as log:
                    subprocess.run(flags, cwd=build, stdout=log, stderr=subprocess.STDOUT, check=True)
                    subprocess.run(['make', '-j2', 'ffmpeg'], cwd=build, stdout=log, stderr=subprocess.STDOUT, check=True)
                shutil.copyfile(build / 'ffmpeg', binary)
                subprocess.run([str(toolchain / 'llvm-strip'), str(binary)], check=True)
                (output / 'stamp').write_text(stamp)
            except subprocess.CalledProcessError:
                print(log_path.read_text()[-18000:])
                raise
        destination = ROOT / 'app/src/main/jniLibs' / abi
        destination.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(binary, destination / 'libmygo-ffmpeg.so')
        print('Built verified Android audio helper:', abi, binary.stat().st_size, flush=True)
build_info = {'version': VERSION, 'source': f'https://ffmpeg.org/releases/ffmpeg-{VERSION}.tar.xz', 'sha256': SHA256,
    'license': 'LGPL-2.1-or-later; GPL, nonfree and version3 disabled', 'ndk': '27.2.12479018', 'configure': configurations,
    'usage': 'Separate Android process. HTTP input is an app-owned loopback proxy; original TLS and request headers remain in Java. No DRM bypass.'}
(licenses / 'FFmpeg-build.json').write_text(json.dumps(build_info, indent=2))
version = __import__('re').search(r"versionName '([^']+)'", (ROOT / 'app/build.gradle').read_text()).group(1)
release = ROOT / 'app/build/outputs/release'
release.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(release / f'mygo-music-v{version}-sources.zip', 'w', zipfile.ZIP_DEFLATED) as output:
    paths = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode().split('\0')
    for name in paths:
        if not name or name.startswith('signing/') or name.endswith(('.apk', '.keystore', '.b64')):
            continue
        path = ROOT / name
        if path.is_file():
            output.write(path, 'mygo-music/' + name)
    output.write(archive, 'third-party/' + archive.name)
    for name in ['FFmpeg-LGPL-2.1.txt', 'FFmpeg-CREDITS.txt', 'FFmpeg-build.json']:
        output.write(licenses / name, 'third-party/' + name)
    output.writestr('BUILD-SOURCE.txt', 'Corresponding source for the separate FFmpeg audio executable and this Android app. FFmpeg source is unchanged and checksum-pinned; see tests/build-native-audio.py and third-party/FFmpeg-build.json. Install NDK 27.2.12479018, run tests/prepare-typescript.py and tests/build-native-audio.py, then gradle assembleDebug. Release signing keys are intentionally excluded; use your own keystore for a release build. This helper is LGPL-2.1-or-later; see its complete license and credits.\n')
print('Prepared complete source and license archive', flush=True)
