(function(root,factory){
  const api=factory(typeof module!=='undefined' ? require('./timetable-codec.js') : root.SYUCTTimetableCodec,
    typeof module!=='undefined' ? require('./timetable-campus-parser.js') : root.SYUCTTimetableParser,
    typeof module!=='undefined' ? require('./section-times.json') : JSON.parse(root.Native?.defaultTimes?.() || '[]'));
  if(typeof module!=='undefined') module.exports=api;
  root.AppCore=api;
})(globalThis,function(codec,undergraduate,defaultTimes){
  'use strict';
  const blank=()=>({settings:{semester:'',firstWeekDate:'',totalWeeks:20},courses:[]});
  const weekdays=['星期一','星期二','星期三','星期四','星期五','星期六','星期日'];
  function validate(state){
    const decoded=codec.decodeShareCode(codec.encodeShareCode(state));
    const s=decoded.settings;
    if(s.firstWeekDate){
      if(!/^\d{4}-\d{2}-\d{2}$/.test(s.firstWeekDate)) throw Error('请选择有效的第一周周一日期。');
      const [y,m,d]=s.firstWeekDate.split('-').map(Number), date=new Date(y,m-1,d);
      if(date.getFullYear()!==y || date.getMonth()!==m-1 || date.getDate()!==d || date.getDay()!==1) throw Error('第一周日期必须是周一。');
    }
    decoded.courses.forEach(c=>{
      if(c.endWeek>s.totalWeeks) throw Error('课程结束周超过学期总周数，请核对。');
      if(c.weekType!=='all' && !Array.from({length:c.endWeek-c.startWeek+1},(_,i)=>i+c.startWeek).some(w=>w%2===(c.weekType==='odd'?1:0))) throw Error('单双周与课程周次不符。');
      if([c.name,c.teacher,c.room].some(v=>v.length>40)) throw Error('课程名、教师、教室分别最多 40 字，以兼容小程序。');
    });
    s.periodTimes=validateTimes(state.settings?.periodTimes || defaultTimes);
    return {settings:s,courses:decoded.courses};
  }
  function ranges(values,step=1){
    const out=[];
    [...new Set(values)].sort((a,b)=>a-b).forEach(n=>{
      const prev=out[out.length-1]; if(prev && prev[1]+step===n) prev[1]=n; else out.push([n,n]);
    }); return out;
  }
  // Exact sets, not a min/max envelope: 1,3 or 2-5,8-10 must not fill gaps.
  function numbers(value,max){
    const text=value.replace(/\s/g,'').replace(/[，、]/g,',').replace(/[—–~～至]/g,'-');
    if(!/^\d{1,2}(?:-\d{1,2})?(?:,\d{1,2}(?:-\d{1,2})?)*$/.test(text)) throw Error('时间格式暂不支持：'+value);
    const result=[];
    text.split(',').forEach(part=>{
      const [a,b=a]=part.split('-').map(Number);
      if(a<1||b<a||b>max) throw Error('时间范围超出限制：'+value);
      for(let i=a;i<=b;i++) result.push(i);
    }); return [...new Set(result)].sort((a,b)=>a-b);
  }
  function graduate(table){
    const g=table.grid;
    if(!Array.isArray(g) || g.length>80) throw Error('硕士课表网格无效。');
    const day=v=>weekdays.indexOf(String(v).trim().replace(/^周/,'星期').replace('星期天','星期日'))+1;
    const hi=g.findIndex(row=>Array.isArray(row) && new Set(row.map(day).filter(Boolean)).size===7);
    if(hi<0) throw Error('硕士课表缺少完整星期表头，请进入「我的课程表」后重试。');
    const header=g[hi], mapping=header.map(day);
    if(mapping.filter(Boolean).length!==7) throw Error('硕士课表表头重复，暂不支持此布局。');
    const courses=[], notices=[], seen=new Set();
    let blocks=0;
    for(const row of g.slice(hi+1)){
      if(!Array.isArray(row)) throw Error('课表行数据无效。');
      for(let col=0;col<row.length;col++){
        const text=String(row[col]||'').replace(/\r/g,'').trim();
        if(!text) continue;
        if(!/节次\s*[:：]/.test(text)) {
          if(mapping[col] && !/^(?:星期|周)[一二三四五六日天]$/.test(text)) throw Error('硕士课表有未标明节次的内容，未导入部分结果。');
          continue;
        }
        if(row.length!==header.length || !mapping[col]) throw Error('课表行列无法对应，已停止导入。');
        const key=mapping[col]+'|'+text;
        if(seen.has(key)) continue; seen.add(key);
        const n=(text.match(/节次\s*[:：]/g)||[]).length;
        if(n!==1) throw Error('一个单元格内有多门硕士课程，需进一步适配，未导入部分结果。');
        blocks++;
        const section=text.match(/节次\s*[:：]\s*([^\n]*?)(?:节|\n|$)/);
        const week=text.match(/周次\s*[:：]\s*([^\n]+)/);
        if(!section||!week) throw Error('课程缺少节次或周次。');
        const periods=numbers(section[1],12);
        let w=week[1].trim(), mode=/单/.test(w)?'odd':/双/.test(w)?'even':'all';
        if(/单/.test(w)&&/双/.test(w)) throw Error('同一周次同时出现单双周，请手动核对。');
        // Teacher annotations such as 2-9(董童) are retained as a diagnostic.
        const annotation=w.match(/[（(]([^()（）]+)[）)]/);
        if(annotation && !/^(单|双)周?$/.test(annotation[1])){
          if(!/^[\u4e00-\u9fff·\s]+$/.test(annotation[1])) throw Error('周次备注无法安全解析：'+w);
          notices.push('周次备注「'+annotation[1]+'」请核对教师及时间。');
        }
        w=w.replace(/[（(][^()（）]+[）)]/g,'').replace(/[单双周|]/g,'');
        const weeks=numbers(w,30).filter(n=>mode==='all'||n%2===(mode==='odd'?1:0));
        if(!weeks.length) throw Error('课程没有有效教学周。');
        const head=text.slice(0,section.index).split('\n').map(v=>v.trim()).filter(Boolean);
        if(head.length<2) throw Error('硕士课程名称与教师无法分离，请反馈课表样本。');
        const teacherLine=head.pop(), name=head.join('');
        const teacherMatch=teacherLine.match(/^([\u4e00-\u9fff·]{2,6})(?:\s|\d.*班)/);
        const teacher=teacherMatch?teacherMatch[1]:teacherLine;
        if(!teacherMatch) notices.push(name+'：教师可能包含班级信息，请核对。');
        const rm=text.match(/地点\s*[:：]\s*([\s\S]*?)(?=开课院系\s*[:：]|电话\s*[:：]|$)/);
        const room=rm?rm[1].replace(/\s+/g,' ').trim():'';
        if(!room) notices.push(name+'：未提供上课地点。');
        ranges(periods).forEach(([a,b])=>ranges(weeks,mode==='all'?1:2).forEach(([x,y])=>courses.push({name,teacher,room,weekday:mapping[col],startSection:a,endSection:b,startWeek:x,endWeek:y,weekType:mode,colorIndex:courses.length%6})));
      }
    }
    if(!blocks) throw Error('硕士课表没有可读取的课程。');
    return {courses,notices,blocks};
  }
  function parseCapture(packet){
    if(!packet || !['undergraduate','graduate'].includes(packet.kind) || !Array.isArray(packet.tables) || !packet.tables.length || packet.tables.length>12) throw Error('读取结果无效，请重新读取。');
    if(packet.unreadableFrames) throw Error('页面有无法读取的框架，暂不能确认课表完整，请反馈页面以适配。');
    const results=packet.tables.map(t=>{
      if(packet.kind==='graduate') return graduate(t);
      const r=undergraduate.parseCampusTimetable(String(t.text||''),{html:String(t.html||'')});
      if(r.meta?.hasBlockingErrors || !r.courses.length) throw Error('课表仍有未解决记录，未导入部分结果。');
      return {courses:r.courses,notices:(r.diagnostics||[]).map(d=>d.message),sections:r.sections,
        blocks:r.stats?.parsedRecords || r.courses.length};
    });
    const canonical=r=>JSON.stringify(r.courses.map(c=>[c.name,c.teacher,c.room,c.weekday,c.startSection,c.endSection,c.startWeek,c.endWeek,c.weekType]).sort());
    if(new Set(results.map(canonical)).size>1) throw Error('页面包含多份不同课表，请只打开当前学期个人课表。');
    const result=results[0];
    result.notices=[...new Set(result.notices)];
    result.supplemental=Array.isArray(packet.supplemental)?packet.supplemental.map(String):[];
    if(result.sections?.length) result.supplemental.push(JSON.stringify(result.sections));
    if(result.courses.length>200) throw Error('课程安排超过 200 条限制。');
    return result;
  }
  function schoolClock(now=new Date()){
    const p=Object.fromEntries(new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(now).map(p=>[p.type,p.value]));
    const day=Date.UTC(+p.year,+p.month-1,+p.day);
    return {date:`${p.year}-${p.month}-${p.day}`,day,weekday:new Date(day).getUTCDay()||7,minute:+p.hour*60 + +p.minute,time:`${p.hour}:${p.minute}`};
  }
  function currentWeek(settings,now=new Date()){
    if(!settings.firstWeekDate) return null;
    const [y,m,d]=settings.firstWeekDate.split('-').map(Number);
    const today=schoolClock(now).day;
    return Math.floor((today-Date.UTC(y,m-1,d))/604800000)+1;
  }
  function inWeek(c,w){return c.startWeek<=w && w<=c.endWeek && (c.weekType==='all' || w%2===(c.weekType==='odd'?1:0));}
  function minute(t){if(!/^([01]\d|2[0-3]):[0-5]\d$/.test(t))throw Error('上课时间应为有效的时:分。');const [h,m]=t.split(':').map(Number);return h*60+m;}
  function validateTimes(times){
    if(!Array.isArray(times)||times.length!==12)throw Error('节次时间表必须有 12 行。');
    let previous=-1;
    return times.map((pair,i)=>{
      if(!Array.isArray(pair)||pair.length!==2)throw Error('第 '+(i+1)+' 节时间格式不正确。');
      const [a,b]=pair;if(a===''&&b==='')return ['',''];
      const start=minute(a),end=minute(b);
      if(end<=start||start<previous)throw Error('第 '+(i+1)+' 节时间重叠或顺序不正确。');previous=end;return [a,b];
    });
  }
  function active(c,settings,now=new Date()){
    const clock=schoolClock(now),w=currentWeek(settings,now);
    if(w===null||w<1||w>settings.totalWeeks||c.weekday!==clock.weekday||!inWeek(c,w))return false;
    const times=settings.periodTimes||defaultTimes;
    for(let s=c.startSection;s<=c.endSection;s++){
      const pair=times[s-1];if(pair?.[0]&&pair?.[1]&&clock.minute>=minute(pair[0])&&clock.minute<minute(pair[1]))return true;
    }return false;
  }
  function layoutWeek(courses){
    const output=[];
    for(let d=1;d<=7;d++){
      const ends=[],items=courses.filter(c=>c.weekday===d).slice().sort((a,b)=>a.startSection-b.startSection||a.endSection-b.endSection);
      const placed=items.map(course=>{let lane=ends.findIndex(end=>end<course.startSection);if(lane<0)lane=ends.length;ends[lane]=course.endSection;return {course,lane};});
      output.push({weekday:d,lanes:Math.max(1,ends.length),items:placed});
    }return output;
  }
  return {blank,validate,graduate,parseCapture,numbers,ranges,currentWeek,inWeek,weekdays,defaultTimes,schoolClock,validateTimes,active,layoutWeek};
});
