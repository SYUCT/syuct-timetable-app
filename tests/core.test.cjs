const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const C=require('../app/src/main/assets/app-core.js');
const codec=require('../app/src/main/assets/timetable-codec.js');
const parser=require('../app/src/main/assets/timetable-campus-parser.js');
const fixture=fs.readFileSync(__dirname+'/fixtures/qq-duplicated.anonymized.txt','utf8');
const expected=JSON.parse(fs.readFileSync(__dirname+'/fixtures/expected-arrangements.json','utf8'));
const course={name:'现代设计方法',teacher:'教师甲',room:'瑞师楼（原3号教学楼）222',weekday:2,startSection:1,endSection:2,startWeek:2,endWeek:17,weekType:'all',colorIndex:0};
const grad=(period='1,2',week='2-17')=>({grid:[['节次',...C.weekdays],['1,2','','现代设计方法\n教师甲 1班\n节次:'+period+'节\n周次:'+week+'\n地点:瑞师楼（原3号教学楼）222\n开课院系:测试学院\n电话:','','','','','']]});
function expand(courses){return courses.flatMap(c=>{let out=[];for(let w=c.startWeek;w<=c.endWeek;w++)if(C.inWeek(c,w))for(let p=c.startSection;p<=c.endSection;p++)out.push(JSON.stringify([c.name,c.teacher,c.room,c.weekday,p,w]));return out;}).sort();}
test('默认20周，学期和日期可留空',()=>assert.equal(C.validate(C.blank()).settings.totalWeeks,20));
test('本科真实匿名样本精确保留20条安排',()=>{
 const r=parser.parseCampusTimetable(fixture);assert.equal(r.courses.length,20);
 assert.deepEqual(expand(r.courses),expected.flatMap(c=>c.periods.flatMap(p=>c.weeks.map(w=>JSON.stringify([c.name,c.teacher,c.location,c.weekday,p,w])))).sort());
});
test('本科读取对象复用原解析器并保留诊断',()=>{
 const r=C.parseCapture({kind:'undergraduate',tables:[{text:fixture}],supplemental:['未排课：测试课程'],unreadableFrames:0});
 assert.equal(r.courses.length,20);assert.ok(r.notices.length);assert.ok(r.supplemental.length);
});
test('拒绝首页摘要，不将星期分组内容冒充个人完整课表',()=>assert.throws(()=>C.parseCapture({kind:'undergraduate',tables:[{text:'课表\n星期一\n1-2节 (1-13|单周)有机化学AI通明楼138\n星期六星期日'}]})));
test('硕士保留楼号和教室号',()=>assert.deepEqual(C.graduate(grad()).courses[0],course));
test('离散周次和节次不得补齐',()=>{
 const r=C.graduate(grad('1,3','2-4,8-9'));assert.equal(r.courses.length,4);
 assert.deepEqual(r.courses.map(c=>[c.startSection,c.endSection,c.startWeek,c.endWeek]),[[1,1,2,4],[1,1,8,9],[3,3,2,4],[3,3,8,9]]);
});
test('硕士单双周与备注',()=>{
 assert.deepEqual(C.graduate(grad('1,2','1-16(单周)')).courses.map(c=>[c.startWeek,c.endWeek,c.weekType]),[[1,15,'odd']]);
 assert.ok(C.graduate(grad('1,2','2-9(教师甲)')).notices.length);
});
test('拒绝无法解析周次，不默默导入部分课程',()=>assert.throws(()=>C.graduate(grad('1,2','2-9(补第12周)'))));
test('拒绝多个课程合并的单元格',()=>{const t=grad();t.grid[1][2]+='\n课程二\n教师乙\n节次:3,4节\n周次:1-4';assert.throws(()=>C.graduate(t));});
test('拒绝缺少星期日的表头',()=>{const t=grad();t.grid[0].pop();assert.throws(()=>C.graduate(t));});
test('拒绝行列错位',()=>{const t=grad();t.grid[1].pop();assert.throws(()=>C.graduate(t));});
test('不忽略缺少节次的非空课程单元格',()=>{const t=grad();t.grid[1][3]='研究生英语';assert.throws(()=>C.graduate(t));});
test('重复DOM表格只导入一次；不同表格不合并',()=>{
 const t=grad();assert.equal(C.parseCapture({kind:'graduate',tables:[t,t]}).courses.length,1);
 assert.throws(()=>C.parseCapture({kind:'graduate',tables:[t,grad('3,4')]}));
});
test('跨域框架失败关闭',()=>assert.throws(()=>C.parseCapture({kind:'graduate',tables:[grad()],unreadableFrames:1})));
test('TT2往返不丢时间与楼名',()=>{const s=C.validate({settings:C.blank().settings,courses:[course]});assert.deepEqual(C.validate(codec.decodeShareCode(codec.encodeShareCode(s))),s);});
test('校验总周与第一周日期',()=>{
 for(const firstWeekDate of ['2026-09-01','2026-02-30','bad'])assert.throws(()=>C.validate({settings:{...C.blank().settings,firstWeekDate},courses:[]}));
 assert.equal(C.validate({settings:{...C.blank().settings,firstWeekDate:'2026-08-31'},courses:[]}).settings.firstWeekDate,'2026-08-31');
 assert.throws(()=>C.validate({settings:{...C.blank().settings,totalWeeks:1},courses:[course]}));
});
test('缺失开学日期不猜测当前周，按本地日历计算',()=>{
 assert.equal(C.currentWeek(C.blank().settings),null);
 assert.equal(C.currentWeek({...C.blank().settings,firstWeekDate:'2026-08-31'},new Date(2026,8,10)),2);
});
test('严格数字集合范围',()=>{for(const v of ['1-0','0','31','1/2','1-2-3','1,'])assert.throws(()=>C.numbers(v,30));assert.deepEqual(C.numbers('1-2,4,4',30),[1,2,4]);});
test('拒绝超过200条与超长字段',()=>{
 assert.throws(()=>C.validate({settings:C.blank().settings,courses:Array(201).fill(course)}));
 assert.throws(()=>C.validate({settings:C.blank().settings,courses:[{...course,name:'课'.repeat(41)}]}));
});
test('横向全览按星期分列，冲突课程不互相遮盖',()=>{
 const a={...course,weekday:1,startSection:1,endSection:2},b={...a,name:'冲突课程',endSection:4},c={...a,name:'第三节课程',startSection:3,endSection:3};
 const layout=C.layoutWeek([a,b,c,{...course,weekday:7}]);assert.equal(layout.length,7);assert.equal(layout[0].lanes,2);assert.equal(layout[6].items.length,1);
 assert.notEqual(layout[0].items.find(x=>x.course===a).lane,layout[0].items.find(x=>x.course===b).lane);
 assert.equal(layout[0].items.find(x=>x.course===a).lane,layout[0].items.find(x=>x.course===c).lane);
});
test('当前课按节次判断，课间与结束边界不标记',()=>{
 const c={...course,weekday:1,startWeek:1,endWeek:20},s={...C.blank().settings,firstWeekDate:'2026-08-31'};
 for(const [time,expected] of [['07:59',false],['08:00',true],['08:49',true],['08:50',false],['08:59',false],['09:00',true],['09:49',true],['09:50',false]])assert.equal(C.active(c,s,new Date('2026-08-31T'+time+':00+08:00')),expected,time);
});
test('当前标记尊重单双周、日期和学期范围',()=>{
 const c={...course,weekday:1,startWeek:1,endWeek:20,weekType:'odd'},s={...C.blank().settings,firstWeekDate:'2026-08-31'};
 assert.equal(C.active(c,s,new Date('2026-09-07T08:10:00+08:00')),false);
 assert.equal(C.active({...c,weekType:'even'},s,new Date('2026-09-07T08:10:00+08:00')),true);
 assert.equal(C.active(c,s,new Date('2026-09-01T08:10:00+08:00')),false);
 assert.equal(C.active(c,C.blank().settings,new Date('2026-08-31T08:10:00+08:00')),false);
 assert.equal(C.active(c,{...s,totalWeeks:1},new Date('2026-09-14T08:10:00+08:00')),false);
});
test('采用北京时间，不随设备时区改变课程日期',()=>{
 assert.equal(C.schoolClock(new Date('2026-08-30T16:30:00Z')).weekday,1);
 assert.equal(C.schoolClock(new Date('2026-08-30T16:30:00Z')).date,'2026-08-31');
});
test('自定义第11节时间能保存，未知时间不猜测',()=>{
 const c={...course,weekday:1,startSection:11,endSection:12,startWeek:1,endWeek:20},s={...C.blank().settings,firstWeekDate:'2026-08-31'};
 const now=new Date('2026-08-31T20:35:00+08:00');assert.equal(C.active(c,s,now),false);
 const periodTimes=structuredClone(C.defaultTimes);periodTimes[10]=['20:30','21:20'];
 const validated=C.validate({settings:{...s,periodTimes},courses:[c]});assert.deepEqual(validated.settings.periodTimes,periodTimes);assert.equal(C.active(c,validated.settings,now),true);
});
test('节次时间必须成对、递增且合法',()=>{
 for(const pair of [['08:00',''],['25:00','26:00'],['08:50','08:00']]){const t=structuredClone(C.defaultTimes);t[0]=pair;assert.throws(()=>C.validateTimes(t));}
 const t=structuredClone(C.defaultTimes);t[1]=['08:30','09:10'];assert.throws(()=>C.validateTimes(t));
});
