// Local-only QA: changes the disposable emulator clock, always restores it.
// Uses its existing timetable; does not edit or export course data.
import {execFileSync} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
const root=path.resolve(import.meta.dirname,'..');
const adb=path.join(os.homedir(),'.local/share/syuct-android-tools/sdk/platform-tools/adb');
const run=(...args)=>execFileSync(adb,['-s','emulator-5554',...args],{timeout:20000,maxBuffer:16*1024*1024});
if(!run('emu','avd','name').toString().includes('SYUCT_Preview_API35'))throw Error('Preview emulator only');
const pause=ms=>new Promise(r=>setTimeout(r,ms));
const xml=()=>{run('shell','uiautomator','dump','/sdcard/syuct-reminder-ui.xml');return run('shell','cat','/sdcard/syuct-reminder-ui.xml').toString();};
const snap=name=>fs.writeFileSync(path.join(root,'test-results',name+'.png'),run('exec-out','screencap','-p'));
const auto=run('shell','settings','get','global','auto_time').toString().trim();
try{
 run('shell','settings','put','global','auto_time','0');
 run('shell','cmd','alarm','set-time',String(Date.parse('2026-09-11T13:14:40+08:00')));
 run('shell','am','start','-n','top.syuct.timetable/.MainActivity');await pause(2000);
 run('shell','input','keyevent','KEYCODE_HOME');
 const alarm=run('shell','dumpsys','alarm').toString();
 if(!alarm.includes('top.syuct.timetable.COURSE_REMINDER'))throw Error('Reminder not scheduled');
 await pause(24000);
 const notifications=run('shell','dumpsys','notification','--noredact').toString();
 if(!/NotificationRecord[^\n]*pkg=top.syuct.timetable/.test(notifications)||!notifications.includes('13:30 开课'))throw Error('Expected actual course notification missing');
 if(!notifications.includes('content://settings/system/notification_sound'))throw Error('Default system notification sound missing');
 console.log('PASS actual 13:15 reminder for 13:30 class; system default sound configured');
 run('shell','cmd','statusbar','expand-notifications');await pause(1200);snap('private-course-notification');
 run('shell','cmd','statusbar','collapse');
 for(const [time,expected] of [['13:30:05',true],['14:25:00',false],['14:40:00',true],['15:20:05',false]]){
  run('shell','cmd','alarm','set-time',String(Date.parse('2026-09-11T'+time+'+08:00')));
  run('shell','am','start','-n','top.syuct.timetable/.MainActivity');await pause(1000);
  run('shell','input','keyevent','KEYCODE_HOME');await pause(2500);
  const tree=xml();if(tree.includes('● 当前课')!==expected)throw Error('Widget marker failed at '+time);
  if(expected)snap('private-widget-current');console.log('PASS widget current marker '+time+' = '+expected);
 }
}finally{
 run('shell','cmd','alarm','set-time',String(Date.now()));
 run('shell','settings','put','global','auto_time',auto);
 run('shell','am','start','-n','top.syuct.timetable/.MainActivity');
}
