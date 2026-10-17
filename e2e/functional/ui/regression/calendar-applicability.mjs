import assert from 'node:assert/strict';
import {writeFile} from 'node:fs/promises';
import {launchChromeBrowser} from '../lib/chrome-cdp.mjs';
const result={item:8,samples:[]};
try{
for(const [mode,baseUrl] of [['http','https://127.0.0.1'],['local','http://127.0.0.1:4200']]){
 const browser=await launchChromeBrowser({baseUrl});let page;
 try{page=await browser.newPage();await page.connection.send('Page.bringToFront');
 for(const width of [1280,390])for(const view of ['week','month']){
  await page.setViewport({width,height:844});await page.navigate('/home?page=showcase&view='+view);
  await page.waitForSelector(view==='week'?'.smart-list__week-hour-slot':'.smart-list__month-cell');
  const sample=await page.evaluate(({mode,width,view})=>({mode,width,view,cells:[...document.querySelectorAll(view==='week'?'.smart-list__week-hour-slot':'.smart-list__month-cell')].slice(0,10).map(n=>({cursor:getComputedStyle(n).cursor,role:n.getAttribute('role'),tabindex:n.getAttribute('tabindex')}))}),{mode,width,view});
  assert.ok(sample.cells.length);assert.ok(sample.cells.every(c=>c.cursor==='default'&&c.role!=='button'&&c.tabindex===null));result.samples.push(sample);
 }
 }finally{if(page)await page.close();await browser.close();}
}
result.status='PASS';
}catch(error){result.status='FAIL';result.error=error.stack;process.exitCode=1;}
await writeFile('.e2e-artifacts/functional/template-regression/calendar-applicability.json',JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));
