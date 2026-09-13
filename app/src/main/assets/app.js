'use strict';
const $=id=>document.getElementById(id), C=AppCore, codec=SYUCTTimetableCodec;
let state=C.blank(), draft=null, selectedDay=C.schoolClock().weekday, selectedWeek=1, allDays=false, activePage='home', messageTimer;
let allWeeks=false,detailIndex=-1,widgetEntry=false;
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
  updateViewMode();
  document.querySelectorAll('.screen').forEach(e=>e.hidden=e.id!==page);
  document.querySelectorAll('nav button').forEach(b=>b.classList.toggle('active',b.dataset.page===page));
  if(page==='home') renderHome();
  if(page==='settings') { settingsFields($('mainSettings'),state.settings); renderTimes(); }
  window.scrollTo(0,0);
}
function closeOverview(){if(widgetEntry&&bridge?.backToDesktop){widgetEntry=false;bridge.backToDesktop();return;}allDays=false;show('home');}
window.setWidgetEntry=value=>{widgetEntry=value===true;};
window.goHome=()=>{if($('miniProgramDialog').open){$('miniProgramDialog').close();return;}if($('communityDialog').open){$('communityDialog').close();return;}if($('courseDetail').open){$('courseDetail').close();return;}if($('firstWeekDialog').open){$('firstWeekDialog').close();return;}if(activePage==='home'&&allDays){closeOverview();return;}if(activePage==='home'&&bridge?.backToDesktop){bridge.backToDesktop();return;}allDays=false;show('home');};
window.openOverview=(fromWidget=false)=>{widgetEntry=fromWidget===true;allDays=true;allWeeks=false;const w=C.currentWeek(state.settings);if(w>=1&&w<=state.settings.totalWeeks)selectedWeek=w;show('home');};
window.refreshClock=()=>{if(activePage==='home')renderHome();};
function el(tag,text,className){const n=document.createElement(tag); if(text!==undefined)n.textContent=text; if(className)n.className=className; return n;}
function updateViewMode(){
  const overview=activePage==='home'&&allDays;
  document.body.classList.toggle('home-mode',activePage==='home');
  document.body.classList.toggle('overview-mode',overview);
  $('overviewToolbar').hidden=!overview;
  $('overviewRange').hidden=!overview;
}
function renderHome(){
  updateViewMode();
  $('semesterTitle').textContent=state.settings.semester||'我的课表';
  const current=C.currentWeek(state.settings);
  $('weekHint').textContent=current===null?'未设置开学日期 · 可手动选择教学周':current<1?'学期尚未开始':current>state.settings.totalWeeks?'当前日期已超出本学期':'当前为第 '+current+' 周';
  $('setFirstWeek').textContent=state.settings.firstWeekDate?'第一周 '+state.settings.firstWeekDate+' · 修改':'设置第一周 ›';
  $('thisWeek').disabled=current===null||current<1||current>state.settings.totalWeeks;
  selectedWeek=Math.min(state.settings.totalWeeks,Math.max(1,selectedWeek));
  $('weekSelect').replaceChildren(...Array.from({length:state.settings.totalWeeks},(_,i)=>{const o=el('option','第 '+(i+1)+' 周');o.value=i+1;return o;}));
  $('weekSelect').value=selectedWeek;
  $('overviewTitle').textContent='课表全览';
  $('overviewTitle').title=state.settings.semester||'化大课表';
  $('overviewWeek').replaceChildren(...Array.from($('weekSelect').options,o=>o.cloneNode(true)));
  $('overviewWeek').value=selectedWeek;
  $('overviewPrev').disabled=allWeeks||selectedWeek<=1;$('overviewNext').disabled=allWeeks||selectedWeek>=state.settings.totalWeeks;
  $('overviewWeek').disabled=allWeeks;$('overviewNow').disabled=false;
  $('overviewNow').textContent='切换';$('overviewNow').setAttribute('aria-label',allWeeks?'切换为本周课表':'切换为全部课表');$('overviewNow').setAttribute('aria-pressed',String(allWeeks));
  $('overviewRange').textContent=allWeeks?'全部安排 · 第 1–'+state.settings.totalWeeks+' 周 · 点击课程可修改':(selectedWeek===current?'本周':'第 '+selectedWeek+' 周')+(selectedWeek===current?' · 第 '+selectedWeek+' 周':'')+' · 点击课程可修改';
  $('prevWeek').disabled=selectedWeek<=1; $('nextWeek').disabled=selectedWeek>=state.settings.totalWeeks;
  $('days').replaceChildren(...C.weekdays.map((d,i)=>{const b=el('button',d.slice(-1),!allDays&&i+1===selectedDay?'selected':''); b.setAttribute('aria-label',d); b.setAttribute('aria-pressed',String(!allDays&&i+1===selectedDay)); b.onclick=()=>{selectedDay=i+1;allDays=false;renderHome();};return b;}));
  $('toggleAll').textContent=allDays?'单日':'全览'; $('courseList').replaceChildren();
  $('days').hidden=allDays;$('courseList').hidden=allDays;$('weekOverview').hidden=!allDays;$('gridHint').hidden=!allDays;
  const courses=state.courses.filter(c=>allDays&&allWeeks||C.inWeek(c,selectedWeek)).sort((a,b)=>a.weekday-b.weekday||a.startSection-b.startSection);
  const visible=courses.filter(c=>allDays||c.weekday===selectedDay);
  const colors=['#6192c6','#64a79c','#a18dc2','#cc9b5b','#d08388','#7387a9'];
  let lastDay=0;
  for(const c of visible){
    if(allDays&&lastDay!==c.weekday){ $('courseList').append(el('h3',C.weekdays[c.weekday-1],'day-heading'));lastDay=c.weekday; }
    const card=el('article',undefined,'course');card.style.borderLeftColor=colors[c.colorIndex%6];
    if(current>=1&&current<=state.settings.totalWeeks&&!C.inWeek(c,current)){card.classList.add('is-outside-week');card.append(el('span','非本周','outside-week-badge'));}
    if(selectedWeek===current&&C.active(c,state.settings)){card.classList.add('is-current');card.append(el('span','● 当前正在上课','current-badge'));}
    const top=el('div',undefined,'course-top');top.append(el('span',c.startSection+'–'+c.endSection+' 节'),el('span',c.startWeek+'–'+c.endWeek+' 周'+({all:'',odd:' · 单周',even:' · 双周'}[c.weekType])));
    card.append(top,el('h3',c.name),el('p',c.room||'地点未提供'),el('p',c.teacher||'教师未提供'));
    $('courseList').append(card);
  }
  $('empty').hidden=state.courses.length>0;
  if(state.courses.length&&!visible.length) $('courseList').append(el('p',allDays?'本周暂无课程。':'这一天没有课程。','empty muted'));
  if(allDays)renderOverview(courses,current);
}
function renderOverview(courses,current){
  const oldTop=$('weekOverview').scrollTop,grid=el('div',undefined,'week-grid'),layout=C.layoutWeek(courses);
  const sections=Math.max(10,...courses.map(c=>c.endSection));
  const rowHeight=Math.max(72,Math.floor(($('weekOverview').clientHeight-30)/sections));
  grid.style.setProperty('--section-height',rowHeight+'px');
  grid.style.gridTemplateColumns='42px repeat(7,minmax(0,1fr))';
  const time=el('div',undefined,'week-time-column');time.append(el('div','节次','week-heading'));
  for(let i=1;i<=sections;i+=2) {
    const end=Math.min(i+1,sections),times=state.settings.periodTimes||C.defaultTimes;
    const row=el('div',i+'–'+end,'week-time');row.style.height=((end-i+1)*rowHeight)+'px';
    const startTime=times[i-1]?.[0],endTime=times[end-1]?.[1];
    if(startTime&&endTime){
      row.append(el('small',startTime),el('span','–','time-separator'),el('small',endTime));
      row.setAttribute('aria-label','第 '+i+'–'+end+' 节，'+startTime+'–'+endTime);
    }else row.append(el('small','待设置'));
    time.append(row);
  }grid.append(time);
  for(const day of layout){
    const col=el('div',undefined,'week-column');const heading=el('div',C.weekdays[day.weekday-1].replace('星期','周'),'week-heading');
    if((allWeeks||selectedWeek===current)&&day.weekday===C.schoolClock().weekday)heading.classList.add('is-today');col.append(heading);
    const body=el('div',undefined,'week-day-body');body.style.height=sections*rowHeight+'px';
    for(const {course:c,lane} of day.items){
      const card=el('button',undefined,'week-course');card.style.top=((c.startSection-1)*rowHeight+3)+'px';card.style.height=((c.endSection-c.startSection+1)*rowHeight-6)+'px';card.style.left=(lane*100/day.lanes)+'%';card.style.width=(100/day.lanes)+'%';
      card.setAttribute('aria-label',c.name+'，'+C.weekdays[c.weekday-1]+'，点击查看详情');
      const outsideWeek=current>=1&&current<=state.settings.totalWeeks&&!C.inWeek(c,current);
      card.onclick=()=>{detailIndex=state.courses.indexOf(c);$('detailRead').hidden=false;$('detailEdit').hidden=true;$('detailName').textContent=c.name;$('detailStatus').textContent=outsideWeek?'非本周课程':current>=1&&current<=state.settings.totalWeeks?'本周课程':'教学周待确认';$('detailStatus').className=outsideWeek?'outside-week-badge':'detail-status';$('detailTime').textContent=C.weekdays[c.weekday-1]+' · 第 '+c.startSection+'–'+c.endSection+' 节';$('detailWeeks').textContent=c.startWeek+'–'+c.endWeek+'周'+({all:'',odd:'（单周）',even:'（双周）'}[c.weekType]);$('detailRoom').textContent=c.room||'地点待定';$('detailTeacher').textContent=c.teacher||'教师未提供';$('courseDetail').showModal();};
      if(outsideWeek){card.classList.add('is-outside-week');card.append(el('span','非本周','outside-week-badge'));}
      if((allWeeks||selectedWeek===current)&&C.active(c,state.settings)){card.classList.add('is-current');card.append(el('span','● 当前课','current-badge'));}
      const colors=[['#e6effb','#294e7c'],['#e0f1ec','#276351'],['#eee8f8','#645084'],['#fbefd9','#80612d'],['#f8e6eb','#854456'],['#e5edf2','#445f76']];
      if(!card.classList.contains('is-current')){const palette=colors[c.colorIndex%colors.length];card.style.background=palette[0];card.style.color=palette[1];}
      const room=(c.room||'地点待定').replace(/[（(][^）)]*[）)]/g,'').replace(/\s+/g,'');
      card.append(el('strong',c.name),el('small',room));
      body.append(card);
    }col.append(body);grid.append(col);
  }$('weekOverview').replaceChildren(grid);$('weekOverview').scrollTop=oldTop;
}
function renderTimes(){
  $('periodTimes').replaceChildren();(state.settings.periodTimes||C.defaultTimes).forEach((pair,i)=>{
    const row=el('div',undefined,'time-row');row.append(el('span','第 '+(i+1)+' 节'));
    for(let j=0;j<2;j++){const input=el('input');input.type='time';input.value=pair[j];input.setAttribute('aria-label','第 '+(i+1)+' 节'+(j?'结束':'开始'));row.append(input);}$('periodTimes').append(row);
  });
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
  draft={courses:structuredClone(result.courses),settings:{...settings,periodTimes:settings.periodTimes||state.settings.periodTimes||C.defaultTimes}};
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
$('closeOverview').onclick=closeOverview;
$('closeDetail').onclick=()=>$('courseDetail').close();
$('editDetail').onclick=()=>{
  const c=state.courses[detailIndex];if(!c)return;
  const fields=$('detailFields');fields.replaceChildren();
  for(const [key,label] of [['name','课程名称'],['teacher','教师'],['room','教室（含教学楼）']])inputField(fields,key,label,c[key]).parentElement.classList.add('wide');
  inputField(fields,'weekday','星期',c.weekday,'number',C.weekdays.map((v,i)=>[i+1,v]));
  inputField(fields,'weekType','单双周',c.weekType,'text',[['all','每周'],['odd','单周'],['even','双周']]);
  for(const [key,label] of [['startSection','开始节次'],['endSection','结束节次'],['startWeek','开始周'],['endWeek','结束周']])inputField(fields,key,label,c[key],'number');
  $('detailError').textContent='';$('detailRead').hidden=true;$('detailEdit').hidden=false;$('courseDetail').scrollTop=0;
};
$('cancelDetailEdit').onclick=()=>{$('detailEdit').hidden=true;$('detailRead').hidden=false;};
$('saveDetail').onclick=()=>{
  try{
    if(detailIndex<0||!state.courses[detailIndex])throw Error('课程已变化，请重新打开。');
    const next=structuredClone(state),course=next.courses[detailIndex];
    $('detailFields').querySelectorAll('[data-field]').forEach(i=>{const k=i.dataset.field;course[k]=['weekday','startSection','endSection','startWeek','endWeek'].includes(k)?Number(i.value):i.value.trim();});
    persist(next);$('courseDetail').close();renderHome();message('修改已保存，小组件同步更新。');
  }catch(e){$('detailError').textContent=e.message;}
};
// A completed hold is the confirmation; taps, scrolling and lost focus never delete.
function setupHoldDelete(){
  const button=$('deleteDetail');let hold=null,frame=0;
  function cancel(){
    const previous=hold;hold=null;cancelAnimationFrame(frame);
    button.style.removeProperty('--hold-progress');button.textContent='长按删除本条安排';
    if(previous?.pointerId!==undefined&&button.hasPointerCapture(previous.pointerId))button.releasePointerCapture(previous.pointerId);
  }
  function tick(){
    if(!hold)return;
    if(!$('courseDetail').open||$('detailEdit').hidden||state.courses[detailIndex]!==hold.course){cancel();return;}
    const progress=Math.min(1,(performance.now()-hold.start)/1200);
    button.style.setProperty('--hold-progress',String(progress));
    if(progress<1){frame=requestAnimationFrame(tick);return;}
    cancel();
    try{
      const next=structuredClone(state);next.courses.splice(detailIndex,1);
      persist(next);detailIndex=-1;$('courseDetail').close();renderHome();
      message('本条安排已删除。可在设置中恢复上一次保存。');
    }catch(e){$('detailError').textContent=e.message;}
  }
  function begin(extra){
    if(hold||!$('courseDetail').open||$('detailEdit').hidden||!state.courses[detailIndex])return;
    hold={...extra,course:state.courses[detailIndex],start:performance.now()};
    button.textContent='继续按住，松手取消';frame=requestAnimationFrame(tick);
  }
  button.addEventListener('pointerdown',e=>{
    if(e.button!==0||!e.isPrimary)return;
    begin({pointerId:e.pointerId,x:e.clientX,y:e.clientY});
    if(hold)button.setPointerCapture(e.pointerId);
  });
  button.addEventListener('pointermove',e=>{
    if(!hold||hold.pointerId!==e.pointerId)return;
    const r=button.getBoundingClientRect();
    if(Math.hypot(e.clientX-hold.x,e.clientY-hold.y)>12||e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)cancel();
  });
  for(const type of ['pointerup','pointercancel','lostpointercapture','blur'])button.addEventListener(type,cancel);
  button.addEventListener('contextmenu',e=>e.preventDefault());
  button.addEventListener('keydown',e=>{if(e.key===' '||e.key==='Enter'){e.preventDefault();if(!e.repeat)begin({key:e.key});}else cancel();});
  button.addEventListener('keyup',cancel);
  button.addEventListener('click',e=>e.preventDefault());
  for(const type of ['close','cancel','scroll'])$('courseDetail').addEventListener(type,cancel);
  window.addEventListener('blur',cancel);
  document.addEventListener('visibilitychange',()=>{if(document.hidden)cancel();});
}
setupHoldDelete();
$('overviewPrev').onclick=()=>{selectedWeek--;renderHome();};$('overviewNext').onclick=()=>{selectedWeek++;renderHome();};
$('overviewNow').onclick=()=>{allWeeks=!allWeeks;if(!allWeeks){const w=C.currentWeek(state.settings);if(w>=1&&w<=state.settings.totalWeeks)selectedWeek=w;}renderHome();};
$('overviewWeek').onchange=e=>{selectedWeek=Number(e.target.value);renderHome();};
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
    draft.settings={...draft.settings,...readSettings($('reviewSettings'))};
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
  try{persist({...state,settings:{...state.settings,...readSettings($('mainSettings'))}});message('学期设置已保存。');}catch(e){message(e.message);}
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
$('openCommunity').onclick=()=>$('communityDialog').showModal();
$('openMiniProgram').onclick=()=>$('miniProgramDialog').showModal();
$('closeMiniProgram').onclick=()=>$('miniProgramDialog').close();
$('closeCommunity').onclick=()=>$('communityDialog').close();
document.querySelectorAll('[data-community]').forEach(b=>b.onclick=()=>{if(bridge?.community)bridge.community(b.dataset.community);else message('请在新版安卓 App 中使用此入口。');});
$('setFirstWeek').onclick=()=>{$('quickFirstWeek').value=state.settings.firstWeekDate||'';$('firstWeekError').textContent='';$('firstWeekDialog').showModal();};
$('cancelFirstWeek').onclick=()=>$('firstWeekDialog').close();
$('confirmFirstWeek').onclick=()=>{
  try{const value=$('quickFirstWeek').value;if(!value)throw Error('请选择第一周的周一。');persist({...state,settings:{...state.settings,firstWeekDate:value}});$('firstWeekDialog').close();selectedWeek=Math.max(1,Math.min(state.settings.totalWeeks,C.currentWeek(state.settings)||1));renderHome();message('第一周日期已保存。');}
  catch(e){$('firstWeekError').textContent=e.message;}
};
$('addWidget').onclick=()=>bridge?.addWidget?bridge.addWidget():message('请长按安卓桌面空白处，在小组件中选择「化大课表」。');
$('reminderSettings').onclick=()=>bridge?.reminderSettings?bridge.reminderSettings():message('请在新版安卓 App 中设置上课提醒。');
let updatePending=false,updateTimer,updateRequestId=0,updateAvailableCode=0;
if(bridge?.appVersion)$('installedVersion').textContent='当前版本 '+bridge.appVersion();
window.receiveUpdateCheck=result=>{
  if(!updatePending||result?.requestId!==updateRequestId)return;
  updatePending=false;clearTimeout(updateTimer);$('checkUpdate').disabled=false;$('checkUpdate').textContent='检测更新';
  const available=result?.status==='available';
  updateAvailableCode=available?result.versionCode:0;
  $('downloadUpdate').hidden=!available;$('updateInstallHint').hidden=!available;
  $('updateNotes').hidden=!available||!result.notes;$('updateNotes').textContent=available?result.notes||'':'';
  $('updateStatus').textContent=available?'发现新版本 '+result.versionName:result?.status==='current'?'当前已是最新版本。':result?.message||'检测失败，请稍后重试。';
};
$('checkUpdate').onclick=()=>{
  if(updatePending)return;
  $('downloadUpdate').hidden=true;$('updateInstallHint').hidden=true;$('updateNotes').hidden=true;
  if(!bridge?.checkUpdate){$('updateStatus').textContent='请在安卓 App 中检测更新。';return;}
  updatePending=true;updateAvailableCode=0;const requestId=++updateRequestId;
  $('checkUpdate').disabled=true;$('checkUpdate').textContent='检测中…';$('updateStatus').textContent='正在连接官网…';
  updateTimer=setTimeout(()=>window.receiveUpdateCheck({requestId,status:'error',message:'检测超时，请稍后重试。'}),20000);
  try{bridge.checkUpdate(requestId);}catch{window.receiveUpdateCheck({requestId,status:'error',message:'检测失败，请稍后重试。'});}
};
$('downloadUpdate').onclick=()=>{if(bridge?.downloadUpdate&&updateAvailableCode)bridge.downloadUpdate(updateAvailableCode);};
window.updateDownloadFailed=()=>{$('updateStatus').textContent='未找到浏览器，请访问 www.syuct.top 下载安装包。';};
$('saveTimes').onclick=()=>{
  try{const periodTimes=Array.from($('periodTimes').children,row=>Array.from(row.querySelectorAll('input'),i=>i.value));persist({...state,settings:{...state.settings,periodTimes}});message('上课时间已保存，小组件同步更新。');}catch(e){message(e.message);}
};
setInterval(()=>{if(!document.hidden)window.refreshClock();},30000);
window.addEventListener('resize',()=>{if(activePage==='home'&&allDays)requestAnimationFrame(renderHome);});
document.addEventListener('visibilitychange',()=>{if(!document.hidden)window.refreshClock();});
load();renderHome();
