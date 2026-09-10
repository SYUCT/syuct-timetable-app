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
 try{res.setHeader('Content-Type',f.endsWith('.js')?'application/javascript':f.endsWith('.css')?'text/css':'text/html');res.end(fs.readFileSync(f));}catch{res.writeHead(404).end();}
});
(async()=>{
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const url='http://127.0.0.1:'+server.address().port;
 let browser;
 let count=0;
 try{
  browser=await chromium.launch({executablePath:process.env.CHROME_PATH||undefined,headless:true});
  const context=await browser.newContext({viewport:{width:390,height:844},deviceScaleFactor:1});
  await context.addInitScript(()=>{
   window.Native={load:()=>localStorage.getItem('test-state')||'',save:value=>{localStorage.setItem('test-backup',localStorage.getItem('test-state')||'');localStorage.setItem('test-state',value);return '';},copy:value=>window.copied=value,school:kind=>window.openedSchool=kind,clearLogin:()=>window.cleared=true,
    restore:()=>{const old=localStorage.getItem('test-backup');if(!old)return '没有上一版';localStorage.setItem('test-state',old);return '';}};
  });
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
  await page.goto(url+'/fixture');let packet=JSON.parse(await page.evaluate(collector));
  assert.equal(packet.tables.length,1);assert.ok(!JSON.stringify(packet).includes('DO_NOT_READ'));assert.ok(packet.supplemental.some(t=>t.includes('安全教育')));packet.kind='undergraduate';assert.equal(core.parseCapture(packet).courses.length,1);count++;console.log('PASS DOM采集排除账号与密码，保留未排课提示');
  fixtureHtml='<table>'+gradHead+gradBody+'</table>';await page.reload();packet=JSON.parse(await page.evaluate(collector));packet.kind='graduate';assert.equal(core.parseCapture(packet).courses[0].room,'瑞师楼（原3号教学楼）222');count++;console.log('PASS 硕士DOM采集→网格解析');
  fixtureHtml='<div class="datagrid-view"><table>'+gradHead+'</table><table>'+gradBody+'</table></div>';await page.reload();packet=JSON.parse(await page.evaluate(collector));packet.kind='graduate';assert.equal(core.parseCapture(packet).courses.length,1);count++;console.log('PASS 分离表头/表体网格');
  fixtureHtml='<h2>首页摘要</h2><table><tr><td>星期一</td><td>1-2节 (1-13|单周)测试课程</td></tr></table>';await page.reload();packet=JSON.parse(await page.evaluate(collector));assert.ok(packet.error);count++;console.log('PASS 首页摘要不误读');
  fixtureHtml='<iframe src="/fixture-inner"></iframe>';await page.reload(); // Empty same-origin frame: no timetable.
  packet=JSON.parse(await page.evaluate(collector));assert.ok(packet.error);count++;console.log('PASS 无课表时明确失败');
  fixtureHtml='<table><tr><td><iframe src="/inner"></iframe></td></tr></table>';await page.reload();packet=JSON.parse(await page.evaluate(collector));packet.kind='undergraduate';assert.equal(core.parseCapture(packet).courses.length,1);count++;console.log('PASS 同源框架中的个人课表');
  fixtureHtml=studentHtml+'<iframe sandbox srcdoc="<p>private frame</p>"></iframe>';await page.reload();packet=JSON.parse(await page.evaluate(collector));assert.ok(packet.unreadableFrames);packet.kind='undergraduate';assert.throws(()=>core.parseCapture(packet));count++;console.log('PASS 不可读框架不静默忽略');
  fixtureHtml='<table>'+gradHead+'<tr><td>1,2</td><td></td><td rowspan="2">'+gradText.replaceAll('\n','<br>')+'</td>'+Array(5).fill('<td></td>').join('')+'</tr><tr><td>3,4</td><td></td>'+Array(5).fill('<td></td>').join('')+'</tr></table>';
  await page.reload();packet=JSON.parse(await page.evaluate(collector));packet.kind='graduate';assert.equal(core.parseCapture(packet).courses.length,1);count++;console.log('PASS rowspan不造成重复安排');
  assert.deepEqual(errors,[]);console.log('Browser tests passed: '+count);
 }finally{await browser?.close();server.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
