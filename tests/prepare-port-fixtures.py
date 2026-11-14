import hashlib, pathlib, subprocess, urllib.request

root=pathlib.Path(__file__).resolve().parents[1]
target=root/'app/src/androidTest/assets/port-fixtures'
target.mkdir(parents=True,exist_ok=True)
url='https://raw.githubusercontent.com/satyawikananda/soundgo/3a5b3e6bd2f6e5bb833be1f1a658a4e4095fc7fe/index.js'
with urllib.request.urlopen(url,timeout=45) as response:data=response.read()
assert hashlib.sha256(data).hexdigest()=='bd256059a8be720df5b79db6a8da90d7a0aba0d660fd4057afba8dac97c0196e'
(target/'soundgo.js').write_bytes(data)
with urllib.request.urlopen('https://raw.githubusercontent.com/satyawikananda/soundgo/3a5b3e6bd2f6e5bb833be1f1a658a4e4095fc7fe/LICENSE',timeout=45) as response:license_text=response.read()
assert b'GNU AFFERO GENERAL PUBLIC LICENSE' in license_text
(target/'SoundGo-AGPL-3.0.txt').write_bytes(license_text)
subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','lavfi','-i','sine=frequency=440:duration=8','-c:a','aac','-b:a','96k','-f','hls','-hls_time','2','-hls_playlist_type','vod','-hls_segment_filename',str(target/'segment-%03d.ts'),str(target/'sample.m3u8')],check=True)
print('Verified original SoundGo source and generated native HLS audio fixtures')

for bits,fmt in [(16,'s16p'),(24,'s32p')]:
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','lavfi','-i','sine=frequency=659:duration=8','-sample_fmt',fmt,'-bits_per_raw_sample',str(bits),'-c:a','alac',str(target/f'alac-{bits}.m4a')],check=True)
(target/'master.m3u8').write_text('#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=128000,CODECS="mp4a.40.2"\nsample.m3u8\n')
key=target/'key.bin'
key.write_bytes(bytes(range(16)))
key_info=target/'key-info.txt'
key_info.write_text('key.bin\n'+str(key.resolve())+'\n00000000000000000000000000000001\n')
try:
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','lavfi','-i','sine=frequency=523:duration=8','-c:a','aac','-b:a','96k','-f','hls','-hls_time','2','-hls_playlist_type','vod','-hls_key_info_file',str(key_info),'-hls_segment_filename',str(target/'encrypted-%03d.ts'),str(target/'encrypted.m3u8')],check=True)
finally:
    key_info.unlink()
print('Generated 16/24-bit ALAC and encrypted HLS audio fixtures')

# Opus in MPEG-TS exercises a format the Android 13 HLS extractor cannot directly prepare.
subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','lavfi','-i','sine=frequency=330:duration=8','-c:a','libopus','-b:a','96k','-f','hls','-hls_time','2','-hls_playlist_type','vod','-hls_segment_filename',str(target/'opus-%03d.ts'),str(target/'opus.m3u8')],check=True)
print('Generated complete Opus HLS for native decoder fallback checks')
