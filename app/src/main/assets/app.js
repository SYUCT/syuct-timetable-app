'use strict';
const $=id=>document.getElementById(id), C=AppCore, codec=SYUCTTimetableCodec;
let state=C.blank(), draft=null, selectedDay=(new Date().getDay()||7), selectedWeek=1, allDays=false, activePage='home', messageTimer;
const bridge=window.Native;
function message(text){ clearTimeout(messageTimer); $('message').textContent=text; $('message').hidden=false; messageTimer=setTimeout(()=>$('message').hidden=true,7000); }
function load(){
  try { const raw=bridge?.load(); state=raw?C.validate(JSON.parse(raw)):C.blank(); }
  catch { state=C.blank(); message('已保存课表无法读取。请到设置尝试恢复上一版，勿直接覆盖。'); }
  const w=C.currentWeek(state.settings); selectedWeek=w && w>=1 && w<=state.settings.totalWeeks?w:1;
}
function show(page){
  if(activePage==='review' && page!=='review' && draft){
    if(!confirm('放弃尚未保存的修改？')) return;
    draft=null;
  }
  activePage=page;
  document.querySelectorAll('.screen').forEach(e=>e.hidden=e.id!==page);
  document.querySelectorAll('nav button').forEach(b=>b.classList.toggle('active',b.dataset.page===page));
  if(page==='home') renderHome();
  if(page==='settings') settingsFields($('mainSettings'),state.settings);
  window.scrollTo(0,0);
}
window.goHome=()=>show('home');
function el(tag,text,className){const n=document.createElement(tag); if(text!==undefined)n.textContent=text; if(className)n.className=className; return n;}
function renderHome(){
  $('semesterTitle').textContent=state.settings.semester||'我的课表';
  const current=C.currentWeek(state.settings);
  $('weekHint').textContent=current===null?'未设置开学日期 · 可手动选择教学周':current<1?'学期尚未开始':current>state.settings.totalWeeks?'当前日期已超出本学期':'当前为第 '+current+' 周';
  $('thisWeek').disabled=current===null||current<1||current>state.settings.totalWeeks;
  selectedWeek=Math.min(state.settings.totalWeeks,Math.max(1,selectedWeek));
  $('weekSelect').replaceChildren(...Array.from({length:state.settings.totalWeeks},(_,i)=>{const o=el('option','第 '+(i+1)+' 周');o.value=i+1;return o;}));
  $('weekSelect').value=selectedWeek;
  $('prevWeek').disabled=selectedWeek<=1; $('nextWeek').disabled=selectedWeek>=state.settings.totalWeeks;
  $('days').replaceChildren(...C.weekdays.map((d,i)=>{const b=el('button',d.slice(-1),!allDays&&i+1===selectedDay?'selected':''); b.setAttribute('aria-label',d); b.setAttribute('aria-pressed',String(!allDays&&i+1===selectedDay)); b.onclick=()=>{selectedDay=i+1;allDays=false;renderHome();};return b;}));
  $('toggleAll').textContent=allDays?'单日':'全周'; $('courseList').replaceChildren();
  const courses=state.courses.filter(c=>C.inWeek(c,selectedWeek)).sort((a,b)=>a.weekday-b.weekday||a.startSection-b.startSection);
  const visible=courses.filter(c=>allDays||c.weekday===selectedDay);
  const colors=['#6192c6','#64a79c','#a18dc2','#cc9b5b','#d08388','#7387a9'];
  let lastDay=0;
  for(const c of visible){
    if(allDays&&lastDay!==c.weekday){ $('courseList').append(el('h3',C.weekdays[c.weekday-1],'day-heading'));lastDay=c.weekday; }
    const card=el('article',undefined,'course');card.style.borderLeftColor=colors[c.colorIndex%6];
    const top=el('div',undefined,'course-top');top.append(el('span',c.startSection+'–'+c.endSection+' 节'),el('span',c.startWeek+'–'+c.endWeek+' 周'+({all:'',odd:' · 单周',even:' · 双周'}[c.weekType])));
    card.append(top,el('h3',c.name),el('p',c.room||'地点未提供'),el('p',c.teacher||'教师未提供'));
    $('courseList').append(card);
  }
  $('empty').hidden=state.courses.length>0;
  if(state.courses.length&&!visible.length) $('courseList').append(el('p',allDays?'本周暂无课程。':'这一天没有课程。','empty muted'));
}
function inputField(parent,key,label,value,type='text',options=null){
  const wrap=el('label',label); let input;
  if(options){input=el('select');for(const [v,t] of options){const o=el('option',t);o.value=v;input.append(o);}}
  else {input=el('input');input.type=type;}
  input.dataset.field=key;input.value=value??'';
  if(type==='number'){input.step='1';input.min='1';input.max=key.includes('Section')?'12':key==='weekday'?'7':'30';}
  if(type==='text') input.maxLength=key==='semester'?80:40;
  wrap.append(input);parent.append(wrap);return input;
}
function settingsFields(parent,settings){
  parent.replaceChildren();
  inputField(parent,'semester','学期名称（选填）',settings.semester);
  inputField(parent,'firstWeekDate','第一周的周一（选填）',settings.firstWeekDate,'date');
  inputField(parent,'totalWeeks','学期总周数',settings.totalWeeks,'number');
}
function readSettings(parent){
  const get=k=>parent.querySelector('[data-field="'+k+'"]').value;
  return {semester:get('semester').trim(),firstWeekDate:get('firstWeekDate'),totalWeeks:Number(get('totalWeeks'))};
}
function review(result,settings=state.settings){
  draft={courses:structuredClone(result.courses),settings:{...settings}};
  $('reviewCount').textContent=draft.courses.length+' 条安排 · 请展开课程核对，未确认前不会覆盖现有课表。';
  const notes=['解析成功不代表复制完整，请对照教务课表核对。',...(result.notices||[])];
  $('notices').textContent=[...new Set(notes)].join('\n');
  $('supplemental').hidden=!(result.supplemental||[]).length;
  $('supplementalText').textContent=(result.supplemental||[]).join('\n\n');
  settingsFields($('reviewSettings'),draft.settings);
  $('reviewSettings').oninput=invalidateReview;
  renderEditors(); invalidateReview(); show('review');
}
function invalidateReview(){ $('reviewed').checked=false;$('saveDraft').disabled=true; }
function renderEditors(){
  $('editList').replaceChildren();
  draft.courses.forEach((c,index)=>{
    const details=el('details',undefined,'edit-card'); if(index===0)details.open=true;
    const summary=el('summary',c.name||'新课程');summary.append(el('small',C.weekdays[c.weekday-1]+' · '+c.startSection+'–'+c.endSection+' 节 · '+c.startWeek+'–'+c.endWeek+' 周')); details.append(summary);
    const fields=el('div',undefined,'fields');details.append(fields);
    for(const [key,label] of [['name','课程名称'],['teacher','教师'],['room','教室（含教学楼）']]){inputField(fields,key,label,c[key]).parentElement.classList.add('wide');}
    inputField(fields,'weekday','星期',c.weekday,'number',C.weekdays.map((v,i)=>[i+1,v]));
    inputField(fields,'weekType','单双周',c.weekType,'text',[['all','每周'],['odd','单周'],['even','双周']]);
    for(const [key,label] of [['startSection','开始节次'],['endSection','结束节次'],['startWeek','开始周'],['endWeek','结束周']]) inputField(fields,key,label,c[key],'number');
    fields.addEventListener('input',e=>{
      const key=e.target.dataset.field;if(!key)return;
      c[key]=['weekday','startSection','endSection','startWeek','endWeek'].includes(key)?Number(e.target.value):e.target.value;
      invalidateReview();
    });
    const remove=el('button','删除本条安排','danger'); remove.onclick=()=>{
      if(confirm('删除「'+(c.name||'新课程')+'」这条安排？')){draft.courses.splice(index,1);renderEditors();invalidateReview();$('reviewCount').textContent=draft.courses.length+' 条安排 · 请核对后保存。';}
    }; details.append(remove);$('editList').append(details);
  });
}
window.receiveCapture=packet=>{
  try {const result=C.parseCapture(packet);review(result);}
  catch(e){show('import');message(e.message);}
};
function persist(value){
  const clean=C.validate(value);
  if(!bridge)throw Error('当前是浏览器预览，保存功能仅在安卓 App 内可用。');
  const error=bridge.save(JSON.stringify(clean));if(error)throw Error(error);
  state=clean;return clean;
}
document.querySelectorAll('[data-page]').forEach(b=>b.onclick=()=>show(b.dataset.page));
document.querySelectorAll('[data-school]').forEach(b=>b.onclick=()=>bridge?bridge.school(b.dataset.school):message('请在安卓 App 中打开教务。'));
$('prevWeek').onclick=()=>{selectedWeek--;renderHome();};$('nextWeek').onclick=()=>{selectedWeek++;renderHome();};
$('thisWeek').onclick=()=>{selectedWeek=C.currentWeek(state.settings)||1;renderHome();};
$('weekSelect').onchange=e=>{selectedWeek=Number(e.target.value);renderHome();};
$('toggleAll').onclick=()=>{allDays=!allDays;renderHome();};
$('parseText').onclick=()=>{
  try {
    const text=$('importText').value.trim();if(!text)throw Error('请先粘贴课表文字或课表码。');
    if(codec.isShareCode(text)){const decoded=codec.decodeShareCode(text);review({courses:decoded.courses},decoded.settings);}
    else {
      const r=SYUCTTimetableParser.parseCampusTimetable(text);
      review({courses:r.courses,notices:(r.diagnostics||[]).map(d=>d.message),supplemental:r.sections?[JSON.stringify(r.sections)]:[]});
    }
  }catch(e){message(e.message);}
};
$('reviewed').onchange=()=>{$('saveDraft').disabled=!$('reviewed').checked;};
$('saveDraft').onclick=()=>{
  if(!draft||!$('reviewed').checked)return;
  try{
    draft.settings=readSettings($('reviewSettings'));
    C.validate(draft);
    if(!draft.courses.length)throw Error('请至少保留一条课程安排。');
    if(state.courses.length&&!confirm('用核对后的课表替换当前课表？上一版可在设置中恢复。'))return;
    persist(draft);draft=null;show('home');message('课表已保存，可离线查看。');
  }catch(e){message(e.message);}
};
$('cancelDraft').onclick=()=>show('import');
$('addCourse').onclick=()=>{
  if(draft.courses.length>=200){message('最多保存 200 条安排。');return;}
  draft.courses.push({name:'',teacher:'',room:'',weekday:1,startSection:1,endSection:2,startWeek:1,endWeek:Math.min(16,state.settings.totalWeeks),weekType:'all',colorIndex:draft.courses.length%6});
  renderEditors();invalidateReview();const last=$('editList').lastElementChild;last.open=true;last.scrollIntoView({block:'start'});
};
$('saveSettings').onclick=()=>{
  try{persist({...state,settings:readSettings($('mainSettings'))});message('学期设置已保存。');}catch(e){message(e.message);}
};
$('editSaved').onclick=()=>review({courses:state.courses});
$('exportCode').onclick=()=>{
  try{if(!state.courses.length)throw Error('请先保存课表。');const code=codec.encodeShareCode(C.validate(state));if(bridge)bridge.copy(code);else message('复制功能仅在 App 中可用。');}catch(e){message(e.message);}
};
$('restore').onclick=()=>{
  if(!bridge||!confirm('恢复上一次保存的课表？当前版本也会保留为备份。'))return;
  const error=bridge.restore();if(error){message(error);return;}load();show('home');message('已恢复上一版课表。');
};
$('clearLogin').onclick=()=>bridge?.clearLogin();
load();renderHome();
