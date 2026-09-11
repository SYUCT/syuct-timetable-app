// Local QA helper, restricted to the disposable preview emulator. No real student data.
import {execFileSync} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url),root=path.resolve(import.meta.dirname,'..');
const adb=path.join(os.homedir(),'.local/share/syuct-android-tools/sdk/platform-tools/adb');
const serial='emulator-5554';
const run=(...args)=>execFileSync(adb,['-s',serial,...args],{timeout:20000,maxBuffer:16*1024*1024});
if(!run('emu','avd','name').toString().includes('SYUCT_Preview_API35'))throw Error('Not the preview emulator');
const args=process.argv.slice(2);
const pause=()=>new Promise(r=>setTimeout(r,1800));
while(args.length){
 const action=args.shift();
 if(action==='tap'){run('shell','input','tap',args.shift(),args.shift());await pause();}
 else if(action==='swipe'){run('shell','input','swipe',...args.splice(0,4),'350');await pause();}
 else if(action==='key'){run('shell','input','keyevent',args.shift());await pause();}
 else if(action==='snap'){const name=args.shift();if(!/^[\w-]+$/.test(name))throw Error('Bad name');fs.writeFileSync(path.join(root,'test-results',name+'.png'),run('exec-out','screencap','-p'));}
 else if(action==='ui'){
  run('shell','uiautomator','dump','/sdcard/syuct-ui.xml');const xml=run('shell','cat','/sdcard/syuct-ui.xml').toString();
  for(const m of xml.matchAll(/<node\b[^>]+>/g)){const a=Object.fromEntries([...m[0].matchAll(/([\w-]+)="([^"]*)"/g)].map(x=>[x[1],x[2]]));if(a.text||a['content-desc'])console.log(a.text||a['content-desc'],a['resource-id'],a.bounds);}
 } else if(action==='demo'){
  const codec=require(root+'/app/src/main/assets/timetable-codec.js');
  const courses=Array.from({length:7},(_,i)=>({name:'Demo-'+(i+1),teacher:'Teacher',room:'Building-3-222',weekday:i+1,startSection:5,endSection:6,startWeek:1,endWeek:20,weekType:'all',colorIndex:i%6}));
  const code=codec.encodeShareCode({settings:{semester:'DEMO',firstWeekDate:'2026-08-31',totalWeeks:20},courses});
  run('shell','input','keycombination','113','29');run('shell','input','keyevent','67');await pause();
  for(let i=0;i<code.length;i+=20){run('shell','input','text',code.slice(i,i+20));await pause();}
 }else if(action==='clock-test'){
  const originalAuto=run('shell','settings','get','global','auto_time').toString().trim();
  try{
   run('shell','settings','put','global','auto_time','0');
   for(const [label,time,expected] of [['active','14:40',true],['break','14:25',false],['end','15:20',false]]){
    run('shell','cmd','alarm','set-time',String(Date.parse('2026-09-10T'+time+':00+08:00')));
    run('shell','am','start','-n','top.syuct.timetable/.MainActivity');await pause();run('shell','input','keyevent','KEYCODE_HOME');await pause();
    run('shell','uiautomator','dump','/sdcard/syuct-ui.xml');const xml=run('shell','cat','/sdcard/syuct-ui.xml').toString();
    if(xml.includes('● 当前课')!==expected)throw Error('Widget clock failed: '+label);
    fs.writeFileSync(path.join(root,'test-results','widget-'+label+'.png'),run('exec-out','screencap','-p'));
    console.log('PASS native widget '+label);
   }
  }finally{
   run('shell','cmd','alarm','set-time',String(Date.now()));run('shell','settings','put','global','auto_time',originalAuto);
   run('shell','am','start','-n','top.syuct.timetable/.MainActivity');await pause();run('shell','input','keyevent','KEYCODE_HOME');
  }
 }else throw Error('Unknown action '+action);
}
