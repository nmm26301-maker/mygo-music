import hashlib, pathlib, subprocess, urllib.request

root=pathlib.Path(__file__).resolve().parents[1]
target=root/'app/src/androidTest/assets/port-fixtures'
target.mkdir(parents=True,exist_ok=True)
url='https://raw.githubusercontent.com/satyawikananda/soundgo/3a5b3e6bd2f6e5bb833be1f1a658a4e4095fc7fe/index.js'
with urllib.request.urlopen(url,timeout=45) as response:data=response.read()
assert hashlib.sha256(data).hexdigest()=='bd256059a8be720df5b79db6a8da90d7a0aba0d660fd4057afba8dac97c0196e'
(target/'soundgo.js').write_bytes(data)
subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','lavfi','-i','sine=frequency=440:duration=8','-c:a','aac','-b:a','96k','-f','hls','-hls_time','2','-hls_playlist_type','vod','-hls_segment_filename',str(target/'segment-%03d.ts'),str(target/'sample.m3u8')],check=True)
print('Verified original SoundGo source and generated native HLS audio fixtures')
