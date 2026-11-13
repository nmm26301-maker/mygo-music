// Generate independent Android expectations by executing the pinned upstream policy.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const root=path.join(__dirname,'..'),source=fs.readFileSync(path.join(root,'third-party/amll/pos-y-spring.ts'));
const expected='ade1d10115fe496046b231aa2a454f800b703360b2bb21fc9a6f1cf22d1301ff';
if(crypto.createHash('sha256').update(source).digest('hex')!==expected)throw Error('AMLL policy source fingerprint changed');
const Babel=require(path.join(root,'app/src/main/assets/vendor/babel.min.js'));
const code=Babel.transform(source.toString('utf8'),{filename:'pos-y-spring.ts',presets:['typescript'],plugins:['transform-modules-commonjs']}).code;
const upstream={};new Function('exports',code)(upstream);
const cases=[0,100,250,500,799,800,8000].map(interval=>({interval,...upstream.getPosYSpringPolicy(false,false,interval,false)}));
fs.writeFileSync(path.join(root,'app/src/androidTest/assets/lyric-motion-policy.json'),JSON.stringify(cases,null,2)+'\n');
console.log('Verified pinned AMLL policy; generated '+cases.length+' independent native expectations');
