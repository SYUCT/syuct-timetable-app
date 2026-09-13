const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const {chromium}=require('playwright');
const root=path.resolve(__dirname,'../app/src/main/assets');
const fixture=fs.readFileSync(__dirname+'/fixtures/qq-duplicated.anonymized.txt','utf8');
const core=require(root+'/app-core.js');
const codec=require(root+'/timetable-codec.js');
const collector=fs.readFileSync(root+'/collector.js','utf8');
const escape=s=>s.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;');
const mobileCourse='测试课程\n必修\n周一第1,2节{第1-13周|单周}\n教师甲\n通明楼138';
const studentHtml='<input value="DO_NOT_READ_PASSWORD"><p>学号：DO_NOT_READ_ID</p><table><tr><th>时间</th>'+core.weekdays.map(x=>'<th>'+x+'</th>').join('')+'</tr><tr><td>第1节</td><td>'+mobileCourse.replaceAll('\n','<br>')+'</td><td></td><td></td><td></td><td></td><td></td><td></td></tr></table><table><tr><td>未安排上课时间的课程</td></tr><tr><td>安全教育</td></tr></table>';
const gradText='现代设计方法\n教师甲 1班\n节次:1,2节\n周次:2-17\n地点:瑞师楼（原3号教学楼）222\n开课院系:测试学院\n电话:';
const gradHead='<tr><th>节次</th>'+core.weekdays.map(x=>'<th>'+x+'</th>').join('')+'</tr>';
const gradBody='<tr><td>1,2</td><td></td><td>'+gradText.replaceAll('\n','<br>')+'</td>'+Array(5).fill('<td></td>').join('')+'</tr>';
let fixtureHtml=studentHtml;
const server=http.createServer((req,res)=>{
 if(req.url==='/fixture'){res.setHeader('Content-Type','text/html;charset=utf-8');res.end(fixtureHtml);return;}
 if(req.url==='/inner'){res.setHeader('Content-Type','text/html;charset=utf-8');res.end(studentHtml);return;}
 const f=path.join(root,req.url==='/'?'index.html':req.url.replace(/^\//,''));
 if(!f.startsWith(root+path.sep)){res.writeHead(403).end();return;}
 try{res.setHeader('Content-Type',f.endsWith('.js')?'application/javascript':f.endsWith('.css')?'text/css':f.endsWith('.png')?'image/png':'text/html');res.end(fs.readFileSync(f));}catch{res.writeHead(404).end();}
});
(async()=>{
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const url='http://127.0.0.1:'+server.address().port;
 let browser;
 let count=0;
 try{
  browser=await chromium.launch({executablePath:process.env.CHROME_PATH||undefined,headless:true});
  const context=await browser.newContext({viewport:{width:390,height:844},deviceScaleFactor:1});
  await context.addInitScript(times=>{
   window.Native={backToDesktop:()=>window.desktopReturns=(window.desktopReturns||0)+1,reminderSettings:()=>window.reminderRequested=true,community:target=>window.communityTarget=target,overview:value=>window.overviewRequested=value,defaultTimes:()=>JSON.stringify(times),addWidget:()=>window.widgetRequested=true,load:()=>localStorage.getItem('test-state')||'',save:value=>{localStorage.setItem('test-backup',localStorage.getItem('test-state')||'');localStorage.setItem('test-state',value);return '';},copy:value=>window.copied=value,school:kind=>window.openedSchool=kind,clearLogin:()=>window.cleared=true,
    restore:()=>{const old=localStorage.getItem('test-backup');if(!old)return '没有上一版';localStorage.setItem('test-state',old);return '';}};
  },core.defaultTimes);
  const page=await context.newPage(),errors=[];
  page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
  await page.goto(url);await page.locator('#empty').waitFor();
  assert.equal(await page.locator('#weekSelect option').count(),20);count++;console.log('PASS 默认20周，空状态');
  await page.locator('nav [data-page="import"]').click();await page.locator('[data-school="undergraduate"]').click();
  assert.equal(await page.evaluate(()=>window.openedSchool),'undergraduate');count++;console.log('PASS 本科原生入口');
  await page.locator('#import details summary').click();await page.locator('#importText').fill(fixture);await page.locator('#parseText').click();
  assert.equal(await page.locator('.edit-card').count(),20);assert.equal(await page.locator('#saveDraft').isDisabled(),true);count++;console.log('PASS 样本读取进入20条核对，不自动保存');
  await page.locator('#reviewed').check();await page.locator('.edit-card [data-field="room"]').first().fill('通明楼138（已核对）');
  assert.equal(await page.locator('#reviewed').isChecked(),false);assert.equal(await page.locator('#saveDraft').isDisabled(),true);count++;console.log('PASS 编辑后撤销确认，不使用旧结果');
  await page.locator('#reviewed').check();await page.locator('#saveDraft').click();
  const saved=await page.evaluate(()=>JSON.parse(localStorage.getItem('test-state')));
  assert.equal(saved.courses.length,20);assert.equal(saved.courses[0].room,'通明楼138（已核对）');assert.equal(saved.settings.totalWeeks,20);assert.equal(saved.settings.semester,'');count++;console.log('PASS 空学期和日期可保存，持久化采用新结果');
  await page.reload();assert.match(await page.locator('#weekHint').innerText(),/未设置/);
  await page.locator('#toggleAll').click();await page.locator('#overviewNow').click();assert.equal(await page.locator('.week-course.is-outside-week').count(),0);await page.locator('#overviewNow').click();await page.locator('#closeOverview').click();count++;console.log('PASS 日期未设置时不误标非本周');
  await page.locator('nav [data-page="settings"]').click();await page.locator('#exportCode').click();
  const code=await page.evaluate(()=>window.copied);assert.equal(codec.decodeShareCode(code).courses.length,20);count++;console.log('PASS 重载恢复与复制TT2');
  await page.locator('#mainSettings [data-field="totalWeeks"]').fill('1');await page.locator('#saveSettings').click();
  assert.match(await page.locator('#message').innerText(),/超过/);assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('test-state')).settings.totalWeeks),20);count++;console.log('PASS 非法设置不覆盖已保存状态');
  await page.locator('[data-page="home"]').first().click();await page.locator('#weekSelect').selectOption('1');
  await page.getByRole('button',{name:'星期一',exact:true}).click();
  await page.locator('#message').waitFor({state:'hidden'});
  await page.screenshot({path:path.join(__dirname,'../test-results/home.png')});
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth));count++;console.log('PASS 390px手机布局无横向溢出');
  const dangerous={...saved,courses:[{...saved.courses[0],name:'<img src=x onerror=alert(1)>'}]};
  await page.locator('nav [data-page="import"]').click();await page.locator('#import details summary').click();await page.locator('#importText').fill(codec.encodeShareCode(dangerous));await page.locator('#parseText').click();
  assert.equal(await page.locator('#review img').count(),0);assert.match(await page.locator('.edit-card summary').innerText(),/<img/);count++;console.log('PASS 不执行课程文本中的HTML');
  await page.locator('#cancelDraft').click();assert.equal(await page.locator('#import').isVisible(),true);count++;console.log('PASS 放弃草稿不覆盖本机');
  await page.clock.install({time:new Date('2026-08-31T08:10:00+08:00')});
  await page.locator('nav [data-page="home"]').click();await page.locator('#setFirstWeek').click();
  await page.locator('#quickFirstWeek').fill('2026-09-01');await page.locator('#confirmFirstWeek').click();assert.match(await page.locator('#firstWeekError').innerText(),/周一/);
  await page.locator('#quickFirstWeek').fill('2026-08-31');await page.locator('#confirmFirstWeek').click();
  assert.equal(await page.locator('#firstWeekDialog').isVisible(),false);assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('test-state')).settings.firstWeekDate),'2026-08-31');count++;console.log('PASS 首页直接保存第一周，并拒绝非周一');
  assert.ok(await page.locator('.hero').evaluate(n=>n.getBoundingClientRect().height<105));count++;console.log('PASS 首页头部压缩为两行');
  for(const width of [320,360,390,412,550]){
    await page.setViewportSize({width,height:844});await page.locator('#weekSelect').selectOption('20');
    for(const zoom of [1,1.3]){
      await page.locator('.week-control').evaluate((n,scale)=>{n.querySelectorAll('button,select').forEach(x=>x.style.fontSize=(12*scale)+'px');},zoom);
      assert.ok(await page.locator('#thisWeek,#weekSelect').evaluateAll(ns=>ns.every(n=>{const s=getComputedStyle(n),c=document.createElement('canvas').getContext('2d');c.font=s.font;return c.measureText(n.tagName==='SELECT'?n.selectedOptions[0].textContent:n.textContent).width<=n.clientWidth-parseFloat(s.paddingLeft)-parseFloat(s.paddingRight);})),width+'px text fits');
      assert.ok(await page.locator('.week-control').evaluate(n=>Array.from(n.children).every(x=>x.getBoundingClientRect().right<=n.getBoundingClientRect().right+1)));
      assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
    }
  }
  await page.locator('.week-control').evaluate(n=>n.querySelectorAll('button,select').forEach(x=>x.style.removeProperty('font-size')));
  await page.setViewportSize({width:390,height:844});await page.locator('#thisWeek').click();assert.equal(await page.locator('#weekSelect').inputValue(),'1');
  await page.screenshot({path:path.join(__dirname,'../test-results/home-week-fixed.png'),animations:'disabled'});count++;console.log('PASS 首页320–550px与130%文字下本周/第20周完整显示，按钮可返回本周');
  await page.locator('#toggleAll').click();assert.equal(await page.evaluate(()=>window.overviewRequested),undefined);assert.equal(await page.locator('.week-column').count(),7);
  const boxes=await page.locator('.week-column').evaluateAll(nodes=>nodes.map(n=>n.getBoundingClientRect().x));assert.ok(boxes.every((x,i)=>!i||x>boxes[i-1]));
  assert.ok(await page.locator('#weekOverview').evaluate(n=>n.scrollWidth<=n.clientWidth));assert.ok(await page.locator('.week-column').evaluateAll(ns=>ns.every(n=>n.getBoundingClientRect().right<=innerWidth)));assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth&&innerHeight>innerWidth));count++;console.log('PASS 竖屏七天同时可见，无横向裁切');
  assert.equal(await page.locator('nav').isVisible(),false);assert.ok(await page.locator('#overviewToolbar').evaluate(n=>n.clientHeight<=56));assert.ok(await page.locator('#weekOverview').evaluate(n=>n.clientHeight>innerHeight*.85));count++;console.log('PASS 全览单行工具栏，课表占屏85%以上');
  assert.deepEqual(await page.locator('.week-time').evaluateAll(ns=>ns.map(n=>n.getAttribute('aria-label'))),['第 1–2 节，08:00–09:50','第 3–4 节，10:10–12:00','第 5–6 节，13:30–15:20','第 7–8 节，15:40–17:30','第 9–10 节，18:30–20:20']);
  assert.ok(await page.locator('.week-time-column').evaluate(n=>Math.abs(n.getBoundingClientRect().height-document.querySelector('.week-column').getBoundingClientRect().height)<2));count++;console.log('PASS 时间轴显示完整双节时段，与课程行位置对齐');
  assert.ok(await page.locator('#closeOverview,#overviewNow').evaluateAll(ns=>ns.every(n=>{const r=n.getBoundingClientRect();return r.width>=64&&r.height>=44;})));count++;console.log('PASS 返回和范围切换按钮至少64×44');
  for(const width of [360,390,412]){await page.setViewportSize({width,height:844});await page.locator('#overviewWeek').selectOption('20');assert.ok(await page.locator('#overviewWeek').evaluate(n=>{const s=getComputedStyle(n),c=document.createElement('canvas').getContext('2d');c.font=s.font;return c.measureText(n.selectedOptions[0].textContent).width<n.clientWidth-parseFloat(s.paddingLeft)-parseFloat(s.paddingRight);}));assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));}await page.setViewportSize({width:390,height:844});await page.locator('#overviewWeek').selectOption('1');count++;console.log('PASS 三种手机宽度下第20周文字不被箭头遮挡');
  await page.locator('.week-course').first().click();assert.equal(await page.locator('#courseDetail').isVisible(),true);assert.ok(await page.locator('#detailRoom').innerText());assert.match(await page.locator('#detailWeeks').innerText(),/周/);assert.doesNotMatch(await page.locator('#detailTime').innerText(),/\d周/);assert.deepEqual(await page.locator('.detail-label').allTextContents(),['上课时间：','开课时间：','授课教师：','课程地点：']);await page.screenshot({path:path.join(__dirname,'../test-results/detail-labelled.png')});await page.locator('#closeDetail').click();count++;console.log('PASS 详情按时间、周次、教师、地点四行展示');
  await page.locator('#overviewNow').click();assert.equal(await page.locator('#overviewNow').innerText(),'切换');assert.match(await page.locator('#overviewRange').innerText(),/全部安排/);assert.equal(await page.locator('.week-course').count(),saved.courses.length);assert.equal(await page.locator('#overviewWeek').isDisabled(),true);
  assert.equal(await page.locator('.week-course.is-outside-week').count(),saved.courses.filter(c=>!core.inWeek(c,1)).length);assert.ok(await page.locator('.week-course.is-outside-week').count());await page.locator('.week-course.is-outside-week').first().click();assert.equal(await page.locator('#detailStatus').innerText(),'非本周课程');await page.locator('#closeDetail').click();count++;console.log('PASS 非本周按真实当前周判断，卡片和详情均标记');
  await page.screenshot({path:path.join(__dirname,'../test-results/theme-all-weeks.png')});
  await page.locator('#overviewNow').click();assert.equal(await page.locator('#overviewNow').innerText(),'切换');assert.match(await page.locator('#overviewRange').innerText(),/本周/);assert.equal(await page.locator('#overviewWeek').isDisabled(),false);count++;console.log('PASS 切换按钮名称固定，范围提示及课程数量正确');
  const beforeEdit=await page.evaluate(()=>JSON.parse(localStorage.getItem('test-state')));
  await page.locator('.week-course').first().click();const editingName=await page.locator('#detailName').innerText();await page.locator('#editDetail').click();await page.locator('#detailFields [data-field="room"]').fill('取消的修改');await page.locator('#cancelDetailEdit').click();assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('test-state'))),beforeEdit);
  await page.locator('#editDetail').click();await page.locator('#detailFields [data-field="endWeek"]').fill('30');await page.locator('#saveDetail').click();assert.ok(await page.locator('#detailError').innerText());assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('test-state'))),beforeEdit);
  await page.locator('#detailFields [data-field="endWeek"]').fill(String(beforeEdit.courses.find(c=>c.name===editingName).endWeek));await page.locator('#detailFields [data-field="room"]').fill('测试楼301');await page.locator('#saveDetail').click();assert.equal(await page.locator('#courseDetail').isVisible(),false);
  const afterEdit=await page.evaluate(()=>JSON.parse(localStorage.getItem('test-state')));assert.equal(afterEdit.courses.filter((c,i)=>JSON.stringify(c)!==JSON.stringify(beforeEdit.courses[i])).length,1);assert.ok(afterEdit.courses.some(c=>c.room==='测试楼301'));count++;console.log('PASS 详情编辑取消不保存、非法周次拒绝、仅修改所选安排');
  assert.ok(await page.locator('.week-course.is-current').count());await page.locator('#overviewWeek').selectOption('2');assert.equal(await page.locator('.week-course.is-current').count(),0);count++;console.log('PASS 当前课只标记当前教学周');
  await page.evaluate(()=>window.openOverview());assert.equal(await page.locator('#weekSelect').inputValue(),'1');assert.equal(await page.locator('#weekOverview').isVisible(),true);count++;console.log('PASS 小组件入口直接打开当前周全览');
  await page.locator('#closeOverview').click();assert.equal(await page.evaluate(()=>window.overviewRequested),undefined);assert.equal(await page.locator('nav').isVisible(),true);count++;console.log('PASS 返回单日页并恢复导航，不改变方向');
  await page.evaluate(()=>window.openOverview(true));await page.locator('.week-course').first().click();await page.evaluate(()=>window.goHome());assert.equal(await page.evaluate(()=>window.desktopReturns||0),0);await page.evaluate(()=>window.goHome());assert.equal(await page.evaluate(()=>window.desktopReturns),1);
  await page.evaluate(()=>window.openOverview(true));await page.locator('#closeOverview').click();assert.equal(await page.evaluate(()=>window.desktopReturns),2);await page.evaluate(()=>window.openOverview(false));await page.locator('#closeOverview').click();assert.equal(await page.locator('nav').isVisible(),true);count++;console.log('PASS 小组件来源返回桌面，详情先关闭；App内来源返回首页');
  await page.locator('nav [data-page="settings"]').click();await page.locator('#addWidget').click();assert.equal(await page.evaluate(()=>window.widgetRequested),true);count++;console.log('PASS 添加小组件入口调用原生方法');
  await page.locator('#reminderSettings').click();assert.equal(await page.evaluate(()=>window.reminderRequested),true);count++;console.log('PASS 上课提醒入口调用原生权限与开关设置');
  const communityState=await page.evaluate(()=>localStorage.getItem('test-state'));await page.locator('#openCommunity').click();assert.equal(await page.locator('#communityDialog').isVisible(),true);assert.equal(await page.locator('[data-community]').count(),3);assert.match(await page.locator('#communityDialog').innerText(),/github.com\/SYUCT/);assert.match(await page.locator('#communityDialog').innerText(),/新生交流群/);assert.match(await page.locator('#communityDialog').innerText(),/1170264357/);assert.match(await page.locator('#communityDialog').innerText(),/www.syuct.top/);
  for(const target of ['github','group','website']){await page.locator('[data-community="'+target+'"]').click();assert.equal(await page.evaluate(()=>window.communityTarget),target);}
  await page.screenshot({path:path.join(__dirname,'../test-results/community-dialog.png'),animations:'disabled'});await page.evaluate(()=>window.goHome());assert.equal(await page.locator('#communityDialog').isVisible(),false);assert.equal(await page.locator('#settings').isVisible(),true);assert.equal(await page.evaluate(()=>localStorage.getItem('test-state')),communityState);await page.locator('#openCommunity').click();await page.locator('#closeCommunity').click();assert.equal(await page.locator('#communityDialog').isVisible(),false);count++;console.log('PASS 项目交流弹窗三入口与关闭，不更改课表');
  await page.locator('#settings details summary').click();await page.getByLabel('第 1 节开始',{exact:true}).fill('08:20');await page.locator('#saveTimes').click();
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('test-state')).settings.periodTimes[0][0]),'08:20');await page.locator('nav [data-page="home"]').click();assert.ok(await page.locator('.course').count());assert.equal(await page.locator('.course.is-current').count(),0);count++;console.log('PASS 自定义上课时间影响当前课判断');
  await page.locator('#toggleAll').click();assert.equal(await page.locator('.week-time').first().getAttribute('aria-label'),'第 1–2 节，08:20–09:50');count++;console.log('PASS 时间轴使用用户设置，不硬编码时段');
  const beforeDelete=await page.evaluate(()=>JSON.parse(localStorage.getItem('test-state')));
  await page.locator('.week-course').first().click();await page.locator('#editDetail').click();
  const deleteTarget=await page.evaluate(()=>detailIndex),remove=page.locator('#deleteDetail');
  await remove.scrollIntoViewIfNeeded();await page.clock.runFor(250);
  await remove.click();await page.clock.runFor(1500);assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('test-state'))),beforeDelete);count++;console.log('PASS 删除按钮轻点不删除');
  async function startHold(){await remove.scrollIntoViewIfNeeded();const b=await remove.boundingBox();await page.mouse.move(b.x+b.width/2,b.y+b.height/2);await page.mouse.down();}
  await startHold();await page.clock.runFor(600);assert.ok(await remove.evaluate(n=>Number(n.style.getPropertyValue('--hold-progress'))>0));
  await page.screenshot({path:path.join(__dirname,'../test-results/hold-delete.png'),animations:'disabled'});
  await page.mouse.up();await page.clock.runFor(1500);assert.equal(await remove.evaluate(n=>n.style.getPropertyValue('--hold-progress')),'');assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('test-state'))),beforeDelete);count++;console.log('PASS 长按进度可见，提前松手取消');
  for(const reason of ['move','pointercancel','blur','scroll','close']){
    await startHold();await page.clock.runFor(300);
    if(reason==='move')await page.mouse.move(0,0);
    else if(reason==='close')await page.evaluate(()=>document.getElementById('courseDetail').close());
    else if(reason==='scroll')await page.locator('#courseDetail').dispatchEvent('scroll');
    else await remove.dispatchEvent(reason);
    await page.clock.runFor(1500);await page.mouse.up();assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('test-state'))),beforeDelete);
  }
  count++;console.log('PASS 移出、系统取消、失焦、滚动和关闭弹窗均取消删除');
  await page.locator('.week-course').first().click();await page.locator('#editDetail').click();
  await page.evaluate(()=>{window.testSave=Native.save;Native.save=()=> '测试保存失败';});
  await startHold();await page.clock.runFor(1300);await page.mouse.up();assert.match(await page.locator('#detailError').innerText(),/测试保存失败/);assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('test-state'))),beforeDelete);
  await page.evaluate(()=>Native.save=window.testSave);count++;console.log('PASS 删除保存失败保留原课表并提示');
  await startHold();await page.clock.runFor(1300);await page.mouse.up();
  const expectedDelete=structuredClone(beforeDelete);expectedDelete.courses.splice(deleteTarget,1);
  assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('test-state'))),expectedDelete);assert.equal(await page.locator('#courseDetail').isVisible(),false);
  await page.reload();await page.locator('nav [data-page="settings"]').click();await page.locator('#exportCode').click();assert.equal(codec.decodeShareCode(await page.evaluate(()=>window.copied)).courses.length,expectedDelete.courses.length);
  await page.locator('#restore').click();assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('test-state'))),beforeDelete);count++;console.log('PASS 长按只删除所选安排，重载/课表码同步，可恢复');
  await page.evaluate(value=>localStorage.setItem('test-state',JSON.stringify({...value,courses:[value.courses[0]]})),beforeDelete);
  await page.reload();await page.locator('#toggleAll').click();await page.locator('.week-course').first().click();await page.locator('#editDetail').click();
  await remove.scrollIntoViewIfNeeded();await remove.focus();await page.keyboard.down('Space');await page.clock.runFor(1300);await page.keyboard.up('Space');
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('test-state')).courses.length),0);await page.locator('#closeOverview').click();assert.equal(await page.locator('#empty').isVisible(),true);count++;console.log('PASS 键盘长按与最后一条删除，显示空课表');
  await page.evaluate(value=>localStorage.setItem('test-state',JSON.stringify(value)),beforeDelete);await page.reload();
  await page.goto(url+'/fixture');let packet=JSON.parse(await page.evaluate(collector));
  assert.equal(packet.tables.length,1);assert.ok(!JSON.stringify(packet).includes('DO_NOT_READ'));assert.ok(packet.supplemental.some(t=>t.includes('安全教育')));packet.kind='undergraduate';assert.equal(core.parseCapture(packet).courses.length,1);count++;console.log('PASS DOM采集排除账号与密码，保留未排课提示');
  fixtureHtml='<table>'+gradHead+gradBody+'</table>';await page.reload();packet=JSON.parse(await page.evaluate(collector));packet.kind='graduate';assert.equal(core.parseCapture(packet).courses[0].room,'瑞师楼（原3号教学楼）222');count++;console.log('PASS 硕士DOM采集→网格解析');
  fixtureHtml='<div class="datagrid-view"><table>'+gradHead+'</table><table>'+gradBody+'</table></div>';await page.reload();packet=JSON.parse(await page.evaluate(collector));packet.kind='graduate';assert.equal(core.parseCapture(packet).courses.length,1);count++;console.log('PASS 分离表头/表体网格');
  fixtureHtml='<h2>首页摘要</h2><table><tr><td>星期一</td><td>1-2节 (1-13|单周)测试课程</td></tr></table>';await page.reload();packet=JSON.parse(await page.evaluate(collector));packet.kind='undergraduate';assert.throws(()=>core.parseCapture(packet));count++;console.log('PASS 首页缺少完整七天时不猜测');
  const homeHtml='<input value="DO_NOT_READ_PASSWORD"><p>学号 DO_NOT_READ_ID</p><table>'+core.weekdays.map((d,i)=>'<tr><td>'+d+'</td><td>'+(i===0?'<p><i></i>1-2节 (1-13|单周)</p><p>有机化学AI</p><p>通明楼(原5#教学楼)138</p><p>1-2节 (2-10|双周)</p><p>电工学</p><p>通明楼(原5#教学楼)138</p>':'')+'</td><td>'+(i===0?'<p>9-10节 (6-7周)</p><p>职业规划与就业指导</p>':'')+'</td></tr>').join('')+'</table>';
  fixtureHtml=homeHtml;await page.reload();const homePacket=JSON.parse(await page.evaluate(collector));homePacket.kind='undergraduate';
  assert.equal(homePacket.tables[0].source,'undergraduate-home');assert.ok(!JSON.stringify(homePacket).includes('DO_NOT_READ'));
  const homeResult=core.parseCapture(homePacket);assert.equal(homeResult.courses.length,3);assert.ok(homeResult.courses.every(c=>c.teacher===''));assert.equal(homeResult.courses[2].room,'');count++;console.log('PASS 本科首页DOM保留星期、单双周和空教室，不读取账号');
  fixtureHtml=homeHtml+studentHtml;await page.reload();packet=JSON.parse(await page.evaluate(collector));packet.kind='undergraduate';assert.equal(core.parseCapture(packet).courses.length,1);assert.equal(core.parseCapture(packet).courses[0].name,'测试课程');count++;console.log('PASS 首页与个人课表并存时优先个人完整课表');
  const stacked='心理健康\n教师甲 材料+环境学院班\n节次:7,8节\n周次:4-8\n地点:瑞师楼224\n开课院系:研究生院\n电话:\n\n工程热化学概论\n1班(一班多师)\n节次:7,8节\n周次:10-17(教师乙)\n地点:瑞师楼226\n开课院系:测试学院\n电话:';
  fixtureHtml='<div class="datagrid-view"><table>'+gradHead+'</table><table><tr><td>7,8</td>'+Array(4).fill('<td></td>').join('')+'<td>'+stacked.replaceAll('\n','<br>')+'</td><td></td><td></td></tr></table></div><table><tr><td>调课信息表</td><td>星期三7,8节 → 星期三9,10节，2-2周</td></tr></table>';
  await page.reload();const multiPacket=JSON.parse(await page.evaluate(collector));multiPacket.kind='graduate';assert.equal(core.parseCapture(multiPacket).courses.length,2);assert.ok(multiPacket.supplemental.some(t=>t.includes('调课信息表')));count++;console.log('PASS 硕士分离表头同格两课采集，调课信息单独保留');
  await page.goto(url);const beforeCapture=await page.evaluate(()=>localStorage.getItem('test-state'));
  await page.evaluate(p=>window.receiveCapture(p),multiPacket);assert.equal(await page.locator('.edit-card').count(),2);assert.equal(await page.locator('#saveDraft').isDisabled(),true);assert.match(await page.locator('#notices').innerText(),/教师乙/);assert.equal(await page.evaluate(()=>localStorage.getItem('test-state')),beforeCapture);
  await page.locator('#reviewed').check();await page.locator('#saveDraft').click();await page.locator('nav [data-page="settings"]').click();await page.locator('#exportCode').click();const gradCode=await page.evaluate(()=>window.copied);assert.equal(codec.decodeShareCode(gradCode).courses.length,2);count++;console.log('PASS 硕士同格多课→核对→保存→TT2导出，不直接覆盖课表');
  await page.evaluate(p=>window.receiveCapture(p),homePacket);assert.equal(await page.locator('.edit-card').count(),3);assert.match(await page.locator('#notices').innerText(),/教师信息未提供/);assert.equal(await page.locator('#saveDraft').isDisabled(),true);
  await page.locator('.edit-card [data-field="teacher"]').first().fill('补填教师');await page.locator('#reviewed').check();await page.locator('#saveDraft').click();await page.locator('nav [data-page="settings"]').click();await page.locator('#exportCode').click();const homeCode=codec.decodeShareCode(await page.evaluate(()=>window.copied));assert.equal(homeCode.courses[0].teacher,'补填教师');assert.equal(homeCode.courses[1].teacher,'');assert.equal(homeCode.courses.length,3);count++;console.log('PASS 首页导入可补填教师、核对保存并导出TT2');
  await page.goto(url+'/fixture');
  fixtureHtml='<iframe src="/fixture-inner"></iframe>';await page.reload(); // Empty same-origin frame: no timetable.
  packet=JSON.parse(await page.evaluate(collector));assert.ok(packet.error);count++;console.log('PASS 无课表时明确失败');
  fixtureHtml='<table><tr><td><iframe src="/inner"></iframe></td></tr></table>';await page.reload();packet=JSON.parse(await page.evaluate(collector));packet.kind='undergraduate';assert.equal(core.parseCapture(packet).courses.length,1);count++;console.log('PASS 同源框架中的个人课表');
  fixtureHtml=studentHtml+'<iframe sandbox srcdoc="<p>private frame</p>"></iframe>';await page.reload();packet=JSON.parse(await page.evaluate(collector));assert.ok(packet.unreadableFrames);packet.kind='undergraduate';assert.throws(()=>core.parseCapture(packet));count++;console.log('PASS 不可读框架不静默忽略');
  fixtureHtml='<table>'+gradHead+'<tr><td>1,2</td><td></td><td rowspan="2">'+gradText.replaceAll('\n','<br>')+'</td>'+Array(5).fill('<td></td>').join('')+'</tr><tr><td>3,4</td><td></td>'+Array(5).fill('<td></td>').join('')+'</tr></table>';
  await page.reload();packet=JSON.parse(await page.evaluate(collector));packet.kind='graduate';assert.equal(core.parseCapture(packet).courses.length,1);count++;console.log('PASS rowspan不造成重复安排');
  const loginLayout=fs.readFileSync(root+'/school-layout.js','utf8');
  fixtureHtml='<div class="login-container"><div class="login-blk"><div class="login-sec"><div class="login-form"><form><input type="password" value="unchanged"><button>登录</button></form></div></div></div></div>';
  await page.reload();await page.evaluate(loginLayout);await page.evaluate(loginLayout);assert.equal(await page.locator('#syuct-login-layout').count(),1);assert.equal(await page.locator('input').inputValue(),'unchanged');assert.equal(await page.locator('form button').innerText(),'登录');assert.equal(await page.locator('.login-blk').evaluate(n=>getComputedStyle(n).minHeight), '844px');
  fixtureHtml='<p>学生个人课表</p><table><tr><td>课程</td></tr></table>';await page.reload();await page.evaluate(loginLayout);assert.equal(await page.locator('#syuct-login-layout').count(),0);count++;console.log('PASS 只压缩本科登录页空白，不改表单值或已登录课表');
  assert.deepEqual(errors,[]);console.log('Browser tests passed: '+count);
 }finally{await browser?.close();server.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
