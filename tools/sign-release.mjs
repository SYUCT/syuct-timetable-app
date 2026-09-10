// Generates a private, durable signing identity OUTSIDE the repository.
// Never print the password or place the key in a GitHub artifact.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
const [unsignedApk,outputApk,privateDirectory]=process.argv.slice(2);
if(!unsignedApk||!outputApk||!privateDirectory||!process.env.JAVA_HOME||!process.env.ANDROID_HOME)throw Error('Usage: node tools/sign-release.mjs unsigned.apk signed.apk /private/signing-directory (JAVA_HOME and ANDROID_HOME required)');
const root=path.resolve(import.meta.dirname,'..');
const privatePath=path.resolve(privateDirectory);
if(privatePath===root||privatePath.startsWith(root+path.sep))throw Error('Signing directory must be outside repository');
fs.mkdirSync(privatePath,{recursive:true,mode:0o700});
const credentialsPath=path.join(privatePath,'signing.json'),keyPath=path.join(privatePath,'syuct-timetable.jks');
let credentials;
if(fs.existsSync(credentialsPath))credentials=JSON.parse(fs.readFileSync(credentialsPath,'utf8'));
else {
 if(fs.existsSync(keyPath))throw Error('Key exists without credentials. Refusing to replace it.');
 credentials={alias:'syuct-timetable',password:crypto.randomBytes(32).toString('hex')};
 fs.writeFileSync(credentialsPath,JSON.stringify(credentials),{mode:0o600,flag:'wx'});
}
const env={...process.env,SYUCT_SIGN_PASSWORD:credentials.password};
function run(cmd,args){const r=spawnSync(cmd,args,{env,encoding:'utf8'});if(r.status!==0)throw Error(r.stderr||'Signing command failed');if(r.stdout)process.stdout.write(r.stdout);}
if(!fs.existsSync(keyPath)){
 run(path.join(process.env.JAVA_HOME,'bin/keytool'),['-genkeypair','-keystore',keyPath,'-storepass:env','SYUCT_SIGN_PASSWORD','-keypass:env','SYUCT_SIGN_PASSWORD','-alias',credentials.alias,'-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=SYUCT Timetable, OU=Student Project, O=SYUCT Community']);
 fs.chmodSync(keyPath,0o600);
}
const build=path.join(process.env.ANDROID_HOME,'build-tools/35.0.0');
fs.mkdirSync(path.dirname(path.resolve(outputApk)),{recursive:true});
const aligned=outputApk+'.aligned';
run(path.join(build,'zipalign'),['-f','-p','4',unsignedApk,aligned]);
run(path.join(build,'apksigner'),['sign','--ks',keyPath,'--ks-key-alias',credentials.alias,'--ks-pass','env:SYUCT_SIGN_PASSWORD','--key-pass','env:SYUCT_SIGN_PASSWORD','--out',outputApk,aligned]);
fs.unlinkSync(aligned);
run(path.join(build,'apksigner'),['verify','--verbose','--print-certs',outputApk]);
console.log('APK signed. Private key kept outside repository.');
