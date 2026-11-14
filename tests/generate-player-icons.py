"""Convert the pinned Lucide SVG originals to Android vector resources without a runtime dependency."""
from pathlib import Path
import xml.etree.ElementTree as E
import hashlib,json

root=Path(__file__).resolve().parents[1]
source=root/'third-party/lucide'
target=root/'app/src/main/res/drawable'
target.mkdir(exist_ok=True)
def num(v):return format(float(v),'.8g')
def path(tag,a):
    if tag=='path':return a['d']
    if tag=='line':return f"M{a['x1']} {a['y1']}L{a['x2']} {a['y2']}"
    if tag in ('polyline','polygon'):
        points=a['points'].replace(',', ' ').split();assert len(points)%2==0
        return 'M'+'L'.join(points[i]+' '+points[i+1] for i in range(0,len(points),2))+('Z' if tag=='polygon' else '')
    if tag=='circle':
        x,y,r=map(float,(a['cx'],a['cy'],a['r']))
        return f'M{num(x-r)} {num(y)}a{num(r)} {num(r)} 0 1 0 {num(r*2)} 0a{num(r)} {num(r)} 0 1 0 {num(-r*2)} 0'
    if tag=='rect':
        x,y,w,h=map(float,(a.get('x',0),a.get('y',0),a['width'],a['height']));r=float(a.get('rx',0))
        if not r:return f'M{x} {y}h{w}v{h}h{-w}Z'
        return f'M{x+r} {y}h{w-r*2}a{r} {r} 0 0 1 {r} {r}v{h-r*2}a{r} {r} 0 0 1 {-r} {r}h{-w+r*2}a{r} {r} 0 0 1 {-r} {-r}v{-h+r*2}a{r} {r} 0 0 1 {r} {-r}Z'
    raise ValueError('Unsupported SVG element: '+tag)
manifest={'repository':'lucide-icons/lucide','commit':'55782beff3a0133df79c417eb68f96e8f19e2404','license':'ISC; MIT for inherited Feather icons as listed in Lucide-LICENSE.txt','icons':{}}
for p in sorted((source/'icons').glob('*.svg')):
    svg=E.fromstring(p.read_text());children=[]
    for element in svg:
        d=path(element.tag.rsplit('}',1)[-1],element.attrib)
        children.append('    <path android:pathData='+json.dumps(d)+' android:fillColor="#00000000" android:strokeColor="#FFF1EDFA" android:strokeWidth="2" android:strokeLineCap="round" android:strokeLineJoin="round" />')
    xml='<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n'+'\n'.join(children)+'\n</vector>\n'
    output=target/('player_'+p.stem.replace('-','_')+'.xml');output.write_text(xml)
    manifest['icons'][p.name]={'svg_sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'android_sha256':hashlib.sha256(output.read_bytes()).hexdigest()}
(source/'metadata.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('Converted',len(manifest['icons']),'pinned Lucide icons')
