"""Generate a controlled stereo ADTS source, without third-party recordings."""
import pathlib,subprocess
root=pathlib.Path(__file__).resolve().parents[1]
out=root/'app/src/androidTest/assets/port-fixtures/live-stereo.aac'
out.parent.mkdir(parents=True,exist_ok=True)
subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','lavfi','-i','aevalsrc=0.2*sin(2*PI*440*t)|0.2*sin(2*PI*880*t):s=48000:d=8','-c:a','aac','-b:a','192k','-f','adts',str(out)],check=True)
print('Generated controlled 440/880Hz stereo AAC fixture:',out.stat().st_size,'bytes')
