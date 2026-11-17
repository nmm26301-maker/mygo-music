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

# A genuine lossy AAC stream whose deliberately incorrect plugin label exercises honest quality reporting.
subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','lavfi','-i','sine=frequency=392:duration=20','-c:a','aac','-b:a','96k','-movflags','+faststart',str(target/'quality-aac.m4a')],check=True)

# Clear fragmented MP4 AAC, matching the public SoundGo transport. Expected ADTS is independently remuxed by FFmpeg.
subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','lavfi','-i','sine=frequency=440:sample_rate=48000:duration=36','-f','lavfi','-i','sine=frequency=880:sample_rate=48000:duration=36','-filter_complex','[0:a][1:a]amerge=inputs=2[a]','-map','[a]','-c:a','aac','-b:a','160k','-f','hls','-hls_time','4','-hls_playlist_type','vod','-hls_segment_type','fmp4','-hls_fmp4_init_filename','fmp4-init.mp4','-hls_segment_filename',str(target/'fmp4-%03d.m4s'),str(target/'fmp4.m3u8')],check=True)
subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(target/'fmp4.m3u8'),'-c:a','copy','-f','adts',str(target/'fmp4-expected.aac')],check=True)
print('Generated clear stereo fMP4 HLS and independently remuxed expected AAC access units')

# High-entropy stereo AAC reproduces decoded-cache growth with a small controlled budget.
subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','lavfi','-i','anoisesrc=color=white:sample_rate=48000:duration=12:seed=114','-f','lavfi','-i','anoisesrc=color=pink:sample_rate=48000:duration=12:seed=217','-filter_complex','[0:a][1:a]amerge=inputs=2[a]','-map','[a]','-c:a','aac','-b:a','128k','-f','hls','-hls_time','2','-hls_playlist_type','vod','-hls_segment_filename',str(target/'cache-noise-%03d.ts'),str(target/'cache-noise.m3u8')],check=True)
(target/'cache-missing.m3u8').write_text('#EXTM3U\n#EXT-X-TARGETDURATION:8\n#EXTINF:8,\nsegment-000.ts\n#EXTINF:8,\ncache-absent.ts\n#EXT-X-ENDLIST\n')
(target/'cache-truncated.m3u8').write_text('#EXTM3U\n#EXT-X-TARGETDURATION:8\n#EXTINF:8,\nsegment-000.ts\n#EXTINF:8,\ncache-truncated.ts\n#EXT-X-ENDLIST\n')

# Native mirror checks require independently distinguishable stereo channels in an ordinary M4A.
subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','lavfi','-i','sine=frequency=440:sample_rate=48000:duration=20','-f','lavfi','-i','sine=frequency=880:sample_rate=48000:duration=20','-filter_complex','[0:a][1:a]amerge=inputs=2[a]','-map','[a]','-c:a','aac','-b:a','160k','-movflags','+faststart',str(target/'bilibili-stereo.m4a')],check=True)
