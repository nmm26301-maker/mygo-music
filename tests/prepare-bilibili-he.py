"""Pinned AOSP CTS decoder vectors, test APK only. Loop encoded packets without transcoding."""
import base64, hashlib, json, pathlib, subprocess, urllib.request
root=pathlib.Path(__file__).resolve().parents[1]
target=root/'app/src/androidTest/assets/port-fixtures';target.mkdir(parents=True,exist_ok=True)
commit='9c633d2ad66237959480b41a511df75b8062c336'
base=f'https://android.googlesource.com/platform/cts/+/{commit}/tests/tests/media/res/raw/'
vectors=[('he-v1','noise_2ch_48khz_aot5_dr_sbr_sig1_mp4.m4a','32d840b0f59c200d4f984083ad647b2350d1f4e1717979256e7a91934180a66e','d8f1ffc59de54a69d469dc86f1b6ae775664e2f0','HE-AAC'),('he-v2','noise_2ch_44khz_aot29_dr_sbr_sig1_mp4.m4a','124770e4e14bb7d1aa6d0839209e555cdd9759a13c5c6ff7e1c3721b457bee66','688cd73ea513950019af1066f432e1b2c9a377fe','HE-AACv2')]
def probe(path):
 return json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-show_packets','-show_entries','stream=codec_name,profile,sample_rate,channels,duration:packet=data_hash','-show_data_hash','sha256','-of','json',str(path)]))
rows=[]
for name,filename,digest,blob,profile in vectors:
 with urllib.request.urlopen(base+filename+'?format=TEXT',timeout=45) as response:data=base64.b64decode(response.read())
 assert hashlib.sha256(data).hexdigest()==digest
 assert hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()==blob
 original=target/(name+'-original.m4a');original.write_bytes(data)
 output=target/(name+'.m4a')
 subprocess.run(['ffmpeg','-v','error','-y','-stream_loop','4','-i',str(original),'-map','0:a:0','-c:a','copy','-movflags','+faststart',str(output)],check=True)
 before,after=probe(original),probe(output)
 assert before['streams'][0]['profile']==after['streams'][0]['profile']==profile
 assert after['streams'][0]['channels']==2 and float(after['streams'][0]['duration'])>=19
 assert [p['data_hash'] for p in after['packets']]==[p['data_hash'] for p in before['packets']]+[p['data_hash'] for p in before['packets']][1:]*4,'HE-AAC encoded packet payload changed'
 rows.append({'name':name,'sourceUrl':base+filename,'sourceCommit':commit,'sourceSha256':digest,'sourceGitBlob':blob,'outputSha256':hashlib.sha256(output.read_bytes()).hexdigest(),'encodedPacketsPreserved':True,'copies':5,'stream':after['streams'][0]})
 original.unlink()
(target/'he-proof.json').write_text(json.dumps({'scope':'Public AOSP CTS decoder vectors for controlled tests only; not a platform music sample and not in the release APK','vectors':rows},indent=2))
print('Verified genuine HE-AAC and HE-AACv2 stereo vectors and unchanged encoded packets')
