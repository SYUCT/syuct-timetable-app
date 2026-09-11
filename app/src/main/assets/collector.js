(function () {
  'use strict';
  // Executed only by a native, explicit user gesture. No cookies, inputs, localStorage
  // or account profile are read. Return a snapshot; never submit or navigate.
  try {
    const tables = [], supplemental = [], visited = new Set();
    let unreadableFrames = 0, totalSize = 0;
    const weekdayCount = text => new Set(text.match(/(?:星期|周)[一二三四五六日天]/g) || []).size;
    function ownRows(table) { return Array.from(table.rows).filter(r => r.closest('table') === table); }
    function grid(table) {
      const output = [];
      ownRows(table).forEach((row,r) => {
        output[r] ||= [];
        let c=0;
        Array.from(row.cells).forEach(cell => {
          while(output[r][c] !== undefined) c++;
          const value=(cell.innerText || cell.textContent || '').trim();
          const rs=Math.min(40,Math.max(1,cell.rowSpan)), cs=Math.min(12,Math.max(1,cell.colSpan));
          for(let y=0;y<rs;y++) for(let x=0;x<cs;x++) {
            output[r+y] ||= []; output[r+y][c+x]=value;
          }
          c+=cs;
        });
      });
      return output;
    }
    function cleanHtml(table) {
      const clone=table.cloneNode(true);
      clone.querySelectorAll('script,style,input,textarea,select,iframe,object,embed,svg,img').forEach(e=>e.remove());
      clone.querySelectorAll('*').forEach(e => Array.from(e.attributes).forEach(a => {
        if(!['rowspan','colspan'].includes(a.name)) e.removeAttribute(a.name);
      }));
      Array.from(clone.attributes).forEach(a=>clone.removeAttribute(a.name));
      return clone.outerHTML;
    }
    function scan(doc, depth) {
      if(depth>4 || visited.has(doc)) return; visited.add(doc);
      const all=Array.from(doc.querySelectorAll('table'));
      all.forEach(table => {
        // Never capture an outer layout table that encloses another table.
        if(table.querySelector('table')) return;
        const text=(table.innerText || table.textContent || '').trim();
        if(!text || text.length>250000) return;
        const explicit=/周[一二三四五六日天]\s*第\s*\d/.test(text);
        const grad=/节次\s*[:：]/.test(text) && /周次\s*[:：]/.test(text);
        const old=/第\s*\d{1,2}\s*节/.test(text) && /节\s*[/／]\s*(?:周|单周|双周)/.test(text);
        let g=grid(table);
        const home=!explicit&&!grad&&!old && /节\s*[（(]/.test(text) && g.some(row=>/^星期[一二三四五六日天]$/.test(String(row[0]||'').trim()));
        if(!explicit && !grad && !old && !home) {
          if(/未安排上课时间|实践课|实习课|调课|调.*停.*补/.test(text)) supplemental.push(text.slice(0,12000));
          return;
        }
        // Some graduate portals separate header/body tables inside one grid view.
        if(grad && weekdayCount(g.slice(0,3).flat().join(' '))<7) {
          const wrapper=table.closest('.datagrid-view,.el-table,.layui-table-view,.ant-table');
          const headers=wrapper ? Array.from(wrapper.querySelectorAll('table')).filter(t=>t!==table) : [];
          const header=headers.map(grid).flat().find(row=>weekdayCount(row.join(' '))===7 && row.length===g[0]?.length);
          if(header) g=[header,...g];
        }
        const html=cleanHtml(table), entry={text,html,grid:g};
        if(home)entry.source='undergraduate-home';
        totalSize+=JSON.stringify(entry).length;
        if(totalSize>600000 || tables.length>=12) throw new Error('页面包含过多表格，请进入当前学期的个人课表页。');
        tables.push(entry);
      });
      // Preserve unscheduled/adjustment notices outside the main table, not the profile.
      doc.querySelectorAll('caption,h3,h4,legend').forEach(e=> {
        if(/未安排|调.*课|实践课|实习课/.test(e.textContent)) supplemental.push(e.textContent.trim().slice(0,500));
      });
      doc.querySelectorAll('iframe,frame').forEach(f=> {
        try {
          if(!f.contentDocument || f.contentWindow.location.origin!==location.origin) { unreadableFrames++; return; }
          scan(f.contentDocument,depth+1);
        } catch { unreadableFrames++; }
      });
    }
    scan(document,0);
    if(!tables.length) return JSON.stringify({error:unreadableFrames ? '课表位于无法读取的跨域框架中，请反馈页面地址以适配。' : '未找到可读取的课表。请登录后打开「学生个人课表」或「我的课程表」。'});
    return JSON.stringify({tables,supplemental:[...new Set(supplemental)],unreadableFrames});
  } catch(e) { return JSON.stringify({error:e.message || '读取失败'}); }
})()
