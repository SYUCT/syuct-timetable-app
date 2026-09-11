// Uses the already-installed SDK, without downloading tools or installing software.
// Build outputs go solely into this checkout's .verify directory.
import fs from 'node:fs';import path from 'node:path';import {spawnSync} from 'node:child_process';
const root=path.resolve(import.meta.dirname,'..'),out=path.join(root,'.verify');
const sdk=process.env.ANDROID_HOME,jdk=process.env.JAVA_HOME;if(!sdk||!jdk)throw Error('JAVA_HOME and ANDROID_HOME required');
const bt=path.join(sdk,'build-tools/35.0.0'),android=path.join(sdk,'platforms/android-35/android.jar');
const run=(cmd,args)=>{const r=spawnSync(cmd,args,{cwd:root,encoding:'utf8',maxBuffer:4e6});if(r.stdout)process.stdout.write(r.stdout);if(r.stderr)process.stderr.write(r.stderr);if(r.status)throw Error('Build failed: '+path.basename(cmd));};
for(const p of ['generated','classes','native-tests','dex'])fs.mkdirSync(path.join(out,p),{recursive:true});
let manifest=fs.readFileSync(path.join(root,'app/src/main/AndroidManifest.xml'),'utf8').replace('<manifest ','<manifest package="top.syuct.timetable" ');
fs.writeFileSync(path.join(out,'AndroidManifest.xml'),manifest);
run(path.join(bt,'aapt2'),['compile','--dir',path.join(root,'app/src/main/res'),'-o',path.join(out,'res.zip')]);
run(path.join(bt,'aapt2'),['link','--auto-add-overlay','-I',android,'--manifest',path.join(out,'AndroidManifest.xml'),'-R',path.join(out,'res.zip'),'-A',path.join(root,'app/src/main/assets'),'--java',path.join(out,'generated'),'--min-sdk-version','26','--target-sdk-version','35','--version-code','3','--version-name','0.2.1-alpha1','-o',path.join(out,'resources.apk')]);
function files(dir,suffix){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?files(path.join(dir,e.name),suffix):e.name.endsWith(suffix)?[path.join(dir,e.name)]:[]);}
run(path.join(jdk,'bin/javac'),['-encoding','UTF-8','-source','17','-target','17','-cp',android,'-d',path.join(out,'classes'),...files(path.join(root,'app/src/main/java'),'.java'),...files(path.join(out,'generated'),'.java')]);
run(path.join(jdk,'bin/javac'),['-encoding','UTF-8','-d',path.join(out,'native-tests'),path.join(root,'app/src/main/java/top/syuct/timetable/LessonClock.java'),path.join(root,'tests/LessonClockTest.java')]);
run(path.join(jdk,'bin/java'),['-cp',path.join(out,'native-tests'),'LessonClockTest']);
run(path.join(jdk,'bin/javac'),['-encoding','UTF-8','-cp',path.join(out,'native-tests'),'-d',path.join(out,'native-tests'),path.join(root,'app/src/main/java/top/syuct/timetable/ReminderPlanner.java'),path.join(root,'tests/ReminderPlannerTest.java')]);
run(path.join(jdk,'bin/java'),['-cp',path.join(out,'native-tests'),'ReminderPlannerTest']);
run(path.join(jdk,'bin/java'),['-cp',path.join(bt,'lib/d8.jar'),'com.android.tools.r8.D8','--release','--min-api','26','--lib',android,'--output',path.join(out,'dex'),...files(path.join(out,'classes'),'.class')]);
fs.copyFileSync(path.join(out,'resources.apk'),path.join(out,'unsigned.apk'));
run('/usr/bin/zip',['-q','-j',path.join(out,'unsigned.apk'),...files(path.join(out,'dex'),'.dex')]);
console.log('Offline compile complete; unsigned APK: '+path.join(out,'unsigned.apk'));
