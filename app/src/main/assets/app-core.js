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
  function weekSet(value){
    const annotations=[...value.matchAll(/[（(]([^()（）]+)[）)]/g)].map(m=>m[1].trim());
    let text=value.replace(/[（(][^()（）]+[）)]/g,'');
    const teachers=[];
    for(const a of annotations){
      if(/^[单双]周?$/.test(a)) text+=a;
      else if(/^[\u4e00-\u9fff·\s/、，,]+$/.test(a) && !/[周节补调]/.test(a)) teachers.push(a);
      else throw Error('周次备注无法安全解析：'+value);
    }
    if(/单/.test(text)&&/双/.test(text)) throw Error('同一周次同时出现单双周，请手动核对。');
    const mode=/单/.test(text)?'odd':/双/.test(text)?'even':'all';
    const weeks=numbers(text.replace(/[单双周|]/g,''),30).filter(n=>mode==='all'||n%2===(mode==='odd'?1:0));
    if(!weeks.length) throw Error('课程没有有效教学周。');
    return {weeks,mode,teachers};
  }
  // The portal ends each record with 电话:, including records without a number.
  // Require that boundary for stacked records; never split on course-name guesses.
  function graduateBlocks(text){
    if((text.match(/节次\s*[:：]/g)||[]).length===1){
      const end=text.match(/电话\s*[:：][^\n]*(?:\n|$)/);
      if(end&&text.slice(end.index+end[0].length).trim()) throw Error('课程末尾存在未解析内容，未导入部分结果。');
      return [text];
    }
    const blocks=[];let start=0;
    for(const m of text.matchAll(/电话\s*[:：][^\n]*(?:\n|$)/g)){
      blocks.push(text.slice(start,m.index+m[0].length).trim());start=m.index+m[0].length;
    }
    if(text.slice(start).trim()) blocks.push(text.slice(start).trim());
    if(blocks.length<2 || blocks.some(b=>(b.match(/节次\s*[:：]/g)||[]).length!==1))
      throw Error('同格多门课程的分隔不完整，请重新打开「我的课程表」读取；未导入部分结果。');
    return blocks;
  }
  function graduateHeading(text,teachers,notices){
    const head=text.split('\n').map(v=>v.trim()).filter(Boolean);
    const classOnly=/^(?:全日制|非全日制)?\d+\s*班/;
    let split=head.findIndex((line,i)=>i>0 && (classOnly.test(line) || /^[\u4e00-\u9fff·]{2,6}\s+\S/.test(line)));
    if(split<0 && head.length===2) split=1;
    if(split<1) throw Error('硕士课程名称与教师无法分离，请反馈课表样本。');
    const name=head.slice(0,split).join(''), teacherLine=head.slice(split).join(' ');
    const match=teacherLine.match(/^([\u4e00-\u9fff·]{2,6})(?:\s|(?=\d.*班)|$)/);
    let teacher=match&&!classOnly.test(teacherLine)?match[1]:'';
    if(teachers.length){
      teacher=[...new Set(teachers)].join('/');
      notices.push(name+'：教师取自周次备注「'+teacher+'」，请核对。');
    }else if(!teacher) notices.push(name+'：未提供明确教师，已留空；班级名称不作为教师导入。');
    return {name,teacher};
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
        for(const block of graduateBlocks(text)){
        const key=mapping[col]+'|'+block;
        if(seen.has(key)) continue; seen.add(key);
        blocks++;
        const section=block.match(/节次\s*[:：]\s*([^\n]*?)(?:节|\n|$)/);
        const week=block.match(/周次\s*[:：]\s*([^\n]+)/);
        if(!section||!week) throw Error('课程缺少节次或周次。');
        const periods=numbers(section[1],12);
        const {weeks,mode,teachers}=weekSet(week[1]);
        const {name,teacher}=graduateHeading(block.slice(0,section.index),teachers,notices);
        const rm=block.match(/地点\s*[:：]\s*([\s\S]*?)(?=开课院系\s*[:：]|电话\s*[:：]|$)/);
        const room=rm?rm[1].replace(/\s+/g,' ').trim():'';
        if(!room) notices.push(name+'：未提供上课地点。');
        ranges(periods).forEach(([a,b])=>ranges(weeks,mode==='all'?1:2).forEach(([x,y])=>courses.push({name,teacher,room,weekday:mapping[col],startSection:a,endSection:b,startWeek:x,endWeek:y,weekType:mode,colorIndex:courses.length%6})));
        }
      }
    }
    if(!blocks) throw Error('硕士课表没有可读取的课程。');
    return {courses,notices,blocks};
  }
  function undergraduateHome(table){
    const g=table.grid, day=v=>weekdays.indexOf(String(v||'').trim())+1;
    if(!Array.isArray(g)||g.length>80) throw Error('本科首页课表网格无效。');
    const rows=g.filter(row=>Array.isArray(row)&&day(row[0]));
    if(rows.length!==7 || new Set(rows.map(row=>day(row[0]))).size!==7)
      throw Error('首页课表未包含完整七天，请进入「学生个人课表」读取。');
    if(g.some(row=>!Array.isArray(row)||(!day(row[0])&&/节\s*[（(]/.test(row.join(' ')))))
      throw Error('首页有无法对应星期的课程，未导入部分结果。');
    const courses=[],notices=['来自本科首页：教师信息未提供，已留空。请核对课程是否完整；如有缺漏，请读取「学生个人课表」。'],seen=new Set();
    for(const row of rows) for(const raw of row.slice(1)){
      const text=String(raw||'').trim();if(!text)continue;
      const anchors=[...text.matchAll(/(\d{1,2}(?:\s*[-—–,，、]\s*\d{1,2})*)\s*节\s*[（(]([^()（）]+)[）)]/g)];
      if(!anchors.length||text.slice(0,anchors[0].index).trim()) throw Error('首页课程时间不完整，未导入部分结果。');
      for(let i=0;i<anchors.length;i++){
        const m=anchors[i],body=text.slice(m.index+m[0].length,anchors[i+1]?.index??text.length).trim();
        if(!body||/节\s*[（(]|周[一二三四五六日天]\s*第/.test(body)) throw Error('首页有未解析的课程时间，未导入部分结果。');
        const parts=body.split('\n').map(s=>s.trim()).filter(Boolean);
        let name,room;
        if(parts.length>1){name=parts[0];room=parts.slice(1).join('');}
        else{
          const venue=body.search(/通明楼|敬仲楼|应星楼|思远楼|瑞师楼|鸣龙楼|致本楼|羽网中心/);
          name=venue>0?body.slice(0,venue):body;room=venue>0?body.slice(venue):'';
        }
        if(!name) throw Error('首页课程名称缺失，未导入部分结果。');
        const periods=numbers(m[1],12), {weeks,mode,teachers}=weekSet(m[2]);
        if(teachers.length) throw Error('首页周次格式暂不支持，请读取学生个人课表。');
        if(!room)notices.push(name+'：未提供上课地点，请核对。');
        ranges(periods).forEach(([a,b])=>ranges(weeks,mode==='all'?1:2).forEach(([x,y])=>{
          const c={name,teacher:'',room,weekday:day(row[0]),startSection:a,endSection:b,startWeek:x,endWeek:y,weekType:mode,colorIndex:courses.length%6};
          const key=JSON.stringify([name,room,c.weekday,a,b,x,y,mode]);if(!seen.has(key)){seen.add(key);courses.push(c);}
        }));
      }
    }
    if(!courses.length)throw Error('首页课表没有可读取的课程。');
    return {courses,notices,blocks:courses.length};
  }
  function parseCapture(packet){
    if(!packet || !['undergraduate','graduate'].includes(packet.kind) || !Array.isArray(packet.tables) || !packet.tables.length || packet.tables.length>12) throw Error('读取结果无效，请重新读取。');
    if(packet.unreadableFrames) throw Error('页面有无法读取的框架，暂不能确认课表完整，请反馈页面以适配。');
    // Full personal timetable takes precedence when the portal keeps its home
    // summary mounted behind the current page. Do not merge different sources.
    const full=packet.tables.filter(t=>t.source!=='undergraduate-home');
    const tables=packet.kind==='undergraduate'&&full.length?full:packet.tables;
    const results=tables.map(t=>{
      if(packet.kind==='graduate') return graduate(t);
      if(t.source==='undergraduate-home') return undergraduateHome(t);
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
  return {blank,validate,graduate,undergraduateHome,parseCapture,numbers,ranges,currentWeek,inWeek,weekdays,defaultTimes,schoolClock,validateTimes,active,layoutWeek};
});
