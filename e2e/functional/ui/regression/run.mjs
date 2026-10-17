import assert from 'node:assert/strict';
import {mkdir, writeFile} from 'node:fs/promises';
import {launchChromeBrowser} from '../lib/chrome-cdp.mjs';
import {stageChatFixture} from '../chat/fixture.mjs';
import {calendar, enter, guide, introduction, chatErrors, notificationLayout, notificationGroupResize} from './assert.mjs';
import {localRows} from './notification-palettes.mjs';

const local=process.argv.includes('--local');
const scrollOnly=process.argv.includes('--notification-scroll-only');
const selected=process.argv.includes('--notification-resize')||scrollOnly?'notification-resize':process.argv.includes('--errors')?'errors':process.argv.includes('--notifications')?'notifications':'layout';
const fixture=local?null:await stageChatFixture();
if(fixture&&['notifications','notification-resize'].includes(selected))await fixture.query(`(()=>{
 const source=db.userNotifications.findOne({_id:'template-welcome-1'});
 if(!source)throw Error('Template notification baseline missing');
 for(let i=0;i<${selected==='notification-resize'?8:12};i++){
  const id='qa-layout:'+${JSON.stringify(fixture.ids[0])}+':'+i;
  db.userNotifications.insertOne({...source,_id:id,syncKey:id,recipientUserId:${JSON.stringify(fixture.ids[0])},createdDate:new Date(Date.now()-Math.floor(i/${selected==='notification-resize'?4:6})*86400000).toISOString()});
 }
})()`);
const browser=await launchChromeBrowser({baseUrl:local
  ?(process.env.E2E_LOCAL_BASE_URL??'http://127.0.0.1:4200')
  :(process.env.E2E_SERVER_BASE_URL??'http://localhost')});
const result={mode:local?'local':'http',startedAt:new Date().toISOString(),checks:{}};
try {
  if(selected==='layout') {
    const intro=await browser.newPage();
    try {result.checks.introduction=await introduction(intro);} finally {await intro.close();}
  }
  const page=await browser.newPage();
  await page.connection.send('Page.bringToFront');
  try {
    await enter(page,{id:fixture?.ids[0]});
    if(local&&selected==='notification-resize'){
      await localRows(page,false,Array.from({length:6},(_,i)=>({title:'QA snap '+i,createdAtIso:new Date(Date.now()-Math.floor(i/3)*86400000).toISOString()})));
      await page.navigate('/home');await page.waitForSelector('.user-selector-menu');
    }
    if(process.argv.includes('--hu')){
      const userAgent=await page.evaluate(()=>navigator.userAgent);
      await page.connection.send('Emulation.setUserAgentOverride',{userAgent,acceptLanguage:'hu-HU,hu'});
    }
    if(selected==='layout') {
      result.checks.calendar=await calendar(page);
      result.checks.guide=await guide(page);
    } else if(selected==='errors')result.checks.chatErrors=await chatErrors(page,{local});
    else if(selected==='notification-resize')result.checks.notificationResize=await notificationGroupResize(page,{scrollOnly});
    else result.checks.notifications=await notificationLayout(page);
    if(selected==='errors'&&process.argv.includes('--hu'))assert.match(result.checks.chatErrors.failed.error,/A beszélgetés nem érhető el/);
  } finally {
    if(local&&selected==='notification-resize')result.localFixtureCleanup=await localRows(page,true);
    await page.close();
  }
  result.status='PASS';
} catch(error) {
  result.status='FAIL';result.error=error.stack;process.exitCode=1;
} finally {
  await browser.close();
  if(fixture)result.cleanup=await fixture.close();
  result.finishedAt=new Date().toISOString();
  const directory='.e2e-artifacts/functional/template-regression';
  await mkdir(directory,{recursive:true});
  await writeFile(`${directory}/${result.mode}-${selected}${scrollOnly?'-scroll-only':process.argv.includes('--hu')?'-hu':'-remainder'}.json`,JSON.stringify(result,null,2)+'\n');
  console.log(JSON.stringify(result));
}
